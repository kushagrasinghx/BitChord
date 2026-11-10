/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player.bitchord

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.composed
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

internal data class BitChordQueueSong(
    val id: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
)

private fun Modifier.thumbnailBorder(shape: Shape): Modifier = composed {
    this.border(
        width = 1.dp,
        color = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.15f),
        shape = shape,
    )
}

@Composable
internal fun InlineQueue(
    queue: List<BitChordQueueSong>,
    currentIndex: Int,
    onJumpTo: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val keepScroll = remember(listState) { keepScrollInList(listState) }

    val keys = remember(queue) { queue.stableQueueKeys() }

    val firstMovable = currentIndex + 1
    val drag = rememberQueueDragState(
        listState = listState,
        lazyRange = firstMovable until queue.size,
        lazyOffset = 0,
        onMove = onMove,
    )

    LaunchedEffect(currentIndex) {
        val holding = drag.draggedKey != null
        if (!holding && currentIndex in queue.indices) {
            listState.scrollToItem(currentIndex)
        }
    }

    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Queue",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Clear",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .clickable(onClick = onClear)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .bleedHorizontally(PLAYER_GUTTER)

                .nestedScroll(keepScroll)
                .fadingEdges(),
            contentPadding = PaddingValues(horizontal = PLAYER_GUTTER),
        ) {
            itemsIndexed(
                items = queue,
                key = { index, _ -> keys[index] },
            ) { index, song ->
                val key = keys[index]
                val dragging = drag.draggedKey == key
                InlineQueueRow(
                    song = song,
                    isCurrent = index == currentIndex,
                    onClick = { onJumpTo(index) },
                    onRemove = { onRemove(index) },

                    draggable = index >= firstMovable,
                    dragging = dragging,
                    onDragStart = { drag.onDragStart(key) },
                    onDrag = drag::onDrag,
                    onDragEnd = drag::onDragEnd,
                    modifier = Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) drag.renderOffset else 0f }

                        .then(if (drag.draggedKey != null) Modifier else Modifier.animateItem()),
                )
            }
        }
    }
}

private fun List<BitChordQueueSong>.stableQueueKeys(prefix: String = ""): List<String> {
    val seen = HashMap<String, Int>()
    return map { song ->
        val n = seen.getOrDefault(song.id, 0)
        seen[song.id] = n + 1
        if (n == 0) "$prefix${song.id}" else "$prefix${song.id}#$n"
    }
}

private val QUEUE_EDGE_SCROLL_ZONE = 40.dp
private val QUEUE_EDGE_SCROLL_SPEED = 340.dp

internal fun edgeScrollSpeed(
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
private fun rememberQueueDragState(
    listState: LazyListState,
    lazyRange: IntRange,
    lazyOffset: Int,
    onMove: (Int, Int) -> Unit,
): QueueDragState {
    val state = remember(listState) { QueueDragState(listState) }
    state.lazyRange = lazyRange
    state.lazyOffset = lazyOffset
    state.onMove = onMove
    with(LocalDensity.current) {
        state.edgeZone = QUEUE_EDGE_SCROLL_ZONE.toPx()
        state.edgeSpeed = QUEUE_EDGE_SCROLL_SPEED.toPx()
    }

    val direction = state.autoScrollDir
    LaunchedEffect(state, direction) {
        if (direction == 0) return@LaunchedEffect
        listState.scroll {
            var previous = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }

                val seconds = ((now - previous) / 1_000_000_000f).coerceAtMost(1f / 30f)
                previous = now
                val scrolled = scrollBy(state.autoScrollSpeed * seconds)

                if (scrolled == 0f) break
                state.onScrolled()
            }
        }
    }
    return state
}

private class QueueDragState(private val listState: LazyListState) {
    var lazyRange: IntRange = IntRange.EMPTY
    var lazyOffset: Int = 0
    var onMove: (Int, Int) -> Unit = { _, _ -> }

    var edgeZone: Float = 0f
    var edgeSpeed: Float = 0f

    var draggedKey by mutableStateOf<Any?>(null)
        private set

    var renderOffset by mutableFloatStateOf(0f)
        private set

    var autoScrollDir by mutableIntStateOf(0)
        private set

    var autoScrollSpeed: Float = 0f
        private set

    private var heldCenter: Float = Float.NaN

    private var awaiting: Int? = null

    fun onDragStart(key: Any) {
        draggedKey = key
        heldCenter = Float.NaN
        renderOffset = 0f
        awaiting = null
        setAutoScroll(0f)
    }

    fun onDrag(deltaY: Float) = settle(deltaY)

    fun onScrolled() = settle(0f)

    fun onDragEnd() {
        draggedKey = null
        heldCenter = Float.NaN
        renderOffset = 0f
        awaiting = null
        setAutoScroll(0f)
    }

    private fun settle(deltaY: Float) {
        val key = draggedKey ?: return
        val items = listState.layoutInfo.visibleItemsInfo

        val dragged = items.find { it.key == key } ?: run {
            setAutoScroll(0f)
            return
        }
        val half = dragged.size / 2f
        if (heldCenter.isNaN()) heldCenter = dragged.offset + half
        heldCenter += deltaY
        holdToSection(items, dragged)

        val top = heldCenter - half

        aimAutoScroll(top, dragged)
        renderOffset = insideViewport(top, dragged.size) - dragged.offset

        awaiting?.let {
            if (dragged.index != it) return
            awaiting = null
        }
        val target = swapTarget(items, dragged) ?: return
        onMove(dragged.index - lazyOffset, target.index - lazyOffset)
        awaiting = target.index
    }

    private fun swapTarget(
        items: List<LazyListItemInfo>,
        dragged: LazyListItemInfo,
    ): LazyListItemInfo? {
        val target = items
            .filter { it.index in lazyRange && it.index != dragged.index }
            .minByOrNull { abs((it.offset + it.size / 2f) - heldCenter) }
            ?: return null

        if (abs(heldCenter - (target.offset + target.size / 2f)) > target.size / 2f) return null

        if (target.index == listState.firstVisibleItemIndex && listState.canScrollBackward) {
            return null
        }
        return target
    }

    private fun aimAutoScroll(top: Float, dragged: LazyListItemInfo) {
        val info = listState.layoutInfo
        val speed = edgeScrollSpeed(
            top = top,
            bottom = top + dragged.size,
            viewportStart = info.viewportStartOffset,
            viewportEnd = info.viewportEndOffset,
            zone = edgeZone,
            speed = edgeSpeed,
        )
        val blocked = when {
            speed < 0f -> dragged.index <= lazyRange.first || !listState.canScrollBackward
            speed > 0f -> dragged.index >= lazyRange.last || !listState.canScrollForward
            else -> true
        }
        setAutoScroll(if (blocked) 0f else speed)
    }

    private fun holdToSection(items: List<LazyListItemInfo>, dragged: LazyListItemInfo) {
        val half = dragged.size / 2f
        items.firstOrNull { it.index == lazyRange.first }?.let {
            heldCenter = heldCenter.coerceAtLeast(it.offset + half)
        }
        items.firstOrNull { it.index == lazyRange.last }?.let {
            heldCenter = heldCenter.coerceAtMost(it.offset + it.size - half)
        }
    }

    private fun insideViewport(top: Float, size: Int): Float {
        val info = listState.layoutInfo
        val minTop = info.viewportStartOffset.toFloat()
        val maxTop = (info.viewportEndOffset - size).toFloat().coerceAtLeast(minTop)
        return top.coerceIn(minTop, maxTop)
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

@Composable
private fun InlineQueueRow(
    song: BitChordQueueSong,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    draggable: Boolean = false,
    dragging: Boolean = false,
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
) {
    val heldOnDispose by rememberUpdatedState(dragging)
    val endDrag by rememberUpdatedState(onDragEnd)
    DisposableEffect(Unit) {
        onDispose { if (heldOnDispose) endDrag() }
    }
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (dragging) Color.White.copy(alpha = 0.06f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (draggable) {
            Icon(
                Icons.Rounded.DragHandle,
                contentDescription = "Drag to reorder",
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier
                    .size(20.dp)

                    .offset(x = (-4).dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { onDragStart() },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.y)
                            },
                        )
                    },
            )
            Spacer(Modifier.width(4.dp))
        }
        val thumbRequest = remember(song.thumbnailUrl) {
            ImageRequest.Builder(context).data(song.thumbnailUrl).build()
        }
        AsyncImage(
            model = thumbRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(6.dp))
                .thumbnailBorder(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.92f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        if (isCurrent) {
            Icon(
                Icons.Rounded.GraphicEq,
                contentDescription = "Now playing",
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Remove from queue",
                tint = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
