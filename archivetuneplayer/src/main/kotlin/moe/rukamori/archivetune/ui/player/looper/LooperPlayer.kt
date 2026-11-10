/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player.looper

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.LocalStableSystemBarsTopPadding
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.component.BottomSheetPageState
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.menu.PlayerMenu
import moe.rukamori.archivetune.ui.player.CanvasArtworkPlayer
import moe.rukamori.archivetune.ui.player.InlineVideoPlayer
import moe.rukamori.archivetune.ui.player.LocalVideoArtworkState
import moe.rukamori.archivetune.ui.player.LocalVideoPlaybackFailed
import moe.rukamori.archivetune.ui.player.LocalVideoPreferredHeight
import moe.rukamori.archivetune.ui.player.LocalVideoOnPreferredHeightChange
import moe.rukamori.archivetune.ui.player.LocalVideoAvailableHeights
import moe.rukamori.archivetune.ui.player.LocalVideoSelectedHeight
import moe.rukamori.archivetune.ui.utils.ShowMediaInfo
import moe.rukamori.archivetune.utils.isLocalMediaId

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LooperPlayerContent(
    mediaMetadata: MediaMetadata,
    isPlaying: Boolean,
    isLoading: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    playerConnection: PlayerConnection,
    navController: NavController,
    state: BottomSheetState,
    menuState: MenuState,
    bottomSheetPageState: BottomSheetPageState,
    currentFormat: FormatEntity?,
    canvasPrimaryUrl: String?,
    canvasFallbackUrl: String?,
    onSeek: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    onLyricsClick: () -> Unit,
    onQueueClick: () -> Unit,
    lyricsVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp

    val currentSong by playerConnection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val currentSongLiked = currentSong?.song?.liked == true
    val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsStateWithLifecycle()
    val repeatMode by playerConnection.repeatMode.collectAsStateWithLifecycle()

    val accentColor = MaterialTheme.colorScheme.primary

    var canvasPlayingForLyrics by remember { mutableStateOf(true) }
    var canvasSurfacesForLyrics by remember { mutableStateOf(true) }
    LaunchedEffect(lyricsVisible) {
        if (lyricsVisible) {
            canvasPlayingForLyrics = false
            canvasSurfacesForLyrics = true
            delay(650)
            canvasSurfacesForLyrics = false
        } else {
            canvasPlayingForLyrics = true
            canvasSurfacesForLyrics = true
        }
    }
    val videoState = LocalVideoArtworkState.current
    val videoPlaybackFailed = LocalVideoPlaybackFailed.current
    val videoShowing =
        videoState != null &&
            mediaMetadata.isMusicVideo &&
            !mediaMetadata.id.isLocalMediaId() &&
            !lyricsVisible &&
            !videoPlaybackFailed

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color(0xFF141414)),
    ) {
        MaterialTheme(typography = LooperTypography) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(top = LocalStableSystemBarsTopPadding.current)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    state.collapseSoft()
                                },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = "Collapse player",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    Box(
                        modifier =
                            Modifier
                                .border(
                                    width = 0.5.dp,
                                    color = Color.White.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(20.dp),
                                ).background(
                                    color = Color.White.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(20.dp),
                                ).padding(horizontal = 10.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = looperQualityText(currentFormat),
                            style = LooperTypography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                        )
                    }

                    Box(
                        modifier =
                            Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.more_vert),
                            contentDescription = "More options",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    val artSize = screenWidthDp - 40.dp
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
                                    .size(artSize)
                                    .clip(RoundedCornerShape(12.dp)),
                        )
                    } else {
                        LooperArtwork(
                            mediaMetadata = mediaMetadata,
                            isPlaying = isPlaying,
                            canvasPrimaryUrl = canvasPrimaryUrl,
                            canvasFallbackUrl = canvasFallbackUrl,
                            canvasPlaying = isPlaying && canvasPlayingForLyrics,
                            canvasVisible = canvasSurfacesForLyrics,
                            onLyricsClick = onLyricsClick,
                            onSkipNext = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                playerConnection.seekToNext()
                            },
                            onSkipPrevious = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                playerConnection.seekToPrevious()
                            },
                            onSeekRelative = { deltaMs ->
                                onSeek((position + deltaMs).coerceAtLeast(0L))
                                onSeekFinished()
                            },
                            modifier = Modifier.size(artSize),
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = mediaMetadata.title,
                            style = LooperTypography.titleLarge,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = mediaMetadata.artists.joinToString { it.name },
                            style = LooperTypography.titleMedium,
                            color = Color.White.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Box(
                        modifier =
                            Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(
                                    color =
                                        if (currentSongLiked) {
                                            accentColor.copy(alpha = 0.05f)
                                        } else {
                                            Color.Transparent
                                        },
                                ).clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    playerConnection.toggleLike()
                                },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    if (currentSongLiked) R.drawable.player_favorite else R.drawable.player_favorite_border,
                                ),
                            contentDescription = "Favourite",
                            tint =
                                if (currentSongLiked) accentColor else Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))

                LooperExpressiveSlider(
                    position = position,
                    duration = duration,
                    isPlaying = isPlaying,
                    scrubPosition = sliderPosition,
                    accentColor = accentColor,
                    onSeek = onSeek,
                    onSeekFinished = { onSeekFinished() },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        LooperTransportPill(
                            endCornerRadius = 40.dp,
                            innerCornerRadius = 12.dp,
                            enabled = canSkipPrevious,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                playerConnection.seekToPrevious()
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.player_fast_forward),
                                contentDescription = "Previous",
                                tint = Color.White,
                                modifier =
                                    Modifier
                                        .size(28.dp)
                                        .graphicsLayer { scaleX = -1f },
                            )
                        }
                    }

                    Box(
                        modifier =
                            Modifier
                                .size(width = 96.dp, height = 80.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isPlaying) Color.White.copy(alpha = 0.04f) else accentColor,
                                ).clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    playerConnection.player.togglePlayPause()
                                },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isLoading) {
                            CircularWavyProgressIndicator(
                                color = if (isPlaying) Color.White else Color.Black,
                                modifier = Modifier.size(32.dp),
                            )
                        } else {
                            Icon(
                                painter =
                                    painterResource(
                                        if (isPlaying) R.drawable.player_pause else R.drawable.player_play,
                                    ),
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = if (isPlaying) Color.White else Color.Black,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        LooperTransportPill(
                            endCornerRadius = 12.dp,
                            innerCornerRadius = 40.dp,
                            enabled = canSkipNext,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                playerConnection.seekToNext()
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.player_fast_forward),
                                contentDescription = "Next",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(32.dp))
                                .background(Color.White.copy(alpha = 0.04f))
                                .padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LooperUtilityPill(
                            iconRes = R.drawable.player_shuffle,
                            contentDescription = "Shuffle",
                            active = shuffleModeEnabled,
                            horizontalPadding = 20.dp,
                            iconSize = 16.dp,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                playerConnection.player.shuffleModeEnabled =
                                    !playerConnection.player.shuffleModeEnabled
                            },
                        )
                        LooperUtilityPill(
                            iconRes =
                                when (repeatMode) {
                                    androidx.media3.common.Player.REPEAT_MODE_ONE -> R.drawable.player_repeat_one
                                    else -> R.drawable.player_repeat
                                },
                            contentDescription = "Repeat",
                            active =
                                repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE ||
                                    repeatMode == androidx.media3.common.Player.REPEAT_MODE_ALL,
                            horizontalPadding = 18.dp,
                            iconSize = 20.dp,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                playerConnection.player.repeatMode =
                                    when (repeatMode) {
                                        androidx.media3.common.Player.REPEAT_MODE_OFF ->
                                            androidx.media3.common.Player.REPEAT_MODE_ALL
                                        androidx.media3.common.Player.REPEAT_MODE_ALL ->
                                            androidx.media3.common.Player.REPEAT_MODE_ONE
                                        else -> androidx.media3.common.Player.REPEAT_MODE_OFF
                                    }
                            },
                        )
                    }

                    Row(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(32.dp))
                                .background(Color.White.copy(alpha = 0.04f))
                                .padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LooperLabeledPill(
                            iconRes = R.drawable.queue_music,
                            label = "Next Up",
                            onClick = onQueueClick,
                        )
                        LooperLabeledPill(
                            iconRes = R.drawable.lyrics,
                            label = "Lyrics",
                            onClick = onLyricsClick,
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun LooperArtwork(
    mediaMetadata: MediaMetadata,
    isPlaying: Boolean,
    canvasPrimaryUrl: String?,
    canvasFallbackUrl: String?,
    canvasPlaying: Boolean,
    canvasVisible: Boolean,
    onLyricsClick: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekRelative: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    var dragOffset by remember { mutableStateOf(0f) }
    var artWidthPx by remember { mutableStateOf(0f) }

    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .onSizeChanged { artWidthPx = it.width.toFloat() }
                .graphicsLayer { translationX = dragOffset }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            val threshold = size.width * 0.25f
                            when {
                                dragOffset < -threshold -> onSkipNext()
                                dragOffset > threshold -> onSkipPrevious()
                            }
                            dragOffset = 0f
                        },
                        onDragCancel = { dragOffset = 0f },
                    ) { change, dragAmount ->
                        change.consume()
                        dragOffset += dragAmount.x
                    }
                }.pointerInput(artWidthPx) {
                    detectTapGestures(
                        onTap = { onLyricsClick() },
                        onDoubleTap = { offset ->
                            val isLeft = offset.x < artWidthPx / 2f
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSeekRelative(if (isLeft) -10_000L else 10_000L)
                        },
                    )
                },
    ) {
        var canvasShowing by remember(canvasPrimaryUrl, canvasFallbackUrl) { mutableStateOf(false) }
        if (canvasPrimaryUrl != null || canvasFallbackUrl != null) {
            CanvasArtworkPlayer(
                primaryUrl = canvasPrimaryUrl,
                fallbackUrl = canvasFallbackUrl,
                isPlaying = canvasPlaying,
                visible = canvasVisible,
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                onPlaybackAvailabilityChange = { canvasShowing = it },
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp)),
            )
        }

        val staticArtworkAlpha by animateFloatAsState(
            targetValue = if (canvasShowing) 0f else 1f,
            animationSpec = tween(300),
            label = "looperStaticArtworkAlpha",
        )
        if (staticArtworkAlpha > 0f) {
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(LocalContext.current)
                        .data(mediaMetadata.thumbnailUrl)
                        .crossfade(320)
                        .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = staticArtworkAlpha }
                        .let { base ->

                            if (isPlaying) base else base.padding(2.dp)
                        }.clip(RoundedCornerShape(12.dp))
                        .border(
                            width = 0.8.dp,
                            color = Color.White.copy(alpha = 0.04f),
                            shape = RoundedCornerShape(12.dp),
                        ),
            )
        }
    }
}

@Composable
private fun LooperUtilityPill(
    iconRes: Int,
    contentDescription: String,
    active: Boolean,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier =
            Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    if (active) Color.White.copy(alpha = 0.06f) else Color.Transparent,
                ).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }.padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = if (active) Color.White else Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun LooperLabeledPill(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier =
            Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            style = LooperTypography.labelLarge,
            color = Color.White,
        )
    }
}

@Composable
private fun LooperTransportPill(
    endCornerRadius: androidx.compose.ui.unit.Dp,
    innerCornerRadius: androidx.compose.ui.unit.Dp,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(80.dp)
                .let {
                    if (enabled) {
                        it.clip(
                            RoundedCornerShape(
                                topStart = innerCornerRadius,
                                bottomStart = innerCornerRadius,
                                topEnd = endCornerRadius,
                                bottomEnd = endCornerRadius,
                            ),
                        )
                    } else {
                        it
                    }
                }.background(
                    Color.White.copy(alpha = 0.04f),
                    shape =
                        RoundedCornerShape(
                            topStart = innerCornerRadius,
                            bottomStart = innerCornerRadius,
                            topEnd = endCornerRadius,
                            bottomEnd = endCornerRadius,
                        ),
                ).let {
                    if (enabled) {
                        it.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClick,
                        )
                    } else {
                        it
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private fun looperQualityText(format: FormatEntity?): String {
    if (format == null) return "High Quality • Audio"
    val container = format.mimeType.substringAfter("/").substringBefore(";").uppercase()
    val losslessCodecs = listOf("FLAC", "WAV", "ALAC", "APE")
    val parts = buildList {
        when {
            losslessCodecs.contains(container) -> add("Lossless")
            format.bitrate in 1..191_999 -> add("Standard Quality")
            else -> add("High Quality")
        }
        add(container)
        if (losslessCodecs.contains(container)) {
            format.sampleRate?.let { add("%.1f kHz".format(java.util.Locale.ROOT, it / 1000f)) }
        } else {
            if (format.bitrate > 0) add("${format.bitrate / 1000} kbps")
        }
    }
    return parts.joinToString(" • ")
}
