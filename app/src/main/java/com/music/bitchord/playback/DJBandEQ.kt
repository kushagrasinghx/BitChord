package com.music.bitchord.playback

import com.music.bitchord.data.TrackLog
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The 3-band DJ EQ a track rides through a Automix transition: LOW (< 200 Hz),
 * MID (200–4000 Hz), HIGH (> 4000 Hz), each with an independent gain per deck.
 *
 * ## Why this exists
 *
 * [CrossfadeController] renders every transition as an equal-power gain blend
 * plus a single LP/HP sweep per player, and neither can fix what actually makes
 * a mix sound amateur: two basslines and two vocals occupying the same spectrum
 * at once. A real DJ mixer controls per-band gain on both decks simultaneously —
 * one deck holds the bass while the other holds the mids — and this is that
 * control, driven per-tick by the EQ schedule for the active transition type.
 *
 * ## The filter
 *
 * Linkwitz-Riley 4th-order crossovers: two cascaded identical 2-pole
 * Butterworth biquads per crossover (per-stage Q = 1/sqrt(2)), giving
 * 24 dB/octave with in-phase outputs, so LP + complementary HP sums back
 * to the input flat. The old single-biquad 12 dB/octave slope leaked bass
 * an octave above the crossover — both kicks fully present 200–400 Hz —
 * which is the "two basslines at once" mud the satisfaction round chased.
 * The high band is derived as input minus the two low-pass outputs, so
 * unity gains reproduce the input bit-exactly apart from float rounding.
 *
 * ## I/O format
 *
 * 16-bit PCM in and out, matching every other processor in the chain. Biquad
 * math runs in float internally with a clamp on the way out. A float-only stage
 * would bow out in normal (S16) playback and only come alive in USB-float mode
 * — inverted from intent — so this follows the [TransitionFilterProcessor]
 * contract instead: non-S16 input returns NOT_SET and the chain routes around.
 *
 * ## Gliding
 *
 * Gains are targets, not values. The controller re-aims them once per fade
 * tick (every 30 ms), and stepping gains in 30 ms jumps is audible as zipper
 * noise, so the live gains chase their targets across [GLIDE_FRAMES]-sample
 * sub-blocks. The per-block fraction is the exact 64-sample equivalent of the
 * spec's 0.0007/sample coefficient (~30 ms time constant): 1 - (1-0.0007)^64.
 */
@UnstableApi
class DJBandEQ : BaseAudioProcessor() {

    @Volatile
    private var targetLow: Float = 1f

    @Volatile
    private var targetMid: Float = 1f

    @Volatile
    private var targetHigh: Float = 1f

    private var smoothLow = 1f
    private var smoothMid = 1f
    private var smoothHigh = 1f

    private var channelCount = 0

    /**
     * Biquad state per crossover per channel: x1, x2, y1, y2, times two
     * cascaded LR4 stages. Sized [channelCount * 8] in [onConfigure], zeroed
     * in [onFlush]. The A arrays hold stage 1, the B arrays stage 2.
     */
    private var lowState = FloatArray(0)
    private var lowStateB = FloatArray(0)
    private var highState = FloatArray(0)
    private var highStateB = FloatArray(0)

    private var lowB0 = 0f
    private var lowB1 = 0f
    private var lowB2 = 0f
    private var lowA1 = 0f
    private var lowA2 = 0f
    private var highB0 = 0f
    private var highB1 = 0f
    private var highB2 = 0f
    private var highA1 = 0f
    private var highA2 = 0f

    /**
     * Aims one band. Gains outside [0, 1] are meaningless here — the EQ only
     * ever removes content — and a typo shouldn't be able to boost.
     */
    fun setGains(low: Float, mid: Float, high: Float) {
        targetLow = low.coerceIn(0f, 1f)
        targetMid = mid.coerceIn(0f, 1f)
        targetHigh = high.coerceIn(0f, 1f)
    }

    /** Parks all bands at unity. Glided, not snapped — see the class doc. */
    fun open() = setGains(1f, 1f, 1f)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount < 1) {
            TrackLog.w(
                TAG,
                "DJ EQ inactive: encoding=${inputAudioFormat.encoding} " +
                    "channels=${inputAudioFormat.channelCount} is not 16-bit PCM",
            )
            return AudioProcessor.AudioFormat.NOT_SET
        }
        channelCount = inputAudioFormat.channelCount
        val sampleRate = inputAudioFormat.sampleRate
        computeCoefficients(LOW_CROSSOVER_HZ, sampleRate, true)
        computeCoefficients(HIGH_CROSSOVER_HZ, sampleRate, false)
        lowState = FloatArray(channelCount * 8)
        lowStateB = FloatArray(channelCount * 8)
        highState = FloatArray(channelCount * 8)
        highStateB = FloatArray(channelCount * 8)
        smoothLow = targetLow
        smoothMid = targetMid
        smoothHigh = targetHigh
        return inputAudioFormat
    }

    override fun onFlush() {
        lowState.fill(0f)
        lowStateB.fill(0f)
        highState.fill(0f)
        highStateB.fill(0f)
        // Snapped, not glided: a flush means a seek or a fresh source, so there
        // is no continuous signal for a glide to be continuous with.
        smoothLow = targetLow
        smoothMid = targetMid
        smoothHigh = targetHigh
    }

    override fun onReset() {
        targetLow = 1f
        targetMid = 1f
        targetHigh = 1f
        lowState = FloatArray(0)
        lowStateB = FloatArray(0)
        highState = FloatArray(0)
        highStateB = FloatArray(0)
    }

    override fun queueInput(inputBuffer: java.nio.ByteBuffer) {
        val bytesPerFrame = BYTES_PER_SAMPLE * channelCount
        if (bytesPerFrame == 0) return
        val frameCount = inputBuffer.remaining() / bytesPerFrame
        if (frameCount == 0) return
        val outputBuffer = replaceOutputBuffer(frameCount * bytesPerFrame)

        val wantLow = targetLow
        val wantMid = targetMid
        val wantHigh = targetHigh
        // Parked at unity *and* already settled there: nothing to do but hand
        // the buffer straight through. Same contract as the sweep filter — a
        // transition that has just finished is still gliding back to unity,
        // and cutting the EQ out from under that glide is the click it exists
        // to avoid.
        val parked = wantLow >= 1f && wantMid >= 1f && wantHigh >= 1f &&
            smoothLow > 1f - SETTLED_GAIN && smoothMid > 1f - SETTLED_GAIN &&
            smoothHigh > 1f - SETTLED_GAIN
        if (parked) {
            // Keep the biquad states warm while parked: disengaging from
            // seconds-old x1/y1 against fresh input starts the filters from
            // stale memory (audible transient). Compute-and-discard costs two
            // biquads and keeps re-engage continuous.
            inputBuffer.mark()
            inputBuffer.order(ByteOrder.nativeOrder())
            repeat(frameCount) {
                for (channel in 0 until channelCount) {
                    val sample = inputBuffer.short.toFloat() / SHORT_SCALE
                    val base = channel * 8
                    val low1 = processLP(lowState, base, lowB0, lowB1, lowB2, lowA1, lowA2, sample)
                    val low = processLP(lowStateB, base, lowB0, lowB1, lowB2, lowA1, lowA2, low1)
                    val rest = sample - low
                    val mid1 = processLP(highState, base, highB0, highB1, highB2, highA1, highA2, rest)
                    processLP(highStateB, base, highB0, highB1, highB2, highA1, highA2, mid1)
                }
            }
            inputBuffer.reset()
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        inputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer.order(ByteOrder.nativeOrder())

        var remaining = frameCount
        while (remaining > 0) {
            val block = min(remaining, GLIDE_FRAMES)
            smoothLow += (wantLow - smoothLow) * GAIN_GLIDE_RATE
            smoothMid += (wantMid - smoothMid) * GAIN_GLIDE_RATE
            smoothHigh += (wantHigh - smoothHigh) * GAIN_GLIDE_RATE
            val gLow = smoothLow
            val gMid = smoothMid
            val gHigh = smoothHigh

            repeat(block) {
                for (channel in 0 until channelCount) {
                    val sample = inputBuffer.short.toFloat() / SHORT_SCALE
                    val base = channel * 8
                    val low1 = processLP(lowState, base, lowB0, lowB1, lowB2, lowA1, lowA2, sample)
                    val low = processLP(lowStateB, base, lowB0, lowB1, lowB2, lowA1, lowA2, low1)
                    val rest = sample - low
                    val mid1 = processLP(highState, base, highB0, highB1, highB2, highA1, highA2, rest)
                    val mid = processLP(highStateB, base, highB0, highB1, highB2, highA1, highA2, mid1)
                    val high = rest - mid
                    outputBuffer.putShort(clampToShort((low * gLow + mid * gMid + high * gHigh) * SHORT_SCALE))
                }
            }
            remaining -= block
        }
        outputBuffer.flip()
    }

    private fun processLP(
        state: FloatArray,
        base: Int,
        b0: Float,
        b1: Float,
        b2: Float,
        a1: Float,
        a2: Float,
        x: Float,
    ): Float {
        val y = b0 * x + b1 * state[base] + b2 * state[base + 1] - a1 * state[base + 2] - a2 * state[base + 3]
        state[base + 1] = state[base]
        state[base] = x
        state[base + 3] = state[base + 2]
        state[base + 2] = y
        return y
    }

    private fun computeCoefficients(cutoffHz: Float, sampleRate: Int, isLow: Boolean) {
        // Clamped below Nyquist like the sweep filter's usableCutoff: the
        // bilinear transform warps to infinity at Nyquist, and a 4 kHz
        // crossover on an 8 kHz voice note must not explode.
        val fc = cutoffHz.coerceIn(MIN_HZ, sampleRate * MAX_CUTOFF_FRACTION)
        val w0 = 2f * PI.toFloat() * fc / sampleRate
        val alpha = sin(w0) / (2f * BUTTERWORTH_Q)
        val cosW0 = cos(w0)
        val a0 = 1f + alpha
        val b0 = ((1f - cosW0) / 2f) / a0
        val b1 = (1f - cosW0) / a0
        val b2 = ((1f - cosW0) / 2f) / a0
        val a1 = (-2f * cosW0) / a0
        val a2 = (1f - alpha) / a0
        if (isLow) {
            lowB0 = b0; lowB1 = b1; lowB2 = b2; lowA1 = a1; lowA2 = a2
        } else {
            highB0 = b0; highB1 = b1; highB2 = b2; highA1 = a1; highA2 = a2
        }
    }

    private fun clampToShort(value: Float): Short =
        value.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()

    companion object {
        private const val TAG = "BitChordDJBandEQ"

        /** Kick lives 40–120 Hz with harmonics to ~250; the low crossover sits just above the harmonics. */
        const val LOW_CROSSOVER_HZ = 250f

        /** Vocal formants peak at 1–4 kHz, so intelligibility stays in MID below this. */
        const val HIGH_CROSSOVER_HZ = 4000f

        private const val BUTTERWORTH_Q = 0.7071f
        private const val MIN_HZ = 10f
        private const val BYTES_PER_SAMPLE = 2
        private const val SHORT_SCALE = 32768f

        /** Frames between gain updates. ~1.5 ms at 44.1 kHz. */
        private const val GLIDE_FRAMES = 64

        /**
         * Per-sub-block gain chase. The exact 64-sample equivalent of the
         * spec's 0.0007/sample coefficient (~30 ms time constant):
         * 1 - (1 - 0.0007)^64.
         */
        private const val GAIN_GLIDE_RATE = 0.0438f

        /** How close to unity counts as settled, so a glide terminates. */
        private const val SETTLED_GAIN = 0.001f

        /** Keeps the bilinear transform away from its pole at Nyquist. */
        private const val MAX_CUTOFF_FRACTION = 0.45f
    }
}

/**
 * The 3-band EQ on both decks: one over the track arriving, one over the
 * track leaving.
 *
 * An interface rather than the processors themselves so [CrossfadeController]
 * stays testable without an audio sink, and so it never has to know that
 * "incoming" and "outgoing" are two different ExoPlayers whose roles swap at
 * the lap.
 */
interface EqFilters {
    /** The track fading up — the session player, once the lap has handed the queue over. */
    fun incoming(low: Float, mid: Float, high: Float)

    /** The track fading out — the ghost player. */
    fun outgoing(low: Float, mid: Float, high: Float)

    /** Parks both decks at unity. Called whenever a transition ends, however it ended. */
    fun open() {
        incoming(1f, 1f, 1f)
        outgoing(1f, 1f, 1f)
    }

    /** For callers with no audio sink to EQ — tests, and the default wiring. */
    object None : EqFilters {
        override fun incoming(low: Float, mid: Float, high: Float) = Unit
        override fun outgoing(low: Float, mid: Float, high: Float) = Unit
    }
}
