package com.music.bitchord.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Wraps a vertical lazy list without changing its padding or scroll handlers. Input lives on
 * the parent, not an intercepting sibling: track taps and non-thumb gestures reach the list.
 * [leadingItemCount] separates artwork/search headers from the regular rows used for estimates.
 * [trackPadding] also clears chrome that overlays, rather than pads, the list.
 */
@Composable
fun QuickScrollBar(
    listState: LazyListState,
    enabled: Boolean,
    leadingItemCount: Int,
    trackPadding: PaddingValues,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val top = with(density) { trackPadding.calculateTopPadding().toPx() }
    val bottom = with(density) { trackPadding.calculateBottomPadding().toPx() }
    val minHeight = with(density) { 48.dp.toPx() }
    val maxHeight = with(density) { 96.dp.toPx() }
    val headerSizes = remember(listState, density) { mutableStateMapOf<Int, Int>() }
    // Only an initial estimate, replaced as soon as regular rows are measured. A tall artwork
    // header must not be extrapolated to every unmeasured song in the playlist.
    var rowSize by remember(listState, density) { mutableStateOf(with(density) { 64.dp.toPx() }) }
    val drag = remember(listState) { mutableStateOf<QuickScrollDrag?>(null) }
    var edgePressed by remember(listState) { mutableStateOf(false) }
    val visible = remember(listState) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(listState, leadingItemCount, density) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }.collect { items ->
            var rowPixels = 0f
            var rowCount = 0
            for (item in items) {
                if (item.index < leadingItemCount) {
                    headerSizes[item.index] = item.size
                } else if (item.size > 0) {
                    rowPixels += item.size
                    rowCount++
                }
            }
            if (rowCount > 0) rowSize = rowPixels / rowCount
        }
    }
    val geometry = remember(listState, leadingItemCount, density, top, bottom) {
        derivedStateOf {
            quickScrollGeometry(listState, leadingItemCount, headerSizes, rowSize, top, bottom, minHeight, maxHeight)
        }
    }
    LaunchedEffect(listState) {
        var first = true
        snapshotFlow {
            QuickScrollActivity(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
                listState.isScrollInProgress,
                edgePressed || drag.value != null,
            )
        }.collectLatest { activity ->
            val active = activity.scrolling || activity.touching
            val initialRest = first && !active
            first = false
            if (!initialRest) {
                visible.value = true
                if (!active) {
                    delay(1200)
                    visible.value = false
                }
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize().pointerInput(listState, geometry, rtl) {
            var scrollJob: Job? = null
            try {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val initial = geometry.value ?: return@awaitEachGesture
                    val inStrip = if (rtl) down.position.x <= 32.dp.toPx() else down.position.x >= size.width - 32.dp.toPx()
                    if (!inStrip || down.position.y < initial.trackTop ||
                        down.position.y > initial.trackTop + initial.trackHeight
                    ) return@awaitEachGesture
                    edgePressed = true
                    val onThumb = down.position.y >= initial.thumbTop &&
                        down.position.y <= initial.thumbTop + initial.thumbHeight
                    val grabOffset = down.position.y - initial.thumbTop
                    var dragging = false
                    try {
                        while (true) {
                            val change = awaitPointerEvent(PointerEventPass.Initial).changes
                                .firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                if (dragging) change.consume()
                                break
                            }
                            // No jump-to-position on the track. Only a vertical drag that started
                            // on the thumb may win; horizontal swipes and system back stay free.
                            if (!onThumb) continue
                            if (!dragging) {
                                if (change.isConsumed) break
                                val dx = abs(change.position.x - down.position.x)
                                val dy = abs(change.position.y - down.position.y)
                                if (dx > viewConfiguration.touchSlop && dx >= dy) break
                                if (dy <= viewConfiguration.touchSlop) continue
                                dragging = true
                            }
                            change.consume()
                            // Freeze the mapping during a grab: continuations may append rows,
                            // but must not move the thumb out from underneath the finger.
                            val thumbTop = (change.position.y - grabOffset).coerceIn(
                                initial.trackTop,
                                initial.trackTop + initial.trackHeight - initial.thumbHeight,
                            )
                            drag.value = QuickScrollDrag(initial, thumbTop)
                            val fraction = (thumbTop - initial.trackTop) / (initial.trackHeight - initial.thumbHeight)
                            val target = initial.target(fraction)
                            scrollJob?.cancel()
                            scrollJob = scope.launch {
                                val count = listState.layoutInfo.totalItemsCount
                                if (count > 0) {
                                    listState.scrollToItem(target.index.coerceIn(0, count - 1), target.offset)
                                }
                            }
                        }
                    } finally {
                        edgePressed = false
                        drag.value = null
                    }
                }
            } finally {
                scrollJob?.cancel()
            }
        },
    ) {
        content()
        QuickScrollThumb(geometry, drag, visible, rtl, Modifier.align(Alignment.TopEnd))
    }
}

/** Kept in its own restart scope so scroll/drag updates never recompose the list content. */
@Composable
private fun QuickScrollThumb(
    geometry: State<QuickScrollGeometry?>,
    drag: State<QuickScrollDrag?>,
    visible: State<Boolean>,
    rtl: Boolean,
    modifier: Modifier,
) {
    val current = drag.value?.geometry ?: geometry.value ?: return
    val dragging = drag.value != null
    val opacity = animateFloatAsState(if (visible.value || dragging) 1f else 0f, tween(150), label = "quick_scroll_alpha")
    val width = animateFloatAsState(if (dragging) 8f else 4f, tween(150), label = "quick_scroll_width")
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dragging) 0.6f else 0.4f)
    val description = stringResource(R.string.quick_scroll_playlist)
    val semantics = if (visible.value || dragging) Modifier.semantics { contentDescription = description } else Modifier
    Box(
        modifier = modifier
            .width(32.dp)
            .height(with(LocalDensity.current) { current.thumbHeight.toDp() })
            .graphicsLayer {
                translationY = drag.value?.top ?: geometry.value?.thumbTop ?: 0f
                alpha = opacity.value
            }
            .then(semantics)
            .drawBehind {
                val capsuleWidth = width.value.dp.toPx()
                val x = if (rtl) 3.dp.toPx() else size.width - 3.dp.toPx() - capsuleWidth
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x, 0f),
                    size = Size(capsuleWidth, size.height),
                    cornerRadius = CornerRadius(capsuleWidth / 2f),
                )
            },
    )
}

private data class QuickScrollActivity(val index: Int, val offset: Int, val scrolling: Boolean, val touching: Boolean)
private data class QuickScrollDrag(val geometry: QuickScrollGeometry, val top: Float)
private data class QuickScrollTarget(val index: Int, val offset: Int)

private data class QuickScrollGeometry(
    val trackTop: Float,
    val trackHeight: Float,
    val thumbTop: Float,
    val thumbHeight: Float,
    val totalItems: Int,
    val headers: List<Float>,
    val rowStride: Float,
    val scrollRange: Float,
) {
    fun target(fraction: Float): QuickScrollTarget {
        if (fraction <= 0f) return QuickScrollTarget(0, 0)
        if (fraction >= 1f) return QuickScrollTarget(totalItems - 1, 0)
        var pixels = fraction * scrollRange
        for (index in headers.indices) {
            if (pixels < headers[index]) return QuickScrollTarget(index, pixels.roundToInt())
            pixels -= headers[index]
        }
        val row = (pixels / rowStride).toInt()
        return QuickScrollTarget(
            (headers.size + row).coerceAtMost(totalItems - 1),
            (pixels - row * rowStride).roundToInt(),
        )
    }
}

private fun quickScrollGeometry(
    state: LazyListState,
    leadingItemCount: Int,
    headerSizes: Map<Int, Int>,
    rowSize: Float,
    top: Float,
    bottom: Float,
    minHeight: Float,
    maxHeight: Float,
): QuickScrollGeometry? {
    val info = state.layoutInfo
    val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
    val trackTop = top.coerceAtLeast(info.beforeContentPadding.toFloat())
    val trackHeight = viewport - trackTop - bottom.coerceAtLeast(info.afterContentPadding.toFloat())
    val bodyCount = info.totalItemsCount - leadingItemCount
    val stride = (rowSize + info.mainAxisItemSpacing).coerceAtLeast(1f)
    // Three screens of regular rows: a tall artwork header alone never qualifies a short list.
    if (trackHeight <= minHeight || bodyCount * stride < trackHeight * 3f ||
        (!state.canScrollBackward && !state.canScrollForward)
    ) return null
    val headers = List(leadingItemCount) { (headerSizes[it]?.toFloat() ?: rowSize) + info.mainAxisItemSpacing }
    var headerHeight = 0f
    for (height in headers) headerHeight += height
    val contentHeight = headerHeight + bodyCount * stride - info.mainAxisItemSpacing
    val listViewport = viewport - info.beforeContentPadding - info.afterContentPadding
    val range = (contentHeight - listViewport).coerceAtLeast(1f)
    var beforeFirst = 0f
    for (index in headers.indices) {
        if (index < state.firstVisibleItemIndex) beforeFirst += headers[index]
    }
    beforeFirst += (state.firstVisibleItemIndex - leadingItemCount).coerceAtLeast(0) * stride
    val progress = when {
        !state.canScrollBackward -> 0f
        !state.canScrollForward -> 1f
        else -> ((beforeFirst + state.firstVisibleItemScrollOffset) / range).coerceIn(0f, 1f)
    }
    val thumbHeight = (trackHeight * listViewport / contentHeight).coerceIn(minHeight, maxHeight.coerceAtMost(trackHeight))
    return QuickScrollGeometry(
        trackTop, trackHeight, trackTop + progress * (trackHeight - thumbHeight), thumbHeight,
        info.totalItemsCount, headers, stride, range,
    )
}
