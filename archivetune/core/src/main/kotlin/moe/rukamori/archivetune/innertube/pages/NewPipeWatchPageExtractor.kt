/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.innertube.pages

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.innertube.NewPipeUtils
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.concurrent.ConcurrentHashMap

/**
 * Watch-page stream extraction via NewPipe's `StreamInfo` — the resolution path
 * SpatialFlow (github.com/MythicalSHUB/SpatialFlow, GPL-3.0) uses, ported 2026-09-04
 * per user request: "see how this repo resolves YouTube source streams because I'm
 * getting an error. Port the exact way into my repo" (the error being InnerTube's
 * HTTP 403 / PO-Token "No stream available", which this path sidesteps entirely —
 * it scrapes the public watch page and deciphers the player response locally, no
 * PO token, no proxy).
 *
 * The mechanism, exactly as SpatialFlow's `NewPipeStreamExtractor` does it:
 *  - `StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=ID")`
 *    fetches the watch page, runs the player request through NewPipe's client
 *    chain and deobfuscates the stream URLs (signature + throttling parameter)
 *    with the cached player JavaScript.
 *  - Multi-level caching: Tier 1 the resolved URL (500 entries), Tier 2 the parsed
 *    `StreamInfo` (200 entries, slow to fetch, fast to re-parse).
 *  - Request de-duplication: concurrent callers for the same video share ONE
 *    in-flight extraction.
 *  - Quality selection by preference: LOW picks the lowest-bitrate audio-only
 *    stream, MEDIUM the median, HIGH the highest (SpatialFlow's Data Saver /
 *    Normal / High mapping).
 *
 * Lives in :core because the MetrolistExtractor (`org.schabi.newpipe.extractor`)
 * dependency is declared here; the app module reaches it through this class.
 * The shared proxy-aware OkHttp downloader (see [NewPipeUtils]) is used for all
 * HTTP so a user-configured YouTube proxy keeps applying. Pure-JVM module, so
 * logging goes through println (the module's convention — see YouTube.kt) and
 * the caches are plain synchronized maps.
 */
object NewPipeWatchPageExtractor {

    private const val TAG = "NewPipeWatchExtractor"

    private const val URL_CACHE_MAX = 500
    private const val INFO_CACHE_MAX = 200

    private val initialized = Mutex()

    // === Multi-level caching (SpatialFlow's exact tiers) ===
    // Tier 1: fastest — just the URL. Bounded LinkedHashMap in LRU order
    // (access-order), the JVM equivalent of android.util.LruCache.
    private val streamUrlCache: LinkedHashMap<String, String> =
        object : LinkedHashMap<String, String>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>): Boolean =
                size > URL_CACHE_MAX
        }

    // Tier 2: slowest to fetch, fast to re-parse.
    private val streamInfoCache: LinkedHashMap<String, StreamInfo> =
        object : LinkedHashMap<String, StreamInfo>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, StreamInfo>): Boolean =
                size > INFO_CACHE_MAX
        }

    private val cacheLock = Any()

    // === Request de-duplication ===
    private val inFlightRequests = ConcurrentHashMap<String, Deferred<ExtractionResult?>>()

    private val extractorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The resolved audio stream, mapped off NewPipe's [AudioStream]. */
    data class ResolvedWatchStream(
        val url: String,
        val itag: Int,
        val mimeType: String,
        val codecs: String?,
        val bitrate: Int,
        val contentLength: Long?,
        val title: String?,
        val artist: String?,
        val durationSeconds: Long,
        val thumbnailUrl: String?,
        val isMuxed: Boolean,
    )

    private data class ExtractionResult(
        val stream: ResolvedWatchStream,
        /** The same extraction also seeds the Tier-1 URL cache. */
        val url: String,
    )

    /** Quality preference — SpatialFlow's Data Saver / Normal / High selection. */
    enum class QualityPreference {
        LOW,
        MEDIUM,
        HIGH,
    }

    private suspend fun ensureInitialized() {
        // The shared downloader is installed by NewPipeUtils' object init; the
        // mutex keeps re-init (a no-op once the object exists) single-threaded.
        initialized.withLock {
            runCatching { NewPipeUtils.ensureInitialized() }
                .onFailure { println("$TAG: NewPipe init failed: ${it.message}") }
        }
    }

    fun clearCache() {
        synchronized(cacheLock) {
            streamUrlCache.clear()
            streamInfoCache.clear()
        }
        inFlightRequests.values.forEach { it.cancel() }
        inFlightRequests.clear()
    }

    fun clearVideoCache(videoId: String) {
        synchronized(cacheLock) {
            streamUrlCache.remove(videoId)
            streamInfoCache.remove(videoId)
        }
    }

    /**
     * Extract the best audio stream URL for a YouTube video — the exact call
     * chain SpatialFlow's player service uses, kept as the URL-only variant
     * so the app can fast-path playback before the full result is needed.
     */
    suspend fun getStreamUrl(
        videoId: String,
        preference: QualityPreference = QualityPreference.HIGH,
    ): Result<String> {
        val cachedUrl = synchronized(cacheLock) { streamUrlCache[videoId] }
        if (cachedUrl != null) return Result.success(cachedUrl)
        return extract(videoId, preference).map { it.url }
    }

    /**
     * Full watch-page extraction: metadata + the selected audio stream.
     */
    suspend fun extract(
        videoId: String,
        preference: QualityPreference = QualityPreference.HIGH,
    ): Result<ResolvedWatchStream> = coroutineScope {
        if (videoId.isBlank()) return@coroutineScope Result.failure(IllegalArgumentException("blank videoId"))

        ensureInitialized()

        // De-duplicate concurrent extractions for the same video.
        val deferred = inFlightRequests.computeIfAbsent(videoId) {
            extractorScope.async {
                try {
                    extractUncached(videoId, preference)
                } catch (t: Throwable) {
                    println("$TAG: watch-page extraction failed for $videoId: ${t.message}")
                    null
                } finally {
                    inFlightRequests.remove(videoId)
                }
            }
        }
        val shared =
            deferred.await()
                ?: return@coroutineScope Result.failure(
                    IllegalStateException("NewPipe watch-page extraction failed for $videoId"),
                )
        Result.success(shared.stream)
    }

    private suspend fun extractUncached(
        videoId: String,
        preference: QualityPreference,
    ): ExtractionResult? {
        val info = fetchStreamInfo(videoId) ?: return null

        val audioStreams = info.audioStreams
        var best: AudioStream? = null
        var muxedFallbackUrl: String? = null
        if (audioStreams.isEmpty()) {
            // SpatialFlow's fallback: when no audio-only stream is available
            // (rare, mostly region-locked uploads), fall back to the first
            // muxed video/audio stream so playback still succeeds.
            muxedFallbackUrl = info.videoStreams.firstOrNull()?.content
        } else {
            best = selectBestStream(audioStreams, preference)
        }

        val url = best?.content ?: muxedFallbackUrl ?: return null

        val resolved =
            ResolvedWatchStream(
                url = url,
                // BravePipe's Stream#getId() returns the itag as a String
                // ("140", "251", ...), so parse it instead of assigning the
                // String straight into the Int field.
                itag = best?.id?.toIntOrNull() ?: -1,
                mimeType = best?.format?.mimeType ?: "audio/webm",
                codecs =
                    best?.format?.mimeType
                        ?.substringAfter("codecs=", "")
                        ?.removeSurrounding("\"")
                        ?.substringBefore("\"")
                        ?.takeIf { it.isNotBlank() },
                bitrate = best?.averageBitrate ?: 0,
                // BravePipe's AudioStream carries no content length (unlike
                // InnerTube's Format) — leave it unknown rather than guessing.
                contentLength = null,
                title = info.name,
                artist = info.uploaderName,
                durationSeconds = info.duration.toLong(),
                thumbnailUrl = info.thumbnails.lastOrNull()?.url,
                isMuxed = best == null && muxedFallbackUrl != null,
            )

        synchronized(cacheLock) { streamUrlCache[videoId] = url }
        return ExtractionResult(resolved, url)
    }

    /**
     * Tier-2 fetch: `StreamInfo.getInfo` on the public watch URL. NewPipe
     * handles the player response parsing, the JS challenge (signature +
     * n-parameter deobfuscation) and the consent/cookie dance internally.
     */
    private suspend fun fetchStreamInfo(videoId: String): StreamInfo? {
        val cachedInfo = synchronized(cacheLock) { streamInfoCache[videoId] }
        if (cachedInfo != null) return cachedInfo

        return withContext(Dispatchers.IO) {
            try {
                val url = "https://www.youtube.com/watch?v=$videoId"
                val info = StreamInfo.getInfo(ServiceList.YouTube, url)
                synchronized(cacheLock) { streamInfoCache[videoId] = info }
                info
            } catch (e: Exception) {
                println("$TAG: StreamInfo.getInfo failed for $videoId: ${e.message}")
                null
            }
        }
    }

    /**
     * SpatialFlow's quality selection: LOW = lowest bitrate (Data Saver),
     * MEDIUM = the median stream (Normal), HIGH = the highest bitrate.
     */
    private fun selectBestStream(
        audioStreams: List<AudioStream>,
        preference: QualityPreference,
    ): AudioStream? {
        if (audioStreams.isEmpty()) return null
        return when (preference) {
            QualityPreference.LOW -> audioStreams.minByOrNull { maxOf(it.bitrate, it.averageBitrate) }
            QualityPreference.MEDIUM -> {
                val sorted = audioStreams.sortedBy { maxOf(it.bitrate, it.averageBitrate) }
                sorted.elementAtOrNull(sorted.size / 2)
            }
            QualityPreference.HIGH -> audioStreams.maxByOrNull { maxOf(it.bitrate, it.averageBitrate) }
        }
    }
}
