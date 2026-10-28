/*
 * The progressive blur here is Haze's (chrisbanes/haze 1.3.1, Apache-2.0):
 * its separable variable-radius Gaussian (BLUR_SKSL), its two-pass chain and
 * its gradient mask, reproduced exactly so the top fade looks as it did. What
 * differs is only where the blurred picture comes from — see [PageSample].
 */
package com.music.bitchord.ui.components

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import com.music.bitchord.ui.components.backdrop.internal.recordLayer
import kotlin.math.PI
import kotlin.math.roundToInt
import android.graphics.RenderEffect as AndroidRenderEffect

/**
 * Blurs [sample] under this element, by [blurRadius] along its top edge and
 * easing out along [easing] to nothing at its foot, over [background] where the
 * page has nothing to give — what Haze's progressive `hazeEffect` drew for the
 * top fade, read from the page's shared sample rather than from a recording of
 * the page made for it alone. Haze also built a fresh layer and recorded it on
 * every frame it drew; this keeps one, and records it only when it moves.
 *
 * API 33 and up: the variable blur is a runtime shader.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun Modifier.sampledProgressiveBlur(
    sample: PageSample,
    blurRadius: Dp,
    easing: Easing,
    background: () -> Color,
): Modifier = this then SampledProgressiveBlurElement(sample, blurRadius, easing, background)

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private data class SampledProgressiveBlurElement(
    val sample: PageSample,
    val blurRadius: Dp,
    val easing: Easing,
    val background: () -> Color,
) : ModifierNodeElement<SampledProgressiveBlurNode>() {
    override fun create() = SampledProgressiveBlurNode(sample, blurRadius, easing, background)

    override fun update(node: SampledProgressiveBlurNode) = node.update(sample, blurRadius, easing, background)

    override fun InspectorInfo.inspectableProperties() {
        name = "sampledProgressiveBlur"
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class SampledProgressiveBlurNode(
    private var sample: PageSample,
    private var blurRadius: Dp,
    private var easing: Easing,
    private var background: () -> Color,
) : DrawModifierNode, GlobalPositionAwareModifierNode, Modifier.Node() {

    private var layer: GraphicsLayer? = null

    // Read as this draws, so the layer is recorded again wherever it moves to.
    private var coordinates: LayoutCoordinates? by mutableStateOf(null, neverEqualPolicy())

    private var effect: RenderEffect? = null
    private var effectSize = Size.Unspecified
    private var effectRadius = Float.NaN

    fun update(sample: PageSample, blurRadius: Dp, easing: Easing, background: () -> Color) {
        if (blurRadius != this.blurRadius || easing != this.easing) effect = null
        this.sample = sample
        this.blurRadius = blurRadius
        this.easing = easing
        this.background = background
        invalidateDraw()
    }

    override fun ContentDrawScope.draw() {
        val coordinates = coordinates
        val layer = layer
        if (coordinates == null || layer == null) {
            drawContent()
            return
        }
        val scale = PAGE_SAMPLE_SCALE
        val contentSize = size * scale
        // Haze's blur radius: the dp radius at the input scale.
        val radiusPx = (blurRadius * scale).toPx()
        if (effect == null || effectSize != contentSize || effectRadius != radiusPx) {
            effect = progressiveBlur(radiusPx, contentSize, easing)
            effectSize = contentSize
            effectRadius = radiusPx
        }
        recordLayer(this@SampledProgressiveBlurNode, layer, IntSize(contentSize.width.roundToInt(), contentSize.height.roundToInt())) {
            drawRect(background())
            clipRect {
                scale(scale, pivot = Offset.Zero) {
                    with(sample) { drawBackdrop(this@draw, coordinates, null) }
                }
            }
        }
        layer.renderEffect = effect
        clipRect {
            scale(1f / scale, pivot = Offset.Zero) {
                drawLayer(layer)
            }
        }
        drawContent()
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (coordinates.isAttached) this.coordinates = coordinates
    }

    override fun onAttach() {
        layer = requireGraphicsContext().createGraphicsLayer()
    }

    override fun onDetach() {
        layer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
        layer = null
        coordinates = null
    }
}

/**
 * Haze's progressive blur: [BlurShader] horizontally, then vertically, its
 * radius scaled at each pixel by the alpha of a 20-stop gradient running from
 * full strength at the top of [size] to none at its foot along [easing].
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun progressiveBlur(radiusPx: Float, size: Size, easing: Easing): RenderEffect {
    val mask = (
        Brush.linearGradient(
            colors = List(MASK_STOPS) { i ->
                val x = i * 1f / (MASK_STOPS - 1)
                Color.Magenta.copy(alpha = 1f - easing.transform(x))
            },
            start = Offset.Zero,
            end = Offset(0f, Float.POSITIVE_INFINITY),
        ) as ShaderBrush
        ).createShader(size)

    fun pass(vertical: Boolean): AndroidRenderEffect {
        val shader = RuntimeShader(BlurShader).apply {
            setFloatUniform("blurRadius", radiusPx)
            setIntUniform("direction", if (vertical) 1 else 0)
            setFloatUniform("crop", 0f, 0f, size.width, size.height)
            setInputShader("mask", mask)
        }
        return AndroidRenderEffect.createRuntimeShaderEffect(shader, "content")
    }

    return AndroidRenderEffect.createChainEffect(pass(vertical = true), pass(vertical = false))
        .asComposeRenderEffect()
}

private const val MASK_STOPS = 20

/** Haze's BLUR_SKSL, unchanged. Adapted there from https://www.shadertoy.com/view/Mtl3Rj */
private val BlurShader = """
  uniform shader content;
  // 0 for horizontal pass, 1 for vertical
  uniform int direction;
  uniform half blurRadius;
  uniform half4 crop;
  uniform shader mask;

  const int maxRadius = 150;
  const half2 directionHorizontal = half2(1.0, 0.0);
  const half2 directionVertical = half2(0.0, 1.0);

  half gaussian(half x, half sigma) {
    return exp(-(x * x) / (2.0 * sigma * sigma)) / (2.0 * ${PI.toFloat()} * sigma * sigma);
  }

  half4 blur(vec2 coord, half radius) {
    half2 directionVec = direction == 0 ? directionHorizontal : directionVertical;
    half sigma = max(radius / 2, 1.0);

    half weight = gaussian(0.0, sigma);
    half4 result = weight * content.eval(coord);
    half weightSum = weight;

    // We need to use a constant max size Skia to know the size of the program. We use a large
    // number, along with a break
    for (int i = 1; i <= maxRadius; i++) {
      half halfI = half(i);
      if (halfI > radius) { break; }

      half weight = gaussian(halfI, sigma);
      half2 offset = halfI * directionVec;

      half2 newCoord = coord - offset;
      if (newCoord.x >= crop[0] && newCoord.y >= crop[1]) {
        result += weight * content.eval(newCoord);
        weightSum += weight;
      }

      newCoord = coord + offset;
      if (newCoord.x < crop[2] && newCoord.y < crop[3]) {
        result += weight * content.eval(newCoord);
        weightSum += weight;
      }
    }

    result /= weightSum;
    return result;
  }

  half4 main(vec2 coord) {
    half intensity = mask.eval(coord).a;
    return blur(coord, mix(0.0, blurRadius, intensity));
  }
"""
