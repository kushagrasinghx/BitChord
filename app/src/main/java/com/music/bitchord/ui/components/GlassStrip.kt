package com.music.bitchord.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import com.music.bitchord.ui.components.backdrop.Backdrop
import com.music.bitchord.ui.components.backdrop.BackdropEffectScope
import com.music.bitchord.ui.components.backdrop.BackdropEffectScopeImpl
import com.music.bitchord.ui.components.backdrop.internal.recordLayer

/**
 * One sample of the page, taken once for a group of glass surfaces that sit
 * together — the tab bar's pill, its Search circle and the accessory; the top
 * bar's back button and action pill — and saturated and blurred once for all
 * of them.
 *
 * Each [liquidGlass] surface used to take its own: draw the whole page again
 * into a layer of its own, saturate it, blur it, then bend it through its lens.
 * The page is a RenderNode the screen already draws, and a node drawn twice
 * in a frame is redrawn whole in the second place on every frame anything at
 * all is drawn — so every surface paid a full replay of the page and a blur on
 * every frame of every animation anywhere, six times over in the middle of a
 * fold. Under a strip, the replay and the blur happen once for the group, and
 * a surface draws its piece of the finished strip and does nothing to it but
 * its lens.
 *
 * Read by [liquidGlass] through [LocalGlassStrip]; filled by [glassStripSource]
 * on an element laid out behind the group and covering it.
 */
@Stable
class GlassStrip internal constructor() : Backdrop {

    /** The saturated, blurred sample, at [scale] and with [padding] past each edge. */
    internal var layer: GraphicsLayer? = null

    /**
     * Where the strip is, as last laid out. Never-equal, so a surface reading it
     * as it draws is redrawn whenever the strip moves under it.
     */
    internal var coordinates: LayoutCoordinates? by mutableStateOf(null, neverEqualPolicy())

    /** How far the sample runs past the strip's edges, in the layer's own pixels. */
    internal var padding = 0f

    /** The fraction of the strip's resolution the layer is recorded at. */
    internal var scale = 1f

    override val isCoordinatesDependent: Boolean = true

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        val surface = coordinates ?: return
        val strip = this@GlassStrip.coordinates ?: return
        val layer = layer ?: return
        val offset = try {
            strip.localPositionOf(surface, Offset.Zero)
        } catch (_: Exception) {
            surface.positionInWindow() - strip.positionInWindow()
        }
        // Layer pixels back to the strip's own coordinates, then to the surface's.
        translate(-offset.x, -offset.y) {
            scale(1f / scale, pivot = Offset.Zero) {
                translate(-padding, -padding) {
                    drawLayer(layer)
                }
            }
        }
    }
}

/** The [GlassStrip] the glass surfaces composed under it share, if any. */
val LocalGlassStrip = staticCompositionLocalOf<GlassStrip?> { null }

@Composable
fun rememberGlassStrip(): GlassStrip = remember { GlassStrip() }

/**
 * Fills [strip] with [page], under [effects], at [scale] — on whatever this is
 * applied to, which draws nothing of its own. Lay it out behind the surfaces
 * that share the strip and over all of them: a surface past its edge, by more
 * than the blur's own reach, samples nothing there.
 */
internal fun Modifier.glassStripSource(
    strip: GlassStrip,
    page: Backdrop,
    effects: BackdropEffectScope.() -> Unit,
    scale: Float,
): Modifier = this then GlassStripElement(strip, page, effects, scale.coerceIn(0.05f, 1f))

private data class GlassStripElement(
    val strip: GlassStrip,
    val page: Backdrop,
    val effects: BackdropEffectScope.() -> Unit,
    val scale: Float,
) : ModifierNodeElement<GlassStripNode>() {
    override fun create() = GlassStripNode(strip, page, effects, scale)

    override fun update(node: GlassStripNode) = node.update(strip, page, effects, scale)

    override fun InspectorInfo.inspectableProperties() {
        name = "glassStripSource"
    }
}

private class GlassStripNode(
    private var strip: GlassStrip,
    private var page: Backdrop,
    private var effects: BackdropEffectScope.() -> Unit,
    private var scale: Float,
) : DrawModifierNode, GlobalPositionAwareModifierNode, ObserverModifierNode, Modifier.Node() {

    private val effectScope = object : BackdropEffectScopeImpl() {
        override val shape: Shape get() = RectangleShape
    }
    private var effectsDirty = true

    // Read in draw, so the strip is recorded again wherever it moves to.
    private var coordinates: LayoutCoordinates? by mutableStateOf(null, neverEqualPolicy())

    fun update(strip: GlassStrip, page: Backdrop, effects: BackdropEffectScope.() -> Unit, scale: Float) {
        if (this.strip !== strip) {
            release()
            this.strip = strip
            if (isAttached) adopt()
        }
        this.page = page
        this.effects = effects
        this.scale = scale
        effectsDirty = true
        invalidateDraw()
    }

    override fun ContentDrawScope.draw() {
        val coordinates = coordinates
        val layer = strip.layer
        if (layer == null || coordinates == null) {
            drawContent()
            return
        }
        if (effectScope.update(this, scale) || effectsDirty) {
            observeReads {
                effectScope.apply(effects)
                layer.renderEffect = effectScope.renderEffect
            }
            effectsDirty = false
        }
        val padding = effectScope.padding
        val scale = scale
        recordLayer(
            this@GlassStripNode,
            layer,
            size = IntSize(
                ((size.width * scale).toInt() + padding.toInt() * 2).coerceAtLeast(1),
                ((size.height * scale).toInt() + padding.toInt() * 2).coerceAtLeast(1),
            ),
        ) {
            translate(padding, padding) {
                scale(scale, pivot = Offset.Zero) {
                    with(page) { drawBackdrop(effectScope, coordinates, null) }
                }
            }
        }
        strip.padding = padding
        strip.scale = scale
        drawContent()
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (!coordinates.isAttached) return
        this.coordinates = coordinates
        strip.coordinates = coordinates
    }

    override fun onObservedReadsChanged() {
        effectsDirty = true
        invalidateDraw()
    }

    override fun onAttach() = adopt()

    override fun onDetach() {
        release()
        effectScope.reset()
        effectsDirty = true
        coordinates = null
    }

    private fun adopt() {
        strip.layer = requireGraphicsContext().createGraphicsLayer()
        effectsDirty = true
    }

    private fun release() {
        strip.layer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
        strip.layer = null
        strip.coordinates = null
    }
}
