package com.music.bitchord.playback.audio

import android.util.Log
import com.music.bitchord.BuildConfig
import com.music.bitchord.playback.EqualizerProcessor
import com.music.bitchord.playback.LoudnessProcessor
import com.music.bitchord.playback.ReverbProcessor
import com.music.bitchord.playback.SpatialAudioProcessor
import com.music.bitchord.playback.TransitionFilterProcessor

/**
 * Composite DSP chain executing BitChord's custom audio processors in their canonical sequence:
 *
 * AudioBlock(Float32) -> SpatialAudioProcessor -> EqualizerProcessor -> ReverbProcessor
 *   -> TransitionFilterProcessor -> LoudnessProcessor -> AudioBlock(Float32)
 *
 * Reverb follows the equaliser so the room hears the listener's tone, and
 * comes before the transition filter so a handoff's bass swap or sweep takes
 * the tail along with the dry sound instead of leaving it behind; before
 * loudness, too, so the limiter sees whatever energy the tail adds.
 *
 * Operates purely on in-place Float32 audio blocks without intermediate fixed-point quantization,
 * preserving full dynamic range and headroom.
 *
 * Loudness normalization is the last stage, and per sink on purpose: each
 * player levels the track *it* is playing, so the two sides of a crossfade are
 * each levelled for their own song — see [LoudnessProcessor]. Last because its
 * limiter has to see the signal every other stage has finished shaping.
 *
 * ## Staying out of the way
 *
 * Every stage here self-bypasses when its own setting is off, returning before
 * it reads a single sample rather than multiplying through by unity. With every
 * stage idle the block leaves this class byte-identical to how it arrived,
 * which is what lets [PcmBoundary]'s power-of-two scaling round-trip 16- and
 * 24-bit integers unchanged — see `PrecisionAudioSink.publishOutputExactness`.
 */
class DspChain(
    val spatial: SpatialAudioProcessor = SpatialAudioProcessor(),
    val equalizer: EqualizerProcessor = EqualizerProcessor(),
    val transition: TransitionFilterProcessor = TransitionFilterProcessor(),
    val loudness: LoudnessProcessor = LoudnessProcessor(),
    val reverb: ReverbProcessor = ReverbProcessor(),
) : FloatAudioProcessor {

    private var currentSampleRate: Int = 0
    private var processCounter: Long = 0L

    override fun configure(sampleRate: Int, channelCount: Int) {
        this.currentSampleRate = sampleRate
        spatial.configure(sampleRate, channelCount)
        equalizer.configure(sampleRate, channelCount)
        reverb.configure(sampleRate, channelCount)
        transition.configure(sampleRate, channelCount)
        loudness.configure(sampleRate, channelCount)
    }

    /** A new track has begun gaplessly on this sink; see [LoudnessProcessor.onStreamBoundary]. */
    fun onStreamBoundary() {
        loudness.onStreamBoundary()
    }

    override fun process(block: AudioBlock) {
        if (block.frameCount == 0) return

        if (BuildConfig.DEBUG) {
            processCounter++
            if (processCounter == 1L || processCounter % 500L == 0L) {
                try {
                    val count = processCounter
                    val frames = block.frameCount
                    val sr = currentSampleRate
                    val spatialOn = spatial.enabled
                    val eqOn = equalizer.isEnabled
                    val reverbOn = reverb.isActive
                    val transitionOn = transition.isFiltering
                    Log.d(
                        TAG,
                        "process() #$count frames=$frames sr=$sr " +
                            "spatial=$spatialOn eq=$eqOn reverb=$reverbOn transition=$transitionOn",
                    )
                } catch (_: Throwable) {
                }
            }
        }

        spatial.process(block)
        equalizer.process(block)
        reverb.process(block.samples, block.sampleCount)
        transition.process(block)
        loudness.process(block)
    }

    @Suppress("DEPRECATION")
    override fun flush() {
        spatial.flush()
        equalizer.flush()
        reverb.reset()
        transition.flush()
        loudness.flush()
    }

    override fun reset() {
        processCounter = 0L
        spatial.reset()
        equalizer.reset()
        reverb.reset()
        transition.reset()
        loudness.reset()
    }

    companion object {
        private const val TAG = "DspChain"
    }
}
