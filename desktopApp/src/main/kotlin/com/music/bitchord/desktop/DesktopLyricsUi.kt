package com.music.bitchord.desktop

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.abs

/** The player's lyrics, sung rather than listed. */
@Composable
internal fun DesktopLyricsPanel(
    lyrics: DesktopLyrics?,
    loading: Boolean,
    error: String?,
    progressMs: Long,
    isPlaying: Boolean,
    blurUnfocused: Boolean,
    onSeek: (Long) -> Unit,
    trackId: String,
    modifier: Modifier = Modifier,
) {
    var translation by remember(trackId, lyrics) { mutableStateOf<DesktopLyrics?>(null) }
    var showing by remember(trackId, lyrics) { mutableStateOf(false) }
    var working by remember(trackId, lyrics) { mutableStateOf(false) }
    var note by remember(trackId, lyrics) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Said once and gone. A caption that stayed would sit over the lyrics for the rest of the song.
    LaunchedEffect(note) {
        if (note == null) return@LaunchedEffect
        delay(NOTE_DURATION_MS)
        note = null
    }

    Box(modifier.fillMaxSize()) {
        LyricsBody(
            (if (showing) translation else null) ?: lyrics,
            loading,
            error,
            progressMs,
            isPlaying,
            blurUnfocused,
            onSeek,
            Modifier.fillMaxSize(),
        )
        // Over the foot of the lyrics rather than in the controls: the bottom block is measured at
        // its natural height, so a row of its own there came straight off the panel above and the
        // lyrics lost a line.
        if (lyrics != null && !loading) {
            TranslateButton(
                showing = showing,
                working = working,
                note = note,
                // The same inset as the panel pills below, so the two stack in one line.
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 14.dp),
                onClick = {
                    note = null
                    when {
                        showing -> showing = false
                        translation != null -> showing = true
                        working -> Unit
                        else -> scope.launch {
                            working = true
                            val outcome = DesktopLyricsTranslation.translate(
                                trackId = trackId,
                                lines = lyrics.lines,
                                targetLanguageTag = DesktopTranslationSetting.resolved(),
                            )
                            working = false
                            when (outcome) {
                                is DesktopLyricsTranslation.Result.Translated -> {
                                    translation = lyrics.copy(lines = outcome.lines)
                                    showing = true
                                }
                                is DesktopLyricsTranslation.Result.SameLanguage ->
                                    note = DesktopStrings["d_already_in_that_language", "Already in that language"]
                                DesktopLyricsTranslation.Result.Unavailable ->
                                    note = DesktopStrings["d_translation_unavailable", "Translation unavailable"]
                            }
                        }
                    }
                },
            )
        }
    }
}

/** How long a translate caption stays up. */
private const val NOTE_DURATION_MS = 2_600L

/**
 * The translate toggle — the player's own pill, so it reads as part of the same chrome as the
 * close, full-screen, lyrics and queue buttons rather than as a control from somewhere else.
 *
 * Icon-only for the same reason. What it has to say about a failed attempt is a caption that shows
 * itself and goes, not a slab of text left standing over the lyrics.
 */
@Composable
private fun TranslateButton(
    showing: Boolean,
    working: Boolean,
    note: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(modifier, horizontalAlignment = Alignment.End) {
        AnimatedVisibility(note != null, enter = fadeIn(), exit = fadeOut()) {
            DesktopPlayerPill(Modifier.padding(bottom = 6.dp)) {
                Text(
                    note.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
            }
        }
        DesktopPlayerPill {
            DesktopPlayerPillButton(onClick = onClick, selected = showing) {
                if (working) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(15.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                } else {
                    Icon(
                        Icons.Rounded.Translate,
                        if (showing) {
                            DesktopStrings["d_show_original", "Original"]
                        } else {
                            DesktopStrings["translate", "Translate"]
                        },
                        tint = if (showing) Color.Black else Color.White,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricsBody(
    lyrics: DesktopLyrics?,
    loading: Boolean,
    error: String?,
    progressMs: Long,
    isPlaying: Boolean,
    blurUnfocused: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier,
) {
    when {
        loading -> LyricsSkeleton(modifier)
        error != null || lyrics == null -> Box(modifier, contentAlignment = Alignment.Center) {
            Text(DesktopStrings["d_lyrics_unavailable", "Lyrics unavailable"], color = DesktopSecondary)
        }
        lyrics.lines.isEmpty() -> Box(modifier, contentAlignment = Alignment.Center) {
            Text(DesktopStrings["instrumental", "Instrumental"], color = DesktopSecondary)
        }
        else -> LyricsList(lyrics.lines, progressMs, isPlaying, blurUnfocused, onSeek, modifier)
    }
}

/** The one line being sung, on a strip above the scrubber. */
@Composable
internal fun DesktopCurrentLyricLine(
    lyrics: DesktopLyrics?,
    loading: Boolean,
    unavailable: Boolean,
    trackKey: String,
    progressMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lines = lyrics?.lines.orEmpty()
    when {
        lines.isNotEmpty() -> SungLyricStrip(
            lines = lines,
            trackKey = trackKey,
            progressMs = progressMs,
            durationMs = durationMs,
            isPlaying = isPlaying,
            onClick = onClick,
            modifier = modifier,
        )
        unavailable -> LyricsUnavailableStrip(trackKey, modifier)
        loading -> LyricsLoadingStrip(trackKey, modifier)
    }
}

@Composable
private fun SungLyricStrip(
    lines: List<DesktopLyricLine>,
    trackKey: String,
    progressMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val synced = remember(lines) { lines.any { it.timeMs > 0L } }
    // Nothing to follow along with, so the strip says the words exist and how to read them rather
    // than pretending to keep time.
    if (!synced) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        ) {
            Icon(
                Icons.Rounded.MusicNote,
                null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                DesktopStrings["d_lyrics_available_click_to_view", "Lyrics available • Click to view"],
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Medium),
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        return
    }

    val clock = rememberLyricClock(progressMs, isPlaying)
    val index by remember(lines) {
        derivedStateOf { lines.indexOfLast { it.timeMs <= clock.longValue } }
    }
    val current = lines.getOrNull(index)
    // Before the first line, and through instrumental breaks, show the note.
    val instrumental = current == null || current.isGap
    // Everything ahead of the first sung line is the intro — an LRC file opens on a bare [00:00.00]
    // gap, so that stretch is gap lines rather than nothing at all.
    val firstSung = remember(lines) { lines.indexOfFirst { !it.isGap } }
    val intro = instrumental && firstSung >= 0 && index < firstSung
    // Picked once for the track: chosen per recomposition, the copy would change every time the
    // playback clock ticked.
    val introLine = remember(trackKey) { LYRIC_INTRO_LINES.random() }
    val text = when {
        intro -> introLine
        instrumental -> "Instrumental"
        else -> current!!.text
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .graphicsLayer {
                if (instrumental) {
                    // Nothing is being sung; hold it steady rather than fading.
                    alpha = 0.5f
                    return@graphicsLayer
                }
                // A line fades out as its own time runs down rather than being swapped for the next
                // one in a single frame.
                val start = lines.getOrNull(index)?.timeMs ?: 0L
                val end = lines.getOrNull(index + 1)?.timeMs
                    ?: durationMs.takeIf { it > start }
                    ?: (start + 4_000L)
                val fade = ((end - start) * LYRIC_FADE_FRACTION)
                    .coerceIn(LYRIC_FADE_MIN_MS, LYRIC_FADE_MAX_MS)
                alpha = 0.78f * ((end - clock.longValue).toFloat() / fade).coerceIn(0f, 1f)
            },
    ) {
        if (instrumental) {
            Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        val swept = current?.takeIf { !instrumental && it.isWordSynced }
        if (swept != null) {
            SweptLyricLine(
                line = swept,
                clock = clock,
                style = MaterialTheme.typography.titleMedium,
                dimAlpha = UNSUNG_ALPHA_STRIP,
                // One line with no room either side of it.
                feather = false,
                // Nor anywhere to rise to.
                rise = false,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        } else {
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        Spacer(Modifier.width(6.dp))
        // Says the strip leads somewhere, which is most of what makes it a way in to the panel
        // rather than a caption.
        Icon(
            Icons.Rounded.ChevronRight,
            null,
            tint = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(14.dp),
        )
    }
}

/**
 * Stands in once a lookup has come back empty — held long enough to register, then left to fade
 * rather than snapping out or staying for the whole track.
 */
@Composable
private fun LyricsUnavailableStrip(trackKey: String, modifier: Modifier) {
    var visible by remember(trackKey) { mutableStateOf(true) }
    LaunchedEffect(trackKey) {
        delay(LYRICS_UNAVAILABLE_HOLD_MS)
        visible = false
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 0.55f else 0f,
        animationSpec = tween(LYRICS_UNAVAILABLE_FADE_MS),
        label = "lyricsUnavailableAlpha",
    )
    Text(
        DesktopStrings["d_lyrics_aren_t_available_for_this_song", "Lyrics aren't available for this song"],
        style = MaterialTheme.typography.titleMedium,
        color = Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(vertical = 4.dp).graphicsLayer { this.alpha = alpha },
    )
}

/** Stands in while a lookup is still in flight. */
@Composable
private fun LyricsLoadingStrip(trackKey: String, modifier: Modifier) {
    // Stable for the length of this track's lookup rather than re-rolled on every recomposition.
    val text = remember(trackKey) { LYRIC_LOADING_LINES.random() }
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = Color.White.copy(alpha = 0.55f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(vertical = 4.dp),
    )
}

/** What the strip says over an intro, before the first line is sung. */
private val LYRIC_INTRO_LINES = listOf(
    "The beat is landing",
    "The song is starting",
    "Warming up",
    "Setting the mood",
    "Bass first, words later",
    "Wait for it",
    "Feel that build",
    "Just the groove for now",
    "The hook is on the way",
    "Let it breathe",
    "Cue the vocals",
    "First notes in",
)

/** What it says while a lookup is still out. */
private val LYRIC_LOADING_LINES = listOf(
    "Getting the lyrics",
    "Finding the words",
    "Fetching the verses",
    "Lyrics are loading",
    "Checking the lyric sheet",
    "Searching the songbook",
    "Lining up the lyrics",
    "Words are on the way",
    "Checking the archives",
    "Finding the right words",
    "Lyrics are coming together",
    "Almost found the words",
)

/** How much of a line's own length it spends fading out, and the bounds on that. */
private const val LYRIC_FADE_FRACTION = 0.28f
private const val LYRIC_FADE_MIN_MS = 160f
private const val LYRIC_FADE_MAX_MS = 700f

/** What the unsung tail reads at on the strip — brighter than in the panel. */
private const val UNSUNG_ALPHA_STRIP = 0.55f

private const val LYRICS_UNAVAILABLE_HOLD_MS = 5_000L
private const val LYRICS_UNAVAILABLE_FADE_MS = 900

@Composable
private fun LyricsList(
    lines: List<DesktopLyricLine>,
    progressMs: Long,
    isPlaying: Boolean,
    blurUnfocused: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier,
) {
    val clock = rememberLyricClock(progressMs, isPlaying)
    val synced = remember(lines) { lines.any { it.timeMs > 0L } }
    // Whether anything here is sung from the other side.
    val duet = remember(lines) { lines.any { it.alignment == DesktopLyricAlignment.End } }

    val activeRows by remember(lines, synced) {
        derivedStateOf { if (!synced) emptyList() else activeLyricRows(lines, clock.longValue) }
    }
    // The uppermost unfinished vocal owns the scroll anchor until its own end, even as later rows
    // begin their own highlights underneath it.
    val scrollLine = activeRows.firstOrNull() ?: -1

    // Where the panel is heading, which is a beat ahead of where the singing is.
    val leadLine by remember(lines, synced) {
        derivedStateOf {
            if (!synced) {
                -1
            } else {
                val now = clock.longValue
                activeLyricRows(lines, now + lyricScrollLead(lines, now)).firstOrNull() ?: -1
            }
        }
    }
    // What the stack arranges itself around.
    val focusLine = if (leadLine >= 0) leadLine else scrollLine

    val listState = rememberLazyListState()
    // Layout info changes on every scrolled pixel; only the height is wanted here, so the whole
    // list is not recomposed to follow a scroll.
    val viewportHeight by remember(listState) {
        derivedStateOf { listState.layoutInfo.viewportSize.height }
    }
    var browsing by remember(lines) { mutableStateOf(false) }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect {
            if (it is DragInteraction.Start) browsing = true
        }
    }
    // Following resumes once the hand has let go and the singing has moved on, rather than being
    // given up for the rest of the track.
    LaunchedEffect(scrollLine) {
        if (browsing && !listState.isScrollInProgress) browsing = false
    }

    // The panel's own journey, published so each row can work out how far behind it should run.
    var run by remember(lines) { mutableStateOf(LyricScrollRun(0, 0f, LYRIC_SETTLE_MS)) }
    val since = remember(lines) { mutableFloatStateOf(0f) }
    LaunchedEffect(run.id) {
        if (run.id == 0) return@LaunchedEffect
        animate(0f, run.spanMs, animationSpec = tween(run.spanMs.toInt(), easing = LinearEasing)) { value, _ ->
            since.floatValue = value
        }
    }

    var placed by remember(lines) { mutableStateOf(false) }
    LaunchedEffect(focusLine, browsing) {
        if (!synced || browsing || focusLine < 0 || focusLine !in lines.indices) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.viewportSize.height }.first { it > 0 }
        val visible = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == focusLine }
        when {
            !placed -> {
                listState.scrollToItem(focusLine)
                placed = true
            }
            // Already on screen, which is the ordinary handover.
            visible != null -> {
                val span = lyricScrollLead(lines, clock.longValue).toInt()
                run = LyricScrollRun(run.id + 1, visible.offset.toFloat(), span)
                listState.animateScrollBy(
                    visible.offset.toFloat(),
                    animationSpec = tween(span, easing = LYRIC_EASING),
                )
            }
            // Off screen after a seek or a long instrumental: how far is not known without laying
            // the rows out, so the list's own staged scroll takes it.
            else -> listState.animateScrollToItem(focusLine)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 24.dp,
            // Room to bring the last line up to the anchor instead of stopping with it pinned to
            // the bottom edge.
            bottom = with(LocalDensity.current) { viewportHeight.toDp() } * 0.7f,
            start = 8.dp,
            end = 8.dp,
        ),
    ) {
        itemsIndexed(lines, key = { index, line -> "$index:${line.timeMs}" }) { index, line ->
            LyricRow(
                lines = lines,
                index = index,
                line = line,
                clock = clock,
                synced = synced,
                browsing = browsing,
                blurUnfocused = blurUnfocused,
                duet = duet,
                active = synced && index in activeRows,
                sung = scrollLine >= 0 && index < scrollLine,
                distance = if (scrollLine < 0) 0 else abs(index - scrollLine),
                stagger = staggerFor(index, focusLine, run),
                run = run,
                since = since.floatValue,
                onSeek = onSeek,
            )
        }
    }
}

/** What the panel shows while the sources are being asked. */
@Composable
private fun LyricsSkeleton(modifier: Modifier = Modifier) {
    val sweep = rememberInfiniteTransition(label = "lyricsSkeleton").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SKELETON_PERIOD_MS, easing = LinearEasing)),
        label = "sweep",
    )
    BoxWithConstraints(modifier.padding(top = 40.dp, start = LYRIC_GUTTER, end = LYRIC_GUTTER)) {
        val column = maxWidth
        Column(verticalArrangement = Arrangement.spacedBy(SKELETON_BLOCK_GAP)) {
            SKELETON_BLOCKS.forEach { rows ->
                Column(verticalArrangement = Arrangement.spacedBy(SKELETON_LEADING)) {
                    rows.forEach { fraction ->
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .height(SKELETON_BAR)
                                .clip(RoundedCornerShape(4.dp))
                                // Read in the draw block, not the body: a pageful of these would
                                // otherwise recompose on every frame, and all any of them needs per
                                // frame is a fresh gradient.
                                .drawWithCache {
                                    val full = column.toPx()
                                    val band = full * 0.45f
                                    val startX = -band + sweep.value * (full + band * 2)
                                    val brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.10f),
                                            Color.White.copy(alpha = 0.26f),
                                            Color.White.copy(alpha = 0.10f),
                                        ),
                                        startX = startX,
                                        endX = startX + band,
                                    )
                                    onDrawBehind { drawRect(brush) }
                                },
                        )
                    }
                }
            }
        }
    }
}

/** The shape of a page of lyrics: verses of ragged-right lines. */
private val SKELETON_BLOCKS = listOf(
    floatArrayOf(0.97f, 0.54f),
    floatArrayOf(0.92f, 0.99f, 0.41f),
    floatArrayOf(0.68f),
    floatArrayOf(0.95f, 0.73f),
    floatArrayOf(0.89f, 0.96f, 0.37f),
)

private val SKELETON_BAR = 26.dp
private val SKELETON_LEADING = 15.dp
private val SKELETON_BLOCK_GAP = 35.dp
private const val SKELETON_PERIOD_MS = 1_400

/** How far behind the list this row runs. */
private fun staggerFor(index: Int, focusLine: Int, run: LyricScrollRun): Float {
    val behind = if (run.delta >= 0f) index - focusLine else focusLine - index
    return behind.coerceIn(0, STAGGER_STEPS) * STAGGER_FRACTION * run.durationMs
}

@Composable
private fun LyricRow(
    lines: List<DesktopLyricLine>,
    index: Int,
    line: DesktopLyricLine,
    clock: MutableLongState,
    synced: Boolean,
    browsing: Boolean,
    blurUnfocused: Boolean,
    /** Whether the song has a second voice; see [DesktopLyricAlignment]. */
    duet: Boolean,
    active: Boolean,
    sung: Boolean,
    distance: Int,
    stagger: Float,
    run: LyricScrollRun,
    since: Float,
    onSeek: (Long) -> Unit,
) {
    // Symmetric either side of the playing line, and shallow.
    val step = distance.coerceAtMost(LINE_FALLOFF_ALPHA.lastIndex)
    val alpha by animateFloatAsState(
        targetValue = when {
            !synced -> 0.95f
            active -> 1f
            // Reading by hand is not following along: the stack flattens to one brightness so no
            // row is being pointed at.
            browsing -> BROWSING_ALPHA
            else -> LINE_FALLOFF_ALPHA[step]
        },
        animationSpec = tween(LYRIC_SETTLE_MS, easing = LYRIC_EASING),
        label = "lyricAlpha",
    )
    val blurRadius by animateDpAsState(
        targetValue = if (!synced || !blurUnfocused || browsing || active) 0.dp else LINE_FALLOFF_BLUR[step],
        animationSpec = tween(LYRIC_SETTLE_MS, easing = LYRIC_EASING),
        label = "lyricBlur",
    )

    if (line.isGap) {
        LyricGapRow(lines, index, line, clock, active, alpha, blurRadius)
        return
    }

    val alignEnd = duet && line.alignment == DesktopLyricAlignment.End

    // The stack sits fractionally back and the playing line comes forward to meet the reader,
    // rather than the playing line swelling past the others.
    val scale by animateFloatAsState(
        targetValue = if (active) 1f else INACTIVE_SCALE,
        animationSpec = tween(LYRIC_SETTLE_MS, easing = LYRIC_EASING),
        label = "lyricScale",
    )
    // Apple's bloom on the line being sung.
    val glow by animateFloatAsState(
        targetValue = if (active && blurUnfocused) GLOW_ALPHA else 0f,
        animationSpec = tween(420),
        label = "lyricGlow",
    )
    val style = if (synced) {
        MaterialTheme.typography.headlineLarge.copy(
            fontSize = 32.sp,
            lineHeight = 39.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
        )
    } else {
        MaterialTheme.typography.headlineMedium.copy(
            fontSize = 28.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
        )
    }

    // A finished line closes up as it dims away rather than popping to full brightness the frame
    // its last word lands.
    val dim by animateFloatAsState(
        targetValue = if (!synced || sung || (active && !line.isWordSynced)) 1f else UNSUNG_ALPHA,
        label = "lyricTail",
    )
    val shape = Modifier
        .fillMaxWidth()
        // The lane the other voice sings in, kept clear.
        .padding(
            start = if (alignEnd) DUET_LANE else 0.dp,
            end = if (duet && !alignEnd) DUET_LANE else 0.dp,
        )
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(if (alignEnd) 1f else 0f, 0.5f)
            this.alpha = alpha
            // Held back against the list's own movement.
            translationY = if (stagger <= 0f) {
                0f
            } else {
                run.delta * (
                    LYRIC_EASING.transform((since / run.durationMs).coerceIn(0f, 1f)) -
                        LYRIC_EASING.transform(((since - stagger) / run.durationMs).coerceIn(0f, 1f))
                    )
            }
        }
        .blur(blurRadius)
        .clip(RoundedCornerShape(10.dp))
        .clickable(enabled = synced) { onSeek(line.timeMs) }

    // Lead and answering vocal are one row: they are one line of the song, they scale and dim
    // together, and clicking either seeks to the same place.
    Column(modifier = shape) {
        SweptLyricLine(
            line = line,
            clock = clock,
            style = style,
            dimAlpha = dim,
            feather = active,
            glowAlpha = glow,
            glowRoom = GLOW_ROOM,
            alignEnd = alignEnd,
            modifier = Modifier.fillMaxWidth(),
        )
        line.background?.let { backing ->
            SweptLyricLine(
                line = backing.withoutBracketPunctuation(),
                clock = clock,
                style = style.copy(fontSize = BACKING_FONT_SIZE, lineHeight = BACKING_LINE_HEIGHT),
                dimAlpha = dim,
                feather = false,
                // No bloom on the second voice.
                glowAlpha = 0f,
                alignEnd = alignEnd,
                modifier = Modifier
                    .fillMaxWidth()
                    // No top inset: the lead's own bottom room is the gap, which leaves the two
                    // voices closer to each other than to the rows either side.
                    .padding(start = GLOW_ROOM, end = GLOW_ROOM, bottom = GLOW_ROOM)
                    .graphicsLayer { this.alpha = BACKING_ALPHA },
            )
        }
    }
}

/** The answering vocal without the brackets it is published in. */
private fun DesktopLyricLine.withoutBracketPunctuation(): DesktopLyricLine = copy(
    text = text.stripParens(),
    words = words.mapNotNull { word ->
        word.text.stripParens().takeIf(String::isNotEmpty)?.let { word.copy(text = it) }
    },
)

private fun String.stripParens(): String = replace("(", "").replace(")", "").trim()

/** A break between verses, counted out rather than marked. */
@Composable
private fun LyricGapRow(
    lines: List<DesktopLyricLine>,
    index: Int,
    line: DesktopLyricLine,
    clock: MutableLongState,
    active: Boolean,
    alpha: Float,
    blurRadius: Dp,
) {
    val swell by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(if (active) 400 else 350, easing = LYRIC_EASING),
        label = "gapSwell",
    )
    val until = lines.getOrNull(index + 1)?.timeMs ?: line.endMs
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .padding(horizontal = LYRIC_GUTTER)
            .height((GAP_ROW_HEIGHT + GAP_ROW_SPACING) * swell)
            .clipToBounds(),
    ) {
        Box(
            Modifier
                .blur(blurRadius)
                .size(
                    width = GAP_DOT_SIZE * GAP_DOTS + GAP_DOT_GAP * (GAP_DOTS - 1),
                    height = GAP_DOT_SIZE,
                )
                .graphicsLayer {
                    val grow = GAP_REST_SCALE + (1f - GAP_REST_SCALE) * swell
                    scaleX = grow
                    scaleY = grow
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    this.alpha = alpha * swell
                }
                .drawBehind {
                    // Read here rather than in composition: the fill moves every frame, and this
                    // way a break costs a redraw of three circles rather than a recomposition.
                    val span = (until - line.timeMs).coerceAtLeast(1L)
                    val through = ((clock.longValue - line.timeMs).toFloat() / span).coerceIn(0f, 1f)
                    val radius = GAP_DOT_SIZE.toPx() / 2f
                    val stride = (GAP_DOT_SIZE + GAP_DOT_GAP).toPx()
                    repeat(GAP_DOTS) { dot ->
                        // Each dot owns its share of the break and fills across it, so they light
                        // left to right.
                        val lit = (through * GAP_DOTS - dot).coerceIn(0f, 1f)
                        drawCircle(
                            color = Color.White.copy(alpha = GAP_DOT_REST + (1f - GAP_DOT_REST) * lit),
                            radius = radius,
                            center = Offset(radius + dot * stride, size.height / 2f),
                        )
                    }
                },
        )
    }
}

/**
 * One line, drawn twice: a dim copy underneath and a lit one clipped to how far the singing has
 * got.
 */
/**
 * A lyric line with the sung part of it lit, the rest dimmed, and the boundary travelling across
 * the words in time with the vocal.
 */
@Composable
private fun SweptLyricLine(
    line: DesktopLyricLine,
    clock: MutableLongState,
    style: TextStyle,
    /** What the words not yet sung read at, under the lit copy. */
    dimAlpha: Float,
    feather: Boolean,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    glowAlpha: Float = 0f,
    glowRadius: Dp = GLOW_RADIUS,
    glowRoom: Dp = 0.dp,
    rise: Boolean = true,
    alignEnd: Boolean = false,
) {
    var layout by remember(line) { mutableStateOf<TextLayoutResult?>(null) }

    // No word timings: nothing to sweep, so the line is drawn once at whatever the caller decided
    // it should read at.
    if (!line.isWordSynced) {
        Text(
            line.text,
            style = style,
            color = Color.White.copy(alpha = dimAlpha),
            maxLines = maxLines,
            overflow = overflow,
            modifier = modifier,
        )
        return
    }

    // Filled in and read back a letter at a time inside the draw lambdas, and shared by all three
    // copies of the line.
    val growth = remember { DesktopCharGrowth() }

    // Carried by every copy: identical insets keep them laying out identically, and the inset is
    // what gives the blurred copy's layer somewhere to put the halo.
    val room = if (glowRoom > 0.dp) Modifier.padding(glowRoom) else Modifier

    // Sits outside [room] and outside the sweep, so what it moves is the finished picture of the
    // word.
    val riseAgainst: (Modifier) -> Modifier = { inner ->
        if (!rise) {
            inner
        } else {
            Modifier
                .drawWithContent {
                    val measured = layout
                    if (measured == null) {
                        drawContent()
                    } else {
                        riseWith(measured, line, clock.longValue, glowRoom.toPx(), WORD_RISE.toPx(), growth)
                    }
                }
                .then(inner)
        }
    }

    val sweep = Modifier.drawWithContent {
        val position = clock.longValue
        when {
            // Sung and done with: all of it is lit.
            position >= line.endMs -> drawContent()
            // Not started: nothing lit, the dim copy is the whole of it.
            position <= line.timeMs -> Unit
            else -> layout?.let { sweepTo(it, line.revealedChars(position), feather) }
        }
    }

    // A right-hand duet line right-aligns twice over.
    Box(modifier, contentAlignment = if (alignEnd) Alignment.TopEnd else Alignment.TopStart) {
        Text(
            line.text,
            style = style,
            color = Color.White.copy(alpha = dimAlpha),
            maxLines = maxLines,
            overflow = overflow,
            onTextLayout = { layout = it },
            modifier = riseAgainst(room),
        )
        if (glowAlpha > 0.01f) {
            Text(
                line.text,
                style = style,
                color = Color.White,
                maxLines = maxLines,
                overflow = overflow,
                modifier = Modifier
                    .graphicsLayer { alpha = glowAlpha }
                    .blur(glowRadius, BlurredEdgeTreatment.Unbounded)
                    // Each letter is masked to its own brightness with DstIn, which needs a layer
                    // of its own to erase into — against the backdrop it would take the artwork
                    // with it.
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        // Deliberately not the shared sweep: that lights everything sung so far,
                        // and this lights only the words being held.
                        val measured = layout ?: return@drawWithContent
                        glowGrown(measured, line, clock.longValue, glowRoom.toPx(), WORD_RISE.toPx(), growth)
                    }
                    .then(room),
            )
        }
        Text(
            line.text,
            style = style,
            color = Color.White,
            maxLines = maxLines,
            overflow = overflow,
            modifier = riseAgainst(
                Modifier
                    // The feather erases into this layer, so the layer has to exist — and only
                    // while it is being drawn.
                    .graphicsLayer {
                        compositingStrategy =
                            if (feather) CompositingStrategy.Offscreen else CompositingStrategy.Auto
                    }
                    .then(room)
                    .then(sweep),
            ),
        )
    }
}

/** Clips this draw to the sung part of the line. */
private fun ContentDrawScope.sweepTo(layout: TextLayoutResult, revealedChars: Float, feather: Boolean) {
    if (revealedChars <= 0f) return
    if (revealedChars >= layout.layoutInput.text.length) {
        drawContent()
        return
    }
    for (visualLine in 0 until layout.lineCount) {
        val start = layout.getLineStart(visualLine)
        if (revealedChars <= start) return
        val end = layout.getLineEnd(visualLine, visibleEnd = true)
        val cut = revealedChars < end
        val right = if (cut) horizontalAt(layout, revealedChars, visualLine) else layout.getLineRight(visualLine)
        val top = layout.getLineTop(visualLine)
        val bottom = layout.getLineBottom(visualLine)
        clipRect(left = layout.getLineLeft(visualLine), top = top, right = right, bottom = bottom) {
            this@sweepTo.drawContent()
        }
        // Only the visual line holding the boundary has an edge to soften; one revealed to its end
        // runs into the wrap, which is not an edge.
        if (!feather || !cut) continue
        // Scoped to this line's band so the mask cannot reach the lines above and below it.
        clipRect(top = top, bottom = bottom) {
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.White,
                    1f to Color.Transparent,
                    startX = (right - WIPE_FEATHER.toPx()).coerceAtLeast(layout.getLineLeft(visualLine)),
                    endX = right,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
    }
}

/** Where an offset sits horizontally *on the row it was cut out of*. */
private fun TextLayoutResult.xOn(offset: Int, visualLine: Int, inset: Float): Float {
    val left = getLineLeft(visualLine) + inset
    val right = getLineRight(visualLine) + inset
    return when {
        offset <= getLineStart(visualLine) -> left
        offset >= getLineEnd(visualLine, visibleEnd = true) -> right
        else -> (getHorizontalPosition(offset, usePrimaryDirection = true) + inset).coerceIn(left, right)
    }
}

/** Where a fractional character index sits across a visual line, in pixels. */
private fun horizontalAt(layout: TextLayoutResult, chars: Float, visualLine: Int): Float {
    val lineStart = layout.getLineStart(visualLine)
    val lineEnd = layout.getLineEnd(visualLine, visibleEnd = true)
    val index = chars.toInt().coerceIn(lineStart, lineEnd)
    // Row-aware at both ends.
    val here = layout.xOn(index, visualLine, 0f)
    val next = layout.xOn((index + 1).coerceAtMost(lineEnd), visualLine, 0f)
    return here + (next - here) * (chars - index)
}

/** Draws this text clipped to the letters of the words being held, each at its own brightness. */
private fun ContentDrawScope.glowGrown(
    layout: TextLayoutResult,
    line: DesktopLyricLine,
    positionMs: Long,
    inset: Float,
    peak: Float,
    growth: DesktopCharGrowth,
) {
    if (!line.isGrowing(positionMs)) return
    val em = layout.layoutInput.style.fontSize.toPx()
    val length = layout.layoutInput.text.length
    for (word in line.growingWords) {
        if (positionMs < word.startMs || positionMs > word.restsAtMs) continue
        val span = line.wordSpans[word.index]
        val fall = line.wordFall(word.index, positionMs)
        for (char in span.first..minOf(span.last, length - 1)) {
            word.sampleInto(char - span.first, positionMs, growth)
            if (growth.bloom <= 0.01f) continue
            val visualLine = layout.getLineForOffset(char)
            // Row-aware, for the same reason the sweep is; see [xOn].
            val from = layout.xOn(char, visualLine, inset)
            val to = layout.xOn(char + 1, visualLine, inset)
            if (to <= from) continue
            val dx = growth.shift * em
            val dy = -growth.rise * peak * fall
            val rowTop = layout.getLineTop(visualLine) + inset
            val bottom = layout.getLineBottom(visualLine) + inset
            val overhang = (to - from) * (growth.scale - 1f) / 2f
            clipRect(
                left = from - overhang + dx,
                top = rowTop - peak * GROW_HEADROOM,
                right = to + overhang + dx,
                bottom = bottom,
            ) {
                translate(left = dx, top = dy) {
                    scale(growth.scale, growth.scale, Offset((from + to) / 2f, (rowTop + bottom) / 2f)) {
                        this@glowGrown.drawContent()
                    }
                }
                // Scoped to this letter's own clip, so it takes this letter's brightness down and
                // leaves its neighbours — which have their own, a beat behind — where they are.
                drawRect(color = Color.White.copy(alpha = growth.bloom), blendMode = BlendMode.DstIn)
            }
        }
    }
}

/**
 * Redraws this row with the word being sung lifted off the line, and the ones behind it settling
 * back down.
 */
private fun ContentDrawScope.riseWith(
    layout: TextLayoutResult,
    line: DesktopLyricLine,
    positionMs: Long,
    inset: Float,
    peak: Float,
    growth: DesktopCharGrowth,
) {
    if (!line.isLifted(positionMs)) {
        drawContent()
        return
    }
    val em = layout.layoutInput.style.fontSize.toPx()
    for (visualLine in 0 until layout.lineCount) {
        val lineStart = layout.getLineStart(visualLine)
        val lineEnd = layout.getLineEnd(visualLine, visibleEnd = true)
        // The row's own box.
        val top = layout.getLineTop(visualLine) + inset
        val bottom = layout.getLineBottom(visualLine) + inset
        var at = lineStart
        var edge = layout.getLineLeft(visualLine) + inset
        for (index in line.words.indices) {
            val span = line.wordSpans[index]
            val from = maxOf(span.first, lineStart)
            val until = minOf(span.last + 1, lineEnd)
            if (from >= until) continue
            // Only while it is actually moving.
            val held = line.growingAt(index)?.takeIf { positionMs in it.startMs..it.restsAtMs }
            val lift = line.wordLift(index, positionMs)
            // A word with nothing happening to it is left to the flat run, which is the whole of
            // the line for all but a syllable of it.
            if (held == null && lift <= 0.01f) continue
            val left = layout.xOn(from, visualLine, inset)
            val right = layout.xOn(until, visualLine, inset)
            // Nothing to cut.
            if (right <= left) continue
            // Everything between the last risen word and this one is flat, and goes down in one
            // piece however many words that spans.
            if (from > at) sliceRisen(edge, top, left, bottom, 0f)
            if (held != null) {
                growEach(layout, held, line, positionMs, visualLine, from, until, top, bottom, inset, peak, em, growth)
            } else {
                // Only what is off the floor gets room above the row to be off it in; see [top].
                sliceRisen(left, top - peak, right, bottom, -lift * peak)
            }
            at = until
            edge = right
        }
        if (at < lineEnd) sliceRisen(edge, top, layout.getLineRight(visualLine) + inset, bottom, 0f)
    }
}

/** Redraws one held word a letter at a time, each at its own swell and height. */
@Suppress("LongParameterList")
private fun ContentDrawScope.growEach(
    layout: TextLayoutResult,
    word: DesktopGrowingWord,
    line: DesktopLyricLine,
    positionMs: Long,
    visualLine: Int,
    start: Int,
    end: Int,
    top: Float,
    bottom: Float,
    inset: Float,
    peak: Float,
    em: Float,
    growth: DesktopCharGrowth,
) {
    // The settle is shared with every other word: a letter comes to rest at the same small lift,
    // and then goes down with the rest of the line.
    val fall = line.wordFall(word.index, positionMs)
    val first = line.wordSpans[word.index].first
    // Room to swell into, above the row rather than inside it.
    val ceiling = top - peak * GROW_HEADROOM
    val middle = (top + bottom) / 2f
    for (char in start until end) {
        word.sampleInto(char - first, positionMs, growth)
        val from = layout.xOn(char, visualLine, inset)
        val to = layout.xOn(char + 1, visualLine, inset)
        if (to <= from) continue
        val dx = growth.shift * em
        val dy = -growth.rise * peak * fall
        val overhang = (to - from) * (growth.scale - 1f) / 2f
        clipRect(left = from - overhang + dx, top = ceiling, right = to + overhang + dx, bottom = bottom) {
            translate(left = dx, top = dy) {
                scale(growth.scale, growth.scale, Offset((from + to) / 2f, middle)) {
                    this@growEach.drawContent()
                }
            }
        }
    }
}

/** One piece of a line, clipped to its own width and drawn at its own height. */
private fun ContentDrawScope.sliceRisen(from: Float, top: Float, to: Float, bottom: Float, dy: Float) {
    if (to <= from) return
    clipRect(left = from, top = top, right = to, bottom = bottom) {
        translate(top = dy) { this@sliceRisen.drawContent() }
    }
}

/** A clock that runs between position reports. */
@Composable
private fun rememberLyricClock(positionMs: Long, isPlaying: Boolean): MutableLongState {
    val clock = remember { mutableLongStateOf(positionMs) }
    LaunchedEffect(positionMs, isPlaying) {
        clock.longValue = reconcileLyricPosition(clock.longValue, positionMs)
        if (!isPlaying) return@LaunchedEffect
        val firstFrame = withFrameMillis { it }
        while (true) {
            withFrameMillis { frame ->
                clock.longValue = maxOf(clock.longValue, positionMs + frame - firstFrame)
            }
        }
    }
    return clock
}

/** One handover: how far the panel is going, and how long it is taking. */
private class LyricScrollRun(val id: Int, val delta: Float, val durationMs: Int) {
    /** The last row to arrive does so this long after the panel sets off. */
    val spanMs: Float get() = durationMs * (1f + STAGGER_FRACTION * STAGGER_STEPS)
}

/** The curve every handover runs on: away quickly, in slowly and softly. */
private val LYRIC_EASING = CubicBezierEasing(0.41f, 0f, 0.12f, 0.99f)
private const val LYRIC_SETTLE_MS = 400

/** How the rows fan out as the panel moves between lines. */
private const val STAGGER_STEPS = 3
private const val STAGGER_FRACTION = 0.06f

/** How the stack falls away either side of the line being sung, indexed by distance from it. */
private val LINE_FALLOFF_ALPHA = floatArrayOf(1f, 0.8f, 0.7f, 0.58f, 0.46f)
private val LINE_FALLOFF_BLUR = arrayOf(0.dp, 1.dp, 1.dp, 1.7.dp, 2.4.dp)

/** What a line reads at while the list is being scrolled by hand. */
private const val BROWSING_ALPHA = 0.8f

/** The playing line sits at 1; the rest sit fractionally back from it. */
private const val INACTIVE_SCALE = 0.98f

/** What the words yet to be sung read at, under the lit copy. */
private const val UNSUNG_ALPHA = 0.42f

/** How far the sweep's leading edge fades out instead of ending on a cut. */
private val WIPE_FEATHER = 30.dp

/** How far the word being sung lifts off the line. */
private val WORD_RISE = 2.dp

/** How far above its row a swelling letter may reach, in lifts. */
private const val GROW_HEADROOM = 3f

/** Apple's bloom: how far it spreads, and how strong it is at its brightest. */
private val GLOW_RADIUS = 6.dp
private const val GLOW_ALPHA = 0.62f

/** The inset every copy of a line carries so the bloom has somewhere to spread into. */
private val GLOW_ROOM = 10.dp

/** The lane the other voice sings in, kept clear on a duet. */
private val DUET_LANE = 44.dp

/** The answering vocal, drawn under the lead. */
private val BACKING_FONT_SIZE = 22.sp
private val BACKING_LINE_HEIGHT = 28.sp
private const val BACKING_ALPHA = 0.72f

private val LYRIC_GUTTER = 10.dp

private val GAP_ROW_HEIGHT = 40.dp
private val GAP_ROW_SPACING = 16.dp
private const val GAP_DOTS = 3
private val GAP_DOT_SIZE = 13.dp
private val GAP_DOT_GAP = 5.dp

/** What an unlit dot still shows: enough to say how many are coming. */
private const val GAP_DOT_REST = 0.25f
private const val GAP_REST_SCALE = 0.76f
