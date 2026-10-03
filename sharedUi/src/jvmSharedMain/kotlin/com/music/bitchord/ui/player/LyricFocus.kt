package com.music.bitchord.ui.player

import com.music.bitchord.data.lyrics.LyricLine

/** Compute end times for every line so that lines without a known end can
 *  still participate in the active-row filter. For lines lacking [LyricLine.hasKnownEnd]
 *  the fallback is the next line's start time, or [Long.MAX_VALUE] for the last line. */
internal fun effectiveEndTimes(lines: List<LyricLine>): LongArray = lines.mapIndexed { i, line ->
    if (line.hasKnownEnd || line.background?.hasKnownEnd == true) {
        line.endMs
    } else {
        lines.getOrNull(i + 1)?.timeMs ?: Long.MAX_VALUE
    }
}.toLongArray()

/** Keep only the lines currently being sung visible as active. */
internal fun activeLyricRows(lines: List<LyricLine>, positionMs: Long): List<Int> {
    val ends = effectiveEndTimes(lines)
    return lines.withIndex().filter { (index, line) ->
        !line.isGap && line.timeMs <= positionMs && positionMs < ends[index]
    }.map { it.index }
}
