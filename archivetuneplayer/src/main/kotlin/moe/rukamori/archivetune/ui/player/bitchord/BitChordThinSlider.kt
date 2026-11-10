/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player.bitchord

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ThinSlider(

    valueProvider: () -> Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,

    mixing: Boolean = false,

    transitionWindow: ClosedFloatingPointRange<Float>? = null,
    idleHeight: Dp = 7.dp,
    activeHeight: Dp = 12.dp,
    activeColor: Color = Color.White.copy(alpha = 0.92f),
    inactiveColor: Color = Color.White.copy(alpha = 0.26f),

    markerColor: Color = Color.White.copy(alpha = 0.5f),
) {
    var dragging by remember { mutableStateOf(false) }

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)

    val height by animateDpAsState(
        targetValue = if (dragging) activeHeight else idleHeight,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "sliderHeight",
    )

    val fillFactor by animateFloatAsState(
        targetValue = if (mixing) 0f else 1f,
        animationSpec = tween(durationMillis = MORPH_MS, easing = FastOutSlowInEasing),
        label = "fillFactor",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()

            .height(activeHeight + 22.dp)

            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    dragging = true
                    currentOnValueChange((down.position.x / size.width).coerceIn(0f, 1f))

                    while (true) {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!pointer.pressed) {
                            pointer.consume()
                            break
                        }
                        if (pointer.positionChanged()) {
                            currentOnValueChange((pointer.position.x / size.width).coerceIn(0f, 1f))
                            pointer.consume()
                        }
                    }

                    dragging = false
                    currentOnValueChangeFinished?.invoke()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height),
        ) {
            val radius = CornerRadius(size.height / 2f)
            drawRoundRect(color = inactiveColor, cornerRadius = radius)

            transitionWindow?.let { window ->
                val from = size.width * window.start.coerceIn(0f, 1f)
                val to = size.width * window.endInclusive.coerceIn(0f, 1f)
                if (to > from) {
                    drawRoundRect(
                        color = markerColor,
                        topLeft = Offset(from, 0f),
                        size = Size(to - from, size.height),
                        cornerRadius = radius,
                    )
                }
            }
            val filled = size.width * valueProvider().coerceIn(0f, 1f) * fillFactor
            if (filled > 0f) {
                drawRoundRect(
                    color = activeColor,
                    size = Size(
                        filled.coerceAtLeast(size.height * fillFactor).coerceAtMost(size.width),
                        size.height,
                    ),
                    cornerRadius = radius,
                )
            }
        }

        AnimatedVisibility(
            visible = mixing,
            enter = fadeIn(tween(durationMillis = MORPH_MS, easing = FastOutSlowInEasing)) +
                scaleIn(
                    animationSpec = tween(durationMillis = MORPH_MS, easing = FastOutSlowInEasing),
                    initialScale = 0f,
                    transformOrigin = TransformOrigin(0f, 0.5f),
                ),
            exit = fadeOut(tween(durationMillis = MORPH_MS, easing = FastOutSlowInEasing)),
        ) {
            MixSheen(height = height, color = activeColor)
        }
    }
}

@Composable
private fun MixSheen(height: Dp, color: Color) {
    val transition = rememberInfiniteTransition(label = "mixSheen")
    val phase by transition.animateFloat(
        initialValue = -SHEEN_BAND_FRACTION,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(

            animation = tween(durationMillis = SHEEN_PERIOD_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "mixSheenPhase",
    )
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val band = size.width * SHEEN_BAND_FRACTION
        val x = phase * size.width
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    0.5f to color,
                    1f to Color.Transparent,
                ),
                startX = x,
                endX = x + band,
            ),
            size = Size(size.width, size.height),
            cornerRadius = CornerRadius(size.height / 2f),
        )
    }
}

private const val SHEEN_BAND_FRACTION = 0.34f
private const val SHEEN_PERIOD_MS = 1000
private const val MORPH_MS = 450
