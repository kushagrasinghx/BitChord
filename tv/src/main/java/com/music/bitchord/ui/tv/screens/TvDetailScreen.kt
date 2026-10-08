package com.music.bitchord.ui.tv.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.session.MediaController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.DetailPage
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.playback.playSongs
import com.music.bitchord.ui.MainViewModel
import com.music.bitchord.ui.tv.components.TvButton
import com.music.bitchord.ui.tv.components.TvEmptyState
import com.music.bitchord.ui.tv.components.TvErrorState
import com.music.bitchord.ui.tv.focus.tvButtonFocus
import com.music.bitchord.ui.tv.theme.TvColors
import com.music.bitchord.ui.tv.theme.TvDimensions
import com.music.bitchord.ui.tv.theme.TvSFProDisplay

@Composable
fun TvDetailScreen(
    browseId: String,
    initialTitle: String,
    initialSubtitle: String,
    initialThumbnailUrl: String?,
    type: BrowseType,
    viewModel: MainViewModel,
    mediaController: MediaController?,
    onNavigateToNowPlaying: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val detailStack by viewModel.detailStack.collectAsState()
    val page = detailStack.lastOrNull { it.browseId == browseId }

    // Storage permission launcher for scanning device audio & USB pendrives
    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.openDetail(browseId, initialTitle, initialSubtitle, initialThumbnailUrl, type)
    }

    LaunchedEffect(browseId) {
        if (page == null) {
            viewModel.openDetail(browseId, initialTitle, initialSubtitle, initialThumbnailUrl, type)
        }
    }

    val coroutineScope = rememberCoroutineScope()

    if (page == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            com.music.bitchord.ui.tv.components.TvBottomLoadingBar(visible = true)
        }
    } else {
        TvDetailContent(
            page = page,
            onRequestStoragePermission = { permissionLauncher.launch(permissionsToRequest) },
            onRetry = { viewModel.openDetail(browseId, initialTitle, initialSubtitle, initialThumbnailUrl, type) },
            onPlaySongAt = { songs, index ->
                coroutineScope.launch {
                    mediaController?.playSongs(songs, index)
                }
                onNavigateToNowPlaying()
            },
            onShufflePlay = { songs ->
                if (songs.isNotEmpty()) {
                    coroutineScope.launch {
                        mediaController?.playSongs(songs.shuffled(), 0)
                    }
                    onNavigateToNowPlaying()
                }
            },
            modifier = modifier,
        )
    }
}

@Composable
private fun TvDetailContent(
    page: DetailPage,
    onRequestStoragePermission: () -> Unit,
    onRetry: () -> Unit,
    onPlaySongAt: (List<Song>, Int) -> Unit,
    onShufflePlay: (List<Song>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val songs = when (val s = page.songs) {
        is UiState.Success -> s.data
        else -> emptyList()
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = TvDimensions.SafeMarginHorizontal,
                end = TvDimensions.SafeMarginHorizontal,
                top = 16.dp,
                bottom = 40.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(36.dp),
    ) {
        // Left Pane: Metadata & Action Buttons
        Column(
            modifier = Modifier
                .weight(0.38f)
                .fillMaxHeight(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(if (page.type == BrowseType.ARTIST) CircleShape else RoundedCornerShape(18.dp))
                    .background(TvColors.SurfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (!page.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(page.thumbnailUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = page.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = page.title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = TvSFProDisplay,
                color = TvColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (!page.subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = page.subtitle,
                    fontSize = 14.sp,
                    fontFamily = TvSFProDisplay,
                    color = TvColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(
                    text = "Play All",
                    icon = Icons.Default.PlayArrow,
                    isPrimary = true,
                    enabled = songs.isNotEmpty(),
                    onClick = { onPlaySongAt(songs, 0) },
                )

                TvButton(
                    text = "Shuffle",
                    icon = Icons.Default.Shuffle,
                    isPrimary = false,
                    enabled = songs.isNotEmpty(),
                    onClick = { onShufflePlay(songs) },
                )
            }
        }

        // Right Pane: Track List / Permission State
        Column(
            modifier = Modifier
                .weight(0.62f)
                .fillMaxHeight(),
        ) {
            when (val s = page.songs) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        com.music.bitchord.ui.tv.components.TvBottomLoadingBar(visible = true)
                    }
                }
                is UiState.Error -> {
                    if (s.message.contains("permission", ignoreCase = true)) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Storage & USB Permission Required",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = TvSFProDisplay,
                                color = Color.White,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Allow storage permission to scan and play music from internal storage and attached USB pendrives.",
                                fontSize = 14.sp,
                                fontFamily = TvSFProDisplay,
                                color = TvColors.TextSecondary,
                                modifier = Modifier.fillMaxWidth(0.7f),
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            TvButton(
                                text = "Grant Storage Permission",
                                isPrimary = true,
                                onClick = onRequestStoragePermission,
                            )
                        }
                    } else {
                        TvErrorState(
                            message = s.message,
                            onRetry = onRetry,
                        )
                    }
                }
                is UiState.Success -> {
                    if (s.data.isEmpty()) {
                        TvEmptyState(
                            title = "No tracks available",
                            message = "This collection does not contain any playable tracks. Connect a USB drive or download music to listen offline.",
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 80.dp),
                        ) {
                            itemsIndexed(
                                items = s.data,
                                key = { index, song -> "${song.videoId}_$index" },
                            ) { index, song ->
                                TvTrackRow(
                                    index = index + 1,
                                    song = song,
                                    onClick = { onPlaySongAt(s.data, index) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvTrackRow(
    index: Int,
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val animatedBg by animateColorAsState(
        targetValue = if (isFocused) TvColors.SurfaceFocused else TvColors.SurfaceVariant,
        label = "trackRowBg",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .tvButtonFocus(
                shape = RoundedCornerShape(12.dp),
                focusedScale = 1.02f,
                focusedBorderColor = TvColors.BorderFocused,
                onClick = onClick,
            )
            .background(animatedBg)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = index.toString(),
            color = if (isFocused) TvColors.AccentRed else TvColors.TextMuted,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = TvSFProDisplay,
            modifier = Modifier.width(24.dp),
        )

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(TvColors.Surface),
            contentAlignment = Alignment.Center,
        ) {
            if (!song.thumbnailUrl.isNullOrBlank()) {
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

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = if (isFocused) TvColors.AccentRed else TvColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.W600,
                fontFamily = TvSFProDisplay,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = song.artist,
                color = TvColors.TextSecondary,
                fontSize = 12.sp,
                fontFamily = TvSFProDisplay,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (!song.durationText.isNullOrBlank()) {
            Text(
                text = song.durationText.orEmpty(),
                color = TvColors.TextMuted,
                fontSize = 13.sp,
                fontFamily = TvSFProDisplay,
            )
        }
    }
}
