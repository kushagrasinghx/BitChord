package com.music.bitchord.desktop

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * An Automix blend into a track of another sample rate goes through this, so it has to leave the
 * pitch where it was — the whole bug it exists for was a semitone and a half of drift.
 */
class DesktopRateConverterTest {

    private val channels = 2

    @Test
    fun `48k to 44_1k keeps the pitch and scales the length`() = assertConverts(48_000, 44_100, hz = 1_000.0)

    @Test
    fun `44_1k to 48k keeps the pitch and scales the length`() = assertConverts(44_100, 48_000, hz = 1_000.0)

    @Test
    fun `96k hi-res down to 44_1k keeps the pitch`() = assertConverts(96_000, 44_100, hz = 3_000.0)

    @Test
    fun `small blocks give the same audio as one large one`() {
        val input = tone(48_000, seconds = 1.0, hz = 440.0)
        val whole = DesktopRateConverter(48_000, 44_100, channels)
        val all = whole.process(input, input.size).copyOf(whole.outputCount)

        val pieces = DesktopRateConverter(48_000, 44_100, channels)
        val joined = ArrayList<Float>()
        var at = 0
        while (at < input.size) {
            // Opus-sized blocks, 20ms at a time.
            val length = minOf(960 * channels, input.size - at)
            val out = pieces.process(input.copyOfRange(at, at + length), length)
            for (i in 0 until pieces.outputCount) joined += out[i]
            at += length
        }
        assertTrue(abs(joined.size - all.size) <= channels, "blocked ${joined.size} against whole ${all.size}")
        for (i in 0 until minOf(joined.size, all.size)) {
            assertTrue(abs(joined[i] - all[i]) < 1e-5f, "sample $i differs: ${joined[i]} vs ${all[i]}")
        }
    }

    private fun assertConverts(from: Int, to: Int, hz: Double) {
        val input = tone(from, seconds = 2.0, hz = hz)
        val converter = DesktopRateConverter(from, to, channels)
        val out = converter.process(input, input.size)
        val frames = converter.outputCount / channels

        val expectedFrames = input.size / channels * to.toDouble() / from
        assertTrue(abs(frames - expectedFrames) < to * 0.01, "produced $frames frames, wanted about $expectedFrames")

        // Zero crossings per second on the left channel, read at the output rate.
        var crossings = 0
        var peak = 0f
        for (frame in 2_000 until frames - 2_000) {
            val previous = out[(frame - 1) * channels]
            val current = out[frame * channels]
            if (previous <= 0f && current > 0f) crossings++
            peak = maxOf(peak, abs(current))
        }
        val measured = crossings.toDouble() * to / (frames - 4_000)
        assertTrue(abs(measured - hz) < hz * 0.005, "measured ${"%.1f".format(measured)} Hz, wanted $hz")
        assertTrue(abs(peak - 0.8f) < 0.02f, "level changed: peak $peak, wanted 0.8")
    }

    private fun tone(rate: Int, seconds: Double, hz: Double): FloatArray =
        FloatArray((rate * seconds).toInt() * channels) { index ->
            (sin(2.0 * PI * hz * (index / channels) / rate) * 0.8).toFloat()
        }
}
