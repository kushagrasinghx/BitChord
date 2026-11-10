/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player.looper

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.sin
import moe.rukamori.archivetune.utils.makeTimeString

private val LooperTrackHeight = 8.dp
private val LooperTrackGap = 6.dp
private val LooperThumbWidth = 6.dp
private val LooperThumbHeight = 16.dp

@Composable
internal fun LooperExpressiveSlider(
    position: Long,
    duration: Long,
    isPlaying: Boolean,
    scrubPosition: Long?,
    accentColor: Color,
    onSeek: (Long) -> Unit,
    onSeekFinished: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val safeDuration = if (duration > 0) duration else 1L
    val displayPosition = (scrubPosition ?: position).coerceIn(0L, safeDuration)
    val progress = (displayPosition.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)

    val enableWave = isPlaying && progress > 0.12f
    val waveBlend by animateFloatAsState(
        targetValue = if (enableWave) 1f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "LooperSquiggle",
    )

    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    var wavePhase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying) {
        var lastNanos = 0L
        while (isPlaying) {
            withFrameNanos { frameNanos ->
                if (lastNanos != 0L) {
                    val advance = (frameNanos - lastNanos) / 1_000_000_000f * 0.05f
                    wavePhase = (wavePhase + advance) % 1f
                }
                lastNanos = frameNanos
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(LooperThumbHeight)
                    .onSizeChanged { trackWidthPx = it.width.toFloat() }
                    .pointerInput(safeDuration, trackWidthPx) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            fun fractionAt(x: Float): Float =
                                if (trackWidthPx <= 0f) 0f else
                                    (x / trackWidthPx).coerceIn(0f, 1f)
                            var lastX = down.position.x
                            onSeek((fractionAt(lastX) * safeDuration).toLong())
                            while (true) {
                                val event = awaitPointerEvent()
                                val change =
                                    event.changes.firstOrNull { it.id == down.id } ?: break
                                lastX = change.position.x
                                if (!change.pressed) break
                                change.consume()
                                onSeek((fractionAt(lastX) * safeDuration).toLong())
                            }
                            onSeekFinished((fractionAt(lastX) * safeDuration).toLong())
                        }
                    },
        ) {
            val width = size.width
            val height = size.height
            val centerY = height / 2f
            val trackH = LooperTrackHeight.toPx()
            val gap = LooperTrackGap.toPx()
            val amplitude = 2.dp.toPx()
            val wavelength = 6.dp.toPx()
            val thumbW = LooperThumbWidth.toPx()
            val thumbH = LooperThumbHeight.toPx()
            val thumbX = progress * width
            val corner = CornerRadius(trackH / 2f, trackH / 2f)

            val inactiveStart = (thumbX + gap).coerceAtMost(width)
            if (inactiveStart < width) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.10f),
                    topLeft = Offset(inactiveStart, centerY - trackH / 2f),
                    size = androidx.compose.ui.geometry.Size(width - inactiveStart, trackH),
                    cornerRadius = corner,
                )
            }

            val activeEnd = (thumbX - gap).coerceAtLeast(0f)
            if (activeEnd > 0f) {
                if (waveBlend <= 0.01f) {
                    drawRoundRect(
                        color = accentColor,
                        topLeft = Offset(0f, centerY - trackH / 2f),
                        size = androidx.compose.ui.geometry.Size(activeEnd, trackH),
                        cornerRadius = corner,
                    )
                } else {
                    val easeLength = wavelength * 3f
                    val path = Path()
                    var x = 0f
                    while (x <= activeEnd) {
                        val ease =
                            when {
                                x < easeLength -> x / easeLength
                                x > activeEnd - easeLength ->
                                    (activeEnd - x) / easeLength
                                else -> 1f
                            }
                        val y =
                            centerY +
                                (sin(x / wavelength + wavePhase * 2f * Math.PI.toFloat()) * amplitude) *
                                    ease *
                                    waveBlend
                        if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                        x += 1f
                    }
                    drawPath(
                        path = path,
                        color = accentColor,
                        style = Stroke(width = trackH, cap = StrokeCap.Round),
                    )
                }
            }

            drawRoundRect(
                color = Color.White,
                topLeft = Offset(thumbX - thumbW / 2f, centerY - thumbH / 2f),
                size = androidx.compose.ui.geometry.Size(thumbW, thumbH),
                cornerRadius = CornerRadius(thumbW / 2f, thumbW / 2f),
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = makeTimeString(displayPosition),
                style = LooperTypography.labelMedium,
                color = Color.White,
            )
            Text(
                text = String.format(Locale.ROOT, "-%s", makeTimeString(safeDuration - displayPosition)),
                style = LooperTypography.labelMedium,
                color = Color.White,
            )
        }
    }
}
