package com.music.bitchord.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp

/**
 * Apple's vibrancy: secondary content added onto whatever is drawn beneath it
 * (plus-lighter) rather than laid over it at reduced opacity.
 *
 * A 55% white label by opacity is 0.55 white and 0.45 of the backdrop — the
 * backdrop's colour diluted towards grey. Added, it keeps all of the backdrop
 * and lifts it, so the label takes the hue of what is behind it and stays as
 * saturated as that is: the tinted, glowing secondary text and glyphs of Apple
 * Music's player, sheets and glass. Over flat black the two are identical. The
 * colour keeps the alpha it already had — that is now the amount added.
 *
 * [content] is the colour being drawn, and only light content is blended. Dark
 * content added to anything adds next to nothing, and Apple's counterpart for
 * it, plus-darker, is a blend mode neither Android nor Skia offers — so a
 * theme-dependent colour passes itself here and keeps plain opacity in light
 * theme.
 *
 * The whole subtree is composited as one layer with [BlendMode.Plus], so this
 * belongs on the label or glyph itself, never on a container that also holds
 * artwork or a coloured control: those would be added onto the page with it.
 * The blend reaches the backdrop only when that is drawn into the same layer —
 * inside an offscreen layer of its own it adds to transparency, which looks
 * exactly like the opacity it replaces. For a plain fill, [vibrantBackground]
 * does the same without a layer.
 */
fun Modifier.vibrancy(content: Color = Color.White): Modifier =
    if (content.isVibrant()) graphicsLayer { blendMode = BlendMode.Plus } else this

/**
 * A [androidx.compose.foundation.background] whose fill is added onto what is
 * beneath it — see [vibrancy]. For the fills that are pure decoration: sheet
 * grabbers, slider tracks, the translucent discs and pills behind glyphs.
 *
 * Drawn with the blend mode directly, so unlike [vibrancy] it costs no layer
 * and leaves the content drawn over it alone.
 */
fun Modifier.vibrantBackground(color: Color, shape: Shape = RectangleShape): Modifier =
    drawBehind {
        val blendMode = vibrantBlendMode(color)
        if (shape === RectangleShape) {
            drawRect(color = color, blendMode = blendMode)
        } else {
            drawOutline(shape.createOutline(size, layoutDirection, this), color = color, blendMode = blendMode)
        }
    }

/**
 * A [androidx.compose.foundation.border] added onto what is beneath it — see
 * [vibrancy]. Inside the shape, over the content, as `border` draws.
 */
fun Modifier.vibrantBorder(width: Dp, color: Color, shape: Shape = RectangleShape): Modifier =
    drawWithCache {
        val edge = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
        // Twice the width, centred on the edge and clipped to the inside half.
        val stroke = Stroke(width = width.toPx() * 2f)
        val blendMode = vibrantBlendMode(color)
        onDrawWithContent {
            drawContent()
            clipPath(edge) { drawPath(edge, color, style = stroke, blendMode = blendMode) }
        }
    }

/**
 * The blend mode for drawing [color] vibrantly with a DrawScope call of its
 * own — a track, a tick, a ring — the way [vibrantBackground] draws a fill.
 */
fun vibrantBlendMode(color: Color): BlendMode =
    if (color.isVibrant()) BlendMode.Plus else DrawScope.DefaultBlendMode

private fun Color.isVibrant(): Boolean = luminance() > 0.5f
