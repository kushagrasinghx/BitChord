package com.music.bitchord.playback

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.music.bitchord.playback.audio.AudioBlock
import com.music.bitchord.playback.audio.FloatAudioProcessor
import com.music.bitchord.playback.audio.PcmBoundary
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * 10-band parametric equaliser, tone controls, pre-amp and left/right balance.
 *
 * ## The layout
 *
 * [EqLayout] fixes the layout of sections. Bands 0 and 9 are shelves (cornered
 * at 60 Hz and 14 kHz); the eight between them are peaking bells. The tone
 * controls (Bass / Treble) share the two shelf sections rather than sitting in
 * series with them, so a curve that touches both adds the two gains together.
 *
 * Balance is an output trim rather than an extra section, and does not run at
 * all for mono files — see [onConfigure].
 *
 * ## State-variable filters
 *
 * Implemented as two-integrator state-variable sections using the bilinear
 * transform with frequency pre-warping (the standard trapezoidal SVF). Chosen
 * because SVFs decouple frequency from Q — changing gain or bandwidth does not
 * shift the centre — and because their internal states stay bounded under
 * parameter modulation, which lets the user drag a band smoothly without
 * generating transients.
 *
 * ## Gliding
 *
 * Every slider change is a target, not a step. The processor updates its
 * coefficients once per [GLIDE_FRAMES] frames, chasing the target curve
 * smoothly. This turns an abrupt preset switch into a short, inaudible glide
 * instead of a transient that would clip or click.
 */
@UnstableApi
class EqualizerProcessor : BaseAudioProcessor() {

    /** What the processor is aiming at. Swapped whole, never mutated in place. */
    private class Tuning(val curve: EqCurve, val balance: Float) {
        companion object {
            val OFF = Tuning(EqCurve.FLAT, 0f)
        }
    }

    @Volatile
    private var target: Tuning = Tuning.OFF

    private var channelCount = 0
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

    /** Which sections are worth running this sub-block, and how many. */
    private val activeSlots = IntArray(EqLayout.SLOTS)
    private val running = BooleanArray(EqLayout.SLOTS)

    /** Two integrator states per section, per channel. */
    private var state = FloatArray(0)

    /** Output trim per channel: make-up attenuation, and balance where stereo. */
    private var channelGain = FloatArray(0)

    /**
     * Aims the equaliser. Called from whoever owns the settings, not from the
     * audio thread; nothing here is read until the next sub-block boundary.
     *
     * `enabled = false` is a flat curve and a centred balance rather than a
     * bypass flag, so switching the equaliser off glides down to nothing like
     * every other change instead of cutting the current curve out from under a
     * playing track.
     */
    fun setTuning(enabled: Boolean, curve: EqCurve, balance: Float) {
        target = if (enabled) Tuning(curve, balance.coerceIn(-1f, 1f)) else Tuning.OFF
    }

    val isEnabled: Boolean
        get() = target !== Tuning.OFF

    /**
     * Configures the Float32 DSP engine for [sampleRate] and [channelCount].
     */
    fun configure(sampleRate: Int, channelCount: Int) {
        this.sampleRate = sampleRate
        this.channelCount = channelCount
        val requiredSize = channelCount * EqLayout.SLOTS * 2
        if (state.size != requiredSize) {
            state = FloatArray(requiredSize)
        }
        if (channelGain.size != channelCount) {
            channelGain = FloatArray(channelCount) { 1f }
        }
        running.fill(false)
        snapToTarget()
    }

    /**
     * Processes interleaved Float32 audio samples in [block] in-place.
     * Preserves dynamic headroom without clamping to [-1.0f, +1.0f].
     */
    fun process(block: AudioBlock) {
        val frameCount = block.frameCount
        if (frameCount == 0 || channelCount < 1 || sampleRate <= 0) return

        val tuning = target
        if (isFlat(tuning) && isSettled(tuning)) {
            return
        }

        var remaining = frameCount
        var frameOffset = 0
        while (remaining > 0) {
            val subBlock = min(remaining, GLIDE_FRAMES)
            glideTowards(tuning)
            val active = prepareSections()
            prepareChannelGains()

            for (f in 0 until subBlock) {
                val baseIdx = (frameOffset + f) * channelCount
                for (channel in 0 until channelCount) {
                    var sample = block.samples[baseIdx + channel]
                    for (index in 0 until active) {
                        sample = section(activeSlots[index], channel, sample)
                    }
                    // Headroom is preserved: sample is stored directly as Float
                    block.samples[baseIdx + channel] = sample * channelGain[channel]
                }
            }
            flushDenormals(active)
            frameOffset += subBlock
            remaining -= subBlock
        }
    }

    /**
     * 16-bit PCM, any channel count.
     */
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount < 1) {
            Log.w(
                TAG,
                "Equaliser inactive: encoding=${inputAudioFormat.encoding} " +
                    "channels=${inputAudioFormat.channelCount} is not 16-bit PCM",
            )
            return AudioProcessor.AudioFormat.NOT_SET
        }
        configure(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
        return inputAudioFormat
    }

    override fun onFlush() {
        state.fill(0f)
        running.fill(false)
        snapToTarget()
    }

    override fun onReset() {
        state = FloatArray(0)
        channelGain = FloatArray(0)
        running.fill(false)
        channelCount = 0
        sampleRate = 0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytesPerFrame = BYTES_PER_SAMPLE * channelCount
        if (bytesPerFrame == 0) return
        val frameCount = inputBuffer.remaining() / bytesPerFrame
        if (frameCount == 0) return
        val outputBuffer = replaceOutputBuffer(frameCount * bytesPerFrame)

        val tuning = target
        if (isFlat(tuning) && isSettled(tuning)) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        inputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer.order(ByteOrder.nativeOrder())

        val invScale = 1.0f / 32768.0f
        var remaining = frameCount
        while (remaining > 0) {
            val block = min(remaining, GLIDE_FRAMES)
            glideTowards(tuning)
            val active = prepareSections()
            prepareChannelGains()

            repeat(block) {
                for (channel in 0 until channelCount) {
                    var sample = inputBuffer.short.toFloat() * invScale
                    for (index in 0 until active) {
                        sample = section(activeSlots[index], channel, sample)
                    }
                    outputBuffer.putShort(PcmBoundary.clamp16FromFloat(sample * channelGain[channel]))
                }
            }
            flushDenormals(active)
            remaining -= block
        }
        outputBuffer.flip()
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
            currentGainDb[slot] = linearGlide(currentGainDb[slot], tuning.curve.gainsDb[slot])
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
        if (sampleRate <= 0) return
        val spec = EqLayout.slots[slot]
        val a = 10f.pow(currentGainDb[slot] / 40f)
        val q = currentQ[slot].coerceAtLeast(MIN_Q)
        val base = tan(Math.PI * usableFrequency(spec.frequencyHz) / sampleRate).toFloat()
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

    private fun usableFrequency(hz: Float): Float =
        hz.coerceIn(MIN_HZ, sampleRate * MAX_FREQUENCY_FRACTION)

    private fun prepareChannelGains() {
        val preamp = 10f.pow(currentPreampDb / 20f)
        if (channelCount == 2) {
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
        for (channel in 0 until channelCount) {
            val i = (channel * EqLayout.SLOTS + slot) * 2
            state[i] = 0f
            state[i + 1] = 0f
        }
    }

    private fun flushDenormals(active: Int) {
        for (index in 0 until active) {
            val slot = activeSlots[index]
            for (channel in 0 until channelCount) {
                val i = (channel * EqLayout.SLOTS + slot) * 2
                if (abs(state[i]) < DENORMAL_FLOOR) state[i] = 0f
                if (abs(state[i + 1]) < DENORMAL_FLOOR) state[i + 1] = 0f
            }
        }
    }

    companion object {
        private const val TAG = "BitChordEqualizer"

        private const val BYTES_PER_SAMPLE = 2

        /** Frames between coefficient updates. ~1.5 ms at 44.1 kHz. */
        private const val GLIDE_FRAMES = 64

        /** Per-sub-block glide fraction. ~18 ms time constant — a fast drag still tracks. */
        private const val GLIDE_RATE = 0.08f

        /** Below this a band is doing nothing anyone can hear, so it counts as flat. */
        private const val SETTLED_DB = 0.01f

        /** Same idea for the balance trim, where the scale is -1 to 1. */
        private const val SETTLED_BALANCE = 0.0005f

        private const val MIN_Q = 0.05f
        private const val MIN_HZ = 10f

        /** Keeps `tan` away from its pole at Nyquist. */
        private const val MAX_FREQUENCY_FRACTION = 0.45f

        /** Safe noise floor threshold to prevent floating point denormals. */
        private const val DENORMAL_FLOOR = 1e-12f
    }
}
