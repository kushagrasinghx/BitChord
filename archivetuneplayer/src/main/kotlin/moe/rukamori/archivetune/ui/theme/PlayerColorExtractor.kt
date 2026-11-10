/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.max

object PlayerColorExtractor {
    const val GREYSCALE_WEIGHTED_SATURATION_THRESHOLD = 0.10f
    const val GREYSCALE_COLORFUL_RATIO_THRESHOLD = 0.08f
    const val GREYSCALE_NEUTRAL_RATIO_THRESHOLD = 0.90f

    private const val COLORFUL_MIN_SATURATION = 0.18f
    private const val NEUTRAL_MAX_SATURATION = 0.12f

    private const val EXTREME_DARK_LIGHTNESS = 0.05f
    private const val EXTREME_LIGHT_LIGHTNESS = 0.97f
    private const val EXTREME_SWATCH_PENALTY = 0.35f

    private const val NEUTRAL_PRESERVE_SATURATION = 0.12f

    private const val GRADIENT_STOP_COUNT = 6

    data class GreyscaleStats(
        val weightedSaturation: Float,
        val colorfulPopulationRatio: Float,
        val neutralPopulationRatio: Float,
        val dominantSaturation: Float,
        val dominantBrightness: Float,
    )

    data class ColorSwatch(
        val rgb: Int,
        val population: Int,
    )

    fun rgbToHsl(rgb: Int): FloatArray {
        val r = ((rgb shr 16) and 0xFF) / 255f
        val g = ((rgb shr 8) and 0xFF) / 255f
        val b = (rgb and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        val lightness = (max + min) / 2f
        val saturation =
            if (delta == 0f) {
                0f
            } else {
                delta / (1f - abs(2f * lightness - 1f))
            }
        val hue =
            when {
                delta == 0f -> 0f
                max == r -> 60f * (((g - b) / delta) % 6f)
                max == g -> 60f * (((b - r) / delta) + 2f)
                else -> 60f * (((r - g) / delta) + 4f)
            }
        val normalizedHue = if (hue < 0f) hue + 360f else hue
        return floatArrayOf(if (normalizedHue >= 360f) 0f else normalizedHue, saturation, lightness)
    }

    fun hslToColor(hsl: FloatArray): Int {
        val h = hsl[0]
        val s = hsl[1]
        val l = hsl[2]
        val c = (1f - abs(2f * l - 1f)) * s
        val m = l - 0.5f * c
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        var r = 0f
        var g = 0f
        var b = 0f
        when ((h / 60f).toInt()) {
            0 -> { r = c; g = x }
            1 -> { r = x; g = c }
            2 -> { g = c; b = x }
            3 -> { g = x; b = c }
            4 -> { r = x; b = c }
            5, 6 -> { r = c; b = x }
        }
        val ir = ((r + m) * 255f).toInt().coerceIn(0, 255)
        val ig = ((g + m) * 255f).toInt().coerceIn(0, 255)
        val ib = ((b + m) * 255f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ir shl 16) or (ig shl 8) or ib
    }

    fun blendArgb(
        first: Int,
        second: Int,
        ratio: Float,
    ): Int {
        val inverse = 1f - ratio
        val a = ((first ushr 24) * inverse + (second ushr 24) * ratio).toInt()
        val r = (((first shr 16) and 0xFF) * inverse + ((second shr 16) and 0xFF) * ratio).toInt()
        val g = (((first shr 8) and 0xFF) * inverse + ((second shr 8) and 0xFF) * ratio).toInt()
        val b = ((first and 0xFF) * inverse + (second and 0xFF) * ratio).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    suspend fun extractGradientColors(
        palette: Palette,
        fallbackColor: Int,
    ): List<Color> =
        withContext(Dispatchers.Default) {
            val swatches =
                listOfNotNull(
                    palette.vibrantSwatch,
                    palette.lightVibrantSwatch,
                    palette.darkVibrantSwatch,
                    palette.dominantSwatch,
                    palette.mutedSwatch,
                    palette.darkMutedSwatch,
                    palette.lightMutedSwatch,
                ).distinctBy { it.rgb }
                    .map { ColorSwatch(rgb = it.rgb, population = it.population) }
            val stats = computeGreyscaleStats(swatches)
            val result = extractGradientColors(swatches, fallbackColor)
            Timber.tag("PlayerColorExtractor").d(
                "palette extraction swatches=%d weightedSat=%.3f colorful=%.3f neutral=%.3f " +
                    "dominantSat=%.3f dominantVal=%.3f greyscale=%s colors=%d",
                swatches.size,
                stats.weightedSaturation,
                stats.colorfulPopulationRatio,
                stats.neutralPopulationRatio,
                stats.dominantSaturation,
                stats.dominantBrightness,
                isGreyscale(stats),
                result.size,
            )
            result
        }

    fun extractGradientColors(
        swatches: List<ColorSwatch>,
        fallbackColor: Int,
    ): List<Color> {
        if (swatches.isEmpty()) {
            return listOf(Color(fallbackColor))
        }

        val stats = computeGreyscaleStats(swatches)
        if (isGreyscale(stats)) {
            return buildGreyscaleGradient(swatches)
        }

        val rankedSwatches =
            swatches
                .map { it to calculateColorWeight(it) }
                .sortedByDescending { (_, weight) -> weight }
                .map { (swatch, _) -> swatch }

        val availableColors = mutableListOf<Color>()
        for (swatch in rankedSwatches) {
            val color = enhanceColorVividness(Color(swatch.rgb))
            if (!isSimilarToAny(color, availableColors)) {
                availableColors.add(color)
            }
            if (availableColors.size >= GRADIENT_STOP_COUNT) break
        }

        if (availableColors.isNotEmpty() && availableColors.size < GRADIENT_STOP_COUNT) {
            val baseColors = availableColors.toList()
            val valueTargets = floatArrayOf(0.80f, 0.62f, 0.44f, 0.70f, 0.52f, 0.34f)
            var derivationIndex = 0
            var guard = 0
            while (availableColors.size < GRADIENT_STOP_COUNT && guard < 64) {
                guard++
                val base = baseColors[derivationIndex % baseColors.size]
                val next = baseColors[(derivationIndex + 1) % baseColors.size]
                val valueTarget = valueTargets[availableColors.size % valueTargets.size]
                val derived =
                    when (derivationIndex % 3) {
                        0 -> adjustBrightness(base, valueTarget)
                        1 -> adjustBrightness(blendColors(base, next, 0.33f), valueTarget)
                        else -> adjustBrightness(blendColors(base, next, 0.66f), valueTarget)
                    }
                derivationIndex++
                if (!isSimilarToAny(derived, availableColors)) {
                    availableColors.add(derived)
                }
            }
        }

        if (availableColors.isEmpty()) {
            availableColors.add(adjustBrightness(Color(fallbackColor), 0.6f))
        }

        return availableColors
    }

    fun computeGreyscaleStats(swatches: List<ColorSwatch>): GreyscaleStats {
        val totalPopulation = swatches.sumOf { it.population }.coerceAtLeast(1).toFloat()
        var saturationSum = 0f
        var colorfulPopulation = 0f
        var neutralPopulation = 0f
        var dominantSwatch: ColorSwatch? = null
        for (swatch in swatches) {
            val hsl = FloatArray(3)
            rgbToHsl(swatch.rgb).copyInto(hsl)
            val saturation = hsl[1]
            val lightness = hsl[2]
            saturationSum += saturation * swatch.population
            if (saturation >= COLORFUL_MIN_SATURATION && lightness in 0.04f..0.97f) {
                colorfulPopulation += swatch.population
            }
            if (saturation < NEUTRAL_MAX_SATURATION) {
                neutralPopulation += swatch.population
            }
            if (dominantSwatch == null || swatch.population > dominantSwatch!!.population) {
                dominantSwatch = swatch
            }
        }
        val dominantHsl = FloatArray(3)
        rgbToHsl(dominantSwatch?.rgb ?: 0).copyInto(dominantHsl)
        return GreyscaleStats(
            weightedSaturation = saturationSum / totalPopulation,
            colorfulPopulationRatio = colorfulPopulation / totalPopulation,
            neutralPopulationRatio = neutralPopulation / totalPopulation,
            dominantSaturation = dominantHsl[1],
            dominantBrightness = dominantHsl[2],
        )
    }

    fun isGreyscale(stats: GreyscaleStats): Boolean =
        stats.weightedSaturation < GREYSCALE_WEIGHTED_SATURATION_THRESHOLD &&
            stats.colorfulPopulationRatio < GREYSCALE_COLORFUL_RATIO_THRESHOLD &&
            stats.neutralPopulationRatio > GREYSCALE_NEUTRAL_RATIO_THRESHOLD

    private fun buildGreyscaleGradient(swatches: List<ColorSwatch>): List<Color> {
        val baseBrightness =
            swatches.maxByOrNull { it.population }?.let { swatch ->
                val hsl = FloatArray(3)
                rgbToHsl(swatch.rgb).copyInto(hsl)
                hsl[2]
            } ?: 0.10f
        val greyStops =
            floatArrayOf(
                (baseBrightness * 1.2f).coerceIn(0.06f, 0.40f),
                (baseBrightness * 0.9f).coerceIn(0.04f, 0.28f),
                (baseBrightness * 0.6f).coerceIn(0.02f, 0.16f),
                (baseBrightness * 1.4f).coerceIn(0.08f, 0.44f),
                (baseBrightness * 0.7f).coerceIn(0.03f, 0.20f),
                (baseBrightness * 0.5f).coerceIn(0.01f, 0.12f),
            )
        return List(GRADIENT_STOP_COUNT) { index ->
            Color(hslToColor(floatArrayOf(0f, 0f, greyStops[index % greyStops.size])))
        }
    }

    private fun calculateColorWeight(swatch: ColorSwatch): Float {
        val hsl = FloatArray(3)
        rgbToHsl(swatch.rgb).copyInto(hsl)
        val saturation = hsl[1]
        val lightness = hsl[2]
        val saturationFactor = 0.30f + 0.70f * saturation
        val brightnessUsability = (1f - abs(lightness - 0.55f) * 1.2f).coerceIn(0.25f, 1f)
        val extremePenalty =
            if (lightness < EXTREME_DARK_LIGHTNESS || lightness > EXTREME_LIGHT_LIGHTNESS) {
                EXTREME_SWATCH_PENALTY
            } else {
                1f
            }
        return swatch.population * saturationFactor * brightnessUsability * extremePenalty
    }

    private fun enhanceColorVividness(color: Color): Color {
        val hsl = FloatArray(3)
        rgbToHsl(color.toArgb()).copyInto(hsl)
        if (hsl[1] >= NEUTRAL_PRESERVE_SATURATION) {
            hsl[1] = (hsl[1] * 1.15f).coerceAtMost(1.0f)
        }
        hsl[2] = hsl[2].coerceIn(0.12f, 0.88f)
        return Color(hslToColor(hsl))
    }

    private fun adjustBrightness(
        color: Color,
        targetLightness: Float,
    ): Color {
        val hsl = FloatArray(3)
        rgbToHsl(color.toArgb()).copyInto(hsl)
        hsl[2] = (hsl[2] * 0.35f + targetLightness * 0.65f).coerceIn(0.06f, 0.92f)
        return Color(hslToColor(hsl))
    }

    private fun blendColors(
        first: Color,
        second: Color,
        ratio: Float,
    ): Color = Color(blendArgb(first.toArgb(), second.toArgb(), ratio))

    private fun isSimilarColor(
        color1: Color?,
        color2: Color?,
    ): Boolean {
        if (color1 == null || color2 == null) return false
        val hsl1 = FloatArray(3)
        val hsl2 = FloatArray(3)
        rgbToHsl(color1.toArgb()).copyInto(hsl1)
        rgbToHsl(color2.toArgb()).copyInto(hsl2)

        val hueDiffRaw = abs(hsl1[0] - hsl2[0])
        val hueDiff = kotlin.math.min(hueDiffRaw, 360f - hueDiffRaw)
        val satDiff = abs(hsl1[1] - hsl2[1])
        val lightnessDiff = abs(hsl1[2] - hsl2[2])

        if (max(hsl1[1], hsl2[1]) < NEUTRAL_MAX_SATURATION) {
            return lightnessDiff < 0.10f
        }
        if (hueDiff < 12f && satDiff < 0.12f && lightnessDiff < 0.12f) return true

        val threshold = 28
        val r1 = (color1.red * 255).toInt()
        val g1 = (color1.green * 255).toInt()
        val b1 = (color1.blue * 255).toInt()
        val r2 = (color2.red * 255).toInt()
        val g2 = (color2.green * 255).toInt()
        val b2 = (color2.blue * 255).toInt()

        return abs(r1 - r2) < threshold &&
            abs(g1 - g2) < threshold &&
            abs(b1 - b2) < threshold
    }

    private fun isSimilarToAny(
        color: Color,
        colors: List<Color>,
    ): Boolean = colors.any { isSimilarColor(color, it) }

    object Config {
        const val MAX_COLOR_COUNT = 32
        const val BITMAP_AREA = 8000
        const val IMAGE_SIZE = 128
    }
}
