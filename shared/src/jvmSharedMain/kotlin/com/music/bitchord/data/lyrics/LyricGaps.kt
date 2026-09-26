package com.music.bitchord.data.lyrics

/** Shorter instrumental breaks aren't worth interrupting the line for. */
internal const val MIN_GAP_MS = 4_000L

/**
 * Marks the instrumental stretches with blank lines, the way an LRC file
 * marks them with a bare timestamp.
 *
 * A break is only drawn where the line before it says when its singing
 * stopped — see [LyricLine.hasKnownEnd]. Given that, the note appears the
 * moment the vocal ends rather than several seconds later once the next line
 * was due, which is the whole advantage over [LrcLib.parseLrc]'s stamp-to-stamp
 * guess. Without it there is nothing to measure silence against: the distance
 * to the next stamp is the line's own slot, and treating that as a break puts a
 * note after every single line of a line-synced source.
 */
internal fun List<LyricLine>.withInstrumentalGaps(): List<LyricLine> {
    if (isEmpty()) return this
    val out = ArrayList<LyricLine>(size + 4)
    // Nothing stands for the intro, so give the run-up its own break.
    if (first().timeMs >= MIN_GAP_MS) out += LyricLine(0L, "")
    forEachIndexed { index, line ->
        out += line
        val next = getOrNull(index + 1) ?: return@forEachIndexed
        if (!line.hasKnownEnd) return@forEachIndexed
        val silence = next.timeMs - line.endMs
        // A marker sharing its line's stamp could never be reached: the cursor
        // takes the last line whose stamp has passed, so the note would sit on
        // top of the line it belongs to and the words would never light up.
        if (silence >= MIN_GAP_MS && line.endMs > line.timeMs) out += LyricLine(line.endMs, "")
    }
    return out
}
