package com.music.bitchord.ui.tv.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Sleek, ultra-thin pure white loading progress bar pinned to the bottom of the screen.
 *
 * Minimalist Apple TV aesthetic:
 * - 2.5dp razor-thin height.
 * - Glowing pure white sweeping wave.
 * - Zero text, zero clutter, 100% fluid.
 */
@Composable
fun TvBottomLoadingBar(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(250)),
        exit = fadeOut(tween(250)),
        modifier = modifier.fillMaxWidth(),
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "loadingBarSweep")
        val progress by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1100, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "loadingSweepProgress",
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.5.dp)
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(2.5.dp)) {
                val width = size.width
                val barLength = width * 0.35f
                val startX = (width + barLength) * progress - barLength
                val endX = startX + barLength

                val sweepBrush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.0f),
                        Color.White.copy(alpha = 0.95f),
                        Color.White,
                        Color.White.copy(alpha = 0.95f),
                        Color.White.copy(alpha = 0.0f),
                    ),
                    start = Offset(startX, 0f),
                    end = Offset(endX, 0f),
                )

                drawLine(
                    brush = sweepBrush,
                    start = Offset(startX.coerceAtLeast(0f), size.height / 2f),
                    end = Offset(endX.coerceAtMost(width), size.height / 2f),
                    strokeWidth = size.height,
                )
            }
        }
    }
}
