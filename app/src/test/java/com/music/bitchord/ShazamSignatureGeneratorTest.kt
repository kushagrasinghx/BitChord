package com.music.bitchord

import com.music.bitchord.data.shazam.ShazamFrequencyBand
import com.music.bitchord.data.shazam.ShazamSignatureGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

class ShazamSignatureGeneratorTest {

    private val sampleRate = ShazamSignatureGenerator.SAMPLE_RATE_HZ
    private val hzPerCorrectedBin = sampleRate / 2.0 / 1024.0 / 64.0

    @Test
    fun `silence produces a signature with no peaks`() {
        val samples = ShortArray(sampleRate * 2)

        val signature = ShazamSignatureGenerator.generate(samples)

        assertEquals(sampleRate, signature.sampleRateHz)
        assertEquals(samples.size, signature.sampleCount)
        assertTrue(signature.peaksByBand.values.all { it.isEmpty() })
    }

    @Test
    fun `a rising chirp produces peaks in the bands it sweeps through`() {
        val signature = ShazamSignatureGenerator.generate(chirp(sampleRate * 3))

        val populatedBands = signature.peaksByBand.filterValues { it.isNotEmpty() }
        assertTrue("the chirp produced no peaks", populatedBands.isNotEmpty())
        assertTrue(
            "only ${populatedBands.keys} were populated; expected the sweep to cross bands",
            populatedBands.size >= 2,
        )
    }

    @Test
    fun `a steady tone with noise peaks around the tone frequency`() {
        val signature = ShazamSignatureGenerator.generate(noisyTone(sampleRate * 3))

        val band = signature.peaksByBand.getValue(ShazamFrequencyBand.BAND_520_1450)
        assertTrue("expected peaks in the 520-1450 Hz band", band.isNotEmpty())

        val nearTone = band.filter { peak ->
            abs(peak.frequencyBin * hzPerCorrectedBin - 1_000.0) <= 60.0
        }
        assertTrue("expected peaks around 1 kHz, got ${band.size} peaks elsewhere", nearTone.isNotEmpty())
    }

    @Test
    fun `every peak stays inside its band and the 16-bit format`() {
        val signature = ShazamSignatureGenerator.generate(noisyTone(sampleRate * 2))

        for ((band, peaks) in signature.peaksByBand) {
            for (peak in peaks) {
                val frequencyHz = (peak.frequencyBin * hzPerCorrectedBin).toInt()
                assertEquals(
                    "peak at $frequencyHz Hz is filed under $band",
                    band,
                    ShazamFrequencyBand.forFrequency(frequencyHz),
                )
                assertTrue("magnitude ${peak.magnitude} does not fit in u16", peak.magnitude in 0..65535)
                assertTrue(
                    "frequency bin ${peak.frequencyBin} does not fit in u16",
                    peak.frequencyBin in 0..65535,
                )
                assertTrue("negative pass number ${peak.fftPassNumber}", peak.fftPassNumber >= 0)
            }
        }
    }

    @Test
    fun `the same samples always produce the same signature`() {
        val samples = noisyTone(sampleRate)

        assertEquals(
            ShazamSignatureGenerator.generate(samples),
            ShazamSignatureGenerator.generate(samples),
        )
    }

    @Test
    fun `trailing samples count towards the length but cannot produce peaks`() {
        val fullHops = 100
        val samples = noisyTone(fullHops * 128 + 37)

        val signature = ShazamSignatureGenerator.generate(samples)
        val peaks = signature.peaksByBand.values.flatten()

        assertEquals(samples.size, signature.sampleCount)
        assertTrue("expected the noisy tone to produce peaks", peaks.isNotEmpty())
        assertTrue(
            "a partial hop produced a peak at pass ${peaks.maxOf { it.fftPassNumber }}",
            peaks.all { it.fftPassNumber <= fullHops - 46 },
        )
    }

    @Test
    fun `band boundaries match the wire format`() {
        assertEquals(ShazamFrequencyBand.BAND_250_520, ShazamFrequencyBand.forFrequency(250))
        assertEquals(ShazamFrequencyBand.BAND_250_520, ShazamFrequencyBand.forFrequency(519))
        assertEquals(ShazamFrequencyBand.BAND_520_1450, ShazamFrequencyBand.forFrequency(520))
        assertEquals(ShazamFrequencyBand.BAND_520_1450, ShazamFrequencyBand.forFrequency(1449))
        assertEquals(ShazamFrequencyBand.BAND_1450_3500, ShazamFrequencyBand.forFrequency(1450))
        assertEquals(ShazamFrequencyBand.BAND_1450_3500, ShazamFrequencyBand.forFrequency(3499))
        assertEquals(ShazamFrequencyBand.BAND_3500_5500, ShazamFrequencyBand.forFrequency(3500))
        assertEquals(ShazamFrequencyBand.BAND_3500_5500, ShazamFrequencyBand.forFrequency(5500))
        assertNull(ShazamFrequencyBand.forFrequency(249))
        assertNull(ShazamFrequencyBand.forFrequency(5501))
    }

    /** A linear sweep from 300 Hz to 4.5 kHz, loud enough to clear the peak threshold. */
    private fun chirp(sampleCount: Int): ShortArray {
        val samples = ShortArray(sampleCount)
        var phase = 0.0
        for (index in samples.indices) {
            val progress = index.toDouble() / sampleCount
            val frequency = 300.0 + 4_200.0 * progress
            phase += 2.0 * PI * frequency / sampleRate
            samples[index] = (12_000.0 * sin(phase)).toInt().toShort()
        }
        return samples
    }

    /**
     * A 1 kHz tone under white noise. The tone gives the spectrum a known
     * peak, while the noise makes consecutive frames differ enough for the
     * strict time-domain comparison to accept it occasionally.
     */
    private fun noisyTone(sampleCount: Int): ShortArray {
        val random = Random(7)
        return ShortArray(sampleCount) { index ->
            val tone = 12_000.0 * sin(2.0 * PI * 1_000.0 * index / sampleRate)
            val noise = (random.nextDouble() * 2.0 - 1.0) * 8_000.0
            (tone + noise).toInt().coerceIn(-32768, 32767).toShort()
        }
    }
}
