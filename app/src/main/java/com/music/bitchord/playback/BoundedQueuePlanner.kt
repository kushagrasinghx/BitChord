package com.music.bitchord.playback

import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song

data class BoundedContextPlan(
    val canonicalContext: List<Song>,
    val currentCanonicalIndex: Int,
    val history: List<Song>,
    val current: Song,
    val userQueue: List<Song>,
    val contextUpcoming: List<QueueWindowEntry>,
    val autoplay: List<Song>,
    val contextTrulyExhausted: Boolean,
    val continuationPending: Boolean,
    val continuationState: ContinuationState,
    val repeatAll: Boolean,
) {
    fun materializedSongs(): List<Song> = buildList(
        history.size + 1 + userQueue.size + contextUpcoming.size + autoplay.size,
    ) {
        addAll(history)
        add(current)
        addAll(userQueue)
        contextUpcoming.forEach { entry ->
            canonicalContext.getOrNull(entry.canonicalIndex)?.let { song ->
                add(if (song.queueTier == QueueTier.CONTEXT) song else song.copy(queueTier = QueueTier.CONTEXT))
            }
        }
        addAll(autoplay)
    }
}

object BoundedQueuePlanner {
    /** The most context rows a queue needs in the player: history, current, look-ahead. */
    const val MAX_PHYSICAL_CONTEXT = MAX_QUEUE_HISTORY + 1 + WINDOW_AHEAD

    fun buildInitialPlan(
        contextSnapshot: ContextSnapshot,
        selectedIndex: Int,
        queueWindow: QueueWindow,
        preservedUserQueue: List<Song> = emptyList(),
        existingAutoplay: List<Song> = emptyList(),
        repeatAll: Boolean = false,
    ): BoundedContextPlan {
        val canonical = contextSnapshot.songs
        require(canonical.isNotEmpty()) { "contextSnapshot must contain songs" }
        require(selectedIndex in canonical.indices) {
            "selectedIndex $selectedIndex is outside canonicalContext"
        }
        require(queueWindow.canonicalSize == canonical.size) {
            "QueueWindow and contextSnapshot must have the same size"
        }
        require(queueWindow.currentCanonicalIndex == selectedIndex) {
            "QueueWindow must be started at selectedIndex"
        }

        val state = contextSnapshot.continuationState
        val upcoming = queueWindow.upcoming(
            WINDOW_AHEAD,
            // A fresh manual start must not expose the next repeat cycle before
            // the currently selected cycle has actually finished. The top-up
            // path may materialise that cycle later at the physical boundary.
            repeatAll = false,
        )
        val history = historyBefore(canonical, queueWindow)
        return BoundedContextPlan(
            canonicalContext = canonical.toList(),
            currentCanonicalIndex = selectedIndex,
            history = history,
            current = canonical[selectedIndex].asContext(),
            userQueue = preservedUserQueue.map { it.asUserQueue() },
            contextUpcoming = upcoming,
            autoplay = existingAutoplay.map { it.asAutoplay() },
            contextTrulyExhausted = upcoming.isEmpty() && state.isTerminal() && !repeatAll,
            continuationPending = state.isPending(),
            continuationState = state,
            repeatAll = repeatAll,
        )
    }

    /**
     * Return only the suffix needed to refill [materialized] to the logical
     * window. A physical queue is eligible for mutation only below low water;
     * a stale or reordered prefix is left for the caller's explicit refresh.
     */
    fun topUpEntries(
        window: QueueWindow,
        materialized: List<QueueWindowEntry>,
        repeatAll: Boolean,
    ): List<QueueWindowEntry> {
        if (materialized.size >= LOW_WATER) return emptyList()
        val desired = physicalUpcoming(window, repeatAll)
        if (materialized.size > desired.size) return emptyList()
        if (materialized.indices.any { !sameEntry(materialized[it], desired[it]) }) {
            return emptyList()
        }
        return desired.drop(materialized.size)
    }

    fun autoplayEligible(
        window: QueueWindow,
        continuationState: ContinuationState,
        repeatAll: Boolean,
    ): Boolean = !repeatAll &&
        window.upcoming(1, repeatAll = false).isEmpty() &&
        continuationState.isTerminal()

    /** Repeat-all may cross a cycle boundary only after continuation is terminal. */
    fun canRepeat(continuationState: ContinuationState): Boolean = continuationState.isTerminal()

    /**
     * Keep the physical queue on the active logical cycle until its last entry
     * is current. Logical look-ahead may inspect a later cycle, but exposing it
     * in Media3 before this point makes the queue UI show duplicate songs.
     */
    fun shouldMaterializeNextCycle(window: QueueWindow, repeatAll: Boolean): Boolean =
        repeatAll && window.upcoming(1, repeatAll = false).isEmpty()

    /**
     * Return a physical look-ahead containing the active cycle and, at the
     * boundary, at most one following cycle. QueueWindow can logically look
     * through several repeat cycles to fill [WINDOW_AHEAD]; doing that in the
     * player would make a small album appear duplicated as soon as Repeat All
     * rolls over.
     */
    fun physicalUpcoming(
        window: QueueWindow,
        repeatAll: Boolean,
        count: Int = WINDOW_AHEAD,
    ): List<QueueWindowEntry> {
        if (count <= 0) return emptyList()
        val active = window.upcoming(count, repeatAll = false)
        if (active.size >= count || !shouldMaterializeNextCycle(window, repeatAll)) {
            return active
        }
        val nextCycle = window.cycleId.takeIf { it < Long.MAX_VALUE }?.plus(1L)
            ?: return active
        val next = window.upcoming(count - active.size, repeatAll = true)
            .filter { it.cycleId == nextCycle }
        return active + next
    }

    private fun historyBefore(canonical: List<Song>, window: QueueWindow): List<Song> {
        val cursor = window.cursor
        val first = (cursor - MAX_QUEUE_HISTORY).coerceAtLeast(0)
        return window.order.subList(first, cursor).map { canonical[it].asContext() }
    }

    private fun sameEntry(left: QueueWindowEntry, right: QueueWindowEntry): Boolean =
        left.canonicalIndex == right.canonicalIndex && left.cycleId == right.cycleId

    private fun Song.asContext(): Song =
        if (queueTier == QueueTier.CONTEXT) this else copy(queueTier = QueueTier.CONTEXT)

    private fun Song.asUserQueue(): Song =
        if (queueTier == QueueTier.USER_QUEUE) this else copy(queueTier = QueueTier.USER_QUEUE)

    private fun Song.asAutoplay(): Song =
        if (queueTier == QueueTier.AUTOPLAY) this else copy(queueTier = QueueTier.AUTOPLAY)

    private fun ContinuationState.isPending(): Boolean =
        this == ContinuationState.AVAILABLE || this == ContinuationState.IN_FLIGHT

    private fun ContinuationState.isTerminal(): Boolean =
        this == ContinuationState.EXHAUSTED || this == ContinuationState.BLOCKED
}
