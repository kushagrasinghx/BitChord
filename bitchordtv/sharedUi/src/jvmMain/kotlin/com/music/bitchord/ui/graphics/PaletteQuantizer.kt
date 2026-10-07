package com.music.bitchord.ui.graphics

import androidx.compose.ui.graphics.ImageBitmap
import java.util.PriorityQueue
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * androidx Palette's colour cut, for the desktop, where the library itself
 * (which takes an `android.graphics.Bitmap`) cannot run.
 *
 * Ported step for step from `Palette.Builder.generate` and `ColorCutQuantizer`
 * (palette 1.0.0) so a cover yields the same swatches on both platforms: the
 * same 112x112 nearest-neighbour downscale, the same RGB555 histogram, the same
 * median cut by box volume and the same default filter.
 */
internal object PaletteQuantizer {

    private const val RESIZE_BITMAP_AREA = 112 * 112

    private const val QUANTIZE_WORD_WIDTH = 5
    private const val QUANTIZE_WORD_MASK = (1 shl QUANTIZE_WORD_WIDTH) - 1

    private const val COMPONENT_RED = -3
    private const val COMPONENT_GREEN = -2
    private const val COMPONENT_BLUE = -1

    fun swatches(image: ImageBitmap, maxColors: Int, clearFilters: Boolean): List<Swatch> {
        val pixels = scaledPixels(image)
        return Quantizer(pixels, maxColors, useDefaultFilter = !clearFilters).quantizedColors
    }

    /** `Palette.Builder.scaleBitmapDown`, then `getPixels`. */
    private fun scaledPixels(image: ImageBitmap): IntArray {
        val source = image.argbPixels()
        val width = image.width
        val height = image.height
        val area = width * height
        if (area <= RESIZE_BITMAP_AREA) return source
        val ratio = sqrt(RESIZE_BITMAP_AREA / area.toDouble())
        val scaledWidth = ceil(width * ratio).toInt().coerceAtLeast(1)
        val scaledHeight = ceil(height * ratio).toInt().coerceAtLeast(1)
        // createScaledBitmap(..., filter = false): nearest neighbour, sampled at
        // each destination pixel's centre.
        val out = IntArray(scaledWidth * scaledHeight)
        for (y in 0 until scaledHeight) {
            val sy = ((y + 0.5) * height / scaledHeight).toInt().coerceIn(0, height - 1)
            for (x in 0 until scaledWidth) {
                val sx = ((x + 0.5) * width / scaledWidth).toInt().coerceIn(0, width - 1)
                out[y * scaledWidth + x] = source[sy * width + sx]
            }
        }
        return out
    }

    private class Quantizer(pixels: IntArray, maxColors: Int, private val useDefaultFilter: Boolean) {
        private val histogram = IntArray(1 shl (QUANTIZE_WORD_WIDTH * 3))
        private val colors: IntArray
        private val tempHsl = FloatArray(3)
        val quantizedColors: List<Swatch>

        init {
            for (i in pixels.indices) {
                val quantized = quantizeFromRgb888(pixels[i])
                pixels[i] = quantized
                histogram[quantized]++
            }
            var distinct = 0
            for (color in histogram.indices) {
                if (histogram[color] > 0 && shouldIgnoreColor565(color)) histogram[color] = 0
                if (histogram[color] > 0) distinct++
            }
            colors = IntArray(distinct)
            var index = 0
            for (color in histogram.indices) {
                if (histogram[color] > 0) colors[index++] = color
            }
            quantizedColors = if (distinct <= maxColors) {
                colors.map { Swatch(approximateToRgb888(it), histogram[it]) }
            } else {
                quantizePixels(maxColors)
            }
        }

        private fun quantizePixels(maxColors: Int): List<Swatch> {
            val queue = PriorityQueue<Vbox>(maxColors) { lhs, rhs -> rhs.volume - lhs.volume }
            queue.offer(Vbox(0, colors.size - 1))
            while (queue.size < maxColors) {
                val vbox = queue.poll()
                if (vbox != null && vbox.canSplit()) {
                    queue.offer(vbox.splitBox())
                    queue.offer(vbox)
                } else {
                    break
                }
            }
            return queue.mapNotNull { vbox ->
                vbox.averageColor().takeUnless { shouldIgnoreColor(it.rgb) }
            }
        }

        private inner class Vbox(private val lowerIndex: Int, private var upperIndex: Int) {
            private var population = 0
            private var minRed = 0
            private var maxRed = 0
            private var minGreen = 0
            private var maxGreen = 0
            private var minBlue = 0
            private var maxBlue = 0

            init {
                fitBox()
            }

            val volume: Int
                get() = (maxRed - minRed + 1) * (maxGreen - minGreen + 1) * (maxBlue - minBlue + 1)

            fun canSplit(): Boolean = 1 + upperIndex - lowerIndex > 1

            fun fitBox() {
                var minR = Int.MAX_VALUE
                var minG = Int.MAX_VALUE
                var minB = Int.MAX_VALUE
                var maxR = Int.MIN_VALUE
                var maxG = Int.MIN_VALUE
                var maxB = Int.MIN_VALUE
                var count = 0
                for (i in lowerIndex..upperIndex) {
                    val color = colors[i]
                    count += histogram[color]
                    val r = quantizedRed(color)
                    val g = quantizedGreen(color)
                    val b = quantizedBlue(color)
                    if (r > maxR) maxR = r
                    if (r < minR) minR = r
                    if (g > maxG) maxG = g
                    if (g < minG) minG = g
                    if (b > maxB) maxB = b
                    if (b < minB) minB = b
                }
                minRed = minR
                maxRed = maxR
                minGreen = minG
                maxGreen = maxG
                minBlue = minB
                maxBlue = maxB
                population = count
            }

            fun splitBox(): Vbox {
                val splitPoint = findSplitPoint()
                val newBox = Vbox(splitPoint + 1, upperIndex)
                upperIndex = splitPoint
                fitBox()
                return newBox
            }

            private fun longestColorDimension(): Int {
                val redLength = maxRed - minRed
                val greenLength = maxGreen - minGreen
                val blueLength = maxBlue - minBlue
                return when {
                    redLength >= greenLength && redLength >= blueLength -> COMPONENT_RED
                    greenLength >= redLength && greenLength >= blueLength -> COMPONENT_GREEN
                    else -> COMPONENT_BLUE
                }
            }

            private fun findSplitPoint(): Int {
                val dimension = longestColorDimension()
                modifySignificantOctet(colors, dimension, lowerIndex, upperIndex)
                colors.sort(lowerIndex, upperIndex + 1)
                modifySignificantOctet(colors, dimension, lowerIndex, upperIndex)
                val midPoint = population / 2
                var count = 0
                for (i in lowerIndex..upperIndex) {
                    count += histogram[colors[i]]
                    if (count >= midPoint) return minOf(upperIndex - 1, i)
                }
                return lowerIndex
            }

            fun averageColor(): Swatch {
                var redSum = 0
                var greenSum = 0
                var blueSum = 0
                var total = 0
                for (i in lowerIndex..upperIndex) {
                    val color = colors[i]
                    val count = histogram[color]
                    total += count
                    redSum += count * quantizedRed(color)
                    greenSum += count * quantizedGreen(color)
                    blueSum += count * quantizedBlue(color)
                }
                val redMean = (redSum / total.toFloat()).roundToInt()
                val greenMean = (greenSum / total.toFloat()).roundToInt()
                val blueMean = (blueSum / total.toFloat()).roundToInt()
                return Swatch(approximateToRgb888(redMean, greenMean, blueMean), total)
            }
        }

        private fun shouldIgnoreColor565(color: Int): Boolean = shouldIgnoreColor(approximateToRgb888(color))

        private fun shouldIgnoreColor(rgb: Int): Boolean {
            if (!useDefaultFilter) return false
            ColorUtils.colorToHSL(rgb, tempHsl)
            // Palette.DEFAULT_FILTER: not white, not black, not the red I-line.
            val isBlack = tempHsl[2] <= 0.05f
            val isWhite = tempHsl[2] >= 0.95f
            val nearRedILine = tempHsl[0] in 10f..37f && tempHsl[1] <= 0.82f
            return isWhite || isBlack || nearRedILine
        }
    }

    private fun modifySignificantOctet(a: IntArray, dimension: Int, lower: Int, upper: Int) {
        when (dimension) {
            COMPONENT_RED -> Unit
            COMPONENT_GREEN -> for (i in lower..upper) {
                val color = a[i]
                a[i] = (quantizedGreen(color) shl (QUANTIZE_WORD_WIDTH + QUANTIZE_WORD_WIDTH)) or
                    (quantizedRed(color) shl QUANTIZE_WORD_WIDTH) or
                    quantizedBlue(color)
            }
            COMPONENT_BLUE -> for (i in lower..upper) {
                val color = a[i]
                a[i] = (quantizedBlue(color) shl (QUANTIZE_WORD_WIDTH + QUANTIZE_WORD_WIDTH)) or
                    (quantizedGreen(color) shl QUANTIZE_WORD_WIDTH) or
                    quantizedRed(color)
            }
        }
    }

    private fun quantizeFromRgb888(color: Int): Int {
        val r = modifyWordWidth((color shr 16) and 0xFF, 8, QUANTIZE_WORD_WIDTH)
        val g = modifyWordWidth((color shr 8) and 0xFF, 8, QUANTIZE_WORD_WIDTH)
        val b = modifyWordWidth(color and 0xFF, 8, QUANTIZE_WORD_WIDTH)
        return (r shl (QUANTIZE_WORD_WIDTH + QUANTIZE_WORD_WIDTH)) or (g shl QUANTIZE_WORD_WIDTH) or b
    }

    private fun approximateToRgb888(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or
            (modifyWordWidth(r, QUANTIZE_WORD_WIDTH, 8) shl 16) or
            (modifyWordWidth(g, QUANTIZE_WORD_WIDTH, 8) shl 8) or
            modifyWordWidth(b, QUANTIZE_WORD_WIDTH, 8)

    private fun approximateToRgb888(color: Int): Int =
        approximateToRgb888(quantizedRed(color), quantizedGreen(color), quantizedBlue(color))

    private fun quantizedRed(color: Int): Int =
        (color shr (QUANTIZE_WORD_WIDTH + QUANTIZE_WORD_WIDTH)) and QUANTIZE_WORD_MASK

    private fun quantizedGreen(color: Int): Int = (color shr QUANTIZE_WORD_WIDTH) and QUANTIZE_WORD_MASK

    private fun quantizedBlue(color: Int): Int = color and QUANTIZE_WORD_MASK

    private fun modifyWordWidth(value: Int, currentWidth: Int, targetWidth: Int): Int {
        val newValue = if (targetWidth > currentWidth) {
            value shl (targetWidth - currentWidth)
        } else {
            value shr (currentWidth - targetWidth)
        }
        return newValue and ((1 shl targetWidth) - 1)
    }
}
