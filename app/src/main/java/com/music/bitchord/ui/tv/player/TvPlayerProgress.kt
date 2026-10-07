package com.music.bitchord.ui.tv.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.ui.tv.focus.onTvKeyEvent
import com.music.bitchord.ui.tv.theme.TvColors
import com.music.bitchord.ui.tv.theme.TvSFProDisplay

/**
 * Ultra-smooth draw-phase progressive seekbar in pure white.
 * Glides smoothly from left to right with linear frame interpolation, zero lag, and 120fps fluid response.
 */
@Composable
fun TvPlayerProgress(
    currentPositionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val trackHeight by animateDpAsState(
        targetValue = if (isFocused) 5.dp else 2.5.dp,
        animationSpec = tween(durationMillis = 150),
        label = "tvProgressHeight",
    )

    val targetFraction = if (durationMs > 0) {
        (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val smoothFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(durationMillis = 250, easing = LinearEasing),
        label = "smoothProgressFraction",
    )

    val activeColor = Color.White
    val bgColor = if (isFocused) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.18f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .focusable(interactionSource = interactionSource)
            .onTvKeyEvent(
                onLeft = {
                    val stepMs = 10_000L // 10s step
                    val targetMs = (currentPositionMs - stepMs).coerceAtLeast(0L)
                    onSeek(targetMs)
                    true
                },
                onRight = {
                    val stepMs = 10_000L // 10s step
                    val targetMs = (currentPositionMs + stepMs).coerceAtMost(durationMs)
                    onSeek(targetMs)
                    true
                },
            ),
    ) {
        // Draw-phase Pure White Track & Thumb Canvas
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp),
        ) {
            val h = trackHeight.toPx()
            val cy = size.height / 2f
            val top = cy - (h / 2f)
            val cornerRadius = CornerRadius(h / 2f, h / 2f)

            // 1. Background Track
            drawRoundRect(
                color = bgColor,
                topLeft = Offset(0f, top),
                size = Size(size.width, h),
                cornerRadius = cornerRadius,
            )

            // 2. Active Pure White Played Track
            val playedWidth = size.width * smoothFraction
            if (playedWidth > 0f) {
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(0f, top),
                    size = Size(playedWidth, h),
                    cornerRadius = cornerRadius,
                )
            }

            // 3. Focused Scrub Thumb with soft halo
            if (isFocused) {
                val thumbRadius = 6.dp.toPx()
                drawCircle(
                    color = Color.White.copy(alpha = 0.25f),
                    radius = thumbRadius * 1.5f,
                    center = Offset(playedWidth.coerceIn(thumbRadius, size.width - thumbRadius), cy),
                )
                drawCircle(
                    color = Color.White,
                    radius = thumbRadius,
                    center = Offset(playedWidth.coerceIn(thumbRadius, size.width - thumbRadius), cy),
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Time Labels (Elapsed on left, negative remaining on right 1:1 with Apple Music TV)
        val remainingMs = (durationMs - currentPositionMs).coerceAtLeast(0L)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDuration(currentPositionMs),
                fontSize = 13.sp,
                fontFamily = TvSFProDisplay,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.7f),
            )
            Text(
                text = if (durationMs > 0) "-${formatDuration(remainingMs)}" else "-0:00",
                fontSize = 13.sp,
                fontFamily = TvSFProDisplay,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "0:00"
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
