package com.example.hy_mt2translator

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.hy_mt2translator.ui.theme.GlassIcons
import com.example.hy_mt2translator.ui.theme.GlassKind
import com.example.hy_mt2translator.ui.theme.LocalGlass
import com.example.hy_mt2translator.ui.theme.glass

// Misure della linguetta: con la larghezza uguale a metà altezza è una semisfera perfetta.
// Per renderla più grande o più piccola si cambia solo PANEL_HEIGHT (e TAB_WIDTH = metà).
private val PANEL_HEIGHT = 60.dp
private val TAB_WIDTH = 30.dp

/** Un pulsante tondo in vetro con solo l'icona; facoltativamente reagisce anche al tocco lungo. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    longClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .glass(CircleShape, GlassKind.Field)
            .clip(CircleShape)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = description,
                onLongClickLabel = longClickLabel,
                onLongClick = onLongClick,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * Linguetta semisferica sul bordo sinistro dello schermo. Toccandola (soltanto: niente trascinamento,
 * perché il bordo sinistro è anche il gesto «indietro» di Android) la freccia si gira e si allarga
 * un pannello con due pulsanti:
 * - Impostazioni;
 * - giorno/notte: un tocco passa all'altra modalità e ricorda la scelta, un tocco lungo torna
 *   all'automatico (tema del telefono). L'icona mostra la modalità attuale.
 */
@Composable
fun QuickPanel(
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onAutoTheme: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    // Il pannello resta aperto o chiuso anche se Android ricrea la schermata (rotazione)
    var open by rememberSaveable { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = tween(250),
        label = "freccia"
    )
    val shape = RoundedCornerShape(topEnd = PANEL_HEIGHT / 2, bottomEnd = PANEL_HEIGHT / 2)

    Row(
        modifier = modifier
            .height(PANEL_HEIGHT)
            .glass(shape, GlassKind.Pill)
            .clip(shape),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // La linguetta vera e propria
        Box(
            modifier = Modifier
                .width(TAB_WIDTH)
                .fillMaxHeight()
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (open) "Chiudi il pannello rapido" else "Apri il pannello rapido"
                ) { open = !open },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = GlassIcons.ChevronRight,
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(arrowRotation)
            )
        }

        AnimatedVisibility(
            visible = open,
            enter = fadeIn(tween(200)) + expandHorizontally(tween(250)),
            exit = fadeOut(tween(150)) + shrinkHorizontally(tween(200))
        ) {
            Row(
                modifier = Modifier.padding(end = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(28.dp)
                        .background(g.glassBorder)
                )
                Spacer(Modifier.width(8.dp))
                QuickIconButton(
                    icon = GlassIcons.Settings,
                    description = "Impostazioni",
                    onClick = {
                        open = false
                        onOpenSettings()
                    }
                )
                Spacer(Modifier.width(6.dp))
                QuickIconButton(
                    icon = if (isDark) GlassIcons.Moon else GlassIcons.Sun,
                    description = "Cambia tra modalità notte e giorno",
                    onClick = onToggleTheme,
                    longClickLabel = "Torna al tema del telefono",
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onAutoTheme()
                        Toast.makeText(context, "Tema automatico", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}