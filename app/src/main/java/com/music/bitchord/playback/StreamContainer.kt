package com.music.bitchord.playback

import androidx.media3.common.MimeTypes
import java.util.concurrent.ConcurrentHashMap
import java.util.Locale

/**
 * Whether the URL serving a track points at audio or at an index of it.
 *
 * A source module's stream URL is whatever its backend feels like serving, and
 * that is not always a file. Tidal answers a lossless or Dolby Atmos request
 * with an MPEG-DASH manifest, and answered the same request with an HLS one
 * until September 2026 — same track, same tier, no version change on our side.
 * Media3 plays both perfectly well, through `DashMediaSource` and
 * `HlsMediaSource`. What it will not do is work out which of those to build
 * after the fact: `DefaultMediaSourceFactory` chooses off the [MediaItem]
 * before a byte is fetched, and this app's playback URIs are
 * `bitchord://watch?v=…` and `bitchord://source?…`. Neither carries an
 * extension to infer from, so the choice defaults to a progressive source — and
 * a progressive source handed a manifest fails on the only error it can raise:
 * it offers the XML to every extractor it has and none of them can sniff it.
 *
 * ```
 *   15:11:53  substituted: 'What Did I Miss?' served by Ricky's Addon over YouTube at Dolby Atmos
 *   15:11:54  playback failed for Lx4gPURH35g at 0ms (ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED)
 *             None of the available extractors (FlvExtractor, FlacExtractor, …) could read the stream
 *             sniff failures: [NoDeclaredBrand, NoDeclaredBrand]
 * ```
 *
 * That URL was not broken, which is the whole reason this exists. Sixteen
 * seconds later the identical URL — same signature, same expiry — played as
 * Dolby Atmos, because it arrived through [QualityUpgrade]'s swap, the one path
 * that declared the type. The failure above was read as a dead stream instead:
 * the track was struck off substitution *and* off automatic upgrades for the
 * session, and only the player menu's "Upgrade quality" could clear that.
 *
 * So the question "what is on the end of this URL" gets one answer, asked in
 * three places — where an upgrade builds its item, where the live path decides
 * whether it may substitute at all, and where a track that failed anyway is put
 * back on its feet.
 *
 * Judged on the extension, deliberately, and not on what the module said the
 * codec was: `format: "flac"` describes the audio inside and says nothing about
 * the envelope the URL points at.
 */
object StreamContainer {

    /**
     * The mime type Media3 needs to be told for [url], or null when it points
     * at ordinary audio and needs no telling.
     *
     * A source that *said* what the URL is outranks the extension — see
     * [declare]. The extension is the fallback, and remains the only answer
     * available for a source that says nothing.
     */
    fun manifestMimeOf(url: String): String? =
        declared[url]
            ?: when (url.substringBefore('?').substringAfterLast('.').lowercase(Locale.ROOT)) {
                "m3u8" -> MimeTypes.APPLICATION_M3U8
                "mpd" -> MimeTypes.APPLICATION_MPD
                else -> null
            }

    /**
     * Records that [url] carries [transport] — `hls` or `dash` — because the
     * source that handed it over said so.
     *
     * Judging on the extension is a guess, and it is wrong in exactly the case
     * this app now meets most often: an addon that serves an HLS playlist from
     * an extensionless path. The reference addon does this at `/dash/{id}`,
     * which has no `.m3u8` to read, is not a DASH manifest despite the name,
     * and reports `format: "flac"` — three ways of looking like a plain FLAC
     * file while being an index of one. Handed to a progressive source, that
     * fails on the only error it can raise, and the recovery path that would
     * normally re-prepare with a declared type has nothing to declare either,
     * because it asks this same question.
     *
     * So a source that knows says so, once, and every existing caller —
     * the item builder, the may-I-substitute check, the failure recovery —
     * gets the right answer without any of them learning about addons.
     *
     * Keyed by URL rather than by media id because that is what all three
     * callers hold, and because a signed URL is already unique to one
     * resolution of one track. Bounded the same cheap way as [serving]: this is
     * an optimisation over sniffing, and losing an entry costs a guess, not a
     * track.
     */
    fun declare(url: String, transport: String) {
        val mime = when (transport.lowercase(Locale.ROOT)) {
            "hls" -> MimeTypes.APPLICATION_M3U8
            "dash" -> MimeTypes.APPLICATION_MPD
            else -> return
        }
        if (declared.size >= MAX_REMEMBERED) declared.clear()
        declared[url] = mime
    }

    /**
     * What a source stated about a URL, where it stated anything.
     *
     * Separate from [serving] because the two answer different questions —
     * this one is "what is this URL", which is true wherever the URL turns up,
     * and that one is "what is this track being served right now", which is
     * only true until the track moves.
     */
    private val declared = ConcurrentHashMap<String, String>()

    /** Whether [url] is an index of the audio rather than the audio. */
    fun isManifest(url: String) = manifestMimeOf(url) != null

    /**
     * What each track was last actually served, keyed by media id.
     *
     * The resolving data source is the only place that knows: everything above
     * it sees `bitchord://…` and everything below it sees bytes. Recorded on
     * every resolve rather than only the interesting ones, so the entry is
     * never a stale claim about a track that has since moved to another source
     * — last write wins, and the last write is what is playing.
     */
    private val serving = ConcurrentHashMap<String, String>()

    /** Records that [mediaId] is being served from [url]. */
    fun served(mediaId: String, url: String) {
        // Bounded rather than pruned: nothing here is a promise the way
        // [StreamChoice]'s entries are, so the cheap eviction is the right one.
        if (serving.size >= MAX_REMEMBERED) serving.clear()
        serving[mediaId] = url
    }

    /**
     * The mime type [mediaId]'s current stream needed and did not get, or null
     * if it is not being served a manifest.
     */
    fun manifestServing(mediaId: String): String? = serving[mediaId]?.let(::manifestMimeOf)

    private const val MAX_REMEMBERED = 64
}
