package com.music.bitchord.ui.player

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Heuristic to reject a frame that is almost certainly the first read after
 * a surface recreation — ExoPlayer has not yet decoded real content, so the
 * buffer is black or near-black.  Mean luminance below ~12 on a 0-255 scale
 * (roughly 4.7%) is our threshold; a genuine dark cover art frame will almost
 * always exceed it because even black sleeves have noise and compression
 * artefacts that push the average up.
 */
internal fun isLikelyBlackFrame(bitmap: ImageBitmap): Boolean {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= 0 || h <= 0) return true
    // Sample a sparse grid to keep this cheap (called every few seconds).
    val stride = maxOf(1, minOf(w, h) / 32)
    var sumLum = 0L
    var count = 0
    val row = IntArray(w)
    for (y in 0 until h step stride) {
        bitmap.readPixels(row, startX = 0, startY = y, width = w, height = 1)
        for (x in 0 until w step stride) {
            val p = row[x]
            // ITU-R BT.601 luma weights
            sumLum += ((p shr 16 and 0xFF) * 30 +
                (p shr 8 and 0xFF) * 59 +
                (p and 0xFF) * 11) / 100
            count++
        }
    }
    val mean = sumLum / count
    return mean < 12
}
