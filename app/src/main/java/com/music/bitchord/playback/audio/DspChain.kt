package com.music.bitchord.playback.audio

import android.util.Log
import com.music.bitchord.BuildConfig
import com.music.bitchord.playback.EqualizerProcessor
import com.music.bitchord.playback.SpatialAudioProcessor
import com.music.bitchord.playback.TransitionFilterProcessor

/**
 * Composite DSP chain executing BitChord's custom audio processors in their canonical sequence:
 *
 * AudioBlock(Float32) -> SpatialAudioProcessor -> EqualizerProcessor -> TransitionFilterProcessor -> AudioBlock(Float32)
 *
 * Operates purely on in-place Float32 audio blocks without intermediate fixed-point quantization,
 * preserving full dynamic range and headroom.
 */
class DspChain(
    val spatial: SpatialAudioProcessor = SpatialAudioProcessor(),
    val equalizer: EqualizerProcessor = EqualizerProcessor(),
    val transition: TransitionFilterProcessor = TransitionFilterProcessor(),
) : FloatAudioProcessor {

    private var currentSampleRate: Int = 0
    private var processCounter: Long = 0L

    override fun configure(sampleRate: Int, channelCount: Int) {
        this.currentSampleRate = sampleRate
        spatial.configure(sampleRate, channelCount)
        equalizer.configure(sampleRate, channelCount)
        transition.configure(sampleRate, channelCount)
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
                    Log.d(
                        TAG,
                        "process() #$count frames=$frames sr=$sr spatial=$spatialOn eq=$eqOn transition=$transitionOn",
                    )
                } catch (_: Throwable) {
                }
            }
        }

        spatial.process(block)
        equalizer.process(block)
        transition.process(block)
    }

    @Suppress("DEPRECATION")
    override fun flush() {
        spatial.flush()
        equalizer.flush()
        transition.flush()
    }

    override fun reset() {
        processCounter = 0L
        spatial.reset()
        equalizer.reset()
        transition.reset()
    }

    companion object {
        private const val TAG = "DspChain"
    }
}
