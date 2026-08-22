package com.music.bitchord.data.lyrics

/**
 * One word of a line, with the stretch of the song it is sung over.
 *
 * Apple's TTML splits long words into syllables; those are merged back into
 * whole words on the way in, so [startMs] is the first syllable's start and
 * [endMs] the last one's end. Whole words are what the sweep needs — a
 * highlight that ran across "e" and "nough" separately reads as a stutter.
 */
data class LyricWord(val startMs: Long, val endMs: Long, val text: String)

/**
 * One synced line. [timeMs] is when it starts; a blank [text] is an
 * instrumental stretch — LRC files mark those with a bare timestamp.
 *
 * [words] is populated only by the providers that carry word-level timing
 * (BetterLyrics, LyricsPlus, SimpMusic's rich sync). LRCLIB has none, so a
 * line from there highlights whole; see [isWordSynced].
 */
data class LyricLine(
    val timeMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList(),
) {
    val isGap: Boolean get() = text.isEmpty()

    val isWordSynced: Boolean get() = words.isNotEmpty()

    /** When the last word finishes, or [timeMs] when the line isn't word-synced. */
    val endMs: Long get() = words.lastOrNull()?.endMs ?: timeMs

    /**
     * How far through the line the singing has got, 0..1, as a fractional
     * index into [text]. The sweep reveals up to this character.
     *
     * Within a word it interpolates across that word's own span, so a held
     * note draws slowly and a rattled-off one snaps. Whitespace between two
     * words is credited to the gap between them: it fills as the singer moves
     * on rather than jumping ahead of the next word's first letter.
     */
    fun revealedChars(positionMs: Long): Float {
        if (words.isEmpty()) return if (positionMs >= timeMs) text.length.toFloat() else 0f
        var offset = 0
        words.forEachIndexed { index, word ->
            // Where this word sits in [text]. Built by walking rather than
            // searching, so a word repeated in the line still lines up.
            val start = text.indexOf(word.text, offset).takeIf { it >= 0 } ?: offset
            val end = start + word.text.length
            if (positionMs < word.startMs) return start.toFloat()
            if (positionMs < word.endMs) {
                val span = (word.endMs - word.startMs).coerceAtLeast(1L)
                val through = (positionMs - word.startMs).toFloat() / span
                return start + through * word.text.length
            }
            // Past this word: the trailing space fills over the pause before
            // the next one, so the highlight keeps creeping instead of resting
            // on the word's last letter.
            val next = words.getOrNull(index + 1)
            if (next != null && positionMs < next.startMs) {
                val gapStart = text.indexOf(next.text, end).takeIf { it >= 0 } ?: end
                val pause = (next.startMs - word.endMs).coerceAtLeast(1L)
                val through = (positionMs - word.endMs).toFloat() / pause
                return end + through * (gapStart - end)
            }
            offset = end
        }
        return text.length.toFloat()
    }

    /**
     * How much bloom the word being sung has earned, 0..1.
     *
     * Two things decide it. How long the word is held sets the ceiling — a
     * note carried for a second swells, a word rattled off in a tenth of one
     * barely registers, which is the difference between a glow that belongs to
     * the singing and a lamp dragged along under the text. Then an envelope
     * across the word's own span rises as it lands and eases off as it goes,
     * so each word blooms and lets go rather than the light being on
     * throughout and stepping between brightnesses at every boundary.
     *
     * Zero between words and after the last one, which is what keeps the
     * pauses dark and costs nothing to draw.
     */
    fun glowIntensity(positionMs: Long): Float {
        val word = words.firstOrNull { positionMs < it.endMs } ?: return 0f
        if (positionMs < word.startMs) return 0f

        val held = (word.endMs - word.startMs).coerceAtLeast(1L)
        val through = ((positionMs - word.startMs).toFloat() / held).coerceIn(0f, 1f)
        val envelope = when {
            through < GLOW_ATTACK -> through / GLOW_ATTACK
            through > 1f - GLOW_RELEASE -> (1f - through) / GLOW_RELEASE
            else -> 1f
        }
        val pace = ((held - GLOW_FAST_MS).toFloat() / (GLOW_SLOW_MS - GLOW_FAST_MS))
            .coerceIn(0f, 1f)
        return (GLOW_FLOOR + (1f - GLOW_FLOOR) * pace) * envelope.coerceIn(0f, 1f)
    }
}

/** A word this short is patter; it gets [GLOW_FLOOR] and no more. */
private const val GLOW_FAST_MS = 130L

/** A word held this long gets the full bloom. */
private const val GLOW_SLOW_MS = 800L

/** What the quickest words still get, so patter doesn't go completely flat. */
private const val GLOW_FLOOR = 0.22f

/** Share of a word's span spent coming up, and going back down. */
private const val GLOW_ATTACK = 0.18f
private const val GLOW_RELEASE = 0.38f
