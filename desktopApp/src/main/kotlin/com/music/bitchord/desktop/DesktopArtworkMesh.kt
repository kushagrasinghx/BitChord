package com.music.bitchord.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/** The artwork's own colours, upside down and reduced to a mesh, for the player to stand on. */
internal object DesktopArtworkMesh {

    /** Cells per side. */
    private const val MESH_GRID = 6

    /** The texture the mesh is resampled into before it is drawn. */
    private const val MESH_TEX = 32

    /** The most rows and columns of the source actually looked at. */
    private const val MESH_SAMPLE = 128

    private const val MESH_VIBRANCE = 1.12f
    private const val MESH_FLOOR = 0.045f

    /** Averages [source] into the mesh texture, flipped top to bottom and then rotated sideways. */
    fun of(source: ImageBitmap, seed: Int): ImageBitmap? {
        val width = source.width
        val height = source.height
        if (width < 1 || height < 1) return null

        val pixels = IntArray(width * height)
        source.readPixels(pixels, 0, 0, width, height)

        // A cell nothing lands in would come out black, so the grid never asks for more cells than
        // the artwork has pixels.
        val cols = min(MESH_GRID, width)
        val rows = min(MESH_GRID, height)
        val rowStep = max(1, height / MESH_SAMPLE)
        val colStep = max(1, width / MESH_SAMPLE)
        val cells = rows * cols
        val red = LongArray(cells)
        val green = LongArray(cells)
        val blue = LongArray(cells)
        val count = IntArray(cells)

        var y = 0
        while (y < height) {
            // Flipped as it is read: row 0 of the grid is the sleeve's bottom.
            val rowBase = ((height - 1 - y) * rows / height) * cols
            var x = 0
            while (x < width) {
                val cell = rowBase + x * cols / width
                val pixel = pixels[y * width + x]
                red[cell] += ((pixel shr 16) and 0xFF).toLong()
                green[cell] += ((pixel shr 8) and 0xFF).toLong()
                blue[cell] += (pixel and 0xFF).toLong()
                count[cell]++
                x += colStep
            }
            y += rowStep
        }

        val grid = IntArray(cells) { cell ->
            val n = max(1, count[cell])
            argb((red[cell] / n).toInt(), (green[cell] / n).toInt(), (blue[cell] / n).toInt()).lifted()
        }
        val texels = grid.rotatedBelowSeam(cols, rows, seed).resampled(cols, rows, MESH_TEX)
        return texels.toImageBitmap(MESH_TEX)
    }

    /** Every row but row 0. */
    private fun IntArray.rotatedBelowSeam(cols: Int, rows: Int, seed: Int): IntArray {
        if (rows <= 1) return this
        val random = Random(seed)
        val mirror = random.nextBoolean()
        val shift = random.nextInt(cols)
        val out = copyOf()
        for (row in 1 until rows) {
            val base = row * cols
            for (x in 0 until cols) {
                val src = if (mirror) cols - 1 - x else x
                out[base + x] = this[base + (src + shift) % cols]
            }
        }
        return out
    }

    /** Smoothly interpolated up to a square texture the sampler can stretch. */
    private fun IntArray.resampled(cols: Int, rows: Int, size: Int): IntArray {
        val out = IntArray(size * size)
        for (ty in 0 until size) {
            val fy = (ty + 0.5f) / size * rows - 0.5f
            val y0 = floor(fy).toInt().coerceIn(0, rows - 1)
            val y1 = (y0 + 1).coerceAtMost(rows - 1)
            val wy = smoothstep(fy - y0)
            for (tx in 0 until size) {
                val fx = (tx + 0.5f) / size * cols - 0.5f
                val x0 = floor(fx).toInt().coerceIn(0, cols - 1)
                val x1 = (x0 + 1).coerceAtMost(cols - 1)
                val wx = smoothstep(fx - x0)
                val top = lerpArgb(this[y0 * cols + x0], this[y0 * cols + x1], wx)
                val bottom = lerpArgb(this[y1 * cols + x0], this[y1 * cols + x1], wx)
                out[ty * size + tx] = lerpArgb(top, bottom, wy)
            }
        }
        return out
    }

    private fun IntArray.toImageBitmap(size: Int): ImageBitmap {
        val info = ImageInfo(size, size, ColorType.BGRA_8888, ColorAlphaType.PREMUL)
        val bytes = ByteArray(size * size * 4)
        forEachIndexed { index, argbValue ->
            val at = index * 4
            bytes[at] = (argbValue and 0xFF).toByte()
            bytes[at + 1] = ((argbValue shr 8) and 0xFF).toByte()
            bytes[at + 2] = ((argbValue shr 16) and 0xFF).toByte()
            bytes[at + 3] = ((argbValue shr 24) and 0xFF).toByte()
        }
        val bitmap = Bitmap()
        bitmap.allocPixels(info)
        bitmap.installPixels(bytes)
        bitmap.setImmutable()
        return org.jetbrains.skia.Image.makeFromBitmap(bitmap).toComposeImageBitmap()
    }

    private fun smoothstep(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    private fun lerpArgb(from: Int, to: Int, t: Float): Int {
        if (t <= 0f) return from
        if (t >= 1f) return to
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + ((b - a) * t)).roundToInt().coerceIn(0, 255)
        }
        return argb(channel(16), channel(8), channel(0))
    }

    private fun argb(red: Int, green: Int, blue: Int): Int =
        (0xFF shl 24) or (red shl 16) or (green shl 8) or blue

    /**
     * A mean is duller than the cover it came from, and a cover that is nearly black would leave
     * the player standing on nothing at all.
     */
    private fun Int.lifted(): Int {
        val r = ((this shr 16) and 0xFF) / 255f
        val g = ((this shr 8) and 0xFF) / 255f
        val b = (this and 0xFF) / 255f
        val maxC = maxOf(r, g, b)
        val minC = minOf(r, g, b)
        val lightness = (maxC + minC) / 2f
        val delta = maxC - minC
        var saturation = if (delta == 0f) 0f else delta / (1f - kotlin.math.abs(2f * lightness - 1f))
        val hue = when {
            delta == 0f -> 0f
            maxC == r -> 60f * (((g - b) / delta) % 6f)
            maxC == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }
        saturation = (saturation * MESH_VIBRANCE).coerceAtMost(1f)
        return hslToArgb(if (hue < 0f) hue + 360f else hue, saturation, lightness.coerceAtLeast(MESH_FLOOR))
    }

    private fun hslToArgb(hue: Float, saturation: Float, lightness: Float): Int {
        val c = (1f - kotlin.math.abs(2f * lightness - 1f)) * saturation
        val x = c * (1f - kotlin.math.abs((hue / 60f) % 2f - 1f))
        val m = lightness - c / 2f
        val (r, g, b) = when {
            hue < 60f -> Triple(c, x, 0f)
            hue < 120f -> Triple(x, c, 0f)
            hue < 180f -> Triple(0f, c, x)
            hue < 240f -> Triple(0f, x, c)
            hue < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return argb(
            ((r + m) * 255f).roundToInt().coerceIn(0, 255),
            ((g + m) * 255f).roundToInt().coerceIn(0, 255),
            ((b + m) * 255f).roundToInt().coerceIn(0, 255),
        )
    }
}
