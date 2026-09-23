package com.music.bitchord.playback.spatializer

/**
 * Uniformly partitioned overlap-save convolution of [INPUTS] input channels into two ears, one [block] at a time.
 *
 * Every (input, ear) impulse response is cut into partitions of [block] samples whose spectra (FFT size
 * 2 x [block]) are precomputed once per sample rate and shared ([SpeakerResponses.Spectra]); each call transforms
 * the new input block of every channel, pushes it into a frequency-domain delay line and sums partition x delayed
 * spectrum products. No latency beyond the block itself: output block k is the convolution result for input block k.
 */
class PartitionedConvolver(private val spectra: SpeakerResponses.Spectra) {
    val block: Int = spectra.block
    private val bins = block + 1
    private val parts = spectra.partitions
    private val fft = RealFft(2 * block)

    private val history = Array(INPUTS) { FloatArray(2 * block) }
    private val fdlRe = Array(INPUTS) { Array(parts) { FloatArray(bins) } }
    private val fdlIm = Array(INPUTS) { Array(parts) { FloatArray(bins) } }
    private var head = 0
    private val silent = BooleanArray(INPUTS)
    private val silentAge = IntArray(INPUTS)
    private val accRe = FloatArray(bins)
    private val accIm = FloatArray(bins)
    private val time = FloatArray(2 * block)

    fun reset() {
        history.forEach { it.fill(0f) }
        for (c in 0 until INPUTS) for (p in 0 until parts) { fdlRe[c][p].fill(0f); fdlIm[c][p].fill(0f) }
        head = 0
        silentAge.fill(parts + 1)
    }

    init { reset() }

    /** [inputs]: 6 x [block] (L R C LFE Ls Rs) -> [outLeft], [outRight] ([block] each). */
    fun process(inputs: Array<FloatArray>, outLeft: FloatArray, outRight: FloatArray) {
        head = if (head == 0) parts - 1 else head - 1
        for (c in 0 until INPUTS) {
            val h = history[c]
            System.arraycopy(h, block, h, 0, block)
            System.arraycopy(inputs[c], 0, h, block, block)
            var any = false
            for (i in block until 2 * block) if (h[i] != 0f) { any = true; break }
            // consecutive silent blocks: from the 2nd one the overlap-save frame is all zeros (no FFT needed), and
            // once every delay-line slot holds such a frame the channel contributes nothing (no products either)
            silentAge[c] = if (any) 0 else minOf(silentAge[c] + 1, parts + 1)
            silent[c] = silentAge[c] >= parts + 1
            if (silentAge[c] >= 2) {
                fdlRe[c][head].fill(0f); fdlIm[c][head].fill(0f)
            } else {
                fft.forward(h, fdlRe[c][head], fdlIm[c][head])
            }
        }
        for (ear in 0 until 2) {
            accRe.fill(0f); accIm.fill(0f)
            for (c in 0 until INPUTS) {
                if (silent[c]) continue
                val active = spectra.activePartitions[c][ear]
                for (p in 0 until active) {
                    val slot = (head + p) % parts
                    val xr = fdlRe[c][slot]; val xi = fdlIm[c][slot]
                    val hr = spectra.re[c][ear][p]; val hi = spectra.im[c][ear][p]
                    for (k in 0 until bins) {
                        val a = xr[k]; val b = xi[k]; val cr = hr[k]; val ci = hi[k]
                        accRe[k] += a * cr - b * ci
                        accIm[k] += a * ci + b * cr
                    }
                }
            }
            fft.inverse(accRe, accIm, time)
            val out = if (ear == 0) outLeft else outRight
            val scale = 1f / (2 * block)
            for (i in 0 until block) out[i] = time[block + i] * scale
        }
    }

    companion object {
        const val INPUTS = 6
    }
}
