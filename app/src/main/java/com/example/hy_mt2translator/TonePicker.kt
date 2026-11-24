package com.example.hy_mt2translator

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hy_mt2translator.ui.theme.GlassIcons
import com.example.hy_mt2translator.ui.theme.GlassKind
import com.example.hy_mt2translator.ui.theme.GlassSheet
import com.example.hy_mt2translator.ui.theme.LocalGlass
import com.example.hy_mt2translator.ui.theme.SheetOption
import com.example.hy_mt2translator.ui.theme.glass

/**
 * Pulsante piccolo "Tono: <tono>" con l'icona colorata. Al tocco apre un pannello in vetro
 * con i toni, ciascuno con una breve descrizione; quello scelto ha la spunta.
 */
@Composable
fun TonePicker(
    selected: Tone,
    onSelected: (Tone) -> Unit,
    modifier: Modifier = Modifier
) {
    val g = LocalGlass.current
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)

    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .glass(shape, GlassKind.Field)
            .clip(shape)
            .clickable(role = Role.Button) { open = true }
            .padding(start = 10.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = GlassIcons.Tone,
            contentDescription = null,
            tint = g.accent,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = "Tono: ${selected.labelIt}",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Icon(
            imageVector = GlassIcons.ChevronDown,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
    }

    if (open) {
        GlassSheet(onDismissRequest = { open = false }) { pick ->
            Text(
                text = "Tono della traduzione",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            TONES.forEach { tone ->
                SheetOption(
                    title = tone.labelIt,
                    supporting = tone.description,
                    selected = tone.id == selected.id,
                    onClick = { pick { onSelected(tone) } }
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}