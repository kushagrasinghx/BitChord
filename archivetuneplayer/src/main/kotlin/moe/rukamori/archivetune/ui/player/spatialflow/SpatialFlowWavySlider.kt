/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player.spatialflow

import android.annotation.SuppressLint
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.runtime.getValue

private val WavePhaseStepNanos = 33_333_333L

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun WavyMusicSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    trackHeight: Dp = 4.dp,
    thumbRadius: Dp = 10.dp,
    activeTrackColor: Color = MaterialTheme.colorScheme.primary,
    inactiveTrackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    waveAmplitudeWhenPlaying: Dp = 2.5.dp,
    waveLength: Dp = 48.dp,
    waveAnimationDuration: Int = 2200,
    hideInactiveTrackPortion: Boolean = true,
    isPlaying: Boolean = true,
    thumbLineHeightWhenInteracting: Dp = 20.dp,
    isWaveEligible: Boolean = true,
    semanticsLabel: String? = null,
    semanticsProgressStep: Float = 0.01f,
) {
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isInteracting = isDragged || isPressed

    val thumbInteractionFraction by animateFloatAsState(
        targetValue = if (isInteracting) 1f else 0f,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "ThumbInteractionAnim",
    )

    val shouldShowWave = isWaveEligible && isPlaying && !isInteracting

    val animatedWaveAmplitude by animateDpAsState(
        targetValue = if (shouldShowWave) waveAmplitudeWhenPlaying else 0.dp,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "WaveAmplitudeAnim",
    )

    val phaseShiftState = remember { mutableFloatStateOf(0f) }
    val phaseShift = phaseShiftState.floatValue

    LaunchedEffect(shouldShowWave, waveAnimationDuration) {
        if (shouldShowWave && waveAnimationDuration > 0) {
            val fullRotation = (2 * PI).toFloat()
            var lastStepNanos = 0L
            while (shouldShowWave) {
                withFrameNanos { frameTimeNanos ->
                    if (lastStepNanos == 0L) {
                        lastStepNanos = frameTimeNanos
                    } else {
                        val elapsedNanos = frameTimeNanos - lastStepNanos
                        if (elapsedNanos >= WavePhaseStepNanos) {
                            val advance =
                                fullRotation * (elapsedNanos / 1_000_000f / waveAnimationDuration)
                            phaseShiftState.floatValue =
                                (phaseShiftState.floatValue + advance) % fullRotation
                            lastStepNanos = frameTimeNanos
                        }
                    }
                }
            }
        }
    }

    val trackHeightPx = with(LocalDensity.current) { trackHeight.toPx() }
    val thumbRadiusPx = with(LocalDensity.current) { thumbRadius.toPx() }
    val waveAmplitudePxInternal = with(LocalDensity.current) { animatedWaveAmplitude.toPx() }
    val waveLengthPx = with(LocalDensity.current) { waveLength.toPx() }
    val waveFrequency = if (waveLengthPx > 0f) ((2 * PI) / waveLengthPx).toFloat() else 0f

    val thumbLineHeightPxInternal = with(LocalDensity.current) { thumbLineHeightWhenInteracting.toPx() }
    val thumbGapPx = with(LocalDensity.current) { 4.dp.toPx() }

    val wavePath = remember { Path() }

    val sliderVisualHeight =
        remember(trackHeight, thumbRadius, thumbLineHeightWhenInteracting) {
            max(trackHeight * 2, max(thumbRadius * 2, thumbLineHeightWhenInteracting) + 8.dp)
        }

    val hapticFeedback = LocalHapticFeedback.current
    val clampedValue = value.coerceIn(valueRange.start, valueRange.endInclusive)
    val normalizedValue =
        if (valueRange.endInclusive == valueRange.start) {
            0f
        } else {
            ((clampedValue - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
        }

    val animatedNormalizedValue by animateFloatAsState(
        targetValue = normalizedValue,
        animationSpec =
            if (normalizedValue == 0f) {
                spring(
                    dampingRatio = 0.75f,
                    stiffness = 200f,
                )
            } else {
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = 380f,
                )
            },
        label = "ProgressValueAnim",
    )
    val renderNormalizedValue = if (isInteracting) normalizedValue else animatedNormalizedValue

    val safeSemanticsStep = semanticsProgressStep.coerceIn(0.005f, 0.25f)
    val semanticNormalizedValue =
        remember(normalizedValue, safeSemanticsStep) {
            ((normalizedValue / safeSemanticsStep).roundToInt() * safeSemanticsStep).coerceIn(0f, 1f)
        }
    val semanticSliderValue =
        remember(semanticNormalizedValue, valueRange) {
            valueRange.start + semanticNormalizedValue * (valueRange.endInclusive - valueRange.start)
        }

    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val lastHapticStep = remember { mutableIntStateOf(-1) }

        Slider(
            value = clampedValue,
            onValueChange = { newValue ->
                val normalizedNew =
                    if (valueRange.endInclusive == valueRange.start) {
                        0f
                    } else {
                        ((newValue - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
                    }

                val currentStep = (normalizedNew * 50f).roundToInt()
                if (currentStep != lastHapticStep.intValue) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    lastHapticStep.intValue = currentStep
                }
                onValueChange(newValue)
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(sliderVisualHeight)
                    .clearAndSetSemantics {
                        if (!semanticsLabel.isNullOrBlank()) {
                            contentDescription = semanticsLabel
                        }
                        progressBarRangeInfo =
                            ProgressBarRangeInfo(
                                current = semanticSliderValue,
                                range = valueRange.start..valueRange.endInclusive,
                                steps = 0,
                            )
                        if (enabled) {
                            setProgress { requested ->
                                val coerced = requested.coerceIn(valueRange.start, valueRange.endInclusive)
                                onValueChange(coerced)
                                onValueChangeFinished?.invoke()
                                true
                            }
                        }
                    },
            enabled = enabled,
            valueRange = valueRange,
            onValueChangeFinished = onValueChangeFinished,
            interactionSource = interactionSource,
            colors =
                SliderDefaults.colors(
                    thumbColor = Color.Transparent,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                ),
        )

        Spacer(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(sliderVisualHeight)
                    .drawWithCache {
                        val canvasWidth = size.width
                        val localCenterY = size.height / 2f
                        val localTrackStart = thumbRadiusPx
                        val localTrackEnd = canvasWidth - thumbRadiusPx
                        val localTrackWidth = (localTrackEnd - localTrackStart).coerceAtLeast(0f)
                        val taperDistancePx = 64.dp.toPx()

                        onDrawWithContent {
                            val currentProgressPxEndVisual = localTrackStart + localTrackWidth * renderNormalizedValue

                            if (hideInactiveTrackPortion) {
                                if (currentProgressPxEndVisual < localTrackEnd) {
                                    drawLine(
                                        color = inactiveTrackColor,
                                        start = Offset(currentProgressPxEndVisual, localCenterY),
                                        end = Offset(localTrackEnd, localCenterY),
                                        strokeWidth = trackHeightPx,
                                        cap = StrokeCap.Round,
                                    )
                                }
                            } else {
                                drawLine(
                                    color = inactiveTrackColor,
                                    start = Offset(localTrackStart, localCenterY),
                                    end = Offset(localTrackEnd, localCenterY),
                                    strokeWidth = trackHeightPx,
                                    cap = StrokeCap.Round,
                                )
                            }

                            if (renderNormalizedValue > 0f) {
                                val activeTrackVisualEnd = currentProgressPxEndVisual - (thumbGapPx * thumbInteractionFraction)

                                val activeGradient =
                                    Brush.linearGradient(
                                        colors =
                                            listOf(
                                                activeTrackColor.copy(alpha = 0.35f),
                                                activeTrackColor,
                                            ),
                                        start = Offset(localTrackStart, localCenterY),
                                        end = Offset(activeTrackVisualEnd.coerceAtLeast(localTrackStart), localCenterY),
                                    )

                                if (waveAmplitudePxInternal > 0.01f && waveFrequency > 0f) {
                                    wavePath.reset()
                                    val waveStartDrawX = localTrackStart
                                    val waveEndDrawX = activeTrackVisualEnd.coerceAtLeast(waveStartDrawX)
                                    if (waveEndDrawX > waveStartDrawX) {
                                        val periodPx = ((2 * PI) / waveFrequency).toFloat()
                                        val samplesPerCycle = 20f
                                        val waveStep =
                                            (periodPx / samplesPerCycle)
                                                .coerceAtLeast(1.2f)
                                                .coerceAtMost(trackHeightPx)

                                        fun yAt(x: Float): Float {
                                            val s = sin(waveFrequency * x + phaseShift)
                                            val breath = 1f

                                            val taper = ((x - waveStartDrawX) / taperDistancePx).coerceIn(0f, 1f)
                                            val finalAmplitude = waveAmplitudePxInternal * breath * taper

                                            return (localCenterY + finalAmplitude * s)
                                                .coerceIn(
                                                    localCenterY - finalAmplitude - trackHeightPx / 2f,
                                                    localCenterY + finalAmplitude + trackHeightPx / 2f,
                                                )
                                        }

                                        var prevX = waveStartDrawX
                                        var prevY = yAt(prevX)
                                        wavePath.moveTo(prevX, prevY)

                                        var x = prevX + waveStep
                                        while (x < waveEndDrawX) {
                                            val y = yAt(x)
                                            val mX = (prevX + x) * 0.5f
                                            val mY = (prevY + y) * 0.5f
                                            wavePath.quadraticTo(prevX, prevY, mX, mY)
                                            prevX = x
                                            prevY = y
                                            x += waveStep
                                        }
                                        val endY = yAt(waveEndDrawX)
                                        wavePath.quadraticTo(prevX, prevY, waveEndDrawX, endY)

                                        drawPath(
                                            path = wavePath,
                                            brush = activeGradient,
                                            style =
                                                Stroke(
                                                    width = trackHeightPx,
                                                    cap = StrokeCap.Round,
                                                    join = StrokeJoin.Round,
                                                    miter = 1f,
                                                ),
                                        )
                                    }
                                } else {
                                    if (activeTrackVisualEnd > localTrackStart) {
                                        drawLine(
                                            brush = activeGradient,
                                            start = Offset(localTrackStart, localCenterY),
                                            end = Offset(activeTrackVisualEnd, localCenterY),
                                            strokeWidth = trackHeightPx,
                                            cap = StrokeCap.Round,
                                        )
                                    }
                                }
                            }

                            val currentThumbCenterX = localTrackStart + localTrackWidth * renderNormalizedValue

                            fun fastLerp(
                                start: Float,
                                stop: Float,
                                fraction: Float,
                            ): Float = start + (stop - start) * fraction

                            val thumbCurrentWidthPx = fastLerp(thumbRadiusPx * 2f, trackHeightPx * 1.2f, thumbInteractionFraction)
                            val thumbCurrentHeightPx = fastLerp(thumbRadiusPx * 2f, thumbLineHeightPxInternal, thumbInteractionFraction)

                            drawRoundRect(
                                color = thumbColor,
                                topLeft =
                                    Offset(
                                        currentThumbCenterX - thumbCurrentWidthPx / 2f,
                                        localCenterY - thumbCurrentHeightPx / 2f,
                                    ),
                                size = Size(thumbCurrentWidthPx, thumbCurrentHeightPx),
                                cornerRadius = CornerRadius(thumbCurrentWidthPx / 2f),
                            )
                        }
                    },
        )
    }
}
