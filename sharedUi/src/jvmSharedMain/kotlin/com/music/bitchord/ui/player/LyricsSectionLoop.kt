package com.music.bitchord.ui.player

import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.playback.SectionLoop

/**
 * Derives the audio end timestamp (in ms) of a lyric line.
 *
 * Rules:
 * 1. If the line has word-level timestamps or [LyricLine.sungUntilMs] that exceeds [LyricLine.timeMs],
 *    that explicit end is used.
 * 2. Otherwise, the start time ([LyricLine.timeMs]) of the next chronological line is used (including
 *    gap lines, which represent instrumental breaks).
 * 3. If there are no subsequent lines, falls back to [trackDurationMs] (if greater than [LyricLine.timeMs])
 *    or a default duration of 5 000 ms.
 */
internal fun deriveLyricLineEndMs(
    lineIndex: Int,
    lines: List<LyricLine>,
    trackDurationMs: Long,
): Long {
    val line = lines.getOrNull(lineIndex) ?: return 0L

    // 1. Explicit end from word sync or sungUntilMs
    val explicitEnd = line.words.lastOrNull()?.endMs ?: line.sungUntilMs
    if (explicitEnd != null && explicitEnd > line.timeMs) {
        return if (trackDurationMs > 0L) minOf(explicitEnd, trackDurationMs) else explicitEnd
    }

    // 2. Next chronological line's timeMs (including gap lines representing instrumental interludes)
    for (i in (lineIndex + 1) until lines.size) {
        val next = lines[i]
        if (next.timeMs > line.timeMs) {
            return if (trackDurationMs > 0L) minOf(next.timeMs, trackDurationMs) else next.timeMs
        }
    }

    // 3. Fallback to track duration or 5 seconds after line start
    return if (trackDurationMs > line.timeMs) {
        trackDurationMs
    } else {
        line.timeMs + 5_000L
    }
}

/**
 * Maps a selection of lyric lines (from [firstLineIndex] to [lastLineIndex]) to an audio
 * timestamp range [startMs, endMs] (in ms) for A-B section looping, applying the user's [lyricsOffsetMs].
 *
 * Returns null if the resulting range is invalid or shorter than [SectionLoop.MIN_GAP_MS].
 */
internal fun mapLyricRangeToAudioRange(
    firstLineIndex: Int,
    lastLineIndex: Int,
    lines: List<LyricLine>,
    lyricsOffsetMs: Int,
    trackDurationMs: Long,
): Pair<Long, Long>? {
    if (lines.isEmpty()) return null
    val startIdx = minOf(firstLineIndex, lastLineIndex).coerceIn(0, lines.lastIndex)
    val endIdx = maxOf(firstLineIndex, lastLineIndex).coerceIn(0, lines.lastIndex)

    val firstLine = lines[startIdx]
    val rawStart = firstLine.timeMs
    val rawEnd = deriveLyricLineEndMs(endIdx, lines, trackDurationMs)

    // Apply lyrics offset: adjustedLyricsSeekTarget(timeMs, offsetMs) = (timeMs + offsetMs).coerceAtLeast(0L)
    val audioStart = adjustedLyricsSeekTarget(rawStart, lyricsOffsetMs)
    val audioEnd = adjustedLyricsSeekTarget(rawEnd, lyricsOffsetMs)

    val clampedStart = if (trackDurationMs > 0L) minOf(audioStart, trackDurationMs) else audioStart
    val clampedEnd = if (trackDurationMs > 0L) minOf(audioEnd, trackDurationMs) else audioEnd

    if (clampedEnd <= clampedStart || clampedEnd - clampedStart < SectionLoop.MIN_GAP_MS) {
        return null
    }
    return clampedStart to clampedEnd
}
