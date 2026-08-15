package com.music.bitchord.data.innertube

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import java.security.MessageDigest

/**
 * Minimal Innertube (youtubei) client.
 *
 * Two kinds of client identity, for different reasons:
 *
 *  - **WEB_REMIX** against music.youtube.com for browse/search/library. It
 *    returns the full YT Music shelf layout and honours the signed-in session.
 *
 *  - **A device client** for the `player` endpoint, chosen per call. Which
 *    ones Google answers changes without notice, so [player] takes the
 *    identity as an argument and [StreamResolver] walks a list of them rather
 *    than betting the app on any single one. See [PlayerClient].
 *
 * Authenticated requests are signed with Google's SAPISIDHASH scheme derived
 * from the stored cookie; no long-lived token is ever minted or stored.
 */
object Innertube {

    private const val MUSIC_BASE = "https://music.youtube.com/youtubei/v1"
    private const val YT_BASE = "https://www.youtube.com/youtubei/v1"
    private const val MUSIC_ORIGIN = "https://music.youtube.com"

    private const val WEB_REMIX_VERSION = "1.20250101.01.00"
    private const val WEB_REMIX_CLIENT_ID = "67"

    private const val TAG = "BitChord"

    /** Session cookie captured by the login WebView; null = browse as guest. */
    var cookie: String? = null

    /**
     * Google's per-session visitor id.
     *
     * Far more load-bearing than "an id for stats". A `player` request that
     * carries no visitor id is treated as a client with no session at all, and
     * Google answers it in one of two ways: the honest one, `LOGIN_REQUIRED` /
     * "Sign in to confirm you're not a bot", or the quiet one — a perfectly
     * ordinary-looking response whose stream URLs serve a byte to anything that
     * asks and then refuse every real read with 403. The second is what
     * "it loads and then doesn't play" is made of.
     *
     * So it is fetched deliberately by [ensureVisitorData] rather than being
     * hoped for: browse responses carry one only sometimes, and a session that
     * never happened to see one would silently never play anything.
     */
    @Volatile
    private var visitorData: String? = null

    /**
     * A visitor id for this session, minting one if there isn't one yet.
     *
     * @param refresh discard the current id and take a fresh one — worth doing
     *   exactly once when a request comes back accusing us of being a bot,
     *   since an id can be burned while the session around it is fine.
     */
    suspend fun ensureVisitorData(refresh: Boolean = false): String? {
        if (!refresh && visitorData != null) return visitorData
        runCatching { fetchVisitorData() }
            .onFailure { Log.w(TAG, "could not mint a visitor id: ${it.message}") }
            .getOrNull()
            ?.let { visitorData = it }
        return visitorData
    }

    /**
     * The service worker bootstrap the web player loads before anything else,
     * which is where a fresh visitor id comes from without needing a page.
     * It answers with an anti-hijacking prefix and then plain nested arrays,
     * so the id is found by shape rather than by a path that would rot.
     */
    private suspend fun fetchVisitorData(): String? {
        val body = client.get("https://www.youtube.com/sw.js_data") {
            header("User-Agent", WEB_USER_AGENT)
        }.bodyAsText()
        val payload = Json.parseToJsonElement(body.substringAfter("\n", body.drop(5)))
        return findVisitorData(payload)
    }

    private fun findVisitorData(element: JsonElement): String? = when (element) {
        is JsonArray -> element.firstNotNullOfOrNull { findVisitorData(it) }
        is JsonPrimitive -> element.contentOrNull?.takeIf { VISITOR_DATA.matches(it) }
        else -> null
    }

    /** Protobuf-in-base64; always this shape, and nothing else in there is. */
    private val VISITOR_DATA = Regex("""Cg[A-Za-z0-9_%-]{40,}""")

    private const val WEB_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"

    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(OkHttp) {
        // Same OkHttp instance ExoPlayer streams through — see Http.
        engine { preconfigured = com.music.bitchord.data.Http.client }
        install(ContentNegotiation) { json(json) }
        // Without this the only bound is OkHttp's own read timeout, and the
        // failure it raises reads as "Socket timeout has expired […]
        // socket_timeout=unknown" — Ktor reporting a limit it was never told.
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 20_000
        }
        expectSuccess = true
    }

    /**
     * Runs [block], giving transport failures another go before letting them
     * reach the caller.
     *
     * A connection reset on mobile data is weather, not information: the
     * request was fine and asking again generally answers. That matters most
     * on a shared connection pool, where a socket torn down under one request
     * — an abandoned search, a network handover — surfaces as
     * "Software caused connection abort" on whichever request picked that
     * connection up next, which had nothing to do with it.
     *
     * Only transport failures. An HTTP error status is an answer, and
     * repeating the question won't change it. Cancellation isn't caught at
     * all: [delay] throws when the coroutine is cancelled, so a search the
     * user has typed past stops here instead of retrying on behalf of a query
     * nobody is waiting for.
     */
    private suspend fun <T> withRetry(attempts: Int = 3, block: suspend () -> T): T {
        var backoff = 500L
        repeat(attempts - 1) {
            try {
                return block()
            } catch (e: IOException) {
                Log.d(TAG, "retrying: ${e.message}")
            }
            delay(backoff)
            backoff *= 2
        }
        return block()
    }

    // ---- Public API ---------------------------------------------------------

    suspend fun browse(browseId: String, params: String? = null): JsonObject =
        postMusic("browse") {
            put("browseId", browseId)
            params?.let { put("params", it) }
        }

    /**
     * The next page of a paged browse response — playlists and library feeds
     * come back roughly 100 rows at a time. YouTube Music takes the token as
     * query parameters rather than in the body, and answers with a bare
     * continuation envelope carrying the same row renderers.
     */
    suspend fun browseContinuation(token: String): JsonObject = postMusic(
        endpoint = "browse",
        // The web client passes the token in the body and the older query-string
        // form is still honoured; both are sent so either is enough.
        query = mapOf("ctoken" to token, "continuation" to token, "type" to "next"),
    ) {
        put("continuation", token)
    }

    /** Signed-in profile: display name, email/handle and avatar. */
    suspend fun accountMenu(): JsonObject = postMusic("account/account_menu") {}

    /**
     * The watch queue that YouTube Music would play after [videoId] — the
     * "RDAMVM" radio mix. Used to keep AutoPlay going past the last track.
     */
    suspend fun next(videoId: String): JsonObject = postMusic("next") {
        put("videoId", videoId)
        put("playlistId", "RDAMVM$videoId")
        put("isAudioOnly", true)
    }

    suspend fun search(query: String, params: String? = null): JsonObject =
        postMusic("search") {
            put("query", query)
            params?.let { put("params", it) }
        }

    /**
     * The `player` response for [videoId] as seen by [client] — the audio
     * formats and whatever it takes to unlock them.
     *
     * The single cheapest thing this app does to start a track: one POST,
     * answered in a few hundred milliseconds, against an endpoint that carries
     * no HTML and is not rate-shaped the way the watch page is.
     *
     * [signatureTimestamp] is required by the clients whose formats come back
     * ciphered ([PlayerClient.needsSignatureTimestamp]) and ignored by the
     * rest; it is read out of YouTube's own player JavaScript.
     *
     * @throws UnplayableException when the track is refused rather than
     *   missing — a region block, a takedown, or the client being turned away.
     *   Callers walk on to the next client on the strength of that distinction.
     */
    suspend fun player(
        videoId: String,
        client: PlayerClient,
        signatureTimestamp: Int? = null,
    ): JsonObject {
        val response = postPlayer(videoId, client, signatureTimestamp)

        val status = response["playabilityStatus"]?.jsonObject
            ?.get("status")?.jsonPrimitive?.content
        if (status != null && status != "OK") {
            val reason = response["playabilityStatus"]?.jsonObject
                ?.get("reason")?.jsonPrimitive?.content
            throw UnplayableException(reason ?: status)
        }
        return response
    }

    class UnplayableException(private val reason: String) :
        IllegalStateException("Track unavailable: $reason") {

        /**
         * Whether this is Google doubting the client rather than the track
         * being unavailable. Worth a fresh visitor id and another go; a real
         * region block or takedown is not.
         */
        val looksLikeBotCheck: Boolean
            get() = reason.contains("bot", ignoreCase = true) ||
                reason.contains("unusual traffic", ignoreCase = true) ||
                reason.contains("sign in", ignoreCase = true)
    }

    /** The stats endpoints a player response nominates for one playback. */
    data class PlaybackTracking(val playbackUrl: String, val watchtimeUrl: String?)

    /**
     * Player response fetched *with* the session cookie, purely to read back
     * `playbackTracking` — [player] deliberately skips auth so its device
     * clients are answered at all, so it never sees this block. Null for
     * guests: there's no account history to update.
     */
    suspend fun playbackTracking(videoId: String): PlaybackTracking? {
        if (cookie == null) return null
        val response = postMusic("player") {
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            // Real clients always describe where playback is happening; the
            // response's tracking block is scoped to it.
            putJsonObject("playbackContext") {
                putJsonObject("contentPlaybackContext") {
                    put("html5Preference", "HTML5_PREF_WANTS")
                    put("referer", "$MUSIC_ORIGIN/watch?v=$videoId")
                }
            }
        }
        val tracking = response["playbackTracking"]?.jsonObject
        if (tracking == null) {
            val playability = response["playabilityStatus"]?.jsonObject
            Log.w(
                TAG,
                "player response has no playbackTracking for $videoId " +
                    "(status=${playability?.get("status")?.jsonPrimitive?.content}, " +
                    "reason=${playability?.get("reason")?.jsonPrimitive?.content})",
            )
            return null
        }
        val playbackUrl = tracking.trackingUrl("videostatsPlaybackUrl") ?: return null
        return PlaybackTracking(playbackUrl, tracking.trackingUrl("videostatsWatchtimeUrl"))
    }

    private fun JsonObject.trackingUrl(key: String): String? =
        this[key]?.jsonObject?.get("baseUrl")?.jsonPrimitive?.content

    /**
     * The "playback started" ping real YouTube Music clients send once a track
     * becomes audible. This is what creates the history entry the home feed
     * feeds off. [cpn] is the client-playback-nonce identifying this one play:
     * it must be the same value used for every [pingWatchtime] that follows.
     */
    suspend fun pingPlayback(baseUrl: String, cpn: String) =
        pingStats(baseUrl, cpn) { parameter("el", "detailpage") }

    /**
     * The follow-up ping reporting how much of the track was actually heard.
     * A history entry with no watchtime behind it reads as a skip, so it
     * carries little weight in recommendations — [seconds] is what makes the
     * play count. `st`/`et` are the watched segment's bounds, in seconds.
     */
    suspend fun pingWatchtime(baseUrl: String, cpn: String, seconds: Long) =
        pingStats(baseUrl, cpn) {
            parameter("st", "0")
            parameter("et", seconds.toString())
            parameter("state", "playing")
        }

    /** Shared shape of the s.youtube.com stats pings, including session auth. */
    private suspend fun pingStats(
        baseUrl: String,
        cpn: String,
        extras: HttpRequestBuilder.() -> Unit,
    ): Int = client.get(baseUrl) {
        parameter("ver", "2")
        parameter("c", "WEB_REMIX")
        parameter("cver", WEB_REMIX_VERSION)
        parameter("cpn", cpn)
        extras()
        header("X-Origin", MUSIC_ORIGIN)
        header("Origin", MUSIC_ORIGIN)
        header("Referer", "$MUSIC_ORIGIN/")
        visitorData?.let { header("X-Goog-Visitor-Id", it) }
        cookie?.let { c ->
            header("Cookie", c)
            sapisidFrom(c)?.let { header("Authorization", sapisidHash(it)) }
        }
    }.status.value

    /** A fresh client-playback-nonce, identifying one play of one track. */
    fun newCpn(): String = (1..16).map { CPN_ALPHABET.random() }.joinToString("")

    private const val CPN_ALPHABET =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"

    // ---- Request plumbing ---------------------------------------------------

    private suspend fun postMusic(
        endpoint: String,
        query: Map<String, String> = emptyMap(),
        bodyExtras: JsonObjectBuilder.() -> Unit,
    ): JsonObject {
        val response = withRetry {
            client.post("$MUSIC_BASE/$endpoint") {
                contentType(ContentType.Application.Json)
                parameter("prettyPrint", "false")
                query.forEach { (key, value) -> parameter(key, value) }
                header("X-Origin", MUSIC_ORIGIN)
                header("Origin", MUSIC_ORIGIN)
                header("Referer", "$MUSIC_ORIGIN/")
                // Stats pings are only honoured for a session Google recognises
                // as a real client, so identify as one here too — the visitor
                // id is minted on the first call and reused for the session.
                header("X-YouTube-Client-Name", WEB_REMIX_CLIENT_ID)
                header("X-YouTube-Client-Version", WEB_REMIX_VERSION)
                visitorData?.let { header("X-Goog-Visitor-Id", it) }
                cookie?.let { c ->
                    header("Cookie", c)
                    header("X-Goog-AuthUser", "0")
                    sapisidFrom(c)?.let { header("Authorization", sapisidHash(it)) }
                }
                setBody(
                    buildJsonObject {
                        putJsonObject("context") {
                            putJsonObject("client") {
                                put("clientName", "WEB_REMIX")
                                put("clientVersion", WEB_REMIX_VERSION)
                                put("hl", "en")
                                put("gl", "US")
                                visitorData?.let { put("visitorData", it) }
                            }
                            putJsonObject("user") { put("lockedSafetyMode", false) }
                            putJsonObject("request") { put("useSsl", true) }
                        }
                        bodyExtras()
                    },
                )
            }.body<JsonObject>()
        }

        if (visitorData == null) {
            visitorData = response["responseContext"]?.jsonObject
                ?.get("visitorData")?.jsonPrimitive?.content
        }
        return response
    }

    /**
     * Deliberately unauthenticated.
     *
     * The app clients this walks through are answered *because* they look like
     * anonymous devices; attaching the session cookie to one is what gets it
     * turned away with `LOGIN_REQUIRED`. Nothing about the account is needed to
     * fetch audio — history is credited separately, by [playbackTracking] and
     * the stats pings, which do carry the session.
     */
    private suspend fun postPlayer(
        videoId: String,
        playerClient: PlayerClient,
        signatureTimestamp: Int?,
    ): JsonObject =
        client.post("${playerClient.apiBase()}/player") {
            contentType(ContentType.Application.Json)
            parameter("prettyPrint", "false")
            header("User-Agent", playerClient.userAgent)
            header("X-YouTube-Client-Name", playerClient.clientId)
            header("X-YouTube-Client-Version", playerClient.clientVersion)
            playerClient.origin?.let { header("Origin", it) }
            playerClient.referer?.let { header("Referer", it) }
            // Shared with browse/search so one session is seen throughout,
            // rather than a device that mints a new identity per request.
            visitorData?.let { header("X-Goog-Visitor-Id", it) }
            setBody(
                buildJsonObject {
                    putJsonObject("context") {
                        putJsonObject("client") {
                            put("clientName", playerClient.clientName)
                            put("clientVersion", playerClient.clientVersion)
                            playerClient.osName?.let { put("osName", it) }
                            playerClient.osVersion?.let { put("osVersion", it) }
                            playerClient.deviceMake?.let { put("deviceMake", it) }
                            playerClient.deviceModel?.let { put("deviceModel", it) }
                            playerClient.androidSdkVersion?.let { put("androidSdkVersion", it.toInt()) }
                            put("hl", "en")
                            put("gl", "US")
                            visitorData?.let { put("visitorData", it) }
                        }
                    }
                    if (playerClient.needsSignatureTimestamp && signatureTimestamp != null) {
                        putJsonObject("playbackContext") {
                            putJsonObject("contentPlaybackContext") {
                                put("signatureTimestamp", signatureTimestamp)
                            }
                        }
                    }
                    put("videoId", videoId)
                    put("contentCheckOk", true)
                    put("racyCheckOk", true)
                },
            )
        }.body<JsonObject>()

    /** Browser-shaped clients are served from the Music host; app clients from YouTube proper. */
    private fun PlayerClient.apiBase(): String = if (usesMusicHost) MUSIC_BASE else YT_BASE

    private fun sapisidFrom(cookieHeader: String): String? =
        cookieHeader.split("; ", ";")
            .firstOrNull { it.trim().startsWith("SAPISID=") }
            ?.substringAfter("=")

    private fun sapisidHash(sapisid: String, origin: String = MUSIC_ORIGIN): String {
        val timestamp = System.currentTimeMillis() / 1000
        val digest = MessageDigest.getInstance("SHA-1")
            .digest("$timestamp $sapisid $origin".toByteArray())
            .joinToString("") { "%02x".format(it) }
        return "SAPISIDHASH ${timestamp}_$digest"
    }
}
