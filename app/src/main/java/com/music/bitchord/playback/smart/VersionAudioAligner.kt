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
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

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
     */
    suspend fun findOffsetMs(
        context: Context,
        sourceSong: Song,
        targetSong: Song,
        currentPosMs: Long = 0L,
    ): Long = withContext(Dispatchers.IO) {
        if (sourceSong.videoId == targetSong.videoId) return@withContext 0L
        val cached = offsetCache[sourceSong.videoId to targetSong.videoId]
        if (cached != null) return@withContext cached

        val sourceUri = Uri.parse(sourceSong.localUri ?: "bitchord://watch?v=${sourceSong.videoId}")
        val targetUri = Uri.parse(targetSong.localUri ?: "bitchord://watch?v=${targetSong.videoId}")

        val sourceDs = openDataSource(context, sourceUri)
        val targetDs = openDataSource(context, targetUri)

        if (sourceDs == null || targetDs == null) {
            safeClose(sourceDs)
            safeClose(targetDs)
            return@withContext 0L
        }

        try {
            // Decode 45 seconds of head audio from both tracks
            val sourceDecode = AudioDecoder.decodeRegion(sourceDs, 0.0, 45.0)
            val targetDecode = AudioDecoder.decodeRegion(targetDs, 0.0, 45.0)

            if (sourceDecode == null || targetDecode == null) {
                return@withContext 0L
            }

            val (pcmSource, startSourceSec) = sourceDecode
            val (pcmTarget, startTargetSec) = targetDecode

            if (pcmSource.samples.isEmpty() || pcmTarget.samples.isEmpty()) {
                return@withContext 0L
            }

            val envSource = computeEnergyEnvelope(pcmSource.samples, pcmSource.sampleRate)
            val envTarget = computeEnergyEnvelope(pcmTarget.samples, pcmTarget.sampleRate)

            val (bestLagFrames, confidence) = crossCorrelate(envSource, envTarget)
            TrackLog.d(
                TAG,
                "Alignment for '${sourceSong.title}' -> '${targetSong.title}': lagFrames=$bestLagFrames, conf=$confidence",
                sourceSong.videoId,
            )

            if (confidence >= MIN_CONFIDENCE) {
                val lagMs = (bestLagFrames * HOP_MS).toLong()
                val startDiffMs = ((startTargetSec - startSourceSec) * 1000.0).toLong()
                val totalOffsetMs = lagMs + startDiffMs

                offsetCache[sourceSong.videoId to targetSong.videoId] = totalOffsetMs
                offsetCache[targetSong.videoId to sourceSong.videoId] = -totalOffsetMs
                return@withContext totalOffsetMs
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to compute audio alignment for ${sourceSong.videoId} -> ${targetSong.videoId}", e)
        } finally {
            safeClose(sourceDs)
            safeClose(targetDs)
        }

        0L
    }

    private fun openDataSource(context: Context, uri: Uri): MediaDataSource? {
        return if (LocalAudioSource.isLocal(uri)) {
            LocalAudioSource.open(context.contentResolver, uri)
        } else {
            AudioCache.mediaDataSource(uri) ?: AudioCache.headMediaDataSource(uri)
        }
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
     * Normalized cross-correlation between source envelope and target envelope.
     * Returns Pair(bestLagInFrames, confidence).
     * Positive lag means target event occurs later than source event (target = source + lag).
     */
    private fun crossCorrelate(
        sourceEnv: FloatArray,
        targetEnv: FloatArray,
        hopMs: Double = HOP_MS,
        maxLagSec: Double = MAX_LAG_SECONDS,
    ): Pair<Double, Double> {
        if (sourceEnv.isEmpty() || targetEnv.isEmpty()) return 0.0 to 0.0

        val maxLagFrames = (maxLagSec * 1000.0 / hopMs).toInt()
        val minOverlapFrames = (3.0 * 1000.0 / hopMs).toInt() // At least 3 seconds overlap

        var bestLag = 0
        var bestCorrelation = -1.0

        // lag d means targetEnv[i + d] corresponds to sourceEnv[i]
        val minLag = max(-maxLagFrames, -(sourceEnv.size - minOverlapFrames))
        val maxLag = min(maxLagFrames, targetEnv.size - minOverlapFrames)

        for (lag in minLag..maxLag) {
            val srcStart = max(0, -lag)
            val tgtStart = max(0, lag)
            val overlapLen = min(sourceEnv.size - srcStart, targetEnv.size - tgtStart)
            if (overlapLen < minOverlapFrames) continue

            var sumSrc = 0.0
            var sumTgt = 0.0
            for (k in 0 until overlapLen) {
                sumSrc += sourceEnv[srcStart + k]
                sumTgt += targetEnv[tgtStart + k]
            }
            val meanSrc = sumSrc / overlapLen
            val meanTgt = sumTgt / overlapLen

            var num = 0.0
            var denSrc = 0.0
            var denTgt = 0.0
            for (k in 0 until overlapLen) {
                val diffSrc = sourceEnv[srcStart + k] - meanSrc
                val diffTgt = targetEnv[tgtStart + k] - meanTgt
                num += diffSrc * diffTgt
                denSrc += diffSrc * diffSrc
                denTgt += diffTgt * diffTgt
            }

            val denom = sqrt(denSrc * denTgt)
            if (denom > 1e-6) {
                val r = num / denom
                if (r > bestCorrelation) {
                    bestCorrelation = r
                    bestLag = lag
                }
            }
        }

        return bestLag.toDouble() to bestCorrelation
    }
}
