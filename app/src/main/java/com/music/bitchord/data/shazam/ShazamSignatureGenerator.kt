package com.music.bitchord.data.shazam

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin

/**
 * Turns 16 kHz mono PCM into the peak list a Shazam signature is made of.
 *
 * This is a Kotlin port of the fingerprinter in
 * [SongRec](https://github.com/marin-m/SongRec) (GPL-3.0), which is also the
 * algorithm [shazamio-core](https://github.com/shazamio/shazamio-core) uses.
 * The constants and the order of operations are deliberately kept identical:
 * the fingerprints only match if both sides compute the same peaks, down to
 * the rounding. In particular, [ShazamHanningWindow] must not be regenerated
 * from a cosine.
 *
 * The output is the signature calculation only. Encoding it into Shazam's
 * binary format and uploading it are separate steps.
 */
object ShazamSignatureGenerator {

    /** The only sample rate this fingerprinter accepts, so the caller must resample. */
    const val SAMPLE_RATE_HZ = 16_000

    private const val FFT_SIZE = 2048
    private const val FFT_SIZE_BITS = 11
    private const val HOP_SIZE = 128
    private const val BIN_COUNT = FFT_SIZE / 2 + 1
    private const val HISTORY_SIZE = 256
    private const val HISTORY_MASK = HISTORY_SIZE - 1
    private const val PEAK_START_PASS = 46

    /**
     * The reference reads the raw spectrum 46 passes back and the spread
     * spectrum 49 passes back. The three-pass difference is part of the
     * algorithm, not a typo: the time-neighbour offsets are relative to the
     * spread ring's post-increment position, so keep both lookbacks exact.
     */
    private const val SPREAD_LOOKBACK = 49

    /** Powers below this cannot be a peak; `1/64` is the reference's cutoff. */
    private const val PEAK_THRESHOLD = 1f / 64f

    /** Keeps silence from turning into `ln(0)` when the power is accumulated. */
    private const val POWER_FLOOR = 1e-10f

    private const val POWER_DIVISOR = 131_072f // 1 << 17
    private const val MAGNITUDE_SCALE = 1477.3f
    private const val MAGNITUDE_OFFSET = 6144f
    private const val MAGNITUDE_LOG_FLOOR = 1f / 64f

    private const val HZ_PER_CORRECTED_BIN = SAMPLE_RATE_HZ / 2f / 1024f / 64f

    /**
     * Bins that must not be louder than the candidate. They are asymmetric
     * because a peak's main lobe leans towards higher bins after the Hanning
     * window; copying them exactly keeps the port bit-compatible with SongRec.
     */
    private val FREQUENCY_NEIGHBOURS = intArrayOf(-10, -7, -4, -3, 1, 2, 5, 8)

    /**
     * Past spread frames compared against the candidate. The large positive
     * offsets wrap around the 256-frame ring buffer and land before the
     * candidate, which is why they are not simply negative.
     */
    private val TIME_NEIGHBOURS = intArrayOf(
        -53, -45, 165, 172, 179, 186, 193, 200, 214, 221, 228, 235, 242, 249,
    )

    /** Frames whose spread maximum is folded back into this one. */
    private val SPREAD_TIME_OFFSETS = intArrayOf(1, 3, 6)

    /**
     * Forward-transform twiddles. A 2048-point transform only ever needs 1024
     * of them, and precomputing them once is cheaper than recurring through
     * `cos`/`sin` for every one of the ~1500 transforms in a 12-second clip.
     */
    private val COS_TABLE = FloatArray(FFT_SIZE / 2) {
        cos(2.0 * PI * it / FFT_SIZE).toFloat()
    }
    private val SIN_TABLE = FloatArray(FFT_SIZE / 2) {
        -sin(2.0 * PI * it / FFT_SIZE).toFloat()
    }

    private val BIT_REVERSAL = IntArray(FFT_SIZE) { index ->
        var value = index
        var reversed = 0
        repeat(FFT_SIZE_BITS) {
            reversed = (reversed shl 1) or (value and 1)
            value = value ushr 1
        }
        reversed
    }

    /**
     * Fingerprints [samples], which must be mono PCM16 already resampled to
     * [SAMPLE_RATE_HZ]. Samples beyond the last full 128-sample hop are left
     * out of the transform, exactly like the reference; they still count
     * towards [ShazamSignature.sampleCount] because the signature header
     * reports the source's true length.
     */
    fun generate(samples: ShortArray): ShazamSignature {
        val state = Generator(samples.size)
        var offset = 0
        while (offset + HOP_SIZE <= samples.size) {
            state.process(samples, offset)
            offset += HOP_SIZE
        }
        return state.toSignature()
    }

    private class Generator(private val sampleCount: Int) {

        /** The most recent 2048 samples, oldest at [ringIndex]. */
        private val ring = ShortArray(FFT_SIZE)
        private var ringIndex = 0

        private val fftRe = FloatArray(FFT_SIZE)
        private val fftIm = FloatArray(FFT_SIZE)

        /** Power spectra of the last 256 hops: `(re² + im²) / 2^17`. */
        private val fftOutputs = Array(HISTORY_SIZE) { FloatArray(BIN_COUNT) }
        private var fftOutputsIndex = 0

        /** The same spectra after frequency- and time-domain spreading. */
        private val spreadOutputs = Array(HISTORY_SIZE) { FloatArray(BIN_COUNT) }
        private var spreadOutputsIndex = 0

        private var spreadFftsDone = 0

        private val peaksByBand =
            Array(ShazamFrequencyBand.entries.size) { mutableListOf<ShazamPeak>() }

        fun process(samples: ShortArray, offset: Int) {
            System.arraycopy(samples, offset, ring, ringIndex, HOP_SIZE)
            ringIndex = (ringIndex + HOP_SIZE) and (FFT_SIZE - 1)

            // Reorder the ring so the oldest sample is first, then window it.
            val window = ShazamHanningWindow.multipliers
            for (index in 0 until FFT_SIZE) {
                val sample = ring[(index + ringIndex) and (FFT_SIZE - 1)].toFloat()
                fftRe[index] = sample * window[index]
                fftIm[index] = 0f
            }

            fft(fftRe, fftIm)

            val powers = fftOutputs[fftOutputsIndex]
            for (bin in 0 until BIN_COUNT) {
                val re = fftRe[bin]
                val im = fftIm[bin]
                powers[bin] = max((re * re + im * im) / POWER_DIVISOR, POWER_FLOOR)
            }
            fftOutputsIndex = (fftOutputsIndex + 1) and HISTORY_MASK

            spreadPeaks()
            spreadFftsDone++
            if (spreadFftsDone >= PEAK_START_PASS) detectPeaks()
        }

        /**
         * Builds the spread spectrum of the frame just transformed: each bin
         * takes the loudest of itself and the next two bins, then that maximum
         * is folded back into the previous 1, 3 and 6 frames. Detection uses
         * this widened view so a strong peak can suppress weaker neighbours in
         * both frequency and time.
         */
        private fun spreadPeaks() {
            val real = fftOutputs[(fftOutputsIndex - 1) and HISTORY_MASK]
            val spread = spreadOutputs[spreadOutputsIndex]
            real.copyInto(spread)

            for (bin in 0..BIN_COUNT - 3) {
                spread[bin] = maxOf(spread[bin], spread[bin + 1], spread[bin + 2])
            }

            for (bin in 0 until BIN_COUNT) {
                for (offset in SPREAD_TIME_OFFSETS) {
                    val earlier = spreadOutputs[(spreadOutputsIndex - offset) and HISTORY_MASK]
                    earlier[bin] = max(earlier[bin], spread[bin])
                }
            }

            spreadOutputsIndex = (spreadOutputsIndex + 1) and HISTORY_MASK
        }

        /**
         * Finds the peaks of the frame that was transformed 46 hops ago.
         *
         * Detection runs late because spreading has to see enough later frames
         * before it can tell which values are really local maxima, so the first
         * candidate is only reported after 46 hops.
         */
        private fun detectPeaks() {
            val raw = fftOutputs[(fftOutputsIndex - PEAK_START_PASS) and HISTORY_MASK]
            val spread = spreadOutputs[(spreadOutputsIndex - SPREAD_LOOKBACK) and HISTORY_MASK]
            val passNumber = spreadFftsDone - PEAK_START_PASS

            for (bin in 10..1014) {
                val power = raw[bin]
                if (power < PEAK_THRESHOLD || power < spread[bin - 1]) continue

                var loudestInFrequency = 0f
                for (offset in FREQUENCY_NEIGHBOURS) {
                    loudestInFrequency = max(loudestInFrequency, spread[bin + offset])
                }
                if (power <= loudestInFrequency) continue

                var loudestInTime = loudestInFrequency
                for (offset in TIME_NEIGHBOURS) {
                    val other = spreadOutputs[(spreadOutputsIndex + offset) and HISTORY_MASK]
                    loudestInTime = max(loudestInTime, other[bin - 1])
                }
                if (power <= loudestInTime) continue

                val magnitude = magnitude(raw[bin])
                val before = magnitude(raw[bin - 1])
                val after = magnitude(raw[bin + 1])

                // SongRec asserts this is non-negative. Skipping the rare
                // malformed peak beats crashing a recognition on noisy input.
                val variation1 = magnitude * 2f - before - after
                if (variation1 <= 0f) continue
                val variation2 = (after - before) * 32f / variation1

                // The stored bin is the FFT bin times 64 plus a fractional
                // correction, which is what gives the frequency its resolution.
                val correctedBin = bin * 64 + variation2.toInt()
                val frequencyHz = correctedBin * HZ_PER_CORRECTED_BIN
                val band = ShazamFrequencyBand.forFrequency(frequencyHz.toInt()) ?: continue

                peaksByBand[band.index].add(
                    ShazamPeak(
                        fftPassNumber = passNumber,
                        magnitude = magnitude.toInt(),
                        frequencyBin = correctedBin,
                    ),
                )
            }
        }

        /**
         * The reference's compressed peak height. Note that the log floor is
         * applied *after* `ln`: a weak peak is not clipped to the threshold
         * first, so its stored magnitude sits just above 6144.
         */
        private fun magnitude(power: Float): Float =
            max(ln(power), MAGNITUDE_LOG_FLOOR) * MAGNITUDE_SCALE + MAGNITUDE_OFFSET

        fun toSignature(): ShazamSignature = ShazamSignature(
            sampleRateHz = SAMPLE_RATE_HZ,
            sampleCount = sampleCount,
            peaksByBand = ShazamFrequencyBand.entries.associateWith { peaksByBand[it.index] },
        )

        /**
         * In-place iterative radix-2 FFT. The arrays must be [FFT_SIZE] long;
         * the transform is unnormalized, as the reference FFT is. Its output
         * is only ever squared into a power, so direction does not matter.
         */
        private fun fft(re: FloatArray, im: FloatArray) {
            for (index in 0 until FFT_SIZE) {
                val reversed = BIT_REVERSAL[index]
                if (index < reversed) {
                    var swap = re[index]
                    re[index] = re[reversed]
                    re[reversed] = swap

                    swap = im[index]
                    im[index] = im[reversed]
                    im[reversed] = swap
                }
            }

            var length = 2
            while (length <= FFT_SIZE) {
                val half = length ushr 1
                val tableStep = FFT_SIZE / length
                for (start in 0 until FFT_SIZE step length) {
                    var tableIndex = 0
                    for (offset in 0 until half) {
                        val twiddleRe = COS_TABLE[tableIndex]
                        val twiddleIm = SIN_TABLE[tableIndex]
                        val even = start + offset
                        val odd = even + half

                        val oddRe = re[odd] * twiddleRe - im[odd] * twiddleIm
                        val oddIm = re[odd] * twiddleIm + im[odd] * twiddleRe

                        re[odd] = re[even] - oddRe
                        im[odd] = im[even] - oddIm
                        re[even] += oddRe
                        im[even] += oddIm

                        tableIndex += tableStep
                    }
                }
                length = length shl 1
            }
        }
    }
}
