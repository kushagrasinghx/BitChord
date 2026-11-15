package com.music.bitchord.playback.audio.bitperfect

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import com.music.bitchord.playback.audio.PrecisionAudioSinkTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The handover between the two sinks [AUD-20].
 *
 * Switching mid-playlist is the case the partial-write rules exist for: the
 * outgoing sink is still draining into the same DAC, so the incoming one must
 * not write a frame until that drain has finished, and no frame may be written
 * to both. Before this file nothing drove the routing level at all — the
 * partial-write tests in `PrecisionAudioSinkTest` cover one sink's delegate,
 * not the switch between two.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class BitPerfectRoutingSinkTest {

    private fun format(): Format =
        Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setPcmEncoding(C.ENCODING_PCM_16BIT)
            .setSampleRate(44100)
            .setChannelCount(2)
            .build()

    private fun config() = AudioSink.AudioSinkConfig.Builder(format()).build()

    /** [frames] stereo 16-bit frames whose samples count up from [start]. */
    private fun pcm16(frames: Int, start: Int = 0): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        repeat(frames * 2) { buffer.putShort((start + it).toShort()) }
        buffer.flip()
        return buffer
    }

    private class Rig {
        val normal = PrecisionAudioSinkTest.FakeAudioSink()
        val outputs = BitPerfectAudioSinkTest.FakeOutputs()
        val bitPerfect = BitPerfectAudioSink(outputs)
        val routing = BitPerfectRoutingSink(normal, bitPerfect)

        /** Normal output first, then a direct one: the ordinary switch. */
        fun switchFromNormalToDirect(pending: Boolean) {
            outputs.available = false
            routing.configure(AudioSink.AudioSinkConfig.Builder(
                Format.Builder()
                    .setSampleMimeType(MimeTypes.AUDIO_RAW)
                    .setPcmEncoding(C.ENCODING_PCM_16BIT)
                    .setSampleRate(44100)
                    .setChannelCount(2)
                    .build(),
            ).build())
            normal.hasPendingDataReturn = pending
            outputs.available = true
            routing.configure(AudioSink.AudioSinkConfig.Builder(
                Format.Builder()
                    .setSampleMimeType(MimeTypes.AUDIO_RAW)
                    .setPcmEncoding(C.ENCODING_PCM_16BIT)
                    .setSampleRate(44100)
                    .setChannelCount(2)
                    .build(),
            ).build())
        }
    }

    /**
     * While the outgoing sink still has audio, the routing sink reports
     * backpressure instead of writing: the renderer must hand the same buffer
     * back, not the next one.
     */
    @Test
    fun `the incoming sink writes nothing until the outgoing one has played out`() {
        val rig = Rig()
        rig.switchFromNormalToDirect(pending = true)
        rig.routing.play()

        val buffer = pcm16(frames = 8)
        val accepted = rig.routing.handleBuffer(buffer, 1_000_000L, 1)

        assertFalse("backpressure while the old sink drains", accepted)
        assertTrue("the outgoing sink was asked to play out", rig.normal.playedToEndOfStream)
        assertEquals("no frame may reach the new sink early", 0, buffer.position())
        assertTrue("nothing opened yet", rig.outputs.opened.isEmpty())
        assertTrue("the sink still holds audio", rig.routing.hasPendingData())
        assertFalse("the stream has not ended", rig.routing.isEnded())
    }

    /**
     * Once the outgoing sink reports ended, the same buffer goes to the new one
     * in full and exactly once: nothing lost to the handover, nothing written
     * twice.
     */
    @Test
    fun `the whole buffer reaches the new sink once the old one has ended`() {
        val rig = Rig()
        rig.switchFromNormalToDirect(pending = true)
        rig.routing.play()

        val buffer = pcm16(frames = 8)
        rig.normal.isEndedReturn = false
        rig.routing.handleBuffer(buffer, 1_000_000L, 1)

        rig.normal.isEndedReturn = true
        val accepted = rig.routing.handleBuffer(buffer, 1_000_000L, 1)

        assertTrue("the buffer is accepted once the handover completes", accepted)
        assertEquals("the outgoing sink was reset exactly once", 1, rig.normal.resetCount)
        assertFalse("the buffer was consumed", buffer.hasRemaining())

        // The fake DAC opens at 24 bits, so eight frames are 6 bytes each: the
        // widening is the sink's, and this asserts the frame count that reached
        // it rather than a byte total written into the test.
        val dac = rig.outputs.opened.single()
        assertEquals("every frame reached the DAC once", 8 * dac.frameBytes, dac.bytes.size())
        assertEquals("duplicated frames would show here", 8L, dac.written)
        assertFalse("the old sink never received the new buffer", rig.normal.handleBufferCallCount > 0)
    }

    /** A switch with nothing pending resets the old sink and writes at once. */
    @Test
    fun `a switch with no pending audio does not hold the new sink`() {
        val rig = Rig()
        rig.switchFromNormalToDirect(pending = false)

        val buffer = pcm16(frames = 4)
        val accepted = rig.routing.handleBuffer(buffer, 0L, 1)

        assertTrue(accepted)
        assertEquals("the old sink is reset immediately", 1, rig.normal.resetCount)
        val dac = rig.outputs.opened.single()
        assertEquals(4 * dac.frameBytes, dac.bytes.size())
    }

    /** Flushing mid-handover must not leave a sink to drain against. */
    @Test
    fun `a flush during the handover drops the outgoing sink`() {
        val rig = Rig()
        rig.switchFromNormalToDirect(pending = true)
        rig.routing.play()

        rig.routing.flush()

        assertTrue("the outgoing sink was flushed", rig.normal.flushed)
        assertFalse("the handover is over", rig.routing.hasPendingData())

        val buffer = pcm16(frames = 2)
        assertTrue("writes go straight through afterwards", rig.routing.handleBuffer(buffer, 0L, 1))
        val dac = rig.outputs.opened.single()
        assertEquals(2 * dac.frameBytes, dac.bytes.size())
    }
}
