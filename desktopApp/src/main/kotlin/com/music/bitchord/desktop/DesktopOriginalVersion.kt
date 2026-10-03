package com.music.bitchord.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.ConcurrentHashMap

/** The tracks the listener has sent back to YouTube's own upload by hand, and wants kept there. */
internal object DesktopOriginalVersion {

    /** Bumped whenever the set changes, and read by [isPinned]. */
    private var revision by mutableStateOf(0)

    private val pinned = ConcurrentHashMap.newKeySet<String>().apply {
        addAll(DesktopPersistence().originalVersionIds())
    }

    fun isPinned(videoId: String): Boolean {
        @Suppress("UNUSED_EXPRESSION") revision
        return videoId.isNotBlank() && videoId in pinned
    }

    /** Keeps [videoId] on YouTube's own upload until it is cleared. */
    fun pin(videoId: String) {
        if (videoId.isBlank() || !pinned.add(videoId)) return
        revision++
        persist()
    }

    /** The way back out — the listener asking for the better copy again. */
    fun clear(videoId: String) {
        if (!pinned.remove(videoId)) return
        revision++
        persist()
    }

    private fun persist() = DesktopPersistence().saveOriginalVersionIds(pinned.toSet())
}
