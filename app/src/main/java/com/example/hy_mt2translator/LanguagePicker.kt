package com.example.hy_mt2translator

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hy_mt2translator.ui.theme.FieldButton
import com.example.hy_mt2translator.ui.theme.GlassIcons
import com.example.hy_mt2translator.ui.theme.GlassKind
import com.example.hy_mt2translator.ui.theme.GlassSheet
import com.example.hy_mt2translator.ui.theme.LocalGlass
import com.example.hy_mt2translator.ui.theme.SheetOption
import com.example.hy_mt2translator.ui.theme.glass

/**
 * Pulsante tondo in vetro con la piccola scritta "Traduci in" sopra la bandiera e il nome
 * della lingua scelta. Al tocco apre un pannello in vetro con tutte le lingue.
 * L'altezza la decide chi lo usa (di solito 54 dp). Con compact = true diventa un campo
 * semplice con la bandiera e il nome (usato nelle impostazioni).
 */
@Composable
fun LanguagePicker(
    selected: Language,
    onSelected: (Language) -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val g = LocalGlass.current
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)

    if (compact) {
        // Variante a campo (usata nelle impostazioni)
        FieldButton(
            text = "${selected.flag}  ${selected.nameIt}",
            onClick = { open = true },
            modifier = modifier
        )
    } else Row(
        modifier = modifier
            .glass(shape, GlassKind.Pill)
            .clip(shape)
            .clickable(role = Role.Button) { open = true }
            .padding(start = 18.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(text = "Traduci in", fontSize = 11.sp, color = g.textMuted, lineHeight = 13.sp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = selected.flag, fontSize = 15.sp, lineHeight = 18.sp)
                Text(
                    text = selected.nameIt,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
        }
        Icon(
            imageVector = GlassIcons.ChevronDown,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
    }

    if (open) {
        GlassSheet(onDismissRequest = { open = false }) { pick ->
            Text(
                text = "Traduci in",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(LANGUAGES) { language ->
                    SheetOption(
                        title = language.nameIt,
                        selected = language.nameEn == selected.nameEn,
                        onClick = { pick { onSelected(language) } },
                        leading = { Text(text = language.flag, fontSize = 22.sp) }
                    )
                }
            }
        }
    }
}