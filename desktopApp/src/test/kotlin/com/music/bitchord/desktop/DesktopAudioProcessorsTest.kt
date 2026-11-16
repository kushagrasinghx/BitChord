package com.music.bitchord.desktop

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopSpatialAudioTest {

    private val rate = 44_100

    @Test
    fun `switched off it does not touch the samples`() {
        val spatial = DesktopSpatialAudio(2, rate)
        val samples = stereo(frames = 1_000)
        val original = samples.copyOf()

        spatial.process(samples, samples.size)

        assertTrue(samples.contentEquals(original))
    }

    @Test
    fun `widening increases the side signal and keeps the mid`() {
        val spatial = DesktopSpatialAudio(2, rate).apply { enabled = true }
        val samples = stereo(frames = 4_000)
        val sideBefore = sideEnergy(samples)
        val midBefore = midEnergy(samples)

        spatial.process(samples, samples.size)

        // The whole point: the difference between the channels grows.
        assertTrue(sideEnergy(samples) > sideBefore * 1.5, "side did not widen")
        // And the centre survives roughly intact — a widener that hollows out the vocal is the
        // classic way this goes wrong.
        val midAfter = midEnergy(samples)
        assertTrue(midAfter > midBefore * 0.5, "mid collapsed from $midBefore to $midAfter")
    }

    @Test
    fun `it declines anything that is not stereo`() {
        val spatial = DesktopSpatialAudio(1, rate).apply { enabled = true }
        val samples = FloatArray(1_000) { sin(it * 0.01).toFloat() }
        val original = samples.copyOf()

        assertFalse(spatial.applies)
        spatial.process(samples, samples.size)

        assertTrue(samples.contentEquals(original), "a mono track was altered")
    }

    private fun stereo(frames: Int) = FloatArray(frames * 2) { index ->
        val frame = index / 2
        // Deliberately different per channel, so there is a side to widen.
        if (index % 2 == 0) {
            (sin(2.0 * PI * 440 * frame / rate) * 0.5).toFloat()
        } else {
            (sin(2.0 * PI * 440 * frame / rate + 0.6) * 0.5).toFloat()
        }
    }

    private fun sideEnergy(samples: FloatArray): Double {
        var sum = 0.0
        var index = 0
        while (index + 1 < samples.size) {
            val side = (samples[index] - samples[index + 1]) * 0.5
            sum += side * side
            index += 2
        }
        return sum
    }

    private fun midEnergy(samples: FloatArray): Double {
        var sum = 0.0
        var index = 0
        while (index + 1 < samples.size) {
            val mid = (samples[index] + samples[index + 1]) * 0.5
            sum += mid * mid
            index += 2
        }
        return sum
    }
}

class DesktopSilenceSkipperTest {

    private val rate = 8_000
    private val channels = 2

    @Test
    fun `switched off everything comes through`() {
        val skipper = DesktopSilenceSkipper(channels, rate)
        val input = tone(1.0) + quiet(3.0) + tone(1.0)

        val out = skipper.process(input, input.size)

        assertEquals(input.size, skipper.outputCount)
        assertTrue(out.contentEquals(input))
    }

    @Test
    fun `a long gap is cut down but its edges survive`() {
        val skipper = DesktopSilenceSkipper(channels, rate).apply { enabled = true }
        val input = tone(1.0) + quiet(4.0) + tone(1.0)

        skipper.process(input, input.size)
        val outFrames = skipper.outputCount / channels

        // Two seconds of music, plus a tenth kept at each end of the gap.
        val expected = (rate * 2.2).toInt()
        assertTrue(
            abs(outFrames - expected) < rate * 0.4,
            "kept $outFrames frames, wanted about $expected",
        )
        // And the accounting adds up: what came in is what went out plus what was dropped, which is
        // what the position clock relies on.
        val inFrames = input.size / channels
        assertTrue(
            abs(inFrames - (outFrames + skipper.skippedFrames)) < rate * 0.4,
            "in $inFrames, out $outFrames, skipped ${skipper.skippedFrames}",
        )
    }

    @Test
    fun `a short rest in the music is left alone`() {
        val skipper = DesktopSilenceSkipper(channels, rate).apply { enabled = true }
        // Half a second — well under the one-second minimum Android uses, which is exactly the case
        // that must not be touched.
        val input = tone(1.0) + quiet(0.5) + tone(1.0)

        skipper.process(input, input.size)

        assertEquals(input.size, skipper.outputCount, "a musical rest was cut")
        assertEquals(0L, skipper.skippedFrames)
    }

    @Test
    fun `the music either side of a cut is unchanged`() {
        val skipper = DesktopSilenceSkipper(channels, rate).apply { enabled = true }
        val leading = tone(1.0)
        val input = leading + quiet(4.0) + tone(1.0)

        val out = skipper.process(input, input.size)

        // The first second is music and must arrive untouched, sample for sample — attenuation
        // belongs to the kept silence, not to the track.
        for (index in leading.indices) {
            assertEquals(leading[index], out[index], 1e-6f, "music altered at $index")
        }
    }

    private fun tone(seconds: Double): FloatArray {
        val frames = (rate * seconds).toInt()
        return FloatArray(frames * channels) { index ->
            (sin(2.0 * PI * 300 * (index / channels) / rate) * 0.6).toFloat()
        }
    }

    private fun quiet(seconds: Double) = FloatArray((rate * seconds).toInt() * channels)
}
