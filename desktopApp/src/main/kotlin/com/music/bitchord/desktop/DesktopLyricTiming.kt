package com.music.bitchord.desktop

import kotlin.math.abs

/**
 * The arithmetic behind the lyrics panel: which rows are being sung, and what time the panel should
 * believe it is.
 */

/** Reconciles the position the panel is displaying with the one playback just reported. */
internal fun reconcileLyricPosition(displayedMs: Long, reportedMs: Long): Long =
    if (abs(displayedMs - reportedMs) <= JITTER_MS) maxOf(displayedMs, reportedMs) else reportedMs

/** Beyond this, a disagreement is a seek rather than polling jitter. */
private const val JITTER_MS = 250L

/** Every row still being sung at [positionMs], in order. */
internal fun activeLyricRows(lines: List<DesktopLyricLine>, positionMs: Long): List<Int> {
    val latest = lines.indexOfLast { it.timeMs <= positionMs }
    if (latest < 0) return emptyList()
    return (0..latest).filter { index ->
        val line = lines[index]
        index == latest ||
            (!line.isGap && line.hasKnownEnd && line.timeMs <= positionMs && positionMs < line.endMs)
    }
}

/**
 * How long before a line lands the panel should start moving to it — and how long the move then
 * takes, which is the same number.
 */
internal fun lyricScrollLead(lines: List<DesktopLyricLine>, positionMs: Long): Long {
    val current = lines.indexOfLast { it.timeMs <= positionMs }
    if (current < 0) return SCROLL_LEAD_MIN_MS
    val next = lines.getOrNull(current + 1) ?: return SCROLL_LEAD_MIN_MS
    return (next.timeMs - lines[current].endMs).coerceIn(SCROLL_LEAD_MIN_MS, SCROLL_LEAD_MAX_MS)
}

internal const val SCROLL_LEAD_MIN_MS = 350L
internal const val SCROLL_LEAD_MAX_MS = 500L
