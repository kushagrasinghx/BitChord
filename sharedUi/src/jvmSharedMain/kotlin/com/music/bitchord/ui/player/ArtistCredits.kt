package com.music.bitchord.ui.player

import com.music.bitchord.data.model.ArtistCredit
import com.music.bitchord.data.model.ArtistNameIndex
import com.music.bitchord.data.model.ArtistRef
import com.music.bitchord.data.model.CreditJoiner

/**
 * The credits to draw for a track, and how the line is cut into the stretches
 * that get drawn.
 *
 * [artists] first, and only [artist] as a fallback. A byline carries one
 * navigation endpoint per artist, and the parser has already filled in the ones
 * an earlier row left out (see [ArtistNameIndex]), so the names that come with
 * them open the page they belong to. A credit line that is only a string has no
 * such promise, and a name is not an identity — searching for one finds whoever
 * else answers to it, which is how tapping "2115" opened somebody else entirely.
 *
 * Checked against the live responses, where the two paths differ sharply. A
 * search row reads `2115, Bedoes 2115, White 2115` and links three separate
 * channels; the watch queue for the same collaboration reads
 * `Bedoes 2115 and Lanek` and links two. Splitting the second on commas and
 * ampersands yields one credit and no links at all — while `2115`, a channel of
 * its own distinct from both `Bedoes 2115` and `White 2115`, is a fragment of
 * neither.
 */
fun artistCredits(
    artist: String,
    artists: List<ArtistRef>,
    primaryArtistId: String?,
): List<ArtistCredit> {
    val stated = artists.filter { it.name.isNotBlank() }.map { credit ->
        ArtistCredit(name = credit.name, browseId = credit.browseId)
    }
    val credits = stated.ifEmpty { splitCreditLine(artist, primaryArtistId) }
    ArtistNameIndex.record(stated.map { ArtistRef(it.name, it.browseId) })
    return credits
}

/**
 * A credit line split into the people it credits, for rows that state no
 * per-artist links and that nothing else in the session has linked either.
 *
 * A last resort, and a worse one than the credits it stands in for: a name is
 * not an identity, so a split can only offer to *search* for whoever is behind
 * each fragment. Every credit it produces is marked as having no page of its own,
 * so nothing pretends otherwise and the reader's own search decides.
 *
 * The split is taken by exactly the rule BprimaryArtist()B uses — the same
 * separators, the same order, the same trim — so the browse id a track carries
 * for its main artist is never handed to a different name. The first credit is
 * that name and keeps the id; the rest have none.
 */
private fun splitCreditLine(artist: String, primaryArtistId: String?): List<ArtistCredit> {
    val text = artist.trim()
    if (text.isEmpty()) return emptyList()

    val cuts = CREDIT_SEPARATOR.findAll(text).toList()
    if (cuts.isEmpty()) {
        return listOf(ArtistCredit(name = text, browseId = primaryArtistId))
    }

    val out = mutableListOf<ArtistCredit>()
    var start = 0
    for (cut in cuts) {
        val name = text.substring(start, cut.range.first).trim()
        if (name.isNotEmpty()) {
            out += ArtistCredit(
                name = name,
                // The track's own id belongs to the lead, and to nobody else.
                browseId = if (out.isEmpty()) primaryArtistId else null,
            )
        }
        start = cut.range.last + 1
    }
    val tail = text.substring(start).trim()
    if (tail.isNotEmpty()) out += ArtistCredit(name = tail, browseId = null)
    if (out.isEmpty()) out += ArtistCredit(name = text, browseId = primaryArtistId)
    return out
}

/** The marks a byline joins credits with — the parser's own list, not a second one. */
private val CREDIT_SEPARATOR = CreditJoiner