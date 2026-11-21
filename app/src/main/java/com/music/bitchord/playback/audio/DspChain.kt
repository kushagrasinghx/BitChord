package com.music.bitchord.playback.audio

import android.util.Log
import com.music.bitchord.BuildConfig
import com.music.bitchord.playback.EqualizerProcessor
import com.music.bitchord.playback.LoudnessProcessor
import com.music.bitchord.playback.SpatialAudioProcessor
import com.music.bitchord.playback.TransitionFilterProcessor
import com.music.bitchord.playback.haptics.MusicHapticsProcessor

/**
 * Composite DSP chain executing BitChord's custom audio processors in their canonical sequence:
 *
 * AudioBlock(Float32) -> SpatialAudioProcessor -> EqualizerProcessor
 *   -> TransitionFilterProcessor -> LoudnessProcessor -> MusicHapticsProcessor (read-only tap)
 *   -> AudioBlock(Float32)
 *
 * Operates purely on in-place Float32 audio blocks without intermediate fixed-point quantization,
 * preserving full dynamic range and headroom.
 *
 * Loudness normalization is the last mutating stage, and per sink on purpose: each
 * player levels the track *it* is playing, so the two sides of a crossfade are
 * each levelled for their own song — see [LoudnessProcessor]. Last because its
 * limiter has to see the signal every other stage has finished shaping.
 * That limiter is also the equaliser's: [EqualizerProcessor] only takes back
 * half of a boost, and while it is shaping the signal the limiter stays on.
 *
 * [musicHaptics] taps the finished signal read-only after loudness normalization so
 * haptic reactions reflect the user's EQ and normalized dynamics without ever
 * mutating a single sample in [AudioBlock].
 *
 * ## Staying out of the way
 *
 * Every stage here self-bypasses when its own setting is off, returning before
 * it reads a single sample rather than multiplying through by unity. With all
 * mutating stages idle the block leaves this class byte-identical to how it arrived,
 * which is what lets [PcmBoundary]'s power-of-two scaling round-trip 16- and
 * 24-bit integers unchanged — see `PrecisionAudioSink.publishOutputExactness`.
 */
class DspChain(
    val spatial: SpatialAudioProcessor = SpatialAudioProcessor(),
    val equalizer: EqualizerProcessor = EqualizerProcessor(),
    val transition: TransitionFilterProcessor = TransitionFilterProcessor(),
    val loudness: LoudnessProcessor = LoudnessProcessor(),
    val musicHaptics: MusicHapticsProcessor? = null,
) : FloatAudioProcessor {

    private var currentSampleRate: Int = 0
    private var processCounter: Long = 0L

    override fun configure(sampleRate: Int, channelCount: Int) {
        this.currentSampleRate = sampleRate
        spatial.configure(sampleRate, channelCount)
        equalizer.configure(sampleRate, channelCount)
        transition.configure(sampleRate, channelCount)
        loudness.configure(sampleRate, channelCount)
        musicHaptics?.configure(sampleRate, channelCount)
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
                    val transitionOn = transition.isFiltering
                    val hapticsOn = musicHaptics?.config?.enabled == true
                    Log.d(
                        TAG,
                        "process() #$count frames=$frames sr=$sr " +
                            "spatial=$spatialOn eq=$eqOn transition=$transitionOn haptics=$hapticsOn",
                    )
                } catch (_: Throwable) {
                }
            }
        }

        spatial.process(block)
        equalizer.process(block)
        transition.process(block)
        // The equaliser leaves its overs in place for the stages after it to
        // see; the loudness stage's limiter is what catches whatever survives.
        loudness.holdLimiter = equalizer.isShaping
        loudness.process(block)
        // Read-only tap: observes the final audible mix without altering any sample.
        musicHaptics?.process(block)
    }

    @Suppress("DEPRECATION")
    override fun flush() {
        spatial.flush()
        equalizer.flush()
        transition.flush()
        loudness.flush()
        musicHaptics?.flush()
    }

    override fun reset() {
        processCounter = 0L
        spatial.reset()
        equalizer.reset()
        transition.reset()
        loudness.reset()
        musicHaptics?.reset()
    }

    companion object {
        private const val TAG = "DspChain"
    }
}
