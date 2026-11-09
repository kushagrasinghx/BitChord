package com.music.bitchord.ui.player.designstyles

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.sharedui.resources.Res
import com.music.bitchord.sharedui.resources.design_like
import com.music.bitchord.sharedui.resources.design_more
import com.music.bitchord.sharedui.resources.design_now_playing
import com.music.bitchord.sharedui.resources.design_queue
import com.music.bitchord.sharedui.resources.ic_player_next
import com.music.bitchord.sharedui.resources.ic_player_pause
import com.music.bitchord.sharedui.resources.ic_player_play
import com.music.bitchord.sharedui.resources.ic_player_previous
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.ui.player.MarqueeText
import com.music.bitchord.ui.player.PlaybackQualityLabel
import com.music.bitchord.ui.player.PlayerSettings
import com.music.bitchord.ui.player.FullArtworkBlurBackdrop
import com.music.bitchord.ui.player.ThinSlider
import com.music.bitchord.ui.player.rememberFullArtworkBlurImage
import com.music.bitchord.ui.theme.stableStatusBarInsets
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * ArchiveTune's V7 "Immersive": a sharp stage of artwork across the top of the
 * screen dissolving through a scrim into the blurred cover behind the rest, the
 * whole foreground white. The bar is V8's flat one — a thumbless track with the
 * remaining time — and the transport is plain glyphs in circles of nothing.
 */
@Composable
internal fun ImmersivePlayerContent(
    data: DesignStylePlayerData,
    colors: DesignStyleColors,
    panels: DesignStylePanelState,
    reduceAnimation: Boolean,
) {
    val hideVolumeBar by PlayerSettings.hideVolumeBar.collectAsStateWithLifecycle()
    val hideSlideBar by PlayerSettings.hideSlideBar.collectAsStateWithLifecycle()
    val blur = rememberFullArtworkBlurImage(data.remoteArt, blurIntensityDp = 44f)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // The blurred cover behind everything, oversized and dimmed, as V7's
        // backdrop is.
        FullArtworkBlurBackdrop(
            image = blur,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.18f
                    scaleY = 1.18f
                    alpha = 0.58f
                },
        )
        // The sharp stage: the artwork crisp across the top of the screen,
        // fading into the blur through a scrim laid over its own foot.
        Box(Modifier.fillMaxSize()) {
            Crossfade(
                targetState = data.song.videoId,
                animationSpec = tween(900),
                label = "immersiveStage",
            ) { _ ->
                AsyncImage(
                    model = data.artRequest,
                    contentDescription = data.song.title,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.55f to Color.Transparent,
                            0.78f to Color.Black.copy(alpha = 0.66f),
                            1f to Color.Black.copy(alpha = 0.94f),
                        ),
                    ),
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.union(stableStatusBarInsets()))
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.design_now_playing).uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
                    color = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.weight(1f),
                )
                StyleIconButton(
                    icon = BitChordIcons.Queue,
                    contentDescription = stringResource(Res.string.design_queue),
                    onClick = { panels.toggleQueue() },
                    tint = Color.White,
                    size = 40.dp,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    MarqueeText(
                        text = data.song.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    MarqueeText(
                        text = data.song.artist,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
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
                    size = 44.dp,
                )
                Spacer(Modifier.padding(horizontal = 4.dp))
                StyleIconButton(
                    icon = BitChordIcons.Plus,
                    contentDescription = stringResource(Res.string.design_more),
                    onClick = data.onOpenMenu,
                    tint = Color.White,
                    size = 44.dp,
                )
            }
            Spacer(Modifier.height(20.dp))
            if (!hideSlideBar) {
                // V8's flat slider: a thumbless 9dp track, the remaining time
                // on the right, the codec chip in the middle.
                ThinSlider(
                    value = data.shown(),
                    onValueChange = data.onScrub,
                    onValueChangeFinished = data.onScrubFinished,
                    idleHeight = 9.dp,
                    activeHeight = 9.dp,
                    activeColor = Color.White,
                    inactiveColor = Color.White.copy(alpha = 0.24f),
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = styleFormatTime((data.shown() * data.durationMs).toLong()),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.55f),
                    )
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        PlaybackQualityLabel(song = data.song, isLoading = data.isLoading)
                    }
                    Text(
                        text = "-" + styleFormatTime(data.durationMs - (data.shown() * data.durationMs).toLong()),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.55f),
                    )
                }
                Spacer(Modifier.height(18.dp))
            }
            // V8's transport: plain glyphs, the play one a hair larger.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ImmersiveGlyph(
                    res = Res.drawable.ic_player_previous,
                    enabled = data.hasPrevious && !data.controlsLocked,
                    size = 44.dp,
                ) { if (data.hasPrevious) data.onPrevious() }
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .clickable {
                            if (data.controlsLocked) data.onBlockedControl() else data.onPlayPause()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (data.isLoading || data.audioVersionSwitching) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(30.dp))
                    } else {
                        Icon(
                            painter = painterResource(if (data.isPlaying) Res.drawable.ic_player_pause else Res.drawable.ic_player_play),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
                ImmersiveGlyph(
                    res = Res.drawable.ic_player_next,
                    enabled = data.hasNext && !data.controlsLocked,
                    size = 44.dp,
                ) { if (data.hasNext) data.onNext() }
            }
            Spacer(Modifier.height(16.dp))
            if (!hideVolumeBar) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.VolumeDown,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.padding(horizontal = 10.dp))
                    ThinSlider(
                        value = data.volumeValue(),
                        onValueChange = data.onVolumeChange,
                        onValueChangeFinished = data.onVolumeChangeFinished,
                        idleHeight = 6.dp,
                        activeHeight = 9.dp,
                        activeColor = Color.White,
                        inactiveColor = Color.White.copy(alpha = 0.22f),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.padding(horizontal = 10.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
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

/** A V8 transport glyph: the plain icon, unboxed, dimmed when it can't go. */
@Composable
private fun ImmersiveGlyph(
    res: org.jetbrains.compose.resources.DrawableResource,
    enabled: Boolean,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size + 12.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(res),
            contentDescription = null,
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.35f),
            modifier = Modifier.size(size),
        )
    }
}
