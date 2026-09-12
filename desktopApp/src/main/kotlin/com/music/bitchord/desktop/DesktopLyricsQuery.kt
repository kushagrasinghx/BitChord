package com.music.bitchord.desktop

/** A YouTube title, as a lyrics database would have indexed it. */
internal fun String.forLyricsSearch(): String {
    var name = this
    LYRICS_CREDITS.forEach { pattern -> name = pattern.replace(name, " ") }
    return name.replace(LYRICS_WHITESPACE, " ").trim().trimEnd(',', '-', '–', '—').trim()
        // A title that was *only* packaging is no title at all; better to ask with what we were
        // given than with nothing.
        .ifBlank { trim() }
}

/** Trims " - Topic" off an auto-generated channel name. */
internal fun String.artistForLyricsSearch(): String =
    removeSuffix(" - Topic").trim().ifBlank { trim() }

private val LYRICS_WHITESPACE = Regex("""\s+""")

private val LYRICS_CREDITS = listOf(
    // Bracketed credits: (feat.
    Regex("""\s*[(\[]\s*(feat|ft|featuring|with)\b[^)\]]*[)\]]""", RegexOption.IGNORE_CASE),
    // The same, unbracketed and running to the end of the title.
    Regex("""\s+(feat|ft|featuring)\.?\s+.*$""", RegexOption.IGNORE_CASE),
    // How the upload was labelled, not what was recorded.
    Regex(
        """\s*[(\[]\s*(official\s*)?(music\s*)?""" +
            """(video|audio|visuali[sz]er|lyrics?\s*video|lyrics?|m/?v|hd|hq|4k|full\s*song)""" +
            """\s*[)\]]""",
        RegexOption.IGNORE_CASE,
    ),
    Regex("""\s*[(\[]\s*official\s*[)\]]""", RegexOption.IGNORE_CASE),
)
