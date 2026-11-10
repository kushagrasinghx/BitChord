/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package moe.rukamori.archivetune.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import dagger.hilt.android.EntryPointAccessors
import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import moe.rukamori.archivetune.di.CanvasCacheEntryPoint
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.utils.StreamClientUtils
import okhttp3.OkHttpClient
import timber.log.Timber
import java.util.Locale
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private const val CanvasPlaybackStallCheckIntervalMs = 1_000L
private const val CanvasPlaybackStallTimeoutMs = 5_000L

private const val CanvasSyncPublishIntervalMs = 50L
private const val CanvasSyncCheckIntervalMs = 200L

private const val CanvasSyncRateLockThresholdMs = 80L

private const val CanvasSyncSeekThresholdMs = 1_500L

private const val CanvasSyncRateLockSpanMs = 2_000f

private const val CanvasSyncMaxRateLockDelta = 0.03f

private const val CanvasOrientationCycleMs = 150L

val LocalPlayerSheetVisible = staticCompositionLocalOf { true }

class CanvasLoopSync {
    @Volatile
    var leaderSource: String? = null

    @Volatile
    var leaderPositionMs: Long = Long.MIN_VALUE
}

private val CanvasPlaybackHttpClient: OkHttpClient by lazy {
    OkHttpClient
        .Builder()
        .proxy(YouTube.streamOkHttpProxy)
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
                return@addInterceptor chain.proceed(
                    request
                        .newBuilder()
                        .header("User-Agent", CanvasPlaybackUserAgent)
                        .build(),
                )
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

private class CanvasPlaybackFactories(
    val mediaSourceFactory: DefaultMediaSourceFactory,
    val renderersFactory: DefaultRenderersFactory,
)

private object CanvasPlaybackFactoriesHolder {
    @Volatile
    private var instance: CanvasPlaybackFactories? = null

    fun obtain(
        context: Context,
        playerCache: Cache,
    ): CanvasPlaybackFactories = instance ?: synchronized(this) {
        instance ?: create(context.applicationContext, playerCache).also { instance = it }
    }

    private fun create(
        context: Context,
        playerCache: Cache,
    ): CanvasPlaybackFactories {
        val upstreamFactory =
            DefaultDataSource.Factory(
                context,
                OkHttpDataSource.Factory(CanvasPlaybackHttpClient),
            )
        val cacheFactory =
            CacheDataSource.Factory()
                .setCache(playerCache)
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        return CanvasPlaybackFactories(
            mediaSourceFactory = DefaultMediaSourceFactory(cacheFactory),
            renderersFactory = DefaultRenderersFactory(context).setEnableDecoderFallback(true),
        )
    }
}

@Composable
fun CanvasArtworkPlayer(
    primaryUrl: String?,
    fallbackUrl: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,

    visible: Boolean = true,

    videoAlpha: Float = 1f,

    maxVideoEdgePx: Int? = null,

    onPlaybackAvailabilityChange: ((available: Boolean) -> Unit)? = null,

    loopSyncLeader: CanvasLoopSync? = null,

    loopSyncFollower: CanvasLoopSync? = null,

    onFirstFrameRendered: (() -> Unit)? = null,

    refreshEpoch: Int = 0,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val primary = primaryUrl?.trim()?.takeIf { it.isNotBlank() }
    val fallback =
        fallbackUrl
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.takeUnless { it == primary }
    val initial = primary ?: fallback ?: return
    var currentUrl by remember(initial) { mutableStateOf(initial) }
    var isVideoReady by remember(initial) { mutableStateOf(false) }
    var hasPlaybackFailed by remember(initial) { mutableStateOf(false) }

    var videoDisplayAspectRatio by remember(initial) { mutableStateOf<Float?>(null) }

    val sheetVisible = LocalPlayerSheetVisible.current
    val playbackActive = isPlaying && sheetVisible
    val contentVisible = visible && sheetVisible
    val shouldPlay by rememberUpdatedState(playbackActive)
    val reportAvailability by rememberUpdatedState(onPlaybackAvailabilityChange)

    var surfaceCycleActive by remember { mutableStateOf(false) }
    LaunchedEffect(refreshEpoch) {
        if (refreshEpoch <= 0) return@LaunchedEffect
        surfaceCycleActive = true
        delay(CanvasOrientationCycleMs)
        surfaceCycleActive = false
    }
    val effectiveContentVisible = contentVisible && !surfaceCycleActive

    val playerCache =
        remember {
            val entryPoint =
                EntryPointAccessors.fromApplication(
                    context,
                    CanvasCacheEntryPoint::class.java,
                )
            entryPoint.playerCache()
        }
    val canvasFactories = remember(playerCache) {
        CanvasPlaybackFactoriesHolder.obtain(context, playerCache)
    }
    val mediaSourceFactory = canvasFactories.mediaSourceFactory
    val renderersFactory = canvasFactories.renderersFactory
    val exoPlayer =
        remember(mediaSourceFactory, renderersFactory, maxVideoEdgePx) {
            val trackSelector =
                DefaultTrackSelector(context).apply {
                    setParameters(
                        buildUponParameters()
                            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                            .setForceHighestSupportedBitrate(true)
                            .let { parameters ->
                                if (maxVideoEdgePx != null) {
                                    parameters.setMaxVideoSize(maxVideoEdgePx, maxVideoEdgePx)
                                } else {
                                    parameters
                                }
                            }
                            .build(),
                    )
                }
            val loadControl =
                DefaultLoadControl
                    .Builder()
                    .setBufferDurationsMs(
                        15_000,
                        30_000,
                        500,
                        1_000,
                    ).setPrioritizeTimeOverSizeThresholds(true)
                    .build()
            ExoPlayer
                .Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .setRenderersFactory(renderersFactory)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl)
                .build()
                .apply {
                    volume = 0f
                    repeatMode = Player.REPEAT_MODE_ONE
                    playWhenReady = shouldPlay
                }
        }

    LaunchedEffect(playbackActive) {
        if (hasPlaybackFailed) {
            exoPlayer.pause()
        } else {
            exoPlayer.setCanvasPlayback(playbackActive)
        }
    }

    LaunchedEffect(effectiveContentVisible) {
        if (effectiveContentVisible) {
            isVideoReady = false
        }
    }

    if (loopSyncLeader != null) {
        LaunchedEffect(exoPlayer, currentUrl) {
            while (isActive) {
                loopSyncLeader.leaderSource = currentUrl
                loopSyncLeader.leaderPositionMs = exoPlayer.currentPosition
                delay(CanvasSyncPublishIntervalMs)
            }
        }
    }

    if (loopSyncFollower != null) {
        LaunchedEffect(exoPlayer, currentUrl, hasPlaybackFailed) {
            while (isActive) {
                if (
                    !hasPlaybackFailed &&
                    exoPlayer.playbackState == Player.STATE_READY &&
                    exoPlayer.playerError == null
                ) {
                    val target = loopSyncFollower.leaderPositionMs
                    if (
                        target != Long.MIN_VALUE &&
                        loopSyncFollower.leaderSource == currentUrl
                    ) {
                        val errorMs = target - exoPlayer.currentPosition
                        val currentSpeed = exoPlayer.playbackParameters.speed
                        when {
                            kotlin.math.abs(errorMs) > CanvasSyncSeekThresholdMs -> {
                                exoPlayer.seekTo(target.coerceAtLeast(0L))
                                if (currentSpeed != 1f) exoPlayer.setPlaybackSpeed(1f)
                            }

                            kotlin.math.abs(errorMs) > CanvasSyncRateLockThresholdMs -> {
                                val speed =
                                    (1f + errorMs / CanvasSyncRateLockSpanMs)
                                        .coerceIn(
                                            1f - CanvasSyncMaxRateLockDelta,
                                            1f + CanvasSyncMaxRateLockDelta,
                                        )
                                if (kotlin.math.abs(speed - currentSpeed) > 0.0005f) {
                                    exoPlayer.setPlaybackSpeed(speed)
                                }
                            }

                            else -> {
                                if (kotlin.math.abs(1f - currentSpeed) > 0.0005f) {
                                    exoPlayer.setPlaybackSpeed(1f)
                                }
                            }
                        }
                    }
                }
                delay(CanvasSyncCheckIntervalMs)
            }
        }
    }

    LaunchedEffect(currentUrl, playbackActive, primary, fallback, exoPlayer) {
        if (!playbackActive || fallback.isNullOrBlank() || currentUrl != primary) return@LaunchedEffect

        var lastPosition = exoPlayer.currentPosition
        var stalledForMs = 0L

        while (isActive && playbackActive && currentUrl == primary) {
            delay(CanvasPlaybackStallCheckIntervalMs)

            val currentPosition = exoPlayer.currentPosition
            val playbackState = exoPlayer.playbackState
            val positionAdvanced = currentPosition != lastPosition
            val isActivelyRendering =
                playbackState == Player.STATE_READY &&
                    exoPlayer.isPlaying &&
                    positionAdvanced

            stalledForMs =
                if (isActivelyRendering) {
                    0L
                } else {
                    stalledForMs + CanvasPlaybackStallCheckIntervalMs
                }

            if (stalledForMs >= CanvasPlaybackStallTimeoutMs) {
                currentUrl = fallback
                isVideoReady = false
                return@LaunchedEffect
            }

            lastPosition = currentPosition
        }
    }

    DisposableEffect(exoPlayer, lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (
                    (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) &&
                    !hasPlaybackFailed &&
                    exoPlayer.playerError == null
                ) {
                    exoPlayer.setCanvasPlayback(shouldPlay)
                }
                if (event == Lifecycle.Event.ON_STOP) {

                    runCatching { exoPlayer.pause() }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(exoPlayer, primary, fallback) {
        val listener =
            object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Timber.tag(CanvasPlaybackLogTag).w(error, "Canvas playback failed")
                    hasPlaybackFailed = true
                    isVideoReady = false
                    val next =
                        when (currentUrl) {
                            primary -> fallback?.takeIf { it != currentUrl }
                            else -> null
                        }
                    if (!next.isNullOrBlank()) {
                        currentUrl = next
                    } else {
                        exoPlayer.stop()
                        reportAvailability?.invoke(false)
                    }
                }

                override fun onRenderedFirstFrame() {
                    isVideoReady = true
                    onFirstFrameRendered?.invoke()
                    reportAvailability?.invoke(true)
                    if (shouldPlay && !hasPlaybackFailed && exoPlayer.playerError == null) {
                        exoPlayer.setCanvasPlayback(isPlaying = true)
                    }
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    val width = videoSize.width
                    val height = videoSize.height
                    videoDisplayAspectRatio =
                        if (width > 0 && height > 0) {
                            val par = videoSize.pixelWidthHeightRatio
                            (width.toFloat() * (if (par > 0f) par else 1f)) / height.toFloat()
                        } else {
                            null
                        }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (!shouldPlay || hasPlaybackFailed || exoPlayer.playerError != null) return
                    exoPlayer.setCanvasPlayback(isPlaying = true)

                    if (playbackState == Player.STATE_READY && loopSyncFollower != null) {
                        val target = loopSyncFollower.leaderPositionMs
                        if (
                            target != Long.MIN_VALUE &&
                            loopSyncFollower.leaderSource == currentUrl
                        ) {
                            exoPlayer.seekTo(target.coerceAtLeast(0L))
                        }
                    }
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    if (reason != Player.DISCONTINUITY_REASON_AUTO_TRANSITION) return
                    when {
                        loopSyncLeader != null -> {
                            loopSyncLeader.leaderSource = currentUrl
                            loopSyncLeader.leaderPositionMs = exoPlayer.currentPosition
                        }

                        loopSyncFollower != null -> {
                            val target = loopSyncFollower.leaderPositionMs
                            if (
                                target != Long.MIN_VALUE &&
                                loopSyncFollower.leaderSource == currentUrl &&
                                kotlin.math.abs(newPosition.positionMs - target) >
                                    CanvasSyncSeekThresholdMs
                            ) {
                                exoPlayer.seekTo(target.coerceAtLeast(0L))
                            }
                        }
                    }
                }

                override fun onPlayWhenReadyChanged(
                    playWhenReady: Boolean,
                    reason: Int,
                ) {
                    if (shouldPlay && !playWhenReady && !hasPlaybackFailed && exoPlayer.playerError == null) {
                        exoPlayer.setCanvasPlayback(isPlaying = true)
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (shouldPlay && !isPlaying && !hasPlaybackFailed && exoPlayer.playerError == null) {
                        exoPlayer.setCanvasPlayback(isPlaying = true)
                    }
                }
            }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(currentUrl, exoPlayer) {
        val normalized = currentUrl.trim()
        isVideoReady = false
        hasPlaybackFailed = false
        videoDisplayAspectRatio = null

        reportAvailability?.invoke(false)
        val lowercaseUrl = normalized.lowercase(Locale.ROOT)
        val mimeType =
            when {
                lowercaseUrl.contains("m3u8") -> MimeTypes.APPLICATION_M3U8
                lowercaseUrl.contains("mp4") -> MimeTypes.VIDEO_MP4
                primary != null && currentUrl == primary -> MimeTypes.APPLICATION_M3U8
                fallback != null && currentUrl == fallback -> MimeTypes.VIDEO_MP4
                else -> MimeTypes.APPLICATION_M3U8
            }

        val mediaItem =
            MediaItem
                .Builder()
                .setUri(normalized)
                .setMimeType(mimeType)
                .build()

        exoPlayer.stop()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.setCanvasPlayback(shouldPlay)
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (isVideoReady) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "canvasAlpha",
    )

    val surfaceRetireAlpha by animateFloatAsState(
        targetValue = if (effectiveContentVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "canvasSurfaceRetireAlpha",
    )
    var surfaceStructureReleased by remember { mutableStateOf(false) }
    LaunchedEffect(effectiveContentVisible) {
        if (effectiveContentVisible) {
            surfaceStructureReleased = false
        } else {
            delay(CanvasSurfaceRetireFadeMs)
            surfaceStructureReleased = true
        }
    }
    val surfaceAlpha = alpha * surfaceRetireAlpha * videoAlpha

    val aspect = videoDisplayAspectRatio
    if (effectiveContentVisible || !surfaceStructureReleased) {
        if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM && aspect != null && aspect > 0f) {
            Box(modifier = modifier.clipToBounds()) {
                PlayerSurface(
                    player = exoPlayer,
                    surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(surfaceAlpha)
                        .canvasCoverLayout(aspect),
                )
            }
        } else {
            Box(modifier = modifier.clipToBounds()) {
                PlayerSurface(
                    player = exoPlayer,
                    surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(surfaceAlpha),
                )
            }
        }
    }
}

private fun Modifier.canvasCoverLayout(videoAspect: Float): Modifier =
    layout { measurable, constraints ->
        val containerWidth = constraints.maxWidth
        val containerHeight = constraints.maxHeight
        if (containerWidth <= 0 || containerHeight <= 0) {
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        } else {
            val containerAspect = containerWidth.toFloat() / containerHeight.toFloat()
            val targetWidth: Int
            val targetHeight: Int
            if (videoAspect >= containerAspect) {
                targetHeight = containerHeight
                targetWidth = (containerHeight.toFloat() * videoAspect + 0.5f).toInt().coerceAtLeast(containerWidth)
            } else {
                targetWidth = containerWidth
                targetHeight = (containerWidth.toFloat() / videoAspect + 0.5f).toInt().coerceAtLeast(containerHeight)
            }
            val placeable =
                measurable.measure(
                    Constraints.fixed(
                        targetWidth.coerceAtLeast(1),
                        targetHeight.coerceAtLeast(1),
                    )
                )
            layout(containerWidth, containerHeight) {
                placeable.place(
                    -((targetWidth - containerWidth) / 2),
                    -((targetHeight - containerHeight) / 2),
                )
            }
        }
    }

private fun ExoPlayer.setCanvasPlayback(isPlaying: Boolean) {
    if (isPlaying) {
        if (playbackState == Player.STATE_ENDED) seekTo(0)
        if (playbackState == Player.STATE_IDLE && mediaItemCount > 0) prepare()
        play()
    } else {
        pause()
    }
}

private const val CanvasPlaybackLogTag = "CanvasPlayback"
private const val CanvasSurfaceRetireFadeMs = 150L
private const val CanvasPlaybackUserAgent =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36"
