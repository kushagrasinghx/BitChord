package com.music.bitchord.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.LocalGroup
import com.music.bitchord.data.LocalGroups
import com.music.bitchord.data.OfflinePlaylists
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.CARD_ART_PX
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.model.playingRowMatcher
import com.music.bitchord.data.settings.LibraryViewType
import com.music.bitchord.data.settings.LocalMusicSort
import com.music.bitchord.download.DownloadState
import com.music.bitchord.download.DownloadedCollection
import com.music.bitchord.download.Downloads
import com.music.bitchord.ui.components.CoverPlaceholder
import com.music.bitchord.ui.components.ExplicitSongTitle
import com.music.bitchord.ui.components.LIBRARY_GRID_SPACING
import com.music.bitchord.ui.components.MessageState
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.ROW_DIVIDER_INSET
import com.music.bitchord.ui.components.SearchPlayingBars
import com.music.bitchord.ui.components.SongRow
import com.music.bitchord.ui.components.longPressMenuClickable
import com.music.bitchord.ui.components.rememberRemoteArtworkUrl
import com.music.bitchord.ui.components.thumbnailBorder
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.ui.theme.ArtworkPalette

/**
 * What a device folder — Downloads, Local Music, the song cache, a file
 * server — adds to the release page it is drawn on. See `folder` on
 * [DetailScreen].
 *
 * A folder *is* a release page now: a cover (the placeholder, since a folder
 * has none), its name, Play and Shuffle, then its tracks — the same page an
 * album or playlist off YouTube gets, so the two can't drift apart. What a
 * folder has that an album hasn't is a choice of how to look at its tracks,
 * Songs, Artists or Albums, on a segmented control under the header. An album
 * or artist tapped there opens as a release page of its own (see
 * [LocalGroups]), which is what makes a Local Music album look like the album
 * it is.
 */
class LocalFolder(
    /**
     * Held by the caller beside the page's scroll position, not remembered by
     * the page: an album opened from the folder replaces the folder's page
     * until Back, and the tab it was opened from has to still be showing then.
     */
    val state: LocalFolderState,
    /** The running order, from the top bar's sort menu. */
    val sort: LocalMusicSort,
    val viewType: LibraryViewType,
    /** Downloads only: the releases those files were asked for as — see [LocalGroups.albumsOf]. */
    val collections: List<DownloadedCollection> = emptyList(),
    /**
     * Downloads only: holding a song or an album selects it, for [onDelete] and
     * [onUpload] to act on, and the row's ⋮ carries the actions sheet instead.
     */
    val selectable: Boolean = false,
    val onOpenGroup: (LocalGroup) -> Unit,
    /** An album or artist held — its menu, with the rows it covers in hand. */
    val onGroupLongPress: (LocalGroup) -> Unit,
    val onDelete: ((List<Song>) -> Unit)? = null,
    /** Copies the selection to WebDAV; null (no server configured) hides the action. */
    val onUpload: ((List<Song>) -> Unit)? = null,
    /**
     * Cached songs only: saves tracks to Downloads straight from the song
     * cache. Set, it puts a download button on every row, Download all in the
     * header, and Download on the selection.
     */
    val onDownload: ((List<Song>) -> Unit)? = null,
    /** Cached songs only: takes the selection out of the song cache. */
    val onRemoveFromCache: ((List<Song>) -> Unit)? = null,
    /** Which ways of looking at the tracks this folder offers, in order. */
    val tabs: List<LocalFolderTab> = LocalFolderTab.BASIC,
    /**
     * Local Music only: the offline player's own playlists, Liked first, as
     * the Playlists tab lists them — see [com.music.bitchord.data.OfflinePlaylists].
     */
    val playlists: List<LocalGroup> = emptyList(),
    /** Puts "New playlist" at the head of the Playlists tab. */
    val onCreatePlaylist: (() -> Unit)? = null,
)

enum class LocalFolderTab {
    SONGS,
    ARTISTS,
    ALBUMS,

    /** Playlists made on this device, of files on this device. */
    PLAYLISTS,

    /** The device's own directories, as a file manager shows them. */
    FOLDERS,
    ;

    companion object {
        /** Every folder's: the three readings any set of tracks has. */
        val BASIC = listOf(SONGS, ARTISTS, ALBUMS)

        /** Local Music's: the whole device, so its playlists and directories too. */
        val DEVICE = entries.toList()
    }
}

/**
 * A folder's own state while its page is on the stack: the tab showing, and
 * the Downloads multi-selection.
 *
 * Albums are selected by key as well as by their tracks: different albums can
 * share tracks, so inferring an album's selection from track ids made one held
 * playlist appear to select unrelated albums.
 */
@Stable
class LocalFolderState {
    var tab by mutableStateOf(LocalFolderTab.SONGS)

    var selectedIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var selectedGroups by mutableStateOf<Set<String>>(emptySet())
        private set
    val selecting: Boolean get() = selectedIds.isNotEmpty()

    fun select(song: Song) {
        selectedIds = selectedIds + song.videoId
    }

    fun toggle(song: Song) {
        selectedIds = if (song.videoId in selectedIds) selectedIds - song.videoId else selectedIds + song.videoId
    }

    fun select(group: LocalGroup) {
        selectedGroups = selectedGroups + group.browseId
        selectedIds = selectedIds + group.songs.map { it.videoId }
    }

    fun toggle(group: LocalGroup) {
        if (group.browseId in selectedGroups) {
            selectedGroups = selectedGroups - group.browseId
            selectedIds = selectedIds - group.songs.mapTo(HashSet()) { it.videoId }
        } else {
            select(group)
        }
    }

    fun selectAll(songs: List<Song>) {
        selectedIds = songs.mapTo(linkedSetOf()) { it.videoId }
    }

    fun clear() {
        selectedIds = emptySet()
        selectedGroups = emptySet()
    }
}

/**
 * The Artists or Albums tab's entries, narrowed by [query]; empty on Songs,
 * which lists the page's own filtered tracks.
 *
 * Grouped off [songs] as listed rather than as sorted, so a grouping comes out
 * in the same order its page reads it back in (see [LocalGroups.songsOf]).
 */
@Composable
fun rememberLocalFolderGroups(
    browseId: String,
    folder: LocalFolder,
    tab: LocalFolderTab,
    songs: List<Song>,
    query: String,
): List<LocalGroup> = remember(browseId, tab, songs, folder.collections, folder.playlists, query) {
    fun LocalGroup.matches() = query.isBlank() ||
        title.contains(query, ignoreCase = true) ||
        subtitle.contains(query, ignoreCase = true)
    when (tab) {
        LocalFolderTab.SONGS -> emptyList()
        LocalFolderTab.ARTISTS -> LocalGroups.artistsOf(browseId, songs).filter { it.matches() }
        LocalFolderTab.ALBUMS -> LocalGroups.albumsOf(browseId, songs, folder.collections).filter { it.matches() }
        LocalFolderTab.PLAYLISTS -> folder.playlists.filter { it.matches() }
        LocalFolderTab.FOLDERS -> LocalGroups.foldersOf(browseId, songs).filter { it.matches() }
    }
}

/**
 * The folder's part of the release page's list, from the tab control down.
 *
 * [songs] is what the Songs tab lists and a tap there plays: the page's own
 * running order, already narrowed by the filter box. A grid is laid out as rows
 * of cards inside the page's one list, so the header scrolls away with it the
 * way it does on every other release page.
 */
internal fun LazyListScope.localFolderItems(
    folder: LocalFolder,
    state: LocalFolderState,
    songs: List<Song>,
    groups: List<LocalGroup>,
    query: String,
    columns: Int,
    palette: ArtworkPalette,
    currentSong: Song?,
    isPlaying: Boolean,
    onSongClick: (List<Song>, Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onSongSwipe: (Song) -> Unit,
) {
    val tabs = folder.tabs
    item(key = "folder-tabs") {
        SegmentedControl(
            options = tabs.map { it.label() },
            selectedIndex = tabs.indexOf(state.tab).coerceAtLeast(0),
            onSelect = { state.tab = tabs[it] },
            modifier = Modifier.padding(start = PAGE_GUTTER, end = PAGE_GUTTER, bottom = 14.dp),
        )
    }

    // Ahead of the list rather than in the top bar: making a playlist is the
    // one thing this tab is for before there is anything on it.
    if (state.tab == LocalFolderTab.PLAYLISTS && query.isBlank()) {
        folder.onCreatePlaylist?.let { create ->
            item(key = "folder-new-playlist") {
                NewPlaylistRow(palette, onClick = create)
                FolderDivider(palette)
            }
        }
    }

    val grid = folder.viewType == LibraryViewType.GRID
    val empty = when (state.tab) {
        LocalFolderTab.SONGS -> songs.isEmpty()
        else -> groups.isEmpty()
    }
    if (empty) {
        item(key = "folder-empty") {
            MessageState(
                if (query.isNotBlank()) {
                    stringResource(R.string.nothing_matches, query)
                } else if (state.tab == LocalFolderTab.ALBUMS) {
                    stringResource(R.string.no_local_albums)
                } else if (state.tab == LocalFolderTab.FOLDERS) {
                    stringResource(R.string.no_local_folders)
                } else {
                    stringResource(R.string.no_tracks_here)
                },
            )
        }
        return
    }

    // Downloads redefines the hold as "select", so the ⋮ has to carry the
    // actions sheet itself or the page loses it.
    val songClick: (Int) -> Unit = { index ->
        if (state.selecting) state.toggle(songs[index]) else onSongClick(songs, index)
    }
    val songHold: (Song) -> Unit = { song ->
        if (folder.selectable) state.select(song) else onSongLongPress(song)
    }
    val groupClick: (LocalGroup) -> Unit = { group ->
        if (state.selecting && group.type != BrowseType.OTHER) state.toggle(group) else folder.onOpenGroup(group)
    }
    val groupHold: (LocalGroup) -> Unit = { group ->
        if (folder.selectable && group.type != BrowseType.OTHER) state.select(group) else folder.onGroupLongPress(group)
    }

    val isCurrentRow = playingRowMatcher(songs, currentSong)
    when (state.tab) {
        LocalFolderTab.SONGS -> if (grid) {
            gridRows(songs.indices.toList(), columns, key = { "song-${songs[it].videoId}-$it" }) { index ->
                val song = songs[index]
                SongGridCard(
                    song = song,
                    selected = song.videoId in state.selectedIds,
                    isCurrent = isCurrentRow(song),
                    isPlaying = isCurrentRow(song) && isPlaying,
                    palette = palette,
                    onClick = { songClick(index) },
                    onLongPress = { songHold(song) },
                )
            }
        } else {
            itemsIndexed(songs, key = { index, song -> "song-${song.videoId}-$index" }) { index, song ->
                SongRow(
                    song = song,
                    selected = song.videoId in state.selectedIds,
                    isCurrent = isCurrentRow(song),
                    isPlaying = isCurrentRow(song) && isPlaying,
                    searchPlayingStyle = true,
                    activeTint = palette.accent,
                    subtitleColor = palette.onBackgroundVariant,
                    rowBackground = Color.Transparent,
                    // Every row here is on the device already.
                    downloadedTint = null,
                    onClick = { songClick(index) },
                    onLongPress = { songHold(song) },
                    onMore = { onSongLongPress(song) },
                    onSwipeToQueue = { onSongSwipe(song) },
                    trailing = folder.onDownload?.let { download ->
                        { RowDownloadButton(song, palette) { download(listOf(song)) } }
                    },
                )
                if (index < songs.lastIndex) FolderDivider(palette)
            }
        }

        LocalFolderTab.ARTISTS -> if (grid) {
            gridRows(groups, columns, key = { it.browseId }) { group ->
                ArtistGridCard(group, palette, onClick = { groupClick(group) }, onLongPress = { groupHold(group) })
            }
        } else {
            items(groups, key = { it.browseId }) { group ->
                ArtistRow(group, palette, onClick = { groupClick(group) }, onLongPress = { groupHold(group) })
                FolderDivider(palette)
            }
        }

        LocalFolderTab.ALBUMS, LocalFolderTab.PLAYLISTS, LocalFolderTab.FOLDERS -> if (grid) {
            gridRows(groups, columns, key = { it.browseId }) { group ->
                AlbumGridCard(
                    group = group,
                    palette = palette,
                    selected = group.browseId in state.selectedGroups,
                    onClick = groupClick,
                    onLongPress = { groupHold(group) },
                )
            }
        } else {
            items(groups, key = { it.browseId }) { group ->
                AlbumRow(
                    group = group,
                    palette = palette,
                    selected = group.browseId in state.selectedGroups,
                    onClick = groupClick,
                    onLongPress = { groupHold(group) },
                )
                FolderDivider(palette)
            }
        }
    }
}

@Composable
private fun LocalFolderTab.label(): String = stringResource(
    when (this) {
        LocalFolderTab.SONGS -> R.string.songs
        LocalFolderTab.ARTISTS -> R.string.artists
        LocalFolderTab.ALBUMS -> R.string.albums
        LocalFolderTab.PLAYLISTS -> R.string.playlists
        LocalFolderTab.FOLDERS -> R.string.folders
    },
)

@Composable
private fun NewPlaylistRow(palette: ArtworkPalette, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = PAGE_GUTTER, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(shape)
                .background(palette.accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                BitChordIcons.Plus,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = stringResource(R.string.new_playlist),
            style = MaterialTheme.typography.titleMedium,
            color = palette.onBackground,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * [items] as rows of [columns] cards, each row one item of the page's list.
 * A short last row keeps its cards the width of the rest rather than
 * stretching them across the gap.
 */
private fun <T> LazyListScope.gridRows(
    items: List<T>,
    columns: Int,
    key: (T) -> Any,
    card: @Composable (T) -> Unit,
) {
    val rows = items.chunked(columns.coerceAtLeast(1))
    items(rows.size, key = { "grid-$it-${key(rows[it].first())}" }) { row ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PAGE_GUTTER)
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(LIBRARY_GRID_SPACING),
        ) {
            rows[row].forEach { entry -> Box(Modifier.weight(1f)) { card(entry) } }
            repeat(columns - rows[row].size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun FolderDivider(palette: ArtworkPalette) {
    HorizontalDivider(
        modifier = Modifier.padding(start = ROW_DIVIDER_INSET),
        thickness = 0.5.dp,
        color = palette.divider,
    )
}

/** Tinted behind a card that is part of the Downloads selection. */
@Composable
private fun selectionFill(selected: Boolean): Color =
    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent

private val CARD_SHAPE = RoundedCornerShape(12.dp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongGridCard(
    song: Song,
    selected: Boolean,
    isCurrent: Boolean,
    isPlaying: Boolean,
    palette: ArtworkPalette,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(CARD_SHAPE)
            .background(selectionFill(selected))
            .longPressMenuClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(CARD_SHAPE)
                .thumbnailBorder(CARD_SHAPE),
            contentAlignment = Alignment.Center,
        ) {
            CoverPlaceholder(Icons.Rounded.MusicNote, Modifier.fillMaxSize())
            if (song.thumbnailUrl != null) {
                AsyncImage(
                    model = song.thumbnailUrl.artworkAt(CARD_ART_PX),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (isCurrent && isPlaying) {
                SearchPlayingBars(Modifier.align(Alignment.Center))
            } else if (isCurrent) {
                Icon(
                    Icons.Rounded.GraphicEq,
                    contentDescription = stringResource(R.string.now_playing),
                    tint = palette.accent,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        ExplicitSongTitle(
            song = song,
            style = MaterialTheme.typography.titleMedium,
            color = if (isCurrent) palette.accent else palette.onBackground,
        )
        Text(
            text = song.artist.ifBlank { stringResource(R.string.unknown_artist) },
            style = MaterialTheme.typography.bodySmall,
            color = palette.onBackgroundVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** An artist's avatar: the first of their tracks that has a picture, in a circle. */
@Composable
private fun ArtistAvatar(group: LocalGroup, modifier: Modifier) {
    val art = remember(group.songs) { group.songs.firstNotNullOfOrNull { it.thumbnailUrl } }
    Box(modifier.clip(CircleShape).thumbnailBorder(CircleShape)) {
        CoverPlaceholder(Icons.Rounded.Person, Modifier.fillMaxSize(), iconFraction = 0.5f)
        if (art != null) {
            AsyncImage(
                model = art.artworkAt(CARD_ART_PX),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtistRow(
    group: LocalGroup,
    palette: ArtworkPalette,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .longPressMenuClickable(
                onClick = onClick,
                onLongClick = onLongPress,
                liftPadding = PaddingValues(horizontal = PAGE_GUTTER),
            )
            .padding(horizontal = PAGE_GUTTER, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtistAvatar(group, Modifier.size(52.dp))
        Spacer(Modifier.width(16.dp))
        GroupLabels(
            title = group.title,
            subtitle = pluralStringResource(R.plurals.song_count_plural, group.songs.size, group.songs.size),
            palette = palette,
            modifier = Modifier.weight(1f),
        )
        TrailingGlyph(selected = false, palette = palette)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtistGridCard(
    group: LocalGroup,
    palette: ArtworkPalette,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(CARD_SHAPE)
            .longPressMenuClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtistAvatar(group, Modifier.fillMaxWidth(0.88f).aspectRatio(1f))
        Spacer(Modifier.height(8.dp))
        Text(
            text = group.title,
            style = MaterialTheme.typography.titleMedium,
            color = palette.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = pluralStringResource(R.plurals.song_count_plural, group.songs.size, group.songs.size),
            style = MaterialTheme.typography.bodySmall,
            color = palette.onBackgroundVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * "Artist · 12 songs", or "Playlist · 40 songs" — a playlist's tracks are off
 * forty different releases, so the kind of thing it is says more than any one
 * of their artists would.
 */
@Composable
private fun LocalGroup.albumSubtitle(): String {
    val count = pluralStringResource(R.plurals.song_count_plural, songs.size, songs.size)
    return when {
        // A directory's subtitle is where it is, which is what tells two
        // "Music" folders apart.
        LocalGroups.isDirectory(browseId) -> if (subtitle.isNotBlank()) "$subtitle · $count" else count
        type == BrowseType.PLAYLIST -> "${stringResource(R.string.playlist)} · $count"
        subtitle.isNotBlank() && subtitle != title -> "$subtitle · $count"
        else -> count
    }
}

/**
 * The cover an album row or card shows: the release's own, else — for a
 * file-server track with no thumbnail — the picture embedded in its first file.
 */
@Composable
private fun LocalGroup.coverArt(): String? {
    val fetched = rememberRemoteArtworkUrl(songs.firstOrNull())
    return thumbnailUrl ?: fetched
}

private fun LocalGroup.placeholderIcon(): ImageVector = when {
    OfflinePlaylists.isLikedPage(browseId) -> Icons.Rounded.Favorite
    LocalGroups.isDirectory(browseId) -> Icons.Rounded.Folder
    type == BrowseType.PLAYLIST -> Icons.AutoMirrored.Rounded.QueueMusic
    else -> Icons.Rounded.Album
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumGridCard(
    group: LocalGroup,
    palette: ArtworkPalette,
    selected: Boolean,
    /** Handed the group with the cover this card found, so its page opens on the same one. */
    onClick: (LocalGroup) -> Unit,
    onLongPress: () -> Unit,
) {
    val art = group.coverArt()
    Column(
        modifier = Modifier
            .clip(CARD_SHAPE)
            .background(selectionFill(selected))
            .longPressMenuClickable(onClick = { onClick(group.copy(thumbnailUrl = art)) }, onLongClick = onLongPress)
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(CARD_SHAPE)
                .thumbnailBorder(CARD_SHAPE),
        ) {
            CoverPlaceholder(group.placeholderIcon(), Modifier.fillMaxSize())
            if (art != null) {
                AsyncImage(
                    model = art.artworkAt(CARD_ART_PX),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = group.title,
            style = MaterialTheme.typography.titleMedium,
            color = palette.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = group.albumSubtitle(),
            style = MaterialTheme.typography.bodySmall,
            color = palette.onBackgroundVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumRow(
    group: LocalGroup,
    palette: ArtworkPalette,
    selected: Boolean,
    onClick: (LocalGroup) -> Unit,
    onLongPress: () -> Unit,
) {
    val art = group.coverArt()
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(selectionFill(selected))
            .longPressMenuClickable(
                onClick = { onClick(group.copy(thumbnailUrl = art)) },
                onLongClick = onLongPress,
                liftPadding = PaddingValues(horizontal = PAGE_GUTTER),
            )
            .padding(horizontal = PAGE_GUTTER, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp).clip(shape).thumbnailBorder(shape)) {
            CoverPlaceholder(group.placeholderIcon(), Modifier.fillMaxSize(), iconFraction = 0.46f)
            if (art != null) {
                AsyncImage(
                    model = art.artworkAt(ROW_ART_PX),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        GroupLabels(
            title = group.title,
            subtitle = group.albumSubtitle(),
            palette = palette,
            modifier = Modifier.weight(1f),
        )
        TrailingGlyph(selected = selected, palette = palette)
    }
}

@Composable
private fun GroupLabels(title: String, subtitle: String, palette: ArtworkPalette, modifier: Modifier) {
    Column(modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = palette.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.onBackgroundVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A chevron, since the row opens a page — or a tick while it is part of the selection. */
@Composable
private fun TrailingGlyph(selected: Boolean, palette: ArtworkPalette) {
    Icon(
        imageVector = if (selected) Icons.Rounded.CheckCircle else BitChordIcons.ChevronRight,
        contentDescription = if (selected) stringResource(R.string.selected) else null,
        tint = if (selected) MaterialTheme.colorScheme.primary else palette.onBackgroundVariant,
        modifier = Modifier.size(if (selected) 22.dp else 16.dp),
    )
}

/**
 * The Downloads selection's actions, floated under the top bar while anything
 * is selected — over the list rather than in it, so it stays in reach however
 * far down the selection was made.
 */
@Composable
internal fun LocalFolderSelectionBar(
    folder: LocalFolder,
    state: LocalFolderState,
    /** Every track in the folder, for Select all and to resolve the selection against. */
    songs: List<Song>,
    modifier: Modifier = Modifier,
) {
    val count = state.selectedIds.size
    val allSelected = songs.isNotEmpty() && songs.all { it.videoId in state.selectedIds }
    fun chosen() = songs.filter { it.videoId in state.selectedIds }.also { state.clear() }
    val shape = RoundedCornerShape(14.dp)
    val dark = isSystemInDarkTheme()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .shadow(10.dp, shape)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { state.selectAll(songs) }, enabled = !allSelected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = stringResource(R.string.select_all),
                tint = if (dark) Color.White else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = "$count ${stringResource(R.string.selected)}",
            style = MaterialTheme.typography.titleSmall,
            color = if (dark) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        folder.onUpload?.let { upload ->
            SelectionAction(
                icon = Icons.Rounded.FileUpload,
                label = stringResource(R.string.upload_to_webdav),
                color = MaterialTheme.colorScheme.primary,
                enabled = count > 0,
                onClick = { upload(chosen()) },
            )
        }
        folder.onDownload?.let { download ->
            SelectionAction(
                icon = Icons.Rounded.Download,
                label = stringResource(R.string.download),
                color = MaterialTheme.colorScheme.primary,
                enabled = count > 0,
                onClick = { download(chosen()) },
            )
        }
        folder.onRemoveFromCache?.let { remove ->
            SelectionAction(
                icon = Icons.Rounded.Delete,
                label = stringResource(R.string.cached_song_remove),
                color = DESTRUCTIVE,
                enabled = count > 0,
                onClick = { remove(chosen()) },
            )
        }
        folder.onDelete?.let { delete ->
            SelectionAction(
                icon = Icons.Rounded.Delete,
                label = stringResource(R.string.delete),
                color = DESTRUCTIVE,
                enabled = count > 0,
                onClick = { delete(chosen()) },
            )
        }
        TextButton(onClick = state::clear) { Text(stringResource(R.string.cancel)) }
    }
}

/**
 * A Cached songs row's own download button, showing where that track's download
 * stands: tap to save it, a ring while it is queued or saving (tap again to
 * call it off), a tick once it is in Downloads, and a retry after a failure.
 *
 * Read straight from [Downloads] where it is drawn, as the song menu's download
 * row does, so it moves as the download does without the page reloading.
 */
@Composable
private fun RowDownloadButton(song: Song, palette: ArtworkPalette, onDownload: () -> Unit) {
    val active by Downloads.active.collectAsStateWithLifecycle()
    val saved by Downloads.saved.collectAsStateWithLifecycle()
    val state = active[song.videoId]
    val done = state == null && song.videoId in saved
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(enabled = !done) {
                when (state) {
                    is DownloadState.Queued, is DownloadState.Running -> Downloads.cancel(song.videoId)
                    else -> onDownload()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            is DownloadState.Queued -> CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = palette.accent,
            )
            is DownloadState.Running -> if (state.fraction > 0f) {
                CircularProgressIndicator(
                    progress = { state.fraction },
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = palette.accent,
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = palette.accent,
                )
            }
            is DownloadState.Failed -> Icon(
                Icons.Rounded.ErrorOutline,
                contentDescription = stringResource(R.string.retry),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
            null -> Icon(
                if (done) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                contentDescription = stringResource(if (done) R.string.downloaded else R.string.download),
                tint = if (done) palette.accent else palette.onBackgroundVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SelectionAction(icon: ImageVector, label: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = color)
    }
}

private val DESTRUCTIVE = Color(0xFFFF453A)

@Composable
internal fun LocalMusicSort.localizedLabel(): String = when (this) {
    LocalMusicSort.TITLE_ASC -> stringResource(R.string.sort_title_ascending)
    LocalMusicSort.TITLE_DESC -> stringResource(R.string.sort_title_descending)
    LocalMusicSort.DATE_ADDED -> stringResource(R.string.sort_date_added)
    LocalMusicSort.DATE_MODIFIED -> stringResource(R.string.sort_date_modified)
}
