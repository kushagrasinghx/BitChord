package com.music.bitchord.playback.smart

import android.media.MediaDataSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class AutoMixPcmTest {
    @Test
    fun chunkedDecodeMatchesContiguousPcmAndTiming() {
        for ((rate, channels, seconds) in listOf(Triple(11_025, 1, 2), Triple(44_100, 1, 2), Triple(48_000, 2, 2))) {
            val frames = seconds * rate + 17
            val bytes = wav(rate, channels, frames)
            val end = frames.toDouble() / rate
            val (legacy, start) = source(bytes).use { requireNotNull(AudioDecoder.decodeRegion(it, 0.0, end)) }
            val (chunked, chunkedStart) = source(bytes).use { requireNotNull(AudioDecoder.decodeRegionChunks(it, 0.0, end)) }

            assertEquals(0.0, start, 0.0)
            assertEquals(start, chunkedStart, 0.0)
            assertEquals(rate.toDouble(), legacy.sampleRate, 0.0)
            assertEquals(legacy.sampleRate, chunked.sampleRate, 0.0)
            assertEquals(frames, chunked.sampleCount)
            assertEquals(frames, legacy.samples.size)
            assertTrue(legacy.samples.contentEquals(chunked.flatten()))
            assertTrue(expectedMono(bytes, channels, frames).contentEquals(legacy.samples))
        }
    }

    @Test
    fun chunkedResamplingPreservesStructuralFeatures() {
        assertTrue(TrackFeatures.available)
        for (rate in listOf(11_025, 44_100, 48_000)) {
            val channels = 2
            val frames = 16 * rate + 17
            val duration = frames.toDouble() / rate
            val bytes = wav(rate, channels, frames)
            val (decoded, _) = source(bytes).use { requireNotNull(AudioDecoder.decodeRegionChunks(it, 0.0, duration)) }
            val flat = expectedMono(bytes, channels, frames)
            val from = decoded.sampleRate
            val target = TrackFeatures.sampleRate
            val baseline = if (abs(from - target) > 1.0) requireNotNull(TrackFeatures.resample(flat, from, target)) else flat
            val optimized = if (abs(from - target) > 1.0) {
                requireNotNull(TrackFeatures.resampleChunks(decoded.chunks, from, target))
            } else {
                decoded.flatten()
            }
            assertTrue("DSP input differed at $rate Hz", baseline.contentEquals(optimized))
            val features = requireNotNull(TrackFeatures.analyze(baseline, duration))
            assertTrue(features.energyCurve.isNotEmpty())
            assertEquals(features, TrackFeatures.analyze(optimized, duration))
        }
    }

    @Test
    fun resamplingDerivesLengthFromUnevenChunksAndRejectsEmptyInput() {
        val samples = FloatArray(1031) { (it % 31 - 15) / 16f }
        val chunks = listOf(samples.copyOfRange(0, 301), FloatArray(0), samples.copyOfRange(301, samples.size))
        val baseline = requireNotNull(TrackFeatures.resample(samples, 48_000.0))
        assertTrue(baseline.contentEquals(requireNotNull(TrackFeatures.resampleChunks(chunks, 48_000.0))))
        assertNull(TrackFeatures.resampleChunks(listOf(FloatArray(0)), 48_000.0))
    }

    @Test
    fun abortAndInvalidInputDoNotPublishPcmAndNextDecodeStillWorks() {
        val bytes = wav(48_000, 2, 48_017)
        var abortPolled = false
        source(bytes).use {
            assertNull(AudioDecoder.decodeRegionChunks(it, 0.0, 2.0) { abortPolled = true; true })
        }
        assertTrue(abortPolled)
        source(ByteArray(4096)).use {
            assertNull(AudioDecoder.decodeRegionChunks(it, 0.0, 2.0))
        }
        source(bytes).use {
            val (decoded, _) = requireNotNull(AudioDecoder.decodeRegionChunks(it, 0.0, 2.0))
            assertEquals(48_017, decoded.sampleCount)
        }
    }

    private fun wav(rate: Int, channels: Int, frames: Int): ByteArray {
        val size = frames * channels * Short.SIZE_BYTES
        return ByteBuffer.allocate(44 + size).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(36 + size)
            put("WAVEfmt ".toByteArray(Charsets.US_ASCII)).putInt(16)
            putShort(1).putShort(channels.toShort()).putInt(rate).putInt(rate * channels * Short.SIZE_BYTES)
            putShort((channels * Short.SIZE_BYTES).toShort()).putShort(16)
            put("data".toByteArray(Charsets.US_ASCII)).putInt(size)
            repeat(frames) { frame ->
                val time = frame.toDouble() / rate
                val pulse = exp(-(time % 0.5) * 20)
                putShort((sin(time * 2 * PI * 110) * pulse * 16_000).toInt().toShort())
                if (channels == 2) putShort((sin(time * 2 * PI * 165) * pulse * 10_000).toInt().toShort())
            }
        }.array()
    }

    private fun expectedMono(bytes: ByteArray, channels: Int, frames: Int): FloatArray {
        val pcm = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).apply { position(44) }
        return FloatArray(frames) {
            val left = pcm.short.toInt()
            val sum = if (channels == 2) left + pcm.short else left
            (sum / channels.toFloat()) / 32768f
        }
    }

    private fun source(bytes: ByteArray): MediaDataSource = object : MediaDataSource() {
        override fun getSize(): Long = bytes.size.toLong()
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= bytes.size) return -1
            val start = position.toInt()
            val count = min(size, bytes.size - start)
            bytes.copyInto(buffer, offset, start, start + count)
            return count
        }
        override fun close() = Unit
    }
}
