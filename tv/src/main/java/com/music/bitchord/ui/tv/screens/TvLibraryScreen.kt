package com.music.bitchord.ui.tv.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.playback.PlayerState
import com.music.bitchord.playback.playSongs
import com.music.bitchord.ui.MainViewModel
import com.music.bitchord.ui.tv.components.TvActivityIndicator
import com.music.bitchord.ui.tv.components.TvButton
import com.music.bitchord.ui.tv.components.TvChromePinned
import com.music.bitchord.ui.tv.components.TvEmptyState
import com.music.bitchord.ui.tv.components.TvErrorState
import com.music.bitchord.ui.tv.components.TvListRow
import com.music.bitchord.ui.tv.components.TvLockup
import com.music.bitchord.ui.tv.components.TvSongRow
import com.music.bitchord.ui.tv.dialogs.TvSongMenu
import com.music.bitchord.ui.tv.theme.TvDimensions
import com.music.bitchord.ui.tv.theme.TvGlass
import kotlinx.coroutines.launch

/** One entry in Library's sidebar. [key] survives the library reloading under it. */
private sealed class LibraryCategory(val key: String, val title: String) {
    data object RecentlyPlayed : LibraryCategory("recent", "Recently Played")
    data object Liked : LibraryCategory("liked", "Liked Songs")
    data object Songs : LibraryCategory("songs", "Songs")
    class Collection(val shelf: HomeShelf) : LibraryCategory("shelf:${shelf.title}", shelf.title)
}

/**
 * Library, as Apple Music lays it out on tvOS: categories down the left, the
 * focused one's contents on the right. The sidebar follows focus, so moving
 * down it previews each category; Downloaded and On This Device open pages.
 */
@Composable
fun TvLibraryScreen(
    viewModel: MainViewModel,
    mediaController: MediaController?,
    playerState: PlayerState,
    onNavigateToDetail: (browseId: String, title: String, subtitle: String, thumbnailUrl: String?, type: BrowseType) -> Unit,
    onNavigateToNowPlaying: () -> Unit,
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvChromePinned()
    val signedIn by viewModel.signedIn.collectAsState()
    val libraryState by viewModel.library.collectAsState()
    val library = (libraryState as? UiState.Success)?.data
    val scope = rememberCoroutineScope()
    var menuSong by remember { mutableStateOf<Song?>(null) }

    val categories = remember(library, signedIn) {
        buildList {
            add(LibraryCategory.RecentlyPlayed)
            if (signedIn && library != null) {
                if (library.likedSongs.isNotEmpty()) add(LibraryCategory.Liked)
                if (library.librarySongs.isNotEmpty()) add(LibraryCategory.Songs)
                library.shelves.filter { it.items.isNotEmpty() }.forEach { add(LibraryCategory.Collection(it)) }
            }
        }
    }
    var selectedKey by rememberSaveable { mutableStateOf(LibraryCategory.RecentlyPlayed.key) }
    val selected = categories.firstOrNull { it.key == selectedKey } ?: categories.first()

    val play: (List<Song>, Int) -> Unit = { songs, index ->
        scope.launch { mediaController?.playSongs(songs, index) }
        onNavigateToNowPlaying()
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(top = TvDimensions.ContentTop),
    ) {
        LazyColumn(
            modifier = Modifier
                .width(300.dp)
                .fillMaxHeight(),
            contentPadding = PaddingValues(start = TvDimensions.SafeMarginHorizontal - 12.dp, end = 8.dp, bottom = 40.dp, top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(categories, key = { it.key }) { category ->
                TvListRow(
                    title = category.title,
                    resting = if (category.key == selected.key) Color.White.copy(alpha = 0.12f) else Color.Transparent,
                    onFocused = { selectedKey = category.key },
                    onClick = { selectedKey = category.key },
                )
            }
            item(key = "downloads") {
                TvListRow(
                    title = "Downloaded",
                    resting = Color.Transparent,
                    trailingIcon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    onClick = { onNavigateToDetail("local:downloads", "Downloaded", "", null, BrowseType.PLAYLIST) },
                )
            }
            item(key = "device") {
                TvListRow(
                    title = "On This Device",
                    resting = Color.Transparent,
                    trailingIcon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    onClick = { onNavigateToDetail("local:all", "On This Device", "", null, BrowseType.PLAYLIST) },
                )
            }
            if (!signedIn) {
                item(key = "signin") {
                    TvListRow(
                        title = "Sign In…",
                        resting = Color.Transparent,
                        onClick = onOpenAccount,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            when (selected) {
                LibraryCategory.RecentlyPlayed -> TvRecentlyPlayed(
                    viewModel = viewModel,
                    playingId = playerState.song?.videoId,
                    isPlaying = playerState.isPlaying,
                    onPlay = play,
                    onSongMenu = { menuSong = it },
                )
                LibraryCategory.Liked -> TvLibrarySongs(
                    songs = library?.likedSongs.orEmpty(),
                    playingId = playerState.song?.videoId,
                    isPlaying = playerState.isPlaying,
                    onPlay = play,
                    onSongMenu = { menuSong = it },
                )
                LibraryCategory.Songs -> TvLibrarySongs(
                    songs = library?.librarySongs.orEmpty(),
                    playingId = playerState.song?.videoId,
                    isPlaying = playerState.isPlaying,
                    onPlay = play,
                    onSongMenu = { menuSong = it },
                )
                is LibraryCategory.Collection -> TvLibraryGrid(
                    shelf = selected.shelf,
                    onPlaySong = { play(listOf(it), 0) },
                    onNavigateToDetail = onNavigateToDetail,
                )
            }

            if (signedIn && selected == LibraryCategory.RecentlyPlayed) {
                when (val state = libraryState) {
                    is UiState.Error -> TvLibraryBanner(message = state.message, onRetry = { viewModel.loadLibrary() })
                    else -> Unit
                }
            }
        }
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

@Composable
private fun TvRecentlyPlayed(
    viewModel: MainViewModel,
    playingId: String?,
    isPlaying: Boolean,
    onPlay: (List<Song>, Int) -> Unit,
    onSongMenu: (Song) -> Unit,
) {
    LaunchedEffect(Unit) { viewModel.loadHistory() }
    val history by viewModel.history.collectAsState()
    when (val state = history) {
        UiState.Loading -> TvPaneLoading()
        is UiState.Error -> TvErrorState(message = state.message, onRetry = { viewModel.loadHistory() })
        is UiState.Success -> if (state.data.isEmpty()) {
            TvEmptyState(title = "Nothing Played Yet", message = "Songs you play show up here.")
        } else {
            TvLibrarySongs(state.data, playingId, isPlaying, onPlay, onSongMenu)
        }
    }
}

@Composable
private fun TvLibrarySongs(
    songs: List<Song>,
    playingId: String?,
    isPlaying: Boolean,
    onPlay: (List<Song>, Int) -> Unit,
    onSongMenu: (Song) -> Unit,
) {
    if (songs.isEmpty()) {
        TvEmptyState(title = "No Songs", message = "There's nothing here yet.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = TvDimensions.SafeMarginHorizontal, top = 6.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(songs, key = { index, song -> "${song.videoId}#$index" }) { index, song ->
            TvSongRow(
                song = song,
                isCurrent = song.videoId == playingId,
                isPlaying = isPlaying,
                showArtwork = true,
                onClick = { onPlay(songs, index) },
                onLongClick = { onSongMenu(song) },
            )
        }
    }
}

@Composable
private fun TvLibraryGrid(
    shelf: HomeShelf,
    onPlaySong: (Song) -> Unit,
    onNavigateToDetail: (browseId: String, title: String, subtitle: String, thumbnailUrl: String?, type: BrowseType) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 28.dp, end = TvDimensions.SafeMarginHorizontal, top = 14.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(TvDimensions.CardSpacing),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        items(shelf.items, key = { it.browseId ?: it.videoId ?: it.title }) { item ->
            val artist = item.browseId?.startsWith("UC") == true
            TvLockup(
                title = item.title,
                subtitle = if (artist) null else item.subtitle,
                artworkUrl = item.thumbnailUrl,
                circle = artist,
                width = 150.dp,
                onClick = { openShelfItem(item, onPlaySong, onNavigateToDetail) },
            )
        }
    }
}

@Composable
private fun TvPaneLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        TvActivityIndicator()
    }
}

/** The library failed to load; Recently Played still works, so this sits at the bottom instead of replacing it. */
@Composable
private fun TvLibraryBanner(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(bottom = 28.dp), contentAlignment = Alignment.BottomCenter) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            androidx.compose.material3.Text(
                text = message,
                style = com.music.bitchord.ui.tv.theme.TvType.Caption,
                color = TvGlass.TextSecondary,
                maxLines = 1,
            )
            TvButton(text = "Reload Library", onClick = onRetry)
        }
    }
}
