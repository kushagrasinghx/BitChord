package com.music.bitchord.data.innertube

import android.os.SystemClock
import android.util.Log
import com.music.bitchord.data.Http
import com.music.bitchord.data.NerdStats
import com.music.bitchord.data.settings.AppSettings
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
 * Strategy, cheapest path first:
 *  1. Innertube IOS player response → direct unciphered URL → deobfuscate `n`.
 *  2. Fall back to NewPipe's full extractor, which re-derives everything
 *     itself and survives client-side changes on YouTube's end.
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
     */
    suspend fun resolve(videoId: String): String {
        init

        recent[videoId]
            ?.takeIf { SystemClock.elapsedRealtime() - it.at < URL_TTL_MS }
            ?.let { return it.url }

        val url = runCatching { newPipeUrl(videoId) }
            .onFailure { Log.w(TAG, "NewPipe resolve failed for $videoId: ${it.message}") }
            .getOrNull()
            ?.also { Log.d(TAG, "resolved via NewPipe") }
            ?: run {
                Log.d(TAG, "falling back to Innertube IOS client for $videoId")
                innertubeUrl(videoId)
            }

        remember(videoId, url)
        return url
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
