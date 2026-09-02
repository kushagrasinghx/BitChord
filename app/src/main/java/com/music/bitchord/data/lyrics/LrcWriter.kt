package com.music.bitchord.data.lyrics

/**
 * Turns parsed lines back into the text of an LRC file.
 *
 * The counterpart to [LrcLib.parseLrc], and the only writer in this package —
 * everything else here reads. It exists for the download path, which embeds
 * what it gets into the saved file's own metadata (see
 * `com.music.bitchord.download.LyricsTag`), and LRC is what that field is read
 * as: whatever a track's lyrics arrived as, they leave here as `[mm:ss.xx]`
 * stamps, which is the one synced-lyric syntax every other player understands.
 *
 * **Word timings are deliberately dropped.** Three of the four providers carry
 * them and the player uses them, but the way to keep them in a file is the
 * "enhanced" A2 extension — `<mm:ss.xx>` runs inside the line — and a reader
 * that doesn't implement it does not ignore those stamps, it *shows* them, so
 * every line reads as angle-bracket noise with words scattered through it.
 * Line stamps degrade to plain text in the same reader; word stamps corrupt it.
 * The file is for other players, so it gets the syntax they all read.
 *
 * Nothing writes the `[ti:]`/`[ar:]`/`[al:]` header tags either. The container's
 * own title, artist and album atoms sit beside this field and already say all
 * three; repeating them here would only be visible in the readers that *don't*
 * parse LRC, which are exactly the ones that can least afford three more lines
 * of markup at the top.
 */
internal fun List<LyricLine>.toLrc(): String {
    if (isEmpty()) return ""
    // Plain unsynced lyrics have no timestamps — emit clean text rows
    if (none { it.timeMs > 0L }) {
        return joinToString("\n") { it.flattened() }
    }
    // Sorted here rather than assumed: the providers each sort their own output,
    // but this is one line of insurance against a file whose stamps run
    // backwards — which every reader renders as lyrics that jump about.
    return sortedBy { it.timeMs }.joinToString("\n") { line -> stamp(line.timeMs) + line.flattened() }
}

/**
 * The line as one row of text, answering vocal and all.
 *
 * [LyricLine.background] is a display split — the backing voice drawn under the
 * lead rather than run into it, see [withBackgroundVocals] — and LRC has no way
 * to say that. Giving it a stamp of its own would put a second line into the
 * file where the song has one, so it goes back where every provider that
 * doesn't mark it structurally had it: on the end of the lead.
 */
private fun LyricLine.flattened(): String =
    background?.let { (text + " " + it.text).trim() } ?: text

/**
 * The same lines as [toLrc], with the word timings kept — the "enhanced" A2
 * extension, `<mm:ss.xx>` runs inside each line.
 *
 * This does **not** replace [toLrc], and the reason is the one that function
 * documents: a reader without A2 does not ignore a word stamp, it shows it, so
 * a file carrying only this reads as angle-bracket noise everywhere else. The
 * two are written to different fields — plain LRC to the container's own lyrics
 * atom, where every other player looks, and this to a BitChord-specific one
 * they don't read (see `Mp4Tagger`, `FlacTagger`, `WebmTagger`). Other players
 * are unaffected; this app gets the timings back off the file instead of the
 * network, which is what lets a downloaded song light up word by word offline.
 *
 * Empty when nothing here is word-synced: the plain field already says
 * everything a line-synced source had, and a second copy of it would be bytes
 * spent to learn nothing.
 *
 * Each line closes with a bare `<stamp>` at its end, which is what carries
 * [LyricLine.endMs] across — without it the last word of every line would come
 * back with no end, and the player reads a silence out of that.
 */
internal const val WORD_LYRICS_FIELD = "BITCHORD_LYRICS"

internal fun List<LyricLine>.toEnhancedLrc(): String {
    if (isEmpty()) return ""
    if (none { it.isWordSynced || it.background?.isWordSynced == true }) return ""
    return sortedBy { it.timeMs }.joinToString("\n") { line -> stamp(line.timeMs) + line.enhancedBody() }
}

/**
 * One line as stamped word runs, or its plain text when it has none.
 *
 * The answering vocal rides along on the end exactly as it does in [flattened],
 * for the same reason — one line of the song is one line of the file — but here
 * it keeps its own stamps, so the backing voice is still timed rather than
 * pinned to the lead's last word. A background with text but no timings of its
 * own becomes a single run at its own stamp, which is the most that can be said
 * about it truthfully.
 */
private fun LyricLine.enhancedBody(): String {
    val runs = timedRuns()
    if (runs.isEmpty()) return flattened()
    val out = StringBuilder()
    // Clamped to run forwards. A background vocal legitimately starts partway
    // through the lead it answers, so concatenating the two can hand us a stamp
    // earlier than the one before it — and a reader taking each run's end from
    // the next one's start would read that as a negative-length word.
    var previous = timeMs
    runs.forEachIndexed { index, word ->
        val start = maxOf(word.startMs, previous)
        out.append(wordStamp(start)).append(word.text)
        if (index != runs.lastIndex) out.append(' ')
        previous = start
    }
    out.append(wordStamp(maxOf(runs.maxOf { it.endMs }, previous)))
    return out.toString()
}

/**
 * The line's words, the answering vocal's after them — or nothing, when the
 * lead has no timings to anchor them to.
 *
 * The lead is required rather than merely preferred: its words are what the
 * line's text is made of, and emitting only the background's runs would write a
 * line that is missing everything before the bracket.
 */
private fun LyricLine.timedRuns(): List<LyricWord> {
    if (words.isEmpty()) return emptyList()
    val answer = background?.let { bg ->
        bg.words.ifEmpty {
            if (bg.text.isBlank()) emptyList() else listOf(LyricWord(bg.timeMs, bg.endMs, bg.text))
        }
    }.orEmpty()
    return words + answer
}

/**
 * `[mm:ss.xx]`, in centiseconds — the two-digit fraction, which is the form
 * with the widest support. [LrcLib.parseLrc] reads three digits too, but
 * writing them is a millisecond of precision bought at the cost of the readers
 * that only accept two.
 *
 * Assembled with [padStart] rather than `String.format`, which is not a style
 * preference: `%02d` formats through the default locale, and under a locale
 * with its own numerals — Arabic, Bengali, several Indic ones — that emits
 * digits no LRC parser on earth matches, including this package's own. The
 * output has to be ASCII wherever the device is.
 *
 * Minutes are not wrapped at 99. A stamp that long is a DJ set rather than a
 * song, but truncating it would silently move the line to the wrong place,
 * where overflowing to three digits at worst loses one reader the timing.
 */
private fun stamp(timeMs: Long): String = "[" + clock(timeMs) + "]"

/** The same clock inside angle brackets — one word's start, in A2. */
private fun wordStamp(timeMs: Long): String = "<" + clock(timeMs) + ">"

private fun clock(timeMs: Long): String {
    // A negative stamp is not something to sort or write; nothing produces one,
    // and clamping is cheaper than a parser somewhere deciding what "[-1:.." is.
    val total = timeMs.coerceAtLeast(0L)
    val minutes = (total / 60_000).toString().padStart(2, '0')
    val seconds = (total % 60_000 / 1_000).toString().padStart(2, '0')
    val centiseconds = (total % 1_000 / 10).toString().padStart(2, '0')
    return "$minutes:$seconds.$centiseconds"
}
