package com.music.bitchord.data.lyrics

/**
 * One stamped run of text as a provider wrote it, its spacing untouched: a
 * word, or one syllable of one. [raw] keeps whatever whitespace sat around it,
 * because that whitespace is the only thing saying where one word stops.
 */
internal class TimedRun(val startMs: Long, val endMs: Long, val raw: String)

/**
 * Glues [runs] into words, each one keeping its runs as [LyricSyllable]s.
 *
 * Every format that times syllables says which ones belong together the same
 * way, by leaving the space out: Apple's `<span>e</span><span>nough</span>`,
 * an A2 line's `<00:31.83>e<00:31.99>nough `, QQ's `e(31839,157)nough(31996,533)`.
 * A run that neither ends in whitespace nor is followed by a run starting with
 * it is therefore the front of a word that carries on. A blank run — a lone
 * space, or the bare stamp that closes an A2 line — ends the word in hand.
 *
 * Two cases where a missing space says nothing:
 *
 *  - Han, kana and Thai are written without spaces at all, so leaving them out
 *    is not a decision about words. Each run there stays a word of its own, as
 *    it always was; glued, a whole line would become one word and lift as one.
 *  - A writer that puts no whitespace anywhere in the line is not stating word
 *    boundaries either, just omitting them. Each run stays a word, which is
 *    what these lines read as before syllables were looked for at all. Passing
 *    [spacingIsExplicit] says the caller rebuilt the spacing from a flag of its
 *    own, so even a line that is one long split word is believed.
 */
internal fun wordsFromRuns(runs: List<TimedRun>, spacingIsExplicit: Boolean = false): List<LyricWord> {
    val glue = spacingIsExplicit || runs.any { run -> run.raw.any(Char::isWhitespace) }

    val words = ArrayList<LyricWord>()
    val current = StringBuilder()
    val parts = ArrayList<LyricSyllable>()
    var start = 0L
    var end = 0L

    fun flush() {
        if (current.isNotEmpty()) {
            val text = current.toString()
            words += LyricWord(start, maxOf(start, end), text, LyricSyllable.normalized(parts, text.length))
        }
        current.setLength(0)
        parts.clear()
    }

    for (run in runs) {
        val content = run.raw.trim()
        if (content.isEmpty()) {
            flush()
            continue
        }
        val joins = glue && current.isNotEmpty() && !run.raw.first().isWhitespace() &&
            !current.last().isUnspacedScript() && !content.first().isUnspacedScript()
        if (!joins) flush()
        if (current.isEmpty()) {
            start = run.startMs
            end = run.startMs
        }
        val from = current.length
        current.append(content)
        val runEnd = maxOf(run.startMs, run.endMs)
        parts += LyricSyllable(run.startMs, runEnd, from, current.length)
        end = maxOf(end, runEnd)
        if (!glue || run.raw.last().isWhitespace()) flush()
    }
    flush()
    return words
}

/** Scripts written without spaces between words. */
private fun Char.isUnspacedScript(): Boolean =
    this in '\u3400'..'\u9FFF' || // Han
        this in '\u3040'..'\u30FF' || // hiragana and katakana
        this in '\u3000'..'\u303F' || // CJK punctuation
        this in '\uFF00'..'\uFFEF' || // full-width forms
        this in '\u0E00'..'\u0E7F' // Thai
