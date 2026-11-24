package com.example.hy_mt2translator.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Costruisce un'icona a tratto (senza riempimento) da tracciati nello stesso formato degli SVG del mockup. */
private fun strokeIcon(name: String, vararg paths: String, width: Float = 1.8f): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        for (d in paths) {
            addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = null,
                // Il colore vero lo decide chi usa l'icona (parametro tint di Icon)
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
    }.build()

/** Le icone del mockup "Glass". */
object GlassIcons {
    val Translate = strokeIcon(
        "translate",
        "M4 5h9M8.5 3v2M6 5c.6 3 2.6 5.5 5.5 7M12 5c-.8 3.4-3.2 6.4-7 8",
        "M13 20l4-9 4 9M14.5 17h5"
    )
    val Chat = strokeIcon(
        "chat",
        "M5 5h14a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-8l-5 4v-4H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z"
    )
    val Copy = strokeIcon(
        "copy",
        "M11 9h7a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2h-7a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2z",
        "M5 15V6a2 2 0 0 1 2-2h9"
    )
    val Share = strokeIcon(
        "share",
        "M15.5 5a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0",
        "M3.5 12a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0",
        "M15.5 19a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0",
        "M8.2 10.8l7.6-4.4M8.2 13.2l7.6 4.4"
    )
    val ChevronDown = strokeIcon("chevron_down", "M6 9l6 6 6-6", width = 2f)
    val ChevronRight = strokeIcon("chevron_right", "M9 5l7 7-7 7", width = 2f)
    val Back = strokeIcon("back", "M15 5l-7 7 7 7", width = 2f)
    val Tone = strokeIcon("tone", "M3 12h2M7 8v8M11 5v14M15 9v6M19 11v2", width = 2f)
    val Send = strokeIcon("send", "M22 2L11 13M22 2l-7 20-4-9-9-4z", width = 2f)
    val Plus = strokeIcon("plus", "M12 5v14M5 12h14", width = 2.2f)
    val Trash = strokeIcon("trash", "M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3")
    val Clock = strokeIcon("clock", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0-18 0", "M12 7v5l3 2", width = 2f)
    val Pin = strokeIcon("pin", "M9 4h6l-1 6 3 3H7l3-3zM12 13v7", width = 2f)
    val Check = strokeIcon("check", "M5 12l5 5 9-10", width = 2f)
    val Export = strokeIcon("export", "M12 4v11M7 10l5 5 5-5M5 20h14", width = 2f)
    val ArrowRight = strokeIcon("arrow_right", "M5 12h14M13 6l6 6-6 6", width = 2f)
    val Settings = strokeIcon(
        "settings",
        "M9 12a3 3 0 1 0 6 0a3 3 0 1 0-6 0",
        "M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z",
        width = 1.9f
    )
    val Moon = strokeIcon("moon", "M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z", width = 1.9f)
    val Sun = strokeIcon(
        "sun",
        "M8 12a4 4 0 1 0 8 0a4 4 0 1 0-8 0",
        "M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41",
        width = 1.9f
    )
    val Close = strokeIcon("close", "M6 6l12 12M18 6L6 18", width = 2.4f)
    val Mic = strokeIcon(
        "mic",
        "M12 2a4 4 0 0 1 4 4v5a4 4 0 0 1-8 0V6a4 4 0 0 1 4-4z",
        "M19 10a7 7 0 0 1-14 0M12 19v3M9 22h6",
        width = 1.9f
    )
    val Refresh = strokeIcon("refresh", "M20 12a8 8 0 1 1-2.34-5.66M20 4v5h-5", width = 2f)
    val Edit = strokeIcon("edit", "M12 20h9", "M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4 12.5-12.5z")
}