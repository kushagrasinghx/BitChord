package com.music.bitchord.data.lyrics

/**
 * Enhanced ("A2") LRC — a normal LRC line with a stamp in front of each word:
 *
 * ```
 * [00:27.39]<00:27.39>I <00:27.54>been <00:27.74>tryna <00:28.07>call
 * ```
 *
 * This is what Musixmatch's rich sync becomes, and it is what SimpMusic
 * serves. Only starts are written down, so each word runs until the next one
 * begins, and the last word of a line until the next line does.
 */
object EnhancedLrc {

    private val LINE = Regex("""^\[(\d{1,3}):(\d{2})[.:](\d{2,3})](.*)$""")
    private val WORD = Regex("""<(\d{1,3}):(\d{2})[.:](\d{2,3})>([^<]*)""")

    /** Empty when [lrc] carries no word stamps — the caller can then fall back. */
    fun parse(lrc: String): List<LyricLine> {
        val rows = lrc.lineSequence()
            .mapNotNull { line -> LINE.matchEntire(line.trim()) }
            .map { match ->
                Row(
                    timeMs = stamp(match.groupValues[1], match.groupValues[2], match.groupValues[3]),
                    words = WORD.findAll(match.groupValues[4]).toList(),
                    plain = match.groupValues[4].trim(),
                )
            }
            .sortedBy { it.timeMs }
            .toList()
        // Nothing word-stamped in here: this is an ordinary LRC file and the
        // caller is better served parsing it as one.
        if (rows.none { it.words.isNotEmpty() }) return emptyList()

        return rows.mapIndexedNotNull { index, row ->
            if (row.words.isEmpty()) {
                val text = decodeEntities(row.plain)
                return@mapIndexedNotNull if (text.isEmpty()) null else LyricLine(row.timeMs, text)
            }
            // A word runs until the next one starts; the last runs until the
            // next line does. Without a next line — the closing word of the
            // song — give it a beat rather than zero, or its sweep never runs.
            val lineEnd = rows.getOrNull(index + 1)?.timeMs
                ?: (stamp(row.words.last()) + TAIL_MS)

            val words = row.words.mapIndexedNotNull { i, match ->
                val text = decodeEntities(match.groupValues[4]).trim()
                if (text.isEmpty()) return@mapIndexedNotNull null
                val wordStart = stamp(match)
                val wordEnd = row.words.getOrNull(i + 1)?.let { stamp(it) } ?: lineEnd
                LyricWord(wordStart, wordEnd.coerceAtLeast(wordStart), text)
            }
            if (words.isEmpty()) return@mapIndexedNotNull null
            LyricLine(
                timeMs = minOf(row.timeMs, words.first().startMs),
                text = words.joinToString(" ") { it.text },
                words = words,
            )
        }.withInstrumentalGaps()
    }

    private class Row(val timeMs: Long, val words: List<MatchResult>, val plain: String)

    private fun stamp(match: MatchResult): Long =
        stamp(match.groupValues[1], match.groupValues[2], match.groupValues[3])

    private fun stamp(minutes: String, seconds: String, fraction: String): Long {
        // Two digits mean centiseconds, three mean milliseconds.
        val fractionMs = if (fraction.length == 3) fraction.toLong() else fraction.toLong() * 10
        return minutes.toLong() * 60_000 + seconds.toLong() * 1_000 + fractionMs
    }

    /**
     * SimpMusic serves its rich sync HTML-escaped, so an apostrophe arrives as
     * `&#x27;` and would be sung literally. Metrolist shipped that bug; this
     * is the fix.
     */
    internal fun decodeEntities(text: String): String {
        if ('&' !in text) return text
        return text
            .replace(Regex("&#x([0-9a-fA-F]+);")) { it.groupValues[1].toInt(16).toChar().toString() }
            .replace(Regex("&#(\\d+);")) { it.groupValues[1].toInt().toChar().toString() }
            .replace("&apos;", "'")
            .replace("&quot;", "\"")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            // Last, so "&amp;#x27;" doesn't decode twice into an apostrophe.
            .replace("&amp;", "&")
    }

    private const val TAIL_MS = 800L
}
