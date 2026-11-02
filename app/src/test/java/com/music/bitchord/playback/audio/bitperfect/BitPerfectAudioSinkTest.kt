package com.music.bitchord.playback.audio.bitperfect

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

@androidx.annotation.OptIn(UnstableApi::class)
class BitPerfectAudioSinkTest {

    /** A DAC that plays only when the test says so, holding [capacityFrames]. */
    internal class FakeTransport(
        override val sampleRate: Int,
        override val channelCount: Int,
        override val outputBits: Int,
        private val capacityFrames: Int = 8192,
    ) : BitPerfectTransport {
        override val driver = BitPerfectDriver.ANDROID_MIXER
        override val deviceName = "Fake DAC"
        override val bufferSizeFrames = capacityFrames.toLong()
        val frameBytes = channelCount * outputBits / 8
        val bytes = java.io.ByteArrayOutputStream()
        var written = 0L
        var played = 0L
        var playing = false
        var stopped = false
        var released = false
        var failOnWrite = false

        override fun write(buffer: ByteBuffer): Int {
            if (failOnWrite) throw BitPerfectTransportException("route_changed")
            val room = ((capacityFrames - (written - played)) * frameBytes).toInt()
            val count = minOf(room, buffer.remaining()) / frameBytes * frameBytes
            repeat(count) { bytes.write(buffer.get().toInt()) }
            written += count / frameBytes
            return count
        }

        override fun play() { playing = true }
        override fun pause() { playing = false }
        override fun flush() { written = 0; played = 0; bytes.reset() }
        override fun stop() { stopped = true }
        override fun playedFrames() = played
        override fun release() { released = true }

        fun advance(frames: Long) {
            played = minOf(written, played + frames)
        }
    }

    internal class FakeOutputs : BitPerfectOutputs {
        val opened = mutableListOf<FakeTransport>()
        val statuses = mutableListOf<BitPerfectOutput.Status>()
        var available = true
        var failOpen = false
        var failures = 0

        /** A DAC that stops here, as one on a capped USB-C dongle does. */
        var dacRate: Int? = null
        var dacBits: Int? = null

        override fun canOpen(sampleRate: Int, channels: Int, sourceBits: Int, requester: Any) = available

        override fun open(sampleRate: Int, channels: Int, sourceBits: Int, requester: Any): BitPerfectTransport {
            if (failOpen) throw BitPerfectTransportException("unsupported")
            return FakeTransport(dacRate ?: sampleRate, channels, dacBits ?: maxOf(sourceBits, 24)).also(opened::add)
        }

        override fun release(requester: Any) = Unit
        override fun markFailed(sampleRate: Int, channels: Int, sourceBits: Int, reason: String) { failures++ }
        override fun publish(status: BitPerfectOutput.Status) { statuses += status }
        override fun publishIdle() = Unit
        var pendingNormal = false
        override fun normalOutputPending() = pendingNormal
    }

    private fun format(rate: Int = 44100, encoding: Int = C.ENCODING_PCM_16BIT, delay: Int = 0): Format =
        Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setPcmEncoding(encoding)
            .setSampleRate(rate)
            .setChannelCount(2)
            .setEncoderDelay(delay)
            .build()

    private fun config(format: Format) = AudioSink.AudioSinkConfig.Builder(format).build()

    /** [frames] stereo 16-bit frames whose samples count up from [start]. */
    private fun pcm16(frames: Int, start: Int = 0): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        repeat(frames * 2) { buffer.putShort((start + it).toShort()) }
        buffer.flip()
        return buffer
    }

    @Test
    fun opensAtTheFirstBufferAndWritesSamplesUnchanged() {
        val outputs = FakeOutputs()
        val sink = BitPerfectAudioSink(outputs)
        sink.configure(config(format()))
        assertTrue("nothing opens before audio arrives", outputs.opened.isEmpty())

        sink.play()
        assertTrue(sink.handleBuffer(pcm16(4), 1_000_000, 1))
        val transport = outputs.opened.single()
        assertTrue(transport.playing)
        val out = ByteBuffer.wrap(transport.bytes.toByteArray()).order(ByteOrder.LITTLE_ENDIAN)
        // 16-bit samples left-aligned into the 24-bit wire format.
        repeat(8) { index ->
            val sample = (out.get().toInt() and 255) or ((out.get().toInt() and 255) shl 8) or (out.get().toInt() shl 16)
            assertEquals(index shl 8, sample)
        }
        assertTrue(outputs.statuses.last().active)
        assertTrue(outputs.statuses.last().exact)
    }

    @Test
    fun positionFollowsFramesTheDeviceActuallyPlayed() {
        val outputs = FakeOutputs()
        val sink = BitPerfectAudioSink(outputs)
        sink.configure(config(format(rate = 48000)))
        sink.play()
        assertEquals(AudioSink.CURRENT_POSITION_NOT_SET, sink.getCurrentPositionUs(false))
        sink.handleBuffer(pcm16(4800), 2_000_000, 1)
        assertEquals(2_000_000, sink.getCurrentPositionUs(false))
        outputs.opened.single().advance(2400)
        assertEquals(2_050_000, sink.getCurrentPositionUs(false))
    }

    @Test
    fun sameFormatContinuesGaplesslyOnOneTransport() {
        val outputs = FakeOutputs()
        val sink = BitPerfectAudioSink(outputs)
        sink.play()
        sink.configure(config(format()))
        assertTrue(sink.handleBuffer(pcm16(441), 0, 1))
        // The next track: a new configure, same stream shape.
        sink.configure(config(format().buildUpon().setId("next").build()))
        assertTrue(sink.handleBuffer(pcm16(441), 10_000, 1))
        assertEquals(1, outputs.opened.size)
        assertFalse(outputs.opened.single().stopped)
    }

    @Test
    fun formatChangeDrainsTheOldStreamBeforeReopening() {
        val outputs = FakeOutputs()
        val sink = BitPerfectAudioSink(outputs)
        sink.play()
        sink.configure(config(format(rate = 44100)))
        assertTrue(sink.handleBuffer(pcm16(441), 0, 1))
        val first = outputs.opened.single()

        sink.configure(config(format(rate = 96000)))
        val next = pcm16(960)
        assertFalse("must wait for the 44.1 kHz tail", sink.handleBuffer(next, 10_000, 1))
        assertTrue(first.stopped)
        assertEquals(1, outputs.opened.size)

        first.advance(441)
        assertTrue(sink.handleBuffer(next, 10_000, 1))
        assertTrue(first.released)
        assertEquals(96000, outputs.opened.last().sampleRate)
        assertEquals(10_000, sink.getCurrentPositionUs(false))
    }

    @Test
    fun endsOnlyOnceTheDeviceHasPlayedEverything() {
        val outputs = FakeOutputs()
        val sink = BitPerfectAudioSink(outputs)
        sink.play()
        sink.configure(config(format()))
        sink.handleBuffer(pcm16(100), 0, 1)
        sink.playToEndOfStream()
        val transport = outputs.opened.single()
        assertTrue(transport.stopped)
        assertFalse(sink.isEnded())
        assertTrue(sink.hasPendingData())
        transport.advance(100)
        assertTrue(sink.isEnded())
    }

    @Test
    fun backpressureKeepsTheRemainderForTheNextCall() {
        val outputs = FakeOutputs()
        val sink = BitPerfectAudioSink(outputs)
        sink.play()
        sink.configure(config(format()))
        val big = pcm16(20_000)
        assertFalse(sink.handleBuffer(big, 0, 1))
        val transport = outputs.opened.single()
        assertEquals(8192, transport.written)
        transport.advance(8192)
        // Re-handed the same buffer, as the renderer does.
        while (!sink.handleBuffer(big, 0, 1)) transport.advance(8192)
        assertEquals(20_000L, transport.written)
        assertFalse(big.hasRemaining())
    }

    @Test
    fun flushKeepsTheDeviceOpenAndRestartsTheClock() {
        val outputs = FakeOutputs()
        val sink = BitPerfectAudioSink(outputs)
        sink.play()
        sink.configure(config(format()))
        sink.handleBuffer(pcm16(441), 0, 1)
        sink.flush()
        assertEquals(AudioSink.CURRENT_POSITION_NOT_SET, sink.getCurrentPositionUs(false))
        sink.handleBuffer(pcm16(441), 30_000_000, 1)
        assertEquals(1, outputs.opened.size)
        assertEquals(30_000_000, sink.getCurrentPositionUs(false))
    }

    @Test
    fun lossyPrimedStreamsAreLeftToTheOrdinaryPath() {
        val sink = BitPerfectAudioSink(FakeOutputs())
        assertFalse(sink.canAccept(format(delay = 1105)))
        assertEquals(AudioSink.SINK_FORMAT_UNSUPPORTED, sink.getFormatSupport(format(delay = 1105)))
        assertTrue(sink.canAccept(format()))
    }

    @Test
    fun floatThatIsNotIntegerPcmIsReportedNotExact() {
        val outputs = FakeOutputs()
        val sink = BitPerfectAudioSink(outputs)
        sink.play()
        sink.configure(config(format(encoding = C.ENCODING_PCM_FLOAT)))
        val buffer = ByteBuffer.allocateDirect(8).order(ByteOrder.LITTLE_ENDIAN).putFloat(0.1234567f).putFloat(-0.3f)
        buffer.flip()
        sink.handleBuffer(buffer, 0, 1)
        assertFalse(outputs.statuses.last().exact)
    }

    @Test
    fun trackBeyondTheDacIsConvertedAndNotCalledBitPerfect() {
        val outputs = FakeOutputs().apply { dacRate = 48000; dacBits = 16 }
        val sink = BitPerfectAudioSink(outputs)
        sink.play()
        sink.configure(config(format(rate = 96000, encoding = C.ENCODING_PCM_24BIT)))
        val frames = 9600
        val buffer = ByteBuffer.allocateDirect(frames * 6).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(ByteArray(frames * 6))
        buffer.flip()
        assertTrue(sink.handleBuffer(buffer, 0, 1))
        sink.playToEndOfStream()

        val transport = outputs.opened.single()
        assertEquals(frames / 2L, transport.written)
        val status = outputs.statuses.last()
        assertTrue(status.dacLimited)
        assertFalse(status.exact)
        assertEquals(48000, status.sampleRateHz)
        assertEquals(16, status.outputBits)
        assertEquals(96000, status.sourceRateHz)
        assertEquals(24, status.sourceBits)

        // The clock runs at the DAC's rate: half the output frames is 50 ms of the track.
        transport.advance(2400)
        assertEquals(50_000, sink.getCurrentPositionUs(false))
        assertFalse(sink.isEnded())
        transport.advance(2400)
        assertTrue(sink.isEnded())
    }

    @Test
    fun trackTheDacCanTakeIsNotConverted() {
        val outputs = FakeOutputs().apply { dacRate = 44100; dacBits = 24 }
        val sink = BitPerfectAudioSink(outputs)
        sink.play()
        sink.configure(config(format(rate = 44100)))
        sink.handleBuffer(pcm16(4), 0, 1)
        assertFalse(outputs.statuses.last().dacLimited)
        assertTrue(outputs.statuses.last().exact)
    }

    @Test
    fun failedOpenIsRecordedAndRaisedAsRecoverable() {
        val outputs = FakeOutputs().apply { failOpen = true }
        val sink = BitPerfectAudioSink(outputs)
        sink.configure(config(format()))
        try {
            sink.handleBuffer(pcm16(10), 0, 1)
            fail("expected an initialization failure")
        } catch (error: AudioSink.InitializationException) {
            assertTrue(error.isRecoverable)
        }
        assertEquals(1, outputs.failures)
    }

    @Test
    fun routeLossSurfacesAsRecoverableWriteError() {
        val outputs = FakeOutputs()
        val sink = BitPerfectAudioSink(outputs)
        sink.play()
        sink.configure(config(format()))
        sink.handleBuffer(pcm16(10), 0, 1)
        outputs.opened.single().failOnWrite = true
        try {
            sink.handleBuffer(pcm16(10), 226, 1)
            fail("expected a write failure")
        } catch (error: AudioSink.WriteException) {
            assertTrue(error.isRecoverable)
        }
    }

    // ---- Routing --------------------------------------------------------------------------

    /** Just enough of an AudioSink to see what the router hands it. */
    private class RecordingSink : AudioSink by NoOpSink() {
        var configured: Format? = null
        var buffers = 0
        var pending = false
        var resets = 0
        var played = false

        override fun configure(audioSinkConfig: AudioSink.AudioSinkConfig) { configured = audioSinkConfig.format }
        override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
            buffers++
            buffer.position(buffer.limit())
            return true
        }
        override fun hasPendingData() = pending
        override fun isEnded() = !pending
        override fun reset() { resets++ }
        override fun play() { played = true }
    }

    @Test
    fun routerForwardsEverythingWhileTheOutputIsUnavailable() {
        val outputs = FakeOutputs().apply { available = false }
        val normal = RecordingSink()
        val router = BitPerfectRoutingSink(normal, BitPerfectAudioSink(outputs))
        router.configure(config(format()))
        assertTrue(router.handleBuffer(pcm16(10), 0, 1))
        assertNotNull(normal.configured)
        assertEquals(1, normal.buffers)
        assertFalse(router.isBitPerfect)
        assertTrue(outputs.opened.isEmpty())
    }

    @Test
    fun routerFallsBackToNormalWhenTheOutputCannotOpen() {
        val outputs = FakeOutputs().apply { failOpen = true }
        val normal = RecordingSink()
        val router = BitPerfectRoutingSink(normal, BitPerfectAudioSink(outputs))
        router.play()
        val format = format()
        router.configure(config(format))
        assertTrue(router.isBitPerfect)
        assertTrue(router.handleBuffer(pcm16(10), 0, 1))
        assertFalse(router.isBitPerfect)
        assertSame(format, normal.configured)
        assertEquals(1, normal.buffers)
        assertTrue(normal.played)
    }

    @Test
    fun routerLetsTheOutgoingSinkFinishBeforeSwitching() {
        val outputs = FakeOutputs()
        val normal = RecordingSink()
        val router = BitPerfectRoutingSink(normal, BitPerfectAudioSink(outputs))
        router.play()
        // A lossy stream on the normal path, still playing out its tail...
        router.configure(config(format(delay = 1105)))
        router.handleBuffer(pcm16(10), 0, 1)
        normal.pending = true
        // ...when a lossless track arrives.
        router.configure(config(format()))
        assertFalse(router.handleBuffer(pcm16(10), 1_000, 1))
        assertTrue(outputs.opened.isEmpty())
        normal.pending = false
        assertTrue(router.handleBuffer(pcm16(10), 1_000, 1))
        assertEquals(1, normal.resets)
        assertEquals(1, outputs.opened.size)
    }

    /** A local 5.1 file (a Dolby track decoded to PCM, say) after a lossless stereo one. */
    @Test
    fun multichannelLocalTrackPlaysOnNormalOutputAfterBitPerfect() {
        val outputs = FakeOutputs()
        val normal = RecordingSink()
        val router = BitPerfectRoutingSink(normal, BitPerfectAudioSink(outputs))
        router.play()
        router.configure(config(format()))
        assertTrue(router.handleBuffer(pcm16(10), 0, 1))
        assertTrue(router.isBitPerfect)

        val surround = format().buildUpon().setChannelCount(6).build()
        router.configure(config(surround))
        assertFalse(router.isBitPerfect)
        val sixChannels = ByteBuffer.allocateDirect(12 * 4).order(ByteOrder.LITTLE_ENDIAN)
        // The stereo tail plays out on the DAC first.
        assertFalse(router.handleBuffer(sixChannels, 1_000, 1))
        outputs.opened.single().advance(10)
        assertTrue(router.handleBuffer(sixChannels, 1_000, 1))
        assertSame(surround, normal.configured)
        assertTrue(outputs.opened.single().released)
    }

    @Test
    fun normalOutputWaitsWhileTheDacReturnsToAndroid() {
        val outputs = FakeOutputs().apply { available = false; pendingNormal = true }
        val normal = RecordingSink()
        val router = BitPerfectRoutingSink(normal, BitPerfectAudioSink(outputs))
        router.configure(config(format()))
        assertFalse(router.handleBuffer(pcm16(10), 0, 1))
        assertEquals(0, normal.buffers)
        outputs.pendingNormal = false
        assertTrue(router.handleBuffer(pcm16(10), 0, 1))
        assertEquals(1, normal.buffers)
    }
}

/** Every AudioSink method as a harmless default, for fakes to override selectively. */
@androidx.annotation.OptIn(UnstableApi::class)
private class NoOpSink : AudioSink {
    override fun setListener(listener: AudioSink.Listener) = Unit
    override fun supportsFormat(format: Format) = true
    override fun getFormatSupport(format: Format) = AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
    override fun getCurrentPositionUs(sourceEnded: Boolean) = AudioSink.CURRENT_POSITION_NOT_SET
    override fun configure(audioSinkConfig: AudioSink.AudioSinkConfig) = Unit
    override fun play() = Unit
    override fun handleDiscontinuity() = Unit
    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int) = true
    override fun playToEndOfStream() = Unit
    override fun isEnded() = true
    override fun hasPendingData() = false
    override fun setPlaybackParameters(playbackParameters: androidx.media3.common.PlaybackParameters) = Unit
    override fun getPlaybackParameters() = androidx.media3.common.PlaybackParameters.DEFAULT
    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) = Unit
    override fun getSkipSilenceEnabled() = false
    override fun setAudioAttributes(audioAttributes: androidx.media3.common.AudioAttributes) = Unit
    override fun getAudioAttributes() = androidx.media3.common.AudioAttributes.DEFAULT
    override fun setAudioSessionId(audioSessionId: Int) = Unit
    override fun setAuxEffectInfo(auxEffectInfo: androidx.media3.common.AuxEffectInfo) = Unit
    override fun getAudioTrackBufferSizeUs() = C.TIME_UNSET
    override fun enableTunnelingV21() = Unit
    override fun disableTunneling() = Unit
    override fun setVolume(volume: Float) = Unit
    override fun pause() = Unit
    override fun flush() = Unit
    override fun reset() = Unit
}
