package com.music.bitchord.ui.tv.components

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.ui.tv.theme.TvGlass
import com.music.bitchord.ui.tv.theme.TvType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ─────────────────────────────────────────────────────────────────────────────
// Artwork colour
// ─────────────────────────────────────────────────────────────────────────────

private val cardColorCache = androidx.collection.LruCache<String, Color>(300)

/**
 * A deep, saturated colour taken from the artwork — the footer of a Top Picks
 * card and the tint of the canvas behind everything. Decodes a 24px copy through
 * the app's shared loader, so the disk cache already holding the cover serves it.
 */
@Composable
fun rememberDominantCardColor(artworkUrl: String?, defaultColor: Color = Color(0xFF2C2D31)): Color {
    val context = LocalContext.current
    var color by remember(artworkUrl) {
        mutableStateOf(artworkUrl?.let { cardColorCache.get(it) } ?: defaultColor)
    }
    LaunchedEffect(artworkUrl) {
        if (artworkUrl.isNullOrBlank()) {
            color = defaultColor
            return@LaunchedEffect
        }
        cardColorCache.get(artworkUrl)?.let {
            color = it
            return@LaunchedEffect
        }
        val extracted = withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(artworkUrl.artworkAt(120) ?: artworkUrl)
                    .size(24, 24)
                    .allowHardware(false)
                    .build()
                val result = SingletonImageLoader.get(context).execute(request)
                (result as? SuccessResult)?.image?.toBitmap()?.let(::sampleDominantColor)
            }.getOrNull()
        } ?: return@LaunchedEffect
        cardColorCache.put(artworkUrl, extracted)
        color = extracted
    }
    return color
}

private fun sampleDominantColor(bitmap: Bitmap): Color {
    var r = 0L
    var g = 0L
    var b = 0L
    var weight = 0L
    for (x in 0 until bitmap.width) {
        for (y in 0 until bitmap.height) {
            val pixel = bitmap.getPixel(x, y)
            if ((pixel ushr 24) < 100) continue
            val pr = (pixel shr 16) and 0xff
            val pg = (pixel shr 8) and 0xff
            val pb = pixel and 0xff
            // Colourful pixels count for more than greys, so a cover with a
            // splash of colour on white doesn't average out to beige.
            val chroma = maxOf(pr, pg, pb) - minOf(pr, pg, pb)
            val w = 1L + chroma / 16
            r += pr * w
            g += pg * w
            b += pb * w
            weight += w
        }
    }
    if (weight == 0L) return Color(0xFF2C2D31)
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV((r / weight).toInt(), (g / weight).toInt(), (b / weight).toInt(), hsv)
    hsv[1] = (hsv[1] * 1.3f).coerceIn(0.30f, 0.80f)
    hsv[2] = 0.38f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

// ─────────────────────────────────────────────────────────────────────────────
// Cards
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The Apple Music "Top Picks" card: artwork over a footer tinted from it, title
 * and subtitle centred in the footer, an optional caption above the card.
 */
@Composable
fun TvCard(
    title: String,
    subtitle: String?,
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 200.dp,
    categoryLabel: String? = null,
    aspectRatio: Float = 1.0f,
    isCircle: Boolean = false,
    isPlaying: Boolean = false,
    badge: String? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    if (isCircle) {
        TvLockup(
            title = title,
            subtitle = subtitle,
            artworkUrl = artworkUrl,
            onClick = onClick,
            onLongClick = onLongClick,
            width = cardWidth,
            circle = true,
            modifier = modifier,
        )
        return
    }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val footer = animateColorAsState(
        targetValue = rememberDominantCardColor(artworkUrl),
        animationSpec = tween(300),
        label = "cardFooter",
    )

    Column(modifier = modifier.width(cardWidth)) {
        if (!categoryLabel.isNullOrBlank()) {
            Text(
                text = categoryLabel,
                style = TvType.Caption,
                color = if (focused) TvGlass.TextPrimary else TvGlass.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .tvLift(interaction, RoundedCornerShape(14.dp), focusedScale = 1.07f, elevation = 22.dp)
                .tvClick(interaction, onLongClick = onLongClick, onClick = onClick)
                .drawBehind { drawRect(footer.value) },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio),
            ) {
                TvArtwork(url = artworkUrl, shape = RectangleShape, modifier = Modifier.fillMaxSize())
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        TvMiniEqualizer(isPlaying = true, barColor = Color.White, maxHeight = 11.dp)
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (subtitle.isNullOrBlank()) 52.dp else 64.dp)
                    .padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = title,
                    style = TvType.CardTitle.copy(fontWeight = FontWeight.W600),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = TvType.Caption,
                        color = Color.White.copy(alpha = 0.74f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Buttons
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The tvOS button: a translucent rounded platter that turns white and lifts on
 * focus. [isPrimary] only brightens the resting fill — on tvOS focus, not colour,
 * is what tells the buttons apart.
 */
@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isPrimary: Boolean = false,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val content = when {
        !enabled -> TvGlass.TextTertiary
        destructive -> TvGlass.Destructive
        focused -> TvGlass.OnPlatter
        else -> TvGlass.TextPrimary
    }
    Row(
        modifier = modifier
            .heightIn(min = 46.dp)
            .tvLift(interaction, RoundedCornerShape(12.dp), focusedScale = 1.06f, elevation = 14.dp)
            .tvPlatter(
                interaction,
                resting = when {
                    !enabled -> Color.White.copy(alpha = 0.05f)
                    isPrimary -> TvGlass.FillStrong
                    else -> TvGlass.Fill
                },
            )
            .tvClick(interaction, enabled = enabled, onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(9.dp))
        }
        Text(text = text, style = TvType.Body.copy(fontWeight = FontWeight.W600), color = content, maxLines = 1)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// States
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TvErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "Couldn't Load", style = TvType.Title.copy(fontSize = 22.sp), color = TvGlass.TextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = TvType.Callout,
            color = TvGlass.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 520.dp),
        )
        Spacer(modifier = Modifier.height(24.dp))
        TvButton(text = "Try Again", onClick = onRetry)
    }
}

@Composable
fun TvEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = TvType.Title.copy(fontSize = 22.sp),
            color = TvGlass.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = TvType.Callout,
            color = TvGlass.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 520.dp),
        )
        if (action != null) {
            Spacer(modifier = Modifier.height(24.dp))
            action()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Alerts
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The tvOS alert: the app dims away and the alert's content floats centred
 * over it — title, an optional message, then its controls. No card, no border.
 * Back dismisses it.
 */
@Composable
fun TvDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    width: Dp = 560.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(220),
        label = "tvDialogIn",
    )

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress }
                .background(Color(0xF2111113)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = modifier
                    .width(width)
                    .graphicsLayer {
                        val s = 0.96f + 0.04f * progress
                        scaleX = s
                        scaleY = s
                    }
                    .padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = TvType.Title,
                    color = TvGlass.TextPrimary,
                    textAlign = TextAlign.Center,
                )
                if (!message.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = message,
                        style = TvType.Callout,
                        color = TvGlass.TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(modifier = Modifier.height(26.dp))
                content()
            }
        }
    }
}

/** A full-width alert button, stacked one per row as tvOS alerts do. */
@Composable
fun TvDialogButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    enabled: Boolean = true,
    initialFocus: Boolean = false,
) {
    TvButton(
        text = text,
        onClick = onClick,
        destructive = destructive,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .tvInitialFocus(initialFocus),
    )
}

/** A rounded group of list rows inside an alert — menus, pickers, settings sheets. */
@Composable
fun TvDialogList(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}
