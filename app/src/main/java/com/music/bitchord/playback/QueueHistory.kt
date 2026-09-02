package com.music.bitchord.playback

/** Number of completed queue entries retained behind the current song. */
internal const val MAX_QUEUE_HISTORY = 25

/** How many entries at the head of the live queue are older than its history window. */
internal fun queueHistoryTrimCount(currentIndex: Int): Int =
    (currentIndex - MAX_QUEUE_HISTORY).coerceAtLeast(0)

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
