/*
 * Ported from Orchard (https://github.com/SFG5453/Orchard).
 *
 * Copyright (C) 2026 SFG545 (original Orchard implementation)
 * Copyright (C) 2026 Kushagra Singh (BitChord adaptation)
 *
 * Orchard's original source is licensed under the GNU Affero General Public
 * License, version 3 or later. Per AGPLv3 section 13, this file is combined
 * here into BitChord -- a work licensed under the GNU General Public
 * License, version 3 or later -- and remains itself governed by the AGPLv3
 * as part of that combination.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.music.bitchord.playback.smart

/**
 * The log-mel front end the Beat This! model expects, backed by the native analyzer.
 *
 * Every constant is dictated by the trained network rather than chosen: it was trained on
 * torchaudio's MelSpectrogram at 22,050 Hz with n_fft 1024, hop 441 (so exactly 50 frames per
 * second), 128 Slaney mel bands from 30 Hz to 11 kHz, and `log1p(1000 * magnitude)`. A front end
 * that differs even in window convention feeds the model something it has never seen, which is why
 * this is a port of the native C++ rather than a reimplementation.
 *
 * [sampleRate] is required, not preferred: audio at any other rate is refused rather than
 * resampled here, because resampling belongs upstream where full-bandwidth samples still exist.
 */
object MelSpectrogram {

    /** True when the native library loaded. Analysis is optional, so this is a fact, not a fault. */
    val available: Boolean = runCatching { System.loadLibrary("bitchord_analysis") }.isSuccess

    /** Mel bands per frame; the model's input width. */
    val mels: Int by lazy { if (available) nativeMelCount() else 128 }

    /** The only sample rate the front end accepts. */
    val sampleRate: Double by lazy { if (available) nativeSampleRate() else 22_050.0 }

    /** Samples between frame starts; 441 at 22,050 Hz is exactly 20 ms. */
    val hop: Int by lazy { if (available) nativeHop() else 441 }

    /** Frames per second of output, which is what beat times are derived from. */
    val frameRate: Double get() = sampleRate / hop

    /**
     * Computes the spectrogram for contiguous mono float PCM at [sampleRate].
     *
     * Returns null when the native library is missing, the rate is wrong, or the input is shorter
     * than one padded frame. Callers treat that as "no model prediction available" rather than as
     * an error; the transition policy already degrades on absent evidence.
     */
    fun compute(samples: FloatArray, sampleRate: Double = this.sampleRate): Spectrogram? {
        if (!available || samples.isEmpty()) return null
        val values = nativeCompute(samples, sampleRate)
        if (values.isEmpty()) return null
        return Spectrogram(values = values, frames = values.size / mels, mels = mels)
    }

    /**
     * A flattened spectrogram, row-major over frames: frame `f` band `b` lives at
     * `f * mels + b`. Flat because the only consumer hands it straight to an ONNX tensor of
     * shape `[1, frames, mels]`.
     */
    data class Spectrogram(val values: FloatArray, val frames: Int, val mels: Int) {
        /** Seconds covered, useful for turning frame indices back into track times. */
        val durationSeconds: Double get() = frames / (sampleRate / hop)

        override fun equals(other: Any?): Boolean =
            this === other ||
                (other is Spectrogram && frames == other.frames && mels == other.mels &&
                    values.contentEquals(other.values))

        override fun hashCode(): Int = 31 * (31 * values.contentHashCode() + frames) + mels
    }

    /**
     * Converts mono float PCM to [sampleRate], the only rate [compute] accepts.
     *
     * A windowed-sinc conversion rather than decimation: dropping samples would fold everything
     * above 11 kHz back into the band the mel filterbank reads, and an aliased spectrogram yields
     * a *wrong* beat grid rather than a noisy one, which the planner would then trust.
     *
     * Returns the input unchanged when the rates already match, and null when the native library
     * is missing or the rates are unusable.
     */
    fun resample(samples: FloatArray, inputRate: Double, outputRate: Double = sampleRate): FloatArray? {
        if (!available || samples.isEmpty() || inputRate <= 0 || outputRate <= 0) return null
        return nativeResample(samples, inputRate, outputRate).takeIf { it.isNotEmpty() }
    }

    @JvmStatic private external fun nativeCompute(samples: FloatArray, sampleRate: Double): FloatArray
    @JvmStatic private external fun nativeResample(
        samples: FloatArray,
        inputRate: Double,
        outputRate: Double,
    ): FloatArray
    @JvmStatic private external fun nativeMelCount(): Int
    @JvmStatic private external fun nativeSampleRate(): Double
    @JvmStatic private external fun nativeHop(): Int
}
