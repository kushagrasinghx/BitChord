package com.music.bitchord.ui.tv.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.UiState
import com.music.bitchord.ui.MainViewModel
import com.music.bitchord.ui.tv.components.TvCard
import com.music.bitchord.ui.tv.components.TvEmptyState
import com.music.bitchord.ui.tv.components.TvErrorState
import com.music.bitchord.ui.tv.components.TvSectionHeader
import com.music.bitchord.ui.tv.components.TvShelfSkeleton
import com.music.bitchord.ui.tv.focus.tvCardFocus
import com.music.bitchord.ui.tv.theme.TvColors
import com.music.bitchord.ui.tv.theme.TvDimensions
import com.music.bitchord.ui.tv.theme.TvSFProDisplay

@Composable
fun TvLibraryScreen(
    viewModel: MainViewModel,
    onNavigateToDetail: (browseId: String, title: String, subtitle: String, thumbnailUrl: String?, type: BrowseType) -> Unit,
    onNavigateToLocalMusic: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToLiked: () -> Unit,
    onNavigateToSearch: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val signedIn by viewModel.signedIn.collectAsState()
    val libraryState by viewModel.library.collectAsState()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = TvDimensions.SafeMarginHorizontal,
            end = TvDimensions.SafeMarginHorizontal,
            top = 12.dp,
            bottom = 120.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(TvDimensions.ShelfSpacing),
    ) {
        // Quick Hub Cards: Liked Songs, Downloads, Local Music, History
        item {
            Column {
                TvSectionHeader(title = "Your Library")
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(TvDimensions.CardSpacing),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                ) {
                    item {
                        TvQuickHubCard(
                            title = "Liked Songs",
                            subtitle = "Auto-saved favorites",
                            icon = Icons.Default.Favorite,
                            accentColor = TvColors.AccentRed,
                            onClick = {
                                onNavigateToDetail(
                                    "LM",
                                    "Liked Music",
                                    "Your favorite songs",
                                    null,
                                    BrowseType.PLAYLIST,
                                )
                            },
                        )
                    }
                    item {
                        TvQuickHubCard(
                            title = "Downloads",
                            subtitle = "Offline tracks",
                            icon = Icons.Default.Download,
                            accentColor = TvColors.AccentBlue,
                            onClick = onNavigateToDownloads,
                        )
                    }
                    item {
                        TvQuickHubCard(
                            title = "Local Music",
                            subtitle = "Device & USB Drives",
                            icon = Icons.Default.Folder,
                            accentColor = Color.White,
                            onClick = onNavigateToLocalMusic,
                        )
                    }
                    item {
                        TvQuickHubCard(
                            title = "History",
                            subtitle = "Recently listened",
                            icon = Icons.Default.History,
                            accentColor = Color.White,
                            onClick = onNavigateToHistory,
                        )
                    }
                    item {
                        TvQuickHubCard(
                            title = "Search",
                            subtitle = "Find songs & artists",
                            icon = Icons.Default.Search,
                            accentColor = Color.White,
                            onClick = onNavigateToSearch,
                        )
                    }
                    item {
                        TvQuickHubCard(
                            title = "Settings",
                            subtitle = "Audio, theme & display",
                            icon = Icons.Default.Settings,
                            accentColor = Color.White,
                            onClick = onNavigateToSettings,
                        )
                    }
                }
            }
        }

        // Online Library Shelves (Playlists, Saved Albums, Subscriptions)
        when (val state = libraryState) {
            is UiState.Loading -> {
                item {
                    TvShelfSkeleton(itemCount = 5)
                }
            }
            is UiState.Error -> {
                item {
                    TvErrorState(
                        message = state.message,
                        onRetry = { viewModel.loadLibrary() },
                    )
                }
            }
            is UiState.Success -> {
                val lib = state.data
                if (lib.shelves.isNotEmpty()) {
                    items(lib.shelves) { shelf ->
                        TvLibraryShelfRow(
                            shelf = shelf,
                            onNavigateToDetail = onNavigateToDetail,
                        )
                    }
                } else if (!signedIn) {
                    item {
                        TvEmptyState(
                            title = "Sign in for personalized library",
                            message = "Sign in to your YouTube Music account to access your playlists, subscriptions, and recommendations.",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvQuickHubCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(180.dp)
            .tvCardFocus(
                shape = RoundedCornerShape(16.dp),
                focusedScale = 1.06f,
                focusedBorderColor = TvColors.BorderFocused,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.25f)
                .clip(RoundedCornerShape(16.dp))
                .background(TvColors.SurfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accentColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(30.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = TvSFProDisplay,
            color = TvColors.TextPrimary,
        )
        Text(
            text = subtitle,
            fontSize = 12.sp,
            fontFamily = TvSFProDisplay,
            color = TvColors.TextSecondary,
        )
    }
}

@Composable
private fun TvLibraryShelfRow(
    shelf: HomeShelf,
    onNavigateToDetail: (browseId: String, title: String, subtitle: String, thumbnailUrl: String?, type: BrowseType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        TvSectionHeader(
            title = shelf.title,
            subtitle = shelf.subtitle.takeIf { it.isNotBlank() },
        )

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(TvDimensions.CardSpacing),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
        ) {
            items(
                items = shelf.items,
                key = { item -> item.browseId ?: item.videoId ?: item.title },
            ) { item ->
                TvCard(
                    title = item.title,
                    subtitle = item.subtitle,
                    artworkUrl = item.thumbnailUrl,
                    cardWidth = 175.dp,
                    onClick = {
                        val browseId = item.browseId ?: return@TvCard
                        val type = when {
                            browseId.startsWith("VL") || browseId.startsWith("PL") -> BrowseType.PLAYLIST
                            browseId.startsWith("MPRE") || browseId.startsWith("FEmusic_album") -> BrowseType.ALBUM
                            browseId.startsWith("UC") || browseId.startsWith("FEmusic_artist") -> BrowseType.ARTIST
                            else -> BrowseType.OTHER
                        }
                        onNavigateToDetail(browseId, item.title, item.subtitle, item.thumbnailUrl, type)
                    },
                )
            }
        }
    }
}
