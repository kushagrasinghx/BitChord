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
 * Which side of the panel a line is sung from.
 *
 * A duet is written in TTML as a `ttm:agent` per line, and Apple lays the two
 * voices out on opposite sides so a call-and-response reads as two people
 * rather than one long verse. [Start] is the default and the only side a
 * single-voice song ever uses — see `TtmlLyrics` for how the sides are worked
 * out from the agents, which is not simply "one agent per side".
 */
enum class LyricAlignment { Start, End }

/**
 * One synced line. [timeMs] is when it starts; a blank [text] is an
 * instrumental stretch — LRC files mark those with a bare timestamp.
 *
 * [words] is populated only by the providers that carry word-level timing
 * (BetterLyrics, LyricsPlus, SimpMusic's rich sync). LRCLIB has none, so a
 * line from there highlights whole; see [isWordSynced].
 *
 * [sungUntilMs] is the line's own end where a line-synced provider states one,
 * which is what lets an interlude be told apart from a slowly sung line.
 *
 * [background] is the answering vocal — the "(ooh)" or the echoed half-phrase
 * a second voice sings over the lead. It is a line in its own right, with its
 * own stamp and its own words, because that is what it is: it starts partway
 * through the line it answers and routinely runs past the *next* line's stamp.
 * Run into [text] it dragged the sweep along with it, and the cursor — which
 * takes the last line whose stamp has passed — moved on before the bracket had
 * been sung, so the tail of the line was skipped. Kept apart it draws
 * underneath the lead on its own clock. Never nested: a background line's own
 * [background] is always null.
 */
data class LyricLine(
    val timeMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList(),
    val sungUntilMs: Long? = null,
    val background: LyricLine? = null,
    val alignment: LyricAlignment = LyricAlignment.Start,
    /** Display-only translation: drive its sweep and bloom from the original vocal. */
    val timingSource: LyricLine? = null,
) {
    val isGap: Boolean get() = text.isEmpty()

    val isWordSynced: Boolean get() = words.isNotEmpty()

    /**
     * Where each of [words] sits in [text], as character ranges.
     *
     * Walked from where the last one ended rather than searched from the start,
     * so a word repeated in the line lines up with its own occurrence — the
     * same walk [revealedChars] does, kept once because the rise reads it on
     * every frame of the line being sung rather than once as it passes.
     */
    val wordSpans: List<IntRange> by lazy(LazyThreadSafetyMode.NONE) {
        var offset = 0
        words.map { word ->
            val start = text.indexOf(word.text, offset).takeIf { it >= 0 } ?: offset
            val end = start + word.text.length
            offset = end
            start until end
        }
    }

    /**
     * Whether anything on this line is off the floor at [positionMs].
     *
     * Two comparisons, so the lines that are not being sung — which is every
     * line but one, on every frame — settle the question without walking their
     * words to find out that none of them have moved.
     */
    fun isLifted(positionMs: Long): Boolean {
        val first = words.firstOrNull() ?: return false
        if (positionMs <= first.startMs) return false
        // A word animated letter by letter runs on past the ordinary fall — its
        // last letter only starts moving as the word ends. Stopping at the fall
        // would drop that letter back to the line mid-swell.
        return positionMs < words.last().endMs + RISE_MS || isGrowing(positionMs)
    }

    /**
     * How far the word covering [positionMs] has lifted, 0..1.
     *
     * Apple Music's words don't only light up, they rise as they land and
     * settle back once they are past — the lift travelling along the line is
     * most of what separates singing from a bar sliding across the text.
     *
     * Up from the word's own start and down from its own end, both over
     * [RISE_MS], so a word held longer than that reaches the top and rests
     * there while patter only ever gets part of the way up — the same "a note
     * carried is worth more than a note rattled off" that shapes the glow.
     */
    fun wordLift(index: Int, positionMs: Long): Float {
        val word = words.getOrNull(index) ?: return 0f
        val rising = ((positionMs - word.startMs) / RISE_MS).coerceIn(0f, 1f)
        val falling = (1f - (positionMs - word.endMs) / RISE_MS).coerceIn(0f, 1f)
        return smooth(minOf(rising, falling))
    }

    /**
     * Whether anything actually told us when the singing stops, rather than
     * only when it starts. Word timings carry it, and so does a provider that
     * stamps the line's own end ([sungUntilMs]).
     *
     * The distance to the next line's stamp is *not* evidence of an end: that
     * distance is the line's own slot, and on a line-synced source it is
     * routinely ten seconds for a line sung over all ten of them.
     */
    val hasKnownEnd: Boolean get() = words.isNotEmpty() || sungUntilMs != null

    /**
     * When the last word finishes — or the line's own end where the provider
     * gave one, or [timeMs] when nothing did. Check [hasKnownEnd] before
     * reading a silence out of this.
     *
     * The answering vocal counts: it is still this line being sung, and it
     * regularly holds a note past the lead's last word. Measured without it, a
     * break would be found in the middle of a line that is still going.
     */
    val endMs: Long
        get() {
            val lead = words.lastOrNull()?.endMs ?: sungUntilMs ?: timeMs
            return maxOf(lead, background?.endMs ?: lead)
        }

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
        timingSource?.let { source ->
            if (source.text.isEmpty()) return 0f
            return (source.revealedChars(positionMs) / source.text.length)
                .coerceIn(0f, 1f) * text.length
        }
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
     * How much of a word's lift is left at [positionMs] — 1 while it is being
     * sung, easing to 0 over [RISE_MS] once it is past.
     *
     * Split out of [wordLift] because a word animated letter by letter has its
     * own way up but settles back down the same way every other word does; the
     * fall is the half of the movement the two share.
     */
    fun wordFall(index: Int, positionMs: Long): Float {
        val word = words.getOrNull(index) ?: return 0f
        return smooth((1f - (positionMs - word.endMs) / RISE_MS).coerceIn(0f, 1f))
    }

    /**
     * The words held long enough to be worth animating letter by letter.
     *
     * A word carried for a second and a half is the one moment in a line where
     * there is time to see anything happen to it, and drawing it the same as
     * the syllables either side wastes that: it is what makes a held note in
     * "golden hour" read as a pause in the highlight rather than as the singer
     * holding on. Each letter of one swells, lifts and lights in turn — see
     * [GrowingWord] for the shape of it.
     *
     * Rare by design. Most lines have none at all and pay a walk of their words
     * once, on the first frame they are drawn.
     */
    val growingWords: List<GrowingWord> by lazy(LazyThreadSafetyMode.NONE) {
        words.mapIndexedNotNull { index, word ->
            if (word.canGrow()) GrowingWord(index, word) else null
        }
    }

    /** The letter-by-letter treatment for word [index], where it has earned one. */
    fun growingAt(index: Int): GrowingWord? =
        growingWords.firstOrNull { it.index == index }

    /**
     * Whether any word on this line is mid-flight at [positionMs]. Answered
     * without touching the words themselves, so the lines that have no held
     * word — which is most of them — settle it in a comparison.
     */
    fun isGrowing(positionMs: Long): Boolean = growingWords.any {
        positionMs >= it.startMs && positionMs <= it.restsAtMs
    }
}

/**
 * One word held long enough to be animated a letter at a time.
 *
 * Every letter runs the same three-beat move — swell up and forward, hold, then
 * settle back to the small lift every sung word carries — but each one starts
 * [GROW_STAGGER] of the word's own length after the one before it, so the
 * movement travels along the word instead of the whole thing pulsing at once.
 *
 * The peaks differ letter by letter as well. Later letters of a long word, and
 * every letter of a word that is only just held long enough to qualify, move
 * and light less: without that a seven-letter word read as a wave with a tail
 * that kept going after the singer had stopped.
 *
 * Peaks are precomputed here because they depend only on the word — its length
 * and how long it is held — while the frame only ever asks where in the move a
 * given letter has got to.
 */
class GrowingWord internal constructor(
    /** Which of the line's words this is. */
    val index: Int,
    word: LyricWord,
) {
    val startMs: Long = word.startMs
    val endMs: Long = word.endMs

    private val chars: Int = word.text.length
    private val scalePeak = FloatArray(chars)
    private val shiftPeak = FloatArray(chars)
    private val risePeak = FloatArray(chars)
    private val bloomPeak = FloatArray(chars)

    /** When the last letter has finished moving and is just sitting lifted. */
    val restsAtMs: Long

    init {
        val held = (endMs - startMs).coerceAtLeast(1L).toFloat()
        // How much of the full treatment this word has earned. Cubed, so the
        // difference between a word held for a second and one held for three is
        // most of the range and everything under a second is barely anything —
        // the alternative reads as every word twitching.
        val earned = ((held - GROW_RAMP_MIN_MS) / (GROW_RAMP_MAX_MS - GROW_RAMP_MIN_MS))
            .coerceIn(0f, 1f)
            .let { it * it * it }
        val decay = decayRate(chars, held)
        // A word barely over the line glows less than one held twice as long,
        // and a long word spreads what it has over more letters.
        val bloomPace = minOf(GROW_BLOOM_PACE_MAX, held / GROW_BLOOM_PACE_MS)
        val bloomSpread = when {
            chars <= 3 -> GROW_BLOOM_SHORT
            chars >= 6 -> GROW_BLOOM_LONG
            else -> 1f
        }
        // Short words swell a little more, having fewer letters to do it with.
        val base = if (chars <= 3) GROW_BASE_SHORT else GROW_BASE_LONG
        val liftPace = (held / GROW_LIFT_PACE_MS).coerceIn(GROW_LIFT_FLOOR, 1f)

        for (i in 0 until chars) {
            val place = if (chars > 1) i.toFloat() / (chars - 1) else 0f
            val reach = earned * (1f - place * decay)
            val scale = 1f + base + reach * GROW_SCALE_RANGE
            scalePeak[i] = scale * GROW_SCALE_TRIM
            bloomPeak[i] = (GROW_BLOOM_FLOOR + reach * GROW_BLOOM_RANGE) * bloomPace * bloomSpread
            // The lift is read off the swell rather than set on its own: a
            // letter that grows more rises further, which is what keeps the two
            // reading as one movement instead of two.
            risePeak[i] = ((scale - 1f) / GROW_SCALE_CEILING) * liftPace
            // Letters lean away from the middle of the word as it swells, so it
            // opens outwards rather than every letter sliding the same way.
            val centre = (i + 0.5f) / chars
            shiftPeak[i] = (centre - 0.5f) * 2f * (scale - 1f) * GROW_SHIFT_EM * GROW_SCALE_TRIM
        }

        val last = (chars - 1).coerceAtLeast(0) * GROW_STAGGER + GROW_SPAN
        restsAtMs = startMs + (held * last).toLong()
    }

    /**
     * Where letter [charIndex] has got to at [positionMs], written into [into]
     * rather than returned: this runs for every letter of the word on every
     * frame, three times over — dim copy, lit copy and bloom — and a line's
     * worth of short-lived objects per frame is a cost with nothing to show
     * for it.
     *
     * [CharGrowth.rise] comes back in multiples of the ordinary sung-word lift,
     * and [CharGrowth.shift] in ems, so the caller converts once with the font
     * size it is actually drawing at.
     */
    fun sampleInto(charIndex: Int, positionMs: Long, into: CharGrowth) {
        val span = (endMs - startMs).coerceAtLeast(1L).toFloat()
        val elapsed = positionMs - startMs - charIndex * span * GROW_STAGGER
        val phase = (elapsed / (span * GROW_SPAN)).coerceIn(0f, 1f)
        val peakScale = scalePeak[charIndex]
        when {
            phase < GROW_IN -> {
                val t = smooth(phase / GROW_IN)
                into.scale = 1f + (peakScale - 1f) * t
                into.shift = shiftPeak[charIndex] * t
                into.rise = risePeak[charIndex] * t
                into.bloom = bloomPeak[charIndex] * t
            }
            phase < GROW_HOLD -> {
                into.scale = peakScale
                into.shift = shiftPeak[charIndex]
                into.rise = risePeak[charIndex]
                into.bloom = bloomPeak[charIndex]
            }
            phase < GROW_OUT -> {
                val t = smooth((phase - GROW_HOLD) / (GROW_OUT - GROW_HOLD))
                into.scale = peakScale + (1f - peakScale) * t
                into.shift = shiftPeak[charIndex] * (1f - t)
                into.rise = risePeak[charIndex] + (GROW_REST - risePeak[charIndex]) * t
                into.bloom = bloomPeak[charIndex] * (1f - t)
            }
            else -> {
                into.scale = 1f
                into.shift = 0f
                into.rise = GROW_REST
                into.bloom = 0f
            }
        }
    }
}

/** Where one letter of a [GrowingWord] is, filled in by [GrowingWord.sampleInto]. */
class CharGrowth {
    /** Swell, about the letter's own centre. 1 is the letter as laid out. */
    var scale: Float = 1f

    /** Lean away from the middle of the word, in ems. */
    var shift: Float = 0f

    /** Lift, in multiples of the ordinary sung-word rise. */
    var rise: Float = 0f

    /** Bloom, 0..1, at its own peak partway up rather than at the top. */
    var bloom: Float = 0f
}

/** How long a word takes to rise, and to settle back down once it is past. */
private const val RISE_MS = 700f

/** Ease in and out of the ends, so the lift has no corners on it. */
private fun smooth(fraction: Float) = fraction * fraction * (3f - 2f * fraction)

/**
 * Whether this word is held long enough, and is short enough, to be animated a
 * letter at a time.
 *
 * The thresholds are steeper for short words than the length alone suggests: a
 * two-letter word held for a second is a held note, whereas a seven-letter one
 * held for the same second is just being sung at an ordinary pace, and only the
 * first of those has anything to show.
 *
 * Scripts whose letters are not laid out side by side independently are left
 * alone — Han, kana and Hangul draw as blocks, and right-to-left scripts join
 * up, so moving one letter of a word would come apart rather than swell. A
 * hyphenated word is really two words and would break at the hyphen.
 */
private fun LyricWord.canGrow(): Boolean {
    val length = text.length
    if (length == 0 || length > GROW_MAX_CHARS) return false
    if ('-' in text || text.any { it.isBlockScript() || it.isJoinedScript() }) return false
    val held = endMs - startMs
    return when {
        length == 1 -> held >= GROW_MIN_SOLO_MS
        length <= 3 -> held >= GROW_MIN_SHORT_MS + (length - 2) * GROW_SHORT_STEP_MS
        length == 4 -> held >= GROW_MIN_FOUR_MS
        else -> held >= GROW_MIN_LONG_MS && held >= length * GROW_MS_PER_CHAR
    }
}

/**
 * How much of the movement is taken back off the later letters of a word.
 *
 * Two reasons to hold back. A long word is a lot of letters to run a wave
 * through, and left flat the tail of it is still swelling well after the singer
 * has moved on. A word held only just long enough to qualify has no room for a
 * wave at all, so the whole thing is damped towards a single swell.
 */
private fun decayRate(length: Int, heldMs: Float): Float {
    val long = length > GROW_DECAY_LONG_CHARS
    val quick = heldMs < GROW_DECAY_QUICK_MS
    if (!long && !quick) return 0f
    var strength = 0f
    if (long) {
        strength += minOf((length - GROW_DECAY_LONG_CHARS) / 5f, 1f) * GROW_DECAY_LONG
    }
    if (quick) {
        val short = maxOf(0f, 1f - (heldMs - GROW_DECAY_QUICK_FLOOR_MS) / 400f)
        strength += short * if (length > 3) GROW_DECAY_QUICK else GROW_DECAY_QUICK_TINY
    }
    return minOf(strength, GROW_DECAY_MAX)
}

private fun Char.isBlockScript(): Boolean =
    this in '一'..'鿿' || this in '぀'..'ゟ' ||
        this in '゠'..'ヿ' || this in '가'..'힯'

private fun Char.isJoinedScript(): Boolean =
    this in '֐'..'ࣿ'

/** Longest word worth running a wave through; past this it reads as a ripple. */
private const val GROW_MAX_CHARS = 7

/** How long a word of each length has to be held before it qualifies. */
private const val GROW_MIN_SOLO_MS = 1_100L
private const val GROW_MIN_SHORT_MS = 1_360L
private const val GROW_SHORT_STEP_MS = 140L
private const val GROW_MIN_FOUR_MS = 1_050L
private const val GROW_MIN_LONG_MS = 900L
private const val GROW_MS_PER_CHAR = 200L

private const val GROW_DECAY_LONG_CHARS = 5
private const val GROW_DECAY_QUICK_MS = 1_200f
private const val GROW_DECAY_QUICK_FLOOR_MS = 800f
private const val GROW_DECAY_LONG = 0.4f
private const val GROW_DECAY_QUICK = 0.3f
private const val GROW_DECAY_QUICK_TINY = 0.1f
private const val GROW_DECAY_MAX = 0.7f

/**
 * Each letter starts this far — as a share of the word's own length — after the
 * one before it, so the swell travels rather than pulsing.
 */
private const val GROW_STAGGER = 0.09f

/** The whole move runs half again as long as the word is held. */
private const val GROW_SPAN = 1.5f

/** Up by [GROW_IN], held to [GROW_HOLD], settled by [GROW_OUT]. */
private const val GROW_IN = 0.25f
private const val GROW_HOLD = 0.30f
private const val GROW_OUT = 0.75f

/** Where a letter comes to rest: the same small lift every sung word carries. */
private const val GROW_REST = 1f

/** The window over which a longer hold earns more of the treatment. */
private const val GROW_RAMP_MIN_MS = 400f
private const val GROW_RAMP_MAX_MS = 3_000f

/** Swell every qualifying letter gets, and the range the hold adds on top. */
private const val GROW_BASE_SHORT = 0.05f
private const val GROW_BASE_LONG = 0.04f
private const val GROW_SCALE_RANGE = 0.08f

/** The most any letter swells, which is what [GrowingWord] reads the lift off. */
private const val GROW_SCALE_CEILING = 0.1f

/** Taken back off the swell so it stops a hair short of the ceiling. */
private const val GROW_SCALE_TRIM = 0.98f

/** Lean, as a share of the font size at full swell. */
private const val GROW_SHIFT_EM = 25f / 34f

/** The bloom's floor, and what the hold adds to it. */
private const val GROW_BLOOM_FLOOR = 0.35f
private const val GROW_BLOOM_RANGE = 0.45f
private const val GROW_BLOOM_PACE_MS = 1_500f
private const val GROW_BLOOM_PACE_MAX = 1.1f
private const val GROW_BLOOM_SHORT = 0.85f
private const val GROW_BLOOM_LONG = 1.1f

/** A word held this long lifts as far as it is going to. */
private const val GROW_LIFT_PACE_MS = 2_000f
private const val GROW_LIFT_FLOOR = 0.3f
