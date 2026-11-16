package com.music.bitchord.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.isFinished
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.unit.Dp
import com.music.bitchord.sharedui.resources.Res
import com.music.bitchord.sharedui.resources.ic_player_next
import com.music.bitchord.sharedui.resources.ic_player_previous
import com.music.bitchord.ui.rememberIsForeground
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.vectorResource

/**
 * Tuned to the requested conveyor choreography, not published Apple constants.
 * Progress is elapsed time / 600ms on an isolated tap; only a retarget changes
 * its speed. All geometry comes from the existing drawable, never a new triangle.
 */
private object SkipMotion {
    const val DurationMs = 600
    const val FrontGone = 0.30f
    const val Settled = 0.90f
    const val NewOnset = 0.28f
    const val CircleDiameter = 1.7f
    const val CircleAlpha = 0.08f
    const val CircleFeatherStart = 0.80f
    const val CircleInMs = 45
    const val CircleOutMs = 100
    const val CircleHoldMs = 125
    // A cubic Hermite easing with start slope <= 2 and end slope 1 is monotone.
    const val MaxStartSlope = 2f
    const val MillisPerSecond = 1000f
}

/** Visual intent only. The owner still calls its original playback action on click. */
class PlayerSkipMotion internal constructor(
    internal val active: State<Boolean>,
    internal val reduceMotion: State<Boolean>,
) {
    // AnimationState retains the last frame time as well as value/velocity on
    // cancellation. Retargets can continue without another zero-time frame.
    internal var cycles by mutableStateOf(AnimationState(0f))
    internal var requestedCycles by mutableIntStateOf(0)
    internal var targetCycles by mutableStateOf(0f)

    fun onTap() {
        targetCycles = playerSkipTarget(cycles.value, targetCycles)
        requestedCycles++
    }
}

/** Keep the interrupted cycle and at most one pending cycle, never a tap backlog. */
internal fun playerSkipTarget(cycles: Float, target: Float): Float {
    val boundary = cycles - cycles % 1f + 1f
    return if (cycles < target) boundary + 1f else boundary
}

@Composable
fun rememberPlayerSkipMotion(active: Boolean, reduceMotion: Boolean): PlayerSkipMotion {
    val foreground = rememberIsForeground()
    val activeState = rememberUpdatedState(active && foreground)
    val reduceMotionState = rememberUpdatedState(reduceMotion)
    val motion = remember { PlayerSkipMotion(activeState, reduceMotionState) }
    LaunchedEffect(motion.requestedCycles, active, foreground, reduceMotion) {
        val cycles = motion.cycles
        val target = motion.targetCycles
        val distance = target - cycles.value
        if (!active || !foreground || reduceMotion) {
            // Consume hidden/reduced-motion taps with no old clock to replay.
            motion.cycles = AnimationState(target)
        } else if (distance > 0f) {
            val continuing = !cycles.isFinished
            val velocity = if (continuing && cycles.velocity > 0f) cycles.velocity else {
                distance * SkipMotion.MillisPerSecond / SkipMotion.DurationMs
            }
            // Finish within one tap window, even when several taps coalesce into
            // one frame. Shorten it if needed to retain velocity without overshoot.
            val duration = minOf(
                SkipMotion.DurationMs,
                (SkipMotion.MaxStartSlope * distance * SkipMotion.MillisPerSecond / velocity)
                    .toInt(),
            ).coerceAtLeast(1)
            val slope = (velocity * duration / (distance * SkipMotion.MillisPerSecond))
                .coerceIn(0f, SkipMotion.MaxStartSlope)
            cycles.animateTo(
                targetValue = target,
                animationSpec = tween(
                    durationMillis = duration,
                    easing = Easing { t ->
                        // Hermite(0, 1, slope, 1). At slope=1 this is exactly linear.
                        (slope - 1f) * t * t * t + 2f * (1f - slope) * t * t + slope * t
                    },
                ),
                // Use the interrupted frame's timestamp, not a fresh frame at
                // play time zero. The Hermite slope above carries its speed.
                sequentialAnimation = continuing,
            )
        }
    }
    return motion
}

internal data class SkipPose(
    val frontScale: Float,
    val frontAlpha: Float,
    val survivorTravel: Float,
    val newScale: Float,
    val newAlpha: Float,
)

/** Pure, non-overshooting conveyor pose; endpoint contours are the original slots. */
internal fun playerSkipPose(progress: Float): SkipPose {
    val p = progress.coerceIn(0f, 1f)
    val contraction = (p / SkipMotion.FrontGone).coerceIn(0f, 1f)
    val frontScale = 1f - skipSmoothStep(contraction)
    // Scale already reduces visible area quadratically. Fade later so FRONT
    // remains a recognizable triangle through the middle of its contraction.
    val frontAlpha = 1f - skipSmoothStep(contraction * contraction)
    val travel = skipTravel(p / SkipMotion.Settled)
    val growth = skipSmoothStep(
        (p - SkipMotion.NewOnset) / (SkipMotion.Settled - SkipMotion.NewOnset),
    )
    return SkipPose(frontScale, frontAlpha, travel, growth, growth)
}

private fun skipMix(start: Float, end: Float, progress: Float): Float =
    start + (end - start) * progress

private fun skipSmoothStep(progress: Float): Float {
    val t = progress.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun skipTravel(progress: Float): Float {
    val t = progress.coerceIn(0f, 1f)
    // One cubic Hermite trip (start slope 1, end slope 0): less front-loaded
    // than the old quadratic, without a midpoint stop or endpoint overshoot.
    return t + t * t * (1f - t)
}

/**
 * Use the original painter at rest, including the final 10% of each cycle.
 * During motion, split its actual contours, not clipped halves of the painter:
 * the current rounded arrowheads' bounding boxes overlap slightly.
 */
@Composable
fun PlayerSkipGlyph(
    motion: PlayerSkipMotion,
    contentDescription: String,
    tint: Color,
    modifier: Modifier = Modifier,
    previous: Boolean = false,
) {
    val resource = if (previous) Res.drawable.ic_player_previous else Res.drawable.ic_player_next
    val vector = vectorResource(resource)
    // Compose resources can return an empty vector while their asynchronous
    // resource state loads. Keep the original painter, rather than index root[0].
    if (vector.root.size == 0) {
        Icon(
            painter = painterResource(resource),
            contentDescription = contentDescription,
            tint = tint,
            modifier = modifier,
        )
        return
    }
    val nodes = (vector.root[0] as VectorPath).pathData
    val split = remember(nodes) { nodes.indexOfLast { it is PathNode.MoveTo } }
    val backNodes = remember(nodes) { nodes.subList(0, split) }
    val frontNodes = remember(nodes) { nodes.subList(split, nodes.size) }
    Icon(
        painter = painterResource(resource),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.drawWithCache {
            val back = backNodes.toPath()
            val front = frontNodes.toPath()
            val survivor = Path()
            val survivorNodes = backNodes.toMutableList()
            val backCenter = back.getBounds().center
            val frontCenter = front.getBounds().center
            onDrawWithContent {
                val p = motion.cycles.value % 1f
                if (!motion.active.value || motion.reduceMotion.value || p == 0f || p >= SkipMotion.Settled) {
                    drawContent()
                } else {
                    val pose = playerSkipPose(p)
                    // Both vectors use absolute M/L/A/Z nodes with matching arc
                    // flags. Interpolating the source coordinates also preserves
                    // their sub-pixel differences instead of assuming translation
                    // alone can make the two existing arrowheads identical.
                    // Reuse the node list and destination Path, not a new list
                    // or native graphics object on every animation frame.
                    for (index in survivorNodes.indices) {
                        survivorNodes[index] = interpolateSkipNode(
                            backNodes[index], frontNodes[index], pose.survivorTravel,
                        )
                    }
                    survivorNodes.toPath(survivor)
                    scale(size.width / vector.viewportWidth, size.height / vector.viewportHeight,
                        pivot = androidx.compose.ui.geometry.Offset.Zero) {
                        scale(pose.frontScale, pivot = frontCenter) {
                            drawPath(front, tint, alpha = pose.frontAlpha)
                        }
                        drawPath(survivor, tint)
                        scale(pose.newScale, pivot = backCenter) {
                            drawPath(back, tint, alpha = pose.newAlpha)
                        }
                    }
                }
            }
        },
    )
}

private fun interpolateSkipNode(from: PathNode, to: PathNode, progress: Float): PathNode =
    when (from) {
        is PathNode.MoveTo -> {
            to as PathNode.MoveTo
            PathNode.MoveTo(skipMix(from.x, to.x, progress), skipMix(from.y, to.y, progress))
        }
        is PathNode.LineTo -> {
            to as PathNode.LineTo
            PathNode.LineTo(skipMix(from.x, to.x, progress), skipMix(from.y, to.y, progress))
        }
        is PathNode.ArcTo -> {
            to as PathNode.ArcTo
            PathNode.ArcTo(
                horizontalEllipseRadius = skipMix(from.horizontalEllipseRadius, to.horizontalEllipseRadius, progress),
                verticalEllipseRadius = skipMix(from.verticalEllipseRadius, to.verticalEllipseRadius, progress),
                theta = skipMix(from.theta, to.theta, progress),
                isMoreThanHalf = from.isMoreThanHalf,
                isPositiveArc = from.isPositiveArc,
                arcStartX = skipMix(from.arcStartX, to.arcStartX, progress),
                arcStartY = skipMix(from.arcStartY, to.arcStartY, progress),
            )
        }
        else -> from // Close; verified against both current drawable paths.
    }

/** Expanded player only. No pointer handler, touch target, or semantics changes. */
@Composable
internal fun Modifier.playerSkipPressCircle(
    motion: PlayerSkipMotion,
    interactionSource: MutableInteractionSource,
    enabled: Boolean,
    tint: Color,
    glyphSize: Dp,
    diameter: Dp,
): Modifier {
    val target = motion.targetCycles
    return playerPressCircle(
        interactionSource = interactionSource,
        trigger = motion.requestedCycles,
        enabled = enabled && motion.active.value,
        reduceMotion = motion.reduceMotion.value,
        tint = tint,
        glyphSize = glyphSize,
        diameter = diameter,
        // Keep the short press response (~270ms), not a 600ms pressed disc.
        minimumHoldMs = SkipMotion.CircleHoldMs,
        hasMotion = { motion.cycles.value < target },
        settled = { true },
    )
}

/** Draw-only feedback shared by expanded transport controls, never mini buttons. */
@Composable
internal fun Modifier.playerPressCircle(
    interactionSource: MutableInteractionSource,
    trigger: Int,
    enabled: Boolean,
    reduceMotion: Boolean,
    tint: Color,
    glyphSize: Dp,
    diameter: Dp,
    hasMotion: () -> Boolean,
    settled: () -> Boolean,
    minimumHoldMs: Int = 0,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val foreground = rememberIsForeground()
    val opacity = remember { Animatable(0f) }
    val hold = remember { Animatable(0f) }
    var consumedTrigger by remember { mutableIntStateOf(trigger) }
    // Updated lambdas let playback change while the same press response awaits
    // the morph's first rise, without restarting the circle or its minimum hold.
    val currentSettled by rememberUpdatedState(settled)
    LaunchedEffect(pressed, trigger, foreground, enabled, reduceMotion) {
        val clicked = trigger != consumedTrigger
        consumedTrigger = trigger
        if (!foreground || !enabled || reduceMotion) {
            opacity.snapTo(0f)
        } else if (pressed || clicked || hasMotion()) {
            opacity.animateTo(1f, tween(SkipMotion.CircleInMs, easing = LinearOutSlowInEasing))
            if (!pressed) {
                if (minimumHoldMs > 0) {
                    // An animation-clock hold, not wall-clock delay: duration
                    // scale zero must collapse the whole response immediately.
                    hold.snapTo(0f)
                    hold.animateTo(1f, tween(minimumHoldMs))
                }
                snapshotFlow { currentSettled() }.first { it }
                opacity.animateTo(0f, tween(SkipMotion.CircleOutMs))
            }
        } else {
            // Cancelled presses never request a conveyor cycle or playback.
            opacity.animateTo(0f, tween(SkipMotion.CircleOutMs))
        }
    }
    return drawWithCache {
        // Existing skip touch boxes are smaller than the reference's 1.7x disc.
        // Cap rather than enlarge their targets or spill over neighbouring controls.
        val circleSize = minOf(diameter.toPx(), glyphSize.toPx() * SkipMotion.CircleDiameter)
        val radius = minOf(size.width, size.height, circleSize) / 2f
        val fill = tint.copy(alpha = tint.alpha * SkipMotion.CircleAlpha)
        val brush = Brush.radialGradient(
            0f to fill,
            SkipMotion.CircleFeatherStart to fill,
            1f to tint.copy(alpha = 0f),
            center = Offset(size.width / 2f, size.height / 2f),
            radius = radius,
        )
        onDrawBehind {
            if (enabled && foreground && !reduceMotion) {
                drawCircle(brush, radius = radius, alpha = opacity.value)
            }
        }
    }
}
