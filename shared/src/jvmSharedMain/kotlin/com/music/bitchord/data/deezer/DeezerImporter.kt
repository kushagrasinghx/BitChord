package com.music.bitchord.data.deezer

import com.music.bitchord.data.Http
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.sources.TrackMatcher
import com.music.bitchord.data.spotify.SpotifyImportTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

object DeezerImporter {

    private const val API_BASE = "https://api.deezer.com"
    private const val PAGE_SIZE = 100
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** A Deezer playlist id is a positive numeric string. */
    private val PLAYLIST_ID = Regex("""\d+""")

    private val SHORT_LINK_HOSTS = setOf(
        "link.deezer.com",
        "dzr.page.link",
        "deezer.page.link",
    )

    private fun isDeezerHost(host: String) =
        host == "deezer.com" || host.endsWith(".deezer.com")

    fun isShortLink(input: String): Boolean {
        val uri = runCatching { java.net.URI(input.trim()) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return false
        val host = uri.host?.lowercase().orEmpty()
        return host in SHORT_LINK_HOSTS
    }

    /** Parses a raw user string or link into a Deezer playlist ID if valid without network calls. */
    fun extractPlaylistId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        if (trimmed.startsWith("deezer:playlist:")) {
            val id = trimmed.removePrefix("deezer:playlist:").substringBefore("?").substringBefore("/")
            return id.takeIf { PLAYLIST_ID.matches(it) }
        }

        val uri = runCatching { java.net.URI(trimmed) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
        if (!isDeezerHost(uri.host?.lowercase().orEmpty())) return null
        val segments = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
        val playlistIdx = segments.indexOf("playlist")
        if (playlistIdx == -1 || playlistIdx + 1 >= segments.size) return null
        return segments[playlistIdx + 1].takeIf { PLAYLIST_ID.matches(it) }
    }

    /**
     * Extracts a Deezer playlist ID directly from [input], or follows redirects when
     * [input] is a Deezer share short-link (`link.deezer.com`, `dzr.page.link`).
     */
    suspend fun resolvePlaylistId(input: String): String? = withContext(Dispatchers.IO) {
        extractPlaylistId(input)?.let { return@withContext it }
        if (!isShortLink(input)) return@withContext null
        // Network errors propagate: "could not reach Deezer" is not "invalid link".
        val url = input.trim().toHttpUrlOrNull() ?: return@withContext null
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        Http.client.newCall(request).execute().use { response ->
            extractPlaylistId(response.request.url.toString())
        }
    }

    /**
     * Fetches public Deezer playlist metadata and all of its tracks from `api.deezer.com`.
     */
    suspend fun fetchPlaylistTracks(playlistId: String): Pair<String, List<SpotifyImportTrack>> =
        withContext(Dispatchers.IO) {
            val metaRequest = Request.Builder()
                .url("$API_BASE/playlist/$playlistId")
                .header("User-Agent", USER_AGENT)
                .build()

            val metaJson = Http.client.newCall(metaRequest).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful || body.isBlank()) {
                    error("Failed to load Deezer playlist (HTTP ${response.code})")
                }
                body
            }

            val metaRoot = json.parseToJsonElement(metaJson).jsonObject
            metaRoot["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull?.let { msg ->
                error(msg)
            }

            val playlistTitle = metaRoot["title"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() }
                ?: "Imported Deezer Playlist"

            val tracks = mutableListOf<SpotifyImportTrack>()
            var offset = 0
            while (true) {
                val tracksUrl = "$API_BASE/playlist/$playlistId/tracks".toHttpUrl().newBuilder()
                    .addQueryParameter("index", offset.toString())
                    .addQueryParameter("limit", PAGE_SIZE.toString())
                    .build()
                val tracksReq = Request.Builder()
                    .url(tracksUrl)
                    .header("User-Agent", USER_AGENT)
                    .build()
                val tracksBody = Http.client.newCall(tracksReq).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    response.body.string()
                }
                val pageRoot = json.parseToJsonElement(tracksBody).jsonObject
                // Errors that come back as HTTP 200 JSON (private, rate limited) throw in the parser.
                val (items, total, rawCount) = parseApiTrackPage(pageRoot)
                for (item in items) {
                    if (item.title.isNotBlank()) {
                        tracks.add(
                            SpotifyImportTrack(
                                title = item.title.trim(),
                                artist = item.artist.trim(),
                                durationSec = item.durationMs.takeIf { it > 0 }?.div(1000),
                                album = item.album,
                            ),
                        )
                    }
                }
                offset += rawCount
                if (rawCount == 0 || offset >= total) break
            }

            if (tracks.isEmpty()) {
                error("No tracks found in public Deezer playlist.")
            }

            Pair(playlistTitle, tracks)
        }

    /**
     * The YouTube Music song that matches [track], or null if none is found.
     */
    suspend fun matchTrack(track: DeezerTrack): Song? {
        val query = listOf(track.title, track.artist).filter { it.isNotBlank() }.joinToString(" ")
        val candidates = YtMusicRepository.search(query, SearchFilter.SONGS).getOrNull()
            ?.filterIsInstance<SearchResult.Track>()
            ?.map { it.song }
            .orEmpty()
        return TrackMatcher.best(
            candidates,
            TrackMatcher.Target(
                title = track.title,
                artist = track.artist,
                durationSec = track.durationMs.takeIf { it > 0 }?.div(1000),
                album = track.album,
            ),
        ) ?: candidates.firstOrNull()
    }
}
