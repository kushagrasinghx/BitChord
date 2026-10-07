package com.music.bitchord.ui.tv.player

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Airplay
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.session.MediaController
import com.music.bitchord.data.LikeState
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.playback.PlayerState
import com.music.bitchord.ui.MainViewModel
import com.music.bitchord.ui.tv.components.TvButton
import com.music.bitchord.ui.tv.dialogs.TvAddToPlaylistDialog
import com.music.bitchord.ui.tv.dialogs.TvQueueDialog
import com.music.bitchord.ui.tv.dialogs.TvSongActionMenuDialog
import com.music.bitchord.ui.tv.focus.onTvKeyEvent
import com.music.bitchord.ui.tv.focus.tvButtonFocus
import com.music.bitchord.ui.tv.theme.LocalTvFontFamily
import com.music.bitchord.ui.tv.theme.TvColors
import com.music.bitchord.ui.tv.theme.TvThemeColors

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
    val position = playerState.position
    val durationMs = playerState.durationMs
    val repeatMode = playerState.repeatMode
    val isShuffleActive by com.music.bitchord.playback.QueueShuffle.enabled.collectAsState()

    val likeOverrides by LikeState.overrides.collectAsState()
    val isLiked = song?.let { likeOverrides[it.videoId] == LikeStatus.LIKE } ?: false

    val lyrics by viewModel.lyrics.collectAsState()
    val lyricsChecked by viewModel.lyricsChecked.collectAsState()

    var isLyricsActive by remember { mutableStateOf(false) }
    var showOptionsDialog by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showQueueDialog by remember { mutableStateOf(false) }
    val currentFont = LocalTvFontFamily.current

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
            .background(TvThemeColors.current.background)
            .onTvKeyEvent(
                onPlayPause = {
                    if (isPlaying) mediaController?.pause() else mediaController?.play()
                    true
                },
                onBack = {
                    when {
                        showOptionsDialog -> { showOptionsDialog = false; true }
                        showPlaylistDialog -> { showPlaylistDialog = false; true }
                        showQueueDialog -> { showQueueDialog = false; true }
                        isLyricsActive -> { isLyricsActive = false; true }
                        else -> { onBack(); true }
                    }
                },
            ),
    ) {
        if (song == null) {
            // High-luxury Empty Placeholder State for First Launch / Idle
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 48.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Top Bar with Back Button and AirPlay Device Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .tvButtonFocus(
                                    shape = CircleShape,
                                    focusedScale = 1.15f,
                                    focusedBorderColor = Color.White,
                                    onClick = onBack,
                                )
                                .background(Color.White.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.White.copy(alpha = 0.12f))
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Airplay,
                                contentDescription = "AirPlay Device",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "BitChordTV",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = currentFont,
                                color = Color.White,
                            )
                        }
                    }

                    Text(
                        text = "Now Playing",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = currentFont,
                        color = Color.White.copy(alpha = 0.70f),
                    )
                }

                // Center Artwork Placeholder & Explanation
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color.White.copy(alpha = 0.10f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.45f),
                            modifier = Modifier.size(72.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "No Music Is Playing",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.W800,
                        fontFamily = currentFont,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Select any track or album from Listen Now, Browse, or Search to start playback.",
                        fontSize = 16.sp,
                        fontFamily = currentFont,
                        color = Color.White.copy(alpha = 0.65f),
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    TvButton(
                        text = "Explore Music",
                        icon = Icons.Default.Explore,
                        isPrimary = true,
                        onClick = onBack,
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
            return@Box
        }

        // Full-bleed Cinematic Background with gradient scrims
        TvPlayerBackground(
            artworkUrl = song.thumbnailUrl,
            isLyricsMode = isLyricsActive,
        )

        val previousSong = if (playerState.queueIndex > 0) playerState.queue.getOrNull(playerState.queueIndex - 1) else null
        val nextSong = if (playerState.queueIndex < playerState.queue.lastIndex) playerState.queue.getOrNull(playerState.queueIndex + 1) else null
        val liveCanvasEnabled by com.music.bitchord.data.settings.AppSettings.animatedCanvas.collectAsState()
        var canvasArtwork by remember(song.videoId) { mutableStateOf<com.music.bitchord.data.canvas.CanvasArtwork?>(null) }

        LaunchedEffect(song.videoId, liveCanvasEnabled) {
            if (!liveCanvasEnabled) {
                canvasArtwork = null
            } else {
                canvasArtwork = com.music.bitchord.data.canvas.CanvasRepository.canvasFor(song)
            }
        }

        // View Mode: Standard Cinematic Player vs. Full Synchronized Lyrics Layout
        Crossfade(
            targetState = isLyricsActive,
            label = "tvPlayerModeCrossfade",
        ) { lyricsMode ->
            if (lyricsMode) {
                TvLyricsOverlay(
                    song = song,
                    isPlaying = isPlaying,
                    position = position,
                    durationMs = durationMs,
                    lyrics = lyrics,
                    isLoadingLyrics = !lyricsChecked && lyrics == null,
                    lyricsError = if (lyricsChecked && lyrics == null) "Lyrics not available" else null,
                    onRetryLyrics = {
                        viewModel.loadLyrics(
                            videoId = song.videoId,
                            title = song.title,
                            artist = song.artist,
                            durationMs = durationMs,
                            album = song.albumName,
                        )
                    },
                    onSeek = { targetMs -> mediaController?.seekTo(targetMs) },
                    onPlayPause = {
                        if (isPlaying) mediaController?.pause() else mediaController?.play()
                    },
                    onPrevious = { mediaController?.seekToPreviousMediaItem() },
                    onNext = { mediaController?.seekToNextMediaItem() },
                    onCloseLyrics = { isLyricsActive = false },
                )
            } else {
                TvPlayerLayout(
                    song = song,
                    isPlaying = isPlaying,
                    currentPositionMs = position.positionMs,
                    durationMs = durationMs,
                    isLiked = isLiked,
                    isShuffleActive = isShuffleActive,
                    repeatMode = repeatMode,
                    isLyricsActive = isLyricsActive,
                    hasPrevious = previousSong != null,
                    hasNext = nextSong != null,
                    previousSong = previousSong,
                    nextSong = nextSong,
                    onBack = onBack,
                    onToggleLike = { viewModel.toggleLike(song.videoId) },
                    onToggleShuffle = { mediaController?.let { com.music.bitchord.playback.QueueShuffle.toggle(it) } },
                    onPrevious = { mediaController?.seekToPreviousMediaItem() },
                    onPlayPause = {
                        if (isPlaying) mediaController?.pause() else mediaController?.play()
                    },
                    onNext = { mediaController?.seekToNextMediaItem() },
                    onToggleQueue = { showQueueDialog = true },
                    onToggleLyrics = { isLyricsActive = !isLyricsActive },
                    onSeek = { targetMs -> mediaController?.seekTo(targetMs) },
                    onCycleRepeat = {
                        mediaController?.let { controller ->
                            val next = when (controller.repeatMode) {
                                androidx.media3.common.Player.REPEAT_MODE_OFF -> androidx.media3.common.Player.REPEAT_MODE_ALL
                                androidx.media3.common.Player.REPEAT_MODE_ALL -> androidx.media3.common.Player.REPEAT_MODE_ONE
                                else -> androidx.media3.common.Player.REPEAT_MODE_OFF
                            }
                            com.music.bitchord.data.settings.AppSettings.setRepeatMode(next)
                            controller.repeatMode = next
                        }
                    },
                    onAddToPlaylist = { showPlaylistDialog = true },
                    onOpenOptions = { showOptionsDialog = true },
                )
            }
        }

        // Action Menu Dialog (•••)
        if (showOptionsDialog) {
            TvSongActionMenuDialog(
                song = song,
                isLiked = isLiked,
                viewModel = viewModel,
                mediaController = mediaController,
                onToggleLike = { viewModel.toggleLike(song.videoId) },
                onDismiss = { showOptionsDialog = false },
            )
        }

        // Add to Playlist / Create Playlist Dialog (+)
        if (showPlaylistDialog) {
            TvAddToPlaylistDialog(
                song = song,
                viewModel = viewModel,
                onDismiss = { showPlaylistDialog = false },
            )
        }

        // Playing Now Queue Dialog
        if (showQueueDialog) {
            TvQueueDialog(
                queue = playerState.queue,
                currentIndex = playerState.queueIndex,
                isPlaying = isPlaying,
                onSelectIndex = { index ->
                    mediaController?.seekToDefaultPosition(index)
                    mediaController?.play()
                },
                onDismiss = { showQueueDialog = false },
            )
        }
    }
}

