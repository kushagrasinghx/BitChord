package com.music.bitchord.ui.player.designstyles

import androidx.compose.animation.animateDpAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.sharedui.resources.Res
import com.music.bitchord.sharedui.resources.design_collapse
import com.music.bitchord.sharedui.resources.design_like
import com.music.bitchord.sharedui.resources.design_more
import com.music.bitchord.sharedui.resources.design_queue
import com.music.bitchord.sharedui.resources.ic_player_next
import com.music.bitchord.sharedui.resources.ic_player_pause
import com.music.bitchord.sharedui.resources.ic_player_play
import com.music.bitchord.sharedui.resources.ic_player_previous
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.ui.player.MarqueeText
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * ArchiveTune's V5 "Little": no artwork at all — the background itself is the
 * seekbar. The container fills with the primary colour as the song plays, a
 * vertical drag inside the filled part of it scrubs, a double-tap on the top
 * half steps back and on the bottom half steps forward, and everything else
 * is typography: a huge marquee title, the album, the artists, the time as big
 * as the title.
 */
@Composable
internal fun LittlePlayerContent(
    data: DesignStylePlayerData,
    colors: DesignStyleColors,
    panels: DesignStylePanelState,
    reduceAnimation: Boolean,
) {
    val container = MaterialTheme.colorScheme.primaryContainer
    val progressFill = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
    var height by remember { mutableIntStateOf(0) }
    val fraction = data.shown()

    Box(
        Modifier
            .fillMaxSize()
            .background(container)
            .onSizeChanged { height = it.height }
            .pointerInput(data.durationMs, data.controlsLocked) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        if (height > 0) {
                            if (offset.y < height / 2f) data.onPrevious() else data.onNext()
                        }
                    },
                )
            },
    ) {
        // The fill: the song's own progress as a wash down the screen. A drag
        // that starts inside it scrubs — the filled region is the bar's whole
        // touch target, exactly as wide as it is tall.
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(fraction)
                .background(progressFill)
                .pointerInput(data.durationMs, data.controlsLocked, height) {
                    if (data.durationMs <= 0 || height <= 0) return@pointerInput
                    androidx.compose.foundation.gestures.detectVerticalDragGestures { change, _ ->
                        if (data.controlsLocked) {
                            data.onBlockedControl()
                            return@detectVerticalDragGestures
                        }
                        val y = change.position.y.coerceIn(0f, height.toFloat())
                        data.onScrub(y / height.toFloat())
                    }
                },
        )
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.union(stableStatusBarInsets()))
                .padding(horizontal = 28.dp, vertical = 18.dp),
        ) {
            Spacer(Modifier.weight(1f))
            // The time, as big as the title: the one number V5 keeps.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(
                    text = styleFormatTime((fraction * data.durationMs).toLong()),
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 44.sp, fontWeight = FontWeight.Black),
                    color = onContainer,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = styleFormatTime(data.durationMs),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                    color = onContainer.copy(alpha = 0.5f),
                )
            }
            Spacer(Modifier.height(18.dp))
            MarqueeText(
                text = data.song.title,
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, fontWeight = FontWeight.Bold),
                color = onContainer,
                modifier = Modifier.fillMaxWidth(),
            )
            data.song.albumName?.let { album ->
                Text(
                    text = album,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                    color = onContainer.copy(alpha = 0.65f),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Text(
                text = stringResource(Res.string.design_by, data.song.artist),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                color = onContainer.copy(alpha = 0.55f),
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(22.dp))
            // The minimal icon row V5 tops its typography with.
            Row(verticalAlignment = Alignment.CenterVertically) {
                StyleIconButton(
                    icon = BitChordIcons.ChevronDown,
                    contentDescription = stringResource(Res.string.design_collapse),
                    onClick = data.onClose,
                    tint = onContainer,
                    size = 40.dp,
                )
                Spacer(Modifier.weight(1f))
                StyleIconButton(
                    icon = if (data.likeStatus == LikeStatus.LIKED) BitChordIcons.HeartFilled else BitChordIcons.Heart,
                    contentDescription = stringResource(Res.string.design_like),
                    onClick = { data.onToggleLike() },
                    tint = if (data.likeStatus == LikeStatus.LIKED) MaterialTheme.colorScheme.primary else onContainer,
                    size = 40.dp,
                )
                StyleIconButton(
                    icon = BitChordIcons.Queue,
                    contentDescription = stringResource(Res.string.design_queue),
                    onClick = { panels.toggleQueue() },
                    tint = onContainer,
                    size = 40.dp,
                )
                StyleIconButton(
                    icon = BitChordIcons.Plus,
                    contentDescription = stringResource(Res.string.design_more),
                    onClick = data.onOpenMenu,
                    tint = onContainer,
                    size = 40.dp,
                )
            }
            Spacer(Modifier.height(14.dp))
            // The evenly weighted row: repeat, prev, the 72dp round play, next,
            // like — V5's transport exactly.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StyleIconButton(
                    icon = BitChordIcons.Repeat,
                    contentDescription = null,
                    onClick = { data.onCycleRepeat() },
                    tint = when (data.repeatMode) {
                        1 -> onContainer
                        2 -> MaterialTheme.colorScheme.primary
                        else -> onContainer.copy(alpha = 0.45f)
                    },
                    size = 44.dp,
                    enabled = !data.controlsLocked,
                )
                LittleSideGlyph(
                    res = Res.drawable.ic_player_previous,
                    tint = onContainer,
                    enabled = data.hasPrevious && !data.controlsLocked,
                ) { if (data.hasPrevious) data.onPrevious() }
                LittlePlayPause(data, onContainer)
                LittleSideGlyph(
                    res = Res.drawable.ic_player_next,
                    tint = onContainer,
                    enabled = data.hasNext && !data.controlsLocked,
                ) { if (data.hasNext) data.onNext() }
                StyleIconButton(
                    icon = BitChordIcons.LyricsQuote,
                    contentDescription = null,
                    onClick = { panels.toggleLyrics() },
                    tint = onContainer,
                    size = 44.dp,
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
        scrim = container.copy(alpha = 0.96f),
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
        scrim = container.copy(alpha = 0.96f),
        headerColor = onContainer,
        onScrollingChange = { panels.queueScrolling = it },
        onDragActiveChange = data.onQueueDragActiveChange,
    )
}

/** V5's play button: 72dp, round, its corners easing 24→36 as it pauses. */
@Composable
private fun LittlePlayPause(data: DesignStylePlayerData, onContainer: Color) {
    val corner by animateDpAsState(
        targetValue = if (data.isPlaying) 24.dp else 36.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = 500f),
        label = "littleCorner",
    )
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(corner))
            .background(onContainer)
            .clickable {
                if (data.controlsLocked) data.onBlockedControl() else data.onPlayPause()
            },
        contentAlignment = Alignment.Center,
    ) {
        if (data.isLoading || data.audioVersionSwitching) {
            CircularProgressIndicator(color = containerInverse(), strokeWidth = 3.dp, modifier = Modifier.size(30.dp))
        } else {
            Icon(
                painter = painterResource(if (data.isPlaying) Res.drawable.ic_player_pause else Res.drawable.ic_player_play),
                contentDescription = null,
                tint = containerInverse(),
                modifier = Modifier.size(34.dp),
            )
        }
    }
}

@Composable
private fun containerInverse(): Color = MaterialTheme.colorScheme.primaryContainer

/** A V5 side skip: the plain glyph in the container's own ink. */
@Composable
private fun LittleSideGlyph(
    res: org.jetbrains.compose.resources.DrawableResource,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(50))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(res),
            contentDescription = null,
            tint = if (enabled) tint else tint.copy(alpha = 0.35f),
            modifier = Modifier.size(30.dp),
        )
    }
}
