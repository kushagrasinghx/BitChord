package com.music.bitchord.playback.spatializer

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Real-input FFT of a power-of-two length [n], Float32, allocation-free after construction.
 *
 * Unnormalised in both directions, like the textbook definitions:
 *
 *  - [forward]: `X[k] = sum_j x[j] e^(-2 pi i j k / n)`, bins `0..n/2`
 *  - [inverse]: `x[j] = sum_k X[k] e^(+2 pi i j k / n)` over the full Hermitian spectrum, i.e. `n` times the true
 *    inverse; callers apply their own scale.
 *
 * Implemented as an `n/2`-point complex FFT (iterative radix-2) on the even/odd packing, plus the usual
 * split / merge step. Twiddles are computed in double precision once.
 */
class RealFft(val n: Int) {
    init {
        require(n >= 4 && n and (n - 1) == 0) { "FFT length must be a power of two >= 4: $n" }
    }

    /** Number of spectral bins, `n/2 + 1`. */
    val bins: Int = n / 2 + 1

    private val m = n / 2
    private val bitrev = IntArray(m)
    private val cosM = FloatArray(m / 2)
    private val sinM = FloatArray(m / 2)
    private val cosN = FloatArray(m + 1)
    private val sinN = FloatArray(m + 1)
    private val zr = FloatArray(m)
    private val zi = FloatArray(m)

    init {
        var bits = 0
        while (1 shl bits < m) bits++
        for (i in 0 until m) {
            var r = 0
            var v = i
            repeat(bits) { r = (r shl 1) or (v and 1); v = v shr 1 }
            bitrev[i] = r
        }
        for (k in 0 until m / 2) {
            val a = -2.0 * PI * k / m
            cosM[k] = cos(a).toFloat(); sinM[k] = sin(a).toFloat()
        }
        for (k in 0..m) {
            val a = -2.0 * PI * k / n
            cosN[k] = cos(a).toFloat(); sinN[k] = sin(a).toFloat()
        }
    }

    /** In-place complex FFT of [zr]/[zi] (length m); [inverse] conjugates the twiddles (unnormalised). */
    private fun complexFft(inverse: Boolean) {
        for (i in 0 until m) {
            val j = bitrev[i]
            if (j > i) {
                val tr = zr[i]; zr[i] = zr[j]; zr[j] = tr
                val ti = zi[i]; zi[i] = zi[j]; zi[j] = ti
            }
        }
        val sgn = if (inverse) -1f else 1f
        var len = 2
        while (len <= m) {
            val half = len / 2
            val step = m / len
            var start = 0
            while (start < m) {
                var t = 0
                for (k in 0 until half) {
                    val wr = cosM[t]; val wi = sgn * sinM[t]
                    val a = start + k; val b = a + half
                    val xr = zr[b] * wr - zi[b] * wi
                    val xi = zr[b] * wi + zi[b] * wr
                    zr[b] = zr[a] - xr; zi[b] = zi[a] - xi
                    zr[a] += xr; zi[a] += xi
                    t += step
                }
                start += len
            }
            len = len shl 1
        }
    }

    /** [x] (length n, read from [offset]) -> [re]/[im] (length >= bins). */
    fun forward(x: FloatArray, re: FloatArray, im: FloatArray, offset: Int = 0) {
        for (j in 0 until m) { zr[j] = x[offset + 2 * j]; zi[j] = x[offset + 2 * j + 1] }
        complexFft(inverse = false)
        // X[k] = E[k] + W^k O[k];  E = (Z[k] + Z*[m-k]) / 2,  O = (Z[k] - Z*[m-k]) / 2i
        for (k in 0..m) {
            val k1 = if (k == m) 0 else k
            val k2 = if (k == 0) 0 else m - k
            val ar = zr[k1]; val ai = zi[k1]
            val br = zr[k2]; val bi = -zi[k2]
            val er = 0.5f * (ar + br); val ei = 0.5f * (ai + bi)
            // (a - b) / 2i = (ai - bi)/2 - i (ar - br)/2
            val odr = 0.5f * (ai - bi); val odi = -0.5f * (ar - br)
            val wr = cosN[k]; val wi = sinN[k]
            re[k] = er + (odr * wr - odi * wi)
            im[k] = ei + (odr * wi + odi * wr)
        }
    }

    /** Hermitian spectrum [re]/[im] (bins entries; im[0], im[m] ignored) -> [out] (length n), unnormalised. */
    fun inverse(re: FloatArray, im: FloatArray, out: FloatArray, offset: Int = 0) {
        // E[k] = (X[k] + X*[m-k]) / 2,  O[k] = (X[k] - X*[m-k]) / (2 W^k),  Z = E + i O
        for (k in 0 until m) {
            val ar = re[k]; val ai = if (k == 0) 0f else im[k]
            val br = re[m - k]; val bi = -(if (m - k == m) 0f else im[m - k])
            val er = 0.5f * (ar + br); val ei = 0.5f * (ai + bi)
            val dr = 0.5f * (ar - br); val di = 0.5f * (ai - bi)
            // divide by W^k = multiply by conj(W^k)
            val wr = cosN[k]; val wi = -sinN[k]
            val odr = dr * wr - di * wi
            val odi = dr * wi + di * wr
            zr[k] = er - odi
            zi[k] = ei + odr
        }
        complexFft(inverse = true)
        for (j in 0 until m) {
            out[offset + 2 * j] = 2f * zr[j]
            out[offset + 2 * j + 1] = 2f * zi[j]
        }
    }
}
