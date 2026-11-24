package com.music.bitchord.ui.player

import android.graphics.LinearGradient
import android.graphics.Shader
import com.music.bitchord.ui.graphics.FADE_CURVE_STOPS
import com.music.bitchord.ui.graphics.smootherStep

private const val ALBUM_FEATHER_FRACTION = 0.22f

/** Full-width bottom fade shared by the poster and the video. */
internal data class AlbumArtworkFade(
    val titleStartPx: Float,
    val headerHeightPx: Float,
) {
    fun shader(height: Float, sourceBottomPx: Float): LinearGradient? {
        val end = minOf(height, sourceBottomPx, headerHeightPx)
        if (end <= 0f || titleStartPx <= 0f) return null
        val start = minOf(titleStartPx, end) * (1f - ALBUM_FEATHER_FRACTION)
        return LinearGradient(
            0f, start, 0f, end,
            IntArray(FADE_CURVE_STOPS + 1) { i ->
                val t = i / FADE_CURVE_STOPS.toFloat()
                android.graphics.Color.argb(1f - smootherStep(t * t), 0f, 0f, 0f)
            },
            null,
            Shader.TileMode.CLAMP,
        )
    }
}
