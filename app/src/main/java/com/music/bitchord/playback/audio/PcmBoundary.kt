package com.music.bitchord.playback.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * High-performance, zero-allocation boundary layer converting raw PCM byte buffers
 * to and from canonical [AudioBlock] Float32 representations.
 *
 * All conversions assume Little-Endian byte order matching Android AudioTrack, Linux ALSA,
 * and Media3 standards.
 *
 * Normalization mappings:
 * - 16-bit: normalized by 1 / 32768.0f (range: -1.0f .. +0.9999695f)
 * - 24-bit: normalized by 1 / 8388608.0f (range: -1.0f .. +0.99999988f)
 * - 32-bit integer: normalized by 1 / 2147483648.0 (range: -1.0f .. +1.0f)
 * - 32-bit float: direct mapping with defensive sanitization for NaN / Infinity
 */
object PcmBoundary {

    /**
     * Decodes PCM bytes from [inputBuffer] into [destinationBlock].
     *
     * Incomplete trailing bytes (less than a complete frame) are left unconsumed in
     * [inputBuffer], allowing streaming decoders to prepend them to subsequent reads.
     *
     * @param inputBuffer ByteBuffer containing incoming PCM bytes (assumed Little-Endian).
     * @param encoding Raw PCM sample format in [inputBuffer].
     * @param destinationBlock AudioBlock receiving the normalized Float32 samples.
     * @param maxFrames Maximum number of frames to decode in this pass.
     * @return Number of full audio frames decoded into [destinationBlock].
     */
    fun decode(
        inputBuffer: ByteBuffer,
        encoding: PcmEncoding,
        destinationBlock: AudioBlock,
        maxFrames: Int = destinationBlock.capacityFrames,
    ): Int {
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val channelCount = destinationBlock.channelCount
        val bytesPerFrame = encoding.bytesPerFrame(channelCount)
        if (bytesPerFrame <= 0) {
            destinationBlock.reset(0)
            return 0
        }

        val availableFrames = inputBuffer.remaining() / bytesPerFrame
        val framesToRead = minOf(availableFrames, maxFrames, destinationBlock.capacityFrames)
        if (framesToRead <= 0) {
            destinationBlock.reset(0)
            return 0
        }

        val totalSamples = framesToRead * channelCount
        var dstIdx = 0
        when (encoding) {
            PcmEncoding.PCM_16BIT -> {
                val inv = 1.0f / 32768.0f
                for (i in 0 until totalSamples) {
                    destinationBlock.samples[dstIdx++] = inputBuffer.getShort().toFloat() * inv
                }
            }
            PcmEncoding.PCM_24BIT_PACKED -> {
                val inv = 1.0f / 8388608.0f
                for (i in 0 until totalSamples) {
                    val b0 = inputBuffer.get().toInt() and 0xFF
                    val b1 = inputBuffer.get().toInt() and 0xFF
                    val b2 = inputBuffer.get().toInt() // sign-extended
                    val sample24 = (b2 shl 16) or (b1 shl 8) or b0
                    destinationBlock.samples[dstIdx++] = sample24.toFloat() * inv
                }
            }
            PcmEncoding.PCM_32BIT -> {
                val inv = 1.0 / 2147483648.0
                for (i in 0 until totalSamples) {
                    destinationBlock.samples[dstIdx++] = (inputBuffer.getInt().toDouble() * inv).toFloat()
                }
            }
            PcmEncoding.PCM_FLOAT -> {
                for (i in 0 until totalSamples) {
                    destinationBlock.samples[dstIdx++] = sanitizeFloat(inputBuffer.getFloat())
                }
            }
        }

        destinationBlock.reset(framesToRead)
        return framesToRead
    }

    /**
     * Encodes Float32 samples from [sourceBlock] into [outputBuffer] in the target [encoding].
     *
     * Samples are clamped defensively to prevent integer overflow wrapping.
     *
     * @param sourceBlock AudioBlock providing normalized Float32 samples.
     * @param encoding Desired target PCM encoding.
     * @param outputBuffer ByteBuffer to receive the encoded bytes (assumed Little-Endian).
     * @param startFrame Index of first frame in [sourceBlock] to encode.
     * @param framesToEncode Maximum number of frames to encode from [sourceBlock].
     * @return Number of full audio frames successfully written into [outputBuffer].
     */
    fun encode(
        sourceBlock: AudioBlock,
        encoding: PcmEncoding,
        outputBuffer: ByteBuffer,
        startFrame: Int = 0,
        framesToEncode: Int = sourceBlock.frameCount - startFrame,
    ): Int {
        outputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val channelCount = sourceBlock.channelCount
        val bytesPerFrame = encoding.bytesPerFrame(channelCount)
        if (bytesPerFrame <= 0) return 0

        require(startFrame >= 0 && startFrame <= sourceBlock.frameCount) {
            "startFrame ($startFrame) out of bounds for frameCount ${sourceBlock.frameCount}"
        }
        val availableSourceFrames = sourceBlock.frameCount - startFrame
        val requestedFrames = minOf(framesToEncode, availableSourceFrames)
        val availableOutputFrames = outputBuffer.remaining() / bytesPerFrame
        val framesToWrite = minOf(requestedFrames, availableOutputFrames)
        if (framesToWrite <= 0) return 0

        var srcIdx = startFrame * channelCount
        val totalSamples = framesToWrite * channelCount
        when (encoding) {
            PcmEncoding.PCM_16BIT -> {
                for (i in 0 until totalSamples) {
                    outputBuffer.putShort(clamp16FromFloat(sourceBlock.samples[srcIdx++]))
                }
            }
            PcmEncoding.PCM_24BIT_PACKED -> {
                for (i in 0 until totalSamples) {
                    val sample24 = clamp24FromFloat(sourceBlock.samples[srcIdx++])
                    outputBuffer.put((sample24 and 0xFF).toByte())
                    outputBuffer.put(((sample24 ushr 8) and 0xFF).toByte())
                    outputBuffer.put(((sample24 ushr 16) and 0xFF).toByte())
                }
            }
            PcmEncoding.PCM_32BIT -> {
                for (i in 0 until totalSamples) {
                    outputBuffer.putInt(clamp32FromFloat(sourceBlock.samples[srcIdx++]))
                }
            }
            PcmEncoding.PCM_FLOAT -> {
                for (i in 0 until totalSamples) {
                    outputBuffer.putFloat(sanitizeFloat(sourceBlock.samples[srcIdx++]))
                }
            }
        }

        return framesToWrite
    }

    /**
     * Clamps and quantizes a normalized Float32 sample to a signed 16-bit Short.
     */
    fun clamp16FromFloat(f: Float): Short {
        if (f.isNaN()) return 0
        val scaled = f * 32768.0f
        if (scaled <= -32768.0f) return Short.MIN_VALUE
        if (scaled >= 32767.0f) return Short.MAX_VALUE
        return Math.round(scaled).toShort()
    }

    /**
     * Clamps and quantizes a normalized Float32 sample to a signed 24-bit Int (-8388608..8388607).
     */
    fun clamp24FromFloat(f: Float): Int {
        if (f.isNaN()) return 0
        val scaled = f * 8388608.0f
        if (scaled <= -8388608.0f) return -8388608
        if (scaled >= 8388607.0f) return 8388607
        return Math.round(scaled)
    }

    /**
     * Clamps and quantizes a normalized Float32 sample to a signed 32-bit Int.
     */
    fun clamp32FromFloat(f: Float): Int {
        if (f.isNaN()) return 0
        val scaled = f.toDouble() * 2147483648.0
        if (scaled <= -2147483648.0) return Int.MIN_VALUE
        if (scaled >= 2147483647.0) return Int.MAX_VALUE
        return Math.round(scaled).toInt()
    }

    /**
     * Sanitizes floating point samples, converting NaN to 0.0f and clamping infinities to +/-1.0f.
     */
    fun sanitizeFloat(f: Float): Float {
        if (f.isNaN()) return 0.0f
        if (f == Float.POSITIVE_INFINITY) return 1.0f
        if (f == Float.NEGATIVE_INFINITY) return -1.0f
        return f
    }
}
