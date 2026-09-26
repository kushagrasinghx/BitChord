package com.music.bitchord.desktop

import com.music.bitchord.playback.EqCurve
import com.music.bitchord.playback.EqLayout
import com.music.bitchord.playback.FilterKind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * The app's own equaliser: ten filter sections and a balance trim over the decoded stream.
 *
 * A port of Android's `EqualizerProcessor`, and it drives the same [EqCurve] the Android build
 * does — [EqLayout] and the curve maths live in `shared`, so the two targets cannot drift into
 * different sounds from the same settings.
 *
 * Floats rather than 16-bit PCM: the desktop engine mixes in float all the way to the sink, so
 * there is no quantisation on the way through and no clamp on the way out. Everything else is the
 * same — the topology-preserving state-variable filter with Cytomic's output mixes, chosen because
 * the trapezoidal form stays well behaved at a 60 Hz cutoff where a naive biquad loses precision.
 *
 * Every number here is a target. Dragging the tone puck re-aims them continuously, and stepping
 * coefficients per block is zipper noise, so gains, Qs, the make-up attenuation and the balance all
 * chase their targets across [GLIDE_FRAMES]-frame sub-blocks.
 */
internal class DesktopEqualizer {

    private class Tuning(val curve: EqCurve, val balance: Float) {
        companion object {
            val OFF = Tuning(EqCurve.FLAT, 0f)
        }
    }

    @Volatile
    private var target: Tuning = Tuning.OFF

    private var channels = 0
    private var sampleRate = 0

    private val currentGainDb = FloatArray(EqLayout.SLOTS)
    private val currentQ = FloatArray(EqLayout.SLOTS) { 0.707f }
    private var currentPreampDb = 0f
    private var currentBalance = 0f

    private val coeffA1 = FloatArray(EqLayout.SLOTS)
    private val coeffA2 = FloatArray(EqLayout.SLOTS)
    private val coeffA3 = FloatArray(EqLayout.SLOTS)
    private val mixInput = FloatArray(EqLayout.SLOTS)
    private val mixBand = FloatArray(EqLayout.SLOTS)
    private val mixLow = FloatArray(EqLayout.SLOTS)

    private val activeSlots = IntArray(EqLayout.SLOTS)
    private val running = BooleanArray(EqLayout.SLOTS)

    /** Two integrator states per section, per channel. */
    private var state = FloatArray(0)

    /** Output trim per channel: make-up attenuation, and balance where stereo. */
    private var channelGain = FloatArray(0)

    /**
     * Aims the equaliser. Called from whoever owns the settings, never from the audio thread.
     *
     * `enabled = false` is a flat curve rather than a bypass flag, so switching it off glides down
     * to nothing instead of cutting the current curve out from under a playing track.
     */
    fun setTuning(enabled: Boolean, curve: EqCurve, balance: Float) {
        target = if (enabled) Tuning(curve, balance.coerceIn(-1f, 1f)) else Tuning.OFF
    }

    /** Sizes the state for a stream. Snaps rather than glides: there is nothing to be continuous with. */
    fun configure(channels: Int, sampleRate: Int) {
        if (channels == this.channels && sampleRate == this.sampleRate) return
        this.channels = channels.coerceAtLeast(1)
        this.sampleRate = sampleRate
        state = FloatArray(this.channels * EqLayout.SLOTS * 2)
        channelGain = FloatArray(this.channels) { 1f }
        running.fill(false)
        snapToTarget()
    }

    /** Forgets what the filters were ringing with, for a seek or a fresh source. */
    fun reset() {
        state.fill(0f)
        running.fill(false)
        snapToTarget()
    }

    /** Equalises [count] interleaved samples of [block], in place. */
    fun process(block: FloatArray, count: Int) {
        if (count == 0 || channels == 0 || sampleRate == 0) return
        val tuning = target
        // Flat *and* settled there: an equaliser that has just been switched off is still gliding
        // down, and cutting that glide short is the click it exists to avoid.
        if (isFlat(tuning) && isSettled(tuning)) return

        var index = 0
        while (index < count) {
            val remaining = count - index
            val block_ = min(remaining, GLIDE_FRAMES * channels)
            glideTowards(tuning)
            val active = prepareSections()
            prepareChannelGains()

            var at = index
            val end = index + block_
            while (at < end) {
                for (channel in 0 until channels) {
                    if (at >= end) break
                    var sample = block[at]
                    for (slot in 0 until active) {
                        sample = section(activeSlots[slot], channel, sample)
                    }
                    block[at] = sample * channelGain[channel]
                    at++
                }
            }
            flushDenormals(active)
            index = end
        }
    }

    // ---- Gliding -----------------------------------------------------------

    private fun snapToTarget() {
        val tuning = target
        tuning.curve.gainsDb.copyInto(currentGainDb)
        tuning.curve.qs.copyInto(currentQ)
        currentPreampDb = tuning.curve.preampDb
        currentBalance = tuning.balance
    }

    private fun glideTowards(tuning: Tuning) {
        for (slot in 0 until EqLayout.SLOTS) {
            // Decibels, not linear gain: it is the perceptual unit, and it puts the resting point
            // at zero so a band crossing from cut to boost passes through flat.
            currentGainDb[slot] = linearGlide(currentGainDb[slot], tuning.curve.gainsDb[slot])
            // Q the other way round — a bandwidth halves and doubles, it does not step.
            currentQ[slot] = geometricGlide(currentQ[slot], tuning.curve.qs[slot])
        }
        currentPreampDb = linearGlide(currentPreampDb, tuning.curve.preampDb)
        currentBalance = linearGlide(currentBalance, tuning.balance)
    }

    private fun linearGlide(current: Float, target: Float): Float =
        current + (target - current) * GLIDE_RATE

    private fun geometricGlide(current: Float, target: Float): Float {
        val from = ln(current.coerceAtLeast(MIN_Q))
        val to = ln(target.coerceAtLeast(MIN_Q))
        return exp(from + (to - from) * GLIDE_RATE)
    }

    private fun isFlat(tuning: Tuning): Boolean =
        abs(tuning.balance) < SETTLED_BALANCE &&
            abs(tuning.curve.preampDb) < SETTLED_DB &&
            tuning.curve.gainsDb.all { abs(it) < SETTLED_DB }

    private fun isSettled(tuning: Tuning): Boolean {
        if (abs(currentBalance - tuning.balance) >= SETTLED_BALANCE) return false
        if (abs(currentPreampDb - tuning.curve.preampDb) >= SETTLED_DB) return false
        for (slot in 0 until EqLayout.SLOTS) {
            if (abs(currentGainDb[slot] - tuning.curve.gainsDb[slot]) >= SETTLED_DB) return false
        }
        return true
    }

    // ---- Coefficients ------------------------------------------------------

    /**
     * Which sections are doing anything, with their coefficients brought up to date.
     *
     * A section at 0 dB is arithmetically a wire, so skipping it costs nothing and saves the whole
     * cascade while the other tab's half of [EqLayout] sits idle. Its state is cleared on the way
     * out rather than left stale.
     */
    private fun prepareSections(): Int {
        var active = 0
        for (slot in 0 until EqLayout.SLOTS) {
            if (abs(currentGainDb[slot]) >= SETTLED_DB) {
                updateCoefficients(slot)
                activeSlots[active++] = slot
                running[slot] = true
            } else if (running[slot]) {
                clearState(slot)
                running[slot] = false
            }
        }
        return active
    }

    private fun updateCoefficients(slot: Int) {
        val spec = EqLayout.slots[slot]
        val a = 10f.pow(currentGainDb[slot] / 40f)
        val q = currentQ[slot].coerceAtLeast(MIN_Q)
        val base = tan(PI * usableFrequency(spec.frequencyHz) / sampleRate).toFloat()
        val g: Float
        val k: Float
        when (spec.kind) {
            FilterKind.BELL -> {
                g = base
                k = 1f / (q * a)
                mixInput[slot] = 1f
                mixBand[slot] = k * (a * a - 1f)
                mixLow[slot] = 0f
            }
            FilterKind.LOW_SHELF -> {
                g = base / sqrt(a)
                k = 1f / q
                mixInput[slot] = 1f
                mixBand[slot] = k * (a - 1f)
                mixLow[slot] = a * a - 1f
            }
            FilterKind.HIGH_SHELF -> {
                g = base * sqrt(a)
                k = 1f / q
                mixInput[slot] = a * a
                mixBand[slot] = k * (1f - a) * a
                mixLow[slot] = 1f - a * a
            }
        }
        val d = 1f / (1f + g * (g + k))
        coeffA1[slot] = d
        coeffA2[slot] = g * d
        coeffA3[slot] = g * (g * d)
    }

    /** Highest centre the bilinear transform can still place without warping to infinity. */
    private fun usableFrequency(hz: Float): Float =
        hz.coerceIn(MIN_HZ, sampleRate * MAX_FREQUENCY_FRACTION)

    private fun prepareChannelGains() {
        val preamp = 10f.pow(currentPreampDb / 20f)
        if (channels == 2) {
            // Attenuate the far side rather than lift the near one: a balance that made things
            // louder would be a volume control with a side effect.
            channelGain[0] = preamp * min(1f, 1f - currentBalance)
            channelGain[1] = preamp * min(1f, 1f + currentBalance)
        } else {
            channelGain.fill(preamp)
        }
    }

    // ---- Filter ------------------------------------------------------------

    private fun section(slot: Int, channel: Int, input: Float): Float {
        val i = (channel * EqLayout.SLOTS + slot) * 2
        val ic1 = state[i]
        val ic2 = state[i + 1]
        val v3 = input - ic2
        val v1 = coeffA1[slot] * ic1 + coeffA2[slot] * v3
        val v2 = ic2 + coeffA2[slot] * ic1 + coeffA3[slot] * v3
        state[i] = 2f * v1 - ic1
        state[i + 1] = 2f * v2 - ic2
        return mixInput[slot] * input + mixBand[slot] * v1 + mixLow[slot] * v2
    }

    private fun clearState(slot: Int) {
        for (channel in 0 until channels) {
            val i = (channel * EqLayout.SLOTS + slot) * 2
            state[i] = 0f
            state[i + 1] = 0f
        }
    }

    /**
     * Zeroes integrator states that have decayed to nothing.
     *
     * A filter left ringing out under silence walks its state down towards denormal floats, and
     * denormal arithmetic is orders of magnitude slower on hardware that traps it. On an audio
     * thread with a buffer deadline that is a dropout, and a quiet passage is when it would happen.
     */
    private fun flushDenormals(active: Int) {
        for (index in 0 until active) {
            val slot = activeSlots[index]
            for (channel in 0 until channels) {
                val i = (channel * EqLayout.SLOTS + slot) * 2
                if (abs(state[i]) < DENORMAL_FLOOR) state[i] = 0f
                if (abs(state[i + 1]) < DENORMAL_FLOOR) state[i + 1] = 0f
            }
        }
    }

    internal companion object {
        /** Frames between coefficient updates. ~1.5 ms at 44.1 kHz. */
        const val GLIDE_FRAMES = 64

        /** Per-sub-block glide fraction. ~18 ms time constant — a fast drag still tracks. */
        private const val GLIDE_RATE = 0.08f

        /** Below this a band is doing nothing anyone can hear, so it counts as flat. */
        private const val SETTLED_DB = 0.01f

        private const val SETTLED_BALANCE = 0.0005f
        private const val MIN_Q = 0.05f
        private const val MIN_HZ = 10f

        /** Keeps `tan` away from its pole at Nyquist. */
        private const val MAX_FREQUENCY_FRACTION = 0.45f

        private const val DENORMAL_FLOOR = 1e-12f
    }
}
