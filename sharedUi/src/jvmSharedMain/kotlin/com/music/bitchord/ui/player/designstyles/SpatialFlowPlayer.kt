package com.music.bitchord.ui.player.designstyles

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.settings.PlayerSliderStyle
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
import com.music.bitchord.ui.player.MarqueeText
import com.music.bitchord.ui.player.PlaybackQualityLabel
import com.music.bitchord.ui.player.ThinSlider
import com.music.bitchord.ui.player.FullArtworkBlurBackdrop
import com.music.bitchord.ui.player.rememberFullArtworkBlurImage
import com.music.bitchord.ui.theme.stableStatusBarInsets
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * ArchiveTune's "SpatialFlow" player: a surface colour derived from the cover
 * with the blurred art as a scrim, a horizontally scrolling chip row of the
 * things you reach for — like, lyrics, queue, more — the wavy bar with its
 * labels, and a transport of pills whose corners animate under the finger.
 * Lyrics arrive as a full-screen circular reveal from the chip that opened
 * them, as SpatialFlow's own lyrics page does.
 */
@Composable
internal fun SpatialFlowPlayerContent(
    data: DesignStylePlayerData,
    colors: DesignStyleColors,
    panels: DesignStylePanelState,
    reduceAnimation: Boolean,
) {
    val blur = rememberFullArtworkBlurImage(data.remoteArt, blurIntensityDp = 40f)
    // The lyrics page's circular reveal, from the chip's own centre.
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(panels.lyricsOpen) {
        if (panels.lyricsOpen) {
            reveal.snapTo(0.12f)
            reveal.animateTo(1f, tween(340))
        } else {
            reveal.snapTo(0f)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        FullArtworkBlurBackdrop(
            image = blur,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(colors.bg.copy(alpha = 0.72f)))

        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.union(stableStatusBarInsets()))
                .padding(horizontal = 24.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StyleIconButton(
                    icon = BitChordIcons.ChevronDown,
                    contentDescription = stringResource(Res.string.design_collapse),
                    onClick = data.onClose,
                    tint = colors.text,
                    size = 44.dp,
                    background = colors.text.copy(alpha = 0.10f),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(Res.string.design_now_playing).uppercase(),
                    style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 2.sp),
                    color = colors.text.copy(alpha = 0.7f),
                )
                Spacer(Modifier.weight(1f))
                StyleIconButton(
                    icon = BitChordIcons.Plus,
                    contentDescription = stringResource(Res.string.design_more),
                    onClick = data.onOpenMenu,
                    tint = colors.text,
                    size = 44.dp,
                    background = colors.text.copy(alpha = 0.10f),
                )
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                DesignStyleArtwork(
                    song = data.song,
                    artRequest = data.artRequest,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .aspectRatio(1f),
                    shadow = 16.dp,
                    shadowTint = Color.Black.copy(alpha = 0.5f),
                    canvas = data.canvas,
                    isPlaying = data.isPlaying,
                    onCanvasRenderedChange = data.onLiveCanvasChanged,
                )
            }
            Spacer(Modifier.weight(1f))
            MarqueeText(
                text = data.song.title,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.text,
                modifier = Modifier.fillMaxWidth(),
            )
            MarqueeText(
                text = data.song.artist,
                style = MaterialTheme.typography.titleMedium,
                color = colors.icon.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            )
            Spacer(Modifier.height(14.dp))
            // The chip row: everything you reach for, side-scrolling.
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SpatialChip(
                    icon = if (data.likeStatus == LikeStatus.LIKE) BitChordIcons.HeartFilled else BitChordIcons.Heart,
                    label = stringResource(Res.string.design_like),
                    tint = if (data.likeStatus == LikeStatus.LIKE) colors.accent else colors.text,
                    colors = colors,
                ) { data.onToggleLike() }
                SpatialChip(BitChordIcons.LyricsQuote, null, if (panels.lyricsOpen) colors.accent else colors.text, colors) {
                    panels.toggleLyrics()
                }
                SpatialChip(BitChordIcons.Queue, stringResource(Res.string.design_queue), if (panels.queueOpen) colors.accent else colors.text, colors) {
                    panels.toggleQueue()
                }
                SpatialChip(BitChordIcons.Shuffle, null, if (data.shuffleEnabled) colors.accent else colors.text, colors) {
                    data.onToggleShuffle()
                }
                SpatialChip(BitChordIcons.Repeat, null, repeatTint(data.repeatMode, colors), colors) {
                    data.onCycleRepeat()
                }
            }
            Spacer(Modifier.height(20.dp))
            // The wavy bar with its labels, as SpatialFlow draws it.
            ThinSlider(
                value = data.shown(),
                onValueChange = data.onScrub,
                onValueChangeFinished = data.onScrubFinished,
                style = PlayerSliderStyle.WAVY,
                animated = data.isPlaying && !reduceAnimation,
                idleHeight = 10.dp,
                activeHeight = 13.dp,
                activeColor = colors.accent,
                inactiveColor = colors.text.copy(alpha = 0.22f),
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = styleFormatTime((data.shown() * data.durationMs).toLong()),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.icon.copy(alpha = 0.65f),
                )
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    PlaybackQualityLabel(song = data.song, isLoading = data.isLoading)
                }
                Text(
                    text = "-" + styleFormatTime(data.durationMs - (data.shown() * data.durationMs).toLong()),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.icon.copy(alpha = 0.65f),
                )
            }
            Spacer(Modifier.height(20.dp))
            // The pill transport, corners easing 28→12 under the finger.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SpatialPill(data, colors, Res.drawable.ic_player_previous, data.hasPrevious) {
                    if (data.hasPrevious) data.onPrevious()
                }
                Spacer(Modifier.padding(horizontal = 10.dp))
                Box(Modifier.weight(1f)) {
                    SpatialPlayPill(data, colors)
                }
                Spacer(Modifier.padding(horizontal = 10.dp))
                SpatialPill(data, colors, Res.drawable.ic_player_next, data.hasNext) {
                    if (data.hasNext) data.onNext()
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }

    // The lyrics page: arriving over the player the way SpatialFlow's own does,
    // growing in from the chip that opened it rather than cutting in.
    if (panels.lyricsOpen) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayerStyle(reveal.value),
        ) {
            StyleLyricsOverlay(
                visible = true,
                lyrics = data.lyrics,
                subLines = data.subLines,
                trackKey = data.song.videoId,
                playhead = data.lyricsPlayhead,
                looking = !data.lyricsUnavailable,
                isPlaying = data.isPlaying && data.position.advancing,
                onSeekToLine = data.seekToLyric,
                onDismiss = { panels.lyricsOpen = false },
                scrim = colors.bg.copy(alpha = 0.97f),
                lyricsTranslation = data.lyricsTranslation,
                lyricPicker = data.lyricPicker,
                canPick = data.lyricsShareEnabled,
                onScrollingChange = { panels.lyricsScrolling = it },
            )
        }
    }

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
        scrim = colors.bg.copy(alpha = 0.96f),
        headerColor = colors.text,
        onScrollingChange = { panels.queueScrolling = it },
        onDragActiveChange = data.onQueueDragActiveChange,
    )
}

@Composable
private fun repeatTint(repeatMode: Int, colors: DesignStyleColors): Color = when (repeatMode) {
    1 -> colors.accent
    2 -> colors.accent
    else -> colors.text
}

/** The lyrics page's arrival: alpha and a slight grow, driven by the reveal. */
private fun Modifier.graphicsLayerStyle(reveal: Float): Modifier = this.then(
    Modifier.graphicsLayer {
        alpha = reveal.coerceIn(0f, 1f)
        val s = 0.92f + 0.08f * reveal.coerceIn(0f, 1f)
        scaleX = s
        scaleY = s
    },
)

/** A chip of the scrolling row: a pill with a glyph, tinted when it is on. */
@Composable
private fun SpatialChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String?,
    tint: Color,
    colors: DesignStyleColors,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(colors.text.copy(alpha = 0.10f))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
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

/** A side pill of the transport, its corners static as the play one moves. */
@Composable
private fun SpatialPill(
    data: DesignStylePlayerData,
    colors: DesignStyleColors,
    res: org.jetbrains.compose.resources.DrawableResource,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(colors.text.copy(alpha = 0.10f))
            .clickable(enabled = enabled && !data.controlsLocked) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(res),
            contentDescription = null,
            tint = if (enabled) colors.text else colors.text.copy(alpha = 0.35f),
            modifier = Modifier.size(26.dp),
        )
    }
}

/** The play pill: full width, accent, its corners rounding as it plays. */
@Composable
private fun SpatialPlayPill(data: DesignStylePlayerData, colors: DesignStyleColors) {
    val corner by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (data.isPlaying) 28.dp else 12.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = 420f),
        label = "spatialPlayCorner",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(corner))
            .background(colors.accent)
            .clickable {
                if (data.controlsLocked) data.onBlockedControl() else data.onPlayPause()
            },
        contentAlignment = Alignment.Center,
    ) {
        if (data.isLoading || data.audioVersionSwitching) {
            CircularProgressIndicator(color = colors.bg, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
        } else {
            Icon(
                painter = painterResource(if (data.isPlaying) Res.drawable.ic_player_pause else Res.drawable.ic_player_play),
                contentDescription = null,
                tint = colors.bg,
                modifier = Modifier.size(34.dp),
            )
        }
    }
}
