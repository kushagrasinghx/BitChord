/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.deezer

import moe.rukamori.archivetune.audiosource.TrackMatching
import moe.rukamori.archivetune.utils.PoolAccountManager
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object DeezerAudioProvider {
    data class Metadata(
        val trackId: String,
        val title: String,
        val artist: String?,
        val album: String?,
        val isrc: String?,
        val durationMs: Long?,
        val previewUrl: String?,
        val coverUrl: String?,
    )

    private const val TAG = "Deezer"
    private const val MIN_MATCH_SCORE = 0.55
    private const val SEARCH_LIMIT = 10
    private const val SEARCH_CACHE_MS = 10 * 60 * 1000L
    private const val STREAM_CACHE_MS = 30 * 60 * 1000L
    private const val FAILURE_CACHE_MS = 10 * 60 * 1000L

    private const val SESSION_TTL_MS = 45 * 60 * 1000L

    private const val GATEWAY = "https://www.deezer.com/ajax/gw-light.php"
    private const val MEDIA_ENDPOINT = "https://media.deezer.com/v1/get_url"

    private const val DEFAULT_RESOLVER_URL = "https://yesitworkssomehow-funny-deeza-api-and-yeah.hf.space/get_url"
    private const val DEFAULT_RESOLVER_URL_128 = "https://api-number-2-poopo.onrender.com/get_url"
    private const val RENDER_RESOLVER_URL = "https://dzmedia-metrofuse.onrender.com/get_url"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Safari/537.36"

    private const val MIME_FLAC = "audio/flac"
    private const val MIME_MPEG = "audio/mpeg"

    const val FORMAT_FLAC = "FLAC"
    const val FORMAT_MP3_320 = "MP3_320"
    const val FORMAT_MP3_128 = "MP3_128"

    private val FORMAT_TIERS = listOf(FORMAT_FLAC, FORMAT_MP3_320, FORMAT_MP3_128)

    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()

    @Volatile
    private var manualAccount: PoolAccountManager.DeezerPoolAccount? = null

    fun setManualArl(
        arl: String?,
        premium: Boolean = false,
    ) {
        val trimmed = arl?.trim()
        val next =
            if (trimmed.isNullOrEmpty()) {
                null
            } else {
                PoolAccountManager.DeezerPoolAccount(id = null, arl = trimmed, premium = premium)
            }
        val previous = manualAccount
        manualAccount = next

        if (previous != null && previous.arl != next?.arl) {
            sessions.remove(previous.arl)
        }
    }

    fun hasAccounts(): Boolean = manualAccount != null || PoolAccountManager.deezerAccounts().isNotEmpty()

    data class AccountAvailability(
        val manual: Boolean,
        val manualPremium: Boolean,
        val pooled: Int,
        val pooledPremium: Int,
    ) {
        val total: Int get() = pooled + if (manual) 1 else 0
    }

    fun accountAvailability(): AccountAvailability {
        val manual = manualAccount
        val pooled = PoolAccountManager.deezerAccounts().filter { it.arl != manual?.arl }
        return AccountAvailability(
            manual = manual != null,
            manualPremium = manual?.premium == true,
            pooled = pooled.size,
            pooledPremium = pooled.count { it.premium },
        )
    }

    fun verifyPreferredAccount(): AccountInfo? = accounts().firstOrNull()?.let { verifyArl(it.arl) }

    private fun accounts(): List<PoolAccountManager.DeezerPoolAccount> {
        val pooled = PoolAccountManager.deezerAccounts()
        val manual = manualAccount ?: return pooled

        return listOf(manual) + pooled.filter { it.arl != manual.arl }
    }

    data class AccountInfo(
        val name: String,
        val lossless: Boolean,
    )

    fun verifyArl(arl: String): AccountInfo? =
        runCatching {
            val json = gateway(arl.trim(), apiToken = "", method = "deezer.getUserData", payload = null)
            val user = json.optJSONObject("results")?.optJSONObject("USER") ?: return@runCatching null

            if (user.optLong("USER_ID", 0L) == 0L) return@runCatching null
            val options = user.optJSONObject("OPTIONS")
            val name =
                sequenceOf(
                    user.optString("BLOG_NAME"),
                    user.optString("FIRSTNAME"),
                    user.optString("EMAIL"),
                ).firstOrNull { it.isNotBlank() } ?: "Deezer"
            AccountInfo(
                name = name,
                lossless =
                    options?.optBoolean("web_lossless", false) == true ||
                        options?.optBoolean("mobile_lossless", false) == true,
            )
        }.onFailure { Timber.tag(TAG).w(it, "ARL verification failed") }
            .getOrNull()

    private data class Session(
        val arl: String,
        val apiToken: String,
        val licenseToken: String,
        val lossless: Boolean,
        val masterSecret: String?,
        val establishedAt: Long,
    )

    data class Resolved(
        val uri: String,
        val mimeType: String,
        val codecs: String,
        val contentLength: Long?,
        val label: String,
        val matchedTitle: String,
        val matchedArtist: String?,
        val matchedAlbum: String?,
        val matchedDurationMs: Long?,
        val sampleRate: Int?,
        val bitDepth: Int?,
    )

    private class CachedStream(
        val stream: Resolved,
        val expiresAt: Long,
    )

    private val sessions = ConcurrentHashMap<String, Session>()
    private val searchCache = ConcurrentHashMap<String, Pair<TrackMatching.Candidate?, Long>>()
    private val streamCache = ConcurrentHashMap<String, CachedStream>()
    private val failureCache = ConcurrentHashMap<String, Long>()

    @Volatile
    var lastResolvedTrackId: String? = null
        private set

    data class Query(
        val mediaId: String,
        val title: String,
        val artists: List<String>,
        val album: String?,
        val durationMs: Long?,

        val isrc: String? = null,
    )

    private fun Query.cacheKey(): String =
        listOf(mediaId, title.lowercase(), artists.joinToString(",").lowercase(), album.orEmpty().lowercase())
            .joinToString("|")

    fun hasBackends(): Boolean = accounts().isNotEmpty() || DeezerInstances.hasInstances()

    fun invalidate(
        query: Query,
        format: String,
    ) {
        val key = query.cacheKey() + ":" + format
        streamCache.remove(key)
        failureCache.remove(key)
    }

    fun resolve(
        query: Query,
        format: String,
    ): Resolved? {
        val accounts = accounts()
        if (accounts.isEmpty() && !DeezerInstances.hasInstances()) {
            Timber.tag(TAG).d("no accounts and no API instances; only the anonymous resolver fallback is left")
        }

        val now = System.currentTimeMillis()
        val cacheKey = query.cacheKey() + ":" + format
        streamCache[cacheKey]?.let { cached ->
            if (cached.expiresAt > now) return cached.stream
            streamCache.remove(cacheKey)
        }
        failureCache[cacheKey]?.let { failedUntil ->
            if (failedUntil > now) return null
            failureCache.remove(cacheKey)
        }

        val ordered = accounts.sortedByDescending { it.premium }
        for (account in ordered) {
            val session =
                runCatching { session(account) }
                    .onFailure { Timber.tag(TAG).w(it, "session failed for pooled account") }
                    .getOrNull() ?: continue

            val match =
                runCatching { matchTrack(session, query) }
                    .onFailure { Timber.tag(TAG).w(it, "search failed") }
                    .getOrNull() ?: continue
            val trackId = match.id

            val media =
                runCatching { requestUrl(session, trackId, format) }
                    .onFailure { Timber.tag(TAG).w(it, "get_url failed for track %s", trackId) }
                    .getOrNull()
            if (media == null) {
                sessions.remove(account.arl)
                continue
            }

            lastResolvedTrackId = trackId
            val stream =
                buildResolvedStream(
                    masterSecret = session.masterSecret,
                    media = media,
                    trackId = trackId,
                    matchedTitle = match.title,
                    matchedArtist = match.artists.firstOrNull(),
                    matchedAlbum = match.album,
                    matchedDurationMs = match.durationMs,
                )
            streamCache[cacheKey] = CachedStream(stream, System.currentTimeMillis() + STREAM_CACHE_MS)
            return stream
        }

        resolveViaInstance(query)?.let { stream ->
            streamCache[cacheKey] = CachedStream(stream, System.currentTimeMillis() + STREAM_CACHE_MS)
            return stream
        }

        runCatching { matchTrackWithoutSession(query) }
            .onFailure { Timber.tag(TAG).w(it, "anonymous fallback match failed") }
            .getOrNull()
            ?.let { match ->
                anonymousResolverStream(
                    trackId = match.id,
                    format = format,
                    matchedTitle = match.title,
                    matchedArtist = match.artists.firstOrNull(),
                    matchedAlbum = match.album,
                    matchedDurationMs = match.durationMs,
                )
            }?.let { cached ->
                streamCache[cacheKey] = cached
                return cached.stream
            }

        failureCache[cacheKey] = System.currentTimeMillis() + FAILURE_CACHE_MS
        return null
    }

    private fun resolveViaInstance(query: Query): Resolved? {
        if (!DeezerInstances.hasInstances()) return null

        if (DeezerInstances.instances().isEmpty()) return null
        val match: TrackMatching.Candidate?
        val isrc: String?
        val byIsrc =
            query.isrc?.takeIf { it.isNotBlank() }?.let { code ->
                runCatching { lookupByIsrc(code) }.getOrNull()
            }
        if (byIsrc != null) {
            match = byIsrc
            isrc = query.isrc
        } else {
            val metadata =
                runCatching { kotlinx.coroutines.runBlocking { lookup(query) } }
                    .onFailure { Timber.tag(TAG).w(it, "public lookup failed") }
                    .getOrNull() ?: return null
            match =
                TrackMatching.Candidate(
                    id = metadata.trackId,
                    title = metadata.title,
                    artists = listOfNotNull(metadata.artist),
                    album = metadata.album,
                    durationMs = metadata.durationMs,
                )
            isrc = metadata.isrc
        }
        val stream = DeezerInstances.resolve(isrc = isrc, trackId = match.id) ?: return null
        lastResolvedTrackId = match.id
        return Resolved(

            uri = stream.url,
            mimeType = if (stream.flac) MIME_FLAC else MIME_MPEG,
            codecs = if (stream.flac) "flac" else "mp3",
            contentLength = stream.contentLength,
            label = if (stream.flac) "Deezer FLAC (API)" else "Deezer MP3 (API)",
            matchedTitle = match.title,
            matchedArtist = match.artists.firstOrNull(),
            matchedAlbum = match.album,
            matchedDurationMs = match.durationMs,
            sampleRate = if (stream.flac) 44_100 else null,
            bitDepth = if (stream.flac) 16 else null,
        )
    }

    private fun matchTrackWithoutSession(query: Query): TrackMatching.Candidate? {
        val key = query.cacheKey()
        val now = System.currentTimeMillis()
        searchCache[key]?.let { (candidate, expiresAt) ->
            if (expiresAt > now && candidate != null) return candidate
            if (expiresAt <= now) searchCache.remove(key)
        }

        query.isrc?.takeIf { it.isNotBlank() }?.let { isrc ->
            val exact =
                runCatching { lookupByIsrc(isrc) }
                    .onFailure { Timber.tag(TAG).w(it, "isrc lookup failed for %s", isrc) }
                    .getOrNull()
            if (exact != null) {
                searchCache[key] = exact to (now + SEARCH_CACHE_MS)
                return exact
            }
        }

        val metadata =
            runCatching { kotlinx.coroutines.runBlocking { lookup(query) } }
                .onFailure { Timber.tag(TAG).w(it, "public lookup failed") }
                .getOrNull() ?: return null
        val candidate =
            TrackMatching.Candidate(
                id = metadata.trackId,
                title = metadata.title,
                artists = listOfNotNull(metadata.artist),
                album = metadata.album,
                durationMs = metadata.durationMs,
            )
        searchCache[key] = candidate to (now + SEARCH_CACHE_MS)
        return candidate
    }

    private fun anonymousResolverStream(
        trackId: String,
        format: String,
        matchedTitle: String,
        matchedArtist: String?,
        matchedAlbum: String?,
        matchedDurationMs: Long?,
    ): CachedStream? {
        for (resolverUrl in anonymousResolverUrls(format)) {
            val media =
                runCatching { requestAnonymousUrl(resolverUrl, trackId, format) }
                    .onFailure { Timber.tag(TAG).w(it, "anonymous resolver failed: %s", resolverUrl) }
                    .getOrNull() ?: continue
            Timber.tag(TAG).d("track %s resolved by anonymous resolver %s (%s)", trackId, resolverUrl, media.format)
            lastResolvedTrackId = trackId

            val stream =
                buildResolvedStream(
                    masterSecret = null,
                    media = media,
                    trackId = trackId,
                    matchedTitle = matchedTitle,
                    matchedArtist = matchedArtist,
                    matchedAlbum = matchedAlbum,
                    matchedDurationMs = matchedDurationMs,
                )

            val expiresAt =
                minOf(media.expiresAtMs ?: Long.MAX_VALUE, System.currentTimeMillis() + STREAM_CACHE_MS)
            return CachedStream(stream, expiresAt)
        }
        return null
    }

    private fun anonymousResolverUrls(format: String): List<String> =
        buildList {
            if (format == FORMAT_MP3_128) add(DEFAULT_RESOLVER_URL_128)
            add(DEFAULT_RESOLVER_URL)
            add(RENDER_RESOLVER_URL)
        }.distinct()

    private fun requestAnonymousUrl(
        resolverUrl: String,
        trackId: String,
        format: String,
    ): Media? {
        val body =
            JSONObject()
                .put("formats", JSONArray().put(format))
                .put("ids", JSONArray().put(trackId.toLongOrNull() ?: trackId))
                .put("fast", false)
        val request =
            Request
                .Builder()
                .url(resolverUrl)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .post(body.toString().toRequestBody(JSON_MEDIA))
                .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("resolver HTTP ${response.code}")
            val root = JSONObject(response.body?.string().orEmpty())

            val mediaArray = root.optJSONArray("data")?.optJSONObject(0)?.optJSONArray("media") ?: return null

            val media =
                (0 until mediaArray.length())
                    .mapNotNull { i -> mediaArray.optJSONObject(i) }
                    .firstOrNull { it.optString("format").equals(format, ignoreCase = true) }
                    ?: mediaArray.optJSONObject(0)
                    ?: return null

            val sources = media.optJSONArray("sources")
            val source = sources?.optJSONObject(1) ?: sources?.optJSONObject(0) ?: return null
            val url = source.optString("url").takeIf { it.isNotBlank() } ?: return null

            val cipherType = media.optJSONObject("cipher")?.optString("type").orEmpty()
            if (!cipherType.equals("BF_CBC_STRIPE", ignoreCase = true)) return null

            val servedFormat = media.optString("format").ifBlank { format }

            val expiresAtMs =
                media.optLong("exp", 0L).takeIf { it > 0L }
                    ?.times(1000L)
                    ?.minus(30_000L)
                    ?.coerceAtLeast(System.currentTimeMillis() + 60_000L)
            return Media(
                url = url,
                flac = servedFormat.equals(FORMAT_FLAC, ignoreCase = true),
                format = servedFormat,
                contentLength = media.optLong("filesize", 0L).takeIf { it > 0L },
                expiresAtMs = expiresAtMs,
            )
        }
    }

    suspend fun resolveByTrackId(
        trackId: String,
        format: String,
    ): Resolved? {
        if (trackId.isBlank()) return null
        val accounts = accounts()
        if (accounts.isEmpty()) {
            Timber.tag(TAG).d("no accounts; only the anonymous resolver fallback is left for track %s", trackId)
        }

        val now = System.currentTimeMillis()
        val cacheKey = "id:$trackId:$format"
        streamCache[cacheKey]?.let { cached ->
            if (cached.expiresAt > now) return cached.stream
            streamCache.remove(cacheKey)
        }
        failureCache[cacheKey]?.let { failedUntil ->
            if (failedUntil > now) return null
            failureCache.remove(cacheKey)
        }

        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val ordered = accounts.sortedByDescending { it.premium }
            for (account in ordered) {
                val session =
                    runCatching { session(account) }
                        .onFailure { Timber.tag(TAG).w(it, "session failed for pooled account") }
                        .getOrNull() ?: continue
                val media =
                    runCatching { requestUrl(session, trackId, format) }
                        .onFailure { Timber.tag(TAG).w(it, "get_url failed for track %s", trackId) }
                        .getOrNull()
                if (media == null) {
                    sessions.remove(account.arl)
                    continue
                }
                lastResolvedTrackId = trackId
                val stream =
                    buildResolvedStream(
                        masterSecret = session.masterSecret,
                        media = media,
                        trackId = trackId,
                        matchedTitle = "",
                        matchedArtist = null,
                        matchedAlbum = null,
                        matchedDurationMs = null,
                    )
                streamCache[cacheKey] = CachedStream(stream, System.currentTimeMillis() + STREAM_CACHE_MS)
                return@withContext stream
            }

            anonymousResolverStream(
                trackId = trackId,
                format = format,
                matchedTitle = "",
                matchedArtist = null,
                matchedAlbum = null,
                matchedDurationMs = null,
            )?.let { cached ->
                streamCache[cacheKey] = cached
                return@withContext cached.stream
            }
            failureCache[cacheKey] = System.currentTimeMillis() + FAILURE_CACHE_MS
            null
        }
    }

    private fun buildResolvedStream(
        masterSecret: String?,
        media: Media,
        trackId: String,
        matchedTitle: String,
        matchedArtist: String?,
        matchedAlbum: String?,
        matchedDurationMs: Long?,
    ): Resolved =
        Resolved(
            uri = DeezerCrypto.buildUri(media.url, trackId, masterSecret),
            mimeType = if (media.flac) MIME_FLAC else MIME_MPEG,

            codecs = if (media.flac) "flac" else "mp3",
            contentLength = media.contentLength,

            label =
                when (media.format.uppercase()) {
                    FORMAT_FLAC -> "Deezer FLAC"
                    FORMAT_MP3_320 -> "Deezer MP3 320"
                    FORMAT_MP3_128 -> "Deezer MP3 128"
                    else -> "Deezer"
                },
            matchedTitle = matchedTitle,
            matchedArtist = matchedArtist,
            matchedAlbum = matchedAlbum,
            matchedDurationMs = matchedDurationMs,

            sampleRate = if (media.flac) 44_100 else null,
            bitDepth = if (media.flac) 16 else null,
        )

    private fun session(account: PoolAccountManager.DeezerPoolAccount): Session {
        val now = System.currentTimeMillis()
        sessions[account.arl]?.let { if (now - it.establishedAt < SESSION_TTL_MS) return it }

        val json = gateway(account.arl, apiToken = "", method = "deezer.getUserData", payload = null)
        val results = json.optJSONObject("results")
        val user = requireNotNull(results?.optJSONObject("USER")) { "no USER in session payload" }

        if (user.optLong("USER_ID", 0L) == 0L) {
            PoolAccountManager.report("deezer", "account", account.id, "dead")
            throw IllegalStateException("ARL rejected by gateway")
        }

        val options = requireNotNull(user.optJSONObject("OPTIONS")) { "no OPTIONS in session payload" }
        val licenseToken = options.optString("license_token")
        require(licenseToken.isNotBlank()) { "no license_token in session" }

        val apiToken = results?.optString("checkForm").orEmpty()
        require(apiToken.isNotBlank()) { "no api token in session" }

        val lossless =
            options.optBoolean("web_lossless", false) ||
                options.optBoolean("mobile_lossless", false)

        if (account.premium && !lossless) {
            PoolAccountManager.report("deezer", "account", account.id, "not_premium")
        }

        val session =
            Session(
                arl = account.arl,
                apiToken = apiToken,
                licenseToken = licenseToken,

                lossless = lossless,
                masterSecret = account.masterSecret,
                establishedAt = now,
            )
        sessions[account.arl] = session
        return session
    }

    private fun gateway(
        arl: String,
        apiToken: String,
        method: String,
        payload: JSONObject?,
    ): JSONObject {
        val url =
            GATEWAY
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("method", method)
                .addQueryParameter("input", "3")
                .addQueryParameter("api_version", "1.0")
                .addQueryParameter("api_token", apiToken)
                .build()
        val body = (payload ?: JSONObject()).toString().toRequestBody(JSON_MEDIA)
        val request =
            Request
                .Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Cookie", "arl=$arl")
                .post(body)
                .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("gateway HTTP ${response.code}")
            return JSONObject(response.body?.string().orEmpty())
        }
    }

    private fun matchTrack(
        session: Session,
        query: Query,
    ): TrackMatching.Candidate? {
        val key = query.cacheKey()
        val now = System.currentTimeMillis()
        searchCache[key]?.let { (candidate, expiresAt) ->
            if (expiresAt > now) return candidate
            searchCache.remove(key)
        }

        query.isrc?.takeIf { it.isNotBlank() }?.let { isrc ->
            val exact =
                runCatching { lookupByIsrc(isrc) }
                    .onFailure { Timber.tag(TAG).w(it, "isrc lookup failed for %s", isrc) }
                    .getOrNull()
            if (exact != null) {
                searchCache[key] = exact to (now + SEARCH_CACHE_MS)
                return exact
            }
        }

        val terms = listOf(query.title) + query.artists.take(1)
        val payload =
            JSONObject()
                .put("query", terms.joinToString(" ").trim())
                .put("start", 0)
                .put("nb", SEARCH_LIMIT)
        val json = gateway(session.arl, session.apiToken, "search.music", payload)
        val data = json.optJSONObject("results")?.optJSONArray("data") ?: JSONArray()

        val candidates =
            (0 until data.length()).mapNotNull { i ->
                val obj = data.optJSONObject(i) ?: return@mapNotNull null
                val id = obj.optString("SNG_ID").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                TrackMatching.Candidate(
                    id = id,
                    title = obj.optString("SNG_TITLE"),
                    artists = listOfNotNull(obj.optString("ART_NAME").takeIf { it.isNotBlank() }),
                    album = obj.optString("ALB_TITLE").takeIf { it.isNotBlank() },

                    durationMs = obj.optLong("DURATION", 0L).takeIf { it > 0L }?.times(1000L),
                )
            }

        val best =
            TrackMatching.best(
                target =
                    TrackMatching.Target(
                        title = query.title,
                        artists = query.artists,
                        album = query.album,
                        durationMs = query.durationMs,
                    ),
                candidates = candidates,
            )
        searchCache[key] = best to (now + SEARCH_CACHE_MS)
        return best
    }

    private fun lookupByIsrc(isrc: String): TrackMatching.Candidate? {
        val req =
            Request
                .Builder()
                .url("https://api.deezer.com/track/isrc:${java.net.URLEncoder.encode(isrc, "UTF-8")}")
                .header("Accept", "application/json")
                .build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return null
            val obj = JSONObject(res.body?.string() ?: return null)

            if (obj.has("error")) return null
            val id = obj.optLong("id", 0L).takeIf { it > 0L }?.toString() ?: return null
            val title = obj.optString("title").takeIf { it.isNotBlank() } ?: return null
            val artist = obj.optJSONObject("artist")?.optString("name")?.takeIf { it.isNotBlank() }
            val album = obj.optJSONObject("album")?.optString("title")?.takeIf { it.isNotBlank() }
            val durationMs = obj.optLong("duration", 0L).takeIf { it > 0L }?.times(1000L)
            return TrackMatching.Candidate(
                id = id,
                title = title,
                artists = listOfNotNull(artist),
                album = album,
                durationMs = durationMs,
            )
        }
    }

    private class Media(
        val url: String,
        val flac: Boolean,

        val format: String,
        val contentLength: Long?,

        val expiresAtMs: Long? = null,
    )

    private fun requestUrl(
        session: Session,
        trackId: String,
        format: String,
    ): Media? {
        val trackJson =
            gateway(
                session.arl,
                session.apiToken,
                "song.getData",
                JSONObject().put("sng_id", trackId),
            )
        val trackToken = trackJson.optJSONObject("results")?.optString("TRACK_TOKEN").orEmpty()
        if (trackToken.isBlank()) return null

        val requested = if (format == FORMAT_FLAC && !session.lossless) FORMAT_MP3_320 else format
        val formats = FORMAT_TIERS.dropWhile { it != requested }.ifEmpty { listOf(FORMAT_MP3_320, FORMAT_MP3_128) }
        val formatArray = JSONArray()
        formats.forEach { tier ->
            formatArray.put(JSONObject().put("cipher", "BF_CBC_STRIPE").put("format", tier))
        }
        val mediaArray =
            JSONArray().put(
                JSONObject()
                    .put("type", "FULL")
                    .put("formats", formatArray),
            )
        val cipherPayload =
            JSONObject()
                .put("license_token", session.licenseToken)
                .put("media", mediaArray)
                .put("track_tokens", JSONArray().put(trackToken))

        val request =
            Request
                .Builder()
                .url(MEDIA_ENDPOINT)
                .header("User-Agent", USER_AGENT)
                .header("Cookie", "arl=${session.arl}")
                .post(cipherPayload.toString().toRequestBody(JSON_MEDIA))
                .build()

        val json =
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw java.io.IOException("get_url HTTP ${response.code}")
                JSONObject(response.body?.string().orEmpty())
            }

        val first = json.optJSONArray("data")?.optJSONObject(0) ?: return null
        val media = first.optJSONArray("media")?.optJSONObject(0) ?: return null
        val source = media.optJSONArray("sources")?.optJSONObject(0) ?: return null
        val url = source.optString("url").takeIf { it.isNotBlank() } ?: return null

        val servedFormat = media.optString("format")
        val flac = servedFormat.equals(FORMAT_FLAC, ignoreCase = true)

        val sizeField = "FILESIZE_${servedFormat.uppercase()}"
        val results = trackJson.optJSONObject("results")
        val contentLength =
            results?.optString(sizeField)?.toLongOrNull()?.takeIf { it > 0L }
                ?: results?.optString("FILESIZE")?.toLongOrNull()?.takeIf { it > 0L }

        return Media(url = url, flac = flac, format = servedFormat, contentLength = contentLength)
    }

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaTypeOrNull()

    suspend fun lookup(query: Query): Metadata? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val q = buildSearchQuery(query)
                val req =
                    Request
                        .Builder()
                        .url("https://api.deezer.com/search?q=${java.net.URLEncoder.encode(q, "UTF-8")}&limit=10")
                        .header("Accept", "application/json")
                        .build()
                client.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) {
                        Timber.tag(TAG).w("search HTTP %d for '%s'", res.code, q)
                        return@use null
                    }
                    val body = res.body?.string() ?: return@use null
                    val root = JSONObject(body)
                    val data = root.optJSONArray("data") ?: return@use null
                    if (data.length() == 0) return@use null

                    val candidates =
                        (0 until data.length()).mapNotNull { i ->
                            val obj = data.optJSONObject(i) ?: return@mapNotNull null
                            val title = obj.optString("title").ifBlank { return@mapNotNull null }
                            val artist = obj.optJSONObject("artist")?.optString("name")?.ifBlank { null }
                            val album = obj.optJSONObject("album")?.optString("title")?.ifBlank { null }
                            val durationSec = obj.optLong("duration", 0L).takeIf { it > 0 }
                            val isrc = obj.optString("isrc").ifBlank { null }
                            val preview = obj.optString("preview").ifBlank { null }
                            val cover = obj.optJSONObject("album")?.optString("cover_big")?.ifBlank { null }
                            val score = scoreCandidate(query, title, artist, album, durationSec)
                            Metadata(
                                trackId = obj.optLong("id").toString(),
                                title = title,
                                artist = artist,
                                album = album,
                                isrc = isrc,
                                durationMs = durationSec?.times(1000L),
                                previewUrl = preview,
                                coverUrl = cover,
                            ) to score
                        }
                    val best = candidates.maxByOrNull { it.second } ?: return@use null
                    if (best.second < MIN_MATCH_SCORE) {
                        Timber.tag(TAG).d("best candidate score %.2f below threshold for '%s'", best.second, q)
                        return@use null
                    }
                    best.first
                }
            }.getOrNull()
        }

    suspend fun searchCandidates(
        term: String,
        limit: Int = SEARCH_LIMIT,
    ): List<Metadata> {
        val trimmed = term.trim()
        if (trimmed.isEmpty()) return emptyList()
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val viaPublicApi =
                runCatching {
                    val encoded = java.net.URLEncoder.encode(trimmed, "UTF-8")
                    val req =
                        Request
                            .Builder()
                            .url("https://api.deezer.com/search?q=$encoded&limit=$limit")
                            .header("Accept", "application/json")
                            .build()
                    client.newCall(req).execute().use { res ->
                        if (!res.isSuccessful) {
                            Timber.tag(TAG).w("searchCandidates HTTP %d for '%s'", res.code, trimmed)
                            return@use emptyList<Metadata>()
                        }
                        val data =
                            JSONObject(res.body?.string() ?: return@use emptyList<Metadata>())
                                .optJSONArray("data") ?: return@use emptyList<Metadata>()
                        (0 until data.length()).mapNotNull { i ->
                            val obj = data.optJSONObject(i) ?: return@mapNotNull null
                            val title = obj.optString("title").ifBlank { return@mapNotNull null }
                            Metadata(
                                trackId = obj.optLong("id").toString(),
                                title = title,
                                artist = obj.optJSONObject("artist")?.optString("name")?.ifBlank { null },
                                album = obj.optJSONObject("album")?.optString("title")?.ifBlank { null },
                                isrc = obj.optString("isrc").ifBlank { null },
                                durationMs = obj.optLong("duration", 0L).takeIf { it > 0 }?.times(1000L),
                                previewUrl = obj.optString("preview").ifBlank { null },
                                coverUrl = obj.optJSONObject("album")?.optString("cover_big")?.ifBlank { null },
                            )
                        }
                    }
                }.onFailure { Timber.tag(TAG).w(it, "searchCandidates failed for '%s'", trimmed) }
                    .getOrDefault(emptyList())
            if (viaPublicApi.isNotEmpty()) {
                viaPublicApi
            } else {
                searchCandidatesViaGateway(trimmed, limit)
            }
        }
    }

    private fun searchCandidatesViaGateway(
        term: String,
        limit: Int,
    ): List<Metadata> {
        val accounts = accounts()
        if (accounts.isEmpty()) return emptyList()
        for (account in accounts.sortedByDescending { it.premium }) {
            val session =
                runCatching { session(account) }
                    .onFailure { Timber.tag(TAG).w(it, "gateway search: session failed") }
                    .getOrNull() ?: continue
            val results =
                runCatching {
                    val payload =
                        JSONObject()
                            .put("query", term)
                            .put("start", 0)
                            .put("nb", limit)
                    val json = gateway(session.arl, session.apiToken, "search.music", payload)
                    json.optJSONObject("results")?.optJSONArray("data") ?: JSONArray()
                }.onFailure { Timber.tag(TAG).w(it, "gateway search failed for '%s'", term) }
                    .getOrNull() ?: continue
            val mapped =
                (0 until results.length()).mapNotNull { i ->
                    val obj = results.optJSONObject(i) ?: return@mapNotNull null
                    val id = obj.optString("SNG_ID").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val title = obj.optString("SNG_TITLE").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    Metadata(
                        trackId = id,
                        title = title,
                        artist = obj.optString("ART_NAME").takeIf { it.isNotBlank() },
                        album = obj.optString("ALB_TITLE").takeIf { it.isNotBlank() },
                        isrc = obj.optString("ISRC").takeIf { it.isNotBlank() },
                        durationMs = obj.optLong("DURATION", 0L).takeIf { it > 0 }?.times(1000L),
                        previewUrl = null,
                        coverUrl = null,
                    )
                }
            if (mapped.isNotEmpty()) return mapped
        }
        return emptyList()
    }

    private fun buildSearchQuery(query: Query): String {
        val parts = mutableListOf<String>()
        query.artists.firstOrNull()?.takeIf(String::isNotBlank)?.let { parts.add("artist:\"$it\"") }
        parts.add("track:\"${query.title}\"")
        query.album?.takeIf(String::isNotBlank)?.let { parts.add("album:\"$it\"") }
        return parts.joinToString(" ")
    }

    private fun scoreCandidate(
        query: Query,
        candidateTitle: String,
        candidateArtist: String?,
        candidateAlbum: String?,
        candidateDurationSec: Long?,
    ): Double {
        val titleScore = normalizedSimilarity(query.title, candidateTitle)
        val artistScore =
            query.artists.firstOrNull()?.let { a ->
                candidateArtist?.let { c -> normalizedSimilarity(a, c) }
            } ?: 0.0
        val albumScore =
            query.album?.let { q ->
                candidateAlbum?.let { c -> normalizedSimilarity(q, c) }
            } ?: 0.5
        val durationScore =
            query.durationMs?.let { qd ->
                candidateDurationSec?.let { cs ->
                    val cd = cs * 1000L
                    val diff = kotlin.math.abs(qd - cd)
                    when {
                        diff < 2_000L -> 1.0
                        diff < 5_000L -> 0.85
                        diff < 10_000L -> 0.6
                        else -> 0.2
                    }
                }
            } ?: 0.5

        return titleScore * 0.45 + artistScore * 0.30 + albumScore * 0.15 + durationScore * 0.10
    }

    private fun normalizedSimilarity(a: String, b: String): Double {
        val normalize: (String) -> String = { s ->
            s.lowercase().trim().replace(Regex("[^\\p{L}\\p{N} ]"), "")
        }
        val na = normalize(a)
        val nb = normalize(b)
        if (na == nb) return 1.0
        if (na.isBlank() || nb.isBlank()) return 0.0
        val sa = na.split(" ").toSet()
        val sb = nb.split(" ").toSet()
        val inter = sa.intersect(sb).size.toDouble()
        val union = sa.union(sb).size.toDouble()
        return if (union == 0.0) 0.0 else inter / union
    }

    suspend fun getLyrics(
        title: String,
        artist: String,
        album: String?,
        durationMs: Long?,
    ): Result<String> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val account = accounts().firstOrNull()
                    ?: throw java.io.IOException("no Deezer account for lyrics")
                val session = session(account)

                val query = Query(mediaId = "lyrics", title = title, artists = listOfNotNull(artist), album = album, durationMs = durationMs)
                val match = lookup(query) ?: throw java.io.IOException("no Deezer match for lyrics")

                val payload = JSONObject().put("song_id", match.trackId.toLong())
                val json = gateway(session.arl, session.apiToken, method = "song.getLyrics", payload = payload)
                val lyricsObj = json.optJSONObject("results")?.optJSONObject("lyrics")
                    ?: throw java.io.IOException("no lyrics in gateway response")
                val synced = lyricsObj.optString("text_time_synced").takeIf { it.isNotBlank() }
                val plain = lyricsObj.optString("text").takeIf { it.isNotBlank() }
                synced ?: plain ?: throw java.io.IOException("empty lyrics")
            }
        }
}
