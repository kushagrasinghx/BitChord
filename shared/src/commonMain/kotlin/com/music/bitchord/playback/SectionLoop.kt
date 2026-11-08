package com.music.bitchord.playback

/**
 * An immutable representation of an A-B section loop.
 *
 * Playback cycles from [startMs] to [endMs] until cleared or track changes.
 *
 * @property startMs Point A (loop start) in milliseconds, non-negative.
 * @property endMs Point B (loop end) in milliseconds, strictly greater than [startMs].
 */
data class SectionLoop(
    val startMs: Long,
    val endMs: Long,
) {
    init {
        require(startMs >= 0L) { "startMs must be non-negative: $startMs" }
        require(endMs > startMs) { "endMs ($endMs) must be strictly greater than startMs ($startMs)" }
        require(endMs - startMs >= MIN_GAP_MS) {
            "Loop range must be at least ${MIN_GAP_MS}ms, but was ${endMs - startMs}ms"
        }
    }

    val lengthMs: Long get() = endMs - startMs

    operator fun contains(positionMs: Long): Boolean = positionMs in startMs..endMs

    companion object {
        /** Minimum allowed duration of a section loop to avoid micro-stutter loops. */
        const val MIN_GAP_MS: Long = 500L

        /**
         * Validates, clamps, and creates a [SectionLoop] instance.
         *
         * - Clamps [startMs] to >= 0L.
         * - Clamps [endMs] to [durationMs] if a positive duration is provided.
         * - Enforces A < B and (end - start) >= [MIN_GAP_MS].
         *
         * Returns null if parameters cannot form a valid loop.
         */
        fun from(
            startMs: Long,
            endMs: Long,
            durationMs: Long? = null,
        ): SectionLoop? {
            val validStart = startMs.coerceAtLeast(0L)
            var validEnd = endMs
            if (durationMs != null && durationMs > 0L) {
                if (validStart >= durationMs) return null
                validEnd = validEnd.coerceAtMost(durationMs)
            }
            if (validEnd <= validStart || validEnd - validStart < MIN_GAP_MS) {
                return null
            }
            return SectionLoop(startMs = validStart, endMs = validEnd)
        }
    }
}

/**
 * State of the A-B loop setting flow.
 */
sealed interface SectionLoopState {
    /** No section loop is set or active. */
    data object Idle : SectionLoopState

    /** Setting mode is open; waiting for listener to mark Point A. */
    data object WaitingForA : SectionLoopState

    /** Point A has been marked at [startMs]; waiting for point B. */
    data class SettingA(val startMs: Long) : SectionLoopState

    /** Both points are set and the section loop is active. */
    data class Active(val loop: SectionLoop) : SectionLoopState
}

/**
 * State machine managing the lifecycle of an A-B section loop.
 *
 * Supported transitions:
 * - Setting Point A ([setPointA]): transitions [SectionLoopState.Idle] or [SectionLoopState.Active] to [SectionLoopState.SettingA].
 * - Setting Point B ([setPointB]): transitions [SectionLoopState.SettingA] to [SectionLoopState.Active] if valid.
 * - Direct range setting ([setRange]): directly sets [SectionLoopState.Active] (used e.g. for lyric line selections or commands).
 * - Clearing ([clear]): transitions to [SectionLoopState.Idle].
 * - Track change ([onTrackChanged]): transitions to [SectionLoopState.Idle].
 */
class SectionLoopStateMachine(
    initialState: SectionLoopState = SectionLoopState.Idle,
) {
    var state: SectionLoopState = initialState
        private set

    val activeLoop: SectionLoop? get() = (state as? SectionLoopState.Active)?.loop
    val isLoopActive: Boolean get() = state is SectionLoopState.Active

    /**
     * Begins the loop setting flow by transitioning to [SectionLoopState.WaitingForA].
     */
    fun beginSetting(): Boolean {
        state = SectionLoopState.WaitingForA
        return true
    }

    /**
     * Marks Point A at [positionMs].
     * Clamps position to >= 0L. If [durationMs] is provided and position >= duration, returns false.
     */
    fun setPointA(positionMs: Long, durationMs: Long? = null): Boolean {
        val validStart = positionMs.coerceAtLeast(0L)
        if (durationMs != null && durationMs > 0L && validStart >= durationMs) {
            return false
        }
        state = SectionLoopState.SettingA(startMs = validStart)
        return true
    }

    /**
     * Marks Point B at [positionMs].
     *
     * If currently in [SectionLoopState.SettingA], attempts to create a loop from Point A to [positionMs].
     * If currently in [SectionLoopState.Active], updates Point B while keeping existing Point A.
     * Returns true if a valid loop was formed and activated, false otherwise.
     */
    fun setPointB(positionMs: Long, durationMs: Long? = null): Boolean {
        val startMs = when (val current = state) {
            is SectionLoopState.SettingA -> current.startMs
            is SectionLoopState.Active -> current.loop.startMs
            is SectionLoopState.WaitingForA,
            is SectionLoopState.Idle -> return false
        }
        val loop = SectionLoop.from(startMs = startMs, endMs = positionMs, durationMs = durationMs)
        return if (loop != null) {
            state = SectionLoopState.Active(loop)
            true
        } else {
            false
        }
    }

    /**
     * Directly activates a section loop from [startMs] to [endMs].
     * Returns true if range is valid and activated, false otherwise.
     */
    fun setRange(startMs: Long, endMs: Long, durationMs: Long? = null): Boolean {
        val loop = SectionLoop.from(startMs = startMs, endMs = endMs, durationMs = durationMs)
        return if (loop != null) {
            state = SectionLoopState.Active(loop)
            true
        } else {
            false
        }
    }

    /** Clears any active loop or in-progress setting state, resetting to [SectionLoopState.Idle]. */
    fun clear() {
        state = SectionLoopState.Idle
    }

    /** Resets loop state when the playing track changes. */
    fun onTrackChanged() {
        clear()
    }
}

/**
 * Pure evaluation function deciding whether playback should wrap back to loop start.
 *
 * @param positionMs Current playhead position in milliseconds.
 * @param loop The active [SectionLoop], or null if no loop is active.
 * @param isPlaying Whether playback is currently active (not paused or stopped).
 * @return Target seek position in milliseconds ([loop.startMs]) if wrap should occur, or null otherwise.
 */
fun evaluateLoopWrap(
    positionMs: Long,
    loop: SectionLoop?,
    isPlaying: Boolean = true,
): Long? {
    if (loop == null || !isPlaying) return null
    return if (positionMs >= loop.endMs) loop.startMs else null
}
