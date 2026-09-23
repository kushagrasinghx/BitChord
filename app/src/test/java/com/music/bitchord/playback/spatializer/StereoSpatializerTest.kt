package com.music.bitchord.playback.spatializer

import com.music.bitchord.playback.SpatialAudioProcessor
import com.music.bitchord.playback.SpatialMode
import com.music.bitchord.playback.audio.AudioBlock
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stereo Spatialization end to end.
 *
 * `golden_<rate>.bin` holds windows of the expected output of the upmix + convolution chain (before the output
 * stage), computed offline by a separate floating-point implementation of the same chain, for a deterministic test
 * signal that [testSignal] rebuilds sample for sample. The spatializer is fed that signal in awkward block sizes, so
 * the hop buffering is exercised too, and must land on the same samples.
 */
class StereoSpatializerTest {

    private fun moduleFile(path: String): File {
        val root = File(".").canonicalFile
        return listOf(File(root, "app/$path"), File(root, path)).first { it.exists() }
    }

    private fun shippedResponses(rate: Int): SpeakerResponses =
        moduleFile("src/main/assets/spatializer/speakers_$rate.bin").inputStream().use { SpeakerResponses.read(it) }

    /** The deterministic test signal behind the golden files (LCG noise + sines), interleaved stereo. */
    private fun testSignal(rate: Int, frames: Int): FloatArray {
        var s = 12345L
        fun next(): Double {
            s = (s * 1664525L + 1013904223L) and 0xFFFFFFFFL
            return ((s ushr 8).toDouble() / 16777216.0) * 2.0 - 1.0
        }
        val out = FloatArray(2 * frames)
        for (i in 0 until frames) {
            val t = i.toDouble() / rate
            val nl = next()
            val nr = 0.6 * nl + 0.4 * next()
            val env = 0.5 + 0.5 * sin(2 * PI * 0.9 * t)
            out[2 * i] = (0.25 * sin(2 * PI * 220 * t) + 0.15 * sin(2 * PI * 3100 * t + 0.5) +
                0.2 * env * nl + 0.1 * sin(2 * PI * 55 * t)).toFloat()
            out[2 * i + 1] = (0.25 * sin(2 * PI * 330 * t + 1.0) + 0.15 * sin(2 * PI * 5200 * t) +
                0.2 * (1 - env) * nr + 0.1 * sin(2 * PI * 55 * t)).toFloat()
        }
        return out
    }

    private fun matchesGolden(rate: Int) {
        val golden = ByteBuffer.wrap(moduleFile("src/test/resources/spatializer/golden_$rate.bin").readBytes())
            .order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(rate, golden.int)
        val frames = golden.int
        val windows = golden.int

        val spatializer = StereoSpatializer(rate, shippedResponses(rate), outputStage = false)
        val signal = testSignal(rate, frames)
        var done = 0
        var blockSize = 997
        while (done < frames) {
            val n = minOf(blockSize, frames - done)
            val block = signal.copyOfRange(2 * done, 2 * (done + n))
            spatializer.process(block, n)
            System.arraycopy(block, 0, signal, 2 * done, 2 * n)
            done += n
            blockSize = if (blockSize == 997) 4096 else 997
        }

        repeat(windows) {
            val start = golden.int
            val count = golden.int
            var errSq = 0.0
            var refSq = 0.0
            var maxErr = 0.0
            for (i in 0 until 2 * count) {
                val expected = golden.float.toDouble()
                val got = signal[2 * start + i].toDouble()
                errSq += (got - expected) * (got - expected)
                refSq += expected * expected
                maxErr = max(maxErr, abs(got - expected))
            }
            val nullDb = 10 * log10(errSq / refSq)
            assertTrue("$rate Hz window @$start: residual $nullDb dB (max error $maxErr)", nullDb < -80.0)
        }
    }

    @Test
    fun `48 kHz output matches the expected output`() = matchesGolden(48000)

    @Test
    fun `44_1 kHz output matches the expected output`() = matchesGolden(44100)

    /** Left-ear minus right-ear level (dB) for one second of noise panned by [gainLeft] / [gainRight]. */
    private fun earBalanceDb(rate: Int, gainLeft: Float, gainRight: Float): Double {
        val spatializer = StereoSpatializer(rate, shippedResponses(rate), outputStage = false)
        val frames = rate
        val buf = FloatArray(2 * frames)
        var s = 987654321L
        for (i in 0 until frames) {
            s = (s * 1664525L + 1013904223L) and 0xFFFFFFFFL
            val v = (((s ushr 8).toDouble() / 16777216.0) * 2.0 - 1.0).toFloat() * 0.3f
            buf[2 * i] = v * gainLeft
            buf[2 * i + 1] = v * gainRight
        }
        spatializer.process(buf, frames)
        var left = 0.0
        var right = 0.0
        for (i in frames / 4 until frames) {
            left += buf[2 * i].toDouble() * buf[2 * i]
            right += buf[2 * i + 1].toDouble() * buf[2 * i + 1]
        }
        return 10 * log10(left / right)
    }

    @Test
    fun `sources stay on their side`() {
        for (rate in intArrayOf(44100, 48000)) {
            val hardLeft = earBalanceDb(rate, 1f, 0f)
            val hardRight = earBalanceDb(rate, 0f, 1f)
            val centre = earBalanceDb(rate, 1f, 1f)
            assertTrue("$rate Hz: hard left should be louder in the left ear ($hardLeft dB)", hardLeft > 3.0)
            assertTrue("$rate Hz: hard right should be louder in the right ear ($hardRight dB)", hardRight < -3.0)
            assertEquals("$rate Hz: a centred source should stay centred", 0.0, centre, 1.5)
        }
    }

    @Test
    fun `real FFT matches a direct DFT and inverts`() {
        for (n in intArrayOf(16, 2048)) {
            val fft = RealFft(n)
            val x = FloatArray(n) { sin(it * 0.37).toFloat() + (it % 7) * 0.1f }
            val re = FloatArray(n / 2 + 1)
            val im = FloatArray(n / 2 + 1)
            fft.forward(x, re, im)
            for (k in 0..n / 2 step maxOf(1, n / 64)) {
                var sr = 0.0
                var si = 0.0
                for (j in 0 until n) {
                    sr += x[j] * cos(2 * PI * j * k / n)
                    si -= x[j] * sin(2 * PI * j * k / n)
                }
                assertEquals("re[$k] n=$n", sr, re[k].toDouble(), 1e-3 * sqrt(n.toDouble()))
                assertEquals("im[$k] n=$n", si, im[k].toDouble(), 1e-3 * sqrt(n.toDouble()))
            }
            val back = FloatArray(n)
            fft.inverse(re, im, back)
            for (j in 0 until n) assertEquals(x[j].toDouble(), back[j] / n.toDouble(), 1e-4)
        }
    }

    @Test
    fun `limiter holds the ceiling and leaves quiet audio alone`() {
        val rate = 48000
        val limiter = PeakLimiter(rate, ceilingDb = -1f)
        val ceiling = Math.pow(10.0, -1.0 / 20).toFloat()
        val loud = FloatArray(2 * rate) { i -> (2.0 * sin(2 * PI * 100 * (i / 2) / rate)).toFloat() }
        limiter.process(loud, rate)
        assertTrue(loud.all { abs(it) <= ceiling + 1e-6f })

        val quiet = PeakLimiter(rate)
        val x = FloatArray(2 * 4800) { i -> (0.3 * sin(2 * PI * 440 * (i / 2) / rate)).toFloat() }
        val y = x.copyOf()
        quiet.process(y, 4800)
        val d = quiet.latencyFrames
        for (i in d until 4800) assertEquals(x[2 * (i - d)], y[2 * i], 1e-6f)
    }

    @Test
    fun `the tail flushes the delayed audio and the room`() {
        val rate = 48000
        val spatializer = StereoSpatializer(rate, shippedResponses(rate))
        val burst = FloatArray(2 * 4096) { i -> if (i / 2 < 2048) (0.5 * sin(i * 0.01)).toFloat() else 0f }
        spatializer.process(burst, 4096)
        val tail = FloatArray(2 * spatializer.tailFrames)
        spatializer.process(tail, spatializer.tailFrames)
        val energy = { a: FloatArray, from: Int, to: Int -> (from until to).sumOf { (a[it] * a[it]).toDouble() } }
        assertTrue("delayed audio comes out in the tail", energy(tail, 0, tail.size) > 1e-3)
        val after = FloatArray(2 * 4096)
        spatializer.process(after, 4096)
        assertTrue("nothing left after the tail", energy(after, 0, after.size) < 1e-12)
    }

    @Test
    fun `spatial processor spatializes and still widens`() {
        val rate = 48000
        SpeakerResponseStore.install(shippedResponses(rate))
        val processor = SpatialAudioProcessor()
        processor.configure(rate, 2)
        processor.enabled = true
        processor.mode = SpatialMode.SPATIALIZE

        val block = AudioBlock(2, 4096)
        val src = testSignal(rate, 4096)
        repeat(4) {
            System.arraycopy(src, 0, block.samples, 0, src.size)
            block.setFrameCount(4096)
            processor.process(block)
        }
        assertTrue(block.samples.all { it.isFinite() })
        assertTrue("spatialized output is audible", block.samples.sumOf { (it * it).toDouble() } > 1.0)
        assertTrue(processor.tailFrames() > 0)

        processor.mode = SpatialMode.WIDEN
        System.arraycopy(src, 0, block.samples, 0, src.size)
        block.setFrameCount(4096)
        processor.process(block)
        assertEquals(0, processor.tailFrames())
        // widening keeps the mid (L+R) exactly, scaled by the output gain
        val i = 1000
        val mid = (src[2 * i] + src[2 * i + 1]) * 0.5f
        assertEquals(mid * 0.82f, (block.samples[2 * i] + block.samples[2 * i + 1]) * 0.5f, 0.2f)
    }
}
