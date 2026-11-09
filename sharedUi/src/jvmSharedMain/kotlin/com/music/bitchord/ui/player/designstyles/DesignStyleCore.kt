package com.music.bitchord.ui.player.designstyles

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.music.bitchord.data.canvas.CanvasArtwork
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.PlayerSliderStyle
import com.music.bitchord.sharedui.resources.Res
import com.music.bitchord.sharedui.resources.design_next_up
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.Haptics
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.ui.player.CanvasArtworkPlayer
import com.music.bitchord.ui.player.InlineQueue
import com.music.bitchord.ui.player.LyricPlayhead
import com.music.bitchord.ui.player.LyricsPicker
import com.music.bitchord.ui.player.LyricsPanel
import com.music.bitchord.ui.player.LyricsTranslationMotion
import com.music.bitchord.ui.player.LyricsTranslationUi
import com.music.bitchord.ui.player.PlayerBackHandler
import com.music.bitchord.ui.player.ThinSlider
import com.music.bitchord.ui.player.rememberArtworkColors
import com.music.bitchord.ui.theme.stableStatusBarInsets
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

/**
 * The ported players' shared colour kit, grown from the artwork the way
 * ArchiveTune's dynamic colours are: one animated swatch set with a rung for
 * every style that tints off the cover.
 *
 * The derivations follow ArchiveTune's Player.kt: the background is the
 * artwork's dominant colour with its saturation and value clamped into a band
 * that keeps text legible; the accent keeps the hue and swings to the far end
 * of the value scale; the editorial field is a mid-tone wash. HSV rather than
 * HCT — no scheme is grown here, and the differences land inside the clamps
 * either way. Pure math, so it runs wherever the player does.
 */
@Composable
internal fun rememberDesignStyleColors(imageUrl: String?): DesignStyleColors {
    val palette = rememberArtworkColors(imageUrl)
    val dark = isSystemInDarkTheme()
    val target = remember(palette, dark) { deriveDesignStyleColors(palette.colors, dark) }
    val bg by animateColorAsState(target.bg, tween(800), label = "styleBg")
    val accent by animateColorAsState(target.accent, tween(800), label = "styleAccent")
    val text by animateColorAsState(target.text, tween(800), label = "styleText")
    val icon by animateColorAsState(target.icon, tween(800), label = "styleIcon")
    val field by animateColorAsState(target.field, tween(800), label = "styleField")
    val fieldAccent by animateColorAsState(target.fieldAccent, tween(800), label = "styleFieldAccent")
    return remember(bg, accent, text, icon, field, fieldAccent) {
        DesignStyleColors(bg, accent, text, icon, field, fieldAccent)
    }
}

/** How colourful a swatch is as a seed: saturated mid-tones first. */
private fun seedQuality(c: Color): Float {
    val (h, s, v) = c.toHsvTriple()
    return s * (1f - abs(v - 0.55f))
}

/** The swatch set every ported style tints from. */
@Immutable
internal class DesignStyleColors(
    /** The player's own wash, as V9's dynamic background is. */
    val bg: Color,
    /** A saturated rung of the artwork's hue. */
    val accent: Color,
    /** High-contrast body text over [bg]. */
    val text: Color,
    /** Icon and secondary text over [bg]. */
    val icon: Color,
    /** V10's flat tinted field. */
    val field: Color,
    /** V10's accent over [field]. */
    val fieldAccent: Color,
)

private fun deriveDesignStyleColors(colors: List<Color>, dark: Boolean): DesignStyleColors {
    val accentSeed = colors.maxByOrNull { seedQuality(it) } ?: Color(0xFF3E5060)
    val bgSeed = colors.maxByOrNull { it.luminance() * 0.4f + seedQuality(it) * 0.6f } ?: accentSeed
    val (bh, bs, bv) = bgSeed.toHsvTriple()
    val (ah, aSat, aVal) = accentSeed.toHsvTriple()
    val bg = if (dark) {
        hsvColor(bh, bs.coerceIn(0.12f, 0.35f), 0.09f + bv * 0.06f)
    } else {
        hsvColor(bh, bs.coerceIn(0.04f, 0.12f), 0.96f)
    }
    val accent = if (dark) {
        hsvColor(ah, aSat.coerceIn(0.35f, 0.85f), (aVal * 0.5f + 0.55f).coerceIn(0.6f, 0.9f))
    } else {
        hsvColor(ah, aSat.coerceIn(0.4f, 0.9f), (aVal * 0.45f + 0.15f).coerceIn(0.15f, 0.45f))
    }
    val text = if (dark) Color(0xFFF2F2F4) else Color(0xFF16181C)
    val icon = if (dark) Color(0xFFE8EAEE) else Color(0xFF23262B)
    val field = if (dark) hsvColor(bh, bs.coerceIn(0.2f, 0.5f), 0.30f) else hsvColor(bh, bs.coerceIn(0.1f, 0.35f), 0.90f)
    val fieldAccent = if (dark) hsvColor(ah, aSat.coerceIn(0.4f, 0.9f), 0.90f) else hsvColor(ah, aSat.coerceIn(0.4f, 0.9f), 0.10f)
    return DesignStyleColors(bg, accent, text, icon, field, fieldAccent)
}

/** Pure-math sRGB to HSV, so the kit runs on every target the player does. */
private fun Color.toHsvTriple(): Triple<Float, Float, Float> {
    val r = red
    val g = green
    val b = blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val h = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    val s = if (max == 0f) 0f else delta / max
    return Triple(h, s, max)
}

private fun hsvColor(h: Float, s: Float, v: Float): Color {
    val c = v * s.coerceIn(0f, 1f)
    val hp = (h % 360f) / 60f
    val x = c * (1f - abs(hp % 2f - 1f))
    val (r1, g1, b1) = when {
        hp < 1f -> Triple(c, x, 0f)
        hp < 2f -> Triple(x, c, 0f)
        hp < 3f -> Triple(0f, c, x)
        hp < 4f -> Triple(0f, x, c)
        hp < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = v - c
    return Color(red = r1 + m, green = g1 + m, blue = b1 + m)
}

/**
 * The artwork every style hangs its layout on: the cover clipped to the
 * style's shape with the shadow the style asks for, the looping canvas laid
 * over it where the style takes one, and the tap and double-tap gestures the
 * style wires — the cover itself plays no part in choosing.
 */
@Composable
internal fun DesignStyleArtwork(
    song: Song,
    artRequest: ImageRequest,
    shape: Shape,
    modifier: Modifier = Modifier,
    shadow: Dp = 0.dp,
    shadowTint: Color = Color.Black.copy(alpha = 0.4f),
    contentScale: ContentScale = ContentScale.Crop,
    canvas: CanvasArtwork? = null,
    isPlaying: Boolean = true,
    onCanvasRenderedChange: (Boolean) -> Unit = {},
    onTap: (() -> Unit)? = null,
    onDoubleTap: ((androidx.compose.ui.geometry.Offset) -> Unit)? = null,
    content: (@Composable BoxScope.() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .then(
                if (shadow > 0.dp) {
                    Modifier.shadow(shadow, shape, ambientColor = shadowTint, spotColor = shadowTint)
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .then(
                if (onTap != null || onDoubleTap != null) {
                    Modifier.pointerInput(onTap, onDoubleTap) {
                        detectTapGestures(
                            onTap = { onTap?.invoke() },
                            onDoubleTap = { offset -> onDoubleTap?.invoke(offset) },
                        )
                    }
                } else {
                    Modifier
                },
            )
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = artRequest,
            contentDescription = song.title,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize(),
        )
        if (canvas != null) {
            CanvasArtworkPlayer(
                canvas = canvas,
                isPlaying = isPlaying,
                modifier = Modifier.fillMaxSize(),
                onRenderedChanged = onCanvasRenderedChange,
            )
        }
        content?.invoke(this)
    }
}

/**
 * The scrubber shared by the ported styles: the ThinSlider in whatever shape
 * and colour the style asks for, with the elapsed and remaining times under it
 * in the style's own text colour.
 */
@Composable
internal fun StyleScrubber(
    value: Float,
    durationMs: Long,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier,
    style: PlayerSliderStyle = PlayerSliderStyle.STANDARD,
    animated: Boolean = false,
    idleHeight: Dp = 7.dp,
    activeHeight: Dp = 12.dp,
    timeColor: Color? = null,
    centerContent: (@Composable BoxScope.() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ThinSlider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            style = style,
            animated = animated,
            idleHeight = idleHeight,
            activeHeight = activeHeight,
            activeColor = activeColor,
            inactiveColor = inactiveColor,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = styleFormatTime((value * durationMs).toLong()),
                color = timeColor ?: activeColor,
                style = MaterialTheme.typography.labelMedium,
            )
            if (centerContent != null) {
                Box(Modifier.weight(1f)) {
                    Box(content = centerContent)
                }
            }
            Text(
                text = "-" + styleFormatTime(durationMs - (value * durationMs).toLong()),
                color = timeColor ?: activeColor,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/** The player's own mm:ss, shared by every ported style. */
internal fun styleFormatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes >= 60) {
        "%d:%02d:%02d".format(minutes / 60, minutes % 60, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/**
 * A ghost round icon button, as the ported styles' headers and rails use: a
 * translucent disc in the given colour, the glyph centred in it.
 */
@Composable
internal fun StyleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    background: Color? = null,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(
                if (background != null) {
                    Modifier.background(background)
                } else {
                    Modifier.background(tint.copy(alpha = if (enabled) 0.12f else 0.05f))
                },
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.38f),
            modifier = Modifier.size(size * 0.46f),
        )
    }
}

/**
 * The full-screen lyrics the inline-lyrics styles raise over themselves —
 * ArchiveTune's MikoLyricsTransition pattern: the panel over a scrim of the
 * player's own background, with a chevron to come back and the system back
 * doing the same.
 */
@Composable
internal fun StyleLyricsOverlay(
    visible: Boolean,
    lyrics: List<LyricLine>?,
    subLines: List<LyricLine>?,
    trackKey: String,
    playhead: LyricPlayhead,
    looking: Boolean,
    isPlaying: Boolean,
    onSeekToLine: (Long) -> Unit,
    onDismiss: () -> Unit,
    scrim: Color,
    lyricsTranslation: LyricsTranslationUi,
    lyricPicker: LyricsPicker,
    canPick: Boolean,
    onScrollingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    PlayerBackHandler(enabled = true) { onDismiss() }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scrim)
            .windowInsetsPadding(WindowInsets.safeDrawing.union(stableStatusBarInsets())),
    ) {
        LyricsTranslationMotion(
            trigger = lyricsTranslation.transition,
            reduceMotion = lyricsTranslation.reduceMotion,
            modifier = Modifier.fillMaxSize().padding(top = 56.dp),
        ) { particleProgress ->
            LyricsPanel(
                lines = lyrics.orEmpty(),
                subLines = subLines,
                trackKey = trackKey,
                playhead = playhead,
                looking = looking,
                isPlaying = isPlaying,
                onSeekToLine = onSeekToLine,
                controlsOpen = true,
                onRevealControls = {},
                onHideControls = {},
                translationProgress = particleProgress,
                onScrollingChange = onScrollingChange,
                canPick = canPick,
                picking = lyricPicker.picking,
                picked = lyricPicker.picks,
                onPickLine = lyricPicker.pick,
                onTogglePick = lyricPicker.toggle,
                modifier = Modifier.fillMaxSize(),
            )
        }
        StyleIconButton(
            icon = BitChordIcons.ChevronDown,
            contentDescription = null,
            onClick = onDismiss,
            tint = Color.White,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        )
    }
}

/**
 * The queue the ported styles raise over themselves: the running order in this
 * app's own InlineQueue — reorderable, jumpable — under a header with a close.
 */
@Composable
internal fun StyleQueueOverlay(
    visible: Boolean,
    queue: List<Song>,
    currentIndex: Int,
    autoplayEnabled: Boolean,
    controlsLocked: Boolean,
    onJumpTo: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    scrim: Color,
    headerColor: Color,
    onScrollingChange: (Boolean) -> Unit,
    onDragActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    PlayerBackHandler(enabled = true) { onDismiss() }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(scrim)
            .windowInsetsPadding(WindowInsets.safeDrawing.union(stableStatusBarInsets())),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.design_next_up),
                style = MaterialTheme.typography.titleMedium,
                color = headerColor,
                modifier = Modifier.weight(1f),
            )
            StyleIconButton(
                icon = BitChordIcons.ChevronDown,
                contentDescription = null,
                onClick = onDismiss,
                tint = headerColor,
                size = 36.dp,
            )
        }
        InlineQueue(
            queue = queue,
            currentIndex = currentIndex,
            autoplayEnabled = autoplayEnabled,
            controlsLocked = controlsLocked,
            onJumpTo = onJumpTo,
            onRemove = onRemove,
            onMove = onMove,
            onClear = onClear,
            onScrollingChange = onScrollingChange,
            onDragActiveChange = onDragActiveChange,
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        )
    }
}

/** A haptics-free convenience so the styles' buttons can buzz as the app's do. */
@Composable
internal fun rememberStyleHaptics(haptics: Haptics): (Haptic) -> Unit = remember { { h -> haptics.play(h) } }

/**
 * The drag-to-skip the ported styles hang on their artwork: the sleeve follows
 * the finger (damped, through [offsetState]), springs back on release, and a
 * drag past [threshold] steps the queue. Ported from ArchiveTune's sleeve
 * gestures — the pager skip of V4, the drag skip of Looper.
 */
@Composable
internal fun Modifier.styleSkipSwipe(
    offsetState: androidx.compose.runtime.MutableFloatState,
    threshold: Float,
    enabled: Boolean,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onBlocked: () -> Unit,
    haptics: Haptics,
): Modifier {
    return this.then(
        Modifier.pointerInput(enabled, threshold) {
            if (!enabled) return@pointerInput
            androidx.compose.foundation.gestures.detectHorizontalDragGestures(
                onDragStart = { },
                onDragEnd = {
                    val total = offsetState.floatValue
                    when {
                        !enabled -> Unit
                        total <= -threshold -> {
                            haptics.play(Haptic.SkipNext)
                            onSkipNext()
                        }
                        total >= threshold -> {
                            haptics.play(Haptic.SkipPrevious)
                            onSkipPrevious()
                        }
                    }
                    offsetState.floatValue = 0f
                },
                onDragCancel = { offsetState.floatValue = 0f },
                onHorizontalDrag = { _, delta ->
                    offsetState.floatValue += delta
                },
            )
        },
    )
}
