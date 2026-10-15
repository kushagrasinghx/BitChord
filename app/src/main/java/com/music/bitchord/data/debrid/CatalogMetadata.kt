package com.music.bitchord.data.debrid

import com.music.bitchord.data.TrackLog
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale

/**
 * Catalogue metadata over a public search API which needs no account.
 *
 * Only metadata is read here, never audio: title, artists, duration and the
 * stable numeric track id. That id is what the deterministic file names are
 * built from, and the cache check itself (name equality) refuses anything
 * that drifted.
 */
object CatalogMetadata {

    private const val TAG = "BitChord"
    private const val SEARCH = "https://api.tidal.com/v1/search"

    // The token lives in exactly one place: the public embed token TidalCanvas
    // already reads metadata with. Duplicating it here is what secret
    // scanners flag, so this file references that copy instead.
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"

    data class CatalogTrack(
        val id: Long,
        val title: String,
        val artists: List<String>,
        val durationSec: Int?,
        val album: String?,
        val coverId: String?,
        val explicit: Boolean,
    )

    private val codec = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(codec) }
            install(HttpTimeout) {
                requestTimeoutMillis = 10_000
                connectTimeoutMillis = 6_000
                socketTimeoutMillis = 10_000
            }
            expectSuccess = false
        }
    }

    private val countryCode: String
        get() = Locale.getDefault().country.takeIf { it.length == 2 }?.uppercase(Locale.ROOT) ?: "US"

    /** Up to [limit] tracks for [query], relevance-ordered. Empty on any failure. */
    suspend fun searchTracks(query: String, limit: Int = 5): List<CatalogTrack> = runCatching {
        val text = client.get(SEARCH) {
            parameter("query", query)
            parameter("limit", limit.coerceIn(1, 10))
            parameter("types", "TRACKS")
            parameter("countryCode", countryCode)
            header("X-Tidal-Token", com.music.bitchord.data.canvas.TidalCanvas.EMBED_TOKEN)
            header("User-Agent", UA)
        }.bodyAsText()
        val root = codec.parseToJsonElement(text).jsonObject
        val items = root["tracks"]?.jsonObject?.get("items")?.jsonArray ?: return@runCatching emptyList()
        items.mapNotNull { element ->
            val track = element.jsonObject
            val id = track["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: return@mapNotNull null
            val title = track["title"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val artists = track["artists"]?.jsonArray
                ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
                .orEmpty()
            if (artists.isEmpty()) return@mapNotNull null
            val album = track["album"]?.jsonObject
            CatalogTrack(
                id = id,
                title = title,
                artists = artists,
                durationSec = track["duration"]?.jsonPrimitive?.intOrNull,
                album = album?.get("title")?.jsonPrimitive?.contentOrNull,
                coverId = album?.get("cover")?.jsonPrimitive?.contentOrNull,
                explicit = track["explicit"]?.jsonPrimitive?.content == "true",
            )
        }
    }.getOrElse {
        TrackLog.w(TAG, "Catalogue search error: ${it.message}")
        emptyList()
    }

    /**
     * Album art for a v1 cover id: five dash-separated segments spelling the
     * CDN path. Anything shaped differently is a format this reader doesn't
     * know, so null rather than a 404.
     */
    fun coverUrl(id: String): String? {
        val parts = id.split("-")
        if (parts.size != 5) return null
        return "https://resources.tidal.com/images/${parts.joinToString("/")}/640x640.jpg"
    }
}
