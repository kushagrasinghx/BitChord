package com.music.bitchord.ui.player

import com.music.bitchord.ui.components.trackArtSize
import androidx.compose.ui.layout.ContentScale
import com.music.bitchord.sharedui.resources.*
import com.music.bitchord.ui.components.ExplicitSongTitle

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.music.bitchord.ui.components.longPressMenuClickable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Velocity
import coil3.compose.AsyncImage
import com.music.bitchord.ui.components.thumbnailBorder
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.autoplaySectionStart
import com.music.bitchord.ui.AppUi
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Swallows whatever scroll the queue list itself didn't use. The player is a
 * ModalBottomSheet, and the sheet's own nested-scroll handler reads that
 * leftover as "drag me down" — so scrolling the queue would slide the player
 * away. Consuming it here keeps the gesture inside the list.
 *
 * A downward *fling* has to be caught in the pre-phase, before the sheet sees
 * it, but only at the top of the list — otherwise the queue could never fling.
 */
internal fun keepScrollInList(listState: LazyListState) = object : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset = available

    override suspend fun onPreFling(available: Velocity): Velocity =
        if (available.y > 0f && !listState.canScrollBackward) available else Velocity.Zero

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
}

/**
 * How a queue row moves when the running order changes under it.
 *
 * Placement only — the fades are off deliberately. A row Compose treats as
 * removed or inserted goes on being drawn at the slot it used to hold for as
 * long as it takes to fade, and a queue row's background is transparent: the
 * fading copy and whichever row is sliding through that slot both draw their
 * title and artist into the same few pixels, which reads as one song's name
 * printed over another's rather than as anything moving. Without the fades a
 * row that leaves is gone the moment it leaves, so movement is the only thing
 * left to see.
 *
 * Bounded rather than the default spring for a related reason: every track
 * change moves the section boundary — see [autoplaySectionStart] — so the
 * newly current row and the AutoPlay heading trade places, and a low-stiffness
 * spring is still visibly settling that trade long after the track changed.
 */
private val QUEUE_ROW_MOTION = tween<IntOffset>(durationMillis = 200, easing = FastOutSlowInEasing)

/** Softens the list where it meets the header and the scrubber. */
internal fun Modifier.fadingEdges(): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val fade = 28.dp.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black),
                startY = 0f,
                endY = fade,
            ),
            blendMode = BlendMode.DstIn,
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Black, Color.Transparent),
                startY = size.height - fade,
                endY = size.height,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/** One track in [InlineQueue], and where it sits in the player's timeline. */
internal data class QueueTrack(
    val timelineIndex: Int,
    val song: Song,
    val key: String,
    val uniqueEntryId: String? = null,
    val uniqueSongReference: Boolean = false,
)

/** Native snapshots can copy metadata, but must retain a unique occurrence id. */
private fun QueueTrack.sameEntryAs(other: QueueTrack): Boolean =
    if (uniqueEntryId != null && other.uniqueEntryId != null) {
        uniqueEntryId == other.uniqueEntryId && song.videoId == other.song.videoId
    } else {
        uniqueSongReference && other.uniqueSongReference && song === other.song
    }

/** The groups a queue row can be dragged within; a drag never leaves its own. */
internal enum class QueueSection { USER, CONTEXT, AUTOPLAY }

/** The queue as [InlineQueue] lists it: what's playing, then each section in running order. */
internal class QueueTracks(
    val nowPlaying: QueueTrack?,
    val user: List<QueueTrack>,
    val context: List<QueueTrack>,
    val autoplay: List<QueueTrack>,
    val history: List<QueueTrack> = emptyList(),
) {
    operator fun get(section: QueueSection): List<QueueTrack> = when (section) {
        QueueSection.USER -> user
        QueueSection.CONTEXT -> context
        QueueSection.AUTOPLAY -> autoplay
    }

    companion object {
        val EMPTY = QueueTracks(null, emptyList(), emptyList(), emptyList())
    }
}

internal fun splitQueue(queue: List<Song>, currentIndex: Int, showHistory: Boolean = false): QueueTracks {
    if (currentIndex !in queue.indices) return QueueTracks.EMPTY
    // Keyed by the entry's own id, so a row keeps its identity through a reorder.
    // Entries without one (a party's tracks, a queue restored from before ids
    // existed) fall back to the song plus how many times it has come up so far
    // — never its position, which changes on every swap and would end the drag.
    val seen = HashMap<String, Int>()
    val occurrence = IntArray(queue.size) { i ->
        val id = queue[i].videoId
        seen.getOrDefault(id, 0).also { seen[id] = it + 1 }
    }
    // A lazy list throws on a repeated key, and two rows can share an entry id
    // (a queue restored twice, an add replayed); the repeat gets a suffix
    // instead of taking the whole player down.
    val usedKeys = HashSet<String>()
    val entryCounts = queue.mapNotNull { it.queueEntryId }.groupingBy { it }.eachCount()
    val referenceCounts = java.util.IdentityHashMap<Song, Int>()
    queue.forEach { referenceCounts[it] = (referenceCounts[it] ?: 0) + 1 }
    fun track(index: Int, prefix: String): QueueTrack {
        val song = queue[index]
        val base = prefix + "_" + (song.queueEntryId ?: "${song.videoId}#${occurrence[index]}")
        var key = base
        var repeat = 1
        while (!usedKeys.add(key)) key = base + "~" + repeat++
        return QueueTrack(
            index, song, key,
            uniqueEntryId = song.queueEntryId?.takeIf { entryCounts[it] == 1 },
            uniqueSongReference = referenceCounts[song] == 1,
        )
    }
    val user = ArrayList<QueueTrack>()
    val context = ArrayList<QueueTrack>()
    val autoplay = ArrayList<QueueTrack>()
    for (index in currentIndex + 1 until queue.size) {
        when (queue[index].queueTier) {
            QueueTier.USER_QUEUE -> user += track(index, "user")
            QueueTier.CONTEXT -> context += track(index, "context")
            QueueTier.AUTOPLAY -> autoplay += track(index, "autoplay")
        }
    }
    val history = if (showHistory) (0 until currentIndex).map { track(it, "history") } else emptyList()
    return QueueTracks(track(currentIndex, "np"), user, context, autoplay, history)
}

/** Rows fade only on arrival; departing rows have already slid out and collapsed. */
private fun LazyItemScope.queueMotion(reduceAnimation: Boolean, heading: Boolean = false): Modifier =
    if (reduceAnimation) Modifier else Modifier.animateItem(
        fadeInSpec = tween(160),
        fadeOutSpec = if (heading) tween(140) else null,
        placementSpec = QUEUE_ROW_MOTION,
    )

/** The live queue, in the player itself. */
@Composable
internal fun InlineQueue(
    queue: List<Song>,
    currentIndex: Int,
    autoplayEnabled: Boolean,
    /**
     * Read-only: the party's running order is the host's while this is set, so
     * the queue is here to be looked at and scrolled, not worked.
     */
    controlsLocked: Boolean,
    onJumpTo: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onClear: () -> Unit,
    onSongMenu: (Int, Song) -> Unit = { _, _ -> },
    onSwipeToQueue: suspend (Int, Song) -> Boolean = { _, _ -> false },
    undoNotice: @Composable () -> Unit = {},
    selection: QueueSelectionState? = null,
    onBulkAction: suspend (List<Song>, QueueBulkAction) -> Boolean = { _, _ -> false },
    onAddAllToPlaylist: (List<Song>) -> Unit = {},
    /** Prewarmed/offscreen queue panes must not accept or finish a swipe. */
    gesturesEnabled: Boolean = true,
    onScrollingChange: (Boolean) -> Unit = {},
    onDragActiveChange: (Boolean) -> Unit = {},
    /** Phone only: let queue scrolling dismiss/restore the lower half player. */
    collapsePlayerOnScroll: Boolean = false,
    onRevealPlayer: () -> Unit = {},
    onHidePlayer: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val selectionHaze = remember { HazeState() }
    val density = LocalDensity.current
    var footerHeight by remember { mutableStateOf(0.dp) }
    val reduceDynamicBlur by PlayerSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect(onScrollingChange)
    }
    val keepScroll = remember(listState) { keepScrollInList(listState) }
    val controlsOnScroll = rememberPlayerControlsOnScroll(
        enabled = collapsePlayerOnScroll,
        onReveal = onRevealPlayer,
        onHide = onHidePlayer,
    )
    val showHistory by PlayerSettings.showQueueHistory.collectAsStateWithLifecycle()
    val reduceAnimation by PlayerSettings.reduceAnimation.collectAsStateWithLifecycle()
    val swipeToPlayNext by AppUi.host.swipeToPlayNext.collectAsStateWithLifecycle()
    val tracks = remember(queue, currentIndex, showHistory) { splitQueue(queue, currentIndex, showHistory) }
    val selectionState = selection ?: remember { QueueSelectionState() }
    val allTracks = remember(tracks) { tracks.rows }
    val latestTracks by rememberUpdatedState(tracks)
    val drag = rememberQueueDrag(listState, tracks, onMove, onDragActiveChange)
    LaunchedEffect(tracks, selectionState.active) {
        selectionState.retain(allTracks)
        if (selectionState.active) drag.held?.let { drag.end(it.key) }
    }
    val swipesEnabled = !controlsLocked && !selectionState.active && gesturesEnabled && drag.held == null
    fun addAll(rows: List<QueueTrack>) {
        if (!selectionState.active && rows.isNotEmpty()) onAddAllToPlaylist(rows.map { it.song })
    }
    fun resolveSwipeTrack(original: QueueTrack): QueueTrack? {
        val live = tracks.nowPlaying?.takeIf { it.key == original.key }
            ?: tracks.history.firstOrNull { it.key == original.key }
            ?: tracks.user.firstOrNull { it.key == original.key }
            ?: tracks.context.firstOrNull { it.key == original.key }
            ?: tracks.autoplay.firstOrNull { it.key == original.key }
        // Legacy/duplicate ids still require the same Song instance; only ids
        // unique in BOTH snapshots may survive native metadata copies.
        return live?.takeIf { it.sameEntryAs(original) }
    }
    val canCommitSwipe: (QueueTrack, QueueSwipeAction) -> Boolean = { original, action ->
        val live = resolveSwipeTrack(original)
        val destination = if (swipeToPlayNext) tracks.user.firstOrNull() else tracks.user.lastOrNull()
        swipesEnabled && live != null &&
            (action == QueueSwipeAction.REMOVE ||
                (live.key != tracks.nowPlaying?.key && live.key != destination?.key))
    }
    val commitSwipe: suspend (QueueTrack, QueueSwipeAction) -> Boolean = { original, action ->
        val live = resolveSwipeTrack(original)
        if (live != null && canCommitSwipe(original, action)) {
            when (action) {
                QueueSwipeAction.REMOVE -> {
                    onRemove(live.timelineIndex)
                    true
                }
                QueueSwipeAction.QUEUE -> {
                    val moveToHead = swipeToPlayNext
                    if (onSwipeToQueue(live.timelineIndex, live.song)) {
                        // Native acknowledgement can precede the UI snapshot.
                        // A same-key manual row stays hidden until arrival is real.
                        withTimeoutOrNull(2_000) {
                            snapshotFlow {
                                val rows = latestTracks.user
                                val destination = if (moveToHead) rows.firstOrNull() else rows.lastOrNull()
                                destination != null && destination.sameEntryAs(live)
                            }.first { it }
                        } == true
                    } else false
                }
            }
        } else false
    }
    val nowPlaying = tracks.nowPlaying
    val contextSong = tracks.context.firstOrNull()?.song
    val contextTitle = contextSong?.playbackSource?.takeIf { it.isNotBlank() }
        ?: contextSong?.albumName?.takeIf { it.isNotBlank() }
        ?: nowPlaying?.song?.playbackSource?.takeIf { it.isNotBlank() }
        ?: nowPlaying?.song?.albumName?.takeIf { it.isNotBlank() }

    // Keep Now playing in view; history is available by scrolling upwards.
    // Include the entry key because trimming history can leave the index unchanged.
    var hasScrolledOnce by remember { mutableStateOf(false) }
    LaunchedEffect(currentIndex, nowPlaying?.key, showHistory) {
        val nowPlayingRow = if (tracks.history.isEmpty()) 0 else tracks.history.size + 1
        val scrolledAway = listState.firstVisibleItemIndex != nowPlayingRow || listState.firstVisibleItemScrollOffset != 0
        if (!selectionState.active && drag.held == null && nowPlaying != null && scrolledAway) {
            if (hasScrolledOnce) {
                listState.animateScrollToItem(nowPlayingRow)
            } else {
                listState.scrollToItem(nowPlayingRow)
                hasScrolledOnce = true
            }
        }
    }

    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (selectionState.active) {
                    stringResource(Res.string.queue_selected_count, selectionState.selected(allTracks).size)
                } else stringResource(Res.string.queue),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            if (selectionState.active) {
                TextButton(
                    onClick = { selectionState.selectAll(allTracks) },
                    enabled = !selectionState.busy && allTracks.isNotEmpty(),
                ) {
                    Text(stringResource(Res.string.queue_select_all), color = Color.White)
                }
                IconButton(onClick = selectionState::clear) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(Res.string.queue_close_selection), tint = Color.White)
                }
            } else {
                Text(
                    text = stringResource(Res.string.queue_select),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = if (controlsLocked) 0.25f else 0.75f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .clickable(enabled = !controlsLocked, onClick = selectionState::start)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .bleedHorizontally(PLAYER_GUTTER),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .fadingEdges()
                    .then(
                        if (!reduceDynamicBlur) {
                            // Below any enclosing source, so the sibling frost can sample it.
                            Modifier.hazeSource(selectionHaze, zIndex = -1f)
                        } else Modifier,
                    )
                    // Without this the sheet treats the list's leftover scroll as a
                    // drag on itself and slides the whole player away.
                    .nestedScroll(keepScroll)
                    .then(if (collapsePlayerOnScroll) Modifier.nestedScroll(controlsOnScroll) else Modifier),
                contentPadding = PaddingValues(start = PLAYER_GUTTER, end = PLAYER_GUTTER, bottom = footerHeight),
            ) {
                if (tracks.history.isNotEmpty()) {
                    item(key = "header-history") {
                        QueueHeading(
                            title = stringResource(Res.string.previously_played),
                            modifier = queueMotion(reduceAnimation, heading = true).padding(top = 12.dp, bottom = 6.dp),
                        ) {
                            if (!selectionState.active) QueueAddAllButton(controlsLocked) { addAll(tracks.history) }
                        }
                    }
                    items(tracks.history, key = { it.key }) { track ->
                        SwipeableQueueRow(
                            track = track,
                            isCurrent = false,
                            onClick = { onJumpTo(track.timelineIndex) },
                            onRemove = { onRemove(track.timelineIndex) },
                            onLongPress = { onSongMenu(track.timelineIndex, track.song) },
                            locked = controlsLocked,
                            selection = selectionState,
                            swipesEnabled = swipesEnabled,
                            reduceAnimation = reduceAnimation,
                            canCommitSwipe = canCommitSwipe,
                            commitSwipe = commitSwipe,
                            modifier = queueMotion(reduceAnimation),
                        )
                    }
                }
                if (nowPlaying != null) {
                    item(key = "header-now-playing") {
                        QueueHeading(
                            title = stringResource(Res.string.now_playing),
                            modifier = queueMotion(reduceAnimation, heading = true).padding(top = 12.dp, bottom = 6.dp),
                        )
                    }
                    item(key = nowPlaying.key) {
                        SwipeableQueueRow(
                            track = nowPlaying,
                            isCurrent = true,
                            onClick = { onJumpTo(nowPlaying.timelineIndex) },
                            onRemove = { onRemove(nowPlaying.timelineIndex) },
                            onLongPress = { onSongMenu(nowPlaying.timelineIndex, nowPlaying.song) },
                            locked = controlsLocked,
                            selection = selectionState,
                            swipesEnabled = swipesEnabled,
                            reduceAnimation = reduceAnimation,
                            canCommitSwipe = canCommitSwipe,
                            commitSwipe = commitSwipe,
                            modifier = queueMotion(reduceAnimation),
                        )
                    }
                }
                if (tracks.user.isNotEmpty()) {
                    item(key = "header-user-queue") {
                        QueueHeading(
                            title = stringResource(Res.string.next_in_queue),
                            modifier = queueMotion(reduceAnimation, heading = true).padding(top = 16.dp, bottom = 6.dp),
                        ) {
                            if (!selectionState.active) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    QueueAddAllButton(controlsLocked) { addAll(tracks.user) }
                                    Spacer(Modifier.width(4.dp))
                                    QueueClearButton(MaterialTheme.typography.labelLarge, controlsLocked, onClear)
                                }
                            }
                        }
                    }
                    queueSection(tracks.user, QueueSection.USER, drag, controlsLocked, onJumpTo, onRemove, onSongMenu,
                        swipesEnabled, reduceAnimation, canCommitSwipe, commitSwipe, selectionState)
                }
                if (tracks.context.isNotEmpty()) {
                    item(key = "header-context") {
                        QueueHeading(
                            title = if (contextTitle != null) {
                                stringResource(Res.string.next_from, contextTitle)
                            } else {
                                stringResource(Res.string.queue)
                            },
                            modifier = queueMotion(reduceAnimation, heading = true).padding(top = 16.dp, bottom = 6.dp),
                        ) {
                            if (!selectionState.active) QueueAddAllButton(controlsLocked) { addAll(tracks.context) }
                        }
                    }
                    queueSection(tracks.context, QueueSection.CONTEXT, drag, controlsLocked, onJumpTo, onRemove, onSongMenu,
                        swipesEnabled, reduceAnimation, canCommitSwipe, commitSwipe, selectionState)
                }
                if (autoplayEnabled || tracks.autoplay.isNotEmpty()) {
                    item(key = "autoplay-heading") {
                        AutoplayHeading(hasTracks = tracks.autoplay.isNotEmpty(), modifier = queueMotion(reduceAnimation, heading = true)) {
                            if (!selectionState.active && tracks.autoplay.isNotEmpty()) {
                                QueueAddAllButton(controlsLocked) { addAll(tracks.autoplay) }
                            }
                        }
                    }
                    queueSection(tracks.autoplay, QueueSection.AUTOPLAY, drag, controlsLocked, onJumpTo, onRemove, onSongMenu,
                        swipesEnabled, reduceAnimation, canCommitSwipe, commitSwipe, selectionState)
                }
            }
            // The held row, drawn over the list where the finger is. Positioned
            // from the finger alone, so nothing the list does underneath — a
            // swap, a relayout, a scroll — can move it.
            drag.held?.let { held ->
                Box(Modifier.matchParentSize().padding(horizontal = PLAYER_GUTTER)) {
                    InlineQueueRow(
                        song = held.song,
                        isCurrent = false,
                        onClick = {},
                        onRemove = {},
                        draggable = true,
                        lifted = true,
                        modifier = Modifier.offset { IntOffset(0, drag.heldTop.roundToInt()) },
                    )
                }
            }
            // A sibling of the source: the list scrolls behind the bar, never captures it.
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = PLAYER_GUTTER)
                    .onSizeChanged { footerHeight = with(density) { it.height.toDp() } },
            ) {
                undoNotice()
                QueueSelectionBar(selectionState, allTracks, reduceAnimation, onBulkAction, selectionHaze)
            }
        }
    }
}

/**
 * One section's rows. The row being dragged stays in the list as an invisible
 * stand-in: it keeps the gesture and trades slots with its neighbours, which
 * slide aside, while the copy the user sees is drawn over the list by
 * [InlineQueue].
 */
private fun LazyListScope.queueSection(
    rows: List<QueueTrack>,
    section: QueueSection,
    drag: QueueDrag,
    locked: Boolean,
    onJumpTo: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onSongMenu: (Int, Song) -> Unit,
    swipesEnabled: Boolean,
    reduceAnimation: Boolean,
    canCommitSwipe: (QueueTrack, QueueSwipeAction) -> Boolean,
    commitSwipe: suspend (QueueTrack, QueueSwipeAction) -> Boolean,
    selection: QueueSelectionState,
) {
    items(rows, key = { it.key }) { track ->
        val held = drag.held?.key == track.key
        if (held) {
            // Scrolled far enough to be disposed, the row takes its gesture
            // with it and neither drag-end callback runs — end the drag here.
            DisposableEffect(Unit) { onDispose { drag.end(track.key) } }
        }
        SwipeableQueueRow(
            track = track,
            isCurrent = false,
            onClick = { onJumpTo(track.timelineIndex) },
            onRemove = { onRemove(track.timelineIndex) },
            onLongPress = { onSongMenu(track.timelineIndex, track.song) },
            locked = locked,
            selection = selection,
            draggable = !locked,
            onDragStart = { drag.start(track.key, section) },
            onDrag = drag::drag,
            onDragEnd = { drag.end(track.key) },
            swipesEnabled = swipesEnabled && !held,
            reduceAnimation = reduceAnimation,
            canCommitSwipe = canCommitSwipe,
            commitSwipe = commitSwipe,
            modifier = if (held) Modifier.alpha(0f) else queueMotion(reduceAnimation),
        )
    }
}

/** The wrapper owns swipe motion; the original row and its handle detector stay intact. */
@Composable
private fun SwipeableQueueRow(
    track: QueueTrack,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onLongPress: () -> Unit,
    locked: Boolean,
    swipesEnabled: Boolean,
    reduceAnimation: Boolean,
    canCommitSwipe: (QueueTrack, QueueSwipeAction) -> Boolean,
    commitSwipe: suspend (QueueTrack, QueueSwipeAction) -> Boolean,
    modifier: Modifier = Modifier,
    selection: QueueSelectionState? = null,
    draggable: Boolean = false,
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
) {
    if (selection?.active == true) {
        InlineQueueRow(
            song = track.song,
            isCurrent = isCurrent,
            onClick = { selection.toggle(track) },
            onRemove = {},
            locked = locked || selection.busy,
            selectionChecked = selection.isSelected(track),
            modifier = modifier,
        )
        return
    }
    QueueSwipeRow(
        track = track,
        enabled = swipesEnabled,
        reduceAnimation = reduceAnimation,
        canCommit = canCommitSwipe,
        onCommit = commitSwipe,
        modifier = modifier,
        allowQueueGesture = !isCurrent,
    ) { interactive ->
        // The original handle's pointerInput(Unit) intentionally survives
        // recomposition. Its callbacks read live gates, not captured ones.
        val currentInteractive by rememberUpdatedState(interactive)
        val currentDragStart by rememberUpdatedState(onDragStart)
        val currentDrag by rememberUpdatedState(onDrag)
        val currentDragEnd by rememberUpdatedState(onDragEnd)
        var handleDragging by remember(track.key) { mutableStateOf(false) }
        InlineQueueRow(
            song = track.song,
            isCurrent = isCurrent,
            onClick = { if (interactive) onClick() },
            onRemove = { if (interactive) onRemove() },
            onLongPress = { if (interactive) onLongPress() },
            locked = locked,
            draggable = draggable,
            onDragStart = {
                handleDragging = currentInteractive
                if (handleDragging) currentDragStart()
            },
            onDrag = { if (handleDragging) currentDrag(it) },
            onDragEnd = {
                if (handleDragging) currentDragEnd()
                handleDragging = false
            },
        )
    }
}

@Composable
private fun QueueHeading(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.75f),
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueAddAllButton(locked: Boolean, onClick: () -> Unit) {
    val label = stringResource(Res.string.queue_add_all_to_playlist)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip(containerColor = Color(0xFF282828), contentColor = Color.White) {
                Text(label, color = Color.White)
            }
        },
        state = rememberTooltipState(),
        focusable = false,
        enableUserInput = !locked,
    ) {
        IconButton(onClick = onClick, enabled = !locked) {
            Icon(
                Icons.AutoMirrored.Rounded.PlaylistAdd,
                contentDescription = label,
                tint = Color.White.copy(alpha = if (locked) 0.25f else 0.75f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun QueueClearButton(style: TextStyle, locked: Boolean, onClear: () -> Unit) {
    Text(
        text = stringResource(Res.string.clear),
        style = style,
        color = Color.White.copy(alpha = if (locked) 0.25f else 0.75f),
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .clickable(enabled = !locked, onClick = onClear)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun AutoplayHeading(
    hasTracks: Boolean,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            BitChordIcons.Infinity,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.75f),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.autoplay),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            Text(
                text = stringResource(
                    if (hasTracks) Res.string.autoplay_queue_description else Res.string.autoplay_empty_description,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.55f),
            )
        }
        trailing()
    }
}

/**
 * How far in from either end of the queue a held row starts scrolling the list,
 * and how fast it scrolls once it is all the way at the edge.
 *
 * The zone is a shade deeper than the 28.dp the list fades out over, so the
 * list is already moving by the time the row begins to disappear into the fade
 * rather than only once it has. The speed at the edge is about six rows a
 * second: quick enough to cross a long queue without waiting on it, slow
 * enough to still read the titles going past and stop on the right one.
 */
private val QUEUE_EDGE_SCROLL_ZONE = 40.dp
private val QUEUE_EDGE_SCROLL_SPEED = 340.dp

/**
 * The pace, in pixels a second, to scroll a list at while a row occupying
 * [top] to [bottom] is held in a viewport spanning [viewportStart] to
 * [viewportEnd] — negative towards the start of the list, positive towards its
 * end, and zero while the row is clear of both edges.
 *
 * Ramped by how far into the [zone] the row has reached, so how fast the queue
 * goes by stays the user's to choose — but from a fifth of [speed] rather than
 * from nothing, since a row just inside the zone should visibly move the list
 * instead of creeping a pixel a second until it is pushed further. A viewport
 * too short to hold the row clear of both edges at once scrolls neither way,
 * rather than picking one arbitrarily and running away with it.
 */
fun edgeScrollSpeed(
    top: Float,
    bottom: Float,
    viewportStart: Int,
    viewportEnd: Int,
    zone: Float,
    speed: Float,
): Float {
    if (zone <= 0f) return 0f
    val intoStart = (viewportStart + zone) - top
    val intoEnd = bottom - (viewportEnd - zone)
    val reach = when {
        intoStart > 0f && intoEnd <= 0f -> -intoStart
        intoEnd > 0f && intoStart <= 0f -> intoEnd
        else -> return 0f
    }
    val ramp = speed * (0.2f + 0.8f * (abs(reach) / zone).coerceAtMost(1f))
    return if (reach < 0f) -ramp else ramp
}

@Composable
private fun rememberQueueDrag(
    listState: LazyListState,
    tracks: QueueTracks,
    onMove: (Int, Int) -> Unit,
    onActiveChange: (Boolean) -> Unit,
): QueueDrag {
    val drag = remember(listState) { QueueDrag(listState) }
    drag.tracks = tracks
    drag.onMove = onMove
    drag.onActiveChange = onActiveChange
    with(LocalDensity.current) {
        drag.edgeZone = QUEUE_EDGE_SCROLL_ZONE.toPx()
        drag.edgeSpeed = QUEUE_EDGE_SCROLL_SPEED.toPx()
    }
    // Held near either edge, the row scrolls the list under itself. The finger
    // stays put, so each frame's scroll is settled as if the list had moved and
    // the row hadn't — it swaps through the rows going past on the same terms.
    val direction = drag.autoScrollDir
    LaunchedEffect(drag, direction) {
        if (direction == 0) return@LaunchedEffect
        listState.scroll {
            var previous = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                // A dropped frame paid back in full lands as a lurch, so it isn't.
                val seconds = ((now - previous) / 1_000_000_000f).coerceAtMost(1f / 30f)
                previous = now
                // Nowhere left to scroll: let go of the list rather than spin.
                if (scrollBy(drag.autoScrollSpeed * seconds) == 0f) break
                drag.scrolled()
            }
        }
    }
    return drag
}

/**
 * The one drag [InlineQueue] can have at a time.
 *
 * Everything turns on [heldCenter], where the finger holds the row's centre in
 * the list's viewport. Only the finger moves it. Swaps are read off the live
 * layout against it, each sent to the player the moment the row crosses a
 * neighbour, and the row itself is drawn at [heldTop] by an overlay — never
 * from its own slot, which jumps a whole row the frame a swap lands.
 */
private class QueueDrag(private val listState: LazyListState) {
    var tracks = QueueTracks.EMPTY
    var onMove: (Int, Int) -> Unit = { _, _ -> }

    /** See [PartySync.beginQueueDrag]: a jam hears about the reorder once, on drop. */
    var onActiveChange: (Boolean) -> Unit = {}

    /** [QUEUE_EDGE_SCROLL_ZONE] and [QUEUE_EDGE_SCROLL_SPEED], in pixels. */
    var edgeZone = 0f
    var edgeSpeed = 0f

    /** The row being moved; null at rest. */
    var held by mutableStateOf<QueueTrack?>(null)
        private set

    /** Where the held row is drawn: its top in the viewport, kept on screen. */
    var heldTop by mutableFloatStateOf(0f)
        private set

    /** -1, 0 or 1 — state, because it starts and stops the scroll loop. */
    var autoScrollDir by mutableIntStateOf(0)
        private set

    /** Signed, px a second. Not state: only the loop reads it, once a frame. */
    var autoScrollSpeed = 0f
        private set

    private var section = QueueSection.USER
    private var heldCenter = 0f
    private var heldSize = 0

    /** Where the last swap sent will put the row, until the list shows it there. */
    private var awaiting: Int? = null

    fun start(key: String, section: QueueSection) {
        val track = tracks[section].firstOrNull { it.key == key } ?: return
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        this.section = section
        heldSize = item.size
        heldCenter = item.offset + item.size / 2f
        heldTop = item.offset.toFloat()
        awaiting = null
        held = track
        onActiveChange(true)
    }

    fun drag(deltaY: Float) = settle(deltaY)

    fun scrolled() = settle(0f)

    fun end(key: String) {
        if (held?.key != key) return
        held = null
        awaiting = null
        setAutoScroll(0f)
        onActiveChange(false)
    }

    private fun settle(deltaY: Float) {
        val key = held?.key ?: return
        val rows = tracks[section]
        val info = listState.layoutInfo
        val items = info.visibleItemsInfo
        val half = heldSize / 2f

        // Stopped at the ends of its own section where those are on screen,
        // rather than running on past them into rows it can't trade with.
        var center = heldCenter + deltaY
        rows.firstOrNull()?.let { first ->
            items.firstOrNull { it.key == first.key }?.let { center = center.coerceAtLeast(it.offset + half) }
        }
        rows.lastOrNull()?.let { last ->
            items.firstOrNull { it.key == last.key }?.let { center = center.coerceAtMost(it.offset + it.size - half) }
        }
        heldCenter = center
        val top = center - half
        val minTop = info.viewportStartOffset.toFloat()
        heldTop = top.coerceIn(minTop, (info.viewportEndOffset - heldSize).toFloat().coerceAtLeast(minTop))

        // Its slot is out of view: nothing to swap against until the list brings it back.
        val dragged = items.firstOrNull { it.key == key } ?: return setAutoScroll(0f)
        aimAutoScroll(top, key, rows)

        // A swap already sent but not laid out yet — deciding another off the
        // old layout would send the same one twice.
        awaiting?.let {
            if (dragged.index != it) return
            awaiting = null
        }
        val target = items
            .filter { item -> item.key != key && rows.any { it.key == item.key } }
            .minByOrNull { abs(it.offset + it.size / 2f - center) }
            ?: return
        // Half a row of overlap per swap, so a row isn't traded back and forth
        // over a single pixel of travel.
        if (abs(center - (target.offset + target.size / 2f)) > target.size / 2f) return
        // LazyColumn keeps its place by the key of its first visible row; move
        // that row while there's list above it and the whole list follows it
        // along by a row. Waiting a few frames for the scroll to bring in the
        // next row up avoids it.
        if (target.index == listState.firstVisibleItemIndex && listState.canScrollBackward) return
        val from = rows.firstOrNull { it.key == key }?.timelineIndex ?: return
        val to = rows.firstOrNull { it.key == target.key }?.timelineIndex ?: return
        onMove(from, to)
        awaiting = target.index
    }

    /** Only while there's a row that way to trade with, and list left to scroll. */
    private fun aimAutoScroll(top: Float, key: String, rows: List<QueueTrack>) {
        val info = listState.layoutInfo
        val speed = edgeScrollSpeed(
            top = top,
            bottom = top + heldSize,
            viewportStart = info.viewportStartOffset,
            viewportEnd = info.viewportEndOffset,
            zone = edgeZone,
            speed = edgeSpeed,
        )
        val blocked = when {
            speed < 0f -> rows.firstOrNull()?.key == key || !listState.canScrollBackward
            speed > 0f -> rows.lastOrNull()?.key == key || !listState.canScrollForward
            else -> true
        }
        setAutoScroll(if (blocked) 0f else speed)
    }

    private fun setAutoScroll(speed: Float) {
        autoScrollSpeed = speed
        val direction = when {
            speed > 0f -> 1
            speed < 0f -> -1
            else -> 0
        }
        if (autoScrollDir != direction) autoScrollDir = direction
    }
}

/** The held menu keeps the row's metadata, but not its queue editing controls. */
@Composable
fun QueueSongPreview(song: Song) {
    InlineQueueRow(song = song, isCurrent = false, onClick = {}, onRemove = {}, preview = true)
}

@Composable
private fun InlineQueueRow(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    selectionChecked: Boolean? = null,
    preview: Boolean = false,
    modifier: Modifier = Modifier,
    /** @see InlineQueue */
    locked: Boolean = false,
    /** Shows the handle; drags on it go to the callbacks below. */
    draggable: Boolean = false,
    /** The copy drawn under the finger while the row is being moved. */
    lifted: Boolean = false,
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
) {
    val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val removeControl: @Composable () -> Unit = {
        if (!locked) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(Res.string.remove_from_queue),
                    tint = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
    val handle: @Composable () -> Unit = {
        if (draggable) {
            Icon(
                Icons.Rounded.DragHandle,
                contentDescription = stringResource(Res.string.drag_to_reorder),
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier
                    .size(20.dp)
                    .offset(x = if (ltr) 4.dp else (-4).dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { onDragStart() },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                            onDrag = { change, amount ->
                                change.consume()
                                onDrag(amount.y)
                            },
                        )
                    },
            )
            Spacer(Modifier.width(4.dp))
        } else if (ltr) {
            Spacer(Modifier.width(24.dp))
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (lifted || selectionChecked == true) Color.White.copy(alpha = 0.06f) else Color.Transparent)
            // Disabled rather than ignored: a tap that ripples and then does
            // nothing reads as the app having missed it.
            .then(
                if (selectionChecked != null) Modifier.clickable(enabled = !locked, onClick = onClick)
                else Modifier.longPressMenuClickable(enabled = !locked, onClick = onClick, onLongClick = onLongPress),
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionChecked != null) {
            Checkbox(
                checked = selectionChecked,
                onCheckedChange = { onClick() },
                enabled = !locked,
                modifier = Modifier.size(32.dp),
            )
        } else if (!preview) {
            if (ltr) removeControl() else handle()
        }
        AsyncImage(
            model = rememberRemoteArtworkUrl(song),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .trackArtSize(44.dp, song.isVideo)
                .clip(RoundedCornerShape(6.dp))
                .thumbnailBorder(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        )
        Spacer(Modifier.width(12.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            // Shrink to the longer line, but reserve the indicator before measuring text.
            Column(Modifier.weight(1f, fill = !isCurrent)) {
                ExplicitSongTitle(
                    song = song,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.92f),
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isCurrent) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Rounded.GraphicEq,
                    contentDescription = stringResource(Res.string.now_playing),
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
            }
        }
        if (selectionChecked != null) {
            Spacer(Modifier.width(24.dp))
        } else if (!preview) {
            if (ltr) handle() else removeControl()
        }
    }
}
