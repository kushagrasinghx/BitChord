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
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Beat and downbeat tracking with the Beat This! model (CPJKU, ISMIR 2024).
 *
 * Why a model at all: tempo and *meter* are different problems. Autocorrelation reads tempo well,
 * but nothing in an autocorrelation says which of four beats is beat one, and a mix that enters on
 * beat three sounds wrong even when every beat lines up.
 *
 * Beat This! is the one that can be shipped: both its code and its trained weights are MIT. Most
 * published MIR weights, Essentia's included, are CC BY-NC-SA.
 *
 * Everything here is optional. A missing model, an unreadable graph, a rate mismatch, too few
 * peaks, all resolve to null, and the caller keeps whatever it had. A missing beat tracker
 * degrades transitions; a throwing one would break playback.
 */
class BeatTracker(private val context: Context) {

    /** A tracked grid on the analysed audio's own timeline, in seconds. */
    data class Grid(
        val beats: List<Double>,
        val downbeats: List<Double>,
        val bpm: Double,
        val beatInterval: Double,
        val firstBeat: Double,
        val beatConfidence: Double,
    )

    @Volatile private var session: OrtSession? = null
    private val lock = Any()

    /** Parsing the graph is far too expensive to repeat per track, so one session is kept. */
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
                    // ORT's arena allocator keeps every block it has ever needed, which for this
                    // graph is tens of megabytes of native heap retained for the life of the
                    // session, far past the model's own size, on a process that also has to
                    // survive in the background. Analysis runs a handful of times per track, so
                    // allocating per run is the right trade.
                    setCPUArenaAllocator(false)
                    setMemoryPatternOptimization(false)
                }
                OrtEnvironment.getEnvironment().createSession(file.absolutePath, options)
                    .also { session = it }
            }.onFailure { Log.w(TAG, "Beat model unavailable; falling back to no grid", it) }
                .getOrNull()
        }
    }

    /**
     * Tracks [pcm], which must already be mono at [MelSpectrogram.sampleRate].
     *
     * [offsetSeconds] is added to every returned time, so a grid tracked on a decoded region maps
     * back onto the full track's timeline rather than starting at zero.
     */
    fun track(pcm: FloatArray, offsetSeconds: Double = 0.0): Grid? {
        val melStarted = System.currentTimeMillis()
        val spectrogram = MelSpectrogram.compute(pcm) ?: return null
        val melMs = System.currentTimeMillis() - melStarted
        val active = session() ?: return null

        val beatLogits = FloatArray(spectrogram.frames)
        val downbeatLogits = FloatArray(spectrogram.frames)
        val inferStarted = System.currentTimeMillis()
        if (!infer(active, spectrogram, beatLogits, downbeatLogits)) return null
        Log.d(
            TAG,
            "mel ${melMs}ms (${spectrogram.frames} frames) " +
                "infer ${System.currentTimeMillis() - inferStarted}ms",
        )

        val fps = MelSpectrogram.frameRate
        val beatFrames = pickPeaks(beatLogits)
        val beats = beatFrames.map { it / fps + offsetSeconds }
        if (beats.size < MIN_BEATS) return null

        val bpm = tempoFromBeats(beats)
        if (bpm <= 0) return null

        // Every downbeat is a beat. The two heads are predicted independently, so their peaks can
        // land a frame apart; snapping each downbeat onto the nearest beat keeps the bar grid a
        // strict subset of the beat grid, which is what the planner assumes when it snaps a
        // transition to a downbeat.
        val downbeats = pickPeaks(downbeatLogits)
            .map { it / fps + offsetSeconds }
            .map { time -> beats.minByOrNull { abs(it - time) } ?: beats.first() }
            .distinct()
            .sorted()

        return Grid(
            beats = beats,
            downbeats = downbeats,
            bpm = bpm,
            beatInterval = 60 / bpm,
            firstBeat = beats.first(),
            beatConfidence = gridConfidence(
                beats,
                beatFrames.map { frame -> beatLogits.getOrElse(frame.roundToInt()) { 0f }.toDouble() },
            ),
        )
    }

    /**
     * Runs the model over the spectrogram in chunks, writing logits into the output arrays.
     *
     * The model reads 1500-frame chunks and has no context at their edges, so [BORDER_FRAMES] are
     * discarded from each side and chunks advance by the difference. The first and last chunk keep
     * their outer border, since there is no neighbouring chunk to supply it.
     */
    @Suppress("UNCHECKED_CAST")
    private fun infer(
        session: OrtSession,
        spectrogram: MelSpectrogram.Spectrogram,
        beatLogits: FloatArray,
        downbeatLogits: FloatArray,
    ): Boolean = runCatching {
        val environment = OrtEnvironment.getEnvironment()
        val mels = spectrogram.mels
        val name = session.inputNames.first()
        val stride = CHUNK_FRAMES - 2 * BORDER_FRAMES

        var start = 0
        while (start < spectrogram.frames) {
            val length = min(CHUNK_FRAMES, spectrogram.frames - start)
            // A chunk shorter than the border padding carries no usable centre.
            if (length <= 2 * BORDER_FRAMES && start > 0) break

            val chunk = spectrogram.values.copyOfRange(start * mels, (start + length) * mels)
            val shape = longArrayOf(1, length.toLong(), mels.toLong())

            OnnxTensor.createTensor(environment, FloatBuffer.wrap(chunk), shape).use { tensor ->
                session.run(mapOf(name to tensor)).use { outputs ->
                    val beat = (outputs.get(0).value as Array<FloatArray>)[0]
                    val downbeat = (outputs.get(1).value as Array<FloatArray>)[0]

                    val keepFrom = if (start == 0) 0 else BORDER_FRAMES
                    val keepTo = if (start + length >= spectrogram.frames) length else length - BORDER_FRAMES
                    for (index in keepFrom until keepTo) {
                        val target = start + index
                        if (target >= beatLogits.size) break
                        beatLogits[target] = beat[index]
                        downbeatLogits[target] = downbeat[index]
                    }
                }
            }

            if (start + length >= spectrogram.frames) break
            start += stride
        }
        true
    }.onFailure { Log.w(TAG, "Beat inference failed", it) }.getOrDefault(false)

    fun release() {
        synchronized(lock) {
            runCatching { session?.close() }
            session = null
        }
    }

    companion object {
        private const val TAG = "BitChordBeatTracker"
        private const val MODEL_ASSET = "beat_this_int8.onnx"
        private const val INFERENCE_THREADS = 4

        /** The window the model was trained on, and the margin discarded from each chunk's edges. */
        const val CHUNK_FRAMES = 1500
        const val BORDER_FRAMES = 6

        /**
         * Window length that costs exactly one inference. A window longer than this splits into
         * two chunks that mostly overlap, paying twice for barely more audio.
         */
        const val WINDOW_SECONDS = (CHUNK_FRAMES - 2 * BORDER_FRAMES) / 50.0

        /** A frame is a beat when it is the maximum of a seven-frame window and its logit positive. */
        private const val PEAK_WINDOW = 7
        private const val MIN_BEATS = 8

        /** Plausible musical tempo, used only to reject a grid the model clearly did not find. */
        private const val MIN_TEMPO = 40.0
        private const val MAX_TEMPO = 220.0

        private fun median(values: List<Double>): Double {
            if (values.isEmpty()) return 0.0
            val sorted = values.sorted()
            return sorted[sorted.size / 2]
        }

        /**
         * Frame indices that are local maxima over [PEAK_WINDOW] and positive, with runs of
         * adjacent peaks collapsed to their mean, then refined to sub-frame resolution.
         *
         * The refinement is not upstream's. The model's frame rate is 50 Hz, so an integer peak
         * quantizes every beat to 20 ms, fine for drawing a grid, but a large share of the flam
         * budget when beat-matching two tracks. Fitting a parabola through the peak and its
         * neighbours recovers where the maximum actually sits.
         */
        fun pickPeaks(logits: FloatArray): List<Double> {
            val half = PEAK_WINDOW / 2
            val peaks = ArrayList<Int>()
            for (index in logits.indices) {
                if (logits[index] <= 0f) continue
                var isMaximum = true
                for (offset in -half..half) {
                    val neighbour = index + offset
                    if (neighbour < 0 || neighbour >= logits.size) continue
                    if (logits[neighbour] > logits[index]) {
                        isMaximum = false
                        break
                    }
                }
                if (isMaximum) peaks += index
            }

            // Collapse adjacent frames that tied for the maximum onto their mean.
            val deduped = ArrayList<Int>()
            var index = 0
            while (index < peaks.size) {
                var mean = peaks[index].toDouble()
                var count = 1
                while (index + 1 < peaks.size && peaks[index + 1] - mean <= 1) {
                    index += 1
                    count += 1
                    mean += (peaks[index] - mean) / count
                }
                deduped += mean.roundToInt()
                index += 1
            }

            return deduped.map { frame ->
                if (frame <= 0 || frame + 1 >= logits.size) return@map frame.toDouble()
                val left = logits[frame - 1].toDouble()
                val centre = logits[frame].toDouble()
                val right = logits[frame + 1].toDouble()
                val denominator = left - 2 * centre + right
                if (abs(denominator) <= 1e-9) return@map frame.toDouble()
                frame + (0.5 * (left - right) / denominator).coerceIn(-0.5, 0.5)
            }
        }

        /** Median inter-beat interval as a tempo, or 0 when it is not a plausible one. */
        fun tempoFromBeats(beats: List<Double>): Double {
            if (beats.size < MIN_BEATS) return 0.0
            val gaps = beats.zipWithNext { left, right -> right - left }
            val rough = median(gaps)
            if (rough <= 0) return 0.0
            // A second pass over gaps close to the first estimate, so a few dropped beats do not
            // drag the interval.
            val kept = gaps.filter { abs(it - rough) <= rough * 0.2 }
            val interval = median(if (kept.size >= 4) kept else gaps)
            if (interval <= 0) return 0.0
            val bpm = 60 / interval
            return if (bpm in MIN_TEMPO..MAX_TEMPO) bpm else 0.0
        }

        /**
         * How far the grid can be trusted, 0..1: regularity of the spacing and decisiveness of
         * the peaks. This is the number the whole transition policy gates on, so it is deliberately
         * capped below 1: a model is evidence, not proof.
         */
        fun gridConfidence(beats: List<Double>, peakLogits: List<Double>): Double {
            if (beats.size < MIN_BEATS) return 0.0
            val gaps = beats.zipWithNext { left, right -> right - left }
            val interval = median(gaps)
            if (interval <= 0) return 0.0

            // Fraction of gaps that are one beat rather than a hole in the grid.
            val regular = gaps.count { abs(it - interval) <= interval * 0.1 }.toDouble() / gaps.size
            // Logits are unbounded; a median around 2 is a decisive peak, around 0 is not.
            val strength = 1 / (1 + exp(-(median(peakLogits) - 0.5)))
            return max(0.0, min(0.95, 0.35 + 0.4 * regular + 0.25 * strength))
        }
    }
}
