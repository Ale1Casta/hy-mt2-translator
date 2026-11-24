package com.example.hy_mt2translator

import android.content.Context
import android.os.StatFs
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arm.aichat.InferenceEngine
import com.example.hy_mt2translator.ui.theme.GlassPillButton
import com.example.hy_mt2translator.ui.theme.LocalGlass
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Nome del file del modello. */
private const val MODEL_FILE_NAME = "Hy-MT2-1.8B-Q4_K_M.gguf"

/** Posizione del modello dentro l'APK finale (cartella assets). */
private const val MODEL_ASSET = "models/$MODEL_FILE_NAME"

/** Spazio libero da lasciare oltre al modello, per non riempire del tutto il telefono. */
private const val SPACE_MARGIN_BYTES = 200L * 1024 * 1024

/**
 * Si occupa di caricare il modello, in un punto solo per tutta l'app,
 * e di ricordare l'ultimo problema incontrato per mostrarlo all'utente.
 */
object ModelManager {
    /** Ultimo problema nel caricare il modello (vuoto se tutto va bene). */
    var lastError by mutableStateOf("")
        private set

    /**
     * Avanzamento della copia del modello al primo avvio, da 0 a 1; null quando non si sta copiando.
     * llama.cpp sa aprire il modello solo da un file vero, non da dentro l'APK: per questo si copia una volta.
     */
    var preparing by mutableStateOf<Float?>(null)
        private set

    /** Copia fatta a mano con adb durante lo sviluppo (APK di debug senza modelli). */
    private fun devFile(context: Context) = File(context.getExternalFilesDir(null), MODEL_FILE_NAME)

    /** Copia estratta dall'APK finale, nella memoria interna dell'app. */
    private fun internalFile(context: Context) = File(context.filesDir, MODEL_FILE_NAME)

    /** Dimensione del modello dentro l'APK, o null se l'APK non lo contiene (APK di debug). */
    private fun assetSize(context: Context): Long? = try {
        // openFd funziona perché il file nell'APK non è compresso (vedi build.gradle.kts)
        context.assets.openFd(MODEL_ASSET).use { it.length }
    } catch (_: Exception) {
        null
    }

    /**
     * Il file da caricare. Ordine: copia di sviluppo, se c'è; poi la copia interna, se è completa;
     * altrimenti la si estrae dall'APK. Null se il modello non si trova da nessuna parte.
     */
    private suspend fun modelFile(context: Context): File? {
        devFile(context).takeIf { it.exists() }?.let { return it }
        val size = assetSize(context)
        val internal = internalFile(context)
        if (size == null) return internal.takeIf { it.exists() }
        // Se la dimensione coincide la copia è completa (e dello stesso modello dell'APK)
        if (internal.exists() && internal.length() == size) return internal
        extractModel(context, size, internal)
        return internal
    }

    /** Copia il modello dall'APK alla memoria interna, mostrando l'avanzamento. */
    private suspend fun extractModel(context: Context, size: Long, target: File) = withContext(Dispatchers.IO) {
        val free = StatFs(context.filesDir.absolutePath).availableBytes + (if (target.exists()) target.length() else 0L)
        check(free >= size + SPACE_MARGIN_BYTES) {
            "Spazio insufficiente per preparare il modello: servono circa ${(size + SPACE_MARGIN_BYTES) / 1_048_576} MB " +
                    "liberi, ce ne sono ${free / 1_048_576}. Libera spazio e riprova."
        }
        // Si scrive su un file temporaneo e lo si rinomina solo alla fine: se l'app si chiude a metà,
        // al prossimo avvio la copia riparte da capo invece di usare un file incompleto
        val part = File(target.parentFile, "$MODEL_FILE_NAME.part")
        try {
            preparing = 0f
            context.assets.open(MODEL_ASSET).use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(1 shl 20)
                    var copied = 0L
                    var lastShown = 0f
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        copied += n
                        val progress = copied.toFloat() / size
                        // Aggiorna la schermata ogni 1%, non a ogni blocco
                        if (progress - lastShown >= 0.01f) {
                            lastShown = progress
                            preparing = progress
                        }
                    }
                }
            }
            target.delete()
            check(part.renameTo(target)) { "Impossibile completare la copia del modello." }
        } finally {
            part.delete()
            preparing = null
        }
    }

    /** Carica il modello se non lo è già. Si può chiamare più volte senza danni. */
    suspend fun ensureLoaded(context: Context, engine: InferenceEngine) {
        // Applica i thread scelti dal benchmark: scrittura e lettura del testo, separati
        engine.applyThreads(BenchmarkStore.chosenThreads(context), BenchmarkStore.chosenBatchThreads(context))
        lastError = ""
        try {
            // Aspetta che il motore nativo sia pronto (o già con il modello caricato)
            val current = engine.state.first {
                it is InferenceEngine.State.Initialized ||
                        it is InferenceEngine.State.ModelReady ||
                        it is InferenceEngine.State.Error
            }
            if (current is InferenceEngine.State.Initialized) {
                val file = modelFile(context)
                if (file == null) {
                    lastError = "Modello non trovato. Copia il file $MODEL_FILE_NAME " +
                            "nella cartella: ${devFile(context).parent}"
                    return
                }
                engine.loadModel(file.absolutePath)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastError = "Errore nel caricare il modello: ${e.message}"
        }
    }

    /** Dopo un problema: azzera lo stato del motore e riprova a caricare il modello. */
    suspend fun retry(context: Context, engine: InferenceEngine) {
        // Se il motore è in errore, cleanUp() lo riporta allo stato iniziale;
        // negli altri casi può dare errore, e non ha importanza
        withContext(Dispatchers.Default) { runCatching { engine.cleanUp() } }
        ensureLoaded(context, engine)
    }
}

/**
 * Avviso con il pulsante "Riprova": compare solo se il modello manca o il motore è andato in errore.
 * Si usa sia in Traduci sia in Chat.
 */
@Composable
fun ModelProblem(
    engine: InferenceEngine,
    engineState: InferenceEngine.State,
    modifier: Modifier = Modifier
) {
    val loadError = ModelManager.lastError
    val g = LocalGlass.current

    // Primo avvio dell'APK finale: il modello si sta copiando, con la percentuale
    ModelManager.preparing?.let { progress ->
        Text(
            text = "Preparo il modello di traduzione (solo al primo avvio)… ${(progress * 100).toInt()}%",
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = g.textMuted,
            modifier = modifier
        )
        return
    }

    if (loadError.isEmpty() && engineState !is InferenceEngine.State.Error) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val text = loadError.ifEmpty {
        val detail = (engineState as? InferenceEngine.State.Error)?.exception?.message
        if (detail.isNullOrBlank()) "Il motore ha avuto un problema." else "Il motore ha avuto un problema: $detail"
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = text, fontSize = 12.sp, lineHeight = 17.sp, color = g.danger)
        GlassPillButton(
            onClick = { scope.launch { ModelManager.retry(context.applicationContext, engine) } },
            height = 40.dp,
            modifier = Modifier.align(Alignment.Start)
        ) {
            Text("Riprova", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}