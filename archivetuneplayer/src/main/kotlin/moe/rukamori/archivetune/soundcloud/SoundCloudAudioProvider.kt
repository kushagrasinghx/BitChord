/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.soundcloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.audiosource.DirectStream
import moe.rukamori.archivetune.constants.AudioSourceType
import moe.rukamori.archivetune.constants.SoundCloudAudioQuality
import moe.rukamori.archivetune.utils.soundcloud.normalizeSoundCloudAuthInput
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs

object SoundCloudAudioProvider {
    const val STREAM_MARKER_QUERY = "_archivetune_soundcloud"
    const val STREAM_HLS_MARKER_QUERY = "_archivetune_soundcloud_hls"
    const val STREAM_SOURCE_QUERY = "_archivetune_soundcloud_source"
    const val STREAM_SOURCE_API = "api"
    const val HLS_SCHEME = "soundcloud-hls"
    const val HLS_MIME_TYPE = "video/mp2t"
    const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36"

    private const val TAG = "SoundCloudAudio"
    private const val API_BASE_URL = "https://api-v2.soundcloud.com"
    private const val STREAM_MARKER_VALUE = "1"
    private const val STREAM_CACHE_MS = 5 * 60 * 1000L
    private const val SEARCH_LIMIT = 20
    private const val DEFAULT_BITRATE = 128_000
    private const val DEFAULT_SAMPLE_RATE = 44_100
    private val assetScriptRegex = Regex("""https://[a-z0-9-]+\.sndcdn\.com/[^"'<>!]+\.js""")
    private val clientIdRegex = Regex("""client_?id["']?\s*[:=]\s*["']([A-Za-z0-9]{20,})["']""", RegexOption.IGNORE_CASE)
    private val appVersionRegex = Regex("""app_?version["']?\s*[:=]\s*["']([0-9]{8,})["']""", RegexOption.IGNORE_CASE)

    data class Query(
        val mediaId: String,
        val title: String,
        val artists: List<String>,
        val album: String?,
        val durationMs: Long?,
        val directTrackUrl: String? = null,
    ) {
        val directUrl: String?
            get() = directTrackUrl?.takeIf { it.isNotBlank() }?.let { url ->
                url.toHttpUrlOrNull()?.let { parsed ->
                    val host = parsed.host.lowercase(Locale.US)
                    if (host == "soundcloud.com" || host.endsWith(".soundcloud.com")) url else null
                } ?: url.toSoundCloudUrlOrNull()
            }
    }

    data class SoundCloudTrackMetadata(
        val trackId: String,
        val title: String,
        val artist: String,
        val permalinkUrl: String,
        val artworkUrl: String?,
        val durationMs: Long?,
    )

    private data class Resolved(
        val mediaUri: String,
        val trackId: String,
        val permalinkUrl: String,
        val title: String,
        val artist: String,
        val artworkUrl: String?,
        val durationMs: Long?,
        val mimeType: String,
        val codecs: String,
        val bitrate: Int,
        val sampleRate: Int?,
        val contentLength: Long?,
        val expiresAtMs: Long,
    )

    class SoundCloudResolutionException(message: String, cause: Throwable? = null) : Exception(message, cause)

    private data class MatchedTrack(
        val trackId: String,
        val title: String,
        val artist: String,
        val artistNames: List<String>,
        val permalinkUrl: String,
        val artworkUrl: String?,
        val durationMs: Long?,
        val trackAuthorization: String?,
        val transcodings: JSONArray?,
    )

    private data class StreamCandidate(
        val url: String,
        val protocol: String,
        val mimeType: String,
        val preset: String,
        val isHls: Boolean,
        val bitrate: Int?,
        val sampleRate: Int?,
    )

    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

    private val streamCache = ConcurrentHashMap<String, Resolved>()
    private val trackCache = ConcurrentHashMap<String, MatchedTrack>()

    @Volatile
    private var cachedClientId: String? = null
    @Volatile
    private var cachedAppVersion: String? = null
    @Volatile
    private var metadataExpiresAt: Long = 0

    private val metadataMutex = Mutex()

    suspend fun resolve(
        query: Query,
        authToken: String = "",
        quality: SoundCloudAudioQuality = SoundCloudAudioQuality.AAC_160,
    ): DirectStream? = withContext(Dispatchers.IO) {
        val hasAuthToken = normalizeSoundCloudAuthInput(authToken) != null
        val streamCacheKey = query.cacheKey(hasAuthToken, quality)
        val now = System.currentTimeMillis()
        streamCache[streamCacheKey]
            ?.takeIf { it.expiresAtMs > now + 20_000L }
            ?.let { return@withContext it.toDirectStream(query, quality) }

        var clientId = getClientId()
        if (clientId.isBlank()) {
            Timber.tag(TAG).w("Failed to scrape SoundCloud client ID; cannot resolve audio for ${query.title}")
            return@withContext null
        }

        suspend fun lookupTrack(id: String): MatchedTrack? = coroutineScope {
            val resolveTask = async {
                (query.directUrl ?: query.mediaId.toSoundCloudUrlOrNull())?.let { url ->
                    resolveApiV2Track(url, id)
                }
            }
            val findTask = async {
                if (query.directUrl == null && query.mediaId.toSoundCloudUrlOrNull() == null) {
                    findBestTrack(query, id)
                } else null
            }
            resolveTask.await() ?: findTask.await()
        }

        val resolvedTrack: MatchedTrack =
            (if (query.directUrl == null) trackCache[query.mediaId] else null)
                ?: lookupTrack(clientId)
                ?: run {

                    Timber.tag(TAG).i(
                        "SoundCloud track lookup empty for ${query.title}; re-scraping client ID and retrying",
                    )
                    clientId = getClientId(forceRefresh = true)
                    lookupTrack(clientId)
                }
                ?: run {
                    Timber.tag(TAG).i("SoundCloud match not found for ${query.title}")
                    return@withContext null
                }
        trackCache[query.mediaId] = resolvedTrack

        val expectedDurationMs = query.durationMs ?: resolvedTrack.durationMs

        val streamResult = runCatching {
            resolveApiV2Stream(
                track = resolvedTrack,
                clientId = clientId,
                expectedDurationMs = expectedDurationMs,
                now = now,
                quality = quality,
            )
        }

        val apiStream = if (streamResult.isFailure && streamResult.exceptionOrNull() is SoundCloudResolutionException) {
            val error = streamResult.exceptionOrNull() as SoundCloudResolutionException
            val msg = error.message.orEmpty()
            when {
                msg.contains("403") || msg.contains("401") || msg.contains("429") -> {
                    Timber.tag(TAG).i("SoundCloud client ID revoked or rate limited ($msg); re-scraping and retrying")
                    clientId = getClientId(forceRefresh = true)
                    runCatching {
                        resolveApiV2Stream(
                            track = resolvedTrack,
                            clientId = clientId,
                            expectedDurationMs = expectedDurationMs,
                            now = now,
                            quality = quality,
                        )
                    }.getOrNull()
                }
                else -> null
            }
        } else {
            streamResult.getOrNull()
        }

        apiStream?.also { resolved ->
            streamCache[streamCacheKey] = resolved
            return@withContext resolved.toDirectStream(query, quality)
        }

        Timber.tag(TAG).w("SoundCloud stream resolution failed for ${query.title} via official API")
        null
    }

    fun invalidate(mediaId: String) {
        val prefix = "$mediaId::"
        for (key in streamCache.keys) {
            if (key.startsWith(prefix)) {
                streamCache.remove(key)
            }
        }
        trackCache.remove(mediaId)
    }

    fun invalidateClientId() {
        metadataExpiresAt = 0L
    }

    fun isSoundCloudUrl(value: String): Boolean = value.toSoundCloudUrlOrNull() != null

    fun isSoundCloudPlaybackUrl(url: HttpUrl): Boolean {
        val host = url.host.lowercase(Locale.US)
        return host == "playback.media-streaming.soundcloud.cloud" ||
            host.endsWith(".sndcdn.com")
    }

    suspend fun clientId(): String = getClientId()

    suspend fun getAppVersion(): String? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        cachedAppVersion?.takeIf { now < metadataExpiresAt }?.let { return@withContext it }

        metadataMutex.withLock {
            val recheck = System.currentTimeMillis()
            cachedAppVersion?.takeIf { recheck < metadataExpiresAt }?.let { return@withLock it }

            scrapeMetadata()
            cachedAppVersion
        }
    }

    data class SoundCloudUserResult(
        val userId: String,
        val username: String,
        val avatarUrl: String?,
        val permalinkUrl: String,
        val trackCount: Int?,
        val isVerified: Boolean,
    )

    data class SoundCloudPlaylistResult(
        val playlistId: String,
        val title: String,
        val artworkUrl: String?,
        val permalinkUrl: String,
        val trackCount: Int,
        val isAlbum: Boolean,
        val tracks: List<SoundCloudTrackMetadata>,
    )

    suspend fun searchUsers(
        term: String,
        limit: Int = SEARCH_LIMIT,
        clientId: String? = null,
    ): List<SoundCloudUserResult> = withContext(Dispatchers.IO) {
        if (term.isBlank()) return@withContext emptyList()

        val effectiveClientId = clientId ?: getClientId()
        if (effectiveClientId.isBlank()) return@withContext emptyList()

        val results = searchCollection("search/users", term, effectiveClientId, limit) ?: return@withContext emptyList()
        buildList {
            for (index in 0 until results.length()) {
                val obj = results.optJSONObject(index) ?: continue
                if (!obj.optString("kind").equals("user", ignoreCase = true)) continue
                val userId = obj.stringOrNull("id") ?: continue
                val username = obj.stringOrNull("username") ?: continue
                val permalinkUrl = obj.stringOrNull("permalink_url") ?: continue
                add(
                    SoundCloudUserResult(
                        userId = userId,
                        username = username,
                        avatarUrl = obj.stringOrNull("avatar_url")?.let(::highResArtworkUrl),
                        permalinkUrl = permalinkUrl,
                        trackCount = obj.intOrNull("track_count"),
                        isVerified = obj.optBoolean("verified", false),
                    ),
                )
            }
        }
    }

    suspend fun searchPlaylists(
        term: String,
        limit: Int = SEARCH_LIMIT,
        clientId: String? = null,
    ): List<SoundCloudPlaylistResult> = withContext(Dispatchers.IO) {
        if (term.isBlank()) return@withContext emptyList()

        val effectiveClientId = clientId ?: getClientId()
        if (effectiveClientId.isBlank()) return@withContext emptyList()

        val results = searchCollection("search/playlists", term, effectiveClientId, limit) ?: return@withContext emptyList()
        buildList {
            for (index in 0 until results.length()) {
                val obj = results.optJSONObject(index) ?: continue
                val kind = obj.optString("kind")
                if (!kind.equals("playlist", ignoreCase = true) && !kind.equals("system-playlist", ignoreCase = true)) continue
                val playlistId = obj.stringOrNull("id") ?: continue
                val title = obj.stringOrNull("title") ?: continue
                val permalinkUrl = obj.stringOrNull("permalink_url") ?: continue
                val embeddedTracks =
                    buildList {
                        val tracksArray = obj.optJSONArray("tracks") ?: return@buildList
                        for (trackIndex in 0 until tracksArray.length()) {
                            val trackObj = tracksArray.optJSONObject(trackIndex) ?: continue
                            if (!trackObj.optString("kind").equals("track", ignoreCase = true)) continue
                            if (!trackObj.optBoolean("streamable", true)) continue
                            trackObj.toMatchedTrack()?.toTrackMetadata()?.let(::add)
                        }
                    }
                add(
                    SoundCloudPlaylistResult(
                        playlistId = playlistId,
                        title = title,
                        artworkUrl = obj.stringOrNull("artwork_url")?.let(::highResArtworkUrl),
                        permalinkUrl = permalinkUrl,
                        trackCount = obj.intOrNull("track_count") ?: embeddedTracks.size,
                        isAlbum = obj.optBoolean("is_album", false),
                        tracks = embeddedTracks,
                    ),
                )
            }
        }
    }

    suspend fun searchMetadata(
        term: String,
        limit: Int = SEARCH_LIMIT,
        clientId: String? = null,
    ): List<SoundCloudTrackMetadata> = withContext(Dispatchers.IO) {
        if (term.isBlank()) return@withContext emptyList()

        val effectiveClientId = clientId ?: getClientId()
        if (effectiveClientId.isBlank()) return@withContext emptyList()

        val results = searchTracks(term, effectiveClientId, limit) ?: return@withContext emptyList()
        buildList {
            for (index in 0 until results.length()) {
                val obj = results.optJSONObject(index) ?: continue
                if (!obj.optString("kind").equals("track", ignoreCase = true)) continue
                if (!obj.optBoolean("streamable", true)) continue
                obj.toMatchedTrack()?.toTrackMetadata()?.let(::add)
            }
        }
    }

    fun addPlaybackHeaders(
        builder: Request.Builder,
        hasRangeHeader: Boolean,
        isApiStream: Boolean = false,
        isHlsStream: Boolean = false,
    ): Request.Builder {
        builder
            .header("User-Agent", BROWSER_USER_AGENT)
            .header("Accept", "audio/*,*/*;q=0.8")
            .header("Accept-Encoding", "identity")
            .header("Referer", "https://soundcloud.com/")
            .header("Origin", "https://soundcloud.com")

        if (isHlsStream) {
            builder.header("Connection", "keep-alive")
        }

        if (!hasRangeHeader && !isHlsStream) {
            builder.header("Range", "bytes=0-")
        }

        return builder
    }

    private suspend fun getClientId(forceRefresh: Boolean = false): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            cachedClientId?.takeIf { now < metadataExpiresAt }?.let { return@withContext it }
        }

        metadataMutex.withLock {
            val recheck = System.currentTimeMillis()
            if (!forceRefresh) {
                cachedClientId?.takeIf { recheck < metadataExpiresAt }?.let { return@withLock it }
            }

            scrapeMetadata()
            cachedClientId ?: ""
        }
    }

    private suspend fun scrapeMetadata() = coroutineScope {
        val homeRequest = Request.Builder()
            .url("https://soundcloud.com")
            .get()
            .header("User-Agent", BROWSER_USER_AGENT)
            .build()

        val (html, scriptUrls) = runCatching {
            client.newCall(homeRequest).execute().use { response ->
                if (!response.isSuccessful) return@use "" to emptyList<String>()
                val body = response.body?.string().orEmpty()
                body to assetScriptRegex.findAll(body).map { it.value }.toList().distinct()
            }
        }.getOrDefault("" to emptyList())

        var foundClientId = clientIdRegex.find(html)?.groups?.get(1)?.value
        var foundAppVersion = appVersionRegex.find(html)?.groups?.get(1)?.value

        if (foundClientId == null || foundAppVersion == null) {

            val jsResponses = scriptUrls.map { scriptUrl ->
                async(Dispatchers.IO) {
                    runCatching {
                        val scriptRequest = Request.Builder()
                            .url(scriptUrl)
                            .get()
                            .header("User-Agent", BROWSER_USER_AGENT)
                            .build()
                        client.newCall(scriptRequest).execute().use { res ->
                            if (res.isSuccessful) res.body?.string().orEmpty() else ""
                        }
                    }.getOrDefault("")
                }
            }.awaitAll()

            for (js in jsResponses) {
                if (foundClientId == null) {
                    foundClientId = clientIdRegex.find(js)?.groups?.get(1)?.value
                }
                if (foundAppVersion == null) {
                    foundAppVersion = appVersionRegex.find(js)?.groups?.get(1)?.value
                }
                if (foundClientId != null && foundAppVersion != null) break
            }
        }

        cachedClientId = foundClientId
        cachedAppVersion = foundAppVersion
        metadataExpiresAt = System.currentTimeMillis() + 12 * 60 * 60 * 1000L

        if (foundClientId == null) {
            Timber.tag(TAG).w("SoundCloud client-id scraping failed; stream resolution will fail")
        }
    }

    private suspend fun findBestTrack(
        query: Query,
        clientId: String,
        forWaveform: Boolean = false,
    ): MatchedTrack? = coroutineScope {
        val terms = searchTerms(query, forWaveform)
        if (terms.isEmpty()) return@coroutineScope null

        suspend fun searchTerm(term: String): List<MatchedTrack> =
            searchTracks(term, clientId, limit = SEARCH_LIMIT)?.let { results ->
                buildList {
                    for (index in 0 until results.length()) {
                        val obj = results.optJSONObject(index) ?: continue
                        if (!obj.optString("kind").equals("track", ignoreCase = true)) continue
                        if (!obj.optBoolean("streamable", true)) continue
                        obj.toMatchedTrack()?.let(::add)
                    }
                }
            }.orEmpty()

        if (!forWaveform) {
            val allTracks = searchTerm(terms.first()).distinctBy { it.permalinkUrl }
            return@coroutineScope selectBestTrack(allTracks, query)
        }

        val termResults = terms.map { term -> async { searchTerm(term) } }.awaitAll()
        val allTracks = termResults.flatten().distinctBy { it.permalinkUrl }
        selectBestTrack(allTracks, query, forWaveform = true)
    }

    private suspend fun searchTracks(
        term: String,
        clientId: String,
        limit: Int = SEARCH_LIMIT,
    ): JSONArray? = searchCollection("search/tracks", term, clientId, limit)

    private suspend fun searchCollection(
        path: String,
        term: String,
        clientId: String,
        limit: Int,
    ): JSONArray? = withContext(Dispatchers.IO) {
        val url = "$API_BASE_URL/$path".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("q", term)
            ?.addQueryParameter("limit", limit.coerceIn(1, 50).toString())
            ?.addQueryParameter("client_id", clientId)
            ?.addQueryParameter("app_locale", "en")
            ?.build() ?: return@withContext null

        runCatching {
            client.newCall(apiRequest(url.toString()).build()).execute().use { response ->
                val payload = response.body?.string()?.takeIf { it.isNotBlank() } ?: return@use null
                if (!response.isSuccessful) {
                    throw SoundCloudResolutionException("SoundCloud search HTTP ${response.code}: ${payload.take(160)}")
                }
                JSONObject(payload).optJSONArray("collection")
            }
        }.getOrNull()
    }

    private suspend fun resolveApiV2Track(
        url: String,
        clientId: String,
    ): MatchedTrack? = withContext(Dispatchers.IO) {
        val resolveUrl = "$API_BASE_URL/resolve".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("url", url)
            ?.addQueryParameter("client_id", clientId)
            ?.addQueryParameter("app_locale", "en")
            ?.build()
            ?: return@withContext null
        val request = apiRequest(resolveUrl.toString()).build()

        runCatching {
            client.newCall(request).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw SoundCloudResolutionException("SoundCloud resolve HTTP ${response.code}: ${payload.take(160)}")
                }
                payload.takeIf { it.isNotBlank() }
                    ?.let(::JSONObject)
                    ?.takeIf { it.optString("kind").equals("track", ignoreCase = true) }
                    ?.toMatchedTrack()
            }
        }.getOrNull()
    }

    private suspend fun resolveApiV2Stream(
        track: MatchedTrack,
        clientId: String,
        expectedDurationMs: Long?,
        now: Long,
        quality: SoundCloudAudioQuality,
    ): Resolved? = withContext(Dispatchers.IO) {
        val effectiveTrack = if (track.transcodings == null) {
            fetchFullTrack(track.trackId, clientId) ?: track
        } else {
            track
        }
        val candidate = selectStreamCandidate(effectiveTrack.transcodings, quality) ?: return@withContext null
        val streamUrl = resolveTranscodingUrl(candidate, effectiveTrack.trackAuthorization, clientId) ?: return@withContext null

        val (mediaUri, mimeType) =
            if (candidate.isHls) {
                Timber.tag(TAG).i(
                    "SoundCloud track %s resolved through HLS transcoding (progressive unavailable)",
                    track.trackId,
                )
                hlsStreamUri(streamUrl) to HLS_MIME_TYPE
            } else {
                addStreamMarker(
                    url = streamUrl,
                    isHls = false,
                    source = STREAM_SOURCE_API,
                ) to candidate.mimeType
            }

        val codecs = if (candidate.isHls) {
            if (candidate.mimeType.contains("aac") || candidate.mimeType.contains("mp4")) "mp4a.40.2" else "mp3"
        } else {
            candidate.mimeType.toCodecs()
        }

        Resolved(
            mediaUri = mediaUri,
            trackId = track.trackId,
            permalinkUrl = track.permalinkUrl,
            title = track.title,
            artist = track.artist,
            artworkUrl = track.artworkUrl,
            durationMs = track.durationMs,
            mimeType = mimeType,
            codecs = codecs,
            bitrate = candidate.bitrate ?: DEFAULT_BITRATE,
            sampleRate = candidate.sampleRate ?: DEFAULT_SAMPLE_RATE,
            contentLength = null,
            expiresAtMs = now + STREAM_CACHE_MS,
        )
    }

    fun hlsStreamUri(playlistUrl: String): String {
        val encoded =
            android.util.Base64.encodeToString(
                playlistUrl.toByteArray(Charsets.UTF_8),
                android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING,
            )
        return "$HLS_SCHEME:$encoded"
    }

    private suspend fun fetchFullTrack(
        trackId: String,
        clientId: String,
    ): MatchedTrack? = withContext(Dispatchers.IO) {
        val url = "$API_BASE_URL/tracks/$trackId".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("client_id", clientId)
            ?.addQueryParameter("app_locale", "en")
            ?.build() ?: return@withContext null
        val request = apiRequest(url.toString()).build()

        runCatching {
            client.newCall(request).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw SoundCloudResolutionException("SoundCloud track HTTP ${response.code}: ${payload.take(160)}")
                }
                payload.takeIf { it.isNotBlank() }
                    ?.let(::JSONObject)
                    ?.takeIf { it.optString("kind").equals("track", ignoreCase = true) }
                    ?.toMatchedTrack()
            }
        }.getOrNull()
    }

    private fun selectStreamCandidate(
        transcodings: JSONArray?,
        quality: SoundCloudAudioQuality = SoundCloudAudioQuality.AAC_160,
    ): StreamCandidate? {
        if (transcodings == null) return null
        val candidates = buildList {
            for (index in 0 until transcodings.length()) {
                val transcoding = transcodings.optJSONObject(index) ?: continue
                val format = transcoding.optJSONObject("format") ?: continue
                val protocol = format.stringOrNull("protocol")?.lowercase(Locale.US) ?: continue
                val mimeType = format.stringOrNull("mime_type")?.lowercase(Locale.US) ?: continue
                if (protocol !in setOf("progressive", "hls")) continue
                val isSupportedMime =
                    mimeType.contains("mpeg") ||
                        mimeType.contains("mp3") ||
                        mimeType.contains("aac") ||
                        mimeType.contains("mp4")
                if (!isSupportedMime) continue
                val streamUrl = transcoding.stringOrNull("url") ?: continue
                add(
                    StreamCandidate(
                        url = streamUrl,
                        protocol = protocol,
                        mimeType = mimeType,
                        preset = transcoding.stringOrNull("preset").orEmpty(),
                        isHls = protocol == "hls",
                        bitrate = transcoding.intOrNull("bitrate", "bit_rate")
                            ?: format.intOrNull("bitrate", "bit_rate")
                            ?: bitrateFromPreset(transcoding.stringOrNull("preset"), mimeType),
                        sampleRate = transcoding.intOrNull("sample_rate", "sampleRate", "audio_sample_rate")
                            ?: format.intOrNull("sample_rate", "sampleRate", "audio_sample_rate"),
                    ),
                )
            }
        }

        return candidates.maxWithOrNull(
            compareByDescending<StreamCandidate> {

                if (!it.isHls) 1_000 else 0
            }.thenByDescending {
                val targetBitrate = when (quality) {
                    SoundCloudAudioQuality.MP3_128 -> 128_000
                    SoundCloudAudioQuality.AAC_96 -> 96_000
                    SoundCloudAudioQuality.AAC_160 -> 160_000
                }
                val targetMime = when (quality) {
                    SoundCloudAudioQuality.MP3_128 -> "audio/mpeg"
                    SoundCloudAudioQuality.AAC_160 -> "audio/aac"
                    SoundCloudAudioQuality.AAC_96 -> "audio/aac"
                    else -> "audio/aac"
                }

                if (it.bitrate == targetBitrate && it.mimeType.contains(targetMime.substringAfter("/"))) 400
                else if (it.bitrate == targetBitrate) 300
                else 0
            }.thenByDescending {
                val targetMimePart = when (quality) {
                    SoundCloudAudioQuality.MP3_128 -> "mpeg"
                    else -> "aac"
                }
                if (it.mimeType.contains(targetMimePart)) 200 else 0
            }.thenByDescending {
                if (it.isHls && (quality == SoundCloudAudioQuality.AAC_160 || quality == SoundCloudAudioQuality.AAC_96)) 150 else 0
            }.thenByDescending {
                it.bitrate ?: 0
            }.thenByDescending {
                if (!it.isHls && quality == SoundCloudAudioQuality.MP3_128) 50 else 0
            },
        )
    }

    private suspend fun resolveTranscodingUrl(
        candidate: StreamCandidate,
        trackAuthorization: String?,
        clientId: String,
    ): String? = withContext(Dispatchers.IO) {
        val url = candidate.url.toHttpUrlOrNull()
            ?.newBuilder()
            ?.removeAllQueryParameters("client_id")
            ?.addQueryParameter("client_id", clientId)
            ?.apply {
                trackAuthorization?.takeIf { it.isNotBlank() }?.let {
                    removeAllQueryParameters("track_authorization")
                    addQueryParameter("track_authorization", it)
                }
            }
            ?.build()
            ?: return@withContext null

        runCatching {
            client.newCall(apiRequest(url.toString()).build()).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw SoundCloudResolutionException("SoundCloud transcoding HTTP ${response.code}: ${payload.take(160)}")
                }
                JSONObject(payload).stringOrNull("url")
            }
        }.getOrNull()
    }

    private fun selectBestTrack(
        tracks: List<MatchedTrack>,
        query: Query,
        forWaveform: Boolean = false,
    ): MatchedTrack? {
        val strict = selectBestTrackInternal(
            tracks = tracks,
            query = query,
            requireArtistMatch = true,
            minScore = 420,
            maxDurationDiffSeconds = 20,
        )
        if (strict != null) return strict

        if (tracks.isNotEmpty()) {
            Timber.tag(TAG).i(
                "selectBestTrack: no strict match among ${tracks.size} candidate(s) for '${query.title}' " +
                    "by '${query.artists.joinToString()}'; retrying with relaxed artist matching",
            )
        }

        val loose = selectBestTrackInternal(
            tracks = tracks,
            query = query,
            requireArtistMatch = false,
            minScore = 260,
            maxDurationDiffSeconds = 8,
        )
        if (loose != null) return loose

        if (tracks.isNotEmpty()) {
            Timber.tag(TAG).i(
                "selectBestTrack: no relaxed match either for '${query.title}' among ${tracks.size} candidate(s)" +
                    if (forWaveform) "; retrying with lenient matching" else "",
            )
        }

        if (!forWaveform) return null

        return selectBestTrackInternal(
            tracks = tracks,
            query = query,
            requireArtistMatch = false,
            minScore = 120,
            maxDurationDiffSeconds = 30,
            allowVersionMismatch = true,
        )
    }

    private fun selectBestTrackInternal(
        tracks: List<MatchedTrack>,
        query: Query,
        requireArtistMatch: Boolean,
        minScore: Int,
        maxDurationDiffSeconds: Long,
        allowVersionMismatch: Boolean = false,
    ): MatchedTrack? {
        val wantedTitle = query.title.normalized()
        val wantedArtists = query.artists.map { it.normalized() }.filter { it.isNotBlank() }
        val wantedAlbum = query.album.normalized()
        val wantedDescriptorText = listOf(wantedTitle, wantedAlbum).joinToString(" ")
        val wantedRawDescriptorText =
            listOf(query.title, query.album.orEmpty(), query.artists.joinToString(" ")).joinToString(" ")
        val wantedDurationMs = query.durationMs?.takeIf { it > 0L }
        val wantedTitleTokens = significantTokens(wantedTitle)

        data class Candidate(
            val track: MatchedTrack,
            val score: Int,
        )

        val candidates = mutableListOf<Candidate>()
        for (track in tracks) {
            val candidateTitle = track.title.normalized()
            val candidateArtists = track.artistNames
                .ifEmpty { listOf(track.artist) }
                .map { it.normalized() }
                .filter { it.isNotBlank() }
            val candidateDescriptorText = listOf(candidateTitle, candidateArtists.joinToString(" ")).joinToString(" ")
            val candidateRawDescriptorText =
                listOf(
                    track.title,
                    track.artist,
                    track.artistNames.joinToString(" "),
                ).joinToString(" ")

            if (!allowVersionMismatch && hasVersionMismatch(wantedRawDescriptorText, candidateRawDescriptorText)) continue
            if (!allowVersionMismatch && hasVersionMismatch(wantedDescriptorText, candidateDescriptorText)) continue

            val candidateTokens = significantTokens(candidateTitle)
            val matchedTokens = wantedTitleTokens.count(candidateTokens::contains)
            val titleCoverage =
                if (wantedTitleTokens.isEmpty()) {
                    0.0
                } else {
                    matchedTokens.toDouble() / wantedTitleTokens.size.toDouble()
                }
            val hasTitleMatch =
                wantedTitle.isBlank() ||
                    candidateTitle == wantedTitle ||
                    (wantedTitle.length >= 4 && (candidateTitle.contains(wantedTitle) || wantedTitle.contains(candidateTitle))) ||
                    titleCoverage >= if (wantedTitleTokens.size <= 2) 1.0 else 0.75
            val hasArtistMatch =
                !requireArtistMatch ||
                    wantedArtists.isEmpty() ||
                    wantedArtists.any { wanted ->
                        candidateArtists.any { candidate -> artistMatches(wanted, candidate) }
                    }
            val durationDiffSeconds =
                if (wantedDurationMs != null && track.durationMs != null) {
                    abs(wantedDurationMs - track.durationMs) / 1000L
                } else {
                    null
                }
            val hasSafeDuration = durationDiffSeconds == null || durationDiffSeconds <= maxDurationDiffSeconds

            if (!hasTitleMatch || !hasArtistMatch || !hasSafeDuration) continue

            var score = 0
            if (wantedTitle.isNotBlank()) {
                score += when {
                    candidateTitle == wantedTitle -> 340
                    candidateTitle.contains(wantedTitle) || wantedTitle.contains(candidateTitle) -> 140
                    wantedTitle.wordsOverlap(candidateTitle) >= 2 -> 80
                    else -> -100
                }
            }

            if (wantedTitleTokens.isNotEmpty()) {
                score += when {
                    matchedTokens == wantedTitleTokens.size -> 120
                    matchedTokens >= wantedTitleTokens.size.coerceAtLeast(1) - 1 -> 45
                    wantedTitleTokens.size <= 2 -> -150
                    else -> -70
                }
            }

            if (wantedArtists.isNotEmpty()) {
                score += when {
                    wantedArtists.any { wanted -> candidateArtists.any { it == wanted } } -> 230
                    wantedArtists.any { wanted -> candidateArtists.any { it.contains(wanted) || wanted.contains(it) } } -> 120
                    wantedArtists.any { wanted -> candidateArtists.any { wanted.wordsOverlap(it) >= 1 } } -> 40
                    else -> if (requireArtistMatch) -80 else 0
                }
            }

            if (durationDiffSeconds != null) {
                score += when {
                    durationDiffSeconds <= 2 -> 170
                    durationDiffSeconds <= 6 -> 110
                    durationDiffSeconds <= 12 -> 45
                    else -> -150
                }
            }

            if (score >= minScore) {
                candidates += Candidate(track, score)
            }
        }

        return candidates.maxByOrNull { it.score }?.track
    }

    private fun addStreamMarker(
        url: String,
        isHls: Boolean,
        source: String,
    ): String =
        url.toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter(STREAM_MARKER_QUERY, STREAM_MARKER_VALUE)
            ?.addQueryParameter(STREAM_SOURCE_QUERY, source)
            ?.apply {
                if (isHls) {
                    addQueryParameter(STREAM_HLS_MARKER_QUERY, "1")
                }
            }
            ?.build()
            ?.toString()
            ?: url

    private suspend fun apiRequest(
        url: String,
    ): Request.Builder = withContext(Dispatchers.IO) {
        val appVersion = getAppVersion()
        val finalUrl = if (appVersion != null && !url.contains("app_version=")) {
            url.toHttpUrlOrNull()?.newBuilder()?.addQueryParameter("app_version", appVersion)?.build()?.toString() ?: url
        } else {
            url
        }

        Request.Builder()
            .url(finalUrl)
            .get()
            .header("Accept", "application/json")
            .header("Referer", "https://soundcloud.com/")
            .header("Origin", "https://soundcloud.com")
            .header("User-Agent", BROWSER_USER_AGENT)
    }

    private fun searchTerms(query: Query, forWaveform: Boolean = false): List<String> {
        val title = query.title.trim()
        val artist = query.artists.firstOrNull().orEmpty().trim()
        val album = query.album.orEmpty().trim()

        val primaryTerm = if (artist.isNotBlank() && title.isNotBlank()) {
            "$artist - $title"
        } else if (title.isNotBlank()) {
            title
        } else {
            ""
        }

        if (!forWaveform) {
            return listOf(primaryTerm).filter { it.isNotBlank() }
        }

        val titleOnly = title
        val titleNoParens = title
            .replace(Regex("""\[[^]]*]"""), " ")
            .replace(Regex("""\([^)]*\)"""), " ")
            .trim()
            .replace(Regex("""\s+"""), " ")

        return listOf(primaryTerm, titleOnly, titleNoParens)
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun JSONObject.toMatchedTrack(): MatchedTrack? {
        if (stringOrNull("policy")?.equals("SNIP", ignoreCase = true) == true) return null
        val trackId = stringOrNull("id") ?: return null
        val title = stringOrNull("title") ?: return null
        val publisherArtist = optJSONObject("publisher_metadata")?.stringOrNull("artist")
        val userName = optJSONObject("user")?.stringOrNull("username")
        val labelName = stringOrNull("label_name")
        val artistNames = listOfNotNull(publisherArtist, userName, labelName)
            .flatMap(::splitArtistNames)
            .distinctBy { it.normalized() }
        val artist = artistNames.firstOrNull() ?: "Unknown Artist"
        val permalinkUrl = stringOrNull("permalink_url") ?: return null
        val durationMs = longOrNull("full_duration") ?: longOrNull("duration")
        val artworkUrl = stringOrNull("artwork_url")?.let(::highResArtworkUrl)
        return MatchedTrack(
            trackId = trackId,
            title = title,
            artist = artist,
            artistNames = artistNames,
            permalinkUrl = permalinkUrl,
            artworkUrl = artworkUrl,
            durationMs = durationMs,
            trackAuthorization = stringOrNull("track_authorization"),
            transcodings = optJSONObject("media")?.optJSONArray("transcodings"),
        )
    }

    private fun highResArtworkUrl(url: String): String {
        val marker = "-large."
        val index = url.lastIndexOf(marker)
        if (index < 0) return url
        return url.substring(0, index) + "-t500x500." + url.substring(index + marker.length)
    }

    private fun MatchedTrack.toTrackMetadata(): SoundCloudTrackMetadata =
        SoundCloudTrackMetadata(
            trackId = trackId,
            title = title,
            artist = artist,
            permalinkUrl = permalinkUrl,
            artworkUrl = artworkUrl,
            durationMs = durationMs,
        )

    private fun Resolved.toDirectStream(
        query: Query,
        quality: SoundCloudAudioQuality,
    ): DirectStream =
        DirectStream(
            uri = mediaUri,
            mimeType = when (mimeType) {
                "audio/mpeg" -> "audio/mpeg"
                HLS_MIME_TYPE -> HLS_MIME_TYPE
                else -> "audio/mp4"
            },
            codecs = codecs,
            contentLength = contentLength,
            label = "SoundCloud ${qualityLabel(quality)}",
            source = AudioSourceType.SOUNDCLOUD,
            matchedTitle = title,
            matchedArtist = artist,
            matchedAlbum = null,
            matchedDurationMs = durationMs,
            trustedDirectId = query.directUrl != null || query.mediaId.toSoundCloudUrlOrNull() != null,
            sampleRate = sampleRate,
            bitDepth = null,
        )

    private fun qualityLabel(quality: SoundCloudAudioQuality): String =
        when (quality) {
            SoundCloudAudioQuality.MP3_128 -> "MP3 128"
            SoundCloudAudioQuality.AAC_96 -> "AAC 96"
            SoundCloudAudioQuality.AAC_160 -> "AAC 160"
        }

    private fun String.toSoundCloudUrlOrNull(): String? {
        val url = toHttpUrlOrNull() ?: return null
        val host = url.host.lowercase(Locale.US)
        return if (host == "soundcloud.com" || host.endsWith(".soundcloud.com")) {
            this
        } else {
            null
        }
    }

    private fun String.toCodecs(): String {
        val lower = lowercase(Locale.US)
        return when {
            lower.contains("aac") || lower.contains("mp4") -> "mp4a.40.2"
            lower.contains("mpegurl") || lower.contains("m3u8") -> "mp3"
            lower.contains("mpeg") || lower.contains("mp3") -> "mp3"
            lower.contains("opus") -> "opus"
            else -> ""
        }
    }

    private fun bitrateFromPreset(
        preset: String?,
        mimeType: String,
    ): Int? {
        val lowerPreset = preset.orEmpty().lowercase(Locale.US)
        val lowerMime = mimeType.lowercase(Locale.US)
        return when {
            lowerPreset.contains("256") -> 256_000
            lowerPreset.contains("192") -> 192_000
            lowerPreset.contains("160") && lowerMime.contains("aac") -> 160_000
            lowerPreset.contains("128") -> 128_000
            lowerPreset.contains("96") && lowerMime.contains("aac") -> 96_000
            lowerPreset.contains("64") -> 64_000
            lowerMime.contains("mpeg") || lowerPreset.contains("mp3") -> DEFAULT_BITRATE
            lowerMime.contains("aac") || lowerMime.contains("mp4") -> DEFAULT_BITRATE
            else -> null
        }
    }

    private fun hasVersionMismatch(
        query: String,
        candidateTitle: String,
    ): Boolean {
        val versionTokens = listOf(
            "remix",
            "live",
            "edit",
            "acoustic",
            "instrumental",
            "karaoke",
            "remaster",
            "remastered",
            "sped up",
            "slowed",
            "bootleg",
            "mix",
            "cover",
            "vip",
            "extended",
        )
        val queryLower = query.lowercase(Locale.US)
        val candidateLower = candidateTitle.lowercase(Locale.US)
        val queryHasVersion = versionTokens.any { queryLower.contains(it) }
        val candidateHasVersion = versionTokens.any { candidateLower.contains(it) }
        return candidateHasVersion && !queryHasVersion
    }

    private fun artistMatches(
        wanted: String,
        candidate: String,
    ): Boolean {
        if (wanted.isBlank() || candidate.isBlank()) return false
        if (wanted == candidate) return true
        if ((wanted.length >= 4 && candidate.contains(wanted)) || (candidate.length >= 4 && wanted.contains(candidate))) {
            return true
        }
        val wantedTokens = significantTokens(wanted)
        val candidateTokens = significantTokens(candidate)
        if (wantedTokens.isEmpty() || candidateTokens.isEmpty()) return false
        val overlap = wantedTokens.intersect(candidateTokens).size
        return if (wantedTokens.size <= 1) {
            overlap == wantedTokens.size
        } else {
            overlap >= wantedTokens.size - 1
        }
    }

    private fun splitArtistNames(value: String): List<String> =
        value.split(Regex("""\s*(?:,|/|&|\+|\bfeat\.?\b|\bft\.?\b|\bwith\b)\s*""", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .filter { it.isNotBlank() }

    private fun Query.cacheKey(
        hasAuthToken: Boolean,
        quality: SoundCloudAudioQuality,
    ): String {
        return listOf(
            mediaId,
            title.normalized(),
            artists.joinToString("|") { it.normalized() },
            album.normalized(),
            durationMs?.toString().orEmpty(),
            quality.name,
            if (hasAuthToken) "auth" else "anon",
        ).joinToString("::")
    }

    private fun significantTokens(value: String): Set<String> {
        val stopWords = setOf("a", "an", "and", "feat", "ft", "for", "of", "the", "with")
        return value.split(" ")
            .map { it.trim() }
            .filter { it.length > 1 && it !in stopWords }
            .toSet()
    }

    private fun String?.normalized(): String {
        val ascii = Normalizer.normalize(this.orEmpty(), Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")
        return ascii
            .lowercase(Locale.US)
            .replace("&", " and ")
            .replace(Regex("""\[[^]]*]"""), " ")
            .replace(Regex("""\([^)]*\)"""), " ")
            .replace(Regex("""[^a-z0-9]+"""), " ")
            .trim()
            .replace(Regex("""\s+"""), " ")
    }

    private fun String.wordsOverlap(other: String): Int {
        val first = split(' ').filter { it.length > 1 }.toSet()
        val second = other.split(' ').filter { it.length > 1 }.toSet()
        return first.intersect(second).size
    }

    private fun JSONObject.stringOrNull(name: String): String? {
        return optString(name).trim().takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
    }

    private fun JSONObject.intOrNull(vararg names: String): Int? {
        names.forEach { name ->
            if (!has(name) || isNull(name)) return@forEach
            runCatching { getInt(name) }.getOrNull()?.let { return it }
            optString(name).trim().toIntOrNull()?.let { return it }
        }
        return null
    }

    private fun JSONObject.longOrNull(name: String): Long? {
        if (!has(name) || isNull(name)) return null
        return runCatching { getLong(name) }.getOrElse {
            optString(name).trim().toLongOrNull()
        }
    }
}
