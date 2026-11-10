/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(androidx.media3.common.util.UnstableApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Job
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.SliderStyle
import moe.rukamori.archivetune.constants.SliderStyleKey
import moe.rukamori.archivetune.constants.VideoAmbientModeKey
import moe.rukamori.archivetune.constants.VideoAspectRatio
import moe.rukamori.archivetune.constants.VideoAspectRatioKey
import moe.rukamori.archivetune.constants.VideoPlaybackSpeedKey
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.ui.component.KeepStatusBarHiddenInDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private const val INLINE_VIDEO_CONTROLS_AUTO_HIDE_MS = 3500L

@Stable
class VideoFullscreenStateHolder {
    var isFullscreen: Boolean by mutableStateOf(false)
        internal set
}

val LocalVideoFullscreenState = compositionLocalOf {
    VideoFullscreenStateHolder()
}

val LocalVideoArtworkState = compositionLocalOf<VideoArtworkState?> { null }

val LocalVideoPlaybackFailed = compositionLocalOf { false }

val LocalVideoPreferredHeight = compositionLocalOf<Int?> { null }

val LocalVideoOnPreferredHeightChange = compositionLocalOf<(Int?) -> Unit> { {} }

val LocalVideoAvailableHeights = compositionLocalOf<List<Int>> { emptyList() }

val LocalVideoSelectedHeight = compositionLocalOf<Int?> { null }

val LocalIsInPipMode = compositionLocalOf { false }

val LocalRootOverlayActive = compositionLocalOf { false }

val LocalPlayerLyricsFullScreen = compositionLocalOf { false }

val LocalPlayerSheetOverlayActive = compositionLocalOf { false }

val LocalPlayerSheetOverlayFraction =
    compositionLocalOf<androidx.compose.runtime.State<Float>> {
        androidx.compose.runtime.mutableStateOf(0f)
    }

@Composable
fun ProvideVideoFullscreenState(content: @Composable () -> Unit) {
    val holder = remember { VideoFullscreenStateHolder() }
    CompositionLocalProvider(LocalVideoFullscreenState provides holder) {
        content()
    }
}

@Composable
fun InlineVideoControlsPill(
    preferredHeight: Int? = LocalVideoPreferredHeight.current,
    onPreferredHeightChange: (Int?) -> Unit = LocalVideoOnPreferredHeightChange.current,
    availableHeights: List<Int> = LocalVideoAvailableHeights.current,
    selectedHeight: Int? = LocalVideoSelectedHeight.current,
    modifier: Modifier = Modifier,
    qualityMenuOpen: Boolean? = null,
    onQualityMenuOpenChange: ((Boolean) -> Unit)? = null,
) {
    val fullscreenHolder = LocalVideoFullscreenState.current
    var internalQualityMenuOpen by remember { mutableStateOf(false) }
    val setMenuOpen: (Boolean) -> Unit = onQualityMenuOpenChange ?: { internalQualityMenuOpen = it }

    Row(
        modifier =
            modifier
                .background(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(28.dp),
                ).padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (availableHeights.size > 1) {
            IconButton(
                onClick = { setMenuOpen(true) },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.solar_settings_linear),
                    contentDescription = stringResource(R.string.video_quality),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        IconButton(
            onClick = { fullscreenHolder.isFullscreen = true },
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.solar_fullscreen_linear),
                contentDescription = stringResource(R.string.video_fullscreen),
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }

    if (qualityMenuOpen == null && internalQualityMenuOpen) {
        VideoQualitySheet(
            preferredHeight = preferredHeight,
            availableHeights = availableHeights,
            selectedHeight = selectedHeight,
            onPreferredHeightChange = onPreferredHeightChange,
            onDismissRequest = { internalQualityMenuOpen = false },
        )
    }
}

@Composable
fun InlineVideoPlayer(
    state: VideoArtworkState? = LocalVideoArtworkState.current,
    preferredHeight: Int? = LocalVideoPreferredHeight.current,
    onPreferredHeightChange: (Int?) -> Unit = LocalVideoOnPreferredHeightChange.current,
    availableHeights: List<Int> = LocalVideoAvailableHeights.current,
    selectedHeight: Int? = LocalVideoSelectedHeight.current,
    modifier: Modifier = Modifier,
    onPlaybackFailed: () -> Unit = {},
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    showControls: Boolean = true,
    controlsOnTap: Boolean = false,
) {
    if (state == null) {
        onPlaybackFailed()
        return
    }

    val fullscreenHolder = LocalVideoFullscreenState.current
    val isFullscreen = fullscreenHolder.isFullscreen

    val playerConnection = LocalPlayerConnection.current
    val fallbackMetadataFlow = remember { kotlinx.coroutines.flow.MutableStateFlow<MediaMetadata?>(null) }
    val mediaMetadata by (playerConnection?.mediaMetadata ?: fallbackMetadataFlow).collectAsStateWithLifecycle()
    val thumbnailUrl = mediaMetadata?.thumbnailUrl

    if (!isFullscreen) {
        var controlsVisible by remember { mutableStateOf(false) }
        var qualityMenuOpen by remember { mutableStateOf(false) }
        val fallbackPlayingFlow = remember { kotlinx.coroutines.flow.MutableStateFlow(false) }
        val isPlaying by (playerConnection?.isPlaying ?: fallbackPlayingFlow)
            .collectAsStateWithLifecycle()

        Box(
            modifier =
                modifier.then(
                    if (controlsOnTap) {
                        Modifier.pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { controlsVisible = !controlsVisible },
                            )
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            if (controlsOnTap) {
                LaunchedEffect(controlsVisible, isPlaying, qualityMenuOpen) {
                    if (controlsVisible && isPlaying && !qualityMenuOpen) {
                        kotlinx.coroutines.delay(INLINE_VIDEO_CONTROLS_AUTO_HIDE_MS)
                        controlsVisible = false
                    }
                }
            }

            VideoArtworkSurface(
                state = state,
                resizeMode = resizeMode,
                ambientMode = false,
                thumbnailUrl = thumbnailUrl,
                modifier = Modifier.fillMaxSize(),
            )

            if (isLoadingState(state)) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }

            AnimatedVisibility(
                visible = controlsOnTap && controlsVisible && !isLoadingState(state) && playerConnection != null,
                enter =
                    fadeIn(tween(220)) +
                        scaleIn(
                            initialScale = 0.7f,
                            animationSpec = tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                        ),
                exit =
                    fadeOut(tween(160)) +
                        scaleOut(targetScale = 0.7f, animationSpec = tween(160)),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(
                        onClick = { playerConnection?.player?.togglePlayPause() },
                        modifier =
                            Modifier
                                .size(64.dp)
                                .background(Color.Black.copy(alpha = 0.45f), CircleShape),
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
            }

            if (controlsOnTap) {
                AnimatedVisibility(
                    visible = showControls && controlsVisible,
                    enter =
                        fadeIn(tween(220)) +
                            slideInVertically(
                                initialOffsetY = { -it / 2 },
                                animationSpec = tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                            ),
                    exit = fadeOut(tween(180)) + slideOutVertically(targetOffsetY = { -it / 2 }, animationSpec = tween(180)),
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    InlineVideoControlsPill(
                        preferredHeight = preferredHeight,
                        onPreferredHeightChange = onPreferredHeightChange,
                        availableHeights = availableHeights,
                        selectedHeight = selectedHeight,
                        modifier = Modifier.padding(8.dp),
                        qualityMenuOpen = qualityMenuOpen,
                        onQualityMenuOpenChange = { qualityMenuOpen = it },
                    )
                }
            } else if (showControls) {
                InlineVideoControlsPill(
                    preferredHeight = preferredHeight,
                    onPreferredHeightChange = onPreferredHeightChange,
                    availableHeights = availableHeights,
                    selectedHeight = selectedHeight,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                )
            }

            if (qualityMenuOpen) {
                VideoQualitySheet(
                    preferredHeight = preferredHeight,
                    availableHeights = availableHeights,
                    selectedHeight = selectedHeight,
                    onPreferredHeightChange = onPreferredHeightChange,
                    onDismissRequest = { qualityMenuOpen = false },
                )
            }
        }
    }
}

@Composable
fun FullscreenVideoOverlay(
    state: VideoArtworkState,
    preferredHeight: Int?,
    onPreferredHeightChange: (Int?) -> Unit,
    availableHeights: List<Int>,
    selectedHeight: Int? = null,
    onDismiss: () -> Unit,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    followSensorOrientation: Boolean = false,
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current

    val isInPipMode = LocalIsInPipMode.current
    var qualityMenuOpen by remember { mutableStateOf(false) }
    var aspectRatioMenuOpen by remember { mutableStateOf(false) }

    var controlsVisible by remember { mutableStateOf(false) }
    var isUserSeeking by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableStateOf<Long?>(null) }
    var showOverflowSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var gestureFeedback by remember { mutableStateOf<GestureFeedback?>(null) }

    var brightnessDragActive by remember { mutableStateOf(false) }

    var volumeDragActive by remember { mutableStateOf(false) }

    var brightnessGestureJob by remember { mutableStateOf<Job?>(null) }
    var volumeGestureJob by remember { mutableStateOf<Job?>(null) }

    val (sliderStyle, onSliderStyleChange) = rememberEnumPreference(SliderStyleKey, defaultValue = SliderStyle.Standard)
    val (playbackSpeed, onPlaybackSpeedChange) = rememberPreference(VideoPlaybackSpeedKey, defaultValue = 1.0f)
    val (ambientMode, onAmbientModeChange) = rememberPreference(VideoAmbientModeKey, defaultValue = false)
    val (aspectRatio, onAspectRatioChange) = rememberEnumPreference(VideoAspectRatioKey, defaultValue = VideoAspectRatio.FIT)

    val effectiveResizeMode = aspectRatio.toExoResizeMode()

    BackHandler { onDismiss() }

    LaunchedEffect(state.hasPlaybackFailed) {
        if (state.hasPlaybackFailed) {
            onDismiss()
        }
    }

    LaunchedEffect(playbackSpeed) {
        if (playerConnection == null) return@LaunchedEffect
        val safeSpeed = playbackSpeed.coerceIn(0.25f, 2f)
        val current = playerConnection.player.playbackParameters.speed
        if (kotlin.math.abs(current - safeSpeed) > 0.001f) {
            playerConnection.player.playbackParameters =
                PlaybackParameters(
                    safeSpeed,
                    playerConnection.player.playbackParameters.pitch,
                )
        }
    }

    val latestPlaybackSpeed by rememberUpdatedState(playbackSpeed)
    val latestOnPlaybackSpeedChange by rememberUpdatedState(onPlaybackSpeedChange)
    LaunchedEffect(Unit) {
        if (playerConnection == null) return@LaunchedEffect
        while (true) {
            val currentSpeed = playerConnection.player.playbackParameters.speed
            if (kotlin.math.abs(currentSpeed - latestPlaybackSpeed) > 0.01f) {
                latestOnPlaybackSpeedChange(currentSpeed)
            }
            delay(2_000)
        }
    }

    DisposableEffect(followSensorOrientation) {
        val activity = context.findActivity()
        val originalOrientation = activity?.requestedOrientation
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val originalBehavior = controller?.systemBarsBehavior

        val originalBrightness = window?.attributes?.screenBrightness

        activity?.requestedOrientation =
            if (followSensorOrientation) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        if (controller != null) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            activity?.requestedOrientation =
                originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (controller != null) {
                controller.show(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    originalBehavior ?: WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            }

            if (window != null && originalBrightness != null) {
                val params = window.attributes
                params.screenBrightness = originalBrightness
                window.attributes = params
            }
        }
    }

    LaunchedEffect(controlsVisible, isUserSeeking, qualityMenuOpen, aspectRatioMenuOpen, showOverflowSheet, isInPipMode) {
        if (isInPipMode) {
            controlsVisible = false
            return@LaunchedEffect
        }
        if (controlsVisible && !isUserSeeking && !qualityMenuOpen && !aspectRatioMenuOpen && !showOverflowSheet) {
            kotlinx.coroutines.delay(FullscreenControlsAutoHideMs)
            controlsVisible = false
        }
    }

    val fallbackMetadataFlow = remember { kotlinx.coroutines.flow.MutableStateFlow<MediaMetadata?>(null) }
    val headerMetadata by (playerConnection?.mediaMetadata ?: fallbackMetadataFlow).collectAsStateWithLifecycle()
    val thumbnailUrl = headerMetadata?.thumbnailUrl

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)

                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->

                            if (isInPipMode) return@detectTapGestures
                            if (showOverflowSheet) {
                                scope.launch { sheetState.hide() }.invokeOnCompletion {
                                    if (!sheetState.isVisible) showOverflowSheet = false
                                }
                            } else {
                                controlsVisible = !controlsVisible

                                gestureFeedback = null
                            }
                        },
                        onDoubleTap = { offset ->

                            if (isInPipMode) return@detectTapGestures

                            if (!showOverflowSheet && playerConnection != null) {
                                val width = size.width.toFloat()
                                val isLeftHalf = offset.x < width / 2f
                                val cur = playerConnection.player.currentPosition
                                val dur = playerConnection.player.duration
                                val target =
                                    if (isLeftHalf) {
                                        (cur - 10_000L).coerceAtLeast(0L)
                                    } else {
                                        (cur + 10_000L).coerceAtMost(
                                            if (dur > 0 && dur != C.TIME_UNSET) dur else Long.MAX_VALUE,
                                        )
                                    }
                                playerConnection.player.seekTo(target)
                                state.requestResync(target, playerConnection.player.playWhenReady)
                                gestureFeedback =
                                    GestureFeedback.Seek(
                                        forward = !isLeftHalf,
                                        showAt = System.currentTimeMillis(),
                                    )

                                volumeGestureJob?.cancel()
                                brightnessGestureJob?.cancel()
                                brightnessGestureJob =
                                    scope.launch {
                                        delay(GestureFeedbackLingerMs)
                                        if (gestureFeedback is GestureFeedback.Seek) {
                                            gestureFeedback = null
                                        }
                                    }

                                controlsVisible = true
                            }
                        },
                    )
                }

                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            val isLeftHalf = offset.x < size.width / 2f
                            if (isLeftHalf) {
                                brightnessDragActive = true
                            } else {
                                volumeDragActive = true
                            }
                        },
                        onDragEnd = {
                            if (brightnessDragActive) {
                                brightnessDragActive = false
                                brightnessGestureJob?.cancel()
                                brightnessGestureJob =
                                    scope.launch {
                                        delay(GestureFeedbackLingerMs)
                                        if (gestureFeedback is GestureFeedback.Brightness) {
                                            gestureFeedback = null
                                        }
                                    }
                            }
                            if (volumeDragActive) {
                                volumeDragActive = false
                                volumeGestureJob?.cancel()
                                volumeGestureJob =
                                    scope.launch {
                                        delay(GestureFeedbackLingerMs)
                                        if (gestureFeedback is GestureFeedback.Volume) {
                                            gestureFeedback = null
                                        }
                                    }
                            }
                        },
                        onDragCancel = {
                            brightnessDragActive = false
                            volumeDragActive = false
                            gestureFeedback = null
                        },
                        onVerticalDrag = { change, dragAmount ->
                            val isLeftHalf = change.position.x < size.width / 2f
                            if (isLeftHalf && brightnessDragActive) {
                                val delta = -dragAmount / 400f
                                val next = (currentWindowBrightness(context) + delta).coerceIn(0f, 1f)
                                applyWindowBrightness(context, next)
                                gestureFeedback =
                                    GestureFeedback.Brightness(
                                        percent = (next * 100f).toInt(),
                                        showAt = System.currentTimeMillis(),
                                    )
                                controlsVisible = false
                            } else if (!isLeftHalf && volumeDragActive) {
                                val maxVol = maxMediaVolume(context)
                                if (maxVol > 0) {
                                    val delta = (-dragAmount / 150f) * maxVol
                                    val currentVol = audioManager(context)?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                                    val next = (currentVol + delta).toInt().coerceIn(0, maxVol)
                                    setMediaVolume(context, next)
                                    gestureFeedback =
                                        GestureFeedback.Volume(
                                            percent = (next * 100 / maxVol),
                                            showAt = System.currentTimeMillis(),
                                        )
                                    controlsVisible = false
                                }
                            }
                        },
                    )
                },
    ) {
        VideoArtworkSurface(
            state = state,
            resizeMode = effectiveResizeMode,
            ambientMode = ambientMode,
            thumbnailUrl = thumbnailUrl,
            modifier = Modifier.fillMaxSize(),
        )

        gestureFeedback?.let { feedback -> GestureFeedbackBubble(feedback) }

        if (isLoadingState(state)) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(56.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = controlsVisible && !showOverflowSheet && !qualityMenuOpen && !aspectRatioMenuOpen && !isInPipMode,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))) {
                headerMetadata?.let { meta ->
                    PlayerTextBackdrop(
                        textColor = Color.White,
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .statusBarsPadding()
                                .padding(start = 16.dp, top = 8.dp, end = 160.dp),
                    ) {
                        Column {
                            Text(
                                text = meta.title,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .basicMarquee(),
                            )
                            val artistText = meta.artists.joinToString(", ") { it.name }
                            if (artistText.isNotBlank()) {
                                Text(
                                    text = artistText,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .basicMarquee(),
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(8.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(28.dp),
                            ).padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (availableHeights.isNotEmpty()) {
                        Row(
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(24.dp))
                                    .clickable { qualityMenuOpen = true }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.solar_settings_linear),
                                contentDescription = stringResource(R.string.video_quality),
                                tint = Color.White,
                                modifier = Modifier.size(22.dp),
                            )
                            Text(
                                text =
                                    videoQualityPillLabel(
                                        preferredHeight = preferredHeight,
                                        selectedHeight = selectedHeight,
                                    ),
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                            )
                        }
                    }

                    IconButton(
                        onClick = { aspectRatioMenuOpen = true },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.solar_aspect_ratio_linear),
                            contentDescription = stringResource(R.string.video_aspect_ratio),
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    IconButton(
                        onClick = { showOverflowSheet = true },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.solar_more_vert_linear),
                            contentDescription = stringResource(R.string.video_overflow_menu),
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.solar_fullscreen_exit_linear),
                            contentDescription = stringResource(R.string.video_exit_fullscreen),
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                if (playerConnection != null) {
                    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
                    val canSkipNext by playerConnection.canSkipNext.collectAsStateWithLifecycle()
                    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsStateWithLifecycle()
                    val playbackStateFs by playerConnection.playbackState.collectAsStateWithLifecycle()

                    Row(
                        modifier =
                            Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 24.dp)
                                .background(
                                    color = Color.Black.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(40.dp),
                                ).padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                            IconButton(
                                onClick = { playerConnection.seekToPrevious() },
                                enabled = canSkipPrevious,
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.solar_skip_previous_linear),
                                    contentDescription = stringResource(R.string.video_fs_previous),
                                    tint = if (canSkipPrevious) Color.White else Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(36.dp),
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (playbackStateFs == Player.STATE_ENDED) {
                                        playerConnection.player.seekTo(0, 0)
                                        playerConnection.player.playWhenReady = true
                                    } else {
                                        playerConnection.player.togglePlayPause()
                                    }
                                },
                                modifier = Modifier.size(64.dp),
                            ) {
                                val playIcon =
                                    when {
                                        playbackStateFs == Player.STATE_ENDED -> R.drawable.solar_replay_linear
                                        isPlaying -> R.drawable.solar_pause_linear
                                        else -> R.drawable.solar_play_linear
                                    }
                                Icon(
                                    painter = painterResource(playIcon),
                                    contentDescription = stringResource(R.string.video_fs_play_pause),
                                    tint = Color.White,
                                    modifier = Modifier.size(44.dp),
                                )
                            }
                            IconButton(
                                onClick = { playerConnection.seekToNext() },
                                enabled = canSkipNext,
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.solar_skip_next_linear),
                                    contentDescription = stringResource(R.string.video_fs_next),
                                    tint = if (canSkipNext) Color.White else Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(36.dp),
                                )
                            }
                        }

                    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
                    val currentPosition = remember(mediaMetadata?.id) {
                        mutableLongStateOf(playerConnection.player.currentPosition)
                    }
                    val totalDuration = remember(mediaMetadata?.id) {
                        mutableLongStateOf(playerConnection.player.duration)
                    }
                    val playbackState by playerConnection.playbackState.collectAsStateWithLifecycle()
                    LaunchedEffect(mediaMetadata?.id, playbackState) {
                        if (playbackState == Player.STATE_READY) {
                            while (isActive) {
                                delay(100)
                                if (!isUserSeeking) {
                                    currentPosition.longValue = playerConnection.player.currentPosition
                                    totalDuration.longValue = playerConnection.player.duration
                                }
                            }
                        }
                    }

                    val duration = totalDuration.longValue
                    val seekEnabled = duration > 0L && duration != C.TIME_UNSET
                    val displayPosition = sliderPosition ?: currentPosition.longValue.coerceIn(0L, duration.coerceAtLeast(0L))

                    Column(
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        StyledPlaybackSlider(
                            sliderStyle = sliderStyle,
                            value = if (seekEnabled) displayPosition.toFloat() else 0f,
                            valueRange = if (seekEnabled) 0f..duration.toFloat() else 0f..1f,
                            onValueChange = { newValue ->
                                if (seekEnabled) {
                                    isUserSeeking = true
                                    sliderPosition = newValue.toLong()
                                }
                            },
                            onValueChangeFinished = {
                                sliderPosition?.let { target ->
                                    playerConnection.player.seekTo(target)

                                    state.requestResync(target, playerConnection.player.playWhenReady)
                                }
                                isUserSeeking = false
                                sliderPosition = null
                            },
                            activeColor = Color.White,
                            isPlaying = playerConnection.player.playWhenReady,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = formatTime(displayPosition),
                                color = Color.White,
                                fontSize = 13.sp,
                            )
                            Text(
                                text = formatTime(duration),
                                color = Color.White,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
        }
    }

    if (qualityMenuOpen) {
        VideoQualitySheet(
            preferredHeight = preferredHeight,
            availableHeights = availableHeights,
            selectedHeight = selectedHeight,
            onPreferredHeightChange = onPreferredHeightChange,
            onDismissRequest = { qualityMenuOpen = false },
        )
    }

    if (aspectRatioMenuOpen) {
        VideoAspectRatioSheet(
            aspectRatio = aspectRatio,
            onAspectRatioChange = onAspectRatioChange,
            onDismissRequest = { aspectRatioMenuOpen = false },
        )
    }

    if (showOverflowSheet) {
        ModalBottomSheet(
            onDismissRequest = { showOverflowSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            KeepStatusBarHiddenInDialog()
            VideoOverflowSheetContent(
                sliderStyle = sliderStyle,
                onSliderStyleChange = onSliderStyleChange,
                playbackSpeed = playbackSpeed,
                onPlaybackSpeedChange = onPlaybackSpeedChange,
                ambientMode = ambientMode,
                onAmbientModeChange = onAmbientModeChange,
            )
        }
    }
}

private const val FullscreenControlsAutoHideMs = 3_500L

@Composable
private fun VideoOverflowSheetContent(
    sliderStyle: SliderStyle,
    onSliderStyleChange: (SliderStyle) -> Unit,
    playbackSpeed: Float,
    onPlaybackSpeedChange: (Float) -> Unit,
    ambientMode: Boolean,
    onAmbientModeChange: (Boolean) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .systemBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.video_slider_style),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SliderStyle.entries.forEach { style ->
                    val labelRes =
                        when (style) {
                            SliderStyle.Standard -> R.string.slider_style_standard
                            SliderStyle.Wavy -> R.string.slider_style_wavy
                            SliderStyle.Thick -> R.string.slider_style_thick
                            SliderStyle.Circular -> R.string.slider_style_circular
                            SliderStyle.Simple -> R.string.slider_style_simple
                        }
                    PillToggle(
                        text = stringResource(labelRes),
                        selected = style == sliderStyle,
                        onClick = { onSliderStyleChange(style) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.video_playback_speed),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.video_playback_speed_value, playbackSpeed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Slider(
                value = playbackSpeed.coerceIn(0.25f, 2f),
                onValueChange = { onPlaybackSpeedChange(it) },
                valueRange = 0.25f..2f,
                steps = 6,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.video_playback_speed_value, 0.25f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PillToggle(
                    text = stringResource(R.string.video_playback_speed_normal),
                    selected = kotlin.math.abs(playbackSpeed - 1f) < 0.01f,
                    onClick = { onPlaybackSpeedChange(1f) },
                )
                Text(
                    text = stringResource(R.string.video_playback_speed_value, 2f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.video_ambient_mode),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.video_ambient_mode_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = ambientMode,
                onCheckedChange = onAmbientModeChange,
            )
        }

    }
}

@Composable
private fun PillToggle(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier =
            modifier
                .background(bg, CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ).padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

internal fun isLoadingState(state: VideoArtworkState): Boolean =
    !state.hasPlaybackFailed &&
        (
            state.isResolvingUrl ||
                state.isResyncing ||
                (state.streamUrl != null && !state.isVideoReady)
        )

private const val GestureFeedbackLingerMs = 800L

private sealed interface GestureFeedback {
    val showAt: Long

    data class Brightness(val percent: Int, override val showAt: Long) : GestureFeedback

    data class Volume(val percent: Int, override val showAt: Long) : GestureFeedback

    data class Seek(val forward: Boolean, override val showAt: Long) : GestureFeedback
}

@Composable
private fun BoxScope.GestureFeedbackBubble(feedback: GestureFeedback) {
    val (iconRes, label) =
        when (feedback) {
            is GestureFeedback.Brightness -> {
                val res =
                    when {
                        feedback.percent <= 0 -> R.drawable.solar_brightness_low_linear
                        feedback.percent >= 100 -> R.drawable.solar_brightness_high_linear
                        else -> R.drawable.solar_brightness_auto_linear
                    }
                res to stringResource(R.string.percentage_format, feedback.percent)
            }
            is GestureFeedback.Volume -> {
                val res =
                    if (feedback.percent <= 0) R.drawable.solar_volume_off_linear else R.drawable.solar_volume_up_linear
                res to stringResource(R.string.percentage_format, feedback.percent)
            }
            is GestureFeedback.Seek -> {
                val res =
                    if (feedback.forward) R.drawable.solar_forward_linear else R.drawable.solar_rewind_linear
                val textRes =
                    if (feedback.forward) R.string.video_gesture_seek_forward else R.string.video_gesture_seek_backward
                res to stringResource(textRes)
            }
        }
    Box(
        modifier =
            Modifier
                .align(Alignment.Center)
                .background(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(16.dp),
                ).padding(horizontal = 24.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(32.dp),
            )
            Text(
                text = label,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun currentWindowBrightness(context: Context): Float {
    val activity = context.findActivity() ?: return 0.5f
    val attrs = activity.window.attributes
    return if (attrs.screenBrightness == WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) {
        val system =
            try {
                Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            } catch (e: Settings.SettingNotFoundException) {
                128
            }

        system / 255f
    } else {
        attrs.screenBrightness
    }
}

private fun applyWindowBrightness(context: Context, brightness: Float) {
    val activity = context.findActivity() ?: return
    val window = activity.window ?: return
    val params = window.attributes
    params.screenBrightness = brightness.coerceIn(0f, 1f)
    window.attributes = params
}

private fun audioManager(context: Context): AudioManager? =
    context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

private fun maxMediaVolume(context: Context): Int =
    audioManager(context)?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0

private fun setMediaVolume(context: Context, volume: Int) {
    val am = audioManager(context) ?: return
    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    am.setStreamVolume(
        AudioManager.STREAM_MUSIC,
        volume.coerceIn(0, max),
        AudioManager.FLAG_SHOW_UI,
    )
}
