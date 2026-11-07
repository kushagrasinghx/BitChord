package com.music.bitchord

import com.music.bitchord.data.shazam.ShazamFrequencyBand
import com.music.bitchord.data.shazam.ShazamPeak
import com.music.bitchord.data.shazam.ShazamSignature
import com.music.bitchord.data.shazam.ShazamSignatureEncoder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import java.util.zip.CRC32

class ShazamSignatureEncoderTest {

    private val sampleRate = 16_000

    @Test
    fun `writes the fixed header and both payload lengths`() {
        val bytes = ShazamSignatureEncoder.encode(signature(sampleCount = sampleRate * 4))

        assertEquals(0xcafe2580.toInt(), readU32(bytes, 0))
        assertEquals(0x94119c00.toInt(), readU32(bytes, 12))
        assertEquals(bytes.size - 48, readU32(bytes, 8))
        assertEquals(bytes.size - 48, readU32(bytes, 52))
        assertEquals(3 shl 27, readU32(bytes, 28))
        assertEquals(sampleRate * 4 + (sampleRate * 0.24).toInt(), readU32(bytes, 40))
        assertEquals((15 shl 19) + 0x40000, readU32(bytes, 44))
        assertEquals(0x40000000, readU32(bytes, 48))
    }

    @Test
    fun `writes a peak as pass delta magnitude and frequency bin`() {
        val bytes = ShazamSignatureEncoder.encode(
            signature(
                peaks = listOf(
                    ShazamPeak(fftPassNumber = 46, magnitude = 7_000, frequencyBin = 12_345),
                ),
            ),
        )

        assertEquals(72, bytes.size)
        assertEquals(0x60030040, readU32(bytes, 56))
        assertEquals(5, readU32(bytes, 60))
        assertEquals(46, bytes[64].toInt() and 0xff)
        assertEquals(7_000, readU16(bytes, 65))
        assertEquals(12_345, readU16(bytes, 67))
        assertEquals(0, bytes[69].toInt())
        assertEquals(0, bytes[70].toInt())
        assertEquals(0, bytes[71].toInt())
    }

    @Test
    fun `a delta of 255 or more writes an absolute pass number first`() {
        val bytes = ShazamSignatureEncoder.encode(
            signature(
                peaks = listOf(
                    ShazamPeak(fftPassNumber = 300, magnitude = 7_000, frequencyBin = 1_000),
                    ShazamPeak(fftPassNumber = 301, magnitude = 7_001, frequencyBin = 1_001),
                ),
            ),
        )

        assertEquals(15, readU32(bytes, 60))
        assertEquals(0xff, bytes[64].toInt() and 0xff)
        assertEquals(300, readU32(bytes, 65))
        assertEquals(0, bytes[69].toInt() and 0xff)
        assertEquals(7_000, readU16(bytes, 70))
        assertEquals(1_000, readU16(bytes, 72))
        assertEquals(1, bytes[74].toInt() and 0xff)
        assertEquals(7_001, readU16(bytes, 75))
        assertEquals(1_001, readU16(bytes, 77))
    }

    @Test
    fun `CRC32 covers everything after the first eight bytes`() {
        val bytes = ShazamSignatureEncoder.encode(signature())

        val crc = CRC32().apply { update(bytes, 8, bytes.size - 8) }
        assertEquals(crc.value.toInt(), readU32(bytes, 4))
    }

    @Test
    fun `empty bands are omitted and bands keep their wire ids`() {
        val bytes = ShazamSignatureEncoder.encode(
            signature(
                peaks = listOf(
                    ShazamPeak(fftPassNumber = 50, magnitude = 6_500, frequencyBin = 22_000),
                ),
                band = ShazamFrequencyBand.BAND_1450_3500,
            ),
        )

        assertEquals(0x60030042, readU32(bytes, 56))
    }

    @Test
    fun `a silent signature has no band blocks`() {
        val bytes = ShazamSignatureEncoder.encode(signature(peaks = emptyList()))

        assertEquals(56, bytes.size)
        assertEquals(8, readU32(bytes, 8))
        assertTrue(bytes.size == 56)
    }

    @Test
    fun `the data URI decodes back to the encoded packet`() {
        val signature = signature()
        val uri = ShazamSignatureEncoder.encodeToUri(signature)

        assertTrue(uri.startsWith(ShazamSignatureEncoder.DATA_URI_PREFIX))
        val encoded = Base64.getDecoder().decode(uri.removePrefix(ShazamSignatureEncoder.DATA_URI_PREFIX))
        assertArrayEquals(ShazamSignatureEncoder.encode(signature), encoded)
    }

    private fun signature(
        sampleCount: Int = sampleRate * 4,
        peaks: List<ShazamPeak> = listOf(
            ShazamPeak(fftPassNumber = 46, magnitude = 7_000, frequencyBin = 12_345),
        ),
        band: ShazamFrequencyBand = ShazamFrequencyBand.BAND_250_520,
    ) = ShazamSignature(
        sampleRateHz = sampleRate,
        sampleCount = sampleCount,
        peaksByBand = ShazamFrequencyBand.entries.associateWith {
            if (it == band) peaks else emptyList()
        },
    )

    private fun readU16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun readU32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)
}
