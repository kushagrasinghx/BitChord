package com.music.bitchord.ui.tv.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import com.music.bitchord.ui.tv.focus.tvButtonFocus
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.playback.PlaybackPosition
import com.music.bitchord.ui.player.SyncedLyricsPanel
import com.music.bitchord.ui.tv.components.TvErrorState
import com.music.bitchord.ui.tv.components.TvLyricsBadge
import com.music.bitchord.ui.tv.focus.onTvKeyEvent
import com.music.bitchord.ui.tv.theme.TvDimensions
import com.music.bitchord.ui.tv.theme.TvSFProDisplay

/**
 * 1:1 Apple Music TV Synchronized Lyrics Overlay.
 *
 * - Left Column: Album Art Card (~280dp), Song Title & Artist, Stats for Nerds with Info Icon, and Lyrics Badge on bottom-left.
 * - Right Column: 1:1 Original Mobile Lyrics Engine (Zero Recomposition Draw-Phase Sweeps & Zero Lag).
 * - Bottom: Full-width pure white seekbar with smooth linear animation.
 */
@Composable
fun TvLyricsOverlay(
    song: Song,
    isPlaying: Boolean,
    durationMs: Long,
    lyrics: List<LyricLine>?,
    isLoadingLyrics: Boolean,
    lyricsError: String?,
    onRetryLyrics: () -> Unit,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCloseLyrics: () -> Unit,
    modifier: Modifier = Modifier,
    position: PlaybackPosition? = null,
    currentPositionMs: Long = position?.positionMs ?: 0L,
    isLiked: Boolean = false,
    onToggleLike: (() -> Unit)? = null,
) {
    val showNerdStats by AppSettings.showNerdStats.collectAsState()
    val liveCanvasEnabled by AppSettings.tvLyricsCanvasEnabled.collectAsState()
    var canvasArtwork by remember(song.videoId) { mutableStateOf<com.music.bitchord.data.canvas.CanvasArtwork?>(null) }

    LaunchedEffect(song.videoId, liveCanvasEnabled) {
        if (liveCanvasEnabled) {
            canvasArtwork = com.music.bitchord.data.canvas.CanvasRepository.canvasFor(song)
        } else {
            canvasArtwork = null
        }
    }

    // Isolate playback ticks into a lambda so the outer TvLyricsOverlay NEVER recomposes on position tick
    val positionLambda: () -> Long = remember(position, currentPositionMs) {
        if (position != null) { { position.positionMs } }
        else { { currentPositionMs } }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .focusable()
            .onTvKeyEvent(
                onUp = {
                    val lines = lyrics
                    if (!lines.isNullOrEmpty()) {
                        val currentMs = positionLambda()
                        val currentIdx = lines.indexOfLast { it.timeMs <= currentMs }
                        if (currentIdx > 0) {
                            onSeek(lines[currentIdx - 1].timeMs)
                        } else if (currentIdx == 0) {
                            onSeek(lines[0].timeMs)
                        }
                    }
                    true
                },
                onDown = {
                    val lines = lyrics
                    if (!lines.isNullOrEmpty()) {
                        val currentMs = positionLambda()
                        val currentIdx = lines.indexOfLast { it.timeMs <= currentMs }
                        if (currentIdx in 0 until lines.lastIndex) {
                            onSeek(lines[currentIdx + 1].timeMs)
                        }
                    }
                    true
                },
                onLeft = {
                    val stepMs = 10_000L // 10s seek
                    onSeek((positionLambda() - stepMs).coerceAtLeast(0L))
                    true
                },
                onRight = {
                    val stepMs = 10_000L // 10s seek
                    onSeek((positionLambda() + stepMs).coerceAtMost(durationMs))
                    true
                },
                onPlayPause = {
                    onPlayPause()
                    true
                },
                onBack = {
                    onCloseLyrics()
                    true
                },
            ),
    ) {
        val palette = com.music.bitchord.ui.tv.theme.TvThemeColors.current

        // Top-Left Back Button: Pure luxury translucent frosted glass button (zero GPU blur overhead)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 48.dp, top = 24.dp)
                .size(44.dp)
                .tvButtonFocus(
                    shape = CircleShape,
                    focusedScale = 1.15f,
                    borderWidth = 3.dp,
                    onClick = onCloseLyrics,
                )
                .background(Color.White.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back from Lyrics",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }

        // Main Content: Left Metadata & Artwork + Right 1:1 Fluid Lyrics Window
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 48.dp,
                    end = 48.dp,
                    top = 28.dp,
                    bottom = 24.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(48.dp),
        ) {
            // Left Column: Album Card, Title, Artist, Stats for Nerds, and Lyrics Logo Badge
            Column(
                modifier = Modifier
                    .weight(0.38f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
            ) {
                // Album Art Card (with animated live canvas support when enabled in settings)
                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .shadow(32.dp, RoundedCornerShape(22.dp), spotColor = Color.Black.copy(alpha = 0.85f))
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.White.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    val currentCanvas = canvasArtwork
                    if (currentCanvas != null && liveCanvasEnabled) {
                        com.music.bitchord.ui.player.CanvasArtworkPlayer(
                            canvas = currentCanvas,
                            isPlaying = isPlaying,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else if (!song.thumbnailUrl.isNullOrBlank()) {
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
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Title
                Text(
                    text = song.title,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.W800,
                    fontFamily = TvSFProDisplay,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Artist
                Text(
                    text = song.artist,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.W500,
                    fontFamily = TvSFProDisplay,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Stats for Nerds (Clean HUD directly beside description with Info icon)
                AnimatedVisibility(
                    visible = showNerdStats,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Row(
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.60f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Stream Stats",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Opus / FLAC • 48 kHz / 24-bit • Lossless Audio",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // (Logo) BitChord on Bottom Left below Album Cover (No Gray Background)
                TvLyricsBadge(
                    backgroundColor = Color.Transparent,
                )
            }

            // Right Column: 1:1 Original Mobile Lyrics Engine (Zero Recomposition, Smooth Apple Spring Glide)
            Box(
                modifier = Modifier
                    .weight(0.62f)
                    .fillMaxHeight(),
            ) {
                if (lyricsError != null && lyrics.isNullOrEmpty()) {
                    TvErrorState(
                        message = "No lyrics found for this track",
                        onRetry = onRetryLyrics,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    SyncedLyricsPanel(
                        lines = lyrics.orEmpty(),
                        trackKey = song.videoId,
                        position = rememberTvPlaybackPosition(position, currentPositionMs),
                        looking = isLoadingLyrics,
                        isPlaying = isPlaying,
                        onSeekToLine = onSeek,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // Bottom Full-Width Progressive Seek Line (Pure White & Glides Smoothly Left-to-Right)
        TvLyricsProgressBar(
            positionMs = positionLambda,
            durationMs = durationMs,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun TvLyricsProgressBar(
    positionMs: () -> Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    PlaybackPositionScope(positionMs) { currentMs ->
        val fraction = if (durationMs > 0) {
            (currentMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f

        val smoothFraction by animateFloatAsState(
            targetValue = fraction,
            animationSpec = tween(durationMillis = 250, easing = LinearEasing),
            label = "lyricsSmoothFraction",
        )

        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(Color.White.copy(alpha = 0.18f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = smoothFraction
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
                    .background(Color.White),
            )
        }
    }
}
