package com.music.bitchord.data.innertube

import android.os.SystemClock
import android.util.Log
import com.music.bitchord.data.Http
import com.music.bitchord.data.NerdStats
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
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

    /**
     * Player clients in the order they are worth asking, cheapest and most
     * reliable first — an order taken from what the live endpoint actually
     * answers, not from what ought to work.
     *
     * The four at the top return plain `url` fields, so a stream is one POST
     * away with no player JavaScript involved at all. The two below hand back
     * ciphered formats, costing a download of that JavaScript and a signature
     * to solve. See each entry in [PlayerClient].
     *
     * The gating that decides which of these answers is applied per network,
     * not globally — an identity refused on one connection is served on
     * another — which is the whole reason this is a list and why the order is
     * only a starting guess that [clientOrder] corrects from experience.
     */
    private val CLIENTS = listOf(
        PlayerClient.ANDROID_VR,
        PlayerClient.ANDROID_VR_LEGACY,
        PlayerClient.IOS,
        PlayerClient.IOS_RECENT,
        PlayerClient.ANDROID,
        PlayerClient.WEB_REMIX,
    )

    /** NewPipe needs a Downloader; reuse the app's single OkHttp client. */
    private class OkHttpDownloader : Downloader() {
        override fun execute(request: Request): Response {
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
                builder.header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; rv:120.0) Gecko/20100101 Firefox/120.0",
                )
            }

            val response = Http.client.newCall(builder.build()).execute()
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

        val url = playerUrl(videoId)
            ?: run {
                Log.w(TAG, "every player client failed for $videoId; falling back to extraction")
                newPipeUrl(videoId)
            }

        remember(videoId, url)
        return url
    }

    /**
     * Walks [CLIENTS] until one produces a URL that actually serves audio.
     *
     * Every step is allowed to fail without taking the attempt with it: a
     * client can be refused the track, hand back formats none of which can be
     * unciphered, or mint a URL that turns out to be dead. Only running out of
     * clients is a failure.
     *
     * @return the validated URL, or null to fall through to [newPipeUrl].
     */
    private suspend fun playerUrl(videoId: String): String? {
        // Before anything asks. Without one, the good clients refuse outright
        // and the rest hand back URLs that only *look* like they work — see
        // [Innertube.ensureVisitorData].
        Innertube.ensureVisitorData()

        var timestamp: Int? = null
        var mintedFreshVisitor = false

        for (client in clientOrder()) {
            if (isStoodDown(videoId, client)) continue
            try {
                // Only fetched once, and only if a client that needs it is
                // reached — it costs a download of YouTube's player JavaScript.
                if (client.needsSignatureTimestamp && timestamp == null) {
                    timestamp = runCatching { YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId) }
                        .onFailure { Log.w(TAG, "no signature timestamp: ${it.message}") }
                        .getOrNull()
                        ?: continue
                }

                var response = try {
                    Innertube.player(videoId, client, timestamp)
                } catch (e: Innertube.UnplayableException) {
                    // A visitor id can be burned while the session around it is
                    // fine, and the only symptom is being called a bot. Worth
                    // one fresh id and one more try, once per resolve.
                    if (!e.looksLikeBotCheck || mintedFreshVisitor) throw e
                    mintedFreshVisitor = true
                    Log.d(TAG, "bot check from ${client.clientName}; minting a fresh visitor id")
                    Innertube.ensureVisitorData(refresh = true)
                    Innertube.player(videoId, client, timestamp)
                }

                val format = pickFormat(response) ?: continue
                val url = streamUrl(videoId, format)
                    ?.let { patchClientVersion(it, client.clientVersion) }
                    ?: continue

                when (probe(url)) {
                    Probe.OK -> {
                        Log.d(TAG, "resolved $videoId via ${client.clientName} @ ${format.kbps}kbps")
                        preferred = client
                        // The container carries no bitrate field, so this is
                        // the only place the real figure is ever known.
                        NerdStats.onStreamPicked(videoId, format.kbps)
                        return url
                    }
                    // The client itself is being refused this track; don't
                    // spend another round trip on it for a while.
                    Probe.REFUSED -> standDown(videoId, client)
                    Probe.UNREACHABLE -> Unit
                }
                Log.w(TAG, "${client.clientName} minted an unusable URL for $videoId")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "${client.clientName} failed for $videoId: ${e.message}")
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
    )

    private fun pickFormat(response: JsonObject): Audio? {
        val formats = response["streamingData"]?.jsonObject
            ?.get("adaptiveFormats")?.jsonArray
            ?.map { it.jsonObject }
            ?.filter { it.str("mimeType")?.startsWith("audio/") == true }
            ?.map {
                Audio(
                    url = it.str("url"),
                    signatureCipher = it.str("signatureCipher") ?: it.str("cipher"),
                    kbps = ((it.str("bitrate")?.toLongOrNull() ?: 0L) / 1000).toInt(),
                )
            }
            ?.filter { it.url != null || it.signatureCipher != null }
            .orEmpty()

        return pickForQuality(formats.map { it.kbps to it })
    }

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
    private fun streamUrl(videoId: String, format: Audio): String? {
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
            YoutubeJavaScriptPlayerManager.deobfuscateSignature(videoId, signature)
        }.getOrElse {
            Log.w(TAG, "signature cipher failed: ${it.message}")
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
    private fun deobfuscate(videoId: String, url: String): String {
        val needsWork = url.toHttpUrlOrNull()?.queryParameter("n")?.isNotBlank() == true
        if (!needsWork) return url
        return runCatching {
            YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(videoId, url)
        }.getOrElse {
            Log.w(TAG, "n-param deobfuscation failed: ${it.message}")
            url
        }
    }

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
     * Read the opening of a URL before trusting it.
     *
     * This is the whole difference between a track that fails and a track that
     * fails *visibly and instantly*. A URL that 403s is indistinguishable from
     * a good one until something reads from it; hand it to ExoPlayer and the
     * failure surfaces as a track that spins and never starts.
     *
     * A real range, not `bytes=0-0`. A single byte is not a test: a URL minted
     * for a session Google has reservations about will serve one to anybody and
     * then refuse every request large enough to be actual listening, so a
     * one-byte probe passes exactly the URLs this exists to catch. Sixteen
     * kilobytes is past that line and still one cheap round trip.
     *
     * The headers are the ones the media fetch will really use — see
     * [PlayerClient.forStreamUrl] — so this tests the request that matters
     * rather than a more favourable version of it.
     */
    private fun probe(url: String): Probe {
        val builder = okhttp3.Request.Builder().url(url)
            .header("Range", "bytes=0-${PROBE_BYTES - 1}")
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
                    // Insisting on the whole range is the point: a trickle that
                    // yields its first byte and stalls is a failure too.
                    response.body?.source()?.request(PROBE_BYTES) != true -> Probe.UNREACHABLE
                    else -> Probe.OK
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "probe failed: ${e.message}")
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

    /** Past what a grudging URL will serve, well under what any track holds. */
    private const val PROBE_BYTES = 16L * 1024

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

    private fun isStoodDown(videoId: String, client: PlayerClient): Boolean {
        val k = key(videoId, client)
        val until = standDownUntil[k] ?: return false
        if (until > SystemClock.elapsedRealtime()) return true
        standDownUntil.remove(k)
        return false
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
     */
    private fun newPipeUrl(videoId: String): String {
        val info = StreamInfo.getInfo(
            ServiceList.YouTube,
            "https://www.youtube.com/watch?v=$videoId",
        )
        val candidates = info.audioStreams
            // Progressive only — DASH/HLS entries carry a manifest, not a URL.
            .filter {
                !it.content.isNullOrBlank() &&
                    it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP
            }
        val stream = pickForQuality(candidates.map { it.averageBitrate to it })
            ?: error("Track unavailable: no audio streams")
        Log.d(TAG, "NewPipe picked ${stream.format?.name} @ ${stream.averageBitrate}kbps")
        NerdStats.onStreamPicked(videoId, stream.averageBitrate)
        return deobfuscate(videoId, stream.content)
    }

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
            val cutoff = SystemClock.elapsedRealtime() - URL_TTL_MS
            recent.entries.removeAll { it.value.at < cutoff }
            if (recent.size >= MAX_REMEMBERED) recent.clear()
        }
        recent[videoId] = Resolved(url, SystemClock.elapsedRealtime())
    }
}
