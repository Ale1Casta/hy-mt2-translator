package com.example.hy_mt2translator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hy_mt2translator.ui.theme.GlassBackButton
import com.example.hy_mt2translator.ui.theme.GlassIcons
import com.example.hy_mt2translator.ui.theme.GlassKind
import com.example.hy_mt2translator.ui.theme.LocalGlass
import com.example.hy_mt2translator.ui.theme.PrimaryButton
import com.example.hy_mt2translator.ui.theme.glass
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Una scheda di formato: pulsante tondo, nome e descrizione; quella scelta ha il bordo corallo. */
@Composable
private fun FormatCard(format: ExportFormat, selected: Boolean, onSelect: () -> Unit) {
    val g = LocalGlass.current
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) g.cardOnFill else g.cardOffFill, shape)
            .border(1.dp, if (selected) g.cardOnBorder else g.cardOffBorder, shape)
            .clip(shape)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = g.accent,
                unselectedColor = g.textMuted
            )
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = format.label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(text = format.description, fontSize = 13.sp, color = g.textMuted)
        }
    }
}

/** Una scelta semplice con pulsante tondo. */
@Composable
private fun OptionRow(label: String, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    val g = LocalGlass.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, enabled = enabled, onClick = onSelect, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
            colors = RadioButtonDefaults.colors(
                selectedColor = g.accent,
                unselectedColor = g.textMuted
            )
        )
        Text(
            text = label,
            fontSize = 16.sp,
            color = if (enabled) g.text else g.textMuted
        )
    }
}

/** Linea sottile tra una scelta e l'altra. */
@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LocalGlass.current.divider)
    )
}

/** Esportazione della chat: formato, contenuto e creazione del file da condividere. */
@Composable
fun ChatExportScreen(
    session: ChatSession,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var format by remember { mutableStateOf(ExportFormat.Pdf) }
    var includeOriginal by remember { mutableStateOf(true) }
    var includeSettings by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    val isCsv = format == ExportFormat.Csv
    val isEmpty = session.entries.isEmpty()

    Column(modifier = modifier.fillMaxSize()) {
        // Intestazione
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlassBackButton(contentDescription = "Torna alle impostazioni", onClick = onBack)
            Column {
                Text("Esporta chat", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = session.displayTitle,
                    fontSize = 12.sp,
                    color = g.textMuted
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Formato
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Formato",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = g.textMuted,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                ExportFormat.entries.forEach { option ->
                    FormatCard(
                        format = option,
                        selected = option == format,
                        onSelect = { format = option }
                    )
                }
            }

            // Contenuto
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(24.dp), GlassKind.Panel)
                    .padding(horizontal = 18.dp, vertical = 6.dp)
                    .selectableGroup()
            ) {
                Text(
                    text = "Cosa includere",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = g.textMuted,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
                OptionRow(
                    label = "Originale e traduzione",
                    selected = includeOriginal || isCsv,
                    enabled = !isCsv,
                    onSelect = { includeOriginal = true }
                )
                Divider()
                OptionRow(
                    label = "Solo le traduzioni",
                    selected = !includeOriginal && !isCsv,
                    enabled = !isCsv,
                    onSelect = { includeOriginal = false }
                )
                Divider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(
                            value = includeSettings && !isCsv,
                            enabled = !isCsv,
                            role = Role.Checkbox,
                            onValueChange = { includeSettings = it }
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Checkbox(
                        checked = includeSettings && !isCsv,
                        onCheckedChange = null,
                        enabled = !isCsv,
                        colors = CheckboxDefaults.colors(
                            checkedColor = g.accent,
                            checkmarkColor = g.primaryText,
                            uncheckedColor = g.textMuted
                        )
                    )
                    Text(
                        text = "Aggiungi istruzioni fisse e glossario",
                        fontSize = 16.sp,
                        color = if (isCsv) g.textMuted else g.text
                    )
                }
                if (isCsv) {
                    Text(
                        text = "Il CSV ha sempre due colonne (originale e traduzione) e non include le impostazioni.",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = g.textMuted,
                        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                    )
                }
            }
        }

        // Pulsante di esportazione
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isEmpty) {
                Text(
                    text = "La chat è vuota: non c'è ancora niente da esportare.",
                    fontSize = 13.sp,
                    color = g.textMuted
                )
            }
            if (error.isNotEmpty()) {
                Text(text = error, fontSize = 13.sp, color = g.danger)
            }
            PrimaryButton(
                enabled = !busy && !isEmpty,
                onClick = {
                    scope.launch {
                        busy = true
                        error = ""
                        try {
                            val options = ExportOptions(format, includeOriginal, includeSettings)
                            val uri = withContext(Dispatchers.IO) { exportChat(context, session, options) }
                            shareFile(context, uri, format.mime, session.displayTitle)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = "Non sono riuscito a creare il file: ${e.message}"
                        } finally {
                            busy = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(GlassIcons.Export, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    text = if (busy) "Preparo il file…" else "Esporta come ${format.shortName}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = "Il file viene creato sul telefono. Poi scegli dove salvarlo o con chi condividerlo.",
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = g.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}