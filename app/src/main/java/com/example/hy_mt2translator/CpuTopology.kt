package com.example.hy_mt2translator

import java.io.File

/** Da quale informazione deriva la divisione tra core veloci e lenti. */
enum class TopologySource(val label: String) {
    CAPACITY("capacità CPU"),
    FREQUENCY("frequenze massime"),
    HOMOGENEOUS("core tutti uguali"),
    UNKNOWN("non determinabile")
}

/** Informazioni lette per un singolo core (null = informazione non leggibile). */
data class CoreInfo(
    val index: Int,
    val capacity: Long?,
    val maxFreqKhz: Long?
)

/**
 * Com'è fatta la CPU di questo telefono, per quanto si riesce a leggere.
 * [fastCount] e [slowCount] valgono solo se [hasDistinction] è vero: negli altri casi
 * (core tutti uguali, informazioni illeggibili) non si inventa nessuna distinzione.
 */
data class CpuTopology(
    val cores: List<CoreInfo>,
    val fastCount: Int,
    val slowCount: Int,
    val source: TopologySource,
    /** Vero se la divisione è una stima (frequenze) o ci sono tre o più gruppi di core. */
    val approximate: Boolean
) {
    val total: Int get() = cores.size

    /** Vero solo se esistono davvero due categorie di core. */
    val hasDistinction: Boolean get() = fastCount > 0 && slowCount > 0

    fun summary(): String = when {
        hasDistinction ->
            "$total core · $fastCount veloci + $slowCount lenti " +
                    "(da ${source.label}${if (approximate) ", stima" else ""})"

        source == TopologySource.HOMOGENEOUS ->
            "$total core · tutti uguali: nessuna distinzione veloci/lenti"

        else ->
            "$total core · classificazione veloci/lenti non determinabile (informazioni non leggibili)"
    }

    /** Dati grezzi di ogni core, da salvare insieme ai risultati. */
    fun details(): String = cores.joinToString("; ") {
        "cpu${it.index}: capacità=${it.capacity ?: "?"} freq=${it.maxFreqKhz ?: "?"}"
    }
}

/**
 * Legge dal sistema, per ogni core, la capacità relativa (cpu_capacity) e la frequenza massima
 * hardware (cpuinfo_max_freq). Non assume nessuna architettura: la divisione cade nel salto
 * relativo più grande tra i valori, e solo se il salto è significativo. Se le informazioni non si
 * possono leggere, lo dichiara invece di inventare una distinzione.
 */
object CpuTopologyDetector {
    // Salto minimo tra due gruppi di core perché siano considerati diversi
    private const val CAPACITY_GAP = 1.25
    private const val FREQUENCY_GAP = 1.12

    private data class Split(val fast: Int, val slow: Int, val clusters: Int)

    fun detect(): CpuTopology {
        val count = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val cores = (0 until count).map { i ->
            CoreInfo(
                index = i,
                capacity = readLong("/sys/devices/system/cpu/cpu$i/cpu_capacity"),
                maxFreqKhz = readLong("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
            )
        }

        classify(cores.map { it.capacity }, CAPACITY_GAP)?.let {
            return build(cores, it, TopologySource.CAPACITY)
        }
        classify(cores.map { it.maxFreqKhz }, FREQUENCY_GAP)?.let {
            return build(cores, it, TopologySource.FREQUENCY)
        }
        return CpuTopology(cores, 0, 0, TopologySource.UNKNOWN, approximate = true)
    }

    private fun build(cores: List<CoreInfo>, split: Split, source: TopologySource): CpuTopology {
        val homogeneous = split.slow == 0
        return CpuTopology(
            cores = cores,
            fastCount = split.fast,
            slowCount = split.slow,
            source = if (homogeneous) TopologySource.HOMOGENEOUS else source,
            approximate = source == TopologySource.FREQUENCY || split.clusters >= 3
        )
    }

    /** Divide i core in due gruppi; null se non tutti i valori sono leggibili. */
    private fun classify(values: List<Long?>, minGap: Double): Split? {
        if (values.any { it == null || it <= 0L }) return null
        val v = values.map { it!! }
        val distinct = v.distinct().sorted()
        if (distinct.size == 1) return Split(v.size, 0, 1)

        // La divisione cade nel salto relativo più grande tra due valori vicini
        var bestIndex = 0
        var bestRatio = 0.0
        for (i in 0 until distinct.size - 1) {
            val ratio = distinct[i + 1].toDouble() / distinct[i]
            if (ratio > bestRatio) {
                bestRatio = ratio
                bestIndex = i
            }
        }
        // Differenze troppo piccole: i core si considerano equivalenti
        if (bestRatio < minGap) return Split(v.size, 0, distinct.size)

        val threshold = distinct[bestIndex]
        val fast = v.count { it > threshold }
        return Split(fast, v.size - fast, distinct.size)
    }

    private fun readLong(path: String): Long? = try {
        File(path).readText().trim().toLongOrNull()
    } catch (_: Exception) {
        null
    }
}

/**
 * Una configurazione da provare: al motore arrivano [total] thread. [fast] e [slow] sono la
 * composizione *richiesta* (core veloci per primi): senza controllo dell'affinità non è garantita.
 */
data class ThreadConfig(val total: Int, val fast: Int, val slow: Int)

/** Genera le configurazioni da provare in funzione dell'hardware, senza duplicati. */
object ThreadConfigs {
    private const val MAX_LLM_ROWS = 8
    private const val MAX_ASR_ROWS = 5

    /** Per Hy-MT2: tutti i totali fino ai core veloci, poi qualche punto in più fino a tutti i core. */
    fun forLlm(t: CpuTopology): List<ThreadConfig> {
        val n = t.total
        val totals = if (t.hasDistinction) {
            (1..minOf(t.fastCount, 4)).toList() + listOf(t.fastCount + 1, t.fastCount + 2, n)
        } else {
            (1..minOf(n, 4)).toList() + listOf(n / 2, n)
        }
        return totals
            .filter { it in 1..n }
            .distinct()
            .sorted()
            .take(MAX_LLM_ROWS)
            .map { compose(t, it) }
    }

    /**
     * Per Qwen3-ASR: meno righe, perché ognuna richiede di ricaricare il modello.
     * Si salta il thread singolo, certamente più lento, a meno che il telefono abbia un solo core.
     */
    fun forAsr(t: CpuTopology): List<ThreadConfig> {
        val candidates = forLlm(t).filter { it.total >= 2 || t.total == 1 }
        return pickEvenly(candidates, MAX_ASR_ROWS)
    }

    /** Composizione richiesta per [total] thread: prima i core veloci, poi i lenti. */
    private fun compose(t: CpuTopology, total: Int): ThreadConfig =
        if (t.hasDistinction) {
            val fast = minOf(total, t.fastCount)
            ThreadConfig(total, fast, total - fast)
        } else {
            ThreadConfig(total, total, 0)
        }

    private fun <T> pickEvenly(list: List<T>, max: Int): List<T> {
        if (list.size <= max) return list
        return (0 until max).map { i -> list[(i * (list.size - 1)) / (max - 1)] }.distinct()
    }
}