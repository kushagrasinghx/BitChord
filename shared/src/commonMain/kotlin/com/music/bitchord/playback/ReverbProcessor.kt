package com.music.bitchord.playback

import kotlin.concurrent.Volatile
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Algorithmic reverb over interleaved Float32 samples, in place. One instance
 * per sink: the tank is per-stream state two sinks cannot share.
 *
 * ## Why not convolution
 *
 * Convolving against an impulse response synthesised per preset ties every
 * change of room to regenerating seconds of impulse and swapping the kernel under a
 * playing track, and a partitioned convolver either buffers a block of
 * latency or carries an FFT stage per partition. Here every parameter is a
 * slider, so the room is a Schroeder/Moorer tank instead — Freeverb's layout:
 * eight damped feedback combs per side into four allpasses, the right side's
 * delays offset so the two tails decorrelate. No latency, no allocation after
 * [configure], a few dozen multiply-adds per frame, and a slider move takes
 * effect on the next block.
 *
 * The settings still mean what they say as room measurements:
 * - **Decay** sets each comb's feedback so its loop falls 60 dB in that time
 *   (`g = 10^(-3·d / RT60)`), rather than Freeverb's unitless "room size".
 * - **Damping** is the cut-off of the one-pole low-pass inside every loop, so
 *   the tail loses highs on each trip round, the way a real one darkens as it
 *   decays.
 * - **Pre-delay** delays the feed into the tank.
 * - The wet signal is scaled to roughly unit energy against the input (see
 *   [aim]), a unit-energy impulse in other terms, so a cathedral
 *   and a small room sit at about the same level for the same mix.
 *
 * ## Staying out of the way
 *
 * Off and settled, [process] returns before reading a sample, which keeps the
 * Android chain's idle bit-exactness intact. Switching on or off, and every mix
 * change, glides the dry and wet levels over [GLIDE_SECONDS] rather than
 * stepping them, so the toggle in the player can be thrown mid-song without a
 * click. Once a switch-off has faded out, the tank is cleared, so switching back
 * on starts from silence rather than replaying a stale tail.
 *
 * ## Channels
 *
 * Stereo is the case it is built for. Mono feeds the one channel and gets the
 * sum of both tails back. More than two channels (a 5.1 bed) feed and receive
 * the tail on the front pair only, with every channel's dry level scaled alike
 * so the balance between the front and the rest is kept.
 */
class ReverbProcessor {

    /** What the processor is aiming at. Swapped whole, never mutated in place. */
    private class Target(val enabled: Boolean, val settings: ReverbSettings)

    @Volatile
    private var target = Target(false, ReverbSettings.DEFAULT)

    /** Last target turned into coefficients, by identity. */
    private var applied: Target? = null

    /**
     * Aims the reverb. Called from whoever owns the settings, not from the audio
     * thread; nothing here is read until the next block.
     */
    fun setTarget(enabled: Boolean, settings: ReverbSettings) {
        target = Target(enabled, settings.clamped())
    }

    /** Whether the listener has it switched on. */
    val isEnabled: Boolean
        get() = target.enabled

    /** Whether samples are being altered right now: on, or off with a tail still fading. */
    @Volatile
    var isActive: Boolean = false
        private set

    private var sampleRate = 0
    private var channelCount = 0

    private var combBuffers: Array<FloatArray> = emptyArray()
    private val combIndex = IntArray(COMBS * 2)
    private val combStore = FloatArray(COMBS * 2)
    private val combFeedback = FloatArray(COMBS * 2)
    private var allpassBuffers: Array<FloatArray> = emptyArray()
    private val allpassIndex = IntArray(ALLPASSES * 2)

    private var preDelay = FloatArray(0)
    private var preDelayWrite = 0
    private var preDelayFrames = 0

    private var damp = 0f
    private var glide = 0f

    /** The levels as heard — gliding towards the targets below, one sample at a time. */
    private var dry = 1f
    private var wetDirect = 0f
    private var wetCross = 0f
    private var dryTarget = 1f
    private var wetDirectTarget = 0f
    private var wetCrossTarget = 0f

    /** Sizes the tank for [sampleRate]. Allocates; never call it from inside [process]. */
    fun configure(sampleRate: Int, channelCount: Int) {
        this.channelCount = channelCount
        if (sampleRate <= 0 || channelCount < 1) {
            this.sampleRate = 0
            return
        }
        if (sampleRate != this.sampleRate) {
            this.sampleRate = sampleRate
            val scale = sampleRate / TUNING_RATE
            combBuffers = Array(COMBS * 2) { index ->
                val spread = if (index >= COMBS) STEREO_SPREAD else 0
                FloatArray(((COMB_TUNING[index % COMBS] + spread) * scale).roundToInt().coerceAtLeast(1))
            }
            allpassBuffers = Array(ALLPASSES * 2) { index ->
                val spread = if (index >= ALLPASSES) STEREO_SPREAD else 0
                FloatArray(((ALLPASS_TUNING[index % ALLPASSES] + spread) * scale).roundToInt().coerceAtLeast(1))
            }
            preDelay = FloatArray(ReverbSettings.PRE_DELAY_RANGE_MS.last * sampleRate / 1_000 + 1)
            glide = (1.0 - exp(-1.0 / (GLIDE_SECONDS * sampleRate))).toFloat()
        }
        applied = null
        reset()
    }

    /**
     * Empties the tank — a seek or a new stream is not a continuous signal, and
     * a tail of the audio from before it would ring on over the new position.
     * The levels are left where they are, so a seek with the reverb on doesn't
     * fade it back in from nothing.
     */
    fun reset() {
        combBuffers.forEach { it.fill(0f) }
        allpassBuffers.forEach { it.fill(0f) }
        preDelay.fill(0f)
        combIndex.fill(0)
        combStore.fill(0f)
        allpassIndex.fill(0)
        preDelayWrite = 0
    }

    /** Processes the first [sampleCount] interleaved samples of [samples] in place. */
    fun process(samples: FloatArray, sampleCount: Int) {
        val channels = channelCount
        if (sampleRate <= 0 || channels < 1 || sampleCount < channels) return

        val aimed = target
        if (aimed !== applied) aim(aimed)
        if (!isActive) {
            if (!aimed.enabled) return
            isActive = true
        }

        val preDelaySize = preDelay.size
        var index = 0
        val end = sampleCount - channels + 1
        while (index < end) {
            val left = samples[index]
            val right = if (channels > 1) samples[index + 1] else left
            var feed = (left + right) * 0.5f

            if (preDelayFrames > 0) {
                var read = preDelayWrite - preDelayFrames
                if (read < 0) read += preDelaySize
                preDelay[preDelayWrite] = feed
                feed = preDelay[read]
                if (++preDelayWrite == preDelaySize) preDelayWrite = 0
            }

            var tankLeft = 0f
            var tankRight = 0f
            for (comb in 0 until COMBS) {
                tankLeft += comb(comb, feed)
                tankRight += comb(comb + COMBS, feed)
            }
            for (allpass in 0 until ALLPASSES) {
                tankLeft = allpass(allpass, tankLeft)
                tankRight = allpass(allpass + ALLPASSES, tankRight)
            }

            dry += glide * (dryTarget - dry)
            wetDirect += glide * (wetDirectTarget - wetDirect)
            wetCross += glide * (wetCrossTarget - wetCross)
            val wetLeft = tankLeft * wetDirect + tankRight * wetCross
            val wetRight = tankRight * wetDirect + tankLeft * wetCross

            if (channels == 1) {
                samples[index] = left * dry + (wetLeft + wetRight) * 0.5f
            } else {
                samples[index] = left * dry + wetLeft
                samples[index + 1] = right * dry + wetRight
                for (channel in 2 until channels) samples[index + channel] *= dry
            }
            index += channels
        }

        // Faded all the way out: hand the samples back untouched from the next
        // block on, and drop the tail so a switch back on starts clean.
        if (!aimed.enabled && abs(wetDirect) < SETTLED && abs(wetCross) < SETTLED && abs(1f - dry) < SETTLED) {
            dry = 1f
            wetDirect = 0f
            wetCross = 0f
            isActive = false
            reset()
        }
    }

    /** One damped feedback comb (Freeverb's), in slot [slot]. */
    private fun comb(slot: Int, input: Float): Float {
        val buffer = combBuffers[slot]
        val at = combIndex[slot]
        val output = buffer[at]
        val store = flushDenormal(output * (1f - damp) + combStore[slot] * damp)
        combStore[slot] = store
        buffer[at] = input + store * combFeedback[slot]
        combIndex[slot] = if (at + 1 == buffer.size) 0 else at + 1
        return output
    }

    /** One of Freeverb's allpasses, in slot [slot]. */
    private fun allpass(slot: Int, input: Float): Float {
        val buffer = allpassBuffers[slot]
        val at = allpassIndex[slot]
        val delayed = buffer[at]
        buffer[at] = flushDenormal(input + delayed * ALLPASS_FEEDBACK)
        allpassIndex[slot] = if (at + 1 == buffer.size) 0 else at + 1
        return delayed - input
    }

    /**
     * Turns [aimed] into coefficients. Runs on the audio thread, but only when
     * the target has changed — a handful of `pow` calls, not per sample.
     */
    private fun aim(aimed: Target) {
        applied = aimed
        val settings = aimed.settings
        val decaySamples = settings.decayMs / 1_000.0 * sampleRate
        val cutoff = settings.dampingHz.toDouble().coerceAtMost(sampleRate * MAX_DAMPING_FRACTION)
        val pole = exp(-2.0 * PI * cutoff / sampleRate)
        damp = pole.toFloat()

        // A feedback comb's energy for white noise is the mean over frequency
        // of 1 / (1 - |loop gain|²) — 1 / (1 - g²) with a flat loop, less once
        // the damping low-pass is in it, which is what keeps a long dark tail
        // from being normalised as if it rang at full bandwidth. The eight are
        // close enough to uncorrelated that their energies add. The allpass
        // chain adds a fixed gain of its own. Scaling the tank by the inverse
        // square root of the lot puts the tail at about the input's own level
        // whatever the room.
        var energy = 0.0
        for (slot in 0 until COMBS * 2) {
            val gain = 10.0.pow(-3.0 * combBuffers[slot].size / decaySamples)
            combFeedback[slot] = gain.toFloat()
            if (slot >= COMBS) continue
            var mean = 0.0
            for (step in 0 until ENERGY_STEPS) {
                val omega = PI * (step + 0.5) / ENERGY_STEPS
                val lowPass = (1 - pole) * (1 - pole) / (1 - 2 * pole * cos(omega) + pole * pole)
                mean += 1.0 / (1.0 - gain * gain * lowPass)
            }
            energy += mean / ENERGY_STEPS
        }
        val normalise = (1.0 / sqrt(energy * ALLPASS_CHAIN_ENERGY)).toFloat()

        preDelayFrames = (settings.preDelayMs * sampleRate / 1_000).coerceIn(0, preDelay.size - 1)

        val wet = if (aimed.enabled) settings.mix * normalise else 0f
        dryTarget = if (aimed.enabled) 1f - settings.mix else 1f
        wetDirectTarget = wet * (0.5f + settings.width * 0.5f)
        wetCrossTarget = wet * (0.5f - settings.width * 0.5f)
    }

    private fun flushDenormal(value: Float) = if (abs(value) < DENORMAL_FLOOR) 0f else value

    private companion object {
        const val COMBS = 8
        const val ALLPASSES = 4

        /** Freeverb's delays, in samples at [TUNING_RATE]; scaled to the stream's rate. */
        val COMB_TUNING = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
        val ALLPASS_TUNING = intArrayOf(556, 441, 341, 225)
        const val TUNING_RATE = 44_100.0

        /** Extra delay on the right side's filters, so the two tails decorrelate. */
        const val STEREO_SPREAD = 23

        const val ALLPASS_FEEDBACK = 0.5f

        /**
         * Energy gain of the four allpasses in series. Freeverb's are not true
         * allpasses: each is `-1 + z⁻ᴰ / (1 - g·z⁻ᴰ)`, whose impulse response
         * carries `1 + 1 / (1 - g²)` of energy.
         */
        val ALLPASS_CHAIN_ENERGY =
            (1.0 + 1.0 / (1.0 - ALLPASS_FEEDBACK * ALLPASS_FEEDBACK)).pow(ALLPASSES)

        /** Frequencies the comb energy is averaged over; only runs when a setting moves. */
        const val ENERGY_STEPS = 64

        /** Keeps the damping pole clear of Nyquist at low sample rates. */
        const val MAX_DAMPING_FRACTION = 0.45

        /** Time constant of the level glide: fast enough to feel instant, slow enough not to click. */
        const val GLIDE_SECONDS = 0.02

        /** Level difference below which a fade counts as finished: -80 dB. */
        const val SETTLED = 1e-4f

        const val DENORMAL_FLOOR = 1e-15f
    }
}
