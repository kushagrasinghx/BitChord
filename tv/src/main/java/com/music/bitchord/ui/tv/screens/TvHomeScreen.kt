package com.music.bitchord.ui.tv.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.playback.playSongs
import com.music.bitchord.ui.MainViewModel
import com.music.bitchord.ui.tv.components.TvActivityIndicator
import com.music.bitchord.ui.tv.components.TvCard
import com.music.bitchord.ui.tv.components.TvChromeScrollEffect
import com.music.bitchord.ui.tv.components.TvEmptyState
import com.music.bitchord.ui.tv.components.TvErrorState
import com.music.bitchord.ui.tv.components.ProvideTvFeedScrolling
import com.music.bitchord.ui.tv.components.TvLockup
import com.music.bitchord.ui.tv.components.TvShelf
import com.music.bitchord.ui.tv.components.TvShelfPlaceholder
import com.music.bitchord.ui.tv.dialogs.TvSongMenu
import com.music.bitchord.ui.tv.theme.TvDimensions
import kotlinx.coroutines.launch

@Composable
fun TvHomeScreen(
    viewModel: MainViewModel,
    mediaController: MediaController?,
    onNavigateToDetail: (browseId: String, title: String, subtitle: String, thumbnailUrl: String?, type: BrowseType) -> Unit,
    onNavigateToNowPlaying: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val homeState by viewModel.home.collectAsState()
    val loadingMore by viewModel.homeLoadingMore.collectAsState()
    val scope = rememberCoroutineScope()
    var menuSong by remember { mutableStateOf<Song?>(null) }

    when (val state = homeState) {
        is UiState.Loading -> TvShelvesLoading(modifier)
        is UiState.Error -> TvErrorState(
            message = state.message,
            onRetry = { viewModel.refresh(MainViewModel.Feed.HOME) },
            modifier = modifier,
        )
        is UiState.Success -> {
            if (state.data.isEmpty()) {
                TvEmptyState(
                    title = "Nothing Here Yet",
                    message = "Sign in from Settings, or check your connection, to see your recommendations.",
                    modifier = modifier,
                )
            } else {
                TvShelfFeed(
                    shelves = state.data,
                    loadingMore = loadingMore,
                    onNearEnd = { viewModel.loadMoreHome() },
                    onPlaySong = { song ->
                        scope.launch { mediaController?.playSongs(listOf(song), 0) }
                        onNavigateToNowPlaying()
                    },
                    onSongMenu = { menuSong = it },
                    onNavigateToDetail = onNavigateToDetail,
                    picksFirst = true,
                    modifier = modifier,
                )
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

/** Placeholder shelves while a feed loads. */
@Composable
internal fun TvShelvesLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = TvDimensions.ContentTop + 6.dp),
        verticalArrangement = Arrangement.spacedBy(TvDimensions.ShelfSpacing),
    ) {
        TvShelfPlaceholder(width = 160.dp, count = 5)
        TvShelfPlaceholder()
    }
}

/**
 * A vertical run of shelves under the tab bar — Home, an Explore category, an
 * artist's releases. With [picksFirst] the first shelf gets the large Top Picks
 * cards; every other shelf is lockups.
 */
@Composable
internal fun TvShelfFeed(
    shelves: List<HomeShelf>,
    onPlaySong: (Song) -> Unit,
    onSongMenu: (Song) -> Unit,
    onNavigateToDetail: (browseId: String, title: String, subtitle: String, thumbnailUrl: String?, type: BrowseType) -> Unit,
    modifier: Modifier = Modifier,
    picksFirst: Boolean = false,
    loadingMore: Boolean = false,
    onNearEnd: (() -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    TvChromeScrollEffect(listState)

    if (onNearEnd != null) {
        val nearEnd by remember(listState) {
            derivedStateOf {
                val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                last >= listState.layoutInfo.totalItemsCount - 3
            }
        }
        LaunchedEffect(nearEnd, shelves.size) { if (nearEnd) onNearEnd() }
    }

    // The first shelf's cards rest just under its title: ContentTop, the title's
    // line, then the row's own top padding. Every focused shelf stops there.
    ProvideTvFeedScrolling(anchor = TvDimensions.ContentTop + 19.dp + 11.dp) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = TvDimensions.ContentTop, bottom = 45.dp),
        verticalArrangement = Arrangement.spacedBy(TvDimensions.ShelfSpacing - 11.dp),
    ) {
        if (header != null) {
            item(key = "header", contentType = "header") { header() }
        }
        itemsIndexed(
            items = shelves,
            key = { index, shelf -> "${shelf.title}#$index" },
            contentType = { index, shelf ->
                when {
                    picksFirst && index == 0 -> "picks"
                    shelf.isArtistShelf() -> "artists"
                    else -> "lockups"
                }
            },
        ) { index, shelf ->
            val onItem: (ShelfItem) -> Unit = { item ->
                openShelfItem(item, onPlaySong, onNavigateToDetail)
            }
            val onItemMenu: (ShelfItem) -> (() -> Unit)? = { item ->
                item.toSong()?.let { song -> { onSongMenu(song) } }
            }
            when {
                picksFirst && index == 0 -> TvShelf(
                    title = shelf.title,
                    subtitle = shelf.subtitle.takeIf { it.isNotBlank() },
                    items = shelf.items,
                    key = { it.itemKey() },
                ) { item ->
                    TvCard(
                        title = item.title,
                        subtitle = item.subtitle,
                        artworkUrl = item.thumbnailUrl,
                        cardWidth = 163.dp,
                        onClick = { onItem(item) },
                        onLongClick = onItemMenu(item),
                    )
                }
                shelf.isArtistShelf() -> TvShelf(
                    title = shelf.title,
                    subtitle = shelf.subtitle.takeIf { it.isNotBlank() },
                    items = shelf.items,
                    key = { it.itemKey() },
                ) { item ->
                    TvLockup(
                        title = item.title,
                        subtitle = null,
                        artworkUrl = item.thumbnailUrl,
                        circle = true,
                        width = 120.dp,
                        onClick = { onItem(item) },
                    )
                }
                else -> TvShelf(
                    title = shelf.title,
                    subtitle = shelf.subtitle.takeIf { it.isNotBlank() },
                    items = shelf.items,
                    key = { it.itemKey() },
                ) { item ->
                    TvLockup(
                        title = item.title,
                        subtitle = item.subtitle,
                        artworkUrl = item.thumbnailUrl,
                        width = 131.dp,
                        onClick = { onItem(item) },
                        onLongClick = onItemMenu(item),
                    )
                }
            }
        }
        if (loadingMore) {
            item(key = "more", contentType = "more") {
                Box(modifier = Modifier.fillMaxWidth().padding(19.dp), contentAlignment = Alignment.Center) {
                    TvActivityIndicator()
                }
            }
        }
    }
    }
}

private fun ShelfItem.itemKey(): String = videoId ?: browseId ?: title

private fun HomeShelf.isArtistShelf(): Boolean =
    items.isNotEmpty() && items.count { it.browseId?.startsWith("UC") == true } * 2 > items.size

internal fun ShelfItem.toSong(): Song? = videoId?.let {
    Song(videoId = it, title = title, artist = subtitle, thumbnailUrl = thumbnailUrl)
}

internal fun browseTypeOf(browseId: String): BrowseType = when {
    browseId.startsWith("UC") || browseId.startsWith("FEmusic_artist") -> BrowseType.ARTIST
    browseId.startsWith("MPRE") || browseId.startsWith("FEmusic_album") -> BrowseType.ALBUM
    browseId.startsWith("VL") || browseId.startsWith("PL") || browseId.startsWith("RD") || browseId == "LM" -> BrowseType.PLAYLIST
    else -> BrowseType.OTHER
}

internal fun openShelfItem(
    item: ShelfItem,
    onPlaySong: (Song) -> Unit,
    onNavigateToDetail: (browseId: String, title: String, subtitle: String, thumbnailUrl: String?, type: BrowseType) -> Unit,
) {
    val song = item.toSong()
    val browseId = item.browseId
    when {
        song != null -> onPlaySong(song)
        browseId != null -> onNavigateToDetail(browseId, item.title, item.subtitle, item.thumbnailUrl, browseTypeOf(browseId))
    }
}
