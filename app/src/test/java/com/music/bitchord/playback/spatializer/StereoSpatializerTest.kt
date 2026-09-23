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
        // audio right up to the last input frame, so the tail has to cover everything on its own
        val burst = FloatArray(2 * 4096) { i -> (0.5 * sin(i * 0.01)).toFloat() }
        spatializer.process(burst, 4096)
        val tail = FloatArray(2 * spatializer.tailFrames)
        spatializer.process(tail, spatializer.tailFrames)
        val energy = { a: FloatArray, from: Int, to: Int -> (from until to).sumOf { (a[it] * a[it]).toDouble() } }
        assertTrue("delayed audio comes out in the tail", energy(tail, 0, tail.size) > 1e-3)
        val after = FloatArray(2 * 4096)
        spatializer.process(after, 4096)
        assertTrue("nothing left after the tail", energy(after, 0, after.size) < 1e-12)
    }

    /** Runs [signal] through [processor] in [block]-frame blocks, calling [before] ahead of each block. */
    private fun run(processor: SpatialAudioProcessor, signal: FloatArray, block: Int = 4096, before: (Int) -> Unit = {}): FloatArray {
        val out = signal.copyOf()
        val audio = AudioBlock(2, block)
        var done = 0
        var index = 0
        while (done < signal.size / 2) {
            val n = minOf(block, signal.size / 2 - done)
            before(index++)
            System.arraycopy(out, 2 * done, audio.samples, 0, 2 * n)
            audio.setFrameCount(n)
            processor.process(audio)
            System.arraycopy(audio.samples, 0, out, 2 * done, 2 * n)
            done += n
        }
        return out
    }

    private fun processorAt(rate: Int, mode: SpatialMode, enabled: Boolean = true): SpatialAudioProcessor {
        SpeakerResponseStore.install(shippedResponses(rate))
        return SpatialAudioProcessor().also {
            it.configure(rate, 2)
            it.enabled = enabled
            it.mode = mode
        }
    }

    @Test
    fun `both effects run with the spatializer's latency and report it`() {
        val rate = 48000
        val latency = StereoSpatializer.latencyFramesFor(rate)
        val spatialize = processorAt(rate, SpatialMode.SPATIALIZE)
        val out = run(spatialize, testSignal(rate, 4 * 4096))
        assertTrue(out.all { it.isFinite() })
        assertTrue("spatialized output is audible", out.sumOf { (it * it).toDouble() } > 1.0)
        assertEquals(latency, spatialize.latencyFrames())
        assertTrue(spatialize.tailFrames() > latency)

        val widen = processorAt(rate, SpatialMode.WIDEN)
        val src = testSignal(rate, 4096)
        val widened = run(widen, src)
        assertEquals(latency, widen.latencyFrames())
        // the widener's output is its input, delayed: the mid (L+R) survives, scaled by the output gain
        for (i in latency + 1000 until 4096 step 97) {
            val mid = (src[2 * (i - latency)] + src[2 * (i - latency) + 1]) * 0.5f
            assertEquals(mid * 0.82f, (widened[2 * i] + widened[2 * i + 1]) * 0.5f, 0.2f)
        }

        val off = processorAt(rate, SpatialMode.SPATIALIZE, enabled = false)
        val dry = testSignal(rate, 4096)
        assertTrue(run(off, dry).contentEquals(dry))
        assertEquals(0, off.latencyFrames())
        assertEquals(0, off.tailFrames())
    }

    @Test
    fun `switching effects mid-stream is a smooth crossfade`() {
        val rate = 48000
        val frames = 8 * rate
        // a steady 440 Hz tone, slightly different in each channel
        val signal = FloatArray(2 * frames) { i ->
            val t = (i / 2).toDouble() / rate
            (0.3 * sin(2 * PI * 440 * t + if (i % 2 == 0) 0.0 else 0.7)).toFloat()
        }
        val processor = processorAt(rate, SpatialMode.SPATIALIZE)
        val block = 1024
        val out = run(processor, signal, block) { index ->
            when (index * block / rate) { // switch once in each second
                1 -> processor.mode = SpatialMode.WIDEN
                2 -> processor.mode = SpatialMode.SPATIALIZE
                3 -> processor.enabled = false
                4 -> processor.enabled = true
                5 -> processor.mode = SpatialMode.WIDEN
                6 -> processor.enabled = false
                7 -> processor.enabled = true
            }
        }
        // a hard switch jumps by up to the full amplitude; the tone itself moves at most ~0.02 per sample
        var worst = 0f
        var at = 0
        for (i in rate / 2 until frames) for (c in 0..1) {
            val step = abs(out[2 * i + c] - out[2 * (i - 1) + c])
            if (step > worst) { worst = step; at = i }
        }
        assertTrue("largest sample-to-sample step $worst at frame $at", worst < 0.05f)
        assertTrue(out.all { it.isFinite() })
    }

    @Test
    fun `turning the effect off moves the latency to zero and back`() {
        val rate = 44100
        val processor = processorAt(rate, SpatialMode.SPATIALIZE)
        run(processor, testSignal(rate, 4096))
        assertEquals(StereoSpatializer.latencyFramesFor(rate), processor.latencyFrames())
        processor.enabled = false
        run(processor, testSignal(rate, 4096))
        assertEquals(0, processor.latencyFrames())
        assertEquals(0, processor.tailFrames())
        processor.enabled = true
        run(processor, testSignal(rate, 4 * 4096))
        assertEquals(StereoSpatializer.latencyFramesFor(rate), processor.latencyFrames())
    }

    @Test
    fun `a non-finite sample does not silence the spatializer`() {
        val rate = 48000
        val spatializer = StereoSpatializer(rate, shippedResponses(rate))
        val signal = testSignal(rate, 8 * 4096)
        signal[2 * 1000] = Float.NaN
        signal[2 * 1001 + 1] = Float.POSITIVE_INFINITY
        spatializer.process(signal, 8 * 4096)
        assertTrue(signal.all { it.isFinite() })
        val late = (6 * 4096 until 8 * 4096).sumOf { (signal[2 * it] * signal[2 * it]).toDouble() }
        assertTrue("audio after the bad samples", late > 1.0)
    }

    /** |H(f)| of one response, in dB. */
    private fun magnitudeDb(h: FloatArray, rate: Int, f: Double): Double {
        var re = 0.0
        var im = 0.0
        for (n in h.indices) {
            re += h[n] * cos(2 * PI * f * n / rate)
            im -= h[n] * sin(2 * PI * f * n / rate)
        }
        return 10 * log10(re * re + im * im)
    }

    @Test
    fun `resampled responses keep their level and frequency response`() {
        val cases = listOf(44100 to 22050, 48000 to 32000, 44100 to 88200, 48000 to 96000, 48000 to 192000)
        for ((from, to) in cases) {
            val base = shippedResponses(from)
            val started = System.nanoTime()
            val resampled = base.resampledTo(to)
            val ms = (System.nanoTime() - started) / 1e6
            assertTrue("$from -> $to Hz took $ms ms", ms < 2000)
            for ((input, ear) in listOf(0 to 0, 2 to 1, 3 to 0, 5 to 0)) {
                for (f in doubleArrayOf(100.0, 1000.0, 6000.0)) {
                    // the LFE feed is low-passed at 150 Hz: above that its level is too small to compare
                    if (f > 0.4 * to || (input == 3 && f > 150)) continue
                    val a = magnitudeDb(base.response(input, ear), from, f)
                    val b = magnitudeDb(resampled.response(input, ear), to, f)
                    assertEquals("$from -> $to Hz, input $input ear $ear at $f Hz", a, b, 0.1)
                }
            }
        }
    }
}
