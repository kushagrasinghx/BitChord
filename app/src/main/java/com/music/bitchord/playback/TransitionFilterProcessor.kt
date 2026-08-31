package com.music.bitchord.playback

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.tan
import kotlin.math.pow

/**
 * The filter a track rides through a Automix transition: a low-pass that can
 * close over the outgoing track, and a high-pass that can lift the low end out
 * of one side of a blend.
 *
 * ## Why this exists
 *
 * [CrossfadeController] renders every transition as an equal-power gain blend,
 * and a gain blend is the one move that cannot fix the two things that actually
 * make a mix sound amateur:
 *
 *  - **Two basslines at once.** Below roughly 200 Hz a mix has very little room;
 *    two kick drums and two bass parts occupying it simultaneously read as mud
 *    and eat headroom, however carefully the gains are matched. Every DJ mixer
 *    ever built has a bass kill for exactly this, and the fix is the same here:
 *    the low end belongs to exactly one track at a time, and it changes hands
 *    once, on a beat the planner picked
 *    ([com.music.bitchord.playback.smart.TransitionPlan.bassSwapFraction]).
 *  - **Two unrelated tempi at once.** When the tracks are too far apart to
 *    beat-match, their transients simply collide. Closing a low-pass over the
 *    outgoing track pulls it behind the incoming one instead of leaving them to
 *    fight, which is why a filtered handoff is the standard move for a tempo
 *    change.
 *
 * ## The filter
 *
 * A topology-preserving (trapezoidal-integrator) state-variable filter, two
 * second-order sections cascaded to a 24 dB/octave Butterworth response. Chosen
 * over the more familiar Chamberlin SVF because the trapezoidal form is stable
 * at every cutoff up to Nyquist, while Chamberlin's is only well behaved below
 * about a sixth of the sample rate — a low-pass parked wide open at 20 kHz sits
 * far outside that, so the naive form would have to be special-cased at exactly
 * the setting it spends most of its time at.
 *
 * `tan` is evaluated once per sub-block rather than per sample, and the whole
 * thing degenerates to a buffer copy when both cutoffs are parked, so a
 * transition that asks for no filtering costs nothing.
 *
 * ## Gliding
 *
 * Cutoffs are targets, not values. [CrossfadeController] re-aims them once per
 * fade tick (every 30 ms), and stepping a filter in 30 ms jumps is audible as
 * zipper noise, so the real cutoff chases its target geometrically across
 * [GLIDE_FRAMES]-sample sub-blocks. Geometric because cutoff is perceived
 * logarithmically: a linear glide down from 20 kHz would spend nearly all of
 * itself inaudible and then lurch through the last octave.
 */
@UnstableApi
class TransitionFilterProcessor : BaseAudioProcessor() {

    @Volatile
    private var targetLowPassHz: Float = OPEN_HZ

    @Volatile
    private var targetHighPassHz: Float = OFF_HZ
    @Volatile private var targetLowGain = 1f
    @Volatile private var targetMidGain = 1f
    @Volatile private var targetHighGain = 1f
    @Volatile private var echoWet = 0f
    @Volatile private var echoFeedback = 0f
    @Volatile private var echoDelayMs = 0f
    @Volatile private var echoClearRequested = false

    private var channelCount = 0
    private var sampleRate = 0

    private var currentLowPassHz = OPEN_HZ
    private var currentHighPassHz = OFF_HZ
    private var currentLowGain = 1f
    private var currentMidGain = 1f
    private var currentHighGain = 1f
    private var eqLowState = FloatArray(0)
    private var eqMidState = FloatArray(0)
    private var echoBuffer = FloatArray(0)
    private var echoPosition = 0
    private var lowEqAlpha = 0f
    private var midEqAlpha = 0f

    /** Two integrator states per second-order section, per channel. */
    private var lowState = FloatArray(0)
    private var highState = FloatArray(0)

    private val lowA1 = FloatArray(STAGES)
    private val lowA2 = FloatArray(STAGES)
    private val lowA3 = FloatArray(STAGES)
    private val highA1 = FloatArray(STAGES)
    private val highA2 = FloatArray(STAGES)
    private val highA3 = FloatArray(STAGES)
    private val highK = FloatArray(STAGES)

    /**
     * Aims the filter. [lowPassHz] at or above [OPEN_HZ] and [highPassHz] at or
     * below [OFF_HZ] mean "not filtering", which is the state this returns to
     * between transitions.
     */
    fun setCutoffs(lowPassHz: Float, highPassHz: Float) {
        targetLowPassHz = lowPassHz.coerceIn(MIN_HZ, OPEN_HZ)
        targetHighPassHz = highPassHz.coerceIn(OFF_HZ, MAX_HIGH_PASS_HZ)
    }

    /** Parks both filters. Glided, not snapped — see the class doc. */
    fun open() = setCutoffs(OPEN_HZ, OFF_HZ)

    fun setDjEq(lowDb: Float, midDb: Float, highDb: Float) {
        targetLowGain = dbGain(lowDb.coerceIn(-48f, 6f))
        targetMidGain = dbGain(midDb.coerceIn(-48f, 6f))
        targetHighGain = dbGain(highDb.coerceIn(-48f, 6f))
    }

    fun setEcho(delayMs: Float, feedback: Float, wet: Float) {
        echoDelayMs = delayMs.coerceIn(0f, 1_000f)
        echoFeedback = feedback.coerceIn(0f, 0.35f)
        echoWet = wet.coerceIn(0f, 0.30f)
        if (echoWet <= 0f) echoClearRequested = true
    }

    /**
     * 16-bit PCM only, matching [SpatialAudioProcessor] — and bowing out with
     * [AudioProcessor.AudioFormat.NOT_SET] rather than throwing for the same
     * reason it does: `DefaultAudioSink` configures every processor in its chain
     * whether or not the effect is switched on, and a throw from any of them
     * kills the renderer outright. NOT_SET means "inactive for this format" and
     * the chain routes around this processor.
     *
     * Logged rather than silent, because the failure mode of a filter that
     * quietly declines to run is a Phase 4 transition that sounds exactly like a
     * Phase 3 one, with nothing anywhere saying why.
     */
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount < 1) {
            Log.w(
                TAG,
                "Transition filtering inactive: encoding=${inputAudioFormat.encoding} " +
                    "channels=${inputAudioFormat.channelCount} is not 16-bit PCM",
            )
            return AudioProcessor.AudioFormat.NOT_SET
        }
        channelCount = inputAudioFormat.channelCount
        sampleRate = inputAudioFormat.sampleRate
        lowState = FloatArray(channelCount * STAGES * 2)
        highState = FloatArray(channelCount * STAGES * 2)
        eqLowState = FloatArray(channelCount)
        eqMidState = FloatArray(channelCount)
        echoBuffer = FloatArray((sampleRate * channelCount).coerceAtLeast(channelCount))
        lowEqAlpha = onePoleAlpha(220f)
        midEqAlpha = onePoleAlpha(2_800f)
        currentLowPassHz = targetLowPassHz
        currentHighPassHz = targetHighPassHz
        currentLowGain = targetLowGain
        currentMidGain = targetMidGain
        currentHighGain = targetHighGain
        return inputAudioFormat
    }

    override fun onFlush() {
        lowState.fill(0f)
        highState.fill(0f)
        eqLowState.fill(0f)
        eqMidState.fill(0f)
        echoBuffer.fill(0f)
        echoPosition = 0
        // Snapped, not glided: a flush means a seek or a fresh source, so there
        // is no continuous signal for a glide to be continuous with.
        currentLowPassHz = targetLowPassHz
        currentHighPassHz = targetHighPassHz
        currentLowGain = targetLowGain
        currentMidGain = targetMidGain
        currentHighGain = targetHighGain
    }

    override fun onReset() {
        targetLowPassHz = OPEN_HZ
        targetHighPassHz = OFF_HZ
        targetLowGain = 1f
        targetMidGain = 1f
        targetHighGain = 1f
        echoWet = 0f
        echoFeedback = 0f
        echoDelayMs = 0f
        lowState = FloatArray(0)
        highState = FloatArray(0)
        eqLowState = FloatArray(0)
        eqMidState = FloatArray(0)
        echoBuffer = FloatArray(0)
    }

    override fun queueInput(inputBuffer: java.nio.ByteBuffer) {
        val bytesPerFrame = BYTES_PER_SAMPLE * channelCount
        if (bytesPerFrame == 0) return
        val frameCount = inputBuffer.remaining() / bytesPerFrame
        if (frameCount == 0) return
        val outputBuffer = replaceOutputBuffer(frameCount * bytesPerFrame)

        if (echoClearRequested) {
            echoBuffer.fill(0f)
            echoPosition = 0
            echoClearRequested = false
        }

        val targetLow = targetLowPassHz
        val targetHigh = targetHighPassHz
        // Parked at both ends *and* already settled there: nothing to do but
        // hand the buffer straight through. The "already settled" half matters
        // — a transition that has just finished is still gliding back open, and
        // cutting the filter out from under that glide is the click it exists
        // to avoid.
        val eqParked = targetLowGain == 1f && targetMidGain == 1f && targetHighGain == 1f &&
            currentLowGain == 1f && currentMidGain == 1f && currentHighGain == 1f
        val parked = targetLow >= OPEN_HZ && targetHigh <= OFF_HZ && echoWet <= 0f && eqParked &&
            currentLowPassHz >= OPEN_HZ - SETTLED_HZ && currentHighPassHz <= OFF_HZ + SETTLED_HZ
        if (parked) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        inputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer.order(ByteOrder.nativeOrder())

        var remaining = frameCount
        while (remaining > 0) {
            val block = min(remaining, GLIDE_FRAMES)
            currentLowPassHz = glide(currentLowPassHz, targetLow)
            currentHighPassHz = glide(currentHighPassHz, targetHigh)
            currentLowGain += (targetLowGain - currentLowGain) * GAIN_GLIDE_RATE
            currentMidGain += (targetMidGain - currentMidGain) * GAIN_GLIDE_RATE
            currentHighGain += (targetHighGain - currentHighGain) * GAIN_GLIDE_RATE
            if (kotlin.math.abs(currentLowGain - targetLowGain) < GAIN_SETTLED) currentLowGain = targetLowGain
            if (kotlin.math.abs(currentMidGain - targetMidGain) < GAIN_SETTLED) currentMidGain = targetMidGain
            if (kotlin.math.abs(currentHighGain - targetHighGain) < GAIN_SETTLED) currentHighGain = targetHighGain
            val lowOn = currentLowPassHz < OPEN_HZ - SETTLED_HZ
            val highOn = currentHighPassHz > OFF_HZ + SETTLED_HZ
            if (lowOn) updateLowCoefficients()
            if (highOn) updateHighCoefficients()

            repeat(block) {
                for (channel in 0 until channelCount) {
                    var sample = applyDjEq(channel, inputBuffer.short.toFloat())
                    if (lowOn) sample = lowPass(channel, sample)
                    if (highOn) sample = highPass(channel, sample)
                    sample = applyEcho(channel, sample)
                    outputBuffer.putShort(clampToShort(sample))
                }
            }
            remaining -= block
        }
        outputBuffer.flip()
    }

    // ---- Filter ------------------------------------------------------------

    private fun glide(current: Float, target: Float): Float {
        val from = ln(current.coerceAtLeast(MIN_HZ))
        val to = ln(target.coerceAtLeast(MIN_HZ))
        return exp(from + (to - from) * GLIDE_RATE)
    }

    /** Highest cutoff the bilinear transform can still represent without warping to infinity. */
    private fun usableCutoff(hz: Float): Float =
        hz.coerceIn(MIN_HZ, sampleRate * MAX_CUTOFF_FRACTION)

    private fun updateLowCoefficients() {
        val g = tan(Math.PI * usableCutoff(currentLowPassHz) / sampleRate).toFloat()
        for (stage in 0 until STAGES) {
            val k = 1f / BUTTERWORTH_Q[stage]
            val a1 = 1f / (1f + g * (g + k))
            lowA1[stage] = a1
            lowA2[stage] = g * a1
            lowA3[stage] = g * (g * a1)
        }
    }

    private fun updateHighCoefficients() {
        val g = tan(Math.PI * usableCutoff(currentHighPassHz) / sampleRate).toFloat()
        for (stage in 0 until STAGES) {
            val k = 1f / BUTTERWORTH_Q[stage]
            val a1 = 1f / (1f + g * (g + k))
            highA1[stage] = a1
            highA2[stage] = g * a1
            highA3[stage] = g * (g * a1)
            highK[stage] = k
        }
    }

    private fun lowPass(channel: Int, input: Float): Float {
        var value = input
        for (stage in 0 until STAGES) {
            val i = (channel * STAGES + stage) * 2
            val ic1 = lowState[i]
            val ic2 = lowState[i + 1]
            val v3 = value - ic2
            val v1 = lowA1[stage] * ic1 + lowA2[stage] * v3
            val v2 = ic2 + lowA2[stage] * ic1 + lowA3[stage] * v3
            lowState[i] = 2f * v1 - ic1
            lowState[i + 1] = 2f * v2 - ic2
            value = v2
        }
        return value
    }

    private fun highPass(channel: Int, input: Float): Float {
        var value = input
        for (stage in 0 until STAGES) {
            val i = (channel * STAGES + stage) * 2
            val ic1 = highState[i]
            val ic2 = highState[i + 1]
            val v3 = value - ic2
            val v1 = highA1[stage] * ic1 + highA2[stage] * v3
            val v2 = ic2 + highA2[stage] * ic1 + highA3[stage] * v3
            highState[i] = 2f * v1 - ic1
            highState[i + 1] = 2f * v2 - ic2
            value -= highK[stage] * v1 + v2
        }
        return value
    }

    private fun applyDjEq(channel: Int, input: Float): Float {
        val low = eqLowState[channel] + lowEqAlpha * (input - eqLowState[channel])
        val lowMid = eqMidState[channel] + midEqAlpha * (input - eqMidState[channel])
        eqLowState[channel] = low
        eqMidState[channel] = lowMid
        val mid = lowMid - low
        val high = input - lowMid
        return low * currentLowGain + mid * currentMidGain + high * currentHighGain
    }

    private fun onePoleAlpha(hz: Float): Float =
        (1.0 - exp(-2.0 * Math.PI * hz / sampleRate)).toFloat()

    private fun applyEcho(channel: Int, input: Float): Float {
        val wet = echoWet
        if (wet <= 0f || echoBuffer.isEmpty() || echoDelayMs <= 0f) return input
        val delayFrames = (sampleRate * echoDelayMs / 1_000f).toInt().coerceIn(1, sampleRate - 1)
        val delaySamples = delayFrames * channelCount
        val read = (echoPosition - delaySamples + echoBuffer.size) % echoBuffer.size
        val delayed = echoBuffer[(read + channel) % echoBuffer.size]
        echoBuffer[(echoPosition + channel) % echoBuffer.size] = input + delayed * echoFeedback
        if (channel == channelCount - 1) echoPosition = (echoPosition + channelCount) % echoBuffer.size
        return input * (1f - wet) + delayed * wet
    }

    private fun clampToShort(value: Float): Short =
        value.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()

    companion object {
        private const val TAG = "BitChordTransitionFilter"

        /** A low-pass at or above this is doing nothing audible, so it counts as off. */
        const val OPEN_HZ = 20_000f

        /** A high-pass at or below this is doing nothing audible, so it counts as off. */
        const val OFF_HZ = 20f

        /** Nothing musical wants the low end lifted above this, and a typo shouldn't be able to. */
        const val MAX_HIGH_PASS_HZ = 2_000f

        private const val MIN_HZ = 10f
        private const val BYTES_PER_SAMPLE = 2

        /** Two cascaded second-order sections: 24 dB/octave, the usual DJ-filter slope. */
        private const val STAGES = 2

        /** Section Qs for a maximally flat (Butterworth) fourth-order response. */
        private val BUTTERWORTH_Q = floatArrayOf(0.54120f, 1.30656f)

        /** Frames between coefficient updates. ~1.5 ms at 44.1 kHz. */
        private const val GLIDE_FRAMES = 64

        /** Per-sub-block glide fraction. ~30 ms time constant, just under one fade tick. */
        private const val GLIDE_RATE = 0.05f
        private const val GAIN_GLIDE_RATE = 0.08f
        private const val GAIN_SETTLED = 0.0001f

        private fun dbGain(db: Float): Float = 10.0.pow(db / 20.0).toFloat()

        /** How close to a parked value counts as parked, so a glide terminates. */
        private const val SETTLED_HZ = 1f

        /** Keeps `tan` away from its pole at Nyquist. */
        private const val MAX_CUTOFF_FRACTION = 0.45f
    }
}

/**
 * The two filters a transition rides: one over the track arriving, one over the
 * track leaving.
 *
 * An interface rather than the processors themselves so [CrossfadeController]
 * stays testable without an audio sink, and so it never has to know that
 * "incoming" and "outgoing" are two different ExoPlayers whose roles swap at the
 * lap.
 */
interface TransitionFilters {
    /** The track fading up — the session player, once the lap has handed the queue over. */
    fun incoming(lowPassHz: Float, highPassHz: Float)

    /** The track fading out — the ghost player. */
    fun outgoing(lowPassHz: Float, highPassHz: Float)

    fun incomingEq(lowDb: Float, midDb: Float, highDb: Float) = Unit
    fun outgoingEq(lowDb: Float, midDb: Float, highDb: Float) = Unit
    fun outgoingEcho(delayMs: Float, feedback: Float, wet: Float) = Unit

    /** Parks both. Called whenever a transition ends, however it ended. */
    fun open() {
        incoming(TransitionFilterProcessor.OPEN_HZ, TransitionFilterProcessor.OFF_HZ)
        outgoing(TransitionFilterProcessor.OPEN_HZ, TransitionFilterProcessor.OFF_HZ)
        incomingEq(0f, 0f, 0f)
        outgoingEq(0f, 0f, 0f)
        outgoingEcho(0f, 0f, 0f)
    }

    /** For callers with no audio sink to filter — tests, and the default wiring. */
    object None : TransitionFilters {
        override fun incoming(lowPassHz: Float, highPassHz: Float) = Unit
        override fun outgoing(lowPassHz: Float, highPassHz: Float) = Unit
    }
}
