/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player.tiktok

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import moe.rukamori.archivetune.utils.makeTimeString
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

internal val TIKTOK_PROGRESS_ROW_HEIGHT = 44.dp

@Composable
internal fun TikTokBottomChrome(
    displayPositionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(

        modifier = modifier.fillMaxWidth().navigationBarsPadding(),
    ) {
        TikTokProgressRow(
            positionMs = displayPositionMs,
            durationMs = durationMs,
            onSeek = onSeek,
            onSeekFinished = onSeekFinished,
        )
    }
}

@Composable
internal fun TikTokProgressRow(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val seekEnabled = durationMs > 0L && durationMs != C.TIME_UNSET

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .height(TIKTOK_PROGRESS_ROW_HEIGHT)
                .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = makeTimeString(positionMs),
            color = TIKTOK_INACTIVE_GRAY,
            fontSize = 12.sp,
            maxLines = 1,
        )
        Spacer(Modifier.width(10.dp))
        TikTokProgressSlider(
            positionMs = positionMs,
            durationMs = durationMs,
            enabled = seekEnabled,
            onSeek = onSeek,
            onSeekFinished = onSeekFinished,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = if (seekEnabled) makeTimeString(durationMs) else "—:—",
            color = TIKTOK_INACTIVE_GRAY,
            fontSize = 12.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun TikTokProgressSlider(
    positionMs: Long,
    durationMs: Long,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(0f) }
    val fraction =
        if (durationMs > 0L && durationMs != C.TIME_UNSET) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    val displayFraction = if (dragging) dragFraction else fraction

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .height(TIKTOK_SLIDER_TOUCH_HEIGHT)
                .let { m ->
                    if (enabled) {
                        m.pointerInput(durationMs) {
                            detectHorizontalDragGestures(
                                onDragStart = { _ ->
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    dragging = true
                                    dragFraction = fraction
                                },
                                onHorizontalDrag = { change, _ ->
                                    change.consume()
                                    dragFraction =
                                        (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                    onSeek((dragFraction * durationMs).toLong())
                                },
                                onDragEnd = {
                                    dragging = false
                                    onSeekFinished()
                                },
                                onDragCancel = {
                                    dragging = false
                                    onSeekFinished()
                                },
                            )
                        }.pointerInput(durationMs) {
                            detectTapGestures(
                                onTap = { offset ->
                                    dragFraction =
                                        (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                    onSeek((dragFraction * durationMs).toLong())
                                    onSeekFinished()
                                },
                            )
                        }
                    } else {
                        m
                    }
                },
    ) {
        Canvas(
            modifier =
                Modifier
                    .weight(1f)
                    .height(TIKTOK_SLIDER_IDLE),
        ) {
            val barHeight = TIKTOK_SLIDER_IDLE.toPx()
            val centerY = size.height / 2f
            val trackWidth = size.width
            val corner = CornerRadius(barHeight / 2f, barHeight / 2f)

            drawRoundRect(
                color = Color.White.copy(alpha = 0.28f),
                topLeft = Offset(0f, centerY - barHeight / 2f),
                size = Size(trackWidth, barHeight),
                cornerRadius = corner,
            )

            val playedWidth = trackWidth * displayFraction
            if (playedWidth > 0f) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.95f),
                    topLeft = Offset(0f, centerY - barHeight / 2f),
                    size = Size(playedWidth.coerceAtLeast(barHeight), barHeight),
                    cornerRadius = corner,
                )
            }

            if (dragging) {
                drawCircle(
                    color = Color.White,
                    radius = 6.dp.toPx(),
                    center = Offset(playedWidth, centerY),
                )
            }
        }
    }
}

private val TIKTOK_SLIDER_IDLE = 3.dp
private val TIKTOK_SLIDER_TOUCH_HEIGHT = 30.dp
