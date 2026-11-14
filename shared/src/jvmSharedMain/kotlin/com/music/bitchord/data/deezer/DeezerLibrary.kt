package com.music.bitchord.data.deezer

import com.music.bitchord.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** A Deezer playlist's page id in the detail stack: this plus the playlist id (or [DeezerLibrary.LOVED_ID]). */
const val DEEZER_PAGE_PREFIX = "deezer:playlist:"

data class DeezerPlaylist(
    val id: String,
    val name: String,
    val owner: String?,
    val imageUrl: String?,
    /** The account's Favorite Tracks entry; [name] is then only a fallback, the UI localizes it. */
    val isLoved: Boolean = false,
)

data class DeezerTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Int,
    val imageUrl: String?,
)

object DeezerLibrary {
    /**
     * Where the `arl` session cookie comes from: Android stores it in [AppSettings]
     * from an in-app WebView login or manual token entry. Null or blank means signed out.
     */
    fun interface Auth {
        suspend fun arl(): String?
    }

    /** Installed by each application at start-up; until then every call fails as signed out. */
    @Volatile
    var auth: Auth? = null

    /** Pseudo-playlist id for Favorite Tracks ("Loved Tracks") when no numeric id is known yet. */
    const val LOVED_ID = "loved"

    private const val GW_LIGHT = "https://www.deezer.com/ajax/gw-light.php"
    private const val API_BASE = "https://api.deezer.com"
    private const val CDN_IMAGES = "https://cdn-images.dzcdn.net/images"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/122.0.0.0 Safari/537.36"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    internal data class Session(
        val arl: String,
        val sid: String?,
        val apiToken: String,
        val userId: String,
        val lovedTracksId: String?,
    ) {
        fun cookieHeader(): String =
            if (sid.isNullOrBlank()) "arl=$arl" else "arl=$arl; sid=$sid"
    }

    /** Every playlist in the signed-in library, with Favorite Tracks offered first. */
    suspend fun playlists(): List<DeezerPlaylist> = withContext(Dispatchers.IO) {
        val session = openSession()
        val byId = LinkedHashMap<String, DeezerPlaylist>()

        // 1. Fetch from gw-light.php (includes private and saved playlists).
        val gw = attempt {
            val body = buildJsonObject {
                put("user_id", session.userId.toLongOrNull() ?: 0L)
                put("tab", "playlists")
                put("nb", 2000)
            }
            val root = gwCall("deezer.pageProfile", session.apiToken, body, session.cookieHeader())
            parseProfilePlaylists(root)
        }
        gw.getOrNull()?.forEach { playlist ->
            byId[playlist.id] = playlist
        }

        // 2. Also fetch from the REST endpoint so public/created playlists are always present
        // even if pageProfile changes shape or omits entries.
        val rest = attempt {
            fetchPublicUserPlaylists(session.userId, session.cookieHeader())
        }
        rest.getOrNull()?.forEach { playlist ->
            if (playlist.id !in byId) {
                byId[playlist.id] = playlist
            }
        }
        // Only when both sources failed is the list untrustworthy; one is enough to show.
        if (gw.isFailure && rest.isFailure) throw gw.exceptionOrNull()!!

        val lovedId = session.lovedTracksId?.takeIf { it.isNotBlank() && it != "0" }
        val existingLoved = lovedId?.let { byId.remove(it) }
        val favoriteEntry = DeezerPlaylist(
            id = lovedId ?: LOVED_ID,
            name = "Favorite Tracks",
            owner = null,
            imageUrl = existingLoved?.imageUrl,
            isLoved = true,
        )
        // The loved playlist is removed by id above, not by name: a user's own
        // playlist may legitimately be called "Favorite Tracks".
        listOf(favoriteEntry) + byId.values
    }

    /**
     * Every track of a playlist, paged in chunks. [onPage] receives the running list
     * after each page so large playlists populate progressively.
     */
    suspend fun tracks(
        playlistId: String,
        onPage: (List<DeezerTrack>) -> Unit = {},
    ): List<DeezerTrack> = withContext(Dispatchers.IO) {
        val sessionResult = attempt { openSession() }
        // Favorites only exist behind the session, so its real error (network
        // or expired sign-in) is the one to show; other playlists can fall back.
        val session = if (playlistId == LOVED_ID) sessionResult.getOrThrow() else sessionResult.getOrNull()
        val resolvedId = if (playlistId == LOVED_ID) {
            // A signed-in account with nothing loved yet has no favorites playlist.
            session?.lovedTracksId ?: return@withContext emptyList()
        } else {
            playlistId
        }

        if (session != null) {
            val gwTracks = attempt {
                fetchGwPlaylistTracks(resolvedId, session, onPage)
            }.getOrNull()
            if (gwTracks != null) return@withContext gwTracks
        }

        fetchApiPlaylistTracks(resolvedId, session?.cookieHeader(), onPage)
    }

    /**
     * The playlist's high-resolution cover art (1000x1000 when available).
     */
    suspend fun cover(playlistId: String): String? = withContext(Dispatchers.IO) {
        if (playlistId == LOVED_ID) return@withContext null
        val session = attempt { openSession() }.getOrNull()
        attempt {
            val request = Request.Builder()
                .url("$API_BASE/playlist/$playlistId")
                .header("User-Agent", USER_AGENT)
                .apply { session?.cookieHeader()?.let { header("Cookie", it) } }
                .build()
            val text = Http.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body.string()
            } ?: return@attempt null
            val root = json.parseToJsonElement(text).jsonObject
            root.str("picture_xl") ?: root.str("picture_big") ?: root.str("picture_medium")
        }.getOrNull()
    }

    /** [runCatching] that lets cancellation through instead of turning it into a failure. */
    private inline fun <T> attempt(block: () -> T): Result<T> =
        runCatching(block).onFailure { if (it is kotlin.coroutines.cancellation.CancellationException) throw it }

    private suspend fun openSession(): Session {
        val rawArl = auth?.arl()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException("Deezer sign-in expired")

        val url = GW_LIGHT.toHttpUrl().newBuilder()
            .addQueryParameter("method", "deezer.getUserData")
            .addQueryParameter("input", "3")
            .addQueryParameter("api_version", "1.0")
            .addQueryParameter("api_token", "null")
            .build()
        val request = Request.Builder()
            .url(url)
            .post("{}".toRequestBody(mediaType))
            .header("Cookie", "arl=$rawArl")
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .header("Origin", "https://www.deezer.com")
            .header("Referer", "https://www.deezer.com/")
            .build()

        var sid: String? = null
        val text = Http.client.newCall(request).execute().use { response ->
            val payload = response.body.string()
            if (!response.isSuccessful) {
                throw IllegalStateException("Deezer session check failed (${response.code})")
            }
            sid = response.headers("Set-Cookie")
                .firstNotNullOfOrNull { header ->
                    header.split(";")
                        .map { it.trim() }
                        .firstOrNull { it.startsWith("sid=") }
                        ?.substringAfter("sid=")
                        ?.takeIf { it.isNotBlank() }
                }
            payload
        }
        val root = json.parseToJsonElement(text).jsonObject
        return parseUserSession(root, rawArl, sid)
    }

    private fun fetchGwPlaylistTracks(
        playlistId: String,
        session: Session,
        onPage: (List<DeezerTrack>) -> Unit,
    ): List<DeezerTrack> {
        val collected = mutableListOf<DeezerTrack>()
        var offset = 0
        val pageSize = 200
        while (true) {
            val body = buildJsonObject {
                put("playlist_id", playlistId)
                put("nb", pageSize)
                put("start", offset)
            }
            val root = gwCall("playlist.getSongs", session.apiToken, body, session.cookieHeader())
            val (items, total, rawCount) = parseGwTrackPage(root)
            collected += items
            offset += rawCount
            onPage(collected.toList())
            if (rawCount == 0 || offset >= total) break
        }
        return collected
    }

    private fun fetchApiPlaylistTracks(
        playlistId: String,
        cookieHeader: String?,
        onPage: (List<DeezerTrack>) -> Unit,
    ): List<DeezerTrack> {
        val collected = mutableListOf<DeezerTrack>()
        var offset = 0
        val limit = 100
        while (true) {
            val url = "$API_BASE/playlist/$playlistId/tracks".toHttpUrl().newBuilder()
                .addQueryParameter("index", offset.toString())
                .addQueryParameter("limit", limit.toString())
                .build()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .apply { cookieHeader?.let { header("Cookie", it) } }
                .build()
            val text = Http.client.newCall(request).execute().use { response ->
                val payload = response.body.string()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Deezer playlist failed (${response.code})")
                }
                payload
            }
            val root = json.parseToJsonElement(text).jsonObject
            val (items, total, rawCount) = parseApiTrackPage(root)
            collected += items
            offset += rawCount
            onPage(collected.toList())
            if (rawCount == 0 || offset >= total) break
        }
        return collected
    }

    private fun fetchPublicUserPlaylists(userId: String, cookieHeader: String): List<DeezerPlaylist> {
        val collected = mutableListOf<DeezerPlaylist>()
        var offset = 0
        val limit = 100
        while (true) {
            val url = "$API_BASE/user/$userId/playlists".toHttpUrl().newBuilder()
                .addQueryParameter("index", offset.toString())
                .addQueryParameter("limit", limit.toString())
                .build()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Cookie", cookieHeader)
                .build()
            // A failure throws rather than ending the list, so [playlists] can
            // tell a failed source from a short one.
            val text = Http.client.newCall(request).execute().use { response ->
                val payload = response.body.string()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Deezer playlists failed (${response.code})")
                }
                payload
            }
            val root = json.parseToJsonElement(text).jsonObject
            val (items, total, rawCount) = parseApiPlaylistPage(root)
            collected += items
            offset += rawCount
            if (rawCount == 0 || offset >= total) break
        }
        return collected
    }

    private fun gwCall(
        method: String,
        apiToken: String,
        body: JsonObject,
        cookieHeader: String,
    ): JsonObject {
        val url = GW_LIGHT.toHttpUrl().newBuilder()
            .addQueryParameter("method", method)
            .addQueryParameter("input", "3")
            .addQueryParameter("api_version", "1.0")
            .addQueryParameter("api_token", apiToken)
            .build()
        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(mediaType))
            .header("Cookie", cookieHeader)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .header("Origin", "https://www.deezer.com")
            .header("Referer", "https://www.deezer.com/")
            .build()
        val text = Http.client.newCall(request).execute().use { response ->
            val payload = response.body.string()
            if (!response.isSuccessful) {
                throw IllegalStateException("Deezer $method failed (${response.code})")
            }
            payload
        }
        val root = json.parseToJsonElement(text).jsonObject
        val errorObj = root.obj("error")
        if (errorObj != null && errorObj.isNotEmpty()) {
            val message = errorObj.values.firstOrNull()?.jsonPrimitive?.contentOrNull
                ?: errorObj.keys.firstOrNull()
                ?: "Deezer request failed"
            throw IllegalStateException(message)
        }
        return root
    }

    internal fun pictureUrl(type: String, md5: String?, size: Int = 500): String? {
        val hash = md5?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return "$CDN_IMAGES/$type/$hash/${size}x${size}-000000-80-0-0.jpg"
    }
}

internal fun parseUserSession(root: JsonObject, arl: String, sid: String?): DeezerLibrary.Session {
    val results = root.obj("results")
        ?: throw IllegalStateException("Deezer session response was empty")
    val user = results.obj("USER")
        ?: throw IllegalStateException("Deezer sign-in expired")
    val userId = user.str("USER_ID")?.takeIf { it.isNotBlank() && it != "0" }
        ?: throw IllegalStateException("Deezer sign-in expired")
    val apiToken = results.str("checkForm")?.takeIf { it.isNotBlank() }
        ?: throw IllegalStateException("Deezer session token missing")
    val lovedTracksId = user.str("LOVEDTRACKS_ID")?.takeIf { it.isNotBlank() && it != "0" }
    return DeezerLibrary.Session(
        arl = arl,
        sid = sid,
        apiToken = apiToken,
        userId = userId,
        lovedTracksId = lovedTracksId,
    )
}

internal fun parseProfilePlaylists(root: JsonObject): List<DeezerPlaylist> {
    val data = root.obj("results")
        ?.obj("TAB")
        ?.obj("playlists")
        ?.arr("data")
        .orEmpty()
    return data.mapNotNull { element ->
        val obj = element.asObj() ?: return@mapNotNull null
        val id = obj.str("PLAYLIST_ID")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val title = obj.str("TITLE")?.trim().orEmpty()
        if (title.isEmpty()) return@mapNotNull null
        val picType = obj.str("PICTURE_TYPE")?.takeIf { it.isNotBlank() } ?: "playlist"
        val picHash = obj.str("PLAYLIST_PICTURE")
        DeezerPlaylist(
            id = id,
            name = title,
            owner = obj.str("PARENT_USERNAME")?.takeIf { it.isNotBlank() },
            imageUrl = DeezerLibrary.pictureUrl(picType, picHash),
        )
    }
}

/** api.deezer.com reports errors as HTTP 200 with an `error` object, e.g. a quota limit. */
private fun JsonObject.throwIfApiError() {
    val error = obj("error") ?: return
    throw IllegalStateException(error.str("message")?.takeIf { it.isNotBlank() } ?: "Deezer request failed")
}

internal fun parseApiPlaylistPage(root: JsonObject): Triple<List<DeezerPlaylist>, Int, Int> {
    root.throwIfApiError()
    val rawItems = root.arr("data").orEmpty()
    val items = rawItems.mapNotNull { element ->
        val obj = element.asObj() ?: return@mapNotNull null
        if (obj.str("is_loved_track") == "true") return@mapNotNull null
        val id = obj.str("id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val title = obj.str("title")?.trim().orEmpty()
        if (title.isEmpty()) return@mapNotNull null
        DeezerPlaylist(
            id = id,
            name = title,
            owner = obj.obj("creator")?.str("name")?.takeIf { it.isNotBlank() },
            imageUrl = obj.str("picture_big") ?: obj.str("picture_medium") ?: obj.str("picture"),
        )
    }
    return Triple(items, root.int("total") ?: rawItems.size, rawItems.size)
}

internal fun parseGwTrackPage(root: JsonObject): Triple<List<DeezerTrack>, Int, Int> {
    val results = root.obj("results")
        ?: throw IllegalStateException("Deezer track response was empty")
    val rawItems = results.arr("data")
        ?: results.obj("SONGS")?.arr("data")
        ?: JsonArray(emptyList())
    val total = results.int("total")
        ?: results.obj("SONGS")?.int("total")
        ?: rawItems.size
    val items = rawItems.mapNotNull { element ->
        val data = element.asObj() ?: return@mapNotNull null
        val id = data.str("SNG_ID")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val baseTitle = data.str("SNG_TITLE")?.trim().orEmpty()
        if (baseTitle.isEmpty()) return@mapNotNull null
        val version = data.str("VERSION")?.trim().orEmpty()
        val title = if (version.isNotEmpty() && !baseTitle.contains(version, ignoreCase = true)) {
            "$baseTitle $version"
        } else {
            baseTitle
        }
        val artistsFromList = data.arr("ARTISTS").orEmpty().mapNotNull { artist ->
            artist.asObj()?.str("ART_NAME")?.trim()?.takeIf { it.isNotEmpty() }
        }.distinct().joinToString(", ")
        val artist = artistsFromList.ifEmpty { data.str("ART_NAME")?.trim().orEmpty() }
        val durationSec = data.int("DURATION") ?: 0
        DeezerTrack(
            id = id,
            title = title,
            artist = artist,
            album = data.str("ALB_TITLE")?.takeIf { it.isNotBlank() },
            durationMs = durationSec * 1000,
            imageUrl = DeezerLibrary.pictureUrl("cover", data.str("ALB_PICTURE")),
        )
    }
    return Triple(items, total, rawItems.size)
}

internal fun parseApiTrackPage(root: JsonObject): Triple<List<DeezerTrack>, Int, Int> {
    root.throwIfApiError()
    val rawItems = root.arr("data").orEmpty()
    val items = rawItems.mapNotNull { element ->
        val data = element.asObj() ?: return@mapNotNull null
        val id = data.str("id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val title = (data.str("title_short") ?: data.str("title"))?.trim().orEmpty()
        if (title.isEmpty()) return@mapNotNull null
        val artist = data.obj("artist")?.str("name")?.trim().orEmpty()
        val album = data.obj("album")
        val durationSec = data.int("duration") ?: 0
        DeezerTrack(
            id = id,
            title = title,
            artist = artist,
            album = album?.str("title")?.takeIf { it.isNotBlank() },
            durationMs = durationSec * 1000,
            imageUrl = album?.str("cover_big")
                ?: album?.str("cover_medium")
                ?: album?.str("cover")
                ?: DeezerLibrary.pictureUrl("cover", album?.str("md5_image")),
        )
    }
    return Triple(items, root.int("total") ?: rawItems.size, rawItems.size)
}

private fun JsonElement?.asObj(): JsonObject? =
    this?.takeIf { it !is JsonNull }?.let { runCatching { it.jsonObject }.getOrNull() }

private fun JsonObject.obj(key: String): JsonObject? = this[key].asObj()

private fun JsonObject.arr(key: String): JsonArray? =
    this[key]?.takeIf { it !is JsonNull }?.let { runCatching { it.jsonArray }.getOrNull() }

private fun JsonObject.str(key: String): String? =
    this[key]?.takeIf { it !is JsonNull }?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }

private fun JsonObject.int(key: String): Int? =
    this[key]?.takeIf { it !is JsonNull }?.let { runCatching { it.jsonPrimitive.intOrNull }.getOrNull() }
