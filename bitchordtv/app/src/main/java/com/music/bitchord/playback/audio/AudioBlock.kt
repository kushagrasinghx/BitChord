package com.music.bitchord.playback.audio

/**
 * Reusable, audio-thread-owned memory block holding interleaved Float32 PCM samples.
 *
 * All samples are stored in [samples] as normalized IEEE 754 32-bit floats.
 * The nominal audio amplitude range is `[-1.0f, +1.0f]`, though intermediate DSP
 * operations may use values outside this range (headroom) before final clamping or dithering.
 *
 * AudioBlock is designed to be allocated once per processor/sink lifecycle and reused
 * across audio callbacks, completely avoiding object allocations in the steady-state
 * playback and processing path.
 *
 * @param channelCount Number of audio channels (e.g. 1 for Mono, 2 for Stereo).
 * @param capacityFrames Maximum number of audio frames the underlying sample buffer can store.
 */
class AudioBlock(
    val channelCount: Int = DEFAULT_CHANNELS,
    val capacityFrames: Int = DEFAULT_CAPACITY_FRAMES,
) {
    init {
        require(channelCount > 0) { "channelCount must be positive: $channelCount" }
        require(capacityFrames > 0) { "capacityFrames must be positive: $capacityFrames" }
    }

    /** Pre-allocated contiguous sample array sized for capacityFrames * channelCount. */
    val samples: FloatArray = FloatArray(capacityFrames * channelCount)

    /** Number of valid audio frames currently present in this block. */
    var frameCount: Int = 0
        private set

    /** Total number of valid samples currently in this block (frameCount * channelCount). */
    val sampleCount: Int
        get() = frameCount * channelCount

    /**
     * Resets the active frame count for a new processing cycle without reallocating memory.
     *
     * @param frames Number of frames to mark as active (must be in 0..capacityFrames).
     */
    fun reset(frames: Int = 0) {
        require(frames in 0..capacityFrames) {
            "frames ($frames) out of range 0..$capacityFrames"
        }
        this.frameCount = frames
    }

    /**
     * Updates frameCount after writing samples directly into [samples].
     */
    fun setFrameCount(frames: Int) {
        reset(frames)
    }

    /**
     * Clears all samples in the active region to 0.0f (silence) and resets frame count to 0.
     */
    fun clear() {
        if (sampleCount > 0) {
            samples.fill(0.0f, 0, sampleCount)
        }
        frameCount = 0
    }

    /**
     * Copies a range of frames from [source] into this block.
     *
     * @param source The source AudioBlock to copy from.
     * @param sourceStartFrame First frame index in [source] to copy.
     * @param destStartFrame First frame index in this block to receive samples.
     * @param frames Number of frames to copy.
     */
    fun copyFrom(
        source: AudioBlock,
        sourceStartFrame: Int = 0,
        destStartFrame: Int = 0,
        frames: Int = source.frameCount - sourceStartFrame,
    ) {
        require(source.channelCount == this.channelCount) {
            "Channel count mismatch: source has ${source.channelCount}, dest has ${this.channelCount}"
        }
        require(sourceStartFrame >= 0 && sourceStartFrame + frames <= source.frameCount) {
            "Source range [$sourceStartFrame, ${sourceStartFrame + frames}) out of bounds for source frameCount ${source.frameCount}"
        }
        require(destStartFrame >= 0 && destStartFrame + frames <= capacityFrames) {
            "Dest range [$destStartFrame, ${destStartFrame + frames}) out of bounds for capacity $capacityFrames"
        }
        if (frames <= 0) return

        val srcOffset = sourceStartFrame * channelCount
        val dstOffset = destStartFrame * channelCount
        val count = frames * channelCount
        System.arraycopy(source.samples, srcOffset, this.samples, dstOffset, count)

        val newFrameCount = maxOf(this.frameCount, destStartFrame + frames)
        this.frameCount = newFrameCount
    }

    companion object {
        const val DEFAULT_CHANNELS: Int = 2
        const val DEFAULT_CAPACITY_FRAMES: Int = 4096
    }
}
