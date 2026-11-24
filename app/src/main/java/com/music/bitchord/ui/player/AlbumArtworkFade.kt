package com.music.bitchord.ui.player

import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.PorterDuff
import android.graphics.Shader
import android.os.Build
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

/** One mask combines the artwork's physical bottom with protection for the centered text. */
internal data class AlbumArtworkFade(
    val titleStartPx: Float,
    val textWidthPx: Int,
    val headerHeightPx: Float,
    val titleOpacity: Float,
) {
    fun shader(width: Float, height: Float, sourceBottomPx: Float): Shader? {
        val end = minOf(height, sourceBottomPx, headerHeightPx)
        if (width <= 0f || end <= 0f || titleStartPx <= 0f) return null
        val textTop = minOf(titleStartPx, end)
        val start = textTop * (1f - ALBUM_FEATHER_FRACTION)
        if (textTop == end) return vertical(start, end) { t -> 1f - smootherStep(t * t) }
        // The measured text block includes its gutters. Feather outside it so
        // clouds can continue on either side without a visible text rectangle.
        val inset = if (textWidthPx > 0) ((width - textWidthPx) / 2f).coerceAtLeast(0f) else 0f
        // Nested/same-type ComposeShader isn't hardware supported on API 26–27.
        if (inset < 1f || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return protectedFoot(start, textTop, end)

        val foot = vertical(start, end) { t -> 1f - smootherStep(t * t) }
        val protection = vertical(start, textTop) { t -> (1f - titleOpacity) * smootherStep(t * t) }
        val edge = inset / width
        val sides = LinearGradient(
            0f, 0f, width, 0f,
            intArrayOf(0, android.graphics.Color.BLACK, android.graphics.Color.BLACK, 0),
            floatArrayOf(0f, edge, 1f - edge, 1f),
            Shader.TileMode.CLAMP,
        )
        val center = ComposeShader(protection, sides, PorterDuff.Mode.DST_IN)
        return ComposeShader(foot, center, PorterDuff.Mode.DST_OUT)
    }

    private fun protectedFoot(start: Float, textTop: Float, end: Float): LinearGradient {
        val join = (textTop - start) / (end - start)
        val n = FADE_CURVE_STOPS
        val positions = FloatArray(2 * n + 1)
        val colors = IntArray(positions.size) { i ->
            val alpha = if (i <= n) {
                val t = i / n.toFloat()
                positions[i] = join * t
                1f - (1f - titleOpacity) * smootherStep(t * t)
            } else {
                val t = (i - n) / n.toFloat()
                positions[i] = join + (1f - join) * t
                titleOpacity * (1f - smootherStep(t))
            }
            android.graphics.Color.argb(alpha, 0f, 0f, 0f)
        }
        return LinearGradient(0f, start, 0f, end, colors, positions, Shader.TileMode.CLAMP)
    }

    private fun vertical(start: Float, end: Float, alpha: (Float) -> Float): LinearGradient =
        LinearGradient(
            0f, start, 0f, end,
            IntArray(FADE_CURVE_STOPS + 1) { i ->
                android.graphics.Color.argb(alpha(i / FADE_CURVE_STOPS.toFloat()), 0f, 0f, 0f)
            },
            null,
            Shader.TileMode.CLAMP,
        )
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
