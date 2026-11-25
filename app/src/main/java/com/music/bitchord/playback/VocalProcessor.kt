package com.music.bitchord.playback

import com.music.bitchord.playback.audio.FloatAudioProcessor

/**
 * The Karaoke vocal stage of the playback DSP chain.
 *
 * Narrow on purpose: the service wiring and any UI talk only in terms of a
 * master switch and a vocal level, so the backend can later be replaced — by
 * a stem-separation model, for instance — without anything above this
 * interface being redesigned. Implementation detail stays below it: nothing
 * here names filter topologies, models or stem caches.
 *
 * `vocalLevel` is 0..1, where 0 is maximum reduction and 1 is the original
 * mix. The internal suppression is `1 - vocalLevel` while enabled and 0 while
 * disabled, so a first activation at the default level is immediately and
 * audibly reducing, and a level of 1 glides back to the original mix.
 *
 * Implementations follow [FloatAudioProcessor]: configured per format,
 * in-place on the shared [com.music.bitchord.playback.audio.AudioBlock],
 * allocation-free in steady state, and exact passthrough once disabled and
 * settled — the same rules every stage of
 * [com.music.bitchord.playback.audio.DspChain] keeps.
 */
interface VocalProcessor : FloatAudioProcessor {

    /** Whether the last processed block was actually altered. */
    val isShaping: Boolean

    /**
     * Aims the processor. Called from whoever owns the settings, not from the
     * audio thread; nothing here is read until the next sub-block boundary.
     */
    fun setTuning(enabled: Boolean, vocalLevel: Float)
}
