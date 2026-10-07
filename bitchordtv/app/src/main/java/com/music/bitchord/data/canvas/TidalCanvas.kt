package com.music.bitchord.data.canvas

import com.music.bitchord.data.DebugLog as Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.Locale

/**
 * Tidal's "video cover" — a square looping clip some albums ship instead of a
 * still sleeve.
 *
 * Read through the same public search endpoint Tidal's own embeddable player
 * uses, which needs no account: a track search returns the album object, the
 * album object carries a `videoCover` id, and that id expands into a fixed URL
 * on Tidal's CDN. Square and 1280px, so it drops into the sleeve without
 * cropping — the best-shaped source of the three.
 */
object TidalCanvas {

    private const val TAG = "TidalCanvas"
    private const val SEARCH = "https://api.tidal.com/v1/search"

    /** The token Tidal's public embed player ships; read-only, no account. */
    private const val EMBED_TOKEN = "vNVdglQOjFJJGG2U"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Tidal's catalogue is regional, and so is which albums have a cover. */
    private val countryCode: String by lazy {
        Locale.getDefault().country.takeIf { it.length == 2 }?.uppercase(Locale.ROOT) ?: "US"
    }

    fun search(title: String, artist: String, album: String?): CanvasArtwork? {
        val query = if (album.isNullOrBlank()) "$artist $title" else "$album $artist $title"
        val url = SEARCH.toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("limit", "10")
            .addQueryParameter("types", "TRACKS")
            .addQueryParameter("countryCode", countryCode)
            .build()
            .toString()

        val body = canvasGet(url, mapOf("X-Tidal-Token" to EMBED_TOKEN, "User-Agent" to CANVAS_UA))
            ?: return null
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null

        // The response carries a section per result type even when only one
        // was asked for — artists, albums and playlists all come back with an
        // empty `items`, so this has to name the section rather than hunt for
        // the first array that fits.
        val items = root["tracks"]?.jsonObject?.get("items")?.jsonArray ?: return null

        for (item in items) {
            val track = item as? JsonObject ?: continue
            val trackTitle = track["title"]?.jsonPrimitive?.contentOrNull ?: continue

            // Tidal credits artists as separate objects, so this is the one
            // service we don't have to guess a separator for.
            val artists = track["artists"]?.jsonArray
                ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
                .orEmpty()

            // Checked here rather than left to the caller's validation: a
            // search for one song returns ten, and taking the first that has
            // a cover would settle on the wrong track and stop looking — the
            // right one is often further down the list.
            if (!isMatch(trackTitle, artists, title, artist)) continue

            val albumObj = track["album"]?.jsonObject
            val videoCover = albumObj?.get("videoCover")?.jsonPrimitive?.contentOrNull
            if (videoCover.isNullOrBlank()) continue
            val videoUrl = coverUrl(videoCover) ?: continue

            Log.d(TAG, "video cover for '$trackTitle' by ${artists.joinToString()}")
            return CanvasArtwork(
                url = videoUrl,
                title = trackTitle,
                artist = artists.joinToString(", ").ifBlank { null },
                album = albumObj["title"]?.jsonPrimitive?.contentOrNull,
            )
        }
        return null
    }

    /**
     * The album's own video cover, for a release page rather than a track.
     *
     * Cheaper and more reliable than going via a track: the cover belongs to
     * the album in Tidal's model, so this asks for it directly instead of
     * finding a song that happens to sit on the right record.
     */
    fun searchAlbum(album: String, artist: String): CanvasArtwork? {
        val url = SEARCH.toHttpUrl().newBuilder()
            .addQueryParameter("query", "$album $artist")
            .addQueryParameter("limit", "10")
            .addQueryParameter("types", "ALBUMS")
            .addQueryParameter("countryCode", countryCode)
            .build()
            .toString()

        val body = canvasGet(url, mapOf("X-Tidal-Token" to EMBED_TOKEN, "User-Agent" to CANVAS_UA))
            ?: return null
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val items = root["albums"]?.jsonObject?.get("items")?.jsonArray ?: return null

        for (item in items) {
            val record = item as? JsonObject ?: continue
            val recordTitle = record["title"]?.jsonPrimitive?.contentOrNull ?: continue
            val artists = record["artists"]?.jsonArray
                ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
                .orEmpty()

            // Without this a search for "SOS" settles on "Ctrl", which also
            // has a cover and is also by SZA.
            if (!isMatch(recordTitle, artists, album, artist)) continue

            val videoCover = record["videoCover"]?.jsonPrimitive?.contentOrNull
            if (videoCover.isNullOrBlank()) continue
            val videoUrl = coverUrl(videoCover) ?: continue

            Log.d(TAG, "video cover for album '$recordTitle' by ${artists.joinToString()}")
            return CanvasArtwork(
                url = videoUrl,
                title = recordTitle,
                artist = artists.joinToString(", ").ifBlank { null },
                album = recordTitle,
            )
        }
        return null
    }

    /** Exact on the name, and every credited artist we asked for present. */
    private fun isMatch(
        gotName: String,
        gotArtists: List<String>,
        wantName: String,
        wantArtist: String,
    ): Boolean {
        if (gotName.normalizeForMatch() != wantName.normalizeForMatch()) return false
        val wanted = splitArtists(wantArtist)
        val credited = gotArtists.map { it.normalizeForMatch() }.filter { it.isNotBlank() }
        if (wanted.isEmpty() || credited.isEmpty()) return false
        return wanted.all { want -> credited.any { it == want } }
    }

    /**
     * A cover id is five dash-separated segments that spell out its path on
     * the CDN. Anything shaped differently is a format we don't know how to
     * address, so treat it as no cover at all rather than build a 404.
     */
    internal fun coverUrl(id: String): String? {
        val parts = id.split("-")
        if (parts.size != 5) return null
        return "https://resources.tidal.com/videos/${parts.joinToString("/")}/1280x1280.mp4"
    }
}
