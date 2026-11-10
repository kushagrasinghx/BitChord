/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.FormatEntity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun LosslessOrStats(
    isLoading: Boolean,
    format: FormatEntity?,
    modifier: Modifier = Modifier,
) {
    val lossless = format?.isLossless() == true
    val hiRes = lossless && (format?.sampleRate ?: 0) >= 88_200
    val hiQuality = !lossless && (format?.bitrate ?: 0) >= 250_000
    when {
        format == null || (isLoading && !lossless) -> LosslessLabel(
            text = "Upgrading Quality",
            animated = false,
            iconRes = R.drawable.ic_lossless_wave,
            modifier = modifier,
        )
        lossless -> LosslessLabel(

            text = if (hiRes) "Hi-Res Lossless" else "Lossless",

            animated = true,

            iconRes = R.drawable.ic_lossless_wave,
            modifier = modifier,
        )

        hiQuality -> LosslessLabel(
            text = "Hi-Quality",
            animated = false,
            iconRes = R.drawable.ic_lossless_wave,
            modifier = modifier,
        )
        else -> {}
    }
}

private fun FormatEntity.isLossless(): Boolean =
    mimeType.endsWith("flac") || mimeType.endsWith("alac")

@Composable
private fun LosslessLabel(
    text: String,
    animated: Boolean,
    iconRes: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(4.dp))
        if (animated) {
            ShimmerText(text)
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = Color.White.copy(alpha = 0.55f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (MaterialTheme.typography.labelMedium.fontSize.value + 1).sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ShimmerText(text: String) {
    var widthPx by remember { mutableIntStateOf(0) }
    val transition = rememberInfiniteTransition(label = "lossless-shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "lossless-shimmer-progress",
    )
    val baseColor = Color.White.copy(alpha = 0.55f)
    val brush = if (widthPx <= 0) {
        Brush.linearGradient(listOf(baseColor, baseColor))
    } else {
        val band = widthPx * 0.6f
        val center = -band + progress * (widthPx + 2 * band)
        Brush.linearGradient(
            colorStops = arrayOf(0f to baseColor, 0.5f to Color.White, 1f to baseColor),
            start = Offset(center - band, 0f),
            end = Offset(center + band, 0f),
        )
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(
            brush = brush,
            fontWeight = FontWeight.SemiBold,
            fontSize = (MaterialTheme.typography.labelMedium.fontSize.value + 1).sp,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.onSizeChanged { widthPx = it.width },
    )
}
