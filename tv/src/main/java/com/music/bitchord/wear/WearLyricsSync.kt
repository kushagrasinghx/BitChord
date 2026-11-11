package com.music.bitchord.wear

import android.content.Context
import com.music.bitchord.data.lyrics.LyricLine

/**
 * The TV build has no watch to talk to. Same surface as the phone's WearLyricsSync, doing nothing,
 * the way this folder's WearBridge does.
 */
object WearLyricsSync {
    class Playhead(val positionMs: Long, val advancing: Boolean, val speed: Float)

    @Volatile var playhead: (() -> Playhead?)? = null

    fun start(context: Context) = Unit

    fun stop(context: Context) = Unit

    fun publish(
        context: Context,
        mediaId: String,
        title: String,
        artist: String,
        durationMs: Long,
        lines: List<LyricLine>?,
        album: String? = null,
        fromUi: Boolean = false,
    ) = Unit
}
