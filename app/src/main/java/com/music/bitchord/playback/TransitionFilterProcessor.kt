package com.music.bitchord.playback

import com.music.bitchord.data.TrackLog
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.tan

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

    /**
     * Review v2.1 C1 resonance multiplier for the outgoing low-pass during
     * filter sweeps. 1.0 = flat Butterworth (all other styles); 1.8 = mild
     * DJ-style resonant peak at the cutoff. Applied to the low-pass only —
     * resonance on a high-pass sounds bad. Volatile like the cutoff targets
     * because the controller re-aims it once per fade tick.
     *
     * Click audit P1: this field is the TARGET. An instant Q jump on running
     * audio is a coefficient discontinuity (a click), so the per-block loop
     * chases [currentResonanceQ] toward it exactly like the cutoff glide.
     */
    @Volatile
    private var resonanceQ: Float = 1.0f

    /** Live Q the coefficients are computed from; chases [resonanceQ]. */
    private var currentResonanceQ = 1.0f

    private var channelCount = 0
    private var sampleRate = 0

    private var currentLowPassHz = OPEN_HZ
    private var currentHighPassHz = OFF_HZ

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

    /**
     * Review v2.1 C1: aims the sweep resonance. Clamped to 1.0–2.5
     * (DJ standard 1.5–2.5); the controller parks it at 1.0 outside
     * DJ_FILTER so resonance never leaks into other styles. Target only —
     * the per-block loop glides the live value, so re-aiming mid-sweep
     * never steps the coefficients.
     */
    fun setResonance(q: Float) {
        resonanceQ = q.coerceIn(1.0f, 2.5f)
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
            TrackLog.w(
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
        currentLowPassHz = targetLowPassHz
        currentHighPassHz = targetHighPassHz
        return inputAudioFormat
    }

    override fun onFlush() {
        lowState.fill(0f)
        highState.fill(0f)
        // Snapped, not glided: a flush means a seek or a fresh source, so there
        // is no continuous signal for a glide to be continuous with.
        currentLowPassHz = targetLowPassHz
        currentHighPassHz = targetHighPassHz
    }

    override fun onReset() {
        targetLowPassHz = OPEN_HZ
        targetHighPassHz = OFF_HZ
        lowState = FloatArray(0)
        highState = FloatArray(0)
    }

    override fun queueInput(inputBuffer: java.nio.ByteBuffer) {
        val bytesPerFrame = BYTES_PER_SAMPLE * channelCount
        if (bytesPerFrame == 0) return
        val frameCount = inputBuffer.remaining() / bytesPerFrame
        if (frameCount == 0) return
        val outputBuffer = replaceOutputBuffer(frameCount * bytesPerFrame)

        val targetLow = targetLowPassHz
        val targetHigh = targetHighPassHz
        // Parked at both ends *and* already settled there: nothing to do but
        // hand the buffer straight through. The "already settled" half matters
        // — a transition that has just finished is still gliding back open, and
        // cutting the filter out from under that glide is the click it exists
        // to avoid.
        val parked = targetLow >= OPEN_HZ && targetHigh <= OFF_HZ &&
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
            // Q chases linearly: its range (1.0–2.5) is narrow enough that a
            // log-domain glide buys nothing, and the same ~30 ms time constant
            // keeps the resonant peak from ever stepping.
            currentResonanceQ += (resonanceQ - currentResonanceQ) * GLIDE_RATE
            val lowOn = currentLowPassHz < OPEN_HZ - SETTLED_HZ
            val highOn = currentHighPassHz > OFF_HZ + SETTLED_HZ
            if (lowOn) updateLowCoefficients()
            if (highOn) updateHighCoefficients()

            repeat(block) {
                for (channel in 0 until channelCount) {
                    var sample = inputBuffer.short.toFloat()
                    if (lowOn) sample = lowPass(channel, sample)
                    if (highOn) sample = highPass(channel, sample)
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
            // C1: stage Q raised by the resonance multiplier — a peak grows
            // at the cutoff as the sweep closes, the DJ "whoosh".
            val k = 1f / (BUTTERWORTH_Q[stage] * currentResonanceQ)
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

        /**
         * Review v2.1 C1 resonance for the sweep low-pass. 1.0 = flat (all
         * other styles); 1.8 = mild resonant peak at the cutoff, DJ standard
         * 1.5–2.5. Outgoing LP only — resonance on a high-pass sounds bad.
         */
        const val FILTER_SWEEP_Q_FACTOR = 1.8f
        /** Resonance park value: unity multiplier, the state outside DJ_FILTER. */
        const val NEUTRAL_Q = 1.0f

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

    /** Parks both. Called whenever a transition ends, however it ended. */
    fun open() {
        // Resonance parks too: a DJ_FILTER arm's Q=1.8 left aimed would meet
        // the next transition's first sweep target mid-glide — the click the
        // resonant-sweep audit chased. Neutral Q is unity-adjacent, so the
        // chase home is inaudible.
        setResonance(TransitionFilterProcessor.NEUTRAL_Q)
        incoming(TransitionFilterProcessor.OPEN_HZ, TransitionFilterProcessor.OFF_HZ)
        outgoing(TransitionFilterProcessor.OPEN_HZ, TransitionFilterProcessor.OFF_HZ)
    }

    /** Review v2.1 C1 sweep resonance; default no-op so fakes stay trivial. */
    fun setResonance(q: Float) = Unit

    /** For callers with no audio sink to filter — tests, and the default wiring. */
    object None : TransitionFilters {
        override fun incoming(lowPassHz: Float, highPassHz: Float) = Unit
        override fun outgoing(lowPassHz: Float, highPassHz: Float) = Unit
    }
}
