package com.music.bitchord.playback.audio

/**
 * Common contract for high-precision, in-place Float32 audio DSP processors.
 *
 * Processors operate directly on canonical [AudioBlock] instances holding normalized
 * IEEE 754 32-bit floats, avoiding intermediate fixed-point quantization and preserving
 * dynamic headroom throughout the DSP pipeline.
 */
interface FloatAudioProcessor {
    /**
     * Configures the processor for the specified [sampleRate] and [channelCount].
     */
    fun configure(sampleRate: Int, channelCount: Int = AudioBlock.DEFAULT_CHANNELS)

    /**
     * Processes audio frames in [block] in-place.
     *
     * @param block Reusable AudioBlock containing interleaved normalized Float32 samples.
     */
    fun process(block: AudioBlock)

    /**
     * Flushes internal filter states and histories (e.g. on seek or stream boundary).
     */
    fun flush()

    /**
     * Resets internal states and releases sample buffers.
     */
    fun reset()
}

/** Adapts [com.music.bitchord.playback.SpatialAudioProcessor] to [FloatAudioProcessor]. */
fun com.music.bitchord.playback.SpatialAudioProcessor.asFloatAudioProcessor(): FloatAudioProcessor =
    object : FloatAudioProcessor {
        override fun configure(sampleRate: Int, channelCount: Int) =
            this@asFloatAudioProcessor.configure(sampleRate, channelCount)
        override fun process(block: AudioBlock) =
            this@asFloatAudioProcessor.process(block)
        @Suppress("DEPRECATION")
        override fun flush() = this@asFloatAudioProcessor.flush()
        override fun reset() = this@asFloatAudioProcessor.reset()
    }

/** Adapts [com.music.bitchord.playback.EqualizerProcessor] to [FloatAudioProcessor]. */
fun com.music.bitchord.playback.EqualizerProcessor.asFloatAudioProcessor(): FloatAudioProcessor =
    object : FloatAudioProcessor {
        override fun configure(sampleRate: Int, channelCount: Int) =
            this@asFloatAudioProcessor.configure(sampleRate, channelCount)
        override fun process(block: AudioBlock) =
            this@asFloatAudioProcessor.process(block)
        @Suppress("DEPRECATION")
        override fun flush() = this@asFloatAudioProcessor.flush()
        override fun reset() = this@asFloatAudioProcessor.reset()
    }

/** Adapts [com.music.bitchord.playback.TransitionFilterProcessor] to [FloatAudioProcessor]. */
fun com.music.bitchord.playback.TransitionFilterProcessor.asFloatAudioProcessor(): FloatAudioProcessor =
    object : FloatAudioProcessor {
        override fun configure(sampleRate: Int, channelCount: Int) =
            this@asFloatAudioProcessor.configure(sampleRate, channelCount)
        override fun process(block: AudioBlock) =
            this@asFloatAudioProcessor.process(block)
        @Suppress("DEPRECATION")
        override fun flush() = this@asFloatAudioProcessor.flush()
        override fun reset() = this@asFloatAudioProcessor.reset()
    }

