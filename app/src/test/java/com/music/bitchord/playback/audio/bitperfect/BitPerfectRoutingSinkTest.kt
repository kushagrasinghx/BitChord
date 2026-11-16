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

    /**
     * The direct route dying is answered by writing the same buffer to the
     * normal output. The converter had already advanced that buffer by a whole
     * block before the write failed, so the block has to be handed back or the
     * fallback silently drops it — up to a full block of audio per fallback
     * [AUD-20].
     */
    @Test
    fun `a failed direct write hands its block to the normal output`() {
        val rig = Rig()
        rig.switchFromNormalToDirect(pending = false)
        rig.routing.play()

        // One good block first, so the direct route is genuinely running.
        rig.routing.handleBuffer(pcm16(frames = 4), 0L, 1)
        val dac = rig.outputs.opened.single()
        assertEquals("the direct route took the first block", 4L, dac.written)

        dac.failOnWrite = true
        // Two frames per call, so what reached the normal sink is visible in the
        // buffer position rather than inferred.
        rig.normal.bytesToConsumePerCall = 8
        val buffer = pcm16(frames = 4)
        rig.routing.handleBuffer(buffer, 1_000_000L, 1)

        assertFalse("the direct route is gone", rig.routing.isBitPerfect)
        assertEquals(
            "the block the failed write consumed is still the caller's",
            8,
            buffer.position(),
        )
        assertTrue("the rest of the block is still to be written", buffer.hasRemaining())
        assertEquals("only the two frames the normal sink took were consumed", 8, buffer.position())
    }

    /**
     * A device that takes a prefix of the block and then fails: the accepted
     * frames belong to the direct route, the rest must be handed back so the
     * fallback writes it. One frame in, three out [AUD-20].
     */
    @Test
    fun `a block the direct route partly accepted hands back only its remainder`() {
        val rig = Rig()
        rig.switchFromNormalToDirect(pending = false)
        rig.routing.play()
        rig.routing.handleBuffer(pcm16(frames = 4), 0L, 1)
        val dac = rig.outputs.opened.single()

        dac.acceptBytesThenFail = dac.frameBytes // exactly one frame
        rig.normal.bytesToConsumePerCall = 4 // one 16-bit stereo frame
        val buffer = pcm16(frames = 4)
        rig.routing.handleBuffer(buffer, 1_000_000L, 1)

        assertFalse("the direct route is gone", rig.routing.isBitPerfect)
        assertEquals(
            "one frame to the DAC, one to the normal sink, nothing else consumed",
            8,
            buffer.position(),
        )
        assertEquals("two frames are still to be written", 8, buffer.remaining())
    }

    /**
     * The same at the other end of the buffer: three frames accepted, the last
     * one handed back on a frame boundary.
     */
    @Test
    fun `a block accepted up to its last frame hands back that frame`() {
        val rig = Rig()
        rig.switchFromNormalToDirect(pending = false)
        rig.routing.play()
        rig.routing.handleBuffer(pcm16(frames = 4), 0L, 1)
        val dac = rig.outputs.opened.single()

        dac.acceptBytesThenFail = dac.frameBytes * 3
        rig.normal.bytesToConsumePerCall = 4
        val buffer = pcm16(frames = 4)
        rig.routing.handleBuffer(buffer, 1_000_000L, 1)

        assertFalse("the direct route is gone", rig.routing.isBitPerfect)
        assertEquals(
            "three frames to the DAC, the fourth to the normal sink",
            16,
            buffer.position(),
        )
        assertEquals(
            "the normal sink was handed exactly the one unconsumed frame",
            4,
            rig.normal.lastHandledRemaining,
        )
        assertFalse("nothing is left over", buffer.hasRemaining())
    }

    /** A rig whose DAC runs at 48 kHz, so the sink resamples 44.1 kHz into it. */
    private fun resampledRig(): Rig {
        val rig = Rig()
        rig.outputs.dacRate = 48000
        rig.outputs.dacBits = 16
        rig.switchFromNormalToDirect(pending = false)
        rig.routing.play()
        return rig
    }

    /**
     * Resampled path, nothing accepted: the block is still entirely the
     * caller's, so the fallback writes all of it once [AUD-20].
     */
    @Test
    fun `a resampled block the direct route never took is handed back whole`() {
        val rig = resampledRig()
        // The filter has to warm up before it emits anything at all.
        repeat(4) { rig.routing.handleBuffer(pcm16(frames = 1024), 0L, 1) }
        val dac = rig.outputs.opened.single()
        assertTrue("the resampler produced output", dac.written > 0)

        dac.failOnWrite = true
        rig.normal.bytesToConsumePerCall = 8
        val buffer = pcm16(frames = 16)
        rig.routing.handleBuffer(buffer, 1_000_000L, 1)

        assertFalse("the direct route is gone", rig.routing.isBitPerfect)
        assertEquals(
            "the block starts over at its first frame",
            8,
            buffer.position(),
        )
        assertTrue("the buffer was not over-advanced", buffer.hasRemaining())
    }

    /**
     * Resampled path, two output frames accepted before the transport died. The
     * filter makes the accepted prefix unmappable to a source boundary, so the
     * documented policy replays the block instead of dropping its remainder.
     * This asserts the policy, not a lossless recovery: exact recovery is not
     * available from a transport that reports only a byte count for a block the
     * resampler has already consumed.
     */
    @Test
    fun `a resampled block the direct route partly took is replayed from its start`() {
        val rig = resampledRig()
        repeat(4) { rig.routing.handleBuffer(pcm16(frames = 1024), 0L, 1) }
        val dac = rig.outputs.opened.single()

        dac.acceptBytesThenFail = dac.frameBytes * 2
        rig.normal.bytesToConsumePerCall = 8
        val buffer = pcm16(frames = 16)
        rig.routing.handleBuffer(buffer, 1_000_000L, 1)

        assertFalse("the direct route is gone", rig.routing.isBitPerfect)
        assertEquals("the block starts over at its first frame", 8, buffer.position())
        assertTrue("the remainder is still to be written", buffer.hasRemaining())
    }
}
