package com.example.hy_mt2translator.ui.theme

import android.graphics.BlurMaskFilter
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * I colori del mockup "Glass", in due versioni (notte e giorno).
 * I nomi seguono quelli del mockup: c1 = testo, c2 = testo attenuato, ecc.
 */
@Immutable
data class GlassTokens(
    val isDark: Boolean,
    val text: Color,
    val textMuted: Color,
    val accent: Color,
    val accentText: Color,
    val tabOn: Color,
    val warn: Color,
    val danger: Color,
    val ok: Color,
    val divider: Color,
    val background: Color,
    val orb1: Color,
    val orb2: Color,
    val orb3: Color,
    // Superfici in vetro
    val glassFill: Color,
    val glassBorder: Color,
    val glassHighlight: Color,
    val pillFill: Color,
    val pillBorder: Color,
    val fieldFill: Color,
    val fieldBorder: Color,
    val focusFill: Color,
    val focusBorder: Color,
    // Elementi di selezione
    val selFill: Color,
    val selBorder: Color,
    val selText: Color,
    val indFill: Color,
    val indBorder: Color,
    val chipFill: Color,
    val chipBorder: Color,
    val chipText: Color,
    // Pulsante principale
    val primaryFill: Color,
    val primaryText: Color,
    val primaryGlow: Color,
    // Chat
    val meFill: Color,
    val meBorder: Color,
    val meText: Color,
    val themFill: Color,
    val themBorder: Color,
    val noteFill: Color,
    val noteBorder: Color,
    val dashedFill: Color,
    val dashedBorder: Color,
    val trackFill: Color,
    val barFill: Color,
    // Schede di esportazione
    val cardOnFill: Color,
    val cardOnBorder: Color,
    val cardOffFill: Color,
    val cardOffBorder: Color,
    val warnBorder: Color,
    val dangerFill: Color,
    val outlineAccentFill: Color,
    val outlineAccentBorder: Color,
    val outlineAccentText: Color,
    // Pannelli e finestre (opachi: sotto non deve trasparire niente)
    val sheetBackground: Color,
    val sheetFill: Color,
    val scrim: Color,
    // Ombra sotto il vetro
    val shadow: Color
)

private val White = Color.White

val NightTokens = GlassTokens(
    isDark = true,
    text = Color(0xFFE8F1F8),
    textMuted = Color(0xFF9FB0C2),
    accent = Color(0xFFFF9F7A),
    accentText = Color(0xFFFFB89A),
    tabOn = Color(0xFFFFD2BF),
    warn = Color(0xFFFFD27A),
    danger = Color(0xFFFFB4AB),
    ok = Color(0xFF5EE0B5),
    divider = White.copy(alpha = 0.07f),
    background = Color(0xFF070B12),
    orb1 = Color(0xFFE8603C).copy(alpha = 0.36f),
    orb2 = Color(0xFFF2A541).copy(alpha = 0.22f),
    orb3 = Color(0xFFC2416B).copy(alpha = 0.26f),
    glassFill = White.copy(alpha = 0.07f),
    glassBorder = White.copy(alpha = 0.12f),
    glassHighlight = White.copy(alpha = 0.26f),
    pillFill = White.copy(alpha = 0.06f),
    pillBorder = White.copy(alpha = 0.13f),
    fieldFill = White.copy(alpha = 0.05f),
    fieldBorder = White.copy(alpha = 0.11f),
    focusFill = White.copy(alpha = 0.07f),
    focusBorder = Color(0xFFFF9F7A).copy(alpha = 0.45f),
    selFill = Color(0xFFFF9F7A).copy(alpha = 0.15f),
    selBorder = Color(0xFFFF9F7A).copy(alpha = 0.42f),
    selText = Color(0xFFFFD2BF),
    indFill = Color(0xFFFF9F7A).copy(alpha = 0.16f),
    indBorder = Color(0xFFFF9F7A).copy(alpha = 0.40f),
    chipFill = Color(0xFFFF9F7A).copy(alpha = 0.13f),
    chipBorder = Color(0xFFFF9F7A).copy(alpha = 0.25f),
    chipText = Color(0xFFFFB89A),
    primaryFill = Color(0xFFFF9F7A),
    primaryText = Color(0xFF3A1206),
    primaryGlow = Color(0xFFFF7850),
    meFill = Color(0xFFFF6E3C).copy(alpha = 0.34f),
    meBorder = Color(0xFFFFAF87).copy(alpha = 0.55f),
    meText = Color.White,
    themFill = White.copy(alpha = 0.07f),
    themBorder = White.copy(alpha = 0.12f),
    noteFill = White.copy(alpha = 0.05f),
    noteBorder = White.copy(alpha = 0.08f),
    dashedFill = White.copy(alpha = 0.04f),
    dashedBorder = Color(0xFFFF9F7A).copy(alpha = 0.6f),
    trackFill = White.copy(alpha = 0.08f),
    barFill = Color(0xFFFF9F7A),
    cardOnFill = Color(0xFFFF9F7A).copy(alpha = 0.14f),
    cardOnBorder = Color(0xFFFF9F7A).copy(alpha = 0.45f),
    cardOffFill = White.copy(alpha = 0.05f),
    cardOffBorder = White.copy(alpha = 0.11f),
    warnBorder = Color(0xFFFFD27A).copy(alpha = 0.35f),
    dangerFill = Color(0xFFDC3545),
    outlineAccentFill = Color(0xFFFF9F7A).copy(alpha = 0.10f),
    outlineAccentBorder = Color(0xFFFF9F7A).copy(alpha = 0.45f),
    outlineAccentText = Color(0xFFFFD2BF),
    sheetBackground = Color(0xFF0F1622),
    sheetFill = Color(0xFF0C1320).copy(alpha = 0.90f),
    scrim = Color.Black.copy(alpha = 0.50f),
    shadow = Color.Black.copy(alpha = 0.65f)
)

val DayTokens = GlassTokens(
    isDark = false,
    text = Color(0xFF0B1B2B),
    textMuted = Color(0xFF4A5D70),
    accent = Color(0xFFFF7A4D),
    accentText = Color(0xFFC2410C),
    tabOn = Color(0xFF2E0E04),
    warn = Color(0xFFB45309),
    danger = Color(0xFFB42318),
    ok = Color(0xFF0E9F6E),
    divider = Color(0xFF0F2D50).copy(alpha = 0.08f),
    background = Color(0xFFDDE9F6),
    orb1 = Color(0xFFFF6A33).copy(alpha = 0.68f),
    orb2 = Color(0xFFFFAA33).copy(alpha = 0.68f),
    orb3 = Color(0xFFF25C8C).copy(alpha = 0.50f),
    glassFill = White.copy(alpha = 0.55f),
    glassBorder = White.copy(alpha = 0.85f),
    glassHighlight = White,
    pillFill = White.copy(alpha = 0.60f),
    pillBorder = White.copy(alpha = 0.90f),
    fieldFill = White.copy(alpha = 0.65f),
    fieldBorder = Color(0xFF0F2D50).copy(alpha = 0.10f),
    focusFill = White.copy(alpha = 0.60f),
    focusBorder = Color(0xFFFF7A4D).copy(alpha = 0.45f),
    selFill = Color(0xFFFF7A4D),
    selBorder = Color(0xFFFF7A4D),
    selText = Color(0xFF2E0E04),
    indFill = Color(0xFFFF7A4D),
    indBorder = Color(0xFFFF7A4D),
    chipFill = Color(0xFFFF7A4D),
    chipBorder = Color(0xFFFF7A4D),
    chipText = Color(0xFF2E0E04),
    primaryFill = Color(0xFFFF7A4D),
    primaryText = Color(0xFF2E0E04),
    primaryGlow = Color(0xFFFF7A4D),
    meFill = Color(0xFFFF7A4D).copy(alpha = 0.88f),
    meBorder = White.copy(alpha = 0.35f),
    meText = Color(0xFF2E0E04),
    themFill = White.copy(alpha = 0.65f),
    themBorder = White.copy(alpha = 0.90f),
    noteFill = White.copy(alpha = 0.50f),
    noteBorder = White.copy(alpha = 0.80f),
    dashedFill = White.copy(alpha = 0.40f),
    dashedBorder = Color(0xFFFF7A4D).copy(alpha = 0.5f),
    trackFill = Color(0xFF0F2D50).copy(alpha = 0.08f),
    barFill = Color(0xFFFF7A4D),
    cardOnFill = White.copy(alpha = 0.75f),
    cardOnBorder = Color(0xFFFF7A4D).copy(alpha = 0.45f),
    cardOffFill = White.copy(alpha = 0.45f),
    cardOffBorder = White.copy(alpha = 0.85f),
    warnBorder = Color(0xFFB45309).copy(alpha = 0.30f),
    dangerFill = Color(0xFFD92D20),
    outlineAccentFill = White.copy(alpha = 0.55f),
    outlineAccentBorder = Color(0xFFFF7A4D).copy(alpha = 0.35f),
    outlineAccentText = Color(0xFFC2410C),
    sheetBackground = Color(0xFFF4F8FD),
    sheetFill = Color(0xFFF4F8FD).copy(alpha = 0.88f),
    scrim = Color(0xFF0B1B2B).copy(alpha = 0.30f),
    shadow = Color(0xFF143C6E).copy(alpha = 0.30f)
)

val LocalGlass = staticCompositionLocalOf { NightTokens }

private fun Outline.toComposePath(): Path = Path().apply {
    when (val outline = this@toComposePath) {
        is Outline.Rectangle -> addRect(outline.rect)
        is Outline.Rounded -> addRoundRect(outline.roundRect)
        is Outline.Generic -> addPath(outline.path)
    }
}

/**
 * Ombra morbida sotto una superficie. Viene disegnata solo FUORI dalla forma: dentro il vetro,
 * che è semitrasparente, non deve trasparire.
 * I valori seguono il mockup: [blur] sfocatura, [offsetY] spostamento in basso,
 * [inset] di quanto l'ombra è più piccola della forma.
 */
private fun Modifier.softShadow(
    shape: Shape,
    color: Color,
    blur: Dp,
    offsetY: Dp,
    inset: Dp
): Modifier = drawBehind {
    val insetPx = inset.toPx()
    val shadowSize = Size(
        (size.width - 2 * insetPx).coerceAtLeast(1f),
        (size.height - 2 * insetPx).coerceAtLeast(1f)
    )
    val shadowPath = shape.createOutline(shadowSize, layoutDirection, this).toComposePath()
    val cutout = shape.createOutline(size, layoutDirection, this).toComposePath()
    val argb = color.toArgb()
    val blurPx = blur.toPx()
    val dy = offsetY.toPx()
    clipPath(cutout, clipOp = ClipOp.Difference) {
        drawIntoCanvas { canvas ->
            val paint = Paint().asFrameworkPaint()
            paint.isAntiAlias = true
            paint.color = argb
            paint.maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
            val native = canvas.nativeCanvas
            native.save()
            native.translate(insetPx, insetPx + dy)
            native.drawPath(shadowPath.asAndroidPath(), paint)
            native.restore()
        }
    }
}

/** Le varianti di vetro: pannello, pulsante tondo, campo, riquadro con il bordo colorato. */
enum class GlassKind { Panel, Pill, Field, Focus }

/**
 * Superficie in vetro: riempimento semitrasparente e bordo con il bordo alto più luminoso.
 * Il mockup sfoca anche ciò che sta dietro il vetro; qui dietro ci sono solo le luci sfocate
 * dello sfondo, quindi basta la trasparenza e l'effetto è quasi identico.
 */
@Composable
fun Modifier.glass(shape: Shape, kind: GlassKind = GlassKind.Panel): Modifier {
    val g = LocalGlass.current
    val fill: Color
    val border: Color
    val highlight: Color
    when (kind) {
        GlassKind.Panel -> {
            fill = g.glassFill; border = g.glassBorder; highlight = g.glassHighlight
        }
        GlassKind.Pill -> {
            fill = g.pillFill; border = g.pillBorder; highlight = g.glassHighlight
        }
        GlassKind.Field -> {
            fill = g.fieldFill; border = g.fieldBorder; highlight = g.fieldBorder
        }
        GlassKind.Focus -> {
            fill = g.focusFill; border = g.focusBorder; highlight = g.focusBorder
        }
    }
    val shadowed = when (kind) {
        GlassKind.Panel, GlassKind.Focus -> softShadow(shape, g.shadow, 22.dp, 18.dp, 16.dp)
        GlassKind.Pill -> softShadow(shape, g.shadow, 10.dp, 8.dp, 8.dp)
        GlassKind.Field -> this
    }
    return shadowed
        .background(fill, shape)
        .border(1.dp, Brush.verticalGradient(listOf(highlight, border, border)), shape)
}

private fun DrawScope.orb(color: Color, center: Offset, radius: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color, color.copy(alpha = color.alpha * 0.55f), Color.Transparent),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}

/** Sfondo dell'app: colore di base e tre luci sfocate. Fornisce anche colore e stile predefiniti del testo. */
@Composable
fun GlassBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val g = LocalGlass.current
    Box(
        modifier = modifier
            .background(g.background)
            .drawBehind {
                orb(g.orb1, Offset(size.width * 0.15f, size.height * 0.10f), size.width * 0.85f)
                orb(g.orb2, Offset(size.width * 0.95f, size.height * 0.66f), size.width * 0.80f)
                orb(g.orb3, Offset(size.width * 0.38f, size.height * 0.97f), size.width * 0.65f)
            }
    ) {
        CompositionLocalProvider(LocalContentColor provides g.text) {
            ProvideTextStyle(MaterialTheme.typography.bodyLarge) {
                content()
            }
        }
    }
}

/** Pulsante tondo in vetro (come "Copia" e "Condividi" nel mockup). */
@Composable
fun GlassPillButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val g = LocalGlass.current
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .height(height)
            .glass(shape, GlassKind.Pill)
            .clip(shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalContentColor provides g.text) {
            content()
        }
    }
}

/**
 * Pulsante principale pieno (corallo), con un leggero alone. Con height = Dp.Unspecified l'altezza la decide chi lo usa.
 * Con [stop] diventa il tasto «Ferma»: vetro con onde rosse liquide negli angoli, bordo e alone rossi
 * e un quadratino prima del testo. Nel tema notte il vetro è scuro e testo e quadratino bianchi;
 * nel tema giorno il vetro è chiaro e testo e quadratino scuri.
 */
@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 54.dp,
    enabled: Boolean = true,
    stop: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    val g = LocalGlass.current
    val shape = RoundedCornerShape(50)
    val stopMode = stop && enabled
    val stopLight = stopMode && !g.isDark
    val fill = when {
        stopLight -> StopGlassLight
        stopMode -> StopGlass
        enabled -> g.primaryFill
        else -> g.primaryFill.copy(alpha = 0.35f)
    }
    val textColor = when {
        stopLight -> StopInkLight
        stopMode -> Color.White
        enabled -> g.primaryText
        else -> g.primaryText.copy(alpha = 0.6f)
    }
    val glow = if (stopMode) StopRed else g.primaryGlow
    Row(
        modifier = modifier
            .then(if (height == Dp.Unspecified) Modifier else Modifier.height(height))
            .shadow(
                elevation = if (stopMode) 16.dp else if (enabled) 10.dp else 0.dp,
                shape = shape,
                ambientColor = glow,
                spotColor = glow
            )
            // Il ritaglio viene prima dei disegni, così le onde seguono gli angoli arrotondati
            .clip(shape)
            .background(fill, shape)
            .then(if (stopMode) Modifier.drawBehind { drawLiquidStop(light = stopLight) } else Modifier)
            // Bordino chiaro, più luminoso in alto (rosato in modalità «Ferma»)
            .border(
                1.dp,
                Brush.verticalGradient(
                    if (stopLight) {
                        listOf(Color.White.copy(alpha = 0.95f), StopRed.copy(alpha = 0.65f))
                    } else if (stopMode) {
                        listOf(Color(0xFFFFC2B8).copy(alpha = 0.85f), StopRed.copy(alpha = 0.75f))
                    } else {
                        listOf(
                            Color.White.copy(alpha = if (enabled) 0.60f else 0.25f),
                            Color.White.copy(alpha = if (enabled) 0.20f else 0.10f)
                        )
                    }
                ),
                shape
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            // In modalità «Ferma» margini più stretti: il quadratino in più deve stare anche nel
            // pulsante orizzontale, largo 110 dp, senza mandare a capo il testo
            .padding(horizontal = if (stopMode) 10.dp else 20.dp),
        horizontalArrangement = Arrangement.spacedBy(
            if (stopMode) 7.dp else 8.dp,
            Alignment.CenterHorizontally
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalContentColor provides textColor) {
            if (stopMode) {
                // Simbolo di stop: quadratino bianco con angoli arrotondati
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(
                            if (stopLight) StopRedDeep else Color.White.copy(alpha = 0.95f),
                            RoundedCornerShape(4.dp)
                        )
                )
            }
            content()
        }
    }
}

// Colori del tasto «Ferma»: rosso acceso per onde, bordo e alone; vetro quasi nero al centro
private val StopRed = Color(0xFFFF4A36)
private val StopRedDeep = Color(0xFFC62A1C)
private val StopGlass = Color(0xFF1A1315).copy(alpha = 0.92f)

// Versione per il tema giorno: vetro chiaro leggermente rosato e inchiostro scuro per il testo
private val StopGlassLight = Color(0xFFFFF5F3).copy(alpha = 0.92f)
private val StopInkLight = Color(0xFF2B1416)

/**
 * Onde rosse negli angoli e due bollicine. Le misure partono dagli angoli e sono in proporzione
 * all'altezza del pulsante largo, così restano uguali sia nel pulsante largo (verticale) sia in
 * quello stretto e alto (orizzontale).
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLiquidStop(light: Boolean = false) {
    val w = size.width
    val h = size.height
    // Pulsante largo: u è circa l'altezza. Pulsante alto e stretto: u cresce con la larghezza,
    // altrimenti le onde finirebbero nascoste dagli angoli molto arrotondati
    val u = minOf(h, maxOf(w / 3.2f, w * 0.6f))
    fun lx(x: Float) = x * u            // distanza dal bordo sinistro
    fun rx(x: Float) = w - x * u        // distanza dal bordo destro
    fun ty(y: Float) = y * u            // distanza dal bordo in alto
    fun by(y: Float) = h - (1f - y) * u // vicino al bordo in basso (y = 1 è il bordo)

    val liquid = Brush.linearGradient(
        colors = listOf(StopRed.copy(alpha = 0.90f), StopRedDeep.copy(alpha = 0.80f)),
        start = Offset(0f, 0f),
        end = Offset(w, h)
    )
    // Sul vetro chiaro il bordino delle onde è bianco, per staccarle dallo sfondo
    val edge = if (light) Color.White.copy(alpha = 0.55f) else Color(0xFFFFB3A6).copy(alpha = 0.30f)
    val edgeStroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())

    fun blob(build: androidx.compose.ui.graphics.Path.() -> Unit) {
        val path = androidx.compose.ui.graphics.Path().apply(build)
        drawPath(path, liquid)
        drawPath(path, edge, style = edgeStroke)
    }

    // In alto a sinistra
    blob {
        moveTo(0f, 0f)
        lineTo(lx(0.95f), 0f)
        cubicTo(lx(0.90f), ty(0.18f), lx(0.70f), ty(0.24f), lx(0.50f), ty(0.27f))
        cubicTo(lx(0.30f), ty(0.30f), lx(0.12f), ty(0.30f), 0f, ty(0.36f))
        close()
    }
    // In basso a sinistra
    blob {
        moveTo(0f, by(0.58f))
        cubicTo(lx(0.30f), by(0.52f), lx(0.55f), by(0.62f), lx(0.80f), by(0.78f))
        cubicTo(lx(1.00f), by(0.90f), lx(1.15f), by(0.95f), lx(1.35f), h)
        lineTo(0f, h)
        close()
    }
    // In alto a destra
    blob {
        moveTo(rx(0.65f), 0f)
        cubicTo(rx(0.62f), ty(0.25f), rx(0.45f), ty(0.40f), rx(0.25f), ty(0.52f))
        cubicTo(rx(0.12f), ty(0.60f), rx(0.04f), ty(0.66f), w, ty(0.72f))
        lineTo(w, 0f)
        close()
    }
    // In basso a destra
    blob {
        moveTo(rx(1.45f), h)
        cubicTo(rx(1.25f), by(0.80f), rx(0.90f), by(0.72f), rx(0.60f), by(0.74f))
        cubicTo(rx(0.35f), by(0.76f), rx(0.15f), by(0.70f), w, by(0.62f))
        lineTo(w, h)
        close()
    }

    // Due bollicine, come gocce staccate dalle onde
    drawCircle(StopRed.copy(alpha = 0.55f), radius = u * 0.075f, center = Offset(lx(0.55f), by(0.60f)))
    drawCircle(StopRed.copy(alpha = 0.45f), radius = u * 0.07f, center = Offset(rx(0.85f), ty(0.22f)))
}

/** Una voce della barra delle tab. */
class GlassTab(val title: String, val icon: ImageVector, val endPadding: Dp = 0.dp)

/**
 * Barra delle tab in vetro con un indicatore che scorre insieme alle pagine.
 *
 * @param position posizione continua tra le tab: 0f = prima, 1f = seconda, 0.5f = a metà strada
 */
@Composable
fun GlassTabBar(
    position: Float,
    tabs: List<GlassTab>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val g = LocalGlass.current
    val innerShape = RoundedCornerShape(22.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(26.dp), GlassKind.Panel)
            .padding(4.dp)
    ) {
        BoxWithConstraints(modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)) {
            val tabWidth = maxWidth / tabs.size

            // Indicatore: segue il dito mentre si scorre tra le pagine
            Box(
                modifier = Modifier
                    .offset(x = tabWidth * position)
                    .width(tabWidth)
                    .fillMaxHeight()
                    .background(g.indFill, innerShape)
                    .border(1.dp, g.indBorder, innerShape)
            )

            Row(modifier = Modifier.fillMaxSize()) {
                tabs.forEachIndexed { index, tab ->
                    val closeness = (1f - abs(position - index)).coerceIn(0f, 1f)
                    val color = lerp(g.textMuted, g.tabOn, closeness)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(innerShape)
                            .selectable(
                                selected = closeness > 0.5f,
                                role = Role.Tab,
                                onClick = { onSelect(index) }
                            )
                            .padding(end = tab.endPadding),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = tab.title,
                            color = color,
                            fontSize = 15.sp,
                            fontWeight = if (closeness > 0.5f) FontWeight.SemiBold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/** Bordo semplice con lo spessore del vetro, utile per le schede selezionabili. */
fun glassBorder(color: Color): BorderStroke = BorderStroke(1.dp, color)


/** Pulsante tondo con la freccia indietro, per l'intestazione delle schermate. */
@Composable
fun GlassBackButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .glass(CircleShape, GlassKind.Pill)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = GlassIcons.Back,
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp)
        )
    }
}

/** Scheda in vetro che raggruppa più elementi (come nelle impostazioni). */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(18.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(0.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(24.dp), GlassKind.Panel)
            .padding(padding),
        verticalArrangement = verticalArrangement,
        content = content
    )
}

/** Pulsante a campo, come un menu a tendina: testo a sinistra e freccia a destra. */
@Composable
fun FieldButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .glass(shape, GlassKind.Field)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = text, fontSize = 16.sp)
        Icon(
            imageVector = GlassIcons.ChevronDown,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** Una scelta tra più opzioni (per esempio lo stile): quella selezionata ha la spunta e il colore corallo. */
@Composable
fun GlassChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val g = LocalGlass.current
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = modifier
            .height(44.dp)
            .then(
                if (selected) {
                    Modifier
                        .background(g.selFill, shape)
                        .border(1.dp, g.selBorder, shape)
                } else {
                    Modifier.glass(shape, GlassKind.Field)
                }
            )
            .clip(shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(start = if (selected) 12.dp else 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) {
            Icon(
                imageVector = GlassIcons.Check,
                contentDescription = null,
                tint = g.selText,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) g.selText else g.text
        )
    }
}

/** Pulsantino con il colore corallo tenue (per esempio "Aggiungi termine"). */
@Composable
fun ChipButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val g = LocalGlass.current
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = modifier
            .height(44.dp)
            .background(g.chipFill, shape)
            .border(1.dp, g.chipBorder, shape)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 12.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = g.chipText,
            modifier = Modifier.size(18.dp)
        )
        Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = g.chipText)
    }
}

/** Pulsante tondo con il bordo corallo (per esempio "Esporta chat"). */
@Composable
fun AccentOutlineButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 54.dp,
    content: @Composable RowScope.() -> Unit
) {
    val g = LocalGlass.current
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .height(height)
            .background(g.outlineAccentFill, shape)
            .border(1.dp, g.outlineAccentBorder, shape)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalContentColor provides g.outlineAccentText) {
            content()
        }
    }
}

/** Pulsante pieno rosso per le azioni che cancellano (per esempio "Elimina chat"). */
@Composable
fun DangerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 54.dp,
    content: @Composable RowScope.() -> Unit
) {
    val g = LocalGlass.current
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .height(height)
            .shadow(10.dp, shape, ambientColor = g.dangerFill, spotColor = g.dangerFill)
            .background(g.dangerFill, shape)
            .border(
                1.dp,
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.15f))),
                shape
            )
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            content()
        }
    }
}


/** Dopo una scelta, quanto resta aperto il pannello prima di chiudersi (serve a vedere la voce scelta). */
private const val SHEET_CLOSE_DELAY_MS = 450L

/**
 * Pannello che sale dal basso (per scegliere una lingua, un tono, una durata) in stile vetro:
 * fondo semitrasparente, due luci sfocate come lo sfondo dell'app, bordo alto luminoso e maniglia.
 *
 * Il contenuto riceve la funzione `pick`: quando l'utente sceglie una voce si chiama
 * `pick { ...azione... }`. L'azione parte subito (la voce scelta si illumina), il pannello resta
 * aperto un istante e poi si chiude scorrendo verso il basso. Eventuali altri tocchi in quel
 * momento vengono ignorati.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassSheet(
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.(pick: (() -> Unit) -> Unit) -> Unit
) {
    val g = LocalGlass.current
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf(false) }

    val pick: (() -> Unit) -> Unit = { action ->
        if (!picked) {
            picked = true
            action()
            scope.launch {
                delay(SHEET_CLOSE_DELAY_MS)
                sheetState.hide()
                onDismissRequest()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = g.sheetFill,
        contentColor = g.text,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        scrimColor = g.scrim,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .background(g.text.copy(alpha = 0.28f), RoundedCornerShape(2.dp))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    orb(g.orb1, Offset(size.width * 0.10f, 0f), size.width * 0.70f)
                    orb(g.orb3, Offset(size.width * 0.95f, size.height), size.width * 0.60f)
                    // Riflesso sul bordo alto del vetro
                    drawLine(
                        color = g.glassHighlight,
                        start = Offset(28.dp.toPx(), 0f),
                        end = Offset(size.width - 28.dp.toPx(), 0f),
                        strokeWidth = 1.dp.toPx()
                    )
                }
        ) {
            content(pick)
        }
    }
}

/**
 * Una voce di un pannello: titolo, descrizione facoltativa e un elemento a sinistra (per esempio
 * la bandiera). La voce scelta si illumina di corallo con una breve animazione: lo sfondo appare
 * piano, la spunta "scatta" in posizione e la voce scelta prima si spegne.
 */
@Composable
fun SheetOption(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leading: (@Composable () -> Unit)? = null
) {
    val g = LocalGlass.current
    val shape = RoundedCornerShape(18.dp)

    // Da trasparente a corallo (e viceversa) senza scatti
    val fill by animateColorAsState(
        targetValue = if (selected) g.selFill else g.selFill.copy(alpha = 0f),
        animationSpec = tween(250),
        label = "sfondo voce"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) g.selBorder else g.selBorder.copy(alpha = 0f),
        animationSpec = tween(250),
        label = "bordo voce"
    )
    val titleColor by animateColorAsState(
        targetValue = if (selected) g.selText else g.text,
        animationSpec = tween(250),
        label = "testo voce"
    )
    val check by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(250),
        label = "spunta"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .background(fill, shape)
            .border(1.dp, borderColor, shape)
            .clip(shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = if (supporting != null) 64.dp else 52.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        leading?.invoke()
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = titleColor
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = g.textMuted
                )
            }
        }
        // La spunta c'è sempre (così il testo non si sposta) e compare ingrandendosi
        Icon(
            imageVector = GlassIcons.Check,
            contentDescription = if (selected) "Scelta" else null,
            tint = g.accent,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer {
                    alpha = check
                    val scale = 0.5f + 0.5f * check
                    scaleX = scale
                    scaleY = scale
                }
        )
    }
}