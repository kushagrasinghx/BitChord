package com.music.bitchord.ui.tv.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.session.MediaController
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.DetailPage
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.playback.PlayerState
import com.music.bitchord.playback.playSongs
import com.music.bitchord.ui.MainViewModel
import com.music.bitchord.ui.tv.components.ProvideTvComfortScrolling
import com.music.bitchord.ui.tv.components.TvActivityIndicator
import com.music.bitchord.ui.tv.components.TvArtwork
import com.music.bitchord.ui.tv.components.TvButton
import com.music.bitchord.ui.tv.components.TvChromeScrollEffect
import com.music.bitchord.ui.tv.components.TvEmptyState
import com.music.bitchord.ui.tv.components.TvErrorState
import com.music.bitchord.ui.tv.components.TvLockup
import com.music.bitchord.ui.tv.components.TvShelf
import com.music.bitchord.ui.tv.components.TvShelfTitle
import com.music.bitchord.ui.tv.components.TvSongRow
import com.music.bitchord.ui.tv.components.rememberDominantCardColor
import com.music.bitchord.ui.tv.dialogs.TvSongMenu
import com.music.bitchord.ui.tv.theme.TvDimensions
import com.music.bitchord.ui.tv.theme.TvGlass
import com.music.bitchord.ui.tv.theme.TvType
import kotlinx.coroutines.launch

@Composable
fun TvDetailScreen(
    browseId: String,
    initialTitle: String,
    initialSubtitle: String,
    initialThumbnailUrl: String?,
    type: BrowseType,
    viewModel: MainViewModel,
    mediaController: MediaController?,
    playerState: PlayerState,
    onNavigateToDetail: (browseId: String, title: String, subtitle: String, thumbnailUrl: String?, type: BrowseType) -> Unit,
    onNavigateToNowPlaying: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val detailStack by viewModel.detailStack.collectAsState()
    val page = detailStack.lastOrNull { it.browseId == browseId }
    val scope = rememberCoroutineScope()
    var menuSong by remember { mutableStateOf<Song?>(null) }

    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    val reopen = { viewModel.openDetail(browseId, initialTitle, initialSubtitle, initialThumbnailUrl, type) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        reopen()
    }

    LaunchedEffect(browseId) { if (page == null) reopen() }

    val play: (List<Song>, Int) -> Unit = { songs, index ->
        scope.launch { mediaController?.playSongs(songs, index) }
        onNavigateToNowPlaying()
    }
    val shuffle: (List<Song>) -> Unit = { songs -> if (songs.isNotEmpty()) play(songs.shuffled(), 0) }

    if ((page?.type ?: type) == BrowseType.ARTIST) {
        TvArtistContent(
            page = page,
            title = page?.title ?: initialTitle,
            artworkUrl = page?.thumbnailUrl ?: initialThumbnailUrl,
            playingId = playerState.song?.videoId,
            isPlaying = playerState.isPlaying,
            onPlay = play,
            onShuffle = shuffle,
            onSongMenu = { menuSong = it },
            onToggleSubscription = { viewModel.toggleSubscription(browseId) },
            onRetry = reopen,
            onNavigateToDetail = onNavigateToDetail,
            modifier = modifier,
        )
    } else {
        TvReleaseContent(
            page = page,
            title = page?.title ?: initialTitle,
            subtitle = page?.subtitle ?: initialSubtitle,
            artworkUrl = page?.thumbnailUrl ?: initialThumbnailUrl,
            type = page?.type ?: type,
            playingId = playerState.song?.videoId,
            isPlaying = playerState.isPlaying,
            onPlay = play,
            onShuffle = shuffle,
            onSongMenu = { menuSong = it },
            onRetry = reopen,
            onRequestPermission = { permissionLauncher.launch(permissions) },
            onNavigateToDetail = onNavigateToDetail,
            modifier = modifier,
        )
    }

    menuSong?.let { song ->
        TvSongMenu(
            song = song,
            viewModel = viewModel,
            mediaController = mediaController,
            onNavigateToDetail = onNavigateToDetail,
            onDismiss = { menuSong = null },
        )
    }
}

/**
 * An album or a playlist, as Apple Music lays one out: a header — the large
 * cover, then title, artist, how long it runs, the blurb and the circle action
 * row — over a full-width track list with hairline separators. Albums number
 * their tracks; playlists show each track's own cover. The page is washed in a
 * colour taken from the cover.
 */
@Composable
private fun TvReleaseContent(
    page: DetailPage?,
    title: String,
    subtitle: String,
    artworkUrl: String?,
    type: BrowseType,
    playingId: String?,
    isPlaying: Boolean,
    onPlay: (List<Song>, Int) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onSongMenu: (Song) -> Unit,
    onRetry: () -> Unit,
    onRequestPermission: () -> Unit,
    onNavigateToDetail: (browseId: String, title: String, subtitle: String, thumbnailUrl: String?, type: BrowseType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val songsState = page?.songs
    val songs = (songsState as? UiState.Success)?.data.orEmpty()
    val suggested = page?.suggestedSongs.orEmpty()
    val numbered = type == BrowseType.ALBUM
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    TvChromeScrollEffect(listState)

    // The cover's colour, washed down from the top of the page.
    val tint = animateColorAsState(
        targetValue = rememberDominantCardColor(artworkUrl, Color.Transparent),
        animationSpec = tween(500),
        label = "releaseTint",
    )

    ProvideTvComfortScrolling(top = TvDimensions.ContentTop, bottom = 58.dp) {
        LazyColumn(
            state = listState,
            modifier = modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        Brush.verticalGradient(
                            0f to tint.value.copy(alpha = tint.value.alpha * 0.8f),
                            0.75f to Color.Transparent,
                        ),
                    )
                },
            contentPadding = PaddingValues(top = TvDimensions.ContentTop + 5.dp, bottom = 45.dp),
        ) {
            item(key = "header") {
                TvReleaseHeader(
                    title = title,
                    subtitle = subtitle,
                    artworkUrl = artworkUrl,
                    summary = songs.summary(),
                    description = page?.description,
                    canPlay = songs.isNotEmpty(),
                    onPlay = { onPlay(songs, 0) },
                    onShuffle = { onShuffle(songs) },
                    onActionsFocused = { scope.launch { listState.animateScrollToItem(0) } },
                )
            }

            when (songsState) {
                null, UiState.Loading -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                        TvActivityIndicator()
                    }
                }
                is UiState.Error -> item(key = "error") {
                    if (songsState.message.contains("permission", ignoreCase = true)) {
                        TvEmptyState(
                            title = "Allow Access to Music",
                            message = "BitChord needs permission to read music on this TV and on connected USB drives.",
                            modifier = Modifier.height(224.dp),
                            action = { TvButton(text = "Allow Access", onClick = onRequestPermission) },
                        )
                    } else {
                        TvErrorState(message = songsState.message, onRetry = onRetry, modifier = Modifier.height(224.dp))
                    }
                }
                is UiState.Success -> if (songs.isEmpty()) {
                    item(key = "empty") {
                        TvEmptyState(
                            title = "No Songs",
                            message = "There's nothing to play here yet.",
                            modifier = Modifier.height(208.dp),
                        )
                    }
                } else {
                    itemsIndexed(songs, key = { index, song -> "${song.videoId}#$index" }) { index, song ->
                        TvTrackLine(
                            song = song,
                            number = if (numbered) index + 1 else null,
                            isCurrent = song.videoId == playingId,
                            isPlaying = isPlaying,
                            divider = index < songs.lastIndex,
                            onClick = { onPlay(songs, index) },
                            onLongClick = { onSongMenu(song) },
                        )
                    }
                }
            }

            // Tracks YouTube offers to round a playlist out, kept apart from
            // the playlist's own — as the phone does.
            if (suggested.isNotEmpty()) {
                item(key = "suggestedTitle") {
                    TvShelfTitle(title = "Suggestions", modifier = Modifier.padding(top = 24.dp, bottom = 6.dp))
                }
                itemsIndexed(suggested, key = { index, song -> "s:${song.videoId}#$index" }) { index, song ->
                    TvTrackLine(
                        song = song,
                        number = null,
                        isCurrent = song.videoId == playingId,
                        isPlaying = isPlaying,
                        divider = index < suggested.lastIndex,
                        onClick = { onPlay(suggested, index) },
                        onLongClick = { onSongMenu(song) },
                    )
                }
            }

            page?.sections?.forEachIndexed { index, shelf ->
                if (shelf.items.isEmpty()) return@forEachIndexed
                item(key = "section#$index") {
                    TvShelf(
                        title = shelf.title,
                        items = shelf.items,
                        key = { it.videoId ?: it.browseId ?: it.title },
                        modifier = Modifier.padding(top = 21.dp),
                    ) { item ->
                        TvLockup(
                            title = item.title,
                            subtitle = item.subtitle,
                            artworkUrl = item.thumbnailUrl,
                            circle = item.browseId?.startsWith("UC") == true,
                            width = 131.dp,
                            onClick = { openShelfItem(item, { song -> onPlay(listOf(song), 0) }, onNavigateToDetail) },
                        )
                    }
                }
            }
        }
    }
}

/** The release's header: the cover beside what it is, how long it runs, and the actions. */
@Composable
private fun TvReleaseHeader(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    summary: String?,
    description: String?,
    canPlay: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onActionsFocused: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TvDimensions.SafeMarginHorizontal)
            .padding(bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        val shape = RoundedCornerShape(10.dp)
        TvArtwork(
            url = artworkUrl,
            px = 720,
            shape = shape,
            modifier = Modifier
                .size(232.dp)
                .shadow(24.dp, shape),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = TvType.LargeTitle.copy(fontSize = 31.sp, lineHeight = 36.sp, fontWeight = FontWeight.W800),
                color = TvGlass.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    text = subtitle,
                    style = TvType.Body.copy(fontSize = 16.sp, fontWeight = FontWeight.W600),
                    color = TvGlass.AppleRed,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!summary.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(text = summary, style = TvType.Callout, color = TvGlass.TextSecondary, maxLines = 1)
            }
            if (!description.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = description,
                    style = TvType.Callout,
                    color = TvGlass.TextSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 512.dp),
                )
            }
            Spacer(Modifier.height(19.dp))
            TvActionRow(
                canPlay = canPlay,
                onPlay = onPlay,
                onShuffle = onShuffle,
                onFocused = onActionsFocused,
            )
        }
    }
}

/** A track line: a [TvSongRow] with a hairline under it, inset to the text. */
@Composable
private fun TvTrackLine(
    song: Song,
    number: Int?,
    isCurrent: Boolean,
    isPlaying: Boolean,
    divider: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = TvDimensions.SafeMarginHorizontal - 11.dp)) {
        TvSongRow(
            song = song,
            number = number,
            showArtwork = number == null,
            isCurrent = isCurrent,
            isPlaying = isPlaying,
            onClick = onClick,
            onLongClick = onLongClick,
        )
        if (divider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = if (number != null) 43.dp else 56.dp, end = 11.dp)
                    .height(1.dp)
                    .background(TvGlass.Hairline),
            )
        }
    }
}

/** "12 songs, 45 minutes" — from the tracks' own durations. */
private fun List<Song>.summary(): String? {
    if (isEmpty()) return null
    val totalSeconds = sumOf { song ->
        song.durationText
            ?.split(":")
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?.fold(0L) { acc, part -> acc * 60 + part }
            ?: 0L
    }
    val count = if (size == 1) "1 song" else "$size songs"
    val minutes = totalSeconds / 60
    return when {
        totalSeconds <= 0 -> count
        minutes >= 60 -> "$count, ${minutes / 60} hr ${minutes % 60} min"
        else -> "$count, $minutes minutes"
    }
}
