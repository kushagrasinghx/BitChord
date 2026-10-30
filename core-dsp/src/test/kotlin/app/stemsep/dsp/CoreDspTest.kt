package app.stemsep.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class CoreDspTest {
    private val config = MdxConfig(
        fftSize = 512,
        hopLength = 128,
        frequencyBins = 256,
        timeFrames = 16,
        segmentSamples = 128 * 15,
        edgeTrimSamples = 256,
        compensation = 1f,
        overlap = 0.30f,
        zeroLowBins = false,
    )

    @Test
    fun stftInverseReconstructsTheSegment() {
        val stft = MdxStft(config)
        val mixture = FloatArray(config.segmentSamples * 2) { index ->
            (sin(index * 0.017) * 0.4 + sin(index * 0.071) * 0.2).toFloat()
        }
        val spectrum = FloatArray(config.tensorElements)
        val reconstructed = FloatArray(config.segmentSamples * 2)

        stft.forward(mixture, spectrum)
        stft.inverse(spectrum, reconstructed)

        var maxError = 0f
        for (index in mixture.indices) {
            maxError = maxOf(maxError, abs(mixture[index] - reconstructed[index]))
        }
        assertTrue("max reconstruction error was $maxError", maxError < 1e-4f)
    }

    @Test
    fun streamingSeparatorReconstructsMixAndCancelsInstrumental() {
        val sourceFrames = 5000
        val source = ArraySource(FloatArray(sourceFrames * 2) { index ->
            (sin(index * 0.013) * 0.35 + sin(index * 0.037) * 0.15).toFloat()
        })
        val vocals = CollectingSink()
        val instrumental = CollectingSink()
        val separator = MdxStreamingSeparator(config, MdxInference { input, output ->
            input.copyInto(output)
        })

        separator.separate(source, vocals, instrumental)

        val vocalSamples = vocals.samples
        val instrumentalSamples = instrumental.samples
        assertEquals(sourceFrames * 2, vocalSamples.size)
        assertEquals(sourceFrames * 2, instrumentalSamples.size)
        var vocalError = 0f
        var instrumentalPeak = 0f
        for (index in 0 until sourceFrames * 2) {
            vocalError = maxOf(vocalError, abs(source.samples[index] - vocalSamples[index]))
            instrumentalPeak = maxOf(instrumentalPeak, abs(instrumentalSamples[index]))
        }
        assertTrue("vocal reconstruction error was $vocalError", vocalError < 1e-3f)
        assertTrue("instrumental peak was $instrumentalPeak", instrumentalPeak < 1e-3f)
    }

    @Test
    fun clampedStartProducesExactlyTheSlicedFullRun() {
        val sourceFrames = 5000
        val samples = FloatArray(sourceFrames * 2) { index ->
            (sin(index * 0.013) * 0.35 + sin(index * 0.037) * 0.15).toFloat()
        }
        val source = ArraySource(samples)

        val fullVocals = CollectingSink()
        val fullInstrumental = CollectingSink()
        val separator = MdxStreamingSeparator(config, MdxInference { input, output ->
            input.copyInto(output)
        })
        separator.separate(source, fullVocals, fullInstrumental)

        val starts = intArrayOf(0, 1, 127, 128, 1000, 1344, 1345, 2688, 3500, 4999)
        for (start in starts) {
            val vocals = CollectingSink()
            val instrumental = CollectingSink()
            separator.separate(source, vocals, instrumental, startFrame = start.toLong())

            val expectedLength = (sourceFrames - start) * 2
            assertEquals(
                "vocal frames from start $start",
                expectedLength,
                vocals.samples.size,
            )
            assertEquals(
                "instrumental frames from start $start",
                expectedLength,
                instrumental.samples.size,
            )
            for (index in 0 until expectedLength) {
                assertEquals(
                    "vocal sample ${start + index / 2},$index % 2",
                    fullVocals.samples[start * 2 + index],
                    vocals.samples[index],
                    0f,
                )
                assertEquals(
                    "instrumental sample ${start + index / 2},$index % 2",
                    fullInstrumental.samples[start * 2 + index],
                    instrumental.samples[index],
                    0f,
                )
            }
        }
    }

    @Test
    fun wavWriterAndReaderRoundTrip() {
        val file = File.createTempFile("stemsep", ".wav")
        try {
            val frames = 1000
            val input = FloatArray(frames * 2) { index ->
                (sin(index * 0.031) * 0.7).toFloat()
            }
            WavWriter(file).use { writer ->
                writer.writeFrames(input, frames)
            }

            WavReader(file).use { reader ->
                assertEquals(44_100, reader.sampleRate)
                assertEquals(2, reader.channels)
                assertEquals(16, reader.bitsPerSample)
                assertEquals(frames.toLong(), reader.frameCount)

                val output = FloatArray(frames * 2)
                assertEquals(frames, reader.readFrames(0, frames, output))
                for (index in input.indices) {
                    assertEquals(input[index].toDouble(), output[index].toDouble(), 2.0 / 32768.0)
                }

                val samples = IntArray(frames * 2)
                assertEquals(frames, reader.readSamples(0, frames, samples))
                assertTrue(samples.all { it in -32768..32767 })
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun resamplerProducesExpectedLengthAndLevel() {
        val inputRate = 48_000
        val outputRate = 44_100
        val inputFrames = inputRate
        val input = FloatArray(inputFrames * 2) { index ->
            val frame = index / 2
            (sin(2.0 * PI * 1000.0 * frame / inputRate) * 0.5).toFloat()
        }
        val output = CollectingSink()
        val resampler = StereoResampler(inputRate, outputRate)
        var offset = 0
        while (offset < inputFrames) {
            val count = minOf(1024, inputFrames - offset)
            val chunk = FloatArray(count * 2)
            input.copyInto(chunk, 0, offset * 2, (offset + count) * 2)
            resampler.process(chunk, count) { frames, frameCount ->
                output.writeFrames(frames, frameCount)
            }
            offset += count
        }
        resampler.finish { frames, frameCount -> output.writeFrames(frames, frameCount) }

        val samples = output.samples
        assertEquals(outputRate * 2, samples.size)
        val start = (outputRate / 10) * 2
        val end = (outputRate * 9 / 10) * 2
        var sum = 0.0
        for (index in start until end) {
            sum += samples[index] * samples[index]
        }
        val rms = sqrt(sum / (end - start))
        assertEquals(0.3535, rms, 0.02)
    }



    @Test
    fun wavStreamWriterRoundTrip() {
        val frames = 500
        val input = FloatArray(frames * 2) { index ->
            (sin(index * 0.041) * 0.5).toFloat()
        }
        val bytes = ByteArrayOutputStream()
        WavStreamWriter(bytes, frameCount = frames.toLong()).use { writer ->
            writer.writeFrames(input, frames)
        }
        val file = File.createTempFile("stemsep-stream", ".wav")
        try {
            file.writeBytes(bytes.toByteArray())
            WavReader(file).use { reader ->
                assertEquals(frames.toLong(), reader.frameCount)
                val output = FloatArray(frames * 2)
                assertEquals(frames, reader.readFrames(0, frames, output))
                for (index in input.indices) {
                    assertEquals(input[index].toDouble(), output[index].toDouble(), 2.0 / 32768.0)
                }
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun floatPcmWriterAndSourceRoundTrip() {
        val file = File.createTempFile("stemsep", ".f32")
        try {
            val frames = 1000
            val input = FloatArray(frames * 2) { index ->
                (sin(index * 0.023) * 0.6).toFloat()
            }
            FloatPcmFileWriter(file).use { writer ->
                writer.writeFrames(input, frames)
                assertEquals(frames.toLong(), writer.frameCount)
            }

            FloatPcmFileSource(file, frames.toLong()).use { source ->
                val output = FloatArray(frames * 2)
                assertEquals(frames, source.readFrames(0, frames, output))
                for (index in input.indices) {
                    assertEquals(input[index].toDouble(), output[index].toDouble(), 1e-7)
                }

                val withPrefix = FloatArray((frames + 10) * 2)
                assertEquals(frames, source.readFrames(-10, frames + 10, withPrefix))
                for (index in 0 until 10 * 2) assertEquals(0f, withPrefix[index], 0f)
                for (index in input.indices) {
                    assertEquals(input[index].toDouble(), withPrefix[index + 20].toDouble(), 1e-7)
                }
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun mixerGainsAreCorrect() {
        assertEquals(StemGains(1f, 0f), StemMixer.gains(StemMode.VOCALS))
        assertEquals(StemGains(0f, 1f), StemMixer.gains(StemMode.INSTRUMENTAL))
        assertEquals(StemGains(0.4f, 1f), StemMixer.gains(StemMode.MIX, 0.4f))

        val out = FloatArray(2)
        StemMixer.mixFrame(0.5f, 0.25f, 0.2f, 0.1f, StemGains(1f, 1f), out)
        assertEquals(0.7f, out[0], 1e-6f)
        assertEquals(0.35f, out[1], 1e-6f)
    }

    private class ArraySource(override val frameCount: Long, val samples: FloatArray) :
        FloatStereoSource {
        constructor(samples: FloatArray) : this((samples.size / 2).toLong(), samples)

        override fun readFrames(startFrame: Long, frameCount: Int, out: FloatArray): Int {
            out.fill(0f, 0, frameCount * 2)
            if (startFrame >= this.frameCount) return 0
            val first = startFrame.coerceAtLeast(0)
            val prefix = (first - startFrame).toInt()
            val count = minOf(frameCount.toLong() - prefix, this.frameCount - first)
                .coerceAtLeast(0)
                .toInt()
            if (count <= 0) return 0
            samples.copyInto(
                out,
                destinationOffset = prefix * 2,
                startIndex = first.toInt() * 2,
                endIndex = (first.toInt() + count) * 2,
            )
            return count
        }
    }

    private class CollectingSink : FloatStereoSink {
        private val values = ArrayList<Float>()
        val samples: FloatArray
            get() = values.toFloatArray()

        override fun writeFrames(frames: FloatArray, frameCount: Int) {
            for (index in 0 until frameCount * 2) values.add(frames[index])
        }

        override fun flush() = Unit
    }
}
