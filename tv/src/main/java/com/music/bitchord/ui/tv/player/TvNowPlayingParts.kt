package com.music.bitchord.ui.tv.player

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.media3.common.Player
import com.music.bitchord.data.NerdStats
import com.music.bitchord.data.canvas.CanvasArtwork
import com.music.bitchord.data.canvas.CanvasRepository
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.playback.PlaybackPosition
import com.music.bitchord.sharedui.resources.Res
import com.music.bitchord.sharedui.resources.ic_dolby_atmos
import com.music.bitchord.sharedui.resources.ic_player_next
import com.music.bitchord.sharedui.resources.ic_player_pause
import com.music.bitchord.sharedui.resources.ic_player_play
import com.music.bitchord.sharedui.resources.ic_player_previous
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.ui.player.ArtworkMeshBackdrop
import com.music.bitchord.ui.player.CanvasArtworkPlayer
import com.music.bitchord.ui.player.SyncedLyricsPanel
import com.music.bitchord.ui.player.rememberArtworkMesh
import com.music.bitchord.ui.tv.components.TvActivityIndicator
import com.music.bitchord.ui.tv.components.TvArtwork
import com.music.bitchord.ui.tv.components.TvButton
import com.music.bitchord.ui.tv.components.TvRow
import com.music.bitchord.ui.tv.components.TvLockup
import com.music.bitchord.ui.tv.components.tvClick
import com.music.bitchord.ui.tv.components.tvInitialFocus
import com.music.bitchord.ui.tv.components.tvLift
import com.music.bitchord.ui.tv.components.tvPlatter
import com.music.bitchord.ui.tv.theme.TvGlass
import com.music.bitchord.ui.tv.theme.TvType
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// Backdrop
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The artwork's colours, smeared into the phone's moving mesh, darkened a touch
 * so white type always reads over it.
 */
@Composable
fun TvPlayerBackground(
    artworkUrl: String?,
    modifier: Modifier = Modifier,
) {
    val mesh = rememberArtworkMesh(imageUrl = artworkUrl)
    Box(modifier = modifier.fillMaxSize()) {
        ArtworkMeshBackdrop(
            mesh = mesh,
            blurRadius = 34.dp,
            seam = 0.dp,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.22f)),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Stage: artwork and lyrics
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The cover, and the lyrics beside it when the song has them. Without lyrics the
 * cover sits centred and a little larger; it glides to the left as lyrics arrive.
 * The move is a single layer transform, so it costs no layout.
 */
@Composable
internal fun TvNowPlayingStage(
    song: Song,
    isPlaying: Boolean,
    position: PlaybackPosition,
    lyrics: List<LyricLine>,
    showLyrics: Boolean,
    lyricsLoading: Boolean,
    controlsVisible: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // While the transport is up the lyrics fade out above it rather than run under it.
    val underControls by animateFloatAsState(if (controlsVisible) 1f else 0f, tween(320), label = "lyricsFade")
    val canvasEnabled by AppSettings.animatedCanvas.collectAsState()
    var canvas by remember(song.videoId) { mutableStateOf<CanvasArtwork?>(null) }
    LaunchedEffect(song.videoId, canvasEnabled) {
        canvas = if (canvasEnabled) CanvasRepository.canvasFor(song) else null
    }
    val side by animateFloatAsState(if (showLyrics) 1f else 0f, tween(520), label = "artToSide")

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val centredSize = (maxHeight * 0.62f).coerceAtMost(320.dp)
        val sideSize = (maxHeight * 0.54f).coerceAtMost(272.dp)
        val sideStart = 51.dp
        val lift = 21.dp // sit a little above centre, clear of the transport
        val centredX = (maxWidth - centredSize) / 2
        val centredY = (maxHeight - centredSize) / 2 - lift
        val sideY = (maxHeight - sideSize) / 2 - lift
        val shape = RoundedCornerShape(11.dp)

        Box(
            modifier = Modifier
                .offset(x = centredX, y = centredY)
                .size(centredSize)
                .graphicsLayer {
                    val scale = lerp(1f, sideSize / centredSize, side)
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = scale
                    scaleY = scale
                    translationX = lerp(0f, (sideStart - centredX).toPx(), side)
                    translationY = lerp(0f, (sideY - centredY).toPx(), side)
                    shadowElevation = 29.dp.toPx()
                    this.shape = shape
                    clip = true
                },
        ) {
            TvArtwork(url = song.thumbnailUrl, px = 720, shape = RectangleShape, modifier = Modifier.fillMaxSize())
            canvas?.let { CanvasArtworkPlayer(canvas = it, isPlaying = isPlaying, modifier = Modifier.fillMaxSize()) }
        }

        // With the transport away, the song is named under the cover: the title and
        // artist the transport carries, riding the same glide to the side.
        val captionShown by animateFloatAsState(if (controlsVisible) 0f else 1f, tween(320), label = "captionFade")
        if (captionShown > 0f) {
            val captionGap = 14.dp
            val sideCaptionY = sideY + sideSize + captionGap
            Column(
                modifier = Modifier
                    .offset(x = centredX, y = centredY + centredSize + captionGap)
                    .width(centredSize)
                    .graphicsLayer {
                        val scale = lerp(1f, sideSize / centredSize, side)
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = scale
                        scaleY = scale
                        translationX = lerp(0f, (sideStart - centredX).toPx(), side)
                        translationY = lerp(0f, (sideCaptionY - (centredY + centredSize + captionGap)).toPx(), side)
                        alpha = captionShown
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = song.title,
                    style = TvType.Title.copy(fontSize = 19.sp, lineHeight = 24.sp),
                    color = TvGlass.TextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (song.artist.isNotBlank()) {
                    Text(
                        text = song.artist,
                        style = TvType.Body.copy(fontWeight = FontWeight.W400, fontSize = 14.sp),
                        color = TvGlass.TextSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (showLyrics || side > 0f) {
            Box(
                modifier = Modifier
                    .padding(start = sideStart + sideSize + 51.dp, end = 38.dp)
                    .fillMaxHeight()
                    .graphicsLayer {
                        alpha = side
                        // Offscreen only while the mask is in use; otherwise the
                        // lyrics draw straight to the screen as usual.
                        compositingStrategy = if (underControls > 0f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
                    }
                    .drawWithContent {
                        drawContent()
                        if (underControls > 0f) {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0f to Color.Black,
                                    0.56f to Color.Black,
                                    0.74f to Color.Black.copy(alpha = 1f - underControls),
                                    1f to Color.Black.copy(alpha = 1f - underControls),
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                        }
                    },
            ) {
                // The shared panel's type is sized for a phone held at arm's
                // length. Shrink only the glyphs: density stays whole, so its
                // dp geometry still lands on pixels.
                val density = LocalDensity.current
                val lyricDensity = remember(density) {
                    Density(density = density.density, fontScale = density.fontScale * LYRIC_FONT_SCALE)
                }
                CompositionLocalProvider(LocalDensity provides lyricDensity) {
                    SyncedLyricsPanel(
                        lines = lyrics,
                        trackKey = song.videoId,
                        position = position,
                        looking = lyricsLoading,
                        isPlaying = isPlaying,
                        onSeekToLine = onSeek,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Transport
// ─────────────────────────────────────────────────────────────────────────────

private const val LYRIC_FONT_SCALE = 0.8f

private val TransportScrim = Brush.verticalGradient(
    0f to Color.Transparent,
    0.4f to Color.Black.copy(alpha = 0.38f),
    1f to Color.Black.copy(alpha = 0.66f),
)

/**
 * The transport: what's playing and its quality on the left, the song's buttons
 * on the right, the scrubber under both. Up Next opens beneath the scrubber and
 * pushes the rest up.
 */
@Composable
internal fun TvTransport(
    song: Song,
    isPlaying: Boolean,
    isLoading: Boolean,
    position: PlaybackPosition,
    durationMs: Long,
    scrubTarget: Long?,
    isLiked: Boolean,
    lyricsAvailable: Boolean,
    lyricsOn: Boolean,
    upNextOpen: Boolean,
    scrubberFocus: FocusRequester,
    onScrub: (Long) -> Unit,
    onSelectOnScrubber: () -> Unit,
    onOpenUpNext: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleLyrics: () -> Unit,
    onToggleUpNext: () -> Unit,
    onMore: () -> Unit,
    upNext: @Composable () -> Unit,
) {
    val showStats by AppSettings.showNerdStats.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind { drawRect(TransportScrim) }
            .padding(start = 45.dp, end = 45.dp, top = 77.dp, bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = TvType.Title.copy(fontSize = 19.sp, lineHeight = 24.sp),
                    color = TvGlass.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOf(song.artist, song.albumName.orEmpty()).filter { it.isNotBlank() }.joinToString(" — "),
                    style = TvType.Body.copy(fontWeight = FontWeight.W400, fontSize = 14.sp),
                    color = TvGlass.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TvQualityLine(showStats = showStats, modifier = Modifier.padding(top = 6.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                TvTransportButton(
                    icon = if (isLiked) BitChordIcons.HeartFilled else BitChordIcons.Heart,
                    contentDescription = if (isLiked) "Unlove" else "Love",
                    onClick = onToggleLike,
                )
                TvTransportButton(
                    icon = BitChordIcons.LyricsQuote,
                    contentDescription = "Lyrics",
                    active = lyricsOn,
                    enabled = lyricsAvailable,
                    onClick = onToggleLyrics,
                )
                TvTransportButton(
                    icon = BitChordIcons.Queue,
                    contentDescription = "Up Next",
                    active = upNextOpen,
                    onClick = onToggleUpNext,
                )
                TvTransportButton(
                    icon = Icons.Rounded.MoreHoriz,
                    contentDescription = "More",
                    onClick = onMore,
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        TvScrubber(
            position = position,
            durationMs = durationMs,
            isPlaying = isPlaying,
            isLoading = isLoading,
            scrubTarget = scrubTarget,
            focusRequester = scrubberFocus,
            onScrub = onScrub,
            onSelect = onSelectOnScrubber,
            onDown = onOpenUpNext,
        )
        AnimatedVisibility(
            visible = upNextOpen,
            enter = expandVertically(tween(300)) + fadeIn(tween(240)),
            exit = shrinkVertically(tween(260)) + fadeOut(tween(180)),
        ) {
            Column {
                Spacer(modifier = Modifier.height(18.dp))
                upNext()
            }
        }
    }
}

/** A round glyph button on the transport; [active] keeps it lit while focus is elsewhere. */
@Composable
private fun TvTransportButton(
    icon: ImageVector?,
    contentDescription: String,
    onClick: () -> Unit,
    active: Boolean = false,
    enabled: Boolean = true,
    size: androidx.compose.ui.unit.Dp = 37.dp,
    badge: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .tvLift(interaction, CircleShape, focusedScale = 1.1f, elevation = 10.dp)
            .tvPlatter(interaction, resting = if (active) TvGlass.FillSelected else TvGlass.Fill)
            .tvClick(interaction, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (focused) TvGlass.OnPlatter else TvGlass.TextPrimary,
                modifier = Modifier.size(size * 0.46f),
            )
        } else if (badge != null) {
            // The phone's Repeat One: the digit in place of the loop.
            Text(
                text = badge,
                style = TvType.Body.copy(fontSize = 14.sp, fontWeight = FontWeight.W700),
                color = if (focused) TvGlass.OnPlatter else TvGlass.TextPrimary,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Scrubber
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The playhead. Its bar is drawn straight from [PlaybackPosition] on every frame
 * while playing — carried forward between the player's readings by the time they
 * were taken — so it glides rather than stepping, and redraws only itself.
 * Left and right scrub; the seek happens once the presses stop.
 */
@Composable
private fun TvScrubber(
    position: PlaybackPosition,
    durationMs: Long,
    isPlaying: Boolean,
    isLoading: Boolean,
    scrubTarget: Long?,
    focusRequester: FocusRequester,
    onScrub: (Long) -> Unit,
    onSelect: () -> Unit,
    onDown: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val frame = remember { mutableLongStateOf(0L) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) while (true) withFrameNanos { frame.longValue = it }
    }
    val thickness by animateDpAsState(if (focused) 6.dp else 4.dp, tween(160), label = "scrubThickness")

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .focusRequester(focusRequester)
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> { onScrub(-10_000L); true }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> { onScrub(10_000L); true }
                        KeyEvent.KEYCODE_DPAD_DOWN -> { onDown(); true }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            onSelect(); true
                        }
                        else -> false
                    }
                }
                .focusable(interactionSource = interaction)
                .drawBehind {
                    val now = frame.longValue
                    val shown = scrubTarget ?: livePosition(position, isPlaying, now, durationMs)
                    val fraction = if (durationMs > 0) (shown.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
                    val h = thickness.toPx()
                    val top = (size.height - h) / 2f
                    val radius = CornerRadius(h / 2f)
                    drawRoundRect(
                        color = Color.White.copy(alpha = if (focused) 0.30f else 0.22f),
                        topLeft = Offset(0f, top),
                        size = Size(size.width, h),
                        cornerRadius = radius,
                    )
                    drawRoundRect(
                        color = Color.White.copy(alpha = if (focused) 1f else 0.88f),
                        topLeft = Offset(0f, top),
                        size = Size(size.width * fraction, h),
                        cornerRadius = radius,
                    )
                    if (focused) {
                        val x = (size.width * fraction).coerceIn(h, size.width - h)
                        drawCircle(Color.White, radius = h * 1.15f, center = Offset(x, size.height / 2f))
                    }
                },
        )
        TvScrubTimes(
            position = position,
            durationMs = durationMs,
            scrubTarget = scrubTarget,
            isLoading = isLoading,
        )
    }
}

private fun livePosition(position: PlaybackPosition, isPlaying: Boolean, frameNanos: Long, durationMs: Long): Long {
    val read = position.positionMs
    if (!isPlaying || !position.advancing || position.sampledAtNanos == 0L || frameNanos == 0L) return read
    val carried = (frameNanos - position.sampledAtNanos) / 1_000_000L
    return (read + carried.coerceIn(0L, 1_500L)).coerceAtMost(durationMs.coerceAtLeast(read))
}

/** Elapsed on the left, remaining on the right — recomposed once a second, not once a frame. */
@Composable
private fun TvScrubTimes(
    position: PlaybackPosition,
    durationMs: Long,
    scrubTarget: Long?,
    isLoading: Boolean,
) {
    val second by remember(position) { derivedStateOf { position.positionMs / 1000L } }
    val shownMs = scrubTarget ?: (second * 1000L)
    Box(modifier = Modifier.fillMaxWidth().padding(top = 3.dp)) {
        Text(
            text = formatClock(shownMs),
            style = TvType.Caption,
            color = if (scrubTarget != null) TvGlass.TextPrimary else TvGlass.TextSecondary,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        if (isLoading) {
            TvActivityIndicator(size = 13.dp, modifier = Modifier.align(Alignment.Center))
        }
        Text(
            text = if (durationMs > 0) "-" + formatClock((durationMs - shownMs).coerceAtLeast(0L)) else "",
            style = TvType.Caption,
            color = TvGlass.TextSecondary,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

internal fun formatClock(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
}

// ─────────────────────────────────────────────────────────────────────────────
// Quality
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Apple Music's quality badge — Dolby Atmos, Hi-Res Lossless or Lossless — read
 * from what's actually decoding, and with Stats for Nerds on, the stream itself.
 */
@Composable
private fun TvQualityLine(showStats: Boolean, modifier: Modifier = Modifier) {
    val snapshot by NerdStats.current.collectAsState()
    val stats = snapshot ?: return
    val label = when {
        stats.isDolbyAtmos -> "Dolby Atmos"
        stats.isHiRes -> "Hi-Res Lossless"
        stats.isLossless -> "Lossless"
        else -> null
    }
    val detail = if (showStats) streamDetail(stats) else null
    if (label == null && detail.isNullOrBlank()) return
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (label != null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.16f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (stats.isDolbyAtmos) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_dolby_atmos),
                        contentDescription = null,
                        tint = TvGlass.TextPrimary.copy(alpha = 0.85f),
                        modifier = Modifier.height(9.dp),
                    )
                }
                Text(
                    text = label,
                    style = TvType.Caption.copy(fontSize = 11.sp, fontWeight = FontWeight.W600),
                    color = TvGlass.TextPrimary.copy(alpha = 0.85f),
                )
            }
        }
        if (!detail.isNullOrBlank()) {
            Text(text = detail, style = TvType.Caption, color = TvGlass.TextTertiary, maxLines = 1)
        }
    }
}

private fun streamDetail(s: NerdStats.Snapshot): String {
    val codec = when (val mime = s.mimeType?.lowercase()) {
        null -> null
        "audio/flac" -> "FLAC"
        "audio/alac" -> "ALAC"
        "audio/mp4a-latm", "audio/aac" -> "AAC"
        "audio/opus" -> "Opus"
        "audio/vorbis" -> "Vorbis"
        "audio/mpeg" -> "MP3"
        "audio/eac3", "audio/eac3-joc" -> "Dolby Digital Plus"
        "audio/ac3" -> "Dolby Digital"
        "audio/ac4" -> "Dolby AC-4"
        "audio/raw" -> "PCM"
        else -> mime.substringAfter('/').uppercase()
    }
    val rate = s.sampleRateHz
    val depth = s.bitDepth
    val format = when {
        depth != null && rate != null -> "$depth-bit/${khz(rate)}"
        rate != null -> khz(rate)
        else -> null
    }
    val bitrate = s.bitrateKbps?.takeIf { it > 0 }?.let { "$it kbps" }
    return listOfNotNull(codec, format, bitrate, s.sourceName).joinToString(" · ")
}

private fun khz(hz: Int): String {
    val k = hz / 1000f
    return if (k % 1f == 0f) "${k.toInt()} kHz" else String.format(Locale.US, "%.1f kHz", k)
}

// ─────────────────────────────────────────────────────────────────────────────
// Up Next
// ─────────────────────────────────────────────────────────────────────────────

/** What plays after this song, with the queue's Shuffle, Repeat and Autoplay. */
@Composable
internal fun TvUpNext(
    queue: List<Song>,
    currentIndex: Int,
    shuffle: Boolean,
    repeatMode: Int,
    autoplay: Boolean,
    firstCardFocus: FocusRequester,
    onJump: (Int) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleAutoplay: () -> Unit,
) {
    val upcoming = remember(queue, currentIndex) { queue.drop(currentIndex + 1) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Up Next",
                style = TvType.Headline,
                color = TvGlass.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvTransportButton(
                    icon = BitChordIcons.Shuffle,
                    contentDescription = "Shuffle",
                    active = shuffle,
                    size = 32.dp,
                    onClick = onToggleShuffle,
                )
                TvTransportButton(
                    icon = if (repeatMode == Player.REPEAT_MODE_ONE) null else BitChordIcons.Repeat,
                    contentDescription = when (repeatMode) {
                        Player.REPEAT_MODE_ONE -> "Repeat One"
                        Player.REPEAT_MODE_ALL -> "Repeat All"
                        else -> "Repeat Off"
                    },
                    active = repeatMode != Player.REPEAT_MODE_OFF,
                    badge = if (repeatMode == Player.REPEAT_MODE_ONE) "1" else null,
                    size = 32.dp,
                    onClick = onCycleRepeat,
                )
                TvTransportButton(
                    icon = BitChordIcons.Infinity,
                    contentDescription = "Autoplay",
                    active = autoplay,
                    size = 32.dp,
                    onClick = onToggleAutoplay,
                )
            }
        }
        if (upcoming.isEmpty()) {
            Text(
                text = if (autoplay) "Similar music will play when this song ends." else "Nothing is queued after this song.",
                style = TvType.Callout,
                color = TvGlass.TextSecondary,
                modifier = Modifier
                    .padding(vertical = 21.dp)
                    .focusRequester(firstCardFocus)
                    .focusable(),
            )
        } else {
            val indexed = remember(upcoming) { upcoming.withIndex().toList() }
            TvRow(
                items = indexed,
                key = { "${it.value.videoId}#${it.index}" },
                leading = 0.dp,
                trailing = 19.dp,
                spacing = 16.dp,
                top = 13.dp,
                bottom = 5.dp,
            ) { i, entry ->
                TvLockup(
                    title = entry.value.title,
                    subtitle = entry.value.artist,
                    artworkUrl = entry.value.thumbnailUrl,
                    width = 99.dp,
                    onClick = { onJump(currentIndex + 1 + i) },
                    modifier = if (i == 0) Modifier.focusRequester(firstCardFocus) else Modifier,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Feedback
// ─────────────────────────────────────────────────────────────────────────────

internal enum class TvFlash { Play, Pause, Next, Previous }

/**
 * The glyph that answers a remote press while the transport is hidden — play,
 * pause, next, previous — in the phone's own transport icons. Shows, holds, fades.
 */
@Composable
internal fun TvFlashGlyph(flash: TvFlash?, tick: Int, modifier: Modifier = Modifier) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(tick) {
        if (tick == 0) return@LaunchedEffect
        alpha.snapTo(1f)
        delay(420)
        alpha.animateTo(0f, tween(360))
    }
    val shown = flash ?: return
    Box(
        modifier = modifier
            .size(94.dp)
            .graphicsLayer {
                this.alpha = alpha.value
                val s = 0.92f + 0.08f * alpha.value
                scaleX = s
                scaleY = s
            }
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.34f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(
                when (shown) {
                    TvFlash.Play -> Res.drawable.ic_player_play
                    TvFlash.Pause -> Res.drawable.ic_player_pause
                    TvFlash.Next -> Res.drawable.ic_player_next
                    TvFlash.Previous -> Res.drawable.ic_player_previous
                },
            ),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(40.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Nothing playing
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun TvNothingPlaying(onBrowse: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(38.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = BitChordIcons.MusicNote,
            contentDescription = null,
            tint = TvGlass.TextTertiary,
            modifier = Modifier.size(58.dp),
        )
        Spacer(modifier = Modifier.height(19.dp))
        Text(text = "Not Playing", style = TvType.Title, color = TvGlass.TextPrimary)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Choose something from Home, Explore or your Library.",
            style = TvType.Callout,
            color = TvGlass.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 384.dp),
        )
        Spacer(modifier = Modifier.height(21.dp))
        TvButton(text = "Browse Music", onClick = onBrowse, modifier = Modifier.width(208.dp).tvInitialFocus())
    }
}
