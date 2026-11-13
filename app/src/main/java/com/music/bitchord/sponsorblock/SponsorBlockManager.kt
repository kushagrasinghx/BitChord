package com.music.bitchord.sponsorblock

import android.util.Log
import androidx.media3.common.Player
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Skips non-music segments (sponsors, intros, off-topic bits, ...) of the
 * current track while "Skip silence & non-music" is on.
 */
object SponsorBlockManager {

    private const val TAG = "SponsorBlock"
    private const val TICKER_INTERVAL_MS = 300L
    private const val SKIP_LEAD_MS = 30L
    private const val REPEAT_GUARD_MS = 1500L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var currentVideoId: String? = null
    private var isVideoContent = false
    private var activeSegments: List<SponsorSegment> = emptyList()
    private var fetchJob: Job? = null
    private var tickerJob: Job? = null
    private var lastSkippedUuid: String? = null
    private var lastSkipTimeMs = 0L

    private fun enabled() = AppSettings.skipSilence.value

    /** The active track changed; fetches its segments. */
    fun onTrackChanged(videoId: String?, isVideo: Boolean = false, playerProvider: () -> Player?) {
        fetchJob?.cancel()
        activeSegments = emptyList()
        lastSkippedUuid = null

        val id = videoId?.trim().orEmpty()
        if (id.length != 11 || '/' in id || ':' in id) {
            currentVideoId = null
            return
        }
        currentVideoId = id
        isVideoContent = isVideo
        if (!enabled()) return

        fetchJob = scope.launch {
            val segments = SponsorBlockClient.getSegments(id)
            if (currentVideoId == id) {
                activeSegments = segments
                playerProvider()?.takeIf { it.isPlaying }?.let { p ->
                    checkAndSkip(p.currentPosition) { p.seekTo(it) }
                }
            }
        }
    }

    /** Starts or stops the position ticker with playback. */
    fun onIsPlayingChanged(isPlaying: Boolean, playerProvider: () -> Player?) {
        tickerJob?.cancel()
        tickerJob = null
        if (!isPlaying || !enabled()) return
        tickerJob = scope.launch {
            while (isActive) {
                val p = playerProvider()
                if (p == null || !p.isPlaying) break
                checkAndSkip(p.currentPosition) { p.seekTo(it) }
                delay(TICKER_INTERVAL_MS)
            }
        }
    }

    /** The setting was toggled. */
    fun onEnabledChanged(on: Boolean, playerProvider: () -> Player?) {
        if (!on) {
            fetchJob?.cancel()
            tickerJob?.cancel()
            tickerJob = null
            return
        }
        if (currentVideoId != null && activeSegments.isEmpty()) {
            onTrackChanged(currentVideoId, isVideoContent, playerProvider)
        }
        if (playerProvider()?.isPlaying == true) onIsPlayingChanged(true, playerProvider)
    }

    /** Seeks past the segment containing [currentPositionMs], if any. */
    fun checkAndSkip(currentPositionMs: Long, seekTo: (targetMs: Long) -> Unit) {
        if (!enabled() || activeSegments.isEmpty()) return
        val segment = activeSegments.firstOrNull { it.contains(currentPositionMs) } ?: return
        val now = System.currentTimeMillis()
        if (lastSkippedUuid == segment.uuid && abs(now - lastSkipTimeMs) < REPEAT_GUARD_MS) return

        lastSkippedUuid = segment.uuid
        lastSkipTimeMs = now
        Log.i(TAG, "Skipping ${segment.category.key} ${segment.startMs}-${segment.endMs}ms")
        seekTo(segment.endMs + SKIP_LEAD_MS)
    }

    fun reset() {
        fetchJob?.cancel()
        tickerJob?.cancel()
        fetchJob = null
        tickerJob = null
        currentVideoId = null
        activeSegments = emptyList()
        lastSkippedUuid = null
    }

    internal fun setSegmentsForTest(videoId: String, segments: List<SponsorSegment>) {
        currentVideoId = videoId
        activeSegments = segments
        lastSkippedUuid = null
    }
}
