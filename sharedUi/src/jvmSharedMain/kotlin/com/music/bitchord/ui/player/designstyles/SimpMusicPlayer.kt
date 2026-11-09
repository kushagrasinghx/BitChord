package com.music.bitchord.ui.player.designstyles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.sharedui.resources.Res
import com.music.bitchord.sharedui.resources.design_artist_card
import com.music.bitchord.sharedui.resources.design_collapse
import com.music.bitchord.sharedui.resources.design_info_album
import com.music.bitchord.sharedui.resources.design_info_card
import com.music.bitchord.sharedui.resources.design_info_duration
import com.music.bitchord.sharedui.resources.design_info_index
import com.music.bitchord.sharedui.resources.design_like
import com.music.bitchord.sharedui.resources.design_lyrics_card
import com.music.bitchord.sharedui.resources.design_lyrics_empty
import com.music.bitchord.sharedui.resources.design_more
import com.music.bitchord.sharedui.resources.design_now_playing
import com.music.bitchord.sharedui.resources.design_queue
import com.music.bitchord.sharedui.resources.ic_player_next
import com.music.bitchord.sharedui.resources.ic_player_pause
import com.music.bitchord.sharedui.resources.ic_player_play
import com.music.bitchord.sharedui.resources.ic_player_previous
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.ui.player.MarqueeText
import com.music.bitchord.ui.player.ThinSlider
import com.music.bitchord.ui.theme.stableStatusBarInsets
import com.music.bitchord.ui.player.rememberArtworkColors
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * ArchiveTune's "SimpMusic" player: a page you scroll. The hero is exactly one
 * screen tall — the top bar, a horizontally paged sleeve that swipes the queue,
 * the one-line lyric in the gap, the track info, the bar and the transport —
 * and below the fold the lyric card, the artist card and the info card, as
 * SimpMusic stacks them. The background is the hero wash: a diagonal of the
 * artwork's colours fading down into the fixed dark.
 */
@Composable
internal fun SimpMusicPlayerContent(
    data: DesignStylePlayerData,
    colors: DesignStyleColors,
    panels: DesignStylePanelState,
    reduceAnimation: Boolean,
) {
    val backdrop = Color(0xFF121212)
    val onBackdrop = Color(0xFFEDEDED)
    val palette = rememberArtworkColors(data.remoteArt)
    val wash = remember(palette) {
        val c0 = (palette.colors.getOrNull(0) ?: Color(0xFF243447)).let { darkenForWash(it) }
        val c1 = (palette.colors.getOrNull(1) ?: Color(0xFF143042)).let { darkenForWash(it) }
        Brush.linearGradient(listOf(c0, c1))
    }

    Box(Modifier.fillMaxSize().background(backdrop)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ---- The hero: one screen's worth of the page ----
            item(key = "hero") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(wash)
                        .windowInsetsPadding(WindowInsets.safeDrawing.union(stableStatusBarInsets()))
                        .padding(horizontal = 20.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StyleIconButton(
                            icon = BitChordIcons.ChevronDown,
                            contentDescription = stringResource(Res.string.design_collapse),
                            onClick = data.onClose,
                            tint = onBackdrop,
                            size = 38.dp,
                        )
                        Text(
                            text = stringResource(Res.string.design_now_playing).uppercase(),
                            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
                            color = onBackdrop.copy(alpha = 0.6f),
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        )
                        StyleIconButton(
                            icon = BitChordIcons.Plus,
                            contentDescription = stringResource(Res.string.design_more),
                            onClick = data.onOpenMenu,
                            tint = onBackdrop,
                            size = 38.dp,
                        )
                    }
                    // The paged sleeve: a swipe between neighbouring tracks.
                    val pageCount = data.queue.size.coerceAtLeast(1)
                    val pagerState = rememberPagerState(
                        initialPage = data.queueIndex.coerceIn(0, pageCount - 1),
                    ) { pageCount }
                    LaunchedEffect(pagerState, data.queueIndex) {
                        snapshotFlow { pagerState.settledPage }.collect { page ->
                            if (page != data.queueIndex && !pagerState.isScrollInProgress) {
                                when (page - data.queueIndex) {
                                    1 -> data.onNext()
                                    -1 -> data.onPrevious()
                                    else -> data.onJumpTo(page.coerceIn(0, pageCount - 1))
                                }
                            }
                        }
                    }
                    LaunchedEffect(data.queueIndex) {
                        if (!pagerState.isScrollInProgress && pagerState.settledPage != data.queueIndex) {
                            pagerState.scrollToPage(data.queueIndex.coerceIn(0, pageCount - 1))
                        }
                    }
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth(),
                    ) { page ->
                        val pageSong = data.queue.getOrNull(page) ?: data.song
                        val request = if (page == data.queueIndex) {
                            data.artRequest
                        } else {
                            ImageRequest.Builder(LocalPlatformContext.current).data(pageSong.thumbnailUrl).build()
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 26.dp)
                                .aspectRatio(1f)
                                .padding(vertical = 10.dp)
                                .shadow(16.dp, RoundedCornerShape(14.dp))
                                .clip(RoundedCornerShape(14.dp)),
                        ) {
                            AsyncImage(
                                model = request,
                                contentDescription = pageSong.title,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    // The one-line lyric in the gap under the sleeve.
                    Box(Modifier.fillMaxWidth().height(30.dp), contentAlignment = Alignment.Center) {
                        val line = currentLyricText(data.displayedLyrics, data.position.positionMs)
                        Text(
                            text = line ?: "",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = onBackdrop.copy(alpha = 0.8f),
                            maxLines = 1,
                        )
                    }
                    MarqueeText(
                        text = data.song.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
                        color = onBackdrop,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                    MarqueeText(
                        text = data.song.artist,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                        color = onBackdrop.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    ThinSlider(
                        value = data.shown(),
                        onValueChange = data.onScrub,
                        onValueChangeFinished = data.onScrubFinished,
                        style = com.music.bitchord.data.settings.PlayerSliderStyle.SIMPLE,
                        idleHeight = 4.dp,
                        activeHeight = 5.dp,
                        activeColor = onBackdrop,
                        inactiveColor = onBackdrop.copy(alpha = 0.25f),
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Text(
                            text = styleFormatTime((data.shown() * data.durationMs).toLong()),
                            style = MaterialTheme.typography.labelSmall,
                            color = onBackdrop.copy(alpha = 0.55f),
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = styleFormatTime(data.durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = onBackdrop.copy(alpha = 0.55f),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SimpGlyph(Res.drawable.ic_player_previous, onBackdrop, data.hasPrevious) {
                            if (data.hasPrevious) data.onPrevious()
                        }
                        Box(
                            Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(onBackdrop)
                                .clickable {
                                    if (data.controlsLocked) data.onBlockedControl() else data.onPlayPause()
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (data.isLoading || data.audioVersionSwitching) {
                                CircularProgressIndicator(color = backdrop, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                            } else {
                                Icon(
                                    painter = painterResource(if (data.isPlaying) Res.drawable.ic_player_pause else Res.drawable.ic_player_play),
                                    contentDescription = null,
                                    tint = backdrop,
                                    modifier = Modifier.size(34.dp),
                                )
                            }
                        }
                        SimpGlyph(Res.drawable.ic_player_next, onBackdrop, data.hasNext) {
                            if (data.hasNext) data.onNext()
                        }
                    }
                    // The action row under the transport.
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        StyleIconButton(
                            icon = if (data.likeStatus == LikeStatus.LIKE) BitChordIcons.HeartFilled else BitChordIcons.Heart,
                            contentDescription = stringResource(Res.string.design_like),
                            onClick = { data.onToggleLike() },
                            tint = if (data.likeStatus == LikeStatus.LIKE) Color(0xFFFC3C44) else onBackdrop,
                            size = 38.dp,
                        )
                        StyleIconButton(
                            icon = BitChordIcons.LyricsQuote,
                            contentDescription = null,
                            onClick = { panels.toggleLyrics() },
                            tint = onBackdrop,
                            size = 38.dp,
                        )
                        StyleIconButton(
                            icon = BitChordIcons.Queue,
                            contentDescription = stringResource(Res.string.design_queue),
                            onClick = { panels.toggleQueue() },
                            tint = onBackdrop,
                            size = 38.dp,
                        )
                        StyleIconButton(
                            icon = BitChordIcons.Shuffle,
                            contentDescription = null,
                            onClick = { data.onToggleShuffle() },
                            tint = if (data.shuffleEnabled) onBackdrop else onBackdrop.copy(alpha = 0.45f),
                            size = 38.dp,
                        )
                        StyleIconButton(
                            icon = BitChordIcons.Repeat,
                            contentDescription = null,
                            onClick = { data.onCycleRepeat() },
                            tint = when (data.repeatMode) {
                                1 -> onBackdrop
                                2 -> Color(0xFF7DD8FF)
                                else -> onBackdrop.copy(alpha = 0.45f)
                            },
                            size = 38.dp,
                        )
                    }
                }
            }

            // ---- Below the fold: the lyric card ----
            item(key = "lyrics-card") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF1E1E1E)),
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            text = stringResource(Res.string.design_lyrics_card),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = onBackdrop,
                        )
                        if (data.lyrics.isNullOrEmpty()) {
                            Text(
                                text = stringResource(Res.string.design_lyrics_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = onBackdrop.copy(alpha = 0.55f),
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        } else {
                            // The lines, the sung one bright, a tap seeks to it.
                            val positionMs = data.position.positionMs
                            val current = data.displayedLyrics.indexOfLast { it.timeMs <= positionMs + 200L }
                            val first = (current - 1).coerceAtLeast(0)
                            val last = minOf(first + 10, data.displayedLyrics.size)
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .height(220.dp)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                data.displayedLyrics.subList(first, last).forEachIndexed { i, line ->
                                    val index = first + i
                                    val sung = index <= current
                                    Text(
                                        text = line.text,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = if (index == current) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = if (index == current) 18.sp else 15.sp,
                                        ),
                                        color = if (sung) onBackdrop else onBackdrop.copy(alpha = 0.38f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { data.seekToLyric(line.timeMs) }
                                            .padding(vertical = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ---- The artist card ----
            item(key = "artist-card") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF1E1E1E)),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(56.dp)
                                .clip(CircleShape),
                        ) {
                            AsyncImage(
                                model = data.artRequest,
                                contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text(
                                text = data.song.artist,
                                style = MaterialTheme.typography.titleMedium,
                                color = onBackdrop,
                            )
                            Text(
                                text = stringResource(Res.string.design_artist_card),
                                style = MaterialTheme.typography.bodySmall,
                                color = onBackdrop.copy(alpha = 0.55f),
                            )
                        }
                        StyleIconButton(
                            icon = BitChordIcons.Search,
                            contentDescription = null,
                            onClick = { data.onOpenArtist(data.song.artistId, data.song.artist) },
                            tint = onBackdrop,
                            size = 38.dp,
                        )
                    }
                }
            }

            // ---- The info card ----
            item(key = "info-card") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF1E1E1E)),
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            text = stringResource(Res.string.design_info_card),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = onBackdrop,
                        )
                        data.song.albumName?.let { album ->
                            InfoRow(stringResource(Res.string.design_info_album), album, onBackdrop)
                        }
                        InfoRow(stringResource(Res.string.design_info_duration), styleFormatTime(data.durationMs), onBackdrop)
                        InfoRow(
                            stringResource(Res.string.design_info_index),
                            "${(data.queueIndex + 1).coerceAtLeast(1)} / ${data.queue.size}",
                            onBackdrop,
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }
                Spacer(Modifier.height(40.dp))
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
        scrim = backdrop.copy(alpha = 0.96f),
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
        scrim = backdrop.copy(alpha = 0.96f),
        headerColor = onBackdrop,
        onScrollingChange = { panels.queueScrolling = it },
        onDragActiveChange = data.onQueueDragActiveChange,
    )
}

/** A card row: the label dim on the left, the value bright on the right. */
@Composable
private fun InfoRow(label: String, value: String, onBackdrop: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = onBackdrop.copy(alpha = 0.55f),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = onBackdrop,
        )
    }
}

/** A SimpMusic side glyph: plain, generous, dimmed when it can't go. */
@Composable
private fun SimpGlyph(
    res: org.jetbrains.compose.resources.DrawableResource,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
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

/** Darkens a swatch for the hero wash, as SimpMusic's surfaces are dark. */
private fun darkenForWash(c: Color): Color = Color(
    red = c.red * 0.34f,
    green = c.green * 0.34f,
    blue = c.blue * 0.34f,
    alpha = 1f,
)
