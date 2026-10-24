package com.music.bitchord.wear.ui

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListAnchorType
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import androidx.wear.compose.material3.Text
import com.music.bitchord.wear.R
import com.music.bitchord.wear.data.NowPlaying
import kotlinx.coroutines.delay
import kotlin.math.abs

private const val IDLE_SNAPBACK_MS = 4_000L
private const val CONTROLS_HIDE_MS = 4_000L
private val SwipeThreshold = 64.dp

/**
 * Synced lyrics, on the phone player's mesh.
 *
 *  - crown: scrolls the lyrics; 4 s after the last touch the list returns to the sung line
 *  - swipe left: next track (swipe right stays the system "back" gesture of the nav host)
 *  - touch controls at the foot, fading out after 4 s
 *  - haptic tick on a new line, firmer tick on entering a chorus
 *  - always-on display: the app's own AmbientPlayer covers this screen, the clock drops to 1 Hz
 *  - playback state comes from the app's NowPlaying stream; transport buttons use PhoneLink's own commands
 */
@Composable
fun LyricsScreen(
    np: NowPlaying?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LyricsViewModel = viewModel(),
) {
    val view = LocalView.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val positionState = viewModel.positionMs.collectAsStateWithLifecycle()
    val ambient = LocalAmbient.current

    // The clock only runs while this screen is on.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.setUiVisible(true)
                Lifecycle.Event.ON_STOP -> viewModel.setUiVisible(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(ambient.active) { viewModel.setAmbient(ambient.active) }

    // Playback state is the app's own NowPlaying stream; nothing is re-implemented here.
    LaunchedEffect(np) { viewModel.onNowPlaying(np) }

    // Same haptic channel as the rest of the app, so no VIBRATE permission is needed.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            view.performHapticFeedback(
                when (event) {
                    LyricsEvent.LineChanged -> HapticFeedbackConstants.CLOCK_TICK
                    LyricsEvent.ChorusEntered -> HapticFeedbackConstants.CONFIRM
                },
            )
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Color.Black)) {
        val w = maxWidth
        val h = maxHeight
        MeshBackground(np?.colors.orEmpty())
        // The mesh is bright; lyrics are all text, so it is pushed well back.
        Spacer(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))

        LyricsBody(
            state = state,
            positionState = positionState,
            isPlaying = np?.playing == true,
            hasNext = np?.hasNext ?: true,
            hasPhone = np != null,
            w = w,
            h = h,
            onPrevious = onPrevious,
            onNext = onNext,
            onTogglePlay = onTogglePlay,
            onRetry = viewModel::requestLyrics,
        )
    }
}

@Composable
private fun LyricsBody(
    state: LyricsUiState,
    positionState: State<Long>,
    isPlaying: Boolean,
    hasNext: Boolean,
    hasPhone: Boolean,
    w: Dp,
    h: Dp,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTogglePlay: () -> Unit,
    onRetry: () -> Unit,
) {
    val view = LocalView.current
    val listState = rememberScalingLazyListState()
    val swipeThresholdPx = with(LocalDensity.current) { SwipeThreshold.toPx() }

    var userBrowsing by remember { mutableStateOf(false) }
    var interactionStamp by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsStamp by remember { mutableLongStateOf(0L) }
    var programmaticScroll by remember { mutableStateOf(false) }
    var didInitialScroll by remember { mutableStateOf(false) }

    fun markBrowsing() {
        userBrowsing = true
        interactionStamp = SystemClock.elapsedRealtime()
    }

    fun wakeControls() {
        controlsVisible = true
        controlsStamp = SystemClock.elapsedRealtime()
    }

    val onNextState by rememberUpdatedState(onNext)

    // 4 s without crown or touch input: back to the line being sung.
    LaunchedEffect(interactionStamp) {
        if (interactionStamp != 0L) {
            delay(IDLE_SNAPBACK_MS)
            userBrowsing = false
        }
    }

    // Controls fade after 4 s of quiet (they stay solid while paused).
    LaunchedEffect(controlsStamp) {
        delay(CONTROLS_HIDE_MS)
        controlsVisible = false
    }

    // Dragging the list by hand counts as browsing; our own follow-scrolls do not.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling && !programmaticScroll) markBrowsing()
        }
    }

    // Follow the sung line (the header is item 0, lyrics start at 1).
    val lines = state.lines
    val targetItem = if (state.activeLineIndex < 0) 0 else state.activeLineIndex + 1
    LaunchedEffect(targetItem, userBrowsing, state.trackId, lines.size) {
        if (userBrowsing || lines.isEmpty()) return@LaunchedEffect
        programmaticScroll = true
        try {
            if (!didInitialScroll) {
                listState.scrollToItem(targetItem)
                didInitialScroll = true
            } else {
                listState.animateScrollToItem(targetItem)
            }
        } finally {
            programmaticScroll = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            // Any touch wakes the controls. Observed in the Initial pass and never consumed.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    wakeControls()
                }
            }
            // Swipe left = next track. Watches the gesture without consuming it, so the
            // nav host's swipe-right-to-go-back keeps working.
            .pointerInput(swipeThresholdPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var last = down.position
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        last = change.position
                    }
                    val dx = last.x - down.position.x
                    val dy = last.y - down.position.y
                    if (dx <= -swipeThresholdPx && abs(dx) > abs(dy) * 1.6f) {
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        onNextState()
                    }
                }
            },
    ) {
        if (lines.isEmpty()) {
            StatusMessage(state = state, hasPhone = hasPhone, onRetry = onRetry)
        } else {
            ScalingLazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .onRotaryScrollEvent { event ->
                        markBrowsing()
                        listState.dispatchRawDelta(event.verticalScrollPixels)
                        true
                    }
                    // The crown goes to the screen on top; take it when this one is.
                    .requestFocusOnHierarchyActive()
                    .focusable(),
                anchorType = ScalingLazyListAnchorType.ItemCenter,
                autoCentering = AutoCenteringParams(itemIndex = 0),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                item(key = "header") {
                    TrackHeader(title = state.title, artist = state.artist)
                }
                items(count = lines.size, key = { lines[it].index }) { i ->
                    LyricLineItem(
                        line = lines[i],
                        isActive = i == state.activeLineIndex,
                        positionState = positionState,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
        }

        // Soft edges: lines scale away towards the bezel.
        Spacer(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(h * 0.12f)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent))),
        )
        Spacer(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(h * 0.30f)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)))),
        )

        val controlsAlpha by animateFloatAsState(
            targetValue = if (controlsVisible || !isPlaying) 1f else 0.28f,
            animationSpec = tween(durationMillis = 400),
            label = "lyricsControlsAlpha",
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = h * 0.07f)
                .alpha(controlsAlpha),
            horizontalArrangement = Arrangement.spacedBy(w * 0.03f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphButton(
                icon = R.drawable.ic_previous,
                description = stringResource(R.string.previous),
                size = w * 0.17f,
                iconSize = w * 0.09f,
                background = Color.White.copy(alpha = 0.14f),
                onClick = onPrevious,
            )
            GlyphButton(
                icon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                description = stringResource(if (isPlaying) R.string.pause else R.string.play),
                size = w * 0.21f,
                iconSize = w * 0.11f,
                tint = Color.Black,
                background = Color.White,
                onClick = onTogglePlay,
            )
            GlyphButton(
                icon = R.drawable.ic_next,
                description = stringResource(R.string.next),
                size = w * 0.17f,
                iconSize = w * 0.09f,
                enabled = hasNext,
                background = Color.White.copy(alpha = 0.14f),
                onClick = onNext,
            )
        }
    }
}

@Composable
private fun TrackHeader(title: String, artist: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 40.dp, vertical = 8.dp),
    ) {
        Text(
            text = title,
            style = WearType.caption,
            color = Color.White.copy(alpha = 0.9f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = artist,
            style = WearType.caption,
            color = LyricsPalette.Cyan.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StatusMessage(state: LyricsUiState, hasPhone: Boolean, onRetry: () -> Unit) {
    val message = when {
        !hasPhone || state.trackId == null -> stringResource(R.string.wear_lyrics_waiting)
        state.lyricsStatus == LyricsStatus.UNAVAILABLE -> stringResource(R.string.wear_lyrics_none)
        else -> stringResource(R.string.wear_lyrics_loading)
    }
    Box(
        Modifier
            .fillMaxSize()
            .clickable(onClick = onRetry)
            .padding(horizontal = 34.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (state.title.isNotBlank()) {
                Text(
                    text = state.title,
                    style = WearType.title,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
            }
            Text(
                text = message,
                style = WearType.rowSub,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }
    }
}
