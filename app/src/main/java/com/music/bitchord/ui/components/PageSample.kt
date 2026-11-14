package com.music.bitchord.ui.components

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import com.music.bitchord.ui.components.backdrop.Backdrop
import com.music.bitchord.ui.components.backdrop.backdrops.LayerBackdrop
import com.music.bitchord.ui.components.backdrop.internal.recordLayer
import kotlin.math.ceil

/**
 * The page, drawn once a frame at [PAGE_SAMPLE_SCALE] into a texture of its
 * own, for everything that blurs what is behind it to read from.
 *
 * Each blurred surface used to draw the page again for itself — the top
 * fade, the top bar's glass, the tab bar's glass. The page is a RenderNode
 * the screen already draws, and the renderer replays every one of its draw
 * operations each time it is drawn somewhere else: on a feed, three more
 * replays of hundreds of rows, cards and glyphs on every frame, measured as
 * the render thread's command issue doubling with blur on. Here the page is
 * replayed once, into a small texture, and every reader draws its piece of
 * that texture — a single textured quad — instead of the page.
 *
 * Read through [LocalPageSample]; filled by [pageSampleSource] on the element
 * that records [page]. [live] says whether anything is filling it.
 */
@Stable
class PageSample internal constructor(internal val page: LayerBackdrop) : Backdrop {

    /** The page at [PAGE_SAMPLE_SCALE], or null while no source is attached. */
    internal var layer: GraphicsLayer? = null

    /** Whether a [pageSampleSource] is filling this, so a reader can fall back to the page. */
    var live by mutableStateOf(false)
        internal set

    /**
     * What each reader reads, in the page's coordinates. The sample is drawn
     * clipped to these and nothing else: the page's draws between the top band
     * and the bars — most of the screen — are culled before the renderer
     * builds a single command for them, which is where a replay of the page
     * costs. State, so the sample is recorded again when a reader moves.
     */
    internal val regions = mutableStateMapOf<Any, Rect>()

    /** [reader] reads [coordinates]'s bounds, widened by [margin] pixels for its blur and its motion. */
    internal fun read(reader: Any, coordinates: LayoutCoordinates, margin: Float) {
        val source = page.layerCoordinates?.takeIf { it.isAttached } ?: return
        val region = try {
            source.localBoundingBoxOf(coordinates, clipBounds = false)
        } catch (_: Exception) {
            return
        }.inflate(margin)
        if (regions[reader] != region) regions[reader] = region
    }

    /** [reader] reads nothing any more. */
    internal fun forget(reader: Any) {
        regions.remove(reader)
    }

    override val isCoordinatesDependent: Boolean = true

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        val surface = coordinates ?: return
        val source = page.layerCoordinates ?: return
        val layer = layer ?: return
        val offset = try {
            source.localPositionOf(surface, Offset.Zero)
        } catch (_: Exception) {
            surface.positionInWindow() - source.positionInWindow()
        }
        translate(-offset.x, -offset.y) {
            scale(1f / PAGE_SAMPLE_SCALE, pivot = Offset.Zero) {
                drawLayer(layer)
            }
        }
    }
}

/**
 * The page's sample resolution. The same third every reader of it works at
 * already — the glass ([liquidGlass]) and the top fade ([TopFadeBlur]) — so
 * a reader drawing it at its own scale gets one texel per pixel.
 */
internal const val PAGE_SAMPLE_SCALE = 0.33f

/** [regions] merged wherever they overlap, as few rectangles as cover them all. */
private fun bands(regions: List<Rect>): List<Rect> {
    val merged = ArrayList<Rect>()
    for (region in regions.sortedBy { it.top }) {
        val last = merged.lastOrNull()
        if (last != null && last.overlaps(region)) {
            merged[merged.lastIndex] = Rect(
                minOf(last.left, region.left), minOf(last.top, region.top),
                maxOf(last.right, region.right), maxOf(last.bottom, region.bottom),
            )
        } else {
            merged += region
        }
    }
    return merged
}

/**
 * Set by a frame recording that samples the whole page, unclipped, for the
 * clip to be priced against it in the same run. State, so the sample redraws.
 */
internal val fullPageSampleForDiagnostics = mutableStateOf(false)

/**
 * The margin a reader asks for around its bounds: its blur's reach, and a
 * frame's travel for something that moves — a bar folding — since a reader
 * that moved this frame reads the sample recorded for where it was placed.
 */
internal val SAMPLE_READ_MARGIN = 32.dp

/** The app's [PageSample], if the page is being sampled. */
val LocalPageSample = staticCompositionLocalOf<PageSample?> { null }

/** The sample a blurred surface should read, or null to read the page itself. */
internal val PageSample?.ifLive: PageSample? get() = this?.takeIf { it.live }

/**
 * Fills [sample] from its page. Goes on the element that records the page —
 * ahead of its `layerBackdrop` — and draws nothing of its own.
 */
internal fun Modifier.pageSampleSource(sample: PageSample): Modifier = this then PageSampleElement(sample)

private data class PageSampleElement(val sample: PageSample) : ModifierNodeElement<PageSampleNode>() {
    override fun create() = PageSampleNode(sample)

    override fun update(node: PageSampleNode) = node.update(sample)

    override fun InspectorInfo.inspectableProperties() {
        name = "pageSampleSource"
    }
}

private class PageSampleNode(private var sample: PageSample) : DrawModifierNode, Modifier.Node() {

    // What the sample was last recorded for. Recorded once per size and page
    // layer, not per frame: it holds the page's RenderNode by reference, so
    // the renderer draws whatever the page last recorded without this
    // recording again.
    private var recordedSize = IntSize.Zero
    private var recordedPage: GraphicsLayer? = null
    private var recordedRegions: List<Rect> = emptyList()

    fun update(sample: PageSample) {
        if (this.sample === sample) return
        release()
        this.sample = sample
        if (isAttached) adopt()
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val layer = sample.layer ?: return
        val pageLayer = sample.page.graphicsLayer
        val target = IntSize(
            ceil(size.width * PAGE_SAMPLE_SCALE).toInt().coerceAtLeast(1),
            ceil(size.height * PAGE_SAMPLE_SCALE).toInt().coerceAtLeast(1),
        )
        // Read here, so a reader moving records the sample again; with none
        // registered, or a recording asking for it, the whole page.
        val regions = if (fullPageSampleForDiagnostics.value) emptyList() else sample.regions.values.toList()
        if (target == recordedSize && recordedPage === pageLayer && regions == recordedRegions) return
        recordLayer(this@PageSampleNode, layer, target) {
            scale(PAGE_SAMPLE_SCALE, pivot = Offset.Zero) {
                if (regions.isEmpty()) {
                    drawLayer(pageLayer)
                } else {
                    // A rectangle each, never one path of all of them: a path
                    // clip is a mask every culled-in draw has to go through,
                    // and measured, it cost more than the culling saved. A
                    // rectangle is a scissor. Overlapping readers — the top
                    // fade and the top bar's glass — share one.
                    for (band in bands(regions)) {
                        clipRect(band.left, band.top, band.right, band.bottom) {
                            drawLayer(pageLayer)
                        }
                    }
                }
            }
        }
        recordedSize = target
        recordedPage = pageLayer
        recordedRegions = regions
    }

    override fun onAttach() = adopt()

    override fun onDetach() = release()

    private fun adopt() {
        // Offscreen, so the renderer draws the page into this layer's own
        // texture once and every reader composites that texture, rather than
        // each reader replaying what the layer holds.
        sample.layer = requireGraphicsContext().createGraphicsLayer().apply {
            compositingStrategy = CompositingStrategy.Offscreen
        }
        recordedSize = IntSize.Zero
        recordedPage = null
        recordedRegions = emptyList()
        sample.live = true
    }

    private fun release() {
        sample.live = false
        sample.layer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
        sample.layer = null
    }
}
