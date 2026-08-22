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

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.ceil
import kotlin.math.floor

/** The linear-frequency STFT front end open-unmix was trained on. */
object VocalSpectrogram {
    val available: Boolean get() = MelSpectrogram.available
    val bins: Int by lazy { if (available) nativeBins() else 2049 }
    val sampleRate: Double by lazy { if (available) nativeSampleRate() else 44_100.0 }
    val hop: Int by lazy { if (available) nativeHop() else 1024 }
    val fftSize: Int by lazy { if (available) nativeFftSize() else 4096 }
    val frameRate: Double get() = sampleRate / hop

    /**
     * Computes the magnitude STFT for planar stereo at [sampleRate].
     *
     * Returns null when the library is missing, the rate is wrong, or the input is shorter than one
     * padded frame, all of which mean "no mask available" rather than an error.
     */
    fun compute(left: FloatArray, right: FloatArray, rate: Double = sampleRate): Spectrogram? {
        if (!available || left.isEmpty() || left.size != right.size) return null
        val values = nativeCompute(left, right, rate)
        if (values.isEmpty()) return null
        return Spectrogram(values, frames = values.size / (CHANNELS * bins), bins = bins)
    }

    /**
     * Bin-major, flattened: channel c, bin b, frame f is at `(c * bins + b) * frames + f`. That
     * ordering is not the natural one for an STFT computed a frame at a time; it is chosen to
     * match the model's `[1, 2, bins, frames]` tensor exactly, so nothing has to transpose.
     */
    data class Spectrogram(val values: FloatArray, val frames: Int, val bins: Int) {
        override fun equals(other: Any?): Boolean =
            this === other || (other is Spectrogram && frames == other.frames &&
                bins == other.bins && values.contentEquals(other.values))

        override fun hashCode(): Int = 31 * (31 * values.contentHashCode() + frames) + bins
    }

    const val CHANNELS = 2

    @JvmStatic private external fun nativeCompute(left: FloatArray, right: FloatArray, rate: Double): FloatArray
    @JvmStatic private external fun nativeBins(): Int
    @JvmStatic private external fun nativeSampleRate(): Double
    @JvmStatic private external fun nativeHop(): Int
    @JvmStatic private external fun nativeFftSize(): Int
}

/**
 * Vocal-presence tracking with open-unmix's "vocals" target (Stöter & Liutkus, Inria/SigSep).
 *
 * The transition policy uses this to avoid mixing two vocals over each other: a blend where both
 * tracks are singing is the one case that reliably sounds wrong however well the beats line up.
 *
 * Chosen because its **weights** are MIT, confirmed on the Zenodo deposit rather than inferred from
 * the code repository. Meta's htdemucs separates better but releases its pretrained weights under
 * CC-BY-NC-4.0, which a distributed application cannot ship, and its ONNX export additionally has
 * unresolved blockers around complex-valued STFT ops.
 *
 * Only the vocals target is used. open-unmix trains four independent checkpoints; BitChord needs to
 * know how much vocal content is present at an instant, not to reconstruct four stems.
 */
class VocalTracker(private val context: Context) {

    @Volatile private var session: OrtSession? = null
    private val lock = Any()

    private fun session(): OrtSession? {
        session?.let { return it }
        synchronized(lock) {
            session?.let { return it }
            return runCatching {
                val file = File(context.filesDir, MODEL_ASSET)
                if (!file.exists() || file.length() == 0L) {
                    context.assets.open(MODEL_ASSET).use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                val options = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(INFERENCE_THREADS)
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    // Same reasoning as BeatTracker: the arena retains every block it allocates for
                    // the life of the session, which a backgrounded music player cannot justify.
                    setCPUArenaAllocator(false)
                    setMemoryPatternOptimization(false)
                }
                OrtEnvironment.getEnvironment().createSession(file.absolutePath, options)
                    .also { session = it }
            }.onFailure { Log.w(TAG, "Vocal model unavailable; no mask will be produced", it) }
                .getOrNull()
        }
    }

    /**
     * Returns one vocal-presence value per STFT frame in [0, 1], or null when unavailable.
     *
     * The model's input width is fixed at [FIXED_FRAMES] (~22.8 s), which was chosen upstream to
     * cover a transition overlap plus padding. Shorter input is zero-padded; longer is refused
     * rather than chunked, because a transition never needs more than one window.
     */
    @Suppress("UNCHECKED_CAST")
    fun track(left: FloatArray, right: FloatArray, rate: Double): FloatArray? {
        if (!VocalSpectrogram.available) return null
        val resampledLeft = MelSpectrogram.resample(left, rate, VocalSpectrogram.sampleRate) ?: return null
        val resampledRight = MelSpectrogram.resample(right, rate, VocalSpectrogram.sampleRate) ?: return null

        val started = System.currentTimeMillis()
        val spectrogram = VocalSpectrogram.compute(resampledLeft, resampledRight) ?: return null
        if (spectrogram.frames > FIXED_FRAMES) {
            Log.d(TAG, "Window of ${spectrogram.frames} frames exceeds the model's $FIXED_FRAMES")
            return null
        }
        val active = session() ?: return null

        return runCatching {
            val bins = spectrogram.bins
            val padded = padToFixedFrames(spectrogram.values, bins, spectrogram.frames)
            val environment = OrtEnvironment.getEnvironment()
            val shape = longArrayOf(1, VocalSpectrogram.CHANNELS.toLong(), bins.toLong(), FIXED_FRAMES.toLong())

            OnnxTensor.createTensor(environment, FloatBuffer.wrap(padded), shape).use { tensor ->
                active.run(mapOf(active.inputNames.first() to tensor)).use { outputs ->
                    val target = (outputs.get(0).value as Array<Array<Array<FloatArray>>>)[0]
                    val curve = reduceToBandCurve(padded, target, bins, spectrogram.frames)
                    Log.d(
                        TAG,
                        "vocal mask ${spectrogram.frames} frames in " +
                            "${System.currentTimeMillis() - started}ms",
                    )
                    curve
                }
            }
        }.onFailure { Log.w(TAG, "Vocal inference failed", it) }.getOrNull()
    }

    /**
     * Zero-pads to the model's fixed width.
     *
     * The stride changes as well as the length: the source is stored at `frames` per bin and the
     * model wants [FIXED_FRAMES], so this is a re-stride rather than an append.
     */
    private fun padToFixedFrames(values: FloatArray, bins: Int, frames: Int): FloatArray {
        if (frames == FIXED_FRAMES) return values
        val padded = FloatArray(VocalSpectrogram.CHANNELS * bins * FIXED_FRAMES)
        for (channel in 0 until VocalSpectrogram.CHANNELS) {
            for (bin in 0 until bins) {
                val from = (channel * bins + bin) * frames
                val to = (channel * bins + bin) * FIXED_FRAMES
                values.copyInto(padded, to, from, from + frames)
            }
        }
        return padded
    }

    /**
     * Averages `mask = target / (mix + eps)` across a frequency band, then across channels.
     *
     * Band-averaging rather than a full per-bin mask: the only consumer is a single number per
     * instant (how vocal this moment is), so per-bin resolution would be work with no reader.
     * Only the frames carrying real audio are reduced; the padded tail's mask is meaningless and
     * folding it in would drag every short window toward silence.
     */
    private fun reduceToBandCurve(
        mix: FloatArray,
        target: Array<Array<FloatArray>>,
        bins: Int,
        usableFrames: Int,
    ): FloatArray {
        val lowBin = floor(LOW_HZ * VocalSpectrogram.fftSize / VocalSpectrogram.sampleRate)
            .toInt().coerceAtLeast(0)
        val highBin = ceil(HIGH_HZ * VocalSpectrogram.fftSize / VocalSpectrogram.sampleRate)
            .toInt().coerceAtMost(bins - 1)
        if (highBin <= lowBin || usableFrames <= 0) return FloatArray(0)

        val curve = FloatArray(usableFrames)
        for (frame in 0 until usableFrames) {
            var sum = 0.0
            var count = 0
            for (channel in 0 until VocalSpectrogram.CHANNELS) {
                for (bin in lowBin..highBin) {
                    val mixValue = mix[(channel * bins + bin) * FIXED_FRAMES + frame]
                    if (mixValue <= 1e-6f) continue
                    val ratio = target[channel][bin][frame] / mixValue
                    sum += ratio.coerceIn(0f, 1f)
                    count += 1
                }
            }
            curve[frame] = if (count > 0) (sum / count).toFloat() else 0f
        }
        return curve
    }

    fun release() {
        synchronized(lock) {
            runCatching { session?.close() }
            session = null
        }
    }

    companion object {
        private const val TAG = "BitChordVocalTracker"
        private const val MODEL_ASSET = "vocals_umxhq_int8.onnx"
        private const val INFERENCE_THREADS = 4

        /** The model's fixed input width, ~22.8 s, chosen upstream to cover a transition overlap. */
        const val FIXED_FRAMES = 960

        /** The band a vocal actually occupies; below and above it the mask says little. */
        private const val LOW_HZ = 200.0
        private const val HIGH_HZ = 4000.0
    }
}
