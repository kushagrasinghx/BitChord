package com.music.bitchord.data.innertube

import com.music.bitchord.data.MonotonicClock
import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.InnerTubeLogLevel
import com.metrolist.innertubex.InnerTubeLogger
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.PoTokenResult
import com.metrolist.innertubex.extraction.TokenProvider
import com.metrolist.innertubex.extraction.TokenProviderCapabilities

import com.metrolist.innertubex.extraction.strategy.PoTokenProviderKind
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.extraction.generateClientPlaybackNonce
import com.metrolist.innertubex.models.YouTubeLocale
import com.music.bitchord.data.Http
import com.music.bitchord.data.TrackLog
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** YouTube stream extraction through InnerTubeX's live-benchmarked client catalog and cipher tiers. */
object InnerTubeXResolver {

    private const val TAG = "BitChord"

    /** A stream InnerTubeX found, and the identity its media fetch has to wear. */
    class Extracted(
        val videoId: String,
        val url: String,
        val kbps: Int,
        val mimeType: String,
        val loudnessDb: Double?,
        val clientName: String,
        /** InnerTubeX's catalog id for the exact client variant; what exclusions key on. */
        val profileId: String,
        val headers: Map<String, String>,
    )

    /**
     * Where the remote player config is remembered between runs — the phone's
     * SharedPreferences, a properties file on the desktop.
     */
    interface ConfigStore {
        fun getString(key: String): String?
        fun getLong(key: String): Long?
        fun putString(key: String, value: String)
        fun putLong(key: String, value: Long)
    }

    /** Whether InnerTubeX's DEBUG lines reach the track log — the phone's debug builds. */
    @Volatile
    var debugLogging: Boolean = false

    private var prefs: ConfigStore? = null
    private var poTokens: TokenProvider? = null
    private var playerDir: File? = null
    private var warmup: Job? = null

    /**
     * @param filesDir where the preprocessed EJS players are kept.
     * @param poTokenProvider the platform's BotGuard minter — the phone's
     *   WebView one. Null where there is none, which leaves the clients that
     *   need a PoToken out of the catalog.
     */
    fun init(filesDir: File, store: ConfigStore, poTokenProvider: TokenProvider?) {
        prefs = store
        poTokens = poTokenProvider
        playerDir = File(filesDir, "innertubex_players").apply { mkdirs() }
        scope.launch {
            cipherService.setPreprocessedPlayerCache(::readPlayer, ::writePlayer)
            warm(WARM_DELAY_MS)
        }
    }

    /**
     * Pays the cold costs before a track needs them: the player config, the EJS
     * solver (8.7s in QuickJS when zemer-cipher lacks the player's hash), and the
     * BotGuard WebView behind WEB_REMIX's PoToken. Measured cold, those put an
     * age-restricted track 15-20s from first audio when paid on the play path.
     */
    private fun warm(delayMs: Long) {
        warmup?.cancel()
        warmup = scope.launch {
            delay(delayMs)
            val start = MonotonicClock.nowMs()
            runCatching {
                innerTube.cookie = Innertube.cookie
                innerTube.visitorData = Innertube.ensureVisitorData()
                innerTube.locale = YouTubeLocale(gl = Innertube.currentRegion, hl = Innertube.currentLanguage)
                extractor.prewarm()
            }.onFailure { if (it is CancellationException) throw it }
                .onFailure { TrackLog.w(TAG, "InnerTubeX warm-up failed: ${it.message}") }
                .onSuccess { TrackLog.d(TAG, "InnerTubeX warmed in ${MonotonicClock.nowMs() - start}ms") }
        }
    }

    /**
     * EJS preprocessed players, kept across processes. Solving a player from
     * scratch is the 8.7s step; a preprocessed one is reused per player version,
     * so only the first launch after YouTube rotates its player pays it.
     */
    private fun readPlayer(key: String): String? =
        playerDir?.let { File(it, key) }?.takeIf { it.isFile }?.readText()

    private fun writePlayer(key: String, value: String?) {
        val dir = playerDir ?: return
        val file = File(dir, key)
        if (value == null) {
            file.delete()
            return
        }
        val tmp = File(dir, "$key.tmp")
        tmp.writeText(value)
        tmp.renameTo(file)
        // Players rotate every few days; only the newest are worth their megabytes.
        dir.listFiles()?.filter { !it.name.endsWith(".tmp") }?.sortedByDescending { it.lastModified() }
            ?.drop(KEPT_PLAYERS)?.forEach { it.delete() }
    }

    /** The installed PoToken provider, or one that can mint nothing when there is not one. */
    private val tokenProvider = object : TokenProvider {
        override val capabilities: TokenProviderCapabilities
            get() = poTokens?.capabilities ?: TokenProviderCapabilities(providers = emptySet(), usesWebView = false)

        override suspend fun getPoToken(videoId: String, visitorData: String, cookie: String?): PoTokenResult? =
            poTokens?.getPoToken(videoId, visitorData, cookie)

        override suspend fun close() {
            poTokens?.close()
        }
    }

    private val http = HttpClient(OkHttp) {
        engine { preconfigured = Http.client }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 20_000
        }
        expectSuccess = false
    }

    private val logger = InnerTubeLogger { event ->
        if (event.level == InnerTubeLogLevel.DEBUG && !debugLogging) return@InnerTubeLogger
        val details = if (event.details.isEmpty()) "" else event.details.entries.joinToString(prefix = " [", postfix = "]") { "${it.key}=${it.value}" }
        val line = "ITX ${event.tag}: ${event.message}$details"
        if (event.level == InnerTubeLogLevel.INFO || event.level == InnerTubeLogLevel.DEBUG) TrackLog.d(TAG, line) else TrackLog.w(TAG, line)
    }

    private val repository = object : PlayerConfigRepository {
        override val enabled: Boolean get() = prefs != null
        override val sourceUrl: String = PLAYER_CONFIG_URL
        override val defaultSourceUrl: String = PLAYER_CONFIG_URL
        override var cachedJson: String
            get() = prefs?.getString("json").orEmpty()
            set(value) { prefs?.putString("json", value) }
        override var cachedAtMs: Long
            get() = prefs?.getLong("cached_at_ms") ?: 0L
            set(value) { prefs?.putLong("cached_at_ms", value) }
        override var cachedSourceUrl: String
            get() = prefs?.getString("source_url").orEmpty()
            set(value) { prefs?.putString("source_url", value) }
        override var cachedEtag: String
            get() = prefs?.getString("etag").orEmpty()
            set(value) { prefs?.putString("etag", value) }
    }

    private val innerTube = InnerTube(http, logger = logger)
    private val remoteStore = RemotePlayerConfigStore(http, repository, logger)
    private val cipherService = YouTubeCipherService(http, remoteStore, logger)
    private val extractor = InnerTubeExtractor(
        configParser = YtConfigParserImpl(http, innerTube, remoteStore, logger),
        cipherService = cipherService,
        innerTube = innerTube,
        tokenProvider = tokenProvider,
        logger = logger,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** What each minted URL was, so its fetch can be dressed and its refusal attributed. */
    private val minted = ConcurrentHashMap<String, Extracted>()

    /** Clients refused a track mid-playback, by videoId, and until when. */
    private val excluded = ConcurrentHashMap<String, ConcurrentHashMap<String, Long>>()

    /**
     * InnerTubeX picks one format per ask and has no bitrate ceiling of its own:
     * [maxKbps] only chooses between its smallest and best rungs, and
     * [requireM4a] asks for its best AAC-in-MP4.
     *
     * [uploaded] says [videoId] is one of the signed-in user's own uploads. That
     * changes which clients InnerTubeX will use — authenticated ones only, login
     * cookies first, no embedded fallback — see [UploadedTracks].
     *
     * @return a stream for [videoId], or null when every client InnerTubeX would
     *   try is refused or [skipClients] has ruled it out.
     * @throws com.metrolist.innertubex.extraction.StreamResolveException with a
     *   reason when InnerTubeX exhausted its catalog.
     */
    suspend fun extract(
        videoId: String,
        maxKbps: Int,
        skipClients: Set<String> = emptySet(),
        requireM4a: Boolean = false,
        uploaded: Boolean = false,
    ): Extracted? {
        innerTube.cookie = Innertube.cookie
        innerTube.visitorData = Innertube.ensureVisitorData()
        innerTube.locale = YouTubeLocale(gl = Innertube.currentRegion, hl = Innertube.currentLanguage)
        val stream = extractor.extract(
            videoId = videoId,
            hints = ContentHints(isUploaded = true.takeIf { uploaded })
                .withStreamCapabilities(allowHls = false, allowSabr = false, allowBoundedRange = true),
            excludedClients = excludedFor(videoId) + skipClients,
            audioQuality = when {
                requireM4a -> AudioQuality.MP4
                maxKbps <= LOW_KBPS -> AudioQuality.LOW
                else -> AudioQuality.AUTO
            },
            clientPlaybackNonce = generateClientPlaybackNonce(),
        ) ?: return null
        check(stream.sabrBootstrap == null) { "SABR is not supported by this playback engine" }
        val mime = stream.mimeType.orEmpty()
        val extracted = Extracted(
            videoId = videoId,
            url = stream.audioUrl,
            kbps = (stream.bitrate ?: 0) / 1000,
            mimeType = if (stream.codecs.isNullOrBlank()) mime else "$mime; codecs=\"${stream.codecs}\"",
            loudnessDb = stream.loudnessDb,
            clientName = stream.clientName,
            profileId = stream.profileId,
            headers = stream.headers,
        )
        if (minted.size >= MAX_REMEMBERED) minted.clear()
        minted[extracted.url] = extracted
        return extracted
    }

    /** A music video's picture: a video-only stream, and the identity its fetch has to wear. */
    class VideoStream(
        val videoId: String,
        val url: String,
        val width: Int,
        val height: Int,
        val mimeType: String,
        val headers: Map<String, String>,
        /** Every height this video is offered at, largest first — what a quality picker lists. */
        val heights: List<Int>,
        /** InnerTubeX's catalog id for the client that minted [url]; what a refusal skips. */
        val profileId: String,
    ) {
        val aspect: Float get() = if (height > 0) width.toFloat() / height else 0f
    }

    /**
     * The picture for [videoId], at most [maxHeight] tall, for the player to
     * show over its own audio — as the web player's videoDeck does. The audio
     * this resolves alongside is ignored: the main player already has its own.
     *
     * [ageRestricted] matters more for video than for audio: asked for video,
     * InnerTubeX ranks the TV, VR and iOS clients first and WEB_REMIX last, and
     * several of those front-runners can't serve an age-gated video at all, so
     * the request budget goes on `LOGIN_REQUIRED`s. Saying so drops them, puts
     * the signed-in clients first and retries a refused one with a fresh PoToken.
     *
     * @param skipClients profiles refused for this picture already.
     * @return null when InnerTubeX found no video-only stream, which for a
     *   catalogue track (a still image as its "video") is the normal answer.
     * @throws com.metrolist.innertubex.extraction.StreamResolveException with
     *   [AGE_RESTRICTED][com.metrolist.innertubex.extraction.StreamResolveException.Reason.AGE_RESTRICTED]
     *   when every client asked was turned away by the age gate.
     */
    suspend fun extractVideo(
        videoId: String,
        maxHeight: Int,
        skipClients: Set<String> = emptySet(),
        ageRestricted: Boolean = false,
    ): VideoStream? {
        innerTube.cookie = Innertube.cookie
        innerTube.visitorData = Innertube.ensureVisitorData()
        innerTube.locale = YouTubeLocale(gl = Innertube.currentRegion, hl = Innertube.currentLanguage)
        val stream = extractor.extract(
            videoId = videoId,
            hints = ContentHints(
                isAgeRestricted = true.takeIf { ageRestricted },
                wantVideo = true,
                maxVideoHeight = maxHeight,
            ).withStreamCapabilities(allowHls = false, allowSabr = false, allowBoundedRange = true),
            excludedClients = excludedFor(videoId) + skipClients,
            audioQuality = AudioQuality.LOW,
            clientPlaybackNonce = generateClientPlaybackNonce(),
        ) ?: return null
        // A live broadcast's video-only URL is a window that ends in seconds,
        // like its audio one; no picture beats a frozen one.
        val url = stream.videoUrl?.takeUnless(StreamResolver::isLiveUrl) ?: return null
        val width = stream.videoWidth ?: 0
        val height = stream.videoHeight ?: 0
        val mime = stream.videoMimeType.orEmpty()
        TrackLog.d(
            TAG,
            "video: ${width}x$height $mime ${stream.videoCodecs.orEmpty()} via ${stream.profileId}" +
                if (ageRestricted) " (age-restricted)" else "",
            about = videoId,
        )
        return VideoStream(
            videoId = videoId,
            url = url,
            width = width,
            height = height,
            mimeType = mime,
            headers = stream.headers,
            heights = (stream.availableVideoHeights.orEmpty() + height)
                .filter { it > 0 }
                .distinct()
                .sortedDescending(),
            profileId = stream.profileId,
        )
    }

    /**
     * A picture's URL was refused mid-playback: the same signal [onRefused]
     * acts on for audio, minus the exclusion — the picture keeps its own, so a
     * video-only refusal doesn't bench a client the audio is fine on.
     */
    fun onVideoRefused() {
        scope.launch { runCatching { cipherService.refreshAfterStreamRejection() } }
    }

    private val _sessionGeneration = MutableStateFlow(0)

    /**
     * Bumped on every sign-in and sign-out, so what's remembered against the
     * old session elsewhere (the picture's "no video here" verdicts) can tell
     * it is stale.
     */
    val sessionGeneration: StateFlow<Int> = _sessionGeneration.asStateFlow()

    /** Headers the media fetch for [url] must carry, when InnerTubeX minted it. */
    fun headersFor(url: String): Map<String, String>? = minted[url]?.headers

    /**
     * Records that googlevideo refused [url] mid-playback.
     *
     * @return the videoId it was minted for, or null when InnerTubeX did not mint it.
     */
    fun onRefused(url: String): String? {
        val refused = minted.remove(url) ?: return null
        exclude(refused.videoId, refused.profileId)
        // A refused ciphered URL is the signal the remote cipher config may be stale; it has its own cooldown.
        scope.launch { runCatching { cipherService.refreshAfterStreamRejection() } }
        return refused.videoId
    }

    fun exclude(videoId: String, profileId: String) {
        TrackLog.w(TAG, "InnerTubeX: $profileId refused $videoId; skipping it for this track", about = videoId)
        excluded.getOrPut(videoId) { ConcurrentHashMap() }[profileId] = MonotonicClock.nowMs() + EXCLUDE_MS
    }

    fun onSessionChanged() {
        excluded.clear()
        minted.clear()
        // A new cookie or visitor means a new player config and PoToken binding.
        if (prefs != null) warm(0)
        _sessionGeneration.update { it + 1 }
    }

    private fun excludedFor(videoId: String): Set<String> {
        val entries = excluded[videoId] ?: return emptySet()
        val now = MonotonicClock.nowMs()
        entries.entries.removeAll { it.value <= now }
        return entries.keys.toSet()
    }

    private const val PLAYER_CONFIG_URL =
        "https://raw.githubusercontent.com/ZemerTeam/zemer-cipher/master/library/src/main/assets/player_configs.json"
    private const val LOW_KBPS = 64
    private const val EXCLUDE_MS = 10 * 60 * 1000L
    private const val MAX_REMEMBERED = 64
    /** Off the cold-start path; the first tap on a track is rarely sooner. */
    private const val WARM_DELAY_MS = 2_000L
    private const val KEPT_PLAYERS = 3
}
