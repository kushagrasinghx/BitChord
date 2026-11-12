package com.music.bitchord.data.canvas

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation
import kotlin.math.roundToInt

/**
 * Turns a scanned signature (dark ink on white paper) into white strokes on
 * transparency, so it reads on the dark artist photo like the tinted SVGs.
 *
 * Maps each pixel's alpha to its darkness (GIMP-style white-to-alpha) and
 * paints the survivors white. Runs only for raster signature loads — see
 * DetailScreen — and refuses images that don't read as scans: when fewer
 * than [SCAN_WHITE_FRACTION] of pixels are near-white the input is returned
 * untouched, so an in-person autograph *photograph* never becomes white
 * blobs. Already-transparent PNG ink passes through the same math harmlessly
 * (transparent stays transparent, dark strokes go white).
 */
object SignatureInkTransformation : Transformation() {

    override val cacheKey = "signature-ink-v1"

    /** Luminance above this counts as paper rather than ink (0..1). */
    private const val PAPER_LUMINANCE = 0.92f

    /** Share of paper pixels required before treating the image as a scan. */
    private const val SCAN_WHITE_FRACTION = 0.55f

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val width = input.width
        val height = input.height
        if (width <= 0 || height <= 0) return input

        val pixels = IntArray(width * height)
        input.getPixels(pixels, 0, width, 0, 0, width, height)

        var paper = 0
        for (pixel in pixels) {
            if (luminance(pixel) >= PAPER_LUMINANCE) paper++
        }
        if (paper.toFloat() / pixels.size < SCAN_WHITE_FRACTION) return input

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val inAlpha = (pixel ushr 24) / 255f
            if (inAlpha <= 0f) continue
            val darkness = 1f - luminance(pixel)
            if (darkness <= 0f) {
                pixels[i] = 0
                continue
            }
            val outAlpha = (darkness * inAlpha * 255f).roundToInt().coerceIn(0, 255)
            pixels[i] = (outAlpha shl 24) or 0x00FFFFFF
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            it.setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    private fun luminance(pixel: Int): Float {
        val r = ((pixel ushr 16) and 0xFF) / 255f
        val g = ((pixel ushr 8) and 0xFF) / 255f
        val b = (pixel and 0xFF) / 255f
        return 0.299f * r + 0.587f * g + 0.114f * b
    }
}
