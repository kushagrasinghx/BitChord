/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.player

import android.content.Context
import android.content.res.Configuration
import android.database.ContentObserver
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import com.materialkolor.ktx.toHct
import com.materialkolor.ktx.toColor
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Player.STATE_BUFFERING
import androidx.media3.common.Player.STATE_READY
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.LocalDownloadUtil
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.LocalStableSystemBarsTopPadding
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import moe.rukamori.archivetune.constants.VideoQualityPreferredHeightKey
import moe.rukamori.archivetune.constants.ArchiveTuneCanvasKey
import moe.rukamori.archivetune.constants.ShowCodecOnPlayerKey
import moe.rukamori.archivetune.constants.SpotifyCanvasKey
import moe.rukamori.archivetune.constants.SongCanvasDisabledKey
import moe.rukamori.archivetune.audiosource.SongCanvasDisabled
import moe.rukamori.archivetune.constants.BackdropBlurAmountKey
import moe.rukamori.archivetune.constants.BackdropEnabledKey
import moe.rukamori.archivetune.constants.BlurRadiusKey
import moe.rukamori.archivetune.constants.DarkModeKey
import moe.rukamori.archivetune.constants.DisableBlurKey
import moe.rukamori.archivetune.constants.EnableHapticFeedbackKey
import moe.rukamori.archivetune.constants.EnableVideoPlaybackKey
import moe.rukamori.archivetune.constants.InnerTubeCookieKey
import moe.rukamori.archivetune.constants.MaxCanvasCacheSizeKey
import moe.rukamori.archivetune.constants.PlayerBackgroundStyle
import moe.rukamori.archivetune.constants.PlayerBackgroundStyleKey
import moe.rukamori.archivetune.constants.PlayerButtonsStyle
import moe.rukamori.archivetune.constants.PlayerButtonsStyleKey
import moe.rukamori.archivetune.constants.PlayerCustomBlurKey
import moe.rukamori.archivetune.constants.PlayerCustomBrightnessKey
import moe.rukamori.archivetune.constants.PlayerCustomContrastKey
import moe.rukamori.archivetune.constants.PlayerCustomImageUriKey
import moe.rukamori.archivetune.constants.PlayerDesignStyle
import moe.rukamori.archivetune.constants.PlayerDesignStyleKey
import moe.rukamori.archivetune.constants.PoTokenGvsKey
import moe.rukamori.archivetune.constants.PoTokenPlayerKey
import moe.rukamori.archivetune.constants.QueuePeekHeight
import moe.rukamori.archivetune.constants.ShowPlayerVolumeBarKey
import moe.rukamori.archivetune.constants.SliderStyle
import moe.rukamori.archivetune.constants.SliderStyleKey
import moe.rukamori.archivetune.constants.ThumbnailCornerRadiusKey
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.innertube.utils.hasYouTubeLoginCookie
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.component.BottomSheet
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.component.COLLAPSED_ANCHOR
import moe.rukamori.archivetune.ui.component.DISMISSED_ANCHOR
import moe.rukamori.archivetune.ui.component.EXPANDED_ANCHOR
import moe.rukamori.archivetune.ui.component.LocalBottomSheetPageState
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.PlayerBackController
import moe.rukamori.archivetune.ui.component.PlayerBackTarget
import moe.rukamori.archivetune.ui.component.PredictiveSheetBackState
import moe.rukamori.archivetune.ui.lottie.ArchiveTuneLottie
import moe.rukamori.archivetune.ui.lottie.ArchiveTuneLottieAnimation
import moe.rukamori.archivetune.ui.component.rememberBottomSheetState
import moe.rukamori.archivetune.ui.menu.PlayerMenu
import moe.rukamori.archivetune.ui.screens.LOGIN_ROUTE
import moe.rukamori.archivetune.ui.screens.buildLoginRoute
import moe.rukamori.archivetune.ui.screens.settings.DarkMode
import moe.rukamori.archivetune.ui.screens.settings.PO_TOKEN_ROUTE
import moe.rukamori.archivetune.ui.theme.PlayerColorExtractor
import moe.rukamori.archivetune.ui.theme.PlayerPaletteCache
import moe.rukamori.archivetune.playback.artwork.PlayerPaletteCacheKey
import moe.rukamori.archivetune.playback.artwork.guessArtworkProvider
import moe.rukamori.archivetune.ui.utils.ShowMediaInfo
import moe.rukamori.archivetune.ui.utils.YtimgResizePolicy
import moe.rukamori.archivetune.ui.utils.getNextFallbackUrl
import moe.rukamori.archivetune.ui.utils.resize
import moe.rukamori.archivetune.utils.ImageBlurUtils
import moe.rukamori.archivetune.utils.isLocalMediaId
import moe.rukamori.archivetune.ui.player.bitchord.BitChordPlayerContent
import moe.rukamori.archivetune.ui.player.flamingo.FlamingoPlayerContent
import moe.rukamori.archivetune.ui.player.tiktok.TikTokPlayerContent
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberLowDataModeActive
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.ui.player.simpmusic.SimpMusicPlayerContent
import moe.rukamori.archivetune.ui.player.spatialflow.SpatialFlowFloatingArtwork
import moe.rukamori.archivetune.ui.player.spatialflow.SpatialFlowPlayerContent
import moe.rukamori.archivetune.ui.utils.highRes
import moe.rukamori.archivetune.ui.player.looper.LooperPlayerContent
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import moe.rukamori.archivetune.ui.component.KeepStatusBarHiddenInDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private const val SeekbarSettleToleranceMs = 1_500L
private const val V7BackdropMinArtworkSizePx = 1_024
private const val V7BackdropMaxArtworkSizePx = 2_048
private const val V7BackdropBlurDp = 44
private const val V7BackdropBlurScale = 1.18f

private const val V7CanvasBackdropUpscale = 6f
private const val V7BackdropArtworkOverscanFactor = 1.15f
private const val V7SharpStagePortraitFraction = 0.62f
private const val V7SharpStageLandscapeFraction = 0.58f
private const val V7BackdropOverlapDp = 72
private const val V7SharpStageBottomScrimStartFraction = 0.40f
private const val V7BackdropFloorBlackStartFraction = 0.88f

@Stable
internal class DeviceMusicVolumeController(
    private val audioManager: AudioManager,
) {
    private var minVolume by mutableIntStateOf(readMinVolume())
    private var maxVolume by mutableIntStateOf(readMaxVolume())
    var volumeFraction by mutableFloatStateOf(readVolumeFraction())
        private set

    fun refresh() {
        minVolume = readMinVolume()
        maxVolume = readMaxVolume()
        volumeFraction = readVolumeFraction()
    }

    @JvmName("setDeviceMusicVolumeFraction")
    fun setVolumeFraction(fraction: Float) {
        val safeFraction = fraction.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: volumeFraction
        val volumeRange = (maxVolume - minVolume).coerceAtLeast(1)
        val targetVolume =
            (minVolume + (safeFraction * volumeRange).roundToInt())
                .coerceIn(minVolume, maxVolume)
        val adjustedTarget =
            if (safeFraction > 0f && targetVolume <= minVolume) {
                (minVolume + 1).coerceAtMost(maxVolume)
            } else {
                targetVolume
            }

        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, adjustedTarget, 0)
        refresh()
    }

    private fun readVolumeFraction(): Float {
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val volumeRange = (maxVolume - minVolume).coerceAtLeast(1)
        return ((currentVolume - minVolume).toFloat() / volumeRange.toFloat()).coerceIn(0f, 1f)
    }

    private fun readMaxVolume(): Int {
        val streamMinVolume = readMinVolume()
        return audioManager
            .getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            .coerceAtLeast(streamMinVolume + 1)
    }

    private fun readMinVolume(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
        } else {
            0
        }
}

private val SpatialFlowArtworkRectSaver =
    Saver<Rect?, List<Float>>(
        save = { rect -> rect?.let { listOf(it.left, it.top, it.right, it.bottom) } },
        restore = { values -> Rect(values[0], values[1], values[2], values[3]) },
    )

@Composable
internal fun rememberDeviceMusicVolumeController(): DeviceMusicVolumeController {
    val context = LocalContext.current
    val audioManager =
        remember(context) {
            context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        }
    val controller =
        remember(audioManager) {
            DeviceMusicVolumeController(audioManager)
        }

    DisposableEffect(context, controller) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    controller.refresh()
                }
            }
        val contentResolver = context.applicationContext.contentResolver
        contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
        controller.refresh()
        onDispose {
            contentResolver.unregisterContentObserver(observer)
        }
    }

    return controller
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetPlayer(
    state: BottomSheetState,
    navController: NavController,
    modifier: Modifier = Modifier,
    pureBlack: Boolean,
    isMiniPlayerPairedWithNavigation: Boolean = false,
    onLyricsVisibilityChange: (Boolean) -> Unit = {},
    compactFractionState: State<Float> = remember { mutableStateOf(0f) },
    compactHorizontalPadding: Dp = 16.dp,
    compactReserveEndControl: Boolean = true,
    navbarHiddenOffset: (() -> Float)? = null,
    backController: PlayerBackController? = null,
    predictiveBackState: PredictiveSheetBackState? = null,
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current

    val bottomSheetPageState = LocalBottomSheetPageState.current

    val playerConnection = LocalPlayerConnection.current ?: return
    val playbackError by playerConnection.error.collectAsStateWithLifecycle()
    val (innerTubeCookie) = rememberPreference(InnerTubeCookieKey, defaultValue = "")
    val (poTokenGvs) = rememberPreference(PoTokenGvsKey, defaultValue = "")
    val (poTokenPlayer) = rememberPreference(PoTokenPlayerKey, defaultValue = "")
    val isYouTubeLoggedIn =
        remember(innerTubeCookie) {
            hasYouTubeLoginCookie(innerTubeCookie)
        }
    val isPoTokenLoggedIn =
        remember(poTokenGvs, poTokenPlayer) {
            poTokenGvs.isNotBlank() && poTokenPlayer.isNotBlank()
        }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val retryPlayback =
        remember(playerConnection) {
            {
                playerConnection.player.prepare()
                playerConnection.player.play()
            }
        }
    val dismissPlaybackError =
        remember(playerConnection) {
            playerConnection::dismissPlaybackError
        }
    val navigateToLogin: (String?) -> Unit =
        remember(navController) {
            { recoveryUrl ->
                navController.navigate(buildLoginRoute(recoveryUrl)) {
                    launchSingleTop = true
                }
            }
        }
    val navigateToPoTokenLogin =
        remember(navController) {
            {
                navController.navigate(PO_TOKEN_ROUTE) {
                    launchSingleTop = true
                }
            }
        }

    val playerDesignStyle by rememberEnumPreference(
        key = PlayerDesignStyleKey,
        defaultValue = PlayerDesignStyle.APPLE_MUSIC,
    )
    val showPlayerVolumeBar by rememberPreference(
        key = ShowPlayerVolumeBarKey,
        defaultValue = true,
    )

    val storedPlayerBackground by rememberEnumPreference(
        key = PlayerBackgroundStyleKey,
        defaultValue = PlayerBackgroundStyle.DEFAULT,
    )
    val playerUsesFixedBackground =
        playerDesignStyle == PlayerDesignStyle.V9 ||
            playerDesignStyle == PlayerDesignStyle.V10 ||
            playerDesignStyle == PlayerDesignStyle.APPLE_MUSIC ||
            playerDesignStyle == PlayerDesignStyle.BITCHORD ||
            playerDesignStyle == PlayerDesignStyle.TIKTOK ||
            playerDesignStyle == PlayerDesignStyle.SIMPMUSIC ||
            playerDesignStyle == PlayerDesignStyle.SPATIALFLOW ||
            playerDesignStyle == PlayerDesignStyle.LOOPER
    val playerBackground =
        if (playerUsesFixedBackground) PlayerBackgroundStyle.DEFAULT else storedPlayerBackground

    val (playerCustomImageUri) = rememberPreference(PlayerCustomImageUriKey, "")
    val (playerCustomBlur) = rememberPreference(PlayerCustomBlurKey, 0f)
    val (playerCustomContrast) = rememberPreference(PlayerCustomContrastKey, 1f)
    val (playerCustomBrightness) = rememberPreference(PlayerCustomBrightnessKey, 1f)

    val (disableBlur) = rememberPreference(DisableBlurKey, false)
    val (blurRadius) = rememberPreference(BlurRadiusKey, 48f)
    val (backdropEnabled) = rememberPreference(BackdropEnabledKey, defaultValue = true)
    val (backdropBlurAmount) = rememberPreference(BackdropBlurAmountKey, defaultValue = 60)
    val (showCodecOnPlayer) = rememberPreference(ShowCodecOnPlayerKey, false)
    val (incrementalSeekSkipEnabled) = rememberPreference(moe.rukamori.archivetune.constants.SeekExtraSeconds, defaultValue = false)
    val enableVideoPlayback by rememberPreference(EnableVideoPlaybackKey, defaultValue = true)
    var keyboardSkipMultiplier by remember { mutableStateOf(1) }
    var lastKeyboardTapTime by remember { mutableLongStateOf(0L) }

    val playerButtonsStyle by rememberEnumPreference(
        key = PlayerButtonsStyleKey,
        defaultValue = PlayerButtonsStyle.DEFAULT,
    )

    val isSystemInDarkTheme = isSystemInDarkTheme()
    val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
    val useDarkTheme =
        remember(darkTheme, isSystemInDarkTheme) {
            if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
        }
    val onBackgroundColor =
        when (playerBackground) {
            PlayerBackgroundStyle.DEFAULT -> {
                MaterialTheme.colorScheme.secondary
            }

            else -> {
                if (useDarkTheme) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onPrimary
                }
            }
        }
    val useBlackBackground =
        remember(isSystemInDarkTheme, darkTheme, pureBlack) {
            val useDarkTheme =
                if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
            useDarkTheme && pureBlack
        }

    val sheetSurfaceColor = MaterialTheme.colorScheme.surface

    val playbackState by playerConnection.playbackState.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val currentSong by playerConnection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val currentSongLiked = currentSong?.song?.liked == true
    val queueTitle by playerConnection.queueTitle.collectAsStateWithLifecycle()
    val currentFormat by playerConnection.currentFormat.collectAsStateWithLifecycle(initialValue = null)

    val currentLyricsEntity by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
    val queueWindows by playerConnection.queueWindows.collectAsStateWithLifecycle()
    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsStateWithLifecycle()
    val deviceMusicVolumeController = rememberDeviceMusicVolumeController()
    val onPlayerVolumeChange =
        remember(deviceMusicVolumeController) {
            { volume: Float ->
                deviceMusicVolumeController.setVolumeFraction(volume)
            }
        }

    val repeatMode by playerConnection.repeatMode.collectAsStateWithLifecycle()

    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsStateWithLifecycle()
    val canSkipNext by playerConnection.canSkipNext.collectAsStateWithLifecycle()

    val aodModeEnabled by playerConnection.aodModeEnabled.collectAsStateWithLifecycle()
    val (thumbnailCornerRadius) = rememberPreference(ThumbnailCornerRadiusKey, defaultValue = 8f)
    val archiveTuneCanvasEnabled by rememberPreference(ArchiveTuneCanvasKey, false)
    val spotifyCanvasEnabled by rememberPreference(SpotifyCanvasKey, false)
    val lowDataModeActive = rememberLowDataModeActive()
    val (maxCanvasCacheSize, _) =
        rememberPreference(
            key = MaxCanvasCacheSizeKey,
            defaultValue = 256,
        )

    val sliderStyle by rememberEnumPreference(SliderStyleKey, SliderStyle.Standard)

    LaunchedEffect(maxCanvasCacheSize) {
        CanvasArtworkPlaybackCache.setMaxSize(maxCanvasCacheSize)
    }

    val positionState =
        rememberSaveable(mediaMetadata?.id) {
            val player = playerConnection.player
            val seededPosition =
                if (player.playbackState == Player.STATE_READY &&
                    player.currentMediaItem?.mediaId == mediaMetadata?.id
                ) {
                    player.currentPosition.coerceAtLeast(0L)
                } else {
                    0L
                }
            mutableLongStateOf(seededPosition)
        }
    var position by positionState
    val positionProvider = remember(positionState) { { positionState.longValue } }

    val spatialFlowMiniArtworkRect =
        rememberSaveable(stateSaver = SpatialFlowArtworkRectSaver) { mutableStateOf<Rect?>(null) }

    val spatialFlowFullArtworkRect =
        rememberSaveable(stateSaver = SpatialFlowArtworkRectSaver) { mutableStateOf<Rect?>(null) }
    var spatialFlowPagerArtworkActive by remember { mutableStateOf(true) }

    var spatialFlowLyricsOpen by remember { mutableStateOf(false) }
    var spatialFlowQueueOpen by remember { mutableStateOf(false) }
    val stylePageBackRequests = remember { mutableIntStateOf(0) }
    var simpmusicStylePageOpen by remember { mutableStateOf(false) }
    var tiktokStylePageOpen by remember { mutableStateOf(false) }
    var bitchordStylePageOpen by remember { mutableStateOf(false) }
    var duration by rememberSaveable(mediaMetadata?.id) {
        mutableLongStateOf(playerConnection.player.duration)
    }

    val durationUpdatedState = rememberUpdatedState(duration)
    val durationProvider = remember { { durationUpdatedState.value } }
    var lyricsSyncOffset by rememberSaveable(mediaMetadata?.id) {
        mutableIntStateOf(0)
    }
    var sliderPosition by remember(mediaMetadata?.id) {
        mutableStateOf<Long?>(null)
    }
    var isUserSeeking by remember(mediaMetadata?.id) {
        mutableStateOf(false)
    }

    val isLoading = playbackState == STATE_BUFFERING || sliderPosition != null

    var gradientColors by remember {
        mutableStateOf<List<Color>>(emptyList())
    }
    var hasValidGradientPalette by remember { mutableStateOf(false) }

    val defaultGradientColors = listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceVariant)
    val fallbackColor = MaterialTheme.colorScheme.surface.toArgb()

    val paletteArtworkUrl = mediaMetadata?.thumbnailUrl

    LaunchedEffect(mediaMetadata?.id, paletteArtworkUrl, playerBackground, useDarkTheme, playerDesignStyle) {
        if (aodModeEnabled) return@LaunchedEffect
        val wantsPalette =
            playerBackground == PlayerBackgroundStyle.GRADIENT || playerBackground == PlayerBackgroundStyle.COLORING ||
                playerBackground == PlayerBackgroundStyle.BLUR ||
                playerBackground == PlayerBackgroundStyle.BLUR_GRADIENT ||
                playerBackground == PlayerBackgroundStyle.GLOW ||
                playerBackground == PlayerBackgroundStyle.GLOW_ANIMATED ||
                playerDesignStyle == PlayerDesignStyle.V9 || playerDesignStyle == PlayerDesignStyle.V10
        if (!wantsPalette) {
            gradientColors = emptyList()
            hasValidGradientPalette = false
            return@LaunchedEffect
        }
        val currentMetadata = mediaMetadata
        val artworkUrl = currentMetadata?.thumbnailUrl
        if (currentMetadata == null || artworkUrl.isNullOrBlank()) {
            if (!hasValidGradientPalette) gradientColors = emptyList()
            return@LaunchedEffect
        }

        val cacheKey =
            PlayerPaletteCacheKey(
                mediaId = currentMetadata.id,
                provider = guessArtworkProvider(artworkUrl),
                artworkIdentity = artworkUrl,
                backgroundMode = playerBackground.name,
                darkTheme = useDarkTheme,
            )
        PlayerPaletteCache.get(cacheKey)?.let { cachedColors ->
            gradientColors = cachedColors
            hasValidGradientPalette = true
            return@LaunchedEffect
        }

        val request =
            ImageRequest
                .Builder(context)
                .data(artworkUrl)
                .memoryCacheKey(artworkUrl)
                .diskCacheKey(artworkUrl)
                .diskCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                .size(PlayerColorExtractor.Config.IMAGE_SIZE, PlayerColorExtractor.Config.IMAGE_SIZE)
                .allowHardware(false)
                .build()

        val result =
            try {
                withContext(Dispatchers.IO) { context.imageLoader.execute(request) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                null
            }

        if (result !is SuccessResult) {
            if (!hasValidGradientPalette) gradientColors = defaultGradientColors
            return@LaunchedEffect
        }
        val bitmap = result.image?.toBitmap()
        if (bitmap == null) {
            if (!hasValidGradientPalette) gradientColors = defaultGradientColors
            return@LaunchedEffect
        }

        val palette =
            withContext(Dispatchers.Default) {
                Palette
                    .from(bitmap)
                    .maximumColorCount(PlayerColorExtractor.Config.MAX_COLOR_COUNT)
                    .resizeBitmapArea(PlayerColorExtractor.Config.BITMAP_AREA)
                    .generate()
            }

        val extractedColors =
            PlayerColorExtractor.extractGradientColors(
                palette = palette,
                fallbackColor = fallbackColor,
            )

        val stillCurrent =
            mediaMetadata?.id == currentMetadata.id &&
                mediaMetadata?.thumbnailUrl == artworkUrl
        if (stillCurrent) {
            PlayerPaletteCache.put(cacheKey, extractedColors)
            gradientColors = extractedColors
            hasValidGradientPalette = true
        }
    }

    val changeBound = state.expandedBound / 3

    val dominantColor = gradientColors.firstOrNull() ?: MaterialTheme.colorScheme.primary

    val targetV10FieldColor =
        remember(dominantColor, useDarkTheme) {
            val hct = dominantColor.toHct()
            if (useDarkTheme) hct.withTone(30.0).toColor() else hct.withTone(90.0).toColor()
        }
    val dynamicV10FieldColor by animateColorAsState(
        targetValue = targetV10FieldColor,
        animationSpec = tween(durationMillis = 800),
        label = "dynamicV10FieldColor",
    )
    val targetV10AccentColor =
        remember(dominantColor, useDarkTheme) {
            val hct = dominantColor.toHct()
            if (useDarkTheme) hct.withTone(90.0).toColor() else hct.withTone(10.0).toColor()
        }
    val dynamicV10AccentColor by animateColorAsState(
        targetValue = targetV10AccentColor,
        animationSpec = tween(durationMillis = 800),
        label = "dynamicV10AccentColor",
    )
    val targetBgColor = remember(dominantColor, useDarkTheme) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(dominantColor.toArgb(), hsv)
        if (useDarkTheme) {
            hsv[1] = hsv[1].coerceIn(0.12f, 0.35f)
            hsv[2] = 0.08f
        } else {
            hsv[1] = hsv[1].coerceIn(0.04f, 0.12f)
            hsv[2] = 0.96f
        }
        Color(android.graphics.Color.HSVToColor(hsv))
    }
    val dynamicBgColor by animateColorAsState(
        targetValue = targetBgColor,
        animationSpec = tween(durationMillis = 800),
        label = "dynamicBgColor",
    )

    val targetAccentColor = dominantColor
    val dynamicAccentColor by animateColorAsState(
        targetValue = targetAccentColor,
        animationSpec = tween(durationMillis = 800),
        label = "dynamicAccentColor",
    )

    val targetTextColor = remember(dominantColor, useDarkTheme) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(dominantColor.toArgb(), hsv)
        if (useDarkTheme) {
            hsv[1] = hsv[1].coerceAtMost(0.12f)
            hsv[2] = 0.96f
        } else {
            hsv[1] = hsv[1].coerceIn(0.12f, 0.35f)
            hsv[2] = 0.08f
        }
        Color(android.graphics.Color.HSVToColor(hsv))
    }
    val dynamicTextColor by animateColorAsState(
        targetValue = targetTextColor,
        animationSpec = tween(durationMillis = 800),
        label = "dynamicTextColor",
    )

    val targetIconButtonColor = remember(dynamicAccentColor) {
        val luminance =
            0.299f * dynamicAccentColor.red +
                0.587f * dynamicAccentColor.green +
                0.114f * dynamicAccentColor.blue
        if (luminance > 0.5f) Color.Black else Color.White
    }
    val dynamicIconButtonColor by animateColorAsState(
        targetValue = targetIconButtonColor,
        animationSpec = tween(durationMillis = 800),
        label = "dynamicIconButtonColor",
    )

    val TextBackgroundColor =
        if (playerDesignStyle == PlayerDesignStyle.V10) {
            dynamicV10AccentColor
        } else if (playerDesignStyle == PlayerDesignStyle.V9) {
            dynamicTextColor
        } else if (playerDesignStyle == PlayerDesignStyle.V7) {
            Color.White
        } else {
            when (playerBackground) {
                PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.onBackground
                PlayerBackgroundStyle.BLUR -> Color.White
                PlayerBackgroundStyle.GRADIENT -> Color.White
                PlayerBackgroundStyle.COLORING -> Color.White
                PlayerBackgroundStyle.BLUR_GRADIENT -> Color.White
                PlayerBackgroundStyle.GLOW -> Color.White
                PlayerBackgroundStyle.GLOW_ANIMATED -> Color.White
                PlayerBackgroundStyle.CUSTOM -> Color.White
            }
        }

    val icBackgroundColor =
        if (playerDesignStyle == PlayerDesignStyle.V9) {
            dynamicBgColor
        } else if (playerDesignStyle == PlayerDesignStyle.V7) {
            Color.Black
        } else {
            when (playerBackground) {
                PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.surface
                PlayerBackgroundStyle.BLUR -> Color.Black
                PlayerBackgroundStyle.GRADIENT -> Color.Black
                PlayerBackgroundStyle.COLORING -> Color.Black
                PlayerBackgroundStyle.BLUR_GRADIENT -> Color.Black
                PlayerBackgroundStyle.GLOW -> Color.Black
                PlayerBackgroundStyle.GLOW_ANIMATED -> Color.Black
                PlayerBackgroundStyle.CUSTOM -> Color.Black
            }
        }

    val (textButtonColor, iconButtonColor) =
        when (playerButtonsStyle) {
            PlayerButtonsStyle.DEFAULT -> {
                Pair(TextBackgroundColor, icBackgroundColor)
            }

            PlayerButtonsStyle.SECONDARY -> {
                Pair(
                    MaterialTheme.colorScheme.secondary,
                    MaterialTheme.colorScheme.onSecondary,
                )
            }
        }.let { (tb, ib) ->
            if (playerDesignStyle == PlayerDesignStyle.V7) {
                Pair(Color.White, Color.Black)
            } else if (playerDesignStyle == PlayerDesignStyle.V9) {
                Pair(dynamicAccentColor, dynamicIconButtonColor)
            } else if (playerDesignStyle == PlayerDesignStyle.V10) {
                Pair(dynamicV10FieldColor, dynamicV10AccentColor)
            } else {
                Pair(tb, ib)
            }
        }

    val download by LocalDownloadUtil.current
        .getDownload(mediaMetadata?.id ?: "")
        .collectAsStateWithLifecycle(initialValue = null)

    val sleepTimerEnabled =
        remember(
            playerConnection.service.sleepTimer.triggerTime,
            playerConnection.service.sleepTimer.pauseWhenSongEnd,
        ) {
            playerConnection.service.sleepTimer.isActive
        }

    var sleepTimerTimeLeft by remember {
        mutableLongStateOf(0L)
    }

    LaunchedEffect(sleepTimerEnabled) {
        if (sleepTimerEnabled) {
            while (isActive) {
                sleepTimerTimeLeft =
                    if (playerConnection.service.sleepTimer.pauseWhenSongEnd) {
                        playerConnection.player.duration - playerConnection.player.currentPosition
                    } else {
                        playerConnection.service.sleepTimer.triggerTime - System.currentTimeMillis()
                    }
                delay(1000L)
            }
        }
    }

    var showSleepTimerDialog by remember {
        mutableStateOf(false)
    }

    var sleepTimerValue by remember {
        mutableFloatStateOf(30f)
    }
    if (showSleepTimerDialog) {
        AlertDialog(
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = { showSleepTimerDialog = false },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.player_bedtime),
                    contentDescription = null,
                )
            },
            title = { Text(stringResource(R.string.sleep_timer)) },
            confirmButton = {
                KeepStatusBarHiddenInDialog()
                TextButton(
                    onClick = {
                        showSleepTimerDialog = false
                        playerConnection.service.sleepTimer.start(sleepTimerValue.roundToInt())
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSleepTimerDialog = false },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text =
                            pluralStringResource(
                                R.plurals.minute,
                                sleepTimerValue.roundToInt(),
                                sleepTimerValue.roundToInt(),
                            ),
                        style = MaterialTheme.typography.bodyLarge,
                    )

                    Slider(
                        value = sleepTimerValue,
                        onValueChange = { sleepTimerValue = it },
                        valueRange = 5f..120f,
                        steps = (120 - 5) / 5 - 1,
                    )

                    OutlinedIconButton(
                        onClick = {
                            showSleepTimerDialog = false
                            playerConnection.service.sleepTimer.start(-1)
                        },
                    ) {
                        Text(stringResource(R.string.end_of_song))
                    }
                }
            },
        )
    }

    LaunchedEffect(mediaMetadata?.id, playbackState, aodModeEnabled) {
        val startTime = SystemClock.elapsedRealtime()
        if (playbackState == STATE_READY) {
            while (isActive) {
                val settledCollapsed = state.isCollapsed
                val settledExpanded = state.isExpanded
                if (!settledCollapsed && !settledExpanded) {
                    delay(50L)
                    continue
                }
                delay(
                    when {
                        aodModeEnabled -> 500L
                        settledCollapsed -> 500L
                        else -> 100L
                    },
                )
                val isTransitioning = playerConnection.player.currentMediaItem?.mediaId != mediaMetadata?.id
                val currentPlayerPosition = playerConnection.player.currentPosition
                val currentPlayerDuration = playerConnection.player.duration

                if (isTransitioning) {
                    val elapsedSinceStart = SystemClock.elapsedRealtime() - startTime
                    position = elapsedSinceStart
                    mediaMetadata?.let {
                        val metaDuration = it.duration.toLong() * 1000
                        duration = if (metaDuration > 0) metaDuration else 0L
                    }
                } else {
                    position = currentPlayerPosition
                    if (currentPlayerDuration > 0L && currentPlayerDuration != C.TIME_UNSET) {
                        duration = currentPlayerDuration
                    } else if (duration <= 0L || duration == C.TIME_UNSET) {
                        mediaMetadata?.let {
                            val metadataDuration = it.duration.toLong() * 1000
                            if (metadataDuration > 0L) duration = metadataDuration
                        }
                    }
                    if (!isUserSeeking) {
                        sliderPosition?.let { targetPosition ->
                            val clampedTargetPosition =
                                when {
                                    currentPlayerDuration > 0L && currentPlayerDuration != C.TIME_UNSET -> {
                                        targetPosition.coerceIn(0L, currentPlayerDuration)
                                    }

                                    else -> {
                                        targetPosition.coerceAtLeast(0L)
                                    }
                                }
                            if (abs(currentPlayerPosition - clampedTargetPosition) <= SeekbarSettleToleranceMs) {
                                sliderPosition = null
                            }
                        }
                    }
                }
            }
        } else {
            mediaMetadata?.let {
                val metaDuration = it.duration.toLong() * 1000
                duration = if (metaDuration > 0) metaDuration else 0L
            }
            val player = playerConnection.player
            val playerMatchesMetadata = player.currentMediaItem?.mediaId == mediaMetadata?.id
            val currentPlayerPosition = player.currentPosition
            if (sliderPosition == null && playerMatchesMetadata && currentPlayerPosition > 0L) {
                position = currentPlayerPosition
            }
        }
    }

    val dynamicQueuePeekHeight =
        if (
            playerDesignStyle == PlayerDesignStyle.V5 ||
            playerDesignStyle == PlayerDesignStyle.APPLE_MUSIC ||
            playerDesignStyle == PlayerDesignStyle.BITCHORD ||
            playerDesignStyle == PlayerDesignStyle.TIKTOK ||
            playerDesignStyle == PlayerDesignStyle.SIMPMUSIC ||
            playerDesignStyle == PlayerDesignStyle.SPATIALFLOW ||
            playerDesignStyle == PlayerDesignStyle.LOOPER
        ) {
            0.dp
        } else if (playerDesignStyle == PlayerDesignStyle.V9) {
            88.dp +
                (if (showCodecOnPlayer) 24.dp else 0.dp) +
                (if (sleepTimerEnabled) 42.dp else 0.dp)
        } else if (playerDesignStyle == PlayerDesignStyle.V10) {
            if (showCodecOnPlayer) 100.dp else 82.dp
        } else if (showCodecOnPlayer) {
            88.dp
        } else {
            QueuePeekHeight
        }

    val systemBarsBottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

    val dismissedBound = 0.dp
    val collapsedBound = dynamicQueuePeekHeight + systemBarsBottom

    val queueSheetState =
        rememberBottomSheetState(
            dismissedBound = dismissedBound,
            expandedBound = state.expandedBound,
            collapsedBound = collapsedBound,
            initialAnchor = COLLAPSED_ANCHOR,
        )

    LaunchedEffect(state.isExpandedOrExpanding) {
        if (state.isExpandedOrExpanding && !queueSheetState.isCollapsed) {
            queueSheetState.collapseSoft()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, queueSheetState) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && !queueSheetState.isCollapsed) {
                queueSheetState.collapseSoft()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var isInlineLyricsOpen by rememberSaveable {
        mutableStateOf(false)
    }

    var isAppleMusicInlineLyricsOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.isExpandedOrExpanding) {
        if (!state.isExpandedOrExpanding) {
            isInlineLyricsOpen = false

            isAppleMusicInlineLyricsOpen = false
        }
    }

    LaunchedEffect(playerConnection) {
        playerConnection.songEndedEvents.collect {
            if (isInlineLyricsOpen) isInlineLyricsOpen = false
            if (isAppleMusicInlineLyricsOpen) isAppleMusicInlineLyricsOpen = false
        }
    }

    val playerLyricsActive =
        (isInlineLyricsOpen || isAppleMusicInlineLyricsOpen) && state.isExpandedOrExpanding
    LaunchedEffect(playerLyricsActive) {
        onLyricsVisibilityChange(playerLyricsActive)
    }
    DisposableEffect(Unit) {
        onDispose { onLyricsVisibilityChange(false) }
    }

    val openQueue =
        remember(state, queueSheetState) {
            {
                isInlineLyricsOpen = false
                if (!state.isExpandedOrExpanding) {
                    state.expandSoft()
                }
                queueSheetState.expandSoft()
            }
        }

    val anyStylePageOverlayOpen by remember {
        derivedStateOf {
            isAppleMusicInlineLyricsOpen ||
                spatialFlowLyricsOpen ||
                spatialFlowQueueOpen ||
                simpmusicStylePageOpen ||
                tiktokStylePageOpen ||
                bitchordStylePageOpen
        }
    }

    if (!aodModeEnabled) {
        val rootOverlayActive = LocalRootOverlayActive.current
        BackHandler(
            enabled =
                (queueSheetState.isExpandedOrExpanding ||
                    state.isExpandedOrExpanding ||
                    bottomSheetPageState.isVisible) && !rootOverlayActive,
        ) {
            when {
                bottomSheetPageState.isVisible -> bottomSheetPageState.dismiss()
                isInlineLyricsOpen && state.isExpandedOrExpanding -> isInlineLyricsOpen = false
                anyStylePageOverlayOpen && state.isExpandedOrExpanding -> stylePageBackRequests.intValue++
                queueSheetState.isExpandedOrExpanding -> queueSheetState.collapseSoft()
                state.isExpandedOrExpanding -> state.collapseSoft()
            }
        }
    }

    if (backController != null) {
        val backRootOverlayActive = LocalRootOverlayActive.current
        val detailsPageVisible = bottomSheetPageState.isVisible
        val publishedBackTarget =
            when {
                backRootOverlayActive -> PlayerBackTarget.NONE
                aodModeEnabled -> PlayerBackTarget.NONE
                !state.isExpandedOrExpanding && !queueSheetState.isExpandedOrExpanding -> PlayerBackTarget.NONE
                queueSheetState.isExpandedOrExpanding -> PlayerBackTarget.SUB_PAGE
                detailsPageVisible -> PlayerBackTarget.SUB_PAGE
                isInlineLyricsOpen || isAppleMusicInlineLyricsOpen || anyStylePageOverlayOpen -> PlayerBackTarget.SUB_PAGE
                else -> PlayerBackTarget.SHEET
            }
        SideEffect {
            backController.backTarget = publishedBackTarget
            backController.routeBack = {
                when {
                    detailsPageVisible && bottomSheetPageState.isVisible -> bottomSheetPageState.dismiss()
                    isInlineLyricsOpen && state.isExpandedOrExpanding -> isInlineLyricsOpen = false
                    anyStylePageOverlayOpen && state.isExpandedOrExpanding -> stylePageBackRequests.intValue++
                    queueSheetState.isExpandedOrExpanding -> queueSheetState.collapseSoft()
                    state.isExpandedOrExpanding -> state.collapseSoft()
                }
            }
        }
    }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(state.isExpanded) {
        if (state.isExpanded) {
            focusRequester.requestFocus()
        }
        if (!state.isExpanded && aodModeEnabled) {
            playerConnection.aodModeEnabled.value = false
        }
    }

    val videoFullscreenHolder = LocalVideoFullscreenState.current
    val videoMediaId =
        mediaMetadata
            ?.takeIf { enableVideoPlayback && it.isMusicVideo == true && !it.id.isLocalMediaId() }
            ?.id

    var videoQualityStored by rememberPreference(VideoQualityPreferredHeightKey, VideoQualityPreference.HIGH_QUALITY)
    var videoPreferredHeight by remember(videoQualityStored) {
        mutableStateOf(VideoQualityPreference.toPreferredHeight(videoQualityStored))
    }
    var videoAvailableHeights by remember { mutableStateOf<List<Int>>(emptyList()) }

    var videoSelectedHeight by remember { mutableStateOf<Int?>(null) }
    var videoPlaybackFailed by remember { mutableStateOf(false) }

    LaunchedEffect(videoMediaId) {
        videoPlaybackFailed = false
    }

    val videoSheetSurfaceActive by remember(state) {
        derivedStateOf { state.progress > 0.5f || state.isExpandedOrExpanding }
    }

    val videoState =
        rememberVideoArtworkStateOrNull(
            videoId = videoMediaId,
            isPlaying = isPlaying,
            positionProvider = { playerConnection.player.currentPosition },
            preferredHeight = videoPreferredHeight,
            holdAudioUntilVideoReady = true,
            onStreamResolved = { info ->
                videoAvailableHeights = info?.availableHeights.orEmpty()
                videoSelectedHeight = info?.selectedHeight
            },
            onPlaybackFailed = { videoPlaybackFailed = true },
            onLoadingStateChange = {  },
            onRequestPauseMain = {
                if (videoMediaId != null && playerConnection.player.currentMediaItem?.mediaId == videoMediaId) {
                    playerConnection.player.pause()
                }
            },
            onRequestResumeMain = {
                if (videoMediaId != null && playerConnection.player.currentMediaItem?.mediaId == videoMediaId) {
                    playerConnection.player.play()
                }
            },
            isMainAudioBuffering = playbackState == STATE_BUFFERING,

            mainAudioReady = playbackState == STATE_READY,

            sheetSurfaceActive = videoSheetSurfaceActive,
        )

    CompositionLocalProvider(
        LocalVideoArtworkState provides videoState,
        LocalVideoPlaybackFailed provides videoPlaybackFailed,
        LocalVideoPreferredHeight provides videoPreferredHeight,
        LocalVideoOnPreferredHeightChange provides { videoQualityStored = VideoQualityPreference.toStoredQuality(it) },
        LocalVideoAvailableHeights provides videoAvailableHeights,
        LocalVideoSelectedHeight provides videoSelectedHeight,
    ) {
    Box(modifier = Modifier.fillMaxSize()) {
    val playerSheetCanvasVisible by remember(state) {
        derivedStateOf { state.progress > 0.5f || state.isExpandedOrExpanding }
    }
    CompositionLocalProvider(LocalPlayerSheetVisible provides playerSheetCanvasVisible) {
    val enrichedMetadata =
        remember(mediaMetadata, currentSong) {
            val meta = mediaMetadata ?: return@remember null
            if (meta.album != null) return@remember meta
            val dbAlbum = currentSong?.album
            val dbAlbumId = currentSong?.song?.albumId
            when {
                dbAlbum != null -> {
                    meta.copy(
                        album = MediaMetadata.Album(id = dbAlbum.id, title = dbAlbum.title),
                    )
                }

                dbAlbumId != null -> {
                    meta.copy(
                        album =
                            MediaMetadata.Album(
                                id = dbAlbumId,
                                title = currentSong?.song?.albumName.orEmpty(),
                            ),
                    )
                }

                else -> {
                    meta
                }
            }
        }

    BottomSheet(
        state = state,
        modifier =
            modifier
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type != KeyEventType.KeyDown || state.isCollapsed) return@onKeyEvent false

                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            val now = SystemClock.uptimeMillis()
                            if (incrementalSeekSkipEnabled && now - lastKeyboardTapTime < 1000) {
                                keyboardSkipMultiplier++
                            } else {
                                keyboardSkipMultiplier = 1
                            }
                            lastKeyboardTapTime = now
                            val skipAmount = 5000L * keyboardSkipMultiplier
                            playerConnection.player.seekTo((playerConnection.player.currentPosition - skipAmount).coerceAtLeast(0))
                            true
                        }

                        Key.DirectionRight -> {
                            val now = SystemClock.uptimeMillis()
                            if (incrementalSeekSkipEnabled && now - lastKeyboardTapTime < 1000) {
                                keyboardSkipMultiplier++
                            } else {
                                keyboardSkipMultiplier = 1
                            }
                            lastKeyboardTapTime = now
                            val skipAmount = 5000L * keyboardSkipMultiplier
                            playerConnection.player.seekTo(
                                (playerConnection.player.currentPosition + skipAmount).coerceAtMost(playerConnection.player.duration),
                            )
                            true
                        }

                        Key.DirectionUp -> {
                            deviceMusicVolumeController.setVolumeFraction(
                                (deviceMusicVolumeController.volumeFraction + 0.05f).coerceAtMost(1f),
                            )
                            true
                        }

                        Key.DirectionDown -> {
                            deviceMusicVolumeController.setVolumeFraction(
                                (deviceMusicVolumeController.volumeFraction - 0.05f).coerceAtLeast(0f),
                            )
                            true
                        }

                        Key.Spacebar -> {
                            playerConnection.player.togglePlayPause()
                            true
                        }

                        Key.N -> {
                            if (keyEvent.isShiftPressed) {
                                playerConnection.seekToNext()
                                true
                            } else {
                                false
                            }
                        }

                        Key.P -> {
                            if (keyEvent.isShiftPressed) {
                                playerConnection.seekToPrevious()
                                true
                            } else {
                                false
                            }
                        }

                        Key.L -> {
                            playerConnection.toggleLike()
                            true
                        }

                        else -> {
                            false
                        }
                    }
                },

        backgroundColor = {
            val progress =
                ((state.value - state.collapsedBound) / (state.expandedBound - state.collapsedBound))
                    .coerceIn(0f, 1f)
            val fadeProgress =
                if (progress < 0.2f) {
                    ((0.2f - progress) / 0.2f).coerceIn(0f, 1f)
                } else {
                    0f
                }
            when (playerDesignStyle) {
                PlayerDesignStyle.V9 -> dynamicBgColor.copy(alpha = 1f - fadeProgress)
                PlayerDesignStyle.V10 -> dynamicV10FieldColor.copy(alpha = 1f - fadeProgress)
                PlayerDesignStyle.V7 -> Color.Black.copy(alpha = 1f - fadeProgress)
                else ->
                    when (playerBackground) {
                        PlayerBackgroundStyle.BLUR, PlayerBackgroundStyle.GRADIENT ->
                            sheetSurfaceColor.copy(alpha = 1f - fadeProgress)

                        else ->
                            if (useBlackBackground) {
                                Color.Black.copy(alpha = 1f - fadeProgress)
                            } else {
                                sheetSurfaceColor.copy(alpha = 1f - fadeProgress)
                            }
                    }
            }
        },
        onDismiss = {
            playerConnection.service.stopAndClearPlayback(clearPersistentState = true)
        },
        backHandlerEnabled = !aodModeEnabled && !isInlineLyricsOpen && !anyStylePageOverlayOpen,
        keepContentAlive = true,
        morphMode = playerDesignStyle == PlayerDesignStyle.SPATIALFLOW,
        navbarHiddenOffset = navbarHiddenOffset,
        predictiveBackState = predictiveBackState,
        sharedLayer =
            if (playerDesignStyle == PlayerDesignStyle.SPATIALFLOW) {
                {
                    enrichedMetadata?.let { metadata ->
                        SpatialFlowFloatingArtwork(
                            state = state,
                            mediaMetadata = metadata,
                            queueWindows = queueWindows,
                            currentWindowIndex = currentWindowIndex,
                            artUrl = metadata.thumbnailUrl?.highRes(),
                            isPlaying = isPlaying,
                            fullArtworkRect = spatialFlowFullArtworkRect.value,
                            miniArtworkRect = spatialFlowMiniArtworkRect.value,
                            lyricsOpen = isInlineLyricsOpen || spatialFlowLyricsOpen,
                            queueOpen = spatialFlowQueueOpen,
                            artworkActive = spatialFlowPagerArtworkActive,
                            onPlaySongAtWindow = { windowIndex ->
                                val window = queueWindows.getOrNull(windowIndex) ?: return@SpatialFlowFloatingArtwork
                                playerConnection.player.seekToDefaultPosition(window.firstPeriodIndex)
                                playerConnection.player.playWhenReady = true
                            },
                        )
                    }
                }
            } else {
                null
            },
        collapsedContent = {
            MiniPlayer(
                positionProvider = positionProvider,
                durationProvider = durationProvider,
                pureBlack = pureBlack,
                isPairedWithNavigation = isMiniPlayerPairedWithNavigation,
                compactFractionState = compactFractionState,
                compactHorizontalPadding = compactHorizontalPadding,
                compactReserveEndControl = compactReserveEndControl,
                onArtworkSlotPositioned = { rect ->
                    if (playerDesignStyle == PlayerDesignStyle.SPATIALFLOW) {
                        spatialFlowMiniArtworkRect.value = rect
                    }
                },
            )
        },
    ) {
        val onSliderValueChange: (Long) -> Unit = {
            isUserSeeking = true
            sliderPosition = it
        }
        val onSliderValueChangeFinished: () -> Unit = {
            sliderPosition?.let {
                val isTransitioning = playerConnection.player.currentMediaItem?.mediaId != mediaMetadata?.id
                if (isTransitioning) {
                    playerConnection.player.seekToNext()
                    playerConnection.player.seekTo(it)
                } else {
                    playerConnection.player.seekTo(it)
                }
                position = it
                videoState?.requestResync(it, isPlaying)
            }
            isUserSeeking = false
        }
        val seekEnabled = duration > 0L && duration != C.TIME_UNSET
        val updatedOnSliderValueChange by rememberUpdatedState(onSliderValueChange)
        val updatedOnSliderValueChangeFinished by rememberUpdatedState(onSliderValueChangeFinished)

        val nextUpMetadata =
            remember(queueWindows, currentWindowIndex) {
                queueWindows.getOrNull(currentWindowIndex + 1)?.mediaItem?.metadata
            }

        val storefront =
            remember {
                val country = Locale.getDefault().country
                if (country.length == 2) country.lowercase(Locale.ROOT) else "us"
            }
        val trackIsMusicVideo = mediaMetadata?.isMusicVideo == true

        val (songCanvasDisabledRaw, _) = rememberPreference(SongCanvasDisabledKey, "")
        val songCanvasDisabledForCurrent =
            remember(songCanvasDisabledRaw, mediaMetadata?.id) {
                SongCanvasDisabled.isDisabled(songCanvasDisabledRaw.ifBlank { null }, mediaMetadata?.id.orEmpty())
            }
        val canvasOptionsEnabled = (archiveTuneCanvasEnabled || spotifyCanvasEnabled) && !songCanvasDisabledForCurrent
        val shouldUseV7Canvas =
            canvasOptionsEnabled &&
                (playerDesignStyle == PlayerDesignStyle.V7 ||
                    playerDesignStyle == PlayerDesignStyle.TIKTOK) &&
                !aodModeEnabled &&
                !trackIsMusicVideo
        val shouldUseArtworkCanvas =
            canvasOptionsEnabled &&
                (
                    playerDesignStyle == PlayerDesignStyle.APPLE_MUSIC ||
                        playerDesignStyle == PlayerDesignStyle.V9 ||
                        playerDesignStyle == PlayerDesignStyle.SPATIALFLOW ||
                        playerDesignStyle == PlayerDesignStyle.BITCHORD ||
                        playerDesignStyle == PlayerDesignStyle.LOOPER
                ) &&
                !aodModeEnabled &&
                !trackIsMusicVideo
        val shouldFetchV7Canvas = shouldUseV7Canvas && !lowDataModeActive
        val shouldFetchArtworkCanvas = shouldUseArtworkCanvas && !lowDataModeActive
        var v7CanvasArtwork by remember(mediaMetadata?.id) {
            mutableStateOf<CanvasArtwork?>(null)
        }
        var v7CanvasFetchInFlight by remember(mediaMetadata?.id) {
            mutableStateOf(false)
        }
        var artworkCanvas by remember(mediaMetadata?.id) {
            mutableStateOf<CanvasArtwork?>(null)
        }
        var artworkCanvasFetchInFlight by remember(mediaMetadata?.id) {
            mutableStateOf(false)
        }
        var canvasArtworkRevision by remember(mediaMetadata?.id) {
            mutableIntStateOf(0)
        }

        LaunchedEffect(nextUpMetadata?.id, shouldUseV7Canvas, shouldUseArtworkCanvas, lowDataModeActive) {
            val next = nextUpMetadata ?: return@LaunchedEffect
            val nextMediaId = next.id.trim().takeIf { it.isNotBlank() } ?: return@LaunchedEffect
            if (!shouldUseV7Canvas && !shouldUseArtworkCanvas) return@LaunchedEffect
            if (lowDataModeActive) return@LaunchedEffect

            if (CanvasArtworkPlaybackCache.hasEntry(nextMediaId)) return@LaunchedEffect
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                runCatching {
                    resolveCanvasArtworkForPlayback(
                        mediaId = nextMediaId,
                        songTitleRaw = next.title,
                        artistNameRaw = next.artists.firstOrNull()?.name.orEmpty(),
                        storefront = storefront,
                        requireVertical = shouldUseV7Canvas,
                        allowNetwork = true,
                        albumTitle = next.album?.title,
                        trySpotifyCanvas = spotifyCanvasEnabled,
                        spotifyTrackId = next.spotifyTrackId,
                    )
                }
            }
        }

        LaunchedEffect(queueWindows, currentWindowIndex, lowDataModeActive) {
            if (lowDataModeActive) return@LaunchedEffect
            val upcoming = queueWindows.drop(currentWindowIndex + 1).take(3)
            if (upcoming.isEmpty()) return@LaunchedEffect
            val loader = context.imageLoader
            for (window in upcoming) {
                val url = window.mediaItem.metadata?.thumbnailUrl?.trim()?.takeIf(String::isNotBlank) ?: continue
                val hiRes = url.highRes()
                val candidates = if (hiRes == url) listOf(url) else listOf(url, hiRes)
                for (candidate in candidates) {
                    val request =
                        ImageRequest
                            .Builder(context)
                            .data(candidate)
                            .memoryCacheKey(candidate)
                            .diskCacheKey(candidate)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .networkCachePolicy(CachePolicy.ENABLED)
                            .build()
                    runCatching { loader.enqueue(request) }
                }
            }
        }

        LaunchedEffect(playerConnection, mediaMetadata?.id, shouldUseV7Canvas, shouldUseArtworkCanvas) {
            playerConnection.canvasArtworkUpdates.collect { update ->
                if (update.mediaId != mediaMetadata?.id) return@collect
                if (!shouldUseV7Canvas && !shouldUseArtworkCanvas) return@collect

                canvasArtworkRevision += 1
                if (!update.artwork.preferredVerticalAnimationUrl.isNullOrBlank()) {
                    v7CanvasArtwork = update.artwork
                }
                if (!update.artwork.preferredAnimationUrl.isNullOrBlank()) {
                    artworkCanvas = update.artwork
                }
            }
        }

        LaunchedEffect(shouldUseV7Canvas, shouldFetchV7Canvas, mediaMetadata?.id) {
            val metadata = mediaMetadata
            if (!shouldUseV7Canvas || metadata == null) {
                v7CanvasArtwork = null
                v7CanvasFetchInFlight = false
                return@LaunchedEffect
            }

            val artistNameRaw =
                metadata.artists
                    .firstOrNull()
                    ?.name
                    .orEmpty()
            if (v7CanvasFetchInFlight) {
                return@LaunchedEffect
            }

            v7CanvasFetchInFlight = true
            try {
                val requestRevision = canvasArtworkRevision
                val resolvedArtwork =
                    resolveCanvasArtworkForPlayback(
                        mediaId = metadata.id,
                        songTitleRaw = metadata.title,
                        artistNameRaw = artistNameRaw,
                        storefront = storefront,
                        requireVertical = true,
                        allowNetwork = shouldFetchV7Canvas,
                        albumTitle = metadata.album?.title,
                        trySpotifyCanvas = spotifyCanvasEnabled,
                        spotifyTrackId = metadata.spotifyTrackId,
                    )
                if (requestRevision == canvasArtworkRevision) {
                    v7CanvasArtwork = resolvedArtwork
                }
            } finally {
                v7CanvasFetchInFlight = false
            }
        }

        LaunchedEffect(shouldUseArtworkCanvas, shouldFetchArtworkCanvas, mediaMetadata?.id) {
            val metadata = mediaMetadata
            if (!shouldUseArtworkCanvas || metadata == null) {
                artworkCanvas = null
                artworkCanvasFetchInFlight = false
                return@LaunchedEffect
            }

            val artistNameRaw =
                metadata.artists
                    .firstOrNull()
                    ?.name
                    .orEmpty()
            if (artworkCanvasFetchInFlight) {
                return@LaunchedEffect
            }

            artworkCanvasFetchInFlight = true
            try {
                val requestRevision = canvasArtworkRevision
                val resolvedArtwork =
                    resolveCanvasArtworkForPlayback(
                        mediaId = metadata.id,
                        songTitleRaw = metadata.title,
                        artistNameRaw = artistNameRaw,
                        storefront = storefront,
                        requireVertical = false,
                        allowNetwork = shouldFetchArtworkCanvas,
                        albumTitle = metadata.album?.title,
                        trySpotifyCanvas = spotifyCanvasEnabled,
                        spotifyTrackId = metadata.spotifyTrackId,
                    )
                if (requestRevision == canvasArtworkRevision) {
                    artworkCanvas = resolvedArtwork
                }
            } finally {
                artworkCanvasFetchInFlight = false
            }
        }

        val controlsContent: @Composable ColumnScope.(MediaMetadata) -> Unit = { mediaMetadata ->
            PlayerControlsContent(
                mediaMetadata = mediaMetadata,
                playerDesignStyle = playerDesignStyle,
                sliderStyle = sliderStyle,
                playbackState = playbackState,
                isPlaying = isPlaying,
                isLoading = isLoading,
                repeatMode = repeatMode,
                canSkipPrevious = canSkipPrevious,
                canSkipNext = canSkipNext,
                textButtonColor = textButtonColor,
                iconButtonColor = iconButtonColor,
                textBackgroundColor = TextBackgroundColor,
                icBackgroundColor = icBackgroundColor,
                sliderPosition = sliderPosition,
                position = position,
                duration = duration,
                playerConnection = playerConnection,
                navController = navController,
                state = state,
                context = context,
                onSliderValueChange = onSliderValueChange,
                onSliderValueChangeFinished = onSliderValueChangeFinished,
                currentFormat = if (playerDesignStyle == PlayerDesignStyle.V7) currentFormat else null,
            )
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize(),
        ) {
        if (!state.isCollapsed &&
            !aodModeEnabled &&
            playerDesignStyle != PlayerDesignStyle.V5 &&
            playerDesignStyle != PlayerDesignStyle.V7 &&
            playerDesignStyle != PlayerDesignStyle.V9 &&
            playerDesignStyle != PlayerDesignStyle.V10 &&
            playerDesignStyle != PlayerDesignStyle.APPLE_MUSIC &&
            playerDesignStyle != PlayerDesignStyle.BITCHORD &&
            playerDesignStyle != PlayerDesignStyle.TIKTOK &&
            playerDesignStyle != PlayerDesignStyle.SIMPMUSIC &&
            playerDesignStyle != PlayerDesignStyle.SPATIALFLOW &&
            playerDesignStyle != PlayerDesignStyle.LOOPER
        ) {
            PlayerBackground(
                playerBackground = playerBackground,
                mediaMetadata = mediaMetadata,
                gradientColors = gradientColors,
                disableBlur = disableBlur,
                blurRadius = blurRadius,
                playerCustomImageUri = playerCustomImageUri,
                playerCustomBlur = playerCustomBlur,
                playerCustomContrast = playerCustomContrast,
                playerCustomBrightness = playerCustomBrightness,
            )
        }

        val currentOrientation = LocalConfiguration.current.orientation
        var orientationRefreshEpoch by remember { mutableIntStateOf(0) }
        LaunchedEffect(currentOrientation) {
            orientationRefreshEpoch += 1
        }

        when (LocalConfiguration.current.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                if (playerDesignStyle == PlayerDesignStyle.BITCHORD) {
                    enrichedMetadata?.let { metadata ->
                        BitChordPlayerContent(
                            mediaMetadata = metadata,
                            isPlaying = isPlaying,
                            isLoading = isLoading,
                            canSkipPrevious = canSkipPrevious,
                            canSkipNext = canSkipNext,
                            positionProvider = positionProvider,
                            duration = duration,
                            playerConnection = playerConnection,
                            navController = navController,
                            state = state,
                            menuState = menuState,
                            bottomSheetPageState = bottomSheetPageState,
                            currentFormat = currentFormat,
                            canvasPrimaryUrl = artworkCanvas?.animated,
                            canvasFallbackUrl = artworkCanvas?.videoUrl,
                            externalBackRequests = stylePageBackRequests,
                            onStylePageOpenChange = { bitchordStylePageOpen = it },
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .nestedScroll(state.preUpPostDownNestedScrollConnection),
                        )
                    }
                } else if (playerDesignStyle == PlayerDesignStyle.TIKTOK) {
                    enrichedMetadata?.let { metadata ->
                        TikTokPlayerContent(
                            mediaMetadata = metadata,
                            isPlaying = isPlaying,
                            isLoading = isLoading,
                            canSkipPrevious = canSkipPrevious,
                            canSkipNext = canSkipNext,
                            sliderPosition = sliderPosition,
                            position = position,
                            duration = duration,
                            playerConnection = playerConnection,
                            navController = navController,
                            state = state,
                            menuState = menuState,
                            bottomSheetPageState = bottomSheetPageState,
                            lyricsSyncOffset = lyricsSyncOffset,
                            onLyricsSyncOffsetChange = { lyricsSyncOffset = it },

                            canvasPrimaryUrl = v7CanvasArtwork?.animatedVertical,
                            canvasFallbackUrl = v7CanvasArtwork?.videoUrlVertical,
                            externalBackRequests = stylePageBackRequests,
                            onStylePageOpenChange = { tiktokStylePageOpen = it },
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .nestedScroll(state.preUpPostDownNestedScrollConnection),

                            onSeek = onSliderValueChange,
                            onSeekFinished = onSliderValueChangeFinished,
                        )
                    }
                } else if (playerDesignStyle == PlayerDesignStyle.V5) {
                    val littleBackground = MaterialTheme.colorScheme.primaryContainer
                    val littleTextColor = MaterialTheme.colorScheme.onPrimaryContainer
                    val displayPositionMs = sliderPosition ?: position
                    val progressFraction =
                        remember(displayPositionMs, duration) {
                            if (duration <= 0L || duration == C.TIME_UNSET) {
                                0f
                            } else {
                                (displayPositionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                            }
                        }
                    val progressOverlayColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)

                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(littleBackground),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(progressFraction)
                                    .align(Alignment.TopStart)
                                    .background(progressOverlayColor),
                        )
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .littlePlayerOverlayGestures(
                                        seekEnabled = seekEnabled,
                                        durationMs = duration,
                                        progressFraction = progressFraction,
                                        canSkipPrevious = canSkipPrevious,
                                        canSkipNext = canSkipNext,
                                        onSeekToPositionMs = updatedOnSliderValueChange,
                                        onSeekFinished = updatedOnSliderValueChangeFinished,
                                        onSkipPrevious = playerConnection::seekToPrevious,
                                        onSkipNext = playerConnection::seekToNext,
                                    ).windowInsetsPadding(
                                        WindowInsets(top = LocalStableSystemBarsTopPadding.current)
                                            .union(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
                                    ),
                        ) {
                            enrichedMetadata?.let { metadata ->
                                LittlePlayerContent(
                                    mediaMetadata = metadata,
                                    sliderPosition = sliderPosition,
                                    positionMs = position,
                                    durationMs = duration,
                                    textColor = littleTextColor,
                                    liked = currentSongLiked,
                                    onCollapse = state::collapseSoft,
                                    onToggleLike = playerConnection::toggleLike,
                                    onExpandQueue = openQueue,
                                    onMenuClick = {
                                        menuState.show {
                                            PlayerMenu(
                                                mediaMetadata = metadata,
                                                navController = navController,
                                                playerBottomSheetState = state,
                                                onShowDetailsDialog = {
                                                    bottomSheetPageState.show {
                                                        ShowMediaInfo(metadata.id)
                                                    }
                                                },
                                                onDismiss = menuState::dismiss,
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                } else if (playerDesignStyle == PlayerDesignStyle.V7) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize(),
                    ) {
                        val v7SwapState =
                            rememberThumbnailSwapState(
                                videoId = mediaMetadata?.id,
                                ytmUrl = mediaMetadata?.thumbnailUrl,
                                lowDataMode = lowDataModeActive,
                                isMusicVideo = mediaMetadata?.isMusicVideo ?: false,
                            )
                        val v7VideoMetadata = mediaMetadata
                        val v7VideoShowing =
                            videoState != null &&
                                v7VideoMetadata?.isMusicVideo == true &&
                                !v7VideoMetadata.id.isLocalMediaId() &&
                                !aodModeEnabled &&
                                !isInlineLyricsOpen &&
                                !videoPlaybackFailed

                        if (v7VideoShowing) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(Color.Black),
                            )
                        } else {
                            V7PlayerBackdrop(
                                thumbnailUrl = v7SwapState.displayUrl,
                                canvasStaticUrl = v7CanvasArtwork?.static,
                                canvasPrimaryUrl = v7CanvasArtwork?.animatedVertical,
                                canvasFallbackUrl = v7CanvasArtwork?.videoUrlVertical,
                                isPlaying = isPlaying && !isInlineLyricsOpen,
                                disableBlur = disableBlur,
                                backdropBlurAmount = backdropBlurAmount,
                                label = "v7BackdropLandscape",
                                playCanvasInBackdrop = v7CanvasArtwork?.isSpotifyProviderCanvas() == true,
                            )
                        }

                        if (v7VideoShowing && v7VideoMetadata != null) {
                            InlineVideoPlayer(
                                state = videoState,
                                preferredHeight = videoPreferredHeight,
                                onPreferredHeightChange = { videoQualityStored = VideoQualityPreference.toStoredQuality(it) },
                                availableHeights = videoAvailableHeights,
                                selectedHeight = videoSelectedHeight,
                                controlsOnTap = true,
                                modifier =
                                    Modifier
                                        .align(Alignment.Center)
                                        .padding(bottom = 180.dp)
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(16.dp)),
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier =
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = queueSheetState.collapsedBound)
                                    .windowInsetsPadding(
                                        WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                                    ).nestedScroll(state.preUpPostDownNestedScrollConnection),
                        ) {
                            enrichedMetadata?.let { metadata ->
                                V8PlayerControlsContent(
                                    mediaMetadata = metadata,
                                    queueTitle = "",
                                    playbackState = playbackState,
                                    isPlaying = isPlaying,
                                    isLoading = isLoading,
                                    canSkipPrevious = canSkipPrevious,
                                    canSkipNext = canSkipNext,
                                    currentSongLiked = currentSongLiked,
                                    sliderPosition = sliderPosition,
                                    position = position,
                                    duration = duration,
                                    volume = deviceMusicVolumeController.volumeFraction,
                                    showVolumeBar = showPlayerVolumeBar,
                                    currentFormat = currentFormat,
                                    playerConnection = playerConnection,
                                    navController = navController,
                                    state = state,
                                    onSliderValueChange = onSliderValueChange,
                                    onSliderValueChangeFinished = onSliderValueChangeFinished,
                                    onVolumeChange = onPlayerVolumeChange,
                                    landscape = true,
                                )
                            }

                            Spacer(Modifier.height(16.dp))
                        }
                    }
} else if (playerDesignStyle == PlayerDesignStyle.V9) {
                    enrichedMetadata?.let { metadata ->
                        V9PlayerContent(
                            mediaMetadata = metadata,
                            playbackState = playbackState,
                            isPlaying = isPlaying,
                            isLoading = isLoading,
                            canSkipPrevious = canSkipPrevious,
                            canSkipNext = canSkipNext,
                            sliderPosition = sliderPosition,
                            position = position,
                            duration = duration,
                            playerConnection = playerConnection,
                            navController = navController,
                            state = state,
                            textBackgroundColor = TextBackgroundColor,
                            textButtonColor = textButtonColor,
                            iconButtonColor = iconButtonColor,
                            canvasSource = artworkCanvas?.inferredProvider(),
                            canvasPrimaryUrl = artworkCanvas?.animated,
                            canvasFallbackUrl = artworkCanvas?.videoUrl,
                            gradientColors = gradientColors,
                            onCollapseClick = { state.collapseSoft() },
                            onQueueClick = openQueue,
                            onLyricsClick = { isInlineLyricsOpen = !isInlineLyricsOpen },
                            lyricsOpen = isInlineLyricsOpen,
                            onCloseLyrics = { isInlineLyricsOpen = false },
                            lyricsSyncOffset = lyricsSyncOffset,
                            onLyricsSyncOffsetChange = { lyricsSyncOffset = it },
                            onSliderValueChange = onSliderValueChange,
                            onSliderValueChangeFinished = onSliderValueChangeFinished,
                            landscape = true,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(bottom = queueSheetState.collapsedBound)
                                    .windowInsetsPadding(
                                        WindowInsets(top = LocalStableSystemBarsTopPadding.current)
                                            .union(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
                                    ).nestedScroll(state.preUpPostDownNestedScrollConnection),
                        )
                    }
                } else if (playerDesignStyle == PlayerDesignStyle.V10) {
                    enrichedMetadata?.let { metadata ->
                        V10PlayerContent(
                            mediaMetadata = metadata,
                            playbackState = playbackState,
                            isPlaying = isPlaying,
                            isLoading = isLoading,
                            sliderPosition = sliderPosition,
                            position = position,
                            duration = duration,
                            playerConnection = playerConnection,
                            navController = navController,
                            state = state,
                            textBackgroundColor = dynamicV10AccentColor,
                            textButtonColor = dynamicV10FieldColor,
                            onCollapseClick = { state.collapseSoft() },
                            onLyricsClick = { isInlineLyricsOpen = !isInlineLyricsOpen },
                            lyricsOpen = isInlineLyricsOpen,
                            onCloseLyrics = { isInlineLyricsOpen = false },
                            lyricsSyncOffset = lyricsSyncOffset,
                            onLyricsSyncOffsetChange = { lyricsSyncOffset = it },
                            onSliderValueChange = onSliderValueChange,
                            onSliderValueChangeFinished = onSliderValueChangeFinished,
                            onSleepTimerClick = {
                                if (sleepTimerTimeLeft > 0L) {
                                    playerConnection.service.sleepTimer.clear()
                                } else {
                                    showSleepTimerDialog = true
                                }
                            },
                            sleepTimerEnabled = sleepTimerTimeLeft > 0L,
                            sleepTimerTimeLeft = sleepTimerTimeLeft,
                            onMenuClick = {
                                menuState.show {
                                    PlayerMenu(
                                        mediaMetadata = metadata,
                                        navController = navController,
                                        playerBottomSheetState = state,
                                        onShowDetailsDialog = {
                                            bottomSheetPageState.show {
                                                ShowMediaInfo(metadata.id)
                                            }
                                        },
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                            landscape = true,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(bottom = queueSheetState.collapsedBound)
                                    .windowInsetsPadding(
                                        WindowInsets(top = LocalStableSystemBarsTopPadding.current)
                                            .union(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
                                    ).nestedScroll(state.preUpPostDownNestedScrollConnection),
                        )
                    }
} else if (playerDesignStyle == PlayerDesignStyle.SPATIALFLOW) {
                    enrichedMetadata?.let { metadata ->
                        SpatialFlowPlayerContent(
                            mediaMetadata = metadata,
                            isPlaying = isPlaying,
                            isLoading = isLoading,
                            canSkipPrevious = canSkipPrevious,
                            canSkipNext = canSkipNext,
                            duration = duration,
                            playerConnection = playerConnection,
                            navController = navController,
                            state = state,
                            menuState = menuState,
                            bottomSheetPageState = bottomSheetPageState,
                            currentFormat = currentFormat,
                            positionProvider = positionProvider,
                            canvasPrimaryUrl = artworkCanvas?.animated,
                            canvasFallbackUrl = artworkCanvas?.videoUrl,
                            appIsDark = useDarkTheme,
                            onSeek = onSliderValueChange,
                            onSeekFinished = onSliderValueChangeFinished,
                            floatingArtwork = true,
                            onArtworkSlotPositioned = { rect ->
                                spatialFlowFullArtworkRect.value = rect
                            },
                            onPagerArtworkActiveChange = { active ->
                                spatialFlowPagerArtworkActive = active
                            },
                            onLyricsOpenChange = { open ->
                                spatialFlowLyricsOpen = open
                            },
                            onQueueExpandedChange = { expanded ->
                                spatialFlowQueueOpen = expanded
                            },
                            externalBackRequests = stylePageBackRequests,
                            modifier =
                                Modifier
                                    .fillMaxSize()

                                    .windowInsetsPadding(
                                        WindowInsets.systemBars.only(WindowInsetsSides.Horizontal),
                                    ).nestedScroll(state.preUpPostDownNestedScrollConnection),
                        )
                    }
} else if (playerDesignStyle == PlayerDesignStyle.LOOPER) {
                    enrichedMetadata?.let { metadata ->
                        LooperPlayerContent(
                            mediaMetadata = metadata,
                            isPlaying = isPlaying,
                            isLoading = isLoading,
                            canSkipPrevious = canSkipPrevious,
                            canSkipNext = canSkipNext,
                            sliderPosition = sliderPosition,
                            position = position,
                            duration = duration,
                            playerConnection = playerConnection,
                            navController = navController,
                            state = state,
                            menuState = menuState,
                            bottomSheetPageState = bottomSheetPageState,
                            currentFormat = currentFormat,
                            canvasPrimaryUrl = artworkCanvas?.animated,
                            canvasFallbackUrl = artworkCanvas?.videoUrl,
                            onSeek = onSliderValueChange,
                            onSeekFinished = onSliderValueChangeFinished,
                            onLyricsClick = { isInlineLyricsOpen = !isInlineLyricsOpen },
                            onQueueClick = openQueue,
                            lyricsVisible = isInlineLyricsOpen,
                            modifier =
                                Modifier
                                    .fillMaxSize()

                                    .windowInsetsPadding(
                                        WindowInsets.systemBars.only(WindowInsetsSides.Horizontal),
                                    ).nestedScroll(state.preUpPostDownNestedScrollConnection),
                        )
                    }
} else if (playerDesignStyle == PlayerDesignStyle.SIMPMUSIC) {
                    enrichedMetadata?.let { metadata ->
                        SimpMusicPlayerContent(
                            mediaMetadata = metadata,
                            isPlaying = isPlaying,
                            isLoading = isLoading,
                            canSkipPrevious = canSkipPrevious,
                            canSkipNext = canSkipNext,
                            sliderPosition = sliderPosition,
                            position = position,
                            duration = duration,
                            playerConnection = playerConnection,
                            navController = navController,
                            state = state,
                            menuState = menuState,
                            bottomSheetPageState = bottomSheetPageState,
                            currentFormat = currentFormat,
                            onSeek = onSliderValueChange,
                            onSeekFinished = onSliderValueChangeFinished,
                            externalBackRequests = stylePageBackRequests,
                            onStylePageOpenChange = { simpmusicStylePageOpen = it },
                            modifier =
                                Modifier
                                    .fillMaxSize()

                                    .windowInsetsPadding(
                                        WindowInsets.systemBars.only(WindowInsetsSides.Horizontal),
                                    ).nestedScroll(state.preUpPostDownNestedScrollConnection),
                        )
                    }
                } else if (playerDesignStyle == PlayerDesignStyle.APPLE_MUSIC) {
                    enrichedMetadata?.let { metadata ->
                        FlamingoPlayerContent(
                            mediaMetadata = metadata,
                            playbackState = playbackState,
                            isPlaying = isPlaying,
                            isLoading = isLoading,
                            canSkipPrevious = canSkipPrevious,
                            canSkipNext = canSkipNext,
                            sliderPosition = sliderPosition,
                            positionProvider = positionProvider,
                            duration = duration,
                            playerConnection = playerConnection,
                            navController = navController,
                            state = state,
                            bottomSheetPageState = bottomSheetPageState,
                            currentSongLiked = currentSongLiked,
                            volume = deviceMusicVolumeController.volumeFraction,
                            onVolumeChange = onPlayerVolumeChange,
                            canvasPrimaryUrl = artworkCanvas?.animated,
                            canvasFallbackUrl = artworkCanvas?.videoUrl,
                            currentFormat = currentFormat,
                            contentBottomPadding = queueSheetState.collapsedBound + 20.dp,
                            onSliderValueChange = onSliderValueChange,
                            onSliderValueChangeFinished = onSliderValueChangeFinished,
                            lyricsSyncOffset = lyricsSyncOffset,
                            onLyricsSyncOffsetChange = { lyricsSyncOffset = it },
                            onLyricsVisibilityChange = { isAppleMusicInlineLyricsOpen = it },
                            externalBackRequests = stylePageBackRequests,
                            landscape = true,
                            orientationRefreshEpoch = orientationRefreshEpoch,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .nestedScroll(state.preUpPostDownNestedScrollConnection),
                        )
                    }
            } else {
                    Row(
                        modifier =
                            Modifier
                                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                                .padding(bottom = queueSheetState.collapsedBound + 48.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.weight(1f),
                        ) {
                            val screenWidth = LocalConfiguration.current.screenWidthDp
                            val thumbnailSize = (screenWidth * 0.4).dp
                            androidx.compose.animation.AnimatedVisibility(
                                visible = !isInlineLyricsOpen,
                                enter = fadeIn(tween(300, easing = FastOutSlowInEasing)),
                                exit = fadeOut(tween(200, easing = FastOutSlowInEasing)),
                            ) {
                                Thumbnail(
                                    sliderPositionProvider = { sliderPosition },
                                    modifier = Modifier.size(thumbnailSize),
                                    isPlayerExpanded = state.isExpanded,
                                    onOverflowClick = {
                                        enrichedMetadata?.let { metadata ->
                                            menuState.show {
                                                PlayerMenu(
                                                    mediaMetadata = metadata,
                                                    navController = navController,
                                                    playerBottomSheetState = state,
                                                    onShowDetailsDialog = {
                                                        bottomSheetPageState.show {
                                                            ShowMediaInfo(metadata.id)
                                                        }
                                                    },
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        }
                                    },
                                )
                            }
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .windowInsetsPadding(WindowInsets(top = LocalStableSystemBarsTopPadding.current)),
                        ) {
                            Spacer(Modifier.weight(1f))

                            enrichedMetadata?.let {
                                controlsContent(it)
                            }

                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            else -> {
                if (playerDesignStyle == PlayerDesignStyle.BITCHORD) {
                    enrichedMetadata?.let { metadata ->
                        BitChordPlayerContent(
                            mediaMetadata = metadata,
                            isPlaying = isPlaying,
                            isLoading = isLoading,
                            canSkipPrevious = canSkipPrevious,
                            canSkipNext = canSkipNext,
                            positionProvider = positionProvider,
                            duration = duration,
                            playerConnection = playerConnection,
                            navController = navController,
                            state = state,
                            menuState = menuState,
                            bottomSheetPageState = bottomSheetPageState,
                            currentFormat = currentFormat,
                            canvasPrimaryUrl = artworkCanvas?.animated,
                            canvasFallbackUrl = artworkCanvas?.videoUrl,
                            externalBackRequests = stylePageBackRequests,
                            onStylePageOpenChange = { bitchordStylePageOpen = it },
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .nestedScroll(state.preUpPostDownNestedScrollConnection),
                        )
                    }
                } else if (playerDesignStyle == PlayerDesignStyle.TIKTOK) {
                    enrichedMetadata?.let { metadata ->
                        TikTokPlayerContent(
                            mediaMetadata = metadata,
                            isPlaying = isPlaying,
                            isLoading = isLoading,
                            canSkipPrevious = canSkipPrevious,
                            canSkipNext = canSkipNext,
                            sliderPosition = sliderPosition,
                            position = position,
                            duration = duration,
                            playerConnection = playerConnection,
                            navController = navController,
                            state = state,
                            menuState = menuState,
                            bottomSheetPageState = bottomSheetPageState,
                            lyricsSyncOffset = lyricsSyncOffset,
                            onLyricsSyncOffsetChange = { lyricsSyncOffset = it },

                            canvasPrimaryUrl = v7CanvasArtwork?.animatedVertical,
                            canvasFallbackUrl = v7CanvasArtwork?.videoUrlVertical,
                            externalBackRequests = stylePageBackRequests,
                            onStylePageOpenChange = { tiktokStylePageOpen = it },
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .nestedScroll(state.preUpPostDownNestedScrollConnection),

                            onSeek = onSliderValueChange,
                            onSeekFinished = onSliderValueChangeFinished,
                        )
                    }
                } else if (playerDesignStyle == PlayerDesignStyle.V5) {
                    val littleBackground = MaterialTheme.colorScheme.primaryContainer
                    val littleTextColor = MaterialTheme.colorScheme.onPrimaryContainer
                    val displayPositionMs = sliderPosition ?: position
                    val progressFraction =
                        remember(displayPositionMs, duration) {
                            if (duration <= 0L || duration == C.TIME_UNSET) {
                                0f
                            } else {
                                (displayPositionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                            }
                        }
                    val progressOverlayColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                    val seekEnabled = duration > 0L && duration != C.TIME_UNSET

                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(littleBackground),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(progressFraction)
                                    .align(Alignment.TopStart)
                                    .background(progressOverlayColor),
                        )
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .littlePlayerOverlayGestures(
                                        seekEnabled = seekEnabled,
                                        durationMs = duration,
                                        progressFraction = progressFraction,
                                        canSkipPrevious = canSkipPrevious,
                                        canSkipNext = canSkipNext,
                                        onSeekToPositionMs = updatedOnSliderValueChange,
                                        onSeekFinished = updatedOnSliderValueChangeFinished,
                                        onSkipPrevious = playerConnection::seekToPrevious,
                                        onSkipNext = playerConnection::seekToNext,
                                    ).windowInsetsPadding(
                                        WindowInsets(top = LocalStableSystemBarsTopPadding.current)
                                            .union(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
                                    ),
                        ) {
                            enrichedMetadata?.let { metadata ->
                                LandscapeLikeBox(modifier = Modifier.fillMaxSize()) {
                                    LittlePlayerContent(
                                        mediaMetadata = metadata,
                                        sliderPosition = sliderPosition,
                                        positionMs = position,
                                        durationMs = duration,
                                        textColor = littleTextColor,
                                        liked = currentSongLiked,
                                        onCollapse = state::collapseSoft,
                                        onToggleLike = playerConnection::toggleLike,
                                        onExpandQueue = openQueue,
                                        onMenuClick = {
                                            menuState.show {
                                                PlayerMenu(
                                                    mediaMetadata = metadata,
                                                    navController = navController,
                                                    playerBottomSheetState = state,
                                                    onShowDetailsDialog = {
                                                        bottomSheetPageState.show {
                                                            ShowMediaInfo(metadata.id)
                                                        }
                                                    },
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                } else if (playerDesignStyle == PlayerDesignStyle.V7) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize(),
                    ) {
                        val v7SwapState =
                            rememberThumbnailSwapState(
                                videoId = mediaMetadata?.id,
                                ytmUrl = mediaMetadata?.thumbnailUrl,
                                lowDataMode = lowDataModeActive,
                                isMusicVideo = mediaMetadata?.isMusicVideo ?: false,
                            )
                        val v7VideoMetadata = mediaMetadata
                        val v7VideoShowing =
                            videoState != null &&
                                v7VideoMetadata?.isMusicVideo == true &&
                                !v7VideoMetadata.id.isLocalMediaId() &&
                                !aodModeEnabled &&
                                !isInlineLyricsOpen &&
                                !videoPlaybackFailed

                        if (v7VideoShowing) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(Color.Black),
                            )
                        } else {
                            V7PlayerBackdrop(
                                thumbnailUrl = v7SwapState.displayUrl,
                                canvasStaticUrl = v7CanvasArtwork?.static,
                                canvasPrimaryUrl = v7CanvasArtwork?.animatedVertical,
                                canvasFallbackUrl = v7CanvasArtwork?.videoUrlVertical,
                                isPlaying = isPlaying && !isInlineLyricsOpen,
                                disableBlur = disableBlur,
                                backdropBlurAmount = backdropBlurAmount,
                                label = "v7BackdropPortrait",
                                playCanvasInBackdrop = v7CanvasArtwork?.isSpotifyProviderCanvas() == true,
                            )
                        }

                        if (v7VideoShowing && v7VideoMetadata != null) {
                            InlineVideoPlayer(
                                state = videoState,
                                preferredHeight = videoPreferredHeight,
                                onPreferredHeightChange = { videoQualityStored = VideoQualityPreference.toStoredQuality(it) },
                                availableHeights = videoAvailableHeights,
                                selectedHeight = videoSelectedHeight,
                                controlsOnTap = true,
                                modifier =
                                    Modifier
                                        .align(Alignment.Center)
                                        .padding(bottom = 180.dp)
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(16.dp)),
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier =
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = queueSheetState.collapsedBound)
                                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                                    .nestedScroll(state.preUpPostDownNestedScrollConnection),
                        ) {
                            enrichedMetadata?.let { metadata ->
                                V8PlayerControlsContent(
                                    mediaMetadata = metadata,
                                    queueTitle = "",
                                    playbackState = playbackState,
                                    isPlaying = isPlaying,
                                    isLoading = isLoading,
                                    canSkipPrevious = canSkipPrevious,
                                    canSkipNext = canSkipNext,
                                    currentSongLiked = currentSongLiked,
                                    sliderPosition = sliderPosition,
                                    position = position,
                                    duration = duration,
                                    volume = deviceMusicVolumeController.volumeFraction,
                                    showVolumeBar = showPlayerVolumeBar,
                                    currentFormat = currentFormat,
                                    playerConnection = playerConnection,
                                    navController = navController,
                                    state = state,
                                    onSliderValueChange = onSliderValueChange,
                                    onSliderValueChangeFinished = onSliderValueChangeFinished,
                                    onVolumeChange = onPlayerVolumeChange,
                                )
                            }

                            Spacer(Modifier.height(24.dp))
                        }
                    }
} else if (playerDesignStyle == PlayerDesignStyle.V9) {
                    enrichedMetadata?.let { metadata ->
                        V9PlayerContent(
                            mediaMetadata = metadata,
                            playbackState = playbackState,
                            isPlaying = isPlaying,
                            isLoading = isLoading,
                            canSkipPrevious = canSkipPrevious,
                            canSkipNext = canSkipNext,
                            sliderPosition = sliderPosition,
                            position = position,
                            duration = duration,
                            playerConnection = playerConnection,
                            navController = navController,
                            state = state,
                            textBackgroundColor = TextBackgroundColor,
                            textButtonColor = textButtonColor,
                            iconButtonColor = iconButtonColor,
                            canvasSource = artworkCanvas?.inferredProvider(),
                            canvasPrimaryUrl = artworkCanvas?.animated,
                            canvasFallbackUrl = artworkCanvas?.videoUrl,
                            gradientColors = gradientColors,
                            onCollapseClick = { state.collapseSoft() },
                            onQueueClick = openQueue,
                            onLyricsClick = { isInlineLyricsOpen = !isInlineLyricsOpen },
                            lyricsOpen = isInlineLyricsOpen,
                            onCloseLyrics = { isInlineLyricsOpen = false },
                            lyricsSyncOffset = lyricsSyncOffset,
                            onLyricsSyncOffsetChange = { lyricsSyncOffset = it },
                            onSliderValueChange = onSliderValueChange,
                            onSliderValueChangeFinished = onSliderValueChangeFinished,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(bottom = queueSheetState.collapsedBound)
                                    .windowInsetsPadding(
                                        WindowInsets(top = LocalStableSystemBarsTopPadding.current)
                                            .union(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
                                    ).nestedScroll(state.preUpPostDownNestedScrollConnection),
                        )
                    }
                } else if (playerDesignStyle == PlayerDesignStyle.V10) {
                    enrichedMetadata?.let { metadata ->
                        V10PlayerContent(
                            mediaMetadata = metadata,
                            playbackState = playbackState,
                            isPlaying = isPlaying,
                            isLoading = isLoading,
                            sliderPosition = sliderPosition,
                            position = position,
                            duration = duration,
                            playerConnection = playerConnection,
                            navController = navController,
                            state = state,
                            textBackgroundColor = dynamicV10AccentColor,
                            textButtonColor = dynamicV10FieldColor,
                            onCollapseClick = { state.collapseSoft() },
                            onLyricsClick = { isInlineLyricsOpen = !isInlineLyricsOpen },
                            lyricsOpen = isInlineLyricsOpen,
                            onCloseLyrics = { isInlineLyricsOpen = false },
                            lyricsSyncOffset = lyricsSyncOffset,
                            onLyricsSyncOffsetChange = { lyricsSyncOffset = it },
                            onSliderValueChange = onSliderValueChange,
                            onSliderValueChangeFinished = onSliderValueChangeFinished,
                            onSleepTimerClick = {
                                if (sleepTimerTimeLeft > 0L) {
                                    playerConnection.service.sleepTimer.clear()
                                } else {
                                    showSleepTimerDialog = true
                                }
                            },
                            sleepTimerEnabled = sleepTimerTimeLeft > 0L,
                            sleepTimerTimeLeft = sleepTimerTimeLeft,
                            onMenuClick = {
                                menuState.show {
                                    PlayerMenu(
                                        mediaMetadata = metadata,
                                        navController = navController,
                                        playerBottomSheetState = state,
                                        onShowDetailsDialog = {
                                            bottomSheetPageState.show {
                                                ShowMediaInfo(metadata.id)
                                            }
                                        },
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(bottom = queueSheetState.collapsedBound)
                                    .windowInsetsPadding(
                                        WindowInsets(top = LocalStableSystemBarsTopPadding.current)
                                            .union(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
                                    ).nestedScroll(state.preUpPostDownNestedScrollConnection),
                        )
                    }
} else if (playerDesignStyle == PlayerDesignStyle.SPATIALFLOW) {
                    enrichedMetadata?.let { metadata ->
                        SpatialFlowPlayerContent(
                            mediaMetadata = metadata,
                            isPlaying = isPlaying,
                            isLoading = isLoading,
                            canSkipPrevious = canSkipPrevious,
                            canSkipNext = canSkipNext,
                            duration = duration,
                            playerConnection = playerConnection,
                            navController = navController,
                            state = state,
                            menuState = menuState,
                            bottomSheetPageState = bottomSheetPageState,
                            currentFormat = currentFormat,
                            positionProvider = positionProvider,
                            canvasPrimaryUrl = artworkCanvas?.animated,
                            canvasFallbackUrl = artworkCanvas?.videoUrl,
                            appIsDark = useDarkTheme,
                            onSeek = onSliderValueChange,
                            onSeekFinished = onSliderValueChangeFinished,
                            floatingArtwork = true,
                            onArtworkSlotPositioned = { rect ->
                                spatialFlowFullArtworkRect.value = rect
                            },
                            onPagerArtworkActiveChange = { active ->
                                spatialFlowPagerArtworkActive = active
                            },
                            onLyricsOpenChange = { open ->
                                spatialFlowLyricsOpen = open
                            },
                            onQueueExpandedChange = { expanded ->
                                spatialFlowQueueOpen = expanded
                            },
                            externalBackRequests = stylePageBackRequests,
                            modifier =
                                Modifier
                                    .fillMaxSize()

                                    .windowInsetsPadding(
                                        WindowInsets.systemBars.only(WindowInsetsSides.Horizontal),
                                    ).nestedScroll(state.preUpPostDownNestedScrollConnection),
                        )
                    }
} else if (playerDesignStyle == PlayerDesignStyle.LOOPER) {
                    enrichedMetadata?.let { metadata ->
                        LooperPlayerContent(
                            mediaMetadata = metadata,
                            isPlaying = isPlaying,
                            isLoading = isLoading,
                            canSkipPrevious = canSkipPrevious,
                            canSkipNext = canSkipNext,
                            sliderPosition = sliderPosition,
                            position = position,
                            duration = duration,
                            playerConnection = playerConnection,
                            navController = navController,
                            state = state,
                            menuState = menuState,
                            bottomSheetPageState = bottomSheetPageState,
                            currentFormat = currentFormat,
                            canvasPrimaryUrl = artworkCanvas?.animated,
                            canvasFallbackUrl = artworkCanvas?.videoUrl,
                            onSeek = onSliderValueChange,
                            onSeekFinished = onSliderValueChangeFinished,
                            onLyricsClick = { isInlineLyricsOpen = !isInlineLyricsOpen },
                            onQueueClick = openQueue,
                            lyricsVisible = isInlineLyricsOpen,
                            modifier =
                                Modifier
                                    .fillMaxSize()

                                    .windowInsetsPadding(
                                        WindowInsets.systemBars.only(WindowInsetsSides.Horizontal),
                                    ).nestedScroll(state.preUpPostDownNestedScrollConnection),
                        )
                    }
} else if (playerDesignStyle == PlayerDesignStyle.SIMPMUSIC) {
                    enrichedMetadata?.let { metadata ->
                        SimpMusicPlayerContent(
                            mediaMetadata = metadata,
                            isPlaying = isPlaying,
                            isLoading = isLoading,
                            canSkipPrevious = canSkipPrevious,
                            canSkipNext = canSkipNext,
                            sliderPosition = sliderPosition,
                            position = position,
                            duration = duration,
                            playerConnection = playerConnection,
                            navController = navController,
                            state = state,
                            menuState = menuState,
                            bottomSheetPageState = bottomSheetPageState,
                            currentFormat = currentFormat,
                            onSeek = onSliderValueChange,
                            onSeekFinished = onSliderValueChangeFinished,
                            externalBackRequests = stylePageBackRequests,
                            onStylePageOpenChange = { simpmusicStylePageOpen = it },
                            modifier =
                                Modifier
                                    .fillMaxSize()

                                    .windowInsetsPadding(
                                        WindowInsets.systemBars.only(WindowInsetsSides.Horizontal),
                                    ).nestedScroll(state.preUpPostDownNestedScrollConnection),
                        )
                    }
                } else if (playerDesignStyle == PlayerDesignStyle.APPLE_MUSIC) {
                    enrichedMetadata?.let { metadata ->
                        FlamingoPlayerContent(
                            mediaMetadata = metadata,
                            playbackState = playbackState,
                            isPlaying = isPlaying,
                            isLoading = isLoading,
                            canSkipPrevious = canSkipPrevious,
                            canSkipNext = canSkipNext,
                            sliderPosition = sliderPosition,
                            positionProvider = positionProvider,
                            duration = duration,
                            playerConnection = playerConnection,
                            navController = navController,
                            state = state,
                            bottomSheetPageState = bottomSheetPageState,
                            currentSongLiked = currentSongLiked,
                            volume = deviceMusicVolumeController.volumeFraction,
                            onVolumeChange = onPlayerVolumeChange,
                            canvasPrimaryUrl = artworkCanvas?.animated,
                            canvasFallbackUrl = artworkCanvas?.videoUrl,
                            currentFormat = currentFormat,
                            contentBottomPadding = queueSheetState.collapsedBound + 20.dp,
                            onSliderValueChange = onSliderValueChange,
                            onSliderValueChangeFinished = onSliderValueChangeFinished,
                            lyricsSyncOffset = lyricsSyncOffset,
                            onLyricsSyncOffsetChange = { lyricsSyncOffset = it },
                            onLyricsVisibilityChange = { isAppleMusicInlineLyricsOpen = it },
                            externalBackRequests = stylePageBackRequests,
                            orientationRefreshEpoch = orientationRefreshEpoch,

                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .nestedScroll(state.preUpPostDownNestedScrollConnection),
                        )
                    }
            } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier =
                            Modifier

                                .windowInsetsPadding(
                                    WindowInsets(top = LocalStableSystemBarsTopPadding.current),
                                )
                                .windowInsetsPadding(
                                    WindowInsets.systemBars.only(
                                        WindowInsetsSides.Horizontal,
                                    ),
                                ).padding(bottom = queueSheetState.collapsedBound),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.weight(1f),
                        ) {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = !isInlineLyricsOpen,
                                enter = fadeIn(tween(300, easing = FastOutSlowInEasing)),
                                exit = fadeOut(tween(200, easing = FastOutSlowInEasing)),
                            ) {
                                Thumbnail(
                                    sliderPositionProvider = { sliderPosition },
                                    modifier = Modifier.nestedScroll(state.preUpPostDownNestedScrollConnection),
                                    isPlayerExpanded = state.isExpanded,
                                    onOverflowClick = {
                                        enrichedMetadata?.let { metadata ->
                                            menuState.show {
                                                PlayerMenu(
                                                    mediaMetadata = metadata,
                                                    navController = navController,
                                                    playerBottomSheetState = state,
                                                    onShowDetailsDialog = {
                                                        bottomSheetPageState.show {
                                                            ShowMediaInfo(metadata.id)
                                                        }
                                                    },
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        }
                                    },
                                )
                            }
                        }

                        enrichedMetadata?.let {
                            controlsContent(it)
                        }

                        Spacer(Modifier.height(30.dp))
                    }
                }
            }
        }
        }

        val queueOnBackgroundColor =
            if (playerDesignStyle == PlayerDesignStyle.APPLE_MUSIC ||
                playerDesignStyle == PlayerDesignStyle.BITCHORD ||
                playerDesignStyle == PlayerDesignStyle.TIKTOK ||
                playerDesignStyle == PlayerDesignStyle.SIMPMUSIC ||
                playerDesignStyle == PlayerDesignStyle.SPATIALFLOW ||
                playerDesignStyle == PlayerDesignStyle.LOOPER ||
                useBlackBackground
            ) {
                Color.White
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        val queueSurfaceColor = if (useBlackBackground) Color.Black else MaterialTheme.colorScheme.surface

        val (queueTextButtonColor, queueIconButtonColor) =
            when (playerButtonsStyle) {
                PlayerButtonsStyle.DEFAULT -> {
                    Pair(queueOnBackgroundColor, queueSurfaceColor)
                }

                PlayerButtonsStyle.SECONDARY -> {
                    Pair(
                        MaterialTheme.colorScheme.secondary,
                        MaterialTheme.colorScheme.onSecondary,
                    )
                }
            }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()

                    .drawBehind {
                        drawRect(
                            color = queueSurfaceColor,
                            alpha = queueSurfaceColor.alpha * queueSheetState.progress.coerceIn(0f, 1f),
                        )
                    },
        )

        AnimatedVisibility(
            visible = !isInlineLyricsOpen,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit =
                shrinkVertically(shrinkTowards = Alignment.Top) +
                    slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            Queue(
                state = queueSheetState,
                playerBottomSheetState = state,
                navController = navController,
                backgroundColor = queueSurfaceColor,
                onBackgroundColor = queueOnBackgroundColor,
                TextBackgroundColor = TextBackgroundColor,
                textButtonColor = textButtonColor,
                iconButtonColor = iconButtonColor,
                onShowLyrics = {
                    isInlineLyricsOpen = true
                    if (!queueSheetState.isCollapsed) {
                        queueSheetState.collapseSoft()
                    }
                },
                pureBlack = pureBlack,
            )
        }

        mediaMetadata?.let { metadata ->
            MikoLyricsTransition(
                visible = isInlineLyricsOpen,

                backHandlerEnabled =
                    isInlineLyricsOpen &&
                        state.isExpandedOrExpanding,
                mediaMetadata = metadata,
                navController = navController,
                lyricsSyncOffset = lyricsSyncOffset,
                onLyricsSyncOffsetChange = { lyricsSyncOffset = it },
                onDismiss = { isInlineLyricsOpen = false },
                onQueueClick = openQueue,
            )
        }

        AnimatedVisibility(
            visible = aodModeEnabled,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300)),
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black),
        ) {
            mediaMetadata?.let { metadata ->
                AodPlayerScreen(
                    mediaMetadata = metadata,
                    isPlaying = isPlaying,
                    position = position,
                    duration = duration,
                    sliderPosition = sliderPosition,
                    canSkipPrevious = canSkipPrevious,
                    canSkipNext = canSkipNext,
                    thumbnailCornerRadius = thumbnailCornerRadius,
                    lyricsText = currentLyricsEntity?.lyrics,
                    onPlayPause = { playerConnection.player.togglePlayPause() },
                    onSkipPrevious = playerConnection::seekToPrevious,
                    onSkipNext = playerConnection::seekToNext,
                    onSeek = { sliderPosition = it },
                    onSeekFinished = onSliderValueChangeFinished,
                    onExit = { playerConnection.aodModeEnabled.value = false },
                )
            }
        }
    }

        val isInPipMode = moe.rukamori.archivetune.ui.player.LocalIsInPipMode.current
        LaunchedEffect(isInPipMode, videoState) {
            if (isInPipMode && videoState != null) {
                videoFullscreenHolder.isFullscreen = true
            }
        }

        val videoFullscreenByRotation = remember { mutableStateOf(false) }
        val currentDeviceOrientation = LocalConfiguration.current.orientation
        LaunchedEffect(currentDeviceOrientation, videoState) {
            if (videoState == null) {
                if (videoFullscreenByRotation.value) {
                    videoFullscreenByRotation.value = false
                    videoFullscreenHolder.isFullscreen = false
                }
                return@LaunchedEffect
            }
            if (currentDeviceOrientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                if (!videoFullscreenHolder.isFullscreen && !videoFullscreenByRotation.value) {
                    videoFullscreenByRotation.value = true
                    videoFullscreenHolder.isFullscreen = true
                }
            } else {
                if (videoFullscreenByRotation.value) {
                    videoFullscreenByRotation.value = false
                    videoFullscreenHolder.isFullscreen = false
                }
            }
        }
        videoState?.let { vs ->
            if (videoFullscreenHolder.isFullscreen) {
                FullscreenVideoOverlay(
                    state = vs,
                    preferredHeight = videoPreferredHeight,
                    onPreferredHeightChange = { videoQualityStored = VideoQualityPreference.toStoredQuality(it) },
                    availableHeights = videoAvailableHeights,
                    selectedHeight = videoSelectedHeight,
                    onDismiss = {
                        videoFullscreenByRotation.value = false
                        videoFullscreenHolder.isFullscreen = false
                    },
                    followSensorOrientation = videoFullscreenByRotation.value,
                )
            }
        }

        if (!state.isCollapsed) {
            var likeBurstTrigger by remember { mutableStateOf<Any?>(null) }

            var lastLiked by remember { mutableStateOf<Boolean?>(null) }
            var lastBurstSongId by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(currentSongLiked, mediaMetadata?.id) {
                val songId = mediaMetadata?.id ?: return@LaunchedEffect
                if (songId != lastBurstSongId) {
                    lastBurstSongId = songId
                    lastLiked = currentSongLiked
                    return@LaunchedEffect
                }
                if (currentSongLiked && lastLiked == false) {
                    likeBurstTrigger = System.nanoTime()
                }
                lastLiked = currentSongLiked
            }
            LaunchedEffect(likeBurstTrigger) {
                if (likeBurstTrigger != null) {
                    delay(700)
                    likeBurstTrigger = null
                }
            }
            if (likeBurstTrigger != null) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .size(140.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ArchiveTuneLottieAnimation(
                        rawRes = ArchiveTuneLottie.LikeRes,
                        trigger = likeBurstTrigger,
                        tintColor = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

    }
    }
    }

    val activePlaybackError = playbackError
    val isRecoveryDestination =
        currentRoute?.startsWith(LOGIN_ROUTE) == true || currentRoute == PO_TOKEN_ROUTE
    if (activePlaybackError != null && !isRecoveryDestination) {
        val errorInfo = remember(activePlaybackError) { activePlaybackError.toPlaybackErrorInfo() }
        val loginClick =
            remember(errorInfo.loginRecoveryUrl, navigateToLogin) {
                { navigateToLogin(errorInfo.loginRecoveryUrl) }
            }

        PlaybackErrorDialog(
            error = activePlaybackError,
            showLoginAction = !isYouTubeLoggedIn,
            showPoTokenLoginAction = !isPoTokenLoggedIn,
            onRetry = retryPlayback,
            onClose = dismissPlaybackError,
            onLogin = loginClick,
            onPoTokenLogin = navigateToPoTokenLogin,
        )
    }
}

@Composable
private fun MikoLyricsTransition(
    visible: Boolean,
    backHandlerEnabled: Boolean,
    mediaMetadata: MediaMetadata,
    navController: NavController,
    lyricsSyncOffset: Int,
    onLyricsSyncOffsetChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    onQueueClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val animationsDisabled = LocalAnimationsDisabled.current
    val progress = remember { Animatable(initialValue = 0f) }
    LaunchedEffect(visible, animationsDisabled) {
        if (animationsDisabled) {
            progress.snapTo(if (visible) 1f else 0f)
        } else {
            progress.animateTo(
                targetValue = if (visible) 1f else 0f,
                animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
            )
        }
    }
    val progressState = progress.asState()
    val showContent by remember(visible) {
        derivedStateOf { visible || progressState.value > 0f }
    }

    if (showContent) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = progressState.value.coerceIn(0f, 1f)

                        alpha = ((p - 0.45f) / 0.55f).coerceIn(0f, 1f)
                        translationY = (1f - p) * 26.dp.toPx()
                        scaleX = 0.92f + 0.08f * p
                        scaleY = 0.92f + 0.08f * p
                    }.background(MaterialTheme.colorScheme.surface),
        ) {
                LyricsScreen(
                    mediaMetadata = mediaMetadata,
                    onBackClick = onDismiss,
                    navController = navController,
                    lyricsSyncOffset = lyricsSyncOffset,
                    onLyricsSyncOffsetChange = onLyricsSyncOffsetChange,
                    onQueueClick = onQueueClick,
                    backHandlerEnabled = backHandlerEnabled,
                )
            }
    }
}
@Composable
private fun BackdropBlurApi30(
    model: String?,
    blurAmount: Int,
    modifier: Modifier = Modifier,
    onError: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val imageLoader = context.imageLoader

    val blurredBitmap by produceState<Bitmap?>(null, model, blurAmount) {
        if (model == null) return@produceState
        value =
            withContext(Dispatchers.IO) {
                try {
                    val request =
                        ImageRequest
                            .Builder(context)
                            .data(model)
                            .memoryCacheKey(model)
                            .diskCacheKey(model)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .networkCachePolicy(CachePolicy.ENABLED)
                            .allowHardware(false)
                            .size(500)
                            .build()
                    val result = imageLoader.execute(request)
                    when (result) {
                        is SuccessResult -> {
                            val bitmap = result.image.toBitmap().copy(Bitmap.Config.ARGB_8888, true)
                            val radius = (blurAmount * 25 / 100f).coerceIn(1f, 25f)
                            ImageBlurUtils.blur(bitmap, radius)
                        }

                        else -> {
                            if (onError != null) {
                                withContext(Dispatchers.Main) {
                                    onError(model)
                                }
                            }
                            null
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    if (onError != null) {
                        withContext(Dispatchers.Main) {
                            onError(model)
                        }
                    }
                    null
                }
            }
    }

    val loadedBitmap = blurredBitmap
    if (loadedBitmap != null) {
        Image(
            painter = BitmapPainter(loadedBitmap.asImageBitmap()),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        AsyncImage(
            model = rememberOfflineArtworkImageRequest(model),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
            onState = { state ->
                if (state is coil3.compose.AsyncImagePainter.State.Error && model != null) {
                    onError?.invoke(model)
                }
            },
        )
    }
}

@Composable
private fun V7PlayerBackdrop(
    thumbnailUrl: String?,
    canvasStaticUrl: String?,
    canvasPrimaryUrl: String?,
    canvasFallbackUrl: String?,
    isPlaying: Boolean,
    disableBlur: Boolean,
    backdropBlurAmount: Int,
    label: String,
    playCanvasInBackdrop: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val fallbackColor = Color.Black.toArgb()
    val backdropArtworkSizePx =
        remember(
            configuration.screenWidthDp,
            configuration.screenHeightDp,
            density.density,
        ) {
            with(density) {
                (
                    maxOf(configuration.screenWidthDp, configuration.screenHeightDp).dp.toPx() *
                        V7BackdropArtworkOverscanFactor
                ).roundToInt()
                    .coerceIn(V7BackdropMinArtworkSizePx, V7BackdropMaxArtworkSizePx)
            }
        }

    val canvasPrimary = canvasPrimaryUrl?.takeIf { it.isNotBlank() }
    val canvasFallback = canvasFallbackUrl?.takeIf { it.isNotBlank() }
    val canvasStatic = canvasStaticUrl?.takeIf { it.isNotBlank() }
    val coverArtworkUrl = thumbnailUrl?.takeIf { it.isNotBlank() }
    val hasCanvas = !canvasPrimary.isNullOrBlank() || !canvasFallback.isNullOrBlank()

    val canvasLoopSync = remember { CanvasLoopSync() }

    val sharpArtworkUrl = if (hasCanvas) (canvasStatic ?: coverArtworkUrl) else (coverArtworkUrl ?: canvasStatic)
    val backdropArtworkUrl = coverArtworkUrl ?: canvasStatic

    val paletteSourceUrl = if (hasCanvas && canvasStatic != null) canvasStatic else backdropArtworkUrl
    var backdropPalette by remember(paletteSourceUrl, fallbackColor) {
        mutableStateOf(V7BackdropPalette.fromColors(emptyList(), fallbackColor))
    }

    LaunchedEffect(paletteSourceUrl, hasCanvas, fallbackColor) {
        backdropPalette = V7BackdropPalette.fromColors(emptyList(), fallbackColor)
        if (paletteSourceUrl == null) return@LaunchedEffect

        val request =
            ImageRequest
                .Builder(context)
                .data(paletteSourceUrl)
                .memoryCacheKey(paletteSourceUrl)
                .diskCacheKey(paletteSourceUrl)
                .diskCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                .size(PlayerColorExtractor.Config.IMAGE_SIZE, PlayerColorExtractor.Config.IMAGE_SIZE)
                .allowHardware(false)
                .build()

        val extractedColors =
            try {
                val image =
                    withContext(Dispatchers.IO) {
                        context.imageLoader.execute(request)
                    }.image
                if (image == null) {
                    null
                } else {
                    withContext(Dispatchers.Default) {
                        val fullBitmap = image.toBitmap()

                        val bitmapForPalette =
                            if (hasCanvas && fullBitmap.height > 4) {
                                val startY = (fullBitmap.height * 0.70f).toInt().coerceAtLeast(0)
                                val cropHeight = (fullBitmap.height - startY).coerceAtLeast(1)
                                android.graphics.Bitmap.createBitmap(fullBitmap, 0, startY, fullBitmap.width, cropHeight)
                            } else {
                                fullBitmap
                            }
                        val palette =
                            Palette
                                .from(bitmapForPalette)
                                .maximumColorCount(PlayerColorExtractor.Config.MAX_COLOR_COUNT)
                                .resizeBitmapArea(PlayerColorExtractor.Config.BITMAP_AREA)
                                .generate()
                        val dominantRgb = palette.dominantSwatch?.rgb ?: palette.getDominantColor(fallbackColor)
                        listOf(Color(dominantRgb))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }

        backdropPalette = V7BackdropPalette.fromColors(extractedColors.orEmpty(), fallbackColor)
    }

    val backdropState =
        remember(sharpArtworkUrl, canvasPrimary, canvasFallback) {
            V7PlayerBackdropState(
                artworkUrl = sharpArtworkUrl,
                canvasPrimaryUrl = canvasPrimary,
                canvasFallbackUrl = canvasFallback,
            )
        }
    var backdropArtworkModel by remember(backdropArtworkUrl, backdropArtworkSizePx) {
        mutableStateOf(
            backdropArtworkUrl?.resize(
                width = backdropArtworkSizePx,
                height = backdropArtworkSizePx,
                maxresAllowed = true,
                ytimgResizePolicy = YtimgResizePolicy.AllowAnyAspect,
            ),
        )
    }
    val backdropArtworkRequest = rememberOfflineArtworkImageRequest(backdropArtworkModel)
    val sharpStageBottomScrim =
        remember(backdropPalette) {
            val blendColor = backdropPalette.bottom
            Brush.verticalGradient(
                colorStops =
                    arrayOf(
                        0f to Color.Transparent,
                        V7SharpStageBottomScrimStartFraction to Color.Transparent,
                        0.60f to blendColor.copy(alpha = 0.18f),
                        0.76f to blendColor.copy(alpha = 0.52f),
                        0.88f to blendColor.copy(alpha = 0.82f),
                        1f to blendColor,
                    ),
            )
        }
    val backdropFloor =
        remember(backdropPalette) {
            Brush.verticalGradient(
                colorStops =
                    arrayOf(
                        0f to backdropPalette.bottom,
                        V7BackdropFloorBlackStartFraction to backdropPalette.bottom,
                        1f to backdropPalette.bottom,
                    ),
            )
        }
    val backdropBlurRadius = V7BackdropBlurDp.dp * (backdropBlurAmount.toFloat() / 100f)
    val needsBlur = !disableBlur && backdropBlurAmount > 0
    val backdropImageModifier =
        remember(disableBlur, needsBlur) {
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = V7BackdropBlurScale
                    scaleY = V7BackdropBlurScale
                    alpha = if (disableBlur || !needsBlur) 0.20f else 0.58f
                }
        }
    val canvasStageModifier =
        remember {
            Modifier
                .fillMaxSize()
        }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .background(backdropPalette.top),
    ) {
        val sharpStageFraction =
            if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                V7SharpStageLandscapeFraction
            } else {
                V7SharpStagePortraitFraction
            }
        val sharpStageHeight = maxHeight * sharpStageFraction
        val sharpStageTopOffset = 0.dp
        val sharpStageBottomOffset = sharpStageTopOffset + sharpStageHeight
        val backdropTopOffset = (sharpStageBottomOffset - V7BackdropOverlapDp.dp).coerceAtLeast(0.dp)
        val backdropHeight = maxHeight - backdropTopOffset

        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(backdropHeight)
                    .clipToBounds()
                    .background(backdropPalette.bottom),
        ) {
            if (backdropArtworkModel != null) {
                if (needsBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    AsyncImage(
                        model = backdropArtworkRequest,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = backdropImageModifier.blur(backdropBlurRadius),
                        onState = { state ->
                            if (state is coil3.compose.AsyncImagePainter.State.Error) {
                                getNextFallbackUrl(backdropArtworkModel)?.let { backdropArtworkModel = it }
                            }
                        },
                    )
                } else if (needsBlur) {
                    BackdropBlurApi30(
                        model = backdropArtworkModel,
                        blurAmount = backdropBlurAmount,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = V7BackdropBlurScale
                                    scaleY = V7BackdropBlurScale
                                    alpha = 0.58f
                                },
                        onError = { failedUrl ->
                            getNextFallbackUrl(failedUrl)?.let { backdropArtworkModel = it }
                        },
                    )
                } else {
                    AsyncImage(
                        model = backdropArtworkRequest,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = backdropImageModifier,
                        onState = { state ->
                            if (state is coil3.compose.AsyncImagePainter.State.Error) {
                                getNextFallbackUrl(backdropArtworkModel)?.let { backdropArtworkModel = it }
                            }
                        },
                    )
                }
            }
            if (playCanvasInBackdrop && hasCanvas && needsBlur &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val scale = V7BackdropBlurScale * V7CanvasBackdropUpscale
                                scaleX = scale
                                scaleY = scale
                                alpha = if (disableBlur || !needsBlur) 0.20f else 0.58f
                            },
                ) {
                    CanvasArtworkPlayer(
                        primaryUrl = canvasPrimary,
                        fallbackUrl = canvasFallback,
                        isPlaying = isPlaying,
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                        loopSyncFollower = canvasLoopSync,
                        modifier =
                            Modifier
                                .fillMaxWidth(1f / V7CanvasBackdropUpscale)
                                .fillMaxHeight(1f / V7CanvasBackdropUpscale)
                                .blur(backdropBlurRadius / V7CanvasBackdropUpscale),
                    )
                }
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(backdropFloor),
            )
        }

        AnimatedContent(
            targetState = backdropState,
            transitionSpec = {
                fadeIn(tween(900)) togetherWith fadeOut(tween(900))
            },
            label = label,
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = sharpStageTopOffset)
                    .fillMaxWidth()
                    .height(sharpStageHeight)
                    .clipToBounds(),
        ) { backdrop ->
            var sharpArtworkModel by remember(backdrop.artworkUrl, backdropArtworkSizePx) {
                mutableStateOf(
                    backdrop.artworkUrl?.resize(
                        width = backdropArtworkSizePx,
                        height = backdropArtworkSizePx,
                        maxresAllowed = true,
                        ytimgResizePolicy = YtimgResizePolicy.AllowAnyAspect,
                    ),
                )
            }
            val sharpArtworkRequest = rememberOfflineArtworkImageRequest(sharpArtworkModel)

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(backdropPalette.top),
                contentAlignment = Alignment.Center,
            ) {
                if (sharpArtworkModel != null) {
                    AsyncImage(
                        model = sharpArtworkRequest,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        onState = { state ->
                            if (state is coil3.compose.AsyncImagePainter.State.Error) {
                                getNextFallbackUrl(sharpArtworkModel)?.let { sharpArtworkModel = it }
                            }
                        },
                    )
                }

                if (hasCanvas) {
                    CanvasArtworkPlayer(
                        primaryUrl = backdrop.canvasPrimaryUrl,
                        fallbackUrl = backdrop.canvasFallbackUrl,
                        isPlaying = isPlaying,
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                        loopSyncLeader = canvasLoopSync,
                        modifier = canvasStageModifier,
                    )
                }
            }
        }

        Box(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = sharpStageTopOffset)
                    .fillMaxWidth()
                    .height(sharpStageHeight)
                    .background(sharpStageBottomScrim),
        )
    }
}

private typealias V7BackdropPalette = moe.rukamori.archivetune.ui.theme.BackdropTonePalette

@Immutable
private data class V7PlayerBackdropState(
    val artworkUrl: String?,
    val canvasPrimaryUrl: String?,
    val canvasFallbackUrl: String?,
)

private fun CanvasArtwork.isSpotifyProviderCanvas(): Boolean =
    inferredProvider() == CanvasArtwork.PROVIDER_SPOTIFY

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LittlePlayerContent(
    mediaMetadata: MediaMetadata,
    sliderPosition: Long?,
    positionMs: Long,
    durationMs: Long,
    textColor: Color,
    liked: Boolean,
    onCollapse: () -> Unit,
    onToggleLike: () -> Unit,
    onExpandQueue: () -> Unit,
    onMenuClick: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val titleColor = textColor.copy(alpha = 0.95f)
        val secondaryColor = textColor.copy(alpha = 0.6f)
        val timeColor = textColor.copy(alpha = 0.85f)

        val scale =
            minOf(maxWidth / 420.dp, maxHeight / 260.dp)
                .coerceIn(0.78f, 1.15f)

        val titleSize = (56f * scale).sp
        val timeSize = (44f * scale).sp
        val iconSize = (26f * scale).dp
        val collapseIconSize = (28f * scale).dp
        val horizontalPadding = (18f * scale).dp
        val verticalPadding = (10f * scale).dp

        val displayPositionMs = sliderPosition ?: positionMs

        val timeText =
            remember(displayPositionMs, durationMs) {
                val positionText = makeTimeString(displayPositionMs)
                val durationText = if (durationMs != C.TIME_UNSET) makeTimeString(durationMs) else ""
                if (durationText.isBlank()) positionText else "$positionText/$durationText"
            }

        val artistsText =
            remember(mediaMetadata.artists) {
                mediaMetadata.artists.joinToString(separator = ", ") { artist -> artist.name }
            }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        ) {
            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                PlayerTextBackdrop(
                    textColor = textColor,
                    modifier = Modifier.weight(1f),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        AnimatedContent(
                            targetState = mediaMetadata.title,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "little_title",
                        ) { title ->
                            PlayerTitleText(
                                title = title,
                                explicit = mediaMetadata.explicit,
                                color = titleColor,
                                style = LocalTextStyle.current,
                                fontSize = titleSize,
                                fontWeight = FontWeight.Bold,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .basicMarquee(),
                            )
                        }

                        Spacer(Modifier.height((10f * scale).dp))

                        mediaMetadata.album?.title?.takeIf { it.isNotBlank() }?.let { albumTitle ->
                            AnimatedContent(
                                targetState = albumTitle,
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                label = "little_album",
                            ) { album ->
                                Text(
                                    text = album,
                                    color = secondaryColor,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .basicMarquee(),
                                )
                            }
                        }

                        artistsText.takeIf { it.isNotBlank() }?.let { artists ->
                            AnimatedContent(
                                targetState = artists,
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                label = "little_artists",
                            ) { artistLine ->
                                Text(
                                    text = "by - $artistLine",
                                    color = secondaryColor,
                                    style = MaterialTheme.typography.bodyMedium,
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

                Spacer(Modifier.width((16f * scale).dp))

                Text(
                    text = timeText,
                    color = timeColor,
                    fontSize = timeSize,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.widthIn(min = (140f * scale).dp),
                )
            }

            Spacer(Modifier.height((14f * scale).dp))

            Spacer(Modifier.height((6f * scale).dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.expand_more),
                    contentDescription = null,
                    tint = textColor.copy(alpha = 0.8f),
                    modifier =
                        Modifier
                            .size(collapseIconSize)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onCollapse,
                            ),
                )

                Spacer(Modifier.weight(1f))

                Icon(
                    painter = painterResource(if (liked) R.drawable.favorite else R.drawable.favorite_border),
                    contentDescription = null,
                    tint =
                        if (liked) {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
                        } else {
                            textColor.copy(alpha = 0.78f)
                        },
                    modifier =
                        Modifier
                            .size(iconSize)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onToggleLike,
                            ),
                )

                Spacer(Modifier.width((18f * scale).dp))

                Icon(
                    painter = painterResource(R.drawable.queue_music),
                    contentDescription = null,
                    tint = textColor.copy(alpha = 0.78f),
                    modifier =
                        Modifier
                            .size(iconSize)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onExpandQueue,
                            ),
                )

                Spacer(Modifier.width((18f * scale).dp))

                Icon(
                    painter = painterResource(R.drawable.more_vert),
                    contentDescription = null,
                    tint = textColor.copy(alpha = 0.78f),
                    modifier =
                        Modifier
                            .size(iconSize)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onMenuClick,
                            ),
                )
            }
        }
    }
}

@Composable
private fun LandscapeLikeBox(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(
        content = content,
        modifier = modifier.graphicsLayer { clip = true },
    ) { measurables, constraints ->
        val measurable = measurables.firstOrNull()
        if (measurable == null) {
            layout(constraints.minWidth, constraints.minHeight) {}
        } else {
            val swappedConstraints =
                Constraints(
                    minWidth = constraints.minHeight,
                    maxWidth = constraints.maxHeight,
                    minHeight = constraints.minWidth,
                    maxHeight = constraints.maxWidth,
                )

            val placeable = measurable.measure(swappedConstraints)
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val rotatedWidth = placeable.height
            val rotatedHeight = placeable.width

            val x = ((width - rotatedWidth) / 2).coerceAtLeast(0)
            val y = ((height - rotatedHeight) / 2).coerceAtLeast(0)

            layout(width, height) {
                placeable.placeWithLayer(x, y) {
                    transformOrigin = TransformOrigin(0f, 0f)
                    rotationZ = 90f
                    translationX = placeable.height.toFloat()
                }
            }
        }
    }
}

private fun Modifier.littlePlayerOverlayGestures(
    seekEnabled: Boolean,
    durationMs: Long,
    progressFraction: Float,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    onSeekToPositionMs: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
): Modifier =
    composed {
        val view = LocalView.current
        val (enableHapticFeedback) = rememberPreference(EnableHapticFeedbackKey, true)

        pointerInput(seekEnabled, durationMs, canSkipPrevious, canSkipNext) {
            var lastTapUptimeMs = 0L
            var lastTapPosition: Offset? = null
            val doubleTapTimeoutMs = viewConfiguration.doubleTapTimeoutMillis.toLong()
            val touchSlop = viewConfiguration.touchSlop

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = true)
                val pointerId = down.id

                var upPosition = down.position
                val minOverlayHeightPx = 24.dp.toPx()
                val overlayHeightPx =
                    (progressFraction * size.height).coerceAtLeast(minOverlayHeightPx)
                val seekAllowedFromDown =
                    seekEnabled &&
                        durationMs > 0L &&
                        durationMs != C.TIME_UNSET &&
                        down.position.y <= overlayHeightPx

                var isSeeking = false

                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val change = event.changes.firstOrNull { it.id == pointerId } ?: continue
                    upPosition = change.position

                    if (!change.pressed) break

                    if (!isSeeking && seekAllowedFromDown) {
                        val distanceFromDown = (change.position - down.position).getDistance()
                        if (distanceFromDown > touchSlop) isSeeking = true
                    }

                    if (isSeeking) {
                        val fraction =
                            if (size.height > 0) (change.position.y / size.height.toFloat()) else 0f
                        val clampedFraction = fraction.coerceIn(0f, 1f)

                        val targetMs =
                            (durationMs.toDouble() * clampedFraction.toDouble()).roundToLong().coerceIn(0L, durationMs)
                        onSeekToPositionMs(targetMs)
                        change.consume()
                    }
                }

                if (isSeeking) {
                    onSeekFinished()
                    lastTapUptimeMs = 0L
                    lastTapPosition = null
                } else {
                    val now = SystemClock.uptimeMillis()
                    val previousTapPosition = lastTapPosition
                    val isDoubleTap =
                        previousTapPosition != null &&
                            (now - lastTapUptimeMs) <= doubleTapTimeoutMs &&
                            (upPosition - previousTapPosition).getDistance() <= (touchSlop * 2f)

                    if (isDoubleTap) {
                        val isTopSide = upPosition.y < size.height / 2f
                        if (isTopSide) {
                            if (canSkipPrevious) {
                                if (enableHapticFeedback) {
                                    view.performHapticFeedback(
                                        android.view.HapticFeedbackConstants.CONTEXT_CLICK,
                                        android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                                    )
                                }
                                onSkipPrevious()
                            }
                        } else {
                            if (canSkipNext) {
                                if (enableHapticFeedback) {
                                    view.performHapticFeedback(
                                        android.view.HapticFeedbackConstants.CONTEXT_CLICK,
                                        android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                                    )
                                }
                                onSkipNext()
                            }
                        }
                        lastTapUptimeMs = 0L
                        lastTapPosition = null
                    } else {
                        lastTapUptimeMs = now
                        lastTapPosition = upPosition
                    }
                }
            }
        }
    }
