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
import java.io.File
import java.nio.FloatBuffer
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
class BeatTracker(
    /**
     * Where the model lives, resolved lazily.
     *
     * A path rather than a [Context]: this is the only thing the tracker ever
     * wanted the platform for, and each application already knows how to put
     * its own copy of the weights somewhere readable — unpacked from assets on
     * Android, from the jar's resources on the desktop.
     */
    private val modelPath: () -> String,
    /** How many threads inference may use; the beat and downbeat pass is the expensive one. */
    private val inferenceThreads: () -> Int,
) {

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
    @Volatile private var sessionThreads = 0
    private val lock = Any()

    /** Parsing the graph is far too expensive to repeat per track, so one session is kept. */
    private fun session(): OrtSession? {
        val threads = inferenceThreads()
        session?.takeIf { sessionThreads == threads }?.let { return it }
        synchronized(lock) {
            session?.takeIf { sessionThreads == threads }?.let { return it }
            runCatching { session?.close() }
            session = null
            return runCatching {
                val file = File(modelPath())
                OrtSession.SessionOptions().use { options ->
                    options.apply {
                        setIntraOpNumThreads(threads)
                        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                        // Do not retain a per-session arena after these occasional analyses.
                        setCPUArenaAllocator(false)
                        setMemoryPatternOptimization(false)
                    }
                    val created = OrtEnvironment.getEnvironment().createSession(file.absolutePath, options)
                    try {
                        AutomixModels.BEAT.validate(created)
                        session = created
                        sessionThreads = threads
                        created
                    } catch (error: Exception) {
                        created.close()
                        throw error
                    }
                }
            }.onFailure { AnalysisLog.warn("Beat model unavailable; falling back to no grid", it) }
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
        if (!offsetSeconds.isFinite()) return null
        val melStarted = System.currentTimeMillis()
        val spectrogram = MelSpectrogram.compute(pcm) ?: return null
        val melMs = System.currentTimeMillis() - melStarted
        val active = session() ?: return null

        val beatLogits = FloatArray(spectrogram.frames)
        val downbeatLogits = FloatArray(spectrogram.frames)
        val inferStarted = System.currentTimeMillis()
        if (!infer(active, spectrogram, beatLogits, downbeatLogits)) return null
        AnalysisLog.warn("mel ${melMs}ms (${spectrogram.frames} frames) " +
                "infer ${System.currentTimeMillis() - inferStarted}ms",
        )

        val fps = MelSpectrogram.frameRate
        val beatFrames = pickPeaks(beatLogits)
        val beats = beatFrames.map { it / fps + offsetSeconds }
        if (beats.size < MIN_BEATS) return null

        val bpm = tempoFromBeats(beats)
        if (bpm <= 0) return null

        // Independent heads can land a frame apart. Snap only nearby predictions;
        // a stray downbeat in a beatless break must not invent a bar boundary.
        val downbeats = BeatDetection.snapDownbeats(
            beats, pickPeaks(downbeatLogits).map { it / fps + offsetSeconds },
        )

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
     * Match upstream split_piece/aggregate_prediction: pad the outer borders, discard all
     * untrained edge predictions, shift the final chunk left and keep the first overlap.
     */
    private fun infer(
        session: OrtSession,
        spectrogram: MelSpectrogram.Spectrogram,
        beatLogits: FloatArray,
        downbeatLogits: FloatArray,
    ): Boolean = runCatching {
        val environment = OrtEnvironment.getEnvironment()
        val mels = spectrogram.mels
        var writtenUntil = 0
        for ((start, length) in BeatDetection.chunks(spectrogram.frames)) {
            val chunk = FloatArray(length * mels)
            val sourceFrom = max(0, start)
            val sourceTo = min(spectrogram.frames, start + length)
            spectrogram.values.copyInto(chunk, (sourceFrom - start) * mels, sourceFrom * mels, sourceTo * mels)
            val shape = longArrayOf(1, length.toLong(), mels.toLong())

            OnnxTensor.createTensor(environment, FloatBuffer.wrap(chunk), shape).use { tensor ->
                session.run(mapOf(AutomixModels.BEAT.input to tensor)).use { outputs ->
                    val beatTensor = outputs.get("beat").get() as OnnxTensor
                    val downbeatTensor = outputs.get("downbeat").get() as OnnxTensor
                    val outputShape = longArrayOf(1, length.toLong())
                    require(beatTensor.info.shape.contentEquals(outputShape) && downbeatTensor.info.shape.contentEquals(outputShape)) {
                        "Unexpected beat output shape"
                    }
                    val beat = beatTensor.floatBuffer
                    val downbeat = downbeatTensor.floatBuffer
                    require(beat.limit() == length && downbeat.limit() == length) { "Unexpected beat output length" }
                    for (index in 0 until length) {
                        require(beat.get(index).isFinite() && downbeat.get(index).isFinite()) { "Non-finite beat output" }
                    }
                    val keepFrom = max(BORDER_FRAMES, writtenUntil - start)
                    val keepTo = min(length - BORDER_FRAMES, spectrogram.frames - start)
                    for (index in keepFrom until keepTo) {
                        val target = start + index
                        beatLogits[target] = beat.get(index)
                        downbeatLogits[target] = downbeat.get(index)
                    }
                    writtenUntil = max(writtenUntil, start + keepTo)
                }
            }
        }
        writtenUntil == spectrogram.frames
    }.onFailure { AnalysisLog.warn("Beat inference failed", it) }.getOrDefault(false)

    fun release() {
        synchronized(lock) {
            runCatching { session?.close() }
            session = null
            sessionThreads = 0
        }
    }

    companion object {
        /**
         * The weights this tracker needs, named once for every build that ships them.
         *
         * The official final0 checkpoint with only its MatMuls quantized (int8, per channel); the
         * convolutions stay float. The earlier `beat_this_int8.onnx` quantized the convolutions
         * too and found no beats at all. A new name rather than the old one because the app
         * unpacks assets once by name and would keep serving the stale copy.
         */
        const val MODEL_ASSET = AutomixModels.BEAT_ASSET

        /** The broken earlier export, whose unpacked copy is deleted on first use of the new one. */
        const val RETIRED_MODEL_ASSET = "beat_this_int8.onnx"
        /** The window the model was trained on, and the margin discarded from each chunk's edges. */
        const val CHUNK_FRAMES = BeatDetection.CHUNK_FRAMES
        const val BORDER_FRAMES = BeatDetection.BORDER_FRAMES

        /**
         * Window length that costs exactly one inference. A window longer than this splits into
         * two chunks that mostly overlap, paying twice for barely more audio.
         */
        // A centered STFT adds one frame; reserve it as well as the two borders.
        const val WINDOW_SECONDS = (CHUNK_FRAMES - 2 * BORDER_FRAMES - 1) / BeatDetection.FRAME_RATE

        /** Enough observations to estimate a musical tempo. */
        private const val MIN_BEATS = BeatDetection.MIN_BEATS

        /**
         * Frame indices that are local maxima over seven frames and positive, with runs of
         * adjacent peaks collapsed to their mean, then refined to sub-frame resolution.
         *
         * The refinement is not upstream's. The model's frame rate is 50 Hz, so an integer peak
         * quantizes every beat to 20 ms, fine for drawing a grid, but a large share of the flam
         * budget when beat-matching two tracks. Fitting a parabola through the peak and its
         * neighbours recovers where the maximum actually sits.
         */
        fun pickPeaks(logits: FloatArray): List<Double> = BeatDetection.pickPeaks(logits)

        /** Median inter-beat interval as a tempo, or 0 when it is not a plausible one. */
        fun tempoFromBeats(beats: List<Double>): Double = BeatDetection.tempoFromBeats(beats)

        /**
         * How far the grid can be trusted, 0..1: regularity of the spacing and decisiveness of
         * the peaks. This is the number the whole transition policy gates on, so it is deliberately
         * capped below 1: a model is evidence, not proof.
         */
        fun gridConfidence(beats: List<Double>, peakLogits: List<Double>): Double =
            BeatDetection.gridConfidence(beats, peakLogits)
    }
}
