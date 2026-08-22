package com.music.bitchord.data.innertube

import android.os.SystemClock
import android.util.Log
import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.Http
import com.music.bitchord.data.NerdStats
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.ConnectionPool
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Turns a videoId into a URL ExoPlayer can actually stream.
 *
 * Three things make or break this, and the order they are attempted in matters
 * as much as the mechanics of each:
 *
 *  1. **Which endpoint asks.** The `youtubei/v1/player` POST is one small JSON
 *     round trip. The watch page — what a full extractor scrape fetches — is
 *     several hundred kilobytes of HTML and is rate-shaped: under load Google
 *     answers its headers immediately and then feeds the body out over tens of
 *     seconds, or simply stops sending and never closes. That shaping is
 *     invisible as an error and reads to a listener as endless buffering, so
 *     the scrape is kept off the hot path entirely — see [newPipeUrl], the
 *     failsafe of last resort.
 *
 *  2. **Which client asks.** Google turns identities away without notice and
 *     without pattern: the client that works today answers `LOGIN_REQUIRED`
 *     next month. So [CLIENTS] is walked rather than trusted, the one that last
 *     worked is tried first, and one that is refused for a track is stood down
 *     for that track for a while.
 *
 *  3. **Whether the URL is real.** Every googlevideo URL carries an `n`
 *     parameter which, sent as-is, gets the response throttled to a crawl or
 *     refused with 403; it has to be transformed by running YouTube's own
 *     player JavaScript, which is what NewPipe's [YoutubeJavaScriptPlayerManager]
 *     does. That can fail quietly, and a URL can be dead on arrival for reasons
 *     no amount of care predicts — so nothing is handed to the player, or
 *     cached, until a single byte has been fetched from it. See [probe].
 */
object StreamResolver {

    private const val TAG = "BitChord"

    /** Past this, an extractor fetch is worth flagging rather than just noting. */
    private const val SLOW_FETCH_MS = 2000L

    /** See [OkHttpDownloader.execute] — the one request the extractor is not allowed to make. */
    private const val NEXT_ENDPOINT = "/youtubei/v1/next"

    /** Well-formed, empty, and over the library's fifty-character floor. */
    private const val EMPTY_NEXT_RESPONSE =
        """{"responseContext":{},"contents":{},"currentVideoEndpoint":{},"trackingParams":""}"""

    /**
     * Player clients in the order they are worth asking, cheapest and most
     * reliable first — an order taken from what the live endpoint actually
     * answers, not from what ought to work.
     *
     * The four at the top return plain `url` fields, so a stream is one POST
     * away with no player JavaScript involved at all. [PlayerClient.ANDROID]
     * below them hands back ciphered formats, costing a download of that
     * JavaScript and a signature to solve. See each entry in [PlayerClient].
     *
     * No web client appears here. `WEB_REMIX` was the tail of this list and
     * paid for itself in neither reliability nor speed — always ciphered,
     * usually refused, and reached only on tracks that were already failing,
     * where the one thing left worth spending is time. [newPipeUrl] is the
     * last resort instead.
     *
     * The gating that decides which of these answers is applied per network,
     * not globally — an identity refused on one connection is served on
     * another — which is the whole reason this is a list and why the order is
     * only a starting guess that [clientOrder] corrects from experience.
     *
     * TVHTML5 (Cobalt v7) is first because it works on flagged IPs without
     * PO Token — the most reliable client as of July 2026.
     */
    private val CLIENTS = listOf(
        PlayerClient.ANDROID_MUSIC,
        PlayerClient.TVHTML5,
        PlayerClient.ANDROID_VR,
        PlayerClient.ANDROID_VR_LEGACY,
        PlayerClient.IOS,
        PlayerClient.IOS_RECENT,
        PlayerClient.ANDROID,
    )

    /** NewPipe needs a Downloader; reuse the app's single OkHttp client. */
    private class OkHttpDownloader : Downloader() {
        override fun execute(request: Request): Response {
            // The `next` endpoint answers "what plays after this" — related
            // videos and the autoplay queue. `fetchPage()` asks for it
            // unconditionally while building a StreamExtractor, and this app
            // never reads the answer: it is here for [audioStreams] and gets
            // its own up-next from [Innertube.next] on a different client.
            //
            // Declining it is worth a special case because of what it costs.
            // Measured across extractions, every other request in the chain
            // lands in 110-670ms, while this one takes seven seconds or simply
            // hangs — it was the single request behind
            // `extractor fetch FAILED after 12004ms`, and since one hung call
            // fails the whole attempt, an endpoint nothing here reads was
            // deciding whether a track played at all.
            //
            // Answered with an empty-but-well-formed envelope rather than an
            // error, so nothing downstream treats it as a failed fetch; NewPipe
            // finds no related items, which is exactly as many as are wanted.
            // It has to be this padded: the library rejects any JSON body under
            // fifty characters outright with "JSON response is too short", so a
            // bare `{}` fails the whole extraction rather than quietly
            // returning nothing.
            if (NEXT_ENDPOINT in request.url()) {
                return Response(200, "OK", emptyMap(), EMPTY_NEXT_RESPONSE, request.url())
            }
            val builder = okhttp3.Request.Builder()
                .method(request.httpMethod(), request.dataToSend()?.toRequestBody())
                .url(request.url())

            var hasUserAgent = false
            request.headers().forEach { (name, values) ->
                if (name.equals("User-Agent", ignoreCase = true) && values.isNotEmpty()) {
                    hasUserAgent = true
                }
                when {
                    values.size > 1 -> {
                        builder.removeHeader(name)
                        values.forEach { builder.addHeader(name, it) }
                    }
                    values.size == 1 -> builder.header(name, values[0])
                }
            }
            if (!hasUserAgent) {
                // Chrome, not Firefox: NewPipe's own internal fetches — the
                // player JS included — go out under whatever this default is.
                // PixelMusic-ref's equivalent downloader defaults to this same
                // Chrome UA and does not hit the "Could not parse
                // deobfuscation function" failure this app was getting on the
                // identical video and NewPipeExtractor version; a Firefox UA
                // here is the one input that differed.
                builder.header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36",
                )
            }

            // Every byte NewPipe fetches passes through here, and until this
            // line none of it was visible: an extraction that took thirty
            // seconds reported thirty seconds, with nothing to say whether that
            // was one shaped watch page, a player POST, or the player
            // JavaScript. Naming the request and its size is what makes the
            // difference between measuring the step and guessing at it.
            val requestStart = SystemClock.elapsedRealtime()
            val response = try {
                extractorClient.newCall(builder.build()).execute()
            } catch (e: Exception) {
                // The one that matters most, and the one a log written only on
                // the way out never sees: a request that times out or is torn
                // down produces no line at all, so an extraction killed by a
                // single hung call looks like an extraction that was slow for
                // no reason. Named here, then rethrown unchanged.
                TrackLog.w(
                    TAG,
                    "extractor fetch FAILED after ${SystemClock.elapsedRealtime() - requestStart}ms " +
                        "${request.httpMethod()} ${request.url()}: ${e.javaClass.simpleName}: ${e.message}",
                )
                throw e
            }
            val took = SystemClock.elapsedRealtime() - requestStart
            if (took > SLOW_FETCH_MS) {
                TrackLog.w(TAG, "extractor fetch ${took}ms ${request.httpMethod()} ${request.url()}")
            } else {
                TrackLog.d(TAG, "extractor fetch ${took}ms ${request.httpMethod()} ${request.url()}")
            }
            if (response.code == 429) {
                response.close()
                throw ReCaptchaException("reCaptcha Challenge requested", request.url())
            }
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body?.string(),
                response.request.url.toString(),
            )
        }
    }

    private val init by lazy { NewPipe.init(OkHttpDownloader()) }

    /**
     * The extractor's own leash on [Http.client].
     *
     * Everything NewPipe fetches — the watch page above all — goes out through
     * here, and [Http.client] sets no `callTimeout` at all. Its 30-second read
     * timeout does not stand in for one: a read timeout is per read, so a
     * response that yields a few bytes at a time resets it forever and the call
     * never ends. That is not a hypothetical failure mode but the exact shaping
     * this file's header describes Google applying to the watch page, and it
     * was observed doing it — an extraction that simply never returned, twice
     * in a row, leaving a track buffering until ExoPlayer gave up and retried
     * into the same wall. Unbounded is the one thing this call must not be,
     * because it is the failsafe: nothing runs after it.
     *
     * It gets its **own connection pool**, and that is the point of it rather
     * than a detail. Sharing [Http.client]'s pool means the watch-page GET can
     * be handed a connection to `www.youtube.com` left over from an Innertube
     * `player` POST, and a pooled connection that the far end has quietly
     * stopped answering does not fail — it hangs, silently, until something
     * times it out. `retryOnConnectionFailure` cannot save it, because nothing
     * is failing. That is exactly the shape the measurements have: a first
     * attempt that burns the whole ceiling and a second, on a fresh connection,
     * that succeeds in 2.2 seconds. Its own pool means extraction never
     * inherits a socket some other part of the app finished with.
     *
     * Sharing the pool was never required, either. The address-family argument
     * in [Http] is about a googlevideo media fetch matching the `player`
     * request that minted its URL; this fetches HTML from `www.youtube.com`,
     * and the googlevideo URL it comes back with is fetched later through
     * [Http.client] regardless.
     *
     * The ceiling is sized against a healthy extraction — about two seconds —
     * rather than against patience. Twelve is generous enough that a merely
     * slow page still completes, and short enough that a hung one costs a few
     * seconds before [EXTRACTION_ATTEMPTS] tries again on a new connection,
     * instead of half a minute of silence.
     */
    private val extractorClient by lazy {
        Http.client.newBuilder()
            // Short, because a connection this app is *not* using is a
            // connection going stale. The observed failure is a request that
            // gets no response at all and dies on the ceiling exactly — twelve
            // thousand and one milliseconds, over and over, on three different
            // endpoints. That is not a slow server, it is a socket the far side
            // (or a NAT on the way) has silently dropped while it sat idle
            // between tracks, being handed to the next request as though it
            // were good. Half a minute of keepalive is short enough that most
            // are re-established rather than resurrected.
            .connectionPool(ConnectionPool(4, 30, TimeUnit.SECONDS))
            // And for the ones that go stale while held: HTTP/2 pings make the
            // client notice a dead peer itself, in seconds, instead of waiting
            // out a response that is never coming. These endpoints are all
            // HTTP/2, so this is the mechanism actually available for detecting
            // it rather than merely giving up on it.
            .pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
            .callTimeout(EXTRACTOR_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    private const val PING_INTERVAL_SECONDS = 5L

    /**
     * Sized so that giving up and trying again is cheaper than waiting.
     *
     * A healthy extractor request lands in 100-700ms and the retry that follows
     * a hung one has, every time it has been watched, succeeded immediately —
     * so the ceiling is not protecting a slow-but-viable request, it is deciding
     * how long a dead connection costs. At twelve seconds one stall turned a
     * four-second start into twenty-six; at five, the same stall costs about
     * six seconds all in.
     */
    private const val EXTRACTOR_TIMEOUT_SECONDS = 5L

    /**
     * @return a directly streamable URL that has been proven to serve bytes,
     *   or throws with a reason worth showing.
     *
     * Results are held briefly — see [recent]. Resolving is the slow part of
     * starting a track, and ExoPlayer asks again for every re-open: each seek
     * outside the buffer, and each range the cache fills in.
     */
    suspend fun resolve(videoId: String): String {
        init

        recent[videoId]
            ?.takeIf { SystemClock.elapsedRealtime() - it.at < URL_TTL_MS }
            ?.let { return it.url }

        val stream = coalescedResolve(videoId)

        // The container carries no bitrate field, so this is the only place the
        // real figure is ever known.
        NerdStats.onStreamPicked(videoId, stream.kbps)
        remember(videoId, stream.url)
        return stream.url
    }

    /**
     * One walk per videoId at a time.
     *
     * [AudioCache]'s read-ahead resolves the queued track before it is
     * reached, to warm the cache; if the queue advances faster than that
     * walk finishes, playback calls [resolve] for the same track a second
     * time before the first walk has populated [recent]. Left alone, that is
     * two full client walks in flight for the same track at once, each
     * paying for the other's requests — round trips measured elsewhere in
     * this file at ~250ms stretched past 3s under exactly this contention.
     * A second caller for a videoId already being resolved waits on the
     * first walk instead of starting its own.
     *
     * Parented to [resolverScope] rather than the caller's own coroutine, so
     * that a caller giving up on its own timeout — see
     * [PlaybackService][com.music.bitchord.playback.PlaybackService] —
     * cancels only its own wait, not the walk a second caller may still be
     * waiting on.
     */
    private suspend fun coalescedResolve(videoId: String): Stream {
        // computeIfAbsent, not getOrPut: getOrPut's get-then-put isn't atomic
        // on a ConcurrentHashMap, and two racing callers each starting their
        // own async before either one's put() lands is the exact race this
        // exists to close.
        val deferred = inFlight.computeIfAbsent(videoId) { resolverScope.async { resolveUncached(videoId) } }
        return try {
            deferred.await()
        } finally {
            inFlight.remove(videoId, deferred)
        }
    }

    private val inFlight = ConcurrentHashMap<String, Deferred<Stream>>()

    private val resolverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private suspend fun resolveUncached(videoId: String): Stream {
        val resolveStart = SystemClock.elapsedRealtime()
        val stream = try {
            timed("$videoId playerStream") { playerStream(videoId, ::pickForPlayback) }
                ?: timed("$videoId authenticatedWebRemixStream") { authenticatedWebRemixStream(videoId, ::pickForPlayback) }
                ?: run {
                    TrackLog.w(TAG, "every player client failed for $videoId; falling back to extraction")
                    timed("$videoId newPipeStream") { newPipeStream(videoId, ::pickForQuality) }
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The one path out of here that said nothing at all. A resolve that
            // throws is handed to ExoPlayer as a load error, which retries it on
            // a backoff of its own — so the symptom is a track that sits in
            // BUFFERING and walks the clients again every thirty seconds, with
            // no line anywhere naming what actually went wrong. The stack trace
            // is the point: the failure is usually several frames inside
            // NewPipe, where the message alone ("null", commonly) identifies
            // nothing.
            TrackLog.w(
                TAG,
                "resolve failed for $videoId after ${SystemClock.elapsedRealtime() - resolveStart}ms: " +
                    "${e.javaClass.name}: ${e.message}",
                e,
            )
            throw e
        }
        TrackLog.d(TAG, "TIMING $videoId total resolve: ${SystemClock.elapsedRealtime() - resolveStart}ms")
        return stream
    }

    /** Logs how long [block] took, whatever it returns — a timing probe, not a control flow change. */
    private suspend inline fun <T> timed(label: String, block: suspend () -> T): T {
        val start = SystemClock.elapsedRealtime()
        return block().also { TrackLog.d(TAG, "TIMING $label: ${SystemClock.elapsedRealtime() - start}ms") }
    }

    /**
     * Tried after [playerStream] and before extraction, and only when there is
     * a session to send: [PlayerClient.WEB_REMIX] carrying the signed-in
     * listener's own cookie.
     *
     * The anonymous walk in [playerStream] is refused on sight far more often
     * than not right now — every device client answering "sign in to confirm
     * you're not a bot" to a request that, honestly, isn't signed in. A real
     * session cookie on a browser-shaped client is the one case that isn't an
     * anonymous device pretending otherwise, which is why it is asked at all,
     * rather than left at the reputation an earlier cookie-less attempt earned
     * it — the one that got it dropped from [CLIENTS] entirely.
     *
     * It is asked *after* that walk rather than ahead of it because of what it
     * costs when it doesn't work. WEB_REMIX is a web client, so its formats
     * come back ciphered without exception, so this is the one path that has to
     * solve a signature on every single track — and a signature that cannot be
     * solved is not a cheap no. Ahead of the walk that made every track pay for
     * the most expensive failure available before anything cheaper was tried;
     * behind it, the clients that answer in one round trip get their say first
     * and this is reached only on tracks that were already failing. See
     * [jsPlayerMutex] for what the expensive failure actually was.
     *
     * Anything short of a working URL — no cookie, a refusal, a format that
     * won't unlock, a probe that fails — falls through to null rather than
     * throwing, so a bad guess here never costs more than the one round trip.
     */
    private suspend fun authenticatedWebRemixStream(
        videoId: String,
        select: (JsonObject) -> Audio?,
    ): Stream? {
        if (Innertube.cookie == null) return null
        return try {
            timed("$videoId WEB_REMIX ensureVisitorData") { Innertube.ensureVisitorData() }
            val timestamp = timed("$videoId WEB_REMIX getSignatureTimestamp") {
                jsPlayerManager { YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId) }
            }
            val response = timed("$videoId WEB_REMIX player()") {
                Innertube.player(videoId, PlayerClient.WEB_REMIX, timestamp, authenticated = true)
            }
            val format = select(response) ?: return null
            val url = timed("$videoId WEB_REMIX streamUrl") {
                streamUrl(videoId, format)?.let { patchClientVersion(it, PlayerClient.WEB_REMIX.clientVersion) }
            } ?: return null
            if (timed("$videoId WEB_REMIX probe") { probe(url) } != Probe.OK) return null
            TrackLog.d(TAG, "resolved $videoId via authenticated WEB_REMIX @ ${format.kbps}kbps")
            Stream(url, format.kbps, format.mimeType)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TrackLog.d(TAG, "authenticated WEB_REMIX failed for $videoId: ${e.message}")
            null
        }
    }

    /**
     * A resolved stream: the URL, and what the format behind it turned out to
     * be. Playback only ever needs the URL; a download needs the rest of it to
     * name the file and declare its type.
     */
    class Stream(val url: String, val kbps: Int, val mimeType: String) {

        val isOpus: Boolean get() = "opus" in mimeType.lowercase()

        /**
         * The container these bytes are actually in, which is not always what
         * names them.
         *
         * Nothing here transcodes or remuxes — what googlevideo sends is what
         * lands on disk — so the extension has to describe the bytes rather
         * than the codec inside them. YouTube's Opus is Opus-in-WebM, and the
         * two sources disagree about how to say so: the player endpoint calls
         * it `audio/webm; codecs="opus"` and NewPipe calls it `audio/opus`.
         * Taking the latter at face value would write a WebM file named
         * `.opus`, and an `.opus` file is expected to be Ogg — which is how a
         * perfectly good download ends up refusing to open in half the players
         * on the device.
         */
        val downloadExtension: String
            get() = when {
                isOpus || "webm" in mimeType -> "webm"
                "mp4" in mimeType || "m4a" in mimeType -> "m4a"
                else -> "webm"
            }

        /** What the media store should be told this file is. */
        val downloadMimeType: String
            get() = if (downloadExtension == "m4a") "audio/mp4" else "audio/webm"
    }

    /**
     * As [resolve], but for a file being kept rather than a stream being heard:
     * the best Opus on offer, whatever the quality ceiling says.
     *
     * Two passes, and the second one almost never runs. Opus is demanded across
     * *every* client before any of them is allowed to answer with AAC, because
     * a per-client "Opus or else the next client" walk would settle for the
     * first client that had only AAC while a later one held Opus all along.
     * Player responses are shared between the passes, so the second costs the
     * probes again but not the round trips.
     *
     * Nothing here touches [recent]. That cache exists to keep ExoPlayer's
     * re-opens off the network, and its entries are picked under the quality
     * ceiling — seeding it with an unbudgeted Opus URL would quietly hand a
     * capped connection the stream it was capped to avoid, and reading from it
     * would hand a download whatever bitrate playback happened to settle for.
     *
     * The whole thing is attempted twice, for the case where a client is turned
     * away with "Sign in to confirm you're not a bot": [playerStream] mints a
     * fresh visitor id and retries that one client, but `mintedFreshVisitor` is
     * scoped to a single walk, so a bot check late in the list burns the retry
     * and the new id benefits only the *next* resolve. The second walk is what
     * turns that into one download that works. It is worth knowing what it
     * cannot do: a client whose URL failed to probe was stood down by that
     * failure and is skipped on the way round again, so the second attempt is
     * the same walk minus its refusals, not a clean one. Bot checks it can fix;
     * refusals it cannot.
     *
     * Which is why extraction sits behind both. When every client is refusing
     * — the observed state, with the VR clients bot-checked and iOS minting
     * Opus URLs that 403 — [resolve] still gets audio, because it falls through
     * to NewPipe and re-derives the URL itself. A download reaching the same
     * wall has to do the same thing or it fails while the track it is refusing
     * to save is audibly playing.
     */
    suspend fun resolveForDownload(videoId: String): Stream {
        init

        // Whether any client offered Opus at all, as distinct from whether one
        // could be turned into a working URL. Those are different failures and
        // only one of them is a reason to accept a worse format: a track that
        // genuinely has no Opus is a fact about the track, while Opus that
        // won't probe is a bad afternoon on Google's side, and quietly saving
        // AAC because of the latter would hand back a permanently worse file
        // for a temporary reason.
        var offered = false

        repeat(DOWNLOAD_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(DOWNLOAD_RETRY_MS)
            // Fresh each time. Responses are only cached once a client has
            // answered, and re-deriving a URL from a cached response produces
            // the same URL that just failed to probe — so carrying the map
            // across attempts would make every attempt after the first a
            // no-op.
            val responses = mutableMapOf<PlayerClient, JsonObject>()
            playerStream(videoId, { response -> pickOpus(response)?.also { offered = true } }, responses)
                ?.let { return it }
        }

        // Not "try again later" — every client being refused at once is a state
        // that lasts hours, and it is precisely the state [resolve] extracts its
        // way out of. Asking for Opus specifically, because this is still a
        // download: the failsafe is a different route to the bytes, not a
        // licence to take a worse format.
        TrackLog.w(TAG, "no client minted a usable Opus URL for $videoId; extracting")
        runCatching {
            newPipeStream(videoId) { candidates ->
                candidates.filter { it.second.isOpus }
                    .maxByOrNull { it.first }?.second
                    ?.also { offered = true }
            }
        }.onSuccess { return it }
            .onFailure { TrackLog.w(TAG, "extraction found no Opus for $videoId: ${it.message}") }

        if (offered) {
            error("Opus wasn't available for this track just now — try again")
        }

        TrackLog.w(TAG, "nothing offered Opus for $videoId; taking the best available")
        return playerStream(videoId, ::pickBest)
            ?: newPipeStream(videoId) { candidates ->
                // Reached only when no client answered at all, so this is
                // re-deriving the formats from scratch rather than picking
                // over the ones already rejected — Opus is still worth asking
                // for here, and still worth doing without.
                val opus = candidates.filter { it.second.isOpus }
                opus.ifEmpty { candidates }.maxByOrNull { it.first }?.second
            }
    }

    /**
     * Walks [CLIENTS] until one produces a URL that actually serves audio.
     *
     * Every step is allowed to fail without taking the attempt with it: a
     * client can be refused the track, hand back formats none of which [select]
     * accepts or none of which can be unciphered, or mint a URL that turns out
     * to be dead. Only running out of clients is a failure.
     *
     * [responses] memoises the player response per client for the caller that
     * walks twice — see [resolveForDownload]. A client that is asked again
     * inside one walk is a bug, not a cost, so the default is a fresh map.
     *
     * @return the validated stream, or null to fall through to [newPipeStream].
     */
    private suspend fun playerStream(
        videoId: String,
        select: (JsonObject) -> Audio?,
        responses: MutableMap<PlayerClient, JsonObject> = mutableMapOf(),
    ): Stream? {
        // Before anything asks. Without one, the good clients refuse outright
        // and the rest hand back URLs that only *look* like they work — see
        // [Innertube.ensureVisitorData].
        timed("$videoId ensureVisitorData") { Innertube.ensureVisitorData() }

        var timestamp: Int? = null
        var mintedFreshVisitor = false

        for (client in clientOrder()) {
            if (isStoodDown(videoId, client)) continue
            val clientStart = SystemClock.elapsedRealtime()
            try {
                // Only fetched once, and only if a client that needs it is
                // reached — it costs a download of YouTube's player JavaScript.
                if (client.needsSignatureTimestamp && timestamp == null) {
                    timestamp = timed("$videoId getSignatureTimestamp") {
                        runCatching { jsPlayerManager { YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId) } }
                            .onFailure { TrackLog.w(TAG, "no signature timestamp: ${it.message}") }
                            .getOrNull()
                    } ?: continue
                }

                val response = responses[client] ?: try {
                    timed("$videoId ${client.clientName} player()") { Innertube.player(videoId, client, timestamp) }
                } catch (e: Innertube.UnplayableException) {
                    // A visitor id can be burned while the session around it is
                    // fine, and the only symptom is being called a bot. Worth
                    // one fresh id and one more try, once per resolve.
                    if (!e.looksLikeBotCheck || mintedFreshVisitor) throw e
                    mintedFreshVisitor = true
                    TrackLog.d(TAG, "bot check from ${client.clientName}; minting a fresh visitor id")
                    timed("$videoId ensureVisitorData(refresh)") { Innertube.ensureVisitorData(refresh = true) }
                    timed("$videoId ${client.clientName} player() retry") { Innertube.player(videoId, client, timestamp) }
                }
                responses[client] = response

                val format = select(response) ?: continue
                val url = timed("$videoId ${client.clientName} streamUrl") {
                    streamUrl(videoId, format)?.let { patchClientVersion(it, client.clientVersion) }
                } ?: continue

                val verdict = timed("$videoId ${client.clientName} probe") { probe(url) }
                TrackLog.d(TAG, "TIMING $videoId ${client.clientName} total: ${SystemClock.elapsedRealtime() - clientStart}ms")
                when (verdict) {
                    Probe.OK -> {
                        TrackLog.d(TAG, "resolved $videoId via ${client.clientName} @ ${format.kbps}kbps")
                        preferred = client
                        return Stream(url, format.kbps, format.mimeType)
                    }
                    // The client itself is being refused this track; don't
                    // spend another round trip on it for a while.
                    Probe.REFUSED -> standDown(videoId, client)
                    Probe.UNREACHABLE -> Unit
                }
                // Which format was rejected, not just that one was: the same
                // client can mint a good URL for one itag and a dead one for
                // another, so without the format this line cannot tell a track
                // being refused from a codec being refused.
                TrackLog.w(
                    TAG,
                    "${client.clientName} minted an unusable URL for $videoId: " +
                        "$verdict for ${format.mimeType} @ ${format.kbps}kbps",
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A client turned away with "Please sign in" or "confirm you're
                // not a bot" is not being told something about this track. It is
                // being told something about this app's session, and the answer
                // will be the same for the next track and the one after that —
                // which is exactly what the logs show: the same five clients
                // refusing, every track, for a whole session, while the walk
                // asks all of them again each time.
                //
                // That is worth more than the two seconds it spends. Each pass
                // is roughly ten requests, several of them minting a fresh
                // visitor id, and churning identities at that rate is itself
                // the behaviour Google throttles — measured here as every
                // request in a resolve going four to twenty times slower for a
                // stretch, which is the difference between a track starting in
                // three seconds and in twenty. Standing a refused client down
                // for everything, not just for one video, cuts both the wait
                // and the volume that provokes the throttling.
                if (e is Innertube.UnplayableException && e.looksLikeBotCheck) {
                    standDownEverywhere(client)
                }
                TrackLog.w(TAG, "${client.clientName} failed for $videoId: ${e.message}")
            }
        }
        return null
    }

    /**
     * [CLIENTS], led by whichever one last worked.
     *
     * Google's decisions apply to the whole app for as long as they last, not
     * to one track, so the client that served the previous song is overwhelmingly
     * likely to serve this one — and starting there is what keeps the common
     * case at a single round trip.
     */
    private fun clientOrder(): List<PlayerClient> {
        val first = preferred ?: return CLIENTS
        return listOf(first) + CLIENTS.filterNot { it == first }
    }

    @Volatile
    private var preferred: PlayerClient? = null

    // ---- Format selection ---------------------------------------------------

    /** One audio entry of a player response, before its URL has been unlocked. */
    private class Audio(
        val url: String?,
        val signatureCipher: String?,
        val kbps: Int,
        val mimeType: String,
    ) {
        /**
         * YouTube's Opus is always carried in WebM — there is no Opus-in-MP4
         * on this endpoint — so the codec is what the mime type names after
         * the container, and matching on the word is enough to tell it from
         * the AAC ladder sitting beside it.
         */
        val isOpus: Boolean get() = "opus" in mimeType.lowercase()
    }

    private fun audioFormats(response: JsonObject): List<Audio> =
        response["streamingData"]?.jsonObject
            ?.get("adaptiveFormats")?.jsonArray
            ?.map { it.jsonObject }
            ?.filter { it.str("mimeType")?.startsWith("audio/") == true }
            ?.map {
                Audio(
                    url = it.str("url"),
                    signatureCipher = it.str("signatureCipher") ?: it.str("cipher"),
                    kbps = ((it.str("bitrate")?.toLongOrNull() ?: 0L) / 1000).toInt(),
                    mimeType = it.str("mimeType").orEmpty(),
                )
            }
            ?.filter { it.url != null || it.signatureCipher != null }
            .orEmpty()

    /** What playback wants: the best format the connection's ceiling allows. */
    private fun pickForPlayback(response: JsonObject): Audio? =
        pickForQuality(audioFormats(response).map { it.kbps to it })

    /**
     * What a download wants: the best Opus there is, and nothing else.
     *
     * No ceiling is applied. The quality setting exists to budget a *stream* —
     * bytes spent on a track being listened to once, over and over — and a file
     * saved to the device is the opposite case: paid for once, kept, and played
     * from disk forever after. Capping it at the setting that happens to be in
     * force would bake a temporary decision about mobile data into a permanent
     * artefact.
     */
    private fun pickOpus(response: JsonObject): Audio? =
        audioFormats(response).filter { it.isOpus }.maxByOrNull { it.kbps }

    /** The fallback for the rare track that no client offers Opus for. */
    private fun pickBest(response: JsonObject): Audio? =
        audioFormats(response).maxByOrNull { it.kbps }

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.content

    /**
     * Highest stream at or under the ceiling set for the connection in use; if
     * everything is above it (e.g. Low on a track that only has 130kbps+), take
     * the cheapest available rather than failing.
     */
    private fun <T> pickForQuality(candidates: List<Pair<Int, T>>): T? {
        if (candidates.isEmpty()) return null
        val ceiling = AppSettings.effectiveAudioQuality.maxKbps
        val withinBudget = candidates.filter { it.first <= ceiling }
        return (withinBudget.maxByOrNull { it.first } ?: candidates.minByOrNull { it.first })
            ?.second
    }

    // ---- Unlocking ----------------------------------------------------------

    /** The playable URL behind a format, or null if it can't be unlocked. */
    private suspend fun streamUrl(videoId: String, format: Audio): String? {
        val direct = format.url
        if (direct != null) return deobfuscate(videoId, direct)

        val cipher = format.signatureCipher ?: return null
        val params = cipher.split("&")
            .mapNotNull { part ->
                val i = part.indexOf('=').takeIf { it > 0 } ?: return@mapNotNull null
                URLDecoder.decode(part.substring(0, i), "UTF-8") to
                    URLDecoder.decode(part.substring(i + 1), "UTF-8")
            }
            .toMap()

        val base = params["url"] ?: return null
        val signature = params["s"] ?: return null
        // Which query parameter the solved signature belongs in; YouTube has
        // changed the name before, so it travels alongside rather than assumed.
        val into = params["sp"] ?: "signature"
        val solved = runCatching {
            jsPlayerManager { YoutubeJavaScriptPlayerManager.deobfuscateSignature(videoId, signature) }
        }.getOrElse {
            TrackLog.w(TAG, "signature cipher failed: ${it.message}")
            return null
        }
        val separator = if ("?" in base) "&" else "?"
        return deobfuscate(videoId, "$base$separator$into=$solved")
    }

    /**
     * Transform the `n` parameter when present. If deobfuscation itself fails
     * we still return the original URL — a throttled stream beats no stream,
     * and [probe] gets the final say on whether it plays at all.
     */
    private suspend fun deobfuscate(videoId: String, url: String): String {
        val needsWork = url.toHttpUrlOrNull()?.queryParameter("n")?.isNotBlank() == true
        if (!needsWork) return url
        return runCatching {
            jsPlayerManager { YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(videoId, url) }
        }.getOrElse {
            TrackLog.w(TAG, "n-param deobfuscation failed: ${it.message}")
            url
        }
    }

    /**
     * Guards every call into [YoutubeJavaScriptPlayerManager].
     *
     * Its player-JS cache — the parsed code, the deobfuscation function, and
     * even a *failed* parse's exception — lives in static fields with no
     * synchronization, shared by the whole process. This app resolves more
     * than one track at once by design (a track playing while its successor
     * pre-caches — see [AudioCache][com.music.bitchord.playback.AudioCache]),
     * so two resolves can enter these calls together; the library was never
     * written for that, and serializing access is what keeps concurrent
     * resolves from corrupting that shared state.
     *
     * A failure here is passed straight out, and deliberately so. An earlier
     * version answered one by calling [clearAllCaches] and running the block
     * again, on the reasoning that the library caches a *failed* parse's
     * exception and replays it forever, so one bad parse would otherwise be
     * permanent for the process. Measured, that cure was far worse than the
     * disease, for two reasons that only show up on a device:
     *
     *  - The failure this app actually sees — "Could not parse deobfuscation
     *    function" against a `base.js` this NewPipe release doesn't understand
     *    — is deterministic. Re-fetching the script and parsing it again cannot
     *    end differently, so the retry only ever bought a second failure, at
     *    5s to 55s a time, holding this mutex while every concurrent resolve
     *    queued behind it.
     *  - [clearAllCaches] is all-or-nothing. It throws away the parsed player
     *    JS that the *working* paths depend on — the `n` parameter transform
     *    that [newPipeStream] runs for every format it extracts — along with
     *    the one broken function. So the price of the retry was charged twice:
     *    once here, and again on the fallback that was about to succeed, which
     *    went from 2.7s warm to 49.8s against the cache this had just emptied.
     *
     * Left alone, the cached exception makes the failure free — it is what
     * turns a broken signature solve into the ~0ms no it should always have
     * been, and what lets the walk move on to something that works. The cost
     * of not clearing is that a parse which failed for a reason that has since
     * passed stays failed until the process restarts; that is a real loss, and
     * a much smaller one than a fifty-second track.
     */
    private val jsPlayerMutex = Mutex()

    private suspend fun <T> jsPlayerManager(block: () -> T): T = jsPlayerMutex.withLock { block() }

    /**
     * Align the URL's `cver` with the client that actually asked.
     *
     * The player response fills it in from the request, but a signature or `n`
     * transform can be solved against player JavaScript of a different vintage,
     * and googlevideo answers a version it doesn't expect with a 403.
     */
    private fun patchClientVersion(url: String, clientVersion: String): String =
        if ("cver=" in url) url.replace(Regex("cver=[^&]+"), "cver=$clientVersion") else url

    // ---- Validation ---------------------------------------------------------

    private enum class Probe {
        /** Served media bytes; safe to play and to cache. */
        OK,

        /** Answered, but refused this request — the client is the problem. */
        REFUSED,

        /** Never got an answer worth interpreting; blame nothing in particular. */
        UNREACHABLE,
    }

    /**
     * Read the end of a URL before trusting it.
     *
     * This is the whole difference between a track that fails and a track that
     * fails *visibly and instantly*. A URL that 403s is indistinguishable from
     * a good one until something reads from it; hand it to ExoPlayer and the
     * failure surfaces as a track that spins and never starts.
     *
     * The range has to be as large as the real fetch will ask for, not a token
     * one. A URL minted for a session Google has reservations about serves
     * small ranges to anybody — enough to pass a small probe — and then refuses
     * the multi-megabyte ranges actual listening is made of with a 403.
     * [PROBE_RANGE_BYTES] matches the chunk size the player and read-ahead
     * fetch with, so a grudging URL fails here instead of on the playback path.
     * Sixteen kilobytes of the answer still have to actually arrive, so a
     * response that stalls after its headers is a failure too.
     *
     * The headers are the ones the media fetch will really use — see
     * [PlayerClient.forStreamUrl] — so this tests the request that matters
     * rather than a more favourable version of it.
     */
    private fun probe(url: String): Probe {
        val builder = okhttp3.Request.Builder().url(url)
            .header("Range", "bytes=0-${PROBE_RANGE_BYTES - 1}")
        PlayerClient.forStreamUrl(url).mediaHeaders().forEach { (name, value) ->
            builder.header(name, value)
        }
        return try {
            prober.newCall(builder.build()).execute().use { response ->
                when {
                    response.code in REFUSAL_CODES -> Probe.REFUSED
                    response.code !in 200..299 && response.code != 416 -> Probe.UNREACHABLE
                    // A refusal dressed as a success: an error page, or the
                    // consent/captcha interstitial, rather than audio.
                    response.header("Content-Type")?.startsWith("audio/") != true -> Probe.REFUSED
                    // Headers can arrive long before a body that never does —
                    // exactly the shaping this whole path exists to sidestep.
                    // Insisting on the bytes is the point: a trickle that
                    // yields its first byte and stalls is a failure too.
                    response.body?.source()?.request(PROBE_READ_BYTES) != true -> Probe.UNREACHABLE
                    else -> Probe.OK
                }
            }
        } catch (e: Exception) {
            TrackLog.w(TAG, "probe failed: ${e.message}")
            Probe.UNREACHABLE
        }
    }

    private val REFUSAL_CODES = setOf(403, 404, 410)

    /**
     * The probe's own client: the app's, but on a short leash.
     *
     * [Http.client]'s 30-second read timeout is right for a stream being
     * consumed as it arrives and far too patient for a yes/no question —
     * waiting it out is indistinguishable from the stall being tested for.
     * Built from the shared client, so the connection pool and DNS are the
     * same ones the real fetch will use.
     */
    private val prober by lazy {
        Http.client.newBuilder()
            .callTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    private const val PROBE_TIMEOUT_SECONDS = 6L

    /**
     * How much the probe asks for, in one range.
     *
     * Has to match what the real fetch asks for ([ChunkedDataSource] and
     * [AudioCache] both fetch two-megabyte ranges), or a URL that grudges real
     * listening-sized requests — while still serving token ones — sails through
     * the probe and dies on the playback path instead.
     */
    private const val PROBE_RANGE_BYTES = 2L * 1024 * 1024

    /** How much of the answer must actually arrive, to catch a stalled body. */
    private const val PROBE_READ_BYTES = 16L * 1024

    // ---- Clients stood down -------------------------------------------------

    /**
     * Clients refused a given track, and until when.
     *
     * A refusal is rarely about the track alone — it usually means Google has
     * stopped answering that identity — but it is recorded per track because
     * that is the granularity it can be observed at. Keyed the same way it is
     * looked up, so a stale entry costs one retry rather than a lasting hole.
     */
    private val standDownUntil = ConcurrentHashMap<String, Long>()

    private const val STAND_DOWN_MS = 10 * 60 * 1000L

    private fun key(videoId: String, client: PlayerClient) =
        "$videoId|${client.clientName}@${client.clientVersion}"

    private fun standDown(videoId: String, client: PlayerClient) {
        standDownUntil[key(videoId, client)] = SystemClock.elapsedRealtime() + STAND_DOWN_MS
    }

    private fun isStoodDown(videoId: String, client: PlayerClient): Boolean =
        isStoodDown(key(videoId, client)) || isStoodDown(key(client))

    private fun isStoodDown(k: String): Boolean {
        val until = standDownUntil[k] ?: return false
        if (until > SystemClock.elapsedRealtime()) return true
        standDownUntil.remove(k)
        return false
    }

    /**
     * A client refused the session rather than the track — see [playerStream].
     *
     * Shares [standDownUntil] and its expiry with the per-track case, under a
     * key naming no video. The expiry is the whole reason this is safe to do
     * app-wide: Google's decisions here last hours but not forever, so one
     * track every [STAND_DOWN_MS] pays for a full walk and finds out whether
     * the client is being served again, while the rest go straight to what
     * works.
     */
    private fun standDownEverywhere(client: PlayerClient) {
        val k = key(client)
        if (!isStoodDown(k)) {
            TrackLog.d(TAG, "${client.clientName} is refusing this session; standing it down app-wide")
        }
        standDownUntil[k] = SystemClock.elapsedRealtime() + STAND_DOWN_MS
        // The walk starts from whichever client last worked; a client that is
        // now being skipped everywhere must not be that one.
        if (preferred == client) preferred = null
    }

    private fun key(client: PlayerClient) = "*|${client.clientName}@${client.clientVersion}"

    /**
     * A URL that [probe] cleared has been refused while actually playing.
     *
     * Everything above assumes a URL that served bytes once will keep serving
     * them, and mostly that holds. When it doesn't, nothing here would ever
     * find out: [probe] runs before playback and not again, so a client that
     * goes bad mid-session stays [preferred], and [recent] keeps handing back
     * the same dead URL for the rest of its TTL. Every following track then
     * fails the same way, and the app can only be talked out of it by being
     * restarted — which is the one symptom users actually report.
     *
     * So the refusal is fed back: forget the URL, stand the client down for
     * that track, and give up the preference so the next resolve starts from
     * the top of [CLIENTS] rather than from the client that just failed.
     *
     * Called from the playback path — see
     * [ChunkedDataSource][com.music.bitchord.playback.ChunkedDataSource].
     */
    fun onPlaybackRefused(url: String, responseCode: Int) {
        if (responseCode !in REFUSAL_CODES) return
        val client = PlayerClient.forStreamUrl(url)
        // Keyed by videoId, and the fetch only knows the googlevideo URL it was
        // handed; the map is a latency cache of a few dozen entries, so finding
        // the way back costs nothing worth measuring.
        recent.entries.firstOrNull { it.value.url == url }?.key?.let { videoId ->
            recent.remove(videoId)
            standDown(videoId, client)
        }
        // Independent of that lookup on purpose: standing down the preference
        // is what breaks the loop, and it must still happen if the URL has
        // already aged out of the cache.
        if (preferred == client) {
            TrackLog.w(TAG, "${client.clientName} refused a URL it had already served; standing it down")
            preferred = null
        }
    }

    // ---- Failsafe -----------------------------------------------------------

    /**
     * NewPipe's full extractor, kept for the case where every player client has
     * been turned away — it re-derives everything itself and is updated
     * upstream when YouTube changes, so it works when nothing else does.
     *
     * Last rather than first because of what it costs: it scrapes the watch
     * page, which is the request Google shapes hardest, and a shaped response
     * can hold this call open for the better part of a minute. Worth waiting
     * out when the alternative is silence; not worth paying for every track.
     *
     * Driven through [StreamExtractor][org.schabi.newpipe.extractor.stream.StreamExtractor]
     * directly rather than through `StreamInfo.getInfo`, which is the obvious
     * call and the expensive one. `getInfo` assembles everything a video page
     * has — it drives forty-odd extractor methods, among them `getVideoStreams`
     * and `getVideoOnlyStreams`, and YouTube answers those with twenty to
     * thirty formats whose `n` parameter each has to be transformed by running
     * YouTube's player JavaScript. This app then discards every one of them and
     * keeps the audio. Fetching the page and asking only for [audioStreams]
     * pays that transform four or five times instead: measured on-device at
     * 49.8s against 2.3s for the same track over the same connection.
     */
    private suspend fun newPipeStream(
        videoId: String,
        select: (List<Pair<Int, AudioStream>>) -> AudioStream?,
    ): Stream {
        var failure: Exception? = null
        repeat(EXTRACTION_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(EXTRACTION_RETRY_MS * attempt)
            try {
                return extractStream(videoId, select)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Worth another go rather than worth giving up on: the common
                // failure here is a shaped or cut-off watch page, which is a
                // fact about this minute rather than about the track, and this
                // is the last thing standing between the listener and silence.
                TrackLog.w(
                    TAG,
                    "extraction attempt ${attempt + 1} of $EXTRACTION_ATTEMPTS failed for $videoId: ${e.message}",
                )
                failure = e
            }
        }
        throw failure ?: IllegalStateException("Track unavailable: no audio streams")
    }

    /** How many times the watch page is worth asking for before giving up. */
    private const val EXTRACTION_ATTEMPTS = 3

    /** Multiplied by the attempt number, so the wait grows if the first retry doesn't take. */
    private const val EXTRACTION_RETRY_MS = 1000L

    /**
     * One extraction at a time, across the whole app.
     *
     * Extraction is not the network-bound step it looks like. Fetching the
     * watch page is the small part; the expensive part is running YouTube's
     * player JavaScript through Rhino to transform each format's `n`
     * parameter, which is CPU work on a phone against static state NewPipe
     * shares process-wide. Run concurrently it does not share out, it
     * collapses — measured on-device at 1.8s alone, 16.2s with two in flight
     * (both finishing within 35ms of each other, which is the tell), and 30.3s
     * with three. Read-ahead is what puts three or four in flight: the track
     * being waited on plus [AudioCache][com.music.bitchord.playback.AudioCache]'s
     * queue warm-up, every one of them an extraction now that no player client
     * is being served.
     *
     * So they are queued rather than raced. Serialised, three cost about two
     * seconds each in turn instead of thirty seconds each at once, and the one
     * a listener is actually waiting on is behind at most one other — see
     * `QUEUE_LOOKAHEAD`, kept at one for exactly this reason. The bound on how
     * long a single holder can keep the gate is [EXTRACTOR_TIMEOUT_SECONDS].
     */
    private val extractionGate = Mutex()

    private suspend fun extractStream(
        videoId: String,
        select: (List<Pair<Int, AudioStream>>) -> AudioStream?,
    ): Stream = extractionGate.withLock {
        withContext(Dispatchers.IO) {
            val waited = SystemClock.elapsedRealtime()
            val extractor = ServiceList.YouTube.getStreamExtractor(
                "https://www.youtube.com/watch?v=$videoId",
            )
            extractor.fetchPage()
            val candidates = extractor.audioStreams
                // Progressive only — DASH/HLS entries carry a manifest, not a URL.
                .filter {
                    !it.content.isNullOrBlank() &&
                        it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP
                }
            val stream = select(candidates.map { it.averageBitrate to it })
                ?: error("Track unavailable: no audio streams")
            TrackLog.d(
                TAG,
                "NewPipe picked ${stream.format?.name} @ ${stream.averageBitrate}kbps " +
                    "(extraction held the gate ${SystemClock.elapsedRealtime() - waited}ms)",
            )
            // stream.content is already playable, not raw: YoutubeStreamExtractor
            // resolves the signature cipher and the `n` parameter itself while
            // building audioStreams, through the same YoutubeJavaScriptPlayerManager
            // this file also calls directly. Running it through deobfuscate() again
            // was a second, redundant trip through that same machinery on every
            // fallback — real latency (and a second chance to hit whatever's
            // currently failing it) spent solving something already solved.
            Stream(
                url = stream.content,
                kbps = stream.averageBitrate,
                mimeType = stream.mime,
            )
        }
    }

    /**
     * The container, which is all NewPipe's mime type reports.
     *
     * `MediaFormat.WEBMA_OPUS` — what YouTube's Opus arrives as — carries the
     * mime type `audio/webm`, identical to the Vorbis-in-WebM entry beside it.
     * Accurate about the bytes, and useless for telling the codec apart, which
     * is what [isOpus] is for.
     */
    private val AudioStream.mime: String get() = format?.mimeType.orEmpty()

    /**
     * Whether these bytes are Opus, asked of the format rather than its name.
     *
     * The tempting version of this is a substring match for "opus" on [mime],
     * and it silently never matches: the format that means Opus says
     * `audio/webm`. A download demanding Opus then concludes the track hasn't
     * any and fails — while playback, which asks only for the best bitrate,
     * takes the same stream and plays it as Opus.
     */
    private val AudioStream.isOpus: Boolean
        get() = format == MediaFormat.WEBMA_OPUS || format == MediaFormat.OPUS

    // ---- Cache --------------------------------------------------------------

    /**
     * How many bytes the whole track is, or null if the URL doesn't say.
     *
     * Every progressive googlevideo URL carries the figure as `clen`. It is
     * worth reading from there because the alternative is an HTTP request that
     * reaches the end of the resource: a bounded range never reveals the total,
     * so read-ahead would have no way to know when it was finished. Resolving
     * is memoised, so asking costs nothing beyond the first time.
     */
    suspend fun contentLength(videoId: String): Long? =
        resolve(videoId).toHttpUrlOrNull()?.queryParameter("clen")?.toLongOrNull()

    private class Resolved(val url: String, val at: Long)

    /**
     * Stream URLs already resolved, by videoId — and, since [resolve] only ever
     * stores one that has served bytes, already known good rather than merely
     * recent.
     *
     * Google issues them with several hours of validity, so the ceiling here is
     * chosen for a different reason: a URL is tied to the playback session that
     * minted it, and holding one indefinitely means a stale entry survives long
     * enough to fail a play. Twenty minutes covers a track and the seeking
     * around it while staying well inside the window where the URL is good.
     */
    private val recent = ConcurrentHashMap<String, Resolved>()

    private const val URL_TTL_MS = 20 * 60 * 1000L

    /** Enough for the queue in hand; this is a latency cache, not a store. */
    private const val MAX_REMEMBERED = 32

    /** See [resolveForDownload]: one walk to burn a stale visitor id, one to use its replacement. */
    private const val DOWNLOAD_ATTEMPTS = 2

    /** Long enough for a freshly minted visitor id to be worth anything, short enough not to be felt. */
    private const val DOWNLOAD_RETRY_MS = 500L

    private fun remember(videoId: String, url: String) {
        if (recent.size >= MAX_REMEMBERED) {
            val cutoff = SystemClock.elapsedRealtime() - URL_TTL_MS
            recent.entries.removeAll { it.value.at < cutoff }
            if (recent.size >= MAX_REMEMBERED) recent.clear()
        }
        recent[videoId] = Resolved(url, SystemClock.elapsedRealtime())
    }
}
