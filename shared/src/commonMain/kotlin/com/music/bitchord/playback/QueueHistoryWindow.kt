package com.music.bitchord.playback

/*
 * How much of a queue is kept behind and ahead of the current song. Pure, so
 * the web player keeps the same history and restart snapshot as the phone.
 * Moved from the app's QueueHistory.kt and LastPlayed.kt, which still use them.
 */

/** Number of completed queue entries retained behind the current song. */
const val MAX_QUEUE_HISTORY = 25

/** How many entries at the head of the live queue are older than its history window. */
fun queueHistoryTrimCount(currentIndex: Int): Int =
    (currentIndex - MAX_QUEUE_HISTORY).coerceAtLeast(0)

/** Upcoming entries a restart snapshot keeps after the current one. */
const val LAST_PLAYED_KEEP_AHEAD = 50

/** The part of a [size]-entry queue a restart snapshot keeps: 25 previous entries, the current one, 50 upcoming. */
fun lastPlayedWindow(size: Int, index: Int): IntRange {
    if (size <= 0) return IntRange.EMPTY
    val safeIndex = index.coerceIn(0, size - 1)
    val start = (safeIndex - MAX_QUEUE_HISTORY).coerceAtLeast(0)
    return start until (safeIndex + 1 + LAST_PLAYED_KEEP_AHEAD).coerceAtMost(size)
}
