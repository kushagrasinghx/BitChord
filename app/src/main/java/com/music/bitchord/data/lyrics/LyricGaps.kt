package com.music.bitchord.data.lyrics

/** Shorter instrumental breaks aren't worth interrupting the line for. */
internal const val MIN_GAP_MS = 4_000L

/**
 * Marks the instrumental stretches with blank lines, the way an LRC file
 * marks them with a bare timestamp.
 *
 * The word-timed providers give every line an end, so unlike
 * [LrcLib.parseLrc] — which can only guess from the stamp of the next line —
 * a break here starts the moment the singing actually stops. That is the
 * difference between the note appearing when the vocal ends and it appearing
 * several seconds later, once the next line was due.
 */
internal fun List<LyricLine>.withInstrumentalGaps(): List<LyricLine> {
    if (isEmpty()) return this
    val out = ArrayList<LyricLine>(size + 4)
    // Nothing stands for the intro, so give the run-up its own break.
    if (first().timeMs >= MIN_GAP_MS) out += LyricLine(0L, "")
    forEachIndexed { index, line ->
        out += line
        val next = getOrNull(index + 1) ?: return@forEachIndexed
        // endMs falls back to the line's own start when there are no word
        // timings, which makes this the same "stamp to stamp" test LRC uses.
        val silence = next.timeMs - line.endMs
        if (silence >= MIN_GAP_MS) out += LyricLine(line.endMs, "")
    }
    return out
}
