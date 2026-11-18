package com.music.bitchord.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.unit.Dp
import com.music.bitchord.ui.rememberIsForeground
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

/** Geometry precision, not Apple animation parameters. No optical recentering. */
private object MorphGeometry {
    // Dense enough to resolve the ~1-unit rounded corners at ~0.03 viewport
    // units per sample (well under 0.2px at mini-player size) before each
    // outer chain is resampled to OuterChainSamples. 4096 was 4x the work.
    const val OutlineSamples = 1024
    const val OuterChainSamples = 256
    const val SplitFraction = 0.5f
    // Facing straight edges, verified in custom pause.xml and Material 1.7.3
    // rounded/Pause.kt: y280..680 / 960, or y7..17 / 24 respectively.
    const val FaceTop = 7f / 24f
    const val FaceBottom = 17f / 24f
}

/** Pulse numbers are APPROX fits; the footage does not isolate scale from blur. */
private object PlayPauseMotionSpec {
    const val Threshold = 0.0001f
    // Same spring as NowPlayingScreen's artScale; artwork itself is untouched.
    const val MorphDamping = Spring.DampingRatioLowBouncy
    const val MorphStiffness = Spring.StiffnessLow
    // The clip pinches almost to a point, not merely to 80% size. Its tiny,
    // blurred frames cannot establish an exact minimum; 0.12 is an APPROX fit.
    const val DipScale = 0.12f
    const val DipStiffness = 2500f
    const val DipDamping = 1f
    const val DipReturnThreshold = 0.03f
    // response ~0.35s; extra damping keeps this deeper pinch's peak near 1.05.
    const val ReturnStiffness = 322f
    const val ReturnDamping = 0.68f
    const val CircleRemaining = 0.20f
    const val CircleHoldMs = 85
}

class PlayerPlayPauseMotion internal constructor(
    internal val playing: State<Boolean>,
    internal val visible: State<Boolean>,
    internal val reduceMotion: State<Boolean>,
) {
    internal val morph = Animatable(if (playing.value) 1f else 0f,
        visibilityThreshold = PlayPauseMotionSpec.Threshold)
    internal val pulse = Animatable(1f, visibilityThreshold = PlayPauseMotionSpec.Threshold)
    internal var previousPlaying = playing.value
    internal var wasVisible = visible.value
    internal var morphVelocity = 0f
    internal var pulseVelocity = 0f
    internal var morphStart = morph.value
    internal var taps by mutableIntStateOf(0)

    fun onTap() { taps++ }
}

/** No local playback Boolean: both target and semantics come from the caller. */
@Composable
fun rememberPlayerPlayPauseMotion(
    isPlaying: Boolean,
    active: Boolean,
    reduceMotion: Boolean,
): PlayerPlayPauseMotion {
    val foreground = rememberIsForeground()
    val playingState = rememberUpdatedState(isPlaying)
    val visibleState = rememberUpdatedState(active && foreground)
    val reduceState = rememberUpdatedState(reduceMotion)
    val motion = remember { PlayerPlayPauseMotion(playingState, visibleState, reduceState) }
    LaunchedEffect(isPlaying, active, foreground, reduceMotion) {
        val changed = isPlaying != motion.previousPlaying
        val animate = changed && motion.wasVisible && active && foreground && !reduceMotion
        motion.previousPlaying = isPlaying
        motion.wasVisible = active && foreground
        val target = if (isPlaying) 1f else 0f
        if (!animate) {
            motion.morph.snapTo(target)
            motion.pulse.snapTo(1f)
            motion.morphVelocity = 0f
            motion.pulseVelocity = 0f
            motion.morphStart = target
        } else {
            motion.morphStart = motion.morph.value
            coroutineScope {
                launch {
                    motion.morph.animateTo(target,
                        spring(PlayPauseMotionSpec.MorphDamping, PlayPauseMotionSpec.MorphStiffness,
                            visibilityThreshold = PlayPauseMotionSpec.Threshold),
                        initialVelocity = motion.morphVelocity) {
                        motion.morphVelocity = velocity
                    }
                    motion.morphVelocity = 0f
                }
                launch {
                    // A second animateTo captures velocity BEFORE Animatable
                    // cancels the dip. Never snap the scale, even on rapid reversal.
                    launch {
                        motion.pulse.animateTo(PlayPauseMotionSpec.DipScale,
                            spring(PlayPauseMotionSpec.DipDamping, PlayPauseMotionSpec.DipStiffness,
                                visibilityThreshold = PlayPauseMotionSpec.Threshold),
                            initialVelocity = motion.pulseVelocity) {
                            motion.pulseVelocity = velocity
                        }
                    }
                    snapshotFlow {
                        abs(motion.pulse.value - PlayPauseMotionSpec.DipScale) <=
                            PlayPauseMotionSpec.DipReturnThreshold
                    }.first { it }
                    motion.pulse.animateTo(1f,
                        spring(PlayPauseMotionSpec.ReturnDamping, PlayPauseMotionSpec.ReturnStiffness,
                            visibilityThreshold = PlayPauseMotionSpec.Threshold),
                        initialVelocity = motion.pulseVelocity) {
                        motion.pulseVelocity = velocity
                    }
                    motion.pulseVelocity = 0f
                }
            }
        }
    }
    return motion
}

/** Original painter at exact endpoints; one compound, genuinely morphing fill between them. */
@Composable
fun PlayerPlayPauseGlyph(
    motion: PlayerPlayPauseMotion,
    play: ImageVector,
    pause: ImageVector,
    playPainter: Painter,
    pausePainter: Painter,
    contentDescription: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    // Resource vectors may be empty until loaded. Do not cache that placeholder
    // as geometry, or replace today's loading/empty-vector behavior with a crash.
    val geometry = remember(play, pause) {
        if (play.root.size == 0 || pause.root.size == 0) null else morphContours(play, pause)
    }
    Icon(
        painter = if (motion.playing.value) pausePainter else playPainter,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.drawWithCache {
            val path = Path()
            onDrawWithContent {
                val visible = motion.visible.value && motion.wasVisible && !motion.reduceMotion.value
                val p = if (visible) motion.morph.value.coerceIn(0f, 1f) else {
                    if (motion.playing.value) 1f else 0f
                }
                val pulse = if (visible) motion.pulse.value else 1f
                scale(pulse) {
                    if (geometry == null) {
                        this@onDrawWithContent.drawContent()
                    } else if (p == 0f || p == 1f) {
                        if ((p == 1f) == motion.playing.value) {
                            this@onDrawWithContent.drawContent()
                        } else {
                            // During reversal, an endpoint can still be the
                            // outgoing state. Never draw the target icon there.
                            with(if (p == 0f) playPainter else pausePainter) {
                                draw(size = size, colorFilter = ColorFilter.tint(tint))
                            }
                        }
                    } else {
                        path.rewind()
                        for (contour in geometry) {
                            val from = contour.first
                            val to = contour.second
                            var i = 0
                            while (i < from.size) {
                                val x = from[i] + (to[i] - from[i]) * p
                                val y = from[i + 1] + (to[i + 1] - from[i + 1]) * p
                                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                i += 2
                            }
                            path.close()
                        }
                        scale(size.width / play.viewportWidth, size.height / play.viewportHeight,
                            pivot = Offset.Zero) { drawPath(path, tint) }
                    }
                }
            }
        },
    )
}

@Composable
internal fun Modifier.playerPlayPausePressCircle(
    motion: PlayerPlayPauseMotion,
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
    minimumHoldMs = PlayPauseMotionSpec.CircleHoldMs,
    hasMotion = { false }, // External playback changes never synthesize a press.
    settled = {
        val target = if (motion.playing.value) 1f else 0f
        abs(motion.morph.value - target) <=
            abs(motion.morphStart - target) * PlayPauseMotionSpec.CircleRemaining
    },
)

/**
 * The contours depend only on the two vectors, which every caller passes as the
 * same Material singletons. Sampling them is thousands of PathMeasure calls, so
 * build them once per process instead of once per MiniPlayer/glass-bar instance
 * (each of which re-enters composition as the bar density changes).
 */
private val morphContourCache =
    java.util.concurrent.ConcurrentHashMap<Pair<ImageVector, ImageVector>, List<Pair<FloatArray, FloatArray>>>()

private fun morphContours(play: ImageVector, pause: ImageVector): List<Pair<FloatArray, FloatArray>> =
    morphContourCache.getOrPut(Pair(play, pause)) { buildMorphContours(play, pause) }

/**
 * Anchor the cut's endpoints, not an arbitrary full-perimeter starting vertex.
 * Left outer chain is bottom -> top; right is top -> bottom. Closing each chain
 * makes the inner edge open linearly into the original Pause gap, without a twist.
 */
private fun buildMorphContours(play: ImageVector, pause: ImageVector): List<Pair<FloatArray, FloatArray>> {
    val triangle = sampleOutline((play.root[0] as VectorPath).pathData.toPath())
    var left = triangle[0].x
    var right = left
    for (point in triangle) { left = minOf(left, point.x); right = maxOf(right, point.x) }
    val cut = left + (right - left) * MorphGeometry.SplitFraction
    val pauseNodes = (pause.root[0] as VectorPath).pathData
    val split = pauseNodes.indexOfLast { it is PathNode.MoveTo }
    val bars = listOf(sampleOutline(pauseNodes.subList(0, split).toPath()),
        sampleOutline(pauseNodes.subList(split, pauseNodes.size).toPath()))
    val result = ArrayList<Pair<FloatArray, FloatArray>>(2)
    for (side in 0..1) {
        val half = clipAtCut(triangle, cut, side == 0)
        var top = half[0]
        var bottom = half[0]
        for (point in half) {
            if (point.x == cut) {
                if (top.x != cut || point.y < top.y) top = point
                if (bottom.x != cut || point.y > bottom.y) bottom = point
            }
        }
        val bar = bars[side]
        var faceX = bar[0].x
        for (point in bar) faceX = if (side == 0) maxOf(faceX, point.x) else minOf(faceX, point.x)
        val faceTop = Offset(faceX, pause.viewportHeight * MorphGeometry.FaceTop)
        val faceBottom = Offset(faceX, pause.viewportHeight * MorphGeometry.FaceBottom)
        val from = if (side == 0) outerChain(half, bottom, top) else outerChain(half, top, bottom)
        val to = if (side == 0) outerChain(bar, faceBottom, faceTop) else outerChain(bar, faceTop, faceBottom)
        result.add(Pair(from, to))
    }
    return result
}

private fun sampleOutline(path: Path): List<Offset> {
    val measure = PathMeasure()
    measure.setPath(path, forceClosed = true)
    val length = measure.length
    val points = List(MorphGeometry.OutlineSamples) {
        measure.getPosition(length * it / MorphGeometry.OutlineSamples)
    }
    var area = 0f
    for (i in points.indices) {
        val a = points[i]
        val b = points[(i + 1) % points.size]
        area += a.x * b.y - a.y * b.x
    }
    // Material Pause has opposite winding to the custom player vectors.
    return if (area < 0f) points.asReversed() else points
}

private fun clipAtCut(points: List<Offset>, cut: Float, left: Boolean): List<Offset> {
    val result = ArrayList<Offset>()
    for (i in points.indices) {
        val a = points[i]
        val b = points[(i + 1) % points.size]
        val aInside = if (left) a.x <= cut else a.x >= cut
        val bInside = if (left) b.x <= cut else b.x >= cut
        if (aInside) result.add(a)
        if (aInside != bInside) {
            val t = (cut - a.x) / (b.x - a.x)
            result.add(Offset(cut, a.y + (b.y - a.y) * t))
        }
    }
    return result
}

private fun outerChain(points: List<Offset>, start: Offset, end: Offset): FloatArray {
    fun nearest(target: Offset): Int {
        var best = 0
        var distance = Float.POSITIVE_INFINITY
        for (i in points.indices) {
            val dx = points[i].x - target.x
            val dy = points[i].y - target.y
            val candidate = dx * dx + dy * dy
            if (candidate < distance) { distance = candidate; best = i }
        }
        return best
    }
    val chain = ArrayList<Offset>()
    chain.add(start)
    var i = nearest(start)
    val last = nearest(end)
    while (i != last) { chain.add(points[i]); i = (i + 1) % points.size }
    chain.add(end)
    val distances = FloatArray(chain.size)
    for (j in 1 until chain.size) {
        val dx = chain[j].x - chain[j - 1].x
        val dy = chain[j].y - chain[j - 1].y
        distances[j] = distances[j - 1] + sqrt(dx * dx + dy * dy)
    }
    val result = FloatArray(MorphGeometry.OuterChainSamples * 2)
    var segment = 1
    for (j in 0 until MorphGeometry.OuterChainSamples) {
        val d = distances[distances.size - 1] * j / (MorphGeometry.OuterChainSamples - 1)
        while (segment < distances.size - 1 && distances[segment] < d) segment++
        val span = distances[segment] - distances[segment - 1]
        val t = if (span > 0f) (d - distances[segment - 1]) / span else 0f
        result[2 * j] = chain[segment - 1].x + (chain[segment].x - chain[segment - 1].x) * t
        result[2 * j + 1] = chain[segment - 1].y + (chain[segment].y - chain[segment - 1].y) * t
    }
    return result
}
