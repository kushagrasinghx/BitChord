package com.music.bitchord.wear

/**
 * The BitChord watch app as one of this phone's outputs, for the output sheet.
 * Kept apart from WearBridge so the TV build, which replaces that file, still
 * has the type.
 */
data class WatchOutputState(
    /** A watch with BitChord on it is in reach. */
    val available: Boolean = false,
    /** What the watch calls itself — "Galaxy Watch4 Classic (KX4F)". */
    val name: String? = null,
    /** The watch is the speaker. */
    val active: Boolean = false,
    /** The watch's own volume, 0..1, while it is. */
    val volume: Float = 0f,
)
