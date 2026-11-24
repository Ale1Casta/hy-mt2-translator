package com.example.hy_mt2translator.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/**
 * Schema colori di Material 3 nella versione notte. I componenti standard (finestre, pannelli dal basso,
 * campi) prendono i colori da qui; sono opachi dove sotto non deve trasparire niente.
 */
private val NightScheme = darkColorScheme(
    primary = Color(0xFFFF9F7A),
    onPrimary = Color(0xFF3A1206),
    primaryContainer = Color(0xFF2C2122),
    onPrimaryContainer = Color(0xFFFFD2BF),
    secondary = Color(0xFFFFB89A),
    onSecondary = Color(0xFF3A1206),
    secondaryContainer = Color(0xFF271E20),
    onSecondaryContainer = Color(0xFFFFB89A),
    tertiary = Color(0xFFFFD27A),
    onTertiary = Color(0xFF3A2A06),
    background = Color(0xFF070B12),
    onBackground = Color(0xFFE8F1F8),
    surface = Color(0xFF070B12),
    onSurface = Color(0xFFE8F1F8),
    surfaceVariant = Color(0xFF161A20),
    onSurfaceVariant = Color(0xFF9FB0C2),
    outline = Color(0xFF4A5560),
    outlineVariant = Color(0xFF25282E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    surfaceContainerLowest = Color(0xFF0B1019),
    surfaceContainerLow = Color(0xFF0F1622),
    surfaceContainer = Color(0xFF131B28),
    surfaceContainerHigh = Color(0xFF172030),
    surfaceContainerHighest = Color(0xFF1B2536)
)

/** Schema colori di Material 3 nella versione giorno. */
private val DayScheme = lightColorScheme(
    primary = Color(0xFFFF7A4D),
    onPrimary = Color(0xFF2E0E04),
    primaryContainer = Color(0xFFE1DCE2),
    onPrimaryContainer = Color(0xFF9A3412),
    secondary = Color(0xFFC2410C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9CC),
    onSecondaryContainer = Color(0xFF9A3412),
    tertiary = Color(0xFFB45309),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFDDE9F6),
    onBackground = Color(0xFF0B1B2B),
    surface = Color(0xFFDDE9F6),
    onSurface = Color(0xFF0B1B2B),
    surfaceVariant = Color(0xFFF1F6FB),
    onSurfaceVariant = Color(0xFF4A5D70),
    outline = Color(0xFF7B8DA0),
    outlineVariant = Color(0xFFBFCFE0),
    error = Color(0xFFB42318),
    onError = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7FAFE),
    surfaceContainer = Color(0xFFF3F8FD),
    surfaceContainerHigh = Color(0xFFEEF4FB),
    surfaceContainerHighest = Color(0xFFEAF2FA)
)

/** Tema dell'app: segue il tema chiaro o scuro scelto sul telefono. */
@Composable
fun HyMT2TranslatorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalGlass provides if (darkTheme) NightTokens else DayTokens) {
        MaterialTheme(
            colorScheme = if (darkTheme) NightScheme else DayScheme,
            content = content
        )
    }
}