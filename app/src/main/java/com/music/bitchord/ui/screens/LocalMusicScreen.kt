package com.music.bitchord.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.CARD_ART_PX
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.Song
import com.music.bitchord.R
import com.music.bitchord.ui.components.ExplicitSongTitle
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.LibraryViewType
import com.music.bitchord.data.settings.LocalMusicSort
import com.music.bitchord.download.DownloadedCollection
import com.music.bitchord.ui.components.MessageState
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.ROW_DIVIDER_INSET
import com.music.bitchord.ui.components.SongRow
import com.music.bitchord.ui.components.thumbnailBorder
import com.music.bitchord.ui.components.TopBarContentGap
import com.music.bitchord.ui.components.topBarHeight
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics
import com.music.bitchord.ui.icons.BitChordIcons
import java.util.Locale

private const val LOCAL_TAB_SONGS = 0
private const val LOCAL_TAB_ARTISTS = 1
private const val LOCAL_TAB_ALBUMS = 2

/**
 * Local Music folder view with three tabs: Songs (default), Artists, Albums.
 *
 * Also the Downloads folder — the two are the same thing from here, a flat list
 * of tracks on this device, and they read as the same page because they are the
 * same page. What differs is only where the list came from, what to say when it
 * is empty, and whether anything knows how those tracks were *asked* for: see
 * [collections], which is the Downloads folder's alone.
 *
 * Tapping an artist or album name slides in a filtered song list inline, so
 * the tab bar stays visible and Back returns to the grid rather than leaving
 * the screen.
 */
@Composable
fun LocalMusicScreen(
    songs: List<Song>,
    onSongClick: (List<Song>, Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onSongSwipe: (Song) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    contentPadding: PaddingValues,
    /**
     * Shown in place of the tab content when there are no songs at all — the
     * reason there are none, which "0 songs" on its own doesn't give.
     */
    emptyMessage: String? = null,
    /**
     * One of the Artists / Albums groupings, held rather than tapped — the
     * album/playlist menu, with the rows it covers already in hand. Nothing
     * here has a browse id to fetch, so this is the only way these get one.
     */
    onCollectionLongPress: ((String, List<Song>) -> Unit)? = null,
    /**
     * The albums and playlists that were downloaded *as* albums and playlists.
     *
     * They lead the Albums tab, because they are the only entries on it that the
     * user actually asked for by name — the rest are groupings this screen
     * derived from whatever album tag each file happens to carry, which is a
     * good guess and nothing more. A playlist cannot be derived that way at all:
     * its tracks are off forty different releases and no tag on any of them says
     * which playlist they were pulled from, so without this a downloaded
     * playlist simply scattered.
     *
     * Empty for Local Music, where nothing was asked for through this app and
     * the tags are all there is.
     */
    collections: List<DownloadedCollection> = emptyList(),
    isDownloads: Boolean = false,
    currentSong: Song? = null,
    isPlaying: Boolean = false,
    /** Deletes the Downloads rows selected through this screen's long-press mode. */
    onDeleteDownloads: ((List<Song>) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // Which top-level tab is selected.
    var selectedTab by rememberSaveable { mutableIntStateOf(LOCAL_TAB_SONGS) }

    // Narrows whichever tab is showing — songs by title/artist/album, artists
    // and albums by name. Not saved across process death: a filter left on a
    // folder that was never reopened is more surprising than one that reset.
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val sortOrder by if (isDownloads) {
        AppSettings.downloadedMusicSort.collectAsStateWithLifecycle()
    } else {
        AppSettings.localMusicSort.collectAsStateWithLifecycle()
    }
    val viewType by if (isDownloads) {
        AppSettings.downloadedMusicViewType.collectAsStateWithLifecycle()
    } else {
        AppSettings.localMusicViewType.collectAsStateWithLifecycle()
    }
    val sortedSongs = remember(songs, sortOrder) { songs.sortedForLibrary(sortOrder) }
    var selectedDownloadIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    // Kept separately from the tracks selected for deletion: different albums
    // can share tracks, so inferring an album's selection from those track ids
    // made one held playlist appear to select unrelated albums.
    var selectedAlbumKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    val selectingDownloads = isDownloads && selectedDownloadIds.isNotEmpty()

    fun toggleDownloadSelection(song: Song) {
        selectedDownloadIds = selectedDownloadIds.let { selected ->
            if (song.videoId in selected) selected - song.videoId else selected + song.videoId
        }
    }

    fun toggleAlbumSelection(entry: AlbumEntry) {
        val albumIds = entry.songs.mapTo(linkedSetOf()) { it.videoId }
        if (entry.key in selectedAlbumKeys) {
            selectedAlbumKeys = selectedAlbumKeys - entry.key
            selectedDownloadIds = selectedDownloadIds - albumIds
        } else {
            selectedAlbumKeys = selectedAlbumKeys + entry.key
            selectedDownloadIds = selectedDownloadIds + albumIds
        }
    }

    // When non-null, we are showing a drill-down list for that artist or album.
    var drillDownLabel by remember { mutableStateOf<String?>(null) }
    var drillDownSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    // The release's own cover, for the drill-down header. Only a downloaded
    // album or playlist has one worth showing — a tag-derived grouping's
    // "artwork" is just whichever of its rows happened to be first.
    var drillDownArt by remember { mutableStateOf<String?>(null) }

    val inDrillDown = drillDownLabel != null

    val leaveDrillDown = {
        drillDownLabel = null
        drillDownSongs = emptyList()
        drillDownArt = null
    }

    BackHandler(enabled = selectingDownloads) {
        selectedDownloadIds = emptySet()
        selectedAlbumKeys = emptySet()
    }
    BackHandler(enabled = inDrillDown && !selectingDownloads) { leaveDrillDown() }

    // The tab row is fixed above the scrolling content, so its own top
    // padding has to clear the frosted top bar / status bar that the
    // LazyColumns beneath it would otherwise scroll under.
    val bodyContentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding())

    // contentPadding.top carries extra breathing room meant for scrolling
    // content resting under the glass bar; the tab row is fixed and sits
    // right below the bar, so it only needs to clear the bar itself.
    val barHeight = topBarHeight()

    Column(modifier = modifier.fillMaxSize()) {
        // ── Search ───────────────────────────────────────────────────────────
        // Above the tabs rather than inside each one, since a query typed on
        // Songs is just as reasonable to carry over to Artists or Albums.
        if (selectingDownloads) {
            DownloadSelectionBar(
                count = selectedDownloadIds.size,
                allSelected = sortedSongs.isNotEmpty() && selectedDownloadIds.containsAll(sortedSongs.map { it.videoId }),
                onSelectAll = { selectedDownloadIds = sortedSongs.mapTo(linkedSetOf()) { it.videoId } },
                onDelete = {
                    val chosen = songs.filter { it.videoId in selectedDownloadIds }
                    selectedDownloadIds = emptySet()
                    selectedAlbumKeys = emptySet()
                    onDeleteDownloads?.invoke(chosen)
                },
                onCancel = {
                    selectedDownloadIds = emptySet()
                    selectedAlbumKeys = emptySet()
                },
                modifier = Modifier.padding(
                    top = barHeight + TopBarContentGap,
                    start = PAGE_GUTTER,
                    end = PAGE_GUTTER,
                    bottom = 4.dp,
                ),
            )
        } else LocalSearchField(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            sortOrder = sortOrder,
            onSortOrderChange = {
                if (isDownloads) AppSettings.setDownloadedMusicSort(it)
                else AppSettings.setLocalMusicSort(it)
            },
            viewType = viewType,
            onViewTypeToggle = {
                val next = if (viewType == LibraryViewType.GRID) LibraryViewType.LIST else LibraryViewType.GRID
                if (isDownloads) AppSettings.setDownloadedMusicViewType(next)
                else AppSettings.setLocalMusicViewType(next)
            },
            modifier = Modifier.padding(
                // The same clearance every other page under the frosted bar
                // gets — see topBarContentPadding, which this screen can't use
                // directly since its tab row is fixed and only the search field
                // above it needs to clear the bar.
                top = barHeight + TopBarContentGap,
                start = PAGE_GUTTER,
                end = PAGE_GUTTER,
                bottom = 4.dp,
            ),
        )

        // ── Tab row ──────────────────────────────────────────────────────────
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = MaterialTheme.colorScheme.primary,
                )
            },
        ) {
            LocalTab(
                icon = Icons.Rounded.MusicNote,
                label = stringResource(R.string.songs),
                selected = selectedTab == LOCAL_TAB_SONGS,
                onClick = {
                    selectedTab = LOCAL_TAB_SONGS
                    leaveDrillDown()
                },
            )
            LocalTab(
                icon = Icons.Rounded.Person,
                label = stringResource(R.string.artists),
                selected = selectedTab == LOCAL_TAB_ARTISTS,
                onClick = {
                    selectedTab = LOCAL_TAB_ARTISTS
                    leaveDrillDown()
                },
            )
            LocalTab(
                icon = Icons.Rounded.Album,
                label = stringResource(R.string.albums),
                selected = selectedTab == LOCAL_TAB_ALBUMS,
                onClick = {
                    selectedTab = LOCAL_TAB_ALBUMS
                    leaveDrillDown()
                },
            )
        }

        // ── Content ──────────────────────────────────────────────────────────
        AnimatedContent(
            targetState = if (inDrillDown) "drill:$drillDownLabel" else "tab:$selectedTab",
            transitionSpec = {
                if (targetState.startsWith("drill:")) {
                    (slideInHorizontally { it } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 3 } + fadeOut())
                } else {
                    (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { it } + fadeOut())
                }
            },
            label = "local_music_content",
            modifier = Modifier.fillMaxSize(),
        ) { key ->
            when {
                // Nothing to tab through. The tab row stays put rather than
                // being swapped out with the list, so the page still reads as
                // itself while it says why it's empty.
                sortedSongs.isEmpty() && emptyMessage != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bodyContentPadding),
                    ) {
                        MessageState(message = emptyMessage)
                    }
                }

                key.startsWith("drill:") -> {
                    // Drill-down song list for artist / album
                    DrillDownSongList(
                        label = drillDownLabel ?: "",
                        artworkUrl = drillDownArt,
                        songs = drillDownSongs,
                        viewType = viewType,
                        selectedIds = selectedDownloadIds,
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        onSongClick = { tracks, index ->
                            val song = tracks[index]
                            if (selectingDownloads) toggleDownloadSelection(song) else onSongClick(tracks, index)
                        },
                        onSongLongPress = { song ->
                            if (isDownloads) selectedDownloadIds = selectedDownloadIds + song.videoId
                            else onSongLongPress(song)
                        },
                        onSongSwipe = onSongSwipe,
                        onShuffle = onShuffle,
                        onMore = onCollectionLongPress?.let { more ->
                            { more(drillDownLabel ?: "", drillDownSongs) }
                        },
                        onBack = leaveDrillDown,
                        contentPadding = bodyContentPadding,
                    )
                }

                key == "tab:$LOCAL_TAB_SONGS" -> {
                    val filteredSongs = remember(sortedSongs, searchQuery) {
                        if (searchQuery.isBlank()) sortedSongs
                        else sortedSongs.filter { it.matchesSearch(searchQuery) }
                    }
                    SongsTab(
                        songs = filteredSongs,
                        viewType = viewType,
                        selectedIds = selectedDownloadIds,
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        onSongClick = { tracks, index ->
                            val song = tracks[index]
                            if (selectingDownloads) toggleDownloadSelection(song) else onSongClick(tracks, index)
                        },
                        onSongLongPress = { song ->
                            if (isDownloads) {
                                selectedDownloadIds = selectedDownloadIds + song.videoId
                                selectedTab = LOCAL_TAB_SONGS
                                leaveDrillDown()
                            } else onSongLongPress(song)
                        },
                        onSongSwipe = onSongSwipe,
                        contentPadding = bodyContentPadding,
                    )
                }

                key == "tab:$LOCAL_TAB_ARTISTS" -> {
                    val artists = remember(sortedSongs, searchQuery) {
                        sortedSongs.groupBy { it.artist }
                            .entries
                            .filter { searchQuery.isBlank() || it.key.contains(searchQuery, ignoreCase = true) }
                            .sortedBy { it.key.lowercase(Locale.ROOT) }
                    }
                    ArtistsTab(
                        artists = artists,
                        viewType = viewType,
                        onArtistClick = { artist, artistSongs ->
                            drillDownLabel = artist
                            drillDownSongs = artistSongs
                            drillDownArt = null
                        },
                        onArtistLongPress = onCollectionLongPress,
                        contentPadding = bodyContentPadding,
                    )
                }

                else -> {
                    // LOCAL_TAB_ALBUMS
                    val albums = remember(sortedSongs, collections, searchQuery) {
                        albumEntries(sortedSongs, collections).filter {
                            searchQuery.isBlank() ||
                                it.title.contains(searchQuery, ignoreCase = true) ||
                                it.artist.contains(searchQuery, ignoreCase = true)
                        }
                    }
                    AlbumsTab(
                        albums = albums,
                        viewType = viewType,
                        selectedKeys = selectedAlbumKeys,
                        onAlbumClick = { entry ->
                            if (selectingDownloads) {
                                toggleAlbumSelection(entry)
                            } else {
                                drillDownLabel = entry.title
                                drillDownSongs = entry.songs
                                drillDownArt = entry.thumbnailUrl
                            }
                        },
                        onAlbumLongPress = { entry ->
                            if (isDownloads) {
                                selectedDownloadIds = selectedDownloadIds + entry.songs.map { it.videoId }
                                selectedAlbumKeys = selectedAlbumKeys + entry.key
                            } else onCollectionLongPress?.invoke(entry.title, entry.songs)
                        },
                        contentPadding = bodyContentPadding,
                    )
                }
            }
        }
    }
}

// ── Songs tab ─────────────────────────────────────────────────────────────────

@Composable
private fun SongsTab(
    songs: List<Song>,
    viewType: LibraryViewType,
    selectedIds: Set<String> = emptySet(),
    currentSong: Song? = null,
    isPlaying: Boolean = false,
    onSongClick: (List<Song>, Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onSongSwipe: (Song) -> Unit,
    contentPadding: PaddingValues,
) {
    if (viewType == LibraryViewType.GRID) {
        val gridState = rememberLazyGridState()
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = PAGE_GUTTER,
                end = PAGE_GUTTER,
                top = 6.dp,
                bottom = contentPadding.calculateBottomPadding() + 8.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(
                    icon = Icons.Rounded.LibraryMusic,
                    title = pluralStringResource(R.plurals.song_count_plural, songs.size, songs.size),
                    modifier = Modifier.padding(horizontal = 0.dp, vertical = 10.dp),
                )
            }
            itemsIndexed(songs) { index, song ->
                SongGridCard(
                    song = song,
                    selected = song.videoId in selectedIds,
                    isCurrent = song.isCurrentLocal(currentSong),
                    onClick = { onSongClick(songs, index) },
                    onLongPress = { onSongLongPress(song) },
                )
            }
        }
    } else {
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            item {
                SectionHeader(
                    icon = Icons.Rounded.LibraryMusic,
                    title = pluralStringResource(R.plurals.song_count_plural, songs.size, songs.size),
                )
            }
            itemsIndexed(songs) { index, song ->
                SongRow(
                    song = song,
                    selected = song.videoId in selectedIds,
                    isCurrent = song.isCurrentLocal(currentSong),
                    isPlaying = song.isCurrentLocal(currentSong) && isPlaying,
                    onClick = { onSongClick(songs, index) },
                    onLongPress = { onSongLongPress(song) },
                    onSwipeToQueue = { onSongSwipe(song) },
                )
                if (index < songs.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = ROW_DIVIDER_INSET),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongGridCard(
    song: Song,
    selected: Boolean = false,
    isCurrent: Boolean = false,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(4.dp),
    ) {
        val shape = RoundedCornerShape(12.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .thumbnailBorder(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp),
            )
            if (song.thumbnailUrl != null) {
                AsyncImage(
                    model = song.thumbnailUrl.artworkAt(CARD_ART_PX),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (isCurrent) {
                Icon(
                    Icons.Rounded.GraphicEq,
                    contentDescription = stringResource(R.string.now_playing),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        ExplicitSongTitle(
            song = song,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = song.artist.ifBlank { stringResource(R.string.unknown_artist) },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── Artists tab ───────────────────────────────────────────────────────────────

@Composable
private fun ArtistsTab(
    artists: List<Map.Entry<String, List<Song>>>,
    viewType: LibraryViewType,
    onArtistClick: (String, List<Song>) -> Unit,
    onArtistLongPress: ((String, List<Song>) -> Unit)?,
    contentPadding: PaddingValues,
) {
    if (viewType == LibraryViewType.GRID) {
        val gridState = rememberLazyGridState()
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 130.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = PAGE_GUTTER,
                end = PAGE_GUTTER,
                top = 6.dp,
                bottom = contentPadding.calculateBottomPadding() + 8.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(
                    icon = Icons.Rounded.Person,
                    title = pluralStringResource(R.plurals.artist_count, artists.size, artists.size),
                    modifier = Modifier.padding(horizontal = 0.dp, vertical = 10.dp),
                )
            }
            items(artists, key = { it.key }) { (artist, artistSongs) ->
                ArtistGridCard(
                    name = artist,
                    songCount = artistSongs.size,
                    thumbnailUrl = artistSongs.firstNotNullOfOrNull { it.thumbnailUrl },
                    onClick = { onArtistClick(artist, artistSongs) },
                    onLongPress = onArtistLongPress?.let { { it(artist, artistSongs) } },
                )
            }
        }
    } else {
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            item {
                SectionHeader(
                    icon = Icons.Rounded.Person,
                    title = pluralStringResource(R.plurals.artist_count, artists.size, artists.size),
                )
            }
            items(artists, key = { it.key }) { (artist, artistSongs) ->
                ArtistRow(
                    name = artist,
                    songCount = artistSongs.size,
                    thumbnailUrl = artistSongs.firstNotNullOfOrNull { it.thumbnailUrl },
                    onClick = { onArtistClick(artist, artistSongs) },
                    onLongPress = onArtistLongPress?.let { { it(artist, artistSongs) } },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = ROW_DIVIDER_INSET),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtistRow(
    name: String,
    songCount: Int,
    thumbnailUrl: String? = null,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = PAGE_GUTTER, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar circle
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(26.dp),
            )
            if (thumbnailUrl != null) {
                AsyncImage(
                    model = thumbnailUrl.artworkAt(ROW_ART_PX),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .thumbnailBorder(CircleShape),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(R.plurals.song_count_plural, songCount, songCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtistGridCard(
    name: String,
    songCount: Int,
    thumbnailUrl: String?,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .aspectRatio(1f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(44.dp),
            )
            if (thumbnailUrl != null) {
                AsyncImage(
                    model = thumbnailUrl.artworkAt(CARD_ART_PX),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .thumbnailBorder(CircleShape),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Text(
            text = pluralStringResource(R.plurals.song_count_plural, songCount, songCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

// ── Albums tab ────────────────────────────────────────────────────────────────

/**
 * One row of the Albums tab, whichever of the two things it came from.
 *
 * The tab used to be a `Map.Entry<String, List<Song>>` straight off a `groupBy`,
 * which was exactly as much as a tag grouping can say. A downloaded release
 * knows three more things — its own cover, whether it is a playlist rather than
 * an album, and the order its tracks go in — and none of those has anywhere to
 * live in a map entry.
 */
private class AlbumEntry(
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    /** Billed as a playlist rather than by artist; see [AlbumRow]. */
    val playlist: Boolean,
    /** Kept in the order it was downloaded in, which is the release's own. */
    val songs: List<Song>,
    /** Whether this is a release the user asked for, or a grouping inferred. */
    val asked: Boolean,
    /**
     * What the list keys this row by — the release's own id where it has one.
     *
     * Not the title: an album and a playlist can be called the same thing (a
     * self-titled record and its "This is …" mix, say), and two rows sharing a
     * key is a crash out of `LazyColumn` rather than a cosmetic clash.
     */
    val key: String,
)

/**
 * The Albums tab's rows: the releases downloaded whole, then whatever else the
 * files' own album tags group up.
 *
 * The two are merged rather than shown as separate sections because they are the
 * same kind of thing to whoever is looking for one — a folder of songs with a
 * name they remember. What matters is only that the *named* ones win a collision:
 * an album downloaded whole also stamps its name onto each of its tracks (see
 * `withAlbum` in MainActivity), so without this every one of them would appear
 * twice, once with its cover and once without.
 *
 * Releases lead within their own alphabetical run rather than being sorted
 * together, because a tag grouping is a guess and a recorded release is not.
 */
private fun albumEntries(
    songs: List<Song>,
    collections: List<DownloadedCollection>,
): List<AlbumEntry> {
    val asked = collections.map { collection ->
        AlbumEntry(
            title = collection.title,
            artist = collection.subtitle.ifBlank {
                collection.songs.firstOrNull()?.artist.orEmpty()
            },
            thumbnailUrl = collection.thumbnailUrl,
            playlist = collection.playlist,
            songs = collection.songs,
            asked = true,
            key = "asked:${collection.id}",
        )
    }
    val claimed = asked.mapTo(HashSet()) { it.title.lowercase(Locale.ROOT) }
    val derived = songs
        .groupBy { it.albumName }
        .mapNotNull { (name, group) ->
            // Null is every track that never said what release it was off, and
            // there is no row to draw for "no album" — those are the Songs tab's
            // and nothing else's.
            if (name == null || name.lowercase(Locale.ROOT) in claimed) return@mapNotNull null
            AlbumEntry(
                title = name,
                artist = group.firstOrNull()?.artist.orEmpty(),
                thumbnailUrl = group.firstNotNullOfOrNull { it.thumbnailUrl },
                playlist = false,
                songs = group,
                asked = false,
                key = "tagged:$name",
            )
        }
    return (asked + derived).sortedWith(
        compareByDescending<AlbumEntry> { it.asked }.thenBy { it.title.lowercase(Locale.ROOT) },
    )
}

@Composable
private fun AlbumsTab(
    albums: List<AlbumEntry>,
    viewType: LibraryViewType,
    selectedKeys: Set<String> = emptySet(),
    onAlbumClick: (AlbumEntry) -> Unit,
    onAlbumLongPress: ((AlbumEntry) -> Unit)?,
    contentPadding: PaddingValues,
) {
    if (viewType == LibraryViewType.GRID) {
        val gridState = rememberLazyGridState()
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = PAGE_GUTTER,
                end = PAGE_GUTTER,
                top = 6.dp,
                bottom = contentPadding.calculateBottomPadding() + 8.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(
                    icon = Icons.Rounded.Album,
                    title = pluralStringResource(R.plurals.album_count, albums.size, albums.size),
                    modifier = Modifier.padding(horizontal = 0.dp, vertical = 10.dp),
                )
            }
            if (albums.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    MessageState(
                        message = stringResource(R.string.no_local_albums),
                    )
                }
            }
            items(albums, key = { it.key }) { entry ->
                AlbumGridCard(
                    entry = entry,
                    selected = entry.key in selectedKeys,
                    onClick = { onAlbumClick(entry) },
                    onLongPress = onAlbumLongPress?.let { { it(entry) } },
                )
            }
        }
    } else {
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            item {
                SectionHeader(
                    icon = Icons.Rounded.Album,
                    title = pluralStringResource(R.plurals.album_count, albums.size, albums.size),
                )
            }
            if (albums.isEmpty()) {
                item {
                    MessageState(
                        message = stringResource(R.string.no_local_albums),
                    )
                }
            }
            items(albums, key = { it.key }) { entry ->
                AlbumRow(
                    entry = entry,
                    selected = entry.key in selectedKeys,
                    onClick = { onAlbumClick(entry) },
                    onLongPress = onAlbumLongPress?.let { { it(entry) } },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = ROW_DIVIDER_INSET),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumGridCard(
    entry: AlbumEntry,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(4.dp),
    ) {
        val shape = RoundedCornerShape(12.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .thumbnailBorder(shape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (entry.playlist) Icons.AutoMirrored.Rounded.QueueMusic else Icons.Rounded.Album,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(36.dp),
            )
            if (entry.thumbnailUrl != null) {
                AsyncImage(
                    model = entry.thumbnailUrl.artworkAt(CARD_ART_PX),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = entry.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val songCount = pluralStringResource(
            R.plurals.song_count_plural,
            entry.songs.size,
            entry.songs.size,
        )
        val playlistLabel = stringResource(R.string.playlist)
        Text(
            text = buildString {
                if (entry.playlist) {
                    append(playlistLabel)
                    append(" · ")
                } else if (entry.artist.isNotBlank() && entry.artist != entry.title) {
                    append("${entry.artist} · ")
                }
                append(songCount)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumRow(
    entry: AlbumEntry,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent, RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = PAGE_GUTTER, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CollectionArtwork(
            url = entry.thumbnailUrl,
            playlist = entry.playlist,
            size = 48.dp,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val songCount = pluralStringResource(
                R.plurals.song_count_plural,
                entry.songs.size,
                entry.songs.size,
            )
            val playlistLabel = stringResource(R.string.playlist)
            Text(
                text = buildString {
                    // A playlist's tracks are off forty different releases, so
                    // the first one's artist is not a credit for it — the kind
                    // of thing it is says more, and is true.
                    if (entry.playlist) {
                        append(playlistLabel)
                        append(" · ")
                    } else if (entry.artist.isNotBlank() && entry.artist != entry.title) {
                        append("${entry.artist} · ")
                    }
                    append(songCount)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.PlayArrow,
            contentDescription = if (selected) stringResource(R.string.selected) else null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * A release's cover, with a glyph standing in when there isn't one.
 *
 * The placeholder is not a fallback so much as the common case for anything
 * grouped off tags: those files' artwork is whatever the media scanner extracted,
 * which for a `.m4a` this app wrote is frequently nothing at all. Drawn behind
 * the image rather than instead of it, so a cover that loads late replaces the
 * glyph without the row changing size under it.
 */
@Composable
private fun CollectionArtwork(url: String?, playlist: Boolean, size: Dp) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (playlist) Icons.AutoMirrored.Rounded.QueueMusic else Icons.Rounded.Album,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(size * 0.54f),
        )
        if (url != null) {
            AsyncImage(
                model = url.artworkAt(ROW_ART_PX),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(shape)
                    .thumbnailBorder(shape),
            )
        }
    }
}

// ── Drill-down song list ───────────────────────────────────────────────────────

@Composable
private fun DrillDownHeader(
    label: String,
    artworkUrl: String?,
    songs: List<Song>,
    onBack: () -> Unit,
    onMore: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = PAGE_GUTTER, top = 6.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.ArrowBack, stringResource(R.string.back), tint = MaterialTheme.colorScheme.onBackground)
            }
            Spacer(Modifier.weight(1f))
            onMore?.let { more ->
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = more),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.MoreHoriz, stringResource(R.string.more), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Box(
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp).size(188.dp)
                .clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (artworkUrl == null) Icons.Rounded.Person else Icons.Rounded.Album,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(64.dp),
            )
            artworkUrl?.let { artwork ->
                AsyncImage(
                    model = artwork.artworkAt(CARD_ART_PX),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = PAGE_GUTTER),
        )
        Text(
            text = buildString {
                if (artworkUrl == null) append("Artist").append(" · ")
                append(pluralStringResource(R.plurals.track_count_plural, songs.size, songs.size))
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DrillDownActionRow(
    songs: List<Song>,
    onSongClick: (List<Song>, Int) -> Unit,
    onShuffle: (List<Song>) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_GUTTER, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                .border(0.5.dp, Color.White.copy(alpha = 0.10f), CircleShape)
                .clickable { if (songs.isNotEmpty()) onShuffle(songs) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                BitChordIcons.Shuffle,
                contentDescription = stringResource(R.string.shuffle),
                tint = if (isSystemInDarkTheme()) Color.White else Color.Black,
                modifier = Modifier.size(18.dp),
            )
        }
        Row(
            modifier = Modifier
                .height(50.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                .border(0.5.dp, Color.White.copy(alpha = 0.10f), CircleShape)
                .clickable { if (songs.isNotEmpty()) onSongClick(songs, 0) }
                .padding(horizontal = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                BitChordIcons.Play,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.play),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun DrillDownSongList(
    label: String,
    /** The release's cover, where it has one — see [CollectionArtwork]. */
    artworkUrl: String?,
    songs: List<Song>,
    viewType: LibraryViewType,
    selectedIds: Set<String> = emptySet(),
    currentSong: Song? = null,
    isPlaying: Boolean = false,
    onSongClick: (List<Song>, Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onSongSwipe: (Song) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onMore: (() -> Unit)?,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
) {
    if (viewType == LibraryViewType.GRID) {
        val gridState = rememberLazyGridState()
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = PAGE_GUTTER,
                end = PAGE_GUTTER,
                top = 0.dp,
                bottom = contentPadding.calculateBottomPadding() + 8.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                DrillDownHeader(
                    label = label,
                    artworkUrl = artworkUrl,
                    songs = songs,
                    onBack = onBack,
                    onMore = onMore,
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                DrillDownActionRow(
                    songs = songs,
                    onSongClick = onSongClick,
                    onShuffle = onShuffle,
                )
            }
            itemsIndexed(songs) { index, song ->
                SongGridCard(
                    song = song,
                    selected = song.videoId in selectedIds,
                    isCurrent = song.isCurrentLocal(currentSong),
                    onClick = { onSongClick(songs, index) },
                    onLongPress = { onSongLongPress(song) },
                )
            }
        }
    } else {
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            item {
                DrillDownHeader(
                    label = label,
                    artworkUrl = artworkUrl,
                    songs = songs,
                    onBack = onBack,
                    onMore = onMore,
                )
            }
            item {
                DrillDownActionRow(
                    songs = songs,
                    onSongClick = onSongClick,
                    onShuffle = onShuffle,
                )
            }
            itemsIndexed(songs) { index, song ->
                SongRow(
                    song = song,
                    selected = song.videoId in selectedIds,
                    isCurrent = song.isCurrentLocal(currentSong),
                    isPlaying = song.isCurrentLocal(currentSong) && isPlaying,
                    trackNumber = index + 1,
                    onClick = { onSongClick(songs, index) },
                    onLongPress = { onSongLongPress(song) },
                    onSwipeToQueue = { onSongSwipe(song) },
                )
                if (index < songs.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = ROW_DIVIDER_INSET),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

// ── Shared helpers ────────────────────────────────────────────────────────────

/** Whether this track is a hit for a query typed into [LocalSearchField]. */
private fun Song.matchesSearch(query: String): Boolean =
    title.contains(query, ignoreCase = true) ||
        artist.contains(query, ignoreCase = true) ||
        albumName?.contains(query, ignoreCase = true) == true

private fun List<Song>.sortedForLibrary(order: LocalMusicSort): List<Song> = when (order) {
    LocalMusicSort.TITLE_ASC -> sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    LocalMusicSort.TITLE_DESC -> sortedWith(compareByDescending<Song> { it.title.lowercase(Locale.ROOT) })
    LocalMusicSort.DATE_ADDED -> sortedWith(
        compareByDescending<Song> { it.localDateAddedSeconds ?: Long.MIN_VALUE }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
    )
    LocalMusicSort.DATE_MODIFIED -> sortedWith(
        compareByDescending<Song> { it.localDateModifiedSeconds ?: Long.MIN_VALUE }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
    )
}

/**
 * The filter box above the tab row.
 *
 * Live rather than submit-on-enter: there is no network round trip behind it,
 * only a list already in memory, so narrowing it on every keystroke costs
 * nothing and a submit action would just be a tap this screen doesn't need.
 */
@Composable
private fun LocalSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    sortOrder: LocalMusicSort,
    onSortOrderChange: (LocalMusicSort) -> Unit,
    viewType: LibraryViewType,
    onViewTypeToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sortMenuOpen by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(11.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.search_this_folder),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable { onQueryChange("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.clear_search),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable {
                    haptics.play(Haptic.Select)
                    onViewTypeToggle()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (viewType == LibraryViewType.GRID) BitChordIcons.ListView else BitChordIcons.GridView,
                contentDescription = stringResource(
                    if (viewType == LibraryViewType.GRID) R.string.switch_to_list_view else R.string.switch_to_grid_view,
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(2.dp))
        Box {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable { sortMenuOpen = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Sort,
                    contentDescription = stringResource(R.string.sort_music),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp),
                )
            }
            DropdownMenu(
                expanded = sortMenuOpen,
                onDismissRequest = { sortMenuOpen = false },
            ) {
                LocalMusicSort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.localizedLabel()) },
                        trailingIcon = if (option == sortOrder) {
                            {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = stringResource(R.string.selected),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        } else {
                            null
                        },
                        onClick = {
                            onSortOrderChange(option)
                            sortMenuOpen = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LocalMusicSort.localizedLabel(): String = when (this) {
    LocalMusicSort.TITLE_ASC -> stringResource(R.string.sort_title_ascending)
    LocalMusicSort.TITLE_DESC -> stringResource(R.string.sort_title_descending)
    LocalMusicSort.DATE_ADDED -> stringResource(R.string.sort_date_added)
    LocalMusicSort.DATE_MODIFIED -> stringResource(R.string.sort_date_modified)
}

/** Local/downloaded rows have a stable URI; ordinary catalogue rows fall back to video id. */
private fun Song.isCurrentLocal(current: Song?): Boolean {
    current ?: return false
    if (localUri != null && current.localUri != null) return localUri == current.localUri
    return videoId == current.videoId
}

/** The Downloads-only replacement for the usual search and sort controls. */
@Composable
private fun DownloadSelectionBar(
    count: Int,
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(11.dp))
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onSelectAll, enabled = !allSelected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = stringResource(R.string.select_all),
                tint = if (isSystemInDarkTheme()) Color.White else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = "$count ${stringResource(R.string.selected)}",
            style = MaterialTheme.typography.titleSmall,
            color = if (isSystemInDarkTheme()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDelete, enabled = count > 0) {
            Icon(
                Icons.Rounded.Delete,
                contentDescription = null,
                tint = Color(0xFFFF453A),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.delete), color = Color(0xFFFF453A))
        }
        TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
    }
}

@Composable
private fun LocalTab(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // Fired here rather than at each of the three call sites, so Songs, Artists
    // and Albums can't drift apart — and the Downloads folder, which is this
    // same screen, gets it for free. Silent on the tab that's already showing.
    val haptics = rememberHaptics()
    Tab(
        selected = selected,
        onClick = {
            if (!selected) haptics.play(Haptic.Select)
            onClick()
        },
        selectedContentColor = MaterialTheme.colorScheme.primary,
        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier.padding(horizontal = PAGE_GUTTER, vertical = 14.dp),
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
