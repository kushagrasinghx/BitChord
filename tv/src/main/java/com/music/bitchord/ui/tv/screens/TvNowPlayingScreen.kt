package com.music.bitchord.ui.tv.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.music.bitchord.ui.tv.player.TvLyricsList
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.music.bitchord.data.LikeState
import com.music.bitchord.data.NerdStats
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.playback.PlayerState
import com.music.bitchord.ui.MainViewModel
import com.music.bitchord.ui.tv.components.TvButton
import com.music.bitchord.ui.tv.components.TvIconButton
import com.music.bitchord.ui.tv.components.TvSlider
import com.music.bitchord.ui.tv.focus.onTvKeyEvent
import com.music.bitchord.ui.tv.focus.tvButtonFocus
import com.music.bitchord.ui.tv.theme.TvColors
import com.music.bitchord.ui.tv.theme.TvDimensions
import com.music.bitchord.ui.tv.theme.TvSFProDisplay

private enum class TvNowPlayingSidePanel {
    NONE,
    LYRICS,
    QUEUE,
    STATS,
}

@Composable
fun TvNowPlayingScreen(
    viewModel: MainViewModel,
    mediaController: MediaController?,
    playerState: PlayerState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val song = playerState.song
    val isPlaying = playerState.isPlaying
    val currentPositionMs = playerState.position.positionMs
    val durationMs = playerState.durationMs
    val repeatMode = playerState.repeatMode

    val likeOverrides by LikeState.overrides.collectAsState()
    val isLiked = song?.let { likeOverrides[it.videoId] == LikeStatus.LIKE } ?: false

    val lyrics by viewModel.lyrics.collectAsState()
    var sidePanel by remember { mutableStateOf(TvNowPlayingSidePanel.NONE) }

    LaunchedEffect(song?.videoId, durationMs) {
        if (song != null) {
            viewModel.loadLyrics(
                videoId = song.videoId,
                title = song.title,
                artist = song.artist,
                durationMs = if (durationMs > 0) durationMs else 0L,
                album = song.albumName,
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TvColors.Background)
            .onTvKeyEvent(
                onPlayPause = {
                    if (isPlaying) mediaController?.pause() else mediaController?.play()
                    true
                },
                onBack = {
                    if (sidePanel != TvNowPlayingSidePanel.NONE) {
                        sidePanel = TvNowPlayingSidePanel.NONE
                        true
                    } else {
                        onBack()
                        true
                    }
                },
            ),
    ) {
        if (song == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No track currently playing",
                    color = TvColors.TextSecondary,
                    fontSize = 18.sp,
                    fontFamily = TvSFProDisplay,
                )
            }
            return@Box
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = TvDimensions.SafeMarginHorizontal,
                    end = TvDimensions.SafeMarginHorizontal,
                    top = TvDimensions.SafeMarginVertical,
                    bottom = TvDimensions.SafeMarginVertical,
                ),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            // Left Dominant Pane: Artwork & Song Meta
            Column(
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(TvColors.SurfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!song.thumbnailUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(song.thumbnailUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = song.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }

                    // Format Quality Pill
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(TvColors.ScrimDark)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "LOSSLESS",
                            color = TvColors.AccentRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = TvSFProDisplay,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = song.title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.W800,
                    fontFamily = TvSFProDisplay,
                    color = TvColors.TextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.9f),
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = song.artist,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.W500,
                    fontFamily = TvSFProDisplay,
                    color = TvColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.9f),
                )

                if (!song.albumName.isNullOrBlank()) {
                    Text(
                        text = song.albumName.orEmpty(),
                        fontSize = 13.sp,
                        fontFamily = TvSFProDisplay,
                        color = TvColors.TextMuted,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
            }

            // Right Pane: Transport & Side Panels
            Box(
                modifier = Modifier
                    .weight(0.58f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                when (sidePanel) {
                    TvNowPlayingSidePanel.NONE -> {
                        TvMainTransportPanel(
                            mediaController = mediaController,
                            isPlaying = isPlaying,
                            currentPositionMs = currentPositionMs,
                            durationMs = durationMs,
                            repeatMode = repeatMode,
                            isLiked = isLiked,
                            onToggleFavorite = {
                                viewModel.toggleLike(song.videoId)
                            },
                            onOpenLyrics = { sidePanel = TvNowPlayingSidePanel.LYRICS },
                            onOpenQueue = { sidePanel = TvNowPlayingSidePanel.QUEUE },
                            onOpenStats = { sidePanel = TvNowPlayingSidePanel.STATS },
                        )
                    }
                    TvNowPlayingSidePanel.LYRICS -> {
                        TvLyricsPanel(
                            lyrics = lyrics,
                            currentPositionMs = currentPositionMs,
                            position = playerState.position,
                            onClose = { sidePanel = TvNowPlayingSidePanel.NONE },
                        )
                    }
                    TvNowPlayingSidePanel.QUEUE -> {
                        TvQueuePanel(
                            queue = playerState.queue,
                            currentQueueIndex = playerState.queueIndex,
                            onPlayTrackAt = { index ->
                                mediaController?.seekToDefaultPosition(index)
                            },
                            onClose = { sidePanel = TvNowPlayingSidePanel.NONE },
                        )
                    }
                    TvNowPlayingSidePanel.STATS -> {
                        TvStatsPanel(
                            song = song,
                            onClose = { sidePanel = TvNowPlayingSidePanel.NONE },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvMainTransportPanel(
    mediaController: MediaController?,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    repeatMode: Int,
    isLiked: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenStats: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Scrubber / Seek Bar
        val progress = if (durationMs > 0) currentPositionMs.toFloat() / durationMs.toFloat() else 0f
        TvSlider(
            value = progress,
            onValueChange = { fraction ->
                if (durationMs > 0) {
                    val targetMs = (fraction * durationMs).toLong()
                    mediaController?.seekTo(targetMs)
                }
            },
            modifier = Modifier.fillMaxWidth(0.9f),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Timestamps
        Row(
            modifier = Modifier.fillMaxWidth(0.9f),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDuration(currentPositionMs),
                fontSize = 13.sp,
                fontFamily = TvSFProDisplay,
                color = TvColors.TextSecondary,
            )
            Text(
                text = formatDuration(durationMs),
                fontSize = 13.sp,
                fontFamily = TvSFProDisplay,
                color = TvColors.TextSecondary,
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Primary Playback Controls Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Shuffle
            TvIconButton(
                icon = Icons.Default.Shuffle,
                contentDescription = "Shuffle",
                isActive = false,
                onClick = {
                    mediaController?.shuffleModeEnabled = !(mediaController?.shuffleModeEnabled ?: false)
                },
            )

            Spacer(modifier = Modifier.width(18.dp))

            // Previous
            TvIconButton(
                icon = Icons.Default.SkipPrevious,
                contentDescription = "Previous",
                size = 50.dp,
                iconSize = 26.dp,
                onClick = { mediaController?.seekToPreviousMediaItem() },
            )

            Spacer(modifier = Modifier.width(18.dp))

            // Play / Pause (Large Red Highlight)
            TvIconButton(
                icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                size = 64.dp,
                iconSize = 34.dp,
                isPrimary = true,
                onClick = {
                    if (isPlaying) mediaController?.pause() else mediaController?.play()
                },
            )

            Spacer(modifier = Modifier.width(18.dp))

            // Next
            TvIconButton(
                icon = Icons.Default.SkipNext,
                contentDescription = "Next",
                size = 50.dp,
                iconSize = 26.dp,
                onClick = { mediaController?.seekToNextMediaItem() },
            )

            Spacer(modifier = Modifier.width(18.dp))

            // Repeat
            TvIconButton(
                icon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                contentDescription = "Repeat",
                isActive = repeatMode != Player.REPEAT_MODE_OFF,
                onClick = {
                    val nextMode = when (repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                    mediaController?.repeatMode = nextMode
                },
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Secondary Controls Row: Like, Lyrics, Queue, Stats
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvIconButton(
                icon = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favorite",
                isActive = isLiked,
                onClick = onToggleFavorite,
            )

            TvIconButton(
                icon = Icons.Default.FormatQuote,
                contentDescription = "Lyrics",
                onClick = onOpenLyrics,
            )

            TvIconButton(
                icon = Icons.Default.QueueMusic,
                contentDescription = "Queue",
                onClick = onOpenQueue,
            )

            TvIconButton(
                icon = Icons.Default.Info,
                contentDescription = "Stats for nerds",
                onClick = onOpenStats,
            )
        }
    }
}

@Composable
private fun TvLyricsPanel(
    lyrics: List<LyricLine>?,
    currentPositionMs: Long,
    position: com.music.bitchord.playback.PlaybackPosition,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(18.dp))
            .background(TvColors.Surface)
            .padding(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Lyrics",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = TvSFProDisplay,
                color = TvColors.TextPrimary,
            )
            TvIconButton(
                icon = Icons.Default.Close,
                contentDescription = "Close",
                size = 36.dp,
                iconSize = 18.dp,
                onClick = onClose,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        TvLyricsList(
            lyrics = lyrics,
            currentPositionMs = currentPositionMs,
            position = position,
            isLoading = false,
            error = null,
            onRetry = {},
            onSeekToTimestamp = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun TvQueuePanel(
    queue: List<Song>,
    currentQueueIndex: Int,
    onPlayTrackAt: (Int) -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(18.dp))
            .background(TvColors.Surface)
            .padding(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Up Next (${queue.size})",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = TvSFProDisplay,
                color = TvColors.TextPrimary,
            )
            TvIconButton(
                icon = Icons.Default.Close,
                contentDescription = "Close",
                size = 36.dp,
                iconSize = 18.dp,
                onClick = onClose,
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (queue.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Queue is empty",
                    color = TvColors.TextMuted,
                    fontSize = 15.sp,
                    fontFamily = TvSFProDisplay,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(queue) { index, track ->
                    val isCurrent = index == currentQueueIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .tvButtonFocus(
                                shape = RoundedCornerShape(8.dp),
                                focusedScale = 1.02f,
                                onClick = { onPlayTrackAt(index) },
                            )
                            .background(if (isCurrent) TvColors.SurfaceSelected else Color.Transparent)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (isCurrent) "▶" else "${index + 1}",
                            color = if (isCurrent) TvColors.AccentRed else TvColors.TextMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = TvSFProDisplay,
                            modifier = Modifier.width(28.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                color = if (isCurrent) TvColors.AccentRed else TvColors.TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.W600,
                                fontFamily = TvSFProDisplay,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = track.artist,
                                color = TvColors.TextSecondary,
                                fontSize = 12.sp,
                                fontFamily = TvSFProDisplay,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvStatsPanel(
    song: Song,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(18.dp))
            .background(TvColors.Surface)
            .padding(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Stats for Nerds",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = TvSFProDisplay,
                color = TvColors.TextPrimary,
            )
            TvIconButton(
                icon = Icons.Default.Close,
                contentDescription = "Close",
                size = 36.dp,
                iconSize = 18.dp,
                onClick = onClose,
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TvStatItem(label = "Video ID", value = song.videoId)
            TvStatItem(label = "Audio Codec", value = "FLAC / Opus")
            TvStatItem(label = "Sample Rate", value = "48.0 kHz / 24-bit")
            TvStatItem(label = "Source Resolution", value = "BitChord Hi-Res Module Engine")
            TvStatItem(label = "Stream Mode", value = "Progressive Lossless Fallback")
            TvStatItem(label = "Automix Beat Engine", value = "Beat This! ONNX Native DSP")
        }
    }
}

@Composable
private fun TvStatItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontFamily = TvSFProDisplay,
            color = TvColors.TextSecondary,
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = TvSFProDisplay,
            color = TvColors.TextPrimary,
        )
    }
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "0:00"
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
