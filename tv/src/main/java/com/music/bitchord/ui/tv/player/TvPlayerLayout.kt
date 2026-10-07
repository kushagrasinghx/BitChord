package com.music.bitchord.ui.tv.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.VideocamOff
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import com.music.bitchord.R
import com.music.bitchord.data.canvas.CanvasArtwork
import com.music.bitchord.data.canvas.CanvasRepository
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.tv.focus.tvButtonFocus
import com.music.bitchord.ui.tv.theme.LocalTvFontFamily
import com.music.bitchord.ui.tv.theme.TvSFProDisplay
import kotlinx.coroutines.delay

/**
 * 1:1 Apple Music TV Now Playing Screen matching user's reference image exactly:
 *
 * - Top-Left: Music branding logo.
 * - Top-Right: 4 frosted circular buttons: Shuffle, Repeat, Infinity (Autoplay), Lyrics (speech bubble).
 * - Center: 3-album Cover Flow carousel:
 *     - Left: Previous track art + centered Title & Artist underneath.
 *     - Center: Elevated currently playing track art + centered Title, Artist + [+] and [•••] buttons.
 *     - Right: Next track art + centered Title & Artist underneath.
 * - Bottom: Full-width slim progress bar with elapsed [0:54] and negative remaining [-4:06] timestamps.
 */
@Composable
fun TvPlayerLayout(
    song: Song,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    isLiked: Boolean,
    isShuffleActive: Boolean,
    repeatMode: Int,
    isLyricsActive: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    previousSong: Song? = null,
    nextSong: Song? = null,
    playingFromSource: String? = null,
    onBack: (() -> Unit)? = null,
    onToggleLike: () -> Unit,
    onToggleShuffle: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onToggleQueue: () -> Unit,
    onToggleLyrics: () -> Unit,
    onSeek: (Long) -> Unit,
    onCycleRepeat: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onOpenOptions: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val currentFont = LocalTvFontFamily.current
    val autoplay by AppSettings.autoplay.collectAsState()
    val liveCanvasEnabled by AppSettings.animatedCanvas.collectAsState()

    var slideForward by remember { mutableStateOf(true) }
    var canvasArtwork by remember(song.videoId) { mutableStateOf<CanvasArtwork?>(null) }
    var showNoCanvasBanner by remember(song.videoId) { mutableStateOf(false) }

    // Live Video Canvas Resolver & Notification
    LaunchedEffect(song.videoId, liveCanvasEnabled) {
        if (!liveCanvasEnabled) {
            canvasArtwork = null
            showNoCanvasBanner = false
            return@LaunchedEffect
        }
        val resolved = CanvasRepository.canvasFor(song)
        canvasArtwork = resolved
        if (resolved == null) {
            showNoCanvasBanner = true
            delay(3500)
            showNoCanvasBanner = false
        } else {
            showNoCanvasBanner = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 48.dp,
                    end = 48.dp,
                    top = 28.dp,
                    bottom = 24.dp,
                ),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // ── 1. TOP BAR (Music Branding on Left, 4 Frosted Action Buttons on Right) ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Top Left: Frosted BitChord TV logo matching the home screen
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_logo),
                            contentDescription = "BitChordTV",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        text = "BitChordTV",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = currentFont,
                        color = Color.White,
                        letterSpacing = (-0.4).sp,
                    )
                }

                // Top Right: 4 Frosted Circular Action Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Button 1: Shuffle
                    TvFrostedCircularButton(
                        icon = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        isActive = isShuffleActive,
                        onClick = onToggleShuffle,
                    )

                    // Button 2: Repeat (Toggles Off -> All -> One [shows 1 badge])
                    val repeatIcon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat
                    TvFrostedCircularButton(
                        icon = repeatIcon,
                        contentDescription = when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> "Repeat One"
                            Player.REPEAT_MODE_ALL -> "Repeat All"
                            else -> "Repeat Off"
                        },
                        isActive = repeatMode != Player.REPEAT_MODE_OFF,
                        onClick = { onCycleRepeat?.invoke() },
                    )

                    // Button 3: Infinity (Autoplay)
                    TvFrostedCircularPainterButton(
                        painter = painterResource(R.drawable.ic_autoplay),
                        contentDescription = "Autoplay Similar Music",
                        isActive = autoplay,
                        onClick = { AppSettings.setAutoplay(!autoplay) },
                    )

                    // Button 4: Lyrics (Speech Bubble) — highlighted solid white when active in screenshot
                    TvFrostedLyricsBubbleButton(
                        contentDescription = "Synchronized Lyrics",
                        isActive = isLyricsActive,
                        onClick = onToggleLyrics,
                    )
                }
            }

            // ── 2. CENTER 3D COVER FLOW CAROUSEL (3 Album Art Cards with Titles Underneath) ──
            androidx.compose.animation.AnimatedContent(
                targetState = song.videoId,
                transitionSpec = {
                    val enterFrac = if (slideForward) 0.40f else -0.40f
                    val exitFrac = if (slideForward) -0.40f else 0.40f
                    (androidx.compose.animation.slideInHorizontally(
                        animationSpec = tween(420, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                    ) { fullWidth -> (fullWidth * enterFrac).toInt() } + fadeIn(tween(350)))
                        .togetherWith(
                            androidx.compose.animation.slideOutHorizontally(
                                animationSpec = tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                            ) { fullWidth -> (fullWidth * exitFrac).toInt() } + fadeOut(tween(250))
                        )
                },
                label = "coverFlowCarouselGlide",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 12.dp),
            ) { _ ->
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    // Left Card: Previous Track
                    TvCoverFlowCard(
                        song = previousSong,
                        isCenter = false,
                        isPrevious = true,
                        onClick = {
                            slideForward = false
                            onPrevious()
                        },
                        modifier = Modifier.weight(0.28f),
                    )

                    Spacer(modifier = Modifier.width(28.dp))

                    // Center Card: Currently Playing Track (Elevated, Prominent, with [+] and [•••] buttons)
                    val centerInteraction = remember { MutableInteractionSource() }
                    val isCenterFocused by centerInteraction.collectIsFocusedAsState()

                    Column(
                        modifier = Modifier
                            .weight(0.38f)
                            .zIndex(10f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.95f)
                                .aspectRatio(1.0f)
                                .shadow(28.dp, RoundedCornerShape(18.dp), spotColor = Color.Black.copy(alpha = 0.85f))
                                .border(
                                    width = 1.5.dp,
                                    color = Color.White.copy(alpha = 0.20f),
                                    shape = RoundedCornerShape(18.dp),
                                )
                                .tvButtonFocus(
                                    shape = RoundedCornerShape(18.dp),
                                    focusedScale = 1.06f,
                                    focusedBorderColor = Color.White,
                                    borderWidth = 4.dp,
                                    onClick = onPlayPause,
                                )
                                .background(Color.White.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (!song.thumbnailUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(song.thumbnailUrl)
                                        .size(720, 720)
                                        .precision(Precision.EXACT)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = song.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(64.dp),
                                )
                            }

                            if (canvasArtwork != null && liveCanvasEnabled) {
                                com.music.bitchord.ui.player.CanvasArtworkPlayer(
                                    canvas = canvasArtwork!!,
                                    isPlaying = isPlaying,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            // Play/Pause Overlay Animation
                            androidx.compose.animation.AnimatedVisibility(
                                visible = !isPlaying,
                                enter = androidx.compose.animation.fadeIn(tween(250)) + androidx.compose.animation.scaleIn(initialScale = 0.8f, animationSpec = tween(250, easing = androidx.compose.animation.core.FastOutSlowInEasing)),
                                exit = androidx.compose.animation.fadeOut(tween(250)) + androidx.compose.animation.scaleOut(targetScale = 0.8f, animationSpec = tween(250, easing = androidx.compose.animation.core.FastOutSlowInEasing)),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.6f))
                                        .border(2.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Pause,
                                        contentDescription = "Paused",
                                        tint = Color.White,
                                        modifier = Modifier.size(40.dp),
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Title with Animated Mini Equalizer ıll
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        ) {
                            if (isPlaying) {
                                com.music.bitchord.ui.tv.components.TvMiniEqualizer(
                                    isPlaying = true,
                                    barColor = Color.White,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                            }
                            Text(
                                text = song.title,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = currentFont,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = song.artist,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = currentFont,
                            color = Color.White.copy(alpha = 0.70f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Small Frosted [+] and [•••] Action Buttons directly under the artist
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // [+] Add to Library / Playlist button
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.16f))
                                    .border(
                                        1.dp,
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.White.copy(alpha = 0.40f),
                                                Color.White.copy(alpha = 0.10f),
                                            ),
                                        ),
                                        CircleShape,
                                    )
                                    .tvButtonFocus(
                                        shape = CircleShape,
                                        focusedScale = 1.18f,
                                        focusedBorderColor = Color.White,
                                        borderWidth = 3.dp,
                                        onClick = { onAddToPlaylist?.invoke() ?: onToggleLike() },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = if (isLiked) Icons.Default.Check else Icons.Default.Add,
                                    contentDescription = if (isLiked) "Added to Library" else "Add to Playlist",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            // [•••] Options / More button (opens full features menu)
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.16f))
                                    .border(
                                        1.dp,
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.White.copy(alpha = 0.40f),
                                                Color.White.copy(alpha = 0.10f),
                                            ),
                                        ),
                                        CircleShape,
                                    )
                                    .tvButtonFocus(
                                        shape = CircleShape,
                                        focusedScale = 1.18f,
                                        focusedBorderColor = Color.White,
                                        borderWidth = 3.dp,
                                        onClick = { onOpenOptions?.invoke() ?: onToggleQueue() },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreHoriz,
                                    contentDescription = "More Options",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(28.dp))

                    // Right Card: Next Track
                    TvCoverFlowCard(
                        song = nextSong,
                        isCenter = false,
                        isPrevious = false,
                        onClick = {
                            slideForward = true
                            onNext()
                        },
                        modifier = Modifier.weight(0.28f),
                    )
                }
            }

            // ── 3. BOTTOM PROGRESS LINE (SLIM WHITE TRACK WITH ELAPSED & NEGATIVE REMAINING) ──
            TvPlayerProgress(
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                onSeek = onSeek,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Live Video Canvas Notification Banner
        AnimatedVisibility(
            visible = showNoCanvasBanner,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp),
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.VideocamOff,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "No live video canvas for this track • Playing standard artwork",
                    fontSize = 13.sp,
                    fontFamily = currentFont,
                    color = Color.White,
                )
            }
        }
    }
}

/**
 * 1:1 Apple Music TV Cover Flow card for Previous or Next track with high-contrast hover border.
 */
@Composable
private fun TvCoverFlowCard(
    song: Song?,
    isCenter: Boolean,
    isPrevious: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentFont = LocalTvFontFamily.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .aspectRatio(1.0f)
                .shadow(16.dp, RoundedCornerShape(16.dp), spotColor = Color.Black.copy(alpha = 0.65f))
                .border(
                    width = if (isFocused) 4.dp else 1.dp,
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(16.dp),
                )
                .tvButtonFocus(
                    shape = RoundedCornerShape(16.dp),
                    focusedScale = 1.08f,
                    focusedBorderColor = Color.White,
                    borderWidth = 4.dp,
                    onClick = onClick,
                )
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            if (song != null && !song.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(song.thumbnailUrl)
                        .size(512, 512)
                        .precision(Precision.EXACT)
                        .crossfade(true)
                        .build(),
                    contentDescription = song.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(54.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = song?.title ?: if (isPrevious) "Previous Track" else "Next Track",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = currentFont,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = song?.artist ?: if (isPrevious) "—" else "Up Next",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = currentFont,
            color = Color.White.copy(alpha = 0.60f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Frosted circular action button with vector ImageVector.
 */
@Composable
private fun TvFrostedCircularButton(
    icon: ImageVector,
    contentDescription: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bg = when {
        isFocused -> Color.White
        isActive -> Color.White.copy(alpha = 0.35f)
        else -> Color.White.copy(alpha = 0.16f)
    }
    val tint = when {
        isFocused -> Color.Black
        isActive -> Color.White
        else -> Color.White.copy(alpha = 0.85f)
    }

    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(bg)
            .tvButtonFocus(
                shape = CircleShape,
                focusedScale = 1.15f,
                focusedBorderColor = Color.White,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Frosted circular action button with painter resource (for Infinity / Autoplay).
 */
@Composable
private fun TvFrostedCircularPainterButton(
    painter: androidx.compose.ui.graphics.painter.Painter,
    contentDescription: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bg = when {
        isFocused -> Color.White
        isActive -> Color.White.copy(alpha = 0.35f)
        else -> Color.White.copy(alpha = 0.16f)
    }
    val tint = when {
        isFocused -> Color.Black
        isActive -> Color.White
        else -> Color.White.copy(alpha = 0.85f)
    }

    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(bg)
            .tvButtonFocus(
                shape = CircleShape,
                focusedScale = 1.15f,
                focusedBorderColor = Color.White,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Frosted lyrics dialogue bubble button (matching the 4th button in screenshot).
 * When active (lyrics toggled), it has a solid white circle background and dark speech bubble inside!
 */
@Composable
private fun TvFrostedLyricsBubbleButton(
    contentDescription: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    // In the screenshot, the lyrics button is active: solid white circle with dark speech bubble!
    val bg = when {
        isFocused -> Color.White
        isActive -> Color.White // Highlighted white circular background as in screenshot
        else -> Color.White.copy(alpha = 0.16f)
    }
    val tint = when {
        isFocused -> Color.Black
        isActive -> Color(0xFF1C1C24) // Dark speech bubble inside white circle
        else -> Color.White.copy(alpha = 0.85f)
    }

    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(bg)
            .tvButtonFocus(
                shape = CircleShape,
                focusedScale = 1.15f,
                focusedBorderColor = Color.White,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_lyrics_bubble),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}
