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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.music.bitchord.wear.lyrics.SyncedLine
import com.music.bitchord.wear.R
import com.music.bitchord.wear.data.NowPlaying
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.math.abs

private const val IDLE_SNAPBACK_MS = 4_000L
private const val CONTROLS_HIDE_MS = 4_000L
private val SwipeThreshold = 64.dp

/** How long a press has to be held to open the panel: longer than the system's, so it is not opened by accident. */
private const val LONG_PRESS_MS = 900L

/** How far the two outer buttons ride up, as a share of the screen height, to follow the rim. */
private const val OUTER_BUTTON_RISE = 0.06f
private const val TUNE_PANEL_MS = 8_000L
private const val KEEP_AWAKE_PAUSE_LIMIT_MS = 30_000L

/**
 * Synced lyrics, on the phone player's mesh.
 *
 *  - crown: scrolls the lyrics; 4 s after the last touch the list returns to the sung line
 *  - swipe left: next track (swipe right stays the system "back" gesture of the nav host)
 *  - touch controls at the foot, fading out after 4 s: translate and Latin-script reading on
 *    either side of previous / play / next
 *  - haptic tick on a new line, firmer tick on entering a chorus
 *  - long-press: a small panel to nudge the lyrics earlier or later (kept), keep the screen on
 *    for this song, how well the automatic sync is doing, and the app version
 *  - always-on display: the app's own AmbientPlayer covers this screen, the clock drops to 1 Hz
 *  - playback state comes from the app's NowPlaying stream; transport buttons use PhoneLink's own commands
 */
@Composable
fun LyricsScreen(
    np: NowPlaying?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LyricsViewModel = viewModel(),
) {
    val view = LocalView.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val positionState = viewModel.positionMs.collectAsStateWithLifecycle()
    val ambient = LocalAmbient.current
    val fontScale by viewModel.fontScale.collectAsStateWithLifecycle()
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val syncInfo by viewModel.syncInfo.collectAsStateWithLifecycle()

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

    val baseConfiguration = LocalViewConfiguration.current
    val slowLongPress = remember(baseConfiguration) {
        object : ViewConfiguration by baseConfiguration {
            override val longPressTimeoutMillis: Long get() = LONG_PRESS_MS
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Color.Black)) {
        val w = maxWidth
        val h = maxHeight
        MeshBackground(np?.colors.orEmpty())
        // The mesh is bright; lyrics are all text, so it is pushed well back.
        Spacer(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))

        CompositionLocalProvider(LocalViewConfiguration provides slowLongPress) {
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
            syncInfo = syncInfo,
            fontScale = fontScale,
            sources = sources,
            onCycleFont = viewModel::cycleFontScale,
            onOpenSources = viewModel::openSources,
            onSelectSource = viewModel::selectSource,
            onSeekLine = { line -> onSeek(viewModel.seekTargetMs(line)) },
            onTranslate = viewModel::toggleTranslation,
            onRomanize = viewModel::toggleRomanization,
            onRetry = viewModel::requestLyrics,
        )
        }
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
    syncInfo: SyncInfo,
    fontScale: Float,
    sources: SourcesUi,
    onCycleFont: () -> Unit,
    onOpenSources: () -> Unit,
    onSelectSource: (String) -> Unit,
    onSeekLine: (SyncedLine) -> Unit,
    onTranslate: () -> Unit,
    onRomanize: () -> Unit,
    onRetry: () -> Unit,
) {
    val view = LocalView.current
    val context = LocalContext.current
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    var showTune by remember { mutableStateOf(false) }
    var showSources by remember { mutableStateOf(false) }
    var tuneStamp by remember { mutableLongStateOf(0L) }

    // "Keep the screen on": until the song changes, the screen is left, or the song has been paused
    // for a while. The window flag also keeps the watch out of the always-on mode meanwhile.
    var keepAwake by remember(state.trackId) { mutableStateOf(false) }
    DisposableEffect(keepAwake) {
        view.keepScreenOn = keepAwake
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(keepAwake, isPlaying) {
        if (keepAwake && !isPlaying) {
            delay(KEEP_AWAKE_PAUSE_LIMIT_MS)
            keepAwake = false
        }
    }
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

    fun openTune() {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        showTune = true
        tuneStamp = SystemClock.elapsedRealtime()
    }

    // The tuning panel closes itself after 5 s without a tap.
    LaunchedEffect(tuneStamp) {
        if (showTune) {
            delay(TUNE_PANEL_MS)
            showTune = false
        }
    }

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
            // Long press: the sync panel. A drag or a swipe cancels it, so scrolling is unaffected.
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { openTune() })
            }
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
                    TrackHeader(
                        title = state.title,
                        artist = state.artist,
                        note = extraNote(state),
                    )
                }
                items(count = lines.size, key = { lines[it].index }) { i ->
                    val line = lines[i]
                    LyricLineItem(
                        line = line,
                        fontScale = fontScale,
                        isActive = i == state.activeLineIndex,
                        positionState = positionState,
                        altText = if (state.viewMode != LyricsView.ORIGINAL && state.extraStatus == ExtraStatus.READY) {
                            state.extraTexts.getOrNull(i)
                        } else {
                            null
                        },
                        // A tap sends the phone to this line (the same rule as tapping one there). The
                        // line handles its own long press too: otherwise releasing a long press over it
                        // would count as a tap and jump.
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .pointerInput(line.index) {
                                detectTapGestures(
                                    onTap = {
                                        if (!line.isGap) {
                                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                            userBrowsing = false
                                            onSeekLine(line)
                                        }
                                    },
                                    onLongPress = { openTune() },
                                )
                            },
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
                .height(h * 0.36f)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)))),
        )

        val controlsAlpha by animateFloatAsState(
            targetValue = if (controlsVisible || !isPlaying) 1f else 0.28f,
            animationSpec = tween(durationMillis = 400),
            label = "lyricsControlsAlpha",
        )
        if (showTune && !showSources) {
            TunePanel(
                keepAwake = keepAwake,
                onKeepAwake = {
                    keepAwake = !keepAwake
                    tuneStamp = SystemClock.elapsedRealtime()
                },
                fontScale = fontScale,
                onCycleFont = {
                    onCycleFont()
                    tuneStamp = SystemClock.elapsedRealtime()
                },
                onOpenSources = {
                    showSources = true
                    onOpenSources()
                },
                onClose = { showTune = false },
                syncInfo = syncInfo,
                versionName = versionName,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // translate | previous | play | next | Latin-script reading. The middle three stay where they
        // were; the two outer ones sit further out, where a round screen is narrower, so they ride
        // up along the rim (about 6% of the height) to stay inside it.
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = h * 0.10f)
                // Fading with Modifier.alpha draws into a separate layer the size of this row, which
                // cut the raised buttons off at the top (and their touch area with it). Modulating the
                // alpha draws them in place; the top padding keeps the raised buttons inside the row.
                .graphicsLayer {
                    alpha = controlsAlpha
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
                .padding(top = h * OUTER_BUTTON_RISE),
            horizontalArrangement = Arrangement.spacedBy(w * 0.02f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val translating = state.viewMode == LyricsView.TRANSLATION
            val romanizing = state.viewMode == LyricsView.ROMANIZATION
            Box(Modifier.offset(y = -(h * OUTER_BUTTON_RISE))) {
            GlyphButton(
                icon = R.drawable.ic_lyrics_translate,
                description = stringResource(R.string.wear_lyrics_translate),
                size = w * 0.125f,
                iconSize = w * 0.07f,
                tint = if (translating) Color.Black else Color.White,
                background = if (translating) LyricsPalette.Cyan else Color.White.copy(alpha = 0.14f),
                onClick = onTranslate,
            )
            }
            GlyphButton(
                icon = R.drawable.ic_previous,
                description = stringResource(R.string.previous),
                size = w * 0.14f,
                iconSize = w * 0.075f,
                background = Color.White.copy(alpha = 0.14f),
                onClick = onPrevious,
            )
            GlyphButton(
                icon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                description = stringResource(if (isPlaying) R.string.pause else R.string.play),
                size = w * 0.17f,
                iconSize = w * 0.09f,
                tint = Color.Black,
                background = Color.White,
                onClick = onTogglePlay,
            )
            GlyphButton(
                icon = R.drawable.ic_next,
                description = stringResource(R.string.next),
                size = w * 0.14f,
                iconSize = w * 0.075f,
                enabled = hasNext,
                background = Color.White.copy(alpha = 0.14f),
                onClick = onNext,
            )
            Box(Modifier.offset(y = -(h * OUTER_BUTTON_RISE))) {
            GlyphButton(
                icon = R.drawable.ic_lyrics_romanize,
                description = stringResource(R.string.wear_lyrics_romanize),
                size = w * 0.125f,
                iconSize = w * 0.07f,
                tint = if (romanizing) Color.Black else Color.White,
                background = if (romanizing) LyricsPalette.Cyan else Color.White.copy(alpha = 0.14f),
                onClick = onRomanize,
            )
            }
        }

        if (showSources) {
            SourcePicker(
                sources = sources,
                onSelect = { id ->
                    onSelectSource(id)
                    showSources = false
                    showTune = false
                },
                onClose = { showSources = false },
            )
        }
    }
}

/** Long-press panel. It closes itself after a few seconds, or with the cross. */
@Composable
private fun TunePanel(
    keepAwake: Boolean,
    onKeepAwake: () -> Unit,
    fontScale: Float,
    onCycleFont: () -> Unit,
    onOpenSources: () -> Unit,
    onClose: () -> Unit,
    syncInfo: SyncInfo,
    versionName: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.92f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TunePill(text = "✕", description = stringResource(R.string.wear_lyrics_close), onClick = onClose)
            Text(
                text = stringResource(R.string.wear_lyrics_tune_title),
                style = WearType.caption,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
        TunePill(
            text = stringResource(if (keepAwake) R.string.wear_lyrics_keep_awake_on else R.string.wear_lyrics_keep_awake),
            active = keepAwake,
            onClick = onKeepAwake,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            TunePill(text = "Aa " + (fontScale * 100).roundToInt() + "%", onClick = onCycleFont)
            TunePill(text = stringResource(R.string.wear_lyrics_source) + " ›", onClick = onOpenSources)
        }
        Text(
            text = if (syncInfo.fixCount > 0) {
                stringResource(R.string.wear_lyrics_auto_sync) + ": " + syncInfo.fixCount + " • " + syncInfo.bestRttMs + " ms"
            } else {
                stringResource(R.string.wear_lyrics_auto_sync_waiting)
            },
            style = WearType.caption,
            color = Color.White.copy(alpha = 0.5f),
        )
        if (versionName.isNotEmpty()) {
            Text(text = "v" + versionName, style = WearType.caption, color = Color.White.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun TunePill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    description: String? = null,
) {
    Box(
        modifier
            .clip(CircleShape)
            .background(if (active) LyricsPalette.Cyan else Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .semantics { if (description != null) contentDescription = description }
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(text = text, style = WearType.caption, color = if (active) Color.Black else Color.White)
    }
}

/**
 * Which sources have synced lyrics for this song, as the phone finds them: only those that answered
 * with lyrics are listed, and the list grows while the phone is still asking.
 */
@Composable
private fun SourcePicker(
    sources: SourcesUi,
    onSelect: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberScalingLazyListState()
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 14.dp),
        ) {
            item(key = "back") {
                TunePill(text = "‹ " + stringResource(R.string.wear_lyrics_source_back), onClick = onClose)
            }
            item(key = "title") {
                Text(
                    text = stringResource(R.string.wear_lyrics_source),
                    style = WearType.header,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
            }
            items(count = sources.items.size, key = { sources.items[it].id }) { i ->
                val item = sources.items[i]
                SourceRow(item = item, current = item.id == sources.currentId, onClick = { onSelect(item.id) })
            }
            if (sources.loading) {
                item(key = "searching") {
                    Text(
                        text = stringResource(R.string.wear_lyrics_source_searching),
                        style = WearType.caption,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }
            } else if (sources.items.isEmpty()) {
                item(key = "none") {
                    Text(
                        text = stringResource(R.string.wear_lyrics_source_none),
                        style = WearType.caption,
                        color = Color.White.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceRow(item: LyricsSourceItem, current: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (current) LyricsPalette.Cyan.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = item.label,
                style = WearType.row,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    if (item.kind == "word") R.string.wear_lyrics_source_word else R.string.wear_lyrics_source_line,
                ),
                style = WearType.caption,
                color = Color.White.copy(alpha = 0.55f),
            )
        }
        if (current) Text(text = "✓", style = WearType.row, color = LyricsPalette.Cyan)
    }
}

/** What to tell the user while a translation or reading is not on screen (yet). */
@Composable
private fun extraNote(state: LyricsUiState): String? {
    if (state.viewMode == LyricsView.ORIGINAL) return null
    return when (state.extraStatus) {
        ExtraStatus.LOADING -> stringResource(R.string.wear_lyrics_extra_loading)
        ExtraStatus.SAME -> stringResource(R.string.wear_lyrics_extra_same)
        ExtraStatus.UNAVAILABLE -> stringResource(R.string.wear_lyrics_extra_unavailable)
        ExtraStatus.READY, ExtraStatus.IDLE -> null
    }
}

@Composable
private fun TrackHeader(title: String, artist: String, note: String?) {
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
        if (note != null) {
            Text(
                text = note,
                style = WearType.caption,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
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
