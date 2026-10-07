package com.music.bitchord.data.canvas

import java.text.Normalizer
import java.util.Locale

/**
 * Which provider a clip came from — read in one place, by
 * [CanvasArtworkPlayer][com.music.bitchord.ui.player.CanvasArtworkPlayer]'s
 * caller, to decide whether the backdrop should keep re-tinting itself off
 * the clip as it loops rather than settling on its first frame. See that
 * call site for why Spotify's Canvas gets the periodic re-tint and the other
 * three don't.
 */
enum class CanvasSource { SPOTIFY, OTHER }

/**
 * A looping video that stands in for a track's cover art — what Spotify calls
 * a Canvas and Apple calls motion artwork.
 *
 * [url] is what the player mounts; [fallbackUrl] is tried once if that errors,
 * which is how the Apple provider hands over a second rendition of the same
 * clip when the preferred one won't decode. The three metadata fields are not
 * decoration: providers search by free text and will happily return the wrong
 * album's clip, so [matches] re-checks the answer against what's playing.
 */
data class CanvasArtwork(
    val url: String,
    val fallbackUrl: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val source: CanvasSource = CanvasSource.OTHER,
) {
    /**
     * Whether this clip really belongs to the track we asked about.
     *
     * Title and artists must match exactly once punctuation, case and accents
     * are stripped — a near miss here is a different song by the same artist,
     * which is the one failure mode users notice. The album is only held to
     * that standard when both sides know it: BitChord resolves a track's album
     * asynchronously after the player opens, and a track queued from search
     * may never get one, so requiring it outright would mean no canvas at all
     * for most of a session.
     */
    fun matches(wantTitle: String, wantArtist: String, wantAlbum: String?): Boolean {
        val titleOk = title == null || wantTitle.isBlank() ||
            title.normalizeForMatch() == wantTitle.normalizeForMatch()

        val titleArtists = splitArtists(wantArtist)
        val ourArtists = splitArtists(artist.orEmpty())
        val artistOk = artist == null || wantArtist.isBlank() ||
            (titleArtists.isNotEmpty() && ourArtists.isNotEmpty() &&
                titleArtists.all { want -> ourArtists.any { it == want } })

        val albumOk = album.isNullOrBlank() || wantAlbum.isNullOrBlank() ||
            album.normalizeForMatch() == wantAlbum.normalizeForMatch()

        return titleOk && artistOk && albumOk
    }
}

/**
 * Case, accents and punctuation all differ between YouTube Music, Apple and
 * Tidal for the same release — "Beyoncé - CRAZY IN LOVE (feat. JAY-Z)" against
 * "Beyonce Crazy in Love feat Jay Z". Fold all three away before comparing.
 */
fun String.normalizeForMatch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

/**
 * One credit string into its individual artists. Every service picks its own
 * separator — commas, ampersands, "feat.", a bare "x" between collaborators —
 * so comparing the joined strings would fail on formatting alone.
 */
fun splitArtists(raw: String): List<String> =
    raw.split(ARTIST_SEPARATORS)
        .map { it.normalizeForMatch() }
        .filter { it.isNotBlank() }

private val ARTIST_SEPARATORS = Regex(
    "(?:\\s*,\\s*|\\s*&\\s*|\\s+×\\s+|\\s+x\\s+|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bfeaturing\\b|\\bwith\\b)",
    RegexOption.IGNORE_CASE,
)
