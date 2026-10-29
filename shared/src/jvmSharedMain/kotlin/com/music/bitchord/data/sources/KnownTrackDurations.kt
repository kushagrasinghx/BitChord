package com.music.bitchord.data.sources

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory registry of confirmed track durations in seconds, keyed by videoId.
 *
 * Populated when catalogue rows are parsed (home feed recents, history, playlists)
 * and when the decoder measures playback duration. This guarantees that tracks
 * queued from surfaces without explicit duration text (like Home shelf cards)
 * still carry an authoritative duration from millisecond zero, enabling instant
 * lossless source substitution.
 */
object KnownTrackDurations {
    private val durations = ConcurrentHashMap<String, Int>()

    fun get(videoId: String): Int? = durations[videoId]

    fun put(videoId: String, durationSec: Int) {
        if (durationSec > 0 && videoId.isNotBlank()) {
            durations[videoId] = durationSec
        }
    }
}
