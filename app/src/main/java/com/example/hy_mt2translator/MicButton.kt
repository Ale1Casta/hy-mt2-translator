package com.example.hy_mt2translator

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hy_mt2translator.ui.theme.GlassIcons
import com.example.hy_mt2translator.ui.theme.GlassKind
import com.example.hy_mt2translator.ui.theme.LocalGlass
import com.example.hy_mt2translator.ui.theme.glass
import kotlin.math.sin

/**
 * Piccola onda animata (7 barre, stile equalizzatore).
 */
@Composable
private fun WaveIndicator(modifier: Modifier = Modifier) {
    val g = LocalGlass.current
    val transition = rememberInfiniteTransition(label = "onda")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "fase"
    )
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        for (i in 0..6) {
            val h = (20 + 14 * sin((phase + i * 0.9f).toDouble())).toFloat().coerceIn(4f, 34f)
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(h.dp)
                    .background(g.accent, RoundedCornerShape(2.dp))
            )
        }
    }
}

/**
 * Pulsante microfono.
 * - **Primo tocco** → avvia la registrazione (vibrazione breve).
 * - **Secondo tocco** → ferma la registrazione e avvia la trascrizione.
 */
@Composable
fun MicButton(
    recording: Boolean,
    seconds: Int,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val g = LocalGlass.current
    val haptic = LocalHapticFeedback.current

    val containerColor = if (recording) g.accent else g.pillFill
    val contentColor   = if (recording) g.primaryText else g.text
    val border         = if (recording) g.accent.copy(alpha = 0.6f) else g.pillBorder

    Box(
        modifier = modifier
            .size(50.dp)
            .background(containerColor, CircleShape)
            .border(1.dp, border, CircleShape)
            .clip(CircleShape)
            .semantics {
                contentDescription =
                    if (recording) "Ferma la registrazione" else "Avvia la registrazione"
            }
            .pointerInput(recording) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    // Aspetta che il dito si alzi prima di agire
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        change.consume()
                        if (!change.pressed) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (recording) onStop() else onStart()
                            break
                        }
                    } while (true)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = GlassIcons.Mic,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * Barra compatta mostrata durante la registrazione.
 */
@Composable
fun RecordingBar(
    seconds: Int,
    onStop: () -> Unit,
    fromUri: Boolean = false,
    modifier: Modifier = Modifier
) {
    val g = LocalGlass.current
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .glass(shape, GlassKind.Focus),
        contentAlignment = Alignment.Center
    ) {
        if (fromUri) {
            // Solo testo centrato, senza cronometro
            Text(
                text = "Trascrizione in corso…",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = g.textMuted
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "%d:%02d".format(seconds / 60, seconds % 60),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = g.accent
                )
                WaveIndicator(modifier = Modifier.weight(1f))
                Text(text = "tocca di nuovo per fermare", fontSize = 12.sp, color = g.textMuted)
            }
        }
    }
}