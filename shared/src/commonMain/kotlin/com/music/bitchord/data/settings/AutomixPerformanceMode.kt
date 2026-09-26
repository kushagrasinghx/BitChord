package com.music.bitchord.data.settings

/** CPU budget for Automix's background analysis, not its audible mix algorithm. */
enum class AutomixPerformanceMode(val inferenceThreads: Int) {
    EFFICIENT(1),
    BALANCED(2),
    PERFORMANCE(4),
    ;

    /** Whether analysis should yield to playback rather than compete with it. */
    val yieldsToPlayback: Boolean get() = this == EFFICIENT
}
