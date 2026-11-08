package com.music.bitchord.ui.components

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * A pill cut as a fully rounded squircle, the way Liquid Glass draws its capsules.
 *
 * Fully rounded like a capsule — each end spans the whole height and reaches in
 * half of it, with no straight run — but each quarter of an end is a
 * superellipse, `|x|ⁿ + |y|ⁿ = 1`, rather than a circle. Past `n = 2` the
 * shoulders sit fuller than a semicircle's and the curve meets the straight
 * edges with no jump in curvature, which is what makes it a squircle; well past
 * it the ends square off into a rounded rectangle. [exponent] is that `n`.
 *
 * Neither way of getting there from [ContinuousCornerShape] worked: at half the
 * height its ramps only soften the shoulders, just inside the semicircle, so it
 * read as a plain capsule; and a radius small enough for the ramps to fit the
 * ends squared them off.
 *
 * A [CornerBasedShape] of half the height, so the backdrop's lens, which reads
 * corner radii off one, takes it for the capsule it nearly is.
 */
class SquirclePillShape(val exponent: Float = SQUIRCLE_PILL_EXPONENT) :
    CornerBasedShape(CornerSize(50), CornerSize(50), CornerSize(50), CornerSize(50)) {

    /** Always fully rounded, whatever corners are asked for. */
    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize,
    ): CornerBasedShape = SquirclePillShape(exponent)

    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline {
        val r = size.minDimension / 2f
        if (r <= 0f) return Outline.Rectangle(size.toRect())
        val w = size.width
        val h = size.height
        val path = Path()
        // Clockwise from the top edge; each quarter centred `r` in from its corner.
        path.moveTo(r, 0f)
        path.lineTo(w - r, 0f)
        path.quarter(w - r, r, 1f, -1f, r, towardEdge = false)
        path.lineTo(w, h - r)
        path.quarter(w - r, h - r, 1f, 1f, r, towardEdge = true)
        path.lineTo(r, h)
        path.quarter(r, h - r, -1f, 1f, r, towardEdge = false)
        path.lineTo(0f, r)
        path.quarter(r, r, -1f, -1f, r, towardEdge = true)
        path.close()
        return Outline.Generic(path)
    }

    /**
     * One quarter of the superellipse round ([cx], [cy]), from the point on its
     * vertical through the centre to the one on its horizontal, or the reverse
     * when [towardEdge] — toward the top or bottom edge.
     */
    private fun Path.quarter(cx: Float, cy: Float, sx: Float, sy: Float, r: Float, towardEdge: Boolean) {
        val power = 2f / exponent
        for (i in 1..QUARTER_STEPS) {
            val step = if (towardEdge) i else QUARTER_STEPS - i
            val t = step * (Math.PI / 2) / QUARTER_STEPS
            val x = cos(t).toFloat().coerceAtLeast(0f).pow(power)
            val y = sin(t).toFloat().coerceAtLeast(0f).pow(power)
            lineTo(cx + sx * r * x, cy + sy * r * y)
        }
    }

    override fun equals(other: Any?): Boolean = other is SquirclePillShape && other.exponent == exponent

    override fun hashCode(): Int = exponent.hashCode()

    override fun toString(): String = "SquirclePillShape(exponent = $exponent)"
}

/**
 * How squircle the pill is: 2 is a plain capsule. Tuned by eye on device, where
 * even 2.2 read a little boxy.
 */
const val SQUIRCLE_PILL_EXPONENT = 2.1f

/** Segments in each quarter of an end: past what any pill's edge shows as facets. */
private const val QUARTER_STEPS = 32
