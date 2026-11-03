package com.music.bitchord.playback

import androidx.media3.common.Player

/** Number of completed queue entries retained behind the current song. */
internal const val MAX_QUEUE_HISTORY = 25

/** How many entries at the head of the live queue are older than its history window. */
internal fun queueHistoryTrimCount(currentIndex: Int): Int =
    (currentIndex - MAX_QUEUE_HISTORY).coerceAtLeast(0)

/**
 * Sends the [count] oldest entries to the end of the queue, order intact.
 *
 * One call, however many entries move. Every playlist edit makes Media3 publish
 * the whole queue to the system, and moving them singly did that once per entry:
 * tapping row 400 of a 754-song queue on repeat-all published all 754 songs 375
 * times, faster than Bluetooth and System UI could read them. The read failing
 * partway is what took com.android.bluetooth down with it.
 */
internal fun Player.rotateExpiredHistory(count: Int) {
    if (count > 0) moveMediaItems(0, count, mediaItemCount - count)
}

/**
 * Entries bypassed when a listener chooses a later row directly from the queue.
 *
 * The current song is deliberately not included: it has actually been current,
 * while the rows between it and [targetIndex] have never played. Backward jumps
 * remove nothing, so revisiting history leaves the songs after it in the queue.
 */
internal fun skippedByQueueJump(currentIndex: Int, targetIndex: Int): IntRange? =
    if (targetIndex > currentIndex + 1) (currentIndex + 1) until targetIndex else null

/**
 * Starts a new queue at the row the listener selected.
 *
 * Rows before it have not played in this session, so putting them behind the
 * current item would make the player present them as history. They can still be
 * reached by starting a new queue from one of those rows later.
 */
internal fun <T> queueStartingAt(items: List<T>, startIndex: Int): List<T> {
    if (items.isEmpty()) return emptyList()
    return items.drop(startIndex.coerceIn(items.indices))
}
