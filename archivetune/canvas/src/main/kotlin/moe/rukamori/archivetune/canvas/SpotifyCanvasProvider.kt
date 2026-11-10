/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.canvas

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import java.util.concurrent.ConcurrentHashMap

object SpotifyCanvasProvider {

    private const val CANVAZ_URL = "https://spclient.wg.spotify.com/canvaz-cache/v0/canvases"

    private const val CACHE_TTL_MS = 60L * 60 * 1000

    @Volatile
    var tokenProvider: (suspend () -> String?)? = null

    @Volatile
    var trackUriResolver: (suspend (videoId: String, title: String?, artist: String?) -> String?)? = null

    @Volatile
    var extraResolverEndpointsProvider: (suspend () -> List<String>)? = null

    @Volatile
    var logger: ((message: String) -> Unit)? = null

    private fun log(message: String) {
        logger?.invoke(message)
    }

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }

    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 15_000
                socketTimeoutMillis = 15_000
            }
            install(ContentEncoding) {
                gzip()
                deflate()
            }
            install(HttpCache)

            defaultRequest {
                header("x-request-source", "muzo")
                header("User-Agent", "ArchiveTune-Android")
                header("Accept", "application/json")
            }
            expectSuccess = false
        }
    }

    private val spotifyClient by lazy {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 15_000
                socketTimeoutMillis = 15_000
            }
            expectSuccess = false
        }
    }

    private data class CacheEntry(
        val value: CanvasArtwork?,
        val expiresAtMs: Long,
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    suspend fun getByVideoId(
        videoId: String,
        songTitle: String? = null,
        artistName: String? = null,
        spotifyTrackUri: String? = null,
    ): CanvasArtwork? {
        if (videoId.isBlank()) return null

        cache[videoId]?.let { entry ->
            if (entry.expiresAtMs > System.currentTimeMillis()) return entry.value
            cache.remove(videoId)
        }

        val official =
            try {
                fetchOfficialCanvas(videoId, songTitle, artistName, spotifyTrackUri)
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                log("Official Spotify Canvas lookup failed for $videoId: ${throwable.message}")
                null
            }
        if (official != null) {
            cache[videoId] = CacheEntry(official, System.currentTimeMillis() + CACHE_TTL_MS)
            return official
        }

        val extraEndpoints =
            try {
                extraResolverEndpointsProvider?.invoke().orEmpty()
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                log("Failed to read extra canvas resolvers: ${throwable.message}")
                emptyList()
            }
        val resolverEndpoints = extraEndpoints.distinct()
        if (resolverEndpoints.isEmpty()) return null

        var anyResolverReachable = false
        for (endpoint in resolverEndpoints) {
            val artwork =
                try {
                    val response =
                        client.get(endpoint) {
                            parameter("id", videoId)
                        }

                    val contentType =
                        response.headers[io.ktor.http.HttpHeaders.ContentType]
                            ?.lowercase()
                            .orEmpty()
                    val looksLikeJson = contentType.contains("json")
                    if (!looksLikeJson) {
                        log("Canvas resolver $endpoint answered non-JSON ($contentType) for $videoId — treating as unreachable")
                        null
                    } else if (response.status != HttpStatusCode.OK) {

                        anyResolverReachable = true
                        log("Canvas resolver $endpoint returned ${response.status.value} for $videoId")
                        null
                    } else {
                        anyResolverReachable = true
                        val body: JsonObject = response.body()
                        parseCanvasArtwork(body, videoId)
                    }
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) throw throwable

                    log("Canvas resolver $endpoint failed for $videoId: ${throwable.message}")
                    null
                }
            if (artwork != null) {
                cache[videoId] = CacheEntry(artwork, System.currentTimeMillis() + CACHE_TTL_MS)
                return artwork
            }
        }

        if (anyResolverReachable) {
            cache[videoId] = CacheEntry(null, System.currentTimeMillis() + CACHE_TTL_MS)
        }
        return null
    }

    private suspend fun fetchOfficialCanvas(
        videoId: String,
        songTitle: String?,
        artistName: String?,
        spotifyTrackUri: String? = null,
    ): CanvasArtwork? {
        val resolveTrackUri = trackUriResolver ?: return null
        val provideToken = tokenProvider ?: return null

        val token = provideToken()?.takeIf { it.isNotBlank() } ?: return null
        val trackUri =
            spotifyTrackUri?.takeIf { it.isNotBlank() }
                ?: resolveTrackUri(videoId, songTitle, artistName)?.takeIf { it.isNotBlank() }
                ?: return null

        val response =
            spotifyClient.post(CANVAZ_URL) {
                header("Authorization", "Bearer $token")
                header("Accept", "application/x-protobuf")
                header("Content-Type", "application/x-protobuf")
                header("User-Agent", "ArchiveTune-Android")
                setBody(SpotifyCanvazProtocol.encodeRequest(listOf(trackUri)))
            }
        if (response.status != HttpStatusCode.OK) {
            log("Spotify canvaz returned ${response.status.value} for $trackUri")
            return null
        }

        val entries = SpotifyCanvazProtocol.decodeResponse(response.body<ByteArray>())
        val canvasUrl =
            entries
                .firstOrNull { it.entityUri == trackUri && !it.url.isNullOrBlank() }
                ?.url
                ?: entries.firstNotNullOfOrNull { entry -> entry.url?.takeIf { it.isNotBlank() } }
                ?: return null

        log("Spotify canvaz resolved $trackUri → $canvasUrl")
        return CanvasArtwork(
            name = songTitle,
            artist = artistName,

            albumId = "yt:$videoId",
            albumName = null,
            static = null,
            animated = null,
            animatedVertical = null,
            videoUrl = canvasUrl,
            videoUrlVertical = canvasUrl,
            provider = CanvasArtwork.PROVIDER_SPOTIFY,
        )
    }

    private fun parseCanvasArtwork(
        body: JsonObject,
        videoId: String,
    ): CanvasArtwork? {

        val payload = body["data"]?.jsonObject ?: body["result"]?.jsonObject ?: body

        val videoUrl =
            payload["url"]?.jsonPrimitive?.contentOrNull
                ?: payload["canvas_url"]?.jsonPrimitive?.contentOrNull
                ?: payload["video_url"]?.jsonPrimitive?.contentOrNull
                ?: payload["canvas"]?.jsonPrimitive?.contentOrNull
                ?: return null
        if (videoUrl.isBlank()) return null

        val songName =
            payload["song"]?.jsonPrimitive?.contentOrNull
                ?: payload["name"]?.jsonPrimitive?.contentOrNull
                ?: payload["title"]?.jsonPrimitive?.contentOrNull
                ?: payload["track"]?.jsonPrimitive?.contentOrNull

        val artistName =
            payload["artist"]?.jsonPrimitive?.contentOrNull
                ?: payload["artists"]?.jsonPrimitive?.contentOrNull
                ?: payload["author"]?.jsonPrimitive?.contentOrNull

        return CanvasArtwork(
            name = songName,
            artist = artistName,

            albumId = "yt:$videoId",
            albumName = null,
            static = null,
            animated = null,
            animatedVertical = null,
            videoUrl = videoUrl,
            videoUrlVertical = videoUrl,
            provider = CanvasArtwork.PROVIDER_SPOTIFY,
        )
    }

    suspend fun diagnoseOfficialEndpoint(
        songTitle: String?,
        artistName: String?,
        spotifyTrackUri: String? = null,
    ): CanvasSourceDiagnosis {
        val provideToken =
            tokenProvider
                ?: return CanvasSourceDiagnosis.Skipped("No Spotify session is wired up in this build.")
        val token =
            try {
                provideToken()?.takeIf { it.isNotBlank() }
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                return CanvasSourceDiagnosis.Skipped("Spotify session error: ${throwable.message}")
            } ?: return CanvasSourceDiagnosis.Skipped("Not signed in to Spotify — the official Canvas endpoint needs your account.")
        val trackUri =
            spotifyTrackUri?.takeIf { it.isNotBlank() }
                ?: try {
                    trackUriResolver?.invoke("canvas-check-probe", songTitle, artistName)?.takeIf { it.isNotBlank() }
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) throw throwable
                    null
                }
                ?: return CanvasSourceDiagnosis.Skipped("Signed in, but the probe song couldn't be identified on Spotify — try again while it plays.")
        return try {
            val response =
                spotifyClient.post(CANVAZ_URL) {
                    header("Authorization", "Bearer $token")
                    header("Accept", "application/x-protobuf")
                    header("Content-Type", "application/x-protobuf")
                    header("User-Agent", "ArchiveTune-Android")
                    setBody(SpotifyCanvazProtocol.encodeRequest(listOf(trackUri)))
                }
            when {
                response.status == HttpStatusCode.OK -> {
                    val entries = SpotifyCanvazProtocol.decodeResponse(response.body<ByteArray>())
                    val canvasUrl =
                        entries.firstOrNull { it.entityUri == trackUri && !it.url.isNullOrBlank() }?.url
                            ?: entries.firstNotNullOfOrNull { entry -> entry.url?.takeIf { it.isNotBlank() } }
                    if (canvasUrl != null) {
                        CanvasSourceDiagnosis.Ok(canvasFound = true, "Answered with a canvas for the probe track.")
                    } else {
                        CanvasSourceDiagnosis.Ok(canvasFound = false, "Answered — endpoint and your account work (this track just has no canvas).")
                    }
                }
                response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden ->
                    CanvasSourceDiagnosis.Rejected(
                        httpStatus = response.status.value,
                        detail = "Spotify rejected your token (HTTP ${response.status.value}) — re-login to Spotify.",
                    )
                else ->
                    CanvasSourceDiagnosis.Rejected(
                        httpStatus = response.status.value,
                        detail = "Answered HTTP ${response.status.value} — the endpoint refused the request.",
                    )
            }
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            CanvasSourceDiagnosis.Unreachable("Unreachable: ${throwable.message ?: throwable::class.simpleName}")
        }
    }

    suspend fun diagnoseResolverEndpoint(
        endpoint: String,
        probeVideoId: String,
    ): CanvasSourceDiagnosis {
        return try {
            val response =
                client.get(endpoint) {
                    parameter("id", probeVideoId)
                }
            val contentType =
                response.headers[io.ktor.http.HttpHeaders.ContentType]
                    ?.lowercase()
                    .orEmpty()
            if (!contentType.contains("json")) {
                CanvasSourceDiagnosis.Rejected(
                    httpStatus = null,
                    detail = "Dead endpoint — answered non-JSON ($contentType).",
                )
            } else if (response.status == HttpStatusCode.OK) {
                val body: JsonObject = response.body()
                val canvas = parseCanvasArtwork(body, probeVideoId)
                if (canvas != null) {
                    CanvasSourceDiagnosis.Ok(canvasFound = true, "Answered with a canvas for the probe track.")
                } else {
                    CanvasSourceDiagnosis.Ok(canvasFound = false, "Working — answered, no canvas for the probe track.")
                }
            } else {
                CanvasSourceDiagnosis.Ok(
                    canvasFound = false,
                    detail = "Working — reachable and answered HTTP ${response.status.value} (no canvas for the probe track).",
                )
            }
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            CanvasSourceDiagnosis.Unreachable("Unreachable: ${throwable.message ?: throwable::class.simpleName}")
        }
    }
}
