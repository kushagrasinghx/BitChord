package com.music.bitchord.data.shazam

import com.music.bitchord.data.TrackLog
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.time.ZoneId
import java.util.Locale
import java.util.UUID

/** The song Shazam matched, reduced to what the app's search needs. */
data class ShazamTrack(
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val shareUrl: String?,
) {
    /**
     * The term the Search tab should run; the user still picks the right row.
     *
     * Written with a separator rather than a space: "title - artist" reads as
     * one search phrase to YouTube Music, so a common word in the title cannot
     * outrank the artist being looked for, and the field shows where the two
     * halves came from.
     */
    val searchQuery: String
        get() = when {
            title.isBlank() -> artist
            artist.isBlank() -> title
            else -> "$title - $artist"
        }
}

/**
 * The three outcomes the recognition UI distinguishes. "No match" is a normal
 * answer from Shazam; [Failed] means the request itself did not complete.
 */
sealed interface ShazamLookupResult {
    data class Found(val track: ShazamTrack) : ShazamLookupResult
    data object NoMatch : ShazamLookupResult
    data object Failed : ShazamLookupResult
}

/**
 * A swappable recognizer. Keeping the endpoint behind this small interface
 * means a future provider (or a different Shazam endpoint) does not reach into
 * the recorder or the sheet.
 */
fun interface ShazamProvider {
    suspend fun recognize(signature: ShazamSignature): ShazamLookupResult
}

/**
 * Sends a fingerprint to the Shazam discovery endpoint used by the desktop
 * clients and reads the top-level `track` object out of the answer.
 *
 * This is the unofficial `amp.shazam.com` endpoint, not an API with a contract:
 * it can change without notice. It is isolated here so that if it does, only
 * this class needs a replacement. See `.claude/Shazam.md` for the request that
 * was verified against a real song.
 */
object ShazamClient : ShazamProvider {

    private const val TAG = "Shazam"
    private const val BASE_URL = "https://amp.shazam.com/discovery/v5/en/US/android/-/tag"
    private const val USER_AGENT = "Shazam/14.1.0 (iPhone; iOS 14.4; Scale/3.00)"

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) {
                requestTimeoutMillis = 10_000
                connectTimeoutMillis = 5_000
                socketTimeoutMillis = 10_000
            }
            expectSuccess = false
        }
    }

    override suspend fun recognize(signature: ShazamSignature): ShazamLookupResult {
        return runCatching {
            val timestamp = System.currentTimeMillis()
            val body = buildJsonObject {
                put("timezone", ZoneId.systemDefault().id)
                putJsonObject("signature") {
                    put("uri", ShazamSignatureEncoder.encodeToUri(signature))
                    put(
                        "samplems",
                        signature.sampleCount * 1_000L / signature.sampleRateHz,
                    )
                }
                put("timestamp", timestamp)
                putJsonObject("context") {}
                putJsonObject("geolocation") {}
            }
            val uuid1 = UUID.randomUUID().toString().uppercase(Locale.US)
            val uuid2 = UUID.randomUUID().toString().lowercase(Locale.US)
            val response = client.post("$BASE_URL/$uuid1/$uuid2") {
                parameter("sync", "true")
                parameter("webv3", "true")
                parameter("sampling", "true")
                parameter("connected", "")
                parameter("shazamapiversion", "v3")
                parameter("sharehub", "true")
                parameter("video", "v3")
                header(HttpHeaders.Accept, "application/json")
                header(HttpHeaders.UserAgent, USER_AGENT)
                header("X-Shazam-Platform", "IPHONE")
                header("X-Shazam-AppVersion", "14.1.0")
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
            if (!response.status.isSuccess()) {
                TrackLog.w(TAG, "Shazam lookup failed: HTTP ${response.status.value}")
                return@runCatching ShazamLookupResult.Failed
            }
            parseShazamResponse(response.bodyAsText())
        }.getOrElse { error ->
            // A newer 8- or 12-second attempt cancels this one. That is not a
            // failed lookup and must not be reported as one.
            if (error is CancellationException) throw error
            TrackLog.w(TAG, "Shazam lookup error: ${error.message}")
            ShazamLookupResult.Failed
        }
    }
}

/**
 * Parses the endpoint's answer. Exposed to tests because the important part -
 * "track present means a match, absent means no match" - should not require a
 * live request to verify.
 */
internal fun parseShazamResponse(body: String): ShazamLookupResult {
    val response = try {
        responseJson.decodeFromString<ShazamResponse>(body)
    } catch (_: Exception) {
        return ShazamLookupResult.Failed
    }
    val track = response.track ?: return ShazamLookupResult.NoMatch
    val title = track.title?.trim().orEmpty()
    val artist = track.subtitle?.trim().orEmpty()
    if (title.isBlank()) return ShazamLookupResult.NoMatch
    return ShazamLookupResult.Found(
        ShazamTrack(
            title = title,
            artist = artist,
            artworkUrl = track.images?.coverarthq
                ?: track.images?.coverart
                ?: track.images?.background,
            shareUrl = track.share?.href,
        ),
    )
}

private val responseJson = Json {
    isLenient = true
    ignoreUnknownKeys = true
    explicitNulls = false
}

@Serializable
private data class ShazamResponse(val track: ShazamTrackResponse? = null)

@Serializable
private data class ShazamTrackResponse(
    val title: String? = null,
    val subtitle: String? = null,
    val images: ShazamImages? = null,
    val share: ShazamShare? = null,
)

@Serializable
private data class ShazamImages(
    val coverarthq: String? = null,
    val coverart: String? = null,
    val background: String? = null,
)

@Serializable
private data class ShazamShare(val href: String? = null)
