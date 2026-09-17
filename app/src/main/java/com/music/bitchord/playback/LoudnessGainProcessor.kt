package com.music.bitchord.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteOrder
import kotlin.math.min
import kotlin.math.pow

/**
 * Full-plan loudness: a per-deck gain stage for LUFS normalization.
 *
 * A plain multiplier, not an effect: [CrossfadeController.begin] aims it from
 * each deck's analyzed integrated LUFS (target − offset, clamped ±6 dB, then
 * peak-headroomed under −1 dBTP), and it holds that gain for the whole track.
 * Same 16-bit BaseAudioProcessor contract as the echo send (volatile target,
 * per-64-frame chase, parked passthrough), so track-to-track corrections
 * glide instead of stepping. Rides LAST in the custom chain (after the splice
 * guard) so fades, EQ, echo and reverb all voice before the correction.
 *
 * [open] parks at 0 dB. [clear]/[onFlush] hold the target (a seek must not
 * change loudness). Only [onReset] zeroes. Bows out (NOT_SET) off 16-bit PCM
 * like every other custom processor.
 */
@UnstableApi
class LoudnessGainProcessor : BaseAudioProcessor() {

    @Volatile
    private var targetLinear: Float = 1f

    private var channelCount = 0
    private var currentLinear: Float = 1f

    /** Aims the stage. [gainDb] is clamped to ±12 dB defensively. */
    fun setGainDb(gainDb: Float) {
        targetLinear = 10f.pow(gainDb.coerceIn(-12f, 12f) / 20f)
    }

    /** Parks at unity. */
    fun open() {
        targetLinear = 1f
    }

    /** Holds the target: a seek must not change loudness. */
    fun clear() {
        currentLinear = targetLinear
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount < 1) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        channelCount = inputAudioFormat.channelCount
        currentLinear = targetLinear
        return inputAudioFormat
    }

    override fun onFlush() = clear()

    override fun onReset() {
        targetLinear = 1f
        currentLinear = 1f
        channelCount = 0
    }

    override fun queueInput(inputBuffer: java.nio.ByteBuffer) {
        val bytesPerFrame = BYTES_PER_SAMPLE * channelCount
        if (bytesPerFrame == 0) return
        val frameCount = inputBuffer.remaining() / bytesPerFrame
        if (frameCount == 0) return
        val outputBuffer = replaceOutputBuffer(frameCount * bytesPerFrame)

        val parked = targetLinear == 1f && currentLinear == 1f
        if (parked) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        inputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer.order(ByteOrder.nativeOrder())

        var remaining = frameCount
        while (remaining > 0) {
            val block = min(remaining, GLIDE_FRAMES)
            currentLinear += (targetLinear - currentLinear) * GLIDE_RATE
            val gain = currentLinear
            repeat(block) {
                for (channel in 0 until channelCount) {
                    val sample = inputBuffer.short.toFloat()
                    outputBuffer.putShort(clampToShort(sample * gain))
                }
            }
            remaining -= block
        }
        outputBuffer.flip()
    }

    private fun clampToShort(value: Float): Short =
        value.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()

    companion object {
        private const val BYTES_PER_SAMPLE = 2
        private const val GLIDE_FRAMES = 64
        private const val GLIDE_RATE = 0.05f
    }
}

/** Role-routed loudness aims, mirroring EqFilters/ReverbFilters. Gains hold
 * across bail/finish (per-track static correction, not transition state) —
 * only the next arm re-aims, so the interface needs no open(). */
interface LoudnessGains {
    fun incoming(gainDb: Float)
    fun outgoing(gainDb: Float)
    fun open()

    object None : LoudnessGains {
        override fun incoming(gainDb: Float) = Unit
        override fun outgoing(gainDb: Float) = Unit
        override fun open() = Unit
    }
}
