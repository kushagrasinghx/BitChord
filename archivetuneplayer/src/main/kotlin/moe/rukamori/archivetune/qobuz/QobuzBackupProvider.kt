/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.qobuz

import moe.rukamori.archivetune.audiosource.FlacStreamInfo
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

object QobuzBackupProvider {
    private const val DEFAULT_ENDPOINT = "https://mls.kouzu.in"

    @Volatile
    var configuredEndpoints: List<String> = emptyList()

    private fun normalizeEndpoint(raw: String): String {
        val https = if (raw.startsWith("http://")) "https://" + raw.removePrefix("http://") else raw

        return https.replace("mlc-ytify.kouzu.in", "mls.kouzu.in")
    }

    private fun endpointChain(): List<String> {
        val custom =
            configuredEndpoints
                .map { normalizeEndpoint(it.trim().trimEnd('/')) }
                .filter { it.startsWith("https://") }
        return (custom + DEFAULT_ENDPOINT).distinct()
    }

    private const val FAILURE_THRESHOLD = 3
    private const val COOLDOWN_MS = 10 * 60 * 1000L

    private val failureCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val cooldownUntil = ConcurrentHashMap<String, Long>()

    private fun endpointAvailable(endpoint: String, now: Long = System.currentTimeMillis()): Boolean =
        (cooldownUntil[endpoint] ?: 0L) < now

    private fun recordFailure(endpoint: String) {
        val count = failureCounts.getOrPut(endpoint) { AtomicInteger(0) }.incrementAndGet()
        if (count >= FAILURE_THRESHOLD) {
            cooldownUntil[endpoint] = System.currentTimeMillis() + COOLDOWN_MS
            Timber.tag("QobuzBackup")
                .w("endpoint %s failed %d times — cooling down for %d min", endpoint, count, COOLDOWN_MS / 60000)
        }
    }

    private fun recordSuccess(endpoint: String) {
        failureCounts.remove(endpoint)
        cooldownUntil.remove(endpoint)
    }

    private fun activeEndpoints(): List<String> {
        val now = System.currentTimeMillis()
        return endpointChain().filter { endpointAvailable(it, now) }
    }

    fun endpointList(): List<String> = endpointChain()
    private const val USER_AGENT = "ArchiveTune-Android"
    private const val SEARCH_CACHE_MS = 10 * 60 * 1000L

    private val VIDEO_ID_REGEX = Regex("^[A-Za-z0-9_-]{11}$")

    private val WHITESPACE_REGEX = Regex("\\s+")

    private const val MAX_QUERY_VARIANTS = 6

    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()

    data class Candidate(
        val videoId: String,
        val title: String,
        val artist: String?,

        val isLossless: Boolean,
    ) {
        val thumbnailUrl: String
            get() = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
    }

    private data class CachedSearch(
        val candidates: List<Candidate>,
        val expiresAt: Long,
    )

    private val searchCache = ConcurrentHashMap<String, CachedSearch>()

    fun searchCandidates(
        query: String,
        limit: Int = 8,
    ): List<Candidate> {
        val trimmed = query.trim()
        if (trimmed.length < 2 || limit <= 0) return emptyList()

        val cacheKey = "${trimmed.lowercase()}|$limit"
        val now = System.currentTimeMillis()
        searchCache[cacheKey]?.let { cached ->
            if (cached.expiresAt > now) return cached.candidates

            searchCache.remove(cacheKey)
        }

        val fetchLimit = limit.coerceAtLeast(24).coerceAtMost(60)
        val candidates =
            queryVariants(trimmed)
                .firstNotNullOfOrNull { variant -> fetchSearch(variant, fetchLimit).takeIf { it.isNotEmpty() } }
                .orEmpty()
                .let { rows -> rankByQueryCoverage(rows, trimmed).take(limit) }

        if (candidates.isNotEmpty()) {
            searchCache[cacheKey] = CachedSearch(candidates, now + SEARCH_CACHE_MS)
        }
        return candidates
    }

    private fun queryVariants(query: String): List<String> {
        val words = query.split(WHITESPACE_REGEX).filter { it.isNotBlank() }
        if (words.size < 2) return listOf(query)
        return buildList {
            add(query)
            for (kept in words.size - 1 downTo 1) {
                add(words.take(kept).joinToString(" "))
                add(words.takeLast(kept).joinToString(" "))
            }
        }.distinct()
            .filter { it.length >= 2 }
            .take(MAX_QUERY_VARIANTS)
    }

    private fun rankByQueryCoverage(
        rows: List<Candidate>,
        query: String,
    ): List<Candidate> {
        val tokens = query.lowercase().split(WHITESPACE_REGEX).filter { it.length > 1 }
        if (tokens.isEmpty()) return rows
        return rows.sortedWith(
            compareByDescending<Candidate> { candidate ->
                val haystack = "${candidate.title} ${candidate.artist.orEmpty()}".lowercase()
                tokens.count { token -> token in haystack }
            }.thenBy { candidate -> candidate.title.length },
        )
    }

    private fun fetchSearch(
        query: String,
        limit: Int,
    ): List<Candidate> {
        for (base in activeEndpoints()) {
            when (val candidates = fetchSearchFrom(base, query, limit)) {
                null -> recordFailure(base)
                else -> {
                    recordSuccess(base)
                    if (candidates.isNotEmpty()) return candidates
                }
            }
        }
        return emptyList()
    }

    private fun fetchSearchFrom(
        base: String,
        query: String,
        limit: Int,
    ): List<Candidate>? {
        val url =
            "$base/api/search"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("limit", limit.toString())
                .build()
        val request =
            Request
                .Builder()
                .url(url)
                .get()
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .header("x-request-source", "muzo")
                .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.tag("QobuzBackup").d(
                        "search \"%s\" failed: HTTP %d",
                        query,
                        response.code,
                    )
                    return@use null
                }
                parseSearchResponse(response.body?.string().orEmpty(), limit)
            }
        }.onFailure { error ->
            Timber.tag("QobuzBackup").d(error, "search \"%s\" failed", query)
        }.getOrDefault(null)
    }

    private fun parseSearchResponse(
        body: String,
        limit: Int,
    ): List<Candidate>? {
        if (body.isBlank()) return null
        val array = runCatching { JSONArray(body) }.getOrNull() ?: return null
        val out = mutableListOf<Candidate>()
        for (index in 0 until array.length()) {
            if (out.size >= limit) break
            val item = array.optJSONObject(index) ?: continue
            val videoId = item.optString("id").trim()
            if (!VIDEO_ID_REGEX.matches(videoId)) continue
            val title = item.optString("name").trim()
            if (title.isEmpty()) continue
            val artist = item.optString("artists").trim().takeIf { it.isNotEmpty() }

            val isLossless = item.optString("lossless").contains("flac", ignoreCase = true)
            out.add(
                Candidate(
                    videoId = videoId,
                    title = title,
                    artist = artist,
                    isLossless = isLossless,
                ),
            )
        }
        return out
    }

    data class ResolvedStream(
        val uri: String,
        val mimeType: String,
        val codecs: String,
        val contentLength: Long?,
        val contentType: String,
        val isLossless: Boolean,
        val sampleRate: Int? = null,
        val bitDepth: Int? = null,
        val durationMs: Long? = null,
    ) {
        val label: String
            get() = if (isLossless) "Qobuz backup (lossless)" else "Qobuz backup (kouzu.in)"
    }

    fun resolveStream(
        videoId: String,
        client: OkHttpClient = this.client,
    ): ResolvedStream? {
        val id = videoId.trim()
        if (!VIDEO_ID_REGEX.matches(id)) {
            Timber.tag("QobuzBackup").d("skip: \"%s\" is not a YouTube video id", id)
            return null
        }

        for (base in activeEndpoints()) {
            val candidates = fetchMirrorCandidates(id, base, client)
            if (candidates.isEmpty()) {
                recordFailure(base)
                continue
            }
            val resolved = candidates.firstNotNullOfOrNull { candidate -> probeMirror(candidate, client) }
            if (resolved != null) {
                recordSuccess(base)
                Timber.tag("QobuzBackup").i(
                    "resolved %s via %s → %s [%s%s]",
                    id,
                    base,
                    resolved.uri.take(80),
                    resolved.contentType,
                    if (resolved.isLossless) ", lossless" else "",
                )
                return resolved
            }
            recordFailure(base)
        }

        Timber.tag("QobuzBackup").w(
            "no live endpoint resolved %s (chain: %s)",
            id,
            endpointChain().joinToString(", "),
        )
        return null
    }

    private fun fetchMirrorCandidates(
        videoId: String,
        base: String,
        client: OkHttpClient,
    ): List<String> {
        val url =
            "$base/api/stream"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("id", videoId)
                .build()
        val request =
            Request
                .Builder()
                .url(url)
                .get()
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .header("x-request-source", "muzo")
                .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.tag("QobuzBackup").d("resolver miss for %s: HTTP %d", videoId, response.code)
                    return@use emptyList()
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    Timber.tag("QobuzBackup").d("resolver miss for %s: empty body", videoId)
                    return@use emptyList()
                }
                val root = runCatching { JSONObject(body) }.getOrNull()
                if (root == null) {
                    Timber.tag("QobuzBackup").d(
                        "resolver miss for %s: body is not JSON (first 100 chars: %s)",
                        videoId,
                        body.take(100),
                    )
                    return@use emptyList()
                }
                buildList {
                    root.optString("lossless").takeIf { it.isNotBlank() }?.let(::add)
                    root.optString("flac").takeIf { it.isNotBlank() }?.let(::add)
                    root.optString("url").takeIf { it.isNotBlank() }?.let(::add)
                    root.optString("canvas_url").takeIf { it.isNotBlank() }?.let(::add)
                    root.optString("video_url").takeIf { it.isNotBlank() }?.let(::add)
                }.distinct().also { mirrors ->
                    if (mirrors.isEmpty()) {
                        Timber.tag("QobuzBackup").d(
                            "resolver miss for %s: no url field in response (first 200 chars: %s)",
                            videoId,
                            body.take(200),
                        )
                    }
                }
            }
        }.onFailure { error ->
            Timber.tag("QobuzBackup").d(error, "resolver call failed for %s", videoId)
        }.getOrDefault(emptyList())
    }

    private fun probeMirror(
        url: String,
        client: OkHttpClient,
    ): ResolvedStream? =
        runCatching {
            val request =
                Request
                    .Builder()
                    .url(url)
                    .get()
                    .header("User-Agent", USER_AGENT)
                    .header("x-request-source", "muzo")
                    .header("Range", "bytes=0-${FlacStreamInfo.REQUIRED_BYTES - 1}")
                    .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val headerBytes = runCatching { response.body?.bytes() }.getOrNull()
                val contentType = response.header("Content-Type")?.lowercase().orEmpty()

                if (contentType.contains("json") || contentType.contains("html")) return@use null
                if (contentType.isNotBlank() &&
                    !contentType.startsWith("audio/") &&
                    !contentType.startsWith("video/") &&
                    !contentType.contains("octet-stream")
                ) {
                    return@use null
                }

                val totalLength =
                    response
                        .header("Content-Range")
                        ?.substringAfter('/', "")
                        ?.trim()
                        ?.toLongOrNull()
                        ?: response.header("Content-Length")?.toLongOrNull()?.takeIf { response.code != 206 }

                val streamInfo = headerBytes?.let(FlacStreamInfo::parse)

                val isFlac = streamInfo != null || contentType.contains("flac")
                ResolvedStream(
                    uri = url,
                    mimeType = if (isFlac) "audio/flac" else "audio/mp4",
                    codecs = if (isFlac) "flac" else "mp4a.40.2",
                    contentLength = totalLength?.takeIf { it > 0 },
                    contentType = contentType.ifBlank { "unknown" },
                    isLossless = isFlac,
                    sampleRate = streamInfo?.sampleRate,
                    bitDepth = streamInfo?.bitDepth,
                    durationMs = streamInfo?.durationMs,
                )
            }
        }.onFailure { error ->
            Timber.tag("QobuzBackup").d(error, "mirror probe failed for %s", url.take(80))
        }.getOrNull()
}
