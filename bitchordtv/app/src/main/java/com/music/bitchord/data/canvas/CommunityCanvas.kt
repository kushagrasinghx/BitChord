package com.music.bitchord.data.canvas

import com.music.bitchord.data.DebugLog as Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A community-curated `song + artist -> looping video` index, published as one
 * JSON file and mirrored by whoever maintains it.
 *
 * The catalogue services only have motion artwork where a label paid to make
 * some, which is a thin slice of anything outside current chart releases. This
 * fills the gaps by hand: small, hit-or-miss, and the only source here that
 * ever covers back catalogue.
 *
 * One file for the whole index means one request and then local lookups, so
 * the manifest is held for [TTL_MS] rather than re-fetched per track.
 */
object CommunityCanvas {

    private const val TAG = "CommunityCanvas"
    private const val MANIFEST = "https://vivimusicanvas.mkmdevilmi.workers.dev/canvas.json"
    private const val TTL_MS = 30L * 60 * 1000

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private data class Entry(
        val song: String,
        val artist: String,
        val album: String,
        val url: String,
    )

    @Volatile private var entries: List<Entry> = emptyList()
    @Volatile private var fetchedAtMs = 0L

    fun search(title: String, artist: String, album: String?): CanvasArtwork? {
        val index = manifest().ifEmpty { return null }

        val wantTitle = title.normalizeForMatch()
        val wantArtist = artist.normalizeForMatch()
        val wantAlbum = album?.normalizeForMatch()

        // Contributors write titles as they please — "Song" against "Song
        // (Official Video)" — so this side matches on containment rather than
        // equality. That is looser than the catalogue providers get, and the
        // album check is what keeps it honest when we know one.
        val hit = index.firstOrNull { entry ->
            val song = entry.song.normalizeForMatch()
            val credited = entry.artist.normalizeForMatch()
            val listed = entry.album.normalizeForMatch()
            val titleOk = song.isNotBlank() &&
                (wantTitle.contains(song) || song.contains(wantTitle))
            val artistOk = credited.isNotBlank() &&
                (wantArtist.contains(credited) || credited.contains(wantArtist))
            val albumOk = listed.isBlank() || wantAlbum.isNullOrBlank() || listed == wantAlbum
            titleOk && artistOk && albumOk
        } ?: return null

        Log.d(TAG, "manifest hit for '${hit.song}' by '${hit.artist}'")
        return CanvasArtwork(
            url = hit.url,
            title = hit.song,
            artist = hit.artist,
            album = hit.album.takeIf { it.isNotBlank() },
        )
    }

    /**
     * Any clip contributed for this release, for the album page. The index is
     * keyed by song, so this takes the first track of the album someone has
     * covered — every clip on a release is usually the same loop anyway.
     */
    fun searchAlbum(album: String, artist: String): CanvasArtwork? {
        val index = manifest().ifEmpty { return null }

        val wantAlbum = album.normalizeForMatch()
        val wantArtist = artist.normalizeForMatch()
        if (wantAlbum.isBlank()) return null

        val hit = index.firstOrNull { entry ->
            val listed = entry.album.normalizeForMatch()
            val credited = entry.artist.normalizeForMatch()
            listed == wantAlbum && credited.isNotBlank() &&
                (wantArtist.contains(credited) || credited.contains(wantArtist))
        } ?: return null

        Log.d(TAG, "manifest hit for album '${hit.album}' by '${hit.artist}'")
        return CanvasArtwork(
            url = hit.url,
            title = hit.album,
            artist = hit.artist,
            album = hit.album,
        )
    }

    @Synchronized
    private fun manifest(): List<Entry> {
        val now = System.currentTimeMillis()
        if (entries.isNotEmpty() && now - fetchedAtMs < TTL_MS) return entries

        val body = canvasGet(MANIFEST, mapOf("User-Agent" to CANVAS_UA))
        if (body == null) {
            // Serve whatever we already have rather than losing canvas
            // entirely for the next half hour because one fetch failed.
            fetchedAtMs = now
            return entries
        }

        val parsed = runCatching {
            json.parseToJsonElement(body).jsonObject["items"]?.jsonArray
                ?.mapNotNull { item ->
                    val obj = item.jsonObject
                    Entry(
                        song = obj["song"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                        artist = obj["artist"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                        album = obj["album"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        url = obj["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                    )
                }
        }.getOrNull().orEmpty()

        if (parsed.isNotEmpty()) {
            Log.d(TAG, "manifest holds ${parsed.size} entries")
            entries = parsed
        }
        fetchedAtMs = now
        return entries
    }
}
