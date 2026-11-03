package com.example.listacompras.ui.calendar

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import com.example.listacompras.R

/** Colours offered when creating or editing a type; the first three are the seeded types. */
val typePalette: List<Pair<String, Int>> = listOf(
    "#4CAF50" to R.string.color_green,
    "#E91E63" to R.string.color_pink,
    "#2196F3" to R.string.color_blue,
    "#FF9800" to R.string.color_orange,
    "#9C27B0" to R.string.color_purple,
    "#00BCD4" to R.string.color_cyan,
    "#795548" to R.string.color_brown,
    "#607D8B" to R.string.color_grey,
)

/** `#RRGGBB` → colour, or null when it does not parse (the caller then uses a neutral colour). */
fun parseTypeColor(hex: String?): Color? {
    val digits = hex?.removePrefix("#")?.takeIf { it.length == 6 } ?: return null
    val rgb = digits.toLongOrNull(16) ?: return null
    return Color(0xFF000000 or rgb)
}

/** Dark colours are lightened on the dark theme so swatches stay visible against the surface. */
fun adaptToTheme(color: Color, dark: Boolean): Color =
    if (dark && color.luminance() < DARK_MIN_LUMINANCE) lerp(color, Color.White, 0.4f) else color

private const val DARK_MIN_LUMINANCE = 0.2f
