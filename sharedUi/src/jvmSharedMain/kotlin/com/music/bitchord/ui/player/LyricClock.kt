package com.music.bitchord.ui.player

import kotlin.math.abs

/**
 * Reconciles the frame-driven lyric clock with the player's slower position reports.
 *
 * The player is sampled every 500 ms, while lyrics advance once per frame. Comparing a
 * fresh report directly with the already-advanced display clock mistakes delivery delay
 * for a seek and rewinds the words. Instead, compare it with where the previous *report*
 * should have reached in the elapsed wall-clock time. Ordinary polling and main-thread
 * delay stay monotonic; an actual seek is still a large discontinuity and resets at once.
 */
internal class LyricClockReconciler(
    initialReportedMs: Long,
    initialObservedAtMs: Long,
    initialPlaying: Boolean,
) {
    private var lastReportedMs = initialReportedMs
    private var lastObservedAtMs = initialObservedAtMs
    private var wasPlaying = initialPlaying

    fun reconcile(
        displayedMs: Long,
        reportedMs: Long,
        observedAtMs: Long,
        isPlaying: Boolean,
    ): Long {
        val elapsedMs = (observedAtMs - lastObservedAtMs).coerceAtLeast(0L)
        val expectedMs = lastReportedMs + if (wasPlaying) elapsedMs else 0L
        // Once playback stops there is no interpolation to protect. Settle on the
        // authoritative position immediately so pausing cannot leave a word ahead.
        val discontinuity = !isPlaying || abs(reportedMs - expectedMs) > SEEK_DISCONTINUITY_MS

        lastReportedMs = reportedMs
        lastObservedAtMs = observedAtMs
        wasPlaying = isPlaying

        return if (discontinuity) reportedMs else maxOf(displayedMs, reportedMs)
    }
}

/**
 * Safely above one delayed 500 ms poll, but below a meaningful lyric seek. Track changes
 * reset the reconciler through its composition key and do not depend on this threshold.
 */
private const val SEEK_DISCONTINUITY_MS = 1_250L
