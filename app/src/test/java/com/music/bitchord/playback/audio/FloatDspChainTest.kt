package com.music.bitchord.playback.audio

import com.music.bitchord.playback.EqCurve
import com.music.bitchord.playback.EqLayout
import com.music.bitchord.playback.EqualizerProcessor
import com.music.bitchord.playback.SpatialAudioProcessor
import com.music.bitchord.playback.TransitionFilterProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Comprehensive verification of the Float32 DSP processors:
 * - EqualizerProcessor (FloatAudioProcessor)
 * - SpatialAudioProcessor (FloatAudioProcessor)
 * - TransitionFilterProcessor (FloatAudioProcessor)
 * - DspChain composite pipeline
 */
class FloatDspChainTest {

    private val sampleRate = 44100

    // ── EqualizerProcessor Float32 Tests ────────────────────────────────────

    @Test
    fun `eq impulse response rings with biquad filter decay`() {
        val eq = EqualizerProcessor()
        eq.configure(sampleRate, 2)

        // Boost 1 kHz band by +6 dB
        val gains = FloatArray(EqLayout.SLOTS)
        val qs = FloatArray(EqLayout.SLOTS) { 0.707f }
        gains[3] = 6.0f // 1000 Hz bell (slot 3)
        eq.setTuning(enabled = true, EqCurve(gains, qs, 0f), balance = 0f)
        eq.flush()

        val frames = 256
        val block = AudioBlock(channelCount = 2, capacityFrames = frames)
        block.samples[0] = 1.0f // Left impulse
        block.samples[1] = 0.0f
        block.reset(frames)

        eq.process(block)

        // The impulse response must be non-zero past frame 0 (filtering effect)
        var nonZeroCount = 0
        for (f in 1 until frames) {
            if (abs(block.samples[f * 2]) > 1e-4f) nonZeroCount++
        }
        assertTrue("Filter impulse response must ring over multiple frames", nonZeroCount > 10)
    }

    @Test
    fun `eq gain response lifts centre frequency by expected decibels`() {
        val eq = EqualizerProcessor()
        eq.configure(sampleRate, 2)

        val boostDb = 6.0f
        val gains = FloatArray(EqLayout.SLOTS)
        val qs = FloatArray(EqLayout.SLOTS) { 0.707f }
        val targetSlot = 3 // 1000 Hz bell (slot 3 in EqLayout)
        gains[targetSlot] = boostDb
        eq.setTuning(enabled = true, EqCurve(gains, qs, 0f), balance = 0f)
        eq.flush()

        // Generate 1 kHz sine wave
        val frames = 4096
        val block = AudioBlock(channelCount = 2, capacityFrames = frames)
        val freq = 1000.0
        val inputAmp = 0.25f
        for (f in 0 until frames) {
            val s = (inputAmp * sin(2.0 * PI * freq * f / sampleRate)).toFloat()
            block.samples[f * 2] = s
            block.samples[f * 2 + 1] = s
        }
        block.reset(frames)

        eq.process(block)

        // Measure steady-state peak amplitude in the second half of the block
        var peakOut = 0f
        for (f in 2048 until frames) {
            val amp = abs(block.samples[f * 2])
            if (amp > peakOut) peakOut = amp
        }

        val expectedGainLinear = Math.pow(10.0, (boostDb / 20.0)).toFloat()
        val expectedPeak = inputAmp * expectedGainLinear
        assertEquals("1 kHz center must be amplified by ~+6 dB", expectedPeak, peakOut, 0.08f)
    }

    @Test
    fun `eq multiple simultaneous bands boost both low and high ends`() {
        val eq = EqualizerProcessor()
        eq.configure(sampleRate, 2)

        val gains = FloatArray(EqLayout.SLOTS)
        val qs = FloatArray(EqLayout.SLOTS) { 0.707f }
        gains[0] = 6.0f // 60 Hz shelf
        gains[9] = 6.0f // 14 kHz shelf
        eq.setTuning(enabled = true, EqCurve(gains, qs, 0f), balance = 0f)
        eq.flush()

        val frames = 1024
        val block = AudioBlock(channelCount = 2, capacityFrames = frames)
        // Composite signal: 60 Hz + 14000 Hz
        for (f in 0 until frames) {
            val s = (0.2 * sin(2.0 * PI * 60.0 * f / sampleRate) +
                    0.2 * sin(2.0 * PI * 14000.0 * f / sampleRate)).toFloat()
            block.samples[f * 2] = s
            block.samples[f * 2 + 1] = s
        }
        block.reset(frames)

        eq.process(block)

        var maxAmp = 0f
        for (f in 512 until frames) {
            val amp = abs(block.samples[f * 2])
            if (amp > maxAmp) maxAmp = amp
        }
        assertTrue("Simultaneous shelves must increase composite peak energy", maxAmp > 0.4f)
    }

    @Test
    fun `eq bypass when flat passes samples through untouched`() {
        val eq = EqualizerProcessor()
        eq.configure(sampleRate, 2)
        eq.setTuning(enabled = false, EqCurve.FLAT, balance = 0f)
        eq.flush()

        val frames = 64
        val block = AudioBlock(channelCount = 2, capacityFrames = frames)
        for (i in 0 until frames * 2) {
            block.samples[i] = (i + 1) * 0.01f
        }
        block.reset(frames)

        eq.process(block)

        for (i in 0 until frames * 2) {
            assertEquals((i + 1) * 0.01f, block.samples[i], 0.0f)
        }
    }

    @Test
    fun `eq maintains stereo channel independence`() {
        val eq = EqualizerProcessor()
        eq.configure(sampleRate, 2)

        val gains = FloatArray(EqLayout.SLOTS) { 3f }
        val qs = FloatArray(EqLayout.SLOTS) { 0.707f }
        eq.setTuning(enabled = true, EqCurve(gains, qs, 0f), balance = 0f)
        eq.flush()

        val frames = 256
        val block = AudioBlock(channelCount = 2, capacityFrames = frames)
        // Left has signal, Right is silence
        for (f in 0 until frames) {
            block.samples[f * 2] = 0.5f
            block.samples[f * 2 + 1] = 0.0f
        }
        block.reset(frames)

        eq.process(block)

        for (f in 0 until frames) {
            assertEquals(0.0f, block.samples[f * 2 + 1], 0.0f)
        }
    }

    @Test
    fun `eq preserves float32 values below 16-bit pcm resolution`() {
        val eq = EqualizerProcessor()
        eq.configure(sampleRate, 2)

        // 16-bit resolution floor is ~3.05e-5. Test with 1.0e-7.
        val tinyVal = 1.0e-7f
        val gains = FloatArray(EqLayout.SLOTS)
        val qs = FloatArray(EqLayout.SLOTS) { 0.707f }
        gains[5] = 6.0f
        eq.setTuning(enabled = true, EqCurve(gains, qs, 0f), balance = 0f)
        eq.flush()

        val frames = 128
        val block = AudioBlock(channelCount = 2, capacityFrames = frames)
        for (f in 0 until frames) {
            block.samples[f * 2] = tinyVal
            block.samples[f * 2 + 1] = tinyVal
        }
        block.reset(frames)

        eq.process(block)

        // In 16-bit PCM this would quantize to 0. In Float32 it must remain non-zero.
        var hasNonZero = false
        for (f in 0 until frames) {
            if (block.samples[f * 2] != 0.0f) hasNonZero = true
        }
        assertTrue("Sub-PCM16 Float32 values must not be truncated to zero", hasNonZero)
    }

    // ── SpatialAudioProcessor Float32 Tests ─────────────────────────────────

    @Test
    fun `spatial crossfeed leaks delayed and low-passed signal to opposite channel`() {
        val spatial = SpatialAudioProcessor()
        spatial.configure(sampleRate, 2)
        spatial.enabled = true

        // 15ms delay at 44.1kHz is ~661 frames
        val frames = 1000
        val block = AudioBlock(channelCount = 2, capacityFrames = frames)
        // Feed only Left channel with a 500 Hz sine
        for (f in 0 until frames) {
            block.samples[f * 2] = (0.5 * sin(2.0 * PI * 500.0 * f / sampleRate)).toFloat()
            block.samples[f * 2 + 1] = 0.0f
        }
        block.reset(frames)

        spatial.process(block)

        // After the delay buffer fills (>700 frames), the right channel must have crossfeed signal
        var rightEnergy = 0.0
        for (f in 700 until frames) {
            rightEnergy += abs(block.samples[f * 2 + 1].toDouble())
        }
        assertTrue("Crossfeed must produce non-zero signal on right channel", rightEnergy > 1.0)
    }

    @Test
    fun `spatial bypass passes samples through untouched when disabled`() {
        val spatial = SpatialAudioProcessor()
        spatial.configure(sampleRate, 2)
        spatial.enabled = false

        val frames = 64
        val block = AudioBlock(channelCount = 2, capacityFrames = frames)
        for (i in 0 until frames * 2) {
            block.samples[i] = (i + 1) * 0.01f
        }
        block.reset(frames)

        spatial.process(block)

        for (i in 0 until frames * 2) {
            assertEquals((i + 1) * 0.01f, block.samples[i], 0.0f)
        }
    }

    // ── TransitionFilterProcessor Float32 Tests ─────────────────────────────

    @Test
    fun `transition low pass attenuates high frequency signal`() {
        val transition = TransitionFilterProcessor()
        transition.configure(sampleRate, 2)
        transition.setCutoffs(lowPassHz = 500f, highPassHz = TransitionFilterProcessor.OFF_HZ)
        transition.flush()

        // Feed 5 kHz sine wave
        val frames = 1024
        val block = AudioBlock(channelCount = 2, capacityFrames = frames)
        val freq = 5000.0
        for (f in 0 until frames) {
            val s = sin(2.0 * PI * freq * f / sampleRate).toFloat()
            block.samples[f * 2] = s
            block.samples[f * 2 + 1] = s
        }
        block.reset(frames)

        transition.process(block)

        // 5 kHz is 10x above 500 Hz cutoff; with 24 dB/octave it should be heavily attenuated
        var peakOut = 0f
        for (f in 512 until frames) {
            val amp = abs(block.samples[f * 2])
            if (amp > peakOut) peakOut = amp
        }
        assertTrue("5 kHz through 500 Hz low-pass must be attenuated (got peak $peakOut)", peakOut < 0.1f)
    }

    @Test
    fun `transition high pass attenuates low frequency signal`() {
        val transition = TransitionFilterProcessor()
        transition.configure(sampleRate, 2)
        transition.setCutoffs(lowPassHz = TransitionFilterProcessor.OPEN_HZ, highPassHz = 2000f)
        transition.flush()

        // Feed 100 Hz sine wave
        val frames = 1024
        val block = AudioBlock(channelCount = 2, capacityFrames = frames)
        val freq = 100.0
        for (f in 0 until frames) {
            val s = sin(2.0 * PI * freq * f / sampleRate).toFloat()
            block.samples[f * 2] = s
            block.samples[f * 2 + 1] = s
        }
        block.reset(frames)

        transition.process(block)

        var peakOut = 0f
        for (f in 512 until frames) {
            val amp = abs(block.samples[f * 2])
            if (amp > peakOut) peakOut = amp
        }
        assertTrue("100 Hz through 2000 Hz high-pass must be attenuated (got peak $peakOut)", peakOut < 0.1f)
    }

    @Test
    fun `transition bypass passes samples through untouched when open`() {
        val transition = TransitionFilterProcessor()
        transition.configure(sampleRate, 2)
        transition.open()
        transition.flush()

        val frames = 64
        val block = AudioBlock(channelCount = 2, capacityFrames = frames)
        for (i in 0 until frames * 2) {
            block.samples[i] = (i + 1) * 0.01f
        }
        block.reset(frames)

        transition.process(block)

        for (i in 0 until frames * 2) {
            assertEquals((i + 1) * 0.01f, block.samples[i], 0.0f)
        }
    }

    // ── Cross-Processor (DspChain) Tests ────────────────────────────────────

    @Test
    fun `dsp chain preserves float32 precision across all three processors without intermediate quantization`() {
        val chain = DspChain()
        chain.configure(sampleRate, 2)

        // Enable spatial, set flat EQ, open transition
        chain.spatial.enabled = true
        chain.equalizer.setTuning(enabled = true, EqCurve.FLAT, balance = 0f)
        chain.transition.open()
        chain.flush()

        // Feed tiny sub-LSB float (1.0e-7)
        val tinyVal = 1.0e-7f
        val frames = 128
        val block = AudioBlock(channelCount = 2, capacityFrames = frames)
        for (f in 0 until frames) {
            block.samples[f * 2] = tinyVal
            block.samples[f * 2 + 1] = tinyVal
        }
        block.reset(frames)

        chain.process(block)

        // Must remain non-zero through all 3 stages
        var hasNonZero = false
        for (f in 0 until frames) {
            if (block.samples[f * 2] != 0.0f) hasNonZero = true
        }
        assertTrue("Sub-LSB Float32 signal must survive entire DSP chain", hasNonZero)
    }

    @Test
    fun `dsp chain preserves headroom without clipping above 1_0`() {
        val chain = DspChain()
        chain.configure(sampleRate, 2)

        chain.spatial.enabled = false
        // Flat EQ with +3 dB preamp
        val gains = FloatArray(EqLayout.SLOTS)
        val qs = FloatArray(EqLayout.SLOTS) { 0.707f }
        chain.equalizer.setTuning(enabled = true, EqCurve(gains, qs, preampDb = 3.0f), balance = 0f)
        chain.transition.open()
        chain.flush()

        val frames = 64
        val block = AudioBlock(channelCount = 2, capacityFrames = frames)
        // Feed 0.9f; +3 dB preamp will lift it to ~1.27f (exceeding nominal 1.0f)
        for (i in 0 until frames * 2) {
            block.samples[i] = 0.9f
        }
        block.reset(frames)

        chain.process(block)

        // Must NOT be clipped to 1.0f
        val sample = block.samples[frames * 2 - 2]
        assertTrue("Headroom must be preserved above 1.0f (got $sample)", sample > 1.1f)
    }

    @Test
    fun `zero length input is handled safely across entire chain`() {
        val chain = DspChain()
        chain.configure(sampleRate, 2)
        val block = AudioBlock(channelCount = 2, capacityFrames = 64)
        block.reset(0)

        // Must return without error
        chain.process(block)
        assertEquals(0, block.frameCount)
    }

    @Test
    fun `repeated block processing maintains filter continuity and stability`() {
        val chain = DspChain()
        chain.configure(sampleRate, 2)

        chain.spatial.enabled = true
        val gains = FloatArray(EqLayout.SLOTS)
        val qs = FloatArray(EqLayout.SLOTS) { 0.707f }
        gains[2] = 4.0f
        chain.equalizer.setTuning(enabled = true, EqCurve(gains, qs, 0f), balance = 0f)
        chain.transition.setCutoffs(lowPassHz = 8000f, highPassHz = 100f)

        val blockSize = 512
        val block = AudioBlock(channelCount = 2, capacityFrames = blockSize)

        // Run 50 consecutive blocks (1 second of audio)
        for (iteration in 0 until 50) {
            for (f in 0 until blockSize) {
                val totalFrame = iteration * blockSize + f
                val s = sin(2.0 * PI * 440.0 * totalFrame / sampleRate).toFloat()
                block.samples[f * 2] = s
                block.samples[f * 2 + 1] = s
            }
            block.reset(blockSize)
            chain.process(block)

            // Verify no NaN or Infinity emerged
            for (i in 0 until blockSize * 2) {
                val v = block.samples[i]
                assertFalse("Sample must not be NaN at block $iteration", v.isNaN())
                assertFalse("Sample must not be Infinite at block $iteration", v.isInfinite())
            }
        }
    }

    @Test
    fun `steady state processing reuses audio block samples array without allocation`() {
        val chain = DspChain()
        chain.configure(sampleRate, 2)

        val block = AudioBlock(channelCount = 2, capacityFrames = 128)
        val arrayRef = block.samples

        for (i in 0 until 10) {
            block.reset(128)
            chain.process(block)
            assertTrue("Array reference must be preserved across iterations", block.samples === arrayRef)
        }
    }
}
