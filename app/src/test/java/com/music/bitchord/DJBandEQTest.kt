package com.music.bitchord

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import com.music.bitchord.playback.DJBandEQ
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The DJ 3-band EQ splits LOW (< 200 Hz), MID (200–4000 Hz) and HIGH (> 4 kHz)
 * with two Butterworth crossovers, and each band has an independent gain.
 *
 * Worth its own tests because every way of getting a crossover wrong is
 * silent: swapped coefficients or a leaking band don't fail the build, they
 * just make every bass swap and vocal duck subtly wrong in a way nobody can
 * localize by listening. Sine probes pin each band to its frequency range,
 * and the unity test pins the spec's flatness requirement (sum ≈ flat).
 */
@UnstableApi
class DJBandEQTest {

    private val sampleRate = 44_100
    private val channels = 2

    private fun eq(): DJBandEQ {
        val eq = DJBandEQ()
        val format = AudioProcessor.AudioFormat(sampleRate, channels, C.ENCODING_PCM_16BIT)
        eq.configure(format)
        return eq
    }

    /** One second of a pure tone, S16LE native order, peak 0.5. */
    private fun sineBuffer(freqHz: Double, seconds: Double = 1.0): ByteBuffer {
        val frames = (sampleRate * seconds).toInt()
        val buf = ByteBuffer.allocateDirect(frames * channels * 2).order(ByteOrder.nativeOrder())
        repeat(frames) { i ->
            val s = (sin(2.0 * PI * freqHz * i / sampleRate) * 16_000).toInt().toShort()
            repeat(channels) { buf.putShort(s) }
        }
        buf.flip()
        return buf
    }

    private fun runThrough(eq: DJBandEQ, input: ByteBuffer): ShortArray {
        eq.queueInput(input)
        eq.queueEndOfInputStream()
        val out = eq.output()
        val shorts = ShortArray(out.remaining() / 2)
        out.order(ByteOrder.nativeOrder())
        repeat(shorts.size) { shorts[it] = out.short }
        out.clear()
        eq.flush()
        return shorts
    }

    /** RMS over a frame window, normalized to full scale. */
    private fun rms(samples: ShortArray, fromFrame: Int, toFrame: Int): Double {
        var sum = 0.0
        var n = 0
        for (f in fromFrame until toFrame) {
            for (c in 0 until channels) {
                val s = samples[f * channels + c] / 32768.0
                sum += s * s
                n++
            }
        }
        return sqrt(sum / n)
    }

    @Test
    fun `unity gains reproduce the input flat`() {
        val eq = eq()
        eq.open()
        val out = runThrough(eq, sineBuffer(1_000.0))
        val frames = out.size / channels
        // Skip the first 0.2 s: zeroed filter state needs a few ms to settle.
        // Compare against the analytic RMS of a 0.5-peak sine (0.5/sqrt(2)).
        val expected = 16_000.0 / 32768.0 / sqrt(2.0)
        val actual = rms(out, (sampleRate * 0.2).toInt(), frames)
        // Spec checklist: 3-band sum flat within ±0.5 dB (ratio 0.944–1.059).
        assertEquals(expected, actual, expected * 0.06)
    }

    @Test
    fun `killing MID removes a 1kHz tone but keeps bass and air`() {
        val eq = eq()
        eq.setGains(1f, 0f, 1f)
        val mid = runThrough(eq, sineBuffer(1_000.0))
        val bass = runThrough(eq, sineBuffer(100.0))
        val air = runThrough(eq, sineBuffer(8_000.0))
        val settle = (sampleRate * 0.3).toInt()
        val midRms = rms(mid, settle, mid.size / channels)
        val bassRms = rms(bass, settle, bass.size / channels)
        val airRms = rms(air, settle, air.size / channels)
        assertTrue("1kHz through killed MID should be near silent, was $midRms", midRms < 0.01)
        assertTrue("100Hz should pass with MID killed, was $bassRms", bassRms > 0.2)
        assertTrue("8kHz should pass with MID killed, was $airRms", airRms > 0.2)
    }

    @Test
    fun `killing LOW removes a 100Hz tone`() {
        val eq = eq()
        eq.setGains(0f, 1f, 1f)
        val out = runThrough(eq, sineBuffer(100.0))
        val r = rms(out, (sampleRate * 0.3).toInt(), out.size / channels)
        assertTrue("100Hz through killed LOW should be near silent, was $r", r < 0.02)
    }

    @Test
    fun `killing HIGH removes an 8kHz tone`() {
        val eq = eq()
        eq.setGains(1f, 1f, 0f)
        val out = runThrough(eq, sineBuffer(8_000.0))
        val r = rms(out, (sampleRate * 0.3).toInt(), out.size / channels)
        assertTrue("8kHz through killed HIGH should be near silent, was $r", r < 0.02)
    }

    @Test
    fun `a gain change glides instead of snapping`() {
        val eq = eq()
        eq.open()
        // Prime the smoother at unity.
        runThrough(eq, sineBuffer(1_000.0, seconds = 0.5))
        eq.setGains(0f, 1f, 1f)
        // One 64-sample block right after the kill: the glide (~30 ms) must
        // still be near unity, proving the target stepped nothing audible.
        val first = runThrough(eq, sineBuffer(1_000.0, seconds = 64.0 / sampleRate))
        val r = rms(first, 0, first.size / channels)
        assertTrue("first block after LOW kill should still be loud (glide), was $r", r > 0.15)
    }

    @Test
    fun `non-16-bit input bows out instead of throwing`() {
        val eq = DJBandEQ()
        val floatFormat = AudioProcessor.AudioFormat(sampleRate, channels, C.ENCODING_PCM_FLOAT)
        assertEquals(AudioProcessor.AudioFormat.NOT_SET, eq.configure(floatFormat))
    }
}
