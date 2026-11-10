/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package moe.rukamori.archivetune.ui.player

import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.withTimeoutOrNull
import moe.rukamori.archivetune.constants.VideoPlaybackSpeedKey
import moe.rukamori.archivetune.constants.AutoChoosePlaybackClientKey
import moe.rukamori.archivetune.constants.MaxVideoCacheSizeKey
import moe.rukamori.archivetune.constants.PlayerStreamClient
import moe.rukamori.archivetune.constants.PlayerStreamClientKey
import moe.rukamori.archivetune.innertube.NewPipeUtils
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.innertube.models.response.PlayerResponse
import moe.rukamori.archivetune.simpstream.SimpMusicPlayer
import moe.rukamori.archivetune.utils.ImageBlurUtils
import moe.rukamori.archivetune.utils.StreamClientUtils
import moe.rukamori.archivetune.utils.rememberPreference
import dagger.hilt.android.EntryPointAccessors
import moe.rukamori.archivetune.di.VideoCacheEntryPoint
import moe.rukamori.archivetune.utils.PreferenceStore
import moe.rukamori.archivetune.utils.YTPlayerUtils
import moe.rukamori.archivetune.extensions.toEnum
import okhttp3.OkHttpClient
import java.net.SocketTimeoutException
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.TimeUnit
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private const val VideoSyncIgnoreToleranceMs = 60L

private const val VideoSoftSeekDriftThresholdMs = 2000L

private const val VideoSyncSpeedCorrectionFactorMax = 0.20f

private const val VideoSeekSettlingTimeMs = 2000L

private const val SurfaceReanchorMinIntervalMs = 2000L

private const val VideoFrozenRendererCycles = 3

private const val VideoFrozenRendererMaxAdvanceMs = 50L

private const val VideoInitialSyncToleranceMs = 200L

private const val VideoHardResyncThresholdMs = 5000L

private const val VideoHardResyncCooldownMs = 30_000L

private const val VideoSyncPollIntervalMs = 250L

private const val VideoStuckBufferingTimeoutMs = 8000L

private fun maxVideoHeightFor(preferredHeight: Int?): Int = VideoQualityPreference.ceilingFor(preferredHeight)

private const val VideoReadyHoldTimeoutMs = 10000L
private const val VideoFirstFrameKickIntervalMs = 2500L
private const val VideoFirstFrameKickMaxAttempts = 12

private const val VideoClientAttemptTimeoutMs = 8000L

private const val VideoSimpMusicAttemptTimeoutMs = 6000L

private const val VideoMinBufferMs = 15_000
private const val VideoMaxBufferMs = 90_000
private const val VideoBufferForPlaybackMs = 600
private const val VideoBufferForPlaybackAfterRebufferMs = 1_000

private const val AudioReadyBarrierTimeoutMs = 30_000L

private const val MaxVideoRecoveryAttempts = 1

private const val VideoLoadResumeDelayMs = 1000L

private const val WebRemixMaxVideoHeight = 1080

private val LowCeilingVideoClientNames =
    setOf(
        "WEB_REMIX",
        "ANDROID_MUSIC",
        "IOS_MUSIC",
        "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
        "WEB_EMBEDDED",
    )

private const val VideoStreamInfoCacheMaxEntries = 16

private const val VideoStreamInfoCacheValidityMs = 45 * 60 * 1000L

private val videoStreamInfoCache =
    java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, Pair<Long, VideoStreamInfo>>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, VideoStreamInfo>>): Boolean =
                size > VideoStreamInfoCacheMaxEntries
        },
    )

private fun resolveVideoStreamInfoFromCache(cacheKey: String): VideoStreamInfo? {
    val cached = videoStreamInfoCache[cacheKey] ?: return null
    val (cachedAtMs, info) = cached
    val fresh = System.currentTimeMillis() - cachedAtMs < VideoStreamInfoCacheValidityMs
    if (!fresh) videoStreamInfoCache.remove(cacheKey)
    return if (fresh) info else null
}

private fun cacheResolvedVideoStreamInfo(
    cacheKey: String,
    info: VideoStreamInfo,
) {
    synchronized(videoStreamInfoCache) {
        videoStreamInfoCache[cacheKey] = System.currentTimeMillis() to info
        while (videoStreamInfoCache.size > VideoStreamInfoCacheMaxEntries) {
            val eldest = videoStreamInfoCache.keys.firstOrNull() ?: break
            videoStreamInfoCache.remove(eldest)
        }
    }
}

private fun evictResolvedVideoStreamInfoForVideo(videoId: String) {
    synchronized(videoStreamInfoCache) {
        videoStreamInfoCache.keys.removeAll { it.startsWith("$videoId|") }
    }
}

data class VideoStreamInfo(
    val streamUrl: String,
    val availableHeights: List<Int>,
    val captionTracks: List<PlayerResponse.CaptionTrack>,
    val selectedHeight: Int? = null,
)

private val VideoStreamHttpClient by lazy {
    OkHttpClient
        .Builder()
        .proxy(YouTube.streamOkHttpProxy)
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            val host = request.url.host
            val isYouTubeMediaHost =
                host.endsWith("googlevideo.com") ||
                    host.endsWith("googleusercontent.com") ||
                    host.endsWith("youtube.com") ||
                    host.endsWith("youtube-nocookie.com") ||
                    host.endsWith("ytimg.com")

            if (!isYouTubeMediaHost) {
                return@addInterceptor chain.proceed(request)
            }

            val requestProfile = StreamClientUtils.resolveRequestProfile(request.url)
            chain.proceed(
                StreamClientUtils
                    .applyRequestProfile(
                        request.newBuilder(),
                        requestProfile,
                    ).build(),
            )
        }.build()
}

internal fun videoStreamHttpClient(): OkHttpClient = VideoStreamHttpClient

@Stable
class VideoArtworkState internal constructor(
    val exoPlayer: ExoPlayer,
) {
    var streamUrl: String? by mutableStateOf(null)
        internal set
    var isVideoReady: Boolean by mutableStateOf(false)
        internal set
    var hasPlaybackFailed: Boolean by mutableStateOf(false)
        internal set
    var isChangingQuality: Boolean by mutableStateOf(false)
        internal set
    var wasPlayingBeforeQualityChange: Boolean by mutableStateOf(false)
        internal set
    var isResyncing: Boolean by mutableStateOf(false)
        internal set
    var wasPlayingBeforeResync: Boolean by mutableStateOf(false)
        internal set
    var isResolvingUrl: Boolean by mutableStateOf(true)
        internal set
    var bufferingStartedAtMs: Long by mutableLongStateOf(0L)
        internal set

    var bufferingRecoveries: Int by mutableStateOf(0)
        internal set

    var videoAspectRatio: Float? by mutableStateOf(null)
        internal set

    var currentSpeedCorrectionFactor by mutableStateOf(1.0f)
        internal set

    var lastSeekAtMs: Long by mutableLongStateOf(0L)
        internal set

    var lastSurfaceReanchorAtMs: Long by mutableLongStateOf(0L)
        internal set

    var pendingResumeAtMs: Long by mutableLongStateOf(0L)
        internal set

    var pendingResumeMainAudio: Boolean by mutableStateOf(false)
        internal set

    var pendingResumeVideo: Boolean by mutableStateOf(false)
        internal set

    var captionTracks: List<PlayerResponse.CaptionTrack> by mutableStateOf(emptyList())
        internal set

    var selectedCaptionTrack: PlayerResponse.CaptionTrack? by mutableStateOf(null)

    var currentCaptionText: String? by mutableStateOf(null)
        internal set

    internal var pendingResync: Triple<Long, Boolean, Boolean>? by mutableStateOf(null)

    internal var lastAutoResyncAtMs: Long by mutableLongStateOf(0L)

    internal var autoResyncDisabled: Boolean by mutableStateOf(false)

    fun requestResync(position: Long, isPlaying: Boolean) {
        if (hasPlaybackFailed) return
        if (isResyncing) return
        pendingResync = Triple(position, isPlaying, false)
    }

    internal fun requestAutoResync(position: Long, isPlaying: Boolean): Boolean {
        if (hasPlaybackFailed) return false
        if (isResyncing) return false
        if (autoResyncDisabled) return false
        val now = SystemClock.elapsedRealtime()
        if (lastAutoResyncAtMs != 0L && now - lastAutoResyncAtMs < VideoHardResyncCooldownMs) {
            Timber
                .tag(VideoPlaybackLogTag)
                .w("Auto-resync cooldown hit — disabling automatic resync for this video")
            autoResyncDisabled = true
            return false
        }
        lastAutoResyncAtMs = now
        pendingResync = Triple(position, isPlaying, true)
        return true
    }

    internal fun kickRenderer(now: Long = SystemClock.elapsedRealtime()): Boolean {
        if (hasPlaybackFailed) return false
        if (isResyncing) return false
        if (exoPlayer.playbackState != Player.STATE_READY) return false
        if (!exoPlayer.playWhenReady) return false
        if (lastSurfaceReanchorAtMs != 0L && now - lastSurfaceReanchorAtMs < SurfaceReanchorMinIntervalMs) {
            return false
        }
        lastSurfaceReanchorAtMs = now

        exoPlayer.pause()
        exoPlayer.play()
        return true
    }
}

@Composable
fun rememberVideoArtworkState(
    videoId: String,
    isPlaying: Boolean,
    positionProvider: () -> Long,
    preferredHeight: Int?,
    holdAudioUntilVideoReady: Boolean,
    onStreamResolved: (VideoStreamInfo?) -> Unit,
    onPlaybackFailed: () -> Unit,
    onLoadingStateChange: (Boolean) -> Unit,
    onRequestPauseMain: () -> Unit,
    onRequestResumeMain: () -> Unit,
    isMainAudioBuffering: Boolean = false,
    mainAudioReady: Boolean = false,
    sheetSurfaceActive: Boolean = true,
): VideoArtworkState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val shouldPlay by rememberUpdatedState(isPlaying)
    val currentPosition by rememberUpdatedState(positionProvider)
    val updatedPreferredHeight by rememberUpdatedState(preferredHeight)
    val updatedOnStreamResolved by rememberUpdatedState(onStreamResolved)
    val updatedOnPlaybackFailed by rememberUpdatedState(onPlaybackFailed)
    val updatedOnLoadingStateChange by rememberUpdatedState(onLoadingStateChange)
    val updatedOnRequestPauseMain by rememberUpdatedState(onRequestPauseMain)
    val updatedOnRequestResumeMain by rememberUpdatedState(onRequestResumeMain)
    val updatedHoldAudioUntilVideoReady by rememberUpdatedState(holdAudioUntilVideoReady)
    val updatedIsMainAudioBuffering by rememberUpdatedState(isMainAudioBuffering)
    val updatedMainAudioReady by rememberUpdatedState(mainAudioReady)
    val updatedSheetSurfaceActive by rememberUpdatedState(sheetSurfaceActive)

    val okHttpClient = remember { videoStreamHttpClient() }

    val (maxVideoCacheSize) = rememberPreference(MaxVideoCacheSizeKey, defaultValue = 512)
    val videoCacheEnabled = maxVideoCacheSize != 0
    val videoCache =
        remember {
            runCatching {
                EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    VideoCacheEntryPoint::class.java,
                ).videoCache()
            }.getOrNull()
        }

    val mediaSourceFactory =
        remember(okHttpClient, videoCache, videoCacheEnabled) {
            val upstreamFactory =
                DefaultDataSource.Factory(
                    context,
                    OkHttpDataSource.Factory(okHttpClient),
                )
            val factory =
                if (videoCache != null && videoCacheEnabled) {
                    CacheDataSource.Factory()
                        .setCache(videoCache)
                        .setUpstreamDataSourceFactory(upstreamFactory)
                        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                } else {
                    upstreamFactory
                }
            DefaultMediaSourceFactory(factory)
        }

    val renderersFactory =
        remember(context) {
            DefaultRenderersFactory(context).setEnableDecoderFallback(true)
        }

    val trackSelector =
        remember(context) {
            DefaultTrackSelector(context).apply {
                setParameters(
                    buildUponParameters()
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setForceHighestSupportedBitrate(true)
                        .build(),
                )
            }
        }

    val loadControl =
        remember {
            DefaultLoadControl
                .Builder()
                .setBufferDurationsMs(
                    VideoMinBufferMs,
                    VideoMaxBufferMs,
                    VideoBufferForPlaybackMs,
                    VideoBufferForPlaybackAfterRebufferMs,
                ).setPrioritizeTimeOverSizeThresholds(true)
                .build()
        }

    val state =
        remember(mediaSourceFactory, renderersFactory, trackSelector, loadControl) {
            val exoPlayer =
                ExoPlayer
                    .Builder(context)
                    .setMediaSourceFactory(mediaSourceFactory)
                    .setRenderersFactory(renderersFactory)
                    .setTrackSelector(trackSelector)
                    .setLoadControl(loadControl)
                    .build()
                    .apply {
                        volume = 0f
                        playWhenReady = isPlaying
                    }
            VideoArtworkState(exoPlayer)
        }

    val exoPlayer = state.exoPlayer

    var awaitingVideoReady by remember { mutableStateOf(false) }
    var resumeAudioAfterVideoReady by remember { mutableStateOf(false) }

    var lastLoadedStreamUrl by remember { mutableStateOf<String?>(null) }
    var lastLoadedCaptionTrack by remember { mutableStateOf<PlayerResponse.CaptionTrack?>(null) }
    var appInForeground by remember { mutableStateOf(true) }

    var videoRecoveryAttempts by remember { mutableStateOf(0) }
    var videoRecoveryRequests by remember { mutableIntStateOf(0) }

    fun beginAudioHold(resumeOverride: Boolean = false) {
        if (!updatedHoldAudioUntilVideoReady) return
        if (awaitingVideoReady) return
        awaitingVideoReady = true
        resumeAudioAfterVideoReady = shouldPlay || resumeOverride

        if (shouldPlay) updatedOnRequestPauseMain()
        Timber
            .tag(VideoPlaybackLogTag)
            .d("Video for $videoId loading — audio paused until first frame (or artwork fallback)")
    }

    fun releaseAudioHold(resumeMainAudio: Boolean = false) {
        if (!awaitingVideoReady) return
        val shouldResumeAudio = resumeAudioAfterVideoReady
        awaitingVideoReady = false

        if (resumeMainAudio && shouldResumeAudio) {
            resumeAudioAfterVideoReady = false
            updatedOnRequestResumeMain()
        }

        Timber
            .tag(VideoPlaybackLogTag)
            .d("Video ready — clearing hold flag (resume scheduled=${!resumeMainAudio})")
    }

    fun declareVideoFailure(reason: String) {
        if (state.hasPlaybackFailed) return
        state.isVideoReady = false
        state.isChangingQuality = false
        state.isResyncing = false
        state.bufferingStartedAtMs = 0L
        releaseAudioHold(resumeMainAudio = true)

        if (videoRecoveryAttempts < MaxVideoRecoveryAttempts) {
            videoRecoveryAttempts += 1
            Timber
                .tag(VideoPlaybackLogTag)
                .w("Video failing for $videoId ($reason) — re-resolving at a 1080p ceiling")
            videoRecoveryRequests += 1
            return
        }

        Timber
            .tag(VideoPlaybackLogTag)
            .w("Video playback failed for $videoId ($reason) — falling back to artwork")
        state.hasPlaybackFailed = true
        exoPlayer.stop()

        state.pendingResumeAtMs = 0L
        state.pendingResumeMainAudio = false
        state.pendingResumeVideo = false

        updatedOnPlaybackFailed()
    }

    LaunchedEffect(
        state.isVideoReady,
        state.isChangingQuality,
        state.isResyncing,
        state.streamUrl,
        state.hasPlaybackFailed,
        state.isResolvingUrl,
    ) {
        val loading =
            !state.hasPlaybackFailed &&
                (
                    state.isResolvingUrl ||
                        state.isResyncing ||
                        (state.streamUrl != null && !state.isVideoReady)
                )
        updatedOnLoadingStateChange(loading)
    }

    LaunchedEffect(videoId) {
        state.streamUrl = null
        state.isVideoReady = false
        state.hasPlaybackFailed = false
        state.isResolvingUrl = true
        state.isResyncing = false
        state.wasPlayingBeforeResync = false
        state.bufferingStartedAtMs = 0L
        state.bufferingRecoveries = 0
        state.selectedCaptionTrack = null
        state.captionTracks = emptyList()
        state.currentCaptionText = null

        state.lastAutoResyncAtMs = 0L
        state.autoResyncDisabled = false

        state.currentSpeedCorrectionFactor = 1.0f
        state.lastSeekAtMs = 0L

        state.lastSurfaceReanchorAtMs = 0L

        state.pendingResumeAtMs = 0L
        state.pendingResumeMainAudio = false
        state.pendingResumeVideo = false

        lastLoadedStreamUrl = null
        lastLoadedCaptionTrack = null

        videoRecoveryAttempts = 0
        videoRecoveryRequests = 0

        val backgroundHoldWasPlaying =
            moe.rukamori.archivetune.playback.VideoBackgroundPreloader.claimUiTakeover(videoId)

        beginAudioHold(resumeOverride = backgroundHoldWasPlaying)

        val resolved =
            withContext(Dispatchers.IO) {
                resolveVideoStreamUrl(videoId, updatedPreferredHeight)
            }

        state.isResolvingUrl = false

        if (resolved == null) {
            state.hasPlaybackFailed = true
            releaseAudioHold(resumeMainAudio = true)
            updatedOnPlaybackFailed()
            updatedOnStreamResolved(null)
        } else {
            state.streamUrl = resolved.streamUrl
            state.captionTracks = resolved.captionTracks
            updatedOnStreamResolved(resolved)
        }
    }

    LaunchedEffect(preferredHeight) {
        if (state.streamUrl == null) return@LaunchedEffect
        state.wasPlayingBeforeQualityChange = shouldPlay
        state.isChangingQuality = true
        state.isVideoReady = false
        state.isResolvingUrl = true
        exoPlayer.pause()
        updatedOnRequestPauseMain()
        state.streamUrl = null
        val resolved =
            withContext(Dispatchers.IO) {
                resolveVideoStreamUrl(videoId, updatedPreferredHeight)
            }
        state.isResolvingUrl = false
        if (resolved != null) {
            state.streamUrl = resolved.streamUrl
            state.captionTracks = resolved.captionTracks
            updatedOnStreamResolved(resolved)
        } else {
            state.isChangingQuality = false
            val fallback =
                withContext(Dispatchers.IO) {
                    resolveVideoStreamUrl(videoId, updatedPreferredHeight)
                }
            if (fallback != null) {
                state.streamUrl = fallback.streamUrl
                state.captionTracks = fallback.captionTracks
                updatedOnStreamResolved(fallback)
            } else {
                state.hasPlaybackFailed = true
                updatedOnPlaybackFailed()
            }
            if (state.wasPlayingBeforeQualityChange) {
                updatedOnRequestResumeMain()
            }
        }
    }

    LaunchedEffect(state.streamUrl, state.selectedCaptionTrack, exoPlayer) {
        val url = state.streamUrl ?: return@LaunchedEffect

        val captionBeingChanged =
            url == lastLoadedStreamUrl &&
                state.selectedCaptionTrack != lastLoadedCaptionTrack
        if (captionBeingChanged) {
            state.wasPlayingBeforeQualityChange = shouldPlay
            state.isChangingQuality = true
            exoPlayer.pause()
            if (shouldPlay) updatedOnRequestPauseMain()
        }
        lastLoadedStreamUrl = url
        lastLoadedCaptionTrack = state.selectedCaptionTrack

        state.isVideoReady = false
        state.hasPlaybackFailed = false
        state.currentCaptionText = null
        state.videoAspectRatio = null

        val lowercaseUrl = url.lowercase(Locale.ROOT)
        val mimeType =
            when {
                lowercaseUrl.contains("m3u8") -> MimeTypes.APPLICATION_M3U8
                lowercaseUrl.contains("mp4") || lowercaseUrl.contains("avc") -> MimeTypes.VIDEO_MP4
                lowercaseUrl.contains("webm") || lowercaseUrl.contains("vp9") -> MimeTypes.VIDEO_WEBM
                lowercaseUrl.contains("av01") || lowercaseUrl.contains("av1") -> MimeTypes.VIDEO_AV1
                else -> MimeTypes.VIDEO_MP4
            }

        val mediaItemBuilder =
            MediaItem
                .Builder()
                .setUri(url)
                .setMimeType(mimeType)

        state.selectedCaptionTrack?.let { track ->
            mediaItemBuilder.setSubtitleConfigurations(
                listOf(
                    MediaItem
                        .SubtitleConfiguration
                        .Builder(track.webVttUrl().toUri())
                        .setMimeType(MimeTypes.TEXT_VTT)
                        .setLanguage(track.languageCode)
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build(),
                ),
            )
        }

        val mediaItem = mediaItemBuilder.build()

        exoPlayer.stop()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()

        val targetPosition = currentPosition()
        if (targetPosition > 0) {
            exoPlayer.seekTo(targetPosition)

            state.lastSeekAtMs = SystemClock.elapsedRealtime()
        }
        exoPlayer.playWhenReady = shouldPlay && !awaitingVideoReady
    }

    LaunchedEffect(
        isPlaying,
        awaitingVideoReady,
        state.isChangingQuality,
        state.isResyncing,
        updatedIsMainAudioBuffering,
        updatedMainAudioReady,
        state.isVideoReady,
        state.isResolvingUrl,
    ) {
        if (state.hasPlaybackFailed) {
            exoPlayer.pause()
        } else if (awaitingVideoReady) {
            if (isPlaying) {
                resumeAudioAfterVideoReady = true
                updatedOnRequestPauseMain()
            }
            exoPlayer.pause()
        } else if (state.isChangingQuality) {
            if (isPlaying) updatedOnRequestPauseMain()
            exoPlayer.pause()
        } else if (state.isResyncing) {
            exoPlayer.pause()
        } else if (!updatedMainAudioReady) {
            exoPlayer.pause()
        } else if (state.isResolvingUrl) {
            exoPlayer.pause()
        } else if (!state.isVideoReady) {
            exoPlayer.pause()
        } else {
            exoPlayer.setVideoPlayback(isPlaying)
        }
    }

    LaunchedEffect(state.selectedCaptionTrack, trackSelector) {
        val lang = state.selectedCaptionTrack?.languageCode
        val params =
            trackSelector
                .buildUponParameters()
                .apply {
                    if (!lang.isNullOrBlank()) {
                        setPreferredTextLanguage(lang)
                    }
                }.build()
        trackSelector.setParameters(params)
    }

    LaunchedEffect(state.streamUrl) {
        if (state.streamUrl == null) return@LaunchedEffect
        delay(VideoReadyHoldTimeoutMs)
        var kicks = 0
        while (!state.isVideoReady && !state.hasPlaybackFailed) {
            if (!updatedSheetSurfaceActive || !appInForeground) {
                delay(500)
                continue
            }
            if (kicks >= VideoFirstFrameKickMaxAttempts) {
                Timber
                    .tag(VideoPlaybackLogTag)
                    .w("Video never rendered a first frame after $kicks kicks — falling back to artwork")
                declareVideoFailure("first frame never rendered")
                break
            }
            if (exoPlayer.playbackState == Player.STATE_READY && exoPlayer.playerError == null) {
                Timber
                    .tag(VideoPlaybackLogTag)
                    .w(
                        "Video first frame still pending while READY — kicking a position reset to force the render (backgrounded start recovery)",
                    )
                exoPlayer.seekTo(exoPlayer.currentPosition)
                state.lastSeekAtMs = SystemClock.elapsedRealtime()
                kicks++
            }
            delay(VideoFirstFrameKickIntervalMs)
        }
    }

    LaunchedEffect(updatedSheetSurfaceActive) {
        if (!updatedSheetSurfaceActive) return@LaunchedEffect
        if (state.hasPlaybackFailed || state.isVideoReady) return@LaunchedEffect
        if (exoPlayer.playerError != null) return@LaunchedEffect
        if (exoPlayer.playbackState == Player.STATE_READY) {
            exoPlayer.seekTo(exoPlayer.currentPosition)
            state.lastSeekAtMs = SystemClock.elapsedRealtime()
        }
    }

    LaunchedEffect(videoRecoveryRequests) {
        if (videoRecoveryRequests == 0) return@LaunchedEffect
        if (state.hasPlaybackFailed) return@LaunchedEffect

        val recoveryCeiling = maxVideoHeightFor(updatedPreferredHeight)
        val recoveryPreferredHeight =
            if (recoveryCeiling > WebRemixMaxVideoHeight) null else updatedPreferredHeight

        exoPlayer.stop()
        state.streamUrl = null
        state.isVideoReady = false
        state.bufferingStartedAtMs = 0L
        state.bufferingRecoveries = 0
        state.isResolvingUrl = true

        evictResolvedVideoStreamInfoForVideo(videoId)

        val recovered =
            withContext(Dispatchers.IO) {
                resolveVideoStreamUrl(videoId, recoveryPreferredHeight)
            }
        state.isResolvingUrl = false

        if (recovered != null && !state.hasPlaybackFailed && recovered.streamUrl.isNotBlank()) {
            Timber
                .tag(VideoPlaybackLogTag)
                .i("Recovered video stream for $videoId at ${recovered.selectedHeight}p after a failure")
            state.captionTracks = recovered.captionTracks
            updatedOnStreamResolved(recovered)
            state.streamUrl = recovered.streamUrl
        } else {
            Timber
                .tag(VideoPlaybackLogTag)
                .w("Video recovery resolution failed for $videoId — falling back to artwork")
            state.hasPlaybackFailed = true
            updatedOnPlaybackFailed()
        }
    }

    val (videoPlaybackSpeed, _) = rememberPreference(VideoPlaybackSpeedKey, defaultValue = 1.0f)
    LaunchedEffect(videoPlaybackSpeed, exoPlayer, state.currentSpeedCorrectionFactor) {
        val safeSpeed = videoPlaybackSpeed.coerceIn(0.25f, 2f)
        val effectiveSpeed =
            (safeSpeed * state.currentSpeedCorrectionFactor).coerceIn(0.1f, 4f)
        val current = exoPlayer.playbackParameters.speed
        if (kotlin.math.abs(current - effectiveSpeed) > 0.001f) {
            exoPlayer.playbackParameters = PlaybackParameters(effectiveSpeed)
        }
    }

    LaunchedEffect(state.pendingResync) {
        val (position, wasPlaying, isAutomatic) = state.pendingResync ?: return@LaunchedEffect

        state.pendingResync = null
        if (state.hasPlaybackFailed || state.isResyncing) return@LaunchedEffect
        Timber
            .tag(VideoPlaybackLogTag)
            .d(
                "Resync to ${position}ms (wasPlaying=$wasPlaying, auto=$isAutomatic) — pause-load-resume",
            )
        state.wasPlayingBeforeResync = wasPlaying
        state.isResyncing = true
        state.isVideoReady = false

        state.currentSpeedCorrectionFactor = 1.0f
        state.lastSeekAtMs = SystemClock.elapsedRealtime()
        if (wasPlaying) updatedOnRequestPauseMain()
        exoPlayer.pause()
        exoPlayer.seekTo(position)
        state.bufferingStartedAtMs = SystemClock.elapsedRealtime()
    }

    LaunchedEffect(state.pendingResumeAtMs) {
        if (state.pendingResumeAtMs == 0L) return@LaunchedEffect
        val resumeMainAudio = state.pendingResumeMainAudio
        val resumeVideo = state.pendingResumeVideo
        val now = SystemClock.elapsedRealtime()
        val delayMs = (state.pendingResumeAtMs - now).coerceAtLeast(0L)
        delay(delayMs)

        if (resumeVideo || resumeMainAudio) {
            val bothLoaded =
                withTimeoutOrNull(AudioReadyBarrierTimeoutMs) {
                    snapshotFlow { state.isVideoReady to updatedMainAudioReady }
                        .first { (videoReady, audioReady) -> videoReady && audioReady }
                }
            if (bothLoaded == null) {
                Timber
                    .tag(VideoPlaybackLogTag)
                    .w("Audio still not loaded after ${AudioReadyBarrierTimeoutMs}ms — resuming anyway so the audio player's stall recovery can engage")
            }
        }

        if (state.hasPlaybackFailed || exoPlayer.playerError != null) {
            Timber
                .tag(VideoPlaybackLogTag)
                .w("Pending resume cancelled — playback failed during delay window")
            state.pendingResumeAtMs = 0L
            state.pendingResumeMainAudio = false
            state.pendingResumeVideo = false
            return@LaunchedEffect
        }
        Timber
            .tag(VideoPlaybackLogTag)
            .d("Firing delayed resume (audio=$resumeMainAudio, video=$resumeVideo, both streams loaded)")
        if (resumeMainAudio) {
            updatedOnRequestResumeMain()
        }
        if (resumeVideo) {
            exoPlayer.playWhenReady = true
            exoPlayer.play()
        }

        state.pendingResumeAtMs = 0L
        state.pendingResumeMainAudio = false
        state.pendingResumeVideo = false
    }

    LaunchedEffect(state.streamUrl, exoPlayer) {
        if (state.streamUrl == null) return@LaunchedEffect

        var prevVideoPos = -1L
        var prevAudioPos = -1L
        var frozenCycles = 0
        var frozenKicks = 0
        var lastWatchdogBufferedPos = -1L
        while (isActive) {
            delay(VideoSyncPollIntervalMs)
            if (state.hasPlaybackFailed) continue

            if (state.bufferingStartedAtMs > 0L) {
                val bufferedPos = exoPlayer.bufferedPosition
                val bufferingForMs = SystemClock.elapsedRealtime() - state.bufferingStartedAtMs

                val progressing = bufferedPos > lastWatchdogBufferedPos + 250L
                lastWatchdogBufferedPos = bufferedPos
                if (progressing) {
                    state.bufferingStartedAtMs = SystemClock.elapsedRealtime()
                    continue
                }
                if (bufferingForMs > VideoStuckBufferingTimeoutMs) {
                    state.bufferingRecoveries = state.bufferingRecoveries + 1
                    if (state.bufferingRecoveries >= 3) {
                        Timber
                            .tag(VideoPlaybackLogTag)
                            .w("Video frozen in BUFFERING for ${bufferingForMs}ms after ${state.bufferingRecoveries - 1} recoveries")
                        state.bufferingStartedAtMs = 0L
                        declareVideoFailure("stuck buffering for ${bufferingForMs}ms")
                    } else {
                        val mainPos = currentPosition()
                        Timber
                            .tag(VideoPlaybackLogTag)
                            .w("Video stuck in BUFFERING for ${bufferingForMs}ms — re-anchoring to ${mainPos}ms and re-preparing")
                        state.bufferingStartedAtMs = SystemClock.elapsedRealtime()
                        lastWatchdogBufferedPos = -1L
                        if (mainPos > 0) {
                            exoPlayer.seekTo(mainPos)
                            state.lastSeekAtMs = SystemClock.elapsedRealtime()
                        }
                        exoPlayer.prepare()
                    }
                }
            } else {
                lastWatchdogBufferedPos = -1L
            }

            if (state.isChangingQuality) continue
            if (state.isResyncing) continue
            if (!state.isVideoReady) continue
            if (awaitingVideoReady) continue

            val now = SystemClock.elapsedRealtime()
            if (state.lastSeekAtMs > 0L && now - state.lastSeekAtMs < VideoSeekSettlingTimeMs) {
                if (state.currentSpeedCorrectionFactor != 1.0f) {
                    state.currentSpeedCorrectionFactor = 1.0f
                }
                continue
            }

            if (exoPlayer.playbackState != Player.STATE_READY) {
                if (state.currentSpeedCorrectionFactor != 1.0f) {
                    state.currentSpeedCorrectionFactor = 1.0f
                }
                continue
            }

            val mainPos = currentPosition()
            if (mainPos <= 0) continue
            val videoPos = exoPlayer.currentPosition

            val signedDrift = videoPos - mainPos
            val absDrift = kotlin.math.abs(signedDrift)

            if (exoPlayer.playWhenReady && prevVideoPos >= 0L) {
                val audioAdvanced = mainPos - prevAudioPos
                val videoAdvanced = videoPos - prevVideoPos
                if (audioAdvanced >= VideoSyncPollIntervalMs && videoAdvanced <= VideoFrozenRendererMaxAdvanceMs) {
                    frozenCycles++
                    if (frozenCycles >= VideoFrozenRendererCycles) {
                        frozenKicks++
                        if (frozenKicks >= 2) {
                            Timber
                                .tag(VideoPlaybackLogTag)
                                .w(
                                    "Video renderer frozen at ${videoPos}ms through a kick (audio at " +
                                        "${mainPos}ms) — re-anchoring and re-preparing",
                                )
                            frozenKicks = 0
                            frozenCycles = 0
                            prevVideoPos = -1L
                            state.lastSeekAtMs = now
                            exoPlayer.seekTo(mainPos)
                            exoPlayer.prepare()
                        } else {
                            Timber
                                .tag(VideoPlaybackLogTag)
                                .w(
                                    "Video renderer frozen: position stuck at ${videoPos}ms while " +
                                        "audio advanced to ${mainPos}ms (${frozenCycles} cycles) — " +
                                        "restarting renderer",
                                )
                            frozenCycles = 0
                            prevVideoPos = -1L
                            state.kickRenderer(now)
                        }
                    }
                } else {
                    frozenCycles = 0
                    frozenKicks = 0
                }
            } else if (!exoPlayer.playWhenReady) {
                frozenCycles = 0
                frozenKicks = 0
            }
            prevVideoPos = videoPos
            prevAudioPos = mainPos

            if (absDrift > VideoHardResyncThresholdMs) {
                Timber
                    .tag(VideoPlaybackLogTag)
                    .w("Hard resync: drift=${signedDrift}ms (main=$mainPos, video=$videoPos, playing=$shouldPlay)")
                state.currentSpeedCorrectionFactor = 1.0f
                val accepted = state.requestAutoResync(mainPos, shouldPlay)
                if (!accepted) {
                    exoPlayer.seekTo(mainPos)
                    state.lastSeekAtMs = now
                    state.lastSurfaceReanchorAtMs = now
                }
            } else if (absDrift > VideoSoftSeekDriftThresholdMs) {
                Timber
                    .tag(VideoPlaybackLogTag)
                    .d("Re-anchor: drift=${signedDrift}ms (main=$mainPos, video=$videoPos)")
                state.currentSpeedCorrectionFactor = 1.0f
                exoPlayer.seekTo(mainPos)
                state.lastSeekAtMs = now

                state.lastSurfaceReanchorAtMs = now
            } else if (absDrift > VideoSyncIgnoreToleranceMs) {
                val normalizedDrift =
                    (signedDrift.toFloat() / VideoSoftSeekDriftThresholdMs.toFloat())
                        .coerceIn(-1f, 1f)

                val targetFactor =
                    1.0f - (normalizedDrift * VideoSyncSpeedCorrectionFactorMax)
                if (kotlin.math.abs(state.currentSpeedCorrectionFactor - targetFactor) > 0.005f) {
                    Timber
                        .tag(VideoPlaybackLogTag)
                        .d("Speed-correct: drift=${signedDrift}ms factor=$targetFactor (main=$mainPos, video=$videoPos)")
                    state.currentSpeedCorrectionFactor = targetFactor
                }
            } else {
                if (state.currentSpeedCorrectionFactor != 1.0f) {
                    state.currentSpeedCorrectionFactor = 1.0f
                }
            }
        }
    }

    DisposableEffect(exoPlayer, lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) {
                    appInForeground = true
                }
                if (event == Lifecycle.Event.ON_STOP) {
                    appInForeground = false
                    runCatching { exoPlayer.pause() }
                }
                if (
                    (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) &&
                    !state.hasPlaybackFailed &&
                    exoPlayer.playerError == null &&
                    state.streamUrl != null
                ) {
                    if (awaitingVideoReady && !state.isVideoReady &&
                        exoPlayer.playbackState == Player.STATE_READY
                    ) {
                        exoPlayer.seekTo(exoPlayer.currentPosition)
                        state.lastSeekAtMs = SystemClock.elapsedRealtime()
                    }
                    if (updatedMainAudioReady) {
                        if (state.isVideoReady) {
                            val mainPos = currentPosition()
                            val videoPos = exoPlayer.currentPosition
                            if (mainPos > 0 && kotlin.math.abs(videoPos - mainPos) > VideoSoftSeekDriftThresholdMs) {
                                exoPlayer.seekTo(mainPos)
                                state.lastSeekAtMs = SystemClock.elapsedRealtime()
                                state.lastSurfaceReanchorAtMs = state.lastSeekAtMs
                            }
                        }
                        exoPlayer.setVideoPlayback(shouldPlay)
                    }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(exoPlayer, state.streamUrl) {
        val listener =
            object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Timber.tag(VideoPlaybackLogTag).w(error, "Video playback failed for $videoId")
                    declareVideoFailure("player error ${error.errorCodeName}")
                }

                override fun onCues(cueGroup: CueGroup) {
                    val text =
                        cueGroup.cues
                            .joinToString("\n") { it.text?.toString().orEmpty() }
                            .takeIf { it.isNotBlank() }
                    state.currentCaptionText = text
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    val width = videoSize.width
                    val height = videoSize.height
                    state.videoAspectRatio =
                        if (width > 0 && height > 0) {
                            (width.toFloat() / height.toFloat()).coerceIn(0.2f, 5f)
                        } else {
                            null
                        }
                }

                override fun onRenderedFirstFrame() {
                    val wasAlreadyReady = state.isVideoReady
                    state.isVideoReady = true
                    state.bufferingRecoveries = 0

                    val shouldResumeAudioAfterHold = resumeAudioAfterVideoReady
                    releaseAudioHold()
                    val wasChangingQuality = state.isChangingQuality
                    state.isChangingQuality = false
                    val wasResync = state.isResyncing
                    state.isResyncing = false
                    val wasPlayingBeforeResyncLocal = state.wasPlayingBeforeResync
                    state.wasPlayingBeforeResync = false
                    state.bufferingStartedAtMs = 0L

                    val now = SystemClock.elapsedRealtime()
                    if (!wasResync) {
                        val mainPos = currentPosition()
                        if (mainPos > 0) {
                            if (wasAlreadyReady) {
                                if (state.kickRenderer(now)) {
                                    Timber
                                        .tag(VideoPlaybackLogTag)
                                        .d("Surface re-attached while playing — restarted video renderer")
                                }
                            } else {
                                val videoPos = exoPlayer.currentPosition
                                val drift = kotlin.math.abs(videoPos - mainPos)
                                if (drift > VideoInitialSyncToleranceMs) {
                                    exoPlayer.seekTo(mainPos)

                                    state.lastSeekAtMs = now
                                }
                            }
                        }
                    }

                    val effectiveShouldPlay =
                        shouldPlay ||
                            (wasResync && wasPlayingBeforeResyncLocal) ||
                            (wasChangingQuality && state.wasPlayingBeforeQualityChange) ||
                            shouldResumeAudioAfterHold

                    val nothingToResume = wasAlreadyReady && !wasChangingQuality && !wasResync
                    if (effectiveShouldPlay && !nothingToResume && !state.hasPlaybackFailed &&
                        exoPlayer.playerError == null
                    ) {
                        val resumeMainAudio =
                            (wasChangingQuality && state.wasPlayingBeforeQualityChange) ||
                                (wasResync && wasPlayingBeforeResyncLocal) ||
                                shouldResumeAudioAfterHold
                        state.pendingResumeMainAudio = resumeMainAudio
                        state.pendingResumeVideo = true
                        state.pendingResumeAtMs =
                            if (resumeMainAudio) {
                                SystemClock.elapsedRealtime() + VideoLoadResumeDelayMs
                            } else {
                                SystemClock.elapsedRealtime()
                            }
                        Timber
                            .tag(VideoPlaybackLogTag)
                            .d(
                                "First frame rendered — resume scheduled " +
                                    "(${if (resumeMainAudio) "in ${VideoLoadResumeDelayMs}ms" else "immediately"}) " +
                                    "(audio=$resumeMainAudio, video=true)",
                            )
                    } else if (!nothingToResume) {
                        state.pendingResumeAtMs = 0L
                        state.pendingResumeMainAudio = false
                        state.pendingResumeVideo = false
                    }

                    resumeAudioAfterVideoReady = false
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> {
                            if (state.bufferingStartedAtMs == 0L) {
                                state.bufferingStartedAtMs = SystemClock.elapsedRealtime()
                            }
                        }
                        Player.STATE_READY -> {
                            state.bufferingStartedAtMs = 0L
                            state.bufferingRecoveries = 0

                            if (awaitingVideoReady && !updatedSheetSurfaceActive) {
                                Timber
                                    .tag(VideoPlaybackLogTag)
                                    .d("Video buffered without an on-screen surface — releasing the audio hold")
                                releaseAudioHold(resumeMainAudio = true)
                            }

                            val effectiveShouldPlay =
                                shouldPlay ||
                                    (state.isResyncing && state.wasPlayingBeforeResync) ||
                                    (state.isChangingQuality && state.wasPlayingBeforeQualityChange) ||
                                    resumeAudioAfterVideoReady
                            if (effectiveShouldPlay && !awaitingVideoReady && !state.hasPlaybackFailed &&
                                exoPlayer.playerError == null
                            ) {
                                val resumeMainAudio =
                                    (state.isChangingQuality && state.wasPlayingBeforeQualityChange) ||
                                        (state.isResyncing && state.wasPlayingBeforeResync) ||
                                        resumeAudioAfterVideoReady
                                state.pendingResumeMainAudio = resumeMainAudio
                                state.pendingResumeVideo = true
                                state.pendingResumeAtMs =
                                    SystemClock.elapsedRealtime() + VideoLoadResumeDelayMs
                            }
                        }
                        Player.STATE_ENDED -> {
                            state.bufferingStartedAtMs = 0L
                            val mainPos = currentPosition()
                            exoPlayer.seekTo(mainPos)

                            state.lastSeekAtMs = SystemClock.elapsedRealtime()
                            exoPlayer.setVideoPlayback(shouldPlay)
                        }
                        Player.STATE_IDLE -> {
                            state.bufferingStartedAtMs = 0L
                        }
                    }
                }
            }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            releaseAudioHold(resumeMainAudio = true)
            Handler(Looper.getMainLooper()).post {
                exoPlayer.release()
            }
        }
    }

    return state
}

@Composable
fun rememberVideoArtworkStateOrNull(
    videoId: String?,
    isPlaying: Boolean,
    positionProvider: () -> Long,
    preferredHeight: Int?,
    holdAudioUntilVideoReady: Boolean,
    onStreamResolved: (VideoStreamInfo?) -> Unit,
    onPlaybackFailed: () -> Unit,
    onLoadingStateChange: (Boolean) -> Unit,
    onRequestPauseMain: () -> Unit,
    onRequestResumeMain: () -> Unit,
    isMainAudioBuffering: Boolean = false,
    mainAudioReady: Boolean = false,
    sheetSurfaceActive: Boolean = true,
): VideoArtworkState? {
    return if (videoId.isNullOrBlank()) {
        onPlaybackFailed()
        null
    } else {
        rememberVideoArtworkState(
            videoId = videoId,
            isPlaying = isPlaying,
            positionProvider = positionProvider,
            preferredHeight = preferredHeight,
            holdAudioUntilVideoReady = holdAudioUntilVideoReady,
            onStreamResolved = onStreamResolved,
            onPlaybackFailed = onPlaybackFailed,
            onLoadingStateChange = onLoadingStateChange,
            onRequestPauseMain = onRequestPauseMain,
            onRequestResumeMain = onRequestResumeMain,
            isMainAudioBuffering = isMainAudioBuffering,
            mainAudioReady = mainAudioReady,
            sheetSurfaceActive = sheetSurfaceActive,
        )
    }
}

@Composable
fun VideoArtworkSurface(
    state: VideoArtworkState,
    modifier: Modifier = Modifier,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    ambientMode: Boolean = false,
    thumbnailUrl: String? = null,
) {
    val alpha by animateFloatAsState(
        targetValue = if (state.isVideoReady) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "videoAlpha",
    )

    Box(modifier = modifier) {
        if (ambientMode && !thumbnailUrl.isNullOrBlank()) {
            VideoAmbientBackdrop(
                thumbnailUrl = thumbnailUrl,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black),
            )
        }

        ContentFrame(
            player = state.exoPlayer,
            surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
            contentScale = resizeMode.toContentScale(),
            keepContentOnReset = false,
            shutter = {},
            modifier = Modifier.fillMaxSize().alpha(alpha),
        )

        val captionText = state.currentCaptionText
        if (state.selectedCaptionTrack != null && !captionText.isNullOrBlank()) {
            androidx.compose.material3.Text(
                text = captionText,
                color = Color.White,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun VideoAmbientBackdrop(
    thumbnailUrl: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isPreS = Build.VERSION.SDK_INT < Build.VERSION_CODES.S

    val transition = rememberInfiniteTransition(label = "video-ambient-drift")
    val animatedDriftX by transition.animateFloat(
        initialValue = -90f,
        targetValue = 90f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 19_000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "video-ambient-x",
    )
    val animatedDriftY by transition.animateFloat(
        initialValue = -60f,
        targetValue = 60f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 27_000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "video-ambient-y",
    )
    val preSDriftOff = isPreS
    fun currentDrift(): Pair<Float, Float> =
        if (preSDriftOff) 0f to 0f else animatedDriftX to animatedDriftY

    val blurredBitmap by produceState<Bitmap?>(null, thumbnailUrl) {
        value =
            withContext(Dispatchers.IO) {
                runCatching {
                    val request =
                        ImageRequest
                            .Builder(context)
                            .data(thumbnailUrl)
                            .allowHardware(false)
                            .memoryCacheKey("ambient:$thumbnailUrl")
                            .diskCacheKey("ambient:$thumbnailUrl")
                            .size(Size(540, 540))
                            .build()
                    val result = context.imageLoader.execute(request)
                    if (result is SuccessResult) {
                        val bitmap = result.image.toBitmap().copy(Bitmap.Config.ARGB_8888, true)
                        val density = context.resources.displayMetrics.density
                        ImageBlurUtils.blur(bitmap, 48f * density)
                    } else {
                        null
                    }
                }.getOrNull()
            }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .clipToBounds()
                .background(Color.Black),
    ) {
        blurredBitmap?.let { bm ->
            Image(
                bitmap = bm.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val (x, y) = currentDrift()
                            translationX = x
                            translationY = y
                            scaleX = 1.4f
                            scaleY = 1.4f
                            alpha = 0.85f
                        },
            )
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
        )
    }
}

private fun pickVideoFormat(
    playerResponse: PlayerResponse,
    preferredHeight: Int?,
): PlayerResponse.StreamingData.Format? {
    val streamingData = playerResponse.streamingData ?: return null
    val heightCeiling = maxVideoHeightFor(preferredHeight)

    val allVideoFormats =
        (streamingData.formats.orEmpty() + streamingData.adaptiveFormats.orEmpty())
            .asSequence()
            .filter {
                val h = it.height
                h != null && h > 0
            }
            .filter { (it.height ?: 0) <= heightCeiling }
            .filter { it.url != null || it.signatureCipher != null || it.cipher != null }
            .toList()

    if (allVideoFormats.isEmpty()) {
        return (streamingData.formats.withUsableHeight() + streamingData.adaptiveFormats.withUsableHeight())
            .filter { it.url != null || it.signatureCipher != null || it.cipher != null }
            .minByOrNull { it.height ?: Int.MAX_VALUE }
    }

    val comparator =
        compareByDescending<PlayerResponse.StreamingData.Format> { it.height ?: 0 }
            .thenByDescending { it.url != null }
            .thenByDescending { it.audioQuality != null }

    return allVideoFormats.sortedWith(comparator).firstOrNull()
}

private fun List<PlayerResponse.StreamingData.Format>?.withUsableHeight(): List<PlayerResponse.StreamingData.Format> =
    orEmpty().filter {
        val h = it.height
        h != null && h > 0
    }

private suspend fun resolveVideoStreamUrlViaSimpMusic(
    videoId: String,
    preferredHeight: Int?,
): VideoStreamInfo? {
    val result =
        withTimeoutOrNull(VideoSimpMusicAttemptTimeoutMs) {
            try {
                SimpMusicPlayer.player(videoId = videoId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (t: Throwable) {
                Result.failure(t)
            }
        } ?: run {
            Timber
                .tag(VideoPlaybackLogTag)
                .w(
                    "SimpMusic video resolution timed out for $videoId after " +
                        "${VideoSimpMusicAttemptTimeoutMs}ms — using the innertube chain",
                )
            return null
        }

    val response = result.getOrNull()?.second
    if (response == null) {
        Timber
            .tag(VideoPlaybackLogTag)
            .w(
                result.exceptionOrNull(),
                "SimpMusic video resolution failed for $videoId — using the innertube chain",
            )
        return null
    }

    val format =
        pickVideoFormat(response, preferredHeight)?.takeIf { !it.url.isNullOrBlank() }
    if (format == null) {
        Timber
            .tag(VideoPlaybackLogTag)
            .w("SimpMusic resolution produced no URL-bearing video format for $videoId")
        return null
    }
    val streamUrl =
        format.url ?: run {
            Timber
                .tag(VideoPlaybackLogTag)
                .w("SimpMusic video format for $videoId lost its URL — using the innertube chain")
            return null
        }

    val availableHeights =
        (response.streamingData?.formats.orEmpty() + response.streamingData?.adaptiveFormats.orEmpty())
            .mapNotNull { candidate ->
                candidate.height?.takeIf { it in 1..VideoDecoderCapabilities.maxSupportedHeight() }
            }.distinct()
            .sorted()
    val captionTracks =
        response.captions
            ?.playerCaptionsTracklistRenderer
            ?.captionTracks
            .orEmpty()

    Timber
        .tag(VideoPlaybackLogTag)
        .i("Resolved video stream for $videoId via SimpMusic (itag=${format.itag}, height=${format.height})")
    return VideoStreamInfo(
        streamUrl = streamUrl,
        availableHeights = availableHeights,
        captionTracks = captionTracks,
        selectedHeight = format.height?.takeIf { it > 0 },
    )
}

internal suspend fun resolveVideoStreamUrl(
    videoId: String,
    preferredHeight: Int?,
): VideoStreamInfo? {
    val heightCeiling = maxVideoHeightFor(preferredHeight)
    val cacheKey = "$videoId|$heightCeiling"
    resolveVideoStreamInfoFromCache(cacheKey)?.let { return it }

    var bestResult: VideoStreamInfo? = null
    var bestResultFromSimpMusic = false
    var simpMusicAttempted = false
    if (heightCeiling <= WebRemixMaxVideoHeight) {
        simpMusicAttempted = true
        resolveVideoStreamUrlViaSimpMusic(videoId, preferredHeight)?.let {
            cacheResolvedVideoStreamInfo(cacheKey, it)
            return it
        }
    }

    val authState = YouTube.currentPlaybackAuthState()
    val preferredClient =
        PreferenceStore
            .get(PlayerStreamClientKey)
            .toEnum(PlayerStreamClient.WEB_REMIX)
    val autoChoose = PreferenceStore.get(AutoChoosePlaybackClientKey) ?: true
    val clients = YTPlayerUtils.buildStreamClientOrder(preferredClient, authState)

    val usableClients =
        clients
            .filterNot { client ->
                YTPlayerUtils.isStreamClientBlocked(
                    videoId = videoId,
                    clientKey = StreamClientUtils.buildClientKey(client),
                    authFingerprint = authState.fingerprint,
                )
            }.let { ordered ->
                if (heightCeiling > WebRemixMaxVideoHeight) {
                    val (highCeiling, lowCeiling) =
                        ordered.partition { it.clientName !in LowCeilingVideoClientNames }
                    highCeiling + lowCeiling
                } else {
                    ordered
                }
            }

    var racedWinner: VideoStreamInfo? = null
    coroutineScope {
        val attempts: List<Pair<YouTubeClient, Deferred<Result<VideoStreamInfo?>>>> =
            usableClients
                .mapNotNull { client ->
                    val usesCookieAuthentication = authState.hasPlaybackLoginContext && client.supportsCookieAuthentication
                    if (client.loginRequired && !usesCookieAuthentication) return@mapNotNull null
                    client to async {
                        withTimeoutOrNull(VideoClientAttemptTimeoutMs) {
                            runCatching {
                                val signatureTimestamp =
                                    if (client.useSignatureTimestamp) {
                                        NewPipeUtils.getSignatureTimestamp(videoId).getOrNull()
                                    } else {
                                        null
                                    }
                                val poToken = authState.resolvePlayerPoToken(client, videoId = videoId)?.takeIf { it.isNotBlank() }
                                val playerResponse =
                                    YouTube
                                        .player(
                                            videoId = videoId,
                                            client = client,
                                            signatureTimestamp = signatureTimestamp,
                                            poToken = poToken,
                                            setLogin = usesCookieAuthentication,
                                            authState = authState,
                                        ).getOrThrow()
                                if (playerResponse.playabilityStatus.status != "OK") {
                                    throw IllegalStateException(
                                        "${client.clientName} returned ${playerResponse.playabilityStatus.status}: " +
                                            playerResponse.playabilityStatus.reason.orEmpty(),
                                    )
                                }

                                val availableHeights =
                                    (playerResponse.streamingData?.formats.orEmpty() +
                                        playerResponse.streamingData?.adaptiveFormats.orEmpty())
                                        .mapNotNull { format ->

                                            format.height?.takeIf { height ->
                                                height in 1..VideoDecoderCapabilities.maxSupportedHeight()
                                            }
                                        }.distinct()
                                        .sorted()
                                val captionTracks =
                                    playerResponse.captions
                                        ?.playerCaptionsTracklistRenderer
                                        ?.captionTracks
                                        .orEmpty()
                                val format = pickVideoFormat(playerResponse, preferredHeight) ?: return@runCatching null

                                val finalUrl = NewPipeUtils.getStreamUrl(format = format, videoId = videoId).getOrThrow()
                                VideoStreamInfo(
                                    streamUrl = finalUrl,
                                    availableHeights = availableHeights,
                                    captionTracks = captionTracks,
                                    selectedHeight = format.height?.takeIf { it > 0 },
                                )
                            }.onFailure { error ->
                                if (error is CancellationException) throw error
                            }
                        } ?: Result.failure(
                            SocketTimeoutException(
                                "Video client ${client.clientName} timed out after ${VideoClientAttemptTimeoutMs}ms",
                            ),
                        )
                    }
                }

        for ((client, deferred) in attempts) {
            val streamInfo = runCatching { deferred.await() }.getOrNull()?.getOrNull()
            if (streamInfo != null && streamInfo.streamUrl.isNotBlank()) {
                if ((streamInfo.selectedHeight ?: 0) >= heightCeiling) {
                    YTPlayerUtils.markStreamUrlSuccessful(streamInfo.streamUrl)
                    Timber
                        .tag(VideoPlaybackLogTag)
                        .i("Resolved video stream for $videoId via ${client.clientName}@${client.clientVersion} at ${streamInfo.selectedHeight}p (raced)")
                    cacheResolvedVideoStreamInfo(cacheKey, streamInfo)
                    racedWinner = streamInfo
                    break
                }

                if ((streamInfo.selectedHeight ?: 0) > (bestResult?.selectedHeight ?: Int.MIN_VALUE)) {
                    bestResult = streamInfo
                }
                Timber
                    .tag(VideoPlaybackLogTag)
                    .i(
                        "Video stream via ${client.clientName} reached only ${streamInfo.selectedHeight}p for $videoId (ceiling ${heightCeiling}p) — continuing",
                    )
                continue
            }

            run {
                if (!autoChoose) return@run
                val failure = runCatching { deferred.await() }.getOrNull()?.exceptionOrNull() ?: return@run
                YTPlayerUtils.markStreamClientFailed(
                    videoId = videoId,
                    clientKey = StreamClientUtils.buildClientKey(client),
                    httpStatusCode = null,
                    authFingerprint = authState.fingerprint,
                )
                Timber
                    .tag(VideoPlaybackLogTag)
                    .w(failure, "Video stream resolution failed for $videoId via ${client.clientName}@${client.clientVersion}")
            }
        }

        attempts.forEach { (_, deferred) -> deferred.cancel() }
    }
    if (racedWinner != null) return racedWinner

    if (!simpMusicAttempted && (bestResult?.selectedHeight ?: 0) < WebRemixMaxVideoHeight) {
        simpMusicAttempted = true
        resolveVideoStreamUrlViaSimpMusic(videoId, preferredHeight)?.let { simpmusic ->
            if ((simpmusic.selectedHeight ?: 0) > (bestResult?.selectedHeight ?: Int.MIN_VALUE)) {
                Timber
                    .tag(VideoPlaybackLogTag)
                    .i(
                        "SimpMusic last-resort supplies ${simpmusic.selectedHeight}p for $videoId " +
                            "after the innertube chain topped out at ${bestResult?.selectedHeight}p",
                    )
                bestResult = simpmusic
                bestResultFromSimpMusic = true
            }
        }
    }

    if (bestResult != null) {
        if (!bestResultFromSimpMusic) {
            YTPlayerUtils.markStreamUrlSuccessful(bestResult.streamUrl)
        }
        Timber
            .tag(VideoPlaybackLogTag)
            .i("Resolved video stream for $videoId at ${bestResult.selectedHeight}p (best available below the ${heightCeiling}p ceiling)")
        cacheResolvedVideoStreamInfo(cacheKey, bestResult)
        return bestResult
    }

    Timber.tag(VideoPlaybackLogTag).w("All video stream clients exhausted for $videoId")
    return null
}

private fun Int.toContentScale(): ContentScale =
    when (this) {
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> ContentScale.Crop
        AspectRatioFrameLayout.RESIZE_MODE_FILL -> ContentScale.FillBounds
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH,
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT,
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        -> ContentScale.Fit
        else -> ContentScale.Fit
    }

private fun ExoPlayer.setVideoPlayback(isPlaying: Boolean) {
    if (isPlaying) {
        if (playbackState == Player.STATE_IDLE && mediaItemCount > 0) prepare()
        if (playbackState == Player.STATE_ENDED) seekTo(0)
        play()
    } else {
        pause()
    }
}

internal const val VideoPlaybackLogTag = "VideoArtworkPlayback"
