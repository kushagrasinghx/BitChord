/*
 * Adapted from Convx's IosOverscroll.kt (GPL-3.0):
 * https://github.com/cosmictaserdev-creator/Convx
 */
package com.music.bitchord.ui.utils

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

private const val rubberBandConstant = 0.55f
private const val fallbackContainerPx = 2_000f
private const val maxBounceVelocity = 10_000f
private const val highVelocityThreshold = 5_000f
private const val normalBounceStiffness = 247f
private const val fastBounceStiffness = 130f
private const val flingPullReleaseMultiplier = 4f
private const val freshPullThresholdPx = 0.5f

/**
 * Converts raw pull distance to UIKit's self-limiting rubber-band curve.
 */
private fun rubberBand(rawDistance: Float, containerPx: Float): Float {
    val dimension = containerPx.takeIf { it > 0f } ?: fallbackContainerPx
    val distance = abs(rawDistance)
    val banded = (1f - 1f / (distance * rubberBandConstant / dimension + 1f)) *
        dimension / rubberBandConstant
    return banded * sign(rawDistance)
}

/**
 * An iOS-style overscroll effect for Compose scroll containers.
 *
 * It replaces Android's stretch/glow with a UIScrollView-like rubber band during a
 * drag, then critically damps the content back to its resting position on release.
 */
internal class IosOverscrollEffect : OverscrollEffect {
    private val rawPullX = mutableFloatStateOf(0f)
    private val rawPullY = mutableFloatStateOf(0f)
    private var containerWidthPx = 0f
    private var containerHeightPx = 0f
    private var settling: Job? = null
    private var gestureGeneration = 0L

    override val isInProgress: Boolean
        get() = rawPullX.floatValue != 0f || rawPullY.floatValue != 0f

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset {
        if (source == NestedScrollSource.UserInput) {
            gestureGeneration++
            cancelSettle()
        }
        // A fling should clear existing stretch quickly, before its decay
        // spends its momentum there. Foundation's native effect uses the same
        // multiplier. Finger-driven recovery remains one-to-one.
        val releaseMultiplier = if (source == NestedScrollSource.SideEffect) flingPullReleaseMultiplier else 1f
        val paidX = payDown(rawPullX, delta.x, releaseMultiplier)
        val paidY = payDown(rawPullY, delta.y, releaseMultiplier)
        val remaining = Offset(delta.x - paidX, delta.y - paidY)
        val consumed = performScroll(remaining)
        val leftover = remaining - consumed

        // Absorb only touch deltas. Fling leftovers must be returned so Compose
        // cancels its decay and gives their velocity to applyToFling below.
        var stretchedX = 0f
        var stretchedY = 0f
        if (source == NestedScrollSource.UserInput) {
            // Small rounding remainders must not create phantom edge tension
            // during otherwise ordinary scrolling. Once a real pull exists,
            // keep following even subpixel finger movement continuously.
            if (leftover.x != 0f && (rawPullX.floatValue != 0f || abs(leftover.x) > freshPullThresholdPx)) {
                rawPullX.floatValue += leftover.x
                stretchedX = leftover.x
            }
            if (leftover.y != 0f && (rawPullY.floatValue != 0f || abs(leftover.y) > freshPullThresholdPx)) {
                rawPullY.floatValue += leftover.y
                stretchedY = leftover.y
            }
        }
        return Offset(consumed.x + paidX + stretchedX, consumed.y + paidY + stretchedY)
    }

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity,
    ) {
        val generation = ++gestureGeneration
        cancelSettle()
        val consumed = performFling(velocity)
        // Compose can finish the old fling after a new drag has taken over.
        // That fling must not start a bounce underneath the new finger.
        if (generation != gestureGeneration) return
        val leftover = velocity - consumed
        if (!isInProgress && leftover == Velocity.Zero) return

        coroutineScope {
            val animation = coroutineContext.job
            settling = animation
            try {
                joinAll(
                    launch { settle(rawPullX, leftover.x) },
                    launch { settle(rawPullY, leftover.y) },
                )
            } finally {
                if (settling === animation) settling = null
            }
        }
    }

    private fun cancelSettle() {
        // This spring runs after performFling releases the list's scroll mutex,
        // so starting a new list drag does not cancel it automatically.
        settling?.cancel()
        settling = null
    }

    private fun payDown(pull: MutableFloatState, delta: Float, releaseMultiplier: Float): Float {
        val current = pull.floatValue
        if (current == 0f || delta == 0f || sign(current) == sign(delta)) return 0f
        val target = current + delta * releaseMultiplier
        return if (sign(target) != sign(current)) {
            pull.floatValue = 0f
            -current / releaseMultiplier
        } else {
            pull.floatValue = target
            // Return input units exactly when all of this delta was used.
            delta
        }
    }

    private suspend fun settle(pull: MutableFloatState, leftoverVelocity: Float) {
        if (pull.floatValue == 0f && leftoverVelocity == 0f) return
        val velocity = leftoverVelocity.coerceIn(-maxBounceVelocity, maxBounceVelocity)
        animate(
            initialValue = pull.floatValue,
            targetValue = 0f,
            initialVelocity = velocity,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = if (abs(velocity) > highVelocityThreshold) {
                    fastBounceStiffness
                } else {
                    normalBounceStiffness
                },
            ),
        ) { value, _ -> pull.floatValue = value }
    }

    override val node: DelegatableNode = IosOverscrollNode(
        offsetX = { rubberBand(rawPullX.floatValue, containerWidthPx) },
        offsetY = { rubberBand(rawPullY.floatValue, containerHeightPx) },
        onMeasured = { width, height ->
            containerWidthPx = width
            containerHeightPx = height
        },
    )
}

private class IosOverscrollNode(
    private val offsetX: () -> Float,
    private val offsetY: () -> Float,
    private val onMeasured: (Float, Float) -> Unit,
) : Modifier.Node(), LayoutModifierNode {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        onMeasured(
            (if (constraints.hasBoundedWidth) constraints.maxWidth else placeable.width).toFloat(),
            (if (constraints.hasBoundedHeight) constraints.maxHeight else placeable.height).toFloat(),
        )
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                translationX = offsetX()
                translationY = offsetY()
            }
        }
    }
}

private class IosOverscrollFactory(
    private val density: Density,
    private val scope: CoroutineScope,
) : OverscrollFactory {
    override fun createOverscrollEffect(): OverscrollEffect = IosOverscrollEffect()

    override fun equals(other: Any?): Boolean =
        other is IosOverscrollFactory && other.density == density && other.scope === scope

    override fun hashCode(): Int = 31 * density.hashCode() + scope.hashCode()
}

/** Provides iOS-style rubber-band overscroll to every descendant scroll container. */
@Composable
fun rememberIosOverscrollFactory(): OverscrollFactory {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    return remember(density, scope) { IosOverscrollFactory(density, scope) }
}
