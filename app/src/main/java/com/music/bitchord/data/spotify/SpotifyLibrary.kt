package com.music.bitchord.data.spotify

import com.music.bitchord.data.Http
import com.music.bitchord.data.canvas.CANVAS_UA
import com.music.bitchord.data.canvas.SpotifyToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class SpotifyPlaylist(
    val id: String,
    val name: String,
    val owner: String?,
    val imageUrl: String?,
)

data class SpotifyTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Int,
    val imageUrl: String?,
)

object SpotifyLibrary {
    private const val GQL = "https://api-partner.spotify.com/pathfinder/v2/query"
    private const val LIBRARY = "973e511ca44261fda7eebac8b653155e7caee3675abb4fb110cc1b8c78b091c3"
    private const val PLAYLIST = "346811f856fb0b7e4f6c59f8ebea78dd081c6e2fb01b77c954b26259d5fc6763"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun playlists(): List<SpotifyPlaylist> = withContext(Dispatchers.IO) {
        val headers = authHeaders()
        val collected = mutableListOf<SpotifyPlaylist>()
        var offset = 0
        var pageIndex = 0
        while (pageIndex < 20) {
            val page = playlistPage(headers, offset)
            collected += page.first
            offset += page.first.size
            pageIndex++
            if (page.first.size < 50 || offset >= page.second) break
        }
        collected
    }

    suspend fun tracks(playlistId: String): List<SpotifyTrack> = withContext(Dispatchers.IO) {
        val headers = authHeaders()
        val collected = mutableListOf<SpotifyTrack>()
        var offset = 0
        var pageIndex = 0
        while (pageIndex < 10) {
            val page = trackPage(headers, playlistId, offset)
            collected += page.first
            offset += page.first.size
            pageIndex++
            if (page.first.size < 100 || offset >= page.second) break
        }
        collected
    }

    private fun playlistPage(headers: Map<String, String>, offset: Int): Pair<List<SpotifyPlaylist>, Int> {
        val variables = buildJsonObject {
            putJsonArray("filters") { add("Playlists") }
            put("order", null as String?)
            put("textFilter", "")
            putJsonArray("features") {
                add("LIKED_SONGS")
                add("YOUR_EPISODES_V2")
                add("PRERELEASES")
                add("EVENTS")
            }
            put("limit", 50)
            put("offset", offset)
            put("flatten", true)
            putJsonArray("expandedFolders") {}
            put("folderUri", null as String?)
            put("includeFoldersWhenFlattening", false)
        }
        return parsePlaylistPage(gql("libraryV3", LIBRARY, variables, headers))
    }

    private fun trackPage(
        headers: Map<String, String>,
        playlistId: String,
        offset: Int,
    ): Pair<List<SpotifyTrack>, Int> {
        val variables = buildJsonObject {
            put("uri", "spotify:playlist:$playlistId")
            put("offset", offset)
            put("limit", 100)
            put("enableWatchFeedEntrypoint", false)
        }
        return parseTrackPage(gql("fetchPlaylist", PLAYLIST, variables, headers))
    }

    private suspend fun authHeaders(): Map<String, String> {
        val token = SpotifyToken.accessToken()
            ?: throw IllegalStateException("Spotify sign-in expired")
        return buildMap {
            put("Authorization", "Bearer $token")
            put("Accept", "application/json")
            put("app-platform", "WebPlayer")
            put("Origin", "https://open.spotify.com")
            put("Referer", "https://open.spotify.com/")
            put("User-Agent", CANVAS_UA)
            SpotifyToken.clientToken()?.let { put("Client-Token", it) }
        }
    }

    private fun gql(operation: String, hash: String, variables: JsonObject, headers: Map<String, String>): JsonObject {
        val body = buildJsonObject {
            put("variables", variables)
            put("operationName", operation)
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("version", 1)
                    put("sha256Hash", hash)
                }
            }
        }
        val request = Request.Builder()
            .url(GQL)
            .post(body.toString().toRequestBody(mediaType))
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .build()
        val text = Http.client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Spotify $operation failed (${response.code})")
            }
            payload
        }
        val root = json.parseToJsonElement(text).jsonObject
        val error = root.arr("errors")?.firstOrNull()?.jsonObject?.str("message")
        if (!error.isNullOrBlank()) throw IllegalStateException(error)
        return root
    }
}

internal fun parsePlaylistPage(root: JsonObject): Pair<List<SpotifyPlaylist>, Int> {
    val library = root.obj("data")?.obj("me")?.obj("libraryV3")
        ?: throw IllegalStateException("Spotify library response was empty")
    val items = library.arr("items").orEmpty().mapNotNull { element ->
        val wrapper = element.jsonObject.obj("item") ?: return@mapNotNull null
        val typeName = wrapper.str("__typename").orEmpty()
        if (!typeName.contains("Playlist", ignoreCase = true)) return@mapNotNull null
        val data = wrapper.obj("data") ?: return@mapNotNull null
        if (data.str("__typename") != "Playlist") return@mapNotNull null
        val uri = wrapper.str("_uri") ?: return@mapNotNull null
        val name = data.str("name").orEmpty()
        if (name.isBlank()) return@mapNotNull null
        SpotifyPlaylist(
            id = uri.substringAfterLast(":"),
            name = name,
            owner = data.obj("ownerV2")?.obj("data")?.str("name"),
            imageUrl = coverUrl(data.obj("images")),
        )
    }
    return items to (library.int("totalCount") ?: items.size)
}

internal fun parseTrackPage(root: JsonObject): Pair<List<SpotifyTrack>, Int> {
    val content = root.obj("data")?.obj("playlistV2")?.obj("content")
        ?: throw IllegalStateException("Spotify playlist response was empty")
    val items = content.arr("items").orEmpty().mapNotNull { element ->
        val data = element.jsonObject.obj("itemV2")?.obj("data") ?: return@mapNotNull null
        val title = data.str("name").orEmpty()
        if (title.isBlank()) return@mapNotNull null
        val uri = data.str("uri") ?: data.str("_uri") ?: return@mapNotNull null
        val artist = data.obj("artists")?.arr("items").orEmpty().mapNotNull { artist ->
            artist.jsonObject.obj("profile")?.str("name")?.takeIf { it.isNotBlank() }
        }.joinToString(", ")
        val album = data.obj("albumOfTrack")
        SpotifyTrack(
            id = uri.substringAfterLast(":"),
            title = title,
            artist = artist,
            album = album?.str("name"),
            durationMs = data.obj("duration")?.int("totalMilliseconds") ?: 0,
            imageUrl = album?.obj("coverArt")?.arr("sources")?.lastUrl(),
        )
    }
    return items to (content.int("totalCount") ?: items.size)
}

private fun coverUrl(images: JsonObject?): String? =
    images?.arr("items").orEmpty()
        .mapNotNull { it.jsonObject.arr("sources")?.lastUrl() }
        .lastOrNull()

private fun JsonArray.lastUrl(): String? =
    mapNotNull { it.jsonObject.str("url") }.lastOrNull()

private fun JsonObject.obj(key: String): JsonObject? =
    this[key]?.takeIf { it !is JsonNull }?.let { runCatching { it.jsonObject }.getOrNull() }

private fun JsonObject.arr(key: String): JsonArray? =
    this[key]?.takeIf { it !is JsonNull }?.let { runCatching { it.jsonArray }.getOrNull() }

private fun JsonObject.str(key: String): String? =
    this[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull

private fun JsonObject.int(key: String): Int? =
    this[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.intOrNull
