package com.music.bitchord.playback.spatializer

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Streaming stereo -> 5.1 upmixer (L R C LFE Ls Rs), one hop at a time.
 *
 * sqrt-Hann STFT; per bin, the left/right covariance is smoothed across frequency and time, and the ambience is its
 * smaller eigenvalue. Steering gains from lookup tables ([UpmixerTables]) split the direct sound between the front
 * channels and a phantom centre; the ambience goes to the surrounds through all-pass decorrelators and an EQ; the band
 * below 80 Hz (Linkwitz-Riley) plus the centre feeds the LFE channel. Float32 throughout, with a fixed order of
 * operations so the output is reproducible to the last bit (see `StereoSpatializerTest`).
 *
 * Latency: [latencyFrames] = two hops (one hop of input buffering, done by the caller, plus the STFT overlap).
 */
class SurroundUpmixer(private val t: UpmixerTables) {
    val hop: Int = t.hop
    private val n = t.n
    private val f = t.bins
    val latencyFrames: Int get() = 2 * hop

    private val fft = RealFft(n)
    private val frame = FloatArray(n)
    private val inL = FloatArray(n)
    private val inR = FloatArray(n)
    private val ola = Array(6) { FloatArray(n) }

    // spectra (bins 0..f-1; the Nyquist bin travels separately)
    private val lre = FloatArray(f + 1); private val lim = FloatArray(f + 1)
    private val rre = FloatArray(f + 1); private val rim = FloatArray(f + 1)
    private val sre = Array(6) { FloatArray(f + 1) }
    private val sim = Array(6) { FloatArray(f + 1) }
    private var nyqL = 0f
    private var nyqR = 0f

    // smoothed state
    private val pll = FloatArray(f); private val prr = FloatArray(f)
    private val plrRe = FloatArray(f); private val plrIm = FloatArray(f)
    private val gL = FloatArray(f); private val gR = FloatArray(f)
    private val gCl = FloatArray(f); private val gCr = FloatArray(f)
    private val oma = FloatArray(f) { 1f - t.alpha[it] }
    private val goma = FloatArray(f) { 1f - t.gainAlpha[it] }

    // per-hop scratch
    private val s1 = FloatArray(f); private val s2 = FloatArray(f); private val s3 = FloatArray(f); private val s4 = FloatArray(f)
    private val fl = FloatArray(f); private val fr = FloatArray(f)
    private val pan = FloatArray(f); private val pan1 = FloatArray(f); private val ph = FloatArray(f)
    private val gl = FloatArray(f); private val gr = FloatArray(f)
    // ambience cross-terms: right spectrum into the left surround, left spectrum into the right surround
    private val rToLsRe = FloatArray(f); private val rToLsIm = FloatArray(f)
    private val lToRsRe = FloatArray(f); private val lToRsIm = FloatArray(f)
    private val gainL = FloatArray(f); private val gainR = FloatArray(f); private val gainC = FloatArray(f)
    private val smoothed = FloatArray(f)

    // time-domain filters
    private val hpL = BiquadCascade(t.crossoverHigh); private val hpR = BiquadCascade(t.crossoverHigh)
    private val lpL = BiquadCascade(t.crossoverLow); private val lpR = BiquadCascade(t.crossoverLow)
    private val eq = arrayOf(BiquadCascade(t.surroundEq), BiquadCascade(t.surroundEq))
    private val combs = Array(2) { Array(t.combDelays.size) { i -> AllPassComb(UpmixerTables.COMB_G, t.combDelays[i]) } }
    private val midL = FloatArray(hop); private val midR = FloatArray(hop)
    private val lowL = FloatArray(hop); private val lowR = FloatArray(hop)
    private val lowBufL = FloatArray(hop); private val lowBufR = FloatArray(hop)
    private val zeros = FloatArray(hop)
    private val scratchOut = Array(6) { FloatArray(hop) }

    private val lfeScale = (10.0.pow(UpmixerTables.LFE_GAIN_DB / 20).toFloat()) * 0.3162277638912201f
    private val surroundGain = 10.0.pow(UpmixerTables.SURROUND_GAIN_DB / 20).toFloat()
    private val a2 = UpmixerTables.AMBIENCE * UpmixerTables.AMBIENCE
    private val b2 = 1f - a2

    init { reset() }

    /**
     * Clears all state, then runs one hop of silence: that moves the steering-gain smoothers off zero to their idle
     * values, so the first few hundred milliseconds of a track are steered like the rest.
     */
    fun reset() {
        inL.fill(0f); inR.fill(0f); ola.forEach { it.fill(0f) }
        for (a in arrayOf(pll, prr, plrRe, plrIm, gL, gR, gCl, gCr)) a.fill(0f)
        hpL.reset(); hpR.reset(); lpL.reset(); lpR.reset(); eq.forEach { it.reset() }
        combs.forEach { c -> c.forEach { it.reset() } }
        lowBufL.fill(0f); lowBufR.fill(0f)
        processHop(zeros, zeros, scratchOut)
    }

    /** One hop: [left]/[right] (length [hop]) -> [out] 6 x [hop] (L R C LFE Ls Rs). */
    fun processHop(left: FloatArray, right: FloatArray, out: Array<FloatArray>) {
        hpL.process(left, midL, hop); hpR.process(right, midR, hop)
        lpL.process(left, lowL, hop); lpR.process(right, lowR, hop)
        analyse(inL, midL, lre, lim, isLeft = true)
        analyse(inR, midR, rre, rim, isLeft = false)
        spectral()
        // slots 0..3 -> L R C LFE, back slots 6/7 -> Ls Rs
        for (o in 0 until 6) synthesise(o, out[o])
        // LFE: centre copy + the band below the crossover, one hop late, x lfe gain x -10 dB
        val lfe = out[3]
        for (i in 0 until hop) lfe[i] = (lfe[i] + (lowBufL[i] + lowBufR[i])) * lfeScale
        System.arraycopy(lowL, 0, lowBufL, 0, hop); System.arraycopy(lowR, 0, lowBufR, 0, hop)
        // surrounds: three all-pass combs, the surround EQ, +3 dB
        for (k in 0 until 2) {
            val v = out[4 + k]
            for (c in combs[k]) c.process(v, hop)
            eq[k].process(v, v, hop)
            for (i in 0 until hop) v[i] *= surroundGain
        }
    }

    private fun analyse(buf: FloatArray, mid: FloatArray, re: FloatArray, im: FloatArray, isLeft: Boolean) {
        System.arraycopy(buf, hop, buf, 0, n - hop)
        System.arraycopy(mid, 0, buf, n - hop, hop)
        for (i in 0 until n) frame[i] = buf[i] * t.window[i]
        fft.forward(frame, re, im)
        val sc = 1f / (2 * n)
        for (k in 0..f) { re[k] *= sc; im[k] *= sc }
        if (isLeft) nyqL = re[f] else nyqR = re[f]
        im[0] = 0f
    }

    private fun synthesise(o: Int, out: FloatArray) {
        val yr = sre[o]; val yi = sim[o]
        yr[f] = when (o) { 0 -> nyqL; 1 -> nyqR; else -> 0f }
        yi[f] = 0f
        fft.inverse(yr, yi, frame)
        val acc = ola[o]
        for (i in 0 until n) frame[i] = (2f * frame[i]) * t.window[i]
        for (i in 0 until n - hop) frame[i] += acc[hop + i]
        System.arraycopy(frame, 0, acc, 0, n)
        System.arraycopy(frame, 0, out, 0, hop)
    }

    /** Frequency smoothing: out[k] = sum_j kernel_k[j] x[start_k + j] (double accumulation). */
    private fun smooth(x: FloatArray, out: FloatArray) {
        val vals = t.kernelValues
        for (k in 0 until f) {
            var s = 0.0
            val st = t.kernelStart[k]
            var j = t.kernelOffset[k]
            val e = t.kernelOffset[k + 1]
            var c = st
            while (j < e) { s += vals[j].toDouble() * x[c].toDouble(); j++; c++ }
            out[k] = s.toFloat()
        }
    }

    private fun lut(p: Float, phase: Float, table: FloatArray): Float {
        var idx = Math.rint((phase * 90f).toDouble()).toFloat()
        idx = idx * 81f + 0.5f
        idx = p * 80f + idx
        idx = min(max(idx, 0f), 7370f)
        return table[idx.toInt()]
    }

    private fun spectral() {
        // covariances, smoothed across frequency then time
        for (k in 0 until f) {
            s1[k] = lre[k] * lre[k] + lim[k] * lim[k]
            s2[k] = rre[k] * rre[k] + rim[k] * rim[k]
            s3[k] = lre[k] * rre[k] + lim[k] * rim[k]          // conj(L) R
            s4[k] = lre[k] * rim[k] - lim[k] * rre[k]
        }
        smooth(s1, smoothed); for (k in 0 until f) pll[k] = oma[k] * smoothed[k] + t.alpha[k] * pll[k]
        smooth(s2, smoothed); for (k in 0 until f) prr[k] = oma[k] * smoothed[k] + t.alpha[k] * prr[k]
        smooth(s3, smoothed); for (k in 0 until f) plrRe[k] = oma[k] * smoothed[k] + t.alpha[k] * plrRe[k]
        smooth(s4, smoothed); for (k in 0 until f) plrIm[k] = oma[k] * smoothed[k] + t.alpha[k] * plrIm[k]

        val x = UpmixerTables.CROSSTALK
        val wCoef = x * x + x * -2f
        val lk = UpmixerTables.LEAK
        for (k in 0 until f) {
            val ll = pll[k]; val rr = prr[k]; val re = plrRe[k]; val im = plrIm[k]
            val phase = atan2(im.toDouble(), re.toDouble()).toFloat()
            val mag2 = re * re + im * im
            var d = ll - rr; d *= d
            val root = sqrt(mag2 * 4f + d)
            val amb = max(((ll + rr) - root) * 0.5f, 0f)
            val den = sqrt(ll * rr + EPS)
            var cre = re / den; var cim = im / den
            val c2 = cre * cre + cim * cim
            val w = c2 * wCoef + 1f
            val glk = sqrt(max(amb / (ll * w + EPS), 0f))
            val grk = sqrt(max(amb / (rr * w + EPS), 0f))
            gl[k] = glk; gr[k] = grk
            cim *= x; cre *= -x
            rToLsRe[k] = grk * cre; rToLsIm[k] = grk * cim
            cim = -cim
            lToRsRe[k] = glk * cre; lToRsIm[k] = glk * cim
            val dl = max(ll - amb, 0f); val dr = max(rr - amb, 0f)
            val powL = (dl * (1f - lk) + EPS) + ll * lk
            val powR = (dr * (1f - lk) + EPS) + rr * lk
            val ratio = powR / powL
            pll[k] = ll + EPS; prr[k] = rr + EPS
            fl[k] = sqrt(dl / pll[k]); fr[k] = sqrt(dr / prr[k])
            var p = 10f * log10(ratio.toDouble()).toFloat()
            p = min(max(p * 0.0125f + 0.5f, 0f), 1f)
            pan[k] = p
            pan1[k] = min(max(p * -1f + 1f, 0f), 1f)
            ph[k] = min(max(abs(phase) * 0.31830987334251404f, 0f), 1f)
        }
        for (k in 0 until f) {
            var g = lut(pan1[k], ph[k], t.lutSide)
            var tt = fl[k] * fl[k]; g *= tt; tt = tt * (a2 - 1f) + b2; g += tt
            gainL[k] = sqrt(g)
            g = lut(pan[k], ph[k], t.lutSide)
            tt = fr[k] * fr[k]; g *= tt; tt = tt * (a2 - 1f) + b2; g += tt
            gainR[k] = sqrt(g)
            gainC[k] = lut(pan[k], ph[k], t.lutCentre)
        }
        smooth(gainL, smoothed); for (k in 0 until f) gL[k] = goma[k] * smoothed[k] + t.gainAlpha[k] * gL[k]
        smooth(gainR, smoothed); for (k in 0 until f) gR[k] = goma[k] * smoothed[k] + t.gainAlpha[k] * gR[k]
        smooth(gainC, smoothed)
        val s = UpmixerTables.AMBIENCE
        val cg = UpmixerTables.CENTRE_GAIN
        for (k in 0 until f) {
            val gc = smoothed[k] * cg
            gCl[k] = goma[k] * (gc * (fl[k] * (1f - lk) + lk)) + t.gainAlpha[k] * gCl[k]
            gCr[k] = goma[k] * (gc * (fr[k] * (1f - lk) + lk)) + t.gainAlpha[k] * gCr[k]
            val lr = lre[k]; val li = lim[k]; val rrk = rre[k]; val ri = rim[k]
            sre[0][k] = lr * gL[k]; sim[0][k] = li * gL[k]
            sre[1][k] = rrk * gR[k]; sim[1][k] = ri * gR[k]
            val cr = (lr * gCl[k] + rrk * gCr[k]) * 0.7071067690849304f
            val ci = (li * gCl[k] + ri * gCr[k]) * 0.7071067690849304f
            sre[2][k] = cr; sim[2][k] = ci
            sre[3][k] = cr; sim[3][k] = ci
            var zr = rToLsRe[k] * rrk - rToLsIm[k] * ri
            var zi = rToLsRe[k] * ri + rToLsIm[k] * rrk
            sre[4][k] = (zr + gl[k] * lr) * s; sim[4][k] = (zi + gl[k] * li) * s
            zr = lToRsRe[k] * lr - lToRsIm[k] * li
            zi = lToRsRe[k] * li + lToRsIm[k] * lr
            sre[5][k] = (zr + gr[k] * rrk) * s; sim[5][k] = (zi + gr[k] * ri) * s
        }
    }

    private companion object {
        const val EPS = 2.220446e-16f
    }
}

/** Cascade of biquad sections [b0 b1 b2 a1 a2]; each section runs in double, Float32 between sections. */
internal class BiquadCascade(private val coefs: Array<DoubleArray>) {
    private val z1 = DoubleArray(coefs.size)
    private val z2 = DoubleArray(coefs.size)

    fun reset() { z1.fill(0.0); z2.fill(0.0) }

    fun process(input: FloatArray, output: FloatArray, frames: Int) {
        if (input !== output) System.arraycopy(input, 0, output, 0, frames)
        for (s in coefs.indices) {
            val c = coefs[s]
            val b0 = c[0]; val b1 = c[1]; val b2 = c[2]; val a1 = c[3]; val a2 = c[4]
            var s1 = z1[s]; var s2 = z2[s]
            for (i in 0 until frames) {
                val x = output[i].toDouble()
                val y = b0 * x + s1
                s1 = b1 * x - a1 * y + s2
                s2 = b2 * x - a2 * y
                output[i] = y.toFloat()
            }
            z1[s] = s1; z2[s] = s2
        }
    }
}

/**
 * Schroeder all-pass comb for the surround decorrelators: `y[n] = g x[n] + x[n-D] - g y[n-D]` (g = +0.3), double
 * state, Float32 output.
 */
internal class AllPassComb(private val g: Double, private val delay: Int) {
    private val xs = DoubleArray(delay)
    private val ys = DoubleArray(delay)
    private var pos = 0

    fun reset() { xs.fill(0.0); ys.fill(0.0); pos = 0 }

    fun process(buf: FloatArray, frames: Int) {
        for (i in 0 until frames) {
            val x = buf[i].toDouble()
            val y = g * x + xs[pos] - g * ys[pos]
            xs[pos] = x; ys[pos] = y
            pos = if (pos + 1 == delay) 0 else pos + 1
            buf[i] = y.toFloat()
        }
    }
}
