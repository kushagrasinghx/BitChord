package com.music.bitchord.ui.components

import android.graphics.BlurMaskFilter
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.dp

/**
 * The edge of Apple's Liquid Glass, drawn over a surface of [shape].
 *
 * Three parts, outermost first:
 *  - a dark outline just outside the edge, a crescent round each end over a
 *    faint line along the flat run between, where the rim draws the edge;
 *  - a bright rim along the top and, mirrored, along the bottom, at its
 *    thickest where the edge runs flat and thinning to nothing round the
 *    corners, where the edge turns vertical;
 *  - a soft glow under that rim: the same rim, deeper, blurred well out and
 *    much fainter.
 *
 * Rim and glow are lit from what is beneath them. Each multiplies the colour
 * there up — color dodge against a grey, which is a gain on every channel —
 * and only then adds a little white. Added white alone, the obvious way,
 * raises every channel by the same amount: it keeps the hue but bleaches it,
 * so a red sleeve got a pale pink edge. A gain keeps the channels' ratios, so
 * red brightens as red, which is the edge Apple's glass has. The white is the
 * floor for what a gain cannot lift — over black there is nothing to multiply.
 *
 * The rim is cut the way Apple's is built — the shape less a copy of itself
 * shifted by the rim's depth, once down for the top and once up for the
 * bottom. Where the edge is horizontal that leaves the full depth; where it
 * turns, it leaves the depth times how far the edge still faces up or down,
 * which is the taper, from the shape's own geometry, at any size. Unlike the
 * backdrop library's [com.music.bitchord.ui.components.backdrop.highlight.Highlight],
 * which lights the edge from one diagonal, it is symmetrical top to bottom.
 *
 * The outline is drawn outside the shape, as Apple's design files draw it, and
 * the rim and glow inside it, from the edge in. Drawn inside, the outline had
 * to take the shape's outermost band and the rim to start beneath it, so where
 * the outline faded out the rim showed the full depth of both, as a solid
 * white band. So a glass surface has to be clipped to its shape after this,
 * never before: a clip ahead of it cuts the outline away.
 *
 * Neither Android nor Skia has plus-darker, which for black at alpha `a`
 * subtracts — `max(0, d - a)` — so the outline is black drawn normally, which
 * scales instead, `d * (1 - a)`: the same on a dark backdrop, where both come
 * out near black, and a shade lighter on a pale one. Working Apple's formula
 * per pixel takes a layer and a shader pass over every glass surface on every
 * frame it changes, and the bars change on every frame of a scroll.
 *
 * [alpha] scales all three, read at draw time, so a surface can fade its rim
 * in and out without recomposing.
 */
internal fun Modifier.glassRim(shape: Shape, alpha: () -> Float = FullRim): Modifier =
    drawWithCache {
        val body = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
        val rim = rims(body, GLASS_RIM_DEPTH.toPx())
        val glow = rims(body, GLASS_GLOW_DEPTH.toPx())
        // The rim's own colour comes from a top-to-bottom gradient rather than
        // the paint, so it can run out before it wraps too far down the ends.
        val rimGain = blurredPaint(BlendMode.ColorDodge, GLASS_RIM_BLUR.toPx()).apply {
            shader = rimReach(size.height, gainGrey(GLASS_RIM_GAIN))
        }
        val rimLight = blurredPaint(BlendMode.Plus, GLASS_RIM_BLUR.toPx()).apply {
            shader = rimReach(size.height, Color.White)
        }
        val glowGain = blurredPaint(BlendMode.ColorDodge, GLASS_GLOW_BLUR.toPx())
        val glowLight = blurredPaint(BlendMode.Plus, GLASS_GLOW_BLUR.toPx())
        // Twice the width, centred on the edge and clipped to the outside half.
        val outline = Stroke(width = GLASS_OUTLINE_WIDTH.toPx() * 2f)
        val outlineBrush = Brush.horizontalGradient(
            *outlineStops(size).map { (at, opacity) -> at to Color.Black.copy(alpha = opacity) }.toTypedArray(),
            startX = 0f,
            endX = size.width,
        )
        onDrawWithContent {
            drawContent()
            val strength = alpha().coerceIn(0f, 1f)
            if (strength <= 0f) return@onDrawWithContent
            clipPath(body) {
                glowGain.color = gainGrey(GLASS_GLOW_GAIN).copy(alpha = strength)
                glowLight.color = Color.White.copy(alpha = GLASS_GLOW_ALPHA * strength)
                rimGain.alpha = strength
                rimLight.alpha = GLASS_RIM_ALPHA * strength
                drawIntoCanvas {
                    it.drawPath(glow, glowGain)
                    it.drawPath(glow, glowLight)
                    it.drawPath(rim, rimGain)
                    it.drawPath(rim, rimLight)
                }
            }
            clipPath(body, ClipOp.Difference) {
                drawPath(body, outlineBrush, alpha = strength, style = outline)
            }
        }
    }

private val FullRim = { 1f }

/**
 * The outline's opacity across the surface: [GLASS_OUTLINE_ALPHA] at each end,
 * falling away fast to [GLASS_OUTLINE_MIDDLE_ALPHA] [GLASS_OUTLINE_REACH] of
 * the way across an end cap and holding there between — a crescent tucked
 * round each end over a faint line along the rest.
 */
private fun outlineStops(size: Size): List<Pair<Float, Float>> {
    val reach = (size.minDimension / 2f / size.width).coerceIn(0f, 0.5f) * GLASS_OUTLINE_REACH
    val ends = squaredFade(reach).map { (at, fade) ->
        at to GLASS_OUTLINE_MIDDLE_ALPHA + (GLASS_OUTLINE_ALPHA - GLASS_OUTLINE_MIDDLE_ALPHA) * fade
    }
    return ends + ends.reversed().map { (at, opacity) -> 1f - at to opacity }
}

/**
 * A fade from whole to nothing over `0..[length]`, as stops: squared, so most
 * of what fades is spent at the start, and the tail runs out without a line.
 */
private fun squaredFade(length: Float): List<Pair<Float, Float>> =
    (0..FADE_STOPS).map { i ->
        val t = i / FADE_STOPS.toFloat()
        length * t to (1f - t) * (1f - t)
    }

private const val FADE_STOPS = 4

private fun blurredPaint(blendMode: BlendMode, blur: Float) = Paint().apply {
    this.blendMode = blendMode
    asFrameworkPaint().maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
}

/**
 * [color] down the height of the surface: whole at the top and bottom edges,
 * fading on the outline's own squared curve and gone [GLASS_RIM_REACH] of the
 * way in to the middle from either — so the rim, which thins as the edge turns
 * down an end, runs out smoothly well before it gets there.
 */
private fun rimReach(height: Float, color: Color): Shader {
    val top = squaredFade(GLASS_RIM_REACH / 2f)
    val stops = top + top.reversed().map { (at, fade) -> 1f - at to fade }
    return LinearGradientShader(
        from = Offset.Zero,
        to = Offset(0f, height),
        colors = stops.map { (_, fade) -> color.copy(alpha = fade) },
        colorStops = stops.map { (at, _) -> at },
    )
}

/** The grey that color-dodges whatever is beneath it up by [gain]: `d / (1 - g)`. */
private fun gainGrey(gain: Float): Color = (1f - 1f / gain).let { Color(it, it, it) }

/** The top and bottom rims of [body], each [depth] deep where the edge runs flat. */
private fun rims(body: Path, depth: Float): Path {
    fun shifted(dy: Float) = Path().apply { addPath(body, Offset(0f, dy)) }
    val top = Path().apply { op(body, shifted(depth), PathOperation.Difference) }
    val bottom = Path().apply { op(body, shifted(-depth), PathOperation.Difference) }
    return Path().apply { op(top, bottom, PathOperation.Union) }
}

/** The dark outline: a band just outside the shape (2.3px at 3x), at its darkest at the two ends. */
private val GLASS_OUTLINE_WIDTH = 0.77.dp
private const val GLASS_OUTLINE_ALPHA = 0.35f
private const val GLASS_OUTLINE_MIDDLE_ALPHA = 0.1f

/** How far across an end cap the outline takes to fall to its middle opacity. */
private const val GLASS_OUTLINE_REACH = 0.6f

/**
 * How deep the bright rim is, in from the edge at the middle of a flat run;
 * how far it multiplies the colour beneath; and the white added after.
 */
private val GLASS_RIM_DEPTH = 0.83.dp
private const val GLASS_RIM_GAIN = 2.4f
private const val GLASS_RIM_ALPHA = 0.07f

/**
 * How far the rim runs down an end before it is gone, as a fraction of the
 * way from the top (or bottom) to the middle.
 */
private const val GLASS_RIM_REACH = 0.55f

/** Just enough to take the hard edge off the rim — a pixel or two. */
private val GLASS_RIM_BLUR = 0.5.dp

/** The glow: a deeper copy of the rim, blurred, and a fraction as bright. */
private val GLASS_GLOW_DEPTH = 4.dp
private val GLASS_GLOW_BLUR = 10.dp
private const val GLASS_GLOW_GAIN = 1.6f
private const val GLASS_GLOW_ALPHA = 0.07f
