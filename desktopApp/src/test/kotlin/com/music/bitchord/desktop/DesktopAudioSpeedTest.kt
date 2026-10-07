package com.music.bitchord.desktop

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Speed changes have exactly two things to get right — the output has to be shorter or longer by
 * the right factor, and it has to come out at the same pitch.
 */
class DesktopAudioSpeedTest {

    private val rate = 44_100
    private val channels = 2

    @Test
    fun `unity speed passes the samples through untouched`() {
        val stretch = DesktopAudioSpeed(channels, rate)
        val input = tone(seconds = 0.5f, hz = 440.0)

        val out = stretch.process(input, input.size)

        assertEquals(input.size, stretch.outputCount)
        assertTrue(out.contentEquals(input), "unity speed altered the samples")
    }

    @Test
    fun `double speed halves the length and keeps the pitch`() {
        val stretch = DesktopAudioSpeed(channels, rate).apply { speed = 2f }
        val input = tone(seconds = 4f, hz = 440.0)

        val out = stretch.process(input, input.size)
        val frames = stretch.outputCount / channels

        val expected = input.size / channels / 2
        assertTrue(
            abs(frames - expected) < expected * 0.1,
            "produced $frames frames, wanted about $expected",
        )
        assertPitch(out, stretch.outputCount, expected = 440.0)
    }

    @Test
    fun `half speed doubles the length and keeps the pitch`() {
        val stretch = DesktopAudioSpeed(channels, rate).apply { speed = 0.5f }
        val input = tone(seconds = 2f, hz = 440.0)

        val out = stretch.process(input, input.size)
        val frames = stretch.outputCount / channels

        val expected = input.size / channels * 2
        assertTrue(
            abs(frames - expected) < expected * 0.1,
            "produced $frames frames, wanted about $expected",
        )
        assertPitch(out, stretch.outputCount, expected = 440.0)
    }

    @Test
    fun `a stretched tone stays in range and does not go silent`() {
        val stretch = DesktopAudioSpeed(channels, rate).apply { speed = 1.5f }
        val input = tone(seconds = 2f, hz = 220.0)

        val out = stretch.process(input, input.size)

        var peak = 0f
        for (index in 0 until stretch.outputCount) {
            assertTrue(abs(out[index]) < 1.5f, "sample ${out[index]} is far out of range")
            peak = maxOf(peak, abs(out[index]))
        }
        // Overlap-add of a steady tone should hold most of its amplitude; a badly aligned join
        // shows up here as cancellation.
        assertTrue(peak > 0.5f, "peak $peak — the windows are cancelling each other")
    }

    @Test
    fun `feeding it in small blocks gives the same length as one large one`() {
        val input = tone(seconds = 2f, hz = 330.0)

        val whole = DesktopAudioSpeed(channels, rate).apply { speed = 1.25f }
        whole.process(input, input.size)

        val pieces = DesktopAudioSpeed(channels, rate).apply { speed = 1.25f }
        var total = 0
        val block = 4_096
        var at = 0
        while (at < input.size) {
            val length = minOf(block, input.size - at)
            pieces.process(input.copyOfRange(at, at + length), length)
            total += pieces.outputCount
            at += length
        }

        // The tail of the last partial window is held back either way; what matters is that block
        // size does not change the rate of output.
        assertTrue(
            abs(total - whole.outputCount) < whole.outputCount * 0.05,
            "blocked $total against whole ${whole.outputCount}",
        )
    }

    @Test
    fun `back at unity after a stretch, music passes through as a straight copy`() {
        // What a beatmatched Automix handoff leaves the incoming track on: stretched for the blend,
        // eased back to 1x, and still running through the windows.
        val stretch = DesktopAudioSpeed(channels, rate).apply { speed = 1.04f }
        val input = music(seconds = 4f)
        val half = input.size / 2 / channels * channels
        stretch.process(input.copyOfRange(0, half), half)
        stretch.speed = 1f
        val out = stretch.process(input.copyOfRange(half, input.size), input.size - half)
        val count = stretch.outputCount
        assertTrue(count > rate * channels, "too little output at 1x: $count")

        // A transparent 1x is a copy of the input at one fixed shift. Find the shift from the
        // start of the 1x output and hold every later frame to it.
        val frames = count / channels
        val probeAt = frames / 4
        val shift = (0 until input.size / channels - 256).minByOrNull { start ->
            var error = 0f
            for (frame in 0 until 256) error += abs(input[(start + frame) * channels] - out[(probeAt + frame) * channels])
            error
        }!!
        var worst = 0f
        for (frame in probeAt until frames) {
            val at = (shift + frame - probeAt) * channels
            if (at >= input.size) break
            worst = maxOf(worst, abs(input[at] - out[frame * channels]))
        }
        assertTrue(worst < 1e-3f, "1x output wanders off the input by up to $worst")
    }

    /** Several unrelated partials and a beat: enough for a wrong join to show. */
    private fun music(seconds: Float): FloatArray {
        val frames = (rate * seconds).toInt()
        return FloatArray(frames * channels) { index ->
            val t = (index / channels).toDouble() / rate
            val beat = kotlin.math.exp(-((t * 2.1) % 1.0) * 9.0)
            (0.25 * sin(2 * PI * 110.0 * t) + 0.2 * sin(2 * PI * 293.7 * t) * beat +
                0.15 * sin(2 * PI * 523.3 * t + 1.3) + 0.1 * sin(2 * PI * 1187.0 * t) * (1 - beat)).toFloat()
        }
    }

    /** Zero crossings per second on the left channel, which for a sine is its frequency. */
    private fun assertPitch(samples: FloatArray, count: Int, expected: Double) {
        var crossings = 0
        var frame = 1
        val frames = count / channels
        // Skip the first and last window: the very ends are half-faded.
        while (frame < frames - 2_000) {
            if (frame > 2_000) {
                val previous = samples[(frame - 1) * channels]
                val current = samples[frame * channels]
                if (previous <= 0f && current > 0f) crossings++
            }
            frame++
        }
        val measured = crossings.toDouble() * rate / (frames - 4_000)
        assertTrue(
            abs(measured - expected) < expected * 0.1,
            "measured ${measured.toInt()} Hz, wanted $expected",
        )
    }

    private fun tone(seconds: Float, hz: Double): FloatArray {
        val frames = (rate * seconds).toInt()
        return FloatArray(frames * channels) { index ->
            (sin(2.0 * PI * hz * (index / channels) / rate) * 0.8).toFloat()
        }
    }
}
