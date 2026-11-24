package com.example.hy_mt2translator

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Legge un file audio (o la traccia audio di un video) con le funzioni di Android e lo trasforma
 * in quello che serve al modello: campioni a 16 kHz, un solo canale, valori tra -1 e 1.
 * Si usa solo ciò che Android sa decodificare da solo: per un formato non supportato lancia un errore
 * con un messaggio comprensibile.
 */
object AudioDecoder {
    const val TARGET_SAMPLE_RATE = 16000

    /** Da chiamare fuori dal thread principale. */
    fun decodeMono16k(context: Context, uri: Uri): FloatArray {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)

            // Cerca la prima traccia audio
            var trackIndex = -1
            var inputFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    inputFormat = format
                    break
                }
            }
            if (trackIndex < 0 || inputFormat == null) {
                throw IllegalArgumentException("Il file non contiene una traccia audio.")
            }
            extractor.selectTrack(trackIndex)

            val mime = inputFormat.getString(MediaFormat.KEY_MIME)!!
            codec = try {
                MediaCodec.createDecoderByType(mime)
            } catch (e: Exception) {
                throw IllegalArgumentException("Android non sa leggere questo formato audio ($mime).")
            }
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var isFloat = false

            val chunks = ArrayList<FloatArray>()
            var total = 0
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                // Dà al decodificatore i pezzi di file compresso
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val buffer = codec.getInputBuffer(inputIndex)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(
                                inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                // Raccoglie l'audio decodificato
                val outputIndex = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outputIndex >= 0 -> {
                        if (info.size > 0) {
                            val buffer = codec.getOutputBuffer(outputIndex)!!
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            val mono = toMono(buffer, channels, isFloat)
                            chunks.add(mono)
                            total += mono.size
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }

                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        // Il formato reale dell'audio decodificato può differire da quello del file
                        val format = codec.outputFormat
                        sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        isFloat = format.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                                format.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    }
                }
            }

            val mono = FloatArray(total)
            var position = 0
            for (chunk in chunks) {
                chunk.copyInto(mono, position)
                position += chunk.size
            }
            return resample(mono, sampleRate)
        } finally {
            try {
                codec?.stop()
            } catch (_: Exception) {
                // già fermo
            }
            codec?.release()
            extractor.release()
        }
    }

    /** Fa la media dei canali: da stereo (o più) a un solo canale. */
    private fun toMono(buffer: ByteBuffer, channels: Int, isFloat: Boolean): FloatArray {
        buffer.order(ByteOrder.nativeOrder())
        return if (isFloat) {
            val samples = buffer.asFloatBuffer()
            FloatArray(samples.remaining() / channels) { i ->
                var sum = 0f
                for (c in 0 until channels) sum += samples.get(i * channels + c)
                sum / channels
            }
        } else {
            val samples = buffer.asShortBuffer()
            FloatArray(samples.remaining() / channels) { i ->
                var sum = 0f
                for (c in 0 until channels) sum += samples.get(i * channels + c)
                sum / channels / 32768f
            }
        }
    }

    /** Porta l'audio a 16 kHz con un'interpolazione semplice (basta per la voce). */
    private fun resample(input: FloatArray, sampleRate: Int): FloatArray {
        if (sampleRate == TARGET_SAMPLE_RATE || input.isEmpty()) return input
        val ratio = sampleRate.toDouble() / TARGET_SAMPLE_RATE
        val outputSize = (input.size / ratio).toInt()
        return FloatArray(outputSize) { i ->
            val position = i * ratio
            val index = position.toInt()
            val fraction = (position - index).toFloat()
            val a = input[index]
            val b = if (index + 1 < input.size) input[index + 1] else a
            a + (b - a) * fraction
        }
    }
}