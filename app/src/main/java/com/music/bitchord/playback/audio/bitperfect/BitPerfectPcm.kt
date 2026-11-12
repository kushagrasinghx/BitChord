/*
 * Copyright (C) 2026 BitChord Project
 *
 * Adapted from Mornye (https://github.com/mornyeapp/Mornye),
 * android/app/src/main/kotlin/com/zarz/mornye/BitPerfectPcm.kt,
 * Copyright 2026 zarzet.
 *
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.music.bitchord.playback.audio.bitperfect

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.round

/**
 * Lossless PCM representation changes only: no gain, dithering or resampling.
 *
 * Mornye decodes FLAC and WAV itself, so its integer samples arrive here
 * untouched. Ours come out of Media3's decoders, and those hand a 24-bit FLAC
 * over as float — `c2.android.flac.decoder` and the FFmpeg renderer emit only
 * 16-bit or float, and float is the one that carries 24 bits. Float input is
 * therefore taken back to the integer it was made from. Two scalings reach
 * this point: the decoders' exact `x / 2^23`, and Media3's own
 * `ToFloatPcmAudioProcessor`, which multiplies by `1 / 0x7FFFFFFF`. Both
 * leave the original 24-bit value within a quarter of one step of the
 * reconstruction below, so rounding recovers it, and [convert] confirms that
 * by re-deriving the float both ways. A float that matches neither came from
 * a lossy decoder or from processing, and is reported as not exact.
 */
@OptIn(UnstableApi::class)
internal object BitPerfectPcm {

    private val INTEGER_BITS = listOf(16, 24, 32)

    /** Media3's int-to-float factor, kept to the bit so its output can be re-derived. */
    private const val MEDIA3_FLOAT_FACTOR = 1.0 / 0x7FFFFFFF

    /** The narrowest supported integer depth that loses nothing of [sourceBits]. */
    fun outputBits(sourceBits: Int, supported: Collection<Int>): Int? =
        supported.filter { it in INTEGER_BITS && it >= sourceBits }.minOrNull()

    /**
     * How many bits an output needs to carry [encoding] without loss, or null
     * when no integer output can. Float is taken as 24-bit: that is what every
     * lossless float this app produces holds, and [convert] checks it.
     */
    fun sourceBits(encoding: Int): Int? = when (encoding) {
        C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 16
        C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN, C.ENCODING_PCM_FLOAT -> 24
        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN -> 32
        else -> null
    }

    fun bytesPerSample(encoding: Int): Int = when (encoding) {
        C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 2
        C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> 3
        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN, C.ENCODING_PCM_FLOAT -> 4
        else -> 0
    }

    /**
     * Writes as many whole frames of [input] as fit in [output], as
     * little-endian integer PCM of [outputBits]. Both buffers advance by what
     * was converted. Returns false when any converted sample could not be
     * carried exactly; it is then rounded and clamped instead of rejected, so
     * playback continues and the caller can say so.
     */
    fun convert(
        input: ByteBuffer,
        encoding: Int,
        channelCount: Int,
        outputBits: Int,
        output: ByteBuffer,
    ): Boolean {
        val inBytes = bytesPerSample(encoding)
        val outBytes = outputBits / 8
        val sourceBits = requireNotNull(sourceBits(encoding)) { "Unsupported PCM encoding $encoding" }
        require(outputBits in INTEGER_BITS && outputBits >= sourceBits) { "Output would reduce precision" }
        val frames = minOf(
            input.remaining() / (inBytes * channelCount),
            output.remaining() / (outBytes * channelCount),
        )
        input.order(if (isBigEndian(encoding)) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN)
        output.order(ByteOrder.LITTLE_ENDIAN)
        var exact = true
        repeat(frames * channelCount) {
            // Left-aligned in 32 bits, so every source depth shares one writer.
            val sample = when (inBytes) {
                2 -> input.short.toInt() shl 16
                3 -> read24(input, isBigEndian(encoding)) shl 8
                4 -> if (encoding == C.ENCODING_PCM_FLOAT) {
                    val value = input.float
                    val recovered = recover24(value)
                    if (exact && !isExact24(value, recovered)) exact = false
                    recovered shl 8
                } else {
                    input.int
                }
                else -> error("Unsupported PCM encoding $encoding")
            }
            when (outputBits) {
                16 -> output.putShort((sample shr 16).toShort())
                24 -> {
                    output.put((sample shr 8).toByte())
                    output.put((sample shr 16).toByte())
                    output.put((sample shr 24).toByte())
                }
                else -> output.putInt(sample)
            }
        }
        return exact
    }

    /** The 24-bit integer a float sample stands for, clamped to range. */
    internal fun recover24(value: Float): Int {
        if (value.isNaN()) return 0
        val scaled = round(value.toDouble() * 0x7FFFFFFF / 256.0)
        return scaled.coerceIn(-8_388_608.0, 8_388_607.0).toInt()
    }

    /** Whether [value] is exactly what either known scaling makes of [sample]. */
    internal fun isExact24(value: Float, sample: Int): Boolean =
        value == sample / 8_388_608f ||
            value == (MEDIA3_FLOAT_FACTOR * (sample shl 8)).toFloat()

    private fun isBigEndian(encoding: Int): Boolean =
        encoding == C.ENCODING_PCM_16BIT_BIG_ENDIAN ||
            encoding == C.ENCODING_PCM_24BIT_BIG_ENDIAN ||
            encoding == C.ENCODING_PCM_32BIT_BIG_ENDIAN

    private fun read24(input: ByteBuffer, bigEndian: Boolean): Int {
        val a = input.get().toInt() and 255
        val b = input.get().toInt() and 255
        val c = input.get().toInt()
        // The last byte read is the most significant one in little-endian
        // order; sign-extend from whichever byte is most significant.
        return if (bigEndian) {
            (a shl 24 shr 8) or (b shl 8) or (c and 255)
        } else {
            (c shl 16) or (b shl 8) or a
        }
    }
}
