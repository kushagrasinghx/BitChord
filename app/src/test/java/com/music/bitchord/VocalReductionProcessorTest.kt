package com.music.bitchord

import com.music.bitchord.playback.StereoVocalReductionProcessor
import com.music.bitchord.playback.audio.AudioBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * The Karaoke vocal-reduction stage in isolation.
 *
 * The fixtures are synthetic on purpose: a centered sine is what a lead
 * vocal panned to the middle is, an anti-phase sine is pure stereo side
 * content, and a centered low sine is the bass and kick the reduction has
 * to leave in the mix. What these prove is the DSP contract — exact
 * passthrough when off, measurable reduction in the vocal band, side and
 * protected content surviving, stability under parameter changes. They
 * cannot prove perceived separation quality on real recordings, which takes
 * listening tests on physical devices.
 */
class VocalReductionProcessorTest {

    private val sampleRate = 44100
    private val amplitude = 0.5f

    /** A stereo block carrying [frames] of a centered (L == R) sine. */
    private fun centeredSine(
        freqHz: Double,
        frames: Int,
        amp: Float = amplitude,
        startFrame: Int = 0,
    ): AudioBlock {
        val block = AudioBlock(channelCount = 2, capacityFrames = frames)
        for (f in 0 until frames) {
            val s = (amp * sin(2.0 * PI * freqHz * (startFrame + f) / sampleRate)).toFloat()
            block.samples[f * 2] = s
            block.samples[f * 2 + 1] = s
        }
        block.reset(frames)
        return block
    }

    /** A stereo block carrying pure stereo side content (R == -L). */
    private fun antiPhaseSine(freqHz: Double, frames: Int, amp: Float = amplitude): AudioBlock {
        val block = AudioBlock(channelCount = 2, capacityFrames = frames)
        for (f in 0 until frames) {
            val s = (amp * sin(2.0 * PI * freqHz * f / sampleRate)).toFloat()
            block.samples[f * 2] = s
            block.samples[f * 2 + 1] = -s
        }
        block.reset(frames)
        return block
    }

    private fun peak(block: AudioBlock, fromFrame: Int = 0): Float {
        var peak = 0f
        for (f in fromFrame until block.frameCount) {
            peak = maxOf(peak, abs(block.samples[f * 2]), abs(block.samples[f * 2 + 1]))
        }
        return peak
    }

    /** Runs [blocks] blocks through [processor] and returns the last output block. */
    private fun settleOn(processor: StereoVocalReductionProcessor, make: (Int) -> AudioBlock, blocks: Int): AudioBlock {
        var last: AudioBlock = make(0)
        for (i in 0 until blocks) {
            last = make(i)
            processor.process(last)
        }
        return last
    }

    // ── Exact passthrough ───────────────────────────────────────────────────

    @Test
    fun `disabled processor is sample identical passthrough`() {
        val processor = StereoVocalReductionProcessor()
        processor.configure(sampleRate, 2)
        processor.setTuning(enabled = false, vocalLevel = 0f)

        val block = AudioBlock(channelCount = 2, capacityFrames = 256)
        for (i in 0 until 256 * 2) {
            // Arbitrary values, including sub-16-bit ones, headroom and extremes.
            block.samples[i] = when (i % 4) {
                0 -> (i + 1) * 0.01f
                1 -> -0.75f
                2 -> 1.0e-7f
                else -> 1.2f
            }
        }
        block.reset(256)
        val expected = block.samples.copyOf()

        processor.process(block)

        for (i in expected.indices) {
            assertEquals("sample $i altered", expected[i], block.samples[i], 0f)
        }
        assertFalse(processor.isShaping)
    }

    @Test
    fun `a never tuned processor defaults to exact passthrough`() {
        val processor = StereoVocalReductionProcessor()
        processor.configure(sampleRate, 2)

        val block = centeredSine(1000.0, 128)
        val expected = block.samples.copyOf()

        processor.process(block)

        for (i in expected.indices) {
            assertEquals("sample $i altered", expected[i], block.samples[i], 0f)
        }
        assertFalse(processor.isShaping)
    }

    @Test
    fun `disabling glides down and is exact again once settled`() {
        val processor = StereoVocalReductionProcessor()
        processor.configure(sampleRate, 2)
        processor.setTuning(enabled = true, vocalLevel = 0f)

        // Glide up to full reduction, then switch off.
        repeat(6) { processor.process(centeredSine(1000.0, 4096)) }
        processor.setTuning(enabled = false, vocalLevel = 0f)
        // Far longer than the ~18 ms glide needs to reach the settled clamp.
        repeat(4) { processor.process(centeredSine(1000.0, 4096)) }

        val block = centeredSine(1000.0, 512)
        val expected = block.samples.copyOf()
        processor.process(block)

        for (i in expected.indices) {
            assertEquals("sample $i altered after settling", expected[i], block.samples[i], 0f)
        }
        assertFalse(processor.isShaping)
    }

    // ── Reduction, protection, side content ─────────────────────────────────

    @Test
    fun `maximum reduction attenuates centered vocal band content`() {
        val processor = StereoVocalReductionProcessor()
        processor.configure(sampleRate, 2)
        processor.setTuning(enabled = true, vocalLevel = 0f)

        val last = settleOn(processor, { centeredSine(1000.0, 4096) }, blocks = 6)

        assertTrue(processor.isShaping)
        val out = peak(last, fromFrame = 2048)
        assertTrue("centered 1 kHz must be strongly reduced (peak $out)", out < 0.05f * amplitude)
    }

    @Test
    fun `stereo side content survives maximum reduction exactly`() {
        val processor = StereoVocalReductionProcessor()
        processor.configure(sampleRate, 2)
        processor.setTuning(enabled = true, vocalLevel = 0f)

        settleOn(processor, { antiPhaseSine(1000.0, 4096) }, blocks = 6)
        val block = antiPhaseSine(1000.0, 512)
        val expected = block.samples.copyOf()

        processor.process(block)

        // Anti-phase content has no mid to cancel, so not one sample may move.
        for (i in expected.indices) {
            assertEquals("side sample $i altered", expected[i], block.samples[i], 0f)
        }
    }

    @Test
    fun `centered low frequency content is protected`() {
        val processor = StereoVocalReductionProcessor()
        processor.configure(sampleRate, 2)
        processor.setTuning(enabled = true, vocalLevel = 0f)

        val last = settleOn(processor, { centeredSine(60.0, 4096) }, blocks = 6)

        val out = peak(last, fromFrame = 2048)
        assertTrue("centered 60 Hz must survive maximum reduction (peak $out)", out > 0.7f * amplitude)
    }

    @Test
    fun `centered content above the reduction band is protected`() {
        val processor = StereoVocalReductionProcessor()
        processor.configure(sampleRate, 2)
        processor.setTuning(enabled = true, vocalLevel = 0f)

        val last = settleOn(processor, { centeredSine(12000.0, 4096) }, blocks = 6)

        val out = peak(last, fromFrame = 2048)
        assertTrue("centered 12 kHz (air) must survive maximum reduction (peak $out)", out > 0.7f * amplitude)
    }

    @Test
    fun `intermediate levels give intermediate attenuation`() {
        val processor = StereoVocalReductionProcessor()
        processor.configure(sampleRate, 2)
        processor.setTuning(enabled = true, vocalLevel = 0.5f)

        val last = settleOn(processor, { centeredSine(1000.0, 4096) }, blocks = 6)

        val out = peak(last, fromFrame = 2048)
        assertTrue("level 0.5 must land between the extremes (peak $out)", out in 0.25f * amplitude..0.75f * amplitude)
    }

    @Test
    fun `full level is the original mix`() {
        val processor = StereoVocalReductionProcessor()
        processor.configure(sampleRate, 2)
        processor.setTuning(enabled = true, vocalLevel = 1f)

        // Target suppression is zero, so the stage settles to exact passthrough.
        repeat(6) { processor.process(centeredSine(1000.0, 4096)) }

        val block = centeredSine(1000.0, 512)
        val expected = block.samples.copyOf()
        processor.process(block)

        for (i in expected.indices) {
            assertEquals("sample $i altered at level 1", expected[i], block.samples[i], 0f)
        }
    }

    // ── Clamping ────────────────────────────────────────────────────────────

    @Test
    fun `out of range levels are clamped`() {
        val above = StereoVocalReductionProcessor()
        above.configure(sampleRate, 2)
        above.setTuning(enabled = true, vocalLevel = 1.5f)
        repeat(6) { above.process(centeredSine(1000.0, 4096)) }
        val atOne = centeredSine(1000.0, 512)
        above.process(atOne)
        // 1.5 clamps to 1: the original mix, not a boost.
        assertTrue("level above 1 must behave as the original mix (peak ${peak(atOne)})", peak(atOne) > 0.9f * amplitude)

        val below = StereoVocalReductionProcessor()
        below.configure(sampleRate, 2)
        below.setTuning(enabled = true, vocalLevel = -0.5f)
        val last = settleOn(below, { centeredSine(1000.0, 4096) }, blocks = 6)
        val atZero = peak(last, fromFrame = 2048)
        // -0.5 clamps to 0: maximum reduction, same as the default level.
        assertTrue("level below 0 must behave as maximum reduction (peak $atZero)", atZero < 0.05f * amplitude)
    }

    // ── Unsupported layouts ──────────────────────────────────────────────────

    @Test
    fun `mono input is unchanged and not shaping`() {
        val processor = StereoVocalReductionProcessor()
        processor.configure(sampleRate, 1)
        processor.setTuning(enabled = true, vocalLevel = 0f)

        val frames = 256
        val block = AudioBlock(channelCount = 1, capacityFrames = frames)
        for (f in 0 until frames) {
            block.samples[f] = (amplitude * sin(2.0 * PI * 1000.0 * f / sampleRate)).toFloat()
        }
        block.reset(frames)
        val expected = block.samples.copyOf()

        processor.process(block)

        for (i in expected.indices) {
            assertEquals("mono sample $i altered", expected[i], block.samples[i], 0f)
        }
        assertFalse(processor.isShaping)
    }

    @Test
    fun `multichannel input is unchanged and not shaping`() {
        val processor = StereoVocalReductionProcessor()
        processor.configure(sampleRate, 6)
        processor.setTuning(enabled = true, vocalLevel = 0f)

        val frames = 128
        val block = AudioBlock(channelCount = 6, capacityFrames = frames)
        for (i in 0 until frames * 6) {
            block.samples[i] = (i + 1) * 0.01f
        }
        block.reset(frames)
        val expected = block.samples.copyOf()

        processor.process(block)

        for (i in expected.indices) {
            assertEquals("multichannel sample $i altered", expected[i], block.samples[i], 0f)
        }
        assertFalse(processor.isShaping)
    }

    // ── Stability under parameter changes ───────────────────────────────────

    @Test
    fun `enable and disable transitions are click free`() {
        val processor = StereoVocalReductionProcessor()
        processor.configure(sampleRate, 2)

        // A discontinuity the size of the removed signal would step by up to
        // the full amplitude between adjacent samples; a glide keeps the step
        // at the sine's own slope (≈ amp·2π·f/fs ≈ 0.07 here). The input is
        // continuous across blocks, as decoded audio is: a phase reset per
        // block would be a step of the test's own making.
        val maxLegitimateStep = 0.25f
        var frame = 0

        fun runAndCheck(enabled: Boolean, level: Float) {
            processor.setTuning(enabled, level)
            repeat(8) {
                val block = centeredSine(1000.0, 1024, startFrame = frame)
                frame += 1024
                processor.process(block)
                var previous = block.samples[0]
                for (i in 1 until block.frameCount * 2) {
                    val step = abs(block.samples[i] - previous)
                    assertTrue("step $step at sample $i is a click", step < maxLegitimateStep)
                    previous = block.samples[i]
                    assertFalse("sample $i is not finite", block.samples[i].isNaN() || block.samples[i].isInfinite())
                }
            }
        }

        runAndCheck(enabled = false, level = 0f)
        runAndCheck(enabled = true, level = 0f)
        runAndCheck(enabled = true, level = 0.3f)
        runAndCheck(enabled = true, level = 1f)
        runAndCheck(enabled = false, level = 0f)
    }

    @Test
    fun `slider sweeps are click free`() {
        val processor = StereoVocalReductionProcessor()
        processor.configure(sampleRate, 2)

        val maxLegitimateStep = 0.25f
        // Drag the slider from max reduction to original and back, a block at a time.
        val levels = buildList {
            repeat(10) { add(0f) }
            var level = 0f
            while (level < 1f) {
                level = (level + 0.1f).coerceAtMost(1f)
                add(level)
            }
            while (level > 0f) {
                level = (level - 0.1f).coerceAtLeast(0f)
                add(level)
            }
            repeat(10) { add(0f) }
        }
        var frame = 0
        for (level in levels) {
            processor.setTuning(enabled = true, vocalLevel = level)
            val block = centeredSine(1000.0, 1024, startFrame = frame)
            frame += 1024
            processor.process(block)
            var previous = block.samples[0]
            for (i in 1 until block.frameCount * 2) {
                val step = abs(block.samples[i] - previous)
                assertTrue("step $step at level $level is a click", step < maxLegitimateStep)
                previous = block.samples[i]
            }
        }
    }

    @Test
    fun `repeated blocks stay finite under mixed content`() {
        val processor = StereoVocalReductionProcessor()
        processor.configure(sampleRate, 2)
        processor.setTuning(enabled = true, vocalLevel = 0f)

        val frames = 1024
        for (iteration in 0 until 100) {
            val block = AudioBlock(channelCount = 2, capacityFrames = frames)
            for (f in 0 until frames) {
                val t = (iteration * frames + f).toDouble()
                val center = 0.4 * sin(2.0 * PI * 1000.0 * t / sampleRate) +
                    0.3 * sin(2.0 * PI * 60.0 * t / sampleRate)
                val side = 0.2 * sin(2.0 * PI * 3000.0 * t / sampleRate)
                block.samples[f * 2] = (center + side).toFloat()
                block.samples[f * 2 + 1] = (center - side).toFloat()
            }
            block.reset(frames)

            processor.process(block)

            for (i in 0 until frames * 2) {
                val v = block.samples[i]
                assertFalse("NaN at block $iteration sample $i", v.isNaN())
                assertFalse("Infinity at block $iteration sample $i", v.isInfinite())
                assertTrue("runaway value $v at block $iteration", abs(v) < 2f)
            }
        }
    }

    // ── Flush, reset, reconfiguration ───────────────────────────────────────

    @Test
    fun `flush clears filter history`() {
        val processor = StereoVocalReductionProcessor()
        processor.configure(sampleRate, 2)
        processor.setTuning(enabled = true, vocalLevel = 0f)
        processor.process(centeredSine(1000.0, 4096))

        processor.flush()

        // With the history gone, silence in must come out exactly zero: a
        // ringing filter would leak its past into these samples.
        val silence = AudioBlock(channelCount = 2, capacityFrames = 256)
        silence.reset(256)
        processor.process(silence)
        for (i in 0 until 256 * 2) {
            assertEquals("silence rang after flush", 0f, silence.samples[i], 0f)
        }
    }

    @Test
    fun `reset clears configuration to passthrough`() {
        val processor = StereoVocalReductionProcessor()
        processor.configure(sampleRate, 2)
        processor.setTuning(enabled = true, vocalLevel = 0f)
        processor.process(centeredSine(1000.0, 1024))

        processor.reset()

        val block = centeredSine(1000.0, 256)
        val expected = block.samples.copyOf()
        processor.process(block)
        for (i in expected.indices) {
            assertEquals("sample $i altered after reset", expected[i], block.samples[i], 0f)
        }
        assertFalse(processor.isShaping)

        // And it comes back: configured again, the reduction works as before.
        processor.configure(sampleRate, 2)
        val last = settleOn(processor, { centeredSine(1000.0, 4096) }, blocks = 6)
        assertTrue(peak(last, fromFrame = 2048) < 0.05f * amplitude)
    }

    @Test
    fun `sample rate and channel reconfiguration stays stable`() {
        val processor = StereoVocalReductionProcessor()
        processor.configure(sampleRate, 2)
        processor.setTuning(enabled = true, vocalLevel = 0f)

        val at44k = settleOn(processor, { centeredSine(1000.0, 4096) }, blocks = 4)
        assertTrue(peak(at44k, fromFrame = 2048) < 0.05f * amplitude)

        // New rate: coefficients redesign, history clears, reduction continues.
        processor.configure(48000, 2)
        val at48k = settleOn(processor, { centeredSine(1000.0, 4096) }, blocks = 4)
        assertTrue("reduction lost after reconfigure (peak ${peak(at48k, fromFrame = 2048)})", peak(at48k, fromFrame = 2048) < 0.05f * amplitude)

        // New layout: stereo reduction bows out rather than upmixing.
        processor.configure(48000, 1)
        val mono = AudioBlock(channelCount = 1, capacityFrames = 256)
        for (f in 0 until 256) mono.samples[f] = 0.5f * (f % 64) / 64f
        mono.reset(256)
        val expected = mono.samples.copyOf()
        processor.process(mono)
        for (i in expected.indices) {
            assertEquals("mono sample $i altered after reconfigure", expected[i], mono.samples[i], 0f)
        }
        assertFalse(processor.isShaping)
    }

    // ── Steady state ────────────────────────────────────────────────────────

    @Test
    fun `steady state processing reuses the audio block without allocation`() {
        val processor = StereoVocalReductionProcessor()
        processor.configure(sampleRate, 2)
        processor.setTuning(enabled = true, vocalLevel = 0f)

        val block = centeredSine(1000.0, 128)
        val arrayRef = block.samples

        for (i in 0 until 10) {
            processor.process(block)
            assertTrue("Array reference must be preserved across iterations", block.samples === arrayRef)
        }
    }
}
