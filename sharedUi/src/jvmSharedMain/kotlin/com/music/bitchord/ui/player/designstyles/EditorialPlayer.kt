package com.music.bitchord.ui.player.designstyles

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.sharedui.resources.Res
import com.music.bitchord.sharedui.resources.design_collapse
import com.music.bitchord.sharedui.resources.design_like
import com.music.bitchord.sharedui.resources.design_more
import com.music.bitchord.sharedui.resources.design_pause
import com.music.bitchord.sharedui.resources.design_play
import com.music.bitchord.sharedui.resources.design_queue
import com.music.bitchord.sharedui.resources.ic_player_next
import com.music.bitchord.sharedui.resources.ic_player_previous
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.ui.player.MarqueeText
import com.music.bitchord.ui.player.ThinSlider
import com.music.bitchord.ui.theme.stableStatusBarInsets
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * ArchiveTune's V10 "Editorial": a flat tinted field, the sleeve as a die-cut
 * shape that morphs between flower, clover, cookie and burst as the track
 * changes, a serif italic display title, and the transport that says what it
 * does — a full-width pill with the word PLAY on it.
 */
@Composable
internal fun EditorialPlayerContent(
    data: DesignStylePlayerData,
    colors: DesignStyleColors,
    panels: DesignStylePanelState,
    reduceAnimation: Boolean,
) {
    // The die-cut: which shape the sleeve is, stepping through the set as the
    // track changes, and how far the morph between the last two has run.
    var shapeIndex by remember { mutableIntStateOf(dieCutIndex(data.song.videoId)) }
    var previousShape by remember { mutableIntStateOf(shapeIndex) }
    val morph = remember { Animatable(1f) }
    LaunchedEffect(data.song.videoId) {
        val next = dieCutIndex(data.song.videoId)
        if (next != shapeIndex) {
            previousShape = shapeIndex
            shapeIndex = next
            morph.snapTo(0f)
            morph.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = 120f))
        }
    }
    val dieCut = remember {
        DieCutShape(
            fromIndex = { previousShape },
            toIndex = { shapeIndex },
            progress = { morph.value },
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.field),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.union(stableStatusBarInsets()))
                .padding(horizontal = 24.dp),
        ) {
            // The header: the collapse, the lyrics and the menu as press-scale
            // circles, right-aligned as V10 keeps its field bare on the left.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EditorialCircle(BitChordIcons.ChevronDown, stringResource(Res.string.design_collapse), data.onClose, colors)
                Spacer(Modifier.padding(horizontal = 8.dp))
                EditorialCircle(
                    BitChordIcons.LyricsQuote,
                    null,
                    { panels.toggleLyrics() },
                    colors,
                    highlight = panels.lyricsOpen,
                )
                Spacer(Modifier.padding(horizontal = 8.dp))
                EditorialCircle(BitChordIcons.Plus, stringResource(Res.string.design_more), data.onOpenMenu, colors)
            }
            Spacer(Modifier.weight(1f))
            // The die-cut sleeve on its accent plate, breathing as it plays.
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .graphicsLayer {
                        val s = if (data.isPlaying) 1f else 0.94f
                        scaleX = s
                        scaleY = s
                    },
            ) {
                // The plate behind the cut, an accent disc the art floats on.
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 0.16f }
                        .clip(dieCut)
                        .background(colors.fieldAccent),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                        .clip(dieCut),
                ) {
                    AsyncImage(
                        model = data.artRequest,
                        contentDescription = data.song.title,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            // The serif italic display title, sized by how long it is.
            val titleStyle = when {
                data.song.title.length <= 12 -> MaterialTheme.typography.displaySmall
                data.song.title.length <= 24 -> MaterialTheme.typography.headlineMedium
                else -> MaterialTheme.typography.titleLarge
            }
            MarqueeText(
                text = data.song.title,
                style = titleStyle.copy(
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Bold,
                ),
                color = colors.text,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = data.song.artist.uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
                color = colors.text.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(28.dp))
            // The word button: the full-width pill that says PLAY.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(80.dp)
                        .clip(RoundedCornerShape(40.dp))
                        .background(colors.fieldAccent)
                        .clickable {
                            if (data.controlsLocked) data.onBlockedControl() else data.onPlayPause()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (data.isLoading || data.audioVersionSwitching) {
                        CircularProgressIndicator(
                            color = colors.field,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(30.dp),
                        )
                    } else {
                        Text(
                            text = stringResource(if (data.isPlaying) Res.string.design_pause else Res.string.design_play),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 3.sp,
                            ),
                            color = colors.field,
                        )
                    }
                }
                Spacer(Modifier.padding(horizontal = 12.dp))
                Box(
                    Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(colors.fieldAccent.copy(alpha = 0.14f))
                        .clickable(enabled = data.hasNext && !data.controlsLocked) {
                            if (data.hasNext) data.onNext()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_player_next),
                        contentDescription = null,
                        tint = if (data.hasNext) colors.text else colors.text.copy(alpha = 0.35f),
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            // The last row: the previous circle, the wavy progress, the times.
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(colors.fieldAccent.copy(alpha = 0.14f))
                        .clickable(enabled = data.hasPrevious && !data.controlsLocked) {
                            if (data.hasPrevious) data.onPrevious()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_player_previous),
                        contentDescription = null,
                        tint = if (data.hasPrevious) colors.text else colors.text.copy(alpha = 0.35f),
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.padding(horizontal = 14.dp))
                Column(Modifier.weight(1f)) {
                    ThinSlider(
                        value = data.shown(),
                        onValueChange = data.onScrub,
                        onValueChangeFinished = data.onScrubFinished,
                        style = com.music.bitchord.data.settings.PlayerSliderStyle.WAVY,
                        animated = data.isPlaying && !reduceAnimation,
                        idleHeight = 6.dp,
                        activeHeight = 8.dp,
                        activeColor = colors.fieldAccent,
                        inactiveColor = colors.text.copy(alpha = 0.2f),
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Text(
                            text = styleFormatTime((data.shown() * data.durationMs).toLong()),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.text.copy(alpha = 0.55f),
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = styleFormatTime(data.durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.text.copy(alpha = 0.55f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StyleIconButton(
                    icon = if (data.likeStatus == LikeStatus.LIKE) BitChordIcons.HeartFilled else BitChordIcons.Heart,
                    contentDescription = stringResource(Res.string.design_like),
                    onClick = { data.onToggleLike() },
                    tint = if (data.likeStatus == LikeStatus.LIKE) colors.fieldAccent else colors.text,
                    size = 40.dp,
                )
                StyleIconButton(
                    icon = BitChordIcons.Queue,
                    contentDescription = stringResource(Res.string.design_queue),
                    onClick = { panels.toggleQueue() },
                    tint = colors.text,
                    size = 40.dp,
                )
            }
        }
    }

    StyleLyricsOverlay(
        visible = panels.lyricsOpen,
        lyrics = data.lyrics,
        subLines = data.subLines,
        trackKey = data.song.videoId,
        playhead = data.lyricsPlayhead,
        looking = !data.lyricsUnavailable,
        isPlaying = data.isPlaying && data.position.advancing,
        onSeekToLine = data.seekToLyric,
        onDismiss = { panels.lyricsOpen = false },
        scrim = colors.field.copy(alpha = 0.96f),
        lyricsTranslation = data.lyricsTranslation,
        lyricPicker = data.lyricPicker,
        canPick = data.lyricsShareEnabled,
        onScrollingChange = { panels.lyricsScrolling = it },
    )
    StyleQueueOverlay(
        visible = panels.queueOpen,
        queue = data.queue,
        currentIndex = data.queueIndex,
        autoplayEnabled = data.autoplayEnabled,
        controlsLocked = data.controlsLocked,
        onJumpTo = data.onJumpTo,
        onRemove = data.onRemoveFromQueue,
        onMove = data.onMoveInQueue,
        onClear = data.onClearQueue,
        onDismiss = { panels.queueOpen = false },
        scrim = colors.field.copy(alpha = 0.96f),
        headerColor = colors.text,
        onScrollingChange = { panels.queueScrolling = it },
        onDragActiveChange = data.onQueueDragActiveChange,
    )
}

/** One of the die-cut set, per track, so the same sleeve keeps its shape. */
private fun dieCutIndex(videoId: String): Int = videoId.hashCode().let { if (it < 0) -it else it } % 4

/**
 * The die-cut sleeve: a polar shape whose radius function is one of the four
 * cuts, interpolated by the morph progress as the track changes. Drawn as a
 * dense sampled path, so every cut reads as a smooth die-punched plate.
 */
private class DieCutShape(
    private val fromIndex: () -> Int,
    private val toIndex: () -> Int,
    private val progress: () -> Float,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) / 2f * 0.92f
        val from = fromIndex()
        val to = toIndex()
        val t = progress().coerceIn(0f, 1f)
        val path = Path()
        val steps = 192
        for (i in 0..steps) {
            val theta = (2.0 * PI * i / steps).toFloat()
            val r = radius * (cutRadius(from, theta) * (1f - t) + cutRadius(to, theta) * t)
            val x = center.x + r * cos(theta)
            val y = center.y + r * sin(theta)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

/** The four cuts' radius functions, as shares of the plate's radius. */
private fun cutRadius(index: Int, theta: Float): Float = when (index) {
    0 -> 1f + 0.10f * cos(8f * theta) // Flower
    1 -> 1f + 0.14f * cos(4f * theta) // Clover, four leaves
    2 -> 1f + 0.035f * cos(12f * theta) // Cookie, twelve sides
    else -> 1f + 0.085f * cos(16f * theta) * (0.7f + 0.3f * cos(3f * theta)) // Soft burst
}

/** A V10 circle button: a tinted disc, the glyph centred in it. */
@Composable
private fun EditorialCircle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    colors: DesignStyleColors,
    highlight: Boolean = false,
) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (highlight) colors.fieldAccent.copy(alpha = 0.22f) else colors.fieldAccent.copy(alpha = 0.12f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (highlight) colors.fieldAccent else colors.text,
            modifier = Modifier.size(20.dp),
        )
    }
}
