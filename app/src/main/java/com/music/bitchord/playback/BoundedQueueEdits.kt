package com.music.bitchord.playback

/**
 * Pending logical edits for a bounded context queue.
 *
 * The service can keep one value of this type while a crossfade is active.  A
 * request only changes the state when its [contextGeneration] is still current,
 * so a continuation callback or a UI event from an old context cannot affect a
 * newly installed queue.
 */
data class BoundedQueueEditState(
    val contextGeneration: Long = 0L,
    val desiredShuffle: Boolean? = null,
    val refreshPending: Boolean = false,
    val topUpPending: Boolean = false,
) {
    val hasPendingEdits: Boolean
        get() = desiredShuffle != null || refreshPending || topUpPending
}

/** The coalesced work a settled queue may apply once. */
data class BoundedQueueEdit(
    val contextGeneration: Long,
    val desiredShuffle: Boolean?,
    val refresh: Boolean,
    val topUp: Boolean,
)

/** Result of an explicit settlement attempt. */
data class BoundedQueueSettlement(
    val state: BoundedQueueEditState,
    val edit: BoundedQueueEdit?,
) {
    val drained: Boolean
        get() = edit != null
}

/**
 * Pure coalescing and settlement policy for bounded queue edits.
 *
 * Callers should use [defer] whenever a transition is unsafe, and call
 * [settle] from the explicit settled callback with the generation captured by
 * that callback.  Deferring never consumes pending work; settling consumes it
 * only when the callback still belongs to the current context generation.
 */
object BoundedQueueEdits {

    fun requestShuffle(
        state: BoundedQueueEditState,
        desiredShuffle: Boolean,
        contextGeneration: Long = state.contextGeneration,
    ): BoundedQueueEditState = state.accepts(contextGeneration).let { accepted ->
        if (!accepted) state else state.copy(desiredShuffle = desiredShuffle)
    }

    fun requestRefresh(
        state: BoundedQueueEditState,
        contextGeneration: Long = state.contextGeneration,
    ): BoundedQueueEditState = state.accepts(contextGeneration).let { accepted ->
        if (!accepted) state else state.copy(refreshPending = true)
    }

    fun requestTopUp(
        state: BoundedQueueEditState,
        contextGeneration: Long = state.contextGeneration,
    ): BoundedQueueEditState = state.accepts(contextGeneration).let { accepted ->
        if (!accepted) state else state.copy(topUpPending = true)
    }

    /**
     * Invalidate all work belonging to the old context and advance its token.
     * The returned state is empty, so no old refresh, top-up, or shuffle can
     * drain after the new context is installed.
     */
    fun invalidateContext(state: BoundedQueueEditState): BoundedQueueEditState {
        check(state.contextGeneration < Long.MAX_VALUE) { "context generation exhausted" }
        return BoundedQueueEditState(contextGeneration = state.contextGeneration + 1L)
    }

    /** An unsafe transition leaves every pending request intact. */
    fun defer(state: BoundedQueueEditState): BoundedQueueSettlement =
        BoundedQueueSettlement(state = state, edit = null)

    /**
     * Drain pending work at an explicit safe settlement.
     *
     * A callback for an older context is deliberately a no-op.  Its pending
     * work remains available for the current context's own settlement.
     */
    fun settle(
        state: BoundedQueueEditState,
        settledGeneration: Long,
    ): BoundedQueueSettlement {
        if (settledGeneration != state.contextGeneration || !state.hasPendingEdits) {
            return BoundedQueueSettlement(state = state, edit = null)
        }

        return BoundedQueueSettlement(
            state = state.copy(
                desiredShuffle = null,
                refreshPending = false,
                topUpPending = false,
            ),
            edit = BoundedQueueEdit(
                contextGeneration = state.contextGeneration,
                desiredShuffle = state.desiredShuffle,
                refresh = state.refreshPending,
                topUp = state.topUpPending,
            ),
        )
    }

    private fun BoundedQueueEditState.accepts(contextGeneration: Long): Boolean =
        contextGeneration == this.contextGeneration
}
