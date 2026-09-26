package com.music.bitchord.data.catalogue

import android.util.Base64
import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.service.ServiceConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

@Serializable
data class CatalogueArtistItem(
    val id: String = "",
    val name: String = "",
)

@Serializable
data class CatalogueArtistMap(
    @SerialName("primary_artists") val primaryArtists: List<CatalogueArtistItem> = emptyList(),
)

@Serializable
data class CatalogueMoreInfo(
    val album_id: String = "",
    val album: String = "",
    @SerialName("encrypted_media_url") val encryptedMediaUrl: String = "",
    val duration: String = "",
    /**
     * Whether a 320kbps rendition exists, as `"true"`/`"false"`.
     *
     * The catalogue states this per track and it is frequently false. Asking
     * the CDN for `_320` anyway does not produce one.
     */
    @SerialName("320kbps") val has320: String = "",
    val artistMap: CatalogueArtistMap = CatalogueArtistMap(),
) {
    val supports320: Boolean get() = has320.equals("true", ignoreCase = true)
}

/** A decoded CDN URL and the bitrate it will really deliver. */
data class CatalogueStream(val url: String, val kbps: Int?)

@Serializable
data class CatalogueSongItem(
    val id: String = "",
    val title: String = "",
    val image: String = "",
    /** The catalogue sends this as the string `"1"` or `"0"`. */
    @SerialName("explicit_content") val explicitContent: String = "",
    @SerialName("more_info") val moreInfo: CatalogueMoreInfo = CatalogueMoreInfo()
) {
    val isExplicit: Boolean
        get() = explicitContent == "1" || explicitContent.equals("true", ignoreCase = true)
}

@Serializable
data class CatalogueSearchResponse(
    val results: List<CatalogueSongItem> = emptyList()
)

@Serializable
data class CatalogueSongsResponse(
    val songs: List<CatalogueSongItem> = emptyList()
)

/**
 * Put uncensored catalogue rows ahead of their clean duplicates.
 *
 * Top-level so the pure ordering rule remains unit-testable without
 * initialising Android's Base64-backed service singleton on the JVM.
 */
internal fun prioritizeExplicit(songs: List<CatalogueSongItem>): List<CatalogueSongItem> =
    songs.sortedByDescending { it.isExplicit }

/**
 * The third-party lossy catalogue: search and stream URLs for tracks the
 * service file configures.
 *
 * Every service value — API root, call names, stream key, user agent and the
 * optional egress hints — arrives in the imported service file's `catalogue`
 * block (see [ServiceConfig]). The repo holds none of them; without that
 * block every call here fails before touching the network.
 */
object CatalogueService {
    private const val TAG = "BitChord"

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    // Note: BitChord uses OkHttp engine for ktor
    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) {
                requestTimeoutMillis = 6_000
                connectTimeoutMillis = 4_000
                socketTimeoutMillis = 6_000
            }
            defaultRequest {
                headers.append(HttpHeaders.Accept, "application/json")
                headers.append(HttpHeaders.UserAgent, ServiceConfig.catalogue().userAgent)
                ServiceConfig.catalogue().forwardedFor?.let { ip ->
                    headers.append("X-Forwarded-For", ip)
                    headers.append("X-Real-IP", ip)
                }
                headers.append("Accept-Language", "en-IN,en;q=0.9")
                ServiceConfig.catalogue().cookie?.let { cookie ->
                    headers.append(HttpHeaders.Cookie, cookie)
                }
            }
            expectSuccess = false
        }
    }

    private fun decryptUrl(encryptedUrl: String, urlKey: String): String {
        if (encryptedUrl.isBlank()) return ""
        return try {
            val secretKey = SecretKeySpec(urlKey.toByteArray(Charsets.UTF_8), "DES")
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey)
            val decodedBytes = Base64.decode(encryptedUrl, Base64.DEFAULT)
            val decryptedBytes = cipher.doFinal(decodedBytes)
            String(decryptedBytes, Charsets.UTF_8).trim()
        } catch (e: Exception) {
            TrackLog.e(TAG, "Catalogue URL decryption failed", e)
            ""
        }
    }

    /**
     * The best CDN URL this track really has, and the bitrate it will deliver.
     * Deciphers with the file's stream key, then picks the rendition — see
     * [selectBestCatalogueStream].
     */
    private fun bestStream(
        encryptedUrl: String,
        supports320: Boolean,
        urlKey: String,
    ): CatalogueStream? =
        selectBestCatalogueStream(decryptUrl(encryptedUrl, urlKey), supports320)

/**
 * Select the highest catalogue CDN rendition without losing URL parameters.
 *
 * The encrypted URL normally names the 96 kbps file and the catalogue states
 * whether the parallel 320 kbps object exists. CDN URLs are not guaranteed to
 * end at the container: cache-busting or access query parameters may follow
 * it. An end-anchored match misses those URLs, leaves `_96.mp4` in place,
 * and still reports 320 upstream — playback and downloads then display 320
 * while both fetch the 96 kbps object.
 */
internal fun selectBestCatalogueStream(decryptedUrl: String, supports320: Boolean): CatalogueStream? {
    if (decryptedUrl.isBlank()) return null
    val rendition = Regex(
        "_(48|96|160|320)\\.(mp4|aac|mp3)(?=[?#]|$)",
        RegexOption.IGNORE_CASE,
    ).find(decryptedUrl)
        // An unrecognised URL must not be labelled 320 merely because the
        // catalogue says that rendition exists; no rewrite was actually made.
        ?: return CatalogueStream(decryptedUrl, null)

    val offered = rendition.groupValues[1].toInt()
    if (!supports320) return CatalogueStream(decryptedUrl, offered)

    val extension = rendition.groupValues[2]
    return CatalogueStream(
        decryptedUrl.replaceRange(rendition.range, "_320.$extension"),
        320,
    )
}

    suspend fun searchSongs(query: String): List<CatalogueSongItem> = runCatching {
        val catalogue = ServiceConfig.catalogue()
        val response = client.get(catalogue.apiBase) {
            parameter("__call", catalogue.searchCall)
            parameter("_format", "json")
            parameter("_marker", "0")
            parameter("api_version", "4")
            parameter("ctx", "android")
            parameter("q", query)
            parameter("p", "1")
            parameter("n", "10")
        }

        if (response.status != HttpStatusCode.OK) {
            TrackLog.w(TAG, "Catalogue search failed: HTTP ${response.status.value}")
            return@runCatching emptyList()
        }

        val body = json.decodeFromString<CatalogueSearchResponse>(response.bodyAsText())
        body.results
    }.getOrElse {
        TrackLog.w(TAG, "Catalogue search error: ${it.message}")
        emptyList()
    }

    suspend fun getStreamUrl(catalogueSongId: String): CatalogueStream? {
        val result = runCatching {
            val catalogue = ServiceConfig.catalogue()
            val response = client.get(catalogue.apiBase) {
                parameter("__call", catalogue.detailsCall)
                parameter("_format", "json")
                parameter("_marker", "0")
                parameter("api_version", "4")
                parameter("ctx", "android")
                parameter("pids", catalogueSongId)
            }

            if (response.status != HttpStatusCode.OK) {
                TrackLog.w(TAG, "Catalogue getDetails failed: HTTP ${response.status.value}")
                return@runCatching null
            }

            // The details call does not answer with the `{"songs":[…]}`
            // envelope search uses. It answers with a map keyed by the id
            // that was asked for, so decoding it as [CatalogueSongsResponse]
            // found no `songs` key, produced an empty list under
            // `ignoreUnknownKeys`, and returned null without an error or an
            // exception to explain itself. Both shapes are read here so
            // neither endpoint changing its mind breaks the other.
            val root = json.parseToJsonElement(response.bodyAsText()) as? JsonObject
                ?: return@runCatching null
            val songElement = (root["songs"] as? JsonArray)?.firstOrNull()
                ?: root.values.firstOrNull { it is JsonObject }
                ?: run {
                    TrackLog.w(TAG, "Catalogue getDetails held no song for $catalogueSongId")
                    return@runCatching null
                }
            val rawSong = json.decodeFromJsonElement(CatalogueSongItem.serializer(), songElement)

            bestStream(rawSong.moreInfo.encryptedMediaUrl, rawSong.moreInfo.supports320, catalogue.urlKey)
        }
        return result.onFailure { TrackLog.w(TAG, "Catalogue getDetails error: ${it.message}") }.getOrNull()
    }
}
