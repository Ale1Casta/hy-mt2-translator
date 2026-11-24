package com.example.hy_mt2translator

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/** Durata massima di una registrazione: 2 minuti. */
const val MAX_RECORDING_SECONDS = 120

/**
 * Registra dal microfono in PCM 16 kHz mono. La registrazione si ferma quando:
 * - si chiama [stop],
 * - si raggiungono [MAX_RECORDING_SECONDS].
 *
 * Uso tipico (dalla coroutine):
 *   val recorder = Recorder(context)
 *   launch { recorder.record { seconds -> aggiorna UI } }
 *   // ... per fermarsi:
 *   recorder.stop()
 *   val samples = recorder.samples()
 */
class Recorder(private val context: Context) {

    private val SAMPLE_RATE = 16_000
    private val CHANNEL   = AudioFormat.CHANNEL_IN_MONO
    private val ENCODING  = AudioFormat.ENCODING_PCM_16BIT

    private val running = AtomicBoolean(false)
    private var buffer  = FloatArray(0)

    /** Avvia la registrazione. Bloccante: da chiamare su Dispatchers.Default o IO. */
    suspend fun record(onProgress: (Int) -> Unit = {}) = withContext(Dispatchers.IO) {
        val minBuf  = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        val bufSize = maxOf(minBuf, SAMPLE_RATE / 2 * 2)   // ~0.5 secondi
        val ar = AudioRecord(
            MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL, ENCODING, bufSize
        )
        require(ar.state == AudioRecord.STATE_INITIALIZED) {
            "Microfono non disponibile. Il permesso RECORD_AUDIO è stato concesso?"
        }

        val shortBuf = ShortArray(bufSize / 2)
        val all      = ArrayList<Float>(SAMPLE_RATE * 10)
        val maxSamples = SAMPLE_RATE * MAX_RECORDING_SECONDS

        running.set(true)
        ar.startRecording()
        try {
            while (running.get() && all.size < maxSamples) {
                val read = ar.read(shortBuf, 0, shortBuf.size)
                if (read <= 0) break
                for (i in 0 until read) all.add(shortBuf[i] / 32768f)
                onProgress(all.size / SAMPLE_RATE)
            }
        } finally {
            ar.stop()
            ar.release()
            running.set(false)
        }
        buffer = all.toFloatArray()
    }

    /** Ferma la registrazione ordinatamente (sicuro da chiamare da qualsiasi thread). */
    fun stop() { running.set(false) }

    /** Campioni registrati. Chiamare dopo che [record] è tornato. */
    fun samples(): FloatArray = buffer
}