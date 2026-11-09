package com.music.bitchord.ui.player.designstyles

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import com.music.bitchord.data.settings.PlayerTheme
import com.music.bitchord.sharedui.resources.Res
import com.music.bitchord.sharedui.resources.design_like
import com.music.bitchord.sharedui.resources.design_more
import com.music.bitchord.sharedui.resources.design_queue
import com.music.bitchord.sharedui.resources.ic_player_next
import com.music.bitchord.sharedui.resources.ic_player_pause
import com.music.bitchord.sharedui.resources.ic_player_play
import com.music.bitchord.sharedui.resources.ic_player_previous
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.ui.player.FullArtworkBlurBackdrop
import com.music.bitchord.ui.player.MarqueeText
import com.music.bitchord.ui.player.MeshGradientBackground
import com.music.bitchord.ui.player.PlaybackQualityLabel
import com.music.bitchord.ui.player.PlayerGlowBackdrop
import com.music.bitchord.ui.player.PlayerSettings
import com.music.bitchord.ui.player.rememberArtworkColors
import com.music.bitchord.ui.player.rememberFullArtworkBlurImage
import com.music.bitchord.ui.theme.stableStatusBarInsets
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource

/**
 * ArchiveTune's V4 "Cinematic": the rounded square sleeve with the swipe pager,
 * the like-and-queue rounds beside the credits, the slider in whatever style
 * the listener picked, and the morphing-corner play button — 28dp corners
 * while it plays, squaring toward 44 as it pauses. Double-tap seeks, and a
 * second tap the same way doubles the step, as V4's multiplier does.
 */
@Composable
internal fun CinematicPlayerContent(
    data: DesignStylePlayerData,
    colors: DesignStyleColors,
    panels: DesignStylePanelState,
    sliderStyle: PlayerSliderStyle,
    reduceAnimation: Boolean,
) {
    val playerTheme by PlayerSettings.playerTheme.collectAsStateWithLifecycle()
    val hideThumbnail by PlayerSettings.hidePlayerThumbnail.collectAsStateWithLifecycle()
    val sleeveOffset = remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val swipeThreshold = with(density) { 72.dp.toPx() }
    var sleeveWidth by remember { mutableIntStateOf(0) }
    // Double-tap seeks ±5s, and the same side twice doubles the step — V4's
    // tap-to-seek multiplier, reset when the side changes or a second passes.
    var lastSeekSign by remember { mutableIntStateOf(0) }
    var lastSeekMs by remember { mutableLongStateOf(0L) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // The backdrop the player's own background setting draws — V4 is one of
        // the two styles ArchiveTune lets customize it on: mesh, blur, flat or
        // glow.
        when (playerTheme) {
            PlayerTheme.DEFAULT, PlayerTheme.LEGACY -> {
                val palette = rememberArtworkColors(data.remoteArt)
                MeshGradientBackground(palette = palette, trackKey = data.song.videoId, modifier = Modifier.fillMaxSize())
            }
            PlayerTheme.SIMPLE -> {
                val blur = rememberFullArtworkBlurImage(data.remoteArt)
                FullArtworkBlurBackdrop(image = blur, modifier = Modifier.fillMaxSize())
            }
            PlayerTheme.FLAT -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            PlayerTheme.GLOW -> PlayerGlowBackdrop(imageUrl = data.remoteArt, modifier = Modifier.fillMaxSize())
        }

        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.union(stableStatusBarInsets()))
                .padding(horizontal = 24.dp),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                if (!hideThumbnail) {
                    DesignStyleArtwork(
                        song = data.song,
                        artRequest = data.artRequest,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .onSizeChanged { sleeveWidth = it.width }
                            .graphicsLayer { translationX = sleeveOffset.floatValue * 0.35f }
                            .styleSkipSwipe(
                                offsetState = sleeveOffset,
                                threshold = swipeThreshold,
                                enabled = !panels.lyricsOpen && !panels.queueOpen,
                                onSkipNext = data.onNext,
                                onSkipPrevious = data.onPrevious,
                                onBlocked = data.onBlockedControl,
                                haptics = data.haptics,
                            ),
                        shadow = 18.dp,
                        canvas = data.canvas,
                        isPlaying = data.isPlaying,
                        onCanvasRenderedChange = data.onLiveCanvasChanged,
                        onDoubleTap = { offset ->
                            val sign = if (sleeveWidth > 0 && offset.x < sleeveWidth / 2f) -1 else 1
                            val now = uptimeMillisCompat()
                            val step = if (sign == lastSeekSign && now - lastSeekMs < 1_200L) 10_000L else 5_000L
                            lastSeekSign = sign
                            lastSeekMs = now
                            data.onSeek((data.position.positionMs + step * sign).coerceIn(0L, data.durationMs))
                        },
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    MarqueeText(
                        text = data.song.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    MarqueeText(
                        text = data.song.artist,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    )
                }
                Spacer(Modifier.padding(horizontal = 8.dp))
                StyleIconButton(
                    icon = if (data.likeStatus == LikeStatus.LIKE) BitChordIcons.HeartFilled else BitChordIcons.Heart,
                    contentDescription = stringResource(Res.string.design_like),
                    onClick = { data.onToggleLike() },
                    tint = if (data.likeStatus == LikeStatus.LIKE) Color(0xFFFF4D67) else Color.White,
                )
                Spacer(Modifier.padding(horizontal = 6.dp))
                StyleIconButton(
                    icon = BitChordIcons.Queue,
                    contentDescription = stringResource(Res.string.design_queue),
                    onClick = { panels.toggleQueue() },
                    tint = Color.White,
                )
            }
            Spacer(Modifier.height(18.dp))
            StyleScrubber(
                value = data.shown(),
                durationMs = data.durationMs,
                onValueChange = data.onScrub,
                onValueChangeFinished = data.onScrubFinished,
                activeColor = Color.White.copy(alpha = 0.92f),
                inactiveColor = Color.White.copy(alpha = 0.26f),
                style = sliderStyle,
                animated = data.isPlaying && !reduceAnimation,
            ) {
                PlaybackQualityLabel(song = data.song, isLoading = data.isLoading)
            }
            Spacer(Modifier.height(10.dp))
            CinematicTransport(data)
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StyleIconButton(
                    icon = BitChordIcons.LyricsQuote,
                    contentDescription = null,
                    onClick = { panels.toggleLyrics() },
                    tint = Color.White,
                    size = 40.dp,
                )
                Spacer(Modifier.padding(horizontal = 10.dp))
                StyleIconButton(
                    icon = BitChordIcons.ChevronDown,
                    contentDescription = null,
                    onClick = data.onClose,
                    tint = Color.White,
                    size = 40.dp,
                )
                Spacer(Modifier.padding(horizontal = 10.dp))
                StyleIconButton(
                    icon = BitChordIcons.Plus,
                    contentDescription = stringResource(Res.string.design_more),
                    onClick = data.onOpenMenu,
                    tint = Color.White,
                    size = 40.dp,
                )
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
        scrim = Color.Black.copy(alpha = 0.55f),
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
        scrim = Color.Black.copy(alpha = 0.6f),
        headerColor = Color.White,
        onScrollingChange = { panels.queueScrolling = it },
        onDragActiveChange = data.onQueueDragActiveChange,
    )
}

/** A wall clock the double-tap multiplier reads, in the composition's own time. */
private fun uptimeMillisCompat(): Long = System.nanoTime() / 1_000_000L

/** The V4 transport: shuffle and repeat flanking the skips, the morphing play. */
@Composable
private fun CinematicTransport(data: DesignStylePlayerData) {
    val corner by animateDpAsState(
        targetValue = if (data.isPlaying) 28.dp else 44.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = 500f),
        label = "cinematicCorner",
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StyleIconButton(
            icon = BitChordIcons.Shuffle,
            contentDescription = null,
            onClick = { data.onToggleShuffle() },
            tint = if (data.shuffleEnabled) Color.White else Color.White.copy(alpha = 0.45f),
            size = 40.dp,
            enabled = !data.controlsLocked,
        )
        StyleSkipGlyph(data, Res.drawable.ic_player_previous, data.hasPrevious) { if (data.hasPrevious) data.onPrevious() }
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(corner))
                .background(Color.White)
                .clickable {
                    if (data.controlsLocked) data.onBlockedControl() else data.onPlayPause()
                },
            contentAlignment = Alignment.Center,
        ) {
            if (data.isLoading || data.audioVersionSwitching) {
                CircularProgressIndicator(color = Color.Black, strokeWidth = 3.dp, modifier = Modifier.size(34.dp))
            } else {
                Icon(
                    painter = org.jetbrains.compose.resources.painterResource(
                        if (data.isPlaying) Res.drawable.ic_player_pause else Res.drawable.ic_player_play,
                    ),
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(38.dp),
                )
            }
        }
        StyleSkipGlyph(data, Res.drawable.ic_player_next, data.hasNext) { if (data.hasNext) data.onNext() }
        StyleIconButton(
            icon = BitChordIcons.Repeat,
            contentDescription = null,
            onClick = { data.onCycleRepeat() },
            tint = when (data.repeatMode) {
                1 -> Color.White
                2 -> Color(0xFF7DD8FF)
                else -> Color.White.copy(alpha = 0.45f)
            },
            size = 40.dp,
            enabled = !data.controlsLocked,
        )
    }
}

/** A V4 side skip: the plain glyph, dimmed while there is nowhere to go. */
@Composable
private fun StyleSkipGlyph(
    data: DesignStylePlayerData,
    res: org.jetbrains.compose.resources.DrawableResource,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(50))
            .clickable(enabled = enabled && !data.controlsLocked) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = org.jetbrains.compose.resources.painterResource(res),
            contentDescription = null,
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.35f),
            modifier = Modifier.size(32.dp),
        )
    }
}
