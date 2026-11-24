package com.example.hy_mt2translator

import android.content.Context
import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineQwen3AsrModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Risultato di una trascrizione, con i tempi per capire quanto costa. */
data class AsrResult(
    val text: String,
    /** In quanti pezzi di parlato è stato diviso l'audio. */
    val segments: Int,
    val audioSeconds: Double,
    /** Tempo per caricare il modello (zero se era già caricato). */
    val loadSeconds: Double,
    /** Tempo per dividere l'audio e trascrivere tutti i pezzi. */
    val transcribeSeconds: Double
)

/**
 * Trascrizione dell'audio con Qwen3-ASR (tramite sherpa-onnx).
 *
 * L'audio si divide nelle pause con un rilevatore di voce (Silero VAD) in pezzi brevi, e ogni pezzo
 * si trascrive da solo: il modello lavora bene su frammenti di una ventina di secondi.
 * La lingua parlata viene riconosciuta dal modello.
 *
 * I file del modello sono: conv_frontend.onnx, encoder.int8.onnx, decoder.int8.onnx, la cartella
 * tokenizer e silero_vad.onnx. Possono stare in due posti:
 * - nell'APK finale, in assets/models/asr: sherpa-onnx li legge direttamente da lì, senza copiarli
 *   (nessuno spazio in più sul telefono);
 * - durante lo sviluppo (APK di debug senza modelli) nella cartella "asr" della memoria interna
 *   (files/asr), copiata con adb e run-as; se manca, si prova ancora quella esterna.
 * Se il modello è nell'APK si usa quello.
 */
object AsrEngine {
    private const val SAMPLE_RATE = 16000

    // Un pezzo di parlato non supera questa durata: così audio, parole generate e istruzioni
    // restano nei limiti di lunghezza del modello (512 elementi in tutto)
    private const val MAX_SPEECH_SECONDS = 18f
    private const val MAX_NEW_TOKENS = 200

    // Dimensione dei blocchi di audio dati al rilevatore di voce
    private const val VAD_WINDOW = 512

    private val REQUIRED_FILES = listOf(
        "conv_frontend.onnx",
        "encoder.int8.onnx",
        "decoder.int8.onnx",
        "tokenizer",
        "silero_vad.onnx"
    )

    // Posizione dei file dentro l'APK (cartella assets)
    private const val ASSET_DIR = "models/asr"

    /** Dove stanno i file del modello. */
    private sealed class Source {
        /** Dentro l'APK: i percorsi sono relativi alla cartella assets. */
        object Apk : Source()

        /** In una cartella del telefono (sviluppo). */
        class Folder(val dir: File) : Source()
    }

    /** Dopo quanto tempo senza registrazioni si libera la memoria del modello (circa un giga). */
    private const val IDLE_UNLOAD_MS = 2 * 60 * 1000L

    // Una trascrizione alla volta
    private val mutex = Mutex()
    private var recognizer: OfflineRecognizer? = null

    // Conto alla rovescia per liberare la memoria: riparte a ogni trascrizione
    private val idleScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var idleJob: Job? = null

    fun modelDir(context: Context): File {
        val internal = File(context.filesDir, "asr")
        return if (internal.exists()) internal else File(context.getExternalFilesDir(null), "asr")
    }

    /** Vero se l'APK contiene il modello (versione finale). Basta elencare la cartella, non si legge nulla. */
    private fun apkHasModel(context: Context): Boolean = try {
        val names = context.assets.list(ASSET_DIR)?.toSet() ?: emptySet()
        val tokenizer = context.assets.list("$ASSET_DIR/tokenizer")?.toSet() ?: emptySet()
        REQUIRED_FILES.all { it in names } && "vocab.json" in tokenizer
    } catch (_: Exception) {
        false
    }

    private fun source(context: Context): Source =
        if (apkHasModel(context)) Source.Apk else Source.Folder(modelDir(context))

    /** Percorso di un file del modello, come lo vuole sherpa-onnx per quella posizione. */
    private fun path(source: Source, name: String): String = when (source) {
        is Source.Apk -> "$ASSET_DIR/$name"
        is Source.Folder -> File(source.dir, name).absolutePath
    }

    /** L'AssetManager va passato solo se i file sono nell'APK; per le cartelle deve essere null. */
    private fun assets(context: Context, source: Source): AssetManager? =
        if (source is Source.Apk) context.assets else null

    /** Nomi dei file del modello che mancano (vuoto se c'è tutto). */
    fun missingFiles(context: Context): List<String> {
        if (apkHasModel(context)) return emptyList()
        val dir = modelDir(context)
        return REQUIRED_FILES.filter { !File(dir, it).exists() }
    }

    /**
     * Trascrive [samples] (16 kHz, mono). Al primo uso carica il modello, e dura qualche secondo.
     * [threads] è il numero di thread del modello: per ora un valore fisso, poi lo sceglierà il benchmark.
     */
    suspend fun transcribe(context: Context, samples: FloatArray, threads: Int? = null): AsrResult =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                // Una nuova trascrizione ferma il conto alla rovescia: il modello serve
                idleJob?.cancel()
                val source = source(context)
                val missing = missingFiles(context)
                require(missing.isEmpty()) {
                    "Mancano file del modello audio in ${modelDir(context).absolutePath}: ${missing.joinToString()}"
                }
                val assetManager = assets(context, source)

                val loadStart = System.nanoTime()
                // Senza un numero esplicito si usano i thread scelti dal benchmark
                val rec = recognizer
                    ?: createRecognizer(source, assetManager, threads ?: BenchmarkStore.chosenAsrThreads(context))
                        .also { recognizer = it }
                val loadSeconds = (System.nanoTime() - loadStart) / 1e9

                val start = System.nanoTime()
                val pieces = splitSpeech(path(source, "silero_vad.onnx"), assetManager, samples)
                val texts = ArrayList<String>()
                for (piece in pieces) {
                    val stream = rec.createStream()
                    try {
                        stream.acceptWaveform(piece, SAMPLE_RATE)
                        rec.decode(stream)
                        val text = rec.getResult(stream).text.trim()
                        if (text.isNotEmpty()) texts.add(text)
                    } finally {
                        stream.release()
                    }
                }

                // Due minuti senza altre trascrizioni: il modello si scarica dalla RAM (i file restano).
                // Alla prossima registrazione si ricarica da solo, con qualche secondo in più.
                idleJob = idleScope.launch {
                    delay(IDLE_UNLOAD_MS)
                    unload()
                }

                AsrResult(
                    text = texts.joinToString(" "),
                    segments = pieces.size,
                    audioSeconds = samples.size / SAMPLE_RATE.toDouble(),
                    loadSeconds = loadSeconds,
                    transcribeSeconds = (System.nanoTime() - start) / 1e9
                )
            }
        }

    /** Libera la memoria del modello (circa un giga). Si ricarica da solo alla prossima trascrizione. */
    suspend fun unload() {
        mutex.withLock {
            recognizer?.release()
            recognizer = null
        }
    }

    private fun createRecognizer(source: Source, assetManager: AssetManager?, threads: Int): OfflineRecognizer {
        val config = OfflineRecognizerConfig(
            modelConfig = OfflineModelConfig(
                qwen3Asr = OfflineQwen3AsrModelConfig(
                    convFrontend = path(source, "conv_frontend.onnx"),
                    encoder = path(source, "encoder.int8.onnx"),
                    decoder = path(source, "decoder.int8.onnx"),
                    tokenizer = path(source, "tokenizer"),
                    maxNewTokens = MAX_NEW_TOKENS
                ),
                tokens = "",
                numThreads = threads,
                provider = "cpu"
            )
        )
        return OfflineRecognizer(assetManager = assetManager, config = config)
    }

    /** Divide l'audio nei pezzi di parlato, usando le pause. Restituisce solo i campioni di ogni pezzo. */
    private fun splitSpeech(vadModelPath: String, assetManager: AssetManager?, samples: FloatArray): List<FloatArray> {
        val vad = Vad(
            assetManager = assetManager,
            config = VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = vadModelPath,
                    threshold = 0.5f,
                    // Una pausa di mezzo secondo chiude un pezzo
                    minSilenceDuration = 0.5f,
                    minSpeechDuration = 0.25f,
                    windowSize = VAD_WINDOW,
                    maxSpeechDuration = MAX_SPEECH_SECONDS
                ),
                sampleRate = SAMPLE_RATE,
                numThreads = 1,
                provider = "cpu"
            )
        )
        val pieces = ArrayList<FloatArray>()
        try {
            var offset = 0
            while (offset < samples.size) {
                val end = minOf(offset + VAD_WINDOW, samples.size)
                vad.acceptWaveform(samples.copyOfRange(offset, end))
                offset = end
                while (!vad.empty()) {
                    pieces.add(vad.front().samples)
                    vad.pop()
                }
            }
            // Chiude l'ultimo pezzo, se l'audio finisce mentre si parla
            vad.flush()
            while (!vad.empty()) {
                pieces.add(vad.front().samples)
                vad.pop()
            }
        } finally {
            vad.release()
        }
        return pieces
    }
}