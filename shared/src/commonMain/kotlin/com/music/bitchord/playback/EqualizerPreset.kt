package com.music.bitchord.playback

/**
 * The manual tab's starting points, one gain per centre in
 * [EqLayout.MANUAL_BANDS_HZ].
 *
 * Written for this equaliser rather than copied from anyone else's: the seven
 * centres here are not the ten a desktop EQ uses, and the two ends are shelves
 * rather than bells ([FilterKind]), so a borrowed table would not describe the
 * same curve even with the numbers transcribed correctly. They are deliberately
 * modest — nothing reaches the ±12 dB the sliders allow, because a preset is a
 * place to start from and every decibel of boost is a decibel of headroom
 * [EqCurve.preampDb] has to take back.
 *
 * [CUSTOM] carries no gains. It is what the tab reports once a slider has been
 * moved, so the row stops naming a preset the bands no longer match.
 */
enum class EqualizerPreset(vararg val bandsDb: Float) {
    FLAT(0f, 0f, 0f, 0f, 0f, 0f, 0f),
    ACOUSTIC(3f, 1.5f, 0f, 1.5f, 2.5f, 2f, 1f),
    BASS_BOOST(6f, 4f, 1.5f, 0f, 0f, 0f, 0f),
    BASS_CUT(-6f, -4f, -1.5f, 0f, 0f, 0f, 0f),
    VOCAL(-3f, -1.5f, 1f, 3.5f, 3f, 1f, -1f),
    TREBLE_BOOST(0f, 0f, 0f, 0f, 1.5f, 3.5f, 5f),
    TREBLE_CUT(0f, 0f, 0f, 0f, -1.5f, -3.5f, -5f),
    LOUDNESS(6f, 3.5f, 0f, -1.5f, -1f, 2f, 5f),
    SPOKEN_WORD(-5f, -2.5f, 1.5f, 4f, 3.5f, 1.5f, -2f),
    ELECTRONIC(5f, 3f, -1f, 0f, 1f, 3f, 4f),
    ROCK(4f, 2.5f, -1f, -1.5f, 1f, 3f, 3.5f),
    HIP_HOP(6f, 4f, 0.5f, -1f, 0.5f, 2f, 2.5f),
    JAZZ(3f, 1.5f, 0f, 1f, 1.5f, 2f, 2.5f),
    CLASSICAL(3f, 2f, 0f, 0f, 1f, 2.5f, 3f),
    SMALL_SPEAKERS(5f, 4f, 2f, 0.5f, 0f, -1f, -2f),
    LATE_NIGHT(3f, 1f, 0f, 1.5f, 1f, -1f, -3f),
    CUSTOM,
    ;

    val bands: List<Float> get() = bandsDb.toList()

    companion object {
        /**
         * The preset whose curve these bands are, or [CUSTOM] if they are
         * nobody's. Lets a restored backup name what it restored, and lets a
         * slider dragged back to where it started stop saying "Custom".
         */
        fun matching(bandsDb: List<Float>): EqualizerPreset = entries.firstOrNull { preset ->
            preset != CUSTOM && preset.bandsDb.size == bandsDb.size &&
                preset.bandsDb.indices.all { kotlin.math.abs(preset.bandsDb[it] - bandsDb[it]) < 0.05f }
        } ?: CUSTOM
    }
}
