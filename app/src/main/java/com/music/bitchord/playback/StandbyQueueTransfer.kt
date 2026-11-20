/*
 * Copyright (C) 2026 BitChord Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package com.music.bitchord.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Prepares the standby player for a transition.
 *
 * The retired standby often still holds the same canonical queue as the active
 * player. When every item matches, seeking the existing timeline avoids a full
 * queue copy and Media3 timeline rebuild. If anything differs, the safe path is
 * still a complete, ordered transfer — never a truncated window.
 */
internal object StandbyQueueTransfer {

    /** True when [into] already contains exactly the same ordered queue as [out]. */
    fun canReuse(out: Player, into: Player): Boolean {
        val count = out.mediaItemCount
        if (count == 0 || count != into.mediaItemCount) return false
        for (i in 0 until count) {
            val a = out.getMediaItemAt(i)
            val b = into.getMediaItemAt(i)
            if (a.mediaId != b.mediaId) return false
            if (a.queueEntryId != b.queueEntryId) return false
            if (a.queueTier != b.queueTier) return false
        }
        return true
    }

    /**
     * Positions [into] at [nextIndex]/[cueMs]. Returns true when the existing
     * standby timeline was reused; false when a full transfer was required.
     */
    fun prepare(out: ExoPlayer, into: ExoPlayer, nextIndex: Int, cueMs: Long): Boolean {
        if (nextIndex !in 0 until out.mediaItemCount) return false
        if (canReuse(out, into)) {
            into.seekTo(nextIndex, cueMs)
            return true
        }
        val items = ArrayList<MediaItem>(out.mediaItemCount)
        for (i in 0 until out.mediaItemCount) items.add(out.getMediaItemAt(i))
        into.setMediaItems(items, nextIndex, cueMs)
        return false
    }
}
