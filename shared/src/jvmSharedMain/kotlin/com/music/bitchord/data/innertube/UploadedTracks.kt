package com.music.bitchord.data.innertube

import java.util.concurrent.ConcurrentHashMap

/** Video ids known to be the signed-in user's own uploads, which need an upload-hinted request. */
object UploadedTracks {

    private const val MAX_REMEMBERED = 20_000

    private val ids = ConcurrentHashMap.newKeySet<String>()

    fun mark(videoId: String) {
        if (videoId.isBlank()) return
        if (ids.size >= MAX_REMEMBERED) ids.clear()
        ids.add(videoId)
    }

    fun contains(videoId: String): Boolean = videoId in ids

    /** Forgets everything — an account switch is a different private library. */
    fun clear() {
        ids.clear()
    }
}
