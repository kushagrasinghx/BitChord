package com.music.bitchord

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import com.music.bitchord.playback.LoopVampProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The loop vamp repeats a quantized phrase with an internal equal-power
 * micro-crossfade at the wrap, so repeats are continuous with no level dip
 * and no step. These tests pin that contract with deterministic ramps:
 * passthrough identity while parked, exact repeat while engaged, and period
 * halving on re-aim.
 */
@UnstableApi
class LoopVampProcessorTest {

    private val sampleRate = 48_000
    private val channels = 2

    private fun vamp(): LoopVampProcessor {
        val v = LoopVampProcessor()
        v.configure(AudioProcessor.AudioFormat(sampleRate, channels, C.ENCODING_PCM_16BIT))
        return v
    }

    /** Frames of a deterministic ramp (value = frame index), S16LE. */
    private fun rampBuffer(startFrame: Int, frames: Int): ByteBuffer {
        val buf = ByteBuffer.allocateDirect(frames * channels * 2).order(ByteOrder.nativeOrder())
        repeat(frames) { i ->
            val s = ((startFrame + i) % 30_000).toShort()
            repeat(channels) { buf.putShort(s) }
        }
        buf.flip()
        return buf
    }

    private fun drain(v: LoopVampProcessor): ShortArray {
        val out = v.output
        out.order(ByteOrder.nativeOrder())
        val shorts = ShortArray(out.remaining() / 2)
        repeat(shorts.size) { shorts[it] = out.short }
        out.clear()
        return shorts
    }

    /** Feeds [frames] ramp frames in 0.1s chunks, returns all output samples. */
    private fun runFrames(v: LoopVampProcessor, startFrame: Int, frames: Int): ShortArray {
        val collected = ArrayList<Short>(frames * channels)
        var fed = 0
        while (fed < frames) {
            val chunk = minOf(4_800, frames - fed)
            v.queueInput(rampBuffer(startFrame + fed, chunk))
            for (s in drain(v)) collected.add(s)
            fed += chunk
        }
        val out = ShortArray(collected.size)
        for (i in collected.indices) out[i] = collected[i]
        return out
    }

    private fun channel0(shorts: ShortArray): IntArray {
        val frames = shorts.size / channels
        return IntArray(frames) { shorts[it * channels].toInt() }
    }

    @Test
    fun `parked vamp passes audio through bit-identical`() {
        val v = vamp()
        val out = channel0(runFrames(v, 0, 9_600))
        repeat(out.size) { i ->
            assertEquals((i % 30_000), out[i])
        }
    }

    @Test
    fun `engaged loop repeats the live pass`() {
        val v = vamp()
        // 4 beats @120 BPM, 0.5 s/beat = 2 s = 96 000 frames.
        v.setVampLoop(4f, 0.5f)
        val loopLen = 96_000
        // First pass plays live while captured (skipping the 8 ms
        // wrap-blend that voices at its tail)…
        val first = channel0(runFrames(v, 0, loopLen))
        for (i in 0 until loopLen - 1_000) {
            assertEquals((i % 30_000), first[i])
        }
        // …second pass repeats it (skipping the 8 ms wrap-blend tail).
        val second = channel0(runFrames(v, loopLen, loopLen))
        for (i in 1_000 until loopLen - 1_000) {
            assertEquals(first[i], second[i])
        }
    }

    @Test
    fun `halving shortens the repeat period`() {
        val v = vamp()
        v.setVampLoop(4f, 0.5f)
        val loopLen = 96_000
        var cursor = 0
        runFrames(v, cursor, loopLen).also { cursor += loopLen }
        runFrames(v, cursor, loopLen).also { cursor += loopLen }
        // Halve to 2 beats (48 000 frames): let the current pass drain,
        // then the new period voices.
        v.setVampLoop(2f, 0.5f)
        runFrames(v, cursor, loopLen).also { cursor += loopLen }
        val steady = channel0(runFrames(v, cursor, 96_000))
        // The new period voices after the transitional pass: assert
        // periodicity with a mismatch budget that absorbs the 8 ms
        // wrap blends (a broken loop mismatches ~everything).
        val half = 48_000
        var mismatches = 0
        var total = 0
        var base = 4_000
        while (base + half + (half - 8_000) <= steady.size) {
            for (i in 0 until half - 8_000) {
                total++
                if (steady[base + i] != steady[base + half + i]) mismatches++
            }
            base += half
        }
        assertTrue("expected at least one full halved pass", total > 0)
        assertTrue(
            "loop not periodic after halving: $mismatches/$total",
            mismatches.toDouble() / total < 0.05,
        )
    }

    @Test
    fun `open disengages back to passthrough`() {
        val v = vamp()
        v.setVampLoop(4f, 0.5f)
        runFrames(v, 0, 96_000)
        v.open()
        val out = channel0(runFrames(v, 500_000, 4_800))
        repeat(out.size) { i ->
            assertEquals(((500_000 + i) % 30_000), out[i])
        }
    }
}
