package com.music.bitchord.playback

import com.music.bitchord.data.TrackLog
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteOrder
import kotlin.math.min

/**
 * Pipeline spec v2 Â§9: a Schroeder reverb send (4 parallel combs + 2 series
 * allpasses), not a convolver â€” no impulse asset, a few KB of delay memory,
 * a fraction of a percent of CPU.
 *
 * Clones [EchoSendProcessor]'s contract on purpose: volatile targets chased
 * per sub-block (the controller re-aims every fade tick; stepping wet in
 * 30 ms jumps would zipper), opening to zero never wipes the tail, and
 * [clear]/[onFlush] are the only wipe, for seeks and fresh sources â€” never
 * call mid-transition.
 *
 * ## Freeze
 *
 * [freeze] mutes the input into the network and pins comb feedback at unity,
 * so the current tail sustains instead of decaying â€” the heavy-clash ending
 * (Â§9b). Unfreezing restores the musical feedback and the tail drains.
 */
@UnstableApi
class ReverbProcessor : BaseAudioProcessor() {

    @Volatile
    private var targetWet: Float = 0f

    @Volatile
    private var frozen: Boolean = false

    private var channelCount = 0
    private var sampleRate = 0
    private var currentWet: Float = 0f

    private var combs = Array(0) { FloatArray(0) }
    private var combPos = IntArray(0)
    private var allpasses = Array(0) { FloatArray(0) }
    private var allpassPos = IntArray(0)
    private var darkState = FloatArray(0)
    private var darkAlpha = 0.3f

    /**
     * Aims the send. [wet] 0..1 is the reverberated level against dry;
     * [freeze] sustains the current tail (Â§9b). Idempotent and glide-safe to
     * call every fade tick.
     */
    fun setReverb(wet: Float, freeze: Boolean) {
        targetWet = wet.coerceIn(0f, MAX_WET)
        frozen = freeze
    }

    /** Rides the wet down and unfreezes; the tail drains rather than cutting. */
    fun open() = setReverb(0f, false)

    /** Wipes every line. Seeks only â€” never call mid-transition. */
    fun clear() {
        combs.forEach { it.fill(0f) }
        allpasses.forEach { it.fill(0f) }
        combPos.fill(0)
        allpassPos.fill(0)
        darkState.fill(0f)
        currentWet = targetWet
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount < 1) {
            TrackLog.w(
                TAG,
                "Reverb send inactive: encoding=${inputAudioFormat.encoding} " +
                    "channels=${inputAudioFormat.channelCount} is not 16-bit PCM",
            )
            return AudioProcessor.AudioFormat.NOT_SET
        }
        channelCount = inputAudioFormat.channelCount
        sampleRate = inputAudioFormat.sampleRate
        combs = Array(COMB_DELAYS_MS.size) { i ->
            FloatArray(msToFrames(COMB_DELAYS_MS[i]) * channelCount)
        }
        combPos = IntArray(COMB_DELAYS_MS.size)
        allpasses = Array(ALLPASS_DELAYS_MS.size) { i ->
            FloatArray(msToFrames(ALLPASS_DELAYS_MS[i]) * channelCount)
        }
        allpassPos = IntArray(ALLPASS_DELAYS_MS.size)
        currentWet = targetWet
        val rc = 1f / (6.2831853f * INPUT_DARKEN_HZ)
        val dt = 1f / sampleRate.coerceAtLeast(8000)
        darkAlpha = (dt / (rc + dt)).coerceIn(0.05f, 1f)
        darkState = FloatArray(channelCount)
        return inputAudioFormat
    }

    override fun onFlush() = clear()

    override fun onReset() {
        targetWet = 0f
        frozen = false
        currentWet = 0f
        combs = Array(0) { FloatArray(0) }
        combPos = IntArray(0)
        allpasses = Array(0) { FloatArray(0) }
        allpassPos = IntArray(0)
        darkState = FloatArray(0)
    }

    override fun queueInput(inputBuffer: java.nio.ByteBuffer) {
        val bytesPerFrame = BYTES_PER_SAMPLE * channelCount
        if (bytesPerFrame == 0 || combs.isEmpty()) return
        val frameCount = inputBuffer.remaining() / bytesPerFrame
        if (frameCount == 0) return
        val outputBuffer = replaceOutputBuffer(frameCount * bytesPerFrame)

        val parked = targetWet <= 0f && currentWet <= SETTLED_WET
        if (parked) {
            currentWet = 0f
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        inputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer.order(ByteOrder.nativeOrder())

        val freeze = frozen
        var remaining = frameCount
        while (remaining > 0) {
            val block = min(remaining, GLIDE_FRAMES)
            currentWet += (targetWet - currentWet) * GLIDE_RATE
            if (currentWet < SETTLED_WET && targetWet <= 0f) currentWet = 0f
            val wet = currentWet
            repeat(block) {
                for (channel in 0 until channelCount) {
                    val dry = inputBuffer.short.toFloat()
                    val darkened = darkState[channel] + darkAlpha * (dry - darkState[channel])
                    darkState[channel] = darkened
                    val input = if (freeze) 0f else darkened
                    var acc = 0f
                    for (i in combs.indices) {
                        val line = combs[i]
                        val frames = line.size / channelCount
                        val pos = combPos[i]
                        val delayed = line[pos * channelCount + channel]
                        val feedback = if (freeze) 0.92f else COMB_FEEDBACK
                        line[pos * channelCount + channel] = input + delayed * feedback
                        acc += delayed
                    }
                    acc /= combs.size
                    for (a in allpasses.indices) {
                        val line = allpasses[a]
                        val frames = line.size / channelCount
                        val pos = allpassPos[a]
                        val delayed = line[pos * channelCount + channel]
                        val out = -ALLPASS_FEEDBACK * acc + delayed
                        line[pos * channelCount + channel] = acc + ALLPASS_FEEDBACK * delayed
                        acc = out
                    }
                    outputBuffer.putShort(clampToShort(dry * (1f - wet * DRY_COMP) + acc * wet))
                }
                for (i in combs.indices) {
                    combPos[i] = (combPos[i] + 1) % (combs[i].size / channelCount)
                }
                for (a in allpasses.indices) {
                    allpassPos[a] = (allpassPos[a] + 1) % (allpasses[a].size / channelCount)
                }
            }
            remaining -= block
        }
        outputBuffer.flip()
    }

    private fun msToFrames(ms: Float): Int =
        ((ms / 1000f) * sampleRate).toInt().coerceAtLeast(8)

    private fun clampToShort(value: Float): Short =
        value.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()

    companion object {
        private const val TAG = "BitChordReverb"

        /** Matches the echo send: a send, not an instrument. âˆ’9 dB at max. */
        private const val MAX_WET = 0.34f

        /**
         * Dry-compensation slope, mirroring the echo send so the series stack
         * (echo into reverb) stays gain-staged at both stages.
         */
        private const val DRY_COMP = 0.25f

        /** Musical decay: ~2.5 s to âˆ’60 dB across the four combs. */
        private const val COMB_FEEDBACK = 0.84f
        private const val ALLPASS_FEEDBACK = 0.5f

        /** Classic Schroeder spacings, mutually near-prime. */
        private val COMB_DELAYS_MS = floatArrayOf(29.7f, 37.1f, 41.1f, 43.7f)
        private val ALLPASS_DELAYS_MS = floatArrayOf(5.0f, 1.7f)

        /** Corner of the input-darkening one-pole. Above the vocal band. */
        private const val INPUT_DARKEN_HZ = 5500f

        private const val BYTES_PER_SAMPLE = 2
        private const val GLIDE_FRAMES = 64
        private const val GLIDE_RATE = 0.05f
        private const val SETTLED_WET = 0.001f
    }
}

/**
 * The two reverb sends a transition rides: one over the track arriving, one
 * over the track leaving. Mirrors [EchoFilters] â€” same role-swap reasoning,
 * same test seam â€” because the sends sit after the echo sends in the same
 * per-player sinks and their roles trade places at the handoff.
 */
interface ReverbFilters {
    /** Wet 0..1 for the track fading up; freeze sustains its tail. */
    fun incoming(wet: Float, freeze: Boolean = false)

    /** Wet 0..1 for the track fading out; freeze sustains its tail. */
    fun outgoing(wet: Float, freeze: Boolean = false)

    /** Rides both wets down and unfreezes; tails drain rather than cutting. */
    fun open() {
        incoming(0f, false)
        outgoing(0f, false)
    }

    /** For callers with no audio sink â€” tests, and the default wiring. */
    object None : ReverbFilters {
        override fun incoming(wet: Float, freeze: Boolean) = Unit
        override fun outgoing(wet: Float, freeze: Boolean) = Unit
    }
}
