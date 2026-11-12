/*
 * Copyright (C) 2026 BitChord Project
 *
 * Adapted from Mornye (https://github.com/mornyeapp/Mornye),
 * android/app/src/test/kotlin/com/zarz/mornye/BitPerfectPcmTest.kt,
 * Copyright 2026 zarzet.
 *
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.music.bitchord.playback.audio.bitperfect

import androidx.media3.common.C
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random

class BitPerfectPcmTest {

    private fun convert(input: ByteBuffer, encoding: Int, outputBits: Int, channels: Int = 1): Pair<ByteBuffer, Boolean> {
        val output = ByteBuffer.allocate(input.remaining() * 2).order(ByteOrder.LITTLE_ENDIAN)
        val exact = BitPerfectPcm.convert(input, encoding, channels, outputBits, output)
        output.flip()
        return output to exact
    }

    private fun bytes(buffer: ByteBuffer): ByteArray = ByteArray(buffer.remaining()).also { buffer.get(it) }

    @Test
    fun selectsOnlyAnIntegerFormatWithEnoughPrecision() {
        assertEquals(24, BitPerfectPcm.outputBits(24, listOf(16, 32, 24)))
        assertEquals(32, BitPerfectPcm.outputBits(24, listOf(16, 32)))
        assertNull(BitPerfectPcm.outputBits(24, listOf(16)))
        assertNull(BitPerfectPcm.outputBits(32, listOf(16, 24)))
    }

    @Test
    fun preservesPacked24BitSamplesIncludingSignExtremes() {
        val samples = byteArrayOf(0, 0, -128, -1, -1, 127, -1, -1, -1, 1, 0, 0)
        val (result, exact) = convert(ByteBuffer.wrap(samples), C.ENCODING_PCM_24BIT, 24)
        assertArrayEquals(samples, bytes(result))
        assertTrue(exact)
    }

    @Test
    fun readsBigEndian24BitAsTheSameSamples() {
        val bigEndian = byteArrayOf(-128, 0, 0, 127, -1, -1, -1, -1, -1, 0, 0, 1)
        val littleEndian = byteArrayOf(0, 0, -128, -1, -1, 127, -1, -1, -1, 1, 0, 0)
        val (result, _) = convert(ByteBuffer.wrap(bigEndian), C.ENCODING_PCM_24BIT_BIG_ENDIAN, 24)
        assertArrayEquals(littleEndian, bytes(result))
    }

    @Test
    fun padsLeastSignificantBitsWithoutChangingSampleAmplitude() {
        val input = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(Short.MIN_VALUE).putShort(Short.MAX_VALUE).putShort(-1)
        input.flip()
        val (result, exact) = convert(input, C.ENCODING_PCM_16BIT, 32)
        assertEquals(Int.MIN_VALUE, result.int)
        assertEquals(0x7fff0000, result.int)
        assertEquals(-65536, result.int)
        assertTrue(exact)
    }

    @Test
    fun carries32BitIntegersUntouched() {
        val input = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(Int.MIN_VALUE).putInt(Int.MAX_VALUE).putInt(0x01234567)
        input.flip()
        val (result, exact) = convert(input, C.ENCODING_PCM_32BIT, 32)
        assertEquals(Int.MIN_VALUE, result.int)
        assertEquals(Int.MAX_VALUE, result.int)
        assertEquals(0x01234567, result.int)
        assertTrue(exact)
    }

    @Test
    fun decoderFloatRoundTripPreservesEveryBitOf24BitSamples() {
        val samples = sweep()
        val input = ByteBuffer.allocate(samples.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (sample in samples) input.putFloat(sample / 8388608f)
        input.flip()
        val (result, exact) = convert(input, C.ENCODING_PCM_FLOAT, 32)
        for (sample in samples) assertEquals(sample shl 8, result.int)
        assertTrue(exact)
    }

    /** Media3's own int-to-float step multiplies by 1/0x7FFFFFFF, not 2^-31. */
    @Test
    fun media3FloatRoundTripPreservesEveryBitOf24BitSamples() {
        val samples = sweep()
        val input = ByteBuffer.allocate(samples.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (sample in samples) input.putFloat(((1.0 / 0x7FFFFFFF) * (sample shl 8)).toFloat())
        input.flip()
        val (result, exact) = convert(input, C.ENCODING_PCM_FLOAT, 24)
        for (sample in samples) {
            val actual = (result.get().toInt() and 255) or
                ((result.get().toInt() and 255) shl 8) or
                (result.get().toInt() shl 16)
            assertEquals(sample, actual)
        }
        assertTrue(exact)
    }

    @Test
    fun reportsRatherThanRejectsFloatThatIsNotIntegerPcm() {
        val input = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .putFloat(0.00000001f).putFloat(1.5f)
        input.flip()
        val (result, exact) = convert(input, C.ENCODING_PCM_FLOAT, 32)
        assertFalse(exact)
        assertEquals(0, result.int)
        assertEquals(8_388_607 shl 8, result.int)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDownconversion() {
        convert(ByteBuffer.allocate(3), C.ENCODING_PCM_24BIT, 16)
    }

    @Test
    fun convertsOnlyWholeFramesThatFitTheOutput() {
        val input = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
        repeat(5) { input.putShort(it.toShort()) }
        input.flip()
        // Room for one stereo 24-bit frame and a bit more, but not two.
        val output = ByteBuffer.allocate(8)
        BitPerfectPcm.convert(input, C.ENCODING_PCM_16BIT, 2, 24, output)
        assertEquals(6, output.position())
        assertEquals(4, input.position())
    }

    private fun sweep(): List<Int> {
        val random = Random(17)
        return listOf(-8388608, 8388607, -1, 0, 1) + List(10000) { random.nextInt(-8388608, 8388608) }
    }
}
