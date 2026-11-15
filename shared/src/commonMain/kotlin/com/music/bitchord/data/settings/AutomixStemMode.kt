package com.music.bitchord.data.settings

/** Stem splitting mode for AutoMix transitions. */
enum class AutomixStemMode {
    /** Disabled: Standard AutoMix EQ sweeps and volume fades. */
    OFF,

    /** Fast 2-stem separation: Isolates vocals and instrumental backing. */
    VOCALS_INSTRUMENTAL,

    /** High-quality 4-stem separation: Separates vocals, drums, bass, and other instruments. */
    FOUR_STEM,
    ;

    val isEnabled: Boolean get() = this != OFF
}
