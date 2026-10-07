package com.music.bitchord.playback.smart

import android.content.Context
import android.media.MediaDataSource
import android.net.Uri
import android.util.Log
import androidx.media3.common.util.UnstableApi
import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.AudioCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.system.measureTimeMillis

/**
 * Analyzes audio waveforms/energy envelopes to find the exact time alignment
 * offset between two versions of the same track (e.g. YouTube music video with intro
 * skit vs studio catalogue audio release).
 */
@UnstableApi
object VersionAudioAligner {

    private const val TAG = "VersionAudioAligner"
    private const val HOP_MS = 10.0 // 100 frames per second (10ms resolution)
    private const val WIN_MS = 40.0 // 40ms analysis window
    private const val MAX_LAG_SECONDS = 40.0 // Search up to ±40s drift/intro
    private const val MIN_CONFIDENCE = 0.40 // Minimum normalized correlation

    // Cache computed offsets so subsequent switches are instantaneous:
    // (sourceVideoId, targetVideoId) -> offsetMs
    private val offsetCache = ConcurrentHashMap<Pair<String, String>, Long>()

    /** How far a switch to the other cut has got; published for stats for nerds. */
    enum class CutPhase { FETCHING, MEASURING, ALIGNED, FAILED }

    /**
     * What the switch to the other cut currently knows — the three things a
     * listener actually wants confirmation of: that the other version has been
     * *fetched*, that it has been *measured*, and what shift the transition is
     * going to apply.
     *
     * Kept after the swap lands rather than cleared with it, because the shift
     * is only readable once the cut it describes is the one playing. Scoped to
     * the pair: [NowPlayingScreen] ignores a record naming a track the player
     * is nowhere near, so yesterday's measurement never describes today's song.
     */
    data class AlignmentStatus(
        /** The cut playing when the switch started. */
        val sourceId: String,
        /** The cut being switched to. */
        val targetId: String,
        val phase: CutPhase,
        /** The shift that will be — or was — applied, once it is known. */
        val offsetMs: Long? = null,
        /** True when the figure came out of the cache instead of a fresh pass. */
        val fromCache: Boolean = false,
    )

    val status = MutableStateFlow<AlignmentStatus?>(null)

    /**
     * Names one run of a switch, so the phases that follow can be told apart
     * from the next switch's.
     *
     * A switch is cancelled the moment a second one outranks it, but only at
     * its next suspension point — the statement a cancelled pass is standing on
     * when the new one takes over still runs. Without this, the pass that had
     * already lost would publish [CutPhase.MEASURING] on top of the
     * replacement's [CutPhase.FETCHING], and the status would describe two
     * switches at once.
     */
    private val attempt = AtomicLong()

    /** Opens a switch's status and reports where it starts: fetching. */
    fun beginAttempt(sourceId: String, targetId: String): Long {
        val token = attempt.incrementAndGet()
        status.value = AlignmentStatus(sourceId, targetId, CutPhase.FETCHING)
        return token
    }

    /**
     * Moves the attempt named by [token] on to [phase], and does nothing if a
     * later attempt has taken over since — see [attempt].
     *
     * Non-suspending for the same reason: this is called from a coroutine that
     * can already be cancelled, and a suspending publish would have to survive
     * that to record a phase.
     */
    fun publishPhase(
        token: Long,
        phase: CutPhase,
        offsetMs: Long? = null,
        fromCache: Boolean = false,
    ) {
        if (attempt.get() != token) return
        val current = status.value ?: return
        status.value = current.copy(phase = phase, offsetMs = offsetMs, fromCache = fromCache)
    }

    /**
     * Returns cached offset in milliseconds if already computed, or null if unknown.
     */
    fun getCachedOffsetMs(sourceVideoId: String, targetVideoId: String): Long? {
        if (sourceVideoId == targetVideoId) return 0L
        return offsetCache[sourceVideoId to targetVideoId]
    }

    /**
     * Calculates or retrieves the time offset (in milliseconds) from [sourceSong] to [targetSong].
     * Target timestamp = (source timestamp + offsetMs).
     *
     * **null means it could not be measured** — no bytes on disk, a container
     * the decoder would not open, a correlation too weak to believe. Callers
     * must not fold that into `0`, which is a real answer meaning the two cuts
     * open on the same second; a silent zero is how a thirty-second difference
     * ends up as a swap that lands exactly where the other version left it,
     * with nothing on screen to say the measurement never happened.
     */
    suspend fun findOffsetMs(
        context: Context,
        sourceSong: Song,
        targetSong: Song,
        currentPosMs: Long = 0L,
    ): Long? = withContext(Dispatchers.IO) {
        if (sourceSong.videoId == targetSong.videoId) return@withContext 0L
        val cached = offsetCache[sourceSong.videoId to targetSong.videoId]
        if (cached != null) return@withContext cached

        val sourceDs = openDataSource(context, sourceSong)
        val targetDs = openDataSource(context, targetSong)

        if (sourceDs == null || targetDs == null) {
            safeClose(sourceDs)
            safeClose(targetDs)
            TrackLog.w(
                TAG,
                "Alignment for '${sourceSong.title}': no bytes on disk to measure against",
                sourceSong.videoId,
            )
            return@withContext null
        }

        try {
            // Both decodes at once rather than one after the other, and every
            // phase timed: the measurement runs against a deadline (see
            // [PlaybackService]'s ALIGNMENT_MEASURE_TIMEOUT_MS), and a result
            // arriving past it is discarded as if it had never happened — the
            // swap then goes out with no shift even though the offset was
            // computed. The field log caught exactly that race, with the
            // correlation succeeding 10 ms over a tighter budget.
            var decodes: Pair<Pair<AudioDecoder.Pcm, Double>?, Pair<AudioDecoder.Pcm, Double>?> =
                null to null
            val decodeMs = measureTimeMillis {
                decodes = coroutineScope {
                    val source = async(Dispatchers.IO) {
                        AudioDecoder.decodeRegion(sourceDs, 0.0, 45.0)
                    }
                    val target = async(Dispatchers.IO) {
                        AudioDecoder.decodeRegion(targetDs, 0.0, 45.0)
                    }
                    source.await() to target.await()
                }
            }
            val (sourceDecode, targetDecode) = decodes

            if (sourceDecode == null || targetDecode == null) {
                return@withContext null
            }

            val (pcmSource, startSourceSec) = sourceDecode
            val (pcmTarget, startTargetSec) = targetDecode

            if (pcmSource.samples.isEmpty() || pcmTarget.samples.isEmpty()) {
                return@withContext null
            }

            var envelopes: Pair<FloatArray, FloatArray> = FloatArray(0) to FloatArray(0)
            val envelopeMs = measureTimeMillis {
                envelopes = computeEnergyEnvelope(pcmSource.samples, pcmSource.sampleRate) to
                    computeEnergyEnvelope(pcmTarget.samples, pcmTarget.sampleRate)
            }

            var correlation: Correlation = Correlation(0, 0.0, 0, 0)
            val correlateMs = measureTimeMillis {
                correlation = crossCorrelate(envelopes.first, envelopes.second)
            }
            TrackLog.d(
                TAG,
                "Alignment timings for '${sourceSong.title}': decode=${decodeMs}ms, " +
                    "envelope=${envelopeMs}ms, correlate=${correlateMs}ms",
                sourceSong.videoId,
            )
            val bestLagFrames = correlation.lagFrames
            val confidence = correlation.confidence
            TrackLog.d(
                TAG,
                "Alignment for '${sourceSong.title}' -> '${targetSong.title}': lagFrames=$bestLagFrames, conf=$confidence, range=${correlation.floor}..${correlation.ceiling}",
                sourceSong.videoId,
            )

            if (confidence >= MIN_CONFIDENCE) {
                // A peak sitting on the edge of what the window could search
                // means the true lag is further out than these bytes reach —
                // which is what a truncated head looks like from here. The
                // eight-second head that a half-finished fetch leaves decodes
                // to a five-second range, finds its best match pinned to it,
                // and would report that as measured: a swap landing a handful
                // of seconds off while claiming thirty. Refuse instead.
                val floor = correlation.floor
                val ceiling = correlation.ceiling
                if (bestLagFrames == floor || bestLagFrames == ceiling) {
                    TrackLog.w(
                        TAG,
                        "Alignment for '${sourceSong.title}' -> '${targetSong.title}': peak pinned to the searchable edge ($bestLagFrames of $floor..$ceiling) — window too short to trust",
                        sourceSong.videoId,
                    )
                    return@withContext null
                }

                val lagMs = (bestLagFrames * HOP_MS).toLong()
                val startDiffMs = ((startTargetSec - startSourceSec) * 1000.0).toLong()
                val totalOffsetMs = lagMs + startDiffMs

                offsetCache[sourceSong.videoId to targetSong.videoId] = totalOffsetMs
                offsetCache[targetSong.videoId to sourceSong.videoId] = -totalOffsetMs
                return@withContext totalOffsetMs
            }

            TrackLog.w(
                TAG,
                "Alignment for '${sourceSong.title}' -> '${targetSong.title}': confidence $confidence below $MIN_CONFIDENCE — no shift measured",
                sourceSong.videoId,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to compute audio alignment for ${sourceSong.videoId} -> ${targetSong.videoId}", e)
        } finally {
            safeClose(sourceDs)
            safeClose(targetDs)
        }

        null
    }

    /**
     * Opens the copy of [song] worth measuring: the largest same-source
     * opening on disk, and only a substitute (`#alt`) when nothing else is
     * there at all.
     *
     * Ordered by bytes rather than by whichever URI answers first, because
     * neither URI names a particular copy — the live one resolves to whatever
     * rendition the player is streaming *now*, and the analysis copy under the
     * plain id can be a stale stub while a sibling key holds the whole
     * opening. A stub hands the decoder a second of audio, and a second of
     * audio can only correlate to garbage, which is how every switch measured
     * against one came out "failed". [AudioCache] asks the same question in
     * the same order when it declares the head ready, so what the wait calls
     * ready is what gets opened here.
     */
    private fun openDataSource(context: Context, song: Song): MediaDataSource? {
        song.localUri?.let { local ->
            val uri = Uri.parse(local)
            if (LocalAudioSource.isLocal(uri)) {
                return LocalAudioSource.open(context.contentResolver, uri)
            }
        }
        val watch = Uri.parse("bitchord://watch?v=${song.videoId}")
        val onDisk = AudioCache.renditionsOf(watch)
        val sameSource = onDisk
            .filter { !it.key.endsWith("#alt") }
            .maxByOrNull { it.cachedPrefix }
        val pick = if (sameSource != null &&
            sameSource.cachedPrefix >= AudioCache.MIN_ALIGNMENT_PREFIX_BYTES
        ) {
            sameSource
        } else {
            onDisk.maxByOrNull { it.cachedPrefix }
        } ?: return null
        return AudioCache.renditionDataSource(watch, pick)
    }

    private fun safeClose(ds: MediaDataSource?) {
        if (ds != null) {
            runCatching { ds.close() }
        }
    }

    /**
     * Computes RMS energy envelope over sliding windows.
     */
    private fun computeEnergyEnvelope(
        samples: FloatArray,
        sampleRate: Double,
        hopMs: Double = HOP_MS,
        winMs: Double = WIN_MS,
    ): FloatArray {
        val hopSamples = max(1, (sampleRate * (hopMs / 1000.0)).toInt())
        val winSamples = max(hopSamples, (sampleRate * (winMs / 1000.0)).toInt())
        val numFrames = max(0, (samples.size - winSamples) / hopSamples)
        if (numFrames <= 0) return FloatArray(0)

        val env = FloatArray(numFrames)
        for (i in 0 until numFrames) {
            val start = i * hopSamples
            val end = min(samples.size, start + winSamples)
            var sumSq = 0.0
            for (j in start until end) {
                val s = samples[j]
                sumSq += s * s
            }
            env[i] = sqrt(sumSq / (end - start)).toFloat()
        }
        return env
    }

    /**
     * A peak found by [crossCorrelate], together with the range it was found in.
     *
     * [floor] and [ceiling] are carried out of the search rather than left
     * there because they are the only way to tell a measured lag from a lag the
     * window was too small to move past — see [findOffsetMs].
     */
    data class Correlation(val lagFrames: Int, val confidence: Double, val floor: Int, val ceiling: Int)

    /**
     * Normalized cross-correlation between source envelope and target envelope.
     * Returns the best lag in frames, its confidence, and the lag range searched.
     * Positive lag means target event occurs later than source event (target = source + lag).
     *
     * Same numbers as the brute-force double loop this replaced — per-lag
     * means and variances from prefix sums, cross terms from one FFT pass —
     * but the cost drops from ~70M multiply-adds per pair of 45 s windows
     * (which on a cold device ran long enough for the measurement to blow its
     * whole deadline *after* finding the answer) to two O(n log n) transforms
     * and a linear scan over the lags.
     */
    fun crossCorrelate(
        sourceEnv: FloatArray,
        targetEnv: FloatArray,
        hopMs: Double = HOP_MS,
        maxLagSec: Double = MAX_LAG_SECONDS,
    ): Correlation {
        if (sourceEnv.isEmpty() || targetEnv.isEmpty()) return Correlation(0, 0.0, 0, 0)

        val maxLagFrames = (maxLagSec * 1000.0 / hopMs).toInt()
        val minOverlapFrames = (3.0 * 1000.0 / hopMs).toInt() // At least 3 seconds overlap

        var bestLag = 0
        var bestCorrelation = -1.0

        // lag d means targetEnv[i + d] corresponds to sourceEnv[i]
        val minLag = max(-maxLagFrames, -(sourceEnv.size - minOverlapFrames))
        val maxLag = min(maxLagFrames, targetEnv.size - minOverlapFrames)

        val crossTerms = crossTermsByLag(sourceEnv, targetEnv)

        val srcPrefix = DoubleArray(sourceEnv.size + 1)
        val srcSqPrefix = DoubleArray(sourceEnv.size + 1)
        for (i in sourceEnv.indices) {
            val v = sourceEnv[i].toDouble()
            srcPrefix[i + 1] = srcPrefix[i] + v
            srcSqPrefix[i + 1] = srcSqPrefix[i] + v * v
        }
        val tgtPrefix = DoubleArray(targetEnv.size + 1)
        val tgtSqPrefix = DoubleArray(targetEnv.size + 1)
        for (i in targetEnv.indices) {
            val v = targetEnv[i].toDouble()
            tgtPrefix[i + 1] = tgtPrefix[i] + v
            tgtSqPrefix[i + 1] = tgtSqPrefix[i] + v * v
        }

        for (lag in minLag..maxLag) {
            val srcStart = max(0, -lag)
            val tgtStart = max(0, lag)
            val overlapLen = min(sourceEnv.size - srcStart, targetEnv.size - tgtStart)
            if (overlapLen < minOverlapFrames) continue

            val sumSrc = srcPrefix[srcStart + overlapLen] - srcPrefix[srcStart]
            val sumTgt = tgtPrefix[tgtStart + overlapLen] - tgtPrefix[tgtStart]
            val sumSrcSq = srcSqPrefix[srcStart + overlapLen] - srcSqPrefix[srcStart]
            val sumTgtSq = tgtSqPrefix[tgtStart + overlapLen] - tgtSqPrefix[tgtStart]

            // The FFT hands back the raw Σx·y; the numerator the denominator's
            // variances expect is the centered one:
            // Σ(x−x̄)(y−ȳ) = Σxy − (Σx)(Σy)/n. Without this the confidence is
            // uncentered and can exceed 1 — a meaningless threshold.
            val num = crossTerms[lag + sourceEnv.size - 1] - sumSrc * sumTgt / overlapLen
            val denSrc = sumSrcSq - sumSrc * sumSrc / overlapLen
            val denTgt = sumTgtSq - sumTgt * sumTgt / overlapLen

            val denom = sqrt(denSrc * denTgt)
            if (denom > 1e-6) {
                val r = num / denom
                if (r > bestCorrelation) {
                    bestCorrelation = r
                    bestLag = lag
                }
            }
        }

        return Correlation(bestLag, bestCorrelation, minLag, maxLag)
    }

    /**
     * Σ source[i]·target[i+lag] for every lag the search may ask about,
     * indexed as `result[lag + source.size - 1]`.
     *
     * One zero-padded FFT pair does what the inner loop used to: the
     * cross-correlation theorem turns the per-lag sums into
     * `IFFT(conj(FFT(source)) · FFT(target))`, and padding to at least
     * source + target samples keeps the circular wrap out of the linear range
     * −(n−1)…(m−1), which covers any windowed lag with overlap.
     */
    private fun crossTermsByLag(source: FloatArray, target: FloatArray): DoubleArray {
        val n = source.size
        val m = target.size
        var fftLen = 1
        while (fftLen < n + m) fftLen = fftLen shl 1

        val srcRe = DoubleArray(fftLen)
        val srcIm = DoubleArray(fftLen)
        for (i in 0 until n) srcRe[i] = source[i].toDouble()
        val tgtRe = DoubleArray(fftLen)
        val tgtIm = DoubleArray(fftLen)
        for (i in 0 until m) tgtRe[i] = target[i].toDouble()

        fft(srcRe, srcIm)
        fft(tgtRe, tgtIm)

        // conj(X) · Y — the transform of the cross-correlation rather than the
        // convolution: the conjugate is what keeps the lag sign as written.
        for (i in 0 until fftLen) {
            val a = srcRe[i]
            val b = -srcIm[i]
            val c = tgtRe[i]
            val d = tgtIm[i]
            srcRe[i] = a * c - b * d
            srcIm[i] = a * d + b * c
        }
        fft(srcRe, srcIm, inverse = true)

        val result = DoubleArray(n + m - 1)
        for (lag in -(n - 1) until m) {
            val bin = ((lag % fftLen) + fftLen) % fftLen
            result[lag + n - 1] = srcRe[bin]
        }
        return result
    }

    /**
     * In-place iterative radix-2 FFT; the arrays' length must be a power of
     * two. [inverse] divides by the length on the way out.
     */
    private fun fft(re: DoubleArray, im: DoubleArray, inverse: Boolean = false) {
        val n = re.size

        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }

        var len = 2
        while (len <= n) {
            val angle = 2.0 * PI / len * (if (inverse) 1.0 else -1.0)
            val stepRe = cos(angle)
            val stepIm = sin(angle)
            for (start in 0 until n step len) {
                var twiddleRe = 1.0
                var twiddleIm = 0.0
                for (k in 0 until len / 2) {
                    val evenRe = re[start + k]
                    val evenIm = im[start + k]
                    val oddRe =
                        re[start + k + len / 2] * twiddleRe - im[start + k + len / 2] * twiddleIm
                    val oddIm =
                        re[start + k + len / 2] * twiddleIm + im[start + k + len / 2] * twiddleRe
                    re[start + k] = evenRe + oddRe
                    im[start + k] = evenIm + oddIm
                    re[start + k + len / 2] = evenRe - oddRe
                    im[start + k + len / 2] = evenIm - oddIm
                    val nextRe = twiddleRe * stepRe - twiddleIm * stepIm
                    twiddleIm = twiddleRe * stepIm + twiddleIm * stepRe
                    twiddleRe = nextRe
                }
            }
            len = len shl 1
        }

        if (inverse) {
            for (i in 0 until n) {
                re[i] /= n
                im[i] /= n
            }
        }
    }
}
