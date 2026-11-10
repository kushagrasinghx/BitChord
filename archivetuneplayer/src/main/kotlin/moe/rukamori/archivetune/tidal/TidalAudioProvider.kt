
package moe.rukamori.archivetune.tidal

import android.net.Uri
import moe.rukamori.archivetune.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.supervisorScope
import moe.rukamori.archivetune.audiosource.AudioSourceAttemptScope
import moe.rukamori.archivetune.audiosource.AudioSourceAttemptTimeouts
import moe.rukamori.archivetune.audiosource.DirectStream
import moe.rukamori.archivetune.audiosource.TrackMatching
import moe.rukamori.archivetune.audiosource.rethrowIfAudioSourceCancelled
import moe.rukamori.archivetune.audiosource.withAudioSourceAttemptDeadline
import moe.rukamori.archivetune.constants.AudioSourceType
import moe.rukamori.archivetune.utils.PoolAccountManager
import android.util.Base64
import moe.rukamori.archivetune.constants.TidalAudioQuality
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.text.Normalizer
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.io.File
import java.io.OutputStream
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

object TidalAudioProvider {
    internal const val PROGRESSIVE_DASH_SCHEME = "tidal-dash"
    private const val API_BASE_URL = "https://tidal.com/v1"
    private const val SONG_LINK_API_URL = "https://api.song.link/v1-alpha.1/links"
    private const val PUBLIC_TOKEN = "49YxDN9a2aFV6RTG"
    private const val COUNTRY_CODE = "US"
    private const val LOCALE = "en_US"
    private const val DEVICE_TYPE = "BROWSER"
    private const val DOWNLOAD_USER_AGENT = "ArchiveTune-Android"
    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36"
    private const val DASH_MIME_TYPE = "application/dash+xml"
    private const val AUDIO_MP4_MIME_TYPE = "audio/mp4"
    private const val AUDIO_FLAC_MIME_TYPE = "audio/flac"
    private const val MP4_BOX_HEADER_SIZE = 8
    private const val MP4_EXTENDED_BOX_HEADER_SIZE = 16
    private const val STREAM_CACHE_MS = 45 * 60 * 1000L
    private const val TRACK_CACHE_MS = 60 * 60 * 1000L
    private const val STREAM_FAILURE_CACHE_MS = 15 * 60 * 1000L
    private const val TRACK_STREAM_FAILURE_CACHE_MS = 60 * 60 * 1000L
    private const val TEMP_FILE_MAX_AGE_MS = 6 * 60 * 60 * 1000L
    private const val RATE_LIMIT_COOLDOWN_MS = 8 * 60 * 1000L
    private const val SEARCH_LIMIT = 8
    private const val SEARCH_CACHE_MS = 10 * 60 * 1000L
    private const val MAX_STREAM_CANDIDATES = 2
    private const val MAX_DIRECT_STREAM_CANDIDATES = 3

    private const val MAX_CONCURRENT_TIDAL_ENDPOINTS = 8
    private const val MIN_MATCH_SCORE = 90
    private const val ARTWORK_MAX_SCORE = 220

    private const val INSTANCE_SOFT_COOLDOWN_MS = 60_000L
    private const val INSTANCE_HARD_COOLDOWN_MS = 600_000L
    private const val STRONG_MATCH_SCORE = 150
    private const val REJECT_SCORE = -1_000_000
    private val AMAZON_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.US)

    private val ISRC_STRIP_REGEX = Regex("[^A-Z0-9]")
    private val ISRC_PATTERN_REGEX = Regex("[A-Z]{2}[A-Z0-9]{3}[0-9]{7}")
    private val DIACRITIC_REGEX = Regex("\\p{Mn}+")

    private val NON_ALPHANUMERIC_REGEX = Regex("[^\\p{L}\\p{N}]+")
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val FEATURED_ARTIST_TITLE_SUFFIX_REGEX = Regex("""\b(feat|ft|featuring)\b.*$""")
    private val EDITION_NOISE_WORD_REGEX = Regex("""\b(explicit|clean|remaster|remastered|version|audio|official)\b""")
    private val FEATURED_ARTIST_BRACKET_REGEX =
        Regex("""\s*[\[(]\s*(feat\.?|ft\.?|featuring)\b.*?[\])]""", RegexOption.IGNORE_CASE)
    private val EDITION_SUFFIX_REGEX =
        Regex("""\s*-\s*(explicit|clean|remaster(?:ed)?|audio|official)\b.*$""", RegexOption.IGNORE_CASE)
    private val NUMERIC_ONLY_REGEX = Regex("\\d+")
    private val TIDAL_URI_TRACK_ID_REGEX = Regex("""^tidal:track:(\d+)$""", RegexOption.IGNORE_CASE)
    private val TIDAL_URL_TRACK_ID_REGEX = Regex("""tidal\.com/(?:browse/)?track/(\d+)""", RegexOption.IGNORE_CASE)
    private val SPOTIFY_URI_TRACK_ID_REGEX = Regex("""spotify[:/](?:track[:/])?([A-Za-z0-9]{22})""", RegexOption.IGNORE_CASE)
    private val SPOTIFY_ID_ONLY_REGEX = Regex("[A-Za-z0-9]{22}")
    private val YOUTUBE_ID_ONLY_REGEX = Regex("[A-Za-z0-9_-]{11}")
    private val HTTP_URL_REGEX = Regex("""https?://[^"'<>\s]+""")
    private val DASH_SEGMENT_TIMELINE_REGEX =
        Regex("""<S\s+[^>]*d="(\d+)"(?:\s+r="(-?\d+)")?[^>]*/?>""", RegexOption.IGNORE_CASE)
    private val DASH_INITIALIZATION_URL_REGEX = Regex("<Initialization\\b[^>]*sourceURL=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
    private val DASH_SEGMENT_URL_REGEX = Regex("<SegmentURL\\b[^>]*media=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
    private val XML_UNESCAPED_AMPERSAND_REGEX = Regex("""&(?!amp;|lt;|gt;|quot;|apos;|#\d+;|#x[0-9a-fA-F]+;)""")

    private val DEFAULT_DOWNLOAD_API_ENDPOINTS = emptyList<TidalDownloadEndpoint>()

    private val INSTANCE_DISCOVERY_SOURCES: List<String> =
        BuildConfig.SOURCE_PROVIDER_URL
            .trim()
            .trimEnd('/')
            .takeIf { it.isNotEmpty() }
            ?.let { listOf("$it/api/discovery/tidal") }
            ?: emptyList()

    @Volatile
    private var customEndpoints: List<TidalDownloadEndpoint> = emptyList()

    private val activeEndpoints: List<TidalDownloadEndpoint>
        get() = customEndpoints

    val defaultInstanceUrls: List<String>
        get() = DEFAULT_DOWNLOAD_API_ENDPOINTS.map { it.baseUrl }

    val activeInstanceUrls: List<String>
        get() = activeEndpoints.map { it.baseUrl }

    fun setInstances(baseUrls: List<String>) {
        val seen = LinkedHashSet<String>()
        customEndpoints =
            baseUrls.mapNotNull { raw ->
                val normalized = normalizeInstanceUrl(raw) ?: return@mapNotNull null
                if (!seen.add(normalized)) return@mapNotNull null
                TidalDownloadEndpoint(instanceLabel(normalized), normalized)
            }
    }

    fun normalizeInstanceUrl(raw: String): String? {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return null
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
        val url = withScheme.toHttpUrlOrNull() ?: return null
        if (url.host.isBlank() || !url.host.contains('.')) return null
        return withScheme
    }

    private fun instanceLabel(baseUrl: String): String =
        baseUrl.toHttpUrlOrNull()?.host ?: baseUrl

    fun checkInstance(baseUrl: String): Long? {
        val normalized = normalizeInstanceUrl(baseUrl) ?: return null
        val request =
            Request
                .Builder()
                .url("$normalized/")
                .header("User-Agent", DOWNLOAD_USER_AGENT)
                .get()
                .build()
        return runCatching {
            val start = System.currentTimeMillis()
            healthClient.newCall(request).execute().use { response ->
                if (response.code in 200..499) System.currentTimeMillis() - start else null
            }
        }.onFailure { it.rethrowIfAudioSourceCancelled() }.getOrNull()
    }

    enum class InstanceHealth {
        HEALTHY,

        PREVIEW_ONLY,

        UNREACHABLE,
    }

    @Volatile
    var lastResolvedTrackId: String? = null
        private set

    fun seedProbeTrack(trackId: String) {
        if (lastResolvedTrackId.isNullOrBlank() && trackId.isNotBlank()) {
            lastResolvedTrackId = trackId
        }
    }

    fun findHealthProbeTrackId(): String? =
        AudioSourceAttemptScope.within(AudioSourceAttemptTimeouts.PROVIDER_ATTEMPT_MS) {
            if (activeEndpoints.isEmpty()) {
                null
            } else {
                runCatching {
                    val items = searchTracks("adele hello") ?: return@runCatching null
                    (0 until items.length())
                        .firstNotNullOfOrNull { items.optJSONObject(it)?.toMatchedTrack()?.trackId }
                        ?.also { lastResolvedTrackId = it }
                }.onFailure { it.rethrowIfAudioSourceCancelled() }.getOrNull()
            }
        }

    fun verifyInstance(
        baseUrl: String,
        probeTrackId: String?,
        quality: TidalAudioQuality = TidalAudioQuality.FLAC,
    ): InstanceHealth {
        val normalized = normalizeInstanceUrl(baseUrl) ?: return InstanceHealth.UNREACHABLE
        val trackId = probeTrackId?.trim().orEmpty().ifBlank { findHealthProbeTrackId().orEmpty() }
        if (trackId.isEmpty()) return InstanceHealth.UNREACHABLE
        val qualityString =
            when (quality) {
                TidalAudioQuality.HI_RES_LOSSLESS -> "HI_RES_LOSSLESS"
                TidalAudioQuality.FLAC -> "LOSSLESS"
                TidalAudioQuality.AAC_320 -> "HIGH"
            }
        val manifestFormats = qualityString.tidalManifestFormats()
            ?: return probeTrackDialect(normalized, trackId, qualityString)
        val viaManifests = probeTrackManifestsDialect(normalized, trackId, manifestFormats)

        if (viaManifests != InstanceHealth.UNREACHABLE) return viaManifests
        return probeTrackDialect(normalized, trackId, qualityString)
    }

    private fun probeTrackManifestsDialect(
        normalized: String,
        trackId: String,
        manifestFormats: List<String>,
    ): InstanceHealth {
        val url =
            normalized
                .toHttpUrl()
                .newBuilder()
                .addPathSegment("trackManifests")
                .addPathSegment("")
                .addQueryParameter("id", trackId)
                .apply { manifestFormats.forEach { addQueryParameter("formats", it) } }
                .addQueryParameter("adaptive", "false")
                .addQueryParameter("manifestType", "MPEG_DASH")
                .addQueryParameter("uriScheme", "HTTPS")
                .addQueryParameter("usage", "PLAYBACK")
                .build()
        return runCatching {
            healthClient.newCall(buildProbeRequest(url)).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful || body.isBlank()) return@use InstanceHealth.UNREACHABLE
                val attributes =
                    JSONObject(body)
                        .optJSONObject("data")
                        ?.optJSONObject("data")
                        ?.optJSONObject("attributes")
                        ?: return@use InstanceHealth.UNREACHABLE
                val presentation =
                    attributes.stringOrNull("trackPresentation")
                        ?: attributes.stringOrNull("assetPresentation")
                if (presentation.equals("PREVIEW", ignoreCase = true)) {
                    InstanceHealth.PREVIEW_ONLY
                } else {
                    InstanceHealth.HEALTHY
                }
            }
        }.getOrElse { InstanceHealth.UNREACHABLE }
    }

    private fun probeTrackDialect(
        normalized: String,
        trackId: String,
        qualityString: String,
    ): InstanceHealth {
        val url =
            normalized
                .toHttpUrl()
                .newBuilder()
                .addPathSegment("track")
                .addPathSegment("")
                .addQueryParameter("id", trackId)
                .addQueryParameter("quality", qualityString)
                .build()
        return runCatching {
            healthClient.newCall(buildProbeRequest(url)).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful || body.isBlank()) return@use InstanceHealth.UNREACHABLE
                val data = JSONObject(body).optJSONObject("data") ?: return@use InstanceHealth.UNREACHABLE
                if (data.optString("assetPresentation").equals("PREVIEW", ignoreCase = true)) {
                    return@use InstanceHealth.PREVIEW_ONLY
                }

                if (data.stringOrNull("manifest").isNullOrBlank()) {
                    InstanceHealth.UNREACHABLE
                } else {
                    InstanceHealth.HEALTHY
                }
            }
        }.getOrElse { InstanceHealth.UNREACHABLE }
    }

    private fun buildProbeRequest(url: HttpUrl): Request =
        Request
            .Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .header("User-Agent", DOWNLOAD_USER_AGENT)
            .build()

    fun applyHealthResult(
        baseUrl: String,
        healthy: Boolean,
    ) {
        val normalized = normalizeInstanceUrl(baseUrl) ?: return
        if (healthy) markInstanceHealthy(normalized) else markInstanceFailed(normalized, hardFailure = true)
    }

    fun discoverInstances(): List<String> =
        AudioSourceAttemptScope.within(AudioSourceAttemptTimeouts.PROVIDER_ATTEMPT_MS) {
            discoverInstancesWithinAttempt()
        }

    private fun discoverInstancesWithinAttempt(): List<String> {
        val discovered = LinkedHashSet<String>()
        for (source in INSTANCE_DISCOVERY_SOURCES) {
            runCatching {
                val builder =
                    Request
                        .Builder()
                        .url(source)
                        .header("User-Agent", DOWNLOAD_USER_AGENT)

                if (BuildConfig.SOURCE_PROVIDER_KEY.isNotBlank()) {
                    builder.header("Authorization", "Bearer ${BuildConfig.SOURCE_PROVIDER_KEY}")
                }
                val request = builder.get().build()
                healthClient.newCall(request).withAudioSourceAttemptDeadline().execute().use { response ->
                    if (!response.isSuccessful) {
                        if (response.code == 401) {
                            Timber
                                .tag("TidalHealth")
                                .w(
                                    "Pool discovery %s rejected as unauthorized (HTTP 401); " +
                                        "SOURCE_PROVIDER_KEY is missing or invalid for this build.",
                                    source,
                                )
                        }
                        return@use
                    }
                    val body = response.body?.string().orEmpty()
                    parseDiscoveredInstances(body).forEach { url ->
                        normalizeInstanceUrl(url)?.let(discovered::add)
                    }
                }
            }.onFailure { it.rethrowIfAudioSourceCancelled() }
        }
        return discovered.toList()
    }

    private fun parseDiscoveredInstances(body: String): List<String> {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return emptyList()

        runCatching {
            val obj = JSONObject(trimmed)
            val result = LinkedHashSet<String>()

            val keys = listOf("streaming", "instances", "api")
            for (key in keys) {
                val arr = obj.optJSONArray(key) ?: continue
                collectStringsFromArray(arr, result)
            }
            val filtered = result.filterNot { it.contains(".squid.wtf", ignoreCase = true) }
            if (filtered.isNotEmpty()) return filtered
        }

        runCatching {
            val result = LinkedHashSet<String>()
            collectStringsFromArray(JSONArray(trimmed), result)
            if (result.isNotEmpty()) return result.toList()
        }

        return trimmed
            .split('\n', '\r', ' ', ',')
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
    }

    private fun collectStringsFromArray(
        arr: JSONArray,
        into: MutableCollection<String>,
    ) {
        for (i in 0 until arr.length()) {
            when (val item = arr.opt(i)) {
                is String -> item.takeIf { it.isNotBlank() }?.let(into::add)
                is JSONObject -> {
                    (item.optString("url").takeIf { it.isNotBlank() }
                        ?: item.optString("host").takeIf { it.isNotBlank() })
                        ?.let(into::add)
                }
            }
        }
    }

    data class Query(
        val mediaId: String,
        val title: String,
        val artists: List<String>,
        val album: String?,
        val isrc: String?,
        val durationMs: Long?,
    )

    data class Resolved(
        val mediaUri: String,
        val trackId: String,
        val label: String,
        val mimeType: String,
        val codecs: String,
        val bitrate: Int,
        val sampleRate: Int?,
        val bitDepth: Int? = null,
        val contentLength: Long?,
        val expiresAtMs: Long,
        val losslessDowngradedBitrateKbps: Int? = null,
        val isLiveManifest: Boolean = false,

        val matchedTitle: String? = null,
        val matchedArtist: String? = null,
        val matchedAlbum: String? = null,
        val matchedDurationMs: Long? = null,
    )

    data class CandidateMetadata(
        val trackId: String,
        val title: String,
        val artist: String,
        val album: String?,
        val isrc: String?,
        val durationMs: Long?,
        val thumbnailUrl: String? = null,
    )

    open class TidalAudioResolutionException(message: String, cause: Throwable? = null) : Exception(message, cause)

    class TidalRateLimitedException(
        val retryAfterMs: Long,
    ) : TidalAudioResolutionException("TIDAL FLAC resolver is rate limited; cooling down for ${retryAfterMs / 1000L}s")

    private class TidalDialectUnsupportedException(message: String) : TidalAudioResolutionException(message)

    private data class CachedTrack(
        val track: MatchedTrack,
        val expiresAtMs: Long,
    )

    private data class CachedFailure(
        val message: String,
        val expiresAtMs: Long,
    )

    private data class CachedSearch(
        val results: JSONArray,
        val expiresAtMs: Long,
    )

    private data class MatchedTrack(
        val trackId: String,
        val title: String,
        val artistNames: List<String>,
        val album: String?,
        val isrc: String?,
        val durationMs: Long?,
        val audioQuality: String?,
        val mediaTags: Set<String>,
        val audioModes: Set<String>,
        val sampleRate: Int?,
        val albumId: String? = null,
        val albumCoverId: String? = null,
    ) {
        fun supportsLossless(): Boolean =
            qualitySignals().any { signal ->
                signal.contains("LOSSLESS", ignoreCase = true) ||
                    signal.contains("FLAC", ignoreCase = true)
            }

        fun supportsHiResLossless(): Boolean =
            qualitySignals().any { signal ->
                signal.contains("HI_RES", ignoreCase = true) ||
                    signal.contains("HIRES", ignoreCase = true) ||
                    signal.contains("MAX", ignoreCase = true)
            }

        fun supportsAtmos(): Boolean =
            qualitySignals().any { signal ->
                signal.contains("DOLBY_ATMOS", ignoreCase = true) ||
                    signal.contains("DOLBY ATMOS", ignoreCase = true) ||
                    signal.contains("EAC3_JOC", ignoreCase = true) ||
                    signal.contains("ATMOS", ignoreCase = true)
            }

        fun losslessRank(): Int =
            when {
                supportsHiResLossless() -> 3
                supportsLossless() -> 2
                supportsAtmos() -> 1
                else -> 0
            }

        fun qualityScoreBoost(): Int =
            when {
                supportsHiResLossless() -> 70
                supportsLossless() -> 55
                supportsAtmos() -> 20
                else -> 0
            }

        private fun qualitySignals(): List<String> =
            buildList {
                audioQuality?.takeIf { it.isNotBlank() }?.let(::add)
                addAll(mediaTags)
                addAll(audioModes)
            }
    }

    private data class ParsedManifest(
        val url: String,
        val mimeType: String,
        val codecs: String,
        val sampleRate: Int?,
        val bitDepth: Int? = null,
        val bitrate: Int?,
        val expiryUrl: String?,
        val isDash: Boolean,
        val segmentUrls: List<String> = emptyList(),
        val outputExtension: String = ".flac",
        val manifestText: String? = null,
    )

    private data class StreamMetadata(
        val mimeType: String?,
        val contentLength: Long?,
    )

    private data class PlaybackStreamInfo(
        val mimeType: String,
        val codecs: String,
        val contentLength: Long?,
    )

    private class TidalDownloadEndpoint(
        val name: String,
        baseUrl: String,
    ) {
        val baseUrl: String = baseUrl.trimEnd('/')
    }

    private data class ScoredTrack(
        val track: MatchedTrack,
        val score: Int,
    )

    private val client =
        OkHttpClient
            .Builder()
            .dns(TidalDns)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .build()

    private val healthClient =
        OkHttpClient
            .Builder()
            .dns(TidalDns)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(6, TimeUnit.SECONDS)
            .build()

    private val trackCache = ConcurrentHashMap<String, CachedTrack>()
    private val searchCache = ConcurrentHashMap<String, CachedSearch>()
    private val streamCache = ConcurrentHashMap<String, Resolved>()
    private val streamFailureCache = ConcurrentHashMap<String, CachedFailure>()

    @Volatile
    private var resolverRateLimitedUntilMs = 0L

    private val instanceCooldownUntilMs = ConcurrentHashMap<String, Long>()

    private fun markInstanceHealthy(baseUrl: String) {
        instanceCooldownUntilMs.remove(baseUrl)
    }

    private fun markInstanceFailed(
        baseUrl: String,
        hardFailure: Boolean,
    ) {
        val cooldownMs = if (hardFailure) INSTANCE_HARD_COOLDOWN_MS else INSTANCE_SOFT_COOLDOWN_MS
        instanceCooldownUntilMs[baseUrl] = System.currentTimeMillis() + cooldownMs
    }

    private fun isInstanceCoolingDown(
        baseUrl: String,
        now: Long,
    ): Boolean = (instanceCooldownUntilMs[baseUrl] ?: 0L) > now

    private fun orderedEndpoints(): List<TidalDownloadEndpoint> {
        val now = System.currentTimeMillis()
        return activeEndpoints.sortedBy { if (isInstanceCoolingDown(it.baseUrl, now)) 1 else 0 }
    }

    fun resolve(
        query: Query,
        cacheDir: File? = null,
        preferAtmos: Boolean = false,
        preferLiveDash: Boolean = true,
        audioQuality: TidalAudioQuality = TidalAudioQuality.AAC_320,
    ): Resolved =
        AudioSourceAttemptScope.within(AudioSourceAttemptTimeouts.PROVIDER_ATTEMPT_MS) {
            resolveWithinAttempt(query, cacheDir, preferAtmos, preferLiveDash, audioQuality)
        }

    private fun resolveWithinAttempt(
        query: Query,
        cacheDir: File?,
        preferAtmos: Boolean,
        preferLiveDash: Boolean,
        audioQuality: TidalAudioQuality,
    ): Resolved {
        if (activeEndpoints.isEmpty()) {
            throw TidalAudioResolutionException("TIDAL playback has no configured instance")
        }
        val now = System.currentTimeMillis()
        val cooldownRemainingMs = resolverRateLimitedUntilMs - now
        if (cooldownRemainingMs > 0L) {
            throw TidalRateLimitedException(cooldownRemainingMs)
        }
        cacheDir?.let(::cleanupTempFiles)
        val directTrackId = query.mediaId.toTidalTrackIdOrNull()
        val trackCacheKey = query.trackCacheKey()
        val tracks = if (directTrackId != null) {
            val directTrack = resolveTrackById(directTrackId) ?: query.toDirectMatchedTrack(directTrackId)
            buildList {
                add(directTrack)
                findCandidateTracks(query)
                    .asSequence()
                    .filterNot { it.trackId == directTrack.trackId }
                    .take(MAX_DIRECT_STREAM_CANDIDATES - 1)
                    .forEach { add(it) }
            }.distinctBy { it.trackId }
        } else {
            val cached =
                trackCache[trackCacheKey]
                ?.takeIf { it.expiresAtMs > now }
                ?.track
            val candidates = findCandidateTracks(query)
            buildList {
                addAll(candidates)
                cached?.let(::add)
            }.distinctBy { it.trackId }
        }

        if (tracks.isEmpty()) {
            throw TidalAudioResolutionException("TIDAL match not found for ${query.title}")
        }

        val errors = mutableListOf<String>()
        var deferredAacFallback: Resolved? = null
        val streamCandidateLimit =
            if (directTrackId != null) MAX_DIRECT_STREAM_CANDIDATES else MAX_STREAM_CANDIDATES
        for (track in tracks.take(streamCandidateLimit)) {
            for (quality in streamQualityCandidates(track, preferAtmos, audioQuality)) {
                if (AudioSourceAttemptScope.current()?.isExpired() == true) break
                val streamCacheKey = "${query.mediaId}::${track.trackId}::$quality::${if (preferLiveDash) "live" else "temp"}"
                val trackFailureCacheKey = "track:${track.trackId}::$quality::${if (preferLiveDash) "live" else "temp"}"
                val cachedStream = streamCache[streamCacheKey]
                    ?.takeIf { it.expiresAtMs > now + 20_000L }
                if (cachedStream != null) {
                    if (cachedStream.losslessDowngradedBitrateKbps == null) {
                        return cachedStream
                    }
                    deferredAacFallback = deferredAacFallback ?: cachedStream
                    errors += "${track.trackId}/$quality: cached AAC fallback"
                    continue
                }

                val cachedFailure = cachedFailureFor(now, streamCacheKey, trackFailureCacheKey)
                if (cachedFailure != null) {
                    errors += "${track.trackId}/$quality: cached resolver failure: ${cachedFailure.message}"
                    continue
                }

                val streamAttempt = runCatching {
                    runBlocking(Dispatchers.IO) {
                        requestDirectFlac(
                            track = track,
                            quality = quality,
                            durationMs = query.durationMs ?: track.durationMs,
                            now = now,
                            cacheDir = cacheDir,
                            preferLiveDash = preferLiveDash,
                            audioQuality = audioQuality,
                        )
                    }
                }.onFailure { error ->
                    error.rethrowIfAudioSourceCancelled()
                    Timber.tag("TidalAudio").w(error, "TIDAL $quality stream failed for ${track.trackId}")
                    errors += "${track.trackId}/$quality: ${error.message ?: error.javaClass.simpleName}"

                    if (AudioSourceAttemptScope.current()?.isExpired() != true) {
                        cacheStreamFailure(now, error, streamCacheKey, trackFailureCacheKey)
                    }
                }

                streamAttempt.getOrNull()?.let { rawResolved ->

                    val resolved =
                        rawResolved.copy(
                            matchedTitle = track.title,
                            matchedArtist = track.artistNames.joinToString(", ").takeIf { it.isNotBlank() },
                            matchedAlbum = track.album,
                            matchedDurationMs = track.durationMs,
                        )
                    streamCache[streamCacheKey] = resolved
                    if (directTrackId == null) {
                        trackCache[trackCacheKey] = CachedTrack(track, now + TRACK_CACHE_MS)
                    }
                    if (resolved.losslessDowngradedBitrateKbps == null) {
                        return resolved
                    }
                    deferredAacFallback = deferredAacFallback ?: resolved
                    errors += "${track.trackId}/$quality: returned AAC fallback"
                }
            }
        }

        deferredAacFallback?.let { return it }

        throw TidalAudioResolutionException(
            "TIDAL FLAC stream not found for ${query.title}: ${errors.joinToString("; ").take(720)}",
        )
    }

    fun invalidate(mediaId: String) {
        val prefix = "$mediaId::"
        for (key in streamCache.keys) {
            if (key.startsWith(prefix)) {
                streamCache.remove(key)?.mediaUri?.deleteIfLocalFileUri()
            }
        }
        for (key in streamFailureCache.keys) {
            if (key.startsWith(prefix)) {
                streamFailureCache.remove(key)
            }
        }
        trackCache.remove(mediaId.trackCacheKeyFallback())
    }

    private fun cachedFailureFor(
        now: Long,
        vararg keys: String,
    ): CachedFailure? {
        for (key in keys.distinct()) {
            val failure = streamFailureCache[key] ?: continue
            if (failure.expiresAtMs > now) return failure
            streamFailureCache.remove(key)
        }
        return null
    }

    private fun cacheStreamFailure(
        now: Long,
        error: Throwable,
        vararg keys: String,
    ) {
        val message = error.message ?: error.javaClass.simpleName
        val expiresAtMs = now + error.streamFailureCacheDurationMs()
        keys
            .distinct()
            .forEach { key ->
                streamFailureCache[key] = CachedFailure(
                    message = message,
                    expiresAtMs = expiresAtMs,
                )
            }
    }

    private fun Throwable.streamFailureCacheDurationMs(): Long =
        when {
            this is TidalRateLimitedException || causeChainContains("rate limited") -> RATE_LIMIT_COOLDOWN_MS
            causeChainContains("did not return FLAC") || causeChainContains("downgraded") -> TRACK_STREAM_FAILURE_CACHE_MS
            else -> STREAM_FAILURE_CACHE_MS
        }

    private fun Throwable.causeChainContains(text: String): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current.message?.contains(text, ignoreCase = true) == true) return true
            current = current.cause
        }
        return false
    }

    fun searchCandidates(
        query: Query,
        limit: Int = 8,
    ): List<CandidateMetadata> =
        findCandidateTracks(query)
            .take(limit.coerceAtLeast(1))
            .map { track ->

                val thumbUrl = track.albumCoverId?.let { coverId ->
                    val normalized = coverId.replace("-", "/")
                    "https://resources.tidal.com/images/$normalized/320x320.jpg"
                }
                CandidateMetadata(
                    trackId = track.trackId,
                    title = track.title,
                    artist = track.artistNames.joinToString(", "),
                    album = track.album,
                    isrc = track.isrc,
                    durationMs = track.durationMs,
                    thumbnailUrl = thumbUrl,
                )
            }

    data class ArtworkSearchResult(
        val trackId: String,
        val releaseId: String?,
        val coverId: String,
        val score: Int,
        val exactIsrc: Boolean,
    ) {
        val confidence: Float
            get() =
                ((score - MIN_MATCH_SCORE).toFloat() / (ARTWORK_MAX_SCORE - MIN_MATCH_SCORE))
                    .coerceIn(0f, 1f)
    }

    fun resolveArtwork(query: Query): ArtworkSearchResult? {
        val wantedIsrc = normalizeIsrc(query.isrc)
        if (wantedIsrc != null) {
            searchTracks(wantedIsrc, exactIsrc = true)
                ?.let { selectArtworkCandidates(it, query, exactIsrcOnly = true) }
                ?.firstOrNull()
                ?.let { return it }
        }

        query.mediaId.toTidalTrackIdOrNull()?.let { trackId ->
            resolveTrackById(trackId)?.let { track ->
                val coverId = track.albumCoverId ?: return@let
                val score = contentArtworkScore(
                    track,
                    query.title.titleMatchNormalized(),
                    query.artists.map { it.normalized() }.filter { it.isNotBlank() },
                    query.album.normalized(),
                    wantedIsrc,
                    query.durationMs?.takeIf { it > 0L },
                )
                if (score >= MIN_MATCH_SCORE) {
                    return ArtworkSearchResult(
                        trackId = track.trackId,
                        releaseId = track.albumId,
                        coverId = coverId,
                        score = score,
                        exactIsrc = wantedIsrc != null && normalizeIsrc(track.isrc) == wantedIsrc,
                    )
                }
            }
        }

        var best: ArtworkSearchResult? = null
        for (term in searchTerms(query)) {
            val results = searchTracks(term) ?: continue
            val top = selectArtworkCandidates(results, query).firstOrNull() ?: continue
            if (top.score >= STRONG_MATCH_SCORE) return top
            if (top.score > (best?.score ?: Int.MIN_VALUE)) best = top
        }
        return best
    }

    fun probeCatalogSearch(
        term: String,
        exactIsrc: Boolean = false,
    ): JSONArray? = searchTracks(term, exactIsrc)

    fun configuredInstanceCount(): Int = orderedEndpoints().size

    private fun contentArtworkScore(
        track: MatchedTrack,
        wantedTitle: String,
        wantedArtists: List<String>,
        wantedAlbum: String,
        wantedIsrc: String?,
        wantedDurationMs: Long?,
    ): Int =
        scoreTrack(track, wantedTitle, wantedArtists, wantedAlbum, wantedIsrc, wantedDurationMs) - track.qualityScoreBoost()

    private fun selectArtworkCandidates(
        results: JSONArray,
        query: Query,
        exactIsrcOnly: Boolean = false,
    ): List<ArtworkSearchResult> {
        val wantedTitle = query.title.titleMatchNormalized()
        val wantedArtists = query.artists.map { it.normalized() }.filter { it.isNotBlank() }
        val wantedAlbum = query.album.normalized()
        val wantedIsrc = normalizeIsrc(query.isrc)
        val wantedDurationMs = query.durationMs?.takeIf { it > 0L }
        val candidates = mutableListOf<ArtworkSearchResult>()
        for (index in 0 until results.length()) {
            val obj = results.optJSONObject(index) ?: continue
            val track = obj.toMatchedTrack() ?: continue
            if (exactIsrcOnly && normalizeIsrc(track.isrc) != wantedIsrc) continue
            val coverId = track.albumCoverId ?: continue
            val score = contentArtworkScore(track, wantedTitle, wantedArtists, wantedAlbum, wantedIsrc, wantedDurationMs)
            if (score < MIN_MATCH_SCORE) continue
            candidates +=
                ArtworkSearchResult(
                    trackId = track.trackId,
                    releaseId = track.albumId,
                    coverId = coverId,
                    score = score,
                    exactIsrc = wantedIsrc != null && normalizeIsrc(track.isrc) == wantedIsrc,
                )
        }
        return candidates.sortedByDescending { it.score }
    }

    internal fun progressiveDashUri(manifestFile: File): String =
        Uri.Builder()
            .scheme(PROGRESSIVE_DASH_SCHEME)
            .authority("stream")
            .appendQueryParameter("manifest", manifestFile.absolutePath)
            .build()
            .toString()

    internal fun progressiveDashSegmentUrls(manifestText: String): List<String> =
        runCatching { extractDashSegmentUrls(manifestText.sanitizeXmlEntities()) }
            .getOrDefault(emptyList())

    internal fun progressiveDashFlacMetadata(initBytes: ByteArray): ByteArray = extractFlacMetadataBlocks(initBytes)

    internal fun progressiveDashAudioPayload(segmentBytes: ByteArray): ByteArray =
        java.io.ByteArrayOutputStream(segmentBytes.size).use { output ->
            writeMdatPayloads(segmentBytes, output)
            output.toByteArray()
        }

    fun normalizeIsrc(value: String?): String? {
        val compact = value
            ?.uppercase(Locale.US)
            ?.replace(ISRC_STRIP_REGEX, "")
            ?: return null
        return ISRC_PATTERN_REGEX
            .find(compact)
            ?.value
    }

    private fun findCandidateTracks(query: Query): List<MatchedTrack> {
        if (AudioSourceAttemptScope.current()?.isExpired() == true) return emptyList()
        val candidates = mutableListOf<ScoredTrack>()
        val wantedTitle = query.title.titleMatchNormalized()
        val wantedArtists = query.artists.map { it.normalized() }.filter { it.isNotBlank() }
        val wantedAlbum = query.album.normalized()
        val wantedIsrc = normalizeIsrc(query.isrc)
        val wantedDurationMs = query.durationMs?.takeIf { it > 0L }
        wantedIsrc?.let { isrc ->
            searchTracks(isrc, exactIsrc = true)
                ?.let { selectCandidateTracks(it, query, exactIsrcOnly = true) }
                ?.losslessFirst()
                ?.firstOrNull()
                ?.takeIf { it.score >= MIN_MATCH_SCORE }
                ?.let { return listOf(it.track) }
        }

        val terms = searchTerms(query)
        for (term in terms) {
            if (AudioSourceAttemptScope.current()?.isExpired() == true) break
            val results = searchTracks(term) ?: continue
            candidates += selectCandidateTracks(results, query)
            candidates.losslessFirst()
                .firstOrNull()
                ?.takeIf { it.score >= STRONG_MATCH_SCORE && it.track.losslessRank() > 0 }
                ?.let { return listOf(it.track) }
        }
        if (AudioSourceAttemptScope.current()?.isExpired() != true) {
            resolveSongLinkTidalTrackId(query)?.let { tidalId ->
                val track = resolveTrackById(tidalId) ?: query.toDirectMatchedTrack(tidalId)
                val score = scoreTrack(track, wantedTitle, wantedArtists, wantedAlbum, wantedIsrc, wantedDurationMs)
                if (score >= MIN_MATCH_SCORE) {
                    if (track.losslessRank() > 0) {
                        return listOf(track)
                    }
                    candidates += ScoredTrack(track, score)
                    Timber.tag("TidalAudio").w("Deferred lossy song.link TIDAL match $tidalId for ${query.title}: score=$score")
                } else {
                    Timber.tag("TidalAudio").w("Ignored weak song.link TIDAL match $tidalId for ${query.title}: score=$score")
                }
            }
        }
        return candidates
            .groupBy { it.track.trackId }
            .mapNotNull { (_, matches) -> matches.maxByOrNull { it.score } }
            .losslessFirst()
            .map { it.track }
    }

    private fun searchTracks(
        term: String,
        exactIsrc: Boolean = false,
    ): JSONArray? {
        if (term.isBlank()) return null
        searchTracksFromDirectApi(term, exactIsrc)?.takeIf { it.length() > 0 }?.let { return it }
        searchTracksFromAccountPool(term)?.takeIf { it.length() > 0 }?.let { return it }
        return searchTracksFromTidalApi(term)
    }

    private fun searchTracksFromAccountPool(term: String): JSONArray? {
        val accounts = PoolAccountManager.tidalAccounts()
        if (accounts.isEmpty()) return null
        val cacheKey = "pool:${term.lowercase(Locale.US)}"
        val now = System.currentTimeMillis()
        searchCache[cacheKey]?.takeIf { it.expiresAtMs > now }?.let { return it.results }
        for (account in accounts) {
            val url =
                "$API_BASE_URL/search/tracks"
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("countryCode", account.countryCode?.takeIf { it.isNotBlank() } ?: COUNTRY_CODE)
                    .addQueryParameter("locale", LOCALE)
                    .addQueryParameter("deviceType", DEVICE_TYPE)
                    .addQueryParameter("query", term)
                    .addQueryParameter("limit", SEARCH_LIMIT.toString())
                    .addQueryParameter("offset", "0")
                    .build()
            val request =
                Request
                    .Builder()
                    .url(url)
                    .get()
                    .header("Accept", "application/json")
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .header("Authorization", "Bearer ${account.token}")
                    .build()
            val results =
                runCatching {
                    client.newCall(request).withAudioSourceAttemptDeadline().execute().use { response ->
                        if (!response.isSuccessful) return@use null
                        val body = response.body?.string()?.takeIf { it.isNotBlank() } ?: return@use null
                        JSONObject(body).optJSONArray("items")
                    }
                }.onFailure { it.rethrowIfAudioSourceCancelled() }.getOrNull()
            if (results != null && results.length() > 0) {
                searchCache[cacheKey] = CachedSearch(results, now + SEARCH_CACHE_MS)
                PoolAccountManager.noteAccountSuccess("tidal", account.id)
                return results
            }
        }
        return null
    }

    private fun searchTracksFromDirectApi(
        term: String,
        exactIsrc: Boolean,
    ): JSONArray? {
        val cacheKey = "direct:${if (exactIsrc) "isrc" else "query"}:${term.lowercase(Locale.US)}"
        val now = System.currentTimeMillis()
        searchCache[cacheKey]?.takeIf { it.expiresAtMs > now }?.let { return it.results }
        val parameter = if (exactIsrc) "i" else "s"
        for (endpoint in orderedEndpoints().take(MAX_CONCURRENT_TIDAL_ENDPOINTS)) {
            if (AudioSourceAttemptScope.current()?.isExpired() == true) break
            val url =
                endpoint.baseUrl
                    .toHttpUrl()
                    .newBuilder()
                    .addPathSegment("search")
                    .addQueryParameter(parameter, term)
                    .addQueryParameter("limit", SEARCH_LIMIT.toString())
                    .addQueryParameter("offset", "0")
                    .build()
            val request =
                Request
                    .Builder()
                    .url(url)
                    .get()
                    .header("Accept", "application/json")
                    .header("User-Agent", DOWNLOAD_USER_AGENT)
                    .build()

            runCatching {
                client.newCall(request).withAudioSourceAttemptDeadline().execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body.string().takeIf { it.isNotBlank() } ?: return@use null
                    val root = JSONObject(body)
                    root.optJSONObject("data")?.optJSONArray("items")
                        ?: root.optJSONArray("items")
                }
            }.onFailure { it.rethrowIfAudioSourceCancelled() }.getOrNull()?.let { results ->
                searchCache[cacheKey] = CachedSearch(results, now + SEARCH_CACHE_MS)
                return results
            }
        }
        return null
    }

    private fun searchTracksFromTidalApi(term: String): JSONArray? {
        val url =
            "$API_BASE_URL/search/tracks"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("countryCode", COUNTRY_CODE)
                .addQueryParameter("locale", LOCALE)
                .addQueryParameter("deviceType", DEVICE_TYPE)
                .addQueryParameter("query", term)
                .addQueryParameter("limit", SEARCH_LIMIT.toString())
                .addQueryParameter("offset", "0")
                .build()
        val request =
            Request
                .Builder()
                .url(url)
                .get()
                .header("Accept", "application/json")
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("x-tidal-token", PUBLIC_TOKEN)
                .build()

        return runCatching {
            client.newCall(request).withAudioSourceAttemptDeadline().execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body.string().takeIf { it.isNotBlank() } ?: return@use null
                JSONObject(body).optJSONArray("items")
            }
        }.onFailure { it.rethrowIfAudioSourceCancelled() }.getOrNull()
    }

    private fun resolveTrackById(trackId: String): MatchedTrack? {
        val url =
            "$API_BASE_URL/tracks/$trackId"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("countryCode", COUNTRY_CODE)
                .addQueryParameter("locale", LOCALE)
                .addQueryParameter("deviceType", DEVICE_TYPE)
                .build()
        val request =
            Request
                .Builder()
                .url(url)
                .get()
                .header("Accept", "application/json")
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("x-tidal-token", PUBLIC_TOKEN)
                .build()

        return runCatching {
            client.newCall(request).withAudioSourceAttemptDeadline().execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body.string().takeIf { it.isNotBlank() } ?: return@use null
                JSONObject(body).toMatchedTrack()
            }
        }.onFailure { it.rethrowIfAudioSourceCancelled() }.getOrNull()
    }

    private fun selectCandidateTracks(
        results: JSONArray,
        query: Query,
        exactIsrcOnly: Boolean = false,
    ): List<ScoredTrack> {
        val wantedTitle = query.title.titleMatchNormalized()
        val wantedArtists = query.artists.map { it.normalized() }.filter { it.isNotBlank() }
        val wantedAlbum = query.album.normalized()
        val wantedIsrc = normalizeIsrc(query.isrc)
        val wantedDurationMs = query.durationMs?.takeIf { it > 0L }
        val candidates = mutableListOf<ScoredTrack>()
        for (index in 0 until results.length()) {
            val obj = results.optJSONObject(index) ?: continue
            val track = obj.toMatchedTrack() ?: continue
            if (exactIsrcOnly && normalizeIsrc(track.isrc) != wantedIsrc) continue
            val score = scoreTrack(track, wantedTitle, wantedArtists, wantedAlbum, wantedIsrc, wantedDurationMs)
            if (score >= MIN_MATCH_SCORE) {
                candidates += ScoredTrack(track, score)
            }
        }
        return candidates.losslessFirst()
    }

    private fun List<ScoredTrack>.losslessFirst(): List<ScoredTrack> =
        sortedWith(
            compareByDescending<ScoredTrack> { it.track.losslessRank() }
                .thenByDescending { it.score },
        )

    private fun scoreTrack(
        track: MatchedTrack,
        wantedTitle: String,
        wantedArtists: List<String>,
        wantedAlbum: String,
        wantedIsrc: String?,
        wantedDurationMs: Long?,
    ): Int {
        val candidateTitle = track.title.titleMatchNormalized()
        val candidateArtists = track.artistNames.map { it.normalized() }.filter { it.isNotBlank() }
        val candidateAlbum = track.album.normalized()
        val candidateIsrc = normalizeIsrc(track.isrc)

        if (wantedTitle.isBlank() || candidateTitle.isBlank()) return REJECT_SCORE
        if (wantedIsrc != null && candidateIsrc == wantedIsrc) {
            return if (TrackMatching.durationMatches(wantedDurationMs, track.durationMs)) {
                220 + track.qualityScoreBoost()
            } else {
                REJECT_SCORE
            }
        }
        if (hasVersionMismatch(wantedTitle, candidateTitle)) return REJECT_SCORE

        val wantedTitleTokens = significantTokens(wantedTitle)
        val candidateTitleTokens = significantTokens(candidateTitle)
        var score = 0
        when {
            candidateTitle == wantedTitle -> score += 110
            candidateTitle.contains(wantedTitle) || wantedTitle.contains(candidateTitle) -> score += 62
            else -> {
                val titleOverlap = tokenOverlap(wantedTitleTokens, candidateTitleTokens)
                if (titleOverlap < 0.50) return REJECT_SCORE
                score += (titleOverlap * 48).roundToInt()
            }
        }
        val extraCandidateTitleTokens = candidateTitleTokens - wantedTitleTokens
        score -= (extraCandidateTitleTokens.size * 6).coerceAtMost(24)

        if (wantedArtists.isNotEmpty() && candidateArtists.isNotEmpty()) {
            val artistHit = wantedArtists.any { wanted ->
                candidateArtists.any { candidate ->
                    candidate == wanted || candidate.contains(wanted) || wanted.contains(candidate)
                }
            }
            if (artistHit) {
                score += 38
            } else {
                return REJECT_SCORE
            }
        }

        if (wantedAlbum.isNotBlank() && candidateAlbum.isNotBlank()) {
            score += when {
                candidateAlbum == wantedAlbum -> 18
                candidateAlbum.contains(wantedAlbum) || wantedAlbum.contains(candidateAlbum) -> 10
                else -> 0
            }
        }

        if (wantedIsrc != null) {
            score += when {
                candidateIsrc == wantedIsrc -> 160
                candidateIsrc != null -> -30
                else -> 0
            }
        }

        val candidateDurationMs = track.durationMs
        if (wantedDurationMs != null && candidateDurationMs != null) {
            val diffSeconds = abs(wantedDurationMs - candidateDurationMs) / 1000L
            if (diffSeconds > 45) return REJECT_SCORE
            score += when {
                diffSeconds <= 3 -> 36
                diffSeconds <= 8 -> 18
                diffSeconds <= 20 -> 4
                else -> -50
            }
        }

        score += track.qualityScoreBoost()

        return score
    }

    private suspend fun requestDirectFlac(
        track: MatchedTrack,
        quality: String,
        durationMs: Long?,
        now: Long,
        cacheDir: File?,
        preferLiveDash: Boolean,
        audioQuality: TidalAudioQuality,
    ): Resolved {
        val isAtmosRequest = quality.equals("DOLBY_ATMOS", ignoreCase = true)
        val manifestFormats = quality.tidalManifestFormats()
        val errors = mutableListOf<String>()
        var rateLimitCount = 0
        var longestRetryAfterMs = 0L

        val endpoints = orderedEndpoints().take(MAX_CONCURRENT_TIDAL_ENDPOINTS)

        return runBlocking(Dispatchers.IO + AudioSourceAttemptScope.coroutineContextElement()) {
            supervisorScope {
                val results = Channel<Pair<TidalDownloadEndpoint, Result<Resolved>>>(endpoints.size.coerceAtLeast(1))
                val jobs = endpoints.map { endpoint ->
                    launch {
                        val result: Result<Resolved> =
                            try {
                                runInterruptible(Dispatchers.IO) {
                                    Result.success(
                                        requestDirectFlacFromEndpoint(
                                            endpoint = endpoint,
                                            isAtmosRequest = isAtmosRequest,
                                            manifestFormats = manifestFormats,
                                            track = track,
                                            quality = quality,
                                            durationMs = durationMs,
                                            now = now,
                                            cacheDir = cacheDir,
                                            preferLiveDash = preferLiveDash,
                                            audioQuality = audioQuality,
                                        ),
                                    )
                                }
                            } catch (cancel: CancellationException) {
                                throw cancel
                            } catch (error: Throwable) {
                                error.rethrowIfAudioSourceCancelled()
                                Result.failure(error)
                            }
                        results.send(endpoint to result)
                    }
                }

                var deferredAacFallback: Resolved? = null
                try {
                    repeat(endpoints.size) {
                        val (endpoint, result) = results.receive()
                        val resolved = result.getOrNull()
                        if (resolved != null) {
                            markInstanceHealthy(endpoint.baseUrl)
                            lastResolvedTrackId = track.trackId
                            if (resolved.losslessDowngradedBitrateKbps == null) {
                                jobs.forEach { it.cancel() }
                                return@supervisorScope resolved
                            }
                            if (deferredAacFallback == null) deferredAacFallback = resolved
                            return@repeat
                        }

                        val error = result.exceptionOrNull() ?: TidalAudioResolutionException("unknown mirror failure")
                        if (AudioSourceAttemptScope.current()?.isExpired() == true) {
                            errors += "${endpoint.name}: source attempt deadline exceeded"
                        } else {
                            if (error is TidalRateLimitedException) {
                                rateLimitCount += 1
                                longestRetryAfterMs = maxOf(longestRetryAfterMs, error.retryAfterMs)
                            } else {
                                val hardFailure =
                                    error is java.net.UnknownHostException || error is java.net.ConnectException
                                markInstanceFailed(endpoint.baseUrl, hardFailure = hardFailure)
                            }
                            errors += "${endpoint.name}: ${error.message ?: error.javaClass.simpleName}"
                            Timber.tag("TidalAudio").w(error, "TIDAL resolver ${endpoint.name} failed for ${track.trackId}")
                        }
                    }
                } finally {
                    jobs.forEach { it.cancel() }
                    results.close()
                }

                deferredAacFallback?.let { return@supervisorScope it }

                if (rateLimitCount == endpoints.size && longestRetryAfterMs > 0L) {
                    resolverRateLimitedUntilMs = System.currentTimeMillis() + longestRetryAfterMs
                    throw TidalRateLimitedException(longestRetryAfterMs)
                }
                throw TidalAudioResolutionException(
                    "TIDAL resolver failed on all mirrors for ${track.title}: ${errors.joinToString(" | ").take(720)}",
                )
            }
        }
    }

    private fun requestDirectFlacFromEndpoint(
        endpoint: TidalDownloadEndpoint,
        isAtmosRequest: Boolean,
        manifestFormats: List<String>?,
        track: MatchedTrack,
        quality: String,
        durationMs: Long?,
        now: Long,
        cacheDir: File?,
        preferLiveDash: Boolean,
        audioQuality: TidalAudioQuality,
    ): Resolved =
        try {
            requestDirectFlacFromEndpointDialect(
                endpoint = endpoint,
                isAtmosRequest = isAtmosRequest,
                manifestFormats = manifestFormats,
                track = track,
                quality = quality,
                durationMs = durationMs,
                now = now,
                cacheDir = cacheDir,
                preferLiveDash = preferLiveDash,
                audioQuality = audioQuality,
            )
        } catch (unsupported: TidalDialectUnsupportedException) {
            Timber.tag("TidalAudio").d(
                "%s has no /trackManifests/ (%s); retrying on the /track/ dialect",
                endpoint.name,
                unsupported.message,
            )
            requestDirectFlacFromEndpointDialect(
                endpoint = endpoint,
                isAtmosRequest = isAtmosRequest,

                manifestFormats = null,
                track = track,
                quality = quality,
                durationMs = durationMs,
                now = now,
                cacheDir = cacheDir,
                preferLiveDash = preferLiveDash,
                audioQuality = audioQuality,
            )
        }

    private fun requestDirectFlacFromEndpointDialect(
        endpoint: TidalDownloadEndpoint,
        isAtmosRequest: Boolean,
        manifestFormats: List<String>?,
        track: MatchedTrack,
        quality: String,
        durationMs: Long?,
        now: Long,
        cacheDir: File?,
        preferLiveDash: Boolean,
        audioQuality: TidalAudioQuality,
    ): Resolved {
        val url = if (manifestFormats != null) {
            endpoint.baseUrl
                .toHttpUrl()
                .newBuilder()
                .addPathSegment("trackManifests")
                .addPathSegment("")
                .addQueryParameter("id", track.trackId)
                .apply {
                    manifestFormats.forEach { format ->
                        addQueryParameter("formats", format)
                    }
                }
                .addQueryParameter("adaptive", "false")
                .addQueryParameter("manifestType", "MPEG_DASH")
                .addQueryParameter("uriScheme", "HTTPS")
                .addQueryParameter("usage", "PLAYBACK")
                .build()
        } else {
            endpoint.baseUrl
                .toHttpUrl()
                .newBuilder()
                .addPathSegment("track")
                .addPathSegment("")
                .addQueryParameter("id", track.trackId)
                .addQueryParameter("quality", quality)
                .build()
        }
        val request =
            Request
                .Builder()
                .url(url)
                .get()
                .header("Accept", "application/json")
                .header("User-Agent", DOWNLOAD_USER_AGENT)
                .build()

        client.newCall(request).withAudioSourceAttemptDeadline().execute().use { response ->
            val responseBody = response.body.string()
            if (response.code == 429) {
                val headerRetryAfterMs = response.header("Retry-After")
                    ?.toLongOrNull()
                    ?.times(1000L)
                    ?.coerceAtLeast(60_000L)
                val bodyRetryAfterMs = runCatching {
                    JSONObject(responseBody)
                        .optLong("retry_after")
                        .takeIf { it > 0L }
                        ?.times(1000L)
                }.getOrNull()
                val retryAfterMs = headerRetryAfterMs
                    ?: bodyRetryAfterMs
                    ?: RATE_LIMIT_COOLDOWN_MS
                throw TidalRateLimitedException(retryAfterMs)
            }
            if (!response.isSuccessful) {
                val detail = "TIDAL ${endpoint.name} HTTP ${response.code}: ${responseBody.take(180)}"

                if (response.code == 404 && manifestFormats != null) {
                    throw TidalDialectUnsupportedException(detail)
                }
                throw TidalAudioResolutionException(detail)
            }
            val root = JSONObject(responseBody)
            val data = root.optJSONObject("data")
                ?: throw TidalAudioResolutionException("TIDAL FLAC resolver returned no data")
            val effectiveDurationMs = durationMs ?: track.durationMs
            val resolvedQuality: String
            val manifest: ParsedManifest
            val dataSampleRate: Int?
            val dataBitDepth: Int?
            if (manifestFormats != null) {
                val attributes = data
                    .optJSONObject("data")
                    ?.optJSONObject("attributes")
                    ?: throw TidalAudioResolutionException("TIDAL manifest payload missing attributes")

                val presentation = attributes.stringOrNull("trackPresentation")
                    ?: attributes.stringOrNull("assetPresentation")
                if (presentation.equals("PREVIEW", ignoreCase = true)) {
                    val reason = attributes.stringOrNull("previewReason").orEmpty()
                    throw TidalAudioResolutionException(
                        "TIDAL instance returned a PREVIEW (unsubscribed account)${if (reason.isNotBlank()) ": $reason" else ""}",
                    )
                }
                val formats = attributes.optJSONArray("formats")
                val returnedFormats =
                    formats
                        ?.let { array ->
                            (0 until array.length())
                                .mapNotNull { index -> array.optString(index).takeIf(String::isNotBlank) }
                        }
                        .orEmpty()
                if (returnedFormats.none { returned -> manifestFormats.any { it.equals(returned, ignoreCase = true) } }) {
                    throw TidalAudioResolutionException("TIDAL API did not report ${manifestFormats.joinToString()} for this track")
                }
                val manifestUrl = attributes.stringOrNull("uri")
                    ?: throw TidalAudioResolutionException("TIDAL manifest URI was empty")
                val manifestText = fetchText(manifestUrl)
                manifest = parseManifestText(manifestText, "audio/mp4")
                resolvedQuality = returnedFormats.toResolvedTidalQuality(quality)
                dataSampleRate = manifest.sampleRate ?: 48_000
                dataBitDepth = null
            } else {
                if (data.optString("assetPresentation").equals("PREVIEW", ignoreCase = true)) {
                    throw TidalAudioResolutionException("TIDAL FLAC resolver returned a preview asset")
                }
                val manifestPayload = data.stringOrNull("manifest")
                    ?: throw TidalAudioResolutionException("TIDAL FLAC resolver returned no manifest")
                resolvedQuality = data.stringOrNull("audioQuality") ?: quality
                manifest = parseManifest(manifestPayload, data.stringOrNull("manifestMimeType"), effectiveDurationMs)
                dataSampleRate = normalizeSampleRate(data.doubleOrNull("sampleRate"))
                dataBitDepth = data.longOrNull("bitDepth")?.toInt()
            }
            val manifestLooksFlac =
                manifest.mimeType.contains("flac", ignoreCase = true) ||
                    manifest.codecs.contains("flac", ignoreCase = true)
            val isAtmosManifest =
                isAtmosRequest ||
                    resolvedQuality.contains("ATMOS", ignoreCase = true) ||
                    manifest.codecs.contains("eac3", ignoreCase = true) ||
                    manifest.codecs.contains("ec-3", ignoreCase = true)
            val returnedHiRes =
                resolvedQuality.contains("HI_RES", ignoreCase = true) ||
                    resolvedQuality.contains("HIRES", ignoreCase = true) ||
                    resolvedQuality.contains("MAX", ignoreCase = true) ||
                    (dataBitDepth ?: 0) >= 24 ||
                    (dataSampleRate ?: manifest.sampleRate ?: 0) > 48_000
            if (
                audioQuality == TidalAudioQuality.HI_RES_LOSSLESS &&
                quality.isLosslessQuality() &&
                !returnedHiRes
            ) {
                throw TidalAudioResolutionException(
                    "TIDAL HiRes was unavailable for ${track.title}; resolver returned ${resolvedQuality.ifBlank { quality }}",
                )
            }

            val progressiveDashFile = if (preferLiveDash && manifest.isDash && manifestLooksFlac && cacheDir != null) {
                writeDashManifestToTempFile(track.trackId, quality, manifest, cacheDir)
            } else {
                null
            }
            val localFile = if (progressiveDashFile == null && manifest.isDash && manifestLooksFlac && cacheDir != null) {
                downloadDashFlacToTempFile(track.trackId, quality, manifest, cacheDir)
            } else if (progressiveDashFile == null && manifest.isDash && cacheDir != null) {
                downloadManifestToTempFile(track.trackId, quality, manifest, cacheDir)
            } else {
                null
            }
            val liveManifestFile = if (progressiveDashFile == null && localFile == null && preferLiveDash && manifest.isDash && cacheDir != null) {
                writeDashManifestToTempFile(track.trackId, quality, manifest, cacheDir)
            } else {
                null
            }
            val streamUri = progressiveDashFile
                ?.let { progressiveDashUri(it) }
                ?: liveManifestFile
                ?.let { Uri.fromFile(it).toString() }
                ?: localFile?.let { Uri.fromFile(it).toString() }
                ?: manifest.url
            val playbackStreamInfo = localFile
                ?.let { inspectLocalPlaybackFile(it, manifest) }
                ?: if (progressiveDashFile == null && liveManifestFile == null && !manifest.isDash) {
                    inspectRemotePlaybackUrl(manifest.url, manifest)
                } else {
                    null
                }
            val resolvedCodecs = playbackStreamInfo?.codecs
                ?: manifest.codecs.takeIf { it.isNotBlank() }
                ?: when {
                    manifestLooksFlac -> "flac"
                    isAtmosManifest -> "ec-3"
                    else -> "mp4a.40.2"
                }
            val playbackMimeType = if (progressiveDashFile != null) {
                AUDIO_FLAC_MIME_TYPE
            } else if (liveManifestFile != null) {
                DASH_MIME_TYPE
            } else {
                playbackStreamInfo?.mimeType ?: manifest.mimeType.coerceDirectPlaybackMimeType(manifest.url)
            }
            val isResolvedFlacLike =
                playbackMimeType.contains("flac", ignoreCase = true) ||
                    resolvedCodecs.contains("flac", ignoreCase = true)
            val isLosslessDowngrade = quality.isLosslessQuality() && !isResolvedFlacLike && !isAtmosManifest
            if (quality.equals("HI_RES_LOSSLESS", ignoreCase = true) && isLosslessDowngrade) {
                throw TidalAudioResolutionException(
                    "TIDAL $quality did not return FLAC (${playbackMimeType}, ${resolvedCodecs})",
                )
            }
            val streamMetadata = when {
                progressiveDashFile != null -> StreamMetadata(AUDIO_FLAC_MIME_TYPE, null)
                liveManifestFile != null -> StreamMetadata(playbackMimeType, null)
                playbackStreamInfo != null -> StreamMetadata(
                    playbackStreamInfo.mimeType,
                    playbackStreamInfo.contentLength,
                )
                manifest.isDash -> throw TidalAudioResolutionException("TIDAL DASH manifest needs live DASH playback")
                else -> fetchStreamMetadata(manifest.url)
            }
            val sampleRate = dataSampleRate
                ?: manifest.sampleRate
                ?: normalizeSampleRate(track.sampleRate?.toDouble())
            val bitDepth = dataBitDepth ?: manifest.bitDepth
            val bitrate = manifest.bitrate ?: estimateBitrate(streamMetadata.contentLength, effectiveDurationMs)
            val resolvedContentLength =
                streamMetadata.contentLength
                    ?: localFile?.length()?.takeIf { it > 0L }
                    ?: bitrate?.takeIf { it > 0 }
                        ?.let { bps -> effectiveDurationMs?.takeIf { it > 0 }?.let { bps * it / 8_000L } }
            validateLikelyFullTrack(track, effectiveDurationMs, streamMetadata.contentLength)
            val resolvedLabel = when {
                isAtmosManifest -> "TIDAL Atmos"
                isLosslessDowngrade -> "TIDAL AAC"
                resolvedQuality.contains("HI_RES", ignoreCase = true) -> "TIDAL HiRes FLAC"
                quality.isLossyQuality() || resolvedQuality.equals("HIGH", ignoreCase = true) -> "TIDAL AAC"
                else -> "TIDAL FLAC"
            }
            val losslessDowngradedBitrateKbps =
                if (isLosslessDowngrade) ((bitrate ?: 320_000) / 1000).coerceAtLeast(1) else null
            Timber.tag("TidalAudio").i(
                "Resolved TIDAL ${resolvedQuality.ifBlank { quality }} stream for ${track.trackId}: label=$resolvedLabel, local=${localFile != null}, progressive=${progressiveDashFile != null}, dash=${manifest.isDash}, bitrate=$bitrate, sampleRate=$sampleRate",
            )

            return Resolved(
                mediaUri = streamUri,
                trackId = track.trackId,
                label = resolvedLabel,
                mimeType = playbackMimeType,
                codecs = resolvedCodecs,
                bitrate = bitrate ?: if (isLosslessDowngrade) 320_000 else 0,
                sampleRate = sampleRate,
                bitDepth = bitDepth,
                contentLength = resolvedContentLength,
                expiresAtMs = (progressiveDashFile ?: localFile ?: liveManifestFile)?.let { now + STREAM_CACHE_MS }
                    ?: extractExpiryMs(manifest.expiryUrl ?: manifest.url, now),
                losslessDowngradedBitrateKbps = losslessDowngradedBitrateKbps,
                isLiveManifest = progressiveDashFile != null || liveManifestFile != null,
            )
        }
    }

    private fun streamQualityCandidates(
        track: MatchedTrack,
        preferAtmos: Boolean,
        audioQuality: TidalAudioQuality,
    ): List<String> =
        buildList {
            if (preferAtmos && audioQuality != TidalAudioQuality.AAC_320) {
                add("DOLBY_ATMOS")
            }
            when (audioQuality) {
                TidalAudioQuality.AAC_320 -> add("HIGH")
                TidalAudioQuality.FLAC -> {
                    if (track.supportsHiResLossless() || track.supportsLossless()) {
                        add("LOSSLESS")
                    }
                    add("HIGH")
                }
                TidalAudioQuality.HI_RES_LOSSLESS -> {
                    if (track.supportsHiResLossless()) {
                        add("HI_RES_LOSSLESS")
                    }
                    if (track.supportsHiResLossless() || track.supportsLossless()) {
                        add("LOSSLESS")
                    }
                    add("HIGH")
                }
            }
        }.distinct()

    private fun String.tidalManifestFormats(): List<String>? =
        when {
            equals("DOLBY_ATMOS", ignoreCase = true) -> listOf("EAC3_JOC")
            equals("HI_RES_LOSSLESS", ignoreCase = true) ||
                equals("HI_RES", ignoreCase = true) -> listOf("FLAC_HIRES")
            equals("LOSSLESS", ignoreCase = true) -> listOf("FLAC")
            else -> null
        }

    private fun List<String>.toResolvedTidalQuality(requestedQuality: String): String =
        when {
            any { it.equals("EAC3_JOC", ignoreCase = true) } -> "DOLBY_ATMOS"
            any { it.equals("FLAC_HIRES", ignoreCase = true) } -> "HI_RES_LOSSLESS"
            any { it.equals("FLAC", ignoreCase = true) } -> "LOSSLESS"
            else -> requestedQuality
        }

    private fun String.isLosslessQuality(): Boolean =
        equals("LOSSLESS", ignoreCase = true) ||
            equals("HI_RES_LOSSLESS", ignoreCase = true) ||
            equals("HI_RES", ignoreCase = true)

    private fun String.isLossyQuality(): Boolean =
        equals("HIGH", ignoreCase = true) ||
            equals("LOW", ignoreCase = true)

    fun resolveAccountManifest(
        manifestB64: String,
        declaredMimeType: String?,
        trackId: String,
        quality: String,
        durationMs: Long?,
        cacheDir: File,
        preferLiveDash: Boolean = false,
    ): DirectStream? =
        runCatching {
            val manifest = parseManifest(manifestB64, declaredMimeType, durationMs)
            if (!manifest.isDash) {
                return@runCatching DirectStream(
                    uri = manifest.url,
                    mimeType = manifest.mimeType,
                    codecs = manifest.codecs,
                    contentLength = null,
                    sampleRate = manifest.sampleRate,
                    bitDepth = manifest.bitDepth,
                    bitrate = manifest.bitrate,
                    label = "account $quality",
                    source = AudioSourceType.TIDAL,
                )
            }

            val looksFlac =
                manifest.mimeType.contains("flac", ignoreCase = true) ||
                    manifest.codecs.contains("flac", ignoreCase = true)
            if (preferLiveDash && looksFlac) {
                val manifestFile = writeDashManifestToTempFile(trackId, quality, manifest, cacheDir)
                return@runCatching DirectStream(
                    uri = progressiveDashUri(manifestFile),
                    mimeType = AUDIO_FLAC_MIME_TYPE,
                    codecs = manifest.codecs.takeIf { it.isNotBlank() } ?: "flac",
                    contentLength = null,
                    sampleRate = manifest.sampleRate,
                    bitDepth = manifest.bitDepth,
                    bitrate = manifest.bitrate,
                    label = "account $quality",
                    source = AudioSourceType.TIDAL,
                )
            }
            val localFile =
                if (looksFlac && manifest.segmentUrls.size >= 2) {
                    downloadDashFlacToTempFile(trackId, quality, manifest, cacheDir)
                } else {
                    downloadManifestToTempFile(trackId, quality, manifest, cacheDir)
                }
            val isFlacOut = localFile.extension.equals("flac", ignoreCase = true)
            DirectStream(
                uri = Uri.fromFile(localFile).toString(),
                mimeType = if (isFlacOut) AUDIO_FLAC_MIME_TYPE else manifest.mimeType,
                codecs = if (isFlacOut) "flac" else manifest.codecs,
                contentLength = localFile.length().takeIf { it > 0L },
                sampleRate = manifest.sampleRate,
                bitDepth = manifest.bitDepth,
                bitrate = manifest.bitrate,
                label = "account $quality",
                source = AudioSourceType.TIDAL,
            )
        }.onFailure {
            Timber.tag("TidalAudioProvider").w(it, "account manifest resolve failed")
        }.getOrNull()

    private fun parseManifest(
        manifestB64: String,
        declaredMimeType: String?,
        durationMs: Long?,
    ): ParsedManifest {
        val text =
            String(
                Base64.decode(manifestB64.trim(), Base64.DEFAULT),
                Charsets.UTF_8,
            ).trim()
        return parseManifestText(text, declaredMimeType, durationMs)
    }

    private fun parseManifestText(
        text: String,
        declaredMimeType: String?,
        durationMs: Long? = null,
    ): ParsedManifest {
        val cleanText = text.trim()
        if (cleanText.isBlank()) {
            throw TidalAudioResolutionException("TIDAL FLAC manifest was empty")
        }
        if (cleanText.startsWith("{")) {
            val root = JSONObject(cleanText)
            val urls = root.optJSONArray("urls")
                ?: throw TidalAudioResolutionException("TIDAL FLAC manifest had no URLs")
            val directUrl = urls.firstStringOrNull()
                ?: throw TidalAudioResolutionException("TIDAL FLAC manifest URL was empty")
            val rawMimeType = root.stringOrNull("mimeType") ?: AUDIO_FLAC_MIME_TYPE
            val codecs = root.stringOrNull("codecs") ?: if (rawMimeType.contains("flac", ignoreCase = true)) "flac" else "mp4a.40.2"
            val mimeType = rawMimeType.coerceDirectPlaybackMimeType(directUrl)
            val sampleRate = normalizeSampleRate(root.doubleOrNull("sampleRate"))
            val bitDepth = root.longOrNull("bitDepth")?.toInt()
            val bitrate = root.longOrNull("bitrate")?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt()
                ?: root.longOrNull("bandwidth")?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt()
            return ParsedManifest(
                url = directUrl,
                mimeType = mimeType,
                codecs = codecs,
                sampleRate = sampleRate,
                bitDepth = bitDepth,
                bitrate = bitrate,
                expiryUrl = directUrl,
                isDash = false,
                outputExtension = if (mimeType.contains("flac", ignoreCase = true) || codecs.contains("flac", ignoreCase = true)) {
                    ".flac"
                } else {
                    ".m4a"
                },
            )
        }
        if (cleanText.contains("<MPD", ignoreCase = true)) {
            return parseDashManifest(cleanText.sanitizeXmlEntities(), declaredMimeType)
        }
        throw TidalAudioResolutionException("TIDAL FLAC manifest format was not recognized")
    }

    private fun parseDashManifest(
        text: String,
        declaredMimeType: String?,
    ): ParsedManifest {
        if (
            !text.contains("<SegmentTemplate", ignoreCase = true) &&
            !text.contains("<SegmentList", ignoreCase = true) &&
            !text.contains("<SegmentBase", ignoreCase = true) &&
            !text.contains("<BaseURL", ignoreCase = true)
        ) {
            throw TidalAudioResolutionException("TIDAL DASH manifest had no playable segments")
        }
        val codecs = firstXmlAttr(text, "codecs").ifBlank { "flac" }
        val representationMime = firstXmlAttr(text, "mimeType")
        val sampleRate = firstXmlAttr(text, "audioSamplingRate").toIntOrNull()
        val bitDepth = firstXmlAttr(text, "bitDepth").toIntOrNull()
        val bandwidth = xmlAttrValues(text, "bandwidth").maxOrNull()
        val segmentUrls = extractDashSegmentUrls(text)
        val firstSegmentUrl = HTTP_URL_REGEX
            .find(text)
            ?.value
            ?.xmlUnescape()
        return ParsedManifest(
            url = "",
            mimeType = representationMime.ifBlank { declaredMimeType ?: "audio/mp4" },
            codecs = codecs,
            sampleRate = sampleRate,
            bitDepth = bitDepth,
            bitrate = bandwidth,
            expiryUrl = firstSegmentUrl,
            isDash = true,
            segmentUrls = segmentUrls,
            outputExtension = ".m4a",
            manifestText = text,
        )
    }

    private fun String.coerceDirectPlaybackMimeType(url: String): String {
        if (!contains("dash", ignoreCase = true)) return this
        return when {
            url.endsWith(".flac", ignoreCase = true) -> AUDIO_FLAC_MIME_TYPE
            else -> AUDIO_MP4_MIME_TYPE
        }
    }

    private fun extractDashSegmentUrls(text: String): List<String> {
        extractSegmentListUrls(text).takeIf { it.isNotEmpty() }?.let { return it }

        val baseUrl = firstXmlTagValue(text, "BaseURL").xmlUnescape()
        val init = firstXmlAttr(text, "initialization").xmlUnescape()
        val mediaTemplate = firstXmlAttr(text, "media").xmlUnescape()
        if (init.isBlank() || mediaTemplate.isBlank()) {
            val directBase = baseUrl.takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
            if (directBase != null) return listOf(directBase)
            throw TidalAudioResolutionException("TIDAL DASH manifest had no initialization/media templates")
        }

        var segmentCount = 0
        DASH_SEGMENT_TIMELINE_REGEX
            .findAll(text)
            .forEach { match ->
                val repeat = match.groupValues.getOrNull(2)?.toIntOrNull()?.takeIf { it > 0 } ?: 0
                segmentCount += repeat + 1
            }
        if (segmentCount <= 0) {
            throw TidalAudioResolutionException("TIDAL DASH manifest listed no media segments")
        }

        return buildList {
            add(resolveDashUrl(baseUrl, init))
            for (index in 1..segmentCount) {
                add(resolveDashUrl(baseUrl, mediaTemplate.replace("\$Number\$", index.toString())))
            }
        }
    }

    private fun extractSegmentListUrls(text: String): List<String> {
        val baseUrl = firstXmlTagValue(text, "BaseURL").xmlUnescape()
        val initialization =
            DASH_INITIALIZATION_URL_REGEX
                .find(text)
                ?.groupValues
                ?.getOrNull(1)
                ?.xmlUnescape()
        val mediaSegments =
            DASH_SEGMENT_URL_REGEX
                .findAll(text)
                .mapNotNull { it.groupValues.getOrNull(1)?.xmlUnescape() }
                .toList()
        return buildList {
            initialization?.let { add(resolveDashUrl(baseUrl, it)) }
            mediaSegments.forEach { add(resolveDashUrl(baseUrl, it)) }
        }
    }

    private fun resolveDashUrl(
        baseUrl: String,
        path: String,
    ): String {
        if (path.startsWith("http://", ignoreCase = true) || path.startsWith("https://", ignoreCase = true)) {
            return path
        }
        if (baseUrl.isBlank()) return path
        val absoluteBase = baseUrl.takeIf {
            it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
        } ?: return path
        return absoluteBase.toHttpUrlOrNull()
            ?.resolve(path)
            ?.toString()
            ?: absoluteBase.trimEnd('/') + "/" + path.trimStart('/')
    }

    private fun downloadDashFlacToTempFile(
        trackId: String,
        quality: String,
        manifest: ParsedManifest,
        cacheDir: File,
    ): File {
        val urls = manifest.segmentUrls
        if (urls.size < 2 || urls.any { it.isBlank() }) {
            throw TidalAudioResolutionException("TIDAL HiRes DASH manifest did not list FLAC segments")
        }

        val tidalDir = File(cacheDir, "tidal-temp").apply { mkdirs() }
        val outputFile = File(
            tidalDir,
            "$trackId-${quality.lowercase(Locale.US)}-${System.currentTimeMillis()}.flac",
        )

        try {
            val initBytes = downloadSegmentBytes(urls.first(), 1, urls.size)
            val flacMetadataBlocks = extractFlacMetadataBlocks(initBytes)
            outputFile.outputStream().buffered().use { out ->
                out.write(byteArrayOf('f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte()))
                out.write(flacMetadataBlocks)
                urls.drop(1).forEachIndexed { index, url ->
                    val segmentBytes = downloadSegmentBytes(url, index + 2, urls.size)
                    writeMdatPayloads(segmentBytes, out)
                }
            }
        } catch (error: Throwable) {
            outputFile.delete()
            if (error is TidalAudioResolutionException) throw error
            throw TidalAudioResolutionException("TIDAL HiRes FLAC remux failed: ${error.message}", error)
        }

        if (outputFile.length() <= 42L) {
            outputFile.delete()
            throw TidalAudioResolutionException("TIDAL HiRes FLAC remux produced an empty file")
        }
        return outputFile
    }

    private fun downloadSegmentBytes(
        url: String,
        index: Int,
        total: Int,
    ): ByteArray {
        val request =
            Request
                .Builder()
                .url(url)
                .get()
                .header("Accept", "audio/mp4,audio/*,*/*;q=0.8")
                .header("Accept-Encoding", "identity")
                .header("User-Agent", BROWSER_USER_AGENT)
                .build()
        client.newCall(request).withAudioSourceAttemptDeadline().execute().use { response ->
            if (!response.isSuccessful) {
                throw TidalAudioResolutionException("TIDAL segment $index/$total HTTP ${response.code}")
            }
            return response.body.bytes()
        }
    }

    private fun extractFlacMetadataBlocks(initBytes: ByteArray): ByteArray {
        val typeOffset = initBytes.indexOfAscii("dfLa")
        if (typeOffset < 4) {
            throw TidalAudioResolutionException("TIDAL HiRes init segment had no FLAC metadata")
        }
        val boxStart = typeOffset - 4
        val boxSize = initBytes.readMp4BoxSize(boxStart)
        val boxEnd = boxStart + boxSize
        val payloadStart = typeOffset + 8
        if (boxSize < 16 || payloadStart + 4 > boxEnd || boxEnd > initBytes.size) {
            throw TidalAudioResolutionException("TIDAL HiRes FLAC metadata box was invalid")
        }
        val metadataBlocks = initBytes.copyOfRange(payloadStart, boxEnd)
        val firstBlockType = metadataBlocks[0].toInt() and 0x7f
        val firstBlockLength =
            ((metadataBlocks[1].toInt() and 0xff) shl 16) or
                ((metadataBlocks[2].toInt() and 0xff) shl 8) or
                (metadataBlocks[3].toInt() and 0xff)
        if (firstBlockType != 0 || firstBlockLength != 34 || metadataBlocks.size < 4 + firstBlockLength) {
            throw TidalAudioResolutionException("TIDAL HiRes FLAC STREAMINFO was invalid")
        }
        return metadataBlocks
    }

    private fun writeMdatPayloads(
        bytes: ByteArray,
        out: OutputStream,
    ) {
        var foundMdat = false
        var offset = 0
        while (offset + MP4_BOX_HEADER_SIZE <= bytes.size) {
            val size = bytes.readMp4BoxSize(offset)
            val type = bytes.readMp4BoxType(offset + 4)
            val headerSize = if (bytes.readUInt32(offset) == 1L) MP4_EXTENDED_BOX_HEADER_SIZE else MP4_BOX_HEADER_SIZE
            val boxEnd = offset + size
            if (size < headerSize || boxEnd > bytes.size) {
                throw TidalAudioResolutionException("TIDAL HiRes segment had an invalid MP4 box")
            }
            if (type == "mdat") {
                out.write(bytes, offset + headerSize, size - headerSize)
                foundMdat = true
            }
            offset = boxEnd
        }
        if (!foundMdat) {
            throw TidalAudioResolutionException("TIDAL HiRes segment had no audio payload")
        }
    }

    private fun ByteArray.indexOfAscii(value: String): Int {
        val needle = value.toByteArray(Charsets.US_ASCII)
        if (needle.isEmpty() || size < needle.size) return -1
        for (index in 0..size - needle.size) {
            var matches = true
            for (needleIndex in needle.indices) {
                if (this[index + needleIndex] != needle[needleIndex]) {
                    matches = false
                    break
                }
            }
            if (matches) return index
        }
        return -1
    }

    private fun ByteArray.readMp4BoxSize(offset: Int): Int {
        val size32 = readUInt32(offset)
        val size = when (size32) {
            0L -> this.size.toLong() - offset.toLong()
            1L -> readUInt64(offset + 8)
            else -> size32
        }
        if (size <= 0L || size > Int.MAX_VALUE) {
            throw TidalAudioResolutionException("TIDAL HiRes segment had an invalid MP4 box size")
        }
        return size.toInt()
    }

    private fun ByteArray.readUInt32(offset: Int): Long {
        if (offset < 0 || offset + 4 > size) {
            throw TidalAudioResolutionException("TIDAL HiRes segment had a truncated MP4 box")
        }
        return ((this[offset].toLong() and 0xffL) shl 24) or
            ((this[offset + 1].toLong() and 0xffL) shl 16) or
            ((this[offset + 2].toLong() and 0xffL) shl 8) or
            (this[offset + 3].toLong() and 0xffL)
    }

    private fun ByteArray.readUInt64(offset: Int): Long {
        if (offset < 0 || offset + 8 > size) {
            throw TidalAudioResolutionException("TIDAL HiRes segment had a truncated large MP4 box")
        }
        var value = 0L
        for (index in 0 until 8) {
            value = (value shl 8) or (this[offset + index].toLong() and 0xffL)
        }
        return value
    }

    private fun ByteArray.readMp4BoxType(offset: Int): String {
        if (offset < 0 || offset + 4 > size) {
            throw TidalAudioResolutionException("TIDAL HiRes segment had a truncated MP4 type")
        }
        return String(this, offset, 4, Charsets.US_ASCII)
    }

    private fun downloadManifestToTempFile(
        trackId: String,
        quality: String,
        manifest: ParsedManifest,
        cacheDir: File,
    ): File {
        val tidalDir = File(cacheDir, "tidal-temp").apply { mkdirs() }
        val outputFile = File(
            tidalDir,
            "$trackId-${quality.lowercase(Locale.US)}-${System.currentTimeMillis()}${manifest.outputExtension}",
        )
        val urls = if (manifest.isDash) manifest.segmentUrls else listOf(manifest.url)
        if (urls.any { it.isBlank() }) {
            throw TidalAudioResolutionException("TIDAL manifest contained an empty URL")
        }

        try {
            outputFile.outputStream().buffered().use { out ->
                urls.forEachIndexed { index, url ->
                    val request =
                        Request
                            .Builder()
                            .url(url)
                            .get()
                            .header("Accept", "audio/flac,audio/mp4,audio/*,*/*;q=0.8")
                            .header("Accept-Encoding", "identity")
                            .header("User-Agent", BROWSER_USER_AGENT)
                            .build()
                    client.newCall(request).withAudioSourceAttemptDeadline().execute().use { response ->
                        if (!response.isSuccessful) {
                            throw TidalAudioResolutionException(
                                "TIDAL segment ${index + 1}/${urls.size} HTTP ${response.code}",
                            )
                        }
                        response.body.byteStream().use { input ->
                            input.copyTo(out)
                        }
                    }
                }
            }
        } catch (error: Throwable) {
            outputFile.delete()
            if (error is TidalAudioResolutionException) throw error
            throw TidalAudioResolutionException("TIDAL temporary download failed: ${error.message}", error)
        }

        if (outputFile.length() <= 0L) {
            outputFile.delete()
            throw TidalAudioResolutionException("TIDAL temporary download produced an empty file")
        }
        return outputFile
    }

    private fun inspectLocalPlaybackFile(
        file: File,
        manifest: ParsedManifest,
    ): PlaybackStreamInfo {
        val length = file.length()
        if (length <= 0L) {
            file.delete()
            throw TidalAudioResolutionException("TIDAL temporary file was empty")
        }

        val header = ByteArray(16)
        val bytesRead = file.inputStream().use { input -> input.read(header) }
        return try {
            inspectPlaybackHeader(
                bytes = header,
                bytesRead = bytesRead,
                manifest = manifest,
                contentLength = length,
                label = "TIDAL temporary file",
            )
        } catch (error: TidalAudioResolutionException) {
            file.delete()
            throw error
        }
    }

    private fun inspectRemotePlaybackUrl(
        url: String,
        manifest: ParsedManifest,
    ): PlaybackStreamInfo {
        val request =
            Request
                .Builder()
                .url(url)
                .get()
                .header("Accept", "audio/flac,audio/mp4,audio/*,*/*;q=0.8")
                .header("Accept-Encoding", "identity")
                .header("Range", "bytes=0-15")
                .header("User-Agent", BROWSER_USER_AGENT)
                .build()

        client.newCall(request).withAudioSourceAttemptDeadline().execute().use { response ->
            if (!response.isSuccessful) {
                throw TidalAudioResolutionException("TIDAL stream probe HTTP ${response.code}")
            }
            val header = ByteArray(16)
            val bytesRead = response.body.byteStream().use { input -> input.read(header) }
            val contentLength =
                response.header("Content-Range")
                    ?.substringAfterLast('/', missingDelimiterValue = "")
                    ?.toLongOrNull()
                    ?.takeIf { it > 0L }
                    ?: response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0L && response.code != 206 }

            return inspectPlaybackHeader(
                bytes = header,
                bytesRead = bytesRead,
                manifest = manifest,
                contentLength = contentLength,
                label = "TIDAL stream",
            )
        }
    }

    private fun inspectPlaybackHeader(
        bytes: ByteArray,
        bytesRead: Int,
        manifest: ParsedManifest,
        contentLength: Long?,
        label: String,
    ): PlaybackStreamInfo {
        val isFlac =
            bytesRead >= 4 &&
                bytes[0] == 'f'.code.toByte() &&
                bytes[1] == 'L'.code.toByte() &&
                bytes[2] == 'a'.code.toByte() &&
                bytes[3] == 'C'.code.toByte()
        val isMp4 =
            bytesRead >= 12 &&
                bytes[4] == 'f'.code.toByte() &&
                bytes[5] == 't'.code.toByte() &&
                bytes[6] == 'y'.code.toByte() &&
                bytes[7] == 'p'.code.toByte()

        if (isFlac) {
            return PlaybackStreamInfo(
                mimeType = AUDIO_FLAC_MIME_TYPE,
                codecs = "flac",
                contentLength = contentLength,
            )
        }
        if (isMp4) {
            return PlaybackStreamInfo(
                mimeType = AUDIO_MP4_MIME_TYPE,
                codecs = manifest.codecs.takeIf { it.isNotBlank() } ?: "mp4a.40.2",
                contentLength = contentLength,
            )
        }

        val headerText = bytes
            .take(bytesRead.coerceAtLeast(0))
            .map { byte ->
                val value = byte.toInt() and 0xff
                if (value in 32..126) value.toChar() else '.'
            }
            .joinToString("")
            .trim()
        throw TidalAudioResolutionException(
            "$label was not a playable FLAC/MP4 container (${headerText.ifBlank { "binary header" }})",
        )
    }

    private fun writeDashManifestToTempFile(
        trackId: String,
        quality: String,
        manifest: ParsedManifest,
        cacheDir: File,
    ): File {
        val manifestText = manifest.manifestText
            ?: throw TidalAudioResolutionException("TIDAL live DASH manifest text was missing")
        val tidalDir = File(cacheDir, "tidal-temp").apply { mkdirs() }
        val outputFile = File(
            tidalDir,
            "${trackId}-${quality.lowercase(Locale.US)}-${System.currentTimeMillis()}.mpd",
        )
        runCatching {
            outputFile.writeText(manifestText, Charsets.UTF_8)
        }.onFailure { error ->
            outputFile.delete()
            throw TidalAudioResolutionException("TIDAL live manifest write failed: ${error.message}", error)
        }
        if (outputFile.length() <= 0L) {
            outputFile.delete()
            throw TidalAudioResolutionException("TIDAL live manifest file was empty")
        }
        return outputFile
    }

    private fun cleanupTempFiles(cacheDir: File) {
        val tidalDir = File(cacheDir, "tidal-temp")
        if (!tidalDir.exists()) return
        val cutoff = System.currentTimeMillis() - TEMP_FILE_MAX_AGE_MS
        tidalDir.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < cutoff) {
                file.delete()
            }
        }
    }

    private fun fetchStreamMetadata(url: String): StreamMetadata {
        val httpUrl = url.toHttpUrlOrNull() ?: return StreamMetadata("audio/flac", null)
        val builder =
            Request
                .Builder()
                .url(httpUrl)
                .header("Accept", "audio/flac,audio/mp4,audio/*,*/*;q=0.8")
                .header("Accept-Encoding", "identity")
                .header("User-Agent", BROWSER_USER_AGENT)

        runCatching {
            client.newCall(builder.head().build()).withAudioSourceAttemptDeadline().execute().use { response ->
                if (!response.isSuccessful) return@use null
                StreamMetadata(
                    mimeType = response.header("Content-Type")?.substringBefore(';'),
                    contentLength = response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0L },
                )
            }
        }.onFailure { it.rethrowIfAudioSourceCancelled() }.getOrNull()?.let { return it }

        return runCatching {
            client.newCall(
                builder
                    .get()
                    .header("Range", "bytes=0-0")
                    .build(),
            ).withAudioSourceAttemptDeadline().execute().use { response ->
                StreamMetadata(
                    mimeType = response.header("Content-Type")?.substringBefore(';'),
                    contentLength =
                        response.header("Content-Range")
                            ?.substringAfterLast('/', missingDelimiterValue = "")
                            ?.toLongOrNull()
                            ?.takeIf { it > 0L }
                            ?: response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0L && response.code == 206 },
                )
            }
        }.onFailure { it.rethrowIfAudioSourceCancelled() }.getOrElse {
            if (AudioSourceAttemptScope.current()?.isExpired() == true) throw it
            StreamMetadata("audio/flac", null)
        }
    }

    private fun fetchText(url: String): String {
        val request =
            Request
                .Builder()
                .url(url)
                .get()
                .header("Accept", "application/dash+xml,application/xml,text/xml,*/*;q=0.8")
                .header("User-Agent", DOWNLOAD_USER_AGENT)
                .build()
        client.newCall(request).withAudioSourceAttemptDeadline().execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                throw TidalAudioResolutionException("TIDAL manifest fetch HTTP ${response.code}: ${body.take(180)}")
            }
            return body.takeIf { it.isNotBlank() }
                ?: throw TidalAudioResolutionException("TIDAL manifest fetch returned an empty body")
        }
    }

    private fun validateLikelyFullTrack(
        track: MatchedTrack,
        durationMs: Long?,
        contentLength: Long?,
    ) {
        val duration = durationMs?.takeIf { it >= 60_000L } ?: return
        val length = contentLength?.takeIf { it > 0L } ?: return
        val minimumBytesForFullTrack = (duration / 1000.0 * 96_000.0 / 8.0 * 0.70).toLong()
        if (length < minimumBytesForFullTrack) {
            throw TidalAudioResolutionException(
                "TIDAL returned a likely preview for ${track.title}: $length bytes for ${duration / 1000L}s",
            )
        }
    }

    private fun searchTerms(query: Query): List<String> =
        buildList {
            val title = query.title.searchQueryTitle()
            val primaryArtist = query.artists
                .firstOrNull { it.isNotBlank() }
                ?.searchQueryArtist()
                .orEmpty()
            val titleAndArtist = listOf(title, primaryArtist)
                .filter { it.isNotBlank() }
                .joinToString(" ")
            add(titleAndArtist)
        }.map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

    private fun Query.toDirectMatchedTrack(trackId: String): MatchedTrack =
        MatchedTrack(
            trackId = trackId,
            title = title,
            artistNames = artists,
            album = album,
            isrc = isrc,
            durationMs = durationMs,
            audioQuality = null,
            mediaTags = emptySet(),
            audioModes = emptySet(),
            sampleRate = null,
        )

    private fun resolveSongLinkTidalTrackId(query: Query): String? {
        for (sourceUrl in songLinkSourceUrls(query.mediaId)) {
            val url =
                SONG_LINK_API_URL
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("url", sourceUrl)
                    .build()
            val request =
                Request
                    .Builder()
                    .url(url)
                    .get()
                    .header("Accept", "application/json")
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .build()
            runCatching {
                client.newCall(request).withAudioSourceAttemptDeadline().execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body.string().takeIf { it.isNotBlank() } ?: return@use null
                    val root = JSONObject(body)
                    root.optJSONObject("linksByPlatform")
                        ?.optJSONObject("tidal")
                        ?.stringOrNull("url")
                        ?.toTidalTrackIdOrNull()
                        ?: root.optJSONObject("songUrls")
                            ?.stringOrNull("Tidal")
                            ?.toTidalTrackIdOrNull()
                }
            }.onFailure { it.rethrowIfAudioSourceCancelled() }.getOrNull()?.let { trackId ->
                Timber.tag("TidalAudio").i("Resolved TIDAL track $trackId through song.link from $sourceUrl")
                return trackId
            }
        }
        return null
    }

    private fun songLinkSourceUrls(mediaId: String): List<String> {
        val trimmed = mediaId.trim()
        if (trimmed.isBlank()) return emptyList()
        return buildList {
            if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                add(trimmed)
            }
            SPOTIFY_URI_TRACK_ID_REGEX
                .find(trimmed)
                ?.groupValues
                ?.getOrNull(1)
                ?.let { add("https://open.spotify.com/track/$it") }
            if (trimmed.matches(SPOTIFY_ID_ONLY_REGEX)) {
                add("https://open.spotify.com/track/$trimmed")
            }
            if (trimmed.matches(YOUTUBE_ID_ONLY_REGEX)) {
                add("https://music.youtube.com/watch?v=$trimmed")
            }
        }.distinct()
    }

    private fun estimateBitrate(
        contentLength: Long?,
        durationMs: Long?,
    ): Int? {
        val length = contentLength?.takeIf { it > 0L } ?: return null
        val duration = durationMs?.takeIf { it > 0L } ?: return null
        val bitrate = (length * 8L * 1000L) / duration
        return bitrate
            .takeIf { it in 32_000L..20_000_000L }
            ?.coerceAtMost(Int.MAX_VALUE.toLong())
            ?.toInt()
    }

    private fun normalizeSampleRate(value: Double?): Int? {
        val sampleRate = value?.takeIf { it > 0.0 } ?: return null
        return when {
            sampleRate < 1000.0 -> (sampleRate * 1000.0).roundToInt()
            else -> sampleRate.roundToInt()
        }.takeIf { it > 0 }
    }

    private fun extractExpiryMs(
        url: String,
        now: Long,
    ): Long {
        val httpUrl = url.toHttpUrlOrNull() ?: return now + STREAM_CACHE_MS
        val expiresSeconds = httpUrl.queryParameterIgnoreCase("X-Amz-Expires")?.toLongOrNull()
        val date = httpUrl.queryParameterIgnoreCase("X-Amz-Date")
        if (expiresSeconds != null && date != null) {
            runCatching {
                val issuedAt = LocalDateTime.parse(date, AMAZON_DATE).toInstant(ZoneOffset.UTC).toEpochMilli()
                return (issuedAt + expiresSeconds * 1000L - 30_000L).coerceAtLeast(now + 60_000L)
            }
        }
        return now + STREAM_CACHE_MS
    }

    private fun JSONObject.toMatchedTrack(): MatchedTrack? {
        val trackId = stringOrNull("id") ?: return null
        val title = stringOrNull("title") ?: stringOrNull("name") ?: return null
        val artists = collectArtistNames()
        val albumObj = optJSONObject("album")
        val album = albumObj?.stringOrNull("title") ?: albumObj?.stringOrNull("name")
        return MatchedTrack(
            trackId = trackId,
            title = title,
            artistNames = artists,
            album = album,
            isrc = stringOrNull("isrc"),
            durationMs = longOrNull("duration")?.takeIf { it > 0L }?.times(1000L),
            audioQuality = stringOrNull("audioQuality"),
            mediaTags = collectMediaTags(),
            audioModes = collectAudioModes(),
            sampleRate = normalizeSampleRate(doubleOrNull("sampleRate")),
            albumId = albumObj?.stringOrNull("id"),
            albumCoverId = albumObj?.stringOrNull("cover"),
        )
    }

    private fun JSONObject.collectArtistNames(): List<String> {
        val names = mutableListOf<String>()
        optJSONArray("artists")?.let { array ->
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.stringOrNull("name")?.takeIf { it.isNotBlank() }?.let(names::add)
            }
        }
        optJSONObject("artist")?.stringOrNull("name")?.takeIf { it.isNotBlank() }?.let(names::add)
        return names.distinct()
    }

    private fun JSONObject.collectMediaTags(): Set<String> {
        val tags = mutableSetOf<String>()
        optJSONObject("mediaMetadata")
            ?.optJSONArray("tags")
            ?.let { array ->
                for (index in 0 until array.length()) {
                    array.optString(index).takeIf { it.isNotBlank() }?.let(tags::add)
                }
            }
        optJSONArray("mediaMetadataTags")
            ?.let { array ->
                for (index in 0 until array.length()) {
                    array.optString(index).takeIf { it.isNotBlank() }?.let(tags::add)
                }
            }
        return tags
    }

    private fun JSONObject.collectAudioModes(): Set<String> {
        val modes = mutableSetOf<String>()
        stringOrNull("audioMode")?.let(modes::add)
        optJSONArray("audioModes")
            ?.let { array ->
                for (index in 0 until array.length()) {
                    array.optString(index).takeIf { it.isNotBlank() }?.let(modes::add)
                }
            }
        optJSONObject("mediaMetadata")
            ?.optJSONArray("audioModes")
            ?.let { array ->
                for (index in 0 until array.length()) {
                    array.optString(index).takeIf { it.isNotBlank() }?.let(modes::add)
                }
            }
        return modes
    }

    private fun String.toTidalTrackIdOrNull(): String? {
        val trimmed = trim()
        if (trimmed.matches(NUMERIC_ONLY_REGEX)) return trimmed
        TIDAL_URI_TRACK_ID_REGEX
            .matchEntire(trimmed)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { return it }
        TIDAL_URL_TRACK_ID_REGEX
            .find(trimmed)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { return it }
        return null
    }

    private fun String.trackCacheKeyFallback(): String = "direct:${trim().lowercase(Locale.US)}"

    private fun Query.trackCacheKey(): String =
        mediaId.toTidalTrackIdOrNull()
            ?.let { "direct:$it" }
            ?: listOf(
                title.normalized(),
                artists.joinToString(",") { it.normalized() },
                album.normalized(),
                durationMs?.div(1000L)?.toString().orEmpty(),
            ).joinToString("::")

    private fun String?.normalized(): String =
        this
            ?.lowercase(Locale.US)
            ?.let { Normalizer.normalize(it, Normalizer.Form.NFD) }
            ?.replace(DIACRITIC_REGEX, "")
            ?.replace(NON_ALPHANUMERIC_REGEX, " ")
            ?.trim()
            .orEmpty()

    private fun String.titleMatchNormalized(): String =
        normalized()
            .replace(FEATURED_ARTIST_TITLE_SUFFIX_REGEX, "")
            .replace(EDITION_NOISE_WORD_REGEX, " ")
            .replace(WHITESPACE_REGEX, " ")
            .trim()

    private fun String.searchQueryTitle(): String =
        trim()
            .replace(FEATURED_ARTIST_BRACKET_REGEX, "")
            .replace(EDITION_SUFFIX_REGEX, "")
            .replace(WHITESPACE_REGEX, " ")
            .trim()

    private fun String.searchQueryArtist(): String =
        trim()
            .substringBefore(',')
            .replace(WHITESPACE_REGEX, " ")
            .trim()

    private fun significantTokens(value: String): Set<String> =
        value
            .split(' ')
            .map { it.trim() }

            .filter { (it.length >= 2 || it.any { ch -> ch.code >= 0x2E80 }) && it !in STOP_WORDS }
            .toSet()

    private fun tokenOverlap(
        wanted: Set<String>,
        candidate: Set<String>,
    ): Double {
        if (wanted.isEmpty() || candidate.isEmpty()) return 0.0
        val shared = wanted.intersect(candidate).size
        return shared.toDouble() / wanted.size.coerceAtLeast(candidate.size).toDouble()
    }

    private fun hasVersionMismatch(
        wanted: String,
        candidate: String,
    ): Boolean {
        val wantedLive = wanted.contains(" live ")
        val candidateLive = candidate.contains(" live ")
        if (wantedLive != candidateLive) return true
        val wantedInstrumental = wanted.contains(" instrumental ")
        val candidateInstrumental = candidate.contains(" instrumental ")
        if (wantedInstrumental != candidateInstrumental) return true
        return VERSION_TOKENS.any { token ->
            wanted.hasToken(token) != candidate.hasToken(token)
        }
    }

    private fun String.hasToken(token: String): Boolean =
        split(' ').any { it == token }

    private fun JSONArray.firstStringOrNull(): String? {
        for (index in 0 until length()) {
            val value = optString(index).takeIf { it.isNotBlank() }
            if (value != null) return value
        }
        return null
    }

    private fun JSONObject.stringOrNull(key: String): String? =
        optString(key).takeIf { it.isNotBlank() && it != "null" }

    private fun JSONObject.longOrNull(key: String): Long? =
        if (has(key)) optLong(key).takeIf { it > 0L } else null

    private fun JSONObject.doubleOrNull(key: String): Double? =
        if (has(key)) optDouble(key).takeIf { it > 0.0 && !it.isNaN() } else null

    private fun HttpUrl.queryParameterIgnoreCase(name: String): String? {
        val key = queryParameterNames.firstOrNull { it.equals(name, ignoreCase = true) } ?: return null
        return queryParameter(key)
    }

    private fun firstXmlAttr(
        text: String,
        attr: String,
    ): String =
        Regex("""\b${Regex.escape(attr)}\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()

    private fun xmlAttrValues(
        text: String,
        attr: String,
    ): List<Int> =
        Regex("""\b${Regex.escape(attr)}\s*=\s*"(\d+)"""", RegexOption.IGNORE_CASE)
            .findAll(text)
            .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
            .toList()

    private fun firstXmlTagValue(
        text: String,
        tag: String,
    ): String =
        Regex(
            """<${Regex.escape(tag)}\b[^>]*>(.*?)</${Regex.escape(tag)}>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).find(text)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
            .trim()

    private fun String.xmlUnescape(): String =
        replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")

    private fun String.sanitizeXmlEntities(): String =
        replace(XML_UNESCAPED_AMPERSAND_REGEX, "&amp;")

    private fun String.deleteIfLocalFileUri() {
        runCatching {
            val uri = Uri.parse(this)
            if (uri.scheme == "file") {
                uri.path?.let(::File)?.delete()
            }
        }
    }

    private val STOP_WORDS =
        setOf(
            "the",
            "and",
            "feat",
            "ft",
            "with",
            "remaster",
            "remastered",
            "version",
            "explicit",
            "clean",
            "audio",
            "official",
        )

    private val VERSION_TOKENS =
        setOf(
            "remix",
            "mix",
            "edit",
            "acoustic",
            "sped",
            "slowed",
            "nightcore",
            "karaoke",
        )
}
