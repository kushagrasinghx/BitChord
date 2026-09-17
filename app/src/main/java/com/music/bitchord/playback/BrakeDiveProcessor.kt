package com.music.bitchord.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

/**
 * DJ-only brake/dive effect. Applies a smooth volume reduction
 * with a quadratic dive curve to the outgoing track during transitions.
 *
 * The magnitude is configurable (0 = no brake, 1 = full stop).
 * The dive curve: gain = 1 - brakeAmount * t^2 * BRAKE_DIVE_FACTOR,
 * where t runs from 0 to 1 over the brake window (64 frames).
 *
 * Processes 16-bit PCM buffers in-place, exactly like [EchoSendProcessor].
 */
@UnstableApi
class BrakeDiveProcessor : BaseAudioProcessor() {

    companion object {
        const val MAX_BRAKE_AMOUNT = 1.0f
        const val BRAKE_DIVE_FACTOR = 0.97f
        const val GLIDE_FRAMES = 64
        const val GLIDE_RATE = 0.05f
    }

    @Volatile
    private var targetBrakeAmount: Float = 0f

    private var channelCount = 0
    private var bytesPerFrame = 0
    // Full-audit F6: start settled; the dive window is armed by setBrake, and
    // isActive() only looks at the aimed target, so the init value never
    // keeps the processor in the chain by itself.
    private var glideCounter = 1f

    /** Aims the brake. [amount] 0..1 where 1 = full stop. */
    fun setBrake(amount: Float) {
        targetBrakeAmount = amount.coerceIn(0f, MAX_BRAKE_AMOUNT)
        // (Re)arming here — not in onFlush — so a fresh/seeked processor
        // stays out of the chain until a real dive is aimed.
        if (targetBrakeAmount > 0f) glideCounter = 0f
    }

    /** Rides the brake back to zero so the track resumes normal speed. */
    fun ride() = setBrake(0f)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount < 1) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        channelCount = inputAudioFormat.channelCount
        bytesPerFrame = 2 * channelCount
        return inputAudioFormat
    }

    override fun onFlush() {
        glideCounter = 0f
    }

    override fun onReset() {
        targetBrakeAmount = 0f
        glideCounter = 0f
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (bytesPerFrame == 0) return
        val frameCount = inputBuffer.remaining() / bytesPerFrame
        if (frameCount == 0) return
        val outputBuffer = replaceOutputBuffer(frameCount * bytesPerFrame)

        val active = glideCounter < 1f && targetBrakeAmount > 0f
        if (!active) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        inputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer.order(ByteOrder.nativeOrder())

        var remaining = frameCount
        while (remaining > 0) {
            val block = min(remaining, GLIDE_FRAMES)
            remaining -= block
            repeat(block) {
                val t = glideCounter.coerceIn(0f, 1f)
                val brakeFactor = 1f - targetBrakeAmount * t * t * BRAKE_DIVE_FACTOR
                for (ch in 0 until channelCount) {
                    val sample = inputBuffer.short.toFloat()
                    outputBuffer.putShort(clampToShort(sample * brakeFactor))
                }
                glideCounter += GLIDE_RATE
            }
        }
        outputBuffer.flip()
    }

    private fun clampToShort(value: Float): Short =
        value.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()

    override fun getOutput(): ByteBuffer {
        return ByteBuffer.allocateDirect(0)
    }

    // Full-audit F6: active purely on aimed target. The old glide clause kept
    // a fresh/seeked processor in the chain running a pointless x1.0 copy on
    // every PCM-16 playback (stock included); the dive window is armed by
    // setBrake and still gates inside queueInput.
    override fun isActive(): Boolean = targetBrakeAmount > 0f

    }
