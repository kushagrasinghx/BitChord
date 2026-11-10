/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player.spatialflow

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size as CoilSize
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.db.entities.codecLabel
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.player.rememberOfflineArtworkImageRequest
import moe.rukamori.archivetune.utils.ImageBlurUtils
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.basicMarqueeWithFadedEdges(
    edgeWidth: Dp = 12.dp,
): Modifier =
    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            val edgeWidthPx = edgeWidth.toPx()

            val leftBrush =
                Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                    startX = 0f,
                    endX = edgeWidthPx,
                )
            val rightBrush =
                Brush.horizontalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startX = size.width - edgeWidthPx,
                    endX = size.width,
                )

            onDrawWithContent {
                drawContent()
                drawRect(brush = leftBrush, blendMode = BlendMode.DstIn)
                drawRect(brush = rightBrush, blendMode = BlendMode.DstIn)
            }
        }.basicMarquee()
        .padding(horizontal = edgeWidth)

internal fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

internal fun deriveArtworkSurfaceColor(
    sourceColor: Color,
    isDark: Boolean,
    darkLightness: Float,
    lightLightness: Float,
    darkSaturationRange: ClosedFloatingPointRange<Float>,
    lightSaturationRange: ClosedFloatingPointRange<Float>,
    monochromeSaturationThreshold: Float = 0.06f,
): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(sourceColor.toArgb(), hsl)
    val isMonochrome = hsl[1] < monochromeSaturationThreshold
    hsl[2] = if (isDark) darkLightness else lightLightness
    hsl[1] =
        if (isMonochrome) {
            0f
        } else if (isDark) {
            hsl[1].coerceIn(darkSaturationRange.start, darkSaturationRange.endInclusive)
        } else {
            hsl[1].coerceIn(lightSaturationRange.start, lightSaturationRange.endInclusive)
        }
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}

@Composable
internal fun SpatialFlowBlurredBackdrop(
    artUrl: String?,
    withScrim: Boolean = true,
    isDark: Boolean = isSystemInDarkTheme(),
    modifier: Modifier = Modifier,
) {
    val isPreS = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
    val context = LocalContext.current
    val imageLoader = context.imageLoader

    val preBlurredBitmap by produceState<Bitmap?>(null, artUrl) {
        if (!isPreS || artUrl.isNullOrBlank()) {
            value = null
            return@produceState
        }
        value =
            withContext(Dispatchers.IO) {
                runCatching {
                    val request =
                        ImageRequest.Builder(context)
                            .data(artUrl)
                            .allowHardware(false)
                            .memoryCacheKey("$artUrl#sfbackdrop")
                            .diskCacheKey("$artUrl#sfbackdrop")
                            .size(CoilSize(320, 320))
                            .build()
                    val result = imageLoader.execute(request)
                    if (result is SuccessResult) {
                        val bitmap =
                            result.image.toBitmap()
                                .copy(Bitmap.Config.ARGB_8888, true)
                        val density = context.resources.displayMetrics.density
                        ImageBlurUtils.blur(bitmap, 36f * density)
                    } else {
                        null
                    }
                }.getOrNull()
            }
    }

    Box(modifier = modifier) {
        if (artUrl != null) {
            if (isPreS) {
                preBlurredBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                val request = rememberOfflineArtworkImageRequest(artUrl)
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .blur(72.dp),
                )
            }
        }

        if (withScrim) {
            val scrimBrush =
                if (isDark) {
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.18f),
                        0.55f to Color.Black.copy(alpha = 0.32f),
                        1f to Color.Black.copy(alpha = 0.60f),
                    )
                } else {
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.30f),
                        0.55f to Color.White.copy(alpha = 0.55f),
                        1f to Color.White.copy(alpha = 0.82f),
                    )
                }
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(scrimBrush),
            )
        }
    }
}

@Composable
internal fun SplitLikeDislikeChip(
    isLiked: Boolean,
    isDisliked: Boolean,
    likesCount: String,
    onLikeClick: () -> Unit,
    onDislikeClick: () -> Unit,
    contentColor: Color,
    accentColor: Color,
    isDark: Boolean,
) {
    val backgroundColor = contentColor.copy(alpha = if (isDark) 0.08f else 0.06f)
    val displayLikesText =
        remember(likesCount) {
            likesCount.ifBlank { "Like" }
        }

    Row(
        modifier =
            Modifier
                .height(36.dp)
                .clip(CircleShape)
                .background(backgroundColor),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp))
                    .clickable(onClick = onLikeClick)
                    .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                painter =
                    painterResource(
                        id = if (isLiked) R.drawable.spatialflow_ic_thumbup else R.drawable.spatialflow_ic_outline_thumbup,
                    ),
                contentDescription = "Like",
                tint = if (isLiked) accentColor else contentColor.copy(alpha = 0.8f),
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = displayLikesText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = if (isLiked) accentColor else contentColor.copy(alpha = 0.8f),
            )
        }

        Spacer(
            modifier =
                Modifier
                    .width(1.dp)
                    .fillMaxHeight(0.5f)
                    .background(contentColor.copy(alpha = 0.15f)),
        )

        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp))
                    .clickable(onClick = onDislikeClick)
                    .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter =
                    painterResource(
                        id = if (isDisliked) R.drawable.spatialflow_ic_thumbdown else R.drawable.spatialflow_ic_outline_thumbdown,
                    ),
                contentDescription = "Dislike",
                tint = if (isDisliked) accentColor else contentColor.copy(alpha = 0.8f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
internal fun PillChip(
    icon: Any,
    label: String,
    onClick: () -> Unit,
    contentColor: Color,
    accentColor: Color,
    isDark: Boolean,
    isSelected: Boolean = false,
    progress: Float? = null,
    modifier: Modifier = Modifier,
) {
    val backgroundColor =
        if (isSelected) {
            accentColor.copy(alpha = if (isDark) 0.25f else 0.18f)
        } else {
            contentColor.copy(alpha = if (isDark) 0.08f else 0.06f)
        }

    val tintColor = contentColor.copy(alpha = 0.8f)
    val progressColor = accentColor.copy(alpha = if (isDark) 0.35f else 0.25f)

    val animatedFill by animateFloatAsState(
        targetValue = progress ?: 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 150f),
        label = "DownloadChipProgress",
    )

    Box(
        modifier =
            modifier
                .height(36.dp)
                .clip(CircleShape)
                .background(backgroundColor)
                .drawBehind {
                    if (progress != null && progress > 0f) {
                        drawRect(
                            color = progressColor,
                            size = size.copy(width = size.width * animatedFill),
                        )
                    }
                }.clickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when (icon) {
                is ImageVector ->
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = tintColor,
                        modifier = Modifier.size(18.dp),
                    )

                is Painter ->
                    Icon(
                        painter = icon,
                        contentDescription = label,
                        tint = tintColor,
                        modifier = Modifier.size(18.dp),
                    )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = tintColor,
            )
        }
    }
}

@Composable
internal fun WavySliderWithLabels(
    currentPositionProvider: () -> Long,
    duration: Long,
    isPlaying: Boolean,
    onSeekTo: (Long) -> Unit,
    dynamicAccentColor: Color,
    contentColor: Color,
    contentSecondary: Color,
    isDark: Boolean,
    currentFormat: FormatEntity? = null,
    modifier: Modifier = Modifier,
) {
    var isScrubbing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var sliderScrubPos by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }

    val currentPosition = currentPositionProvider()
    val safeDur = if (duration > 0) duration.toFloat() else 1f
    val displayPos = if (isScrubbing) (sliderScrubPos * safeDur).toLong() else currentPosition
    val progressRatio = (currentPosition.toFloat() / safeDur).coerceIn(0f, 1f)

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
    ) {
        WavyMusicSlider(
            value = if (isScrubbing) sliderScrubPos else progressRatio,
            onValueChange = {
                isScrubbing = true
                sliderScrubPos = it
            },
            onValueChangeFinished = {
                isScrubbing = false
                onSeekTo((sliderScrubPos * safeDur).toLong())
            },
            activeTrackColor = dynamicAccentColor,
            inactiveTrackColor = contentColor.copy(alpha = if (isDark) 0.08f else 0.06f),
            thumbColor = contentColor,
            isPlaying = isPlaying,
            trackHeight = 4.dp,
            thumbRadius = 6.dp,
            waveAmplitudeWhenPlaying = 6.dp,
            waveLength = 48.dp,
        )

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
        ) {
            Text(
                text = formatDuration(displayPos),
                style = MaterialTheme.typography.labelSmall,
                color = contentSecondary,
                modifier = Modifier.align(Alignment.CenterStart),
            )

            if (currentFormat != null) {
                val label =
                    remember(currentFormat.mimeType, currentFormat.codecs) {
                        currentFormat.codecLabel()
                    }
                Row(
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(6.dp))
                            .background(contentColor.copy(alpha = if (isDark) 0.10f else 0.08f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.spatialflow_ic_music_note),
                        contentDescription = null,
                        tint = contentSecondary,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = contentSecondary,
                    )
                }
            }

            Text(
                text = formatDuration(duration),
                style = MaterialTheme.typography.labelSmall,
                color = contentSecondary,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
internal fun LyricsMetadataFooter(
    currentSong: MediaMetadata?,
    selectedProvider: String?,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    if (currentSong == null) return

    val mutedColor = contentColor.copy(alpha = 0.35f)
    val metaStyle =
        MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.3.sp,
            color = mutedColor,
        )

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (currentSong.title.isNotBlank()) {
            Text(text = currentSong.title, style = metaStyle, maxLines = 1)
        }

        if (currentSong.artists.isNotEmpty() &&
            !currentSong.artists.joinToString { it.name }.equals("Unknown Artist", ignoreCase = true)
        ) {
            Text(text = currentSong.artists.joinToString { it.name }, style = metaStyle, maxLines = 1)
        }

        if (!selectedProvider.isNullOrBlank()) {
            Text(
                text = "Lyrics by $selectedProvider",
                style = metaStyle,
                maxLines = 1,
            )
        }
    }
}
