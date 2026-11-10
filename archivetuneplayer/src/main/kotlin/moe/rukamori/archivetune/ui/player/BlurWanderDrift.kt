/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

internal class BlurWanderDrift(
    private val random: Random = Random.Default,
    private val maxDriftDp: Float = DefaultWanderRadiusDp,
    private val speedDpPerSecond: Float = WanderSpeedDpPerSecond,
    private val minLegDurationMs: Float = MinLegDurationMs,
    private val maxLegDurationMs: Float = MaxLegDurationMs,
) {
    private val xState = mutableFloatStateOf(0f)
    private val yState = mutableFloatStateOf(0f)
    private val rotationState = mutableFloatStateOf(0f)

    val xDp: FloatState get() = xState

    val yDp: FloatState get() = yState

    val rotationDeg: FloatState get() = rotationState

    private var fromX = 0f
    private var fromY = 0f
    private var toX = 0f
    private var toY = 0f
    private var fromRotation = 0f
    private var toRotation = 0f
    private var legDurationMs = 0f
    private var legElapsedMs = 0f

    init {
        startNextLeg()
    }

    fun advance(deltaMs: Float) {
        if (deltaMs <= 0f) return
        legElapsedMs += deltaMs
        while (legElapsedMs >= legDurationMs) {
            legElapsedMs -= legDurationMs
            startNextLeg()
        }

        val t = legElapsedMs / legDurationMs
        val eased = 0.5f - 0.5f * cos(PI.toFloat() * t)
        xState.floatValue = fromX + (toX - fromX) * eased
        yState.floatValue = fromY + (toY - fromY) * eased
        rotationState.floatValue = fromRotation + (toRotation - fromRotation) * eased
    }

    private fun startNextLeg() {
        fromX = toX
        fromY = toY
        fromRotation = toRotation

        val radius = maxDriftDp * sqrt(random.nextFloat())
        val angle = random.nextFloat() * TwoPi
        toX = cos(angle) * radius
        toY = sin(angle) * radius

        val rotationSign = if (random.nextBoolean()) 1f else -1f
        val rotationSpan =
            MinLegRotationDegrees + random.nextFloat() * (MaxLegRotationDegrees - MinLegRotationDegrees)
        toRotation = fromRotation + rotationSign * rotationSpan

        val distance = hypot(toX - fromX, toY - fromY)
        legDurationMs =
            (distance / speedDpPerSecond * 1000f)
                .coerceIn(minLegDurationMs, maxLegDurationMs)
    }

    internal companion object {
        const val DefaultWanderRadiusDp = 120f

        const val WanderSpeedDpPerSecond = 26f

        const val MinLegDurationMs = 6_000f

        const val MaxLegDurationMs = 26_000f

        private const val MinLegRotationDegrees = 18f

        private const val MaxLegRotationDegrees = 55f

        private const val TwoPi = (2.0 * PI).toFloat()
    }
}

internal fun movingBlurWanderMaxDriftDp(
    width: Dp,
    height: Dp,
): Float {
    val w = width.value
    val h = height.value
    if (w <= 0f || h <= 0f) return BlurWanderDrift.DefaultWanderRadiusDp
    return (hypot(w, h) / 2f) * 0.85f
}

internal fun blurBackdropFootprint(
    width: Dp,
    height: Dp,
    restScale: Float,
    driftScale: Float,
    maxDriftDp: Float = BlurWanderDrift.DefaultWanderRadiusDp,
): DpSize {
    val w = width.value
    val h = height.value
    if (w <= 0f || h <= 0f || restScale <= 0f || driftScale <= 0f) return DpSize(width, height)

    val corner = hypot(w, h) / 2f

    val requiredAtRest = 2f * corner / restScale
    val requiredAtFullDrift = 2f * (corner + maxDriftDp) / driftScale
    val required = max(requiredAtRest, requiredAtFullDrift) * BlurBackdropCoverSafety

    return DpSize(max(w, required).dp, max(h, required).dp)
}

private const val BlurBackdropCoverSafety = 1.02f

internal fun blurBackdropFootprintLandscape(
    width: Dp,
    height: Dp,
    restScale: Float,
    maxDriftDp: Float,
    driftFactor: Float,
    blurEdgeMarginDp: Dp,
): DpSize {
    val w = width.value
    val h = height.value
    if (w <= 0f || h <= 0f || restScale <= 0f) return DpSize(width, height)
    val drift = maxDriftDp * driftFactor
    val margin = drift + blurEdgeMarginDp.value
    val requiredW = (w + 2f * margin) / restScale * BlurBackdropCoverSafety
    val requiredH = (h + 2f * margin) / restScale * BlurBackdropCoverSafety
    return DpSize(requiredW.dp, requiredH.dp)
}

@Composable
internal fun rememberBlurWanderDrift(
    active: Boolean,
    maxDriftDp: Float = BlurWanderDrift.DefaultWanderRadiusDp,
    speedDpPerSecond: Float = BlurWanderDrift.WanderSpeedDpPerSecond,
    minLegDurationMs: Float = BlurWanderDrift.MinLegDurationMs,
    maxLegDurationMs: Float = BlurWanderDrift.MaxLegDurationMs,
): BlurWanderDrift {
    val drift =
        remember(maxDriftDp, speedDpPerSecond, minLegDurationMs, maxLegDurationMs) {
            BlurWanderDrift(
                maxDriftDp = maxDriftDp,
                speedDpPerSecond = speedDpPerSecond,
                minLegDurationMs = minLegDurationMs,
                maxLegDurationMs = maxLegDurationMs,
            )
        }

    LaunchedEffect(active, maxDriftDp, speedDpPerSecond) {
        if (!active) return@LaunchedEffect
        var lastFrameNanos = 0L
        var unappliedMs = 0f
        while (isActive) {
            withFrameNanos { frameTimeNanos ->
                if (lastFrameNanos != 0L) {
                    val deltaMs = (frameTimeNanos - lastFrameNanos) / 1_000_000f
                    unappliedMs += deltaMs
                    if (unappliedMs >= DriftUpdateIntervalMs) {
                        drift.advance(unappliedMs)
                        unappliedMs = 0f
                    }

                }
                lastFrameNanos = frameTimeNanos
            }
        }
    }
    return drift
}

private const val DriftUpdateIntervalMs = 0f
