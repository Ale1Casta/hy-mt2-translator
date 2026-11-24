package com.example.hy_mt2translator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hy_mt2translator.ui.theme.AccentOutlineButton
import com.example.hy_mt2translator.ui.theme.GlassBackButton
import com.example.hy_mt2translator.ui.theme.GlassCard
import com.example.hy_mt2translator.ui.theme.GlassIcons
import com.example.hy_mt2translator.ui.theme.LocalGlass
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Colore per i thread richiesti sui core lenti (quelli sui core veloci usano g.accent)
private val SlowColor = Color(0xFF60A5FA)

/**
 * Una riga della tabella: [total] thread passati al motore; [fast] e [slow] sono la composizione
 * richiesta (non garantita); [first] e [second] sono i due risultati, già formattati.
 */
data class BenchmarkRow(
    val total: Int,
    val fast: Int,
    val slow: Int,
    val first: String,
    val second: String,
    /** Vero se è la scelta automatica per la prima colonna (mostra ★). */
    val firstBest: Boolean = false,
    /** Vero se è la scelta automatica per la seconda colonna (mostra ★). */
    val secondBest: Boolean = false
)

// Larghezze relative delle tre colonne: thread, primo risultato, secondo risultato
private const val COL_THREADS = 0.9f
private const val COL_RESULT = 1.2f

/** Intestazione della tabella. */
@Composable
private fun TableHeader(firstTitle: String, secondTitle: String) {
    val g = LocalGlass.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Thread",
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
            color = g.textMuted,
            modifier = Modifier
                .weight(COL_THREADS)
                .padding(start = 22.dp)
        )
        Text(
            text = firstTitle,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
            color = g.textMuted,
            modifier = Modifier.weight(COL_RESULT)
        )
        Text(
            text = secondTitle,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
            color = g.textMuted,
            modifier = Modifier.weight(COL_RESULT)
        )
    }
}

/**
 * I thread di una riga. Se il telefono ha davvero due categorie di core ([colored]) si scrive
 * "veloci+lenti" (per esempio "2+2"), con le due cifre colorate (corallo e azzurro): è la
 * composizione *richiesta*, spiegata dalla nota sotto la tabella. Altrimenti è solo il numero totale,
 * senza colori, perché non si inventa una distinzione.
 */
@Composable
private fun ThreadLabel(row: BenchmarkRow, colored: Boolean, chosen: Boolean) {
    val g = LocalGlass.current
    val plain = if (chosen) g.selText else g.text
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (colored) {
            Text(text = row.fast.toString(), fontSize = 14.sp, color = if (chosen) g.selText else g.accent)
            Text(text = "+", fontSize = 14.sp, color = if (chosen) g.selText else g.textMuted)
            Text(text = row.slow.toString(), fontSize = 14.sp, color = if (chosen) g.selText else SlowColor)
        } else {
            Text(text = row.total.toString(), fontSize = 14.sp, color = plain)
        }
    }
}

/** Una riga della tabella: quella scelta ha lo sfondo corallo e la spunta. */
@Composable
private fun TableRow(
    row: BenchmarkRow,
    chosen: Boolean,
    enabled: Boolean,
    colored: Boolean,
    onClick: () -> Unit
) {
    val g = LocalGlass.current
    val shape = RoundedCornerShape(14.dp)
    val textColor = when {
        chosen -> g.selText
        enabled -> g.text
        else -> g.textMuted
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .then(
                if (chosen) {
                    Modifier
                        .background(g.selFill, shape)
                        .border(1.dp, g.selBorder, shape)
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.weight(COL_THREADS),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (chosen) {
                Icon(
                    imageVector = GlassIcons.Check,
                    contentDescription = "Scelto",
                    tint = g.accent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
            } else {
                Spacer(Modifier.width(22.dp))
            }
            ThreadLabel(row, colored, chosen)
        }
        ResultCell(row.first, row.firstBest, chosen, textColor, Modifier.weight(COL_RESULT))
        ResultCell(row.second, row.secondBest, chosen, textColor, Modifier.weight(COL_RESULT))
    }
}

/** Un valore della tabella; se è la scelta automatica per la sua colonna, ha una stellina accanto. */
@Composable
private fun ResultCell(value: String, best: Boolean, chosen: Boolean, color: Color, modifier: Modifier) {
    val g = LocalGlass.current
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
            color = color
        )
        if (best) {
            Text(
                text = " ★",
                fontSize = 12.sp,
                color = if (chosen) g.selText else g.accent
            )
        }
    }
}

/** Pallino colorato + etichetta, per la legenda sotto la tabella. */
@Composable
private fun LegendDot(color: Color, label: String) {
    val g = LocalGlass.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
        Text(text = label, fontSize = 11.sp, color = g.textMuted)
    }
}

/**
 * Scheda di un modello con la sua tabella. Senza risultati mostra tre righe vuote;
 * con i risultati, quella scelta ([selectedTotal]) è evidenziata e le altre si possono toccare.
 * [notes] sono avvertenze mostrate sotto la tabella.
 */
@Composable
private fun BenchmarkTableCard(
    title: String,
    firstTitle: String,
    secondTitle: String,
    rows: List<BenchmarkRow>,
    selectedTotal: Int?,
    colored: Boolean,
    onSelect: (BenchmarkRow) -> Unit,
    summary: String,
    notes: List<String>
) {
    val g = LocalGlass.current
    val hasResults = rows.isNotEmpty()
    val shown = if (hasResults) rows else listOf(1, 2, 3).map { BenchmarkRow(it, it, 0, "–", "–") }

    GlassCard(padding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
        Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        TableHeader(firstTitle, secondTitle)
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            shown.forEach { row ->
                TableRow(
                    row = row,
                    chosen = hasResults && row.total == selectedTotal,
                    enabled = hasResults,
                    colored = colored && hasResults,
                    onClick = { onSelect(row) }
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(text = summary, fontSize = 13.sp, lineHeight = 18.sp, color = g.textMuted)
        if (hasResults && colored) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LegendDot(g.accent, "veloci (tentati)")
                LegendDot(SlowColor, "lenti (tentati)")
            }
        }
        notes.filter { it.isNotBlank() }.forEach { note ->
            Spacer(Modifier.height(6.dp))
            Text(text = note, fontSize = 11.sp, lineHeight = 15.sp, color = g.textMuted)
        }
    }
}

/**
 * Scheda «Benchmark»: informazioni sull'hardware, pulsante che esegue i due test uno dopo l'altro
 * (Hy-MT2 e poi Qwen3-ASR), e le due tabelle con i risultati salvati, separate.
 */
@Composable
private fun BenchmarkSection(context: android.content.Context, engine: com.arm.aichat.InferenceEngine?) {
    val g = LocalGlass.current
    val scope = rememberCoroutineScope()
    var benchRunning by remember { mutableStateOf(false) }
    var benchCurrent by remember { mutableIntStateOf(0) }
    var benchTotal by remember { mutableIntStateOf(0) }
    var benchDesc by remember { mutableStateOf("") }
    var runNote by remember { mutableStateOf(BenchmarkStore.lastNotes(context)) }
    val prerequisites = remember { BenchmarkEngine.clipStatus(context) }
    var hyResult by remember { mutableStateOf(BenchmarkStore.loadHyMt2(context)) }
    var asrResult by remember { mutableStateOf(BenchmarkStore.loadAsr(context)) }
    var selectedHy by remember { mutableIntStateOf(BenchmarkStore.chosenThreads(context)) }
    var selectedHyBatch by remember { mutableIntStateOf(BenchmarkStore.chosenBatchThreads(context)) }
    var selectedAsr by remember { mutableIntStateOf(BenchmarkStore.chosenAsrThreads(context)) }
    val liveTopology = remember { CpuTopologyDetector.detect() }

    val canBench = engine != null && !benchRunning

    // Durante il test lo schermo resta acceso: se si spegne, le misure non sono affidabili
    val view = LocalView.current
    DisposableEffect(benchRunning) {
        view.keepScreenOn = benchRunning
        onDispose { view.keepScreenOn = false }
    }

    fun launchBench() {
        val eng = engine ?: return
        benchRunning = true
        runNote = ""
        scope.launch {
            try {
                eng.state.first { it is com.arm.aichat.InferenceEngine.State.ModelReady }
                val outcome = BenchmarkEngine.runAll(context, eng) { cur, tot, desc ->
                    benchCurrent = cur; benchTotal = tot; benchDesc = desc
                }
                hyResult = BenchmarkStore.loadHyMt2(context)
                asrResult = BenchmarkStore.loadAsr(context)
                selectedHy = BenchmarkStore.chosenThreads(context)
                selectedHyBatch = BenchmarkStore.chosenBatchThreads(context)
                selectedAsr = BenchmarkStore.chosenAsrThreads(context)
                runNote = listOfNotNull(outcome.hyError, outcome.asrNote).joinToString("\n")
            } finally {
                benchRunning = false
            }
        }
    }

    // Se il test è stato fatto, le informazioni sull'hardware sono quelle salvate con i risultati
    val hardware = hyResult?.hardware ?: asrResult?.hardware
    val hwSummary = hardware?.summary?.takeIf { it.isNotBlank() } ?: liveTopology.summary()

    // Scheda con il pulsante, la data dell'ultimo test e l'hardware
    GlassCard(padding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
        Text(text = "Benchmark", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        if (hyResult == null && !benchRunning) {
            Text(
                text = "Nessun test eseguito su questo telefono.",
                fontSize = 13.sp, lineHeight = 18.sp, color = g.textMuted
            )
        } else if (hyResult != null) {
            val date = java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.ITALIAN)
                .format(java.util.Date(hyResult!!.timestamp))
            Text(text = "Ultimo test: $date", fontSize = 13.sp, color = g.textMuted)
        }
        Spacer(Modifier.height(2.dp))
        Text(text = hwSummary, fontSize = 12.sp, lineHeight = 16.sp, color = g.textMuted)
        if (benchRunning) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Test $benchCurrent di $benchTotal: $benchDesc",
                fontSize = 13.sp, color = g.accent
            )
            Text(
                text = "Lascia l'app aperta: lo schermo resta acceso fino alla fine (fino a circa 5 minuti).",
                fontSize = 11.sp, lineHeight = 15.sp, color = g.textMuted
            )
        } else {
            if (runNote.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(text = runNote, fontSize = 12.sp, lineHeight = 16.sp, color = g.textMuted)
            }
            if (prerequisites != null) {
                Spacer(Modifier.height(8.dp))
                Text(text = prerequisites, fontSize = 12.sp, lineHeight = 16.sp, color = g.textMuted)
            }
        }
        Spacer(Modifier.height(12.dp))
        AccentOutlineButton(
            onClick = ::launchBench,
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (canBench) 1f else 0.5f)
        ) {
            Icon(GlassIcons.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                if (benchRunning) "Test in corso…" else "Rilancia il benchmark",
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold
            )
        }
    }

    val starNote = "★ = scelta automatica per quella colonna: la più veloce, oppure una con meno thread " +
            "se la differenza è sotto il 5%."
    val tentative = "Configurazione tentata: i thread sono richiesti per categoria (veloci per primi), ma " +
            "l'assegnazione ai core non è verificata e Android può spostarli. Al motore arriva solo il numero totale."

    // Tabella Hy-MT2
    val hyColored = hyResult?.hardware?.hasDistinction == true
    val hyRows = hyResult?.rows ?: emptyList()
    val hyChosen = hyRows.firstOrNull { it.total == selectedHy }
    val hyChosenBatch = hyRows.firstOrNull { it.total == selectedHyBatch }
    fun composition(row: BenchRow?, total: Int): String =
        if (hyColored && row != null) "$total thread (${row.fast}+${row.slow})" else "$total thread"
    BenchmarkTableCard(
        title = "Hy-MT2 · traduzione",
        firstTitle = "Lettura\n(pp32, t/s)",
        secondTitle = "Scrittura\n(tg16, t/s)",
        rows = hyRows.mapIndexed { i, it ->
            BenchmarkRow(
                it.total, it.fast, it.slow,
                "%.1f".format(it.ppTokensPerSec), "%.1f".format(it.tgTokensPerSec),
                firstBest = i == hyResult?.bestPpIndex,
                secondBest = i == hyResult?.bestIndex
            )
        },
        selectedTotal = selectedHy,
        colored = hyColored,
        onSelect = { row ->
            // Scelta manuale: stessi thread per lettura e scrittura
            selectedHy = row.total
            selectedHyBatch = row.total
            engine?.applyThreads(row.total, row.total)
            BenchmarkStore.setChosenThreads(context, row.total, row.total)
        },
        summary = when {
            hyResult == null -> "Esegui il benchmark per trovare la combinazione migliore."
            selectedHy == selectedHyBatch ->
                "In uso adesso: ${composition(hyChosen, selectedHy)} per lettura e scrittura. " +
                        "Tocca un'altra riga per cambiare."

            else -> "In uso adesso: scrittura ${composition(hyChosen, selectedHy)}, lettura " +
                    "${composition(hyChosenBatch, selectedHyBatch)}. Tocca una riga per usarne una sola."
        },
        notes = listOf(
            if (hyResult != null) starNote else "",
            if (hyResult != null && hyColored) tentative else "",
            hyResult?.let { r ->
                val pp = r.rows.maxOfOrNull { it.ppSpreadPercent } ?: 0
                val tg = r.rows.maxOfOrNull { it.tgSpreadPercent } ?: 0
                if (pp > 0 || tg > 0) {
                    "Ogni valore è la mediana di 4 prove in due giri (il secondo in ordine " +
                            "inverso). Variabilità tra le prove: fino al $pp% in lettura e al $tg% in scrittura."
                } else {
                    ""
                }
            } ?: "",
            hyResult?.note ?: ""
        )
    )

    // Tabella Qwen3-ASR
    val asrColored = asrResult?.hardware?.hasDistinction == true
    val asrRows = asrResult?.rows ?: emptyList()
    val asrChosen = asrRows.firstOrNull { it.total == selectedAsr }
    BenchmarkTableCard(
        title = "Qwen3-ASR · trascrizione",
        firstTitle = "Latenza\n(secondi)",
        secondTitle = "RTF\n(più basso = meglio)",
        rows = asrRows.mapIndexed { i, it ->
            BenchmarkRow(
                it.total, it.fast, it.slow,
                "%.1f".format(it.latencySeconds), "%.2f".format(it.rtf),
                secondBest = i == asrResult?.bestIndex
            )
        },
        selectedTotal = selectedAsr,
        colored = asrColored,
        onSelect = { row ->
            selectedAsr = row.total
            BenchmarkStore.setChosenAsrThreads(context, row.total)
        },
        summary = when {
            asrResult == null ->
                "Esegui il benchmark (il modello audio e l'audio di prova devono essere presenti)."

            else -> {
                val inUse = if (asrColored && asrChosen != null) {
                    "In uso: $selectedAsr thread (richiesti: ${asrChosen.fast} veloci + " +
                            "${asrChosen.slow} lenti, non verificato)."
                } else {
                    "In uso: $selectedAsr thread."
                }
                "RTF < 1 = più veloce dell'audio. Audio di prova: " +
                        "${"%.1f".format(asrResult!!.audioSeconds)} s. $inUse"
            }
        },
        notes = listOf(
            if (asrResult != null) starNote else "",
            if (asrResult != null && asrColored) tentative else "",
            if (asrResult != null && asrResult!!.ramMb > 0) {
                "Memoria aggiuntiva con il modello caricato: circa ${asrResult!!.ramMb} MB (stima sul processo intero)."
            } else {
                ""
            },
            asrResult?.note ?: ""
        )
    )

    // Torna alle scelte automatiche dell'ultimo benchmark (attivo solo se qualcosa è stato cambiato a mano)
    val autoHy = BenchmarkStore.autoThreads(context)
    val autoHyBatch = BenchmarkStore.autoBatchThreads(context)
    val autoAsr = BenchmarkStore.autoAsrThreads(context)
    val canRestore = !benchRunning && (
            (hyResult != null && (selectedHy != autoHy || selectedHyBatch != autoHyBatch)) ||
                    (asrResult != null && selectedAsr != autoAsr)
            )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Ripristina il setup migliore",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (canRestore) g.accent else g.textMuted,
            modifier = Modifier
                .alpha(if (canRestore) 1f else 0.5f)
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .clickable(enabled = canRestore, role = Role.Button) {
                    if (hyResult != null) {
                        selectedHy = autoHy
                        selectedHyBatch = autoHyBatch
                        engine?.applyThreads(autoHy, autoHyBatch)
                        BenchmarkStore.setChosenThreads(context, autoHy, autoHyBatch)
                    }
                    if (asrResult != null) {
                        selectedAsr = autoAsr
                        BenchmarkStore.setChosenAsrThreads(context, autoAsr)
                    }
                }
                .padding(horizontal = 18.dp, vertical = 12.dp)
        )
    }
}

/**
 * Impostazioni: benchmark dei due modelli. Le configurazioni provate dipendono dai core reali di
 * questo telefono; il pulsante esegue Hy-MT2 e poi Qwen3-ASR (se il modello audio è installato).
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    engine: com.arm.aichat.InferenceEngine? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlassBackButton(contentDescription = "Torna a Traduci", onClick = onBack)
            Text(text = "Impostazioni", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            BenchmarkSection(context, engine)
        }
    }
}