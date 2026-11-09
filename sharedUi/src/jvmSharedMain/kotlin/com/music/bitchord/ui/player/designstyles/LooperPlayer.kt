package com.music.bitchord.ui.player.designstyles

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.settings.PlayerSliderStyle
import com.music.bitchord.sharedui.resources.Res
import com.music.bitchord.sharedui.resources.design_like
import com.music.bitchord.sharedui.resources.design_lyrics
import com.music.bitchord.sharedui.resources.design_more
import com.music.bitchord.sharedui.resources.design_next_up
import com.music.bitchord.sharedui.resources.design_queue
import com.music.bitchord.sharedui.resources.ic_player_next
import com.music.bitchord.sharedui.resources.ic_player_pause
import com.music.bitchord.sharedui.resources.ic_player_play
import com.music.bitchord.sharedui.resources.ic_player_previous
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.ui.player.MarqueeText
import com.music.bitchord.ui.player.ThinSlider
import com.music.bitchord.ui.theme.stableStatusBarInsets
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * ArchiveTune's "Looper" player: the fixed near-black page, the sleeve that
 * drags aside to skip and taps for lyrics, the wave slider that only waves
 * once the track is actually going somewhere, and the asymmetric transport —
 * a pill with one rounded end each side of a centre block that inverts as it
 * plays.
 */
@Composable
internal fun LooperPlayerContent(
    data: DesignStylePlayerData,
    colors: DesignStyleColors,
    panels: DesignStylePanelState,
    reduceAnimation: Boolean,
) {
    val surface = Color(0xFF141414)
    val onSurface = Color(0xFFF2F2F2)
    val sleeveOffset = remember { mutableFloatStateOf(0f) }
    var sleeveWidth by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    Box(Modifier.fillMaxSize().background(surface)) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.union(stableStatusBarInsets()))
                .padding(horizontal = 20.dp),
        ) {
            // The header: close, the quality pill, more.
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StyleIconButton(
                    icon = BitChordIcons.ChevronDown,
                    contentDescription = null,
                    onClick = data.onClose,
                    tint = onSurface,
                    size = 44.dp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(Res.string.design_now_playing).uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
                    color = onSurface.copy(alpha = 0.5f),
                )
                Spacer(Modifier.weight(1f))
                StyleIconButton(
                    icon = BitChordIcons.Plus,
                    contentDescription = stringResource(Res.string.design_more),
                    onClick = data.onOpenMenu,
                    tint = onSurface,
                    size = 44.dp,
                )
            }
            Spacer(Modifier.weight(1f))
            // The sleeve: a drag past a quarter of its width skips, a tap opens
            // the lyrics, a double-tap seeks ten seconds.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                DesignStyleArtwork(
                    song = data.song,
                    artRequest = data.artRequest,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .onSizeChangedCompat { sleeveWidth = it }
                        .graphicsLayer { translationX = sleeveOffset.floatValue }
                        .styleSkipSwipe(
                            offsetState = sleeveOffset,
                            threshold = with(density) { 56.dp.toPx() },
                            enabled = !panels.lyricsOpen && !panels.queueOpen,
                            onSkipNext = data.onNext,
                            onSkipPrevious = data.onPrevious,
                            onBlocked = data.onBlockedControl,
                            haptics = data.haptics,
                        ),
                    shadow = 14.dp,
                    shadowTint = Color.Black.copy(alpha = 0.5f),
                    canvas = data.canvas,
                    isPlaying = data.isPlaying,
                    onCanvasRenderedChange = data.onLiveCanvasChanged,
                    onTap = { panels.toggleLyrics() },
                    onDoubleTap = { offset ->
                        val sign = if (sleeveWidth > 0 && offset.x < sleeveWidth / 2f) -1 else 1
                        data.onSeek((data.position.positionMs + 10_000L * sign).coerceIn(0L, data.durationMs))
                    },
                )
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    MarqueeText(
                        text = data.song.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                        color = onSurface,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    MarqueeText(
                        text = data.song.artist,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                        color = onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    )
                }
                Spacer(Modifier.padding(horizontal = 8.dp))
                StyleIconButton(
                    icon = if (data.likeStatus == LikeStatus.LIKED) BitChordIcons.HeartFilled else BitChordIcons.Heart,
                    contentDescription = stringResource(Res.string.design_like),
                    onClick = { data.onToggleLike() },
                    tint = if (data.likeStatus == LikeStatus.LIKED) Color(0xFFFC3C44) else onSurface,
                    size = 44.dp,
                )
            }
            Spacer(Modifier.height(22.dp))
            // The wave slider: the wave only rises once the track is playing
            // and actually past its start.
            val waveAlive = data.isPlaying && data.shown() > 0.12f && !reduceAnimation
            val waveAlpha by animateFloatAsState(
                targetValue = if (waveAlive) 1f else 0.35f,
                animationSpec = tween(280),
                label = "looperWave",
            )
            ThinSlider(
                value = data.shown(),
                onValueChange = data.onScrub,
                onValueChangeFinished = data.onScrubFinished,
                style = PlayerSliderStyle.WAVY,
                animated = waveAlive,
                idleHeight = 9.dp,
                activeHeight = 12.dp,
                activeColor = onSurface.copy(alpha = waveAlpha),
                inactiveColor = onSurface.copy(alpha = 0.2f),
            )
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text(
                    text = styleFormatTime((data.shown() * data.durationMs).toLong()),
                    style = MaterialTheme.typography.labelSmall,
                    color = onSurface.copy(alpha = 0.5f),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = styleFormatTime(data.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = onSurface.copy(alpha = 0.5f),
                )
            }
            Spacer(Modifier.height(20.dp))
            // The asymmetric transport: one-ended pills flanking the block.
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                LooperEndPill(
                    res = Res.drawable.ic_player_previous,
                    roundedStart = true,
                    enabled = data.hasPrevious && !data.controlsLocked,
                    onSurface = onSurface,
                ) { if (data.hasPrevious) data.onPrevious() }
                Spacer(Modifier.padding(horizontal = 10.dp))
                // The centre block: 96×80, 12dp corners, inverting as it plays.
                val playing = data.isPlaying
                Box(
                    Modifier
                        .size(width = 96.dp, height = 80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (playing) onSurface.copy(alpha = 0.06f) else onSurface,
                        )
                        .clickable {
                            if (data.controlsLocked) data.onBlockedControl() else data.onPlayPause()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (data.isLoading || data.audioVersionSwitching) {
                        CircularProgressIndicator(
                            color = if (playing) onSurface else surface,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(28.dp),
                        )
                    } else {
                        Icon(
                            painter = painterResource(if (playing) Res.drawable.ic_player_pause else Res.drawable.ic_player_play),
                            contentDescription = null,
                            tint = if (playing) onSurface else surface,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                }
                Spacer(Modifier.padding(horizontal = 10.dp))
                LooperEndPill(
                    res = Res.drawable.ic_player_next,
                    roundedStart = false,
                    enabled = data.hasNext && !data.controlsLocked,
                    onSurface = onSurface,
                ) { if (data.hasNext) data.onNext() }
            }
            Spacer(Modifier.height(20.dp))
            // The pill clusters: shuffle and repeat together, then the queue
            // and the lyrics labelled.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                LooperUtilityPill(BitChordIcons.Shuffle, null, if (data.shuffleEnabled) Color(0xFF7DD8FF) else onSurface.copy(alpha = 0.6f), onSurface) {
                    data.onToggleShuffle()
                }
                LooperUtilityPill(BitChordIcons.Repeat, null, repeatTintLooper(data.repeatMode, onSurface), onSurface) {
                    data.onCycleRepeat()
                }
                LooperLabeledPill(BitChordIcons.Queue, stringResource(Res.string.design_next_up), onSurface) {
                    panels.toggleQueue()
                }
                LooperLabeledPill(BitChordIcons.LyricsQuote, stringResource(Res.string.design_lyrics), onSurface) {
                    panels.toggleLyrics()
                }
            }
            Spacer(Modifier.height(10.dp))
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
        scrim = surface.copy(alpha = 0.96f),
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
        scrim = surface.copy(alpha = 0.96f),
        headerColor = onSurface,
        onScrollingChange = { panels.queueScrolling = it },
        onDragActiveChange = data.onQueueDragActiveChange,
    )
}

private fun repeatTintLooper(repeatMode: Int, onSurface: Color): Color = when (repeatMode) {
    1 -> onSurface
    2 -> Color(0xFF7DD8FF)
    else -> onSurface.copy(alpha = 0.6f)
}

/** A Looper end pill: rounded on the end that faces out, square on the one in. */
@Composable
private fun LooperEndPill(
    res: org.jetbrains.compose.resources.DrawableResource,
    roundedStart: Boolean,
    enabled: Boolean,
    onSurface: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(width = 72.dp, height = 56.dp)
            .clip(
                if (roundedStart) {
                    RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)
                } else {
                    RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)
                },
            )
            .background(onSurface.copy(alpha = 0.08f))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(res),
            contentDescription = null,
            tint = if (enabled) onSurface else onSurface.copy(alpha = 0.35f),
            modifier = Modifier.size(26.dp),
        )
    }
}

/** A small utility pill: the glyph alone, for shuffle and repeat. */
@Composable
private fun LooperUtilityPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String?,
    tint: Color,
    onSurface: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(onSurface.copy(alpha = 0.08f))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = tint,
                modifier = Modifier.padding(start = 7.dp),
            )
        }
    }
}

/** A labelled pill, as Looper names its queue and lyrics clusters. */
@Composable
private fun LooperLabeledPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onSurface: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(onSurface.copy(alpha = 0.08f))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = onSurface,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = onSurface,
            modifier = Modifier.padding(start = 7.dp),
        )
    }
}

/** onSizeChanged with the width only, for the double-tap's left/right test. */
private fun Modifier.onSizeChangedCompat(set: (Int) -> Unit): Modifier = this.then(
    Modifier.onSizeChanged { set(it.width) },
)
