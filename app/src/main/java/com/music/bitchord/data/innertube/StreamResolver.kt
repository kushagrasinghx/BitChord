package com.music.bitchord.data.innertube

import android.os.SystemClock
import android.util.Log
import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.Http
import com.music.bitchord.data.NerdStats
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
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
import org.schabi.newpipe.extractor.exceptions.AccountTerminatedException
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SoundCloudGoPlusContentException
import org.schabi.newpipe.extractor.exceptions.UnsupportedContentInCountryException
import org.schabi.newpipe.extractor.exceptions.YoutubeMusicPremiumContentException
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import java.io.IOException
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.Locale

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

        // A verdict, not a failure: asking again cannot change the answer, so
        // every caller after the first is told so without a request being sent.
        // See [rememberUnplayable] for why this is the fix for "stuck loading".
        unplayableReason(videoId)?.let { throw PermanentlyUnplayableException(it) }

        val stream = coalescedResolve(videoId)

        // The container carries no bitrate field, so this is the only place the
        // real figure is ever known.
        NerdStats.onStreamPicked(videoId, stream.kbps)
        remember(videoId, stream.url)
        return stream.url
    }

    /**
     * A track this app cannot play, for a reason that will read the same in ten
     * seconds — an age gate no session gets past, a takedown, a region block.
     *
     * Its own type because everything above the resolver has to be able to tell
     * it apart from a failure worth retrying, and the layers in between are
     * ExoPlayer's: a load error carries whatever exception it was given and
     * nothing else, so the distinction has to travel in the type. See
     * [PlaybackService][com.music.bitchord.playback.PlaybackService]'s load-error
     * policy and `recoverFrom`.
     */
    class PermanentlyUnplayableException(reason: String) : IOException(reason)

    /**
     * Tracks that have already failed for a reason retrying cannot fix, and
     * until when.
     *
     * This is the single change that turns the observed failure — a track that
     * sits in BUFFERING for minutes on end, hammering youtubei — back into a
     * failure that happens once. Nothing above this object retries *less* than
     * three deep: ExoPlayer's own load-error policy retries the source, this
     * service's `recoverFrom` retries the player, and read-ahead resolves the
     * same track again on its own schedule. Against a permanent refusal every
     * one of those is a full client walk plus a triple extraction — measured in
     * the report at roughly twenty-seven walks and fifty youtubei requests in a
     * 2m41s window, for a track whose answer was settled by the first one.
     *
     * Entries expire rather than being permanent, because the reasons behind
     * them do: an age gate stops mattering the moment the listener signs in
     * (see [forgetUnplayable], called from the login flow), and Google's region
     * and bot verdicts are measured in hours, not sessions. Ten minutes is the
     * same budget [STAND_DOWN_MS] uses, for the same reason — long enough that
     * the storm cannot re-form, short enough that a listener who fixes the
     * cause does not have to restart the app.
     */
    private val unplayable = ConcurrentHashMap<String, Verdict>()

    private class Verdict(val reason: String, val at: Long)

    private fun unplayableReason(videoId: String): String? {
        val entry = unplayable[videoId] ?: return null
        if (SystemClock.elapsedRealtime() - entry.at < UNPLAYABLE_TTL_MS) return entry.reason
        unplayable.remove(videoId)
        return null
    }

    private fun rememberUnplayable(videoId: String, reason: String) {
        if (unplayable.size > MAX_REMEMBERED) unplayable.clear()
        unplayable[videoId] = Verdict(reason, SystemClock.elapsedRealtime())
    }

    /**
     * Forget every verdict recorded above.
     *
     * Signing in is the one event that can turn an age-gated track playable,
     * and signing out the one that can turn it back — so both have to clear
     * this, or the listener who signs in specifically to play a track is told
     * for the next ten minutes that it still cannot be played. The stand-downs
     * go with it: a client refused while anonymous is owed a fresh hearing now
     * that there is a session to send.
     */
    fun onSessionChanged() {
        unplayable.clear()
        standDownUntil.clear()
        refusalsByClient.clear()
        preferred = null
    }

    private const val UNPLAYABLE_TTL_MS = 10 * 60 * 1000L

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
     * waiting on. Being parented elsewhere is also why the walk has to be told
     * whose it is — [TrackLog.about] — rather than inheriting it: this is the
     * single largest producer of lines in the log, and every one of them was
     * previously filed against whatever happened to be playing while the walk
     * ran, which for read-ahead is the track before this one.
     */    private suspend fun coalescedResolve(videoId: String): Stream {
        inFlight[videoId]?.let { return it.await() }
        // Started lazily so that losing the race below costs nothing: the walk
        // that gets discarded has not run a line, so cancelling it fires no
        // requests and leaves no half-finished deferred behind.
        val walk = resolverScope.async(TrackLog.about(videoId), start = CoroutineStart.LAZY) {
            resolveUncached(videoId)
        }
        val running = inFlight.putIfAbsent(videoId, walk)
        if (running != null) {
            walk.cancel()
            return running.await()
        }
        // Unregistered by the walk's own completion rather than by the awaiter,
        // which is the difference between coalescing and only appearing to.
        //
        // This used to be `try { deferred.await() } finally { inFlight.remove }`,
        // and the finally is the bug: the walk is parented to [resolverScope]
        // precisely so a caller giving up does not kill it, so a caller that
        // gives up — which is every read-ahead resolve the queue moves past, and
        // every [PlaybackService] resolve that hits its own timeout — took the
        // still-running walk out of the map on its way out. The next caller then
        // found nothing in flight and started a second full walk against the
        // same videoId, which is exactly what the logs show: two overlapping
        // resolves for one track, 4472ms and 4620ms, 2.8s of them concurrent,
        // each paying for the other's round trips. The doc comment above
        // promised one walk per videoId and the finally guaranteed the opposite.
        walk.invokeOnCompletion { inFlight.remove(videoId, walk) }
        walk.start()
        return walk.await()
    }

    private val inFlight = ConcurrentHashMap<String, Deferred<Stream>>()

    private val resolverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private suspend fun resolveUncached(videoId: String): Stream {
        val resolveStart = SystemClock.elapsedRealtime()
        val stream = try {
            timed("$videoId playerStream") { playerStream(videoId, ::rankForPlayback) }
                ?: timed("$videoId authenticatedWebRemixStream") { authenticatedWebRemixStream(videoId, ::rankForPlayback) }
                ?: run {
                    TrackLog.w(TAG, "every player client failed for $videoId; falling back to extraction")
                    timed("$videoId newPipeStream") { newPipeStream(videoId, ::pickForQuality) }
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: LinkageError) {
            // Every strategy above either runs third-party extraction code or
            // drives YouTube's player JavaScript, so any of them can turn out to
            // have been compiled against an API this OS version does not carry.
            // That arrives as an Error, which the clause below does not catch and
            // no caller of this function catches either — ExoPlayer's loader
            // thread least of all, which is where it surfaced as a process kill
            // rather than a failed track. Converted here, at the one point every
            // strategy passes through, so the answer is the same wherever the
            // linkage failure came from.
            TrackLog.w(
                TAG,
                "resolve hit a linkage failure for $videoId after " +
                    "${SystemClock.elapsedRealtime() - resolveStart}ms: ${e.javaClass.name}: ${e.message}",
                e,
            )
            throw IOException("Stream resolution cannot run on this device: $e", e)
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
            // Recorded before it is rethrown, so the retries stacked above this
            // — ExoPlayer's, the service's, read-ahead's — are answered from
            // memory instead of each one walking seven clients and extracting
            // three times against a refusal that is never going to soften.
            permanentReason(e)?.let { reason ->
                rememberUnplayable(videoId, reason)
                TrackLog.w(TAG, "$videoId is not playable: $reason; not asking again for 10 minutes")
                throw PermanentlyUnplayableException(reason)
            }
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
        select: (JsonObject) -> List<Audio>,
    ): Stream? {
        if (Innertube.cookie == null) return null
        // Every format this client returns is ciphered, so with the solver
        // broken there is nothing here but a round trip and a log line. The
        // signed-in device clients in [playerStream] are the route that works.
        if (signatureSolverBroken) return null
        return try {
            timed("$videoId WEB_REMIX ensureVisitorData") { Innertube.ensureVisitorData() }
            val timestamp = timed("$videoId WEB_REMIX getSignatureTimestamp") {
                signatureTimestamp(videoId)
            }
            val response = timed("$videoId WEB_REMIX player()") {
                Innertube.player(videoId, PlayerClient.WEB_REMIX, timestamp, authenticated = true)
            }
            val candidates = select(response)
            if (candidates.isEmpty()) return null
            var format: Audio? = null
            var url: String? = null
            timed("$videoId WEB_REMIX streamUrl") {
                for (candidate in candidates) {
                    val unlocked = streamUrl(videoId, candidate)
                        ?.let { patchClientVersion(it, PlayerClient.WEB_REMIX.clientVersion) }
                    if (unlocked != null) {
                        format = candidate
                        url = unlocked
                        break
                    }
                }
            }
            val picked = format ?: return null
            val playable = url ?: return null
            if (timed("$videoId WEB_REMIX probe") { probe(playable) } != Probe.OK) return null
            TrackLog.d(TAG, "resolved $videoId via authenticated WEB_REMIX @ ${picked.kbps}kbps")
            Stream(playable, picked.kbps, picked.mimeType)
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
         *
         * Downloads no longer reach the WebM branch — [resolveForDownload]
         * takes MP4 or nothing — but playback still hands Opus around, and a
         * file an older build already wrote is still a `.webm` this app has to
         * be able to describe.
         */
        val downloadExtension: String
            get() = when {
                "mp4" in mimeType || "m4a" in mimeType -> "m4a"
                else -> "webm"
            }

        /** What the media store should be told this file is. */
        val downloadMimeType: String
            get() = if (downloadExtension == "m4a") "audio/mp4" else "audio/webm"
    }

    /**
     * As [resolve], but for a file being kept rather than a stream being heard:
     * the best Opus the *download* setting allows, and no connection has a say.
     *
     * The format is not a preference here, it is the only option. Every
     * adaptive audio format YouTube offers is either AAC in MP4 or Opus (or
     * Vorbis) in WebM, and Android's media store will not mint a row in the
     * audio collection for `audio/webm` — measured on-device as
     * `IllegalArgumentException: Unsupported MIME type audio/webm` out of
     * `ContentResolver.insert`, thrown before a single byte had been fetched.
     * So every download this app offered failed, and Opus being the better
     * codec of the two never got to matter.
     *
     * Which is why there is no "best available" fallback below any more. A
     * walk that ends by taking whatever the last client offered ends by
     * taking WebM, and a WebM the store refuses is not a worse download, it
     * is no download — so running out of AAC is a failure with a sentence
     * attached rather than something to work around. It is not a common one:
     * the AAC ladder is on essentially every track, rather more reliably than
     * Opus was.
     *
     * MP4 is still demanded across *every* client before any of them is
     * allowed to give up, because a per-client walk cannot tell a client that
     * has no MP4 from a client that has been refused the track. Player
     * responses are shared between the passes, so the second costs the probes
     * again but not the round trips.
     *
     * Nothing here touches [recent]. That cache exists to keep ExoPlayer's
     * re-opens off the network, and its entries are picked under the *playback*
     * ceiling — seeding it from here would hand a capped connection a stream it
     * was capped to avoid, and reading from it would hand a download whatever
     * bitrate playback happened to settle for. Both directions are wrong, and
     * they are wrong independently of what [maxKbps] says.
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
     * URLs that 403 — [resolve] still gets audio, because it falls through to
     * NewPipe and re-derives the URL itself. A download reaching the same wall
     * has to do the same thing or it fails while the track it is refusing to
     * save is audibly playing.
     *
     * @param maxKbps the ceiling from
     *   [DownloadQuality][com.music.bitchord.data.settings.DownloadQuality].
     *   Passed in rather than read here so that one download resolves at one
     *   bitrate: a setting changed mid-fetch, or a re-resolve after a refusal
     *   (see [Downloader.fetch][com.music.bitchord.download.Downloader.fetch]),
     *   must not splice two different renditions into one file.
     */
    suspend fun resolveForDownload(videoId: String, maxKbps: Int): Stream {
        val stream = downloadStream(videoId, maxKbps)
        // YouTube fallback is Opus-in-WebM; configured sources may still have
        // supplied AAC-in-MP4. Both are taggable and storable audio containers.
        check(stream.downloadExtension in setOf("m4a", "webm")) {
            "Can't save ${stream.mimeType} — try again"
        }
        return stream
    }

    private suspend fun downloadStream(videoId: String, maxKbps: Int): Stream =
        withContext(TrackLog.about(videoId)) {
            init

            // Whether any client offered AAC at all, as distinct from whether one
            // could be turned into a working URL. Those are different failures and
            // only one of them is worth telling someone to try again about: a track
            // no client has an MP4 for will not have one in five minutes either,
            // while an MP4 that won't probe is a bad afternoon on Google's side.
            var offered = false

            repeat(DOWNLOAD_ATTEMPTS) { attempt ->
                if (attempt > 0) delay(DOWNLOAD_RETRY_MS)
                // Fresh each time. Responses are only cached once a client has
                // answered, and re-deriving a URL from a cached response produces
                // the same URL that just failed to probe — so carrying the map
                // across attempts would make every attempt after the first a
                // no-op.
                val responses = mutableMapOf<PlayerClient, JsonObject>()
                playerStream(
                    videoId,
                    { response -> pickOpus(response, maxKbps).also { if (it.isNotEmpty()) offered = true } },
                    responses,
                )?.let { return@withContext it }
            }

            // Not "try again later" — every client being refused at once is a state
            // that lasts hours, and it is precisely the state [resolve] extracts its
            // way out of. Still asking for Opus, because this is still a download:
            // the failsafe is a different route to the bytes, not a licence to
            // fetch a container that cannot then be saved.
            TrackLog.w(TAG, "no client minted a usable Opus URL for $videoId; extracting")
            runCatching {
                newPipeStream(videoId) { candidates ->
                    // Capped the same way [pickOpus] is, off the same setting, or
                    // the failsafe would quietly hand back a rendition the user
                    // said they didn't want to keep.
                    underCeiling(candidates.filter { it.second.isWebmOpus }, maxKbps)
                        ?.also { offered = true }
                }
            }.onSuccess { return@withContext it }
                .onFailure { TrackLog.w(TAG, "extraction found no Opus for $videoId: ${it.message}") }

            if (offered) error("Couldn't reach a downloadable copy just now — try again")
            error("No downloadable audio for this track")
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
        select: (JsonObject) -> List<Audio>,
        responses: MutableMap<PlayerClient, JsonObject> = mutableMapOf(),
    ): Stream? {
        // Before anything asks. Without one, the good clients refuse outright
        // and the rest hand back URLs that only *look* like they work — see
        // [Innertube.ensureVisitorData].
        timed("$videoId ensureVisitorData") { Innertube.ensureVisitorData() }

        var timestamp: Int? = null
        var mintedFreshVisitor = false
        // One signed-in retry per client per walk. Without the bound, a client
        // that answers the age gate with the same age gate signed in would be
        // asked twice for every walk, and there are seven of them.
        val triedSignedIn = mutableSetOf<PlayerClient>()

        for (client in clientOrder()) {
            if (isStoodDown(videoId, client)) continue
            val clientStart = SystemClock.elapsedRealtime()
            try {
                // Only fetched once, and only if a client that needs it is
                // reached — it costs a download of YouTube's player JavaScript.
                if (client.needsSignatureTimestamp && timestamp == null) {
                    timestamp = timed("$videoId getSignatureTimestamp") {
                        signatureTimestamp(videoId)
                    } ?: continue
                }

                val response = responses[client] ?: try {
                    timed("$videoId ${client.clientName} player()") { Innertube.player(videoId, client, timestamp) }
                } catch (e: Innertube.UnplayableException) {
                    when {
                        // The fix for the reported bug, and the only one that
                        // makes an age-restricted track actually play.
                        //
                        // The refusal here is "Sign in to confirm your age" (or,
                        // from the iOS and Android clients, "This video may be
                        // inappropriate for some users"), and it is a statement
                        // about the *request*, not the track: the same client
                        // asked again carrying the listener's session is
                        // answered OK. Worth doing on these clients in
                        // particular because they return plain `url` fields — so
                        // this route never touches YouTube's player JavaScript,
                        // which is the thing that is currently broken. Before
                        // this, the only path with a hope of an age-gated track
                        // was [authenticatedWebRemixStream], whose formats are
                        // ciphered without exception, so a track YouTube was
                        // perfectly willing to serve failed on the signature
                        // solve instead. See [onSignatureSolverBroken].
                        e.isAgeGate && Innertube.cookie != null && !triedSignedIn.contains(client) -> {
                            triedSignedIn.add(client)
                            TrackLog.d(
                                TAG,
                                "${client.clientName} wants an age check for $videoId; asking again signed in",
                            )
                            timed("$videoId ${client.clientName} player() signed in") {
                                Innertube.player(videoId, client, timestamp, authenticated = true)
                            }
                        }
                        // A visitor id can be burned while the session around it
                        // is fine, and the only symptom is being called a bot.
                        // Worth one fresh id and one more try, once per resolve.
                        e.looksLikeBotCheck && !mintedFreshVisitor -> {
                            mintedFreshVisitor = true
                            TrackLog.d(TAG, "bot check from ${client.clientName}; minting a fresh visitor id")
                            timed("$videoId ensureVisitorData(refresh)") { Innertube.ensureVisitorData(refresh = true) }
                            timed("$videoId ${client.clientName} player() retry") {
                                Innertube.player(videoId, client, timestamp)
                            }
                        }
                        else -> throw e
                    }
                }
                responses[client] = response

                // Answered, but with nothing this app can use. Logged because
                // the two ways that happens are worth telling apart and the
                // timings alone cannot: a client that offers no acceptable
                // format never reaches [streamUrl], so both cases look
                // identical from outside — a `player()` line and then silence.
                val candidates = select(response)
                if (candidates.isEmpty()) {
                    TrackLog.d(TAG, "${client.clientName} offered no usable format for $videoId")
                    refused(videoId, client)
                    continue
                }
                // Down the ladder rather than one shot at the top of it.
                //
                // [select] used to return a single format, and a format that
                // would not unlock ended the client's turn — which conflates two
                // different things: a client being refused the track, and the
                // one format that happened to win on bitrate being the one whose
                // URL could not be unlocked. A response is routinely a mix, some
                // entries with a plain `url` and some ciphered, and ranking by
                // bitrate alone is blind to which is which — so a broken
                // signature solver threw away whole clients that were offering a
                // perfectly serviceable unciphered rung one step down.
                var format: Audio? = null
                var url: String? = null
                timed("$videoId ${client.clientName} streamUrl") {
                    for (candidate in candidates) {
                        val unlocked = streamUrl(videoId, candidate)
                            ?.let { patchClientVersion(it, client.clientVersion) }
                        if (unlocked != null) {
                            format = candidate
                            url = unlocked
                            break
                        }
                    }
                }
                val picked = format
                val playable = url
                if (picked == null || playable == null) {
                    TrackLog.d(
                        TAG,
                        "${client.clientName} offered ${candidates.size} format(s) for $videoId but none " +
                            "could be unlocked (${candidates.joinToString { "${it.mimeType} @ ${it.kbps}kbps" }})",
                    )
                    refused(videoId, client)
                    continue
                }

                val verdict = timed("$videoId ${client.clientName} probe") { probe(playable) }
                TrackLog.d(TAG, "TIMING $videoId ${client.clientName} total: ${SystemClock.elapsedRealtime() - clientStart}ms")
                when (verdict) {
                    Probe.OK -> {
                        TrackLog.d(TAG, "resolved $videoId via ${client.clientName} @ ${picked.kbps}kbps")
                        served(client)
                        preferred = client
                        return Stream(playable, picked.kbps, picked.mimeType)
                    }
                    // The client itself is being refused this track; don't
                    // spend another round trip on it for a while.
                    Probe.REFUSED -> {
                        standDown(videoId, client)
                        refused(videoId, client)
                    }
                    // Nobody answered, so this says nothing about the client —
                    // deliberately not counted as a refusal, or a bad minute on
                    // the connection would stand down clients that are fine.
                    Probe.UNREACHABLE -> Unit
                }
                // Which format was rejected, not just that one was: the same
                // client can mint a good URL for one itag and a dead one for
                // another, so without the format this line cannot tell a track
                // being refused from a codec being refused.
                TrackLog.w(
                    TAG,
                    "${client.clientName} minted an unusable URL for $videoId: " +
                        "$verdict for ${picked.mimeType} @ ${picked.kbps}kbps",
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
                //
                // A phrase match is the right test only when it matches, and it
                // is one sentence of Google's wording away from not. On this
                // emulator TVHTML5 is turned away with "The page needs to be
                // reloaded." every track — the same refusal, worded so as to
                // contain none of "bot", "unusual traffic" or "sign in" — and so
                // was asked again for every track of the session. Any refusal
                // therefore also counts toward [refused], which needs no
                // vocabulary because it waits for the repetition instead.
                if (e is Innertube.UnplayableException) {
                    if (e.looksLikeBotCheck) standDownEverywhere(client) else refused(videoId, client)
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
         * YouTube's Opus is always carried in WebM and its AAC always in MP4 —
         * there is no Opus-in-MP4 on this endpoint — so the container the mime
         * type names is enough to tell the two ladders apart, and the container
         * is the thing a download actually cares about.
         */
        val isAac: Boolean get() = "mp4" in mimeType.lowercase(Locale.ROOT)
        val isOpus: Boolean get() = "opus" in mimeType.lowercase(Locale.ROOT)
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

    /**
     * What playback wants, best first: the formats the connection's ceiling
     * allows, in the order they are worth trying.
     *
     * A list rather than a single pick because unlocking can fail per format —
     * see [playerStream] and [streamUrl].
     */
    private fun rankForPlayback(response: JsonObject): List<Audio> =
        rankByQuality(audioFormats(response), AppSettings.effectiveAudioQuality.maxKbps)

    /**
     * [candidates] in the order they are worth attempting: the highest at or
     * under [maxKbps] first and the rest of the ladder descending from it, then
     * anything above the ceiling ascending — because a rung over budget still
     * beats no audio at all, and the cheapest such rung is the least wrong.
     *
     * Unciphered formats are preferred within a bitrate tie, and moved ahead of
     * ciphered ones outright once the signature solver has been found broken:
     * a ciphered format is then not merely more expensive, it is unplayable, and
     * ordering it first would spend the client's turn on a certainty. See
     * [onSignatureSolverBroken].
     */
    private fun rankByQuality(candidates: List<Audio>, maxKbps: Int): List<Audio> {
        val order = compareByDescending<Audio> { it.url != null }
        val (withinBudget, overBudget) = candidates.partition { it.kbps <= maxKbps }
        val ranked = withinBudget.sortedWith(compareByDescending<Audio> { it.kbps }.then(order)) +
            overBudget.sortedWith(compareBy<Audio> { it.kbps }.then(order))
        return if (signatureSolverBroken) ranked.sortedWith(order) else ranked
    }

    /**
     * What a download wants: the best AAC at or under the download setting's
     * own ceiling.
     *
     * MP4 rather than the better codec because it is the only container the
     * media store will accept for the audio collection — see
     * [resolveForDownload].
     *
     * The ceiling comes in as an argument rather than being read here, and it is
     * a different setting from the one [rankForPlayback] reads. The quality
     * ceilings budget a *stream* — bytes spent again on every replay of a track
     * being listened to — and a file saved to the device is the opposite case:
     * paid for once, kept, played from disk forever after. Capping a permanent
     * artefact at whichever network happened to be in hand would bake a
     * temporary decision into it, so a download is capped by a decision made
     * about downloads, or not at all.
     */
    private fun pickOpus(response: JsonObject, maxKbps: Int): List<Audio> =
        rankByQuality(audioFormats(response).filter { it.isOpus }, maxKbps)

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.content

    /**
     * Highest stream at or under the ceiling set for the connection in use; if
     * everything is above it (e.g. Low on a track that only has 130kbps+), take
     * the cheapest available rather than failing.
     */
    private fun <T> pickForQuality(candidates: List<Pair<Int, T>>): T? =
        underCeiling(candidates, AppSettings.effectiveAudioQuality.maxKbps)

    /**
     * Highest of [candidates] at or under [maxKbps]; if everything is above it
     * — Standard on a track whose AAC ladder starts at 256, say — the cheapest
     * available, because a rung over budget still beats no audio at all.
     */
    private fun <T> underCeiling(candidates: List<Pair<Int, T>>, maxKbps: Int): T? {
        if (candidates.isEmpty()) return null
        val withinBudget = candidates.filter { it.first <= maxKbps }
        return (withinBudget.maxByOrNull { it.first } ?: candidates.minByOrNull { it.first })
            ?.second
    }

    // ---- Unlocking ----------------------------------------------------------

    /** The playable URL behind a format, or null if it can't be unlocked. */
    private suspend fun streamUrl(videoId: String, format: Audio): String? {
        val direct = format.url
        if (direct != null) return deobfuscate(videoId, direct)

        val cipher = format.signatureCipher ?: return null
        // Asked before the library is, because the library's answer is a cached
        // exception and its cost is a log line per format per walk rather than
        // any real work. See [signatureSolverBroken].
        if (signatureSolverBroken) return null
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
            if (it.isUnparseablePlayer()) onSignatureSolverBroken(it)
            return null
        }
        val separator = if ("?" in base) "&" else "?"
        return deobfuscate(videoId, "$base$separator$into=$solved")
    }

    /**
     * Whether NewPipe cannot read the current `base.js` at all, as opposed to
     * having failed to solve one particular signature.
     *
     * The wording is the only thing that distinguishes them, which is
     * unsatisfying and still worth acting on: "Could not parse deobfuscation
     * function" is thrown before this app's input is looked at, so it says
     * nothing about the track and everything about the pair of (this extractor
     * release, whatever player YouTube is currently serving).
     *
     * Matched narrowly on purpose. A false positive here costs every ciphered
     * format for the rest of the process — the ANDROID client and WEB_REMIX
     * both — so a broad "could not parse" would trade one track's failure for a
     * session's, which is the wrong way round. Every player-JS parse failure
     * NewPipe raises names the function or the script it could not read.
     */
    private fun Throwable.isUnparseablePlayer(): Boolean {
        val text = generateSequence(this) { it.cause?.takeIf { c -> c !== it } }
            .mapNotNull { it.message }
            .joinToString(" ")
            .lowercase(Locale.ROOT)
        return "deobfuscation function" in text ||
            "player js" in text ||
            "player javascript" in text ||
            "javascript base url" in text
    }

    /**
     * NewPipe cannot solve signatures against the player YouTube is currently
     * serving, so nothing that depends on solving one is worth attempting again
     * this process.
     *
     * This is the actual root cause of the report, and the reason it looks like
     * an age-restriction bug when it isn't. Ordinary tracks never reach a
     * signature at all: the device clients at the top of [CLIENTS] hand back
     * plain `url` fields, so a broken solver is invisible on everything that
     * plays. An age-restricted track is the one case where every unciphered
     * client is refused and the *only* remaining route —
     * [authenticatedWebRemixStream] — is ciphered without exception, so the
     * broken solver is fatal exactly there and nowhere else. Hence "other songs
     * played fine", and hence clearing caches and restarting not helping: the
     * parse failure is deterministic, and NewPipe caches even the failed parse's
     * exception (see [jsPlayerMutex]).
     *
     * Recorded as a flag rather than acted on, because there is nothing to do
     * about it in this file beyond not paying for it repeatedly. What makes the
     * track play regardless is [playerStream]'s authenticated retry, which gets
     * an *unciphered* URL out of a device client and never touches this path.
     * Bumping NewPipeExtractor is not the fix: no release through v0.26.5
     * addresses this parse failure, and the last upstream fix in this area
     * shipped in 2025.
     */
    private fun onSignatureSolverBroken(cause: Throwable) {
        if (signatureSolverBroken) return
        signatureSolverBroken = true
        TrackLog.w(
            TAG,
            "this NewPipe release cannot read YouTube's current player JavaScript " +
                "(${cause.message}); ciphered formats are unavailable for the rest of this session — " +
                "signed-in device clients are the only route to an age-restricted track",
        )
    }

    @Volatile
    private var signatureSolverBroken = false

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
     * YouTube's current player revision, as the number a client quotes to prove
     * it is running that player.
     *
     * Named and shared rather than fetched at each of the three call sites,
     * because there is now a fourth kind of caller that is nothing to do with
     * streaming: [PlaybackTracker] needs one to be issued a tracking block at
     * all, and it has no business knowing that the value comes from parsing
     * YouTube's player JavaScript.
     *
     * Memoised for the process. It is a property of YouTube's deployment, not of
     * a track — the videoId is passed only because NewPipe's API takes one, to
     * decide which player script to fetch — and it changes on the order of days,
     * against a first parse that costs seconds.
     */
    suspend fun signatureTimestamp(videoId: String): Int? {
        cachedSignatureTimestamp?.let { return it }
        return runCatching {
            jsPlayerManager { YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId) }
        }
            .onFailure { TrackLog.w(TAG, "no signature timestamp: ${it.message}") }
            .getOrNull()
            ?.also { cachedSignatureTimestamp = it }
    }

    @Volatile
    private var cachedSignatureTimestamp: Int? = null

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
     * Which tracks each client has been refused since it last served one.
     *
     * [standDownEverywhere] is the right answer for a client that has stopped
     * being served, and reading the refusal is the hard part: Google says no in
     * whatever words it likes, and only some of them are recognisable. So this
     * does not try to read it. A client asked for one track and refused has
     * told us about that track; a client asked for three different tracks and
     * refused all three, without serving anything in between, has told us about
     * itself — whatever the wording. Videos rather than a count because one
     * track can put the same client through this twice (see [resolveForDownload],
     * which walks for AAC and then for anything), and two refusals of the same
     * track are one piece of evidence.
     */
    private val refusalsByClient = ConcurrentHashMap<String, MutableSet<String>>()

    /**
     * How much evidence is enough. Low, because the cost of being wrong is
     * bounded by [STAND_DOWN_MS] and the cost of being slow is paid on every
     * track: three tracks of the walk, then the rest of the ten minutes going
     * straight to what works.
     */
    private const val REFUSALS_BEFORE_STANDING_DOWN = 3

    /** A client answered about [videoId], and the answer was no use. */
    private fun refused(videoId: String, client: PlayerClient) {
        val refusedTracks = refusalsByClient.computeIfAbsent(key(client)) {
            ConcurrentHashMap.newKeySet<String>()
        }
        refusedTracks.add(videoId)
        if (refusedTracks.size >= REFUSALS_BEFORE_STANDING_DOWN) {
            // Cleared as it escalates, so that when the stand-down expires the
            // client is owed a fresh [REFUSALS_BEFORE_STANDING_DOWN] tracks
            // rather than being stood down again by the first one.
            refusalsByClient.remove(key(client))
            standDownEverywhere(client)
        }
    }

    /**
     * A client served a track, so what came before it was about those tracks
     * rather than about the client.
     */
    private fun served(client: PlayerClient) {
        refusalsByClient.remove(key(client))
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
        // Only googlevideo's URLs say anything about a [PlayerClient]. Anything
        // else — a module's stream URL, a downloaded file — carries no `c`
        // parameter, and [PlayerClient.forStreamUrl] answers IOS for a URL it
        // can't read rather than nothing. So without this, a Tidal URL
        // answering 404 stands down the client that mints most of YouTube's,
        // and the next YouTube track pays for a failure on a different server.
        if (url.toHttpUrlOrNull()?.host?.endsWith("googlevideo.com") != true) return
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
            } catch (e: LinkageError) {
                // Not a failed extraction — a method or class the extractor was
                // compiled against that this OS version does not have, which is
                // the same answer every time and so is not worth the remaining
                // attempts. It reaches here as an Error rather than an Exception,
                // and an Error let past this point does not fail the track: it
                // unwinds through ExoPlayer's loader thread, where nothing is
                // catching it, and takes the process with it. That is what a
                // NoSuchMethodError out of NewPipe's URL codec did on every
                // Android below 13 — see the note in the vendored
                // `org.schabi.newpipe.extractor.utils.Utils`. Reported as an
                // Exception so the layers above treat it as the load failure it
                // is, with the type name kept because the message alone ("No
                // static method ...") reads like nothing.
                throw IOException("Extractor cannot run on this device: $e", e)
            } catch (e: Exception) {
                // A verdict about the content, not about this minute. There was
                // no classification here at all, so the most expensive retry in
                // the app was spent on the one class of failure that cannot
                // benefit from it: an age-restricted track threw
                // AgeRestrictedContentException, which extends
                // ContentNotAvailableException extends ParsingException extends
                // Exception, landed in this clause, and was granted all three
                // attempts with 1s and 2s backoffs — each one re-fetching the
                // watch page and holding [extractionGate] against every other
                // resolve in the process while it did. Three identical refusals
                // per walk, and the walk itself repeated. Named and rethrown on
                // the first one instead.
                permanentReason(e)?.let { reason ->
                    TrackLog.w(TAG, "extraction will not succeed for $videoId: $reason")
                    throw e
                }
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

    /**
     * Why [e] means "never", or null if it only means "not just now".
     *
     * The distinction is the difference between one failed track and a request
     * storm, and it is not available from the exception hierarchy: NewPipe files
     * a takedown, a region block and a truncated watch page under the same
     * [ParsingException] ancestry, so a `catch (e: Exception)` treats "this
     * video does not exist" exactly like "the page arrived cut in half". Every
     * retry in this app sat downstream of that conflation.
     *
     * Kept as a message rather than a boolean because the message is what the
     * listener eventually sees, and "This video is age-restricted" is a
     * different thing to be told than `ERROR_CODE_IO_UNSPECIFIED`.
     */
    private fun permanentReason(e: Throwable): String? = when (e) {
        is PermanentlyUnplayableException -> e.message ?: "This track cannot be played"
        // The whole reason this function exists — see [newPipeStream].
        is AgeRestrictedContentException ->
            if (Innertube.cookie == null) {
                "This track is age-restricted. Sign in to YouTube to play it."
            } else {
                "YouTube will not serve this age-restricted track to this app."
            }
        is GeographicRestrictionException -> "This track isn't available in your country"
        is UnsupportedContentInCountryException -> "This track isn't available in your country"
        is PaidContentException -> "This track is paid content"
        is YoutubeMusicPremiumContentException -> "This track needs YouTube Music Premium"
        is PrivateContentException -> "This track is private"
        is AccountTerminatedException -> "The channel behind this track was terminated"
        is SoundCloudGoPlusContentException -> "This track needs SoundCloud Go+"
        is Innertube.UnplayableException ->
            when {
                // An age gate is only permanent once the session that could get
                // past it has been tried and refused. Signed out it is a
                // sentence with an action attached, and [resolve] must not cache
                // it in a way that survives the listener taking that action —
                // hence [onSessionChanged].
                e.isAgeGate ->
                    if (Innertube.cookie == null) {
                        "This track is age-restricted. Sign in to YouTube to play it."
                    } else {
                        null
                    }
                e.isPermanent -> e.message
                else -> null
            }
        // ExoPlayer and the coroutine machinery both wrap freely, and the
        // classification has to survive being wrapped or it never fires: the
        // failure that reaches [resolveUncached] arrives as whatever the last
        // layer chose to throw it as.
        else -> e.cause?.takeIf { it !== e }?.let(::permanentReason)
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
     * is what [isM4a] is asked of the format for instead.
     */
    private val AudioStream.mime: String get() = format?.mimeType.orEmpty()

    /**
     * Whether these bytes are AAC in MP4, asked of the format rather than its
     * name.
     *
     * The name would in fact do here — `MediaFormat.M4A` reports `audio/mp4`,
     * the same thing the player endpoint calls it — but the enum is what
     * actually carries the answer, and the sibling case is a standing warning
     * against reading these mime types as codecs: the format that means Opus
     * says `audio/webm`, so a download demanding Opus by name concluded the
     * track hadn't any and fell through, while playback took the very same
     * stream and played it as Opus.
     */
    private val AudioStream.isM4a: Boolean
        get() = format == MediaFormat.M4A

    private val AudioStream.isWebmOpus: Boolean
        get() = format == MediaFormat.WEBMA_OPUS

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
