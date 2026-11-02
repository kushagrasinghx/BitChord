package com.music.bitchord.ui.components

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.size.Size
import coil3.transform.Transformation

@Composable
actual fun rememberBlurredBackdrop(url: String?): Any? {
    val context = LocalPlatformContext.current
    // Remembered: a freshly built request never equals the last one, and Coil
    // would cancel and restart the load on every recomposition.
    return remember(url, context) {
        url ?: return@remember null
        ImageRequest.Builder(context)
            .data(url)
            .transformations(BackdropBlur)
            .build()
    }
}

/**
 * The lower part of a cover, shrunk and blurred: what Apple Music lays under a
 * release card's caption, where the cover seems to run on, out of focus.
 *
 * Shrinking first is what makes this cheap — the blur runs over a bitmap
 * [WIDTH] pixels wide, three passes of a box blur (close to a Gaussian), a
 * millisecond or so — and a picture that soft loses nothing when the card
 * stretches it back up.
 */
private object BackdropBlur : Transformation() {

    /** The share of the cover, from the bottom, that the backdrop continues. */
    private const val STRIP = 0.4f
    private const val WIDTH = 48
    private const val RADIUS = 3
    private const val PASSES = 3

    override val cacheKey = "backdropBlur:$STRIP:$WIDTH:$RADIUS:$PASSES"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val top = (input.height * (1f - STRIP)).toInt().coerceIn(0, input.height - 1)
        val height = ((input.height - top) * WIDTH / input.width.toFloat()).toInt().coerceAtLeast(4)
        val software = if (input.config == Bitmap.Config.HARDWARE) input.copy(Bitmap.Config.ARGB_8888, false) else input
        val strip = Bitmap.createBitmap(software, 0, top, software.width, software.height - top)
        val small = Bitmap.createScaledBitmap(strip, WIDTH, height, true)
        val pixels = IntArray(WIDTH * height)
        small.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, height)
        repeat(PASSES) {
            boxBlur(pixels, WIDTH, height, RADIUS, horizontal = true)
            boxBlur(pixels, WIDTH, height, RADIUS, horizontal = false)
        }
        return Bitmap.createBitmap(pixels, WIDTH, height, Bitmap.Config.ARGB_8888)
    }

    /** One pass of a running-sum box blur along rows or columns, edges clamped. */
    private fun boxBlur(pixels: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean) {
        val lines = if (horizontal) height else width
        val length = if (horizontal) width else height
        val line = IntArray(length)
        val window = radius * 2 + 1
        for (l in 0 until lines) {
            fun at(i: Int) = if (horizontal) l * width + i else i * width + l
            for (i in 0 until length) line[i] = pixels[at(i)]
            var r = 0
            var g = 0
            var b = 0
            for (k in -radius..radius) {
                val p = line[k.coerceIn(0, length - 1)]
                r += p shr 16 and 0xFF
                g += p shr 8 and 0xFF
                b += p and 0xFF
            }
            for (i in 0 until length) {
                pixels[at(i)] = (0xFF shl 24) or ((r / window) shl 16) or ((g / window) shl 8) or (b / window)
                val out = line[(i - radius).coerceIn(0, length - 1)]
                val inn = line[(i + radius + 1).coerceIn(0, length - 1)]
                r += (inn shr 16 and 0xFF) - (out shr 16 and 0xFF)
                g += (inn shr 8 and 0xFF) - (out shr 8 and 0xFF)
                b += (inn and 0xFF) - (out and 0xFF)
            }
        }
    }
}
