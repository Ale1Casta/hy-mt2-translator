package com.example.hy_mt2translator

import android.Manifest
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Stato osservabile della registrazione / trascrizione del microfono. */
class MicViewModel(private val scope: CoroutineScope) {

    /** Registrazione in corso. */
    var recording by mutableStateOf(false)
        private set

    /** Registrazione bloccata (l'utente ha scorso in su). */
    /** Secondi registrati fin qui. */
    var seconds by mutableIntStateOf(0)
        private set

    /** Testo prodotto dalla trascrizione più recente. */
    var transcribed by mutableStateOf("")
        private set

    /** Messaggio di errore, o stringa vuota se tutto è a posto. */
    var error by mutableStateOf("")
        private set

    /** Vero mentre la trascrizione è in corso. */
    var transcribing by mutableStateOf(false)
        private set

    /** Vero se la trascrizione viene da un URI (non dal microfono): non mostra la barra. */
    var transcribingFromUri by mutableStateOf(false)
        private set

    private var currentRecorder: Recorder? = null
    private var job: Job? = null

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    /** Avvia la registrazione. */
    fun start(context: Context) {
        if (recording) return
        error = ""
        transcribed = ""
        recording = true
        seconds = 0

        val recorder = Recorder(context)
        currentRecorder = recorder

        job = scope.launch {
            try {
                // Registra (bloccante finché non si chiama recorder.stop())
                recorder.record { secs -> seconds = secs }

                recording = false

                val samples = recorder.samples()
                if (samples.isEmpty()) {
                    error = "Nessun audio registrato."
                    return@launch
                }

                transcribing = true
                val result = AsrEngine.transcribe(context, samples)
                transcribed = result.text
                if (result.text.isBlank()) {
                    error = "Nessun parlato riconosciuto."
                }
            } catch (e: Exception) {
                recording = false
                error = e.message ?: "Errore durante la registrazione."
            } finally {
                transcribing = false
                currentRecorder = null
                AsrEngine.unload()
            }
        }
    }

    /**
     * Ferma la registrazione: segnala al Recorder di smettere (non cancella la coroutine,
     * così la trascrizione parte regolarmente dopo).
     */
    fun stop() {
        if (!recording) return
        currentRecorder?.stop()
        // recording viene azzerato dentro la coroutine dopo che record() ritorna
    }

    fun transcribeUri(context: Context, uri: Uri) {
        if (recording || transcribing) return
        error = ""
        transcribed = ""
        transcribing = true
        transcribingFromUri = true
        job = scope.launch {
            try {
                val samples = withContext(Dispatchers.IO) { AudioDecoder.decodeMono16k(context, uri) }
                if (samples.isEmpty()) { error = "Il file non contiene audio."; return@launch }
                val result = AsrEngine.transcribe(context, samples)
                transcribed = result.text
                if (result.text.isBlank()) error = "Nessun parlato riconosciuto."
            } catch (e: Exception) {
                error = e.message ?: "Errore."
            } finally {
                transcribing = false
                transcribingFromUri = false
                AsrEngine.unload()
            }
        }
    }

    /** Ferma subito tutto (per esempio quando si cambia schermata). */
    fun cancel() {
        currentRecorder?.stop()
        job?.cancel()
        recording = false
        transcribing = false
    }
}