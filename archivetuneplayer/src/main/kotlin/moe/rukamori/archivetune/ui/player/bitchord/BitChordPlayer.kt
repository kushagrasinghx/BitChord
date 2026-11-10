/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player.bitchord

import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.media3.ui.AspectRatioFrameLayout
import moe.rukamori.archivetune.ui.player.CanvasArtworkPlayer
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt
import moe.rukamori.archivetune.LocalStableSystemBarsTopPadding
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.automix.AutoMixAnalysisState
import moe.rukamori.archivetune.playback.automix.AutoMixUiState
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.lyrics.LyricsUtils
import moe.rukamori.archivetune.constants.AutoTranslateExcludedLanguagesKey
import moe.rukamori.archivetune.constants.AutoTranslateLyricsKey
import moe.rukamori.archivetune.constants.TranslatorTargetLangKey
import moe.rukamori.archivetune.ui.component.BottomSheetPageState
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.ui.utils.ShowMediaInfo
import moe.rukamori.archivetune.ui.menu.PlayerMenu
import moe.rukamori.archivetune.ui.utils.resize
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.LyricsMenuViewModel
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.constants.LyricsBackgroundStyle
import moe.rukamori.archivetune.constants.LyricsBackgroundStyleKey
import moe.rukamori.archivetune.constants.LyricsMode
import moe.rukamori.archivetune.constants.LyricsModeKey
import moe.rukamori.archivetune.constants.PlayerBackgroundStyle
import moe.rukamori.archivetune.constants.PlayerBackgroundStyleKey
import moe.rukamori.archivetune.ui.player.StyledLyricsBackground
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.ui.component.LyricsEnhanced
import moe.rukamori.archivetune.ui.component.LyricsV2
import moe.rukamori.archivetune.ui.player.LosslessOrStats
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.navigation.NavController
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

internal const val ART_PX = 1200

internal const val SEEK_SETTLE_TOLERANCE_MS = 1_500L

internal const val SEEK_SETTLE_TIMEOUT_MS = 4_000L

internal val THUMB_SIZE = 54.dp
internal val HEADER_HEIGHT = 60.dp
internal val ART_TITLE_GAP = 20.dp

internal const val QUEUE_TRAVEL_MS = 420

internal const val QUEUE_CARRY_FRACTION = 0.3f

internal const val QUEUE_FLICK_VELOCITY = 450f

internal val DISMISS_STRIP_HEIGHT = 32.dp

internal val ART_BOX_TOP_PAD = 8.dp

internal const val HERO_FADE_FRACTION = 0.42f

internal val PLAYER_GUTTER = 30.dp

internal val PLAYER_MAX_WIDTH = 560.dp

internal val CONTROL_GAP_SPREAD_MAX = 24.dp

private var lastControlSpread: Dp = 0.dp

internal const val BACK_RESTARTS_AFTER_MS = 10_000L

internal const val LYRIC_FADE_FRACTION = 0.28f
internal const val LYRIC_FADE_MIN_MS = 160f
internal const val LYRIC_FADE_MAX_MS = 700f

internal const val UNSUNG_ALPHA = 0.45f
internal const val UNSUNG_ALPHA_STRIP = 0.55f

internal const val GLOW_ALPHA = 0.62f
internal val GLOW_RADIUS = 9.dp

internal val GLOW_TRAIL = 62.dp
internal const val GLOW_TRAIL_FLOOR = 0.55f

internal val GLOW_ROOM = 10.dp

internal val BACKING_FONT_SIZE = 19.sp
internal val BACKING_LINE_HEIGHT = 24.sp
internal const val BACKING_ALPHA = 0.72f

internal val AUX_FONT_SIZE = 16.sp
internal val AUX_LINE_HEIGHT = 20.sp
internal const val AUX_ALPHA = 0.6f

internal const val INSTRUMENTAL_MARK = "Instrumental"

internal val INTRO_LINES = listOf(
    "Beat's landing",
    "Song's starting",
    "Intro's cooking",
    "Warming up",
    "Here we go",
    "Setting the mood",
    "Drums are in",
    "Bass first, words later",
    "Turn it up",
    "Vibe check",
    "Wait for it",
    "Feel that build",
    "Let it ride",
    "Just the groove for now",
    "Speakers breathing",
    "Rolling in",
    "Hold tight",
    "Riff o'clock",
    "Strings first",
    "Hook's on the way",
    "Eyes closed",
    "Loading the vibe",
    "Almost words",
    "Pure heat, no words",
    "Tuning in",
    "Buckle up",
    "Let it breathe",
    "That opening though",
    "Bass is talking",
    "Lyrics loading",
    "Give it a sec",
    "Building something",
    "Cue the vocals",
    "Slow burn",
    "First notes in",
    "Nod along",
    "Groove's on deck",
    "Melody first",
    "Ease into it",
    "Big things coming",
    "Stage is set",
    "The calm before",
    "Sit with it",
    "Any second now",
    "Volume up, phone down",
    "Drums doing the talking",
    "Locked in",
    "Something's brewing",
    "Finding its feet",
    "Deep breath",
)

internal val LYRICS_LOADING_LINES = listOf(
    "Getting lyrics",
    "Chasing the words",
    "Digging up the lyrics",
    "Words incoming",
    "On the hunt for lyrics",
    "Fetching the verses",
    "Tracking down the words",
    "Lyrics loading",
    "Reading between the lines",
    "Scanning for lyrics",
    "Words on the way",
    "Looking this one up",
    "Checking the lyric sheet",
    "Pulling up the words",
    "Searching the songbook",
    "Lining up the lyrics",
    "One sec, finding the words",
    "Combing through for lyrics",
    "Lyrics inbound",
    "Sourcing the verses",
    "Cross-checking the words",
    "Rounding up the lyrics",
    "Text hunt in progress",
    "Syncing up the words",
    "Peeking at the lyric sheet",
    "Almost got the words",
    "Fishing for lyrics",
    "Grabbing the transcript",
    "Lyrics, one moment",
    "Tuning in the words",
    "Locating the verses",
    "Words are en route",
    "Checking the archives",
    "Piecing the lyrics together",
    "Loading up the words",
    "Lyric search underway",
    "Finding the right words",
    "Tracking the lyric sheet",
    "Verses incoming",
    "Getting the words lined up",
    "Hang tight, fetching lyrics",
    "Looking for the hook",
    "Words are loading",
    "Lyrics on their way",
    "Checking what's sung here",
    "Reading the room for lyrics",
    "Lyric lookup in progress",
    "Bringing up the words",
    "Just a sec, finding words",
    "Lyrics coming together",
)

internal const val LYRICS_UNAVAILABLE_HOLD_MS = 5_000L
internal const val LYRICS_UNAVAILABLE_FADE_MS = 900

@Composable
fun BitChordPlayerContent(
    mediaMetadata: MediaMetadata,
    isPlaying: Boolean,
    isLoading: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,

    positionProvider: () -> Long,
    duration: Long,
    playerConnection: PlayerConnection,
    navController: NavController,
    state: BottomSheetState,
    menuState: MenuState,
    bottomSheetPageState: BottomSheetPageState,
    currentFormat: FormatEntity?,
    canvasPrimaryUrl: String? = null,
    canvasFallbackUrl: String? = null,
    externalBackRequests: androidx.compose.runtime.MutableIntState? = null,
    onStylePageOpenChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptics = rememberHaptics()
    val database = LocalDatabase.current
    val player = playerConnection.player

    val automixOn by AutoMixUiState.enabled.collectAsStateWithLifecycle()
    val automixAnalysis by AutoMixUiState.analysis.collectAsStateWithLifecycle()
    val automixTransitionWindow by AutoMixUiState.transitionWindow.collectAsStateWithLifecycle()
    val crossfadeMixing by playerConnection.service.crossfadeMixing.collectAsStateWithLifecycle()
    val automixMixing by AutoMixUiState.mixing.collectAsStateWithLifecycle()
    val sliderMixing = crossfadeMixing || automixMixing
    val sliderTransitionWindow = remember(automixTransitionWindow) {
        automixTransitionWindow?.let { it.startFraction..it.endFraction }
    }

    val reduceAnimations = LocalAnimationsDisabled.current

    val lyricsEntity by database.lyrics(mediaMetadata.id)
        .collectAsStateWithLifecycle(initialValue = null)
    val parsedLyrics = remember(lyricsEntity?.lyrics, mediaMetadata.duration) {
        val raw = lyricsEntity?.lyrics
        if (raw == null || raw == LyricsEntityNotFound) {
            null
        } else {
            parseBitChordLyrics(raw, mediaMetadata.duration)
        }
    }
    val lyrics = parsedLyrics?.lines
    val lyricsSynced = parsedLyrics?.isSynced ?: true
    val lyricsUnavailable = lyricsEntity?.lyrics == LyricsEntityNotFound

    val lyricsMenuViewModel: LyricsMenuViewModel = hiltViewModel()
    val (autoTranslateLyrics) = rememberPreference(AutoTranslateLyricsKey, defaultValue = false)
    val (translatorTargetLang) = rememberPreference(TranslatorTargetLangKey, defaultValue = "")
    val (autoTranslateExcludedLanguages) =
        rememberPreference(AutoTranslateExcludedLanguagesKey, defaultValue = emptySet())
    val translationDismissedMediaIds by lyricsMenuViewModel.translationDismissedMediaIds
        .collectAsStateWithLifecycle()
    LaunchedEffect(
        mediaMetadata.id,
        lyricsEntity?.lyrics,
        lyricsEntity?.source,
        autoTranslateLyrics,
        translatorTargetLang,
        autoTranslateExcludedLanguages,
        translationDismissedMediaIds,
    ) {
        if (!autoTranslateLyrics) return@LaunchedEffect
        val snapshot = lyricsEntity ?: return@LaunchedEffect
        val text = snapshot.lyrics
        if (text.isBlank() || text == LyricsEntity.LYRICS_NOT_FOUND) return@LaunchedEffect

        if (snapshot.source == LyricsEntity.Source.AI_TRANSLATION.value &&
            LyricsUtils.hasTranslation(text)
        ) {
            return@LaunchedEffect
        }

        if (mediaMetadata.id in translationDismissedMediaIds) return@LaunchedEffect
        if (!LyricsUtils.shouldAutoTranslate(
                lyrics = text,
                targetLanguage = translatorTargetLang,
                excludedLanguageCodes = autoTranslateExcludedLanguages,
            )
        ) {
            return@LaunchedEffect
        }
        lyricsMenuViewModel.translateLyricsWithAi(
            mediaMetadata = mediaMetadata,
            lyrics = text,
            targetLanguage = translatorTargetLang,
        )
    }

    val queueWindows by playerConnection.queueWindows.collectAsStateWithLifecycle()
    val queueIndex by playerConnection.currentWindowIndex.collectAsStateWithLifecycle()
    val queue = remember(queueWindows) {
        queueWindows.map { window ->
            val meta = window.mediaItem.metadata
            BitChordQueueSong(
                id = window.mediaItem.mediaId,
                title = meta?.title.orEmpty(),
                artist = meta?.artists?.joinToString(", ") { it.name }.orEmpty(),
                thumbnailUrl = meta?.thumbnailUrl,
            )
        }
    }

    val repeatMode by playerConnection.repeatMode.collectAsStateWithLifecycle()
    val shuffleEnabled by playerConnection.shuffleModeEnabled.collectAsStateWithLifecycle()
    val currentSong by playerConnection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val currentSongLiked = currentSong?.song?.liked == true

    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }

    var queueOpen by remember { mutableStateOf(false) }
    var lyricsOpen by remember { mutableStateOf(false) }
    LaunchedEffect(mediaMetadata.id) { lyricsOpen = false }

    var lyricsSyncOffset by rememberSaveable(mediaMetadata.id) {
        mutableIntStateOf(0)
    }

    BackHandler(enabled = lyricsOpen || queueOpen) {
        if (queueOpen) {
            queueOpen = false
        } else {
            lyricsOpen = false
        }
    }
    LaunchedEffect(state.isExpandedOrExpanding) {
        if (!state.isExpandedOrExpanding && (lyricsOpen || queueOpen)) {
            queueOpen = false
            lyricsOpen = false
        }
    }
    val bitchordStylePageOpen = lyricsOpen || queueOpen
    LaunchedEffect(bitchordStylePageOpen) { onStylePageOpenChange(bitchordStylePageOpen) }
    val externalBackEpoch = externalBackRequests?.intValue ?: 0
    LaunchedEffect(externalBackEpoch) {
        if (externalBackEpoch > 0) {
            if (queueOpen) queueOpen = false
            if (lyricsOpen) lyricsOpen = false
        }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val view = LocalView.current
        DisposableEffect(view, lyricsOpen, queueOpen) {
            val callback =
                if (lyricsOpen || queueOpen) {
                    OverlayBack.register(view) {
                        if (queueOpen) {
                            queueOpen = false
                        } else {
                            lyricsOpen = false
                        }
                    }
                } else {
                    null
                }
            onDispose { OverlayBack.unregister(view, callback) }
        }
    }

    val queueSlide = remember { mutableFloatStateOf(0f) }
    val queueProgress = queueSlide.floatValue
    var queueDragging by remember { mutableStateOf(false) }
    var queueReleased by remember { mutableIntStateOf(0) }
    LaunchedEffect(queueOpen, queueDragging, queueReleased) {
        if (queueDragging) return@LaunchedEffect
        val target = if (queueOpen) 1f else 0f
        val from = queueSlide.floatValue
        if (from == target) return@LaunchedEffect
        animate(
            initialValue = from,
            targetValue = target,
            animationSpec = tween(
                durationMillis = (QUEUE_TRAVEL_MS * abs(target - from)).roundToInt(),
                easing = FastOutSlowInEasing,
            ),
        ) { value, _ -> queueSlide.floatValue = value }
    }

    val swipeThreshold = with(density) { 72.dp.toPx() }
    var swipeOffset by remember { mutableFloatStateOf(0f) }
    val swipeSettle by animateFloatAsState(
        targetValue = swipeOffset,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "swipeOffset",
    )

    var pendingSeek by remember { mutableStateOf<Float?>(null) }

    val shownFraction: () -> Float = {
        when {
            scrubbing -> scrubValue
            pendingSeek != null -> pendingSeek!!
            duration > 0 -> (positionProvider().toFloat() / duration).coerceIn(0f, 1f)
            else -> 0f
        }
    }

    LaunchedEffect(duration, pendingSeek) {
        if (pendingSeek == null) return@LaunchedEffect
        snapshotFlow { positionProvider() }.collect { position ->
            val target = pendingSeek ?: return@collect
            if (duration > 0 && abs(position - (target * duration).toLong()) < SEEK_SETTLE_TOLERANCE_MS) {
                pendingSeek = null
            }
        }
    }
    LaunchedEffect(pendingSeek) {
        if (pendingSeek == null) return@LaunchedEffect
        delay(SEEK_SETTLE_TIMEOUT_MS)
        pendingSeek = null
    }
    LaunchedEffect(mediaMetadata.id) { pendingSeek = null }

    val artScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.86f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "artScale",
    )

    val audioManager = remember(context) {
        context.getSystemService(AudioManager::class.java)
    }
    val maxVolume = remember(audioManager) {
        audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 15
    }
    val scope = rememberCoroutineScope()

    val volume = remember {
        Animatable(
            (audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0).toFloat() / maxVolume,
        )
    }
    var volumeDragging by remember { mutableStateOf(false) }
    var systemVolume by remember { mutableFloatStateOf(volume.value) }

    LaunchedEffect(systemVolume) {
        if (!volumeDragging) {
            volume.animateTo(systemVolume, tween(durationMillis = 220, easing = FastOutSlowInEasing))
        }
    }

    DisposableEffect(audioManager) {
        val observer = object : android.database.ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                val current = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: return
                systemVolume = current.toFloat() / maxVolume
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI,
            true,
            observer,
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    val p by animateFloatAsState(
        targetValue = if (lyricsOpen || queueOpen) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "sleeveCollapse",
    )

    val heroMode = playerFillsWindow(LocalConfiguration.current.screenWidthDp.dp)

    val artUrl = remember(mediaMetadata.id, mediaMetadata.thumbnailUrl) {
        mediaMetadata.thumbnailUrl?.resize(width = ART_PX, height = ART_PX, maxresAllowed = true)
    }
    var artLoaded by remember(artUrl) { mutableStateOf(false) }

    var heroSettled by remember { mutableStateOf(false) }
    LaunchedEffect(artLoaded) {
        if (artLoaded) heroSettled = true
    }
    val heroT by animateFloatAsState(
        targetValue = if (heroMode && (artLoaded || heroSettled)) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "heroCanvas",
    )

    val heroVisible = heroT * (1f - p)

    var heroHeight by remember { mutableStateOf(0.dp) }

    val statusBarTop = LocalStableSystemBarsTopPadding.current

    val topStrip = DISMISS_STRIP_HEIGHT

    var dismissBandTop by remember { mutableFloatStateOf(0f) }
    var dismissBandBottom by remember { mutableFloatStateOf(0f) }
    val dismissBandSpace = remember { mutableStateOf<LayoutCoordinates?>(null) }

    val meshColors = rememberArtworkColors(artUrl)
    val artMesh = rememberArtworkMesh(artUrl)
    var meshSeamPx by remember { mutableFloatStateOf(0f) }

    val lyricsBackgroundStylePref by rememberEnumPreference(LyricsBackgroundStyleKey, LyricsBackgroundStyle.DEFAULT)
    val playerBackgroundStylePref by rememberEnumPreference(PlayerBackgroundStyleKey, PlayerBackgroundStyle.DEFAULT)
    val resolvedLyricsBackground = lyricsBackgroundStylePref.resolveFor(playerBackgroundStylePref)
    val lyricsUseStyledBackground = lyricsOpen && resolvedLyricsBackground != LyricsBackgroundStyle.DEFAULT

    val onPlayPause = {
        if (player.isPlaying) player.pause() else player.play()
    }

    val onSeekFraction: (Float) -> Unit = { f ->
        val livePlayer = playerConnection.player
        val d = livePlayer.duration
        if (d > 0 && d != androidx.media3.common.C.TIME_UNSET) {
            livePlayer.seekTo((f * d).toLong())
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (lyricsUseStyledBackground) {
            StyledLyricsBackground(
                style = resolvedLyricsBackground,
                mediaMetadata = mediaMetadata,
                gradientColors = meshColors.colors,
            )
        } else {
            val meshSeam = with(density) { meshSeamPx.toDp() }
            ArtworkMeshBackdrop(
                mesh = artMesh,
                seam = meshSeam,
                reduceAnimation = reduceAnimations,
            )
        }

        if (heroHeight > 0.dp) {
            if (heroMode && (p < 0.5f || heroVisible > 0.001f)) {
                var heroCanvasShowing by remember(canvasPrimaryUrl, canvasFallbackUrl) { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .height(heroHeight)
                        .graphicsLayer {
                            alpha = heroVisible

                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.Black, Color.Transparent),
                                    startY = size.height * (1f - HERO_FADE_FRACTION),
                                    endY = size.height,
                                ),
                                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                            )
                        },
                ) {
                    if (!canvasPrimaryUrl.isNullOrBlank() || !canvasFallbackUrl.isNullOrBlank()) {
                        CanvasArtworkPlayer(
                            primaryUrl = canvasPrimaryUrl,
                            fallbackUrl = canvasFallbackUrl,
                            isPlaying = isPlaying,
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                            onPlaybackAvailabilityChange = { heroCanvasShowing = it },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    val heroArtRequest = remember(artUrl) {
                        ImageRequest.Builder(context)
                            .data(artUrl)
                            .size(ART_PX)
                            .build()
                    }
                    if (!heroCanvasShowing) {
                        AsyncImage(
                            model = heroArtRequest,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            if (heroVisible > 0.01f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .height(statusBarTop + topStrip)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.38f * heroVisible),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets(top = statusBarTop))
                .navigationBarsPadding()
                .pointerInput(Unit) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragCancel = { swipeOffset = 0f },
                        onDragEnd = {
                            when {
                                total <= -swipeThreshold && canSkipNext -> {
                                    haptics.play(Haptic.SkipNext)
                                    playerConnection.seekToNext()
                                }
                                total >= swipeThreshold && canSkipPrevious -> {
                                    haptics.play(Haptic.SkipPrevious)
                                    playerConnection.seekToPrevious()
                                }
                            }
                            swipeOffset = 0f
                        },
                        onHorizontalDrag = { _, delta ->
                            total += delta

                            swipeOffset = total * 0.35f
                        },
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(topStrip),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(38.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.32f)),
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()

                    .onGloballyPositioned { coordinates ->
                        if (coordinates.isAttached) {
                            dismissBandSpace.value = coordinates
                        }
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)

                            val space = dismissBandSpace.value?.takeIf { it.isAttached }
                            val y = space
                                ?.let { it.positionInRoot().y + down.position.y }
                                ?: down.position.y

                            val panelUp = queueOpen || lyricsOpen ||
                                queueSlide.floatValue > 0.01f
                            val bandTop: Float
                            val bandBottom: Float
                            if (panelUp) {
                                bandTop = space?.positionInRoot()?.y ?: 0f
                                bandBottom = bandTop +
                                    (ART_BOX_TOP_PAD + HEADER_HEIGHT).toPx()
                            } else {
                                bandTop = dismissBandTop
                                bandBottom = dismissBandBottom
                            }
                            if (y >= bandTop && y <= bandBottom) {
                                if (!panelUp) {
                                    dragQueueIn(
                                        down = down,
                                        travel = bandBottom - bandTop -
                                            HEADER_HEIGHT.toPx(),
                                        slide = queueSlide,
                                        onHold = { queueDragging = it },
                                        onSettle = { open ->
                                            if (open != queueOpen) {
                                                haptics.play(
                                                    if (open) Haptic.Expand else Haptic.Tap,
                                                )
                                                queueOpen = open
                                            }
                                            queueReleased++
                                        },
                                    )
                                }
                                return@awaitEachGesture
                            }

                            val drag = awaitVerticalTouchSlopOrCancellation(down.id) { change, _ ->
                                change.consume()
                            }
                            if (drag != null) verticalDrag(drag.id) { it.consume() }
                        }
                    }
                    .padding(horizontal = PLAYER_GUTTER),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            var controlSpread by remember { mutableStateOf(lastControlSpread) }
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = PLAYER_MAX_WIDTH)
                    .fillMaxWidth()
                    .padding(top = ART_BOX_TOP_PAD, bottom = 18.dp),
            ) {
                val roomy = maxHeight + if (lyricsOpen) 0.dp else controlSpread

                val wantArt = minOf(maxWidth, roomy - ART_TITLE_GAP - HEADER_HEIGHT)

                val fullArt = minOf(wantArt, maxHeight - ART_TITLE_GAP - HEADER_HEIGHT)
                    .coerceAtLeast(THUMB_SIZE)

                val slack = (roomy - wantArt - ART_TITLE_GAP - HEADER_HEIGHT)
                    .coerceAtLeast(0.dp)
                if (!lyricsOpen) {
                    val target = with(density) {
                        val half = slack
                            .coerceAtMost(CONTROL_GAP_SPREAD_MAX * 2)
                            .toPx()
                            .div(2f)
                            .roundToInt()
                        (half * 2).toDp()
                    }

                    val granted = with(density) {
                        val steppedPx = (controlSpread.toPx() +
                            (target.toPx() - controlSpread.toPx()) * 0.4f)
                            .roundToInt()
                        steppedPx.toDp()
                    }
                    if (granted != controlSpread) {
                        SideEffect {
                            controlSpread = granted
                            lastControlSpread = granted
                        }
                    }
                }

                val groupTop = (maxHeight - fullArt - ART_TITLE_GAP - HEADER_HEIGHT)
                    .coerceAtLeast(0.dp) / 2
                val artSize = lerp(fullArt, THUMB_SIZE, p)
                val artTop = lerp(groupTop, 0.dp, p)

                val artStart = lerp((maxWidth - fullArt) / 2, 0.dp, p)
                val titleTop = lerp(groupTop + fullArt + ART_TITLE_GAP, 0.dp, p)
                val titleStart = lerp(0.dp, THUMB_SIZE + 12.dp, p)

                val bannerBottom = statusBarTop + topStrip + ART_BOX_TOP_PAD +
                    groupTop + fullArt + ART_TITLE_GAP / 2
                if (bannerBottom != heroHeight) {
                    SideEffect { heroHeight = bannerBottom }
                }

                Box(
                    modifier = Modifier

                        .offset { IntOffset(artStart.roundToPx(), artTop.roundToPx()) }
                        .size(artSize)

                        .onGloballyPositioned {
                            dismissBandTop = it.boundsInRoot().top
                            meshSeamPx = it.boundsInRoot().bottom
                        }

                        .then(
                            if (queueOpen || lyricsOpen) {
                                Modifier.clickable {
                                    queueOpen = false
                                    lyricsOpen = false
                                }
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = if (artLoaded) 1f - heroVisible else 1f }

                            .shadow(
                                if (artLoaded) lerp(10.dp, 6.dp, p) else 0.dp,
                                RoundedCornerShape(lerp(8.dp, 7.dp, p)),
                            )
                            .clip(RoundedCornerShape(lerp(8.dp, 7.dp, p)))
                            .background(Color.Black.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!artLoaded) {
                            Icon(
                                imageVector = BitChordIcons.MusicNote,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.35f),
                                modifier = Modifier.size(lerp(40.dp, 20.dp, p)),
                            )
                        }
                        var cardCanvasShowing by remember(canvasPrimaryUrl, canvasFallbackUrl) { mutableStateOf(false) }
                        if (!canvasPrimaryUrl.isNullOrBlank() || !canvasFallbackUrl.isNullOrBlank()) {
                            CanvasArtworkPlayer(
                                primaryUrl = canvasPrimaryUrl,
                                fallbackUrl = canvasFallbackUrl,
                                isPlaying = isPlaying,
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                                onPlaybackAvailabilityChange = { cardCanvasShowing = it },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        val cardArtRequest = remember(artUrl) {
                            ImageRequest.Builder(context)
                                .data(artUrl)
                                .size(ART_PX)
                                .build()
                        }
                        AsyncImage(

                            model = cardArtRequest,
                            contentDescription = null,

                            contentScale = ContentScale.Crop,
                            onState = { artLoaded = it is AsyncImagePainter.State.Success },
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = if (cardCanvasShowing) 1f / 255f else 1f
                                }
                                .graphicsLayer {
                                    val idle = artScale + (1f - artScale) * p
                                    scaleX = idle
                                    scaleY = idle
                                    translationX = swipeSettle * (1f - p)
                                },
                        )
                    }

                    if (automixOn && p < 0.5f) {
                        val currentStateLabel = localizedAnalysisState(automixAnalysis.current)
                        val nextStateLabel = localizedAnalysisState(automixAnalysis.next)
                        Text(
                            text = stringResource(
                                R.string.automix_analysis_status,
                                currentStateLabel,
                                nextStateLabel,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                    }
                }

                val swipeHintProgress = (abs(swipeSettle) / swipeThreshold)
                    .coerceIn(0f, 1f) * (1f - p)
                if (swipeHintProgress > 0.01f) {
                    val showNext = swipeSettle < 0f
                    val enabled = if (showNext) canSkipNext else canSkipPrevious
                    Icon(
                        imageVector = if (showNext) Icons.Rounded.FastForward else Icons.Rounded.FastRewind,
                        contentDescription = null,
                        tint = Color.White.copy(
                            alpha = swipeHintProgress * if (enabled) 0.85f else 0.3f,
                        ),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = artTop + artSize + (ART_TITLE_GAP - 16.dp) / 2)
                            .size(16.dp),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = titleTop)
                        .padding(start = titleStart)
                        .height(HEADER_HEIGHT)

                        .onGloballyPositioned { dismissBandBottom = it.boundsInRoot().bottom },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        val titleSize = lerp(20.sp, 16.sp, p)
                        Text(
                            text = mediaMetadata.title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = titleSize,
                            ),
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier
                                .basicMarquee(iterations = Int.MAX_VALUE)
                                .opensPage(
                                    mediaMetadata.album?.id,
                                    onOpen = { navController.navigate("album/${it}") },
                                ),
                        )
                        Text(
                            text = mediaMetadata.artists.joinToString(", ") { it.name },
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.W500,
                                fontSize = titleSize,
                            ),
                            color = Color.White.copy(alpha = 0.55f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.opensPage(
                                mediaMetadata.artists.firstOrNull()?.id,
                                onOpen = { navController.navigate("artist/${it}") },
                            ),
                        )
                    }
                    Spacer(Modifier.width(10.dp))

                    CircleGlyph(
                        icon = if (currentSongLiked) BitChordIcons.HeartFilled else BitChordIcons.Heart,
                        contentDescription = if (currentSongLiked) "Remove from Liked Music" else "Like",
                        onClick = { playerConnection.toggleLike() },
                        active = currentSongLiked,
                        haptic = if (currentSongLiked) Haptic.ToggleOff else Haptic.ToggleOn,
                    )
                    Spacer(Modifier.width(8.dp))
                    CircleGlyph(
                        icon = Icons.Rounded.MoreHoriz,
                        contentDescription = "More",
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
                    )
                }

                if (lyricsOpen) {
                    val panelModifier = Modifier
                        .fillMaxSize()
                        .padding(top = HEADER_HEIGHT + 10.dp)

                        .graphicsLayer {
                            alpha = ((p - 0.45f) / 0.55f).coerceIn(0f, 1f)
                            translationY = (1f - p) * 26.dp.toPx()
                        }

                    val latestScrubbing = rememberUpdatedState(scrubbing)
                    val latestDuration = rememberUpdatedState(duration)
                    val lyricsPositionProvider = remember {
                        {
                            if (latestScrubbing.value) {
                                val shown = shownFraction()
                                ((if (latestDuration.value > 0) shown / latestDuration.value else 0f) *
                                    maxOf(latestDuration.value, 1L)).toLong()
                            } else {
                                null
                            }
                        }
                    }
                    val lyricsMode by rememberEnumPreference(LyricsModeKey, LyricsMode.ENHANCED)
                    when (lyricsMode) {
                        LyricsMode.ENHANCED ->
                            LyricsEnhanced(
                                sliderPositionProvider = lyricsPositionProvider,
                                lyricsSyncOffset = lyricsSyncOffset,
                                modifier = panelModifier,
                                textColorOverride = Color.White,
                            )

                        LyricsMode.SPOTIFY ->
                            LyricsV2(
                                sliderPositionProvider = lyricsPositionProvider,
                                lyricsSyncOffset = lyricsSyncOffset,
                                modifier = panelModifier,
                                textColorOverride = Color.White,
                                spotifyStyle = true,
                            )

                        LyricsMode.V2 ->
                            LyricsV2(
                                sliderPositionProvider = lyricsPositionProvider,
                                lyricsSyncOffset = lyricsSyncOffset,
                                modifier = panelModifier,
                                textColorOverride = Color.White,
                            )
                    }
                }

                if (!lyricsOpen && queueProgress > 0.01f) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = HEADER_HEIGHT + 10.dp)
                            .graphicsLayer {
                                alpha = ((queueProgress - 0.45f) / 0.55f).coerceIn(0f, 1f)
                                translationY = (1f - queueProgress) * 26.dp.toPx()
                            },
                    ) {
                        InlineQueue(
                            queue = queue,
                            currentIndex = queueIndex,
                            onJumpTo = { index -> player.seekTo(index, 0) },
                            onRemove = { index -> player.removeMediaItem(index) },
                            onMove = { from, to -> player.moveMediaItem(from, to) },
                            onClear = {
                                val size = player.mediaItemCount
                                for (i in (size - 1) downTo (queueIndex + 1)) {
                                    player.removeMediaItem(i)
                                }
                                for (i in (queueIndex - 1) downTo 0) {
                                    player.removeMediaItem(i)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .widthIn(max = PLAYER_MAX_WIDTH)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()

                    .offset(y = 6.dp),
            ) {
                if (!lyricsOpen) {
                    if (!lyrics.isNullOrEmpty()) {
                        BitChordCurrentLyric(
                            lines = lyrics,
                            trackKey = mediaMetadata.id,
                            positionProvider = positionProvider,
                            lyricsSyncOffset = lyricsSyncOffset,
                            isPlaying = isPlaying,
                            durationMs = duration,

                            onClick = {
                                queueOpen = false
                                lyricsOpen = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            synced = lyricsSynced,
                        )
                    } else if (lyricsUnavailable) {
                        LyricsUnavailableLine(
                            trackKey = mediaMetadata.id,
                            modifier = Modifier.fillMaxWidth(),

                            onClick = { lyricsOpen = true },
                        )
                    } else {
                        LyricsLoadingLine(
                            trackKey = mediaMetadata.id,
                            modifier = Modifier.fillMaxWidth(),

                            onClick = { lyricsOpen = true },
                        )
                    }
                }
            }
            ThinSlider(
                valueProvider = shownFraction,
                onValueChange = {
                    scrubbing = true
                    scrubValue = it
                },
                onValueChangeFinished = {
                    haptics.play(Haptic.Select)
                    pendingSeek = scrubValue
                    onSeekFraction(scrubValue)
                    scrubbing = false
                },
                mixing = sliderMixing,
                transitionWindow = sliderTransitionWindow,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()

                    .offset(y = (-9).dp),
            ) {
                BitChordScrubTimes(shownFraction = shownFraction, duration = duration)

                LosslessOrStats(
                    isLoading = isLoading,
                    format = currentFormat,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 8.dp),
                )
            }

            if (!lyricsOpen) {
            Spacer(Modifier.height(8.dp + controlSpread / 2))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BitChordPreviousGlyph(
                    positionProvider = positionProvider,
                    canSkipPrevious = canSkipPrevious,
                    onClick = { playerConnection.seekToPrevious() },
                )

                if (isLoading) {
                    Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(38.dp),
                        )
                    }
                } else {
                    TransportGlyph(
                        icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        size = 74.dp,
                        touchSize = 92.dp,
                        onClick = onPlayPause,
                        haptic = if (isPlaying) Haptic.Pause else Haptic.Resume,
                    )
                }
                TransportGlyph(
                    icon = Icons.Rounded.FastForward,
                    contentDescription = "Next",
                    size = 53.dp,
                    touchSize = 53.dp,
                    heightScale = 0.85f,
                    onClick = { playerConnection.seekToNext() },
                    enabled = canSkipNext,
                    haptic = Haptic.SkipNext,
                )
            }

            Spacer(Modifier.height(12.dp + controlSpread / 2))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.VolumeDown,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                ThinSlider(
                    valueProvider = { volume.value },
                    onValueChange = {
                        volumeDragging = true

                        scope.launch { volume.snapTo(it) }
                        audioManager?.setStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            (it * maxVolume).roundToInt(),
                            0,
                        )
                    },
                    onValueChangeFinished = { volumeDragging = false },
                    idleHeight = 6.dp,
                    activeHeight = 10.dp,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                Icon(
                    Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(Modifier.height(6.dp))

            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val widestRow = BOTTOM_ACTION_SIZE * 2 + pillWidth(2)
                val edgeInset = ((maxWidth - widestRow) / 4).coerceAtLeast(0.dp)
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = edgeInset),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BottomGlyph(
                        icon = BitChordIcons.LyricsQuote,
                        contentDescription = if (lyricsOpen) "Close lyrics" else "Open lyrics",

                        onClick = {
                            queueOpen = false
                            lyricsOpen = !lyricsOpen
                        },
                        highlighted = lyricsOpen,
                        haptic = if (lyricsOpen) Haptic.Tap else Haptic.Expand,
                    )
                    Pill {
                        PillSegment(
                            icon = BitChordIcons.Shuffle,
                            contentDescription = if (shuffleEnabled) "Shuffle on" else "Shuffle off",
                            onClick = { player.shuffleModeEnabled = !shuffleEnabled },
                            highlighted = shuffleEnabled,
                            haptic = if (shuffleEnabled) Haptic.ToggleOff else Haptic.ToggleOn,
                        )
                        PillDivider()
                        PillSegment(
                            icon = if (repeatMode == Player.REPEAT_MODE_ONE) null else BitChordIcons.Repeat,
                            label = if (repeatMode == Player.REPEAT_MODE_ONE) "1" else null,
                            contentDescription = when (repeatMode) {
                                Player.REPEAT_MODE_ONE -> "Repeat one"
                                Player.REPEAT_MODE_ALL -> "Repeat all"
                                else -> "Repeat off"
                            },
                            onClick = {
                                player.repeatMode = when (repeatMode) {
                                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                    else -> Player.REPEAT_MODE_OFF
                                }
                            },
                            highlighted = repeatMode != Player.REPEAT_MODE_OFF,
                            haptic = when (repeatMode) {
                                Player.REPEAT_MODE_OFF -> Haptic.ToggleOn
                                Player.REPEAT_MODE_ONE -> Haptic.ToggleOff
                                else -> Haptic.Select
                            },
                        )
                    }
                    BottomGlyph(
                        icon = Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = "Up next",
                        onClick = {
                            lyricsOpen = false
                            queueOpen = !queueOpen
                        },
                        highlighted = queueOpen,
                        haptic = if (queueOpen) Haptic.Tap else Haptic.Expand,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            }
            }
            }
        }
    }
}

private const val LyricsEntityNotFound = "LYRICS_NOT_FOUND"

internal fun playerFillsWindow(windowWidth: Dp): Boolean =
    windowWidth <= PLAYER_MAX_WIDTH + PLAYER_GUTTER * 2

private suspend fun AwaitPointerEventScope.dragQueueIn(
    down: PointerInputChange,
    travel: Float,
    slide: MutableFloatState,
    onHold: (Boolean) -> Unit,
    onSettle: (Boolean) -> Unit,
) {
    if (travel < 1f) return

    var pulled = 0f
    val drag = awaitVerticalTouchSlopOrCancellation(down.id) { change, overSlop ->
        if (overSlop < 0f) {
            pulled = -overSlop
            change.consume()
        }
    }
    if (drag == null || pulled <= 0f) return

    onHold(true)
    val velocity = VelocityTracker()
    velocity.addPointerInputChange(drag)
    slide.floatValue = (pulled / travel).coerceIn(0f, 1f)
    verticalDrag(drag.id) { change ->
        velocity.addPointerInputChange(change)
        pulled -= change.positionChange().y
        slide.floatValue = (pulled / travel).coerceIn(0f, 1f)
        change.consume()
    }

    val flick = -velocity.calculateVelocity().y
    val open = when {
        flick >= QUEUE_FLICK_VELOCITY -> true
        flick <= -QUEUE_FLICK_VELOCITY -> false
        else -> slide.floatValue >= QUEUE_CARRY_FRACTION
    }
    onHold(false)
    onSettle(open)
}

@Composable
private fun CircleGlyph(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    active: Boolean = false,
    haptic: Haptic = Haptic.Tap,
) {
    val haptics = rememberHaptics()
    val discAlpha by animateFloatAsState(
        targetValue = if (active) 0.34f else 0.18f,
        label = "glyphDisc",
    )
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = discAlpha))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptics.play(haptic)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(19.dp),
        )
    }
}

@Composable
private fun TransportGlyph(
    icon: ImageVector,
    contentDescription: String,
    size: Dp,
    onClick: () -> Unit,
    enabled: Boolean = true,
    haptic: Haptic = Haptic.Tap,
    touchSize: Dp = size + 12.dp,
    heightScale: Float = 1f,
) {
    val haptics = rememberHaptics()

    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.3f,
        label = "transportAlpha",
    )
    Box(
        modifier = Modifier
            .size(touchSize)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
            ) {
                haptics.play(haptic)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = alpha),
            modifier = Modifier
                .size(size)
                .graphicsLayer { scaleY = heightScale },
        )
    }
}

@Composable
private fun BottomGlyph(
    icon: ImageVector?,
    contentDescription: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
    haptic: Haptic = Haptic.Tap,
    label: String? = null,
) {
    val haptics = rememberHaptics()
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(
                if (highlighted) Color.White.copy(alpha = 0.20f) else Color.Transparent,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptics.play(haptic)
                onClick()
            }
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        val tint = Color.White.copy(alpha = if (highlighted) 1f else 0.75f)
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(26.dp),
            )
        } else if (label != null) {
            Text(
                text = label,
                color = tint,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun Modifier.opensPage(browseId: String?, onOpen: (String) -> Unit): Modifier =
    if (browseId == null) {
        this
    } else {
        clip(RoundedCornerShape(6.dp)).clickable { onOpen(browseId) }
    }

private val BOTTOM_ACTION_SIZE = 44.dp

private val PILL_SEGMENT_WIDTH = 64.dp

private val PILL_ICON_SIZE = 24.dp

private fun pillWidth(segments: Int): Dp =
    PILL_SEGMENT_WIDTH * segments + 1.dp * (segments - 1)

@Composable
private fun Pill(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .height(BOTTOM_ACTION_SIZE)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f)),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun PillDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(20.dp)
            .background(Color.White.copy(alpha = 0.20f)),
    )
}

@Composable
private fun PillSegment(
    contentDescription: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    iconSize: Dp = PILL_ICON_SIZE,
    label: String? = null,
    highlighted: Boolean = false,
    haptic: Haptic = Haptic.Tap,
) {
    val haptics = rememberHaptics()
    Box(
        modifier = Modifier
            .width(PILL_SEGMENT_WIDTH)
            .height(BOTTOM_ACTION_SIZE)
            .background(if (highlighted) Color.White.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptics.play(haptic)
                onClick()
            }
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        val tint = Color.White.copy(alpha = if (highlighted) 1f else 0.75f)
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(iconSize),
            )
        } else if (label != null) {
            Text(
                text = label,
                color = tint,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return "%d:%02d".format(Locale.ROOT, minutes, seconds)
}

@Composable
private fun localizedAnalysisState(state: AutoMixAnalysisState): String =
    stringResource(
        when (state) {
            AutoMixAnalysisState.ANALYSED -> R.string.automix_state_analysed
            AutoMixAnalysisState.ANALYSING -> R.string.automix_state_analysing
            AutoMixAnalysisState.FAILED -> R.string.automix_state_failed
            AutoMixAnalysisState.WAITING -> R.string.automix_state_waiting
        },
    )

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object OverlayBack {
    fun register(view: View, onBack: () -> Unit): Any? {
        val dispatcher = view.findOnBackInvokedDispatcher() ?: return null
        val callback = OnBackInvokedCallback { onBack() }
        dispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_OVERLAY,
            callback,
        )
        return callback
    }

    fun unregister(view: View, callback: Any?) {
        if (callback !is OnBackInvokedCallback) return
        view.findOnBackInvokedDispatcher()?.unregisterOnBackInvokedCallback(callback)
    }
}

@Composable
private fun BitChordScrubTimes(
    shownFraction: () -> Float,
    duration: Long,
) {
    val shown = shownFraction()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = formatTime((shown * duration).toLong()),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.55f),
        )
        Text(
            text = "-" + formatTime(duration - (shown * duration).toLong()),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.55f),
        )
    }
}

@Composable
private fun BitChordPreviousGlyph(
    positionProvider: () -> Long,
    canSkipPrevious: Boolean,
    onClick: () -> Unit,
) {
    TransportGlyph(
        icon = Icons.Rounded.FastRewind,
        contentDescription = "Previous",
        size = 53.dp,
        touchSize = 53.dp,
        heightScale = 0.85f,
        onClick = onClick,

        enabled = canSkipPrevious || positionProvider() > BACK_RESTARTS_AFTER_MS,
        haptic = Haptic.SkipPrevious,
    )
}

@Composable
private fun BitChordCurrentLyric(
    lines: List<LyricLine>,
    trackKey: Any,
    positionProvider: () -> Long,
    lyricsSyncOffset: Int,
    isPlaying: Boolean,
    durationMs: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    synced: Boolean = true,
) {
    CurrentLyricLine(
        lines = lines,
        trackKey = trackKey,
        positionMs = (positionProvider() + lyricsSyncOffset.toLong()).coerceAtLeast(0L),
        isPlaying = isPlaying,
        durationMs = durationMs,
        onClick = onClick,
        modifier = modifier,
        synced = synced,
    )
}
