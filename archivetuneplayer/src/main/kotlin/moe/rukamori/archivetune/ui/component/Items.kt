/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.component

import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.Download.STATE_COMPLETED
import androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING
import androidx.media3.exoplayer.offline.Download.STATE_QUEUED
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalDownloadUtil
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.CropThumbnailToSquareKey
import moe.rukamori.archivetune.constants.GridThumbnailCornerRadius
import moe.rukamori.archivetune.constants.GridThumbnailHeight
import moe.rukamori.archivetune.constants.ListItemHeight
import moe.rukamori.archivetune.constants.ListThumbnailSize
import moe.rukamori.archivetune.constants.SwipeToSongKey
import moe.rukamori.archivetune.constants.ThumbnailCornerRadius
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.EpisodeItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.PodcastItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.queues.LocalAlbumRadio
import moe.rukamori.archivetune.ui.utils.YtimgResizePolicy
import moe.rukamori.archivetune.ui.utils.getNextFallbackUrl
import moe.rukamori.archivetune.ui.utils.preferredThumbnailRatio
import moe.rukamori.archivetune.ui.utils.resize
import moe.rukamori.archivetune.ui.utils.thumbnailSourceRatio
import moe.rukamori.archivetune.utils.joinByBullet
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.utils.reportException
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

const val ActiveBoxAlpha = 0.6f

const val ActiveBackdropContainerAlpha = 0.55f
const val ActiveBackdropBorderAlpha = 0.28f

@Composable
inline fun ListItem(
    modifier: Modifier = Modifier,
    title: String,
    noinline subtitle: (@Composable RowScope.() -> Unit)? = null,
    thumbnailContent: @Composable () -> Unit,
    crossinline trailingContent: @Composable RowScope.() -> Unit = {},
    isActive: Boolean = false,
    showActiveContainer: Boolean = true,
    textColorOverride: Color? = null,
    activeContainerBackdrop: Boolean = false,
) {
    val titleColor =
        textColorOverride ?: when {
            isActive && activeContainerBackdrop -> MaterialTheme.colorScheme.onSurface
            isActive -> MaterialTheme.colorScheme.onSecondaryContainer
            else -> MaterialTheme.colorScheme.onSurface
        }
    val subtitleContentColor =
        textColorOverride?.copy(alpha = 0.7f) ?: when {
            isActive && activeContainerBackdrop -> MaterialTheme.colorScheme.onSurfaceVariant
            isActive -> MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    val trailingContentColor =
        textColorOverride ?: when {
            isActive && activeContainerBackdrop -> MaterialTheme.colorScheme.onSurfaceVariant
            isActive -> MaterialTheme.colorScheme.onSecondaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .focusable()
                .height(ListItemHeight)
                .padding(horizontal = 8.dp)
                .then(
                    if (isActive && showActiveContainer) {
                        if (activeContainerBackdrop) {
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    MaterialTheme.colorScheme.secondaryContainer.copy(
                                        alpha = ActiveBackdropContainerAlpha,
                                    ),
                                ).border(
                                    width = Dp.Hairline,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                        alpha = ActiveBackdropBorderAlpha,
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                )
                        } else {
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                        }
                    } else {
                        Modifier
                    },
                ),
    ) {
        Box(Modifier.padding(8.dp), contentAlignment = Alignment.Center) { thumbnailContent() }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = titleColor,
            )
            if (subtitle != null) {
                CompositionLocalProvider(LocalContentColor provides subtitleContentColor) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) { subtitle() }
                }
            }
        }
        CompositionLocalProvider(LocalContentColor provides trailingContentColor) {
            trailingContent()
        }
    }
}

@Composable
fun ListItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String?,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailContent: @Composable () -> Unit,
    trailingContent: @Composable RowScope.() -> Unit = {},
    isActive: Boolean = false,
    showActiveContainer: Boolean = true,
    activeContainerBackdrop: Boolean = false,
) = ListItem(
    title = title,
    modifier = modifier,
    isActive = isActive,
    showActiveContainer = showActiveContainer,
    activeContainerBackdrop = activeContainerBackdrop,
    subtitle = {
        badges()
        if (!subtitle.isNullOrEmpty()) {
            Text(
                text = subtitle,
                color =
                    when {
                        isActive && activeContainerBackdrop -> MaterialTheme.colorScheme.onSurfaceVariant
                        isActive -> MaterialTheme.colorScheme.onSecondaryContainer.copy(
                            alpha = 0.7f,
                        )
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    },
    thumbnailContent = thumbnailContent,
    trailingContent = trailingContent,
)

@Composable
fun GridItem(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    subtitle: @Composable () -> Unit,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailContent: @Composable BoxWithConstraintsScope.() -> Unit,
    thumbnailRatio: Float = 1f,
    fillMaxWidth: Boolean = false,
) {
    Column(
        modifier =
            if (fillMaxWidth) {
                modifier
                    .focusable()
                    .padding(12.dp)
                    .fillMaxWidth()
            } else {
                modifier
                    .focusable()
                    .padding(12.dp)
                    .width(GridThumbnailHeight * thumbnailRatio)
            },
    ) {
        BoxWithConstraints(
            contentAlignment = Alignment.Center,
            modifier =
                if (fillMaxWidth) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier.height(GridThumbnailHeight)
                }.aspectRatio(thumbnailRatio),
        ) {
            thumbnailContent()
        }

        Spacer(modifier = Modifier.height(6.dp))

        title()

        Row(verticalAlignment = Alignment.CenterVertically) {
            badges()

            subtitle()
        }
    }
}

@Composable
fun GridItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailContent: @Composable BoxWithConstraintsScope.() -> Unit,
    thumbnailRatio: Float = 1f,
    fillMaxWidth: Boolean = false,
) = GridItem(
    modifier = modifier,
    title = {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
    },
    subtitle = {
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    },
    thumbnailContent = thumbnailContent,
    thumbnailRatio = thumbnailRatio,
    fillMaxWidth = fillMaxWidth,
)

@Composable
fun SongListItem(
    song: Song,
    modifier: Modifier = Modifier,
    albumIndex: Int? = null,
    viewCountText: String? = null,
    showLikedIcon: Boolean = true,
    showInLibraryIcon: Boolean = false,
    showDownloadIcon: Boolean = true,
    showSongIconPlaceholder: Boolean = false,
    badges: @Composable RowScope.() -> Unit = {
        if (showLikedIcon && song.song.liked) {
            Icon.Favorite()
        }
        if (song.song.explicit) {
            Icon.Explicit()
        }
        if (showInLibraryIcon && song.song.inLibrary != null) {
            Icon.Library()
        }
        if (showDownloadIcon) {
            val download by LocalDownloadUtil.current
                .getDownload(song.id)
                .collectAsStateWithLifecycle(initialValue = null)
            Icon.Download(download?.state, percent = download?.percentDownloaded ?: -1f)
        }
    },
    isSelected: Boolean = false,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    isSwipeable: Boolean = true,
    swipeContentBackgroundColor: Color? = null,
    showActiveContainer: Boolean = true,
    activeContainerBackdrop: Boolean = false,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    val swipeEnabled by rememberPreference(SwipeToSongKey, defaultValue = true)
    val resolvedSwipeContentBackgroundColor = swipeContentBackgroundColor ?: MaterialTheme.colorScheme.surface

    val content: @Composable () -> Unit = {
        ListItem(
            title = song.song.title,

            subtitle = song.artists.joinToString { it.name }.takeIf { it.isNotBlank() },
            badges = badges,
            thumbnailContent = {
                ItemThumbnail(
                    thumbnailUrl = song.song.thumbnailUrl,
                    albumIndex = albumIndex,
                    isSelected = isSelected,
                    isActive = isActive,
                    isPlaying = isPlaying,
                    shape = RoundedCornerShape(ThumbnailCornerRadius),
                    placeholderIconRes = if (showSongIconPlaceholder) R.drawable.music_note else null,
                    maxSizePx = 200,
                    modifier = Modifier.size(ListThumbnailSize),
                )
            },
            trailingContent = trailingContent,
            modifier = modifier,
            isActive = isActive,
            showActiveContainer = showActiveContainer,
            activeContainerBackdrop = activeContainerBackdrop,
        )
    }

    if (isSwipeable && swipeEnabled) {
        SwipeToSongBox(
            mediaItem = song.toMediaItem(),
            modifier = Modifier.fillMaxWidth(),
            contentBackgroundColor = resolvedSwipeContentBackgroundColor,
        ) {
            content()
        }
    } else {
        content()
    }
}

@Composable
fun ArtistListItem(
    artist: Artist,
    modifier: Modifier = Modifier,
    badges: @Composable RowScope.() -> Unit = {
        if (artist.artist.bookmarkedAt != null) {
            Icon(
                painter = painterResource(R.drawable.favorite),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier =
                    Modifier
                        .size(18.dp)
                        .padding(end = 2.dp),
            )
        }
    },
    trailingContent: @Composable RowScope.() -> Unit = {},
) = ListItem(
    title = artist.artist.name,
    subtitle = pluralStringResource(R.plurals.n_song, artist.songCount, artist.songCount),
    badges = badges,
    thumbnailContent = {
        ItemThumbnail(
            thumbnailUrl = artist.artist.thumbnailUrl,
            isActive = false,
            isPlaying = false,
            shape = RoundedCornerShape(ThumbnailCornerRadius),
            contentScale = ContentScale.Crop,
            maxSizePx = 200,
            modifier = Modifier.size(ListThumbnailSize),
        )
    },
    trailingContent = trailingContent,
    modifier = modifier,
)

@Composable
fun ArtistGridItem(
    artist: Artist,
    modifier: Modifier = Modifier,
    badges: @Composable RowScope.() -> Unit = {
        if (artist.artist.bookmarkedAt != null) {
            Icon.Favorite()
        }
    },
    fillMaxWidth: Boolean = false,
) = GridItem(
    title = artist.artist.name,
    subtitle = pluralStringResource(R.plurals.n_song, artist.songCount, artist.songCount),
    badges = badges,
    thumbnailContent = {
        ItemThumbnail(
            thumbnailUrl = artist.artist.thumbnailUrl,
            isActive = false,
            isPlaying = false,
            shape = RoundedCornerShape(GridThumbnailCornerRadius),
            contentScale = ContentScale.Crop,
            maxSizePx = 544,
            modifier = Modifier.fillMaxSize(),
        )
    },
    fillMaxWidth = fillMaxWidth,
    modifier = modifier,
)

@Composable
fun AlbumListItem(
    album: Album,
    modifier: Modifier = Modifier,
    showLikedIcon: Boolean = true,
    badges: @Composable RowScope.() -> Unit = {
        val database = LocalDatabase.current
        val downloadUtil = LocalDownloadUtil.current
        var songs by remember {
            mutableStateOf(emptyList<Song>())
        }

        LaunchedEffect(Unit) {
            database.albumSongs(album.id).collect {
                songs = it
            }
        }

        var downloadState by remember {
            mutableStateOf(Download.STATE_STOPPED)
        }

        LaunchedEffect(songs) {
            if (songs.isEmpty()) return@LaunchedEffect
            downloadUtil.downloads.collect { downloads ->
                downloadState =
                    when {
                        songs.all { downloads[it.id]?.state == STATE_COMPLETED } -> STATE_COMPLETED

                        songs.all {
                            downloads[it.id]?.state in
                                listOf(
                                    STATE_QUEUED,
                                    STATE_DOWNLOADING,
                                    STATE_COMPLETED,
                                )
                        } -> STATE_DOWNLOADING

                        else -> Download.STATE_STOPPED
                    }
            }
        }

        if (showLikedIcon && album.album.bookmarkedAt != null) {
            Icon.Favorite()
        }
        if (album.album.explicit) {
            Icon.Explicit()
        }
        Icon.Download(downloadState)
    },
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    trailingContent: @Composable RowScope.() -> Unit = {},
) = ListItem(
    title = album.album.title,
    subtitle =
        joinByBullet(
            album.artists.joinToString { it.name },
            pluralStringResource(R.plurals.n_song, album.album.songCount, album.album.songCount),
            album.album.year?.toString(),
        ),
    badges = badges,
    thumbnailContent = {
        ItemThumbnail(
            thumbnailUrl = album.album.thumbnailUrl,
            isActive = isActive,
            isPlaying = isPlaying,
            shape = RoundedCornerShape(ThumbnailCornerRadius),
            maxSizePx = 200,
            modifier = Modifier.size(ListThumbnailSize),
        )
    },
    trailingContent = trailingContent,
    modifier = modifier,
)

@Composable
fun AlbumGridItem(
    album: Album,
    modifier: Modifier = Modifier,
    coroutineScope: CoroutineScope,
    badges: @Composable RowScope.() -> Unit = {
        val database = LocalDatabase.current
        val downloadUtil = LocalDownloadUtil.current
        var songs by remember { mutableStateOf(emptyList<Song>()) }

        LaunchedEffect(Unit) {
            database.albumSongs(album.id).collect { songs = it }
        }

        var downloadState by remember { mutableStateOf(Download.STATE_STOPPED) }

        LaunchedEffect(songs) {
            if (songs.isEmpty()) return@LaunchedEffect
            downloadUtil.downloads.collect { downloads ->
                downloadState =
                    when {
                        songs.all { downloads[it.id]?.state == STATE_COMPLETED } -> STATE_COMPLETED

                        songs.all {
                            downloads[it.id]?.state in
                                listOf(
                                    STATE_QUEUED,
                                    STATE_DOWNLOADING,
                                    STATE_COMPLETED,
                                )
                        } -> STATE_DOWNLOADING

                        else -> Download.STATE_STOPPED
                    }
            }
        }

        if (album.album.bookmarkedAt != null) {
            Icon.Favorite()
        }
        if (album.album.explicit) {
            Icon.Explicit()
        }
        Icon.Download(downloadState)
    },
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
) = GridItem(
    title = {
        Text(
            text = album.album.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.basicMarquee().fillMaxWidth(),
        )
    },
    subtitle = {
        Text(
            text = album.artists.joinToString { it.name },
            style = MaterialTheme.typography.bodyMedium,

            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    },
    badges = badges,
    thumbnailContent = {
        val database = LocalDatabase.current
        val playerConnection = LocalPlayerConnection.current ?: return@GridItem

        ItemThumbnail(
            thumbnailUrl = album.album.thumbnailUrl,
            isActive = isActive,
            isPlaying = isPlaying,
            shape = RoundedCornerShape(GridThumbnailCornerRadius),
            maxSizePx = 544,
        )

        AlbumPlayButton(
            visible = !isActive,
            onClick = {
                coroutineScope.launch {
                    database.albumWithSongs(album.id).firstOrNull()?.let { albumWithSongs ->
                        playerConnection.playQueue(LocalAlbumRadio(albumWithSongs))
                    }
                }
            },
        )
    },
    fillMaxWidth = fillMaxWidth,
    modifier = modifier,
)

@Composable
fun PlaylistListItem(
    playlist: Playlist,
    modifier: Modifier = Modifier,
    autoPlaylist: Boolean = false,
    badges: @Composable RowScope.() -> Unit = {},
    trailingContent: @Composable RowScope.() -> Unit = {},
) = ListItem(
    title = playlist.playlist.name,
    subtitle =
        if (autoPlaylist) {
            ""
        } else {
            if (playlist.songCount == 0 && playlist.playlist.remoteSongCount != null) {
                pluralStringResource(
                    R.plurals.n_song,
                    playlist.playlist.remoteSongCount,
                    playlist.playlist.remoteSongCount,
                )
            } else {
                pluralStringResource(
                    R.plurals.n_song,
                    playlist.songCount,
                    playlist.songCount,
                )
            }
        },
    badges = badges,
    thumbnailContent = {
        PlaylistThumbnail(
            thumbnails = playlist.thumbnails,
            size = ListThumbnailSize,
            placeHolder = {
                val painter =
                    when (playlist.playlist.name) {
                        stringResource(R.string.liked) -> R.drawable.favorite_border
                        stringResource(R.string.offline) -> R.drawable.offline
                        stringResource(R.string.cached_playlist) -> R.drawable.cached
                        else -> if (autoPlaylist) R.drawable.trending_up else R.drawable.queue_music
                    }
                Icon(
                    painter = painterResource(painter),
                    contentDescription = null,
                    tint = LocalContentColor.current.copy(alpha = 0.8f),
                    modifier = Modifier.size(ListThumbnailSize / 2),
                )
            },
            shape = RoundedCornerShape(ThumbnailCornerRadius),
        )
    },
    trailingContent = trailingContent,
    modifier = modifier,
)

@Composable
fun MediaMetadataListItem(
    mediaMetadata: MediaMetadata,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    shouldLoadImage: Boolean = true,

    showActiveContainer: Boolean = true,
    activeContainerBackdrop: Boolean = false,
    trailingContent: @Composable RowScope.() -> Unit = {},
    textColorOverride: Color? = null,
) {
    ListItem(
        title = mediaMetadata.title,
        subtitle = {
            Text(
                text = mediaMetadata.artists.joinToString { it.name },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        thumbnailContent = {
            ItemThumbnail(
                thumbnailUrl = mediaMetadata.thumbnailUrl,
                albumIndex = null,
                isSelected = isSelected,
                isActive = isActive,
                isPlaying = isPlaying,
                shouldLoadImage = shouldLoadImage,
                shape = RoundedCornerShape(ThumbnailCornerRadius),
                modifier = Modifier.size(ListThumbnailSize),
            )
        },
        trailingContent = trailingContent,
        modifier = modifier,
        isActive = isActive,
        showActiveContainer = showActiveContainer,
        activeContainerBackdrop = activeContainerBackdrop,
        textColorOverride = textColorOverride,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeListItem(
    item: YTItem,
    modifier: Modifier = Modifier,
    albumIndex: Int? = null,
    viewCountText: String? = null,
    isSelected: Boolean = false,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    isSwipeable: Boolean = true,
    swipeContentBackgroundColor: Color? = null,
    showActiveContainer: Boolean = true,
    activeContainerBackdrop: Boolean = false,
    trailingContent: @Composable RowScope.() -> Unit = {},
    badges: @Composable RowScope.() -> Unit = {
        val database = LocalDatabase.current
        val song by database.song(item.id).collectAsStateWithLifecycle(initialValue = null)
        val album by database.album(item.id).collectAsStateWithLifecycle(initialValue = null)

        if ((item is SongItem && song?.song?.liked == true) ||
            (item is AlbumItem && album?.album?.bookmarkedAt != null)
        ) {
            Icon.Favorite()
        }
        if (item.explicit) Icon.Explicit()
        if (item is SongItem && song?.song?.inLibrary != null) {
            Icon.Library()
        }
        if (item is SongItem) {
            val downloads by LocalDownloadUtil.current.downloads.collectAsStateWithLifecycle()
            val download = downloads[item.id]
            Icon.Download(download?.state, percent = download?.percentDownloaded ?: -1f)
        }
    },
) {
    val swipeEnabled by rememberPreference(SwipeToSongKey, defaultValue = true)

    val content: @Composable () -> Unit = {
        ListItem(
            title = item.title,
            subtitle =
                when (item) {
                    is SongItem -> item.artists.joinToString { it.name }.takeIf { it.isNotBlank() }

                    is AlbumItem -> {
                        joinByBullet(item.artists?.joinToString { it.name }, item.year?.toString())
                    }

                    is ArtistItem -> {
                        null
                    }

                    is PlaylistItem -> {
                        joinByBullet(item.author?.name, item.songCountText)
                    }

                    is PodcastItem -> item.author?.name

                    is EpisodeItem -> joinByBullet(item.podcast?.name, item.dateText, item.durationText)
                },
            badges = badges,
            thumbnailContent = {
                val rowRatio =
                    item.thumbnailSourceRatio
                        ?.takeIf { it >= 4f / 3f }
                        ?.let { 16f / 9f }
                        ?: 1f
                ItemThumbnail(
                    thumbnailUrl = item.thumbnail,
                    albumIndex = albumIndex,
                    isSelected = isSelected,
                    isActive = isActive,
                    isPlaying = isPlaying,
                    shape = RoundedCornerShape(ThumbnailCornerRadius),
                    thumbnailRatio = rowRatio,
                    modifier =
                        if (rowRatio > 1f) {
                            Modifier.size(
                                width = ListThumbnailSize * rowRatio,
                                height = ListThumbnailSize,
                            )
                        } else {
                            Modifier.size(ListThumbnailSize)
                        },
                )
            },
            trailingContent = trailingContent,
            modifier = modifier,
            isActive = isActive,
            showActiveContainer = showActiveContainer,
            activeContainerBackdrop = activeContainerBackdrop,
        )
    }

    if (item is SongItem && isSwipeable && swipeEnabled) {
        SwipeToSongBox(
            mediaItem =
                item
                    .copy(
                        thumbnail =
                            item.thumbnail.resize(
                                width = 1080,
                                height = 1080,
                                ytimgResizePolicy = YtimgResizePolicy.PreserveOriginal,
                            ),
                        thumbnailWidth = null,
                        thumbnailHeight = null,
                    ).toMediaItem(),
            modifier = Modifier.fillMaxWidth(),
            contentBackgroundColor = swipeContentBackgroundColor,
        ) {
            content()
        }
    } else {
        content()
    }
}

@Composable
fun YouTubeGridItem(
    item: YTItem,
    modifier: Modifier = Modifier,
    coroutineScope: CoroutineScope? = null,
    badges: @Composable RowScope.() -> Unit = {
        val database = LocalDatabase.current
        val song by database.song(item.id).collectAsStateWithLifecycle(initialValue = null)
        val album by database.album(item.id).collectAsStateWithLifecycle(initialValue = null)

        if (item is SongItem && song?.song?.liked == true ||
            item is AlbumItem && album?.album?.bookmarkedAt != null
        ) {
            Icon.Favorite()
        }
        if (item.explicit) Icon.Explicit()
        if (item is SongItem && song?.song?.inLibrary != null) Icon.Library()
        if (item is SongItem) {
            val downloads by LocalDownloadUtil.current.downloads.collectAsStateWithLifecycle()
            val download = downloads[item.id]
            Icon.Download(download?.state, percent = download?.percentDownloaded ?: -1f)
        }
    },
    thumbnailRatio: Float? = null,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
    showPlayOverlay: Boolean = true,
) {
    val (cropThumbnailToSquare, _) = rememberPreference(CropThumbnailToSquareKey, false)
    val resolvedThumbnailRatio = thumbnailRatio ?: item.preferredThumbnailRatio(cropThumbnailToSquare)

    GridItem(
        title = {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (item is ArtistItem) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.basicMarquee().fillMaxWidth(),
            )
        },
        subtitle = {
            val subtitle =
                when (item) {
                    is SongItem -> item.artists.joinToString { it.name }.takeIf { it.isNotBlank() }
                    is AlbumItem -> joinByBullet(item.artists?.joinToString { it.name }, item.year?.toString())
                    is ArtistItem -> null
                    is PlaylistItem -> joinByBullet(item.author?.name, item.songCountText)

                    is PodcastItem -> item.author?.name

                    is EpisodeItem -> joinByBullet(item.podcast?.name, item.dateText, item.durationText)
                }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,

                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        badges = badges,
        thumbnailContent = {
            val database = LocalDatabase.current
            val playerConnection = LocalPlayerConnection.current ?: return@GridItem
            val shape = RoundedCornerShape(GridThumbnailCornerRadius)

            ItemThumbnail(
                thumbnailUrl = item.thumbnail,
                isActive = isActive,
                isPlaying = isPlaying,
                shape = shape,
                thumbnailRatio = resolvedThumbnailRatio,
                sourceAspectRatio = item.thumbnailSourceRatio,
            )

            if (item is SongItem && !isActive && showPlayOverlay) {
                OverlayPlayButton(
                    visible = true,
                )
            }

            if (showPlayOverlay) {
                AlbumPlayButton(
                    visible = item is AlbumItem && !isActive,
                    onClick = {
                        coroutineScope?.launch(Dispatchers.IO) {
                            var albumWithSongs = database.albumWithSongs(item.id).first()
                            if (albumWithSongs?.songs.isNullOrEmpty()) {
                                YouTube
                                    .album(item.id)
                                    .onSuccess { albumPage ->
                                        database.transaction { insert(albumPage) }
                                        albumWithSongs = database.albumWithSongs(item.id).first()
                                    }.onFailure { reportException(it) }
                            }
                            albumWithSongs?.let {
                                withContext(Dispatchers.Main) {
                                    playerConnection.playQueue(LocalAlbumRadio(it))
                                }
                            }
                        }
                    },
                )
            }
        },
        thumbnailRatio = resolvedThumbnailRatio,
        fillMaxWidth = fillMaxWidth,
        modifier = modifier,
    )
}

@Composable
fun LocalArtistsGrid(
    title: String,
    subtitle: String,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailUrl: String?,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
    modifier: Modifier = Modifier,
) = GridItem(
    title = title,
    subtitle = subtitle,
    badges = badges,
    thumbnailContent = {
        LocalThumbnail(
            thumbnailUrl = thumbnailUrl,
            isActive = false,
            isPlaying = false,
            shape = RoundedCornerShape(GridThumbnailCornerRadius),
            modifier = if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier,
            showCenterPlay = false,
            playButtonVisible = false,
        )
    },
    fillMaxWidth = fillMaxWidth,
    modifier = modifier,
)

@Composable
fun LocalAlbumsGrid(
    title: String,
    subtitle: String,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailUrl: String?,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
    modifier: Modifier = Modifier,
) = GridItem(
    title = title,
    subtitle = subtitle,
    badges = badges,
    thumbnailContent = {
        LocalThumbnail(
            thumbnailUrl = thumbnailUrl,
            isActive = isActive,
            isPlaying = isPlaying,
            shape = RoundedCornerShape(GridThumbnailCornerRadius),
            modifier = if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier,
            showCenterPlay = false,
            playButtonVisible = true,
        )
    },
    fillMaxWidth = fillMaxWidth,
    modifier = modifier,
)

@Composable
fun ItemThumbnail(
    thumbnailUrl: String?,
    isActive: Boolean,
    isPlaying: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    albumIndex: Int? = null,
    isSelected: Boolean = false,
    shouldLoadImage: Boolean = true,
    @DrawableRes placeholderIconRes: Int? = null,
    thumbnailRatio: Float = 1f,
    contentScale: ContentScale? = null,
    showPlaceholder: Boolean = false,
    maxSizePx: Int? = null,
    sizeBuckets: List<Int>? = null,
    sourceAspectRatio: Float? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .fillMaxSize()
                .aspectRatio(thumbnailRatio)
                .clip(shape)
                .let {
                    if (showPlaceholder) {
                        it.background(MaterialTheme.colorScheme.surfaceVariant)
                    } else {
                        it
                    }
                },
    ) {
        val (cropThumbnailToSquare, _) = rememberPreference(CropThumbnailToSquareKey, false)
        val isYouTubeThumb = thumbnailUrl?.contains("ytimg.com", ignoreCase = true) == true
        val isSquareFrame = kotlin.math.abs(thumbnailRatio - 1f) < 0.001f
        val shouldApplySquareCrop = cropThumbnailToSquare && isYouTubeThumb && isSquareFrame

        val resolvedContentScale =
            contentScale
                ?: if (shouldApplySquareCrop || (isYouTubeThumb && isSquareFrame)) ContentScale.Crop else ContentScale.Fit
        val widthPx = if (maxWidth == Dp.Infinity) null else with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
        val heightPx = if (maxHeight == Dp.Infinity) null else with(density) { maxHeight.roundToPx().coerceAtLeast(1) }

        val guardedWidthPx = if (maxSizePx != null && widthPx != null) minOf(widthPx, maxSizePx) else widthPx
        val guardedHeightPx = if (maxSizePx != null && heightPx != null) minOf(heightPx, maxSizePx) else heightPx

        var currentUrl by remember(thumbnailUrl, guardedWidthPx, guardedHeightPx, sizeBuckets, sourceAspectRatio) {
            mutableStateOf(
                thumbnailUrl?.resize(
                    width = guardedWidthPx,
                    height = guardedHeightPx,
                    maxresAllowed = false,
                    sizeBuckets = sizeBuckets,
                    ytimgResizePolicy = YtimgResizePolicy.MatchSourceAspect,
                    sourceAspectRatio = sourceAspectRatio,
                ),
            )
        }

        if (albumIndex == null) {
            if (placeholderIconRes != null) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(placeholderIconRes),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(ListThumbnailSize * 0.48f),
                    )
                }
            }

            if (shouldLoadImage && !currentUrl.isNullOrBlank()) {
                val request =
                    remember(currentUrl, guardedWidthPx, guardedHeightPx) {
                        ImageRequest
                            .Builder(context)
                            .data(currentUrl)
                            .allowHardware(true)
                            .apply {
                                if (guardedWidthPx != null && guardedHeightPx != null) {
                                    size(guardedWidthPx, guardedHeightPx)
                                }
                            }.build()
                    }
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = resolvedContentScale,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .let { if (shouldApplySquareCrop) it.aspectRatio(1f) else it },
                    onState = { state ->
                        if (state is coil3.compose.AsyncImagePainter.State.Error) {
                            getNextFallbackUrl(currentUrl)?.let { currentUrl = it }
                        }
                    },
                )
            } else if (placeholderIconRes == null) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }

        if (albumIndex != null) {
            AnimatedVisibility(
                visible = !isActive,
                enter = fadeIn() + expandIn(expandFrom = Alignment.Center),
                exit = shrinkOut(shrinkTowards = Alignment.Center) + fadeOut(),
            ) {
                Text(
                    text = albumIndex.toString(),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        if (isSelected) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .zIndex(1f)
                        .clip(shape)
                        .background(Color.Black.copy(alpha = 0.5f)),
            ) {
                Icon(
                    painter = painterResource(R.drawable.done),
                    contentDescription = null,
                )
            }
        }

        PlayingIndicatorBox(
            isActive = isActive,
            playWhenReady = isPlaying,
            color =
                if (albumIndex != null) {
                    if (isActive) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                } else {
                    Color.White
                },
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        color =
                            if (albumIndex != null) {
                                Color.Transparent
                            } else {
                                Color.Black.copy(alpha = ActiveBoxAlpha)
                            },
                        shape = shape,
                    ),
        )
    }
}

@Composable
fun LocalThumbnail(
    thumbnailUrl: String?,
    isActive: Boolean,
    isPlaying: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    showCenterPlay: Boolean = false,
    playButtonVisible: Boolean = false,
    thumbnailRatio: Float = 1f,
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .aspectRatio(thumbnailRatio)
                .clip(shape),
    ) {
        val (cropThumbnailToSquare, _) = rememberPreference(CropThumbnailToSquareKey, false)
        val isYouTubeThumb = thumbnailUrl?.contains("ytimg.com", ignoreCase = true) == true
        val shouldApplySquareCrop = cropThumbnailToSquare && isYouTubeThumb && kotlin.math.abs(thumbnailRatio - 1f) < 0.001f
        val widthPx = if (maxWidth == Dp.Infinity) null else with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
        val heightPx = if (maxHeight == Dp.Infinity) null else with(density) { maxHeight.roundToPx().coerceAtLeast(1) }
        val request =
            remember(thumbnailUrl, widthPx, heightPx) {
                ImageRequest
                    .Builder(context)
                    .data(thumbnailUrl)
                    .allowHardware(true)
                    .apply {
                        if (widthPx != null && heightPx != null) {
                            size(widthPx, heightPx)
                        }
                    }.build()
            }
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = if (shouldApplySquareCrop) ContentScale.Crop else ContentScale.Fit,
            modifier = Modifier.fillMaxSize().let { if (shouldApplySquareCrop) it.aspectRatio(1f) else it },
        )

        AnimatedVisibility(
            visible = isActive,
            enter = fadeIn(tween(500)),
            exit = fadeOut(tween(500)),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f), shape),
            ) {
                if (isPlaying) {
                    PlayingIndicator(
                        color = Color.White,
                        modifier = Modifier.height(24.dp),
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
            }
        }

        if (showCenterPlay) {
            AnimatedVisibility(
                visible = !(isActive && isPlaying),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .padding(8.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f)),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
            }
        }

        if (playButtonVisible) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = ActiveBoxAlpha)),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistThumbnail(
    thumbnails: List<String>,
    size: Dp,
    placeHolder: @Composable () -> Unit,
    shape: Shape,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sizePx = with(density) { size.roundToPx().coerceAtLeast(1) }

    when (thumbnails.size) {
        0 -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(size)
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surfaceContainer),
            ) {
                placeHolder()
            }
        }

        1 -> {
            val request =
                remember(thumbnails, sizePx) {
                    ImageRequest
                        .Builder(context)
                        .data(
                            thumbnails[0].resize(
                                width = (sizePx * 1.5).toInt(),
                                height = (sizePx * 1.5).toInt(),
                                ytimgResizePolicy = YtimgResizePolicy.PreserveOriginal,
                            ),
                        ).size(sizePx, sizePx)
                        .allowHardware(true)
                        .build()
                }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(size)
                        .clip(shape),
            )
        }

        else -> {
            Box(
                modifier =
                    Modifier
                        .size(size)
                        .clip(shape),
            ) {
                listOf(
                    Alignment.TopStart,
                    Alignment.TopEnd,
                    Alignment.BottomStart,
                    Alignment.BottomEnd,
                ).fastForEachIndexed { index, alignment ->
                    val halfPx = (sizePx / 2).coerceAtLeast(1)
                    val url = thumbnails.getOrNull(index)
                    val request =
                        remember(url, halfPx) {
                            ImageRequest
                                .Builder(context)
                                .data(
                                    url?.resize(
                                        width = (halfPx * 1.5).toInt(),
                                        height = (halfPx * 1.5).toInt(),
                                        ytimgResizePolicy = YtimgResizePolicy.PreserveOriginal,
                                    ),
                                ).size(halfPx, halfPx)
                                .allowHardware(true)
                                .build()
                        }
                    AsyncImage(
                        model = request,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .align(alignment)
                                .size(size / 2),
                    )
                }
            }
        }
    }
}

@Composable
fun BoxScope.OverlayPlayButton(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier =
            Modifier
                .align(Alignment.Center),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = ActiveBoxAlpha)),
        ) {
            Icon(
                painter = painterResource(R.drawable.play),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun BoxScope.AlbumPlayButton(
    visible: Boolean,
    onClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier =
            Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = ActiveBoxAlpha))
                    .clickable(onClick = onClick),
        ) {
            Icon(
                painter = painterResource(R.drawable.play),
                contentDescription = null,
                tint = Color.White,
            )
        }
    }
}

@Composable
fun SwipeToSongBox(
    modifier: Modifier = Modifier,
    mediaItem: MediaItem,
    contentBackgroundColor: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val ctx = LocalContext.current
    val player = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()
    val offset = remember { mutableFloatStateOf(0f) }
    val threshold = 300f
    val resolvedContentBackgroundColor = contentBackgroundColor ?: MaterialTheme.colorScheme.surface

    val dragState =
        rememberDraggableState { delta ->
            offset.value = (offset.value + delta).coerceIn(-threshold, threshold)
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = dragState,
                    onDragStopped = {
                        when {
                            offset.value >= threshold -> {
                                player?.playNext(listOf(mediaItem))
                                Toast.makeText(ctx, R.string.play_next, Toast.LENGTH_SHORT).show()
                                reset(offset, scope)
                            }

                            offset.value <= -threshold -> {
                                player?.addToQueue(listOf(mediaItem))
                                Toast.makeText(ctx, R.string.add_to_queue, Toast.LENGTH_SHORT).show()
                                reset(offset, scope)
                            }

                            else -> {
                                reset(offset, scope)
                            }
                        }
                    },
                ),
    ) {
        if (offset.value != 0f) {
            val (iconRes, bg, tint, align) =
                if (offset.value > 0) {
                    Quadruple(
                        R.drawable.playlist_play,
                        MaterialTheme.colorScheme.secondary,
                        MaterialTheme.colorScheme.onSecondary,
                        Alignment.CenterStart,
                    )
                } else {
                    Quadruple(
                        R.drawable.queue_music,
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.onPrimary,
                        Alignment.CenterEnd,
                    )
                }

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .align(Alignment.Center)
                        .background(bg),
                contentAlignment = align,
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .padding(horizontal = 24.dp)
                            .size(30.dp)
                            .alpha(0.9f),
                    tint = tint,
                )
            }
        }

        Box(
            modifier =
                Modifier

                    .graphicsLayer {
                        translationX = offset.value
                    }
                    .fillMaxWidth()
                    .then(
                        if (offset.value != 0f) {
                            Modifier.background(resolvedContentBackgroundColor)
                        } else {
                            Modifier
                        },
                    ),
            content = content,
        )
    }
}

private fun reset(
    offset: MutableState<Float>,
    scope: CoroutineScope,
) {
    scope.launch {
        animate(
            initialValue = offset.value,
            targetValue = 0f,
            animationSpec = tween(durationMillis = 300),
        ) { value, _ -> offset.value = value }
    }
}

data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)

private object Icon {
    @Composable
    fun Favorite() {
        Icon(
            painter = painterResource(R.drawable.favorite),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier =
                Modifier
                    .size(18.dp)
                    .padding(end = 2.dp),
        )
    }

    @Composable
    fun Library() {
        Icon(
            painter = painterResource(R.drawable.library_add_check),
            contentDescription = null,
            modifier =
                Modifier
                    .size(18.dp)
                    .padding(end = 2.dp),
        )
    }

    @Composable
    fun Download(
        state: Int?,
        percent: Float = -1f,
    ) {
        when (state) {
            STATE_COMPLETED -> {
                var burstTrigger by remember { mutableStateOf<Any?>(null) }
                var lastSeenState by remember { mutableStateOf<Int?>(null) }
                LaunchedEffect(state) {
                    if (state == STATE_COMPLETED &&
                        lastSeenState != null &&
                        lastSeenState != STATE_COMPLETED
                    ) {
                        burstTrigger = System.nanoTime()
                    }
                    lastSeenState = state
                }
                LaunchedEffect(burstTrigger) {
                    if (burstTrigger != null) {
                        delay(800)
                        burstTrigger = null
                    }
                }
                if (burstTrigger != null) {
                    moe.rukamori.archivetune.ui.lottie.ArchiveTuneLottieAnimation(
                        rawRes = moe.rukamori.archivetune.ui.lottie.ArchiveTuneLottie.DownloadCompleteRes,
                        trigger = burstTrigger,
                        tintColor = MaterialTheme.colorScheme.primary,
                        modifier =
                            Modifier
                                .size(20.dp)
                                .padding(end = 0.dp),
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.offline),
                        contentDescription = null,
                        modifier =
                            Modifier
                                .size(18.dp)
                                .padding(end = 2.dp),
                    )
                }
            }

            STATE_QUEUED, STATE_DOWNLOADING -> {
                if (percent > 0f) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { percent / 100f },
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            trackColor = MaterialTheme.colorScheme.outlineVariant,
                        )
                        Text(
                            text = "${percent.toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 8.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                } else {
                    CircularWavyProgressIndicator(
                        modifier =
                            Modifier
                                .size(16.dp)
                                .padding(end = 2.dp),
                    )
                }
            }

            else -> {  }
        }
    }

    @Composable
    fun Explicit() {
        Icon(
            painter = painterResource(R.drawable.explicit),
            contentDescription = null,
            modifier =
                Modifier
                    .size(18.dp)
                    .padding(end = 2.dp),
        )
    }
}
