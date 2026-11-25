package com.music.bitchord.playback

import com.music.bitchord.playback.audio.AudioBlock
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The Karaoke DSP backend: stereo mid/side vocal reduction.
 *
 * Vocal reduction, not guaranteed vocal removal. Center-panned content in the
 * vocal frequency range is what a lead vocal usually is, but centered
 * instruments ride with it, hard-panned or doubled vocals do not sit in the
 * middle at all, and a mono recording has no middle to subtract. Results vary
 * by recording — the player says "reduction" for that reason, and so does
 * this class.
 *
 * ## The shape
 *
 * For each stereo frame:
 *
 * ```text
 * mid  = (left + right) / 2      — the centered content
 * mod  = low(mid) + air(mid) - mid — the centered content the band keeps, minus what came in
 * leftOut  = left  + suppression * mod
 * rightOut = right + suppression * mod
 * ```
 *
 * `mid` is split by cascaded Linkwitz-Riley crossovers, whose low and high
 * outputs sum to a flat-magnitude allpass: `low` is everything below
 * [PROTECTED_LOW_HZ] (bass, kick) and `air` everything above
 * [VOCAL_PRESENCE_HZ] (cymbals), both kept at full level. So `mod` is minus
 * the centered vocal band and only that band — subtracting it equally from
 * both channels fades the vocal while `leftOut - rightOut` stays exactly
 * `left - right`, and hard-panned content, which lives in the side, rides
 * through untouched.
 *
 * The modification is aimed, not the filter: `suppression` scales it, so the
 * stage morphs from identity the way the equaliser's glide does — a stale or
 * empty filter history can only reach the output at the glide's current
 * scale, which is what keeps an enable mid-track from clicking.
 *
 * `suppression` is `1 - vocalLevel` while enabled and 0 while disabled — the
 * slider's 0% is maximum reduction and 100% is the original mix.
 *
 * ## Staying out of the way
 *
 * Disabled, this stage returns before it reads a single sample, exactly like
 * every other stage of the chain — see
 * [DspChain][com.music.bitchord.playback.audio.DspChain]. Disabling glides
 * the suppression down over ~18 ms rather than cutting it, then hands back a
 * bit-exact block once settled, so the chain's idle passthrough guarantee
 * holds with Karaoke in it.
 *
 * ## Gliding
 *
 * Following [EqualizerProcessor]: the suppression is a target chased at
 * sub-block boundaries ([GLIDE_FRAMES] frames), with the equaliser's glide
 * rate, so a rapid slider drag never steps the gain audibly.
 *
 * ## Formats
 *
 * Stereo only. Mono and multichannel blocks return unaltered with
 * [isShaping] false — the status model reports those as unavailable rather
 * than the processor upmixing a fake stereo image to cancel against.
 *
 * No limiter of its own: the subtraction keeps the band-passed pieces of an
 * already-bounded signal, and the end-of-chain limiter in
 * [LoudnessProcessor] remains responsible for whatever overs a transient can
 * still produce.
 */
class StereoVocalReductionProcessor : VocalProcessor {

    /** 0 = original mix, 1 = maximum reduction. Written by [setTuning], read on the audio thread. */
    @Volatile
    private var targetSuppression = 0f

    /** The suppression currently in force, glided per sub-block. Audio thread only. */
    private var suppression = 0f

    private var sampleRate = 0
    private var channelCount = 0

    /**
     * Normalized biquad coefficients per section. Sections come in cascaded
     * pairs: 0–1 the protected low-pass, 2–3 the low-pass's high-pass
     * complement, 4–5 the air high-pass fed by that complement.
     */
    private val coeffB0 = FloatArray(SECTIONS)
    private val coeffB1 = FloatArray(SECTIONS)
    private val coeffB2 = FloatArray(SECTIONS)
    private val coeffA1 = FloatArray(SECTIONS)
    private val coeffA2 = FloatArray(SECTIONS)

    /** Direct-form-I state per section: x1, x2, y1, y2. */
    private val state = FloatArray(SECTIONS * 4)

    override var isShaping: Boolean = false
        private set

    /**
     * Aims the processor. `vocalLevel` is clamped to 0..1, where 0 is maximum
     * reduction and 1 is the original mix; `enabled = false` targets zero
     * suppression so the stage glides down to exact passthrough like every
     * other change, rather than cutting out from under a playing track.
     */
    override fun setTuning(enabled: Boolean, vocalLevel: Float) {
        val level = vocalLevel.coerceIn(0f, 1f)
        targetSuppression = if (enabled) 1f - level else 0f
    }

    override fun configure(sampleRate: Int, channelCount: Int) {
        this.sampleRate = sampleRate
        this.channelCount = channelCount
        state.fill(0f)
        isShaping = false
        if (channelCount == 2 && sampleRate > 0) {
            designCrossover(SECTION_LOW_LP, lowPass = true, PROTECTED_LOW_HZ, sampleRate)
            designCrossover(SECTION_LOW_HP, lowPass = false, PROTECTED_LOW_HZ, sampleRate)
            designCrossover(SECTION_AIR_HP, lowPass = false, VOCAL_PRESENCE_HZ, sampleRate)
        }
        // A configuration is a fresh start, not a change to glide through —
        // the equaliser snaps on configure for the same reason.
        suppression = targetSuppression
    }

    /**
     * Processes interleaved Float32 audio in [block] in-place.
     * Preserves dynamic headroom without clamping to [-1.0f, +1.0f].
     */
    override fun process(block: AudioBlock) {
        val frameCount = block.frameCount
        if (frameCount == 0) return
        if (channelCount != 2 || block.channelCount != 2 || sampleRate <= 0) {
            isShaping = false
            return
        }

        val target = targetSuppression
        // Settled idle: return before reading a single sample, so the block
        // leaves bit-identical to how it arrived.
        if (target <= 0f && suppression == 0f) {
            isShaping = false
            return
        }

        var shaped = false
        val samples = block.samples
        var remaining = frameCount
        var frameOffset = 0
        while (remaining > 0) {
            val subBlock = min(remaining, GLIDE_FRAMES)
            suppression += (target - suppression) * GLIDE_RATE
            // A glide down asymptotes without ever landing: clamp it so the
            // next block takes the idle path and goes exact.
            if (target <= 0f && suppression < SETTLED_SUPPRESSION) suppression = 0f
            val s = suppression
            if (s > 0f) shaped = true

            var index = frameOffset * 2
            for (frame in 0 until subBlock) {
                val left = samples[index]
                val right = samples[index + 1]
                val mid = (left + right) * 0.5f

                var low = section(SECTION_LOW_LP, mid)
                low = section(SECTION_LOW_LP + 1, low)

                var rest = section(SECTION_LOW_HP, mid)
                rest = section(SECTION_LOW_HP + 1, rest)

                var air = section(SECTION_AIR_HP, rest)
                air = section(SECTION_AIR_HP + 1, air)

                // Minus the centered vocal band; the kept bands and the side
                // content ride through this unchanged. The glide scales the
                // modification, not the filter, so a stale or empty filter
                // history cannot reach the output at full force.
                val modification = (low + air - mid) * s
                // Headroom is preserved: no clamping to [-1.0f, +1.0f].
                samples[index] = left + modification
                samples[index + 1] = right + modification
                index += 2
            }
            flushDenormals()
            frameOffset += subBlock
            remaining -= subBlock
        }
        isShaping = shaped
    }

    /**
     * Clears filter history after a seek or a stream boundary, and snaps the
     * suppression to its target: the audio either side of the discontinuity
     * was at target already, so there is nothing to glide to.
     */
    override fun flush() {
        state.fill(0f)
        suppression = targetSuppression
    }

    override fun reset() {
        state.fill(0f)
        suppression = 0f
        isShaping = false
        sampleRate = 0
        channelCount = 0
    }

    // ---- Filters -----------------------------------------------------------

    /**
     * One normalized biquad section. Direct form I, audio thread only, all
     * state in [state] — no allocation, no object per section.
     */
    private fun section(sectionIndex: Int, input: Float): Float {
        val i = sectionIndex * 4
        val x1 = state[i]
        val x2 = state[i + 1]
        val y1 = state[i + 2]
        val y2 = state[i + 3]
        val output = coeffB0[sectionIndex] * input +
            coeffB1[sectionIndex] * x1 +
            coeffB2[sectionIndex] * x2 +
            coeffA1[sectionIndex] * y1 +
            coeffA2[sectionIndex] * y2
        state[i] = input
        state[i + 1] = x1
        state[i + 2] = output
        state[i + 3] = y1
        return output
    }

    /**
     * Two cascaded second-order sections: a fourth-order Linkwitz-Riley
     * crossover arm, Butterworth Q per section so the low and high arms of a
     * crossover sum to a flat-magnitude allpass.
     */
    private fun designCrossover(start: Int, lowPass: Boolean, cornerHz: Float, sampleRate: Int) {
        val corner = cornerHz.coerceIn(MIN_HZ, sampleRate * MAX_FREQUENCY_FRACTION)
        val w0 = 2.0 * Math.PI * corner / sampleRate
        val cosW0 = cos(w0).toFloat()
        val alpha = (sin(w0) / (2.0 * Q_BUTTERWORTH)).toFloat()
        val a0 = 1f + alpha
        // RBJ cookbook sections, normalized so that in [section]:
        // y = b0*x + b1*x1 + b2*x2 + A1*y1 + A2*y2, with A1 = -(a1/a0)
        // and A2 = -(a2/a0) for the cookbook's a1 = -2cos(w0), a2 = 1-alpha.
        val a1 = 2f * cosW0 / a0
        val a2 = -(1f - alpha) / a0
        for (section in 0 until 2) {
            val index = start + section
            if (lowPass) {
                coeffB0[index] = ((1f - cosW0) / 2f) / a0
                coeffB1[index] = (1f - cosW0) / a0
                coeffB2[index] = ((1f - cosW0) / 2f) / a0
            } else {
                coeffB0[index] = ((1f + cosW0) / 2f) / a0
                coeffB1[index] = -(1f + cosW0) / a0
                coeffB2[index] = ((1f + cosW0) / 2f) / a0
            }
            coeffA1[index] = a1
            coeffA2[index] = a2
        }
    }

    /** Safe noise floor threshold to prevent floating point denormals. */
    private fun flushDenormals() {
        for (i in state.indices) {
            if (abs(state[i]) < DENORMAL_FLOOR) state[i] = 0f
        }
    }

    companion object {
        /** Frames between suppression updates. ~1.5 ms at 44.1 kHz, as the equaliser glides. */
        private const val GLIDE_FRAMES = 64

        /** Per-sub-block glide fraction. ~18 ms time constant — a fast drag still tracks. */
        private const val GLIDE_RATE = 0.08f

        /** Below this a glide down counts as over: suppression becomes exactly 0. */
        private const val SETTLED_SUPPRESSION = 1e-4f

        /**
         * Centered content below this is protected — bass and kick stay in
         * the mix when the vocal goes. Starting point of the plan's
         * 180–220 Hz range; to be tuned by listening, not by fiat.
         */
        private const val PROTECTED_LOW_HZ = 200f

        /**
         * The top of the reduction band. Centered content above it —
         * cymbals, air — is left alone; sibilance rides the band below it.
         */
        private const val VOCAL_PRESENCE_HZ = 8000f

        /** Biquad sections: two per crossover arm, three arms. */
        private const val SECTIONS = 6

        /** Butterworth per section; cascaded pairs make the crossovers. */
        private const val Q_BUTTERWORTH = 0.70710678f

        private const val MIN_HZ = 10f

        /** Keeps the bilinear transform away from its pole at Nyquist. */
        private const val MAX_FREQUENCY_FRACTION = 0.45f

        /** Safe noise floor threshold to prevent floating point denormals. */
        private const val DENORMAL_FLOOR = 1e-12f

        private const val SECTION_LOW_LP = 0
        private const val SECTION_LOW_HP = 2
        private const val SECTION_AIR_HP = 4
    }
}
