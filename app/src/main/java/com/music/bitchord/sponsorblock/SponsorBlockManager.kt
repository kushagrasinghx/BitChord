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
    private const val RESTART_THRESHOLD_MS = 1000L
    private val YOUTUBE_ID_REGEX = Regex("^[a-zA-Z0-9_-]{11}$")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var currentVideoId: String? = null
    private var isVideoContent = false
    private var activeSegments: List<SponsorSegment> = emptyList()
    private var fetchJob: Job? = null
    private var tickerJob: Job? = null
    private var lastSkippedUuid: String? = null
    private var lastSkipTimeMs = 0L
    private var userWhitelistedUuid: String? = null
    private var pendingSkipTargetMs: Long? = null

    private fun enabled() = AppSettings.skipSilence.value
    private fun nowMs(): Long = System.nanoTime() / 1_000_000L

    /** The active track changed; fetches its segments. */
    fun onTrackChanged(videoId: String?, isVideo: Boolean = false, playerProvider: () -> Player?) {
        fetchJob?.cancel()
        activeSegments = emptyList()
        lastSkippedUuid = null
        userWhitelistedUuid = null
        pendingSkipTargetMs = null

        val id = videoId?.trim().orEmpty()
        if (!YOUTUBE_ID_REGEX.matches(id)) {
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
            activeSegments = emptyList()
            userWhitelistedUuid = null
            pendingSkipTargetMs = null
            return
        }
        if (currentVideoId != null && activeSegments.isEmpty()) {
            onTrackChanged(currentVideoId, isVideoContent, playerProvider)
        }
        if (playerProvider()?.isPlaying == true) onIsPlayingChanged(true, playerProvider)
    }

    /**
     * Called when the user manually seeks in playback.
     *
     * Rule 1 (Restart from Beginning): If seeking to the very start (< 1000ms),
     * the user intended to replay the song from start. Any starting segment is NOT
     * whitelisted and immediately skipped past, taking [seekTo] target.
     *
     * Rule 2 (Interior Seek): If the user specifically scrubbed past 1000ms into the
     * middle of a segment, they deliberately wanted to hear that section. That segment
     * is whitelisted and played without auto-skipping.
     */
    fun onUserSeek(positionMs: Long, seekTo: ((targetMs: Long) -> Unit)? = null) {
        if (!enabled()) return

        // If this seek matches our own pending programmatic skip landing, clear the pending
        // target and immediately check for any chained segments without whitelisting.
        val pending = pendingSkipTargetMs
        if (pending != null) {
            if (abs(positionMs - pending) < 1500L) {
                pendingSkipTargetMs = null
                seekTo?.let { checkAndSkip(positionMs, it) }
                return
            }
            pendingSkipTargetMs = null
        }

        val segment = activeSegments.firstOrNull {
            it.contains(positionMs) ||
                (positionMs < RESTART_THRESHOLD_MS && it.startMs < RESTART_THRESHOLD_MS && it.endMs > positionMs)
        }
        if (segment == null) {
            userWhitelistedUuid = null
            return
        }

        if (positionMs < RESTART_THRESHOLD_MS && segment.startMs < RESTART_THRESHOLD_MS) {
            // Rule 1: User tapped Previous or dragged to start -> replay song, don't whitelist
            userWhitelistedUuid = null
            lastSkippedUuid = segment.uuid
            lastSkipTimeMs = nowMs()
            val target = segment.endMs + SKIP_LEAD_MS
            pendingSkipTargetMs = target
            seekTo?.invoke(target)
        } else {
            // Rule 2: Deliberate interior seek into the segment
            userWhitelistedUuid = segment.uuid
        }
    }

    /** Seeks past the segment containing [currentPositionMs], if any. */
    fun checkAndSkip(currentPositionMs: Long, seekTo: (targetMs: Long) -> Unit) {
        if (!enabled() || activeSegments.isEmpty()) return

        // Clear whitelist if playhead has naturally moved past the whitelisted segment
        userWhitelistedUuid?.let { id ->
            val wl = activeSegments.firstOrNull { it.uuid == id }
            if (wl == null || !wl.contains(currentPositionMs)) {
                userWhitelistedUuid = null
            }
        }

        val segment = activeSegments.firstOrNull {
            it.contains(currentPositionMs) ||
                (currentPositionMs < RESTART_THRESHOLD_MS && it.startMs < RESTART_THRESHOLD_MS && it.endMs > currentPositionMs)
        } ?: return
        if (segment.uuid == userWhitelistedUuid) return
        val now = nowMs()
        if (lastSkippedUuid == segment.uuid && abs(now - lastSkipTimeMs) < REPEAT_GUARD_MS) return

        lastSkippedUuid = segment.uuid
        lastSkipTimeMs = now
        val target = segment.endMs + SKIP_LEAD_MS
        pendingSkipTargetMs = target
        Log.i(TAG, "Skipping ${segment.category.key} ${segment.startMs}-${segment.endMs}ms")
        seekTo(target)
    }

    fun reset() {
        fetchJob?.cancel()
        tickerJob?.cancel()
        fetchJob = null
        tickerJob = null
        currentVideoId = null
        activeSegments = emptyList()
        lastSkippedUuid = null
        userWhitelistedUuid = null
        pendingSkipTargetMs = null
    }

    internal fun setSegmentsForTest(videoId: String, segments: List<SponsorSegment>) {
        currentVideoId = videoId
        activeSegments = segments
        lastSkippedUuid = null
        userWhitelistedUuid = null
        pendingSkipTargetMs = null
    }
}
