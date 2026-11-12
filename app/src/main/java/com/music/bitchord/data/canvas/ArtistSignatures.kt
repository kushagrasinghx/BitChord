package com.music.bitchord.data.canvas

import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

/**
 * A handwritten signature image for an artist, served by a user-configured
 * artist-signature API (see https://github.com/itsmeadarsh2008/artist-signature).
 *
 * Secondary to [AppleArtistArt.logoUrl]: Apple's title logo is typeset artwork
 * for the artist's name, while this is ink — a panoramic strip that reads best
 * small and centered rather than as a name replacement. Null when the provider
 * is unconfigured, the artist has no signature, or the name is ambiguous
 * (the API returns `matches[]` instead of guessing).
 *
 * [imageUrl] is the served mirror URL when present, else the upstream
 * `original_url`. [aspect] is width over height for sizing without decode.
 */
data class ArtistSignature(
    val imageUrl: String,
    val aspect: Float,
    /** Lowercased asset format (`svg`, `png`, …); "" when unknown. */
    val format: String = "",
)

/**
 * Name-keyed lookup against a custom base URL, mirroring
 * [AppleArtistArtRepository]'s contract: in-memory LRU, never throws,
 * `null` for "nothing known" (never looked up and known-to-have-nothing
 * read the same to a caller deciding what to draw first).
 */
object ArtistSignatureRepository {

    private const val TAG = "ArtistSignature"
    private const val CACHE_SIZE = 64
    const val MAX_BASE_URL_LENGTH = 500

    private class Entry(val signature: ArtistSignature?)

    private val cache = object : LinkedHashMap<String, Entry>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>) =
            size > CACHE_SIZE
    }

    private val _updates = MutableStateFlow(0)

    /** Ticks whenever a lookup settles, so headers re-read [cached]. */
    val updates: StateFlow<Int> = _updates

    /** Trimmed base without trailing slash, or "" when blank. */
    fun normalizeBaseUrl(raw: String): String =
        raw.trim().trimEnd('/').take(MAX_BASE_URL_LENGTH)

    /** Only http(s) URLs are ever requested. Blank never is. */
    fun isValidBaseUrl(baseUrl: String): Boolean {
        val base = normalizeBaseUrl(baseUrl)
        if (base.isEmpty()) return false
        return base.startsWith("http://") || base.startsWith("https://")
    }

    /** Effective base, or null when the provider is off/unconfigured. */
    fun effectiveBaseUrl(enabled: Boolean, baseUrl: String): String? {
        if (!enabled) return null
        val base = normalizeBaseUrl(baseUrl)
        return base.takeIf { isValidBaseUrl(it) }
    }

    fun cached(artistName: String, baseUrl: String?): ArtistSignature? {
        if (baseUrl.isNullOrEmpty()) return null
        val key = cacheKey(artistName, baseUrl) ?: return null
        return synchronized(cache) { cache[key] }?.signature
    }

    /** The best signature for [artistName], or null. Never throws. */
    suspend fun signatureFor(artistName: String, baseUrl: String?): ArtistSignature? {
        if (baseUrl.isNullOrEmpty()) return null
        val key = cacheKey(artistName, baseUrl) ?: return null
        synchronized(cache) { cache[key] }?.let {
            Log.d(TAG, "cache hit for $artistName: ${it.signature?.imageUrl}")
            return it.signature
        }

        val entry = withContext(Dispatchers.IO) {
            runCatching { lookup(artistName, baseUrl) }
                .onFailure { Log.d(TAG, "lookup failed for $artistName: $it") }
                .getOrNull()
        } ?: return null
        Log.d(TAG, "settled for $artistName: ${entry.signature?.imageUrl}")
        synchronized(cache) { cache[key] = entry }
        _updates.value++
        return entry.signature
    }

    /** Forgets everything, e.g. after the base URL changes. */
    fun clear() {
        synchronized(cache) { cache.clear() }
        _updates.value++
    }

    /** `true` when `GET base/v1/health` answers 2xx. Never throws. */
    suspend fun checkHealth(baseUrl: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val base = normalizeBaseUrl(baseUrl)
            if (!isValidBaseUrl(base)) return@runCatching false
            val request = Request.Builder().url("$base/v1/health").get().build()
            Http.client.newCall(request).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    private fun cacheKey(artistName: String, baseUrl: String): String? {
        val name = artistName.normalized()
        if (name.isEmpty()) return null
        return "${normalizeBaseUrl(baseUrl)}\n$name"
    }

    private fun lookup(artistName: String, baseUrl: String): Entry? {
        val base = normalizeBaseUrl(baseUrl)
        if (!isValidBaseUrl(base)) return Entry(null)
        val url = "$base/v1/signatures?artist=${URLEncoder.encode(artistName, "UTF-8")}"
        val body = get(url) ?: return null
        return Entry(parse(body))
    }

    /**
     * Tolerant of both shapes the API has documented: `asset` as an object
     * with `url` or as a plain string, and legacy top-level `url` /
     * `original_url`. A `matches[]` response (ambiguous name) parses as null.
     *
     * When several signatures exist the best pick wins rather than whatever
     * was listed first — same order as the server's `best()`: redistributable
     * license, then SVG format, then verification state, then a panoramic
     * bonus (wide images are usually complete signature lines; square canvases
     * are often fragments).
     */
    internal fun parse(body: String): ArtistSignature? {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        if (!root.has("signatures") && root.has("matches")) return null
        val signatures = root.optJSONArray("signatures") ?: return null
        var best: ArtistSignature? = null
        var bestScore = Int.MIN_VALUE
        var bestArea = -1L
        for (i in 0 until signatures.length()) {
            val sig = signatures.optJSONObject(i) ?: continue
            val url = extractUrl(sig) ?: continue
            val score = rank(sig, url)
            // Resolution only breaks ties: a larger raster of the same rank
            // upscales less on the header.
            val area = extractArea(sig)
            if (best == null || score > bestScore || (score == bestScore && area > bestArea)) {
                bestScore = score
                bestArea = area
                best = ArtistSignature(
                    imageUrl = url,
                    aspect = extractAspect(sig),
                    format = extractFormat(sig, url),
                )
            }
        }
        return best
    }

    private fun extractFormat(sig: JSONObject, url: String): String {
        sig.optJSONObject("asset")?.optString("format")?.lowercase(Locale.ROOT)
            ?.takeIf { it.isNotBlank() }?.let { return it }
        sig.optString("format").lowercase(Locale.ROOT)
            .takeIf { it.isNotBlank() }?.let { return it }
        return url.substringBefore('?').substringAfterLast('.', "").lowercase(Locale.ROOT)
    }

    private fun rank(sig: JSONObject, url: String): Int {
        val status = sig.optJSONObject("license")?.optString("status").orEmpty().lowercase(Locale.ROOT)
        val license = when (status) {
            "known" -> 2
            "requires_review" -> 1
            else -> 0
        }
        val formatScore = when (extractFormat(sig, url)) {
            "svg" -> 2
            "png" -> 1
            else -> 0
        }
        val verification = sig.optString("verification").lowercase(Locale.ROOT)
        val verificationScore = when (verification) {
            "artist_verified" -> 3
            "verified" -> 2
            "unverified" -> 1
            else -> 0
        }
        val panoramic = if (extractAspect(sig) >= 1.4f) 1 else 0
        return license * 1000 + formatScore * 100 + verificationScore * 10 + panoramic
    }

    private fun extractUrl(sig: JSONObject): String? {
        sig.optJSONObject("asset")?.optString("url")?.takeIf { it.startsWith("http") }?.let { return it }
        // Some builds serve the mirror as a bare string asset.
        sig.optString("asset").takeIf { it.startsWith("http") }?.let { return it }
        sig.optString("url").takeIf { it.startsWith("http") }?.let { return it }
        sig.optString("original_url").takeIf { it.startsWith("http") }?.let { return it }
        sig.optString("originalUrl").takeIf { it.startsWith("http") }?.let { return it }
        return null
    }

    private fun extractAspect(sig: JSONObject): Float {
        val asset = sig.optJSONObject("asset")
        val w = sig.optInt("width").takeIf { it > 0 }
            ?: asset?.optInt("width")?.takeIf { it > 0 }
        val h = sig.optInt("height").takeIf { it > 0 }
            ?: asset?.optInt("height")?.takeIf { it > 0 }
        return if (w != null && h != null && h > 0) w.toFloat() / h else 2.5f
    }

    private fun extractArea(sig: JSONObject): Long {
        val asset = sig.optJSONObject("asset")
        val w = sig.optInt("width").takeIf { it > 0 }
            ?: asset?.optInt("width")?.takeIf { it > 0 } ?: return 0
        val h = sig.optInt("height").takeIf { it > 0 }
            ?: asset?.optInt("height")?.takeIf { it > 0 } ?: return 0
        return w.toLong() * h
    }

    private fun get(url: String): String? {
        val request = Request.Builder().url(url).get().build()
        Http.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }

    /** Same folding as Apple's lookup: case, accents and punctuation don't identify. */
    private fun String.normalized(): String =
        java.text.Normalizer.normalize(lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD)
            .filter { it.isLetterOrDigit() }
}
