package com.music.bitchord.data.innertube

import android.os.SystemClock
import android.util.Log
import com.music.bitchord.data.Http
import com.music.bitchord.data.NerdStats
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import java.util.concurrent.ConcurrentHashMap

/**
 * Turns a videoId into a URL ExoPlayer can actually stream.
 *
 * The subtlety that makes or breaks this: every googlevideo URL carries an
 * `n` query parameter. Sent as-is, YouTube throttles the response to a crawl
 * or rejects it outright with **HTTP 403**. The value has to be transformed
 * by running YouTube's own player JavaScript — which is what NewPipe's
 * [YoutubeJavaScriptPlayerManager] does (and caches per player version).
 *
 * Strategy, most reliable path first:
 *  1. NewPipe's full extractor, which picks a client that still works, derives
 *     the signature timestamp and solves `n` itself. Tried [NEWPIPE_ATTEMPTS]
 *     times, because its failures are usually transient — a timeout, or the
 *     loader thread being interrupted — rather than a real "can't play this".
 *  2. Only then the hand-rolled Innertube IOS player response. Google has
 *     since tightened that client, and the URLs it mints are frequently
 *     rejected with **HTTP 403**, so it is a last resort and not a peer of the
 *     path above. A 403 from it is recovered by [invalidate] + a re-resolve,
 *     driven from PlaybackService's error handler.
 */
object StreamResolver {

    private const val TAG = "BitChord"

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
     * @return a directly streamable URL, or throws with a reason worth showing.
     *
     * NewPipe goes first: it picks a client that still works, derives the
     * signature timestamp and solves `n` itself, and is updated upstream when
     * YouTube changes. The hand-rolled Innertube path is only a fallback.
     *
     * Results are held briefly — see [recent]. Resolving is the slow part of
     * starting a track, and ExoPlayer asks again for every re-open: each seek
     * outside the buffer, and each range the cache fills in.
     *
     * Resolution for one videoId is serialised by [locks]. Without it the
     * player's loader and [com.music.bitchord.playback.AudioCache]'s read-ahead
     * race to resolve the same track at the same moment, tripling the work —
     * each extraction is several round trips plus running YouTube's player
     * JavaScript — and making the timeouts that push us onto the failing
     * fallback far more likely. The second caller through the gate finds the
     * first one's result already in [recent] and pays nothing.
     */
    suspend fun resolve(videoId: String): String {
        init

        cached(videoId)?.let { return it }

        val lock = locks.computeIfAbsent(videoId) { Mutex() }
        return lock.withLock {
            // Whoever held the lock has very likely just resolved this.
            cached(videoId) ?: extract(videoId).also { remember(videoId, it) }
        }
    }

    /** The remembered URL for [videoId], if one is still inside its TTL. */
    private fun cached(videoId: String): String? = recent[videoId]
        ?.takeIf { SystemClock.elapsedRealtime() - it.at < URL_TTL_MS }
        ?.url

    /**
     * Drops the remembered URL for [videoId], so the next [resolve] goes back
     * out and mints a fresh one.
     *
     * Needed because a URL is remembered as soon as it is produced, before
     * anything has tried to fetch it. When one turns out to be dead — a 403
     * off the IOS fallback — the entry would otherwise keep being served from
     * [recent] for the whole TTL, and every retry would fail identically
     * without a single request leaving the device.
     */
    fun invalidate(videoId: String) {
        recent.remove(videoId)
    }

    /**
     * NewPipe first and repeatedly, then the IOS fallback.
     *
     * Cancellation is rethrown rather than retried: when ExoPlayer abandons a
     * load it interrupts the thread this runs on, and there is nothing left to
     * resolve for.
     */
    private suspend fun extract(videoId: String): String {
        repeat(NEWPIPE_ATTEMPTS) { attempt ->
            try {
                return newPipeUrl(videoId).also { Log.d(TAG, "resolved via NewPipe") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: InterruptedException) {
                // The interrupt flag is still set; anything further fails
                // instantly, so stop rather than burn the remaining attempts.
                Thread.currentThread().interrupt()
                throw e
            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "NewPipe resolve failed for $videoId " +
                        "(attempt ${attempt + 1}/$NEWPIPE_ATTEMPTS): ${e.message}",
                )
            }
        }
        Log.w(TAG, "falling back to Innertube IOS client for $videoId — expect 403s")
        return innertubeUrl(videoId)
    }

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
     * Stream URLs already resolved, by videoId.
     *
     * Google issues them with several hours of validity, so the ceiling here is
     * chosen for a different reason: a URL is tied to the playback session that
     * minted it, and holding one indefinitely means a stale entry survives long
     * enough to fail a play. Twenty minutes covers a track and the seeking
     * around it while staying well inside the window where the URL is good.
     */
    private val recent = ConcurrentHashMap<String, Resolved>()

    /** One gate per videoId, so concurrent callers resolve it only once. */
    private val locks = ConcurrentHashMap<String, Mutex>()

    private const val URL_TTL_MS = 20 * 60 * 1000L

    /** Enough for the queue in hand; this is a latency cache, not a store. */
    private const val MAX_REMEMBERED = 32

    /**
     * How many times NewPipe is asked before the IOS fallback.
     *
     * Its failures in practice are `timeout` and `interrupted` — transient, and
     * a second ask usually succeeds. Since the fallback is the thing that
     * returns 403s, spending another few seconds here is strictly better than
     * reaching it.
     */
    private const val NEWPIPE_ATTEMPTS = 3

    private fun remember(videoId: String, url: String) {
        if (recent.size >= MAX_REMEMBERED) {
            val cutoff = SystemClock.elapsedRealtime() - URL_TTL_MS
            recent.entries.removeAll { it.value.at < cutoff }
            if (recent.size >= MAX_REMEMBERED) recent.clear()
        }
        recent[videoId] = Resolved(url, SystemClock.elapsedRealtime())
        // The gates outlive the entries they guarded, so they are trimmed on
        // the same schedule rather than growing for the life of the process.
        if (locks.size > MAX_REMEMBERED) {
            locks.keys.removeAll { !recent.containsKey(it) && locks[it]?.isLocked != true }
        }
    }

    private suspend fun innertubeUrl(videoId: String): String {
        val raw = Innertube.playerStreamUrl(videoId)
            ?: error("no audio format for $videoId")
        return deobfuscate(videoId, raw)
    }

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
        // The container carries no bitrate field, so this is the only place the
        // real figure for the chosen stream is ever known.
        NerdStats.onStreamPicked(videoId, stream.averageBitrate)
        return deobfuscate(videoId, stream.content)
    }

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

    /**
     * Transform the `n` parameter when present. If deobfuscation itself fails
     * we still return the original URL — a throttled stream beats no stream.
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
}
