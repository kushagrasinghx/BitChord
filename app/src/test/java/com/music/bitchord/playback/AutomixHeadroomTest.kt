package com.music.bitchord.playback

import com.music.bitchord.playback.audio.AudioBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class AutomixHeadroomTest {
    @Test
    fun `loud overlapping masters stay below mixer full scale throughout a fade`() {
        // Both modern masters can peak together. Per-deck limiters cannot catch
        // clipping that happens later, when Android sums the two AudioTracks.
        for (step in 0..100) {
            val incoming = sin(step / 100.0 * PI / 2).toFloat()
            val outgoing = cos(step / 100.0 * PI / 2).toFloat()
            val trim = AutomixHeadroom.trimFor(incoming, outgoing)
            val combinedPeak = 0.98f * trim * (incoming + outgoing)
            assertTrue("Mixer peak $combinedPeak at fade step $step", combinedPeak <= 1f)
        }
    }

    @Test
    fun `headroom scales loud samples uniformly without limiter pumping`() {
        val gain = sin(PI / 4).toFloat()
        val trim = AutomixHeadroom.trimFor(gain, gain)
        val deck = LoudnessProcessor().apply {
            configure(48_000, 2)
            setBlendTrim(trim)
        }
        val block = AudioBlock(channelCount = 2, capacityFrames = 1024)
        val input = FloatArray(2048) { i -> (0.98 * sin(2 * PI * 1000 * (i / 2) / 48_000)).toFloat() }
        input.copyInto(block.samples)
        block.setFrameCount(1024)
        deck.process(block)
        for (i in input.indices) {
            assertEquals(input[i] * trim, block.samples[i], 1e-5f)
            assertTrue(abs(block.samples[i] * gain * 2) <= 1f)
        }
    }
}
