package com.music.bitchord.wear.data

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import com.google.android.gms.wearable.DataMap
import com.music.bitchord.wear.WearProtocol as P

/** What the phone says is playing. Built from a [P.PATH_NOW_PLAYING] DataMap. */
@Immutable
data class NowPlaying(
    val mediaId: String,
    val title: String,
    val artist: String,
    val album: String,
    val playing: Boolean,
    val advancing: Boolean,
    val positionMs: Long,
    val positionAt: Long,
    val durationMs: Long,
    val speed: Float,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
    val liked: Boolean,
    /** The phone player's own rule: signed in, and not a local file or download. */
    val canLike: Boolean,
    val shuffle: Boolean,
    val repeatMode: Int,
    val colors: List<Color>,
    val artId: String?,
) {
    /** Where the playhead is now, run on from the phone's last reading while it is moving. */
    fun positionAt(wallMs: Long): Long {
        if (!advancing || durationMs <= 0L) return positionMs
        val ran = ((wallMs - positionAt).coerceAtLeast(0L) * speed).toLong()
        return (positionMs + ran).coerceAtMost(durationMs)
    }

    fun progressAt(wallMs: Long): Float =
        if (durationMs <= 0L) 0f else (positionAt(wallMs).toFloat() / durationMs).coerceIn(0f, 1f)

    companion object {
        /** Null for "connected, but nothing queued on the phone". */
        fun from(map: DataMap): NowPlaying? {
            if (!map.getBoolean(P.KEY_HAS_TRACK, false)) return null
            return NowPlaying(
                mediaId = map.getString(P.KEY_MEDIA_ID).orEmpty(),
                title = map.getString(P.KEY_TITLE).orEmpty(),
                artist = map.getString(P.KEY_ARTIST).orEmpty(),
                album = map.getString(P.KEY_ALBUM).orEmpty(),
                playing = map.getBoolean(P.KEY_PLAYING),
                advancing = map.getBoolean(P.KEY_ADVANCING),
                positionMs = map.getLong(P.KEY_POSITION_MS),
                positionAt = map.getLong(P.KEY_POSITION_AT),
                durationMs = map.getLong(P.KEY_DURATION_MS),
                speed = map.getFloat(P.KEY_SPEED, 1f),
                hasPrevious = map.getBoolean(P.KEY_HAS_PREVIOUS),
                hasNext = map.getBoolean(P.KEY_HAS_NEXT),
                liked = map.getBoolean(P.KEY_LIKED),
                canLike = map.getBoolean(P.KEY_CAN_LIKE),
                shuffle = map.getBoolean(P.KEY_SHUFFLE),
                repeatMode = map.getInt(P.KEY_REPEAT),
                colors = map.getIntegerArrayList(P.KEY_COLORS)?.map { Color(it) }.orEmpty(),
                artId = map.getString(P.KEY_ART_ID),
            )
        }
    }
}

/** The phone's media volume, in its own steps. */
@Immutable
data class Volume(val level: Int, val max: Int) {
    val fraction: Float get() = if (max <= 0) 0f else level.toFloat() / max
}

@Immutable
data class QueueRow(
    val index: Int,
    val title: String,
    val artist: String,
    val art: ImageBitmap?,
)

@Immutable
data class Queue(val rows: List<QueueRow>, val currentIndex: Int, val mediaId: String)

enum class Link {
    /** Looking for the phone, or waiting on its first answer. */
    Connecting,

    /** No reachable phone with this build of BitChord on it. */
    NoPhone,

    Connected,
}

/** One of the phone's outputs, as its player lists them. */
@Immutable
data class PhoneOutput(val id: Int, val name: String, val kind: String, val active: Boolean)
