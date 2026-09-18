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
    @Volatile
    private var isBackspin = false

    private var channelCount = 0
    private var bytesPerFrame = 0
    private var ring = ShortArray(0)
    private var ringPos = 0
    private var ringFilled = 0
    private var glideCounter = 1f

    /** Aims the brake. [amount] 0..1 where 1 = full stop. */
    fun setBrake(amount: Float) {
        targetBrakeAmount = amount.coerceIn(0f, MAX_BRAKE_AMOUNT)
        if (targetBrakeAmount > 0f) glideCounter = 0f
    }

    fun setBackspin(enabled: Boolean) { isBackspin = enabled }

    /** Rides the brake back to zero so the track resumes normal speed. */
    fun ride() { setBrake(0f); isBackspin = false }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount < 1) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        channelCount = inputAudioFormat.channelCount
        bytesPerFrame = 2 * channelCount
        val ringFrames = (inputAudioFormat.sampleRate * 1.5).toInt().coerceAtLeast(48000)
        ring = ShortArray(ringFrames * channelCount)
        ringPos = 0
        ringFilled = 0
        return inputAudioFormat
    }

    override fun onFlush() {
        glideCounter = 0f
    }

    override fun onReset() {
        targetBrakeAmount = 0f
        isBackspin = false
        glideCounter = 0f
        ringPos = 0
        ringFilled = 0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (bytesPerFrame == 0) return
        val frameCount = inputBuffer.remaining() / bytesPerFrame
        if (frameCount == 0) return
        val outputBuffer = replaceOutputBuffer(frameCount * bytesPerFrame)

        if (ring.isNotEmpty()) {
            val pos = inputBuffer.position()
            for (i in 0 until frameCount * channelCount) {
                ring[ringPos] = inputBuffer.getShort(pos + i * 2)
                ringPos = (ringPos + 1) % ring.size
            }
            ringFilled = min(ringFilled + frameCount * channelCount, ring.size)
        }

        val active = glideCounter < 1f && targetBrakeAmount > 0f
        if (!active) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        inputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer.order(ByteOrder.nativeOrder())

        val backspinNow = isBackspin && glideCounter > 0.45f && ringFilled > channelCount * 256
        if (backspinNow) {
            var reverseIdx = (ringPos - channelCount + ring.size) % ring.size
            var stepAcc = 0f
            for (i in 0 until frameCount) {
                val t = glideCounter.coerceIn(0f, 1f)
                val revSpeed = 0.8f + t * 2.7f
                val gain = (1f - targetBrakeAmount * t * BRAKE_DIVE_FACTOR * 0.55f).coerceIn(0f, 1f)
                stepAcc += revSpeed
                val steps = stepAcc.toInt()
                stepAcc -= steps
                repeat(steps) { reverseIdx = (reverseIdx - channelCount + ring.size) % ring.size }
                for (ch in 0 until channelCount) {
                    val idx = (reverseIdx + ch + ring.size) % ring.size
                    val s = ring[idx].toFloat() * gain
                    outputBuffer.putShort(clampToShort(s))
                }
                for (ch in 0 until channelCount) inputBuffer.short
                glideCounter = (glideCounter + GLIDE_RATE * 0.7f).coerceAtMost(1f)
            }
            outputBuffer.flip()
            return
        }

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

    override fun isActive(): Boolean = targetBrakeAmount > 0f

    }
