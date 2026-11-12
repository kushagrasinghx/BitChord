package com.music.bitchord.ui.components.backdrop.backdrops

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import com.music.bitchord.ui.components.backdrop.Backdrop
import com.music.bitchord.ui.components.backdrop.internal.InverseLayerScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Vendored addition: a backdrop that is a still of its source rather than the
 * source live.
 *
 * [LayerBackdrop] keeps a recording that is the source by reference — every
 * animated thing inside it, a progress bar's tick or a drifting mesh, makes
 * whatever samples it render the whole source again and re-run its effects on
 * the next frame. For a panel that is up for a few seconds over content the
 * person isn't looking at, that is a full-screen redraw and a blur per frame
 * bought for nothing. This takes one picture when [capture] is called — small,
 * since only a blur will ever see it — and the effects run on that, again only
 * when the panel itself moves.
 *
 * Nothing is recorded until [capture]: the source draws exactly as it would
 * without this. [release] lets go of the picture once nothing samples it.
 */
@Stable
class SnapshotBackdrop internal constructor() : Backdrop {

    internal var image: ImageBitmap? by mutableStateOf(null)
    internal var sourceCoordinates: LayoutCoordinates? = null
    /** Read while drawing, so bumping it is what asks for a frame to record. */
    internal var requests by mutableIntStateOf(0)
    internal var handled = 0
    /** Bumped by [release], so a still that lands after it is dropped. */
    internal var generation = 0

    private var inverseLayerScope: InverseLayerScope? = null

    /** Takes a still of the source on its next frame. */
    fun capture() {
        requests++
    }

    /** Whether a still is in — for a panel that would rather not open on none. */
    val hasStill: Boolean get() = image != null

    /** Lets the still go; until the next [capture] this draws nothing. */
    fun release() {
        image = null
        generation++
    }

    override val isCoordinatesDependent: Boolean = true

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        val image = image ?: return
        val coordinates = coordinates ?: return
        val source = sourceCoordinates?.takeIf { it.isAttached } ?: return
        withTransform({
            if (layerBlock != null) {
                with(obtainInverseLayerScope()) { inverseTransform(density, layerBlock) }
            }
            val (offset, panelScale) =
                try {
                    // Where the panel's origin, and a point a step along from it,
                    // land in the source: their distance is the scale anything
                    // above the panel has it at — a menu growing in on a spring.
                    val origin = source.localPositionOf(coordinates)
                    val step = source.localPositionOf(coordinates, Offset(SCALE_PROBE_PX, 0f))
                    origin to ((step.x - origin.x) / SCALE_PROBE_PX).takeIf { it > 0f }
                } catch (_: Exception) {
                    (coordinates.positionInWindow() - source.positionInWindow()) to null
                }
            // Undone, so the still stays where it is on screen while the panel
            // grows over it, rather than zooming in and out with the panel.
            if (panelScale != null && panelScale != 1f) scale(1f / panelScale, pivot = Offset.Zero)
            translate(-offset.x, -offset.y)
        }) {
            scale(1f / SNAPSHOT_SCALE, pivot = Offset.Zero) { drawImage(image) }
        }
    }

    private fun obtainInverseLayerScope(): InverseLayerScope {
        return inverseLayerScope?.apply { reset() }
            ?: InverseLayerScope().also { inverseLayerScope = it }
    }
}

@Composable
fun rememberSnapshotBackdrop(): SnapshotBackdrop = remember { SnapshotBackdrop() }

/**
 * Makes this what [backdrop] takes its stills of. Draws as if it weren't here,
 * except on the one frame after [SnapshotBackdrop.capture], when it also
 * records itself small and turns that into the picture.
 */
@Composable
fun Modifier.snapshotBackdropSource(backdrop: SnapshotBackdrop): Modifier {
    val graphicsContext = LocalGraphicsContext.current
    val scope = rememberCoroutineScope()
    return this
        .onGloballyPositioned { backdrop.sourceCoordinates = it }
        .drawWithContent {
            drawContent()
            val request = backdrop.requests
            if (request == backdrop.handled) return@drawWithContent
            backdrop.handled = request
            if (size.width < 1f || size.height < 1f) return@drawWithContent
            val layer = graphicsContext.createGraphicsLayer()
            val width = (size.width * SNAPSHOT_SCALE).roundToInt().coerceAtLeast(1)
            val height = (size.height * SNAPSHOT_SCALE).roundToInt().coerceAtLeast(1)
            layer.record(size = IntSize(width, height)) {
                scale(SNAPSHOT_SCALE, pivot = Offset.Zero) { this@drawWithContent.drawContent() }
            }
            val generation = backdrop.generation
            scope.launch {
                try {
                    // Only if nothing asked again, or let go, in the meantime.
                    val still = layer.toImageBitmap()
                    if (backdrop.handled == request && backdrop.generation == generation) backdrop.image = still
                } finally {
                    graphicsContext.releaseGraphicsLayer(layer)
                }
            }
        }
}

/**
 * The still's resolution against its source's. A quarter: the panels sampling
 * it draw their backdrop at a quarter before blurring it anyway, so more would
 * be detail thrown away.
 */
private const val SNAPSHOT_SCALE = 0.25f

/** How far along the panel the scale is read over, in its own pixels. */
private const val SCALE_PROBE_PX = 1000f
