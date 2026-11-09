package com.music.bitchord.ui.player.designstyles

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.sharedui.resources.Res
import com.music.bitchord.sharedui.resources.design_collapse
import com.music.bitchord.sharedui.resources.design_like
import com.music.bitchord.sharedui.resources.design_more
import com.music.bitchord.sharedui.resources.design_now_playing
import com.music.bitchord.sharedui.resources.design_queue
import com.music.bitchord.sharedui.resources.ic_player_next
import com.music.bitchord.sharedui.resources.ic_player_pause
import com.music.bitchord.sharedui.resources.ic_player_play
import com.music.bitchord.sharedui.resources.ic_player_previous
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.ui.player.CanvasArtworkPlayer
import com.music.bitchord.ui.player.MarqueeText
import com.music.bitchord.ui.player.OutputCaption
import com.music.bitchord.ui.player.PlayerSettings
import com.music.bitchord.ui.player.FullArtworkBlurBackdrop
import com.music.bitchord.ui.player.ThinSlider
import com.music.bitchord.ui.player.rememberFullArtworkBlurImage
import com.music.bitchord.ui.theme.stableStatusBarInsets
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * ArchiveTune's "Apple Music" player, ported from the Flamingo design: the
 * floating-light background (the cover saturated, darkened and blurred behind
 * everything), the album in the whole upper region shrinking a little as it
 * pauses, the 19.5sp credits, a thin track with a thumb dot, and the volume
 * slider under the transport. Lyrics and the playing list are overlays, as
 * Flamingo's own pages are.
 */
@Composable
internal fun AppleMusicPlayerContent(
    data: DesignStylePlayerData,
    colors: DesignStyleColors,
    panels: DesignStylePanelState,
    reduceAnimation: Boolean,
) {
    val hideVolumeBar by PlayerSettings.hideVolumeBar.collectAsStateWithLifecycle()
    val movingBlur by PlayerSettings.movingBlur.collectAsStateWithLifecycle()
    val hideSlideBar by PlayerSettings.hideSlideBar.collectAsStateWithLifecycle()
    val blur = rememberFullArtworkBlurImage(data.remoteArt, blurIntensityDp = 56f)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // The floating light: the cover, blurred hard, dimmed to keep the
        // foreground's white ink readable — Flamingo's background, moving with
        // the optional Ken Burns drift where it is on.
        FullArtworkBlurBackdrop(
            image = blur,
            modifier = Modifier.fillMaxSize(),
            drift = movingBlur && !reduceAnimation,
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f)))

        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.union(stableStatusBarInsets()))
                .padding(horizontal = 24.dp),
        ) {
            // Flamingo's 4.5dp slide bar at the very top.
            Box(
                Modifier
                    .padding(vertical = 6.dp)
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f))
                    .clickable { data.onClose() },
            )
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.design_now_playing),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                StyleIconButton(
                    icon = BitChordIcons.ChevronDown,
                    contentDescription = stringResource(Res.string.design_collapse),
                    onClick = data.onClose,
                    tint = Color.White.copy(alpha = 0.8f),
                    size = 36.dp,
                )
            }
            Spacer(Modifier.weight(1f))
            // The album, filling what is left and shrinking as it pauses —
            // the padding grows 7→34dp, the sleeve with it.
            val albumPad by androidx.compose.animation.core.animateDpAsState(
                targetValue = if (data.isPlaying) 7.dp else 34.dp,
                animationSpec = tween(300),
                label = "flamingoAlbumPad",
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .padding(albumPad)
                        .shadow(20.dp, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp)),
                ) {
                    AsyncImage(
                        model = data.artRequest,
                        contentDescription = data.song.title,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (data.canvas != null) {
                        com.music.bitchord.ui.player.CanvasArtworkPlayer(
                            canvas = data.canvas,
                            isPlaying = data.isPlaying,
                            modifier = Modifier.fillMaxSize(),
                            onRenderedChanged = data.onLiveCanvasChanged,
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            // The credits: 19.5/18.5sp, as Flamingo sizes them.
            MarqueeText(
                text = data.song.title,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.5.sp, fontWeight = FontWeight.SemiBold),
                color = Color.White,
                modifier = Modifier.fillMaxWidth(),
            )
            MarqueeText(
                text = data.song.artist,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.5.sp),
                color = Color.White.copy(alpha = 0.64f),
                modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
            )
            Spacer(Modifier.height(20.dp))
            if (!hideSlideBar) {
                // Flamingo's track: a thin bar with a thumb dot riding it.
                ThinSlider(
                    value = data.shown(),
                    onValueChange = data.onScrub,
                    onValueChangeFinished = data.onScrubFinished,
                    idleHeight = 7.dp,
                    activeHeight = 7.dp,
                    activeColor = Color.White,
                    inactiveColor = Color.White.copy(alpha = 0.24f),
                )
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(
                        text = styleFormatTime((data.shown() * data.durationMs).toLong()),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.55f),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "-" + styleFormatTime(data.durationMs - (data.shown() * data.durationMs).toLong()),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.55f),
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            // The transport: prev, the big play, next, then the like.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FlamingoGlyph(Res.drawable.ic_player_previous, data.hasPrevious && !data.controlsLocked) {
                    if (data.hasPrevious) data.onPrevious()
                }
                Box(
                    Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .clickable {
                            if (data.controlsLocked) data.onBlockedControl() else data.onPlayPause()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (data.isLoading || data.audioVersionSwitching) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                    } else {
                        Icon(
                            painter = painterResource(if (data.isPlaying) Res.drawable.ic_player_pause else Res.drawable.ic_player_play),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
                FlamingoGlyph(Res.drawable.ic_player_next, data.hasNext && !data.controlsLocked) {
                    if (data.hasNext) data.onNext()
                }
                StyleIconButton(
                    icon = if (data.likeStatus == LikeStatus.LIKED) BitChordIcons.HeartFilled else BitChordIcons.Heart,
                    contentDescription = stringResource(Res.string.design_like),
                    onClick = { data.onToggleLike() },
                    tint = if (data.likeStatus == LikeStatus.LIKED) Color(0xFFFC3C44) else Color.White,
                    size = 44.dp,
                )
            }
            if (!hideVolumeBar) {
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Rounded.VolumeDown,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.padding(horizontal = 10.dp))
                    ThinSlider(
                        value = data.volumeValue(),
                        onValueChange = data.onVolumeChange,
                        onValueChangeFinished = data.onVolumeChangeFinished,
                        idleHeight = 6.dp,
                        activeHeight = 8.dp,
                        activeColor = Color.White,
                        inactiveColor = Color.White.copy(alpha = 0.22f),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.padding(horizontal = 10.dp))
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            // Flamingo's action row: lyrics, the playing list, the output, more.
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StyleIconButton(
                    icon = BitChordIcons.LyricsQuote,
                    contentDescription = null,
                    onClick = { panels.toggleLyrics() },
                    tint = if (panels.lyricsOpen) Color.White else Color.White.copy(alpha = 0.75f),
                    size = 42.dp,
                )
                StyleIconButton(
                    icon = BitChordIcons.Queue,
                    contentDescription = stringResource(Res.string.design_queue),
                    onClick = { panels.toggleQueue() },
                    tint = if (panels.queueOpen) Color.White else Color.White.copy(alpha = 0.75f),
                    size = 42.dp,
                )
                StyleIconButton(
                    icon = BitChordIcons.MusicNote,
                    contentDescription = null,
                    onClick = data.onOpenOutput,
                    tint = Color.White.copy(alpha = 0.75f),
                    size = 42.dp,
                )
                StyleIconButton(
                    icon = BitChordIcons.Plus,
                    contentDescription = stringResource(Res.string.design_more),
                    onClick = data.onOpenMenu,
                    tint = Color.White.copy(alpha = 0.75f),
                    size = 42.dp,
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
        scrim = Color.Black.copy(alpha = 0.72f),
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
        scrim = Color.Black.copy(alpha = 0.72f),
        headerColor = Color.White,
        onScrollingChange = { panels.queueScrolling = it },
        onDragActiveChange = data.onQueueDragActiveChange,
    )
}

/** A Flamingo transport glyph: plain, generous, dimmed when it can't go. */
@Composable
private fun FlamingoGlyph(
    res: org.jetbrains.compose.resources.DrawableResource,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(res),
            contentDescription = null,
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.35f),
            modifier = Modifier.size(34.dp),
        )
    }
}
