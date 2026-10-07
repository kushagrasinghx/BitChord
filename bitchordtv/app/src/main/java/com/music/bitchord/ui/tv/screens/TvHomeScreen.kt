package com.music.bitchord.ui.tv.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.session.MediaController
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.playback.playSongs
import com.music.bitchord.ui.MainViewModel
import com.music.bitchord.ui.tv.components.TvCard
import com.music.bitchord.ui.tv.components.TvEmptyState
import com.music.bitchord.ui.tv.components.TvErrorState
import com.music.bitchord.ui.tv.components.TvSectionHeader
import com.music.bitchord.ui.tv.components.TvShelfSkeleton
import com.music.bitchord.ui.tv.theme.TvColors
import com.music.bitchord.ui.tv.theme.TvDimensions
import com.music.bitchord.ui.tv.theme.TvSFProDisplay

@Composable
fun TvHomeScreen(
    viewModel: MainViewModel,
    mediaController: MediaController?,
    onNavigateToDetail: (browseId: String, title: String, subtitle: String, thumbnailUrl: String?, type: BrowseType) -> Unit,
    onNavigateToNowPlaying: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val homeState by viewModel.home.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    when (val state = homeState) {
        is UiState.Loading -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = TvDimensions.SafeMarginHorizontal,
                        vertical = TvDimensions.SafeMarginVertical,
                    ),
            ) {
                TvShelfSkeleton(itemCount = 5)
                Spacer(modifier = Modifier.height(TvDimensions.ShelfSpacing))
                TvShelfSkeleton(itemCount = 5)
            }
        }
        is UiState.Error -> {
            TvErrorState(
                message = state.message,
                onRetry = { viewModel.refresh(MainViewModel.Feed.HOME) },
                modifier = modifier,
            )
        }
        is UiState.Success -> {
            val shelves = state.data
            if (shelves.isEmpty()) {
                TvEmptyState(
                    title = "Nothing to display",
                    message = "Connect to the internet or sign in to load your music feed.",
                    modifier = modifier,
                )
            } else {
                TvHomeFeed(
                    shelves = shelves,
                    onPlaySong = { song ->
                        coroutineScope.launch {
                            mediaController?.playSongs(listOf(song), 0)
                        }
                        onNavigateToNowPlaying()
                    },
                    onNavigateToDetail = onNavigateToDetail,
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
private fun TvHomeFeed(
    shelves: List<HomeShelf>,
    onPlaySong: (Song) -> Unit,
    onNavigateToDetail: (browseId: String, title: String, subtitle: String, thumbnailUrl: String?, type: BrowseType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val nickname by com.music.bitchord.data.settings.AppSettings.tvNickname.collectAsState()
    val verticalListState = rememberLazyListState()

    LazyColumn(
        state = verticalListState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = TvDimensions.SafeMarginHorizontal,
            end = TvDimensions.SafeMarginHorizontal,
            top = 12.dp,
            bottom = 120.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(TvDimensions.ShelfSpacing),
    ) {
        // Personalized Welcome Header
        item {
            Column {
                Text(
                    text = "Welcome back, $nickname",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = TvSFProDisplay,
                    color = TvColors.TextPrimary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Personalized mixes, trending charts, and new releases",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = TvSFProDisplay,
                    color = TvColors.TextSecondary,
                )
            }
        }

        itemsIndexed(
            items = shelves,
            key = { index, shelf -> "${shelf.title}_$index" },
        ) { _, shelf ->
            TvShelfRow(
                shelf = shelf,
                onPlaySong = onPlaySong,
                onNavigateToDetail = onNavigateToDetail,
            )
        }
    }
}

@Composable
private fun TvShelfRow(
    shelf: HomeShelf,
    onPlaySong: (Song) -> Unit,
    onNavigateToDetail: (browseId: String, title: String, subtitle: String, thumbnailUrl: String?, type: BrowseType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val horizontalRowState = rememberLazyListState()

    Column(modifier = modifier) {
        TvSectionHeader(title = shelf.title)

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            state = horizontalRowState,
            horizontalArrangement = Arrangement.spacedBy(TvDimensions.CardSpacing),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
        ) {
            itemsIndexed(
                items = shelf.items,
                key = { index, item -> "${item.videoId ?: item.browseId ?: item.title}_$index" },
            ) { _, item ->
                TvShelfItemCard(
                    item = item,
                    onPlaySong = onPlaySong,
                    onNavigateToDetail = onNavigateToDetail,
                )
            }
        }
    }
}

@Composable
private fun TvShelfItemCard(
    item: ShelfItem,
    onPlaySong: (Song) -> Unit,
    onNavigateToDetail: (browseId: String, title: String, subtitle: String, thumbnailUrl: String?, type: BrowseType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isTrack = item.videoId != null
    val isArtist = item.browseId?.startsWith("UC") == true

    TvCard(
        title = item.title,
        subtitle = item.subtitle,
        artworkUrl = item.thumbnailUrl,
        categoryLabel = when {
            isTrack -> "Track"
            isArtist -> "Artist"
            item.browseId?.startsWith("MPRE") == true -> "New Release"
            item.browseId?.startsWith("VL") == true -> "Made For You"
            else -> "Featured"
        },
        isCircle = isArtist,
        badge = when {
            isTrack -> "Song"
            isArtist -> "Artist"
            item.browseId?.startsWith("MPRE") == true -> "Album"
            item.browseId?.startsWith("VL") == true -> "Playlist"
            else -> null
        },
        onClick = {
            if (isTrack && item.videoId != null) {
                onPlaySong(
                    Song(
                        videoId = item.videoId,
                        title = item.title,
                        artist = item.subtitle,
                        thumbnailUrl = item.thumbnailUrl,
                    ),
                )
            } else if (item.browseId != null) {
                val type = when {
                    isArtist -> BrowseType.ARTIST
                    item.browseId.startsWith("MPRE") -> BrowseType.ALBUM
                    item.browseId.startsWith("VL") -> BrowseType.PLAYLIST
                    else -> BrowseType.OTHER
                }
                onNavigateToDetail(
                    item.browseId,
                    item.title,
                    item.subtitle,
                    item.thumbnailUrl,
                    type,
                )
            }
        },
        modifier = modifier,
    )
}
