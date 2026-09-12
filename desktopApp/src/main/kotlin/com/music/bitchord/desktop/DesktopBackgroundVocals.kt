package com.music.bitchord.desktop

/**
 * Pulls the answering vocal out of a line and hangs it underneath, as [DesktopLyricLine.background].
 *
 * Only Apple's TTML says outright which spans are the backing voice (`ttm:role="x-bg"`, read in
 * [DesktopTtmlLyrics]). Every other provider writes it into the line as a bracket:
 *
 * ```
 * I get my weed from California (That's that shit)
 * ```
 *
 * — a bracket doing the job of a second line. The words inside it are sung *over* the line that
 * follows, so left inline the cursor moves on with the bracket half-swept. Split out, the bracket
 * keeps its own timings and draws below the lead.
 *
 * The parentheses stay on the text: they are what the provider published, and a smaller line under
 * the lead already says "this is the answer" without the punctuation being taken away.
 */
internal fun List<DesktopLyricLine>.withBackgroundVocals(): List<DesktopLyricLine> =
    map { it.splitTrailingBracket() }

private fun DesktopLyricLine.splitTrailingBracket(): DesktopLyricLine {
    // A source that marked its own backing vocal has already said everything guessing from
    // punctuation could, and better.
    if (background != null || isGap) return this

    val open = bracketStart(text) ?: return this
    val lead = text.substring(0, open).trimEnd()
    val backing = text.substring(open).trim()
    if (lead.isEmpty() || !backing.any { it.isLetterOrDigit() }) return this

    if (words.isEmpty()) {
        // Line-synced: there is no timing to divide, so the two halves share the line's stamp and
        // simply stack. Both keep the stated end — it is the line's end, and the line is both.
        return copy(
            text = lead,
            background = DesktopLyricLine(timeMs = timeMs, text = backing, sungUntilMs = sungUntilMs),
        )
    }

    // [text] is the words joined by single spaces in every word-synced parser here, so the
    // bracket's character offset is a word boundary — unless the bracket opens mid-word
    // ("wait(ing)"), in which case it isn't one and there is nothing to hand the backing line.
    val split = words.indexOfFirstStartingAt(open) ?: return this
    if (split <= 0) return this

    val backingWords = words.drop(split)
    return copy(
        text = lead,
        words = words.take(split),
        background = DesktopLyricLine(
            timeMs = backingWords.first().startMs,
            text = backing,
            words = backingWords,
        ),
    )
}

/** Index of the word starting at character [offset] in the joined text, or null if none does. */
private fun List<DesktopLyricWord>.indexOfFirstStartingAt(offset: Int): Int? {
    var at = 0
    forEachIndexed { index, word ->
        if (at == offset) return index
        if (at > offset) return null
        // The space that joins this word to the next one.
        at += word.text.length + 1
    }
    return null
}

/**
 * Where the bracket that closes the line opens, or null if the line does not end in one.
 *
 * Walked back from the end counting depth, so a nested bracket does not split the line at the inner
 * pair. A line that is *entirely* bracketed is already its own backing line and has no lead to hang
 * under, so it is left alone.
 */
private fun bracketStart(text: String): Int? {
    if (!text.endsWith(')')) return null
    var depth = 0
    for (index in text.indices.reversed()) {
        when (text[index]) {
            ')' -> depth++
            '(' -> {
                depth--
                if (depth == 0) return index.takeIf { it > 0 }
            }
        }
    }
    return null
}
