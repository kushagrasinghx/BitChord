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

/**
 * An Automix blend in flight, as the scrubber draws it: the beat it is running
 * on, and whether it is moving. Only republished when one of those changes.
 */
data class MixBlend(
    /**
     * Wall-clock length of one beat at the tempo the blend is actually playing
     * — after any beatmatch stretch and the listener's own speed — or 0 when
     * neither track's tempo is known.
     */
    val beatMs: Float,
    /** A `System.nanoTime()` at which a beat landed; with [beatMs], places every other one. */
    val beatAnchorNanos: Long,
    /** Whether the blend is playing rather than paused; a paused blend holds still. */
    val playing: Boolean,
)

/**
 * One side of an Automix blend as the mix popup draws it: the track's card and
 * a three-band waveform of the stretch of it around the blend.
 */
class MixDeck(
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    /** The analysed tempo, or 0 when there is none. */
    val bpm: Double,
    /** As the analyzer names it — "A♭ minor" — or blank. */
    val key: String,
    val durationMs: Long,
    /**
     * The span of this track's own timeline, in seconds, the waveform covers. Usually wider
     * than what the popup shows: it is decoded ahead of the blend, padded either side.
     */
    val waveStart: Double,
    val waveEnd: Double,
    /**
     * Peak level per band, 0..1, in evenly spaced bins over [waveStart]..[waveEnd]: below
     * ~200 Hz, the middle, and above ~2.5 kHz. Empty until the region has been decoded.
     */
    val low: FloatArray = FloatArray(0),
    val mid: FloatArray = FloatArray(0),
    val high: FloatArray = FloatArray(0),
) {
    fun withWave(low: FloatArray, mid: FloatArray, high: FloatArray, start: Double, end: Double) = MixDeck(
        title, artist, artworkUrl, bpm, key, durationMs, start, end, low, mid, high,
    )
}

/**
 * An Automix blend laid out for the mix popup. Times are in each track's own
 * seconds; the blend runs [outStart]..[outEnd] on the outgoing track while the
 * incoming one covers [inStart]..[inEnd].
 */
class MixTransitionView(
    val outgoing: MixDeck,
    val incoming: MixDeck,
    val outStart: Double,
    val outEnd: Double,
    val inStart: Double,
    val inEnd: Double,
    /** The overlap's length in beats of the outgoing grid; 0 when the grid is not trusted. */
    val beats: Int,
    /** Beats inside the blend, as fractions 0..1 of it, for the grid lines. */
    val beatMarks: FloatArray,
    /** The faders the blend actually rides, sampled evenly over its progress, 0..1. */
    val fadeOut: FloatArray,
    val fadeIn: FloatArray,
    /** Where in the blend, 0..1, the low end changes hands; negative when it does not. */
    val bassSwapAt: Float = -1f,
) {
    fun withDecks(outgoing: MixDeck, incoming: MixDeck) = MixTransitionView(
        outgoing, incoming, outStart, outEnd, inStart, inEnd, beats, beatMarks, fadeOut, fadeIn, bassSwapAt,
    )
}
