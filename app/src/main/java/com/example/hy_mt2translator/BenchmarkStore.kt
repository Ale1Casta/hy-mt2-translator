package com.example.hy_mt2translator

import android.content.Context
import android.content.SharedPreferences

/** Informazioni sull'hardware in cui è stato fatto il test, salvate con i risultati. */
data class HardwareInfo(
    val summary: String,
    /** Vero se esistono davvero due categorie di core (altrimenti niente colori). */
    val hasDistinction: Boolean,
    /** Dati grezzi di ogni core. */
    val details: String
)

/**
 * Una configurazione di Hy-MT2 provata. [total] sono i thread davvero passati al motore (applicati);
 * [fast] e [slow] sono la composizione richiesta, non garantita finché l'affinità non è verificata.
 */
data class BenchRow(
    val total: Int,
    val fast: Int,
    val slow: Int,
    /** Token al secondo in lettura (prompt processing, 32 token). */
    val ppTokensPerSec: Double,
    /** Token al secondo in scrittura (text generation, 16 token). */
    val tgTokensPerSec: Double,
    /** Variabilità della lettura: differenza tra la prova più lenta e la più veloce, in % della mediana. */
    val ppSpreadPercent: Int = 0,
    /** Variabilità della scrittura, come sopra. */
    val tgSpreadPercent: Int = 0
)

data class BenchmarkResult(
    val rows: List<BenchRow>,
    /** Indice della riga scelta per la scrittura (la più veloce, o una più semplice quasi pari). */
    val bestIndex: Int,
    /** Data e ora della misura (epoch ms). */
    val timestamp: Long,
    /** Thread per la scrittura scelti in automatico (n_threads di llama.cpp). */
    val chosenThreads: Int,
    val hardware: HardwareInfo,
    /** Indice della riga scelta per la lettura del testo. */
    val bestPpIndex: Int = bestIndex,
    /** Thread per la lettura scelti in automatico (n_threads_batch di llama.cpp). */
    val chosenBatchThreads: Int = chosenThreads,
    /** Avvertenze sul test (riscaldamento, limite di tempo), o null. */
    val note: String? = null
)

/** Una configurazione di Qwen3-ASR provata, misurata su un audio di prova fisso con parlato. */
data class AsrBenchRow(
    val total: Int,
    val fast: Int,
    val slow: Int,
    /** Tempo mediano di trascrizione (rilevamento del parlato + riconoscimento), senza il caricamento. */
    val latencySeconds: Double,
    /** Tempo di elaborazione diviso la durata dell'audio: più basso è meglio. */
    val rtf: Double,
    /** Differenza tra la prova più lenta e la più veloce, in percentuale della mediana. */
    val spreadPercent: Int
)

data class AsrBenchmarkResult(
    val rows: List<AsrBenchRow>,
    val bestIndex: Int,
    val timestamp: Long,
    val chosenThreads: Int,
    val audioSeconds: Double,
    /** Memoria aggiuntiva del processo con il modello caricato, in MB (stima). */
    val ramMb: Long,
    /** Avvertenza sul test (per esempio interrotto per il limite di tempo), o null. */
    val note: String?,
    val hardware: HardwareInfo
)

/**
 * Salva e carica i risultati dei due benchmark, separatamente.
 * Nessun dato sensibile: solo numeri di prestazioni, date e informazioni sul processore.
 * Il file di preferenze è nuovo («v2»): i dati del formato precedente non sono più validi
 * (quelli di Qwen3-ASR misuravano il caricamento del modello, non la trascrizione).
 */
object BenchmarkStore {
    private const val PREFS = "benchmark_v2"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun putHardware(e: SharedPreferences.Editor, hw: HardwareInfo) {
        e.putString("hw_summary", hw.summary)
        e.putBoolean("hw_distinct", hw.hasDistinction)
        e.putString("hw_details", hw.details)
        e.remove("hw_affinity")   // campo dei test precedenti, non più usato
    }

    private fun readHardware(p: SharedPreferences) = HardwareInfo(
        summary = p.getString("hw_summary", "") ?: "",
        hasDistinction = p.getBoolean("hw_distinct", false),
        details = p.getString("hw_details", "") ?: ""
    )

    // ===== Motivi dell'ultimo test non riuscito =====

    /** Salva i messaggi dell'ultimo benchmark (null = nessun problema), per mostrarli anche dopo. */
    fun saveLastNotes(context: Context, hyError: String?, asrNote: String?) {
        val e = prefs(context).edit()
        if (hyError != null) e.putString("last_hy_error", hyError) else e.remove("last_hy_error")
        if (asrNote != null) e.putString("last_asr_note", asrNote) else e.remove("last_asr_note")
        e.apply()
    }

    /** Messaggi dell'ultimo benchmark, uno per riga; stringa vuota se non c'è nulla da segnalare. */
    fun lastNotes(context: Context): String {
        val p = prefs(context)
        return listOfNotNull(p.getString("last_hy_error", null), p.getString("last_asr_note", null))
            .joinToString("\n")
    }

    // ===== Hy-MT2 =====

    fun saveHyMt2(context: Context, result: BenchmarkResult) {
        val e = prefs(context).edit()
        e.putString(
            "hy_rows",
            result.rows.joinToString("|") {
                "${it.total},${it.fast},${it.slow},${it.ppTokensPerSec},${it.tgTokensPerSec}," +
                        "${it.ppSpreadPercent},${it.tgSpreadPercent}"
            }
        )
        e.putInt("hy_best", result.bestIndex)
        e.putInt("hy_best_pp", result.bestPpIndex)
        e.putLong("hy_ts", result.timestamp)
        // In uso e automatici: dopo un nuovo test coincidono
        e.putInt("hy_threads", result.chosenThreads)
        e.putInt("hy_threads_batch", result.chosenBatchThreads)
        e.putInt("hy_auto_threads", result.chosenThreads)
        e.putInt("hy_auto_threads_batch", result.chosenBatchThreads)
        if (result.note != null) e.putString("hy_note", result.note) else e.remove("hy_note")
        putHardware(e, result.hardware)
        e.apply()
    }

    fun loadHyMt2(context: Context): BenchmarkResult? {
        val p = prefs(context)
        val csv = p.getString("hy_rows", null) ?: return null
        return try {
            val rows = csv.split("|").map { row ->
                val f = row.split(",")
                BenchRow(
                    f[0].toInt(), f[1].toInt(), f[2].toInt(), f[3].toDouble(), f[4].toDouble(),
                    // I test fatti prima della Fase 2 non hanno la variabilità
                    f.getOrNull(5)?.toIntOrNull() ?: 0,
                    f.getOrNull(6)?.toIntOrNull() ?: 0
                )
            }
            val best = p.getInt("hy_best", 0)
            BenchmarkResult(
                rows = rows,
                bestIndex = best,
                timestamp = p.getLong("hy_ts", 0L),
                chosenThreads = autoThreads(context),
                hardware = readHardware(p),
                bestPpIndex = p.getInt("hy_best_pp", best),
                chosenBatchThreads = autoBatchThreads(context),
                note = p.getString("hy_note", null)
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Thread in uso per la scrittura di Hy-MT2, o 2 (misura di Ale) se il benchmark non è ancora stato eseguito. */
    fun chosenThreads(context: Context): Int = prefs(context).getInt("hy_threads", 2)

    /** Thread in uso per la lettura del testo; se mai scelti, gli stessi della scrittura. */
    fun chosenBatchThreads(context: Context): Int =
        prefs(context).getInt("hy_threads_batch", chosenThreads(context))

    /** Scelta manuale (tocco su una riga): stessi thread per scrittura e lettura. */
    fun setChosenThreads(context: Context, total: Int, batch: Int = total) {
        prefs(context).edit().putInt("hy_threads", total).putInt("hy_threads_batch", batch).apply()
    }

    /** Valori scelti dall'ultimo benchmark (per «Ripristina il setup migliore»). */
    fun autoThreads(context: Context): Int {
        val p = prefs(context)
        return p.getInt("hy_auto_threads", p.getInt("hy_threads", 2))
    }

    fun autoBatchThreads(context: Context): Int =
        prefs(context).getInt("hy_auto_threads_batch", autoThreads(context))

    fun hasBenchmark(context: Context): Boolean = prefs(context).contains("hy_rows")

    // ===== Qwen3-ASR =====

    fun saveAsr(context: Context, result: AsrBenchmarkResult) {
        val e = prefs(context).edit()
        e.putString(
            "asr_rows",
            result.rows.joinToString("|") {
                "${it.total},${it.fast},${it.slow},${it.latencySeconds},${it.rtf},${it.spreadPercent}"
            }
        )
        e.putInt("asr_best", result.bestIndex)
        e.putLong("asr_ts", result.timestamp)
        e.putInt("asr_threads", result.chosenThreads)
        e.putInt("asr_auto_threads", result.chosenThreads)
        e.putString("asr_audio", result.audioSeconds.toString())
        e.putLong("asr_ram", result.ramMb)
        if (result.note != null) e.putString("asr_note", result.note) else e.remove("asr_note")
        putHardware(e, result.hardware)
        e.apply()
    }

    fun loadAsr(context: Context): AsrBenchmarkResult? {
        val p = prefs(context)
        val csv = p.getString("asr_rows", null) ?: return null
        return try {
            val rows = csv.split("|").map { row ->
                val f = row.split(",")
                AsrBenchRow(
                    f[0].toInt(), f[1].toInt(), f[2].toInt(),
                    f[3].toDouble(), f[4].toDouble(), f[5].toInt()
                )
            }
            AsrBenchmarkResult(
                rows = rows,
                bestIndex = p.getInt("asr_best", 0),
                timestamp = p.getLong("asr_ts", 0L),
                chosenThreads = autoAsrThreads(context),
                audioSeconds = p.getString("asr_audio", "0")?.toDoubleOrNull() ?: 0.0,
                ramMb = p.getLong("asr_ram", 0L),
                note = p.getString("asr_note", null),
                hardware = readHardware(p)
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Thread scelti per Qwen3-ASR, o 3 (valore dell'esempio ufficiale) se non ancora misurato. */
    fun chosenAsrThreads(context: Context): Int = prefs(context).getInt("asr_threads", 3)

    fun setChosenAsrThreads(context: Context, total: Int) {
        prefs(context).edit().putInt("asr_threads", total).apply()
    }

    /** Thread scelti per Qwen3-ASR dall'ultimo benchmark. */
    fun autoAsrThreads(context: Context): Int {
        val p = prefs(context)
        return p.getInt("asr_auto_threads", p.getInt("asr_threads", 3))
    }
}