package com.music.bitchord.playback.audio.bitperfect

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import com.music.bitchord.playback.audio.bitperfect.usb.UsbDescriptors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@androidx.annotation.OptIn(UnstableApi::class)
class DacFormatConverterTest {

    /** Interleaved stereo 32-bit integer PCM of a sine at [hz], left and right alike. */
    private fun sine(hz: Double, rate: Int, seconds: Double, amplitude: Double = 0.5): ByteBuffer {
        val frames = (rate * seconds).toInt()
        val buffer = ByteBuffer.allocateDirect(frames * 8).order(ByteOrder.LITTLE_ENDIAN)
        repeat(frames) { frame ->
            val value = (amplitude * sin(2 * PI * hz * frame / rate) * Int.MAX_VALUE).toInt()
            buffer.putInt(value).putInt(value)
        }
        buffer.flip()
        return buffer
    }

    /** Runs all of [input] through, drain included, and returns the left channel at a scale of ±1. */
    private fun convert(input: ByteBuffer, inRate: Int, outRate: Int, bits: Int = 24): DoubleArray {
        val converter = DacFormatConverter(C.ENCODING_PCM_32BIT, 2, inRate, outRate, bits, bits)
        val bytes = bits / 8
        val chunk = ByteBuffer.allocateDirect(4096 * 2 * bytes).order(ByteOrder.LITTLE_ENDIAN)
        val left = ArrayList<Double>()
        fun collect() {
            chunk.flip()
            while (chunk.hasRemaining()) {
                left += read(chunk, bits)
                read(chunk, bits)
            }
            chunk.clear()
        }
        while (input.remaining() >= 8) {
            converter.process(input, chunk)
            collect()
        }
        while (!converter.drain(chunk)) collect()
        collect()
        return left.toDoubleArray()
    }

    private fun read(buffer: ByteBuffer, bits: Int): Double = when (bits) {
        16 -> buffer.short / 32_768.0
        else -> {
            val a = buffer.get().toInt() and 255
            val b = buffer.get().toInt() and 255
            val c = buffer.get().toInt()
            ((c shl 16) or (b shl 8) or a) / 8_388_608.0
        }
    }

    /** The level of [hz] in [samples], by correlation, as a linear amplitude. */
    private fun levelAt(samples: DoubleArray, hz: Double, rate: Int): Double {
        var re = 0.0
        var im = 0.0
        samples.forEachIndexed { i, sample ->
            re += sample * cos(2 * PI * hz * i / rate)
            im += sample * sin(2 * PI * hz * i / rate)
        }
        return 2 * sqrt(re * re + im * im) / samples.size
    }

    /** Skips the filter's run-in and run-out at either end. */
    private fun DoubleArray.middle() = copyOfRange(size / 4, size * 3 / 4)

    @Test
    fun everyInputFrameOfATrackIsAccountedForByTheOutputLength() {
        assertEquals(24_000, convert(sine(1000.0, 96_000, 0.5), 96_000, 48_000).size)
        assertEquals(22_050, convert(sine(1000.0, 88_200, 0.5), 88_200, 44_100).size)
        assertEquals(24_000, convert(sine(1000.0, 44_100, 0.5), 44_100, 48_000).size)
        assertEquals(22_050, convert(sine(1000.0, 48_000, 0.5), 48_000, 44_100).size)
    }

    @Test
    fun anAudibleToneComesThroughAtItsLevelLessTheHeadroom() {
        for ((inRate, outRate) in listOf(96_000 to 48_000, 44_100 to 48_000, 192_000 to 44_100)) {
            val out = convert(sine(1000.0, inRate, 0.5), inRate, outRate).middle()
            assertEquals("$inRate to $outRate", 0.5 * 0.891, levelAt(out, 1000.0, outRate), 0.001)
        }
    }

    @Test
    fun toneStaysInPhaseSoThePlayerClockIsRight() {
        val out = convert(sine(1000.0, 96_000, 0.5), 96_000, 48_000)
        for (i in out.size / 4 until out.size * 3 / 4) {
            assertEquals(0.5 * 0.891 * sin(2 * PI * 1000.0 * i / 48_000), out[i], 0.001)
        }
    }

    @Test
    fun aToneAboveTheDacsNyquistDoesNotFoldBackDown() {
        // 30 kHz in a 48 kHz output would alias to 18 kHz without the filter.
        val out = convert(sine(30_000.0, 96_000, 0.5, amplitude = 0.9), 96_000, 48_000).middle()
        assertTrue(levelAt(out, 18_000.0, 48_000) < 0.0001)
    }

    @Test
    fun audibleTopOctaveSurvives() {
        val out = convert(sine(19_000.0, 96_000, 0.5), 96_000, 48_000).middle()
        assertEquals(0.5 * 0.891, levelAt(out, 19_000.0, 48_000), 0.005)
    }

    @Test
    fun aFullScaleToneClampsInsteadOfWrapping() {
        val out = convert(sine(1000.0, 48_000, 0.1, amplitude = 1.0), 48_000, 48_000, bits = 16)
        assertTrue(out.all { abs(it) <= 1.0 })
        // A wrapped sample would jump by nearly full scale between neighbours.
        for (i in 1 until out.size) assertTrue(abs(out[i] - out[i - 1]) < 0.25)
    }

    @Test
    fun depthOnlyReductionKeepsTheRateAndDithersWithinOneLsb() {
        val input = sine(1000.0, 48_000, 0.1)
        val samples = input.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val out = convert(input, 48_000, 48_000, bits = 16)
        assertEquals(4800, out.size)
        val lsb = 1 / 32_768.0
        var changed = false
        out.forEach { sample ->
            val original = samples.int / 2_147_483_648.0
            samples.int
            assertTrue(abs(sample - original) <= 1.5 * lsb)
            if (sample != Math.round(original * 32_768) / 32_768.0) changed = true
        }
        assertTrue("dither should move some samples off plain rounding", changed)
    }

    @Test
    fun silenceStaysDigitalSilence() {
        val input = ByteBuffer.allocateDirect(8 * 4800).order(ByteOrder.LITTLE_ENDIAN)
        input.put(ByteArray(8 * 4800))
        input.flip()
        assertTrue(convert(input, 96_000, 48_000, bits = 16).all { it == 0.0 })
    }

    @Test
    fun aFlushForgetsThePreviousPosition() {
        val converter = DacFormatConverter(C.ENCODING_PCM_32BIT, 2, 96_000, 48_000, 24, 24)
        val output = ByteBuffer.allocateDirect(1 shl 16)
        converter.process(sine(1000.0, 96_000, 0.05), output)
        converter.reset()
        output.clear()
        // Nothing went in since the reset, so nothing is held back to come out.
        assertTrue(converter.drain(output))
        assertEquals(0, output.position())
    }

    // ---- Choosing the format --------------------------------------------------------------

    @Test
    fun aHighRateTrackPrefersTheHighestSameFamilyRateTheDacHas() {
        assertEquals(48_000, BitPerfectPcm.rankRates(96_000, listOf(44_100, 48_000)).first())
        assertEquals(96_000, BitPerfectPcm.rankRates(192_000, listOf(48_000, 96_000, 44_100)).first())
        assertEquals(44_100, BitPerfectPcm.rankRates(88_200, listOf(44_100, 48_000)).first())
    }

    @Test
    fun aDacWithoutTheFamilyGetsTheNearestOtherRate() {
        assertEquals(48_000, BitPerfectPcm.rankRates(44_100, listOf(48_000, 96_000)).first())
        assertEquals(96_000, BitPerfectPcm.rankRates(176_400, listOf(48_000, 96_000)).first())
    }

    @Test
    fun aTracksOwnRateAlwaysComesFirst() {
        assertEquals(44_100, BitPerfectPcm.rankRates(44_100, listOf(48_000, 44_100, 88_200)).first())
    }

    @Test
    fun aRatioTooAwkwardToTabulateIsNotOffered() {
        assertTrue(BitPerfectPcm.rankRates(44_099, listOf(48_000)).isEmpty())
    }

    @Test
    fun depthFallsBackToTheDeepestTheDacHas() {
        assertEquals(24, BitPerfectPcm.dacBits(24, listOf(16, 24, 32)))
        assertEquals(16, BitPerfectPcm.dacBits(24, listOf(16)))
        assertEquals(24, BitPerfectPcm.dacBits(32, listOf(16, 24)))
        assertNull(BitPerfectPcm.dacBits(24, emptyList()))
    }

    @Test
    fun aUac2DacIsOfferedEveryRateWithLosslessDepthsFirst() {
        val endpoint = UsbDescriptors.Endpoint(address = 1, attributes = 1, maxPacket = 1024, interval = 1)
        fun alternate(index: Int, bits: Int, subslot: Int) = UsbDescriptors.Alternate(
            interface_ = 1, alternate = index, uac2 = true, clock = 41, channels = 2, bits = bits,
            subslot = subslot, pcm = true, endpoints = listOf(endpoint),
        )
        val device = UsbDescriptors.Device(alternates = listOf(alternate(1, 16, 2), alternate(2, 24, 3)))
        val formats = UsbDescriptors.dacFormats(device, 96_000, 2, 24, convert = true)
        // Its own rate first, deepest setting first because 24 bits is what the source needs.
        assertEquals(96_000, formats[0].second.sampleRate)
        assertEquals(24, formats[0].second.bits)
        assertEquals(96_000, formats[1].second.sampleRate)
        assertEquals(16, formats[1].second.bits)
        assertTrue(formats.any { it.second.sampleRate == 48_000 })
    }

    @Test
    fun aUac1DacIsOnlyOfferedTheRatesItLists() {
        val endpoint = UsbDescriptors.Endpoint(address = 1, attributes = 1, maxPacket = 1024, interval = 1)
        val alternate = UsbDescriptors.Alternate(
            interface_ = 1, alternate = 1, channels = 2, bits = 16, subslot = 2, pcm = true,
            rates = listOf(44_100 to 44_100, 48_000 to 48_000), endpoints = listOf(endpoint),
        )
        val formats = UsbDescriptors.dacFormats(UsbDescriptors.Device(alternates = listOf(alternate)), 96_000, 2, 24, convert = true)
        assertEquals(listOf(48_000, 44_100), formats.map { it.second.sampleRate })
        assertTrue(formats.all { it.second.bits == 16 })
    }

    // ---- With conversion off, as it is by default ------------------------------------------

    private val cappedUac1Dac = UsbDescriptors.Device(
        alternates = listOf(
            UsbDescriptors.Alternate(
                interface_ = 1, alternate = 1, channels = 2, bits = 16, subslot = 2, pcm = true,
                rates = listOf(44_100 to 44_100, 48_000 to 48_000),
                endpoints = listOf(UsbDescriptors.Endpoint(address = 1, attributes = 1, maxPacket = 1024, interval = 1)),
            ),
        ),
    )

    @Test
    fun withoutConversionARateTheDacLacksIsNotOffered() {
        assertTrue(UsbDescriptors.dacFormats(cappedUac1Dac, 96_000, 2, 16, convert = false).isEmpty())
    }

    @Test
    fun withoutConversionADepthTheDacLacksIsNotOffered() {
        assertTrue(UsbDescriptors.dacFormats(cappedUac1Dac, 48_000, 2, 24, convert = false).isEmpty())
    }

    @Test
    fun withoutConversionATrackTheDacCanTakeIsOfferedAtItsOwnFormatOnly() {
        val formats = UsbDescriptors.dacFormats(cappedUac1Dac, 44_100, 2, 16, convert = false)
        assertEquals(listOf(44_100), formats.map { it.second.sampleRate })
        assertEquals(listOf(16), formats.map { it.second.bits })
    }

    @Test
    fun withoutConversionAUac2DacIsOnlyAskedForTheTracksOwnRate() {
        val endpoint = UsbDescriptors.Endpoint(address = 1, attributes = 1, maxPacket = 1024, interval = 1)
        val alternate = UsbDescriptors.Alternate(
            interface_ = 1, alternate = 1, uac2 = true, clock = 41, channels = 2, bits = 24,
            subslot = 3, pcm = true, endpoints = listOf(endpoint),
        )
        val formats = UsbDescriptors.dacFormats(UsbDescriptors.Device(alternates = listOf(alternate)), 96_000, 2, 24, convert = false)
        assertEquals(listOf(96_000), formats.map { it.second.sampleRate })
    }
}
