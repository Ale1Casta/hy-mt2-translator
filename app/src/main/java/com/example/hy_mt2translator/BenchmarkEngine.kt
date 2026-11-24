package com.example.hy_mt2translator

import android.content.Context
import android.net.Uri
import android.os.PowerManager
import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Esito di un benchmark completo. Ogni campo è null se il rispettivo test è andato a buon fine:
 * altrimenti contiene il motivo, da mostrare all'utente.
 */
data class BenchmarkOutcome(val hyError: String?, val asrNote: String?)

/**
 * Benchmark dei due modelli, uno dopo l'altro (mai insieme), con un limite di tempo complessivo.
 *
 * Le configurazioni dipendono dall'hardware letto da [CpuTopologyDetector]. Al motore arriva solo il
 * numero totale di thread: la composizione veloci+lenti è la categoria *richiesta* (veloci per primi)
 * e non è garantita, perché l'assegnazione dei thread ai core non è controllata (né verificata).
 *
 * Hy-MT2: ogni configurazione si misura in due giri, il secondo in ordine inverso, con un
 * riscaldamento prima di ogni misura; il valore è la mediana di tutte le prove. Lettura (pp32) e
 * scrittura (tg16) si scelgono separatamente, perché llama.cpp usa due numeri di thread distinti
 * (n_threads_batch per la lettura, n_threads per la scrittura) e li applica insieme.
 *
 * Qwen3-ASR: audio di prova fisso con parlato (assets/bench_speech.wav), caricamento escluso.
 *
 * Per entrambi, se una configurazione con meno thread è entro il 5% della migliore, si sceglie quella.
 */
object BenchmarkEngine {
    private const val PP = 32
    private const val TG = 16

    /** Giri per Hy-MT2 (il secondo in ordine inverso) e prove per ogni giro. */
    private const val ROUNDS = 2
    private const val REPS_PER_ROUND = 2

    /** Differenza sotto la quale si preferisce la configurazione con meno thread. */
    private const val TOLERANCE = 0.05

    /** Calo oltre il quale si segnala un probabile riscaldamento. */
    private const val DRIFT_WARNING = 0.10

    /** Attesa massima per far raffreddare il telefono prima di una prova. */
    private const val MAX_COOL_DOWN_SECONDS = 30

    private const val BUDGET_SECONDS = 300L
    private const val ASR_REPS = 3
    private const val CLIP_ASSET = "bench_speech.wav"
    private const val SAMPLE_RATE = 16_000

    /** Si attiva se il telefono diventa troppo caldo: il test si ferma e salva ciò che ha misurato. */
    private class HeatState {
        var tooHot = false
        var warm = false
    }

    /**
     * Esegue prima Hy-MT2, poi Qwen3-ASR se il modello audio è installato. È il solo punto d'ingresso:
     * lo usano sia il primo avvio sia il pulsante nelle Impostazioni.
     * Il modello di traduzione deve essere già caricato.
     */
    suspend fun runAll(
        context: Context,
        engine: InferenceEngine,
        onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }
    ): BenchmarkOutcome = withContext(Dispatchers.Default) {
        val deadline = System.nanoTime() + BUDGET_SECONDS * 1_000_000_000L
        val topology = CpuTopologyDetector.detect()
        val hyConfigs = ThreadConfigs.forLlm(topology)
        val asrAvailable = AsrEngine.missingFiles(context).isEmpty()
        val asrConfigs = if (asrAvailable) ThreadConfigs.forAsr(topology) else emptyList()
        val heat = HeatState()

        val total = hyConfigs.size * ROUNDS + asrConfigs.size
        var done = 0
        val step: (String) -> Unit = { desc ->
            done++
            onProgress(done, total, desc)
        }

        val hyError = try {
            runHyMt2(context, engine, topology, hyConfigs, deadline, heat, step)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.message ?: "Test di Hy-MT2 non riuscito."
        }

        val asrNote = when {
            !asrAvailable -> "Il modello audio non è installato: testato solo Hy-MT2."
            heat.tooHot -> "Test di Qwen3-ASR saltato: il telefono è troppo caldo. Riprova più tardi."
            else -> try {
                runAsr(context, topology, asrConfigs, deadline, heat, step)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.message ?: "Test di Qwen3-ASR non riuscito."
            }
        }

        BenchmarkStore.saveLastNotes(context, hyError, asrNote)
        BenchmarkOutcome(hyError, asrNote)
    }

    /**
     * Stato dei prerequisiti del test audio, da mostrare nelle Impostazioni:
     * modello installato e audio di prova presente. Null se è tutto a posto.
     */
    fun clipStatus(context: Context): String? {
        val missingModel = AsrEngine.missingFiles(context).isNotEmpty()
        val hasClip = try {
            context.assets.open(CLIP_ASSET).close(); true
        } catch (_: Exception) { false }
        return when {
            missingModel && !hasClip -> "Per testare Qwen3-ASR mancano il modello audio e $CLIP_ASSET (cartella assets)."
            missingModel -> "Per testare Qwen3-ASR manca il modello audio."
            !hasClip -> "Per testare Qwen3-ASR manca $CLIP_ASSET nella cartella assets dell'app."
            else -> null
        }
    }

    private suspend fun runHyMt2(
        context: Context,
        engine: InferenceEngine,
        topology: CpuTopology,
        configs: List<ThreadConfig>,
        deadline: Long,
        heat: HeatState,
        step: (String) -> Unit
    ) {
        val ppValues = configs.map { ArrayList<Double>() }
        val tgValues = configs.map { ArrayList<Double>() }
        // Mediana della scrittura per giro: serve a vedere se il telefono rallenta scaldandosi
        val roundTg = Array(ROUNDS) { DoubleArray(configs.size) }
        val notes = ArrayList<String>()
        var roundsDone = 0

        rounds@ for (round in 0 until ROUNDS) {
            if (round > 0 && System.nanoTime() > deadline) {
                notes += "Secondo giro saltato per il limite di tempo: valori da un giro solo."
                break
            }
            // Ordine inverso nel secondo giro: nessuna configurazione è sempre la prima (a freddo)
            // o sempre l'ultima (a caldo)
            val order = if (round % 2 == 0) configs.indices.toList() else configs.indices.reversed()
            for (i in order) {
                if (!coolDown(context, heat)) {
                    notes += "Test fermato: il telefono è troppo caldo. I valori mostrati sono parziali."
                    break@rounds
                }
                val cfg = configs[i]
                step("Hy-MT2 · ${describe(cfg, topology)} (giro ${round + 1} di $ROUNDS)")
                val raw = engine.benchThreads(PP, TG, REPS_PER_ROUND, cfg.total, cfg.total)
                check(!raw.startsWith("error")) { "Misura di Hy-MT2 non riuscita ($raw)." }
                val (pp, tg) = parseSeries(raw)
                check(pp.isNotEmpty() && tg.isNotEmpty() && pp.all { it > 0 } && tg.all { it > 0 }) {
                    "Misura di Hy-MT2 non valida: $raw"
                }
                ppValues[i].addAll(pp)
                tgValues[i].addAll(tg)
                roundTg[round][i] = median(tg)
            }
            roundsDone++
        }

        // La prima configurazione è misurata all'inizio e alla fine: se è calata, il telefono si è scaldato
        if (roundsDone == ROUNDS && configs.isNotEmpty()) {
            val first = roundTg[0][0]
            val last = roundTg[ROUNDS - 1][0]
            if (first > 0 && (first - last) / first > DRIFT_WARNING) {
                notes += "La scrittura è calata del ${((first - last) / first * 100).roundToInt()}% tra inizio e " +
                        "fine del test (probabile riscaldamento): i confronti sono meno affidabili."
            }
        }
        if (heat.warm) notes += "Il telefono era caldo durante il test: i valori possono essere più bassi del solito."

        val measured = configs.indices.filter { tgValues[it].isNotEmpty() }
        check(measured.isNotEmpty()) { notes.firstOrNull() ?: "Nessuna misura di Hy-MT2 completata." }

        val rows = measured.map { i ->
            val cfg = configs[i]
            BenchRow(
                total = cfg.total,
                fast = cfg.fast,
                slow = cfg.slow,
                ppTokensPerSec = round1(median(ppValues[i])),
                tgTokensPerSec = round1(median(tgValues[i])),
                ppSpreadPercent = spreadPercent(ppValues[i]),
                tgSpreadPercent = spreadPercent(tgValues[i])
            )
        }
        val totals = rows.map { it.total }
        val bestTg = pickWithTolerance(rows.map { it.tgTokensPerSec }, totals, higherIsBetter = true)
        val bestPp = pickWithTolerance(rows.map { it.ppTokensPerSec }, totals, higherIsBetter = true)
        val genThreads = rows[bestTg].total
        val batchThreads = rows[bestPp].total

        // Vale subito: non serve riavviare l'app
        engine.applyThreads(genThreads, batchThreads)

        BenchmarkStore.saveHyMt2(
            context,
            BenchmarkResult(
                rows = rows,
                bestIndex = bestTg,
                timestamp = System.currentTimeMillis(),
                chosenThreads = genThreads,
                hardware = hardwareInfo(topology),
                bestPpIndex = bestPp,
                chosenBatchThreads = batchThreads,
                note = notes.joinToString(" ").ifBlank { null }
            )
        )
    }

    /** Ritorna una nota se il test è stato interrotto, altrimenti null; lancia un'eccezione se fallisce. */
    private suspend fun runAsr(
        context: Context,
        topology: CpuTopology,
        configs: List<ThreadConfig>,
        deadline: Long,
        heat: HeatState,
        step: (String) -> Unit
    ): String? {
        val samples = loadClip(context)
            ?: throw IllegalStateException(
                "Audio di prova non trovato: aggiungi bench_speech.wav alla cartella assets dell'app."
            )
        val audioSeconds = samples.size / SAMPLE_RATE.toDouble()
        val rows = ArrayList<AsrBenchRow>()
        var ramMb = 0L
        val notes = ArrayList<String>()
        heat.warm = false   // l'avviso sul caldo di Hy-MT2 è già nella sua nota

        try {
            for (cfg in configs) {
                if (rows.isNotEmpty() && System.nanoTime() > deadline) {
                    notes += "Test interrotto per il limite di tempo: provate ${rows.size} configurazioni su ${configs.size}."
                    break
                }
                if (!coolDown(context, heat)) {
                    notes += "Test fermato: il telefono è troppo caldo. Provate ${rows.size} configurazioni su ${configs.size}."
                    break
                }
                step("Qwen3-ASR · ${describe(cfg, topology)}")

                // Il numero di thread è fissato alla creazione del riconoscitore: si scarica e si ricrea
                AsrEngine.unload()
                val rssBefore = MemoryProbe.rssMb()

                // Riscaldamento: carica il modello (tempo escluso dalla misura) e fa una trascrizione di prova
                val warmUp = AsrEngine.transcribe(context, samples, cfg.total)
                check(warmUp.segments > 0) { "Nell'audio di prova non è stato riconosciuto parlato." }
                ramMb = maxOf(ramMb, MemoryProbe.rssMb() - rssBefore)

                // Misure vere: stesso audio, modello già caricato
                val times = (1..ASR_REPS).map {
                    AsrEngine.transcribe(context, samples, cfg.total).transcribeSeconds
                }
                val med = median(times)
                rows.add(
                    AsrBenchRow(
                        total = cfg.total,
                        fast = cfg.fast,
                        slow = cfg.slow,
                        latencySeconds = round2(med),
                        rtf = round2(med / audioSeconds),
                        spreadPercent = spreadPercent(times)
                    )
                )
            }
        } finally {
            AsrEngine.unload()
        }
        check(rows.isNotEmpty()) { notes.firstOrNull() ?: "Nessuna misura di Qwen3-ASR completata." }
        if (heat.warm) notes += "Il telefono era caldo durante il test: i valori possono essere più alti del solito."

        val bestIdx = pickWithTolerance(rows.map { it.rtf }, rows.map { it.total }, higherIsBetter = false)
        val note = notes.joinToString(" ").ifBlank { null }
        BenchmarkStore.saveAsr(
            context,
            AsrBenchmarkResult(
                rows = rows,
                bestIndex = bestIdx,
                timestamp = System.currentTimeMillis(),
                chosenThreads = rows[bestIdx].total,
                audioSeconds = audioSeconds,
                ramMb = ramMb,
                note = note,
                hardware = hardwareInfo(topology)
            )
        )
        return note
    }

    // ===== Scelta e statistica =====

    /**
     * Indice della configurazione scelta: la migliore, oppure quella con meno thread se resta entro
     * la tolleranza dalla migliore (più thread consumano di più senza un vantaggio reale).
     */
    internal fun pickWithTolerance(
        values: List<Double>,
        totals: List<Int>,
        higherIsBetter: Boolean,
        tolerance: Double = TOLERANCE
    ): Int {
        require(values.isNotEmpty() && values.size == totals.size)
        val best = if (higherIsBetter) values.max() else values.min()
        val candidates = values.indices.filter { i ->
            if (higherIsBetter) values[i] >= best * (1 - tolerance) else values[i] <= best * (1 + tolerance)
        }
        return candidates.minWith(
            compareBy<Int>({ totals[it] }, { if (higherIsBetter) -values[it] else values[it] })
        )
    }

    internal fun median(values: List<Double>): Double {
        val s = values.sorted()
        val n = s.size
        if (n == 0) return 0.0
        return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2
    }

    /** Differenza tra la prova più lenta e la più veloce, in percentuale della mediana. */
    internal fun spreadPercent(values: List<Double>): Int {
        val m = median(values)
        if (values.size < 2 || m <= 0) return 0
        return ((values.max() - values.min()) / m * 100).roundToInt()
    }

    /** Legge "pp=22.1,22.4;tg=8.5,8.4" restituito da benchThreads. */
    internal fun parseSeries(raw: String): Pair<List<Double>, List<Double>> {
        val parts = raw.trim().split(";").associate { part ->
            val key = part.substringBefore("=").trim()
            val values = part.substringAfter("=", "").split(",").mapNotNull { it.trim().toDoubleOrNull() }
            key to values
        }
        return (parts["pp"] ?: emptyList()) to (parts["tg"] ?: emptyList())
    }

    // ===== Temperatura =====

    /**
     * Prima di ogni prova controlla lo stato termico di Android. Se il telefono è caldo aspetta fino a
     * [MAX_COOL_DOWN_SECONDS] secondi; se è molto caldo restituisce false e il test si ferma.
     */
    private suspend fun coolDown(context: Context, heat: HeatState): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        var status = pm.currentThermalStatus
        var waited = 0
        while (status == PowerManager.THERMAL_STATUS_MODERATE && waited < MAX_COOL_DOWN_SECONDS) {
            delay(5_000)
            waited += 5
            status = pm.currentThermalStatus
        }
        if (status >= PowerManager.THERMAL_STATUS_SEVERE) {
            heat.tooHot = true
            return false
        }
        if (status >= PowerManager.THERMAL_STATUS_MODERATE) heat.warm = true
        return true
    }

    private fun describe(cfg: ThreadConfig, topology: CpuTopology): String =
        if (topology.hasDistinction) "${cfg.fast}+${cfg.slow}" else "${cfg.total} thread"

    private fun hardwareInfo(t: CpuTopology) = HardwareInfo(
        summary = t.summary(),
        hasDistinction = t.hasDistinction,
        details = t.details()
    )

    private fun round1(x: Double) = (x * 10).roundToInt() / 10.0
    private fun round2(x: Double) = (x * 100).roundToInt() / 100.0

    // ===== Audio di prova =====

    /** Legge assets/bench_speech.wav (16 bit PCM) o, se è un altro formato, lo decodifica con Android. */
    private fun loadClip(context: Context): FloatArray? {
        val bytes = try {
            context.assets.open(CLIP_ASSET).use { it.readBytes() }
        } catch (_: Exception) {
            return null
        }
        readWavPcm16(bytes)?.let { return it }

        // Altro formato: si passa dal decodificatore di Android, tramite un file temporaneo
        val tmp = File(context.cacheDir, "bench_speech_tmp")
        return try {
            tmp.writeBytes(bytes)
            AudioDecoder.decodeMono16k(context, Uri.fromFile(tmp))
        } catch (_: Exception) {
            null
        } finally {
            tmp.delete()
        }
    }

    /** WAV a 16 bit: media dei canali e portato a 16 kHz. Null se non è in questo formato. */
    private fun readWavPcm16(bytes: ByteArray): FloatArray? {
        if (bytes.size < 44) return null
        if (bytes.decodeToString(0, 4) != "RIFF" || bytes.decodeToString(8, 12) != "WAVE") return null
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        var pos = 12
        var format = 0
        var channels = 0
        var rate = 0
        var bits = 0
        var dataStart = -1
        var dataLen = 0
        while (pos + 8 <= bytes.size) {
            val id = bytes.decodeToString(pos, pos + 4)
            val size = bb.getInt(pos + 4)
            val body = pos + 8
            if (id == "fmt " && body + 16 <= bytes.size) {
                format = bb.getShort(body).toInt() and 0xFFFF
                channels = bb.getShort(body + 2).toInt()
                rate = bb.getInt(body + 4)
                bits = bb.getShort(body + 14).toInt()
            } else if (id == "data") {
                dataStart = body
                dataLen = if (size < 0 || size > bytes.size - body) bytes.size - body else size
                break
            }
            if (size < 0) return null
            pos = body + size + (size and 1)
        }
        if (dataStart < 0 || format != 1 || bits != 16 || channels < 1 || rate <= 0) return null

        val frames = dataLen / (2 * channels)
        val mono = FloatArray(frames) { i ->
            var sum = 0f
            for (c in 0 until channels) {
                sum += bb.getShort(dataStart + (i * channels + c) * 2) / 32768f
            }
            sum / channels
        }
        if (rate == SAMPLE_RATE) return mono

        val ratio = rate.toDouble() / SAMPLE_RATE
        val outSize = (mono.size / ratio).toInt()
        return FloatArray(outSize) { i ->
            val p = i * ratio
            val idx = p.toInt()
            val frac = (p - idx).toFloat()
            val a = mono[idx]
            val b = if (idx + 1 < mono.size) mono[idx + 1] else a
            a + (b - a) * frac
        }
    }

    // ===== Memoria =====

    /**
     * Memoria residente dell'intero processo, in MB, letta da /proc. A differenza dello heap Java
     * comprende la memoria nativa usata da ONNX Runtime (era il motivo per cui la colonna RAM era a zero).
     */
    private object MemoryProbe {
        fun rssMb(): Long = try {
            File("/proc/self/status").useLines { lines ->
                lines.firstOrNull { it.startsWith("VmRSS:") }
                    ?.split(Regex("\\s+"))?.getOrNull(1)?.toLongOrNull()
            }?.div(1024) ?: 0L
        } catch (_: Exception) {
            0L
        }
    }
}