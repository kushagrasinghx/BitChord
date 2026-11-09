package com.music.bitchord.ui.player.designstyles

import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.Song
import com.music.bitchord.sharedui.resources.Res
import com.music.bitchord.sharedui.resources.design_collapse
import com.music.bitchord.sharedui.resources.design_like
import com.music.bitchord.sharedui.resources.design_more
import com.music.bitchord.sharedui.resources.design_now_playing
import com.music.bitchord.sharedui.resources.design_queue
import com.music.bitchord.sharedui.resources.ic_player_pause
import com.music.bitchord.sharedui.resources.ic_player_play
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.ui.player.MarqueeText
import com.music.bitchord.ui.player.MeshGradientBackground
import com.music.bitchord.ui.player.PlayerSettings
import com.music.bitchord.ui.player.ThinSlider
import com.music.bitchord.ui.player.rememberArtworkColors
import com.music.bitchord.ui.theme.stableStatusBarInsets
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

/**
 * ArchiveTune's "TikTok" player: the queue itself as a vertical feed. Each
 * page is its own track — a mesh backdrop grown from that page's cover, the
 * square sleeve centred with a shadow, a tap to play, a double-tap to like
 * with a heart burst, the right rail with the like, lyrics, queue and share,
 * and the caption running along the bottom above the feed's own 3dp scrubber.
 * Settling on a page asks the player for that track; ±1 steps the queue the
 * way the transport does.
 */
@Composable
internal fun TikTokPlayerContent(
    data: DesignStylePlayerData,
    colors: DesignStyleColors,
    panels: DesignStylePanelState,
    reduceAnimation: Boolean,
    windowWidth: Dp,
    windowHeight: Dp,
) {
    val mainLyrics by PlayerSettings.tikTokMainLyrics.collectAsStateWithLifecycle()
    val pageCount = data.queue.size.coerceAtLeast(1)
    val pagerState = rememberPagerState(initialPage = data.queueIndex.coerceIn(0, pageCount - 1)) { pageCount }

    // Settling on a page is the feed's way of asking for that track. One step
    // either way reads as the transport's skip; anything further jumps.
    LaunchedEffect(pagerState, data.queueIndex) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (page != data.queueIndex && !pagerState.isScrollInProgress) {
                when (page - data.queueIndex) {
                    1 -> {
                        data.haptics.play(Haptic.SkipNext)
                        data.onNext()
                    }
                    -1 -> {
                        data.haptics.play(Haptic.SkipPrevious)
                        data.onPrevious()
                    }
                    else -> data.onJumpTo(page.coerceIn(0, pageCount - 1))
                }
            }
        }
    }
    // A track changed from outside the feed — the notification shade, the
    // queue overlay — brings the feed along rather than letting it drift.
    LaunchedEffect(data.queueIndex) {
        if (!pagerState.isScrollInProgress && pagerState.settledPage != data.queueIndex) {
            pagerState.scrollToPage(data.queueIndex.coerceIn(0, pageCount - 1))
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val pageSong = data.queue.getOrNull(page) ?: data.song
            val isCurrent = page == data.queueIndex
            TikTokSongPage(
                song = pageSong,
                artRequest = if (isCurrent) data.artRequest else null,
                isCurrent = isCurrent,
                isPlaying = data.isPlaying,
                isLoading = data.isLoading || data.audioVersionSwitching,
                likeStatus = if (isCurrent) data.likeStatus else LikeStatus.INDIFFERENT,
                onToggleLike = data.onToggleLike,
                onPlayPause = data.onPlayPause,
                onOpenLyrics = { if (isCurrent) panels.toggleLyrics() },
                onOpenQueue = { if (isCurrent) panels.toggleQueue() },
                onOpenMenu = { if (isCurrent) data.onOpenMenu },
                mainLyric = if (isCurrent && mainLyrics) {
                    currentLyricText(data.displayedLyrics, data.position.positionMs)
                } else {
                    null
                },
                onCanvasRenderedChange = data.onLiveCanvasChanged,
                canvas = if (isCurrent) data.canvas else null,
            )
        }

        // The feed's own chrome: the top row and the scrubber, over the pages.
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.union(stableStatusBarInsets()))
                .padding(horizontal = 16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StyleIconButton(
                    icon = BitChordIcons.ChevronDown,
                    contentDescription = stringResource(Res.string.design_collapse),
                    onClick = data.onClose,
                    tint = Color.White,
                    size = 40.dp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(Res.string.design_now_playing),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Spacer(Modifier.weight(1f))
                StyleIconButton(
                    icon = BitChordIcons.Plus,
                    contentDescription = stringResource(Res.string.design_more),
                    onClick = data.onOpenMenu,
                    tint = Color.White,
                    size = 40.dp,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = styleFormatTime((data.shown() * data.durationMs).toLong()) + "  ·  " +
                    styleFormatTime(data.durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.padding(bottom = 6.dp),
            )
            ThinSlider(
                value = data.shown(),
                onValueChange = data.onScrub,
                onValueChangeFinished = data.onScrubFinished,
                idleHeight = 3.dp,
                activeHeight = 3.dp,
                activeColor = Color.White,
                inactiveColor = Color.White.copy(alpha = 0.28f),
            )
            Spacer(Modifier.height(14.dp))
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

/** One feed page: the mesh, the sleeve, the rail, the caption. */
@Composable
private fun TikTokSongPage(
    song: Song,
    artRequest: coil3.request.ImageRequest?,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isLoading: Boolean,
    likeStatus: LikeStatus,
    onToggleLike: () -> Unit,
    onPlayPause: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenMenu: () -> Unit,
    mainLyric: String?,
    canvas: com.music.bitchord.data.canvas.CanvasArtwork?,
    onCanvasRenderedChange: (Boolean) -> Unit,
) {
    val palette = rememberArtworkColors(song.thumbnailUrl)
    // The heart burst a double-tap leaves behind, growing and fading.
    var burst by remember { mutableStateOf(0) }

    Box(Modifier.fillMaxSize()) {
        MeshGradientBackground(palette = palette, trackKey = song.videoId, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)))

        // The sleeve, centred, its own tap the play control and its double-tap
        // the like — with the burst to say it landed.
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .aspectRatio(1f),
        ) {
            DesignStyleArtwork(
                song = song,
                artRequest = artRequest ?: coil3.request.ImageRequest.Builder(
                    coil3.compose.LocalPlatformContext.current,
                ).data(song.thumbnailUrl).build(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val s = if (isCurrent && isPlaying) 1.04f else 1f
                        scaleX = s
                        scaleY = s
                    },
                shadow = 18.dp,
                canvas = canvas,
                isPlaying = isPlaying,
                onCanvasRenderedChange = onCanvasRenderedChange,
                onTap = { if (isCurrent) onPlayPause() },
                onDoubleTap = {
                    if (isCurrent) {
                        onToggleLike()
                        burst++
                    }
                },
            )
            if (burst > 0) {
                val heartScale = remember { Animatable(0f) }
                LaunchedEffect(burst) {
                    heartScale.snapTo(0.4f)
                    heartScale.animateTo(1.4f, tween(420))
                    heartScale.animateTo(0f, tween(240))
                }
                Icon(
                    imageVector = if (likeStatus == LikeStatus.LIKE) BitChordIcons.HeartFilled else BitChordIcons.Heart,
                    contentDescription = stringResource(Res.string.design_like),
                    tint = Color(0xFFFC3C44),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(96.dp)
                        .graphicsLayer {
                            scaleX = heartScale.value
                            scaleY = heartScale.value
                            alpha = heartScale.value.coerceIn(0f, 1f)
                        },
                )
            }
            if (isCurrent && isLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 3.dp,
                    modifier = Modifier.align(Alignment.Center).size(36.dp),
                )
            }
        }

        // The right rail: like, lyrics, queue, share, washed toward the bottom.
        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 10.dp, bottom = 150.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RailGlyph(
                icon = if (likeStatus == LikeStatus.LIKE) BitChordIcons.HeartFilled else BitChordIcons.Heart,
                label = null,
                tint = if (likeStatus == LikeStatus.LIKE) Color(0xFFFC3C44) else Color.White,
            ) { onToggleLike() }
            Spacer(Modifier.height(20.dp))
            RailGlyph(BitChordIcons.LyricsQuote, null, Color.White) { onOpenLyrics() }
            Spacer(Modifier.height(20.dp))
            RailGlyph(BitChordIcons.Queue, stringResource(Res.string.design_queue), Color.White) { onOpenQueue() }
            Spacer(Modifier.height(20.dp))
            RailGlyph(BitChordIcons.Plus, stringResource(Res.string.design_more), Color.White) { onOpenMenu() }
        }

        // The caption: the title and artist along the bottom, with the sung
        // line above it when the karaoke strip is on.
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, end = 84.dp, bottom = 64.dp),
        ) {
            if (mainLyric != null) {
                Text(
                    text = mainLyric,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    maxLines = 1,
                )
                Spacer(Modifier.height(8.dp))
            }
            MarqueeText(
                text = song.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp),
                color = Color.White,
                modifier = Modifier.fillMaxWidth(),
            )
            MarqueeText(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            )
        }
    }
}

/** A rail glyph: the icon, big and plain, with a tap of its own. */
@Composable
private fun RailGlyph(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String?,
    tint: Color,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(30.dp),
            )
        }
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
