/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ThumbnailCornerRadius
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue

@Composable
fun PlayingIndicator(
    color: Color,
    modifier: Modifier = Modifier,
    bars: Int = 3,
    barWidth: Dp = 4.dp,
    cornerRadius: Dp = ThumbnailCornerRadius,
) {
    val transition = rememberInfiniteTransition(label = "playingIndicator")
    val cycleDurationMs = 1100

    val barStates: List<State<Float>> =
        (0 until bars.coerceAtLeast(1)).map { index ->

            val phaseStep = cycleDurationMs / (bars.coerceAtLeast(1) + 1)
            val delayMs = (bars - index - 1).coerceAtLeast(0) * phaseStep
            transition.animateFloat(
                initialValue = 0.1f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = cycleDurationMs

                        0.1f at 0
                        1.0f at 360
                        0.3f at 660
                        0.1f at cycleDurationMs
                    },
                    repeatMode = RepeatMode.Restart,
                    initialStartOffset = StartOffset(delayMs),
                ),
                label = "bar$index",
            )
        }

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = modifier,
    ) {
        barStates.forEachIndexed { index, barState ->
            Canvas(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .width(barWidth),
            ) {
                val value = barState.value
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x = 0f, y = size.height * (1 - value)),
                    size = size.copy(height = value * size.height),
                    cornerRadius = CornerRadius(cornerRadius.toPx()),
                )
            }
        }
    }
}

@Composable
fun PlayingIndicatorBox(
    modifier: Modifier = Modifier,
    isActive: Boolean,
    playWhenReady: Boolean,
    color: Color = LocalContentColor.current,
) {
    AnimatedVisibility(
        visible = isActive,
        enter = fadeIn(tween(500)),
        exit = fadeOut(tween(500)),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier,
        ) {
            if (playWhenReady) {
                PlayingIndicator(
                    color = color,
                    modifier = Modifier.height(24.dp),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.play),
                    contentDescription = null,
                    tint = color,
                )
            }
        }
    }
}
