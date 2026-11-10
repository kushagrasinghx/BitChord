/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.player

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.lerp
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import moe.rukamori.archivetune.ui.player.PlayerFadeConfig
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.EnableHapticFeedbackKey
import moe.rukamori.archivetune.constants.MiniPlayerHeight
import moe.rukamori.archivetune.constants.NavigationBarHorizontalPadding
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.utils.getNextFallbackUrl
import moe.rukamori.archivetune.utils.rememberLowDataModeActive
import moe.rukamori.archivetune.utils.rememberPreference
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

private val MiniPlayerTransportButtonSpacing = 4.dp

@Immutable
data class MiniPlayerContentColors(
    val title: Color,
    val secondary: Color,
    val progress: Color,
    val progressTrack: Color,
    val artworkContainer: Color,
    val artworkBorder: Color,
    val primaryButtonContainer: Color,
    val primaryButtonIcon: Color,
    val secondaryButtonContainer: Color,
    val buttonIcon: Color,
    val disabledButtonIcon: Color,
)

@Composable
fun SwipeableMiniPlayerBox(
    modifier: Modifier = Modifier,
    contentMaxWidth: Dp? = null,
    swipeSensitivity: Float,
    swipeThumbnail: Boolean,
    playerConnection: PlayerConnection,
    layoutDirection: LayoutDirection,
    coroutineScope: CoroutineScope,
    pureBlack: Boolean = false,
    useLegacyBackground: Boolean = false,
    compactFraction: Float = 0f,
    content: @Composable (Float) -> Unit,
) {
    val offsetXAnimatable = remember { Animatable(0f) }
    var dragStartTime by remember { mutableLongStateOf(0L) }
    var totalDragDistance by remember { mutableFloatStateOf(0f) }

    val view = LocalView.current
    val (enableHapticFeedback) = rememberPreference(EnableHapticFeedbackKey, true)

    val animationSpec =
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow,
        )

    fun calculateAutoSwipeThreshold(swipeSensitivity: Float): Int =
        (600 / (1f + kotlin.math.exp(-(-11.44748 * swipeSensitivity + 9.04945)))).roundToInt()
    val autoSwipeThreshold = calculateAutoSwipeThreshold(swipeSensitivity)
    val containerMaxWidth =
        if (useLegacyBackground) {
            null
        } else {
            contentMaxWidth?.let {
                it + NavigationBarHorizontalPadding + NavigationBarHorizontalPadding
            }
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(MiniPlayerHeight)
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .let { baseModifier ->
                        if (containerMaxWidth == null) {
                            baseModifier.fillMaxWidth()
                        } else {
                            baseModifier
                                .widthIn(max = containerMaxWidth)
                                .fillMaxWidth()
                        }
                    }.height(MiniPlayerHeight)
                    .let { baseModifier ->
                        if (useLegacyBackground) {
                            baseModifier.background(
                                if (pureBlack) {
                                    Color.Black
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainer
                                },
                            )
                        } else {
                            baseModifier.padding(
                                horizontal = lerp(NavigationBarHorizontalPadding, 0.dp, compactFraction),
                            )
                        }
                    }.let { baseModifier ->
                        if (swipeThumbnail) {
                            baseModifier.pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onDragStart = {
                                        dragStartTime = System.currentTimeMillis()
                                        totalDragDistance = 0f
                                    },
                                    onDragCancel = {
                                        coroutineScope.launch {
                                            offsetXAnimatable.animateTo(
                                                targetValue = 0f,
                                                animationSpec = animationSpec,
                                            )
                                        }
                                    },
                                    onHorizontalDrag = { _, dragAmount ->
                                        val adjustedDragAmount =
                                            if (layoutDirection == LayoutDirection.Rtl) -dragAmount else dragAmount
                                        val canSkipPrevious = playerConnection.player.previousMediaItemIndex != -1
                                        val canSkipNext = playerConnection.player.nextMediaItemIndex != -1
                                        val allowLeft = adjustedDragAmount < 0 && canSkipNext
                                        val allowRight = adjustedDragAmount > 0 && canSkipPrevious
                                        if (allowLeft || allowRight) {
                                            totalDragDistance += kotlin.math.abs(adjustedDragAmount)
                                            coroutineScope.launch {
                                                offsetXAnimatable.snapTo(offsetXAnimatable.value + adjustedDragAmount)
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        val dragDuration = System.currentTimeMillis() - dragStartTime
                                        val velocity = if (dragDuration > 0) totalDragDistance / dragDuration else 0f
                                        val currentOffset = offsetXAnimatable.value

                                        val minDistanceThreshold = 50f
                                        val velocityThreshold = (swipeSensitivity * -8.25f) + 8.5f

                                        val shouldChangeSong =
                                            (
                                                kotlin.math.abs(currentOffset) > minDistanceThreshold &&
                                                    velocity > velocityThreshold
                                            ) || (kotlin.math.abs(currentOffset) > autoSwipeThreshold)

                                        if (shouldChangeSong) {
                                            val isRightSwipe = currentOffset > 0
                                            val canSkipPrevious = playerConnection.player.previousMediaItemIndex != -1
                                            val canSkipNext = playerConnection.player.nextMediaItemIndex != -1

                                            if (isRightSwipe && canSkipPrevious) {
                                                if (enableHapticFeedback) {
                                                    view.performHapticFeedback(
                                                        android.view.HapticFeedbackConstants.CONTEXT_CLICK,
                                                        android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                                                    )
                                                }
                                                playerConnection.player.seekToPreviousMediaItem()
                                            } else if (!isRightSwipe && canSkipNext) {
                                                if (enableHapticFeedback) {
                                                    view.performHapticFeedback(
                                                        android.view.HapticFeedbackConstants.CONTEXT_CLICK,
                                                        android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                                                    )
                                                }
                                                playerConnection.player.seekToNext()
                                            }
                                        }

                                        coroutineScope.launch {
                                            offsetXAnimatable.animateTo(
                                                targetValue = 0f,
                                                animationSpec = animationSpec,
                                            )
                                        }
                                    },
                                )
                            }
                        } else {
                            baseModifier
                        }
                    },

            contentAlignment = Alignment.Center,
        ) {
            content(offsetXAnimatable.value)

            if (offsetXAnimatable.value.absoluteValue > 50f) {
                Box(
                    modifier =
                        Modifier
                            .align(if (offsetXAnimatable.value > 0) Alignment.CenterStart else Alignment.CenterEnd)
                            .padding(horizontal = 16.dp),
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (offsetXAnimatable.value > 0) R.drawable.player_skip_previous else R.drawable.player_skip_next,
                            ),
                        contentDescription = null,
                        tint =
                            MaterialTheme.colorScheme.primary.copy(
                                alpha = (offsetXAnimatable.value.absoluteValue / autoSwipeThreshold).coerceIn(0f, 1f),
                            ),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun RowScope.MiniPlayerInfo(
    mediaMetadata: MediaMetadata,
    colors: MiniPlayerContentColors,
) {
    PlayerTextBackdrop(
        textColor = colors.title,
        modifier =
            Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
        ) {
            AnimatedContent(
                targetState = mediaMetadata.title,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "title",
            ) { title ->
                val titleLayout = remember { mutableStateOf<TextLayoutResult?>(null) }
                val titleViewport = remember { mutableStateOf(0) }
                val titleShouldFade =
                    titleViewport.value > 0 &&
                        (titleLayout.value?.size?.width ?: 0) > titleViewport.value
                Box(
                    modifier =
                        (if (titleShouldFade) Modifier.fillMaxWidth().viewportEdgeFade(PlayerFadeConfig.miniPlayer.fadeWidth) else Modifier.fillMaxWidth())
                            .clipToBounds()
                            .onSizeChanged { titleViewport.value = it.width },
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = colors.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { titleLayout.value = it },
                        modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
                    )
                }
            }

            AnimatedContent(
                targetState = mediaMetadata.artists,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "artist",
            ) { artists ->
                val artistText = artists.joinToString { it.name }
                val artistLayout = remember { mutableStateOf<TextLayoutResult?>(null) }
                val artistViewport = remember { mutableStateOf(0) }
                val artistShouldFade =
                    artistViewport.value > 0 &&
                        (artistLayout.value?.size?.width ?: 0) > artistViewport.value
                Box(
                    modifier =
                        (if (artistShouldFade) Modifier.fillMaxWidth().viewportEdgeFade(PlayerFadeConfig.miniPlayer.fadeWidth) else Modifier.fillMaxWidth())
                            .clipToBounds()
                            .onSizeChanged { artistViewport.value = it.width },
                ) {
                    Text(
                        text = artistText,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { artistLayout.value = it },
                        modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniPlayerArtwork(
    mediaMetadata: MediaMetadata?,
    colors: MiniPlayerContentColors,
    onArtworkSlotPositioned: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(52.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(48.dp)
                    .onGloballyPositioned { coordinates ->
                        if (onArtworkSlotPositioned != null) {
                            onArtworkSlotPositioned(
                                androidx.compose.ui.geometry.Rect(
                                    offset = coordinates.positionInRoot(),
                                    size =
                                        androidx.compose.ui.geometry.Size(
                                            width = coordinates.size.width.toFloat(),
                                            height = coordinates.size.height.toFloat(),
                                        ),
                                ),
                            )
                        }
                    }
                    .clip(MiniPlayerArtworkShape)
                    .background(colors.artworkContainer)
                    .border(
                        width = 1.dp,
                        color = colors.artworkBorder,
                        shape = MiniPlayerArtworkShape,
                    ),
        ) {
            val baseThumbnailUrl = mediaMetadata?.thumbnailUrl
            if (baseThumbnailUrl != null) {
                val thumbnailSwapState =
                    rememberThumbnailSwapState(
                        videoId = mediaMetadata.id,
                        ytmUrl = baseThumbnailUrl,
                        lowDataMode = rememberLowDataModeActive(),
                        isMusicVideo = mediaMetadata.isMusicVideo,
                    )

                var displayUrl by remember(thumbnailSwapState.displayUrl) {
                    mutableStateOf(thumbnailSwapState.displayUrl)
                }
                val artworkRequest =
                    remember(displayUrl) {
                        ImageRequest
                            .Builder(context)
                            .data(displayUrl)
                            .memoryCacheKey(displayUrl)
                            .diskCacheKey(displayUrl)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .networkCachePolicy(CachePolicy.ENABLED)
                            .build()
                    }
                AsyncImage(
                    model = artworkRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onState = { state ->
                        if (state is AsyncImagePainter.State.Error) {
                            getNextFallbackUrl(displayUrl)?.let { displayUrl = it }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.about_splash),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun MiniPlayerTransportButton(
    iconResId: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPrimary: Boolean = false,
    compact: Boolean = false,
    colors: MiniPlayerContentColors,
) {
    val view = LocalView.current
    val (enableHapticFeedback) = rememberPreference(EnableHapticFeedbackKey, true)

    val handleClick =
        remember(enableHapticFeedback, onClick, view) {
            {
                if (enableHapticFeedback) {
                    view.performHapticFeedback(
                        android.view.HapticFeedbackConstants.CONTEXT_CLICK,
                        android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                    )
                }
                onClick()
            }
        }

    val iconSize =
        when {
            compact -> if (isPrimary) 28.dp else 24.dp
            isPrimary -> 38.dp
            else -> 31.dp
        }
    val tint = if (isPrimary) colors.primaryButtonIcon else colors.buttonIcon

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(if (compact) 40.dp else 44.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = enabled,
                    onClick = handleClick,
                ),
    ) {
        Icon(
            painter = painterResource(iconResId),
            contentDescription = contentDescription,
            tint = if (enabled) tint else colors.disabledButtonIcon,
            modifier = Modifier.size(iconSize),
        )
    }
}

private val MiniPlayerArtworkShape = RoundedCornerShape(10.dp)

@Composable
private fun MiniPlayerTransportControls(
    isPlaying: Boolean,
    playbackState: Int,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    playerConnection: PlayerConnection,
    colors: MiniPlayerContentColors,
) {
    val onPrevious = remember(playerConnection) { { playerConnection.seekToPrevious() } }
    val onNext = remember(playerConnection) { { playerConnection.seekToNext() } }
    val onPlayPause =
        remember(playbackState, playerConnection) {
            {
                if (playbackState == Player.STATE_ENDED) {
                    playerConnection.player.seekTo(0, 0)
                    playerConnection.player.playWhenReady = true
                } else {
                    playerConnection.player.togglePlayPause()
                }
            }
        }

    Row(
        horizontalArrangement = Arrangement.spacedBy(MiniPlayerTransportButtonSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiniPlayerTransportButton(
            iconResId = R.drawable.apple_skip_previous,
            contentDescription = stringResource(R.string.widget_previous),
            onClick = onPrevious,
            enabled = canSkipPrevious,
            colors = colors,
        )

        MiniPlayerTransportButton(
            iconResId =
                when {
                    playbackState == Player.STATE_ENDED -> R.drawable.solar_replay_linear
                    isPlaying -> R.drawable.pause_applemusic
                    else -> R.drawable.play_applemusic
                },
            contentDescription =
                stringResource(
                    if (playbackState == Player.STATE_ENDED || !isPlaying) R.string.play else R.string.widget_pause,
                ),
            onClick = onPlayPause,
            isPrimary = true,
            colors = colors,
        )

        MiniPlayerTransportButton(
            iconResId = R.drawable.apple_skip_next,
            contentDescription = stringResource(R.string.next),
            onClick = onNext,
            enabled = canSkipNext,
            colors = colors,
        )
    }
}

@Composable
fun NewMiniPlayerContent(
    positionProvider: () -> Long,
    durationProvider: () -> Long,
    playerConnection: PlayerConnection,
    colors: MiniPlayerContentColors,
    compactFraction: Float = 0f,
    compactShowTransportControls: Boolean = false,
    onArtworkSlotPositioned: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
) {
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val playbackState by playerConnection.playbackState.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsStateWithLifecycle()
    val canSkipNext by playerConnection.canSkipNext.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        if (compactFraction < 0.95f) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)
                        .graphicsLayer {
                            alpha = 1f - compactFraction
                            val scale = 1f - 0.10f * compactFraction
                            scaleX = scale
                            scaleY = scale
                        },
            ) {
                MiniPlayerArtwork(
                    mediaMetadata = mediaMetadata,
                    colors = colors,
                    onArtworkSlotPositioned = onArtworkSlotPositioned,
                )

                mediaMetadata?.let {
                    MiniPlayerInfo(
                        mediaMetadata = it,
                        colors = colors,
                    )
                } ?: Spacer(Modifier.weight(1f))

                MiniPlayerTransportControls(
                    isPlaying = isPlaying,
                    playbackState = playbackState,
                    canSkipPrevious = canSkipPrevious,
                    canSkipNext = canSkipNext,
                    playerConnection = playerConnection,
                    colors = colors,
                )
            }
        }

        if (compactFraction > 0.05f) {
            CompactMiniPlayerContent(
                positionProvider = positionProvider,
                durationProvider = durationProvider,
                playerConnection = playerConnection,
                colors = colors,
                showTransportControls = compactShowTransportControls,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = compactFraction
                            val scale = 0.92f + 0.08f * compactFraction
                            scaleX = scale
                            scaleY = scale
                        },
            )
        }
    }
}

@Composable
fun CompactMiniPlayerContent(
    positionProvider: () -> Long,
    durationProvider: () -> Long,
    playerConnection: PlayerConnection,
    colors: MiniPlayerContentColors,
    modifier: Modifier = Modifier,
    showTransportControls: Boolean = false,
    onArtworkSlotPositioned: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
) {
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val playbackState by playerConnection.playbackState.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsStateWithLifecycle()
    val canSkipNext by playerConnection.canSkipNext.collectAsStateWithLifecycle()

    val onPlayPause =
        remember(playbackState, playerConnection) {
            {
                if (playbackState == Player.STATE_ENDED) {
                    playerConnection.player.seekTo(0, 0)
                    playerConnection.player.playWhenReady = true
                } else {
                    playerConnection.player.togglePlayPause()
                }
            }
        }
    val onPrevious = remember(playerConnection) { { playerConnection.seekToPrevious() } }
    val onNext = remember(playerConnection) { { playerConnection.seekToNext() } }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    ) {
        MiniPlayerArtwork(
            mediaMetadata = mediaMetadata,
            colors = colors,
            onArtworkSlotPositioned = onArtworkSlotPositioned,
        )

        mediaMetadata?.let {
            MiniPlayerInfo(
                mediaMetadata = it,
                colors = colors,
            )
        } ?: Spacer(Modifier.weight(1f))

        if (showTransportControls) {
            MiniPlayerTransportButton(
                iconResId = R.drawable.apple_skip_previous,
                contentDescription = stringResource(R.string.widget_previous),
                onClick = onPrevious,
                enabled = canSkipPrevious,
                compact = true,
                colors = colors,
            )
        }

        MiniPlayerTransportButton(
            iconResId =
                when {
                    playbackState == Player.STATE_ENDED -> R.drawable.solar_replay_linear
                    isPlaying -> R.drawable.pause_applemusic
                    else -> R.drawable.play_applemusic
                },
            contentDescription =
                stringResource(
                    if (playbackState == Player.STATE_ENDED || !isPlaying) R.string.play else R.string.widget_pause,
                ),
            onClick = onPlayPause,
            isPrimary = true,
            compact = true,
            colors = colors,
        )

        if (showTransportControls) {
            MiniPlayerTransportButton(
                iconResId = R.drawable.apple_skip_next,
                contentDescription = stringResource(R.string.next),
                onClick = onNext,
                enabled = canSkipNext,
                compact = true,
                colors = colors,
            )
        }
    }
}
