package com.music.bitchord.data.model

import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Folds a name to the form two spellings of one artist have in common.
 *
 * ## Why this is not [com.music.bitchord.data.canvas.normalizeForMatch]
 *
 * That one is right for the Spotify-canvas matcher and wrong here, because it
 * reduces to `[^a-z0-9\s]` before comparing — every character that is not a
 * lowercase ASCII letter becomes a space. A Cyrillic, Greek, Devanagari or CJK
 * name therefore folds to the empty string, and every one of them folds to the
 * *same* empty string: asking for `Мата` would accept a page called `Мета`, and
 * `中島美嘉` would match anything at all. An exact-name check that does that is
 * not a check.
 *
 * So the folding here is case, accents and spacing, and nothing else. Letters of
 * every script survive it; only the marks a locale decorates its letters with,
 * and the case it writes them in, are let go.
 */
fun normalizedArtistName(raw: String): String =
    Normalizer.normalize(raw, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase(Locale.ROOT)
        .replace(REPEATED_SPACE, " ")
        .trim()

/** The marks a locale puts on its letters, which do not change who wrote them. */
private val COMBINING_MARKS = Regex("\\p{InCombiningDiacriticalMarks}+")

private val REPEATED_SPACE = Regex("\\s+")

/**
 * Whether a search result is the artist that was asked for, exactly.
 *
 * The rule [YtMusicRepository.findArtistPageId] applies, kept here so it can be
 * read and tested on its own rather than only through a network call.
 *
 * A blank [wanted] is refused rather than folded, because both sides fold to
 * nothing and every blank would then match every other blank — which is the one
 * case where the comparison could open a page nobody asked for.
 */
fun isExactArtistMatch(answer: String, wanted: String): Boolean {
    val name = normalizedArtistName(wanted)
    if (name.isEmpty()) return false
    return normalizedArtistName(answer) == name
}

/**
 * One name in a credit line.
 *
 * @property name Who it is, spelled as the source spells it.
 * @property browseId Their own channel, so the page that opens is theirs. Null
 * for a name the source stated without a channel behind it.
 */
data class ArtistCredit(
    val name: String,
    val browseId: String? = null,
)

/**
 * One stretch of the credit line as it will be drawn: either a credit, which can
 * be tapped, or plain text — the punctuation between credits — which cannot.
 *
 * A [credit] is null on the plain stretches. The plain text of a run is kept as
 * it was written rather than reassembled from separators, so what this line
 * draws is character for character what the credit line said.
 */
data class CreditSegment(
    val text: String,
    val credit: ArtistCredit? = null,
)

/**
 * Cuts [text] into the stretches it is drawn from, marking each place where a
 * credited name begins.
 *
 * The names are located in the line rather than the line being cut into names,
 * which is what keeps the drawing identical to a single string: the characters
 * that are not a name are emitted exactly as written, so a run that joins its
 * artists joined by a word draws with that word in it.
 *
 * The longest name wins at any position, so `2115, Bedoes 2115` marks both
 * credits rather than reading the first `2115` twice and losing the name that
 * contains it. Names are matched as written apart from case and surrounding
 * air, because YouTube's own byline and its channel titles spell the same
 * artist the same way and a looser match would put one artist's tap on
 * another's name.
 */
fun creditSegments(text: String, credits: List<ArtistCredit>): List<CreditSegment> {
    val names = credits
        .filter { it.name.isNotBlank() }
        .distinctBy { normalizedArtistName(it.name) }
        .sortedByDescending { it.name.length }
    if (text.isEmpty()) return emptyList()
    if (names.isEmpty()) return listOf(CreditSegment(text))

    val out = mutableListOf<CreditSegment>()
    val plain = StringBuilder()
    var index = 0
    while (index < text.length) {
        val hit = names.firstOrNull { text.startsWithIgnoringCase(it.name, index) }
        if (hit == null) {
            plain.append(text[index])
            index++
            continue
        }
        if (plain.isNotEmpty()) {
            out += CreditSegment(plain.toString())
            plain.clear()
        }
        // The line's own spelling of the name, not the credit's: matching
        // ignores case so that two spellings of one artist are one artist, but
        // what is drawn is still what the line said.
        out += CreditSegment(text.substring(index, index + hit.name.length), hit)
        index += hit.name.length
    }
    if (plain.isNotEmpty()) out += CreditSegment(plain.toString())
    return out
}

private fun String.startsWithIgnoringCase(prefix: String, at: Int): Boolean =
    regionMatches(at, prefix, 0, prefix.length, ignoreCase = true)

/**
 * What the app has learned about artists, from rows YouTube has already linked.
 *
 * ## Why this exists
 *
 * YouTube does not link every credited artist, and it does not do so
 * consistently. A real track's byline read `CBW and Monday Waxie` and linked
 * only CBW; on the same album another row read `CBW, White 2115, slowez` and
 * linked CBW and slowez, leaving out White 2115 — who has a channel, which a
 * third row on a different album duly stated. So a missing link is a gap in one
 * response, not a fact about the artist.
 *
 * Every link the parser reads is therefore kept here under the name it was
 * written with, and a row whose byline mentions an artist it did not link can be
 * completed from it. Nothing is invented: a name is only ever given a channel
 * that YouTube itself stated for that exact name, somewhere the app has looked.
 *
 * ## And the separators
 *
 * A byline that joins its artists with a word rather than a comma leaves that
 * word in the middle of the line looking exactly like a name that was never
 * linked. Which word it is depends on the locale and changes between requests,
 * so none of them is written down here: the text *between* two linked credits
 * is watched and remembered, and a line's leftover is only read as a missing
 * name when it is not one. That is what tells `CBW and Monday Waxie` apart
 * from a track credited to an artist actually called "and".
 *
 * ## Ambiguity
 *
 * Two different channels under one name means two artists share it, and which
 * one a row meant is unknowable from the row. The name is kept, marked as
 * ambiguous, and never used to complete a credit — the reader's own search
 * decides instead, which can ask them.
 */
object ArtistNameIndex {
    private val channels = ConcurrentHashMap<String, MutableSet<String>>()
    private val joiners = ConcurrentHashMap.newKeySet<String>()
    private val joinerTexts = ConcurrentHashMap.newKeySet<String>()
    private val joinerSightings = ConcurrentHashMap<String, Int>()

    /** Notes an artist YouTube linked, under the name it wrote. */
    fun record(refs: List<ArtistRef>) {
        refs.forEach { ref ->
            val name = normalizedArtistName(ref.name)
            val id = ref.browseId
            if (name.isEmpty() || id.isNullOrBlank()) return@forEach
            channels.computeIfAbsent(name) { ConcurrentHashMap.newKeySet() }.add(id)
        }
    }

    /**
     * Notes the text that separated two credited artists on a line.
     *
     * Only ever called with the stretches [creditSegments] left plain *between*
     * two credits. A mark is believed on sight. A word is not: an unlinked name
     * sitting between two linked credits is indistinguishable from a joiner in
     * any script, so a word has to turn up in the same place twice before it is
     * taken for one — and anything with a space or a digit in it never is.
     */
    fun recordJoiners(segments: List<CreditSegment>) {
        val creditAt = segments.withIndex().filter { it.value.credit != null }.map { it.index }
        if (creditAt.size < 2) return
        val first = creditAt.first()
        val last = creditAt.last()
        for (index in first + 1..last) {
            val segment = segments[index]
            if (segment.credit != null) continue
            // Only what could be punctuation. A stretch holding an unlinked name
            // arrives wrapped in whatever sits around it — `", White 2115, "`,
            // `・中島美嘉・` — and learning that as a joiner would rule out the
            // very name it is meant to let through.
            val key = normalizedArtistName(segment.text)
            if (key.isEmpty()) continue
            // Punctuation is a joiner on sight. A *word* is not, because one
            // unlinked name sitting between two linked credits looks exactly
            // like one — in any script, not only in the Latin one. `White 2115`
            // is excluded by its digit and a space, but `・中島美嘉・` has
            // neither, so a word has to be seen in the same place more than
            // once before it is believed.
            if (segment.text.any { it.isLetterOrDigit() }) {
                // More than one word is a phrase, not a joiner: `and Mata`
                // has a name in it and must never be learned as punctuation.
                if (key.any { it.isWhitespace() } || key.any { it.isDigit() }) continue
                val seen = joinerSightings.merge(key, 1, Int::plus) ?: 1
                if (seen < JOINER_WORD_SIGHTINGS) continue
            }
            joiners.add(key)
            joinerTexts.add(segment.text.trim())
        }
    }

    /**
     * The channel YouTube stated for [name], or null when it has never stated
     * one or has stated more than one.
     */
    fun lookup(name: String): String? {
        val ids = channels[normalizedArtistName(name)] ?: return null
        return ids.singleOrNull()
    }

    /** Whether [text] has been seen joining two credited names. */
    fun isJoiner(text: String): Boolean = normalizedArtistName(text) in joiners

    /**
     * The joiners as they were written, for cutting a line up on them.
     *
     * [joiners] is keyed by the folded form, which has lost the punctuation, so
     * it cannot be used to split a line - the word has to be cut on as written.
     */
    fun joinerTexts(): Set<String> = joinerTexts

    /**
     * Forgets everything learned.
     *
     * A test seam, so that one test cannot lean on rows another one happened to
     * parse first. Nothing in the app needs it: what is learnt is who an artist
     * is, which does not change with the account signed in.
     */
    fun forget() {
        channels.clear()
        joiners.clear()
        joinerTexts.clear()
        joinerSightings.clear()
    }
}

/**
 * Completes a byline's credits from what the app already knows.
 *
 * A name the source mentioned but did not link gets the channel another row
 * stated for that same name — and nothing else. A leftover that has been seen
 * joining two credits is punctuation and stays punctuation, which is what keeps
 * a word joiner from being read as an artist.
 *
 * Names that cannot be resolved this way are left out, so the reader's own
 * search (which opens a page only on an exact name) gets them instead.
 */
fun completeArtistCredits(
    line: String,
    stated: List<ArtistRef>,
): List<ArtistRef> {
    if (line.isBlank() || stated.isEmpty()) return stated
    val named = stated.filter { it.name.isNotBlank() }
    val segments = creditSegments(line, named.map { ArtistCredit(it.name, it.browseId) })
    ArtistNameIndex.recordJoiners(segments)

    // A channel already spent, so two names resolving to one artist is not
    // printed as two credits pointing at the same page.
    val used = mutableSetOf<String>()
    named.forEach { ref -> ref.browseId?.let(used::add) }
    val cutter = fragmentCutter()
    val out = mutableListOf<ArtistRef>()
    val placed = mutableSetOf<String>()

    // Walked in the order the line reads, not in the order the source linked the
    // names: a credit recovered from an earlier row has to land where the reader
    // sees it, between the credits either side of it.
    segments.forEach { segment ->
        val credit = segment.credit
        if (credit != null) {
            val ref = named.firstOrNull { it.name.equals(credit.name, ignoreCase = true) }
                ?: ArtistRef(credit.name, credit.browseId)
            if (placed.add(normalizedArtistName(ref.name))) out += ref
            return@forEach
        }
        fragmentsOf(segment.text, cutter).forEach { text ->
            if (ArtistNameIndex.isJoiner(text)) return@forEach
            val id = ArtistNameIndex.lookup(text) ?: return@forEach
            if (!used.add(id)) return@forEach
            if (placed.add(normalizedArtistName(text))) out += ArtistRef(name = text, browseId = id)
        }
    }

    // A credit the line never mentioned is still a credit the source stated, so
    // it is kept rather than dropped — there is nowhere in the line to put it.
    named.forEach { ref ->
        if (placed.add(normalizedArtistName(ref.name))) out += ref
    }
    return out
}

/**
 * The candidate names inside one stretch of a line that held no credit.
 *
 * A stretch has to stay whole for [creditSegments] — that is what keeps the line
 * drawn as it was written — which means a name the source mentioned but did not
 * link arrives wrapped in whatever sits around it: the middle of
 * `CBW, White 2115, slowez` is the single stretch `", White 2115, "`, and the
 * end of `Bedoes 2115 and Lanek`, once `Bedoes 2115` is linked, is the single
 * stretch `" and Lanek"`. Looking a stretch up whole finds nothing, so it is
 * cut here and each piece asked about on its own.
 *
 * The cut is on joiners only — the punctuation YouTube writes, and the words it
 * has been seen writing — so a stretch is never split in the middle of a name.
 */
private fun fragmentsOf(stretch: String, cutter: Regex): List<String> =
    stretch.split(cutter)
        .map { it.trim() }
        .filter { it.isNotEmpty() }

/**
 * Everything a line is known to be joined by: the punctuation, plus the words
 * [ArtistNameIndex] has watched sit between two credited names.
 */
private fun fragmentCutter(): Regex {
    val words = ArtistNameIndex.joinerTexts().filter { it.isNotBlank() }
    if (words.isEmpty()) return JOINER
    // A learned word is only ever a joiner when it stands alone between two
    // spaces, so the cut has to be anchored there. Cutting on the bare letters
    // would cut names in half — a learned `et` splits `Beta` into `B` and `a`,
    // and a learned `a` splits every name it appears in.
    val anchored = words.map { word -> "(?:^|(?<=\\s))\\s*${Regex.escape(word)}\\s*(?=\\s|\$)" }
    return Regex((anchored + listOf(JOINER.pattern)).joinToString("|"), RegexOption.IGNORE_CASE)
}

/**
 * The marks a byline joins credits and sections with, in every script.
 *
 * The comma and its full-width form, both ampersands, the bullet and the middle
 * dot, and the three marks Japanese and Chinese bylines use: `・`, `、` and `，`.
 * They are the marks rather than any word, because the words are written by the
 * locale and read off the response — see [ArtistNameIndex] — and none of these
 * can be a name.
 */
private val JOINER = Regex("""\s*,\s*|\s*，\s*|\s+&\s+|\s+＆\s+|\s*•\s*|\s*·\s*|\s*・\s*|\s*、\s*""")

/**
 * The marks a byline joins credits and sections with, for callers that cut a
 * credit line themselves.
 *
 * Exposed so there is one list. The player's fallback split and the parser's
 * completion have to agree on where a line is joined, or a name one of them can
 * see is a name the other cannot.
 */
val CreditJoiner = JOINER

/**
 * How many times a single unbroken word has to be seen between two credited
 * names before it is believed to be what joins them.
 *
 * Marks are taken on sight; a word is not, because in the scripts that write
 * names without spaces it cannot be told apart from a name at all — `・中島美嘉・`
 * between two linked credits is one unplayed name or one joiner, and only
 * seeing it twice says which.
 */
private const val JOINER_WORD_SIGHTINGS = 2
