package com.music.bitchord.playback

import com.music.bitchord.data.TrackLog
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Blueprint Â§5.7 LOOP_CUT_DROP: a real quantized loop vamp for the outgoing
 * track's buildup, DJ-booth style.
 *
 * What a DJ does with a loop-and-build: engage a beat-quantized loop on the
 * outgoing deck (4 beats is the workhorse for transition cover), halve it
 * repeatedly (4 â†’ 2 â†’ 1 â†’ 1/2) while a high-pass filter and echo rise over
 * it, then release the loop and kill the deck on the downbeat as the
 * incoming track's drop lands. The playhead never stops advancing (slip
 * semantics), so phrasing stays intact.
 *
 * Why PCM-level instead of player seeks: re-issuing media items or seeking
 * the outgoing player mid-transition re-prepares its decoder and flams every
 * repeat by the seek latency (50â€“200 ms). A ring buffer repeats with an
 * internal equal-power micro-crossfade at the wrap point, so repeats are
 * sample-continuous and `player.currentPosition` (source media time, which
 * the controller schedules off) is never disturbed.
 *
 * ## How the loop is captured
 *
 * The ring always records the last [RING_SECONDS] of input. Engaging
 * ([setVampLoop] with beats > 0) snapshots the loop region as the *next*
 * frames to arrive â€” the first pass plays live while being captured (exactly
 * like pressing auto-loop on a CDJ: the loop plays through once, then
 * repeats). Halving keeps the loop start and shortens the length, applied at
 * the next wrap so a length change can never click mid-pass.
 *
 * ## Wrap clicks
 *
 * The wrap blends the loop tail into the loop head over [XFADE_MS]
 * equal-power (sinÂ²/cosÂ²), so no level dip and no step. Never fire
 * [SpliceGuardProcessor.triggerCut] per iteration for this â€” a 16 ms notch at
 * the wrap rate would pump. The guard stays downstream as the safety net for
 * the final hard cut.
 *
 * 16-bit PCM only, like the echo/reverb sends â€” other encodings bow out with
 * [AudioProcessor.AudioFormat.NOT_SET].
 */
@UnstableApi
class LoopVampProcessor : BaseAudioProcessor() {

    /** Loop length in beats requested by the controller; 0 = disengaged. */
    @Volatile
    private var targetLoopBeats: Float = 0f

    /** Outgoing beat length in seconds; set alongside the beats. */
    @Volatile
    private var targetBeatSeconds: Float = 0f

    private var channelCount = 0
    private var sampleRate = 0

    /** Interleaved ring of the most recent input, per channel. */
    private var ring = FloatArray(0)
    private var ringFrames = 0

    /** Absolute frame counters; ring index is (abs % ringFrames). */
    private var writeAbs: Long = 0L
    private var readAbs: Long = 0L
    private var loopStartAbs: Long = 0L
    private var loopLenFrames: Int = 0

    private var vampOn = false
    private var xfadeLeft = 0
    private var xfadePos = 0
    private var xfadeFrames = 0

    /**
     * Aims the vamp. [loopBeats] > 0 engages (or re-aims) the loop,
     * [loopBeats] <= 0 disengages to live passthrough. [beatSeconds] is one
     * outgoing beat in seconds. Length changes apply at the next wrap.
     */
    fun setVampLoop(loopBeats: Float, beatSeconds: Float) {
        targetLoopBeats = loopBeats.coerceAtLeast(0f)
        targetBeatSeconds = beatSeconds.coerceAtLeast(0f)
    }

    /** Disengages to passthrough; the ring keeps recording. */
    fun open() = setVampLoop(0f, targetBeatSeconds)

    /**
     * Wipes the ring and drops the live loop. Seeks only â€” never call
     * mid-transition. Targets are kept: a reconfigure mid-vamp re-engages
     * from the fresh stream (first pass live, seamless), while a parked
     * processor stays parked because its targets are already zero.
     */
    fun clear() {
        ring.fill(0f)
        writeAbs = 0L
        readAbs = 0L
        loopStartAbs = 0L
        loopLenFrames = 0
        vampOn = false
        xfadeLeft = 0
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount < 1) {
            TrackLog.w(
                TAG,
                "Loop vamp inactive: encoding=${inputAudioFormat.encoding} " +
                    "channels=${inputAudioFormat.channelCount} is not 16-bit PCM",
            )
            return AudioProcessor.AudioFormat.NOT_SET
        }
        channelCount = inputAudioFormat.channelCount
        sampleRate = inputAudioFormat.sampleRate
        ringFrames = (RING_SECONDS * sampleRate).toInt().coerceAtLeast(4096)
        ring = FloatArray(ringFrames * channelCount)
        xfadeFrames = ((XFADE_MS * sampleRate) / 1000).toInt().coerceAtLeast(32)
        clear()
        return inputAudioFormat
    }

    override fun onFlush() = clear()

    override fun onReset() {
        targetLoopBeats = 0f
        targetBeatSeconds = 0f
        channelCount = 0
        sampleRate = 0
        ring = FloatArray(0)
        ringFrames = 0
        writeAbs = 0L
        readAbs = 0L
        loopStartAbs = 0L
        loopLenFrames = 0
        vampOn = false
        xfadeLeft = 0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytesPerFrame = BYTES_PER_SAMPLE * channelCount
        if (bytesPerFrame == 0 || ringFrames == 0 || sampleRate == 0) return
        val frameCount = inputBuffer.remaining() / bytesPerFrame
        if (frameCount == 0) return
        val outputBuffer = replaceOutputBuffer(frameCount * bytesPerFrame)

        inputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer.order(ByteOrder.nativeOrder())

        val parked = targetLoopBeats <= 0f || targetBeatSeconds <= 0f
        if (parked) {
            if (vampOn) vampOn = false
        } else if (!vampOn) {
            loopStartAbs = writeAbs
            loopLenFrames = (targetLoopBeats * targetBeatSeconds * sampleRate).toInt()
                .coerceIn(1, ringFrames - 1024)
            readAbs = writeAbs
            xfadeLeft = 0
            vampOn = true
        }

        var remaining = frameCount
        while (remaining > 0) {
            val wIdx = ((writeAbs % ringFrames).toInt() * channelCount)
            for (channel in 0 until channelCount) {
                ring[wIdx + channel] = inputBuffer.short.toFloat()
            }
            writeAbs++

            if (!vampOn) {
                for (channel in 0 until channelCount) {
                    outputBuffer.putShort(clampToShort(ring[wIdx + channel]))
                }
            } else if (xfadeLeft > 0) {
                val k = xfadePos
                val t = (k + 1).toFloat() / (xfadeFrames + 1).toFloat()
                val c = cos(t * PI / 2.0).toFloat()
                val s = sin(t * PI / 2.0).toFloat()
                val tailAbs = loopStartAbs + loopLenFrames - xfadeFrames + k
                val headAbs = loopStartAbs + k
                for (channel in 0 until channelCount) {
                    val tail = ring[((tailAbs % ringFrames).toInt() * channelCount) + channel]
                    val head = ring[((headAbs % ringFrames).toInt() * channelCount) + channel]
                    outputBuffer.putShort(clampToShort(tail * c * c + head * s * s))
                }
                xfadePos++
                xfadeLeft--
                if (xfadeLeft == 0) {
                    loopLenFrames = wantLenOf(targetLoopBeats, targetBeatSeconds)
                    readAbs = loopStartAbs + xfadeFrames
                }
            } else {
                val rIdx = ((readAbs % ringFrames).toInt() * channelCount)
                for (channel in 0 until channelCount) {
                    outputBuffer.putShort(clampToShort(ring[rIdx + channel]))
                }
                readAbs++
                val loopEndAbs = loopStartAbs + loopLenFrames
                if (readAbs >= loopEndAbs - xfadeFrames && loopLenFrames > xfadeFrames * 2) {
                    xfadeLeft = xfadeFrames
                    xfadePos = 0
                } else if (readAbs >= loopEndAbs) {
                    loopLenFrames = wantLenOf(targetLoopBeats, targetBeatSeconds)
                    readAbs = loopStartAbs
                }
            }
            remaining--
        }
        outputBuffer.flip()
    }

    private fun wantLenOf(loopBeats: Float, beatSeconds: Float): Int {
        if (sampleRate == 0) return loopLenFrames
        return (loopBeats * beatSeconds * sampleRate).toInt()
            .coerceIn(1, ringFrames - 1024)
    }

    private fun clampToShort(value: Float): Short =
        value.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()

    companion object {
        private const val TAG = "BitChordLoopVamp"

        /** History window: covers a 16-beat loop plus the vamp at 60 BPM. */
        private const val RING_SECONDS = 24

        /** Wrap crossfade: long enough to kill the step, short of a flam. */
        private const val XFADE_MS = 8L

        private const val BYTES_PER_SAMPLE = 2
    }
}

/**
 * The two loop vamps a transition rides: the vamp only ever runs on the
 * outgoing deck (pre- and post-handoff it sits on the spare player), so the
 * surface is outgoing-only plus a both-decks [open]. Mirrors [EchoFilters] â€”
 * same role reasoning, same test seam.
 */
interface LoopVamps {
    /** Loop length in beats plus one outgoing beat in seconds; 0 disengages. */
    fun outgoing(loopBeats: Float, beatSeconds: Float)

    /** Disengages both decks; the rings keep recording. */
    fun open() {
        outgoing(0f, 0f)
    }

    /** For callers with no audio sink â€” tests, and the default wiring. */
    object None : LoopVamps {
        override fun outgoing(loopBeats: Float, beatSeconds: Float) = Unit
    }
}
