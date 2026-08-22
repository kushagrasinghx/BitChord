package com.music.bitchord.data.canvas

import com.music.bitchord.data.Http
import okhttp3.Request
import java.text.Normalizer
import java.util.Locale

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
internal fun String.normalizeForMatch(): String =
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
internal fun splitArtists(raw: String): List<String> =
    raw.split(ARTIST_SEPARATORS)
        .map { it.normalizeForMatch() }
        .filter { it.isNotBlank() }

private val ARTIST_SEPARATORS = Regex(
    "(?:\\s*,\\s*|\\s*&\\s*|\\s+×\\s+|\\s+x\\s+|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bfeaturing\\b|\\bwith\\b)",
    RegexOption.IGNORE_CASE,
)

/**
 * A plain GET returning the body, or null for anything that isn't a 2xx or
 * that throws. Canvas is decoration: no provider failure is allowed to reach
 * the caller, and none of these hosts are ours to depend on.
 *
 * Shares [Http.client] with the rest of the app so these lookups reuse its
 * connection pool rather than standing up a second HTTP stack.
 */
internal fun canvasGet(url: String, headers: Map<String, String> = emptyMap()): String? {
    val request = Request.Builder().url(url).apply {
        headers.forEach { (name, value) -> header(name, value) }
    }.build()
    return runCatching {
        Http.client.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    }.getOrNull()
}

/** The browser UA these catalog endpoints expect; they 403 an unknown one. */
internal const val CANVAS_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/122.0.0.0 Safari/537.36"
