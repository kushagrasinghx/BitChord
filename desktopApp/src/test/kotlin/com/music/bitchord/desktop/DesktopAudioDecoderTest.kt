package com.music.bitchord.desktop

import java.io.ByteArrayInputStream
import java.io.File
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The decoder against real files, because the whole reason for owning the decode is to be able to
 * look at the samples — so the tests look at them.
 */
class DesktopAudioDecoderTest {

    private val scratch = mutableListOf<File>()

    @AfterTest
    fun cleanUp() = scratch.forEach { it.delete() }

    @Test
    fun `a tone decodes back to the samples it was written from`() {
        val file = tone(seconds = 1.0, rate = 44_100, hz = 440.0)
        val decoder = DesktopAudioDecoder()

        decoder.open(file.absolutePath, requested = DesktopPcmFormat(44_100, 2, 2)).getOrThrow()
        try {
            assertEquals(44_100, decoder.outputFormat.sampleRate)
            assertEquals(2, decoder.outputFormat.channels)

            val pcm = decoder.readAll()

            // One second of 44.1k stereo 16-bit, give or take the codec's own framing at the tail.
            val expected = 44_100 * 4
            assertTrue(abs(pcm.size - expected) < 4 * 1_024, "decoded ${pcm.size} bytes, wanted about $expected")

            // The tone itself: peaks near full scale, and not a flat line.
            val peak = (0 until pcm.size / 2).maxOf { abs(sampleAt(pcm, it)) }
            assertTrue(peak > 20_000, "peak $peak looks like silence, not a tone")

            // Both channels carry it — a mono downmix or a dropped channel would leave one side
            // empty.
            val left = (0 until pcm.size / 4).maxOf { abs(sampleAt(pcm, it * 2)) }
            val right = (0 until pcm.size / 4).maxOf { abs(sampleAt(pcm, it * 2 + 1)) }
            assertTrue(left > 20_000 && right > 20_000, "left $left right $right")
        } finally {
            decoder.close()
        }
    }

    @Test
    fun `duration is read from the container`() {
        val file = tone(seconds = 2.0, rate = 48_000, hz = 220.0)
        val decoder = DesktopAudioDecoder()

        decoder.open(file.absolutePath, requested = DesktopPcmFormat(48_000, 2, 2)).getOrThrow()
        try {
            val duration = assertNotNull(decoder.durationUs)
            assertTrue(abs(duration - 2_000_000) < 50_000, "duration $duration")
            // And the source's own rate is kept rather than resampled away.
            assertEquals(48_000, decoder.outputFormat.sampleRate)
        } finally {
            decoder.close()
        }
    }

    @Test
    fun `seeking lands where it was asked to`() {
        val file = tone(seconds = 4.0, rate = 44_100, hz = 440.0)
        val decoder = DesktopAudioDecoder()

        decoder.open(file.absolutePath, requested = DesktopPcmFormat(44_100, 2, 2)).getOrThrow()
        try {
            assertTrue(decoder.seek(2_000_000))
            assertNotNull(decoder.read())
            assertTrue(abs(decoder.positionUs - 2_000_000) < 200_000, "landed at ${decoder.positionUs}")

            // And what is left is the rest of the file, not the whole of it.
            val remaining = decoder.readAll().size + 0
            val whole = 44_100 * 4 * 4
            assertTrue(remaining < whole / 2 + 4 * 1_024, "read $remaining of $whole after seeking to halfway")
        } finally {
            decoder.close()
        }
    }

    @Test
    fun `float output is four bytes a sample and stays in range`() {
        val file = tone(seconds = 0.5, rate = 44_100, hz = 440.0)
        val decoder = DesktopAudioDecoder()

        decoder.open(
            file.absolutePath,
            requested = DesktopPcmFormat(44_100, 2, bytesPerSample = 4, isFloat = true),
        ).getOrThrow()
        try {
            val pcm = decoder.readAll()
            val expected = (44_100 * 0.5).toInt() * 2 * 4
            assertTrue(abs(pcm.size - expected) < 8 * 1_024, "decoded ${pcm.size} bytes, wanted about $expected")

            // Float PCM is normalised to -1..1; anything outside means the sample format was
            // mislabelled somewhere.
            var peak = 0f
            for (at in 0 until pcm.size - 3 step 4) {
                val bits = (pcm[at].toInt() and 0xFF) or
                    ((pcm[at + 1].toInt() and 0xFF) shl 8) or
                    ((pcm[at + 2].toInt() and 0xFF) shl 16) or
                    (pcm[at + 3].toInt() shl 24)
                val value = Float.fromBits(bits)
                assertTrue(value in -1.001f..1.001f, "sample $value out of range")
                peak = maxOf(peak, abs(value))
            }
            assertTrue(peak > 0.5f, "peak $peak looks like silence")
        } finally {
            decoder.close()
        }
    }

    @Test
    fun `a file that is not audio fails to open rather than throwing later`() {
        val junk = File.createTempFile("bitchord-not-audio", ".bin").also {
            scratch += it
            it.writeText("this is not a media file")
        }
        val decoder = DesktopAudioDecoder()

        assertTrue(decoder.open(junk.absolutePath, requested = DesktopPcmFormat(44_100, 2, 2)).isFailure)
        // And a decoder that never opened reads nothing rather than crashing.
        assertNull(decoder.read())
        decoder.close()
    }

    private fun DesktopAudioDecoder.readAll(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        while (true) {
            val block = read() ?: break
            out.write(block)
        }
        return out.toByteArray()
    }

    private fun sampleAt(pcm: ByteArray, index: Int): Int {
        val at = index * 2
        return ((pcm[at].toInt() and 0xFF) or (pcm[at + 1].toInt() shl 8)).toShort().toInt()
    }

    /** A stereo sine, written as a WAV that FFmpeg will read back exactly. */
    private fun tone(seconds: Double, rate: Int, hz: Double): File {
        val frames = (rate * seconds).toInt()
        val bytes = ByteArray(frames * 4)
        for (frame in 0 until frames) {
            val value = (sin(2.0 * PI * hz * frame / rate) * 30_000).toInt().toShort()
            val at = frame * 4
            bytes[at] = value.toInt().toByte()
            bytes[at + 1] = (value.toInt() shr 8).toByte()
            bytes[at + 2] = bytes[at]
            bytes[at + 3] = bytes[at + 1]
        }
        val format = AudioFormat(rate.toFloat(), 16, 2, true, false)
        val file = File.createTempFile("bitchord-tone", ".wav").also { scratch += it }
        AudioInputStream(ByteArrayInputStream(bytes), format, frames.toLong()).use {
            AudioSystem.write(it, AudioFileFormat.Type.WAVE, file)
        }
        return file
    }
}
