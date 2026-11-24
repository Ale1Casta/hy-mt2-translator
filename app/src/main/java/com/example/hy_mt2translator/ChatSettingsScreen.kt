package com.example.hy_mt2translator

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hy_mt2translator.ui.theme.AccentOutlineButton
import com.example.hy_mt2translator.ui.theme.ChipButton
import com.example.hy_mt2translator.ui.theme.DangerButton
import com.example.hy_mt2translator.ui.theme.FieldButton
import com.example.hy_mt2translator.ui.theme.GlassBackButton
import com.example.hy_mt2translator.ui.theme.GlassCard
import com.example.hy_mt2translator.ui.theme.GlassChoiceChip
import com.example.hy_mt2translator.ui.theme.GlassIcons
import com.example.hy_mt2translator.ui.theme.GlassKind
import com.example.hy_mt2translator.ui.theme.GlassSheet
import com.example.hy_mt2translator.ui.theme.SheetOption
import com.example.hy_mt2translator.ui.theme.LocalGlass
import com.example.hy_mt2translator.ui.theme.glass

/** Titolo piccolo di una sezione. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = LocalGlass.current.textMuted
    )
}

/** Frase di spiegazione sotto un titolo o un campo. */
@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = LocalGlass.current.textMuted
    )
}

/** Il cestino tondo di una riga. */
@Composable
private fun RemoveButton(description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = GlassIcons.Trash,
            contentDescription = description,
            tint = LocalGlass.current.textMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** Una riga del glossario: termine, freccia e traduzione. */
@Composable
private fun GlossaryRow(entry: GlossaryEntry, onRemove: () -> Unit) {
    val g = LocalGlass.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .glass(RoundedCornerShape(16.dp), GlassKind.Field)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = entry.source, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Icon(
            imageVector = GlassIcons.ArrowRight,
            contentDescription = null,
            tint = g.textMuted,
            modifier = Modifier.size(18.dp)
        )
        Text(text = entry.target, fontSize = 16.sp, modifier = Modifier.weight(1f))
        RemoveButton("Rimuovi il termine ${entry.source}", onRemove)
    }
}

/** Una riga con un'istruzione salvata. */
@Composable
private fun InstructionRow(text: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .glass(RoundedCornerShape(16.dp), GlassKind.Field)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        RemoveButton("Rimuovi l'istruzione", onRemove)
    }
}

/** Scelta della durata della chat: pulsante a campo che apre un pannello dal basso con le opzioni. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpiryPicker(days: Int, onSelected: (Int) -> Unit) {
    val g = LocalGlass.current
    var open by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Elimina la chat dopo")
        FieldButton(text = expiryLabel(days), onClick = { open = true })
        HintText("Le chat più vecchie vengono eliminate da sole.")
    }

    if (open) {
        GlassSheet(onDismissRequest = { open = false }) { pick ->
            Text(
                text = "Elimina la chat dopo",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            EXPIRY_OPTIONS.forEach { option ->
                SheetOption(
                    title = expiryLabel(option),
                    selected = option == days,
                    onClick = { pick { onSelected(option) } }
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Impostazioni della chat: lingua, stile, glossario, contesto, istruzioni, durata,
 * esportazione ed eliminazione.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatSettingsScreen(
    session: ChatSession,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onDeleteChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val g = LocalGlass.current
    var showAddTerm by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        // Intestazione
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlassBackButton(contentDescription = "Torna alla chat", onClick = onBack)
            Text(
                text = "Impostazioni della chat",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Lingua e stile
            GlassCard(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Lingua di destinazione")
                    LanguagePicker(
                        selected = session.target,
                        onSelected = { session.targetName = it.nameEn },
                        compact = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Stile")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TONES.forEach { tone ->
                            GlassChoiceChip(
                                label = tone.labelIt,
                                selected = tone.id == session.toneId,
                                onClick = { session.toneId = tone.id }
                            )
                        }
                    }
                }
            }

            // Glossario
            GlassCard {
                SectionLabel("Glossario")
                Spacer(Modifier.height(4.dp))
                HintText("Il modello userà queste traduzioni ogni volta che trova il termine.")
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    session.glossary.toList().forEach { entry ->
                        GlossaryRow(entry = entry, onRemove = { session.glossary.remove(entry) })
                    }
                    ChipButton(
                        label = "Aggiungi termine",
                        icon = GlassIcons.Plus,
                        onClick = { showAddTerm = true }
                    )
                }
            }

            // Contesto, istruzioni e durata
            GlassCard(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Contesto")
                    BasicTextField(
                        value = session.context,
                        onValueChange = { session.context = it },
                        textStyle = TextStyle(color = g.text, fontSize = 16.sp, lineHeight = 23.sp),
                        cursorBrush = SolidColor(g.accent),
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .glass(RoundedCornerShape(16.dp), GlassKind.Field)
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .semantics { contentDescription = "Contesto" },
                        decorationBox = { innerTextField ->
                            Box {
                                if (session.context.isEmpty()) {
                                    Text(
                                        text = "Per esempio: cena di lavoro con colleghi giapponesi",
                                        fontSize = 16.sp,
                                        lineHeight = 23.sp,
                                        color = g.textMuted
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                    HintText("Aiuta il modello a scegliere le parole giuste.")
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Istruzioni")
                    if (session.instructions.isEmpty()) {
                        HintText("Nessuna. Nella chat, scorri in su sul pulsante di invio per scriverne una.")
                    }
                    session.instructions.toList().forEach { instruction ->
                        InstructionRow(
                            text = instruction,
                            onRemove = { session.instructions.remove(instruction) }
                        )
                    }
                }

                ExpiryPicker(
                    days = session.expiryDays,
                    onSelected = { session.expiryDays = it }
                )
            }

            // Esportazione ed eliminazione
            Column(
                modifier = Modifier.padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AccentOutlineButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                    Icon(GlassIcons.Export, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("Esporta chat", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                DangerButton(onClick = onDeleteChat, modifier = Modifier.fillMaxWidth()) {
                    Icon(GlassIcons.Trash, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("Elimina chat", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    // Finestra per aggiungere un termine al glossario
    if (showAddTerm) {
        var term by remember { mutableStateOf("") }
        var translation by remember { mutableStateOf("") }
        val fieldColors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = g.text,
            unfocusedTextColor = g.text,
            focusedBorderColor = g.accent,
            unfocusedBorderColor = g.textMuted.copy(alpha = 0.5f),
            focusedLabelColor = g.accent,
            unfocusedLabelColor = g.textMuted,
            cursorColor = g.accent
        )
        AlertDialog(
            onDismissRequest = { showAddTerm = false },
            containerColor = g.sheetBackground,
            titleContentColor = g.text,
            textContentColor = g.text,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Nuovo termine") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = term,
                        onValueChange = { term = it },
                        label = { Text("Termine (nella tua lingua)") },
                        singleLine = true,
                        colors = fieldColors
                    )
                    OutlinedTextField(
                        value = translation,
                        onValueChange = { translation = it },
                        label = { Text("Come va tradotto") },
                        singleLine = true,
                        colors = fieldColors
                    )
                }
            },
            confirmButton = {
                val canAdd = term.isNotBlank() && translation.isNotBlank()
                TextButton(
                    enabled = canAdd,
                    onClick = {
                        session.glossary.add(GlossaryEntry(term.trim(), translation.trim()))
                        showAddTerm = false
                    }
                ) {
                    Text("Aggiungi", color = if (canAdd) g.accent else g.textMuted)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTerm = false }) {
                    Text("Annulla", color = g.textMuted)
                }
            }
        )
    }
}