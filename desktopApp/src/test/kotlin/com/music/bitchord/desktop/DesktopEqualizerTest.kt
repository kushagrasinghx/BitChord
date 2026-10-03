package com.music.bitchord.desktop

import com.music.bitchord.playback.EqLayout
import com.music.bitchord.playback.EqualizerPreset
import com.music.bitchord.playback.manualCurve
import com.music.bitchord.playback.toneCurve
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the equaliser actually does to a signal.
 *
 * The risk this covers is not that the code throws — it is that a filter is quietly the wrong
 * shape. A state-variable section with a mistyped output mix still runs, still sounds like
 * *something*, and is indistinguishable by ear from a correct one until you go looking for the
 * decibels you asked for. So every test here measures: a sine goes in, its amplitude is read out of
 * the result by correlation at the frequency it was generated at, and the gain is compared against
 * what the curve promised.
 *
 * A port of Android's `EqualizerProcessorTest`, against the same shared curves.
 */
class DesktopEqualizerTest {

    @Test
    fun aBandLiftsItsOwnCentreByTheDecibelsItWasGiven() {
        // The five interior bands are bells, and a bell's gain at its centre is the gain it was set
        // to; the two ends are shelves and are checked separately.
        for (band in 1 until EqLayout.MANUAL_COUNT - 1) {
            assertEquals(BOOST_DB.toDouble(), measureBand(band, BOOST_DB), TOLERANCE_DB, "band $band")
        }
    }

    @Test
    fun aBandCutsItsOwnCentreByTheDecibelsItWasGiven() {
        for (band in 1 until EqLayout.MANUAL_COUNT - 1) {
            assertEquals(-BOOST_DB.toDouble(), measureBand(band, -BOOST_DB), TOLERANCE_DB, "band $band")
        }
    }

    /**
     * A shelf's labelled frequency is its corner, which is half way up its travel, so 60 Hz reads
     * +3 dB when the slider says +6 and the full +6 arrives below it.
     */
    @Test
    fun theEndBandsAreShelvesHalfWayUpAtTheirCorner() {
        val low = EqLayout.MANUAL_BANDS_HZ[0].toDouble()
        assertEquals(BOOST_DB / 2.0, measureBand(0, BOOST_DB, low), TOLERANCE_DB, "60 Hz corner")
        assertEquals(BOOST_DB.toDouble(), measureBand(0, BOOST_DB, 20.0), TOLERANCE_DB, "20 Hz shelf")

        val top = EqLayout.MANUAL_COUNT - 1
        val high = EqLayout.MANUAL_BANDS_HZ.last().toDouble()
        assertEquals(BOOST_DB / 2.0, measureBand(top, BOOST_DB, high), TOLERANCE_DB, "14 kHz corner")
    }

    /** The pad's horizontal axis: left warm, right bright, by equal amounts. */
    @Test
    fun theTonePadTiltsTheSpectrum() {
        val bright = toneCurve(x = EqLayout.TONE_STEPS, y = 0, focused = false)
        val corner = (EqLayout.TONE_STEPS * EqLayout.TONE_DB_PER_STEP).toDouble()
        val equalizer = DesktopEqualizer().also { it.setTuning(true, bright, 0f) }

        assertEquals(-corner, measure(equalizer, 60.0) - bright.preampDb, TILT_TOLERANCE_DB, "bass cut")
        assertEquals(corner, measure(equalizer, 12_000.0) - bright.preampDb, TILT_TOLERANCE_DB, "treble lift")
    }

    /** And its vertical one, which only ever touches the middle. */
    @Test
    fun theTonePadsContourMovesTheMidsAndLeavesTheEndsAlone() {
        val forward = toneCurve(x = 0, y = EqLayout.TONE_STEPS, focused = true)
        val equalizer = DesktopEqualizer().also { it.setTuning(true, forward, 0f) }

        val expected = (EqLayout.TONE_STEPS * EqLayout.TONE_DB_PER_STEP).toDouble()
        assertEquals(expected, measure(equalizer, 1_000.0) - forward.preampDb, TOLERANCE_DB, "1 kHz")
        val bass = measure(equalizer, 60.0) - forward.preampDb
        assertTrue(abs(bass) < 0.5, "60 Hz should be left alone, was $bass dB")
    }

    /** Focused is narrower than Broad, and that is the whole of the difference. */
    @Test
    fun focusedIsNarrowerThanBroadAtTheSameSetting() {
        val steps = EqLayout.TONE_STEPS
        val broadCurve = toneCurve(0, steps, focused = false)
        val focusedCurve = toneCurve(0, steps, focused = true)

        // An octave below the 1 kHz centre, where the two skirts are furthest apart.
        val broad = measure(DesktopEqualizer().also { it.setTuning(true, broadCurve, 0f) }, 500.0) -
            broadCurve.preampDb
        val focused = measure(DesktopEqualizer().also { it.setTuning(true, focusedCurve, 0f) }, 500.0) -
            focusedCurve.preampDb
        assertTrue(broad > focused + 1.5, "broad ($broad dB) should reach further than focused ($focused dB)")
    }

    @Test
    fun balanceSilencesTheSideItIsPushedAwayFrom() {
        val equalizer = DesktopEqualizer()
        equalizer.setTuning(true, manualCurve(EqualizerPreset.FLAT.bands), balance = -1f)
        val output = run(equalizer, stereoTone(1_000.0))

        val right = (1 until output.size step 2).maxOf { abs(output[it]) }
        val left = (0 until output.size step 2).maxOf { abs(output[it]) }
        assertTrue(right < 1e-6f, "hard left should mute the right channel, peaked at $right")
        assertTrue(left > 0.2f, "hard left should leave the left channel alone, peaked at $left")
    }

    /**
     * The make-up attenuation exists so that boosting cannot run the signal past full scale. Every
     * band up as far as it goes is the worst case there is.
     */
    @Test
    fun boostingEveryBandAtOnceDoesNotClip() {
        val equalizer = DesktopEqualizer()
        equalizer.setTuning(true, manualCurve(List(EqLayout.MANUAL_COUNT) { EqLayout.MANUAL_RANGE_DB }), 0f)
        val peak = run(equalizer, stereoTone(400.0, amplitude = 0.75f)).maxOf { abs(it) }
        assertTrue(peak <= 0.78f, "output peaked at $peak, above what went in")
    }

    @Test
    fun aSwitchedOffEqualiserHandsTheSignalBackUntouched() {
        val equalizer = DesktopEqualizer()
        equalizer.setTuning(false, manualCurve(EqualizerPreset.ROCK.bands), balance = 0.5f)
        val input = stereoTone(1_000.0)
        val output = run(equalizer, input)
        for (index in input.indices) {
            assertEquals(input[index], output[index], 0f, "sample $index")
        }
    }

    /**
     * Switching off mid-track glides down rather than cutting, so the buffers either side are
     * neither identical nor silent — but once the glide lands the passthrough has to be exact
     * again, or the processor sits there altering every sample for the rest of the session.
     */
    @Test
    fun onceTheSwitchOffHasGlidedDownThePassthroughIsExactAgain() {
        val equalizer = DesktopEqualizer()
        equalizer.configure(2, SAMPLE_RATE)
        equalizer.setTuning(true, manualCurve(EqualizerPreset.BASS_BOOST.bands), 0f)
        val chunk = stereoTone(1_000.0, frames = 4_096)
        equalizer.process(chunk.copyOf(), chunk.size)
        equalizer.setTuning(false, manualCurve(EqualizerPreset.FLAT.bands), 0f)
        // A second of audio is far longer than the glide needs.
        repeat(12) { equalizer.process(chunk.copyOf(), chunk.size) }

        val after = chunk.copyOf()
        equalizer.process(after, after.size)
        for (index in chunk.indices) assertEquals(chunk[index], after[index], 0f, "sample $index")
    }

    /** Attenuation only — a curve is never allowed to make itself louder. */
    @Test
    fun theMakeUpAttenuationNeverBecomesABoost() {
        for (preset in EqualizerPreset.entries) {
            if (preset == EqualizerPreset.CUSTOM) continue
            val preamp = manualCurve(preset.bands).preampDb
            assertTrue(preamp <= 0.001f, "${preset.name} asked for $preamp dB of make-up")
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
        val equalizer = DesktopEqualizer().also { it.setTuning(true, curve, 0f) }
        return measure(equalizer, atHz) - curve.preampDb
    }

    /** Gain in decibels at [hz], measured rather than predicted. */
    private fun measure(equalizer: DesktopEqualizer, hz: Double): Double {
        val input = stereoTone(hz)
        val output = run(equalizer, input)
        return 20 * log10(amplitudeAt(output, hz) / amplitudeAt(input, hz))
    }

    /**
     * Correlation against the generating frequency, over a window holding whole cycles of it.
     * Rejects any harmonics an RMS would have counted as signal.
     */
    private fun amplitudeAt(samples: FloatArray, hz: Double): Double {
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

    private fun stereoTone(hz: Double, amplitude: Float = AMPLITUDE, frames: Int = TONE_FRAMES): FloatArray {
        val samples = FloatArray(frames * 2)
        for (frame in 0 until frames) {
            val value = (amplitude * sin(2 * PI * hz * frame / SAMPLE_RATE)).toFloat()
            samples[frame * 2] = value
            samples[frame * 2 + 1] = value
        }
        return samples
    }

    /** Configures and pushes a copy of [input] through in realistic chunks. */
    private fun run(equalizer: DesktopEqualizer, input: FloatArray): FloatArray {
        equalizer.configure(2, SAMPLE_RATE)
        val output = input.copyOf()
        var offset = 0
        while (offset < output.size) {
            val length = minOf(CHUNK_SAMPLES, output.size - offset)
            val chunk = output.copyOfRange(offset, offset + length)
            equalizer.process(chunk, length)
            chunk.copyInto(output, offset)
            offset += length
        }
        return output
    }

    private companion object {
        const val SAMPLE_RATE = 48_000

        /** Two seconds, so every frequency under test holds whole cycles in the window. */
        const val TONE_FRAMES = SAMPLE_RATE * 2

        /** Half a second of run-up, discarded: the filters need a moment to ring up. */
        const val SETTLE_FRAMES = SAMPLE_RATE / 2

        const val CHUNK_SAMPLES = 4_096 * 2

        /** Well below full scale, so a boost under test has somewhere to go. */
        const val AMPLITUDE = 0.25f

        const val BOOST_DB = 6f
        const val TOLERANCE_DB = 0.4
        const val TILT_TOLERANCE_DB = 0.8
    }
}
