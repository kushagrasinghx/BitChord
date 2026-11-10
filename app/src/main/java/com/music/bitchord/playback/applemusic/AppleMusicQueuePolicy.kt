package com.music.bitchord.playback.applemusic

import com.music.bitchord.data.model.Song
import kotlin.math.abs

/** Apple-only rows have no YouTube id. Never send their catalog marker to Innertube. */
internal object AppleMusicQueuePolicy {
    private val youtubeId = Regex("[A-Za-z0-9_-]{11}")

    fun recommendationSeed(current: Song, queue: List<Song>, currentIndex: Int): Song? {
        if (!AppleMusicSpeaker.isAppleRow(current.videoId)) return current
        return queue.indices.sortedBy { abs(it - currentIndex) }
            .firstOrNull { youtubeId.matches(queue[it].videoId) }
            ?.let(queue::get)
    }
}
