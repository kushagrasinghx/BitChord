package com.music.bitchord.data.innertube

import com.music.bitchord.auth.normalizeDataSyncId
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
import com.music.bitchord.data.service.ServiceConfig
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
import java.util.Base64
import java.util.Locale
import androidx.appcompat.app.AppCompatDelegate

/**
 * Minimal inner-tube client.
 *
 * The file's web client against the music host for browse/search/library.
 * It returns the full shelf layout and honours the signed-in session.
 * Stream URLs are not fetched here; see [InnerTubeXResolver].
 *
 * Every host, version and user agent below arrives in the imported service
 * file — see [ServiceConfig]. There are no working values here, only the
 * shapes of the requests. Authenticated requests are signed with the
 * SAPISIDHASH scheme derived from the stored cookie; no long-lived token is
 * ever minted or stored.
 */
object Innertube {
    internal val currentLanguage: String
        get() {
            val raw = AppCompatDelegate.getApplicationLocales().get(0)?.language?.ifEmpty { null }
                ?: Locale.getDefault().language.ifEmpty { "en" }
            return when (raw.lowercase(Locale.ROOT)) {
                "iw" -> "he"
                "in" -> "id"
                "ji" -> "yi"
                else -> raw
            }
        }

    private val acceptLanguageHeader: String
        get() {
            val lang = currentLanguage
            return if (lang == "en") {
                "en-US,en;q=0.9"
            } else {
                "$lang,en-US;q=0.8,en;q=0.7"
            }
        }

    // All service hosts and identities arrive in the imported service file.
    // Getters (not constants) so a freshly imported file takes effect
    // without restarting the process. Every one throws
    // [com.music.bitchord.data.service.ServiceFileRequired] while none is
    // installed, which is what gates every service-backed path on the file.
    private val MUSIC_BASE: String get() = ServiceConfig.musicBase()
    private val MUSIC_ORIGIN: String get() = ServiceConfig.musicOrigin()
    private val YOUTUBE_ORIGIN: String get() = ServiceConfig.tubeOrigin()

    private val WEB_USER_AGENT: String get() = ServiceConfig.webUa()

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
                // The chosen channel belonged to the account that just left.
                // A `dataSyncId` from one login sent under another's cookie is
                // answered with 401 on every request, so it goes with it.
                channelOverride = null
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
        val body = client.get(ServiceConfig.swDataUrl()) {
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

    /** A channel the listener picked, standing in for the shell's default. */
    class ChannelSelection(
        val pageId: String?,
        val dataSyncId: String?,
        /**
         * Which Google account in the cookie jar the channel belongs to. Null
         * leaves the shell's own answer alone — a brand channel sits under the
         * account that owns it, so this only differs when the listener switched
         * to a channel of a *different* signed-in Google account.
         */
        val authUser: String? = null,
    )

    /**
     * The channel to act as, when the listener has said which.
     *
     * [fetchSessionScope] can only report the one music.youtube.com serves by
     * default, and for an account whose music lives on a brand channel that is
     * the wrong one — the library, the likes and the history all belong to the
     * other identity. Once a channel has been chosen it outranks the shell
     * completely, including when what it chose is the account's own channel and
     * the shell is the one offering a brand page.
     */
    @Volatile
    private var channelOverride: ChannelSelection? = null

    /**
     * Act as this channel from now on; both null goes back to the shell's
     * default. Takes effect on the next request — nothing is cached from it.
     */
    fun selectChannel(pageId: String?, dataSyncId: String?, authUser: String? = null) {
        channelOverride = if (pageId == null && dataSyncId == null) {
            null
        } else {
            ChannelSelection(pageId, dataSyncId, authUser)
        }
        Log.d(
            TAG,
            "acting as channel pageId=${pageId ?: "none"} authUser=${authUser ?: "as-is"} " +
                "(override=${channelOverride != null})",
        )
    }

    /**
     * Takes the session scope from a page the listener was actually looking at,
     * rather than working it out later from a fetch of our own.
     *
     * The shell fetch in [fetchSessionScope] can only ever report the channel
     * music.youtube.com serves this app by default, and the whole reason the
     * in-app browser exists is that the listener has just told it, by hand,
     * that they want a different one. That answer is written into the page's
     * own `ytcfg`, so it is read from there and adopted whole.
     *
     * Adopting also settles [ensureSessionScope] — a scope already in hand is
     * not refetched — so the shell cannot quietly overwrite the choice with its
     * default on the next request.
     *
     * A page that reported itself signed out is ignored apart from its client
     * version: its `DATASYNC_ID` belongs to no account, and sending one Google
     * cannot tie to the session is answered with 401 on every request.
     */
    fun adoptSessionScope(
        pageId: String?,
        dataSyncId: String?,
        authUser: String?,
        visitorData: String?,
        clientVersion: String?,
        loggedIn: Boolean,
    ) {
        val version = clientVersion ?: scope?.clientVersion
        if (!loggedIn) {
            Log.w(TAG, "captured page was signed out; not scoping requests to it")
            scope = version?.let { SessionScope(null, null, "0", it) }
            return
        }
        scope = SessionScope(
            dataSyncId = dataSyncId?.takeIf { it.isNotBlank() },
            pageId = pageId?.takeIf { it.isNotBlank() },
            authUser = authUser?.takeIf { it.isNotBlank() } ?: "0",
            clientVersion = version,
        )
        // The page's own visitor id, bound to this session — strictly better
        // than the anonymous one [fetchVisitorData] mints. See [visitorData].
        visitorData?.takeIf { it.isNotBlank() }?.let {
            this.visitorData = it
            visitorDataIsSessionBound = true
        }
        Log.d(TAG, "adopted page scope: pageId=${pageId ?: "none"} authUser=${authUser ?: "0"}")
    }

    /** The brand channel to send, chosen one first. */
    private fun pageIdFor(session: SessionScope?): String? =
        (channelOverride ?: return session?.pageId).pageId

    /**
     * The account to send as `onBehalfOfUser`, chosen one first.
     *
     * No falling back to the shell's value once a channel has been chosen: the
     * shell's id names the default identity, and pairing it with another
     * channel's [pageId] describes an account/page combination that doesn't
     * exist.
     */
    private fun dataSyncIdFor(session: SessionScope?): String? =
        (channelOverride ?: return session?.dataSyncId).dataSyncId

    /** Which account in the cookie jar, chosen channel's first. */
    private fun authUserFor(session: SessionScope?): String =
        channelOverride?.authUser ?: session?.authUser ?: "0"

    /**
     * The web client version to claim, live if the shell has been read.
     *
     * Worth taking from the shell rather than pinning: the stats pings carry
     * it as `cver`, and a version the service has never shipped is a standing
     * invitation to be treated as something other than a music client.
     */
    private val webRemixVersion: String
        get() = scope?.clientVersion ?: ServiceConfig.webVersion()

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
                    // A login/profile switch can happen while the shell is in
                    // flight. Never install the old cookie's answer under the
                    // new one: that is a guaranteed 401 and, worse, can credit
                    // a play to the profile that just left.
                    if (cookie != session) {
                        Log.d(TAG, "discarding a session scope from an account that is no longer active")
                        return@let
                    }
                    scope = fresh
                    channelOverride?.let { selected ->
                        if (selected.pageId != fresh.pageId || selected.dataSyncId != fresh.dataSyncId) {
                            Log.w(TAG, "server shell identity differs from selected profile; retaining override")
                        }
                    }
                    Log.d(
                        TAG,
                        "session scope: authUser=${fresh.authUser} " +
                            "pageId=${fresh.pageId ?: "none"} " +
                            "dataSyncId=${if (fresh.dataSyncId != null) "present" else "none"} " +
                            "cver=${fresh.clientVersion ?: ServiceConfig.webVersion()}",
                    )
                }
        }
    }

    /** Re-read request context once after an authenticated rejection. */
    suspend fun refreshSessionScope() {
        if (cookie == null) return
        scopeLock.withLock { scope = null }
        ensureSessionScope()
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
        val shellUrl = "$MUSIC_ORIGIN/"
        val html = client.get(shellUrl) {
            header("User-Agent", WEB_USER_AGENT)
            header("Accept-Language", acceptLanguageHeader)
            // The shell carries the account; without the cookie it cannot say
            // whose. Guarded like every other session send.
            if (sendAuthTo(shellUrl)) {
                header("Cookie", session)
                sapisidFrom(session)?.let { header("Authorization", sapisidHash(it)) }
            }
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

        val pageId = CONFIG_PAGE_ID.find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
        val dataSyncId = pageId ?: normalizeDataSyncId(
            CONFIG_DATASYNC_ID.find(html)?.groupValues?.get(1),
        )
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
     * Every channel this session can act as, as Innertube's account switcher
     * lists them: the account's own channel first, then its brand channels.
     *
     * Each entry carries the two things a request needs to be made *as* that
     * channel — a `pageIdToken` and a `datasyncIdToken` — which is the whole
     * reason to ask rather than to reason about it. See [selectChannel].
     */
    suspend fun accountsList(): JsonObject = postMusic("account/accounts_list") {}

    /**
     * The same list from youtube.com's own switcher, as a second route.
     *
     * Worth having both. `accounts_list` is the tidier call but it is not
     * uniformly answered for every client identity, and a listener whose music
     * is on a brand channel is stuck with the wrong library until *something*
     * enumerates their channels. This endpoint is what the youtube.com avatar
     * menu itself calls, and it answers a plain signed GET.
     *
     * The body is JSON behind Google's XSSI guard — a `)]}'` line that exists
     * to make the response invalid JavaScript — so it is trimmed before parsing
     * rather than being handed to the JSON reader as-is.
     */
    suspend fun accountSwitcher(): JsonObject {
        requireSession()
        ensureSessionScope()
        val session = scope
        val text = withRetry {
            client.get("$YOUTUBE_ORIGIN/getAccountSwitcherEndpoint") {
                header("User-Agent", WEB_USER_AGENT)
                header("Accept-Language", acceptLanguageHeader)
                header("X-Origin", YOUTUBE_ORIGIN)
                header("Referer", "$YOUTUBE_ORIGIN/")
                if (sendAuthTo("$YOUTUBE_ORIGIN/getAccountSwitcherEndpoint")) cookie?.let { c ->
                    header("Cookie", c)
                    header("X-Goog-AuthUser", authUserFor(session))
                    sapisidFrom(c)?.let {
                        header("Authorization", sapisidHash(it, YOUTUBE_ORIGIN))
                    }
                }
            }.bodyAsText()
        }
        val body = text.substringAfter(")]}'", text).trim()
        return json.parseToJsonElement(body).jsonObject
    }

    /**
     * The watch queue that YouTube Music would play after [videoId] — the
     * "RDAMVM" radio mix. Used to keep AutoPlay going past the last track.
     */
    suspend fun next(videoId: String): JsonObject = postMusic("next") {
        put("videoId", videoId)
        put("playlistId", "RDAMVM$videoId")
        put("isAudioOnly", true)
    }

    /** Timed caption transcript used as a last-resort lyrics source. */
    suspend fun transcript(videoId: String): JsonObject = postMusic("get_transcript") {
        // get_transcript expects a tiny protobuf: field 1, length, video id.
        val bytes = byteArrayOf(10, videoId.toByteArray().size.toByte()) + videoId.toByteArray()
        put("params", Base64.getEncoder().encodeToString(bytes))
    }

    suspend fun search(query: String, params: String? = null): JsonObject =
        postMusic("search") {
            put("query", query)
            params?.let { put("params", it) }
        }

    /** The next page of a filtered search result. */
    suspend fun searchContinuation(token: String): JsonObject = postMusic(
        endpoint = "search",
        query = mapOf("ctoken" to token, "continuation" to token, "type" to "next"),
    ) {
        put("continuation", token)
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
     * Live media results for the typeahead phase — same shape as [search] but
     * deliberately unauthenticated so YouTube Music does not log each debounced
     * keystroke to the account's server-side search history.
     *
     * The regular [search] endpoint records every call against the signed-in
     * account, which turns a slow typist's intermediate queries ("P", "Pe",
     * "Perf…") into polluting history entries.  By omitting the session cookie
     * here we still get full search results (tracks, artists, albums) but they
     * land as anonymous lookups that don't touch the user's account history.
     */
    suspend fun searchTypeahead(query: String): JsonObject =
        postMusicAnonymous("search") {
            put("query", query)
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
     * The file's web client is a browser identity, and a browser proves it is running
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
    suspend fun pingAtr(baseUrl: String, cpn: String): Int {
        // Playback can outlive the Activity that restored the session. Ensure
        // the selected profile's headers exist before this first history ping.
        if (cookie != null) ensureSessionScope()
        val session = scope
        return client.get(baseUrl) {
            parameter("cpn", cpn)
            statsHeaders(session, sendAuthTo(baseUrl))
        }.status.value
    }

    /** Shared shape of the stats pings served off the configured hosts, including session auth. */
    private suspend fun pingStats(
        baseUrl: String,
        cpn: String,
        extras: HttpRequestBuilder.() -> Unit,
    ): Int {
        if (cookie != null) ensureSessionScope()
        val session = scope
        val stats = ServiceConfig.webStats()
        return client.get(baseUrl) {
            parameter("ver", "2")
            parameter("c", ServiceConfig.webName())
            parameter("cver", webRemixVersion)
            parameter("cpn", cpn)
            // What the web client says about itself. Cheap, and the pings are
            // weighted by how much they look like a real session.
            parameter("cplayer", stats.cplayer)
            parameter("cbr", stats.cbr)
            parameter("cbrver", stats.cbrver)
            parameter("cos", stats.cos)
            parameter("cosver", stats.cosver)
            parameter("hl", "en_US")
            parameter("cr", "US")
            extras()
            statsHeaders(session, sendAuthTo(baseUrl))
        }.status.value
    }

    /**
     * The three headers the tracking block asks for by name — `USER_AUTH`,
     * `VISITOR_ID` and `PLUS_PAGE_ID`. The service lists them per ping URL in
     * the player response; sending fewer is what makes a ping land somewhere
     * other than the listener's own history.
     *
     * [sendAuth] gates the session half: ping URLs arrive inside the player
     * response rather than the service file, so a response pointing them at a
     * host outside the auth guard gets anonymous pings only.
     */
    private fun HttpRequestBuilder.statsHeaders(session: SessionScope?, sendAuth: Boolean = true) {
        header("X-Origin", MUSIC_ORIGIN)
        header("Origin", MUSIC_ORIGIN)
        header("Referer", "$MUSIC_ORIGIN/")
        header("User-Agent", WEB_USER_AGENT)
        visitorData?.let { header("X-Goog-Visitor-Id", it) }
        if (sendAuth) cookie?.let { c ->
            header("Cookie", c)
            header("X-Goog-AuthUser", authUserFor(session))
            pageIdFor(session)?.let { header("X-Goog-PageId", it) }
            sapisidFrom(c)?.let { header("Authorization", sapisidHash(it)) }
        }
    }

    // ---- Writes -------------------------------------------------------------
    //
    // Everything below changes something on the account, so all of it needs
    // the session cookie [postMusic] already signs with. None of it needs a
    // new credential or a different client — the same web identity that
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
     * Subscribes to an artist's channel, or unsubscribes from it.
     *
     * Not one of the `like/…` endpoints: a subscription is a YouTube-wide
     * relationship rather than a Music one, and it is addressed by channel id —
     * the `UC…` the artist page is served under. See
     * [com.music.bitchord.data.model.SubscriptionState].
     *
     * As in [rate], the body is read rather than the status line: Innertube
     * answers a refused write with HTTP 200 and an `error` object.
     */
    suspend fun setSubscribed(channelId: String, subscribed: Boolean) {
        requireSession()
        val endpoint = if (subscribed) "subscription/subscribe" else "subscription/unsubscribe"
        val response = postMusic(endpoint) {
            putJsonArray("channelIds") { add(channelId) }
        }
        response["error"]?.let { error ->
            val message = error.jsonObject["message"]?.jsonPrimitive?.contentOrNull
            error("YouTube Music refused the change: ${message ?: error}")
        }
        Log.d(TAG, "$endpoint $channelId -> ${findString(response, "text") ?: "no confirmation"}")
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
        // This is the common authenticated request path: Home, Library,
        // likes, playlists, account menus and all their continuations pass
        // through it. Waiting here makes restoration process-wide rather than
        // a special case implemented by whichever screen happened to open.
        if (cookie != null) ensureSessionScope()
        val session = scope
        val clientVersion = webRemixVersion
        val response = withRetry {
            client.post("$MUSIC_BASE/$endpoint") {
                contentType(ContentType.Application.Json)
                parameter("prettyPrint", "false")
                parameter("hl", currentLanguage)
                header("Accept-Language", acceptLanguageHeader)
                query.forEach { (key, value) -> parameter(key, value) }
                header("X-Origin", MUSIC_ORIGIN)
                header("Origin", MUSIC_ORIGIN)
                header("Referer", "$MUSIC_ORIGIN/")
                // Stats pings are only honoured for a session Google recognises
                // as a real client, so identify as one here too — the visitor
                // id is minted on the first call and reused for the session.
                header("X-YouTube-Client-Name", ServiceConfig.webId())
                header("X-YouTube-Client-Version", clientVersion)
                visitorData?.let { header("X-Goog-Visitor-Id", it) }
                // Guarded: the session never leaves for a host outside the
                // auth guard suffixes, whatever the imported file claims.
                if (sendAuthTo("$MUSIC_BASE/$endpoint")) cookie?.let { c ->
                    header("Cookie", c)
                    // Which account in the jar, and which brand channel of it.
                    // Both were fixed at "the first one" before — see
                    // [SessionScope].
                    header("X-Goog-AuthUser", authUserFor(session))
                    pageIdFor(session)?.let { header("X-Goog-PageId", it) }
                    sapisidFrom(c)?.let { header("Authorization", sapisidHash(it)) }
                }
                setBody(
                    buildJsonObject {
                        putJsonObject("context") {
                            putJsonObject("client") {
                                put("clientName", ServiceConfig.webName())
                                put("clientVersion", clientVersion)
                                put("hl", currentLanguage)
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
                                dataSyncIdFor(session)?.let { put("onBehalfOfUser", it) }
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
     * Like [postMusic] but deliberately strips the session cookie so YouTube
     * Music does not record the call against any account.
     *
     * Used for typeahead lookups where intermediate keystrokes must remain
     * anonymous — see [searchTypeahead].
     */
    private suspend fun postMusicAnonymous(
        endpoint: String,
        bodyExtras: JsonObjectBuilder.() -> Unit,
    ): JsonObject {
        val clientVersion = webRemixVersion
        return withRetry {
            client.post("$MUSIC_BASE/$endpoint") {
                contentType(ContentType.Application.Json)
                parameter("prettyPrint", "false")
                header("X-Origin", MUSIC_ORIGIN)
                header("Origin", MUSIC_ORIGIN)
                header("Referer", "$MUSIC_ORIGIN/")
                header("X-YouTube-Client-Name", ServiceConfig.webId())
                header("X-YouTube-Client-Version", clientVersion)
                visitorData?.let { header("X-Goog-Visitor-Id", it) }
                // No Cookie / Authorization headers — anonymous request.
                setBody(
                    buildJsonObject {
                        putJsonObject("context") {
                            putJsonObject("client") {
                                put("clientName", ServiceConfig.webName())
                                put("clientVersion", clientVersion)
                                put("hl", "en")
                                put("gl", "US")
                                visitorData?.let { put("visitorData", it) }
                            }
                            putJsonObject("user") {
                                put("lockedSafetyMode", false)
                                // No onBehalfOfUser — no account context.
                            }
                            putJsonObject("request") { put("useSsl", true) }
                        }
                        bodyExtras()
                    },
                )
            }.body<JsonObject>()
        }
    }

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

    /**
     * Whether the session may be attached to a request for [url].
     *
     * The cookie and its signature go out ONLY to a host at or under the
     * auth guard suffixes — see [ServiceConfig.canSendAuth] — no matter what
     * an imported file claims. A hostile file can redirect anonymous traffic,
     * but never harvest the login session.
     */
    private fun sendAuthTo(url: String): Boolean {
        val host = url.substringAfter("://").substringBefore("/").substringBefore(":")
        return ServiceConfig.canSendAuth(host)
    }

    private fun sapisidHash(sapisid: String, origin: String = ServiceConfig.musicOrigin()): String {
        val timestamp = System.currentTimeMillis() / 1000
        val digest = MessageDigest.getInstance("SHA-1")
            .digest("$timestamp $sapisid $origin".toByteArray())
            .joinToString("") { "%02x".format(Locale.ROOT, it) }
        return "SAPISIDHASH ${timestamp}_$digest"
    }
}
