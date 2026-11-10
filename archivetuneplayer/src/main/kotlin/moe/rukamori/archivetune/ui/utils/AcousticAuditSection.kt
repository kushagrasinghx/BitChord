/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.utils

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalDownloadUtil
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.acoustics.AcousticAuditResult
import moe.rukamori.archivetune.acoustics.AcousticAuditSource
import moe.rukamori.archivetune.acoustics.AcousticVerdict
import moe.rukamori.archivetune.acoustics.SpectrogramGenerator
import moe.rukamori.archivetune.acoustics.release
import moe.rukamori.archivetune.acoustics.resolveAcousticAuditSource

private sealed interface AcousticAuditUiState {
    data object Idle : AcousticAuditUiState

    data class Running(
        val progress: Float,
    ) : AcousticAuditUiState

    data class Done(
        val result: AcousticAuditResult,
    ) : AcousticAuditUiState

    data object Unavailable : AcousticAuditUiState

    data object Failed : AcousticAuditUiState
}

private val AcousticVerdictTrueColor = Color(0xFF2E7D32)
private val AcousticVerdictUpscaledColor = Color(0xFFEF6C00)
private val AcousticVerdictFakeColor = Color(0xFFC62828)
private val AcousticSpectrogramBackdrop = Color(0xFF0A0A1E)

@Composable
fun AcousticAuditSection(
    videoId: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val downloadUtil = LocalDownloadUtil.current
    val scope = rememberCoroutineScope()
    var state by remember(videoId) { mutableStateOf<AcousticAuditUiState>(AcousticAuditUiState.Idle) }
    var source by remember(videoId) { mutableStateOf<AcousticAuditSource?>(null) }

    DisposableEffect(videoId) {
        onDispose {
            source?.release()
            source = null
        }
    }

    val runAudit: () -> Unit = runAudit@{
        if (state is AcousticAuditUiState.Running) {
            return@runAudit
        }
        state = AcousticAuditUiState.Running(0f)
        scope.launch {
            val resolved =
                source
                    ?: resolveAcousticAuditSource(context, videoId, downloadUtil)
                        .also { source = it }
            if (resolved == null) {
                state = AcousticAuditUiState.Unavailable
                return@launch
            }
            val result =
                SpectrogramGenerator.generate(context, resolved) { progress ->
                    state = AcousticAuditUiState.Running(progress)
                }
            state =
                if (result != null) {
                    AcousticAuditUiState.Done(result)
                } else {
                    AcousticAuditUiState.Failed
                }
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.acoustic_audit_title).uppercase(Locale.ROOT),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        when (val current = state) {
            is AcousticAuditUiState.Idle -> {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = runAudit)
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                modifier = Modifier.size(34.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(R.drawable.graphic_eq),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            Column(
                                verticalArrangement = Arrangement.spacedBy(1.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = stringResource(R.string.acoustic_audit_run),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = stringResource(R.string.acoustic_audit_run_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                painter = painterResource(R.drawable.arrow_forward),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            is AcousticAuditUiState.Running -> {
                AcousticAuditProgressCard(progress = current.progress)
            }

            is AcousticAuditUiState.Done -> {
                AcousticAuditResultCard(
                    result = current.result,
                    onRunAgain = runAudit,
                )
            }

            is AcousticAuditUiState.Unavailable -> {
                AcousticAuditMessageCard(
                    iconRes = R.drawable.solar_ruler,
                    message = stringResource(R.string.acoustic_unavailable),
                    actionLabel = stringResource(R.string.acoustic_try_again),
                    onAction = runAudit,
                )
            }

            is AcousticAuditUiState.Failed -> {
                AcousticAuditMessageCard(
                    iconRes = R.drawable.solar_danger_circle_linear,
                    message = stringResource(R.string.acoustic_failed),
                    actionLabel = stringResource(R.string.acoustic_try_again),
                    onAction = runAudit,
                )
            }
        }

        Text(
            text = stringResource(R.string.acoustic_disclaimer),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun AcousticAuditProgressCard(progress: Float) {
    val transition = rememberInfiniteTransition(label = "acousticScanSweep")
    val sweep by
        transition.animateFloat(
            initialValue = -0.4f,
            targetValue = 1.4f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(1400, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "acousticScanSweepX",
        )
    val sweepColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.graphic_eq),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.acoustic_audit_running),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            LinearProgressIndicator(
                progress = { progress.coerceIn(0.02f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(AcousticSpectrogramBackdrop)
                        .drawWithCache {
                            val sweepX = size.width * sweep
                            val brush =
                                Brush.horizontalGradient(
                                    0f to Color.Transparent,
                                    sweepX - 240f to Color.Transparent,
                                    sweepX to sweepColor,
                                    sweepX + 80f to Color.Transparent,
                                    1f to Color.Transparent,
                                )
                            onDrawWithContent {
                                drawContent()
                                drawRect(brush)
                            }
                        },
            )
        }
    }
}

@Composable
private fun AcousticAuditResultCard(
    result: AcousticAuditResult,
    onRunAgain: () -> Unit,
) {
    val verdictColor =
        when (result.verdict) {
            AcousticVerdict.TRUE_LOSSLESS -> AcousticVerdictTrueColor
            AcousticVerdict.UPSCALED_LOSSY -> AcousticVerdictUpscaledColor
            AcousticVerdict.SUSPECTED_FAKE -> AcousticVerdictFakeColor
        }

    val cutoffKHz = result.cutoffHz / 1000f
    val nyquistKHz = result.nyquistHz / 1000f

    val verdictHeadline =
        when (result.verdict) {
            AcousticVerdict.TRUE_LOSSLESS -> stringResource(R.string.acoustic_verdict_true_lossless)
            AcousticVerdict.UPSCALED_LOSSY -> stringResource(R.string.acoustic_verdict_upscaled)
            AcousticVerdict.SUSPECTED_FAKE -> stringResource(R.string.acoustic_verdict_fake)
        }
    val verdictDetail =
        when (result.verdict) {
            AcousticVerdict.TRUE_LOSSLESS ->
                stringResource(R.string.acoustic_verdict_true_lossless_detail, "%.1f".format(cutoffKHz))
            AcousticVerdict.UPSCALED_LOSSY ->
                stringResource(R.string.acoustic_verdict_upscaled_detail, "%.1f".format(cutoffKHz))
            AcousticVerdict.SUSPECTED_FAKE ->
                stringResource(R.string.acoustic_verdict_fake_detail, "%.1f".format(cutoffKHz))
        }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        AnimatedVisibility(
            visible = true,
            enter =
                fadeIn(tween(360, easing = FastOutSlowInEasing)) +
                    scaleIn(
                        animationSpec =
                            spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        initialScale = 0.72f,
                    ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(verdictColor.copy(alpha = 0.14f))
                        .border(
                            width = 0.75.dp,
                            color = verdictColor.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(18.dp),
                        ).padding(14.dp),
            ) {
                Icon(
                    painter =
                        painterResource(
                            when (result.verdict) {
                                AcousticVerdict.TRUE_LOSSLESS -> R.drawable.solar_check_circle_linear
                                AcousticVerdict.UPSCALED_LOSSY -> R.drawable.solar_danger_circle_linear
                                AcousticVerdict.SUSPECTED_FAKE -> R.drawable.solar_danger_circle_linear
                            },
                        ),
                    contentDescription = null,
                    tint = verdictColor,
                    modifier = Modifier.size(24.dp),
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = verdictHeadline,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = verdictColor,
                    )
                    Text(
                        text = verdictDetail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        Text(
            text =
                stringResource(
                    R.string.acoustic_cutoff_label,
                    "%.1f".format(cutoffKHz),
                    "%.1f".format(nyquistKHz),
                ),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        AcousticSpectrogramCanvas(
            result = result,
            verdictColor = verdictColor,
            cutoffKHz = cutoffKHz,
            nyquistKHz = nyquistKHz,
        )

        Text(
            text = stringResource(R.string.acoustic_run_again),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onRunAgain)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun AcousticSpectrogramCanvas(
    result: AcousticAuditResult,
    verdictColor: Color,
    cutoffKHz: Float,
    nyquistKHz: Float,
) {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(result) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(1200, easing = FastOutSlowInEasing))
    }
    val cutoffLineAlpha by
        animateFloatAsState(
            targetValue = if (reveal.value >= 0.995f) 1f else 0f,
            animationSpec = tween(420, easing = FastOutSlowInEasing),
            label = "acousticCutoffLineAlpha",
        )

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(AcousticSpectrogramBackdrop)
                .border(
                    width = 0.75.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(16.dp),
                ),
    ) {
        Image(
            bitmap = result.spectrogram.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier =
                Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .drawWithCache {
                        val progress = reveal.value
                        onDrawWithContent {
                            clipRect(0f, 0f, size.width * progress, size.height) {
                                this@onDrawWithContent.drawContent()
                            }
                            if (progress > 0f && progress < 1f) {
                                val edgeX = size.width * progress
                                drawLine(
                                    color = Color.White.copy(alpha = 0.75f),
                                    start = Offset(edgeX, 0f),
                                    end = Offset(edgeX, size.height),
                                    strokeWidth = 2.dp.toPx(),
                                )
                            }
                        }
                    },
        )

        val axisAlpha = (reveal.value * 1.6f).coerceIn(0f, 1f)
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            AcousticAxisRow(label = "%.0f kHz".format(nyquistKHz), alpha = axisAlpha)
            AcousticAxisRow(label = "%.0f kHz".format(nyquistKHz * 2 / 3), alpha = axisAlpha)
            AcousticAxisRow(label = "%.0f kHz".format(nyquistKHz / 3), alpha = axisAlpha)
            AcousticAxisRow(label = "0 kHz", alpha = axisAlpha)
        }

        if (cutoffLineAlpha > 0f && nyquistKHz > 0f && cutoffKHz > 0f) {
            val cutoffFraction = (cutoffKHz / nyquistKHz).coerceIn(0f, 1f)
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .drawWithCache {
                            val y = size.height * (1f - cutoffFraction)
                            onDrawWithContent {
                                drawLine(
                                    color = verdictColor.copy(alpha = 0.9f * cutoffLineAlpha),
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = 1.5.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
                                )
                            }
                        },
            )
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(y = ((1f - cutoffFraction) * 200).dp - 10.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(verdictColor.copy(alpha = 0.9f * cutoffLineAlpha))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "%.1f kHz".format(cutoffKHz),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun AcousticAxisRow(
    label: String,
    alpha: Float,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.5f * alpha),
            fontSize = 10.sp,
        )
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.15f * alpha)),
        )
    }
}

@Composable
private fun AcousticAuditMessageCard(
    iconRes: Int,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAction)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(34.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
