package com.music.bitchord.data.innertube

import com.music.bitchord.data.MonotonicClock
import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.Http
import com.music.bitchord.data.NerdStats
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Turns a videoId into a URL ExoPlayer can actually stream.
 *
 *  1. **InnerTubeX first.** Its live-benchmarked client catalog, cipher tiers
 *     and PoTokens decide which identity asks and unlock what it answers. See
 *     [InnerTubeXResolver].
 *
 *  2. **NewPipe as the failsafe.** It re-derives everything from the watch
 *     page, which is several hundred kilobytes of HTML Google rate-shapes under
 *     load, so it is kept off the hot path and reached only when InnerTubeX
 *     finds nothing. See [newPipeStream].
 *
 *  3. **Whether the URL is real.** A URL can be dead on arrival for reasons no
 *     amount of care predicts, so nothing is handed to the player, or cached,
 *     until bytes have been fetched from it. See [probe].
 */
object StreamResolver {
    /** The bitrate ceiling in force — the phone's quality setting for the connection it is on. */
    @Volatile
    var maxKbps: () -> Int = { Int.MAX_VALUE }


    private const val TAG = "BitChord"

    /** Past this, an extractor fetch is worth flagging rather than just noting. */
    private const val SLOW_FETCH_MS = 2000L

    /** See [OkHttpDownloader.execute] — the one request the extractor is not allowed to make. */
    private const val NEXT_ENDPOINT = "/youtubei/v1/next"

    /** Well-formed, empty, and over the library's fifty-character floor. */
    private const val EMPTY_NEXT_RESPONSE =
        """{"responseContext":{},"contents":{},"currentVideoEndpoint":{},"trackingParams":""}"""

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
            val requestStart = MonotonicClock.nowMs()
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
                    "extractor fetch FAILED after ${MonotonicClock.nowMs() - requestStart}ms " +
                        "${request.httpMethod()} ${request.url()}: ${e.javaClass.simpleName}: ${e.message}",
                )
                throw e
            }
            val took = MonotonicClock.nowMs() - requestStart
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
            ?.takeIf { MonotonicClock.nowMs() - it.at < URL_TTL_MS }
            ?.let { return it.url }

        // A verdict, not a failure: asking again cannot change the answer, so
        // every caller after the first is told so without a request being sent.
        // See [rememberUnplayable] for why this is the fix for "stuck loading".
        unplayableReason(videoId)?.let { throw PermanentlyUnplayableException(it) }

        val stream = coalescedResolve(videoId)

        // The container carries no bitrate field, so this is the only place the
        // real figure is ever known.
        NerdStats.onStreamPicked(videoId, stream.kbps)
        stream.loudnessDb?.let { loudness[videoId] = it }
        remember(videoId, stream.url)
        return stream.url
    }

    /** Per-track loudness, read once and kept for as long as the process runs. */
    private val loudness = ConcurrentHashMap<String, Double>()

    /**
     * YouTube's own normalization figure for [videoId], or null when it has
     * never resolved or never carried one.
     *
     * Populated by [resolve] the first time a track's stream is asked for —
     * which happens for every YouTube-queued track whether or not another
     * source ends up serving its bytes, since the YouTube walk always runs
     * alongside a substitute lookup rather than only when one fails. So a
     * track substituted to JioSaavn or an addon still carries the figure its
     * YouTube counterpart resolved.
     */
    fun loudnessDbFor(videoId: String): Double? = loudness[videoId]

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
        if (MonotonicClock.nowMs() - entry.at < UNPLAYABLE_TTL_MS) return entry.reason
        unplayable.remove(videoId)
        return null
    }

    private fun rememberUnplayable(videoId: String, reason: String) {
        if (unplayable.size > MAX_REMEMBERED) unplayable.clear()
        unplayable[videoId] = Verdict(reason, MonotonicClock.nowMs())
    }

    /**
     * Forget every verdict recorded above.
     *
     * Signing in is the one event that can turn an age-gated track playable,
     * and signing out the one that can turn it back — so both have to clear
     * this, or the listener who signs in specifically to play a track is told
     * for the next ten minutes that it still cannot be played. InnerTubeX's
     * exclusions go with it: a client refused while anonymous is owed a fresh
     * hearing now that there is a session to send.
     */
    fun onSessionChanged() {
        InnerTubeXResolver.onSessionChanged()
        unplayable.clear()
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
        val resolveStart = MonotonicClock.nowMs()
        val stream = try {
            timed("$videoId InnerTubeX") { innerTubeXStream(videoId) }
                ?: run {
                    TrackLog.w(TAG, "InnerTubeX found no usable stream for $videoId; falling back to extraction")
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
                    "${MonotonicClock.nowMs() - resolveStart}ms: ${e.javaClass.name}: ${e.message}",
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
                "resolve failed for $videoId after ${MonotonicClock.nowMs() - resolveStart}ms: " +
                    "${e.javaClass.name}: ${e.message}",
                e,
            )
            // Recorded before it is rethrown, so the retries stacked above this
            // — ExoPlayer's, the service's, read-ahead's — are answered from
            // memory instead of each one asking InnerTubeX and extracting three
            // times against a refusal that is never going to soften.
            permanentReason(e)?.let { reason ->
                rememberUnplayable(videoId, reason)
                TrackLog.w(TAG, "$videoId is not playable: $reason; not asking again for 10 minutes")
                throw PermanentlyUnplayableException(reason)
            }
            throw e
        }
        TrackLog.d(TAG, "TIMING $videoId total resolve: ${MonotonicClock.nowMs() - resolveStart}ms")
        return stream
    }

    /**
     * A probed stream from [InnerTubeXResolver], or null to fall through to extraction.
     *
     * A client whose URL fails the probe is skipped and InnerTubeX asked again, up
     * to [INNERTUBEX_ATTEMPTS] times, since its catalog has further clients behind it.
     */
    private suspend fun innerTubeXStream(
        videoId: String,
        maxKbps: Int = maxKbps(),
        requireM4a: Boolean = false,
    ): Stream? {
        val skip = mutableSetOf<String>()
        repeat(INNERTUBEX_ATTEMPTS) {
            val found = try {
                InnerTubeXResolver.extract(videoId, maxKbps, skip, requireM4a)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                TrackLog.w(TAG, "InnerTubeX failed for $videoId: ${e.javaClass.simpleName}: ${e.message}")
                return null
            } ?: return null
            val verdict = timed("$videoId InnerTubeX ${found.profileId} probe") { probe(found.url) }
            if (verdict == Probe.OK) {
                TrackLog.d(TAG, "resolved $videoId via InnerTubeX ${found.profileId} @ ${found.kbps}kbps")
                return Stream(found.url, found.kbps, found.mimeType, found.loudnessDb)
            }
            TrackLog.w(TAG, "InnerTubeX ${found.profileId} minted an unusable URL for $videoId: $verdict")
            skip += found.profileId
        }
        return null
    }

    /** The headers a media fetch for [url] must carry to match whoever minted it. */
    fun mediaHeadersFor(url: String): Map<String, String> =
        InnerTubeXResolver.headersFor(url) ?: PlayerClient.forStreamUrl(url).mediaHeaders()

    private const val INNERTUBEX_ATTEMPTS = 3

    /** Logs how long [block] took, whatever it returns — a timing probe, not a control flow change. */
    private suspend inline fun <T> timed(label: String, block: suspend () -> T): T {
        val start = MonotonicClock.nowMs()
        return block().also { TrackLog.d(TAG, "TIMING $label: ${MonotonicClock.nowMs() - start}ms") }
    }

    /**
     * A resolved stream: the URL, and what the format behind it turned out to
     * be. Playback only ever needs the URL; a download needs the rest of it to
     * name the file and declare its type.
     */
    class Stream(val url: String, val kbps: Int, val mimeType: String, val loudnessDb: Double? = null) {

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
         * App-private downloads can keep the WebM rendition, while exported
         * downloads require MP4. Playback also hands Opus around, and a file an
         * older build already wrote is still a `.webm` this app has to be able
         * to describe.
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
     * the best format the *download* setting allows, and no connection has a
     * say. Opus-in-WebM is preferred for app-private downloads; exported files
     * require AAC-in-MP4 because Android's Music collection rejects WebM.
     *
     * Every adaptive audio format YouTube offers is either AAC in MP4 or Opus
     * (or Vorbis) in WebM. Android's media store will not mint an audio row for
     * `audio/webm` — measured on-device as
     * `IllegalArgumentException: Unsupported MIME type audio/webm` out of
     * `ContentResolver.insert`, thrown before a single byte has been fetched.
     * App-private storage has no such restriction, so it keeps Opus; an export
     * asks for MP4 from the start instead of discovering the restriction when
     * it creates the destination.
     *
     * There is no "best available" fallback below. A walk that takes another
     * container can no longer fulfil the destination already chosen: WebM
     * cannot be exported through the Music collection, and silently replacing
     * a requested private Opus download with AAC changes its codec. Running out
     * of the required ladder is therefore a failure with a sentence attached.
     *
     * The container is asked of InnerTubeX up front rather than filtered out
     * of whatever it picks, because it answers with one format per ask: MP4
     * when the destination needs it, its best Opus otherwise. It has no
     * bitrate ceiling of its own, so a rung over [maxKbps] sends the download
     * on to extraction for a lower one, and is kept should extraction fail.
     *
     * Nothing here touches [recent]. That cache exists to keep ExoPlayer's
     * re-opens off the network, and its entries are picked under the *playback*
     * ceiling — seeding it from here would hand a capped connection a stream it
     * was capped to avoid, and reading from it would hand a download whatever
     * bitrate playback happened to settle for. Both directions are wrong, and
     * they are wrong independently of what [maxKbps] says.
     *
     * Extraction sits behind InnerTubeX for the same reason it does in
     * [resolve]: a download reaching a wall playback climbs over has to climb
     * it too, or it fails while the track it is refusing to save is audibly
     * playing.
     *
     * @param maxKbps the ceiling from
     *   [DownloadQuality][com.music.bitchord.data.settings.DownloadQuality].
     *   Passed in rather than read here so that one download resolves at one
     *   bitrate: a setting changed mid-fetch, or a re-resolve after a refusal
     *   (see [Downloader.fetch][com.music.bitchord.download.Downloader.fetch]),
     *   must not splice two different renditions into one file.
     */
    suspend fun resolveForDownload(
        videoId: String,
        maxKbps: Int,
        requireM4a: Boolean = false,
    ): Stream {
        val stream = downloadStream(videoId, maxKbps, requireM4a)
        val allowed = if (requireM4a) setOf("m4a") else setOf("m4a", "webm")
        check(stream.downloadExtension in allowed) {
            "Can't save ${stream.mimeType} — try again"
        }
        return stream
    }

    private suspend fun downloadStream(videoId: String, maxKbps: Int, requireM4a: Boolean): Stream =
        withContext(TrackLog.about(videoId)) {
            init

            // Whether any client offered AAC at all, as distinct from whether one
            // could be turned into a working URL. Those are different failures and
            // only one of them is worth telling someone to try again about: a track
            // no client has an MP4 for will not have one in five minutes either,
            // while an MP4 that won't probe is a bad afternoon on Google's side.
            var offered = false

            // InnerTubeX hands back its best rung of the container asked for, and
            // has no ceiling of its own. Within the setting's ceiling it is the
            // answer; above it (best Opus over Standard's 128kbps) extraction
            // below can pick the lower rung, and this is kept as the fallback.
            val found = innerTubeXStream(videoId, maxKbps, requireM4a)
                ?.takeIf { if (requireM4a) it.downloadExtension == "m4a" else it.downloadExtension == "webm" }
            if (found != null) {
                offered = true
                if (found.kbps <= maxKbps) return@withContext found
                TrackLog.d(TAG, "InnerTubeX's ${found.kbps}kbps is over the ${maxKbps}kbps download ceiling for $videoId")
            }

            // The failsafe changes how the URL is found, not which container the
            // destination can accept.
            val format = if (requireM4a) "MP4" else "Opus"
            if (found == null) TrackLog.w(TAG, "InnerTubeX found no usable $format for $videoId; extracting")
            runCatching {
                newPipeStream(videoId) { candidates ->
                    // Capped the same way as the player-response selection, off
                    // the same setting, or the failsafe would quietly hand back
                    // a rendition the user said they didn't want to keep.
                    val matching = candidates.filter {
                        if (requireM4a) it.second.isM4a else it.second.isWebmOpus
                    }
                    underCeiling(matching, maxKbps)
                        ?.also { offered = true }
                }
            }.onSuccess { return@withContext it }
                .onFailure { TrackLog.w(TAG, "extraction found no $format for $videoId: ${it.message}") }

            // A rung above the ceiling beats no download at all; extraction does the same.
            found?.let { return@withContext it }
            if (offered) error("Couldn't reach a downloadable copy just now — try again")
            error("No downloadable audio for this track")
        }

    // ---- Format selection ---------------------------------------------------

    /**
     * Highest stream at or under the ceiling set for the connection in use; if
     * everything is above it (e.g. Low on a track that only has 130kbps+), take
     * the cheapest available rather than failing.
     */
    private fun <T> pickForQuality(candidates: List<Pair<Int, T>>): T? =
        underCeiling(candidates, maxKbps())

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

    // ---- Player JavaScript ----------------------------------------------------

    /**
     * Guards every call into [YoutubeJavaScriptPlayerManager].
     *
     * Its player-JS cache, including a *failed* parse's exception, lives in
     * static fields with no synchronization, shared by the whole process, and
     * this app resolves more than one track at once by design. A failure is
     * passed straight out rather than answered with `clearAllCaches` and a
     * retry: measured, that retry could only fail again and threw away the
     * parsed player the NewPipe fallback depends on, taking it from 2.7s to
     * 49.8s.
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
     * The range starts past [AUTH_BOUNDARY_BYTES] when the file is that long:
     * some clients' URLs serve the first megabyte and 403 everything after
     * it, so a probe of the opening passes and playback dies ~50s in.
     * Sixteen kilobytes of the answer still have to actually arrive, so a
     * response that stalls after its headers is a failure too.
     *
     * The headers are the ones the media fetch will really use — see
     * [PlayerClient.forStreamUrl] — so this tests the request that matters
     * rather than a more favourable version of it.
     */
    private fun probe(url: String): Probe {
        val length = url.toHttpUrlOrNull()?.queryParameter("clen")?.toLongOrNull()
        val start = if (length != null && length > AUTH_BOUNDARY_BYTES + PROBE_READ_BYTES) AUTH_BOUNDARY_BYTES else 0L
        val end = minOf(start + PlayerClient.rangeBytesFor(url), length ?: Long.MAX_VALUE) - 1
        val builder = okhttp3.Request.Builder().url(url)
            .header("Range", "bytes=$start-$end")
        mediaHeadersFor(url).forEach { (name, value) -> builder.header(name, value) }
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

    /** Where googlevideo stops authorising some clients' URLs (InnerTubeX: "CDN 403 after 1 MiB"). */
    private const val AUTH_BOUNDARY_BYTES = 1024L * 1024

    /** How much of the answer must actually arrive, to catch a stalled body. */
    private const val PROBE_READ_BYTES = 16L * 1024

    /**
     * A URL that [probe] cleared has been refused while actually playing.
     *
     * [probe] runs before playback and not again, so without this [recent]
     * would keep handing back the same dead URL for the rest of its TTL and
     * every replay would fail the same way. So the refusal is fed back: forget
     * the URL, and when InnerTubeX minted it, skip that client for the track.
     *
     * Called from the playback path — see
     * [ChunkedDataSource][com.music.bitchord.playback.ChunkedDataSource].
     */
    fun onPlaybackRefused(url: String, responseCode: Int) {
        if (responseCode !in REFUSAL_CODES) return
        // Only googlevideo's URLs say anything about the client that minted
        // them. A module's stream URL answering 404 is that server's business,
        // and must not bench a YouTube client.
        if (url.toHttpUrlOrNull()?.host?.endsWith("googlevideo.com") != true) return
        val videoId = InnerTubeXResolver.onRefused(url)
            // The map is a latency cache of a few dozen entries, so finding the
            // way back from a URL costs nothing worth measuring.
            ?: recent.entries.firstOrNull { it.value.url == url }?.key
            ?: return
        recent.remove(videoId)
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
            val waited = MonotonicClock.nowMs()
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
                    "(extraction held the gate ${MonotonicClock.nowMs() - waited}ms)",
            )
            // stream.content is already playable, not raw: YoutubeStreamExtractor
            // resolves the signature cipher and the `n` parameter itself while
            // building audioStreams, so it is handed on as-is.
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

    private fun remember(videoId: String, url: String) {
        if (recent.size >= MAX_REMEMBERED) {
            val cutoff = MonotonicClock.nowMs() - URL_TTL_MS
            recent.entries.removeAll { it.value.at < cutoff }
            if (recent.size >= MAX_REMEMBERED) recent.clear()
        }
        recent[videoId] = Resolved(url, MonotonicClock.nowMs())
    }
}
