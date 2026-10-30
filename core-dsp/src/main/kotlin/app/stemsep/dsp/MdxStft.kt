package app.stemsep.dsp

import org.jtransforms.fft.FloatFFT_1D
import pl.edu.icm.jlargearrays.ConcurrencyUtils

/**
 * The stereo STFT used by UVR MDX-Net models.
 *
 * Input tensor layout is `[1, 4, frequencyBins, timeFrames]`:
 * L-real, L-imag, R-real, R-imag. The real FFT is computed with a periodic
 * Hann window, `center = true` (reflect padding by `fftSize / 2`) and the
 * UVR convention of zeroing the first three frequency bins.
 */
class MdxStft(val config: MdxConfig) {
    private val fft = FloatFFT_1D(config.fftSize.toLong())
    private val window = periodicHann(config.fftSize)
    private val analysisFrame = FloatArray(config.fftSize * 2)
    private val fullSpectrum = FloatArray(config.fftSize * 2)
    private val synthesisLength = (config.timeFrames - 1) * config.hopLength + config.fftSize
    private val synthesis = FloatArray(synthesisLength)
    private val windowSum = FloatArray(synthesisLength)

    init {
        ConcurrencyUtils.setNumberOfThreads(1)
        for (time in 0 until config.timeFrames) {
            val frameStart = time * config.hopLength
            for (sample in 0 until config.fftSize) {
                val value = window[sample]
                windowSum[frameStart + sample] += value * value
            }
        }
    }

    fun forward(mixture: FloatArray, out: FloatArray) {
        require(mixture.size >= config.segmentSamples * 2) { "mixture buffer is too small" }
        require(out.size >= config.tensorElements) { "tensor buffer is too small" }
        out.fill(0f)

        val centerPad = config.fftSize / 2
        for (channel in 0 until 2) {
            val realPlane = channel * 2
            val imaginaryPlane = realPlane + 1
            for (time in 0 until config.timeFrames) {
                val frameStart = time * config.hopLength - centerPad
                for (sample in 0 until config.fftSize) {
                    val sourceIndex = frameStart + sample
                    analysisFrame[sample] = if (sourceIndex in 0 until config.segmentSamples) {
                        mixture[sourceIndex * 2 + channel] * window[sample]
                    } else {
                        val reflected = reflectIndex(sourceIndex, config.segmentSamples)
                        mixture[reflected * 2 + channel] * window[sample]
                    }
                }
                analysisFrame.fill(0f, config.fftSize, config.fftSize * 2)
                fft.realForwardFull(analysisFrame)

                for (frequency in 0 until config.frequencyBins) {
                    out[tensorIndex(realPlane, frequency, time)] = analysisFrame[frequency * 2]
                    out[tensorIndex(imaginaryPlane, frequency, time)] = analysisFrame[frequency * 2 + 1]
                }
            }
        }

        if (config.zeroLowBins) {
            val binsToZero = minOf(3, config.frequencyBins)
            for (channel in 0 until 4) {
                for (frequency in 0 until binsToZero) {
                    for (time in 0 until config.timeFrames) {
                        out[tensorIndex(channel, frequency, time)] = 0f
                    }
                }
            }
        }
    }

    fun inverse(spectrum: FloatArray, out: FloatArray) {
        require(spectrum.size >= config.tensorElements) { "tensor buffer is too small" }
        require(out.size >= config.segmentSamples * 2) { "output buffer is too small" }
        out.fill(0f)

        val centerPad = config.fftSize / 2
        val nyquist = config.fftSize / 2
        for (channel in 0 until 2) {
            val realPlane = channel * 2
            val imaginaryPlane = realPlane + 1
            synthesis.fill(0f)

            for (time in 0 until config.timeFrames) {
                fullSpectrum.fill(0f)
                for (frequency in 0 until config.frequencyBins) {
                    val real = spectrum[tensorIndex(realPlane, frequency, time)]
                    val imaginary = spectrum[tensorIndex(imaginaryPlane, frequency, time)]
                    if (!real.isFinite() || !imaginary.isFinite()) {
                        throw IllegalStateException("Model output contains NaN or Inf")
                    }
                    fullSpectrum[frequency * 2] = real
                    fullSpectrum[frequency * 2 + 1] = imaginary
                }

                if (config.frequencyBins <= nyquist) {
                    fullSpectrum[config.fftSize] = 0f
                }
                fullSpectrum[config.fftSize + 1] = 0f

                for (frequency in 1 until nyquist) {
                    val mirror = config.fftSize - frequency
                    fullSpectrum[mirror * 2] = fullSpectrum[frequency * 2]
                    fullSpectrum[mirror * 2 + 1] = -fullSpectrum[frequency * 2 + 1]
                }

                fft.complexInverse(fullSpectrum, true)
                val frameStart = time * config.hopLength
                for (sample in 0 until config.fftSize) {
                    synthesis[frameStart + sample] +=
                        fullSpectrum[sample * 2] * window[sample]
                }
            }

            for (sample in 0 until config.segmentSamples) {
                val synthesisIndex = centerPad + sample
                val denominator = windowSum[synthesisIndex]
                val value = if (denominator > 1e-8f) {
                    synthesis[synthesisIndex] / denominator
                } else {
                    0f
                }
                out[sample * 2 + channel] = if (value.isFinite()) value else 0f
            }
        }
    }

    private fun tensorIndex(plane: Int, frequency: Int, time: Int): Int =
        (plane * config.frequencyBins + frequency) * config.timeFrames + time

    private fun reflectIndex(index: Int, size: Int): Int {
        if (size <= 1) return 0
        var value = index
        while (value < 0 || value >= size) {
            if (value < 0) value = -value
            if (value >= size) value = 2 * size - 2 - value
        }
        return value
    }
}
