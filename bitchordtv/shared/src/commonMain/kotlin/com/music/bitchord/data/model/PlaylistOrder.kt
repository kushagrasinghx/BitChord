package com.music.bitchord.data.model

/**
 * One step of a playlist reorder: put the entry [setVideoId] directly before
 * [before], or at the very end of the playlist when [before] is null.
 *
 * Expressed in set-video-ids rather than positions because that is the only
 * shape YouTube's `ACTION_MOVE_VIDEO_BEFORE` takes, and because a position
 * means nothing once the first move has shifted everything after it.
 */
data class PlaylistMove(val setVideoId: String, val before: String?)

/**
 * The moves that turn the playlist [current] into [target], both given as
 * set-video-ids, fewest first.
 *
 * Only the entries outside the longest run already in the right relative order
 * move at all, so dragging one track to a new spot is one request entry however
 * far it went — not one per row it passed. The rest are placed walking [target]
 * from its end, each directly before the entry that should follow it, which by
 * then is already where it belongs.
 *
 * Empty when nothing changed, and when the two lists are not the same entries:
 * a playlist that gained or lost a row since [current] was read is not one this
 * can safely rearrange.
 */
fun playlistMoves(current: List<String>, target: List<String>): List<PlaylistMove> {
    if (current == target) return emptyList()
    if (current.size != target.size || current.toSet() != target.toSet() || current.toSet().size != current.size) {
        return emptyList()
    }
    val position = current.withIndex().associate { (index, id) -> id to index }
    val stay = longestIncreasingRun(target.map { position.getValue(it) })
        .mapTo(HashSet()) { target[it] }
    val moves = ArrayList<PlaylistMove>()
    for (index in target.indices.reversed()) {
        val id = target[index]
        if (id in stay) continue
        moves += PlaylistMove(id, target.getOrNull(index + 1))
    }
    return moves
}

/** Indices into [values] of one longest strictly increasing subsequence. */
private fun longestIncreasingRun(values: List<Int>): List<Int> {
    if (values.isEmpty()) return emptyList()
    // tails[k]: index of the smallest tail of an increasing run of length k + 1.
    val tails = IntArray(values.size)
    val previous = IntArray(values.size) { -1 }
    var length = 0
    for (i in values.indices) {
        var low = 0
        var high = length
        while (low < high) {
            val mid = (low + high) ushr 1
            if (values[tails[mid]] < values[i]) low = mid + 1 else high = mid
        }
        if (low > 0) previous[i] = tails[low - 1]
        tails[low] = i
        if (low == length) length++
    }
    val run = ArrayList<Int>(length)
    var at = tails[length - 1]
    while (at >= 0) {
        run += at
        at = previous[at]
    }
    return run.asReversed()
}
