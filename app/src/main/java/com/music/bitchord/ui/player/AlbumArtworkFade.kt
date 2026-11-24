package com.music.bitchord.ui.player

import android.graphics.LinearGradient
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.music.bitchord.ui.graphics.FADE_CURVE_STOPS
import com.music.bitchord.ui.graphics.smootherStep
import com.music.bitchord.ui.theme.ArtworkPalette

private const val ALBUM_FEATHER_FRACTION = 0.22f
private const val HEADER_TEXT_CONTRAST = 4.5f
// At most a faint tail behind the header, even when the text has ample contrast.
private const val TITLE_ALPHA_LIMIT = 32

/** One coverage mask for the poster and video, in their unscaled hero coordinates. */
internal data class AlbumArtworkFade(val titleStartPx: Float, val titleOpacity: Float) {
    fun shader(height: Float): LinearGradient? {
        if (height <= 0f || titleStartPx <= 0f) return null
        val title = titleStartPx.coerceAtMost(height)
        val start = title * (1f - ALBUM_FEATHER_FRACTION)
        val end = if (titleOpacity > 0f) height else title
        val tail = if (title < end) titleOpacity else 0f
        val join = (title - start) / (end - start)
        val count = if (tail > 0f) 2 * FADE_CURVE_STOPS + 1 else FADE_CURVE_STOPS + 1
        val colors = IntArray(count)
        val positions = FloatArray(count)
        for (i in 0 until count) {
            val t = (i % FADE_CURVE_STOPS) / FADE_CURVE_STOPS.toFloat()
            val alpha = when {
                i < FADE_CURVE_STOPS -> {
                    positions[i] = join * t
                    // Delay the loss of fine detail without changing the crop or
                    // introducing a hard knee at the start or the text boundary.
                    tail + (1f - tail) * (1f - smootherStep(t * t))
                }
                i == FADE_CURVE_STOPS -> {
                    positions[i] = join
                    tail
                }
                else -> {
                    val u = (i - FADE_CURVE_STOPS) / FADE_CURVE_STOPS.toFloat()
                    positions[i] = join + (1f - join) * u
                    tail * (1f - smootherStep(u))
                }
            }
            colors[i] = android.graphics.Color.argb(alpha, 0f, 0f, 0f)
        }
        return LinearGradient(0f, start, 0f, end, colors, positions, Shader.TileMode.CLAMP)
    }
}

/** The strongest tail that keeps the existing heading and secondary ink readable. */
internal fun ArtworkPalette.albumArtworkTitleOpacity(): Float {
    val ground = background.convert(ColorSpaces.Srgb)
    val base = ground.luminance()
    val title = onBackground.luminance()
    val secondary = onBackgroundVariant.luminance()
    val lightInk = title > base
    val limit = if (lightInk) {
        (minOf(title, secondary) + 0.05f) / HEADER_TEXT_CONTRAST - 0.05f
    } else {
        (maxOf(title, secondary) + 0.05f) * HEADER_TEXT_CONTRAST - 0.05f
    }
    // Bound any pixel/frame with the extreme shade that hurts this ink most.
    // Check both sRGB source-over and linear-light mixing, in 8-bit mask steps.
    val extreme = if (lightInk) Color.White else Color.Black
    var passing = 0
    var remaining = TITLE_ALPHA_LIMIT
    while (passing < remaining) {
        val step = (passing + remaining + 1) / 2
        val alpha = step / 255f
        val srgb = extreme.copy(alpha = alpha).compositeOver(ground).luminance()
        val linear = base + (extreme.luminance() - base) * alpha
        val safe = if (lightInk) maxOf(srgb, linear) <= limit else minOf(srgb, linear) >= limit
        if (safe) passing = step else remaining = step - 1
    }
    return passing / 255f
}
