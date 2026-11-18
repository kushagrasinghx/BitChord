package com.music.bitchord.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.music.bitchord.ui.rememberIsForeground
import kotlin.math.abs

/**
 * Fits to consecutive frames of 60fps.design's Apple Music Play Pause Interaction,
 * not published Apple constants. The visible handoff shrinks and blurs the two
 * silhouettes; it does not expose a triangle split into two sharp polygons.
 * Keep the older shared morph unchanged for mini-player/navigation-bar callers.
 */
private object ExpandedPlayPauseSpec {
    const val Damping = 0.68f
    const val Stiffness = 170f
    const val Threshold = 0.0001f
    const val MinimumScale = 0.35f
    const val MaximumScale = 1.08f
    const val BlurFraction = 0.16f
    const val PressScale = 0.90f
    const val PressMs = 100
    const val CircleHoldMs = 85
    const val CircleRemaining = 0.20f
}

internal fun expandedSymbolScale(weight: Float): Float =
    weight.coerceIn(ExpandedPlayPauseSpec.MinimumScale, ExpandedPlayPauseSpec.MaximumScale)

internal fun expandedSymbolAlpha(weight: Float): Float {
    val w = weight.coerceIn(0f, 1f)
    return w * w * (3f - 2f * w)
}

/** Fraction of the unchanged icon slot, before the symbol's inner scale transform. */
internal fun expandedSymbolBlur(weight: Float): Float =
    ExpandedPlayPauseSpec.BlurFraction * (1f - weight.coerceIn(0f, 1f))

internal class ExpandedPlayPauseMotion(
    private val playing: State<Boolean>,
    internal val visible: State<Boolean>,
    internal val reduceMotion: State<Boolean>,
) {
    internal val phase = Animatable(if (playing.value) 1f else 0f,
        visibilityThreshold = ExpandedPlayPauseSpec.Threshold)
    internal var previousPlaying = playing.value
    internal var wasVisible = visible.value
    // Animatable resets its velocity when a keyed effect is cancelled. Retain
    // the last frame's velocity so rapid reversals continue instead of restarting.
    internal var carriedVelocity = 0f
    internal var taps by mutableIntStateOf(0)

    internal fun target(): Float = if (playing.value) 1f else 0f

    internal fun animates(): Boolean = visible.value && wasVisible && !reduceMotion.value

    internal fun shownPhase(): Float = if (animates()) phase.value else target()

    fun onTap() { taps++ }
}

@Composable
internal fun rememberExpandedPlayPauseMotion(
    isPlaying: Boolean,
    active: Boolean,
    reduceMotion: Boolean,
): ExpandedPlayPauseMotion {
    val foreground = rememberIsForeground()
    val playingState = rememberUpdatedState(isPlaying)
    val visibleState = rememberUpdatedState(active && foreground)
    val reduceState = rememberUpdatedState(reduceMotion)
    val motion = remember { ExpandedPlayPauseMotion(playingState, visibleState, reduceState) }
    LaunchedEffect(isPlaying, active, foreground, reduceMotion) {
        val animate = isPlaying != motion.previousPlaying && motion.wasVisible &&
            active && foreground && !reduceMotion
        motion.previousPlaying = isPlaying
        motion.wasVisible = active && foreground
        if (animate) {
            motion.phase.animateTo(
                targetValue = motion.target(),
                animationSpec = spring(
                    dampingRatio = ExpandedPlayPauseSpec.Damping,
                    stiffness = ExpandedPlayPauseSpec.Stiffness,
                    visibilityThreshold = ExpandedPlayPauseSpec.Threshold,
                ),
                initialVelocity = motion.carriedVelocity,
            ) {
                motion.carriedVelocity = velocity
            }
        } else {
            // Initial/reopened/hidden/loading/reduced-motion states never replay
            // missed transitions. shownPhase also guards the pre-effect frame.
            motion.phase.snapTo(motion.target())
        }
        motion.carriedVelocity = 0f
    }
    return motion
}

/**
 * Original painters at every frame, including exact endpoints. Coordinated
 * scale/blur/opacity conceals replacement at reduced size, never two crisp
 * full-size icons. One spring phase makes a reversal continuous in both layers.
 * Native RenderEffect blur is ignored before Android 12; scale/opacity remain.
 */
@Composable
internal fun ExpandedPlayPauseGlyph(
    motion: ExpandedPlayPauseMotion,
    interactionSource: MutableInteractionSource,
    playPainter: Painter,
    pausePainter: Painter,
    contentDescription: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale = animateFloatAsState(
        targetValue = if (pressed && motion.animates()) ExpandedPlayPauseSpec.PressScale else 1f,
        animationSpec = tween(ExpandedPlayPauseSpec.PressMs, easing = LinearOutSlowInEasing),
        label = "expandedPlayPausePress",
    )
    Box(
        modifier = modifier
            .semantics { this.contentDescription = contentDescription }
            .graphicsLayer {
                val scale = if (motion.animates()) pressScale.value else 1f
                scaleX = scale
                scaleY = scale
            },
    ) {
        for (pause in listOf(false, true)) {
            Icon(
                painter = if (pause) pausePainter else playPainter,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        val p = motion.shownPhase()
                        val weight = if (pause) p else 1f - p
                        alpha = expandedSymbolAlpha(weight)
                        val radius = size.width * expandedSymbolBlur(weight)
                        // Blur the scaled silhouette, not the whole player or
                        // its backdrop. Decal prevents edge-colour smearing.
                        renderEffect = if (alpha > 0f && radius > 0f) {
                            BlurEffect(radius, radius, TileMode.Decal)
                        } else null
                    }
                    .graphicsLayer {
                        val p = motion.shownPhase()
                        val weight = if (pause) p else 1f - p
                        val scale = expandedSymbolScale(weight)
                        scaleX = scale
                        scaleY = scale
                    },
            )
        }
    }
}

@Composable
internal fun Modifier.expandedPlayPausePressCircle(
    motion: ExpandedPlayPauseMotion,
    interactionSource: MutableInteractionSource,
    tint: Color,
    glyphSize: Dp,
    diameter: Dp,
): Modifier = playerPressCircle(
    interactionSource = interactionSource,
    trigger = motion.taps,
    enabled = motion.visible.value,
    reduceMotion = motion.reduceMotion.value,
    tint = tint,
    glyphSize = glyphSize,
    diameter = diameter,
    minimumHoldMs = ExpandedPlayPauseSpec.CircleHoldMs,
    hasMotion = { false }, // External playback changes do not manufacture touch feedback.
    settled = { abs(motion.phase.value - motion.target()) <= ExpandedPlayPauseSpec.CircleRemaining },
)
