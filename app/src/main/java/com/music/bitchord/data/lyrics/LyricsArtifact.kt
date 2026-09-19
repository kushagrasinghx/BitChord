package com.music.bitchord.data.lyrics

/**
 * A lyrics response that can outlive the network request that produced it.
 *
 * [lines] is the app's common representation for rendering. [content] keeps
 * the richest portable representation available so a downloaded track does not
 * have to throw away word timing merely because the player consumes
 * [LyricLine].
 */
data class LyricsArtifact(
    val source: LyricsSource,
    val format: LyricsArtifactFormat,
    val content: String,
    val lines: List<LyricLine>,
) {
    val isWordSynced: Boolean get() = lines.any { it.isWordSynced }
}

/** File representation used by the sidecar store and the offline parser. */
enum class LyricsArtifactFormat(
    val extension: String,
    val mimeType: String,
) {
    LRC("lrc", "text/plain"),
    ENHANCED_LRC("lrc", "text/plain"),
    TTML("ttml", "application/ttml+xml"),
}

/** Serialises the app's common lyric model without changing its timing. */
object LyricsSerializer {

    const val MAX_LYRICS_CHARS = 64_000

    fun fromLines(source: LyricsSource, lines: List<LyricLine>): LyricsArtifact? {
        if (lines.none { it.text.isNotBlank() }) return null
        val wordSynced = lines.any { it.isWordSynced }
        val content = toLrc(lines, wordSynced)
        if (content.length > MAX_LYRICS_CHARS) return null

        return LyricsArtifact(
            source = source,
            format = if (wordSynced) {
                LyricsArtifactFormat.ENHANCED_LRC
            } else {
                LyricsArtifactFormat.LRC
            },
            content = content,
            lines = lines,
        )
    }

    /** Writes standard or Enhanced LRC, depending on [wordSynced]. */
    fun toLrc(lines: List<LyricLine>, wordSynced: Boolean = lines.any { it.isWordSynced }): String =
        lines.sortedBy { it.timeMs }.joinToString("\n") { line ->
            val prefix = stamp(line.timeMs)
            if (!wordSynced || !line.isWordSynced) {
                prefix + line.text
            } else {
                prefix + line.words.joinToString(" ") { word ->
                    "<${stampInner(word.startMs)}>${word.text}"
                }
            }
        }

    /** Plain text fallback for the embedded audio tag. */
    fun plainText(lines: List<LyricLine>): String =
        lines.joinToString("\n") { it.text }

    /**
     * `[mm:ss.xx]`, in centiseconds.
     *
     * Assembled with [padStart] rather than `String.format` to guarantee
     * ASCII numerals under locales with non-Latin numbering (Arabic, Indic, etc.).
     */
    fun stamp(timeMs: Long): String = "[${stampInner(timeMs)}]"

    private fun stampInner(timeMs: Long): String {
        val total = timeMs.coerceAtLeast(0L)
        val minutes = (total / 60_000).toString().padStart(2, '0')
        val seconds = (total % 60_000 / 1_000).toString().padStart(2, '0')
        val centiseconds = (total % 1_000 / 10).toString().padStart(2, '0')
        return "$minutes:$seconds.$centiseconds"
    }
}
