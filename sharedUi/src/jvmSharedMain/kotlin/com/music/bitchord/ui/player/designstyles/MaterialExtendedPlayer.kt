package com.music.bitchord.ui.player.designstyles

import androidx.compose.animation.animateDpAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.settings.PlayerSliderStyle
import com.music.bitchord.ui.player.CanvasArtworkPlayer
import com.music.bitchord.sharedui.resources.Res
import com.music.bitchord.sharedui.resources.design_collapse
import com.music.bitchord.sharedui.resources.design_like
import com.music.bitchord.sharedui.resources.design_more
import com.music.bitchord.sharedui.resources.design_now_playing
import com.music.bitchord.sharedui.resources.design_pause
import com.music.bitchord.sharedui.resources.design_play
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
import kotlin.math.roundToInt

/**
 * ArchiveTune's V9 "Material Extended": the whole player a dynamic colour field
 * grown from the artwork, a 36dp-rounded square sleeve, the wavy slider, and
 * the weight-morphing transport — the pressed pill grows while its siblings
 * compress, and the play button's corners round 60→26 as it plays.
 */
@Composable
internal fun MaterialExtendedPlayerContent(
    data: DesignStylePlayerData,
    colors: DesignStyleColors,
    panels: DesignStylePanelState,
    sliderStyle: PlayerSliderStyle,
    reduceAnimation: Boolean,
) {
    // Which transport pill is pressed, for the weight morph.
    var pressedPill by remember { mutableStateOf(-1) }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.union(stableStatusBarInsets()))
                .padding(horizontal = 24.dp),
        ) {
            // The header: a circular collapse, the lyrics and queue as 22dp
            // rounds, the page's title between them.
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
                    background = colors.text.copy(alpha = 0.08f),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(Res.string.design_now_playing),
                    style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 1.sp),
                    color = colors.icon.copy(alpha = 0.7f),
                )
                Spacer(Modifier.weight(1f))
                StyleIconButton(
                    icon = BitChordIcons.LyricsQuote,
                    contentDescription = null,
                    onClick = { panels.toggleLyrics() },
                    tint = if (panels.lyricsOpen) colors.accent else colors.text,
                    size = 44.dp,
                    background = colors.text.copy(alpha = 0.08f),
                )
                Spacer(Modifier.padding(horizontal = 6.dp))
                StyleIconButton(
                    icon = BitChordIcons.Queue,
                    contentDescription = stringResource(Res.string.design_queue),
                    onClick = { panels.toggleQueue() },
                    tint = if (panels.queueOpen) colors.accent else colors.text,
                    size = 44.dp,
                    background = colors.text.copy(alpha = 0.08f),
                )
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .graphicsLayer {
                            val s = if (data.isPlaying) 1f else 0.96f
                            scaleX = s
                            scaleY = s
                        }
                        .clip(RoundedCornerShape(36.dp)),
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
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    MarqueeText(
                        text = data.song.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
                        color = colors.text,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    MarqueeText(
                        text = data.song.artist,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                        color = colors.icon.copy(alpha = 0.65f),
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    )
                }
                Spacer(Modifier.padding(horizontal = 10.dp))
                StyleIconButton(
                    icon = if (data.likeStatus == LikeStatus.LIKED) BitChordIcons.HeartFilled else BitChordIcons.Heart,
                    contentDescription = stringResource(Res.string.design_like),
                    onClick = { data.onToggleLike() },
                    tint = if (data.likeStatus == LikeStatus.LIKED) colors.accent else colors.text,
                    size = 48.dp,
                )
            }
            Spacer(Modifier.height(24.dp))
            // The wavy slider, 36dp tall as the Expressive one is.
            ThinSlider(
                value = data.shown(),
                onValueChange = data.onScrub,
                onValueChangeFinished = data.onScrubFinished,
                style = PlayerSliderStyle.WAVY,
                animated = data.isPlaying && !reduceAnimation,
                idleHeight = 10.dp,
                activeHeight = 14.dp,
                activeColor = colors.accent,
                inactiveColor = colors.text.copy(alpha = 0.22f),
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = styleFormatTime((data.shown() * data.durationMs).toLong()),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.icon.copy(alpha = 0.65f),
                )
                Text(
                    text = "-" + styleFormatTime(data.durationMs - (data.shown() * data.durationMs).toLong()),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.icon.copy(alpha = 0.65f),
                )
            }
            Spacer(Modifier.height(20.dp))
            V9Transport(data, colors, pressedPill) { pressedPill = it }
            Spacer(Modifier.height(12.dp))
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
        scrim = colors.bg.copy(alpha = 0.96f),
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
        scrim = colors.bg.copy(alpha = 0.96f),
        headerColor = colors.text,
        onScrollingChange = { panels.queueScrolling = it },
        onDragActiveChange = data.onQueueDragActiveChange,
    )
}

/**
 * V9's transport: three pills in one row, the pressed one growing
 * (weight 1.1) while the others compress (0.65), releasing with a spring.
 */
@Composable
private fun V9Transport(
    data: DesignStylePlayerData,
    colors: DesignStyleColors,
    pressedPill: Int,
    onPillPress: (Int) -> Unit,
) {
    val playCorner by animateDpAsState(
        targetValue = if (data.isPlaying) 60.dp else 26.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = 380f),
        label = "v9PlayCorner",
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val prevWeight by androidx.compose.animation.core.animateFloatAsState(
            targetValue = pillWeight(pressedPill, 0),
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 500f),
            label = "v9PrevWeight",
        )
        Box(Modifier.weight(prevWeight)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(colors.text.copy(alpha = 0.08f))
                    .v9Press(0, onPillPress) {
                        if (data.hasPrevious) data.onPrevious()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_player_previous),
                    contentDescription = null,
                    tint = if (data.hasPrevious) colors.text else colors.text.copy(alpha = 0.35f),
                    modifier = Modifier.size(34.dp),
                )
            }
        }
        Spacer(Modifier.padding(horizontal = 8.dp))
        val playWeight by androidx.compose.animation.core.animateFloatAsState(
            targetValue = pillWeight(pressedPill, 1),
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 500f),
            label = "v9PlayWeight",
        )
        Box(Modifier.weight(playWeight)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(RoundedCornerShape(playCorner))
                    .background(colors.accent)
                    .v9Press(1, onPillPress) {
                        if (data.controlsLocked) data.onBlockedControl() else data.onPlayPause()
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (data.isLoading || data.audioVersionSwitching) {
                    CircularProgressIndicator(color = colors.bg, strokeWidth = 3.dp, modifier = Modifier.size(30.dp))
                } else {
                    Icon(
                        painter = painterResource(if (data.isPlaying) Res.drawable.ic_player_pause else Res.drawable.ic_player_play),
                        contentDescription = null,
                        tint = colors.bg,
                        modifier = Modifier.size(38.dp),
                    )
                }
            }
        }
        Spacer(Modifier.padding(horizontal = 8.dp))
        val nextWeight by androidx.compose.animation.core.animateFloatAsState(
            targetValue = pillWeight(pressedPill, 2),
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 500f),
            label = "v9NextWeight",
        )
        Box(Modifier.weight(nextWeight)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(colors.text.copy(alpha = 0.08f))
                    .v9Press(2, onPillPress) {
                        if (data.hasNext) data.onNext()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_player_next),
                    contentDescription = null,
                    tint = if (data.hasNext) colors.text else colors.text.copy(alpha = 0.35f),
                    modifier = Modifier.size(34.dp),
                )
            }
        }
    }
}

/** 1.1 while [index] is the pressed pill, 0.65 while another is, 1 at rest. */
private fun pillWeight(pressedPill: Int, index: Int): Float = when {
    pressedPill < 0 -> 1f
    pressedPill == index -> 1.1f
    else -> 0.65f
}

/**
 * A pill that reports its press for the weight morph and still takes the tap:
 * [detectTapGestures]'s own press scope, pressed on touch-down and released
 * under the finger, with the tap landing on top of it.
 */
private fun Modifier.v9Press(
    index: Int,
    onPillPress: (Int) -> Unit,
    onTap: () -> Unit,
): Modifier = this.then(
    Modifier.pointerInput(index) {
        androidx.compose.foundation.gestures.detectTapGestures(
            onTap = { onTap() },
            onPress = {
                onPillPress(index)
                tryAwaitRelease()
                onPillPress(-1)
            },
        )
    },
)
