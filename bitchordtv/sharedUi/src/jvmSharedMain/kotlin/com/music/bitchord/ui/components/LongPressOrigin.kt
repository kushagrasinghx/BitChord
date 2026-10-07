package com.music.bitchord.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalGraphicsContext
import kotlinx.coroutines.launch

/**
 * One held row or card: where it sat on screen, and what it looked like.
 *
 * The picture is taken from the item's own drawing, so the menu it lifts
 * into shows exactly the thing that was held — a grid tile as a grid tile, a
 * search row as a search row — rather than a second, generic rendition of
 * the same song. It arrives a frame after the hold, which is why it is state.
 */
@Stable
class HeldItem internal constructor(
    /** The item's bounds in window coordinates. */
    val bounds: Rect,
) {
    var image: ImageBitmap? by mutableStateOf(null)
        internal set
}

/**
 * The last hold, for a menu that wants to lift the held item out of its list
 * rather than slide a sheet up from the bottom.
 *
 * Only a *hold* records anything. The ⋮ on the same row opens the same menu
 * through the same callback, and that path has to keep opening the sheet — so
 * the menu's host asks [consume] whether this particular opening came from a
 * finger held on an item, and gets null for everything else.
 *
 * A single slot rather than state threaded through every list: only one item
 * can be held at a time, and the host reads it in the same call stack the hold
 * fired in. The timestamp is the guard against a hold whose host never asked —
 * Downloads' multi-select, or the desktop build — being claimed later by an
 * unrelated tap on a ⋮.
 */
object LongPressOrigin {
    private var held: HeldItem? = null
    private var recordedAt = 0L

    internal fun record(item: HeldItem) {
        held = item
        recordedAt = System.nanoTime()
    }

    /** The item this opening was a hold on, or null when it wasn't a hold. */
    fun consume(): HeldItem? {
        val item = held?.takeIf { System.nanoTime() - recordedAt < FRESH_NANOS }
        held = null
        return item
    }

    private const val FRESH_NANOS = 500_000_000L
}

/**
 * [combinedClickable] that also tells [LongPressOrigin] what was held.
 *
 * Nothing is paid until a hold actually lands: the coordinates are kept by
 * reference, and the item's drawing is recorded once, on the frame after the
 * hold, into a layer that is turned into a bitmap and released. The recording
 * sits inside the click's own ripple, so the picture is of the item and not of
 * the press highlight over it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.longPressMenuClickable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    enabled: Boolean = true,
): Modifier {
    val capture = remember { HoldCapture() }
    val graphicsContext = LocalGraphicsContext.current
    val scope = rememberCoroutineScope()
    val longClick = onLongClick?.let { held ->
        {
            capture.coordinates?.takeIf { it.isAttached }?.let { coordinates ->
                val item = HeldItem(coordinates.boundsInWindow())
                LongPressOrigin.record(item)
                capture.pending = item
                capture.requests++
            }
            held()
        }
    }
    return this
        .onPlaced { capture.coordinates = it }
        .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = longClick)
        .drawWithContent {
            drawContent()
            // Reading the counter is what subscribes this draw to a hold;
            // nothing state-backed is written back from inside the draw.
            val request = capture.requests
            if (request == capture.handled) return@drawWithContent
            capture.handled = request
            val item = capture.pending ?: return@drawWithContent
            capture.pending = null
            val layer = graphicsContext.createGraphicsLayer()
            layer.record { this@drawWithContent.drawContent() }
            scope.launch {
                try {
                    item.image = layer.toImageBitmap()
                } finally {
                    graphicsContext.releaseGraphicsLayer(layer)
                }
            }
        }
}

private class HoldCapture {
    var coordinates: LayoutCoordinates? = null
    var pending: HeldItem? = null
    /** Read while drawing, so bumping it is what asks for a frame to record. */
    var requests by mutableIntStateOf(0)
    var handled = 0
}
