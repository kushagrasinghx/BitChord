/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player.spatialflow

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import moe.rukamori.archivetune.LocalStableSystemBarsTopPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.offline.Download
import androidx.media3.ui.AspectRatioFrameLayout
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.ui.player.CanvasArtworkPlayer
import moe.rukamori.archivetune.ui.player.CanvasLoopSync
import moe.rukamori.archivetune.ui.player.LocalVideoSelectedHeight
import moe.rukamori.archivetune.ui.player.LocalVideoAvailableHeights
import moe.rukamori.archivetune.ui.player.LocalVideoOnPreferredHeightChange
import moe.rukamori.archivetune.ui.player.LocalVideoPreferredHeight
import moe.rukamori.archivetune.ui.player.LocalVideoPlaybackFailed
import moe.rukamori.archivetune.ui.player.LocalVideoArtworkState
import moe.rukamori.archivetune.ui.player.InlineVideoPlayer
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.source.ShuffleOrder
import coil3.compose.AsyncImage
import kotlin.math.roundToInt
import moe.rukamori.archivetune.LocalDownloadUtil
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.extensions.move
import moe.rukamori.archivetune.lyrics.LyricsUtils
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.ExoDownloadService
import moe.rukamori.archivetune.playback.MusicHapticsSettings
import moe.rukamori.archivetune.ui.component.BottomSheetPageState
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.menu.PlayerMenu
import moe.rukamori.archivetune.ui.utils.ShowMediaInfo
import moe.rukamori.archivetune.utils.isLocalMediaId
import moe.rukamori.archivetune.ui.player.rememberMeshPalette
import moe.rukamori.archivetune.ui.utils.highRes
import androidx.navigation.NavController
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private const val SfCanvasBackdropUpscale = 6f
private const val SfCanvasBackdropOverscan = 1.10f
private val SfCanvasBackdropBlurRadius = 72.dp
private const val SfCanvasBackdropMaxVideoEdgePx = 480

internal val SfCanvasScrimBrush =
    Brush.verticalGradient(
        0f to Color.Black.copy(alpha = 0.25f),
        0.5f to Color.Black.copy(alpha = 0.40f),
        1f to Color.Black.copy(alpha = 0.65f),
    )

private const val SfSharpStageFadeStart = 0.62f

private val SfSharpStageFadeBrush =
    Brush.verticalGradient(
        SfSharpStageFadeStart to Color.Black,
        1f to Color.Transparent,
    )

private const val SfLyricsBackdropMorphMs = 650

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun SpatialFlowPlayerContent(
    mediaMetadata: MediaMetadata,
    isPlaying: Boolean,
    isLoading: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    duration: Long,
    playerConnection: PlayerConnection,
    navController: NavController,
    state: BottomSheetState,
    menuState: MenuState,
    bottomSheetPageState: BottomSheetPageState,
    currentFormat: FormatEntity?,
    positionProvider: () -> Long,
    canvasPrimaryUrl: String? = null,
    canvasFallbackUrl: String? = null,
    appIsDark: Boolean = isSystemInDarkTheme(),
    onSeek: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    floatingArtwork: Boolean = false,
    onArtworkSlotPositioned: ((androidx.compose.ui.geometry.Rect?) -> Unit)? = null,
    onPagerArtworkActiveChange: ((Boolean) -> Unit)? = null,
    onLyricsOpenChange: ((Boolean) -> Unit)? = null,
    onQueueExpandedChange: ((Boolean) -> Unit)? = null,
    externalBackRequests: androidx.compose.runtime.MutableIntState? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val isDark = appIsDark
    val canvasAvailable = !canvasPrimaryUrl.isNullOrBlank() || !canvasFallbackUrl.isNullOrBlank()

    val canvasLoopSync = remember { CanvasLoopSync() }

    val videoState = LocalVideoArtworkState.current
    val videoPlaybackFailed = LocalVideoPlaybackFailed.current
    val surfaceIsDark = isDark || canvasAvailable
    val contentColor = if (surfaceIsDark) Color.White else Color(0xFF1C1B1F)
    val contentSecondary = if (surfaceIsDark) Color.White.copy(alpha = 0.6f) else Color(0xFF1C1B1F).copy(alpha = 0.6f)

    val queueWindows by playerConnection.queueWindows.collectAsStateWithLifecycle()
    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsStateWithLifecycle()
    val currentSong by playerConnection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val currentLyricsEntity by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
    val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsStateWithLifecycle()
    val repeatMode by playerConnection.repeatMode.collectAsStateWithLifecycle()
    val downloadUtil = LocalDownloadUtil.current
    val download by downloadUtil
        .getDownload(mediaMetadata.id)
        .collectAsStateWithLifecycle(initialValue = null)
    val fetchProgressMap by moe.rukamori.archivetune.playback.DownloadFetchProgress.flow
        .collectAsStateWithLifecycle()

    val artUrl = remember(mediaMetadata.id, mediaMetadata.thumbnailUrl) { mediaMetadata.thumbnailUrl?.highRes() }
    val palette = rememberMeshPalette(artUrl)
    val playerBackgroundColor = palette.colors.firstOrNull() ?: Color(0xFF202022)

    LaunchedEffect(artUrl) {
        if (artUrl != null && SfLyricsBlurBitmapCache.get(artUrl) == null) {
            loadSfLyricsBlurredBitmap(context, artUrl)
        }
    }

    val dynamicAccentColor =
        remember(playerBackgroundColor, surfaceIsDark) {
            val hsl = FloatArray(3)
            androidx.core.graphics.ColorUtils.colorToHSL(playerBackgroundColor.toArgb(), hsl)
            if (hsl[1] < 0.08f) {
                if (surfaceIsDark) Color.White else Color(0xFF1C1B1F)
            } else {
                if (surfaceIsDark) {
                    playerBackgroundColor
                } else {
                    hsl[2] = hsl[2].coerceAtMost(0.45f)
                    hsl[1] = hsl[1].coerceAtLeast(0.6f)
                    Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
                }
            }
        }

    val backgroundBrush =
        remember(playerBackgroundColor, surfaceIsDark) {
            val finalColor =
                deriveArtworkSurfaceColor(
                    sourceColor = playerBackgroundColor,
                    isDark = surfaceIsDark,
                    darkLightness = 0.155f,
                    lightLightness = 0.835f,
                    darkSaturationRange = 0.32f..0.54f,
                    lightSaturationRange = 0.30f..0.48f,
                )
            SolidColor(finalColor)
        }

    val lyricsBackgroundBrush =
        remember(playerBackgroundColor) {
            val finalColor =
                deriveArtworkSurfaceColor(
                    sourceColor = playerBackgroundColor,
                    isDark = true,
                    darkLightness = 0.145f,
                    lightLightness = 0.825f,
                    darkSaturationRange = 0.32f..0.54f,
                    lightSaturationRange = 0.30f..0.48f,
                )
            SolidColor(finalColor)
        }

    var lyricsModeEnabled by rememberSaveable { mutableStateOf(false) }
    var lyricsModeSongId by rememberSaveable { mutableStateOf(mediaMetadata.id) }
    LaunchedEffect(mediaMetadata.id) {
        val candidate = mediaMetadata.id
        if (candidate == lyricsModeSongId) return@LaunchedEffect

        delay(250)
        if (mediaMetadata.id == candidate) {
            lyricsModeSongId = candidate
            lyricsModeEnabled = false
        }
    }
    val videoShowing =
        videoState != null &&
            mediaMetadata.isMusicVideo &&
            !mediaMetadata.id.isLocalMediaId() &&
            !lyricsModeEnabled &&
            !videoPlaybackFailed
    val syncedLyrics =
        remember(currentLyricsEntity?.lyrics) {
            val text = currentLyricsEntity?.lyrics
            if (text.isNullOrBlank()) {
                null
            } else {
                runCatching {
                    if (LyricsUtils.isTtml(text)) {
                        LyricsUtils.parseTtml(text)
                    } else {
                        LyricsUtils.parseLyrics(text)
                    }
                }
                    .getOrNull()
                    ?.takeIf { it.isNotEmpty() }
            }
        }
    val plainLyrics =
        remember(currentLyricsEntity?.lyrics, syncedLyrics) {
            if (syncedLyrics != null) null else currentLyricsEntity?.lyrics?.takeIf { it.isNotBlank() }
        }

    var queueExpanded by rememberSaveable { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    val sleepTimer = remember(playerConnection) { playerConnection.service.sleepTimer }
    val sleepTimerMode =
        remember(sleepTimer.triggerTime, sleepTimer.pauseWhenSongEnd) {
            when {
                sleepTimer.pauseWhenSongEnd -> SpatialFlowSleepTimerMode.END_OF_SONG
                sleepTimer.triggerTime != -1L -> SpatialFlowSleepTimerMode.CUSTOM
                else -> SpatialFlowSleepTimerMode.OFF
            }
        }

    BackHandler(enabled = lyricsModeEnabled || queueExpanded) {
        if (lyricsModeEnabled) {
            lyricsModeEnabled = false
        } else if (queueExpanded) {
            queueExpanded = false
        }
    }

    LaunchedEffect(state.isExpandedOrExpanding) {
        if (!state.isExpandedOrExpanding && (lyricsModeEnabled || queueExpanded)) {
            lyricsModeEnabled = false
            queueExpanded = false
        }
    }
    val externalBackEpoch = externalBackRequests?.intValue ?: 0
    LaunchedEffect(externalBackEpoch) {
        if (externalBackEpoch > 0) {
            if (lyricsModeEnabled) lyricsModeEnabled = false
            if (queueExpanded) queueExpanded = false
        }
    }

    LaunchedEffect(lyricsModeEnabled) {
        onLyricsOpenChange?.invoke(lyricsModeEnabled)
    }
    LaunchedEffect(queueExpanded) {
        onQueueExpandedChange?.invoke(queueExpanded)
    }

    var hapticsEnabled by remember { mutableStateOf(MusicHapticsSettings.isEnabled(context)) }

    var canvasPlayingForLyrics by remember { mutableStateOf(true) }
    var canvasSurfacesForLyrics by remember { mutableStateOf(true) }
    LaunchedEffect(lyricsModeEnabled) {
        if (lyricsModeEnabled) {
            canvasPlayingForLyrics = false
            canvasSurfacesForLyrics = true
            delay(SfLyricsBackdropMorphMs.toLong())
            canvasSurfacesForLyrics = false
        } else {
            canvasPlayingForLyrics = true
            canvasSurfacesForLyrics = true
        }
    }
    val lyricsBackdropProgress by animateFloatAsState(
        targetValue = if (lyricsModeEnabled) 1f else 0f,
        animationSpec = tween(durationMillis = SfLyricsBackdropMorphMs, easing = FastOutSlowInEasing),
        label = "SfLyricsCanvasFade",
    )

    val lyricsArtworkProgress by animateFloatAsState(
        targetValue = if (lyricsModeEnabled) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 420f),
        label = "SfLyricsArtworkSharedElement",
    )

    val density = LocalDensity.current
    var playerRootTopY by remember { mutableStateOf(0f) }
    var titleTopInRootY by remember { mutableStateOf<Float?>(null) }
    val sharpStageHeight: Dp? =
        titleTopInRootY?.let { top ->
            with(density) { (top - playerRootTopY).coerceAtLeast(0f).toDp() }
        }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .onGloballyPositioned { playerRootTopY = it.positionInRoot().y },
    ) {
        SpatialFlowBlurredBackdrop(
            artUrl = artUrl,
            withScrim = !canvasAvailable,
            isDark = isDark,
            modifier = Modifier.matchParentSize(),
        )

        if (canvasAvailable) {
            val configuration = LocalConfiguration.current
            val stageFraction =
                sharpStageHeight?.let { (it / configuration.screenHeightDp.dp).coerceIn(0.1f, 1f) } ?: 0.55f
            val frostFraction = (1f - SfSharpStageFadeStart * stageFraction).coerceIn(0.2f, 1f)
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(frostFraction)
                        .graphicsLayer {
                            alpha = 1f - lyricsBackdropProgress
                        },
            ) {
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .graphicsLayer {
                                val scale = SfCanvasBackdropOverscan * SfCanvasBackdropUpscale
                                scaleX = scale
                                scaleY = scale
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    CanvasArtworkPlayer(
                        primaryUrl = canvasPrimaryUrl,
                        fallbackUrl = canvasFallbackUrl,
                        isPlaying = isPlaying && canvasPlayingForLyrics,
                        visible = canvasSurfacesForLyrics,
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                        maxVideoEdgePx = SfCanvasBackdropMaxVideoEdgePx,
                        loopSyncFollower = canvasLoopSync,
                        modifier =
                            Modifier
                                .fillMaxWidth(1f / SfCanvasBackdropUpscale)
                                .fillMaxHeight(1f / SfCanvasBackdropUpscale)
                                .blur(SfCanvasBackdropBlurRadius / SfCanvasBackdropUpscale),
                    )
                }
            }

            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .background(SfCanvasScrimBrush),
            )

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (sharpStageHeight != null) {
                                Modifier.height(sharpStageHeight)
                            } else {
                                Modifier.fillMaxHeight(0.55f)
                            },
                        )
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                            alpha = 1f - lyricsBackdropProgress
                        }
                        .drawWithContent {
                            drawContent()
                            drawRect(brush = SfSharpStageFadeBrush, blendMode = BlendMode.DstIn)
                        },
            ) {
                CanvasArtworkPlayer(
                    primaryUrl = canvasPrimaryUrl,
                    fallbackUrl = canvasFallbackUrl,
                    isPlaying = isPlaying && canvasPlayingForLyrics,
                    visible = canvasSurfacesForLyrics,
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                    loopSyncLeader = canvasLoopSync,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }

        MaterialTheme(typography = SpatialFlowTypography) {
                val configuration = LocalConfiguration.current
                val screenWidth = configuration.screenWidthDp.dp
                val albumArtSize = screenWidth * 0.9f

                val statusBarTopDp = LocalStableSystemBarsTopPadding.current

                var lyricsButtonCenterInRoot by remember { mutableStateOf<Offset?>(null) }
                var artworkPagerBoundsInRoot by remember { mutableStateOf<Rect?>(null) }
                val lyricsRevealProgress by animateFloatAsState(
                    targetValue = if (lyricsModeEnabled) 1f else 0f,
                    animationSpec = tween(durationMillis = 340, easing = FastOutSlowInEasing),
                    label = "LyricsCircularReveal",
                )

                val lyricsContentReady by remember {
                    derivedStateOf { lyricsRevealProgress > 0.45f }
                }
                val lyricsOverlayVisible by remember {
                    derivedStateOf { lyricsRevealProgress > 0.01f }
                }

                val keepMainContentComposed by remember(mediaMetadata.id) {
                    derivedStateOf { !lyricsModeEnabled || lyricsRevealProgress < 0.995f }
                }
                androidx.compose.runtime.LaunchedEffect(videoShowing, canvasAvailable, lyricsModeEnabled) {
                    onPagerArtworkActiveChange?.invoke(!videoShowing && !canvasAvailable && !lyricsModeEnabled)
                }
                if (keepMainContentComposed) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(top = statusBarTopDp)
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { state.collapseSoft() }) {
                        Icon(
                            painter = painterResource(id = R.drawable.spatialflow_ic_keyboard_arrow_down),
                            contentDescription = "Collapse Player",
                            tint = contentColor.copy(alpha = 0.8f),
                            modifier = Modifier.size(28.dp),
                        )
                    }

                    Text(
                        text = "NOW PLAYING",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = contentSecondary,
                    )

                    IconButton(
                        onClick = {
                            menuState.show {
                                PlayerMenu(
                                    mediaMetadata = mediaMetadata,
                                    navController = navController,
                                    playerBottomSheetState = state,
                                    onShowDetailsDialog = {
                                        bottomSheetPageState.show {
                                            ShowMediaInfo(mediaMetadata.id)
                                        }
                                    },
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.spatialflow_ic_more_vert),
                            contentDescription = "More",
                            tint = contentColor.copy(alpha = 0.8f),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                if (videoShowing && videoState != null) {
                    InlineVideoPlayer(
                        state = videoState,
                        preferredHeight = LocalVideoPreferredHeight.current,
                        onPreferredHeightChange = LocalVideoOnPreferredHeightChange.current,
                        availableHeights = LocalVideoAvailableHeights.current,
                        selectedHeight = LocalVideoSelectedHeight.current,
                        controlsOnTap = true,
                        modifier =
                            Modifier
                                .size(albumArtSize)
                                .clip(RoundedCornerShape(16.dp)),
                    )

                    Spacer(modifier = Modifier.height(36.dp))
                } else if (!canvasAvailable && floatingArtwork) {
                    Box(
                        modifier =
                            Modifier
                                .size(albumArtSize)
                                .onGloballyPositioned { coordinates ->
                                    val position = coordinates.positionInRoot()
                                    val rect =
                                        Rect(
                                            offset = position,
                                            size =
                                                Size(
                                                    width = coordinates.size.width.toFloat(),
                                                    height = coordinates.size.height.toFloat(),
                                                ),
                                        )
                                    artworkPagerBoundsInRoot = rect
                                    onArtworkSlotPositioned?.invoke(rect)
                                },
                    )
                    androidx.compose.runtime.DisposableEffect(Unit) {
                        onDispose {
                            if (!lyricsModeEnabled) {
                                artworkPagerBoundsInRoot = null
                                onArtworkSlotPositioned?.invoke(null)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(36.dp))
                } else if (!canvasAvailable) {
                    SpatialFlowArtworkPager(
                        mediaMetadata = mediaMetadata,
                        queueWindows = queueWindows,
                        currentWindowIndex = currentWindowIndex,
                        userScrollEnabled = !lyricsModeEnabled && !queueExpanded,
                        artUrl = artUrl,
                        isPlaying = isPlaying,
                        cornerRadius = 16.dp,
                        shadowElevation = 0.dp,
                        onPlaySongAtWindow = { windowIndex ->
                            val window = queueWindows.getOrNull(windowIndex) ?: return@SpatialFlowArtworkPager
                            playerConnection.player.seekToDefaultPosition(window.firstPeriodIndex)
                            playerConnection.player.playWhenReady = true
                        },
                        modifier =
                            Modifier
                                .size(albumArtSize)
                                .onGloballyPositioned { coordinates ->
                                    val position = coordinates.positionInRoot()
                                    artworkPagerBoundsInRoot =
                                        Rect(
                                            offset = position,
                                            size =
                                                Size(
                                                    width = coordinates.size.width.toFloat(),
                                                    height = coordinates.size.height.toFloat(),
                                                ),
                                        )
                                },
                    )

                    Spacer(modifier = Modifier.height(36.dp))
                }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                            .onGloballyPositioned { titleTopInRootY = it.positionInRoot().y },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = mediaMetadata.title,
                            style =
                                if (canvasAvailable) {
                                    MaterialTheme.typography.displayMedium
                                } else {
                                    MaterialTheme.typography.headlineMediumEmphasized
                                },
                            fontWeight = FontWeight.Bold,
                            color = contentColor,
                            maxLines = 1,
                            modifier = Modifier.basicMarqueeWithFadedEdges(),
                        )
                        Spacer(modifier = Modifier.height(if (canvasAvailable) 6.dp else 4.dp))
                        Text(
                            text = mediaMetadata.artists.joinToString { it.name },
                            style = if (canvasAvailable) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                            color = contentSecondary,
                            maxLines = 1,
                            modifier =
                                Modifier
                                    .basicMarqueeWithFadedEdges()
                                    .clickable {
                                        mediaMetadata.artists.firstOrNull()?.id?.let { artistId ->
                                            state.collapseSoft()
                                            navController.navigate("artist/$artistId")
                                        }
                                    },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .layout { measurable, constraints ->
                                val pad = 20.dp.roundToPx()
                                val placeable =
                                    measurable.measure(
                                        constraints.copy(
                                            maxWidth = constraints.maxWidth + 2 * pad,
                                        ),
                                    )
                                layout(placeable.width - 2 * pad, placeable.height) {
                                    placeable.place(-pad, 0)
                                }
                            }.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(modifier = Modifier.width(12.dp))

                    SplitLikeDislikeChip(
                        isLiked = currentSong?.song?.liked == true,
                        isDisliked = false,
                        likesCount = "Like",
                        onLikeClick = { playerConnection.toggleLike() },

                        onDislikeClick = {
                            if (currentSong?.song?.liked == true) playerConnection.toggleLike()
                        },
                        contentColor = contentColor,
                        accentColor = dynamicAccentColor,
                        isDark = surfaceIsDark,
                    )

                    PillChip(
                        icon = painterResource(id = R.drawable.spatialflow_ic_haptic),
                        label = "Music Haptics",
                        isSelected = hapticsEnabled,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val next = !hapticsEnabled
                            MusicHapticsSettings.setEnabled(context, next)
                            hapticsEnabled = next
                        },
                        contentColor = contentColor,
                        accentColor = dynamicAccentColor,
                        isDark = surfaceIsDark,
                    )

                    PillChip(
                        icon = painterResource(id = R.drawable.spatialflow_ic_lyrics),
                        label = "Lyrics",
                        isSelected = lyricsModeEnabled,
                        onClick = { lyricsModeEnabled = true },
                        contentColor = contentColor,
                        accentColor = dynamicAccentColor,
                        isDark = surfaceIsDark,
                        modifier =
                            Modifier.onGloballyPositioned { coordinates ->
                                val position = coordinates.positionInRoot()
                                lyricsButtonCenterInRoot =
                                    Offset(
                                        x = position.x + coordinates.size.width / 2f,
                                        y = position.y + coordinates.size.height / 2f,
                                    )
                            },
                    )

                    PillChip(
                        icon = painterResource(id = R.drawable.spatialflow_ic_share),
                        label = "Share",
                        onClick = {
                            val shareIntent =
                                Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "https://music.youtube.com/watch?v=${mediaMetadata.id}",
                                    )
                                    type = "text/plain"
                                }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Track"))
                        },
                        contentColor = contentColor,
                        accentColor = dynamicAccentColor,
                        isDark = surfaceIsDark,
                    )

                    val realDownloaded = download?.state == Download.STATE_COMPLETED
                    val realDownloadProgress =
                        if (download?.state == Download.STATE_DOWNLOADING) {
                            val media3Percent = (download?.percentDownloaded ?: 0f).toDouble()
                            val fetchPercent = fetchProgressMap[downloadUtil
                                .currentSourceDownloadTarget(mediaMetadata.id)
                                .key]?.percent ?: 0
                            maxOf(media3Percent, fetchPercent.toDouble()).roundToInt()
                        } else {
                            null
                        }
                    val isDownloading = realDownloadProgress != null

                    val downloadLabel =
                        when {
                            realDownloaded -> "Downloaded"
                            isDownloading -> "Downloading $realDownloadProgress%"
                            else -> "Download"
                        }
                    val downloadIcon: Any =
                        if (realDownloaded) {
                            painterResource(id = R.drawable.spatialflow_ic_downloaded)
                        } else {
                            painterResource(id = R.drawable.spatialflow_ic_download)
                        }
                    PillChip(
                        icon = downloadIcon,
                        label = downloadLabel,
                        isSelected = realDownloaded || isDownloading,
                        progress = if (isDownloading) (realDownloadProgress ?: 0) / 100f else null,
                        onClick = {
                            val target = downloadUtil.currentSourceDownloadTarget(mediaMetadata.id)
                            when (download?.state) {
                                Download.STATE_COMPLETED, Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> {
                                    DownloadService.sendRemoveDownload(
                                        context,
                                        ExoDownloadService::class.java,
                                        target.key,
                                        false,
                                    )
                                }

                                else -> {
                                    val dl = download
                                    if (dl != null && dl.state != Download.STATE_COMPLETED) {
                                        DownloadService.sendRemoveDownload(
                                            context,
                                            ExoDownloadService::class.java,
                                            dl.request.id,
                                            false,
                                        )
                                    }
                                    val downloadRequest =
                                        DownloadRequest
                                            .Builder(target.key, mediaMetadata.id.toUri())
                                            .setCustomCacheKey(target.key)
                                            .setData(mediaMetadata.title.toByteArray())
                                            .build()
                                    DownloadService.sendAddDownload(
                                        context,
                                        ExoDownloadService::class.java,
                                        downloadRequest,
                                        false,
                                    )
                                }
                            }
                        },
                        contentColor = contentColor,
                        accentColor = dynamicAccentColor,
                        isDark = surfaceIsDark,
                    )

                    Spacer(modifier = Modifier.width(12.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))

                WavySliderWithLabels(
                    currentPositionProvider = positionProvider,
                    duration = duration,
                    isPlaying = isPlaying,
                    onSeekTo = { seekMs ->
                        onSeek(seekMs)
                        onSeekFinished()
                    },
                    dynamicAccentColor = dynamicAccentColor,
                    contentColor = contentColor,
                    contentSecondary = contentSecondary,
                    isDark = surfaceIsDark,
                    currentFormat = currentFormat,
                )

                Spacer(modifier = Modifier.height(16.dp))

                ButtonGroup(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    expandedRatio = 0.3f,
                    overflowIndicator = {},
                ) {
                    val scope = this

                    customItem(
                        buttonGroupContent = {
                            val interactionSource = remember { MutableInteractionSource() }
                            val isPressed by interactionSource.collectIsPressedAsState()
                            val cornerRadius by animateDpAsState(
                                targetValue = if (isPressed) 12.dp else 28.dp,
                                animationSpec =
                                    spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium,
                                    ),
                                label = "PrevCorner",
                            )
                            Button(
                                onClick = { playerConnection.seekToPrevious() },
                                modifier =
                                    with(scope) {
                                        Modifier
                                            .animateWidth(interactionSource)
                                            .weight(1f)
                                            .height(76.dp)
                                    },
                                interactionSource = interactionSource,
                                shape = RoundedCornerShape(cornerRadius),
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = contentColor.copy(alpha = if (surfaceIsDark) 0.08f else 0.06f),
                                        contentColor = contentColor,
                                    ),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                enabled = canSkipPrevious,
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.spatialflow_ic_skip_previous),
                                        contentDescription = "Previous Song",
                                        modifier = Modifier.size(36.dp),
                                    )
                                }
                            }
                        },
                        menuContent = {},
                    )
                    customItem(
                        buttonGroupContent = {
                            val interactionSource = remember { MutableInteractionSource() }
                            val isPressed by interactionSource.collectIsPressedAsState()
                            val cornerRadius by animateDpAsState(
                                targetValue = if (isPressed) 12.dp else 28.dp,
                                animationSpec =
                                    spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium,
                                    ),
                                label = "PlayCorner",
                            )
                            Button(
                                onClick = {
                                    if (isPlaying) {
                                        playerConnection.player.pause()
                                    } else {
                                        playerConnection.player.play()
                                    }
                                },
                                modifier =
                                    with(scope) {
                                        Modifier
                                            .animateWidth(interactionSource)
                                            .weight(1.2f)
                                            .height(76.dp)
                                    },
                                interactionSource = interactionSource,
                                shape = RoundedCornerShape(cornerRadius),
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = dynamicAccentColor,
                                        contentColor = if (surfaceIsDark) Color(0xFF1C1B1F) else Color.White,
                                    ),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isLoading && !isPlaying) {
                                        CircularWavyProgressIndicator(modifier = Modifier.size(42.dp))
                                    } else {
                                        Icon(
                                            painter =
                                                painterResource(
                                                    id = if (isPlaying) R.drawable.spatialflow_ic_pause else R.drawable.spatialflow_ic_play,
                                                ),
                                            contentDescription = if (isPlaying) "Pause" else "Play",
                                            modifier = Modifier.size(42.dp),
                                        )
                                    }
                                }
                            }
                        },
                        menuContent = {},
                    )
                    customItem(
                        buttonGroupContent = {
                            val interactionSource = remember { MutableInteractionSource() }
                            val isPressed by interactionSource.collectIsPressedAsState()
                            val cornerRadius by animateDpAsState(
                                targetValue = if (isPressed) 12.dp else 28.dp,
                                animationSpec =
                                    spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium,
                                    ),
                                label = "NextCorner",
                            )
                            Button(
                                onClick = { playerConnection.seekToNext() },
                                modifier =
                                    with(scope) {
                                        Modifier
                                            .animateWidth(interactionSource)
                                            .weight(1f)
                                            .height(76.dp)
                                    },
                                interactionSource = interactionSource,
                                shape = RoundedCornerShape(cornerRadius),
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = contentColor.copy(alpha = if (surfaceIsDark) 0.08f else 0.06f),
                                        contentColor = contentColor,
                                    ),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                enabled = canSkipNext,
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.spatialflow_ic_skip_next),
                                        contentDescription = "Next Song",
                                        modifier = Modifier.size(36.dp),
                                    )
                                }
                            }
                        },
                        menuContent = {},
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .pointerInput(Unit) {
                                detectVerticalDragGestures { _, dragAmount ->
                                    if (dragAmount < -10f && !queueExpanded && !lyricsModeEnabled) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        queueExpanded = true
                                    }
                                }
                            }.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                queueExpanded = true
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.spatialflow_ic_keyboard_arrow_down),
                        contentDescription = "Open Queue",
                        tint = contentColor.copy(alpha = 0.5f),
                        modifier =
                            Modifier
                                .size(32.dp)
                                .graphicsLayer { rotationZ = 180f },
                    )
                }
                }
            }

            if (lyricsOverlayVisible) {
                SpatialFlowLyricsOverlay(
                    currentSong = mediaMetadata,
                    syncedLyrics = syncedLyrics,
                    plainLyrics = plainLyrics,
                    lyricsProvider = currentLyricsEntity?.providerName,
                    currentPositionProvider = positionProvider,
                    contentReady = lyricsContentReady,
                    backgroundBrush = lyricsBackgroundBrush,
                    artUrl = artUrl,
                    revealProgressProvider = { lyricsRevealProgress },
                    revealCenterProvider = { lyricsButtonCenterInRoot },
                    contentColor = Color.White,
                    contentSecondary = Color.White.copy(alpha = 0.6f),
                    onSeekTo = onSeek,
                    onDismiss = { lyricsModeEnabled = false },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            val showFlyingArtwork =
                !canvasAvailable &&
                    !videoShowing &&
                    !artUrl.isNullOrBlank() &&
                    artworkPagerBoundsInRoot != null &&
                    (lyricsModeEnabled || lyricsArtworkProgress > 0.001f)
            if (showFlyingArtwork) {
                var flyingLayerRootPos by remember { mutableStateOf(Offset.Zero) }
                var flyingLayerWidthPx by remember { mutableStateOf(0f) }
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .onGloballyPositioned {
                                flyingLayerRootPos = it.positionInRoot()
                                flyingLayerWidthPx = it.size.width.toFloat()
                            },
                ) {
                    Box(
                        modifier =
                            Modifier
                                .graphicsLayer {
                                    val t = lyricsArtworkProgress.coerceIn(0f, 1f)
                                    val bounds = artworkPagerBoundsInRoot
                                    if (bounds == null) {
                                        alpha = 0f
                                        return@graphicsLayer
                                    }
                                    val fullSizePx = albumArtSize.toPx()

                                    val thumbSizePx = 56.dp.toPx()

                                    val targetRootX =
                                        flyingLayerWidthPx - 20.dp.toPx() - 48.dp.toPx() - 8.dp.toPx() - thumbSizePx
                                    val targetRootY = statusBarTopDp.toPx() + 18.dp.toPx()
                                    val scale = 1f + (thumbSizePx / fullSizePx - 1f) * t
                                    scaleX = scale
                                    scaleY = scale
                                    translationX =
                                        bounds.left + (targetRootX - bounds.left) * t - flyingLayerRootPos.x
                                    translationY =
                                        bounds.top + (targetRootY - bounds.top) * t - flyingLayerRootPos.y
                                    transformOrigin = TransformOrigin(1f, 0f)
                                    shape = RoundedCornerShape(lerp(16.dp, 10.dp, t))
                                    clip = true
                                    shadowElevation = lerp(0.dp, 6.dp, t).toPx()
                                }.size(albumArtSize),
                    ) {
                        AsyncImage(
                            model = artUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            SlidingQueueDrawer(
                isQueueExpanded = queueExpanded,
                onQueueExpandedChange = { queueExpanded = it },
                queueWindows = queueWindows,
                currentSongIndex = currentWindowIndex,
                isShuffleEnabled = shuffleModeEnabled,
                repeatMode = repeatMode,
                sleepTimerMode = sleepTimerMode,
                onReorderQueue = { from, to ->
                    if (from == to) return@SlidingQueueDrawer
                    if (!playerConnection.player.shuffleModeEnabled) {
                        playerConnection.player.moveMediaItem(from, to)
                    } else {
                        playerConnection.localPlayer.setShuffleOrder(
                            ShuffleOrder.DefaultShuffleOrder(
                                queueWindows
                                    .map { it.firstPeriodIndex }
                                    .toMutableList()
                                    .move(from, to)
                                    .toIntArray(),
                                System.currentTimeMillis(),
                            ),
                        )
                    }
                },
                onPlaySongAtIndex = { windowIndex ->
                    val window = queueWindows.getOrNull(windowIndex) ?: return@SlidingQueueDrawer
                    playerConnection.player.seekToDefaultPosition(window.firstPeriodIndex)
                    playerConnection.player.playWhenReady = true
                },
                onToggleShuffle = {
                    playerConnection.player.shuffleModeEnabled = !shuffleModeEnabled
                },
                onToggleLoopMode = {
                    playerConnection.player.repeatMode =
                        when (repeatMode) {
                            androidx.media3.common.Player.REPEAT_MODE_OFF ->
                                androidx.media3.common.Player.REPEAT_MODE_ALL

                            androidx.media3.common.Player.REPEAT_MODE_ALL ->
                                androidx.media3.common.Player.REPEAT_MODE_ONE

                            else -> androidx.media3.common.Player.REPEAT_MODE_OFF
                        }
                },
                onShowSleepTimerDialog = { showSleepTimerDialog = true },
                playerBackgroundColor = playerBackgroundColor,
                dynamicAccentColor = dynamicAccentColor,
                isDark = isDark,
            )

            if (showSleepTimerDialog) {
                SpatialFlowSleepTimerSheet(
                    onDismissRequest = { showSleepTimerDialog = false },
                    sleepTimerEndTime = sleepTimer.triggerTime,
                    sleepTimerMode = sleepTimerMode,
                    onStartTimer = { mins -> playerConnection.service.sleepTimer.start(mins) },
                    onCancelTimer = { playerConnection.service.sleepTimer.clear() },
                    onSetEndOfSong = { enable ->
                        if (enable) {
                            playerConnection.service.sleepTimer.start(-1)
                        } else {
                            playerConnection.service.sleepTimer.clear()
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SpatialFlowArtworkPager(
    mediaMetadata: MediaMetadata,
    queueWindows: List<androidx.media3.common.Timeline.Window>,
    currentWindowIndex: Int,
    userScrollEnabled: Boolean,
    artUrl: String?,
    isPlaying: Boolean,
    cornerRadius: androidx.compose.ui.unit.Dp,
    shadowElevation: androidx.compose.ui.unit.Dp,
    onPlaySongAtWindow: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState =
        rememberPagerState(initialPage = currentWindowIndex.coerceAtLeast(0)) {
            queueWindows.size.coerceAtLeast(1)
        }

    LaunchedEffect(currentWindowIndex) {
        if (currentWindowIndex >= 0 &&
            currentWindowIndex < pagerState.pageCount &&
            pagerState.currentPage != currentWindowIndex
        ) {
            pagerState.animateScrollToPage(currentWindowIndex)
        }
    }

    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress &&
            currentWindowIndex >= 0 &&
            pagerState.currentPage != currentWindowIndex
        ) {
            onPlaySongAtWindow(pagerState.currentPage)
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = userScrollEnabled,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val pageMetadata =
                if (page == currentWindowIndex) {
                    mediaMetadata
                } else {
                    queueWindows.getOrNull(page)?.mediaItem?.metadata ?: mediaMetadata
                }
            val pageArtUrl = if (page == currentWindowIndex) artUrl else pageMetadata.thumbnailUrl

            var isError by remember(pageArtUrl) { mutableStateOf(false) }

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .shadow(shadowElevation, RoundedCornerShape(cornerRadius))
                        .clip(RoundedCornerShape(cornerRadius)),
                contentAlignment = Alignment.Center,
            ) {
                if (!pageArtUrl.isNullOrBlank() && !isError) {
                    AsyncImage(
                        model = pageArtUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        onError = { isError = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(
                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                        colors =
                                            listOf(
                                                MaterialTheme.colorScheme.surfaceVariant,
                                                MaterialTheme.colorScheme.surfaceContainerHighest,
                                            ),
                                    ),
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.spatialflow_ic_music_note),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(96.dp),
                        )
                    }
                }
            }
        }
    }
}
