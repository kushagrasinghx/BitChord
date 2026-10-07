package com.music.bitchord

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.music.bitchord.playback.EqLayout
import com.music.bitchord.playback.EqualizerPreset
import com.music.bitchord.playback.EqualizerProcessor
import com.music.bitchord.playback.manualCurve
import com.music.bitchord.playback.toneCurve
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the equaliser actually does to a signal.
 *
 * The risk this covers is not that the code throws — it is that a filter is
 * quietly the wrong shape. A state-variable section with a mistyped output mix
 * still runs, still sounds like *something*, and is indistinguishable by ear
 * from one that is correct until you go looking for the decibels you asked for.
 * So every test here measures: a sine goes in, its amplitude is read out of the
 * result by correlation at the frequency it was generated at, and the gain is
 * compared against what the curve promised.
 */
class EqualizerProcessorTest {

    @Test
    fun `a band lifts its own centre by the decibels it was given`() {
        // The five interior bands are bells, and a bell's gain at its centre is
        // the gain it was set to.
        for (band in 1 until EqLayout.MANUAL_COUNT - 1) {
            val gain = measureBand(band, BOOST_DB)
            assertEquals("band $band at +$BOOST_DB dB", BOOST_DB.toDouble(), gain, TOLERANCE_DB)
        }
    }

    @Test
    fun `a band cuts its own centre by the decibels it was given`() {
        for (band in 1 until EqLayout.MANUAL_COUNT - 1) {
            val gain = measureBand(band, -BOOST_DB)
            assertEquals("band $band at -$BOOST_DB dB", -BOOST_DB.toDouble(), gain, TOLERANCE_DB)
        }
    }

    /**
     * The two end bands are shelves, not bells — see [com.music.bitchord.playback.FilterKind].
     * A shelf's labelled frequency is its corner, which is the half-way point of
     * its travel, so 60 Hz reads +3 dB when the slider says +6 and the full +6
     * arrives below it.
     */
    @Test
    fun `the end bands are shelves, half way up at their corner and all the way past it`() {
        val low = EqLayout.MANUAL_BANDS_HZ[0].toDouble()
        assertEquals("60 Hz corner", BOOST_DB / 2.0, measureBand(0, BOOST_DB, low), TOLERANCE_DB)
        assertEquals("20 Hz shelf", BOOST_DB.toDouble(), measureBand(0, BOOST_DB, 20.0), TOLERANCE_DB)

        val high = EqLayout.MANUAL_BANDS_HZ.last().toDouble()
        val top = EqLayout.MANUAL_COUNT - 1
        assertEquals("14 kHz corner", BOOST_DB / 2.0, measureBand(top, BOOST_DB, high), TOLERANCE_DB)
    }

    /** The pad's horizontal axis: left warm, right bright, by equal amounts. */
    @Test
    fun `the tone pad tilts the spectrum without moving its middle`() {
        val bright = toneCurve(x = EqLayout.TONE_STEPS, y = 0, focused = false)
        val corner = EqLayout.TONE_STEPS * EqLayout.TONE_DB_PER_STEP

        val processor = EqualizerProcessor()
        processor.setTuning(enabled = true, curve = bright, balance = 0f)
        val lowEnd = measure(processor, 60.0) - bright.preampDb
        val highEnd = measure(processor, 12_000.0) - bright.preampDb

        assertEquals("bass cut at x=+5", -corner.toDouble(), lowEnd, TILT_TOLERANCE_DB)
        assertEquals("treble lifted at x=+5", corner.toDouble(), highEnd, TILT_TOLERANCE_DB)
    }

    /** And its vertical one, which only ever touches the middle. */
    @Test
    fun `the tone pad's contour moves the mids and leaves the ends alone`() {
        val forward = toneCurve(x = 0, y = EqLayout.TONE_STEPS, focused = true)
        val processor = EqualizerProcessor()
        processor.setTuning(enabled = true, curve = forward, balance = 0f)

        val mids = measure(processor, 1_000.0) - forward.preampDb
        val bass = measure(processor, 60.0) - forward.preampDb

        val expected = (EqLayout.TONE_STEPS * EqLayout.TONE_DB_PER_STEP).toDouble()
        assertEquals("1 kHz lifted", expected, mids, TOLERANCE_DB)
        assertTrue("60 Hz should be left alone, was $bass dB", abs(bass) < 0.5)
    }

    /** Focused is narrower than Broad, and that is the whole of the difference. */
    @Test
    fun `focused is narrower than broad at the same setting`() {
        val steps = EqLayout.TONE_STEPS
        val broad = EqualizerProcessor().also {
            it.setTuning(true, toneCurve(0, steps, focused = false), 0f)
        }
        val focused = EqualizerProcessor().also {
            it.setTuning(true, toneCurve(0, steps, focused = true), 0f)
        }
        val broadCurve = toneCurve(0, steps, focused = false)
        val focusedCurve = toneCurve(0, steps, focused = true)

        // An octave below the 1 kHz centre, where the two skirts are furthest
        // apart: the wide bell is still lifting and the narrow one has let go.
        val broadSkirt = measure(broad, 500.0) - broadCurve.preampDb
        val focusedSkirt = measure(focused, 500.0) - focusedCurve.preampDb
        assertTrue(
            "broad ($broadSkirt dB) should reach further than focused ($focusedSkirt dB)",
            broadSkirt > focusedSkirt + 1.5,
        )
    }

    @Test
    fun `balance silences the side it is pushed away from`() {
        val processor = EqualizerProcessor()
        processor.setTuning(enabled = true, curve = manualCurve(EqualizerPreset.FLAT.bands), balance = -1f)
        val output = run(processor, stereoTone(1_000.0))

        val right = (1 until output.size step 2).maxOf { abs(output[it].toInt()) }
        val left = (0 until output.size step 2).maxOf { abs(output[it].toInt()) }
        assertEquals("hard left should mute the right channel", 0, right)
        assertTrue("hard left should leave the left channel alone, peaked at $left", left > 7_000)
    }

    /**
     * The make-up attenuation exists so that boosting cannot run the signal into
     * the top of a 16-bit sample. Every band up as far as it goes is the worst
     * case there is.
     */
    @Test
    fun `boosting every band at once does not clip`() {
        val processor = EqualizerProcessor()
        val bands = List(EqLayout.MANUAL_COUNT) { EqLayout.MANUAL_RANGE_DB }
        processor.setTuning(enabled = true, curve = manualCurve(bands), balance = 0f)

        val input = stereoTone(400.0, amplitude = 24_000.0)
        val output = run(processor, input)
        val peak = output.maxOf { abs(it.toInt()) }
        assertTrue("output peaked at $peak, above what went in", peak <= 24_200)
    }

    @Test
    fun `a switched-off equaliser hands the signal back untouched`() {
        val processor = EqualizerProcessor()
        processor.setTuning(enabled = false, curve = manualCurve(EqualizerPreset.ROCK.bands), balance = 0.5f)
        val input = stereoTone(1_000.0)
        assertArrayEqualsShort(input, run(processor, input))
    }

    /**
     * Switching off mid-track glides down rather than cutting, so the buffers
     * either side of the change are neither identical nor silent — but once the
     * glide lands, the passthrough has to be exact again. A processor that
     * settled a hair off flat would sit there quantising every sample it touched
     * for the rest of the session.
     */
    @Test
    fun `once the switch-off has glided down the passthrough is exact again`() {
        val processor = EqualizerProcessor()
        processor.setTuning(enabled = true, curve = manualCurve(EqualizerPreset.BASS_BOOST.bands), balance = 0f)
        val format = AudioProcessor.AudioFormat(SAMPLE_RATE, 2, C.ENCODING_PCM_16BIT)
        processor.configure(format)
        processor.flush()

        val chunk = stereoTone(1_000.0, frames = 4_096)
        drain(processor, chunk)
        processor.setTuning(enabled = false, curve = manualCurve(EqualizerPreset.FLAT.bands), balance = 0f)
        // A second of audio is far longer than the glide needs.
        repeat(12) { drain(processor, chunk) }
        assertArrayEqualsShort(chunk, drain(processor, chunk))
    }

    @Test
    fun `every preset covers every band`() {
        for (preset in EqualizerPreset.entries) {
            if (preset == EqualizerPreset.CUSTOM) continue
            assertEquals(
                "${preset.name} should have one value per band",
                EqLayout.MANUAL_COUNT,
                preset.bandsDb.size,
            )
        }
    }

    @Test
    fun `a preset's own bands name it, and anything else is custom`() {
        assertEquals(EqualizerPreset.ROCK, EqualizerPreset.matching(EqualizerPreset.ROCK.bands))
        assertEquals(EqualizerPreset.CUSTOM, EqualizerPreset.matching(List(EqLayout.MANUAL_COUNT) { 7f }))
    }

    /** Attenuation only — a curve is never allowed to make itself louder. */
    @Test
    fun `the make-up attenuation never becomes a boost`() {
        for (preset in EqualizerPreset.entries) {
            if (preset == EqualizerPreset.CUSTOM) continue
            val preamp = manualCurve(preset.bands).preampDb
            assertTrue("${preset.name} asked for $preamp dB of make-up", preamp <= 0.001f)
        }
    }

    // ---- Harness -----------------------------------------------------------

    /** Sets one band and reports what the cascade did at [atHz], net of make-up. */
    private fun measureBand(
        band: Int,
        gainDb: Float,
        atHz: Double = EqLayout.MANUAL_BANDS_HZ[band].toDouble(),
    ): Double {
        val bands = MutableList(EqLayout.MANUAL_COUNT) { 0f }
        bands[band] = gainDb
        val curve = manualCurve(bands)
        val processor = EqualizerProcessor()
        processor.setTuning(enabled = true, curve = curve, balance = 0f)
        return measure(processor, atHz) - curve.preampDb
    }

    /** Gain in decibels at [hz], measured rather than predicted. */
    private fun measure(processor: EqualizerProcessor, hz: Double): Double {
        val input = stereoTone(hz)
        val output = run(processor, input)
        return 20 * log10(amplitudeAt(output, hz) / amplitudeAt(input, hz))
    }

    /**
     * Correlation against the generating frequency, over a window holding a
     * whole number of its cycles. Rejects the quantisation noise and any
     * harmonics an RMS would have counted as signal.
     */
    private fun amplitudeAt(samples: ShortArray, hz: Double): Double {
        var real = 0.0
        var imaginary = 0.0
        var count = 0
        for (frame in SETTLE_FRAMES until samples.size / 2) {
            val angle = 2 * PI * hz * frame / SAMPLE_RATE
            val value = samples[frame * 2].toDouble()
            real += value * cos(angle)
            imaginary += value * sin(angle)
            count++
        }
        return 2 * sqrt(real * real + imaginary * imaginary) / count
    }

    private fun stereoTone(
        hz: Double,
        amplitude: Double = AMPLITUDE,
        frames: Int = TONE_FRAMES,
    ): ShortArray {
        val samples = ShortArray(frames * 2)
        for (frame in 0 until frames) {
            val value = (amplitude * sin(2 * PI * hz * frame / SAMPLE_RATE)).toInt().toShort()
            samples[frame * 2] = value
            samples[frame * 2 + 1] = value
        }
        return samples
    }

    /** Configures, flushes and pushes [input] through in realistic chunks. */
    private fun run(processor: EqualizerProcessor, input: ShortArray): ShortArray {
        val format = AudioProcessor.AudioFormat(SAMPLE_RATE, 2, C.ENCODING_PCM_16BIT)
        processor.configure(format)
        processor.flush()
        val output = ShortArray(input.size)
        var written = 0
        var offset = 0
        while (offset < input.size) {
            val length = minOf(CHUNK_SAMPLES, input.size - offset)
            val chunk = drain(processor, input.copyOfRange(offset, offset + length))
            chunk.copyInto(output, written)
            written += chunk.size
            offset += length
        }
        return output
    }

    private fun drain(processor: EqualizerProcessor, input: ShortArray): ShortArray {
        processor.queueInput(input.toBuffer())
        val out = processor.output
        val result = ShortArray(out.remaining() / 2)
        out.order(ByteOrder.nativeOrder()).asShortBuffer().get(result)
        return result
    }

    private fun ShortArray.toBuffer(): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(size * 2).order(ByteOrder.nativeOrder())
        forEach { buffer.putShort(it) }
        buffer.flip()
        return buffer
    }

    private fun assertArrayEqualsShort(expected: ShortArray, actual: ShortArray) {
        assertEquals("sample count", expected.size, actual.size)
        for (index in expected.indices) {
            if (expected[index] != actual[index]) {
                throw AssertionError(
                    "sample $index: expected ${expected[index]} but was ${actual[index]}",
                )
            }
        }
    }

    private companion object {
        const val SAMPLE_RATE = 48_000

        /** Two seconds, so every frequency under test holds whole cycles in the window. */
        const val TONE_FRAMES = SAMPLE_RATE * 2

        /** Half a second of run-up, discarded: the filters need a moment to ring up. */
        const val SETTLE_FRAMES = SAMPLE_RATE / 2

        const val CHUNK_SAMPLES = 4_096 * 2

        /** Well below full scale, so a boost under test has somewhere to go. */
        const val AMPLITUDE = 8_000.0

        const val BOOST_DB = 6f

        const val TOLERANCE_DB = 0.4

        /** The tilt pair's shelves overlap slightly, so their ends read a touch shy. */
        const val TILT_TOLERANCE_DB = 0.8
    }
}
