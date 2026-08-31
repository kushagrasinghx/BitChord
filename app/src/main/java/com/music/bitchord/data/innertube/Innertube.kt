package com.music.bitchord.data.innertube

import com.music.bitchord.data.DebugLog as Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
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
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.PlaylistPrivacy
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale

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
    private const val YOUTUBE_ORIGIN = "https://www.youtube.com"

    /**
     * Fallback WEB_REMIX version, used until [SessionScope] reads the live one
     * out of the music.youtube.com shell. Only a starting point: the real
     * version moves every few days, and the one that matters is the one
     * [webRemixVersion] reports.
     */
    private const val WEB_REMIX_VERSION = "1.20250101.01.00"
    private const val WEB_REMIX_CLIENT_ID = "67"

    private const val TAG = "BitChord"

    /** Session cookie captured by the login WebView; null = browse as guest. */
    var cookie: String? = null
        set(value) {
            if (field != value) {
                // Both belong to the session that just left. A scope kept across
                // a sign-in would credit the new account's plays to the old one,
                // and a visitor id minted under the old session is not bound to
                // the new one — see [SessionScope] and [visitorData].
                scope = null
                visitorData = null
                visitorDataIsSessionBound = false
            }
            field = value
        }

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
     * Whether [visitorData] came from the signed-in shell rather than being
     * minted anonymously.
     *
     * A session-bound id outranks an anonymous one and must not be replaced by
     * it. Both are fetched near startup and nothing orders them, so without this
     * the better id was lost to whichever request happened to finish second.
     */
    @Volatile
    private var visitorDataIsSessionBound = false

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
            ?.let {
                if (refresh || !visitorDataIsSessionBound) {
                    visitorData = it
                    visitorDataIsSessionBound = false
                }
            }
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

    // ---- Which account is this, exactly -------------------------------------

    /**
     * Who the session cookie actually acts as, and which client version it acts
     * with — read out of the signed-in music.youtube.com shell.
     *
     * A cookie is not an account. One Google login carries every account the
     * browser has ever signed into, plus every brand channel hanging off them,
     * and *nothing in the cookie says which one is meant*. The web client
     * resolves that from its page config and then says so on every request. An
     * app that skips this step is not making an ambiguous request — it is
     * making a request about the first account in the jar, whoever that is.
     *
     * That is the whole of "history works for me and not for them": for a
     * listener whose YouTube Music account *is* the first one, guessing is
     * indistinguishable from asking. For anyone with two Google accounts, or a
     * brand channel — the account YouTube Music itself pushes you onto when you
     * have one — every play was being credited to the wrong identity, so their
     * own history stayed empty no matter how many pings went out successfully.
     *
     * @param dataSyncId the account, as `context.user.onBehalfOfUser`. Only
     *   ever taken from a shell that reported itself signed in: Google answers
     *   an `onBehalfOfUser` it cannot tie to a session with 401, so a guessed
     *   value would break every request in the app rather than just history.
     * @param pageId the brand channel, as `X-Goog-PageId` — the header the
     *   stats endpoints ask for by name as `PLUS_PAGE_ID`. Absent for a plain
     *   personal account, which is why it is nullable rather than defaulted.
     * @param authUser which entry in the cookie jar, as `X-Goog-AuthUser`.
     *   Hardcoded `0` before this, which is the same guess by another name.
     */
    private class SessionScope(
        val dataSyncId: String?,
        val pageId: String?,
        val authUser: String,
        val clientVersion: String?,
    )

    @Volatile
    private var scope: SessionScope? = null

    private val scopeLock = Mutex()

    /**
     * The WEB_REMIX version to claim, live if the shell has been read.
     *
     * Worth taking from the shell rather than pinning: the stats pings carry it
     * as `cver`, and a version Google has never shipped is a standing invitation
     * to be treated as something other than a music client.
     */
    private val webRemixVersion: String
        get() = scope?.clientVersion ?: WEB_REMIX_VERSION

    /**
     * Reads the session scope, once per cookie, before anything that depends on
     * being the right account.
     *
     * Cheap to be wrong about and expensive to skip, so it fails open: a shell
     * that cannot be fetched or parsed leaves [scope] null and every request
     * behaves exactly as it did before. What it must never do is invent a
     * [SessionScope.dataSyncId] — see that field.
     */
    suspend fun ensureSessionScope() {
        val session = cookie ?: return
        if (scope != null) return
        scopeLock.withLock {
            if (scope != null || cookie != session) return
            runCatching { fetchSessionScope(session) }
                .onFailure { Log.w(TAG, "could not read the session scope: ${it.message}") }
                .getOrNull()
                ?.let { fresh ->
                    scope = fresh
                    Log.d(
                        TAG,
                        "session scope: authUser=${fresh.authUser} " +
                            "pageId=${fresh.pageId ?: "none"} " +
                            "dataSyncId=${if (fresh.dataSyncId != null) "present" else "none"} " +
                            "cver=${fresh.clientVersion ?: WEB_REMIX_VERSION}",
                    )
                }
        }
    }

    /**
     * The music.youtube.com shell, fetched with the session, for its `ytcfg`.
     *
     * Read by regex rather than by evaluating the config blob: it is one script
     * assignment among hundreds of kilobytes of app JavaScript, and the four
     * values wanted are flat strings in it. A key that moves reads as absent,
     * which is the same as not having asked.
     */
    private suspend fun fetchSessionScope(session: String): SessionScope? {
        val html = client.get("$MUSIC_ORIGIN/") {
            header("User-Agent", WEB_USER_AGENT)
            header("Accept-Language", "en-US,en;q=0.9")
            header("Cookie", session)
            sapisidFrom(session)?.let { header("Authorization", sapisidHash(it)) }
        }.bodyAsText()

        // The one value that must not be guessed. A shell that says it is
        // signed out either has a dead cookie or was served to nobody in
        // particular; either way its DATASYNC_ID belongs to no account, and
        // sending it would 401 every request in the app.
        val signedIn = CONFIG_LOGGED_IN.find(html)?.groupValues?.get(1) == "true"
        val clientVersion = CONFIG_CLIENT_VERSION.find(html)?.groupValues?.get(1)
        if (!signedIn) {
            Log.w(TAG, "music.youtube.com served a signed-out shell; not scoping requests")
            // Still worth the client version — that part is true either way.
            return clientVersion?.let { SessionScope(null, null, "0", it) }
        }

        // `<accountSyncId>||<sessionSyncId>`; only the first half identifies
        // the account, and the second changes on its own schedule.
        val dataSyncId = CONFIG_DATASYNC_ID.find(html)?.groupValues?.get(1)
            ?.substringBefore("||")
            ?.takeIf { it.isNotBlank() }
        val pageId = CONFIG_PAGE_ID.find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
        val authUser = CONFIG_SESSION_INDEX.find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

        // The shell's own visitor id, which is bound to this session. Strictly
        // better than the anonymous one [fetchVisitorData] mints: the stats
        // pings are attributed against the visitor the player response was
        // issued to, so a signed-in play reported under an anonymous id is a
        // play reported about nobody.
        CONFIG_VISITOR_DATA.find(html)?.groupValues?.get(1)
            ?.takeIf { it.isNotBlank() }
            ?.let {
                visitorData = it
                visitorDataIsSessionBound = true
            }

        return SessionScope(dataSyncId, pageId, authUser ?: "0", clientVersion)
    }

    private val CONFIG_LOGGED_IN = Regex(""""LOGGED_IN"\s*:\s*(true|false)""")
    private val CONFIG_DATASYNC_ID = Regex(""""DATASYNC_ID"\s*:\s*"([^"]+)"""")
    private val CONFIG_PAGE_ID = Regex(""""DELEGATED_SESSION_ID"\s*:\s*"([^"]+)"""")
    private val CONFIG_SESSION_INDEX = Regex(""""SESSION_INDEX"\s*:\s*"?(\d+)""")
    private val CONFIG_VISITOR_DATA = Regex(""""VISITOR_DATA"\s*:\s*"([^"]+)"""")
    private val CONFIG_CLIENT_VERSION = Regex(""""INNERTUBE_CLIENT_VERSION"\s*:\s*"([^"]+)"""")

    private const val WEB_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"

    private val json = Json { ignoreUnknownKeys = true }

    /** See [postPlayer] — the per-request ceiling on the walk's hot path. */
    private const val PLAYER_TIMEOUT_MS = 6_000L

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
            } catch (e: HttpRequestTimeoutException) {
                // Not weather, and not worth repeating. A timeout is this app's
                // own decision that the request had long enough — so trying it
                // again cannot learn anything the first attempt didn't, and the
                // cost is multiplied rather than shared: [HttpRequestTimeoutException]
                // is an [IOException], so before this branch existed every timed-out
                // `player` call was quietly attempted three times. That turned a
                // six-second ceiling into a nineteen-second one on a walk of
                // seven clients, which is worse than the unbounded call the
                // ceiling was added to prevent. Give up on this client and let
                // the caller move to the next one.
                Log.d(TAG, "not retrying, request timed out: ${e.message}")
                throw e
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
     * The typeahead list YouTube Music's own search box shows for a
     * half-typed query — query strings, not results.
     *
     * A different endpoint from [search] rather than a cheap mode of it, and
     * far cheaper than one: the response is a few hundred bytes of text with
     * no shelves, thumbnails or playback endpoints in it, which is what makes
     * it affordable per keystroke where a search is not.
     */
    suspend fun searchSuggestions(input: String): JsonObject =
        postMusic("music/get_search_suggestions") {
            put("input", input)
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
        authenticated: Boolean = false,
    ): JsonObject {
        val response = postPlayer(videoId, client, signatureTimestamp, authenticated)

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
         *
         * [isAgeGate] is excluded, and that exclusion is the whole reason this
         * is not a one-line substring test. YouTube words its age gate "Sign in
         * to confirm your age", which contains "sign in" — so every
         * age-restricted track read as a session-level refusal, and
         * [StreamResolver][com.music.bitchord.data.innertube.StreamResolver]
         * answered it by standing the client down *app-wide* for ten minutes
         * and burning a fresh visitor id. One age-restricted song in a queue
         * therefore took three of the seven clients out of service for
         * everything after it, which is the "it works, then it stops working"
         * report. An age gate is a verdict about one track and one identity; a
         * bot check is a verdict about the session, and only the second one is
         * worth acting on session-wide.
         */
        val looksLikeBotCheck: Boolean
            get() = !isAgeGate && (
                reason.contains("bot", ignoreCase = true) ||
                    reason.contains("unusual traffic", ignoreCase = true) ||
                    reason.contains("sign in", ignoreCase = true) ||
                    reason.contains("login_required", ignoreCase = true)
                )

        /**
         * Whether the track is gated on the viewer's age rather than refused.
         *
         * Worth naming because it is the one refusal a signed-in listener can
         * actually get past: the same client asked again *with* the session
         * cookie is answered `OK` — see [StreamResolver.playerStream]. Both
         * wordings appear on the same track from different clients, which is
         * why both are matched: the TV and VR clients say "Sign in to confirm
         * your age", the iOS and Android ones say "This video may be
         * inappropriate for some users."
         */
        val isAgeGate: Boolean
            get() = reason.contains("confirm your age", ignoreCase = true) ||
                reason.contains("age-restricted", ignoreCase = true) ||
                reason.contains("age restricted", ignoreCase = true) ||
                reason.contains("inappropriate for some users", ignoreCase = true)

        /**
         * Whether asking again can only ever get the same answer — a takedown,
         * a region block, a private or paid video.
         *
         * Deliberately short, and every entry a phrase Google uses for one
         * verdict only. A loose match here is worse than no match: it makes a
         * track that would have played on the next client unplayable for ten
         * minutes (see [StreamResolver]'s verdict cache), so "unavailable" —
         * which Google says while bot-checking as readily as while refusing —
         * is not in the list and is not going to be.
         */
        val isPermanent: Boolean
            get() = PERMANENT_REASONS.any { reason.contains(it, ignoreCase = true) }

        private companion object {
            private val PERMANENT_REASONS = listOf(
                "not available in your country",
                "who has blocked it in your country",
                "removed by the uploader",
                "account associated with this video has been terminated",
                "private video",
                "members-only",
            )
        }
    }

    /** The stats endpoints a player response nominates for one playback. */
    data class PlaybackTracking(
        val playbackUrl: String,
        val watchtimeUrl: String?,
        /** The ad-tracking ping real clients fire a few seconds in. */
        val atrUrl: String?,
        /** How far in [atrUrl] is due, per the response's own schedule. */
        val atrAfterSeconds: Long,
    )

    /**
     * Player response fetched *with* the session cookie, purely to read back
     * `playbackTracking` — [player] deliberately skips auth so its device
     * clients are answered at all, so it never sees this block. Null for
     * guests: there's no account history to update.
     *
     * [signatureTimestamp] is not optional in practice, and that is the bug
     * this whole file was reported for.
     *
     * WEB_REMIX is a browser identity, and a browser proves it is running
     * YouTube's current player by quoting that player's timestamp. Without one
     * — or with a stale one — Google does not refuse the request in any way a
     * caller would notice: it answers HTTP 200, `playabilityStatus` `UNPLAYABLE`,
     * reason "Video unavailable", subreason "The page needs to be reloaded",
     * and simply omits `playbackTracking` entirely. So every play registration
     * this app made returned null here, logged one line, and stopped. No ping
     * was ever sent; no history was ever written. Nothing failed loudly enough
     * to notice, which is why it read as working.
     *
     * It is the *only* gate. Verified against the live endpoint: with a current
     * timestamp and nothing else — no visitor id, no referer, no
     * `html5Preference` — the block comes back. With every one of those and a
     * timestamp one revision old, it does not.
     */
    suspend fun playbackTracking(videoId: String, signatureTimestamp: Int?): PlaybackTracking? {
        if (cookie == null) return null
        ensureSessionScope()
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
                    signatureTimestamp?.let { put("signatureTimestamp", it) }
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
                    "reason=${playability?.get("reason")?.jsonPrimitive?.content}, " +
                    "sts=${signatureTimestamp ?: "none"})",
            )
            return null
        }
        val playbackUrl = tracking.trackingUrl("videostatsPlaybackUrl") ?: return null
        return PlaybackTracking(
            playbackUrl = playbackUrl,
            watchtimeUrl = tracking.trackingUrl("videostatsWatchtimeUrl"),
            atrUrl = tracking.trackingUrl("atrUrl"),
            atrAfterSeconds = tracking["atrUrl"]?.jsonObject
                ?.get("elapsedMediaTimeSeconds")?.jsonPrimitive?.contentOrNull
                ?.toLongOrNull() ?: DEFAULT_ATR_SECONDS,
        )
    }

    /** What YouTube Music itself schedules `atr` for, when it doesn't say. */
    private const val DEFAULT_ATR_SECONDS = 5L

    private fun JsonObject.trackingUrl(key: String): String? =
        this[key]?.jsonObject?.get("baseUrl")?.jsonPrimitive?.content

    /**
     * The "playback started" ping real YouTube Music clients send once a track
     * becomes audible. This is what creates the history entry the home feed
     * feeds off. [cpn] is the client-playback-nonce identifying this one play:
     * it must be the same value used for every [pingWatchtime] that follows.
     *
     * No `el` here. The base URL already carries `el=detailpage` — Google puts
     * it there — and a repeated query parameter is not a stronger statement of
     * the same thing, it is an ambiguous request whose resolution is Google's
     * to decide.
     */
    suspend fun pingPlayback(baseUrl: String, cpn: String) = pingStats(baseUrl, cpn) {}

    /**
     * The follow-up ping reporting how much of the track was actually heard.
     * A history entry with no watchtime behind it reads as a skip, so it
     * carries little weight in recommendations — [seconds] is what makes the
     * play count. `st`/`et` are the watched segment's bounds, in seconds.
     *
     * @param final whether this is the last report for the play, which is what
     *   lets Google close the play out rather than leave it looking abandoned.
     */
    suspend fun pingWatchtime(baseUrl: String, cpn: String, seconds: Long, final: Boolean = false) =
        pingStats(baseUrl, cpn) {
            parameter("st", "0")
            parameter("et", seconds.toString())
            // Where the playhead is, as distinct from how much was watched.
            // The web client sends both and they are not redundant: `et` bounds
            // a segment, `cmt` is a position.
            parameter("cmt", seconds.toString())
            parameter("state", if (final) "paused" else "playing")
            if (final) parameter("final", "1")
        }

    /**
     * The `atr` ping, fired a few seconds into a play.
     *
     * Not analytics garnish. It is the third leg of the sequence a real client
     * performs — playback, atr, watchtime — and the one that distinguishes a
     * play that started from a play that happened. Its base URL already carries
     * `ver`, `c` and `cver`, so unlike the others it is sent as-is.
     */
    suspend fun pingAtr(baseUrl: String, cpn: String): Int = client.get(baseUrl) {
        parameter("cpn", cpn)
        statsHeaders()
    }.status.value

    /** Shared shape of the s.youtube.com stats pings, including session auth. */
    private suspend fun pingStats(
        baseUrl: String,
        cpn: String,
        extras: HttpRequestBuilder.() -> Unit,
    ): Int = client.get(baseUrl) {
        parameter("ver", "2")
        parameter("c", "WEB_REMIX")
        parameter("cver", webRemixVersion)
        parameter("cpn", cpn)
        // What the web client says about itself. Cheap, and the pings are
        // weighted by how much they look like a real session.
        parameter("cplayer", "UNIPLAYER")
        parameter("cbr", "Chrome")
        parameter("cbrver", "141.0.0.0")
        parameter("cos", "Windows")
        parameter("cosver", "10.0")
        parameter("hl", "en_US")
        parameter("cr", "US")
        extras()
        statsHeaders()
    }.status.value

    /**
     * The three headers the tracking block asks for by name — `USER_AUTH`,
     * `VISITOR_ID` and `PLUS_PAGE_ID`. Google lists them per ping URL in the
     * player response; sending fewer is what makes a ping land somewhere other
     * than the listener's own history.
     */
    private fun HttpRequestBuilder.statsHeaders() {
        header("X-Origin", MUSIC_ORIGIN)
        header("Origin", MUSIC_ORIGIN)
        header("Referer", "$MUSIC_ORIGIN/")
        header("User-Agent", WEB_USER_AGENT)
        visitorData?.let { header("X-Goog-Visitor-Id", it) }
        cookie?.let { c ->
            header("Cookie", c)
            header("X-Goog-AuthUser", scope?.authUser ?: "0")
            scope?.pageId?.let { header("X-Goog-PageId", it) }
            sapisidFrom(c)?.let { header("Authorization", sapisidHash(it)) }
        }
    }

    // ---- Writes -------------------------------------------------------------
    //
    // Everything below changes something on the account, so all of it needs
    // the session cookie [postMusic] already signs with. None of it needs a
    // new credential or a different client — the same WEB_REMIX identity that
    // reads the library is the one allowed to edit it.

    /** A write attempted without a session; the caller has a sign-in prompt to show. */
    class NotSignedInException : IllegalStateException("Sign in to YouTube Music to do that")

    private fun requireSession() {
        if (cookie == null) throw NotSignedInException()
    }

    /**
     * Thumbs up / down / neither, for [videoId].
     *
     * The response is inspected rather than discarded. Innertube answers a
     * refused write with HTTP 200 and an `error` object in the body, so the
     * status line alone will happily report a rating that never happened.
     */
    suspend fun rate(videoId: String, status: LikeStatus) {
        requireSession()
        val endpoint = when (status) {
            LikeStatus.LIKE -> "like/like"
            LikeStatus.DISLIKE -> "like/dislike"
            LikeStatus.INDIFFERENT -> "like/removelike"
        }
        val response = postMusic(endpoint) {
            putJsonObject("target") { put("videoId", videoId) }
        }
        response["error"]?.let { error ->
            val message = error.jsonObject["message"]?.jsonPrimitive?.contentOrNull
            error("YouTube Music refused the rating: ${message ?: error}")
        }
        // YouTube states what it did in the toast it would have shown. Worth
        // keeping: a rating it declines to act on still answers 200, and this
        // one line is the difference between "the call was made" and "the
        // call did something".
        Log.d(TAG, "$endpoint $videoId -> ${findString(response, "text") ?: "no confirmation"}")
    }

    /**
     * Saves an album or playlist to the library, or takes it back out.
     *
     * The same endpoints [rate] uses, aimed at a playlist instead of a video:
     * YouTube has no separate "save" verb for a release — a saved album *is* a
     * liked one, which is why the Library tab's Albums and Playlists shelves and
     * the account's likes are the same list. [playlistId] is the id the page
     * itself named, not its browse id; see
     * [com.music.bitchord.data.model.LibraryState].
     *
     * No dislike half, unlike [rate]: nothing in YouTube Music reads a disliked
     * release, so the only two states worth expressing are saved and not.
     */
    suspend fun ratePlaylist(playlistId: String, saved: Boolean) {
        requireSession()
        val endpoint = if (saved) "like/like" else "like/removelike"
        val response = postMusic(endpoint) {
            putJsonObject("target") { put("playlistId", playlistId) }
        }
        // As in [rate]: a refusal arrives as HTTP 200 with an error in the body.
        response["error"]?.let { error ->
            val message = error.jsonObject["message"]?.jsonPrimitive?.contentOrNull
            error("YouTube Music refused the change: ${message ?: error}")
        }
        Log.d(TAG, "$endpoint $playlistId -> ${findString(response, "text") ?: "no confirmation"}")
    }

    /**
     * Adds or removes a track from the library, using a token minted by
     * YouTube for exactly that transition — see [com.music.bitchord.data.model.SongMenu].
     * There is no video-id form of this call; the token *is* the request.
     */
    suspend fun sendFeedback(token: String) {
        requireSession()
        postMusic("feedback") {
            putJsonArray("feedbackTokens") { add(token) }
        }
    }

    /**
     * Creates a playlist and returns its id.
     *
     * [videoIds] seeds it in the same request, which is what "add to a new
     * playlist" is: one round trip rather than a create followed by an edit
     * that could half-succeed.
     */
    suspend fun createPlaylist(
        title: String,
        privacy: PlaylistPrivacy,
        description: String? = null,
        videoIds: List<String> = emptyList(),
    ): String {
        requireSession()
        val response = postMusic("playlist/create") {
            put("title", title)
            put("description", description.orEmpty())
            put("privacyStatus", privacy.apiValue)
            if (videoIds.isNotEmpty()) {
                putJsonArray("videoIds") { videoIds.forEach { add(it) } }
            }
        }
        // Normally a bare top-level id; occasionally only inside the command
        // that would navigate the web client to the new page, so fall back to
        // finding it by name rather than by a path that would rot.
        return response["playlistId"]?.jsonPrimitive?.contentOrNull
            ?: findString(response, "playlistId")
            ?: error("playlist created but no id came back")
    }

    suspend fun deletePlaylist(playlistId: String) {
        requireSession()
        postMusic("playlist/delete") { put("playlistId", playlistId.removePrefix("VL")) }
    }

    /**
     * One or more edits to a playlist, applied together.
     *
     * The endpoint answers `STATUS_SUCCEEDED` rather than an HTTP error when
     * it refuses — a playlist the account merely saved rather than owns is
     * the usual reason — so the body is checked as well as the status line.
     */
    private suspend fun editPlaylist(
        playlistId: String,
        actions: JsonArrayBuilder.() -> Unit,
    ): JsonObject {
        requireSession()
        val response = postMusic("browse/edit_playlist") {
            // The edit endpoint takes the raw id; `VL` is the browse prefix.
            put("playlistId", playlistId.removePrefix("VL"))
            putJsonArray("actions", actions)
        }
        val status = response["status"]?.jsonPrimitive?.contentOrNull
        if (status != null && status != "STATUS_SUCCEEDED") {
            error("YouTube Music refused the edit ($status)")
        }
        return response
    }

    /**
     * Adds tracks to a playlist, and reports the per-entry id each one landed
     * under — video id to set-video-id, for the tracks the response named.
     *
     * Worth reading rather than discarding, because it is the only chance to
     * learn it without re-fetching the whole playlist: a set-video-id is minted
     * by this call, and it is what a later removal has to be expressed in (see
     * [removeFromPlaylist]). A row added to a playlist already on screen is
     * otherwise one the user can see but not take back out until the page is
     * reopened.
     *
     * Absences are normal and not an error — the add still happened; only the
     * id for undoing it is unknown.
     */
    suspend fun addToPlaylist(playlistId: String, videoIds: List<String>): Map<String, String> {
        val response = editPlaylist(playlistId) {
            videoIds.forEach { videoId ->
                addJsonObject {
                    put("action", "ACTION_ADD_VIDEO")
                    put("addedVideoId", videoId)
                }
            }
        }
        return (response["playlistEditResults"] as? JsonArray)
            .orEmpty()
            .mapNotNull { result ->
                val added = (result as? JsonObject)
                    ?.get("playlistEditVideoAddedResultData") as? JsonObject
                    ?: return@mapNotNull null
                val videoId = (added["videoId"] as? JsonPrimitive)?.contentOrNull
                    ?: return@mapNotNull null
                val setVideoId = (added["setVideoId"] as? JsonPrimitive)?.contentOrNull
                    ?: return@mapNotNull null
                videoId to setVideoId
            }
            .toMap()
    }

    /**
     * Removes entries from a playlist. Keyed by set-video-id as well as video
     * id: the same track added twice is two entries, and only the pair says
     * which of them to drop.
     */
    suspend fun removeFromPlaylist(playlistId: String, entries: List<Pair<String, String>>) {
        editPlaylist(playlistId) {
            entries.forEach { (setVideoId, videoId) ->
                addJsonObject {
                    put("action", "ACTION_REMOVE_VIDEO")
                    put("setVideoId", setVideoId)
                    put("removedVideoId", videoId)
                }
            }
        }
    }

    suspend fun renamePlaylist(playlistId: String, title: String) {
        editPlaylist(playlistId) {
            addJsonObject {
                put("action", "ACTION_SET_PLAYLIST_NAME")
                put("playlistName", title)
            }
        }
    }

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
        val session = scope
        val clientVersion = webRemixVersion
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
                header("X-YouTube-Client-Version", clientVersion)
                visitorData?.let { header("X-Goog-Visitor-Id", it) }
                cookie?.let { c ->
                    header("Cookie", c)
                    // Which account in the jar, and which brand channel of it.
                    // Both were fixed at "the first one" before — see
                    // [SessionScope].
                    header("X-Goog-AuthUser", session?.authUser ?: "0")
                    session?.pageId?.let { header("X-Goog-PageId", it) }
                    sapisidFrom(c)?.let { header("Authorization", sapisidHash(it)) }
                }
                setBody(
                    buildJsonObject {
                        putJsonObject("context") {
                            putJsonObject("client") {
                                put("clientName", "WEB_REMIX")
                                put("clientVersion", clientVersion)
                                put("hl", "en")
                                put("gl", "US")
                                visitorData?.let { put("visitorData", it) }
                            }
                            putJsonObject("user") {
                                put("lockedSafetyMode", false)
                                // Only ever a value read back from a shell that
                                // said it was signed in: Google answers an
                                // `onBehalfOfUser` it cannot tie to the cookie
                                // with 401, so a guess here would take the
                                // whole app down rather than just history.
                                session?.dataSyncId?.let { put("onBehalfOfUser", it) }
                            }
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
     * Unauthenticated by default.
     *
     * The app clients [StreamResolver] walks through are answered *because*
     * they look like anonymous devices; attaching the session cookie to one
     * of those is what gets it turned away with `LOGIN_REQUIRED`. Nothing
     * about the account is needed to fetch audio through them — history is
     * credited separately, by [playbackTracking] and the stats pings, which
     * do carry the session.
     *
     * [authenticated] is the deliberate exception, and there are two callers of
     * it. [PlayerClient.WEB_REMIX] is a browser identity, and a browser without
     * the session cookie a signed-in listener actually has is the thing that
     * reads as suspicious, not the other way around.
     *
     * The second is an age gate. A device client refused with "Sign in to
     * confirm your age" has already told us the anonymous request will not be
     * answered, so there is nothing left to protect by withholding the session
     * — and everything to gain, because the device clients return *unciphered*
     * `url` fields. That is the only route to an age-restricted track that does
     * not depend on solving a signature. See [StreamResolver.playerStream].
     *
     * Only meaningful with [cookie] set — a caller asking for it while signed
     * out gets the same unauthenticated request as everything else.
     */
    private suspend fun postPlayer(
        videoId: String,
        playerClient: PlayerClient,
        signatureTimestamp: Int?,
        authenticated: Boolean = false,
    ): JsonObject =
        client.post("${playerClient.apiBase()}/player") {
            // A much tighter budget than the shared 30 seconds, because this is
            // the one request on a loop. A player call that is going to answer
            // answers in 120-330ms; one that is going to hang is indifferent to
            // how long it is given, and there are up to seven clients walked
            // per track, each of which may be retried. At the shared ceiling a
            // single unlucky client turned a walk that normally costs two
            // seconds into forty-nine, which the listener spends staring at a
            // track that will in the end be served by extraction anyway. Six
            // seconds is twenty times a healthy answer and cheap to give up on.
            //
            // Set here rather than on the shared client on purpose: browse and
            // search return payloads orders of magnitude larger over the same
            // connection, and a ceiling right for this would truncate those.
            timeout { requestTimeoutMillis = PLAYER_TIMEOUT_MS }
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
            if (authenticated) {
                cookie?.let { c ->
                    header("Cookie", c)
                    header("X-Goog-AuthUser", scope?.authUser ?: "0")
                    scope?.pageId?.let { header("X-Goog-PageId", it) }
                    // Hashed against the host this request is actually going
                    // to, not against music.youtube.com unconditionally. Google
                    // recomputes the digest over the origin it sees and rejects
                    // a mismatch with 401, so an app client posting to
                    // www.youtube.com signed for the music origin is not a
                    // weaker request — it is a refused one, which would have
                    // made the age-gate retry below look like a dead end.
                    val origin = playerClient.origin
                        ?: if (playerClient.usesMusicHost) MUSIC_ORIGIN else YOUTUBE_ORIGIN
                    sapisidFrom(c)?.let { header("Authorization", sapisidHash(it, origin)) }
                }
            }
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

    /** First string value under [key] anywhere in [element], depth-first. */
    private fun findString(element: JsonElement, key: String): String? = when (element) {
        is JsonObject -> (element[key] as? JsonPrimitive)?.contentOrNull
            ?: element.values.firstNotNullOfOrNull { findString(it, key) }
        is JsonArray -> element.firstNotNullOfOrNull { findString(it, key) }
        else -> null
    }

    /**
     * The API-signing secret out of a cookie header.
     *
     * Three names for one value, and all three have to be looked for. `SAPISID`
     * is the one everybody documents, and it is also the one a cookie jar can
     * be missing: on a third-party-cookie-partitioned or `__Host`-prefixed
     * login, Google sets only the `__Secure-` forms. Any of them signs a
     * request; the digest does not care which it came from.
     *
     * The cost of not looking was invisible and total. `AuthStore.isSignedIn`
     * tests the cookie for the *substring* `SAPISID`, which `__Secure-3PAPISID`
     * satisfies — so the app knew it was signed in, sent the cookie, and sent
     * no `Authorization` header, which Google reads as a request from nobody.
     * Every write and every history ping was silently anonymous for those
     * users, while the UI showed them signed in.
     *
     * Order matters: the plain form first because it is what Google's own
     * origin-scoped hash is documented against, then the third-party form, then
     * the first-party one.
     */
    private fun sapisidFrom(cookieHeader: String): String? {
        val jar = cookieHeader.split(';')
            .mapNotNull { entry ->
                val name = entry.substringBefore('=').trim()
                val value = entry.substringAfter('=', "").trim()
                if (name.isEmpty() || value.isEmpty()) null else name to value
            }
            .toMap()
        return SAPISID_NAMES.firstNotNullOfOrNull { jar[it] }
    }

    private val SAPISID_NAMES =
        listOf("SAPISID", "__Secure-3PAPISID", "__Secure-1PAPISID")

    private fun sapisidHash(sapisid: String, origin: String = MUSIC_ORIGIN): String {
        val timestamp = System.currentTimeMillis() / 1000
        val digest = MessageDigest.getInstance("SHA-1")
            .digest("$timestamp $sapisid $origin".toByteArray())
            .joinToString("") { "%02x".format(Locale.ROOT, it) }
        return "SAPISIDHASH ${timestamp}_$digest"
    }
}
