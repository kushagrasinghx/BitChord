package com.music.bitchord.ui.components

import android.Manifest
import android.view.HapticFeedbackConstants
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.shazam.RecognitionSession
import com.music.bitchord.data.shazam.RecognitionSessionState
import com.music.bitchord.data.shazam.ShazamTrack
import com.music.bitchord.ui.components.backdrop.drawBackdrop
import com.music.bitchord.ui.components.backdrop.effects.blur
import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.ui.components.backdrop.effects.colorControls
import com.music.bitchord.ui.screens.TopResultBounds
import coil3.compose.AsyncImage
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

private val RecognitionTop = Color(0xFF1D1D20)
private val RecognitionBottom = Color(0xFF0A0A0B)
// The sheet is always dark, so the orb uses the same faint white lift as the
// dark-theme nav bar. A black tint would read as a hole rather than glass.
private val RecognitionGlassTint = Color.White.copy(alpha = 0.10f)
private val RecognitionGlassFallback = Color.White.copy(alpha = 0.09f)
private val RecognitionControlFallback = Color.White.copy(alpha = 0.07f)

/** Critically damped, like the player sheet: brisk out, no bounce at either end. */
private const val SHEET_STIFFNESS = 195f

/** The slide-away alone runs ~2x stiffer — the same spring, one notch earlier home. */
private const val SHEET_DISMISS_STIFFNESS = 380f
private const val SHEET_SETTLED_PX = 0.5f
private const val SHEET_CLOSE_FRACTION = 0.24f
private const val SHEET_FALLBACK_HEIGHT_DP = 800f

/** How fast a downward flick has to be, in dp per second, to close on its own. */
private const val SHEET_FLING_DP = 720f

/** The rounded top edge, matching the sheets elsewhere in the app. */
private val RecognitionSheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/** Air between the status bar and the sheet, so it reads as a sheet, not a page. */
private val SHEET_TOP_GAP = 16.dp

/** How long a match is held on screen before it starts moving to Search. */
private const val RESULT_DWELL_MS = 1_050L

/**
 * The beat the sheet gives Search to promote the card the query becomes. The
 * flight lands on that card when it arrives; a slow or unpromoted search falls
 * back to the field that started the listen.
 */
private const val TOP_RESULT_WAIT_MS = 5_000L

/** The flight from the sheet to the search field, and how small it lands. */
private const val RESULT_FLIGHT_MS = 620
private const val RESULT_FLIGHT_END_SCALE = 0.16f

/** The last stretch of the flight, where it dissolves into the real result. */
private const val HANDOFF_FADE_START = 0.82f

/** The matched card's pieces as they sit on the sheet, before it lifts. */
private data class FoundLayout(
    val artwork: Rect,
    val title: Rect,
    val artist: Rect?,
)

/**
 * The matched card in flight: what it carries, the card it detaches from, and
 * where it lands. [layout] and [target] are only both present when Search has
 * already promoted the matching result, which is the element-by-element path.
 */
private data class FoundHandoff(
    val track: ShazamTrack,
    val card: Rect,
    val layout: FoundLayout?,
    val target: TopResultBounds?,
)

/** The live microphone has not been tapped yet, so the permission prompt owns the screen. */
private enum class MicrophoneAccess { Waiting, Granted, Denied }

/**
 * YouTube Music's "search a song" surface, opened by the microphone in the
 * search bar.
 *
 * The sheet owns the microphone grant for the same reason the search field
 * does: the first tap is the moment that permission makes sense, and the
 * listening state is this app's UI rather than the system speech recognizer's.
 * While the grant is live, [RecognitionSession] streams the mic level into the
 * orb, so the rings and bars answer the sound in the room instead of running on
 * a fixed loop, and searches the same recording at 4, 8 and 12 seconds. If the
 * grant is refused the sheet stays useful by explaining what it needs and
 * offering the way back to the system toggle instead of dropping a toast and
 * closing.
 *
 * A match is held for a beat and then handed to Search while [onSearch] loads
 * the results behind the sheet. If that search promotes the matching song, the
 * cover, title and artist fly into the promoted card's own rectangles and
 * dissolve into it; otherwise the card falls back to [searchFieldBounds], the
 * field it was started from. The Search tab still owns the final choice rather
 * than the recognizer guessing which upload is the right one.
 */
@Composable
fun SongRecognitionDialog(
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit,
    searchFieldBounds: Rect?,
    topResultBounds: TopResultBounds?,
    onLanded: (onCard: Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = remember(scope) { RecognitionSession(scope) }
    val density = LocalDensity.current
    val reduceMotion by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // The sheet follows the finger down, then either springs back or carries on
    // out of the window — the same shape as the player's dismiss.
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var sheetHeight by remember { mutableFloatStateOf(0f) }
    var dismissing by remember { mutableStateOf(false) }
    // The matched card's place on screen, then the same card detached into a
    // flight of its own once the Search tab has been asked for results. The
    // three rects are measured separately so each piece can land on its own
    // counterpart in Search's promoted result.
    var resultBounds by remember { mutableStateOf<Rect?>(null) }
    var foundArtworkBounds by remember { mutableStateOf<Rect?>(null) }
    var foundTitleBounds by remember { mutableStateOf<Rect?>(null) }
    var foundArtistBounds by remember { mutableStateOf<Rect?>(null) }
    var handoff by remember { mutableStateOf<FoundHandoff?>(null) }
    var scrimAlpha by remember { mutableFloatStateOf(0f) }
    val flight = remember { Animatable(0f) }
    var rootWidth by remember { mutableFloatStateOf(0f) }

    val flingVelocity = with(density) { SHEET_FLING_DP.dp.toPx() }
    val fallbackHeight = with(density) { SHEET_FALLBACK_HEIGHT_DP.dp.toPx() }

    fun animateSheetTo(target: Float, velocity: Float, after: (() -> Unit)? = null, stiffness: Float = SHEET_STIFFNESS) {
        scope.launch {
            animate(
                initialValue = dragOffset,
                targetValue = target,
                initialVelocity = velocity,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = stiffness,
                    visibilityThreshold = SHEET_SETTLED_PX,
                ),
            ) { value, _ -> dragOffset = value }
            after?.invoke()
        }
    }

    val dismiss: () -> Unit = {
        if (!dismissing) {
            dismissing = true
            if (reduceMotion) {
                onDismiss()
            } else {
                animateSheetTo(sheetHeight.takeIf { it > 0f } ?: fallbackHeight, 0f, onDismiss, SHEET_DISMISS_STIFFNESS)
            }
        }
    }
    // The rationale question can only be put to an Activity; the Compose
    // context normally is one, but wrapping it keeps this safe in previews and
    // inside other context wrappers.
    val activity = remember(context) { context.findActivity() }
    var access by remember {
        mutableStateOf(
            if (context.hasMicrophone()) MicrophoneAccess.Granted else MicrophoneAccess.Waiting,
        )
    }
    // False when Android will no longer show the request itself — the second
    // refusal, or "don't ask again" — which is when Settings is the only door
    // left. The callback and the resume check both refresh it.
    var canAskAgain by remember { mutableStateOf(true) }
    val requestMicrophone = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        access = if (granted) MicrophoneAccess.Granted else MicrophoneAccess.Denied
        canAskAgain = !granted && activity?.canAskForMicrophone() == true
    }
    LaunchedEffect(Unit) {
        if (!context.hasMicrophone()) requestMicrophone.launch(Manifest.permission.RECORD_AUDIO)
    }
    // Only a granted sheet owns the microphone. This also closes the recorder
    // when the permission prompt pushes the activity down mid-request.
    LaunchedEffect(access) {
        if (access == MicrophoneAccess.Granted && context.hasMicrophone()) {
            session.start()
        } else {
            session.stop()
        }
    }
    DisposableEffect(session) {
        onDispose { session.stop() }
    }
    val level by session.level.collectAsStateWithLifecycle()
    val state by session.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        session.stop()
    }
    // Coming back from the system prompt, or from Settings after the user
    // flipped the switch there, is the only way the grant can change without
    // this sheet doing anything. A revoked grant falls back to the explanation
    // rather than leaving a listening animation running with no microphone.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val granted = context.hasMicrophone()
        if (granted) {
            access = MicrophoneAccess.Granted
            // A result or a settled "no match" is not interrupted listening;
            // only restart a take that the pause cut off.
            if (state == RecognitionSessionState.Listening || state == RecognitionSessionState.Searching) {
                session.start()
            }
        } else if (access == MicrophoneAccess.Granted) {
            access = MicrophoneAccess.Denied
        }
        canAskAgain = activity?.canAskForMicrophone() == true
    }

    // A match does not wait for a Search tap. It is held for a beat, the
    // results are asked for behind the sheet, and then the card flies into the
    // promoted result — or, when Search has nothing to promote, to the field
    // that started the listen — while the sheet leaves underneath it. By the
    // time it lands, Search is already the page behind.
    val foundTrack = (state as? RecognitionSessionState.Found)?.track
    val view = LocalView.current
    // The effect below starts before Search has reported anything, so it has to
    // read the newest bounds through a state holder rather than the value this
    // composition launched with.
    val latestTopResultBounds by rememberUpdatedState(topResultBounds)
    LaunchedEffect(foundTrack?.searchQuery) {
        val track = foundTrack ?: return@LaunchedEffect
        // A soft tick says "I heard you" the instant a match resolves.
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        // Kick the search off immediately, in parallel with the dwell, so the
        // promoted card is usually already on screen when the beat ends. The
        // dwell becomes a moment to admire the match rather than dead time.
        onSearch(track.searchQuery)
        if (reduceMotion) {
            onDismiss()
            return@LaunchedEffect
        }
        delay(RESULT_DWELL_MS)
        // A back press or a swipe during the beat already owns the exit.
        if (dismissing) return@LaunchedEffect
        // Search loads behind the sheet. Wait for its promoted card so the
        // pieces have somewhere exact to land; without it (slow network, no
        // top hit) the card still leaves for the field that was tapped.
        val promoted = withTimeoutOrNull(TOP_RESULT_WAIT_MS) {
            snapshotFlow { latestTopResultBounds }.first { bounds ->
                bounds != null && bounds.matches(track)
            }
        }
        if (dismissing) return@LaunchedEffect
        val card = resultBounds
        val artwork = foundArtworkBounds
        val title = foundTitleBounds
        val layout = if (artwork != null && title != null) {
            FoundLayout(artwork = artwork, title = title, artist = foundArtistBounds)
        } else {
            null
        }
        val travel = sheetHeight.takeIf { it > 0f } ?: fallbackHeight
        dismissing = true
        if (card != null) {
            handoff = FoundHandoff(track, card, layout, promoted)
            flight.snapTo(0f)
        }
        val startDim = scrimAlpha
        coroutineScope {
            launch {
                animate(
                    initialValue = dragOffset,
                    targetValue = travel,
                    initialVelocity = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = SHEET_STIFFNESS,
                        visibilityThreshold = SHEET_SETTLED_PX,
                    ),
                ) { value, _ -> dragOffset = value }
            }
            if (card != null) {
                launch {
                    // Let the sheet begin to recede first, so the card lifts
                    // out of a surface that is already making room for it.
                    delay(80)
                    flight.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = 400f,
                            visibilityThreshold = 0.001f,
                        ),
                    )
                    // A soft tick marks the moment the match lands on its result.
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onLanded(promoted != null)
                }
            }
            // Let the page brighten as the sheet leaves, so dismissing the
            // window at the end does not snap the scrim off in one frame.
            if (startDim > 0f) {
                launch {
                    animate(
                        initialValue = startDim,
                        targetValue = 0f,
                        animationSpec = tween(RESULT_FLIGHT_MS),
                    ) { value, _ ->
                        scrimAlpha = value
                    }
                }
            }
        }
        onDismiss()
    }

    // Rendered in-composition (not as a separate window) so the Search page
    // behind it keeps receiving layout passes and can report its Top result
    // rectangles for the handoff.
    BackHandler(enabled = true) { dismiss() }
    LaunchedEffect(Unit) {
        animate(0f, 0.5f) { v, _ -> scrimAlpha = v }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { rootWidth = it.width.toFloat() },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha)),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { rootWidth = it.width.toFloat() },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // A sheet, not a page: the status bar and a little air stay
                    // outside it, and the dimmed app shows through the gap.
                    .padding(top = statusBarTop + SHEET_TOP_GAP)
                    .onSizeChanged { sheetHeight = it.height.toFloat() }
                    .graphicsLayer { translationY = dragOffset }
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            if (!dismissing) {
                                dragOffset = (dragOffset + delta).coerceAtLeast(0f)
                            }
                        },
                        onDragStopped = { velocity ->
                            if (!dismissing) {
                                val travel = sheetHeight.takeIf { it > 0f } ?: fallbackHeight
                                val flick = abs(velocity) >= flingVelocity
                                val close = if (flick) {
                                    velocity > 0f
                                } else {
                                    dragOffset > travel * SHEET_CLOSE_FRACTION
                                }
                                when {
                                    close && reduceMotion -> onDismiss()
                                    close -> {
                                        dismissing = true
                                        animateSheetTo(travel, velocity.coerceAtLeast(0f), onDismiss)
                                    }
                                    reduceMotion -> dragOffset = 0f
                                    else -> animateSheetTo(0f, velocity)
                                }
                            }
                        },
                    )
                    .clip(RecognitionSheetShape)
                    .recognitionSheetSurface(RecognitionSheetShape),
            ) {
                // A soft halo under the control, so the glass orb does not sit on
                // a flat black field while the rings travel out from it.
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(360.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color.White.copy(alpha = 0.07f), Color.Transparent),
                            ),
                        ),
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                            ),
                        )
                        .padding(horizontal = 24.dp),
                ) {
                    // The same grip the player wears, at the top edge of the sheet:
                    // it names the gesture and gives the eye one thing to grab.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(width = 38.dp, height = 5.dp)
                                .shadow(2.dp, RoundedCornerShape(3.dp), clip = false)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.White.copy(alpha = 0.55f)),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.recognition_title),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = dismiss,
                            modifier = Modifier
                                .size(40.dp)
                                .recognitionGlassControl(CircleShape, RecognitionControlFallback),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.close),
                                tint = Color.White.copy(alpha = 0.78f),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (access) {
                            MicrophoneAccess.Waiting, MicrophoneAccess.Granted -> when (val current = state) {
                                RecognitionSessionState.Listening ->
                                    ListeningContent(level = level, searching = false)
                                RecognitionSessionState.Searching ->
                                    ListeningContent(level = 0f, searching = true)
                                is RecognitionSessionState.Found -> if (handoff == null) {
                                    // Measured once and handed to the flight
                                    // overlay when the dwell ends. While the
                                    // card is in the air this branch is empty,
                                    // so there is never a second copy of it on
                                    // the sheet.
                                    Box(
                                        Modifier.onGloballyPositioned {
                                            resultBounds = it.boundsInWindow()
                                        },
                                    ) {
                                        FoundContent(
                                            track = current.track,
                                            onArtworkBounds = { foundArtworkBounds = it },
                                            onTitleBounds = { foundTitleBounds = it },
                                            onArtistBounds = { foundArtistBounds = it },
                                        )
                                    }
                                }
                                RecognitionSessionState.NoMatch -> RecognitionMessageContent(
                                    icon = Icons.Rounded.MusicOff,
                                    title = stringResource(R.string.no_results),
                                    body = stringResource(R.string.recognition_no_match_hint),
                                    actionLabel = stringResource(R.string.try_again),
                                    onAction = { session.start() },
                                )
                                RecognitionSessionState.Failed -> RecognitionMessageContent(
                                    icon = Icons.Rounded.CloudOff,
                                    title = stringResource(R.string.failed),
                                    body = stringResource(R.string.recognition_failed_hint),
                                    actionLabel = stringResource(R.string.try_again),
                                    onAction = { session.start() },
                                )
                            }
                            MicrophoneAccess.Denied -> MicrophoneDeniedContent(
                                canAskAgain = canAskAgain,
                                onEnable = {
                                    if (canAskAgain) {
                                        requestMicrophone.launch(Manifest.permission.RECORD_AUDIO)
                                    } else {
                                        context.openAppSettings()
                                    }
                                },
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 12.dp)
                            .recognitionGlassControl(
                                shape = RoundedCornerShape(percent = 50),
                                fallback = RecognitionControlFallback,
                            )
                            .clickable(onClick = dismiss)
                            .padding(horizontal = 22.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.cancel),
                            color = Color.White.copy(alpha = 0.76f),
                            fontSize = 15.sp,
                        )
                    }
                }
            }
            // The resting card is hidden the moment it lifts; this is that
            // same card in the root, outside the sheet's clip. When Search has
            // promoted the matching song the pieces land on its own result;
            // otherwise the whole card still leaves for the field that asked
            // for it. The sheet slides away underneath either way.
            val flying = handoff
            if (flying != null) {
                val progress = flight.value
                val layout = flying.layout
                val promoted = flying.target
                if (layout != null && promoted != null) {
                    FoundFlight(
                        track = flying.track,
                        layout = layout,
                        target = promoted,
                        progress = progress,
                    )
                } else {
                    val target = searchFieldBounds?.center ?: Offset(
                        x = rootWidth / 2f,
                        y = with(density) { (statusBarTop + 41.dp).toPx() },
                    )
                    val scale = 1f + (RESULT_FLIGHT_END_SCALE - 1f) * progress
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x = flying.card.left.roundToInt(),
                                    y = flying.card.top.roundToInt(),
                                )
                            }
                            .width(with(density) { flying.card.width.toDp() })
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = (target.x - flying.card.center.x) * progress
                                translationY = (target.y - flying.card.center.y) * progress
                                // Held readable for most of the flight, then
                                // dissolved into the field it lands on.
                                alpha = ((1f - progress) / (1f - HANDOFF_FADE_START))
                                    .coerceIn(0f, 1f)
                            },
                    ) {
                        FoundContent(track = flying.track)
                    }
                }
            }
        }
    }
}

/**
 * The listening orb. The pulse keeps time, but [level] decides how bright and
 * wide the rings travel, how much the orb swells, and how high the live bars
 * inside it jump. [searching] swaps the bars for a spinner once the microphone
 * has been released and the last fingerprint is on its way to Shazam.
 */
@Composable
private fun ListeningContent(level: Float, searching: Boolean) {
    // The level itself is feedback, so it keeps moving even under "reduce
    // animation" — but it snaps rather than eases, and the decorative pulse,
    // breathing and idle bar wobble stop.
    val reduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val animatedLevel by animateFloatAsState(
        targetValue = level.coerceIn(0f, 1f),
        animationSpec = if (reduceAnimation) {
            snap()
        } else {
            spring(
                stiffness = Spring.StiffnessMediumLow,
                dampingRatio = 0.78f,
            )
        },
        label = "recognition-level",
    )
    val transition = rememberInfiniteTransition(label = "song-recognition")
    val pulseState = if (reduceAnimation) {
        null
    } else {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2400, easing = LinearEasing),
            ),
            label = "pulse",
        )
    }
    val breatheState = if (reduceAnimation) {
        null
    } else {
        transition.animateFloat(
            initialValue = 0.99f,
            targetValue = 1.03f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "breathe",
        )
    }
    val pulse = pulseState?.value ?: 0f
    val breathe = breatheState?.value ?: 1f
    val microphoneDescription = stringResource(R.string.recognition_microphone)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(340.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val inner = size.minDimension * 0.22f
                val outer = size.minDimension * 0.49f
                // The glow behind the orb rises with the room, so even the
                // quietest reading still leaves a soft footprint.
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.04f + animatedLevel * 0.12f),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = size.minDimension * 0.36f,
                    ),
                    radius = size.minDimension * 0.36f,
                )
                repeat(3) { index ->
                    val progress = (pulse + index / 3f) % 1f
                    val radius = inner + (outer - inner) * progress
                    val alpha = (0.10f + animatedLevel * 0.30f) * (1f - progress)
                    drawCircle(
                        color = Color.White.copy(alpha = alpha),
                        radius = radius,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(168.dp)
                    .graphicsLayer {
                        val scale = breathe + animatedLevel * 0.06f
                        scaleX = scale
                        scaleY = scale
                    }
                    .recognitionGlass(CircleShape)
                    .semantics { contentDescription = microphoneDescription },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.08f + animatedLevel * 0.22f),
                                Color.Transparent,
                            ),
                            center = center,
                            radius = size.minDimension * 0.50f,
                        ),
                        radius = size.minDimension * 0.50f,
                    )
                }
                if (searching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(52.dp),
                        color = Color.White.copy(alpha = 0.82f),
                        strokeWidth = 3.dp,
                    )
                } else {
                    LiveWaveform(
                        level = animatedLevel,
                        animate = !reduceAnimation,
                        modifier = Modifier.size(width = 84.dp, height = 58.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(
                if (searching) R.string.recognition_searching else R.string.recognition_listening,
            ),
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        if (!searching) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.recognition_hint),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Five rounded bars that move with the live level; never completely still. */
@Composable
private fun LiveWaveform(level: Float, animate: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "recognition-waveform")
    val phaseState = if (animate) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1400, easing = LinearEasing),
            ),
            label = "waveform-phase",
        )
    } else {
        null
    }
    val phase = phaseState?.value ?: 0f
    Canvas(modifier) {
        val bars = 5
        val gap = size.width * 0.10f
        val barWidth = (size.width - gap * (bars - 1)) / bars
        val maxHeight = size.height * 0.94f
        val alpha = 0.62f + level * 0.38f
        repeat(bars) { index ->
            val wave = 0.30f + 0.70f * abs(sin(phase + index * 0.85f))
            val idleHeight = size.height * (0.22f + 0.12f * wave)
            val height = idleHeight + (maxHeight - idleHeight) * level * wave
            val left = index * (barWidth + gap)
            drawRoundRect(
                color = Color.White.copy(alpha = alpha),
                topLeft = Offset(left, (size.height - height) / 2f),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}

/**
 * The matched song. Recognition never auto-plays and no longer waits for a
 * Search tap: after a beat the sheet hands the card to Search by itself, where
 * the listener still picks the upload they actually want. The same composable
 * draws the card at rest on the sheet and, once detached, while it flies to the
 * search field — which is why it carries no buttons or state of its own.
 */
@Composable
private fun FoundContent(
    track: ShazamTrack,
    onArtworkBounds: (Rect) -> Unit = {},
    onTitleBounds: (Rect) -> Unit = {},
    onArtistBounds: (Rect) -> Unit = {},
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 8.dp),
    ) {
        FoundArtwork(
            track = track,
            modifier = Modifier
                .size(190.dp)
                .onGloballyPositioned { onArtworkBounds(it.boundsInWindow()) },
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = track.title,
            color = Color.White,
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.onGloballyPositioned { onTitleBounds(it.boundsInWindow()) },
        )
        if (track.artist.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = track.artist,
                color = Color.White.copy(alpha = 0.62f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.onGloballyPositioned { onArtistBounds(it.boundsInWindow()) },
            )
        }
    }
}

/** The cover square, shared by the resting card and the flight overlay. */
@Composable
private fun FoundArtwork(track: ShazamTrack, modifier: Modifier = Modifier) {
    val artwork = track.artworkUrl
    Box(
        modifier = modifier.recognitionGlass(RoundedCornerShape(28.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (!artwork.isNullOrBlank()) {
            AsyncImage(
                model = artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(28.dp)),
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.72f),
                modifier = Modifier.size(56.dp),
            )
        }
    }
}

/**
 * The match on its way into Search's promoted result. Each piece keeps its own
 * measured rectangle and is carried to the corresponding rectangle in that
 * card, so the cover lands on the cover and the title lands on the title rather
 * than the whole card shrinking into a single point. The real result is already
 * underneath; the last stretch fades the fliers into it.
 */
@Composable
private fun FoundFlight(
    track: ShazamTrack,
    layout: FoundLayout,
    target: TopResultBounds,
    progress: Float,
) {
    val colors = MaterialTheme.colorScheme
    // The cover leads, the title follows, the artist closes the sequence: a
    // cascade reads as intentional rather than three things moving at once.
    val pCover = progress
    val pTitle = ((progress - 0.10f) / 0.90f).coerceIn(0f, 1f)
    val pArtist = ((progress - 0.20f) / 0.80f).coerceIn(0f, 1f)
    val alpha = ((1f - progress) / (1f - HANDOFF_FADE_START)).coerceIn(0f, 1f)
    FlightPiece(layout.artwork, target.artwork, pCover, alpha) {
        FoundArtwork(track = track, modifier = Modifier.fillMaxSize())
    }
    FlightPiece(layout.title, target.title, pTitle, alpha) {
        Text(
            text = track.title,
            color = lerp(Color.White, colors.onSurface, pTitle),
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxSize(),
        )
    }
    val artistStart = layout.artist
    if (artistStart != null && track.artist.isNotBlank()) {
        FlightPiece(artistStart, target.artist, pArtist, alpha) {
            Text(
                text = track.artist,
                color = lerp(Color.White.copy(alpha = 0.62f), colors.onSurfaceVariant, pArtist),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * One piece of [FoundFlight]. It is laid out at [start], then translated and
 * scaled from its top-left corner until its bounds equal [target] at
 * `progress == 1f`.
 */
@Composable
private fun FlightPiece(
    start: Rect,
    target: Rect,
    progress: Float,
    alpha: Float,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val targetScaleX = if (start.width > 0f) target.width / start.width else 1f
    val targetScaleY = if (start.height > 0f) target.height / start.height else 1f
    Box(
        modifier = Modifier
            .offset { IntOffset(start.left.roundToInt(), start.top.roundToInt()) }
            .size(
                width = with(density) { start.width.toDp() },
                height = with(density) { start.height.toDp() },
            )
            .graphicsLayer {
                transformOrigin = TransformOrigin(0f, 0f)
                translationX = (target.left - start.left) * progress
                translationY = (target.top - start.top) * progress
                scaleX = 1f + (targetScaleX - 1f) * progress
                scaleY = 1f + (targetScaleY - 1f) * progress
                this.alpha = alpha
            },
    ) {
        content()
    }
}

/**
 * Whether the promoted card is the song the recognizer heard. Only the title is
 * compared: the search query carries the artist already, and the promoted cards
 * read YouTube's title rather than Shazam's, so an exact artist match would
 * cost the handoff more often than it would protect it.
 */
private fun TopResultBounds.matches(track: ShazamTrack): Boolean {
    val heard = track.title.matchKey()
    val promoted = songTitle.matchKey()
    if (heard.isEmpty() || promoted.isEmpty()) return false
    if (heard == promoted || promoted.contains(heard) || heard.contains(promoted)) return true
    // Fall back to the first significant word: YouTube and Shazam often disagree
    // on subtitles ("(Official Video)", "feat. ..."), so the shared lead word
    // is the most stable signal that this is the same song.
    val heardFirst = heard.split(' ').firstOrNull { it.length >= 4 }
    val promotedFirst = promoted.split(' ').firstOrNull { it.length >= 4 }
    return heardFirst != null && heardFirst == promotedFirst
}

/** Folds a title to lowercase words so a parenthetical still matches its song. */
private fun String.matchKey(): String = lowercase(Locale.ROOT)
    .replace(Regex("\\([^)]*\\)"), " ")
    .replace(Regex("\\[[^\\]]*]"), " ")
    .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
    .trim()

/**
 * A settled answer or a failed request, with one clear way to start over.
 *
 * The sheet's centre slot is generous in portrait and barely a ribbon in
 * landscape, and the unreachable thing below was the button itself — the one
 * child that matters — sliding under the Cancel pill. So the rhythm reads its
 * own limits: the relaxed spacing is the portrait look, and a short slot
 * compresses the steps and lets the disc give back some room until the button
 * always sits whole above it.
 */
@Composable
private fun RecognitionMessageContent(
    icon: ImageVector,
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        // Portrait and tall contracts keep the roomy layout; a landscape sheet
        // (or any short slot) tightens everything except the label sizes.
        val compact = maxHeight < 300.dp
        val disc = if (compact) 64.dp else 96.dp
        val discIcon = if (compact) 28.dp else 40.dp
        val gapUnderDisc = if (compact) 14.dp else 24.dp
        val gapUnderTitle = if (compact) 6.dp else 10.dp
        val gapUnderBody = if (compact) 14.dp else 28.dp
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(disc)
                    .recognitionGlass(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.size(discIcon),
                )
            }
            Spacer(Modifier.height(gapUnderDisc))
            Text(
                text = title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(gapUnderTitle))
            Text(
                text = body,
                color = Color.White.copy(alpha = 0.62f),
                fontSize = 15.sp,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(gapUnderBody))
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                ),
                contentPadding = PaddingValues(
                    horizontal = 28.dp,
                    vertical = if (compact) 10.dp else 14.dp,
                ),
            ) {
                Text(
                    text = actionLabel,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
            }
        }
    }
}

/** Shown after a refusal, with the route to the system toggle instead of a toast. */
@Composable
private fun MicrophoneDeniedContent(canAskAgain: Boolean, onEnable: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(horizontal = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .recognitionGlass(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.MicOff,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.72f),
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.recognition_permission_title),
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.recognition_permission_body),
            color = Color.White.copy(alpha = 0.62f),
            fontSize = 15.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onEnable,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color.Black,
            ),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
        ) {
            Text(
                text = stringResource(
                    if (canAskAgain) {
                        R.string.recognition_enable_microphone
                    } else {
                        R.string.recognition_open_settings
                    },
                ),
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
        }
    }
}

/**
 * A pale frosted layer across the whole sheet, not just the orb. It samples the
 * page the dialog covers at the same low resolution the other glass surfaces
 * use, then keeps most of the sheet's dark weight on top so the blur reads as
 * texture rather than as another picture behind the text.
 */
@Composable
private fun Modifier.recognitionSheetSurface(shape: Shape): Modifier {
    val glass = LocalLiquidGlassEnabled.current && isGlassSupported()
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    if (!glass || reduceDynamicBlur) {
        return background(Brush.verticalGradient(listOf(RecognitionTop, RecognitionBottom)))
    }
    val density = LocalDensity.current
    val blurPx = with(density) { 18.dp.toPx() * 0.33f }
    return drawBackdrop(
        backdrop = LocalAppBackdrop.current,
        shape = { shape },
        effects = {
            colorControls(saturation = 1.12f)
            blur(blurPx)
        },
        highlight = null,
        shadow = null,
        onDrawSurface = {
            drawRect(Color.Black.copy(alpha = 0.42f))
        },
        backdropScale = 0.33f,
    ).background(
        Brush.verticalGradient(
            listOf(
                RecognitionTop.copy(alpha = 0.70f),
                RecognitionBottom.copy(alpha = 0.86f),
            ),
        ),
    )
}

/**
 * The main orb is a real glass surface: when Liquid Glass is on it samples the
 * app behind the dialog, so the room-facing control refracts the page rather
 * than sitting as a flat white disc. With the effect off, reduced, or on a
 * device without RenderEffect support, the same shape falls back to a smoked
 * fill and keeps the hairline the rest of the app uses on plain surfaces.
 */
@Composable
private fun Modifier.recognitionGlass(shape: CornerBasedShape): Modifier {
    val glass = LocalLiquidGlassEnabled.current && isGlassSupported()
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    return clip(shape).then(
        if (glass && !reduceDynamicBlur) {
            Modifier.liquidGlass(shape = shape, surfaceTint = RecognitionGlassTint)
        } else {
            Modifier
                .background(RecognitionGlassFallback)
                .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, shape)
        },
    )
}

/**
 * Smaller controls only need the glass rim — the highlight that catches the
 * light along the edge. Skipping the backdrop capture here keeps the close and
 * cancel buttons from adding two more full blur passes to a screen that already
 * has the orb's.
 */
@Composable
private fun Modifier.recognitionGlassControl(shape: CornerBasedShape, fallback: Color): Modifier =
    clip(shape)
        .background(fallback)
        .liquidGlassRim(shape)

private fun Context.hasMicrophone(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

private fun Activity.canAskForMicrophone(): Boolean =
    shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)

private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", packageName, null)),
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
