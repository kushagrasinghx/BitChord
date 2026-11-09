package com.music.bitchord.playback.cast

/**
 * Which of the phone's queued tracks the receiver is given to hold beside the
 * one it is playing: the one before it, and [ahead] after it.
 *
 * Deliberately plain functions over plain lists. Every decision the receiver's
 * queue depends on — which of the phone's tracks a receiver could open at all,
 * which of them belong beside the playing one — is a question about a list and a
 * predicate, not about a session or a network, and can be asked and checked
 * without either. The parts that do need a session are the ones that move the
 * queue around; deciding what should be in it is here, and says so about no type
 * in particular.
 */

/**
 * The nearest index before [current] that [isUsable] accepts, or null at the top
 * of the list and when everything before it is unusable.
 *
 * Stepping backwards over unusable tracks, like [nextUsableIndex] does forwards,
 * is what makes back on a receiver a jump to something it can actually play
 * rather than a reload of a track it must refuse.
 */
internal fun <T> previousUsableIndex(items: List<T>, current: Int, isUsable: (T) -> Boolean): Int? {
    for (i in (current - 1) downTo 0) {
        if (isUsable(items[i])) return i
    }
    return null
}

/**
 * The track before [current], if there is one and [isUsable] accepts it. Null at
 * the top of the list, and when everything before it is unusable.
 */
internal fun <T> windowBefore(items: List<T>, current: Int, isUsable: (T) -> Boolean): T? =
    previousUsableIndex(items, current, isUsable)?.let { items[it] }

/**
 * The index of the first track after [current] that [isUsable] accepts, or null
 * when there is nothing left a receiver could play.
 *
 * The difference between a skip and the end of the session: one moves the phone
 * to the next track the receiver can open, the other says there is no such
 * track. Both are only questions about the list, and the answer decides which
 * of two very different things happens next — a new load, or a receiver told to
 * stop.
 */
internal fun <T> nextUsableIndex(items: List<T>, current: Int, isUsable: (T) -> Boolean): Int? {
    for (i in (current + 1) until items.size) {
        if (isUsable(items[i])) return i
    }
    return null
}

/**
 * The [ahead] items after [current] that [isUsable] accepts.
 *
 * Skipping the ones it rejects is what keeps the window full: counting positions
 * instead spends the whole window on a few items the receiver cannot open and
 * leaves it with nothing to play next. A queue with unusable tracks between two
 * usable ones gives the same window as a queue that is entirely usable.
 */
internal fun <T> windowAhead(items: List<T>, current: Int, ahead: Int, isUsable: (T) -> Boolean): List<T> {
    if (ahead <= 0) return emptyList()
    val out = ArrayList<T>(ahead)
    var i = current + 1
    while (i < items.size && out.size < ahead) {
        val item = items[i]
        if (isUsable(item)) out.add(item)
        i++
    }
    return out
}
