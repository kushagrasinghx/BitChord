/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

data class BackdropTonePalette(
    val top: Color,
    val mid: Color,
    val bottom: Color,
) {
    companion object {
        fun fromColors(
            colors: List<Color>,
            fallbackColor: Int,
        ): BackdropTonePalette {
            val dominantColor = colors.firstOrNull()
            val fallback = Color(fallbackColor).backdropTone(valueMin = 0.12f, valueMax = 0.38f)
            val top = dominantColor?.backdropTone(valueMin = 0.20f, valueMax = 0.72f) ?: fallback
            val mid = dominantColor?.backdropTone(valueMin = 0.13f, valueMax = 0.48f) ?: top
            val bottom = dominantColor?.backdropTone(valueMin = 0.08f, valueMax = 0.32f) ?: mid
            return BackdropTonePalette(
                top = top,
                mid = mid,
                bottom = bottom,
            )
        }

        fun fromColorsLight(
            colors: List<Color>,
            fallbackColor: Int,
            darkTheme: Boolean,
        ): BackdropTonePalette {
            val sourceColor = colors.firstOrNull()
            if (darkTheme) {
                val fallback =
                    Color(fallbackColor).backdropTone(
                        valueMin = 0.12f,
                        valueMax = 0.38f,
                        saturationScale = 0.45f,
                        saturationCap = 0.30f,
                    )
                val top =
                    sourceColor?.backdropTone(
                        valueMin = 0.32f,
                        valueMax = 0.66f,
                        saturationScale = 0.45f,
                        saturationCap = 0.30f,
                    ) ?: fallback
                val mid =
                    sourceColor?.backdropTone(
                        valueMin = 0.24f,
                        valueMax = 0.52f,
                        saturationScale = 0.45f,
                        saturationCap = 0.30f,
                    ) ?: top
                val bottom =
                    sourceColor?.backdropTone(
                        valueMin = 0.15f,
                        valueMax = 0.38f,
                        saturationScale = 0.45f,
                        saturationCap = 0.30f,
                    ) ?: mid
                return BackdropTonePalette(top = top, mid = mid, bottom = bottom)
            }
            val fallback =
                Color(fallbackColor).backdropTone(
                    valueMin = 0.72f,
                    valueMax = 0.98f,
                    saturationScale = 0.45f,
                    saturationCap = 0.30f,
                )
            val top =
                sourceColor?.backdropTone(
                    valueMin = 0.88f,
                    valueMax = 0.98f,
                    saturationScale = 0.45f,
                    saturationCap = 0.30f,
                ) ?: fallback
            val mid =
                sourceColor?.backdropTone(
                    valueMin = 0.82f,
                    valueMax = 0.96f,
                    saturationScale = 0.45f,
                    saturationCap = 0.30f,
                ) ?: top
            val bottom =
                sourceColor?.backdropTone(
                    valueMin = 0.74f,
                    valueMax = 0.92f,
                    saturationScale = 0.45f,
                    saturationCap = 0.30f,
                ) ?: mid
            return BackdropTonePalette(top = top, mid = mid, bottom = bottom)
        }
    }
}

fun Color.backdropTone(
    valueMin: Float,
    valueMax: Float,
    saturationScale: Float = 1.27f,
    saturationCap: Float = 1f,
): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[1] =
        if (hsv[1] < 0.12f) {
            hsv[1].coerceAtMost(0.08f)
        } else {
            (hsv[1] * saturationScale).coerceIn(0f, saturationCap)
        }
    hsv[2] = hsv[2].coerceIn(valueMin, valueMax)
    return Color(android.graphics.Color.HSVToColor(hsv))
}
