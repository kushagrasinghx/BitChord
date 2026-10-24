package com.music.bitchord.ui.components

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Apple's continuous corner — the squircle every iOS surface is cut to — rather
 * than the circular arc of a [androidx.compose.foundation.shape.RoundedCornerShape].
 *
 * A circular corner meets its straight edge with a jump in curvature, from none
 * to all of it, and that jump is the hard "shoulder" a rounded rectangle shows
 * next to an Apple one. Here the curvature ramps in over a cubic either side of
 * a shorter arc, the construction Figma's corner smoothing uses, with
 * [smoothing] the share of each corner's quarter-turn handed to those ramps.
 *
 * Each corner smooths into each of its two edges separately, as far as the
 * straight run of that edge leaves room. So a capsule keeps a true semicircular
 * end — the short edge has no straight run to ramp into — while its long edges
 * ease into the curve, which is how a continuous capsule is drawn; a circle
 * stays a circle. Every point of the curve lies on or inside the circular corner
 * of the same radius, so a surface clipped by the plain shape loses nothing.
 *
 * A [CornerBasedShape], so the backdrop's lens and highlight shaders, which read
 * corner radii off one, take it as they would the plain shape.
 */
class ContinuousCornerShape(
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize,
    val smoothing: Float = GLASS_CORNER_SMOOTHING,
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {

    constructor(percent: Int, smoothing: Float = GLASS_CORNER_SMOOTHING) :
        this(CornerSize(percent), CornerSize(percent), CornerSize(percent), CornerSize(percent), smoothing)

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize,
    ): CornerBasedShape = ContinuousCornerShape(topStart, topEnd, bottomEnd, bottomStart, smoothing)

    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline {
        if (topStart + topEnd + bottomEnd + bottomStart == 0f) return Outline.Rectangle(size.toRect())
        val ltr = layoutDirection == LayoutDirection.Ltr
        val maxRadius = size.minDimension / 2f
        val topLeft = (if (ltr) topStart else topEnd).coerceAtMost(maxRadius)
        val topRight = (if (ltr) topEnd else topStart).coerceAtMost(maxRadius)
        val bottomRight = (if (ltr) bottomEnd else bottomStart).coerceAtMost(maxRadius)
        val bottomLeft = (if (ltr) bottomStart else bottomEnd).coerceAtMost(maxRadius)
        val w = size.width
        val h = size.height
        val s = smoothing.coerceIn(0f, 1f)

        val path = Path()
        // Clockwise from the top edge, each corner drawn from the edge running
        // into it ([inward] along that edge) to the edge leaving it ([outward]).
        path.moveTo(topLeft * (1 + edgeSmoothing(s, topLeft, topRight, w)), 0f)
        path.corner(
            corner = Offset(w, 0f), radius = topRight,
            inward = Offset(1f, 0f), outward = Offset(0f, 1f),
            smoothingIn = edgeSmoothing(s, topRight, topLeft, w),
            smoothingOut = edgeSmoothing(s, topRight, bottomRight, h),
        )
        path.corner(
            corner = Offset(w, h), radius = bottomRight,
            inward = Offset(0f, 1f), outward = Offset(-1f, 0f),
            smoothingIn = edgeSmoothing(s, bottomRight, topRight, h),
            smoothingOut = edgeSmoothing(s, bottomRight, bottomLeft, w),
        )
        path.corner(
            corner = Offset(0f, h), radius = bottomLeft,
            inward = Offset(-1f, 0f), outward = Offset(0f, -1f),
            smoothingIn = edgeSmoothing(s, bottomLeft, bottomRight, w),
            smoothingOut = edgeSmoothing(s, bottomLeft, topLeft, h),
        )
        path.corner(
            corner = Offset.Zero, radius = topLeft,
            inward = Offset(0f, -1f), outward = Offset(1f, 0f),
            smoothingIn = edgeSmoothing(s, topLeft, bottomLeft, h),
            smoothingOut = edgeSmoothing(s, topLeft, topRight, w),
        )
        path.close()
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ContinuousCornerShape) return false
        return topStart == other.topStart && topEnd == other.topEnd &&
            bottomEnd == other.bottomEnd && bottomStart == other.bottomStart &&
            smoothing == other.smoothing
    }

    override fun hashCode(): Int {
        var result = topStart.hashCode()
        result = 31 * result + topEnd.hashCode()
        result = 31 * result + bottomEnd.hashCode()
        result = 31 * result + bottomStart.hashCode()
        result = 31 * result + smoothing.hashCode()
        return result
    }

    override fun toString(): String =
        "ContinuousCornerShape(topStart = $topStart, topEnd = $topEnd, bottomEnd = " +
            "$bottomEnd, bottomStart = $bottomStart, smoothing = $smoothing)"
}

/**
 * The same corners as this shape, cut continuous — see [ContinuousCornerShape].
 * A [SquirclePillShape] is already as continuous as it is meant to be.
 */
fun CornerBasedShape.continuous(smoothing: Float = GLASS_CORNER_SMOOTHING): CornerBasedShape =
    when (this) {
        is ContinuousCornerShape, is SquirclePillShape -> this
        else -> ContinuousCornerShape(topStart, topEnd, bottomEnd, bottomStart, smoothing)
    }

/**
 * Apple's corner smoothing, as the share of each corner's turn given over to the
 * curvature ramps. Figma calls 60% "iOS"; measured off Apple's own glass it
 * comes to a touch over that.
 */
const val GLASS_CORNER_SMOOTHING = 0.607f

/**
 * How much of [smoothing] a corner of [radius] can spend along an edge of
 * [length] whose other end has a corner of [otherRadius]: the straight run
 * between the two arcs is shared between them in proportion to their radii,
 * so neither ramp reaches into the other's.
 */
private fun edgeSmoothing(smoothing: Float, radius: Float, otherRadius: Float, length: Float): Float {
    if (radius <= 0f) return 0f
    val straight = (length - radius - otherRadius).coerceAtLeast(0f)
    val share = straight * radius / (radius + otherRadius)
    return smoothing.coerceAtMost(share / radius)
}

/**
 * One corner, from the end of the straight edge running into it to the start
 * of the straight edge leaving it.
 *
 * Each half is a cubic from the edge onto the arc, Figma's construction: the
 * edge point sits `(1 + s) * radius` from the corner, the arc keeps the
 * circular corner's own centre, and gives up `45 * s` degrees to the cubic.
 */
private fun Path.corner(
    corner: Offset,
    radius: Float,
    inward: Offset,
    outward: Offset,
    smoothingIn: Float,
    smoothingOut: Float,
) {
    if (radius <= 0f) {
        lineTo(corner.x, corner.y)
        return
    }
    val center = corner - inward * radius + outward * radius

    val inHalf = CornerHalf(radius, smoothingIn)
    // Measured back along the incoming edge from the corner, and in from it.
    fun alongIn(distance: Float, depth: Float = 0f) = corner - inward * distance + outward * depth
    lineTo(alongIn(inHalf.edge))
    val arcStart = alongIn(inHalf.arcAlong, inHalf.arcDepth)
    if (smoothingIn > 0f) {
        cubicTo(alongIn(inHalf.edge - inHalf.a), alongIn(inHalf.edge - inHalf.a - inHalf.b), arcStart)
    }

    val outHalf = CornerHalf(radius, smoothingOut)
    // Measured on along the outgoing edge from the corner, and in from it.
    fun alongOut(distance: Float, depth: Float = 0f) = corner + outward * distance - inward * depth
    val arcEnd = alongOut(outHalf.arcAlong, outHalf.arcDepth)
    val startAngle = angleOf(arcStart - center)
    val sweep = 90f - 45f * smoothingIn - 45f * smoothingOut
    arcTo(
        rect = Rect(center, radius),
        startAngleDegrees = startAngle,
        sweepAngleDegrees = sweep,
        forceMoveTo = false,
    )
    if (smoothingOut > 0f) {
        cubicTo(
            alongOut(outHalf.edge - outHalf.a - outHalf.b),
            alongOut(outHalf.edge - outHalf.a),
            alongOut(outHalf.edge),
        )
    } else {
        lineTo(arcEnd)
    }
}

/** The measurements of half a smoothed corner, along and in from its edge. */
private class CornerHalf(radius: Float, smoothing: Float) {
    private val turn = Math.toRadians(45.0 * smoothing)

    /** Where the curve leaves the straight edge, measured from the corner. */
    val edge = (1 + smoothing) * radius

    /** Where the cubic meets the arc. */
    val arcAlong = radius - radius * sin(turn).toFloat()
    val arcDepth = radius * (1 - cos(turn).toFloat())

    private val c = radius * (tan(turn / 2) * cos(turn)).toFloat()
    val b = (edge - arcAlong - c) / 3f
    val a = 2f * b
}

private fun angleOf(v: Offset): Float = Math.toDegrees(atan2(v.y, v.x).toDouble()).toFloat()

private fun Path.lineTo(p: Offset) = lineTo(p.x, p.y)

private fun Path.cubicTo(p1: Offset, p2: Offset, p3: Offset) = cubicTo(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y)
