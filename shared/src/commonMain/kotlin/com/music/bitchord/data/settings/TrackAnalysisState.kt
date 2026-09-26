package com.music.bitchord.data.settings

/*
 * Shared between the Android and desktop players: both run the same analyser
 * and both report the same states from it, so the vocabulary lives in one
 * place. The package is unchanged, so neither side needed a new import.
 */

/** Where one track stands in Automix's analysis. */
enum class TrackAnalysisState {
    /** Nothing in flight and no result — usually waiting on bytes to arrive. */
    WAITING,

    /** Decode and inference running now; a result is a few seconds away. */
    ANALYSING,

    /** Measured, with a tempo the planner can actually use. */
    ANALYSED,

    /**
     * Measured off the track's opening, with the whole-track pass running now to replace those
     * numbers with better ones.
     */
    REFINING,

    /** Tried and came back with nothing usable — a decode error, or audio that yielded no tempo. */
    FAILED,
}

/** Both sides of the next transition, for stats for nerds. */
data class SmartAnalysis(
    val current: TrackAnalysisState = TrackAnalysisState.WAITING,
    val next: TrackAnalysisState = TrackAnalysisState.WAITING,
)

/**
 * A span of the playing track, in fractions of its duration, that the next transition is planned to
 * occupy.
 */
data class TransitionWindow(val start: Float, val end: Float)
