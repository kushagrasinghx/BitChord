package com.music.bitchord.ui.tv.components

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.music.bitchord.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.music.bitchord.ui.tv.focus.onTvKeyEvent
import com.music.bitchord.ui.tv.focus.tvButtonFocus
import com.music.bitchord.ui.tv.focus.tvCardFocus
import com.music.bitchord.ui.tv.theme.AppleSpringPreset
import com.music.bitchord.ui.tv.theme.TvColors
import com.music.bitchord.ui.tv.theme.TvSFProDisplay
import com.music.bitchord.ui.tv.theme.TvThemeColors
import com.music.bitchord.ui.tv.theme.appleSpring

private val cardColorCache = androidx.collection.LruCache<String, Color>(200)

@Composable
fun rememberDominantCardColor(artworkUrl: String?, defaultColor: Color = Color(0xFF22222C)): Color {
    val context = LocalContext.current
    var dominantColor by remember(artworkUrl) {
        mutableStateOf(artworkUrl?.let { cardColorCache.get(it) } ?: defaultColor)
    }

    LaunchedEffect(artworkUrl) {
        if (artworkUrl.isNullOrBlank()) {
            dominantColor = defaultColor
            return@LaunchedEffect
        }
        val cached = cardColorCache.get(artworkUrl)
        if (cached != null) {
            dominantColor = cached
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            try {
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(artworkUrl)
                    .size(32, 32)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = result.image.toBitmap()
                    val extracted = sampleDominantDarkColor(bitmap)
                    cardColorCache.put(artworkUrl, extracted)
                    withContext(Dispatchers.Main) {
                        dominantColor = extracted
                    }
                }
            } catch (e: Exception) {
                // Fallback to default
            }
        }
    }
    return dominantColor
}

private fun sampleDominantDarkColor(bitmap: Bitmap): Color {
    var rSum = 0L
    var gSum = 0L
    var bSum = 0L
    var count = 0L
    val width = bitmap.width
    val height = bitmap.height

    for (x in 0 until width step 2) {
        for (y in 0 until height step 2) {
            val pixel = bitmap.getPixel(x, y)
            val a = (pixel shr 24) and 0xff
            if (a > 100) {
                rSum += (pixel shr 16) and 0xff
                gSum += (pixel shr 8) and 0xff
                bSum += pixel and 0xff
                count++
            }
        }
    }

    if (count == 0L) return Color(0xFF22222C)

    val rAvg = (rSum / count).toInt()
    val gAvg = (gSum / count).toInt()
    val bAvg = (bSum / count).toInt()

    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(rAvg, gAvg, bAvg, hsv)
    hsv[1] = (hsv[1] * 1.3f).coerceIn(0.40f, 0.95f) // Rich vibrant saturation
    hsv[2] = 0.22f // Deep luxury dark value for pure white text readability

    val finalColorInt = android.graphics.Color.HSVToColor(hsv)
    return Color(finalColorInt)
}

/**
 * 1:1 Apple Music TV Content Card matching Image 6.
 * Features a category label above the card, full-width artwork on top, and an integrated
 * dynamic artwork-tinted text block at the bottom with title and subtitle.
 */
@Composable
fun TvCard(
    title: String,
    subtitle: String?,
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 168.dp,
    categoryLabel: String? = null,
    aspectRatio: Float = 1.0f,
    isCircle: Boolean = false,
    isPlaying: Boolean = false,
    badge: String? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape: Shape = if (isCircle) CircleShape else RoundedCornerShape(16.dp)
    val palette = TvThemeColors.current

    val dominantBg = rememberDominantCardColor(artworkUrl, palette.surfaceVariant)
    val textBgColor by animateColorAsState(
        targetValue = dominantBg,
        label = "cardDynamicTextBg",
    )

    Column(
        modifier = modifier.then(
            if (cardWidth != Dp.Unspecified) Modifier.width(cardWidth) else Modifier.fillMaxWidth()
        ),
    ) {
        // Small category label above card
        if (!categoryLabel.isNullOrBlank()) {
            Text(
                text = categoryLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = TvSFProDisplay,
                color = if (isFocused) palette.textPrimary else palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
        }

        // Main Unified Card Container
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .tvCardFocus(
                    shape = shape,
                    focusedScale = 1.06f,
                    onClick = onClick,
                )
                .background(textBgColor),
        ) {
            // Artwork Portion
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
                    .clip(if (isCircle) CircleShape else RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(palette.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (!artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(artworkUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }

                // Apple Music badge watermark in top right of card (if not circle)
                if (!isCircle) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_logo),
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }

                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(palette.accentRed),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Playing",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                if (badge != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(TvColors.ScrimDark)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = badge,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = TvSFProDisplay,
                        )
                    }
                }
            }

            // Dynamic Artwork-Tinted Bottom Text Container
            if (!isCircle) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(textBgColor)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = TvSFProDisplay,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (!subtitle.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            color = Color.White.copy(alpha = 0.70f),
                            fontSize = 12.sp,
                            fontFamily = TvSFProDisplay,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Standard TV focusable button with Apple Spring animation.
 */
@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isPrimary: Boolean = false,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val palette = TvThemeColors.current

    val targetScale = when {
        isPressed -> 0.95f
        isFocused -> 1.05f
        else -> 1.0f
    }
    val buttonScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = appleSpring(AppleSpringPreset.Snappy),
        label = "tvButtonScale",
    )

    val bgColor by animateColorAsState(
        targetValue = when {
            !enabled -> TvColors.SurfaceVariant.copy(alpha = 0.5f)
            isFocused -> if (isPrimary) Color.White else TvColors.SurfaceFocused
            isPrimary -> if (palette.isDark) Color.White else Color(0xFF1C1C1E)
            else -> TvColors.SurfaceVariant
        },
        animationSpec = appleSpring(AppleSpringPreset.Snappy),
        label = "tvButtonBg",
    )

    val contentColor = when {
        !enabled -> TvColors.TextMuted
        isPrimary && isFocused -> Color.Black
        isPrimary -> if (palette.isDark) Color.Black else Color.White
        isFocused -> if (palette.isDark) Color.White else Color.Black
        else -> Color.White
    }

    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = buttonScale
                scaleY = buttonScale
            }
            .tvButtonFocus(
                shape = RoundedCornerShape(12.dp),
                focusedScale = 1.0f,
                onClick = if (enabled) onClick else null,
            )
            .background(bgColor)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            color = contentColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.W600,
            fontFamily = TvSFProDisplay,
        )
    }
}

/**
 * Focusable TV icon button with Apple Spring animation.
 */
@Composable
fun TvIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    isPrimary: Boolean = false,
    isActive: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val palette = TvThemeColors.current

    val targetScale = when {
        isPressed -> 0.94f
        isFocused -> 1.08f
        else -> 1.0f
    }
    val buttonScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = appleSpring(AppleSpringPreset.Snappy),
        label = "tvIconBtnScale",
    )

    val bgColor by animateColorAsState(
        targetValue = when {
            isFocused -> if (isPrimary) Color.White else TvColors.SurfaceFocused
            isActive -> Color.White.copy(alpha = 0.25f)
            isPrimary -> if (palette.isDark) Color.White else Color(0xFF1C1C1E)
            else -> TvColors.SurfaceVariant
        },
        animationSpec = appleSpring(AppleSpringPreset.Snappy),
        label = "tvIconBtnBg",
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = buttonScale
                scaleY = buttonScale
            }
            .tvButtonFocus(
                shape = CircleShape,
                focusedScale = 1.0f,
                onClick = onClick,
            )
            .background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = when {
                isFocused -> if (isPrimary) Color.Black else if (palette.isDark) Color.White else Color.Black
                isActive -> Color.White
                isPrimary -> if (palette.isDark) Color.Black else Color.White
                else -> palette.textPrimary
            },
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * TV D-pad Remote Seek / Value Slider.
 */
@Composable
fun TvSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    step: Float = 0.05f,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start))
        .coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .focusable(interactionSource = interactionSource)
            .onTvKeyEvent(
                onLeft = {
                    val newVal = (value - step).coerceIn(valueRange.start, valueRange.endInclusive)
                    onValueChange(newVal)
                    true
                },
                onRight = {
                    val newVal = (value + step).coerceIn(valueRange.start, valueRange.endInclusive)
                    onValueChange(newVal)
                    true
                },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        // Track Background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isFocused) 8.dp else 4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(TvColors.SurfaceVariant)
        )

        // Active Track
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(if (isFocused) 8.dp else 4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (isFocused) TvColors.AccentRed else TvColors.TextPrimary)
        )

        // Thumb Indicator (visible on focus)
        if (isFocused) {
            val palette = TvThemeColors.current
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .padding(end = 0.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (palette.isDark) Color.White else palette.accentRed)
                )
            }
        }
    }
}

/**
 * Apple-style Container Card for settings and group sections.
 * Clean rounded 20dp surface with subtle border.
 */
@Composable
fun TvAppleCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    shape: Shape = RoundedCornerShape(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = TvThemeColors.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.surface)
            .border(1.dp, palette.borderSubtle, shape)
            .padding(20.dp),
    ) {
        if (!title.isNullOrBlank()) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = TvSFProDisplay,
                color = palette.textPrimary,
            )
            Spacer(modifier = Modifier.height(14.dp))
        }
        content()
    }
}

/**
 * TV Shelf Section Header.
 */
@Composable
fun TvSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = title,
                fontSize = 22.sp,
                fontWeight = FontWeight.W700,
                fontFamily = TvSFProDisplay,
                color = TvThemeColors.current.textPrimary,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    fontFamily = TvSFProDisplay,
                    color = TvThemeColors.current.textSecondary,
                )
            }
        }

        if (actionLabel != null && onActionClick != null) {
            TvButton(
                text = actionLabel,
                onClick = onActionClick,
                modifier = Modifier.height(36.dp),
            )
        }
    }
}

/**
 * TV Loading Shimmer Skeletons for shelves.
 */
@Composable
fun TvShelfSkeleton(
    modifier: Modifier = Modifier,
    itemCount: Int = 6,
    cardWidth: Dp = 160.dp,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        repeat(itemCount) {
            Column(modifier = Modifier.width(cardWidth)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(TvColors.SurfaceVariant)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(TvColors.SurfaceVariant)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(TvColors.SurfaceVariant)
                )
            }
        }
    }
}

/**
 * TV Recoverable Error View with focusable Retry button.
 */
@Composable
fun TvErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Something went wrong",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = TvSFProDisplay,
            color = TvThemeColors.current.textPrimary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            fontSize = 14.sp,
            fontFamily = TvSFProDisplay,
            color = TvThemeColors.current.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.6f),
        )
        Spacer(modifier = Modifier.height(20.dp))
        TvButton(
            text = "Retry",
            icon = Icons.Default.Refresh,
            isPrimary = true,
            onClick = onRetry,
        )
    }
}

/**
 * TV Empty State View.
 */
@Composable
fun TvEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = TvSFProDisplay,
            color = TvThemeColors.current.textPrimary,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = message,
            fontSize = 14.sp,
            fontFamily = TvSFProDisplay,
            color = TvThemeColors.current.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * TV Modal Dialog Container with focus trap, back press handling, and Apple spring entrance animation.
 */
@Composable
fun TvDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    val animatedScale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.88f,
        animationSpec = appleSpring(AppleSpringPreset.Snappy),
        label = "dialogScale",
    )
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(180),
        label = "dialogAlpha",
    )

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = modifier
                    .fillMaxWidth(0.55f)
                    .graphicsLayer {
                        scaleX = animatedScale
                        scaleY = animatedScale
                        alpha = animatedAlpha
                    }
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        if (TvThemeColors.current.isDark) Color(0xF5121218)
                        else Color(0xF7F7F7FA)
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.35f),
                                Color.White.copy(alpha = 0.08f),
                            )
                        ),
                        shape = RoundedCornerShape(24.dp),
                    )
                    .padding(28.dp),
            ) {
                Text(
                    text = title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = TvSFProDisplay,
                    color = TvThemeColors.current.textPrimary,
                )
                Spacer(modifier = Modifier.height(16.dp))
                content()
            }
        }
    }
}
