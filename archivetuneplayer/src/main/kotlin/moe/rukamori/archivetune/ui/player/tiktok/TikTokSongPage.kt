/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player.tiktok

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.LocalStableSystemBarsTopPadding
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.TikTokMainLyricsEnabledKey
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.component.BottomSheetPageState
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.component.LyricsEnhanced
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.player.CanvasArtworkPlayer
import moe.rukamori.archivetune.ui.player.InlineVideoControlsPill
import moe.rukamori.archivetune.ui.player.InlineVideoPlayer
import moe.rukamori.archivetune.ui.player.LocalVideoArtworkState
import moe.rukamori.archivetune.ui.player.LocalVideoAvailableHeights
import moe.rukamori.archivetune.ui.player.LocalVideoFullscreenState
import moe.rukamori.archivetune.ui.player.LocalVideoOnPreferredHeightChange
import moe.rukamori.archivetune.ui.player.LocalVideoPreferredHeight
import moe.rukamori.archivetune.ui.player.LocalVideoSelectedHeight
import moe.rukamori.archivetune.ui.player.VideoQualitySheet
import moe.rukamori.archivetune.ui.player.isLoadingState
import moe.rukamori.archivetune.ui.player.rememberOfflineArtworkImageRequest
import moe.rukamori.archivetune.ui.utils.getNextFallbackUrl
import moe.rukamori.archivetune.ui.utils.resize
import moe.rukamori.archivetune.utils.rememberPreference
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

internal val TIKTOK_INACTIVE_GRAY = Color(0xFFA9A9B2)

internal val TIKTOK_CAPTION_ROW_HEIGHT = 40.dp

internal val TIKTOK_CAPTION_TEXT_CLEARANCE = 56.dp

@Composable
internal fun TikTokSongPage(
    pageMetadata: MediaMetadata,
    isCurrentPage: Boolean,
    isPlaying: Boolean,

    suppressPauseOverlay: Boolean,
    playerConnection: PlayerConnection,
    queueTitle: String?,
    immersive: Boolean,
    lyricsOpen: Boolean,

    canvasPrimaryUrl: String?,
    canvasFallbackUrl: String?,
    sliderPositionProvider: () -> Long?,
    lyricsSyncOffset: Int,
    topChromeHeight: Dp,
    bottomChromeHeight: Dp,
    sheetState: BottomSheetState,
    onAddToPlaylist: () -> Unit,
    onToggleLyrics: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onQueueClick: () -> Unit,
    onOpenLyricsMenu: () -> Unit,
    onLyricsOverflowAnchorChange: (Rect) -> Unit,
    navController: NavController,
    menuState: MenuState,
    bottomSheetPageState: BottomSheetPageState,
) {
    val haptics = LocalHapticFeedback.current

    val likeAction =
        rememberTikTokLikeAction(
            pageMetadata = pageMetadata,
            isCurrentPage = isCurrentPage,
            playerConnection = playerConnection,
        )

    val heartBursts = remember { mutableStateListOf<TikTokHeartBurst>() }
    var nextHeartBurstId by remember { mutableStateOf(0L) }

    val stableTopInset = LocalStableSystemBarsTopPadding.current

    val artUrl =
        remember(pageMetadata.id, pageMetadata.thumbnailUrl) {
            pageMetadata.thumbnailUrl?.resize(
                width = TIKTOK_ART_PX,
                height = TIKTOK_ART_PX,
                maxresAllowed = true,
            )
        }

    var artworkModel by remember(artUrl) { mutableStateOf(artUrl) }
    val artworkRequest = rememberOfflineArtworkImageRequest(artworkModel)

    Box(modifier = Modifier.fillMaxSize().background(TIKTOK_EMPTY_BACKDROP)) {
        val videoState = LocalVideoArtworkState.current
        val videoShowing =
            isCurrentPage &&
                videoState != null &&
                !videoState.hasPlaybackFailed &&
                !lyricsOpen
        val videoFullscreenHolder = LocalVideoFullscreenState.current
        val videoRatio = videoState?.videoAspectRatio ?: TIKTOK_VIDEO_FALLBACK_RATIO

        val videoLoading = videoState != null && isLoadingState(videoState)

        var videoControlsVisible by remember(pageMetadata.id) { mutableStateOf(false) }
        var videoQualityMenuOpen by remember(pageMetadata.id) { mutableStateOf(false) }

        val meshColors = rememberTikTokArtworkColors(pageMetadata.thumbnailUrl)
        TikTokMeshBackdrop(
            palette = meshColors,
            trackKey = pageMetadata.id,
            reduceAnimation = LocalAnimationsDisabled.current,
        )

        val videoBackdropAlpha by animateFloatAsState(
            targetValue = if (videoShowing) 1f else 0f,
            animationSpec = tween(300),
            label = "tiktokVideoBackdropAlpha",
        )
        if (videoBackdropAlpha > 0f) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = videoBackdropAlpha }
                        .background(Color.Black),
            )
        }

        var canvasShowing by remember(canvasPrimaryUrl, canvasFallbackUrl) { mutableStateOf(false) }
        if (canvasPrimaryUrl != null || canvasFallbackUrl != null) {
            CanvasArtworkPlayer(
                primaryUrl = canvasPrimaryUrl,
                fallbackUrl = canvasFallbackUrl,
                isPlaying = isPlaying && !lyricsOpen,

                visible = !(isCurrentPage && lyricsOpen),
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                onPlaybackAvailabilityChange = { canvasShowing = it },
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(TIKTOK_CANVAS_CORNER)),
            )
        }

        if (videoShowing) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                InlineVideoPlayer(
                    state = videoState,
                    showControls = false,
                    modifier = Modifier.aspectRatio(videoRatio),
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
                )
            }
        }
        Box(modifier = Modifier.fillMaxSize().tiktokScrim())

        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.height(if (immersive) stableTopInset else topChromeHeight))

            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    val artSize = minOf(maxWidth, maxHeight)
                    val cornerRadius = if (artSize < maxWidth) 16.dp else 10.dp
                    val artworkScale by animateFloatAsState(
                        targetValue = if (immersive) 1.04f else 1f,
                        animationSpec = tween(250),
                        label = "tiktokArtworkScale",
                    )

                    val showInlineLyrics = isCurrentPage && lyricsOpen

                    AnimatedContent(
                        targetState = showInlineLyrics,
                        transitionSpec = {
                            fadeIn(tween(240)) togetherWith fadeOut(tween(240))
                        },
                        contentAlignment = Alignment.Center,
                        label = "tiktokCoverOrLyrics",
                    ) { showLyrics ->
                        if (showLyrics) {
                            TikTokInlineLyricsPane(
                                sliderPositionProvider = sliderPositionProvider,
                                lyricsSyncOffset = lyricsSyncOffset,
                            )
                        } else {
                            Box(
                                modifier =
                                    Modifier
                                        .size(artSize)
                                        .graphicsLayer {
                                            scaleX = artworkScale
                                            scaleY = artworkScale
                                        }

                                        .graphicsLayer {
                                            compositingStrategy =
                                                CompositingStrategy.Offscreen
                                        }
                                        .drawWithContent {
                                            drawContent()
                                            drawRect(
                                                brush = TIKTOK_ART_EDGE_FADE,
                                                blendMode = BlendMode.DstIn,
                                            )
                                        }.let { m ->

                                            if (isCurrentPage) {
                                                m.pointerInput(pageMetadata.id) {
                                                    detectTapGestures(
                                                        onTap = {
                                                            haptics.performHapticFeedback(
                                                                HapticFeedbackType.TextHandleMove,
                                                            )
                                                            if (videoShowing) {
                                                                videoControlsVisible = !videoControlsVisible
                                                            } else {
                                                                onTogglePlayPause()
                                                            }
                                                        },
                                                        onDoubleTap = { tap ->
                                                            haptics.performHapticFeedback(
                                                                HapticFeedbackType.LongPress,
                                                            )
                                                            likeAction(true)
                                                            heartBursts +=
                                                                TikTokHeartBurst(
                                                                    id = nextHeartBurstId++,
                                                                    x = tap.x.toDp(),
                                                                    y = tap.y.toDp(),
                                                                )
                                                        },
                                                    )
                                                }
                                            } else {
                                                m
                                            }
                                        },
                            ) {
                                val artworkFallbackAlpha by animateFloatAsState(
                                    targetValue = if (canvasShowing || videoShowing) 0f else 1f,
                                    animationSpec = tween(300),
                                    label = "tiktokArtworkFallbackAlpha",
                                )
                                AsyncImage(
                                    model = artworkRequest,
                                    contentDescription = pageMetadata.title,
                                    contentScale = ContentScale.Crop,
                                    onState = { state ->
                                        if (state is coil3.compose.AsyncImagePainter.State.Error) {
                                            getNextFallbackUrl(artworkModel)?.let { artworkModel = it }
                                        }
                                    },
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .graphicsLayer { alpha = artworkFallbackAlpha }
                                            .shadow(
                                                elevation = 18.dp,
                                                shape = RoundedCornerShape(cornerRadius),
                                                clip = true,
                                            ),
                                )

                                TikTokPausedOverlay(
                                    visible =
                                        isCurrentPage &&
                                            !videoShowing &&
                                            !isPlaying &&
                                            !suppressPauseOverlay &&
                                            !lyricsOpen,
                                )

                                heartBursts.forEach { burst ->
                                    key(burst.id) {
                                        TikTokHeartBurstView(
                                            burst = burst,
                                            onFinished = {
                                                heartBursts.removeAll { it.id == burst.id }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (!immersive && !showInlineLyrics) {
                        Box(
                            modifier =
                                Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight()
                                    .width(TIKTOK_RAIL_WASH_WIDTH)
                                    .graphicsLayer {
                                        compositingStrategy =
                                            CompositingStrategy.Offscreen
                                    }
                                    .drawWithContent {
                                        drawRect(brush = TIKTOK_RAIL_WASH)
                                        drawRect(
                                            brush = TIKTOK_RAIL_WASH_VERTICAL_FADE,
                                            blendMode = BlendMode.DstIn,
                                        )
                                    },
                        )
                    }
                }
            }

            val mainLyricsEnabled by rememberPreference(TikTokMainLyricsEnabledKey, false)
            if (!immersive && mainLyricsEnabled && !lyricsOpen) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(TikTokMainLyricsHeight)
                            .clipToBounds(),
                ) {
                    if (isCurrentPage) {
                        TikTokMainLyrics(
                            sliderPositionProvider = sliderPositionProvider,
                            lyricsSyncOffset = lyricsSyncOffset,
                            modifier =
                                Modifier
                                    .fillMaxSize()

                                    .padding(start = 16.dp, end = TIKTOK_CAPTION_TEXT_CLEARANCE + 16.dp)
                                    .padding(bottom = 4.dp),
                        )
                    }
                }
            }

            if (!immersive) {
                TikTokSongInfo(
                    pageMetadata = pageMetadata,
                    queueTitle = queueTitle,
                    onQueueClick = onQueueClick,

                    lyricsControlsVisible = isCurrentPage && lyricsOpen,
                    onCloseLyrics = onToggleLyrics,
                    onOpenLyricsMenu = onOpenLyricsMenu,
                    onLyricsOverflowAnchorChange = onLyricsOverflowAnchorChange,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp),
                )
            }

            Spacer(Modifier.height(if (immersive) 0.dp else bottomChromeHeight))
        }

        if (videoShowing && !videoFullscreenHolder.isFullscreen) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.aspectRatio(videoRatio)) {
                    TikTokPausedOverlay(
                        visible =
                            isCurrentPage &&
                                !isPlaying &&
                                !suppressPauseOverlay &&
                                !videoLoading &&
                                !(videoShowing && videoControlsVisible),
                    )
                }
            }
        }

        if (videoShowing && !videoFullscreenHolder.isFullscreen) {
            LaunchedEffect(videoControlsVisible, isPlaying, videoQualityMenuOpen) {
                if (videoControlsVisible && isPlaying && !videoQualityMenuOpen) {
                    kotlinx.coroutines.delay(TIKTOK_VIDEO_CONTROLS_AUTO_HIDE_MS)
                    videoControlsVisible = false
                }
            }
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.aspectRatio(videoRatio)) {
                    AnimatedVisibility(
                        visible = videoControlsVisible,
                        enter =
                            fadeIn(tween(220)) +
                                scaleIn(
                                    initialScale = 0.92f,
                                    animationSpec = tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                ),
                        exit =
                            fadeOut(tween(180)) +
                                scaleOut(targetScale = 0.92f, animationSpec = tween(180)),
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AnimatedVisibility(
                                visible = !videoLoading,
                                enter = fadeIn(tween(200)),
                                exit = fadeOut(tween(150)),
                            ) {
                                IconButton(
                                    onClick = onTogglePlayPause,
                                    modifier =
                                        Modifier
                                            .align(Alignment.Center)
                                            .size(64.dp)
                                            .background(
                                                Color.Black.copy(alpha = 0.45f),
                                                CircleShape,
                                            ),
                                ) {
                                    Icon(
                                        painter =
                                            painterResource(
                                                if (isPlaying) R.drawable.solar_pause_linear else R.drawable.solar_play_linear,
                                            ),
                                        contentDescription = stringResource(R.string.video_fs_play_pause),
                                        tint = Color.White,
                                        modifier = Modifier.size(44.dp),
                                    )
                                }
                            }
                            InlineVideoControlsPill(
                                modifier =
                                    Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(
                                            end = TIKTOK_VIDEO_CONTROLS_END_CLEARANCE,
                                            bottom = 8.dp,
                                        ),
                                qualityMenuOpen = videoQualityMenuOpen,
                                onQualityMenuOpenChange = { videoQualityMenuOpen = it },
                            )
                        }
                    }
                }
            }

            if (videoQualityMenuOpen) {
                VideoQualitySheet(
                    preferredHeight = LocalVideoPreferredHeight.current,
                    availableHeights = LocalVideoAvailableHeights.current,
                    selectedHeight = LocalVideoSelectedHeight.current,
                    onPreferredHeightChange = LocalVideoOnPreferredHeightChange.current,
                    onDismissRequest = { videoQualityMenuOpen = false },
                )
            }
        }

        if (!immersive) {
            TikTokRail(
                pageMetadata = pageMetadata,
                isCurrentPage = isCurrentPage,
                playerConnection = playerConnection,
                sheetState = sheetState,
                lyricsActive = isCurrentPage && lyricsOpen,
                onToggleLyrics = onToggleLyrics,
                onAddToPlaylist = onAddToPlaylist,
                onOpenLyricsMenu = onOpenLyricsMenu,
                navController = navController,
                menuState = menuState,
                bottomSheetPageState = bottomSheetPageState,
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = bottomChromeHeight + 8.dp),
            )
        }
    }
}

@Composable
private fun TikTokPausedOverlay(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter =
            fadeIn(tween(150)) +
                scaleIn(
                    initialScale = 0.55f,
                    animationSpec =
                        spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                ),
        exit =
            fadeOut(tween(120)) +
                scaleOut(targetScale = 0.55f, animationSpec = tween(120)),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.solar_play_linear),
                contentDescription = stringResource(R.string.play),
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(64.dp),
            )
        }
    }
}

@Composable
private fun TikTokInlineLyricsPane(
    sliderPositionProvider: () -> Long?,
    lyricsSyncOffset: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier.fillMaxSize(),
    ) {
        LyricsEnhanced(
            sliderPositionProvider = sliderPositionProvider,
            lyricsSyncOffset = lyricsSyncOffset,
            textColorOverride = Color.White,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = 4.dp, bottom = 4.dp)
                    .padding(horizontal = 6.dp),
        )
    }
}

@Composable
private fun TikTokSongInfo(
    pageMetadata: MediaMetadata,
    queueTitle: String?,
    onQueueClick: () -> Unit,
    lyricsControlsVisible: Boolean,
    onCloseLyrics: () -> Unit,
    onOpenLyricsMenu: () -> Unit,
    onLyricsOverflowAnchorChange: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        val showChipRow = lyricsControlsVisible || !queueTitle.isNullOrBlank()
        if (showChipRow) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(TIKTOK_CAPTION_ROW_HEIGHT),
            ) {
                if (!queueTitle.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.14f))
                                .tiktokNoRippleClickable(onClick = onQueueClick)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.solar_music_note_2_linear),
                            contentDescription = null,
                            tint = TIKTOK_INACTIVE_GRAY,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = queueTitle,
                            color = TIKTOK_INACTIVE_GRAY,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier =
                                Modifier
                                    .widthIn(max = 160.dp)
                                    .basicMarquee(iterations = Int.MAX_VALUE),
                        )
                    }
                }

                AnimatedVisibility(
                    visible = lyricsControlsVisible,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(200)),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!queueTitle.isNullOrBlank()) {
                            Spacer(Modifier.width(8.dp))
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .size(40.dp)
                                    .tiktokNoRippleClickable(onClick = onCloseLyrics),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.player_close),
                                contentDescription = stringResource(R.string.close_dialog),
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .size(40.dp)
                                    .tiktokNoRippleClickable(onClick = onOpenLyricsMenu)

                                    .onGloballyPositioned {
                                        onLyricsOverflowAnchorChange(it.boundsInRoot())
                                    },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.more_horiz),
                                contentDescription = stringResource(R.string.more),
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Text(
            text = pageMetadata.title,
            color = Color.White,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = TIKTOK_CAPTION_TEXT_CLEARANCE),
        )
        Spacer(Modifier.height(3.dp))
        val artistName = pageMetadata.artists.joinToString(", ") { it.name }
        val secondary =
            if (pageMetadata.album?.title.isNullOrBlank() || artistName.isBlank()) {
                artistName.ifBlank { pageMetadata.album?.title.orEmpty() }
            } else {
                "$artistName • ${pageMetadata.album?.title}"
            }
        if (secondary.isNotBlank()) {
            Text(
                text = secondary,
                color = TIKTOK_INACTIVE_GRAY,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = TIKTOK_CAPTION_TEXT_CLEARANCE),
            )
        }
    }
}

private data class TikTokHeartBurst(
    val id: Long,
    val x: Dp,
    val y: Dp,
)

@Composable
private fun TikTokHeartBurstView(
    burst: TikTokHeartBurst,
    onFinished: () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(burst.id) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(TIKTOK_HEART_BURST_MS, easing = LinearOutSlowInEasing),
        )
        onFinished()
    }
    val p = progress.value

    val scale = when {
        p < 0.22f -> 0.2f + (p / 0.22f) * 1.15f
        p < 0.38f -> 1.35f - ((p - 0.22f) / 0.16f) * 0.35f
        else -> 1f
    }
    val alpha = if (p < 0.55f) 1f else 1f - ((p - 0.55f) / 0.45f)
    val rise = 160.dp * (p * p)
    val rotation = ((burst.id % 5) - 2) * 6f
    Icon(
        painter = painterResource(R.drawable.solar_heart_bold),
        contentDescription = null,
        tint = TIKTOK_RED,
        modifier =
            Modifier
                .offset(
                    x = burst.x - TIKTOK_HEART_BURST_SIZE / 2f,
                    y = burst.y - TIKTOK_HEART_BURST_SIZE / 2f - rise,
                ).size(TIKTOK_HEART_BURST_SIZE)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha.coerceIn(0f, 1f)
                    rotationZ = rotation
                },
    )
}

private val TIKTOK_HEART_BURST_SIZE = 88.dp

private const val TIKTOK_HEART_BURST_MS = 650

private val TIKTOK_RAIL_WASH_WIDTH = 120.dp

private val TIKTOK_RAIL_WASH =
    Brush.horizontalGradient(
        listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)),
    )

private val TIKTOK_RAIL_WASH_VERTICAL_FADE =
    Brush.verticalGradient(
        0.00f to Color.Transparent,
        0.28f to Color.Black,
        0.90f to Color.Black,
        1.00f to Color.Transparent,
    )

private val TIKTOK_ART_EDGE_FADE =
    Brush.verticalGradient(
        0.00f to Color.Transparent,
        0.14f to Color.Black,
        0.86f to Color.Black,
        1.00f to Color.Transparent,
    )

internal fun Modifier.tiktokScrim(): Modifier = drawBehind { drawRect(TIKTOK_SCRIM) }

internal const val TIKTOK_ART_PX = 1080

internal val TIKTOK_CANVAS_CORNER = 20.dp

internal val TIKTOK_VIDEO_FALLBACK_RATIO = 16f / 9f

internal val TIKTOK_VIDEO_CONTROLS_END_CLEARANCE = 66.dp

internal const val TIKTOK_VIDEO_CONTROLS_AUTO_HIDE_MS = 3500L

internal val TIKTOK_EMPTY_BACKDROP = Color(0xFF0B0B0F)

private val TIKTOK_SCRIM =
    Brush.verticalGradient(
        colorStops =
            arrayOf(
                0.00f to Color.Black.copy(alpha = 0.32f),
                0.22f to Color.Black.copy(alpha = 0.06f),
                0.55f to Color.Black.copy(alpha = 0.08f),
                1.00f to Color.Black.copy(alpha = 0.52f),
            ),
    )
