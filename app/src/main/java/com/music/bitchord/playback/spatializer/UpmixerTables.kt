package com.music.bitchord.playback.spatializer

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Every runtime table of the stereo -> 5.1 upmixer ([SurroundUpmixer]), generated from the parameters in the
 * companion object for one sample rate and frame size. Nothing is precomputed or shipped: the kernels, time
 * constants, steering tables and filters all follow from ~25 numbers. Float32 arithmetic is kept deliberately where
 * it appears, so the tables match the reference to float rounding on every device. [forRate] shares one set per rate.
 *
 * @param sampleRate stream rate in Hz
 * @param n STFT frame size (hop = n / 2); 2048 up to 48 kHz, scaled with the rate above that
 */
class UpmixerTables(val sampleRate: Int, val n: Int = frameSizeFor(sampleRate)) {
    val hop: Int = n / 2
    /** Spectral bins handled (the Nyquist bin bypasses the spectral stage). */
    val bins: Int = n / 2

    val window = FloatArray(n) { i -> sqrt(0.5 - 0.5 * cos(2.0 * PI * i / n)).toFloat() }

    /** Per-bin frequency-smoothing kernels, CSR: row k covers columns [kernelStart[k], kernelStart[k] + kernelLen[k]). */
    val kernelStart = IntArray(bins)
    val kernelOffset = IntArray(bins + 1)
    val kernelValues: FloatArray
    private val peak = FloatArray(bins)

    /** Covariance smoothing coefficient per bin, and the steering-gain one. */
    val alpha = FloatArray(bins)
    val gainAlpha = FloatArray(bins)

    /** Steering lookup tables, 91 (phase) x 81 (pan), index = phase * 81 + pan. */
    val lutSide = FloatArray(LUT_SIZE)
    val lutCentre = FloatArray(LUT_SIZE)

    /** Linkwitz-Riley 2nd-order crossover at [CROSSOVER_HZ]: two first-order sections each, [b0 b1 b2 a1 a2]. */
    val crossoverLow: Array<DoubleArray>
    val crossoverHigh: Array<DoubleArray>
    val surroundEq: Array<DoubleArray>
    val combDelays: IntArray

    init {
        // ---- frequency-smoothing kernels: raised cosine on a log-frequency axis, SMOOTH_OCT wide -----------
        val f = bins
        val oct = SMOOTH_OCT.coerceIn(0.0, 1.0)
        var u = (f.toFloat() * (16000.0 / sampleRate).toFloat() + 0.5f).toInt()
        if (u < 3) u = 2
        val w = FloatArray(f) { 1f }
        for (k in 1 until f) {
            val kf = k.toFloat()
            w[k] = ln((kf + 0.5f).toDouble()).toFloat() - ln((kf + -0.5f).toDouble()).toFloat()
        }
        val loF = 2.0.pow(oct * -0.5).toFloat()
        val hiF = 2.0.pow(oct * 0.5).toFloat()
        val uc = if ((u + 1).toFloat() / u.toFloat() <= hiF) u else 0
        var values = FloatArray(f * 64)
        var count = 0
        fun add(v: Float) {
            if (count == values.size) values = values.copyOf(2 * values.size)
            values[count++] = v
        }
        val tmp = FloatArray(f)
        var prevStart = 0
        var prevKernel = floatArrayOf(1f)
        kernelStart[0] = 0; kernelOffset[0] = 0; add(1f); peak[0] = 1f
        for (kk in 1 until f) {
            val kf = kk.toFloat()
            val up = if (kk < uc) hiF * kf else (hiF * uc.toFloat() - uc.toFloat()) + kf
            if (kk <= uc || f.toFloat() <= up) {
                val lo = if (kk < uc) loF * kf else (loF * uc.toFloat() - uc.toFloat()) + kf
                val llo = ln(lo.toDouble()).toFloat()
                val j0 = ceil(lo.toDouble()).toInt()
                val j1 = min(floor(up.toDouble()).toInt(), f - 1)
                java.util.Arrays.fill(tmp, 0f)
                var mx = 0f
                if (j0 <= j1) {
                    val lup = ln(up.toDouble()).toFloat()
                    val den = (lup - llo) + 2.220446e-16f
                    for (j in j0..j1) {
                        val lj = ln(j.toFloat().toDouble()).toFloat()
                        val v = cos((((lj - llo) / den) * 6.2831854820251465f).toDouble()).toFloat()
                        tmp[j] = (v * -0.5f + 0.5f) * w[j]
                    }
                    mx = tmp[j0]
                    for (j in j0..j1) if (tmp[j] > mx) mx = tmp[j]
                }
                var a = j0
                while (!(tmp[a] >= mx * 0.15f)) a++
                var b = j1
                while (!(tmp[b] >= mx * 0.15f)) b--
                var s = 0f
                for (j in a..b) s += tmp[j]
                val inv = 1.0f / s
                prevKernel = FloatArray(b - a + 1) { tmp[a + it] * inv }
                prevStart = a
                peak[kk] = mx * inv
            } else {
                prevStart += 1
                peak[kk] = peak[kk - 1]
            }
            kernelStart[kk] = prevStart
            kernelOffset[kk] = count
            for (v in prevKernel) add(v)
        }
        kernelOffset[f] = count
        kernelValues = values.copyOf(count)

        // ---- covariance / steering-gain time constants -------------------------------------------------------
        val aCov = exp(-1.0 / ((COV_TAU * sampleRate) / hop)).toFloat()
        val aMin = exp(-1.0 / ((COV_TAU_MIN * sampleRate) / hop)).toFloat()
        for (k in 0 until f) {
            alpha[k] = max(aCov.toDouble().pow((1.0f / peak[k]).toDouble()).toFloat(), aMin)
        }
        val lo = min(max((LF_HZ / sampleRate).toFloat() * n.toFloat(), 0f), (f - 1).toFloat())
        val hi = min((HF_HZ / sampleRate).toFloat() * n.toFloat(), f.toFloat())
        val nLo = max(lo.toInt() + 1, 1)
        val gLo = exp(-1.0 / ((sampleRate * LF_TAU) / hop)).toFloat()
        for (k in 0 until min(nLo, f)) gainAlpha[k] = gLo
        val nHi = ceil(hi.toDouble()).toInt()
        for (k in lo.toInt() + 1 until min(nHi, f)) {
            val t = ((k.toFloat() - lo) / (hi - lo)).toDouble()
            gainAlpha[k] = exp(-1.0 / ((sampleRate * (t * HF_TAU + LF_TAU * (1.0 - t))) / hop)).toFloat()
        }
        val gHi = exp(-1.0 / ((HF_TAU * sampleRate) / hop)).toFloat()
        for (k in nHi until f) gainAlpha[k] = gHi

        // ---- steering lookup tables: (inter-channel phase, pan) -> centre gain, side gain --------------------
        val mn = min(10.0.pow(MIN_GAIN_DB * 0.05), 0.99498742818832397).toFloat()
        val hiG = 0.99498742818832397f
        val steerLog = FloatArray(LUT_SIZE)
        for (pan in 0 until 81) {
            val g = 10.0.pow(((((pan.toFloat() + pan.toFloat()) / 80.0f) + -1.0f) * 40.0f / 20.0f).toDouble()).toFloat()
            val ang = asin(((g + -1f) / (g + 1f)).toDouble()).toFloat()
            val ap = abs(ang).toDouble().pow(EXPONENT).toFloat()
            for (ph in 0 until 91) {
                val ramp = ph.toFloat() * 2.0f
                steerLog[ph * 81 + pan] = ramp * (-1.0 / (PHASE_WIDTH + PHASE_WIDTH)).toFloat() + (-1.0 / (WIDTH + WIDTH)).toFloat() * ap
            }
        }
        for (i in 0 until LUT_SIZE) {
            val e = exp(steerLog[i].toDouble()).toFloat()
            lutCentre[i] = e * ((hiG - mn) * CENTRE_LEVEL) + mn * CENTRE_LEVEL
            var v = -(lutCentre[i].toDouble().pow(ENERGY_NORM.toDouble()).toFloat()) + 1f
            v = max(v, 0f).toDouble().pow((1.0 / 1.7).toFloat().toDouble()).toFloat()
            v = max(v, 0.01f)
            lutSide[i] = v * v
        }

        // ---- filters -------------------------------------------------------------------------------------------
        val kx = tan(PI * CROSSOVER_HZ / sampleRate)
        val lp = doubleArrayOf(kx / (1 + kx), kx / (1 + kx), 0.0, (kx - 1) / (kx + 1), 0.0)
        val hp = doubleArrayOf(1 / (1 + kx), -1 / (1 + kx), 0.0, (kx - 1) / (kx + 1), 0.0)
        val hp2 = doubleArrayOf(-hp[0], -hp[1], -hp[2], hp[3], hp[4])   // LR2: second high-pass numerator inverted
        crossoverLow = arrayOf(lp, lp.copyOf())
        crossoverHigh = arrayOf(hp, hp2)
        surroundEq = Array(SURROUND_EQ.size) { i ->
            val (fc, gainDb, q) = SURROUND_EQ[i]
            rbjPeak(fc, gainDb, q, sampleRate.toDouble())
        }
        // all-pass comb delays are in samples at 44.1/48 kHz; at higher rates keep their duration, scaling from the
        // rate's own family (88.2 kHz from 44.1, 96 kHz from 48)
        val family = if (sampleRate % 11025 == 0) 44100 else 48000
        val scale = if (sampleRate > family) sampleRate.toDouble() / family else 1.0
        combDelays = IntArray(COMB_DELAYS.size) { max(1, Math.round(COMB_DELAYS[it] * scale).toInt()) }
    }

    companion object {
        const val LUT_SIZE = 91 * 81

        // Headphone tuning. docs/stereo-spatialization.md explains where these values come from.
        const val CENTRE_GAIN = 1.0f
        const val LFE_GAIN_DB = 6.0
        const val AMBIENCE = 0.5f
        const val CROSSTALK = 0.9f
        const val LEAK = 0.2f
        const val CENTRE_LEVEL = 1.0f
        const val WIDTH = 0.135
        const val EXPONENT = 1.5
        const val PHASE_WIDTH = 90.0
        const val ENERGY_NORM = 1.7f
        const val MIN_GAIN_DB = -30.0
        const val SMOOTH_OCT = 0.27
        const val COV_TAU = 0.13
        const val COV_TAU_MIN = 0.03
        const val LF_HZ = 500.0
        const val HF_HZ = 5000.0
        const val LF_TAU = 0.05
        const val HF_TAU = 0.04
        const val CROSSOVER_HZ = 80.0
        const val SURROUND_GAIN_DB = 3.0
        const val COMB_G = 0.3
        val COMB_DELAYS = intArrayOf(105, 116, 17)
        val SURROUND_EQ = arrayOf(
            Triple(3837.0, -7.535, 1.15727),
            Triple(5529.0, 6.021, 0.86265),
            Triple(6374.0, -7.959, 2.87771),
        )

        private val shared = HashMap<Int, UpmixerTables>()

        /** The tables for [sampleRate] at the default frame size, built once and shared: they are read-only. */
        @Synchronized
        fun forRate(sampleRate: Int): UpmixerTables = shared.getOrPut(sampleRate) { UpmixerTables(sampleRate) }

        /** STFT frame: 2048 up to 48 kHz, doubled per octave of rate above that (same time/frequency resolution). */
        fun frameSizeFor(sampleRate: Int): Int {
            var n = 2048
            while (sampleRate > 48000 * (n / 2048)) n *= 2
            return n
        }

        fun rbjPeak(fc: Double, gainDb: Double, q: Double, sr: Double): DoubleArray {
            val a = 10.0.pow(gainDb / 40)
            val w0 = 2 * PI * fc / sr
            val al = sin(w0) / (2 * q)
            val cw = cos(w0)
            val a0 = 1 + al / a
            return doubleArrayOf((1 + al * a) / a0, -2 * cw / a0, (1 - al * a) / a0, -2 * cw / a0, (1 - al / a) / a0)
        }
    }
}
