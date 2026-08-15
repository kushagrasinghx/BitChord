package com.music.bitchord.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.ui.components.MessageState
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.PullToRefresh
import com.music.bitchord.ui.components.ROW_DIVIDER_INSET
import com.music.bitchord.ui.components.SongRow
import com.music.bitchord.ui.components.librarySkeleton

/**
 * The signed-in library: saved collections as shelves of cards, then the
 * tracks — Liked Music first, since that's the list people actually come here
 * for, with anything else added to the library under it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    signedIn: Boolean,
    state: UiState<LibraryPage>,
    listState: LazyListState,
    onSongClick: (List<Song>, Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onSongSwipe: (Song) -> Unit,
    onShelfItemClick: (ShelfItem) -> Unit,
    onSignIn: () -> Unit,
    onRetry: () -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    pullState: PullToRefreshState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
) {
    PullToRefresh(
        refreshing = refreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = modifier,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            item {
                Text(
                    text = "Library",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = PAGE_GUTTER, vertical = 8.dp),
                )
            }
            if (!signedIn) {
                item {
                    MessageState(
                        message = "Sign in to your Google account to see your YouTube Music " +
                            "liked songs, playlists and history.",
                        actionLabel = "Sign in",
                        onAction = onSignIn,
                    )
                }
                return@LazyColumn
            }
            when (state) {
                is UiState.Loading -> librarySkeleton()
                is UiState.Error -> item {
                    MessageState(state.message, actionLabel = "Retry", onAction = onRetry)
                }
                is UiState.Success -> {
                    state.data.shelves.forEach { shelf ->
                        item(key = "shelf:${shelf.title}") {
                            Shelf(shelf = shelf, onItemClick = onShelfItemClick)
                        }
                    }
                    songSection("Liked Music", state.data.likedSongs, onSongClick, onSongLongPress, onSongSwipe)
                    songSection("Songs", state.data.librarySongs, onSongClick, onSongLongPress, onSongSwipe)
                }
            }
        }
    }
}

/** A titled run of track rows; contributes nothing when the list is empty. */
private fun LazyListScope.songSection(
    title: String,
    songs: List<Song>,
    onSongClick: (List<Song>, Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onSongSwipe: (Song) -> Unit,
) {
    if (songs.isEmpty()) return
    item(key = "header:$title") { SectionHeader(title) }
    itemsIndexed(songs, key = { _, song -> "$title:${song.videoId}" }) { index, song ->
        SongRow(
            song = song,
            onClick = { onSongClick(songs, index) },
            onLongPress = { onSongLongPress(song) },
            onSwipeToQueue = { onSongSwipe(song) },
        )
        if (index < songs.lastIndex) {
            HorizontalDivider(
                modifier = Modifier.padding(start = ROW_DIVIDER_INSET),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
