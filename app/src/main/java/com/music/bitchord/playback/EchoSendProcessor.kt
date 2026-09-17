package com.music.bitchord.playback

import com.music.bitchord.data.TrackLog
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteOrder
import kotlin.math.min

/**
 * Blueprint §5.7 ECHO_REVERB_OUT: a tempo-synced echo send for the outgoing
 * track's decay tail.
 *
 * A single delay line with feedback, not a reverb convolver: the reverb-ish
 * wash comes from [TransitionFilterProcessor] closing over the same track
 * while this send throws a 1-bar ([setDelayFromBeatSeconds]) repeat behind
 * it. Delay + filter wash reads as a dub tail at a fraction of a convolver's
 * cost, and it needs no impulse asset.
 *
 * ## Wet behavior
 *
 * Wet is a target chased per sub-block, exactly like the filter's cutoff
 * glide: [CrossfadeController] re-aims it every fade tick and stepping wet in
 * 30 ms jumps would zipper. Opening to zero does NOT wipe the line — the tail
 * rings out naturally, which is the whole point of an echo-out. [clear] (and
 * [onFlush]) is the only thing that wipes, for seeks and fresh sources.
 */
@UnstableApi
class EchoSendProcessor : BaseAudioProcessor() {

    @Volatile
    private var targetWet: Float = 0f

    @Volatile
    private var delaySeconds: Float = 0f

    private var channelCount = 0
    private var sampleRate = 0
    private var currentWet: Float = 0f

    /** Interleaved circular line, per channel. */
    private var line = FloatArray(0)
    private var lineFrames = 0
    private var writePos = 0

    /**
     * Aims the send. [wet] 0..1 is the repeat level against dry; [delaySeconds]
     * is the repeat period — the caller passes one bar in seconds. A zero or
     * negative delay parks the line length at one frame (harmless: wet gates it).
     */
    fun setEcho(wet: Float, delaySeconds: Float) {
        targetWet = wet.coerceIn(0f, MAX_WET)
        this.delaySeconds = delaySeconds.coerceAtLeast(0f)
    }

    /** Rides the wet down; the line keeps ringing until it decays. */
    fun open() = setEcho(0f, delaySeconds)

    /** Wipes the line. Seeks only — never call mid-transition. */
    fun clear() {
        line.fill(0f)
        writePos = 0
        currentWet = targetWet
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount < 1) {
            TrackLog.w(
                TAG,
                "Echo send inactive: encoding=${inputAudioFormat.encoding} " +
                    "channels=${inputAudioFormat.channelCount} is not 16-bit PCM",
            )
            return AudioProcessor.AudioFormat.NOT_SET
        }
        channelCount = inputAudioFormat.channelCount
        sampleRate = inputAudioFormat.sampleRate
        lineFrames = (MAX_DELAY_SECONDS * sampleRate).toInt().coerceAtLeast(1)
        line = FloatArray(lineFrames * channelCount)
        writePos = 0
        currentWet = targetWet
        return inputAudioFormat
    }

    override fun onFlush() = clear()

    override fun onReset() {
        targetWet = 0f
        delaySeconds = 0f
        currentWet = 0f
        line = FloatArray(0)
        lineFrames = 0
        writePos = 0
    }

    override fun queueInput(inputBuffer: java.nio.ByteBuffer) {
        val bytesPerFrame = BYTES_PER_SAMPLE * channelCount
        if (bytesPerFrame == 0 || lineFrames == 0) return
        val frameCount = inputBuffer.remaining() / bytesPerFrame
        if (frameCount == 0) return
        val outputBuffer = replaceOutputBuffer(frameCount * bytesPerFrame)

        val delayFrames = ((delaySeconds * sampleRate).toInt()).coerceIn(1, lineFrames - 1)
        val parked = targetWet <= 0f && currentWet <= SETTLED_WET
        if (parked) {
            currentWet = 0f
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        inputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer.order(ByteOrder.nativeOrder())

        var remaining = frameCount
        while (remaining > 0) {
            val block = min(remaining, GLIDE_FRAMES)
            currentWet += (targetWet - currentWet) * GLIDE_RATE
            if (currentWet < SETTLED_WET && targetWet <= 0f) currentWet = 0f
            val wet = currentWet
            repeat(block) {
                val readPos = (writePos - delayFrames + lineFrames) % lineFrames
                for (channel in 0 until channelCount) {
                    val dry = inputBuffer.short.toFloat()
                    val delayed = line[readPos * channelCount + channel]
                    line[writePos * channelCount + channel] = dry + delayed * FEEDBACK
                    // Gain-staged send: dry ducks as the repeats rise, so a hot
                    // tail can never stack past full scale into the hard clip.
                    // Unity when parked (wet = 0), ~0.88 dry at max wet.
                    outputBuffer.putShort(clampToShort(dry * (1f - wet * DRY_COMP) + delayed * wet))
                }
                writePos = (writePos + 1) % lineFrames
            }
            remaining -= block
        }
        outputBuffer.flip()
    }

    private fun clampToShort(value: Float): Short =
        value.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()

    companion object {
        private const val TAG = "BitChordEchoSend"

        /** Repeats sit under dry: this is a send, not an instrument. −6 dB at max. */
        private const val MAX_WET = 0.50f

        /** Each repeat keeps this much of itself. ~4 audible tails per throw. */
        private const val FEEDBACK = 0.38f

        /**
         * Dry-compensation slope: dry scales by (1 − wet·DRY_COMP) as the send
         * rises. 0.25 keeps the summed bus under full scale without thinning
         * the dry at musical wet levels — the old 0.5 ducked the vocal just
         * when it needed to sit above the repeats.
         */
        private const val DRY_COMP = 0.25f

        /** Longest single throw. A 1-bar delay at 60 BPM is 4 s; cap above it. */
        private const val MAX_DELAY_SECONDS = 4.5f

        private const val BYTES_PER_SAMPLE = 2
        private const val GLIDE_FRAMES = 64
        private const val GLIDE_RATE = 0.05f
        private const val SETTLED_WET = 0.001f
    }
}

/**
 * The two echo sends a transition rides: one over the track arriving, one
 * over the track leaving. Mirrors [TransitionFilters] — same role-swap
 * reasoning, same test seam — because the sends sit in the same per-player
 * sinks and their roles trade places at the handoff.
 */
interface EchoFilters {
    /** Wet 0..1 plus the repeat period in seconds for the track fading up. */
    fun incoming(wet: Float, delaySeconds: Float)

    /** Wet 0..1 plus the repeat period in seconds for the track fading out. */
    fun outgoing(wet: Float, delaySeconds: Float)

    /** Rides both wets down; tails ring out rather than cutting. */
    fun open() {
        incoming(0f, 0f)
        outgoing(0f, 0f)
    }

    /** For callers with no audio sink — tests, and the default wiring. */
    object None : EchoFilters {
        override fun incoming(wet: Float, delaySeconds: Float) = Unit
        override fun outgoing(wet: Float, delaySeconds: Float) = Unit
    }
}
