package com.music.bitchord.playback

import com.music.bitchord.playback.audio.AudioBlock
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class LoudnessProcessorTest {

    private val sampleRate = 44_100

    private fun sine(amplitude: Float, frames: Int = 4096): AudioBlock {
        val block = AudioBlock(channelCount = 2, capacityFrames = frames)
        for (f in 0 until frames) {
            val v = amplitude * sin(2.0 * PI * 440.0 * f / sampleRate).toFloat()
            block.samples[f * 2] = v
            block.samples[f * 2 + 1] = v
        }
        block.setFrameCount(frames)
        return block
    }

    private fun processor(gains: Map<String, Float>) = LoudnessProcessor().apply {
        gainFor = { gains[it] ?: 1f }
        configure(sampleRate, 2)
    }

    private fun peak(block: AudioBlock, fromFrame: Int = 0): Float {
        var p = 0f
        for (i in fromFrame * 2 until block.sampleCount) p = maxOf(p, abs(block.samples[i]))
        return p
    }

    @Test
    fun unityGainLeavesSamplesBitExact() {
        val block = sine(0.9f)
        val before = block.samples.copyOf()
        processor(emptyMap()).apply { track("a", null) }.process(block)
        assertArrayEquals(before, block.samples, 0f)
    }

    @Test
    fun attenuationIsAppliedFromTheFirstSampleOfATrack() {
        val block = sine(0.8f)
        processor(mapOf("a" to 0.5f)).apply { track("a", null) }.process(block)
        assertEquals(0.4f, peak(block), 0.01f)
    }

    @Test
    fun boostNeverPassesFullScale() {
        val block = sine(0.95f)
        processor(mapOf("a" to 1.41f)).apply { track("a", null) }.process(block)
        assertTrue("peak ${peak(block)}", peak(block) <= 1f)
    }

    @Test
    fun blendTrimIsAPlainGainThatNeverReshapesLoudTracks() {
        // The midpoint of a blend: trim 1/sqrt(1.414).
        val trim = 1f / kotlin.math.sqrt(1.4142135f)
        val proc = processor(emptyMap()).apply { track("a", null) }
        proc.setBlendTrim(trim)
        proc.process(sine(0.98f))
        val block = sine(0.98f)
        val before = block.samples.copyOf()
        proc.process(block)
        // Every sample scaled by the same factor: no limiting, no distortion.
        for (i in 0 until block.sampleCount) {
            assertEquals(before[i] * trim, block.samples[i], 1e-4f)
        }
    }

    @Test
    fun gaplessBoundarySwitchesToTheNextTracksGain() {
        val proc = processor(mapOf("a" to 0.5f, "b" to 0.25f)).apply { track("a", "b") }
        proc.process(sine(0.8f))
        proc.onStreamBoundary()
        val block = sine(0.8f)
        proc.process(block)
        assertEquals(0.2f, peak(block), 0.01f)
    }

    @Test
    fun lateFigureGlidesInRatherThanStepping() {
        val gains = mutableMapOf<String, Float>()
        val proc = LoudnessProcessor().apply {
            gainFor = { gains[it] ?: 1f }
            configure(sampleRate, 2)
            track("a", null)
        }
        proc.process(sine(0.8f))
        gains["a"] = 0.5f
        val block = sine(0.8f)
        proc.process(block)
        // The first quarter-cycle still peaks near the old level...
        assertTrue(peak(block, fromFrame = 0).let { it > 0.7f })
        // ...and a few time constants later it has arrived at the new one.
        val settled = sine(0.8f)
        proc.process(settled)
        assertEquals(0.4f, peak(settled, fromFrame = 3000), 0.01f)
    }
}
