/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player.simpmusic

import androidx.activity.compose.BackHandler
import moe.rukamori.archivetune.ui.utils.smoothFadingEdge
import androidx.compose.ui.graphics.luminance
import moe.rukamori.archivetune.ui.menu.AddToPlaylistDialog
import moe.rukamori.archivetune.lyrics.LyricsUtils
import moe.rukamori.archivetune.extensions.toMediaItem
import androidx.room.withTransaction
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.LocalStableSystemBarsTopPadding
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.innertube.models.MediaInfo
import moe.rukamori.archivetune.lyrics.LyricsUtils.findCurrentLineIndex
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.component.BottomSheetPageState
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.component.LyricsEnhanced
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.menu.PlayerMenu
import moe.rukamori.archivetune.ui.player.LosslessOrStats
import moe.rukamori.archivetune.ui.player.rememberInlineLyricLines
import moe.rukamori.archivetune.ui.player.rememberMeshPalette
import moe.rukamori.archivetune.ui.utils.ShowMediaInfo
import moe.rukamori.archivetune.ui.utils.highRes
import moe.rukamori.archivetune.ui.utils.rememberMediaInfo
import java.util.Locale
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private val Backdrop = Color(0xFF121212)

private val CardPanel = Color(0xFF212121)

private const val MAX_SURFACE_LUMINANCE = 0.10f

private fun Color.asSurface(): Color {
    var c = this
    var steps = 0
    while (c.luminance() > MAX_SURFACE_LUMINANCE && steps++ < 16) {
        c = lerp(c, Backdrop, 0.2f)
    }
    return c
}

private val Seed = Color(0xFF8ECAE6)

private const val CARD_LYRICS_SIZE_SP = 16f

private val Gutter = 20.dp

private val MinGap = 30.dp

@Composable
fun SimpMusicPlayerContent(
    mediaMetadata: MediaMetadata,
    isPlaying: Boolean,
    isLoading: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    playerConnection: PlayerConnection,
    navController: NavController,
    state: BottomSheetState,
    menuState: MenuState,
    bottomSheetPageState: BottomSheetPageState,
    currentFormat: FormatEntity?,
    onSeek: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    externalBackRequests: androidx.compose.runtime.MutableIntState? = null,
    onStylePageOpenChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val queueWindows by playerConnection.queueWindows.collectAsStateWithLifecycle()
    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsStateWithLifecycle()
    val queueTitle by playerConnection.queueTitle.collectAsStateWithLifecycle()

    val artUrl =
        remember(mediaMetadata.id, mediaMetadata.thumbnailUrl) {
            mediaMetadata.thumbnailUrl?.highRes()
        }
    val palette = rememberMeshPalette(artUrl)

    val startColor = (palette.colors.getOrNull(0) ?: Backdrop).asSurface()
    val endColor = (palette.colors.getOrNull(1) ?: lerp(startColor, Backdrop, 0.6f)).asSurface()

    val mediaInfo = rememberMediaInfo(mediaMetadata.id)

    val scrollState = rememberScrollState()

    var hasScrolled by remember { mutableStateOf(false) }
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value > 0 }.first { it }
        hasScrolled = true
    }
    var queueOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = queueOpen) { queueOpen = false }

    var lyricsFullscreenOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(mediaMetadata.id) { lyricsFullscreenOpen = false }
    BackHandler(enabled = lyricsFullscreenOpen) { lyricsFullscreenOpen = false }

    LaunchedEffect(state.isExpandedOrExpanding) {
        if (!state.isExpandedOrExpanding && (queueOpen || lyricsFullscreenOpen)) {
            queueOpen = false
            lyricsFullscreenOpen = false
        }
    }
    val simpmusicStylePageOpen = queueOpen || lyricsFullscreenOpen
    LaunchedEffect(simpmusicStylePageOpen) { onStylePageOpenChange(simpmusicStylePageOpen) }
    val externalBackEpoch = externalBackRequests?.intValue ?: 0
    LaunchedEffect(externalBackEpoch) {
        if (externalBackEpoch > 0) {
            if (lyricsFullscreenOpen) lyricsFullscreenOpen = false
            if (queueOpen) queueOpen = false
        }
    }

    var topBarHeight by remember { mutableStateOf(0.dp) }
    var infoHeight by remember { mutableStateOf(0.dp) }

    MaterialTheme(typography = SimpMusicTypography) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenHeight = maxHeight

        val artworkSide =
            (maxWidth - Gutter * 2)
                .coerceAtMost(screenHeight - topBarHeight - infoHeight - MinGap * 2)
                .coerceAtLeast(0.dp)
        val gap =
            ((screenHeight - topBarHeight - artworkSide - infoHeight - MinGap) / 2)
                .coerceAtLeast(MinGap)
        val screenHeightPx = with(density) { screenHeight.toPx() }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Backdrop)
                    .simpMusicHeroWash(startColor, endColor, screenHeightPx)

                    .verticalScroll(scrollState, enabled = state.isExpanded),
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(screenHeight)) {
                SimpMusicArtworkPager(
                    queueWindows = queueWindows,
                    currentWindowIndex = currentWindowIndex,
                    fallback = mediaMetadata,
                    playerConnection = playerConnection,
                    topInset = topBarHeight + gap,
                    side = artworkSide,
                    modifier = Modifier.fillMaxWidth().height(screenHeight),
                )

                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(topBarHeight))
                    Spacer(Modifier.height(gap))

                    Spacer(Modifier.fillMaxWidth().height(artworkSide))

                    SimpMusicLyricLine(
                        playerConnection = playerConnection,

                        active = isPlaying && state.isExpanded,
                        modifier = Modifier.fillMaxWidth().height(gap),
                    )

                    Column(
                        modifier =
                            Modifier.onGloballyPositioned {
                                infoHeight = with(density) { it.size.height.toDp() }
                            },
                    ) {
                        SimpMusicTrackInfoRow(
                            mediaMetadata = mediaMetadata,
                            playerConnection = playerConnection,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter),
                        )

                        Spacer(Modifier.height(15.dp))

                        SimpMusicProgressRow(
                            sliderPosition = sliderPosition,
                            position = position,
                            duration = duration,
                            isLoading = isLoading,
                            currentFormat = currentFormat,
                            onSeek = onSeek,
                            onSeekFinished = onSeekFinished,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter),
                        )

                        Spacer(Modifier.height(6.dp))

                        SimpMusicTransportRow(
                            isPlaying = isPlaying,
                            canSkipPrevious = canSkipPrevious,
                            canSkipNext = canSkipNext,
                            playerConnection = playerConnection,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter),
                        )

                        SimpMusicActionRow(
                            mediaMetadata = mediaMetadata,
                            playerConnection = playerConnection,
                            bottomSheetPageState = bottomSheetPageState,
                            onOpenQueue = { queueOpen = true },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter),
                        )
                    }
                }

                SimpMusicTopBar(
                    playlistName = queueTitle ?: mediaMetadata.album?.title ?: "",
                    onCollapse = state::collapseSoft,
                    onMenu = {
                        menuState.show {
                            PlayerMenu(
                                mediaMetadata = mediaMetadata,
                                navController = navController,
                                playerBottomSheetState = state,
                                onShowDetailsDialog = {
                                    bottomSheetPageState.show { ShowMediaInfo(mediaMetadata.id) }
                                },
                                onDismiss = menuState::dismiss,
                            )
                        }
                    },
                    modifier =
                        Modifier.fillMaxWidth().onGloballyPositioned {
                            topBarHeight = with(density) { it.size.height.toDp() }
                        },
                )
            }

            Column(modifier = Modifier.padding(horizontal = Gutter)) {
                SimpMusicLyricsCard(
                    playerConnection = playerConnection,
                    containerColor = startColor,

                    renderLyrics = hasScrolled && !lyricsFullscreenOpen,
                    onShowLyrics = { lyricsFullscreenOpen = true },
                    modifier = Modifier.padding(top = 10.dp),
                )
                Spacer(Modifier.height(10.dp))
                SimpMusicArtistCard(
                    info = mediaInfo,
                    onOpenArtist = { id -> navController.navigate("artist/$id") },
                )
                Spacer(Modifier.height(10.dp))
                SimpMusicInfoCard(info = mediaInfo, containerColor = startColor)
                Spacer(Modifier.height(10.dp))
                Spacer(
                    Modifier.height(
                        WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
                    ),
                )
            }
        }

        val toolbarVisible by remember(screenHeightPx) {
            derivedStateOf { scrollState.value > screenHeightPx * 0.6f }
        }
        AnimatedVisibility(
            visible = toolbarVisible && state.isExpanded,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
        ) {
            SimpMusicStickyToolbar(
                mediaMetadata = mediaMetadata,
                isPlaying = isPlaying,
                playerConnection = playerConnection,
                containerColor = lerp(startColor, Color.Black, 0.18f),
            )
        }

        if (queueOpen) {
            SimpMusicQueueSheet(
                playerConnection = playerConnection,
                navController = navController,
                onDismiss = { queueOpen = false },
            )
        }

        if (lyricsFullscreenOpen) {
            SimpMusicFullscreenLyricsSheet(
                mediaMetadata = mediaMetadata,
                playerConnection = playerConnection,
                navController = navController,
                bottomSheetPageState = bottomSheetPageState,
                color = startColor,
                onDismiss = { lyricsFullscreenOpen = false },
                paletteColors = palette.colors,
            )
        }
    }
    }
}

private fun Modifier.simpMusicHeroWash(
    start: Color,
    end: Color,
    screenHeightPx: Float,
): Modifier =
    this.drawBehind {
        val area = Size(size.width, screenHeightPx)
        drawRect(
            brush =
                Brush.linearGradient(
                    colors = listOf(start, end),
                    start = Offset.Zero,
                    end = Offset(size.width, screenHeightPx),
                ),
            size = area,
        )

        drawRect(
            brush =
                Brush.verticalGradient(
                    0.0f to Backdrop.copy(alpha = 0f),
                    0.55f to Backdrop.copy(alpha = 0.45f),
                    0.95f to Backdrop,
                    startY = 0f,
                    endY = screenHeightPx,
                ),
            size = area,
        )
    }

@Composable
private fun SimpMusicTopBar(
    playlistName: String,
    onCollapse: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .padding(top = LocalStableSystemBarsTopPadding.current)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onCollapse) {
            Icon(
                painter = painterResource(R.drawable.simpmusic_keyboard_arrow_down),
                contentDescription = stringResource(R.string.collapse),
                tint = Color.White,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.now_playing).uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                maxLines = 1,
            )

            Text(
                text = playlistName,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .basicMarquee(
                            iterations = Int.MAX_VALUE,
                            animationMode = MarqueeAnimationMode.Immediately,
                        ),
            )
        }
        IconButton(onClick = onMenu) {
            Icon(
                painter = painterResource(R.drawable.simpmusic_more_vert),
                contentDescription = stringResource(R.string.more_options),
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun SimpMusicArtworkPager(
    queueWindows: List<Timeline.Window>,
    currentWindowIndex: Int,
    fallback: MediaMetadata,
    playerConnection: PlayerConnection,
    topInset: androidx.compose.ui.unit.Dp,
    side: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    if (queueWindows.isEmpty()) {
        SimpMusicArtwork(fallback, topInset, side, modifier)
        return
    }

    val pagerState =
        rememberPagerState(initialPage = currentWindowIndex.coerceAtLeast(0)) { queueWindows.size }
    var pendingSeek by remember { mutableStateOf<Int?>(null) }
    val liveIndex = rememberUpdatedState(currentWindowIndex)
    val liveQueue = rememberUpdatedState(queueWindows)

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { settled ->
                val index = liveIndex.value
                if (settled != index && settled in liveQueue.value.indices) {
                    pendingSeek = settled
                    when (settled) {
                        index + 1 -> playerConnection.seekToNext()
                        index - 1 -> playerConnection.seekToPrevious()
                        else -> playerConnection.player.seekTo(settled, 0)
                    }
                }
            }
    }

    LaunchedEffect(currentWindowIndex, queueWindows.size) {
        val pending = pendingSeek
        if (pending != null) {
            if (currentWindowIndex == pending) pendingSeek = null
            return@LaunchedEffect
        }
        if (currentWindowIndex !in queueWindows.indices) return@LaunchedEffect
        if (pagerState.currentPage == currentWindowIndex) return@LaunchedEffect
        if (kotlin.math.abs(currentWindowIndex - pagerState.currentPage) == 1) {
            pagerState.animateScrollToPage(currentWindowIndex)
        } else {
            pagerState.scrollToPage(currentWindowIndex)
        }
    }

    HorizontalPager(state = pagerState, beyondViewportPageCount = 1, modifier = modifier) { page ->
        SimpMusicArtwork(
            metadata = queueWindows.getOrNull(page)?.mediaItem?.metadata ?: fallback,
            topInset = topInset,
            side = side,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun SimpMusicArtwork(
    metadata: MediaMetadata,
    topInset: androidx.compose.ui.unit.Dp,
    side: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val sleevePalette = rememberMeshPalette(metadata.thumbnailUrl?.highRes())
    val spotColor = sleevePalette.colors.firstOrNull() ?: Color.Black
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(topInset))
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(side)
                    .shadow(
                        elevation = 3.dp,
                        shape = RoundedCornerShape(8.dp),
                        spotColor = spotColor.copy(alpha = 0.6f),
                        ambientColor = Color.Transparent,
                    ),
        ) {
            AsyncImage(
                model = metadata.thumbnailUrl?.highRes(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(3.dp)
                        .clip(RoundedCornerShape(8.dp)),
            )
        }
    }
}

@Composable
private fun SimpMusicLyricLine(
    playerConnection: PlayerConnection,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val lines = rememberInlineLyricLines(playerConnection)
    var line by remember(lines) { mutableStateOf("") }

    LaunchedEffect(lines, active) {
        if (lines.isEmpty() || !active) {
            line = ""
            return@LaunchedEffect
        }
        while (true) {
            val index = findCurrentLineIndex(lines, playerConnection.player.currentPosition)
            line = lines.getOrNull(index)?.text.orEmpty()
            delay(200L)
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Crossfade(targetState = line, animationSpec = tween(300), label = "simpMusicLyricLine") { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                maxLines = 1,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Gutter)
                        .basicMarquee(
                            iterations = Int.MAX_VALUE,
                            animationMode = MarqueeAnimationMode.Immediately,
                        ),
            )
        }
    }
}

@Composable
private fun SimpMusicTrackInfoRow(
    mediaMetadata: MediaMetadata,
    playerConnection: PlayerConnection,
    modifier: Modifier = Modifier,
) {
    val database = LocalDatabase.current
    val currentSong by playerConnection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val liked = currentSong?.song?.liked == true
    var showPlaylistDialog by rememberSaveable { mutableStateOf(false) }

    AddToPlaylistDialog(
        isVisible = showPlaylistDialog,
        onGetSong = {
            database.withTransaction { insert(mediaMetadata) }
            listOf(mediaMetadata.id)
        },
        onDismiss = { showPlaylistDialog = false },
    )

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = mediaMetadata.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                modifier =
                    Modifier.basicMarquee(
                        iterations = Int.MAX_VALUE,
                        animationMode = MarqueeAnimationMode.Immediately,
                    ),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = mediaMetadata.artists.joinToString { it.name },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.66f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { showPlaylistDialog = true }, modifier = Modifier.size(36.dp)) {
            Icon(
                painter = painterResource(R.drawable.simpmusic_add_circle_outline),
                contentDescription = stringResource(R.string.add_to_playlist),
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.size(12.dp))
        IconButton(onClick = playerConnection::toggleLike, modifier = Modifier.size(40.dp)) {
            Icon(
                painter =
                    painterResource(
                        if (liked) R.drawable.simpmusic_favorite else R.drawable.simpmusic_favorite_border,
                    ),
                contentDescription = stringResource(R.string.action_like),
                tint = if (liked) MaterialTheme.colorScheme.error else Color.White,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpMusicProgressRow(
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    isLoading: Boolean,
    currentFormat: FormatEntity?,
    onSeek: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasDuration = duration > 0L && duration != C.TIME_UNSET
    val safeDuration = if (hasDuration) duration else 1L
    val shown = (sliderPosition ?: position).coerceIn(0L, safeDuration)
    val trackColor = Color.White

    Column(modifier = modifier) {
        Slider(
            value = shown.toFloat() / safeDuration.toFloat(),
            onValueChange = { onSeek((it * safeDuration).toLong()) },
            onValueChangeFinished = onSeekFinished,
            track = { sliderState ->
                SliderDefaults.Track(
                    modifier = Modifier.height(5.dp),
                    enabled = true,
                    sliderState = sliderState,
                    colors =
                        SliderDefaults.colors().copy(
                            thumbColor = trackColor,
                            activeTrackColor = trackColor,

                            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                        ),
                    thumbTrackGapSize = 0.dp,
                    drawTick = { _, _ -> },
                    drawStopIndicator = null,
                )
            },
            thumb = {
                SliderDefaults.Thumb(
                    modifier = Modifier.height(18.dp).width(8.dp).padding(vertical = 4.dp),
                    thumbSize = DpSize(8.dp, 8.dp),
                    interactionSource = remember { MutableInteractionSource() },
                    colors = SliderDefaults.colors().copy(thumbColor = trackColor),
                    enabled = true,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = clockTime(shown),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.weight(1f),
            )

            LosslessOrStats(isLoading = isLoading, format = currentFormat)
            Text(
                text = if (hasDuration) clockTime(duration) else "",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.55f),
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun clockTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0L)
    return String.format(Locale.getDefault(), "%02d:%02d", total / 60, total % 60)
}

@Composable
private fun SimpMusicTransportRow(
    isPlaying: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    playerConnection: PlayerConnection,
    modifier: Modifier = Modifier,
) {
    val shuffleEnabled by playerConnection.shuffleModeEnabled.collectAsStateWithLifecycle()
    val repeatMode by playerConnection.repeatMode.collectAsStateWithLifecycle()

    Row(
        modifier = modifier.height(96.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SimpMusicControl(
            cell = 42.dp,
            icon = 32.dp,
            painter = painterResource(R.drawable.simpmusic_shuffle),
            contentDescription = stringResource(R.string.shuffle),
            tint = if (shuffleEnabled) Seed else Color.White,
            onClick = { playerConnection.player.shuffleModeEnabled = !shuffleEnabled },
        )
        SimpMusicControl(
            cell = 52.dp,
            icon = 42.dp,
            painter = painterResource(R.drawable.simpmusic_skip_previous),
            contentDescription = stringResource(R.string.widget_previous),
            tint = Color.White.copy(alpha = if (canSkipPrevious) 1f else 0.4f),
            enabled = canSkipPrevious,
            onClick = playerConnection::seekToPrevious,
        )
        SimpMusicControl(
            cell = 96.dp,
            icon = 72.dp,
            painter =
                painterResource(
                    if (isPlaying) R.drawable.simpmusic_pause_circle else R.drawable.simpmusic_play_circle,
                ),
            contentDescription = stringResource(if (isPlaying) R.string.widget_pause else R.string.play),
            tint = Color.White,
            onClick = { playerConnection.player.togglePlayPause() },
        )
        SimpMusicControl(
            cell = 52.dp,
            icon = 42.dp,
            painter = painterResource(R.drawable.simpmusic_skip_next),
            contentDescription = stringResource(R.string.next),
            tint = Color.White.copy(alpha = if (canSkipNext) 1f else 0.4f),
            enabled = canSkipNext,
            onClick = playerConnection::seekToNext,
        )
        SimpMusicControl(
            cell = 42.dp,
            icon = 32.dp,
            painter =
                painterResource(
                    if (repeatMode == Player.REPEAT_MODE_ONE) {
                        R.drawable.simpmusic_repeat_one
                    } else {
                        R.drawable.simpmusic_repeat
                    },
                ),
            contentDescription =
                stringResource(
                    when (repeatMode) {
                        Player.REPEAT_MODE_ONE -> R.string.repeat_mode_one
                        Player.REPEAT_MODE_ALL -> R.string.repeat_mode_all
                        else -> R.string.repeat_mode_off
                    },
                ),
            tint = if (repeatMode == Player.REPEAT_MODE_OFF) Color.White else Seed,
            onClick = {
                playerConnection.player.repeatMode =
                    when (repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
            },
        )
    }
}

@Composable
private fun RowScope.SimpMusicControl(
    cell: androidx.compose.ui.unit.Dp,
    icon: androidx.compose.ui.unit.Dp,
    painter: androidx.compose.ui.graphics.painter.Painter,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
        Box(
            modifier =
                Modifier
                    .size(cell)
                    .clip(CircleShape)
                    .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painter,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(icon),
            )
        }
    }
}

@Composable
private fun SimpMusicActionRow(
    mediaMetadata: MediaMetadata,
    playerConnection: PlayerConnection,
    bottomSheetPageState: BottomSheetPageState,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.height(32.dp), verticalAlignment = Alignment.CenterVertically) {
        SimpMusicActionIcon(
            painter = painterResource(R.drawable.simpmusic_info),
            contentDescription = stringResource(R.string.details),
            onClick = { bottomSheetPageState.show { ShowMediaInfo(mediaMetadata.id) } },
        )
        Spacer(Modifier.weight(1f))
        SimpMusicActionIcon(
            painter = painterResource(R.drawable.simpmusic_playlist_add),
            contentDescription = stringResource(R.string.play_next),
            onClick = { playerConnection.playNext(mediaMetadata.toMediaItem()) },
        )
        Spacer(Modifier.size(12.dp))
        SimpMusicActionIcon(
            painter = painterResource(R.drawable.simpmusic_queue_music),
            contentDescription = stringResource(R.string.queue),
            onClick = onOpenQueue,
        )
    }
}

@Composable
private fun SimpMusicActionIcon(
    painter: androidx.compose.ui.graphics.painter.Painter,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.size(24.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painter = painter, contentDescription = contentDescription, tint = Color.White)
    }
}

@Composable
private fun SimpMusicLyricsCard(
    playerConnection: PlayerConnection,
    containerColor: Color,
    renderLyrics: Boolean,
    onShowLyrics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lyricsPositionProvider = remember { { null as Long? } }

    val lyricsEntity by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
    val lyricsText = lyricsEntity?.lyrics
    val hasLyrics = lyricsText?.isNotBlank() == true && lyricsText != LYRICS_NOT_FOUND
    if (!hasLyrics) return

    val syncLabel =
        when {
            LyricsUtils.isTtml(lyricsText!!) -> stringResource(R.string.rich_synced)
            LyricsUtils.isLineSyncedLrc(lyricsText) -> stringResource(R.string.line_synced)
            else -> stringResource(R.string.unsynced)
        }
    val provider = lyricsEntity?.providerName?.takeIf { it.isNotBlank() }

    ElevatedCard(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.elevatedCardColors().copy(containerColor = containerColor),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.lyrics),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
                Spacer(Modifier.weight(1f))
                SimpMusicActionIcon(
                    painter = painterResource(R.drawable.simpmusic_share),
                    contentDescription = stringResource(R.string.share),
                    onClick = {
                        val body = lyricsText.lineSequence().joinToString("\n") { it.substringAfter("]") }
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, body)
                                },
                                null,
                            ),
                        )
                    },
                )
                Spacer(Modifier.size(8.dp))
                TextButton(
                    onClick = onShowLyrics,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.height(20.dp),
                ) {
                    Text(text = stringResource(R.string.show), color = Color.White)
                }
            }
            Spacer(Modifier.height(18.dp))
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(300.dp)

                        .smoothFadingEdge(vertical = 36.dp),
            ) {
                if (!renderLyrics) {
                } else {
                    LyricsEnhanced(
                        sliderPositionProvider = lyricsPositionProvider,
                        lyricsSyncOffset = 0,
                        modifier = Modifier.fillMaxSize(),
                        textColorOverride = Color.White,
                        textSizeOverride = CARD_LYRICS_SIZE_SP,
                    )
                }
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                Text(
                    text = syncLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.45f),
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
                if (provider != null) {
                    Text(
                        text = stringResource(R.string.lyrics_provided_by, provider),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.45f),
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SimpMusicArtistCard(
    info: MediaInfo?,
    onOpenArtist: (String) -> Unit,
) {
    AnimatedVisibility(visible = info?.author != null) {
        val author = info?.author.orEmpty()
        val authorId = info?.authorId
        ElevatedCard(
            onClick = { authorId?.let(onOpenArtist) },
            enabled = authorId != null,
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.elevatedCardColors().copy(containerColor = CardPanel),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.fillMaxWidth().height(250.dp)) {
                    AsyncImage(
                        model = info?.authorThumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )

                    Box(
                        modifier =
                            Modifier
                                .matchParentSize()
                                .background(
                                    Brush.verticalGradient(
                                        0f to Color.Black.copy(alpha = 0.6f),
                                        0.4f to Color.Transparent,
                                    ),
                                ),
                    )
                    Text(
                        text = stringResource(R.string.artists),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.TopStart).padding(15.dp),
                    )
                }
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 12.dp)) {
                    Text(
                        text = author,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                    info?.subscribers?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SimpMusicInfoCard(
    info: MediaInfo?,
    containerColor: Color,
) {
    AnimatedVisibility(visible = info?.viewCount != null || info?.description != null) {
        ElevatedCard(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.elevatedCardColors().copy(containerColor = containerColor),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(15.dp).fillMaxWidth()) {
                info?.uploadDate?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = stringResource(R.string.published_on, it),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                info?.viewCount?.let {
                    Text(
                        text = stringResource(R.string.view_count_value, groupDigits(it)),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                if (info?.like != null || info?.dislike != null) {
                    Text(
                        text =
                            stringResource(
                                R.string.like_and_dislike,
                                groupDigits(info.like ?: 0),
                                groupDigits(info.dislike ?: 0),
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                    Spacer(Modifier.height(10.dp))
                }
                info?.description?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = stringResource(R.string.description),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SimpMusicStickyToolbar(
    mediaMetadata: MediaMetadata,
    isPlaying: Boolean,
    playerConnection: PlayerConnection,
    containerColor: Color,
) {
    val currentSong by playerConnection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val liked = currentSong?.song?.liked == true

    ElevatedCard(
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.elevatedCardColors().copy(containerColor = containerColor),
        elevation = CardDefaults.elevatedCardElevation(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .padding(top = LocalStableSystemBarsTopPadding.current)
                    .padding(start = Gutter, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mediaMetadata.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = mediaMetadata.artists.joinToString { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.66f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = playerConnection::toggleLike) {
                Icon(
                    painter =
                        painterResource(
                            if (liked) R.drawable.player_favorite else R.drawable.player_favorite_border,
                        ),
                    contentDescription = stringResource(R.string.action_like),
                    tint = if (liked) MaterialTheme.colorScheme.error else Color.White,
                )
            }
            IconButton(onClick = { playerConnection.player.togglePlayPause() }) {
                Icon(
                    painter =
                        painterResource(
                            if (isPlaying) R.drawable.player_pause else R.drawable.player_play,
                        ),
                    contentDescription =
                        stringResource(if (isPlaying) R.string.widget_pause else R.string.play),
                    tint = Color.White,
                )
            }
        }
    }
}

private fun groupDigits(value: Int): String = String.format(Locale.getDefault(), "%,d", value)
