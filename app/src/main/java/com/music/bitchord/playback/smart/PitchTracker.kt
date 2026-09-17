package com.music.bitchord.playback.smart

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.settings.AppSettings
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Vocal/instrument fundamental tracking for the blueprint's key-shift verify
 * step: the planner shifts the incoming track toward the outgoing key (§5.2),
 * and a measured median F0 is the only thing that can contradict the detected
 * key before the shift is committed.
 *
 * Two engines, one contract. CREPE-tiny (MIT, 1.9 MB ONNX, runs on the
 * already-present ORT 1.28 — no new dependency) is tried first; a pure-Kotlin
 * YIN fallback answers when the asset is missing or inference throws. A wrong
 * F0 vetoes a shift, but a missing F0 never blocks a transition: every
 * call-site treats 0 Hz / 0 confidence as "no evidence".
 *
 * All frame math lives in the companion with no Android dependency so it runs
 * under plain JUnit (see PitchTrackerTest): the Context only opens the model
 * file.
 */
class PitchTracker(private val context: Context) {

    /** F0 per frame on the analysed audio's own timeline; 0.0 marks unvoiced. */
    data class PitchCurve(
        val times: List<Double>,
        val f0Hz: List<Double>,
        val confidence: List<Double>,
    ) {
        /** Median over voiced frames, or 0 when none sang confidently. */
        fun voicedMedianHz(): Double {
            val voiced = f0Hz.filter { it > 0 }
            if (voiced.isEmpty()) return 0.0
            return voiced.sorted().let { it[it.size / 2] }
        }

        /** Mean confidence over voiced frames, or 0 when none. */
        fun voicedMeanConfidence(): Double {
            var sum = 0.0
            var count = 0
            for (i in f0Hz.indices) {
                if (f0Hz[i] > 0 && i < confidence.size) {
                    sum += confidence[i]
                    count += 1
                }
            }
            return if (count > 0) sum / count else 0.0
        }
    }

    @Volatile private var session: OrtSession? = null
    @Volatile private var sessionThreads = 0
    private val lock = Any()

    private fun session(): OrtSession? {
        val threads = AppSettings.automixPerformanceMode.value.inferenceThreads
        session?.takeIf { sessionThreads == threads }?.let { return it }
        synchronized(lock) {
            session?.takeIf { sessionThreads == threads }?.let { return it }
            runCatching { session?.close() }
            session = null
            return runCatching {
                val file = File(context.filesDir, MODEL_ASSET)
                if (!file.exists() || file.length() == 0L) {
                    context.assets.open(MODEL_ASSET).use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                val options = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(threads)
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    // Same reasoning as BeatTracker: analysis runs a handful of
                    // times per track, so per-run allocation beats a retained arena.
                    setCPUArenaAllocator(false)
                    setMemoryPatternOptimization(false)
                }
                OrtEnvironment.getEnvironment().createSession(file.absolutePath, options)
                    .also {
                        session = it
                        sessionThreads = threads
                    }
            }.onFailure { TrackLog.w(TAG, "CREPE model unavailable; YIN fallback answers", it) }
                .getOrNull()
        }
    }

    /**
     * Tracks [pcm], which must already be mono at [SAMPLE_RATE] Hz.
     * [offsetSeconds] maps frame times back onto the track's timeline.
     */
    fun track(pcm: FloatArray, offsetSeconds: Double = 0.0): PitchCurve? {
        val onnx = runCatching { trackOnnx(pcm, offsetSeconds) }.getOrNull()
        if (onnx != null) return onnx
        return yinTrack(pcm, offsetSeconds)
    }

    private fun trackOnnx(pcm: FloatArray, offsetSeconds: Double): PitchCurve? {
        val active = session() ?: return null
        if (pcm.size < FRAME_SAMPLES) return null
        val environment = OrtEnvironment.getEnvironment()
        val name = active.inputNames.first()
        val frameCount = 1 + (pcm.size - FRAME_SAMPLES) / HOP_SAMPLES

        val times = ArrayList<Double>(frameCount)
        val f0 = ArrayList<Double>(frameCount)
        val conf = ArrayList<Double>(frameCount)
        var start = 0
        while (start < frameCount) {
            val batch = min(INFERENCE_BATCH, frameCount - start)
            val input = FloatArray(batch * FRAME_SAMPLES)
            for (b in 0 until batch) {
                normalizeFrame(pcm, (start + b) * HOP_SAMPLES, input, b * FRAME_SAMPLES)
            }
            val shape = longArrayOf(batch.toLong(), FRAME_SAMPLES.toLong())
            OnnxTensor.createTensor(environment, FloatBuffer.wrap(input), shape).use { tensor ->
                active.run(mapOf(name to tensor)).use { outputs ->
                    @Suppress("UNCHECKED_CAST")
                    val activation = outputs[0].value as Array<FloatArray>
                    for (b in 0 until batch) {
                        val (freq, confidence) = decodeActivation(activation[b])
                        val index = start + b
                        times += offsetSeconds + (index * HOP_SAMPLES + FRAME_SAMPLES / 2).toDouble() / SAMPLE_RATE
                        f0 += freq
                        conf += confidence
                    }
                }
            }
            start += batch
        }
        TrackLog.d(TAG, "pitch via crepe: $frameCount frames")
        return PitchCurve(times, f0, conf)
    }

    fun release() {
        synchronized(lock) {
            runCatching { session?.close() }
            session = null
            sessionThreads = 0
        }
    }

    companion object {
        private const val TAG = "BitChordPitchTracker"
        private const val MODEL_ASSET = "crepe_tiny.onnx"
        private const val INFERENCE_BATCH = 32

        /** CREPE reads 16 kHz mono, 1024-sample frames every 10 ms. */
        const val SAMPLE_RATE = 16000
        const val FRAME_SAMPLES = 1024
        const val HOP_SAMPLES = 160

        /** 360 bins of 20 cents from 1997.38 cents re 10 Hz (torchcrepe constants). */
        const val BINS = 360
        const val CENTS_PER_BIN = 20.0
        const val CENTS_OFFSET = 1997.3794084376191

        /** Sigmoid peak below this is unvoiced. Matches CREPE's own 0.5 periodicity gate. */
        const val CONFIDENCE_THRESHOLD = 0.5

        /** YIN fallback: 1024-sample window every 32 ms, fundamentals 50–1000 Hz. */
        private const val YIN_WINDOW = 1024
        private const val YIN_HOP = 512
        private const val YIN_MIN_TAU = 16
        private const val YIN_MAX_TAU = 320
        private const val YIN_THRESHOLD = 0.15
        private const val YIN_SILENCE_RMS = 0.002

        fun binsToFrequency(bin: Int): Double {
            val cents = CENTS_PER_BIN * bin + CENTS_OFFSET
            return 10.0 * 2.0.pow(cents / 1200.0)
        }

        /**
         * torchcrepe's weighted-argmax decode: sigmoid the logits, then the
         * probability-weighted mean of bin centres in a ±4 window around the
         * peak. Returns (0.0, peak) when nothing clears the gate.
         */
        fun decodeActivation(activation: FloatArray): Pair<Double, Double> {
            if (activation.size != BINS) return 0.0 to 0.0
            var best = 0
            var bestLogit = Float.NEGATIVE_INFINITY
            for (i in activation.indices) {
                if (activation[i] > bestLogit) {
                    bestLogit = activation[i]
                    best = i
                }
            }
            val from = max(0, best - 4)
            val to = min(BINS, best + 5)
            var num = 0.0
            var den = 0.0
            var peak = 0.0
            for (i in from until to) {
                val prob = 1.0 / (1.0 + exp(-activation[i].toDouble()))
                if (prob > peak) peak = prob
                num += (CENTS_PER_BIN * i + CENTS_OFFSET) * prob
                den += prob
            }
            if (peak <= CONFIDENCE_THRESHOLD || den <= 0.0) return 0.0 to peak
            return (10.0 * 2.0.pow(num / den / 1200.0)) to peak
        }

        private fun normalizeFrame(pcm: FloatArray, from: Int, out: FloatArray, to: Int) {
            var mean = 0.0
            for (i in 0 until FRAME_SAMPLES) mean += pcm[from + i]
            mean /= FRAME_SAMPLES
            var variance = 0.0
            for (i in 0 until FRAME_SAMPLES) {
                val centred = pcm[from + i] - mean
                variance += centred * centred
            }
            val std = kotlin.math.sqrt(variance / FRAME_SAMPLES)
            if (std < 1e-8) {
                java.util.Arrays.fill(out, to, to + FRAME_SAMPLES, 0f)
                return
            }
            for (i in 0 until FRAME_SAMPLES) out[to + i] = ((pcm[from + i] - mean) / std).toFloat()
        }

        /**
         * YIN (de Cheveigné & Kawahara 2002) in ~60 lines: cumulative-mean-
         * normalized difference, first dip under threshold, parabolic
         * refinement. Slow next to the model (~1 s per 30 s head) but
         * dependency-free, which is exactly what a fallback is for.
         */
        fun yinTrack(pcm: FloatArray, offsetSeconds: Double = 0.0): PitchCurve? {
            if (pcm.size < YIN_WINDOW) return null
            val times = ArrayList<Double>()
            val f0 = ArrayList<Double>()
            val conf = ArrayList<Double>()
            val squared = DoubleArray(pcm.size) { pcm[it].toDouble() * pcm[it] }
            var pos = 0
            while (pos + YIN_WINDOW <= pcm.size) {
                var energy = 0.0
                for (i in 0 until YIN_WINDOW) energy += squared[pos + i]
                energy /= YIN_WINDOW
                val centre = offsetSeconds + (pos + YIN_WINDOW / 2).toDouble() / SAMPLE_RATE
                if (energy < YIN_SILENCE_RMS * YIN_SILENCE_RMS) {
                    times += centre
                    f0 += 0.0
                    conf += 0.0
                    pos += YIN_HOP
                    continue
                }
                val (freq, periodicity) = yinFrame(pcm, pos)
                times += centre
                if (periodicity < CONFIDENCE_THRESHOLD) {
                    f0 += 0.0
                    conf += periodicity
                } else {
                    f0 += freq
                    conf += periodicity
                }
                pos += YIN_HOP
            }
            if (times.isEmpty()) return null
            TrackLog.d(TAG, "pitch via yin: ${times.size} frames")
            return PitchCurve(times, f0, conf)
        }

        private fun yinFrame(pcm: FloatArray, pos: Int): Pair<Double, Double> {
            val running = DoubleArray(YIN_MAX_TAU + 1)
            var cumulative = 0.0
            var tauEstimate = -1
            var minCmnd = Double.POSITIVE_INFINITY
            var minTau = YIN_MIN_TAU
            for (tau in YIN_MIN_TAU..YIN_MAX_TAU) {
                var diff = 0.0
                val limit = pos + YIN_WINDOW - tau
                for (j in pos until limit) {
                    val delta = pcm[j].toDouble() - pcm[j + tau]
                    diff += delta * delta
                }
                running[tau] = diff
                cumulative += diff
                // Cumulative-mean-normalized difference; tau 0 is 1 by definition.
                val cmnd = if (cumulative > 0) diff * tau / cumulative else 1.0
                if (tauEstimate == -1 && tau > YIN_MIN_TAU && cmnd < YIN_THRESHOLD) {
                    tauEstimate = tau
                }
                if (cmnd < minCmnd) {
                    minCmnd = cmnd
                    minTau = tau
                }
            }
            val tau = if (tauEstimate != -1) {
                // Past the first dip, take the local minimum — the dip's floor,
                // not its edge.
                var best = tauEstimate
                while (best + 1 <= YIN_MAX_TAU && running[best + 1] < running[best]) best += 1
                best
            } else {
                minTau
            }
            // Parabolic interpolation around the estimate, on the raw difference.
            val refined = if (tau > YIN_MIN_TAU && tau < YIN_MAX_TAU) {
                val left = running[tau - 1]
                val centre = running[tau]
                val right = running[tau + 1]
                val denominator = left - 2 * centre + right
                if (denominator > 0) tau + (0.5 * (left - right) / denominator).coerceIn(-0.5, 0.5) else tau.toDouble()
            } else {
                tau.toDouble()
            }
            val confidence = (1.0 - minCmnd).coerceIn(0.0, 1.0)
            return (SAMPLE_RATE / refined) to confidence
        }

        /** Absolute pitch distance in semitones, or null when either end is unmeasured. */
        fun pitchSemitoneGap(aHz: Double, bHz: Double): Double? {
            if (aHz <= 0 || bHz <= 0 || !aHz.isFinite() || !bHz.isFinite()) return null
            return kotlin.math.abs(12 * (ln(aHz / bHz) / ln(2.0)))
        }
    }
}
