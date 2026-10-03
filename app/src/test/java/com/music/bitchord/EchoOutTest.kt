package com.music.bitchord

import com.music.bitchord.playback.TransitionFilterProcessor
import com.music.bitchord.playback.audio.AudioBlock
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The echo-out stage of [TransitionFilterProcessor], driven the way the crossfade drives it. */
class EchoOutTest {

    private val rate = 1_000
    private val block = AudioBlock(channelCount = 1, capacityFrames = 100)

    private fun processor() = TransitionFilterProcessor().apply { configure(rate, 1) }

    /** Runs [frames] of [input] through [filter], one 100-frame block at a time. */
    private fun run(filter: TransitionFilterProcessor, input: FloatArray): FloatArray {
        val out = FloatArray(input.size)
        var at = 0
        while (at < input.size) {
            val frames = minOf(100, input.size - at)
            input.copyInto(block.samples, 0, at, at + frames)
            block.setFrameCount(frames)
            filter.process(block)
            block.samples.copyInto(out, at, 0, frames)
            at += frames
        }
        return out
    }

    @Test
    fun `an idle echo leaves every sample untouched`() {
        val input = FloatArray(500) { (it % 7) / 7f - 0.4f }
        assertArrayEquals(input, run(processor(), input), 0f)
    }

    @Test
    fun `an echo repeats on its delay, each repeat quieter, after the dry signal is gone`() {
        val filter = processor()
        // Ramp the send and dry in, as the controller does, then feed one click.
        filter.setEcho(delaySeconds = 0.1f, send = 1f, dry = 1f)
        run(filter, FloatArray(100))
        val input = FloatArray(1_000).also { it[0] = 1f }
        val out = run(filter, input)
        assertEquals("the dry click passes", 1f, out[0], 1e-6f)
        val first = out[100]
        val second = out[200]
        assertTrue("first repeat at the delay: $first", first > 0.3f)
        assertTrue("each repeat quieter than the last: $first then $second", abs(second) < abs(first))

        // Kill the dry signal and the send: new input is silent, the tail rings on.
        filter.setEcho(delaySeconds = 0.1f, send = 0f, dry = 0f)
        run(filter, FloatArray(100))
        val loud = FloatArray(300) { 1f }
        val killed = run(filter, loud)
        assertTrue("no dry signal once killed", killed.all { abs(it) < 0.5f })
    }

    @Test
    fun `parking puts the dry signal back and drops the tail`() {
        val filter = processor()
        filter.setEcho(delaySeconds = 0.1f, send = 1f, dry = 0f)
        run(filter, FloatArray(300) { 1f })
        filter.parkEcho()
        val input = FloatArray(300) { 0.25f }
        assertArrayEquals(input, run(filter, input), 0f)
    }
}
