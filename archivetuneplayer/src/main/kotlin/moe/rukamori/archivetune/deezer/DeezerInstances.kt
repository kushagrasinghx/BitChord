/*
 * ArchiveTune (2026)
 * © vossgraves — github.com/vossgraves
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.deezer

import moe.rukamori.archivetune.audiosource.AudioSourceAttemptScope
import moe.rukamori.archivetune.audiosource.rethrowIfAudioSourceCancelled
import moe.rukamori.archivetune.audiosource.withAudioSourceAttemptDeadline
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.utils.PoolAccountManager
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object DeezerInstances {
    private const val TAG = "DeezerInstances"
    private const val DISCOVERY_TTL_MS = 30 * 60 * 1000L
    private const val FAILURE_COOLDOWN_MS = 10 * 60 * 1000L
    private const val USER_AGENT = "ArchiveTune-Android"

    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()

    @Volatile
    private var userInstances: List<String> = emptyList()

    @Volatile
    private var discovered: List<String> = emptyList()

    @Volatile
    private var discoveredAt = 0L

    private val cooldownUntil = ConcurrentHashMap<String, Long>()

    private val workingShape = ConcurrentHashMap<String, Shape>()

    private enum class Shape { ISRC, TRACK_ID }

    data class Stream(
        val url: String,
        val flac: Boolean,
        val contentLength: Long?,
        val instance: String,
    )

    fun parse(raw: String): List<String> =
        raw
            .split('\n', '\r', ',', ' ')
            .mapNotNull(::normalize)
            .distinct()

    fun setUserInstances(raw: String) {
        userInstances = parse(raw)
    }

    fun invalidatePoolInstancesCache() {
        discovered = emptyList()
        discoveredAt = 0L
    }

    fun hasInstances(): Boolean =
        userInstances.isNotEmpty() ||
            (PoolAccountManager.isPoolEnabled() && (discovered.isNotEmpty() || discoveredAt == 0L) && poolFeedUrl() != null)

    fun instances(): List<String> {
        val pool = if (PoolAccountManager.isPoolEnabled()) discoverIfStale() else emptyList()
        val merged = LinkedHashSet<String>().apply {
            addAll(userInstances)
            addAll(pool)
        }.toList()
        val now = System.currentTimeMillis()
        return merged.sortedBy { (cooldownUntil[it] ?: 0L) > now }
    }

    fun resolve(
        isrc: String?,
        trackId: String?,
    ): Stream? {
        if (isrc.isNullOrBlank() && trackId.isNullOrBlank()) return null
        for (base in instances()) {
            if (AudioSourceAttemptScope.current()?.isExpired() == true) return null
            val shapes =
                buildList {
                    workingShape[base]?.let(::add)
                    if (!isrc.isNullOrBlank()) add(Shape.ISRC)
                    if (!trackId.isNullOrBlank()) add(Shape.TRACK_ID)
                }.distinct()
            for (shape in shapes) {
                val url =
                    when (shape) {
                        Shape.ISRC -> isrc?.takeIf { it.isNotBlank() }?.let { "$base/stream/?isrc=${URLEncoder.encode(it, "UTF-8")}" }
                        Shape.TRACK_ID -> trackId?.takeIf { it.isNotBlank() }?.let { "$base/stream/${URLEncoder.encode(it, "UTF-8")}" }
                    } ?: continue
                val probed = probe(url, base)
                if (probed != null) {
                    workingShape[base] = shape
                    cooldownUntil.remove(base)
                    return probed
                }
                if (AudioSourceAttemptScope.current()?.isExpired() == true) return null
            }
            if (AudioSourceAttemptScope.current()?.isExpired() != true) {
                cooldownUntil[base] = System.currentTimeMillis() + FAILURE_COOLDOWN_MS
            }
        }
        return null
    }

    data class Health(
        val instance: String,
        val ok: Boolean,
        val detail: String,
    )

    fun checkFirst(): Health? {
        val base = instances().firstOrNull() ?: return null
        return runCatching {
            val request = Request.Builder().url("$base/health").header("User-Agent", USER_AGENT).get().build()
            client.newCall(request).withAudioSourceAttemptDeadline().execute().use { response ->
                val body = response.body?.string().orEmpty().take(20_000)
                val json = runCatching { JSONObject(body) }.getOrNull()
                when {
                    !response.isSuccessful -> Health(base, false, "HTTP ${response.code}")
                    json == null -> Health(base, false, "health response is not JSON")
                    json.opt("ok") == false -> Health(base, false, "instance reports it cannot serve")
                    json.optJSONObject("accounts")?.let {
                        it.optInt("total") > 0 && it.optInt("available") == 0 && it.optInt("cooling") == 0
                    } == true -> Health(base, false, "no accounts available on the instance")
                    else -> Health(base, true, "live")
                }
            }
        }.getOrElse { Health(base, false, it.message ?: it.javaClass.simpleName) }
    }

    private fun probe(
        url: String,
        base: String,
    ): Stream? =
        runCatching {
            val request =
                Request
                    .Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "audio/*")
                    .header("Range", "bytes=0-15")
                    .get()
                    .build()
            client.newCall(request).withAudioSourceAttemptDeadline().execute().use { response ->
                if (response.code != 200 && response.code != 206) {
                    Timber.tag(TAG).d("instance %s answered HTTP %d", base, response.code)
                    return@use null
                }
                val head = ByteArray(16)
                val source = response.body?.byteStream() ?: return@use null
                var read = 0
                while (read < head.size) {
                    val n = source.read(head, read, head.size - read)
                    if (n <= 0) break
                    read += n
                }
                val contentType = response.header("Content-Type").orEmpty().lowercase()
                val flac = read >= 4 && head[0] == 'f'.code.toByte() && head[1] == 'L'.code.toByte() &&
                    head[2] == 'a'.code.toByte() && head[3] == 'C'.code.toByte()
                val mp3 =
                    (read >= 3 && head[0] == 'I'.code.toByte() && head[1] == 'D'.code.toByte() && head[2] == '3'.code.toByte()) ||
                        (read >= 2 && (head[0].toInt() and 0xFF) == 0xFF && (head[1].toInt() and 0xE0) == 0xE0)
                if (!flac && !mp3) {
                    Timber.tag(TAG).d("instance %s did not answer with audio (%s)", base, contentType)
                    return@use null
                }
                val total =
                    response
                        .header("Content-Range")
                        ?.substringAfter('/', "")
                        ?.toLongOrNull()
                        ?: response.body?.contentLength()?.takeIf { response.code == 200 && it > 0L }
                Stream(url = url, flac = flac, contentLength = total, instance = base)
            }
        }.onFailure {
            it.rethrowIfAudioSourceCancelled()
            Timber.tag(TAG).d(it, "probe failed for %s", base)
        }
            .getOrNull()

    private fun poolFeedUrl(): String? =
        BuildConfig.SOURCE_PROVIDER_URL
            .trim()
            .trimEnd('/')
            .takeIf { it.isNotEmpty() }
            ?.let { "$it/api/instances/deezer" }

    private fun discoverIfStale(): List<String> {
        if (!PoolAccountManager.isPoolEnabled()) {
            invalidatePoolInstancesCache()
            return emptyList()
        }
        val now = System.currentTimeMillis()
        if (discoveredAt != 0L && now - discoveredAt < DISCOVERY_TTL_MS) return discovered
        val url = poolFeedUrl() ?: return emptyList()
        synchronized(this) {
            if (!PoolAccountManager.isPoolEnabled()) {
                invalidatePoolInstancesCache()
                return emptyList()
            }
            if (discoveredAt != 0L && System.currentTimeMillis() - discoveredAt < DISCOVERY_TTL_MS) return discovered
            val result =
                runCatching {
                    val builder = Request.Builder().url(url).header("User-Agent", USER_AGENT)
                    if (BuildConfig.SOURCE_PROVIDER_KEY.isNotBlank()) {
                        builder.header("Authorization", "Bearer ${BuildConfig.SOURCE_PROVIDER_KEY}")
                    }
                    client.newCall(builder.get().build()).withAudioSourceAttemptDeadline().execute().use { response ->
                        if (!PoolAccountManager.isPoolEnabled()) return@use null
                        if (!response.isSuccessful) {
                            Timber.tag(TAG).w("pool instance feed answered HTTP %d", response.code)
                            return@use null
                        }
                        parseFeed(response.body?.string().orEmpty())
                    }
                }.onFailure {
                    it.rethrowIfAudioSourceCancelled()
                    Timber.tag(TAG).w(it, "pool instance feed failed")
                }
                    .getOrNull()
            if (!PoolAccountManager.isPoolEnabled()) {
                invalidatePoolInstancesCache()
                return emptyList()
            }

            if (result != null) discovered = result
            discoveredAt = System.currentTimeMillis()
            return discovered
        }
    }

    private fun parseFeed(body: String): List<String> {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        val out = LinkedHashSet<String>()
        for (key in listOf("streaming", "api")) {
            val arr: JSONArray = root.optJSONArray(key) ?: continue
            for (i in 0 until arr.length()) {
                val value =
                    when (val item = arr.opt(i)) {
                        is String -> item
                        is JSONObject -> item.optString("url").ifBlank { item.optString("baseUrl") }
                        else -> null
                    }
                normalize(value)?.let(out::add)
            }
        }
        return out.toList()
    }

    private fun normalize(raw: String?): String? {
        val trimmed = raw?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: return null
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
        return runCatching { java.net.URI(withScheme) }.getOrNull()?.takeIf { !it.host.isNullOrBlank() }?.let { withScheme }
    }
}
