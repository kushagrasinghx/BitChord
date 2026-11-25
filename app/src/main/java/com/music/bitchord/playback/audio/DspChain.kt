package com.music.bitchord.playback.audio

import android.util.Log
import com.music.bitchord.BuildConfig
import com.music.bitchord.playback.EqualizerProcessor
import com.music.bitchord.playback.LoudnessProcessor
import com.music.bitchord.playback.SpatialAudioProcessor
import com.music.bitchord.playback.StereoVocalReductionProcessor
import com.music.bitchord.playback.TransitionFilterProcessor
import com.music.bitchord.playback.VocalProcessor

/**
 * Composite DSP chain executing BitChord's custom audio processors in their canonical sequence:
 *
 * AudioBlock(Float32) -> VocalProcessor -> SpatialAudioProcessor -> EqualizerProcessor
 *   -> TransitionFilterProcessor -> LoudnessProcessor -> AudioBlock(Float32)
 *
 * Operates purely on in-place Float32 audio blocks without intermediate fixed-point quantization,
 * preserving full dynamic range and headroom.
 *
 * Vocal reduction is first, and on purpose: it needs the decoder's original
 * stereo relationship — what the mix actually panned where — before spatial
 * widening's crossfeed and the listener's equaliser have changed it. It is
 * also not the listener's: the equaliser further down belongs to the user and
 * the whole session, so enabling Karaoke never touches their curve.
 *
 * Loudness normalization is the last stage, and per sink on purpose: each
 * player levels the track *it* is playing, so the two sides of a crossfade are
 * each levelled for their own song — see [LoudnessProcessor]. Last because its
 * limiter has to see the signal every other stage has finished shaping.
 * That limiter is also the equaliser's: [EqualizerProcessor] only takes back
 * half of a boost, and while it is shaping the signal the limiter stays on.
 *
 * ## Staying out of the way
 *
 * Every stage here self-bypasses when its own setting is off, returning before
 * it reads a single sample rather than multiplying through by unity. With all
 * four idle the block leaves this class byte-identical to how it arrived,
 * which is what lets [PcmBoundary]'s power-of-two scaling round-trip 16- and
 * 24-bit integers unchanged — see `PrecisionAudioSink.publishOutputExactness`.
 */
class DspChain(
    val spatial: SpatialAudioProcessor = SpatialAudioProcessor(),
    val equalizer: EqualizerProcessor = EqualizerProcessor(),
    val transition: TransitionFilterProcessor = TransitionFilterProcessor(),
    val loudness: LoudnessProcessor = LoudnessProcessor(),
    /**
     * The Karaoke vocal stage, first in the chain. A constructor property so
     * the service can hand each crossfade deck its own instance — the filter
     * history is per sink — while both always carry the same tuning.
     */
    val vocal: VocalProcessor = StereoVocalReductionProcessor(),
) : FloatAudioProcessor {

    private var currentSampleRate: Int = 0
    private var processCounter: Long = 0L

    override fun configure(sampleRate: Int, channelCount: Int) {
        this.currentSampleRate = sampleRate
        vocal.configure(sampleRate, channelCount)
        spatial.configure(sampleRate, channelCount)
        equalizer.configure(sampleRate, channelCount)
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
                    val transitionOn = transition.isFiltering
                    val vocalOn = vocal.isShaping
                    Log.d(
                        TAG,
                        "process() #$count frames=$frames sr=$sr " +
                            "vocal=$vocalOn spatial=$spatialOn eq=$eqOn transition=$transitionOn",
                    )
                } catch (_: Throwable) {
                }
            }
        }

        vocal.process(block)
        spatial.process(block)
        equalizer.process(block)
        transition.process(block)
        // The vocal, equaliser and spatial widener stages leave their overs in
        // place for the stages after them to see; the loudness stage's limiter
        // is what catches whatever survives.
        loudness.holdLimiter = vocal.isShaping || equalizer.isShaping || spatial.enabled
        loudness.process(block)
    }

    @Suppress("DEPRECATION")
    override fun flush() {
        vocal.flush()
        spatial.flush()
        equalizer.flush()
        transition.flush()
        loudness.flush()
    }

    override fun reset() {
        processCounter = 0L
        vocal.reset()
        spatial.reset()
        equalizer.reset()
        transition.reset()
        loudness.reset()
    }

    companion object {
        private const val TAG = "DspChain"
    }
}
