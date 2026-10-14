package com.music.bitchord.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.music.bitchord.ui.components.longPressMenuClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
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
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.bitchord.data.LocalMediaRepository
import com.music.bitchord.data.canvas.AppleArtistArtRepository
import com.music.bitchord.data.model.CARD_ART_PX
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.Song
import com.music.bitchord.R
import com.music.bitchord.ui.components.ExplicitSongTitle
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.model.isSameTrackAs
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.LibraryViewType
import com.music.bitchord.data.settings.LocalMusicSort
import com.music.bitchord.download.DownloadedCollection
import com.music.bitchord.ui.components.MessageState
import com.music.bitchord.ui.components.PlayingAccent
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.hangIntoGutter
import com.music.bitchord.ui.components.NUMBERED_ROW_DIVIDER_INSET
import com.music.bitchord.ui.components.ROW_DIVIDER_INSET
import com.music.bitchord.ui.components.SongRow
import com.music.bitchord.ui.components.SearchPlayingBars
import com.music.bitchord.ui.components.rememberRemoteArtworkUrl
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
    isIndexing: Boolean = false,
    indexedCount: Int = 0,
    onRefresh: (() -> Unit)? = null,
    /** Deletes the Downloads rows selected through this screen's long-press mode. */
    onDeleteDownloads: ((List<Song>) -> Unit)? = null,
    /** Copies the selected Downloads rows to the WebDAV server; null hides the action. */
    onUploadToWebDav: ((List<Song>) -> Unit)? = null,
    onOpenOnlineArtist: ((String) -> Unit)? = null,
    onOpenOnlineAlbum: ((String, String) -> Unit)? = null,
    /** Opens an album in the full release DetailScreen view. */
    onOpenAlbum: ((AlbumEntry) -> Unit)? = null,
    /** Opens an artist in the full release DetailScreen view. */
    onOpenArtist: ((String, List<Song>) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // Which top-level tab is selected.
    var selectedTab by rememberSaveable { mutableIntStateOf(LOCAL_TAB_SONGS) }

    // Scroll states for each tab, hoisted and initialized from cache so scroll positions are never lost when navigating into drill-downs or detail pages
    val songsListState = rememberLazyListState(
        initialFirstVisibleItemIndex = if (isDownloads) LocalMusicDerivedCache.lastDlSongIndex else LocalMusicDerivedCache.lastSongIndex,
        initialFirstVisibleItemScrollOffset = if (isDownloads) LocalMusicDerivedCache.lastDlSongOffset else LocalMusicDerivedCache.lastSongOffset,
    )
    val songsGridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = if (isDownloads) LocalMusicDerivedCache.lastDlSongGridIndex else LocalMusicDerivedCache.lastSongGridIndex,
        initialFirstVisibleItemScrollOffset = if (isDownloads) LocalMusicDerivedCache.lastDlSongGridOffset else LocalMusicDerivedCache.lastSongGridOffset,
    )
    val artistsListState = rememberLazyListState(
        initialFirstVisibleItemIndex = if (isDownloads) LocalMusicDerivedCache.lastDlArtistIndex else LocalMusicDerivedCache.lastArtistIndex,
        initialFirstVisibleItemScrollOffset = if (isDownloads) LocalMusicDerivedCache.lastDlArtistOffset else LocalMusicDerivedCache.lastArtistOffset,
    )
    val artistsGridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = if (isDownloads) LocalMusicDerivedCache.lastDlArtistGridIndex else LocalMusicDerivedCache.lastArtistGridIndex,
        initialFirstVisibleItemScrollOffset = if (isDownloads) LocalMusicDerivedCache.lastDlArtistGridOffset else LocalMusicDerivedCache.lastArtistGridOffset,
    )
    val albumsListState = rememberLazyListState(
        initialFirstVisibleItemIndex = if (isDownloads) LocalMusicDerivedCache.lastDlAlbumIndex else LocalMusicDerivedCache.lastAlbumIndex,
        initialFirstVisibleItemScrollOffset = if (isDownloads) LocalMusicDerivedCache.lastDlAlbumOffset else LocalMusicDerivedCache.lastAlbumOffset,
    )
    val albumsGridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = if (isDownloads) LocalMusicDerivedCache.lastDlAlbumGridIndex else LocalMusicDerivedCache.lastAlbumGridIndex,
        initialFirstVisibleItemScrollOffset = if (isDownloads) LocalMusicDerivedCache.lastDlAlbumGridOffset else LocalMusicDerivedCache.lastAlbumGridOffset,
    )

    LaunchedEffect(songsListState) {
        snapshotFlow { songsListState.firstVisibleItemIndex to songsListState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (isDownloads) {
                    LocalMusicDerivedCache.lastDlSongIndex = index
                    LocalMusicDerivedCache.lastDlSongOffset = offset
                } else {
                    LocalMusicDerivedCache.lastSongIndex = index
                    LocalMusicDerivedCache.lastSongOffset = offset
                }
            }
    }
    LaunchedEffect(songsGridState) {
        snapshotFlow { songsGridState.firstVisibleItemIndex to songsGridState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (isDownloads) {
                    LocalMusicDerivedCache.lastDlSongGridIndex = index
                    LocalMusicDerivedCache.lastDlSongGridOffset = offset
                } else {
                    LocalMusicDerivedCache.lastSongGridIndex = index
                    LocalMusicDerivedCache.lastSongGridOffset = offset
                }
            }
    }
    LaunchedEffect(artistsListState) {
        snapshotFlow { artistsListState.firstVisibleItemIndex to artistsListState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (isDownloads) {
                    LocalMusicDerivedCache.lastDlArtistIndex = index
                    LocalMusicDerivedCache.lastDlArtistOffset = offset
                } else {
                    LocalMusicDerivedCache.lastArtistIndex = index
                    LocalMusicDerivedCache.lastArtistOffset = offset
                }
            }
    }
    LaunchedEffect(artistsGridState) {
        snapshotFlow { artistsGridState.firstVisibleItemIndex to artistsGridState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (isDownloads) {
                    LocalMusicDerivedCache.lastDlArtistGridIndex = index
                    LocalMusicDerivedCache.lastDlArtistGridOffset = offset
                } else {
                    LocalMusicDerivedCache.lastArtistGridIndex = index
                    LocalMusicDerivedCache.lastArtistGridOffset = offset
                }
            }
    }
    LaunchedEffect(albumsListState) {
        snapshotFlow { albumsListState.firstVisibleItemIndex to albumsListState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (isDownloads) {
                    LocalMusicDerivedCache.lastDlAlbumIndex = index
                    LocalMusicDerivedCache.lastDlAlbumOffset = offset
                } else {
                    LocalMusicDerivedCache.lastAlbumIndex = index
                    LocalMusicDerivedCache.lastAlbumOffset = offset
                }
            }
    }
    LaunchedEffect(albumsGridState) {
        snapshotFlow { albumsGridState.firstVisibleItemIndex to albumsGridState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (isDownloads) {
                    LocalMusicDerivedCache.lastDlAlbumGridIndex = index
                    LocalMusicDerivedCache.lastDlAlbumGridOffset = offset
                } else {
                    LocalMusicDerivedCache.lastAlbumGridIndex = index
                    LocalMusicDerivedCache.lastAlbumGridOffset = offset
                }
            }
    }

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
    val noLocalAlbums by AppSettings.noLocalAlbums.collectAsStateWithLifecycle()

    var prevSortOrder by rememberSaveable { mutableStateOf<LocalMusicSort?>(null) }
    LaunchedEffect(sortOrder) {
        if (prevSortOrder != null && prevSortOrder != sortOrder) {
            LocalMusicDerivedCache.resetScroll(isDownloads)
            songsListState.scrollToItem(0)
            songsGridState.scrollToItem(0)
            artistsListState.scrollToItem(0)
            artistsGridState.scrollToItem(0)
            albumsListState.scrollToItem(0)
            albumsGridState.scrollToItem(0)
        }
        prevSortOrder = sortOrder
    }

    var prevSearchQuery by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(searchQuery) {
        if (prevSearchQuery != searchQuery && searchQuery.isNotBlank()) {
            LocalMusicDerivedCache.resetScroll(isDownloads)
            songsListState.scrollToItem(0)
            songsGridState.scrollToItem(0)
            artistsListState.scrollToItem(0)
            artistsGridState.scrollToItem(0)
            albumsListState.scrollToItem(0)
            albumsGridState.scrollToItem(0)
        }
        prevSearchQuery = searchQuery
    }

    var sortedSongs by remember(songs, sortOrder) {
        mutableStateOf(if (songs.size <= 200) songs.sortedForLibrary(sortOrder) else songs)
    }
    LaunchedEffect(songs, sortOrder) {
        if (songs.size > 200) {
            val sorted = withContext(Dispatchers.Default) {
                songs.sortedForLibrary(sortOrder)
            }
            sortedSongs = sorted
        }
    }

    var filteredSongs by remember(sortedSongs, searchQuery) {
        mutableStateOf(
            if (searchQuery.isBlank()) sortedSongs
            else if (sortedSongs.size <= 200) sortedSongs.filter { it.matchesSearch(searchQuery) }
            else emptyList()
        )
    }
    LaunchedEffect(sortedSongs, searchQuery) {
        if (searchQuery.isNotBlank() && sortedSongs.size > 200) {
            val filtered = withContext(Dispatchers.Default) {
                sortedSongs.filter { it.matchesSearch(searchQuery) }
            }
            filteredSongs = filtered
        } else if (searchQuery.isBlank()) {
            filteredSongs = sortedSongs
        }
    }

    var artists by remember(sortedSongs, sortOrder, searchQuery) {
        mutableStateOf(
            LocalMusicDerivedCache.getArtists(sortedSongs, sortOrder).let { list ->
                if (searchQuery.isBlank()) list
                else list.filter { it.key.contains(searchQuery, ignoreCase = true) }
            }
        )
    }
    LaunchedEffect(sortedSongs, sortOrder, searchQuery) {
        if (sortedSongs.size > 200) {
            val result = withContext(Dispatchers.Default) {
                LocalMusicDerivedCache.getArtists(sortedSongs, sortOrder).let { list ->
                    if (searchQuery.isBlank()) list
                    else list.filter { it.key.contains(searchQuery, ignoreCase = true) }
                }
            }
            artists = result
        }
    }

    var albums by remember(sortedSongs, collections, sortOrder, searchQuery, noLocalAlbums) {
        mutableStateOf(
            LocalMusicDerivedCache.getAlbums(sortedSongs, collections, sortOrder, isDownloads = isDownloads, noLocalAlbums = noLocalAlbums).let { list ->
                if (searchQuery.isBlank()) list
                else list.filter {
                    it.title.contains(searchQuery, ignoreCase = true) ||
                        it.artist.contains(searchQuery, ignoreCase = true)
                }
            }
        )
    }
    LaunchedEffect(sortedSongs, collections, sortOrder, searchQuery, noLocalAlbums) {
        if (sortedSongs.size > 200) {
            val result = withContext(Dispatchers.Default) {
                LocalMusicDerivedCache.getAlbums(sortedSongs, collections, sortOrder, isDownloads = isDownloads, noLocalAlbums = noLocalAlbums).let { list ->
                    if (searchQuery.isBlank()) list
                    else list.filter {
                        it.title.contains(searchQuery, ignoreCase = true) ||
                            it.artist.contains(searchQuery, ignoreCase = true)
                    }
                }
            }
            albums = result
        }
    }
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
    var drillDownIsArtist by remember { mutableStateOf(false) }
    // The release's own cover, for the drill-down header. Only a downloaded
    // album or playlist has one worth showing — a tag-derived grouping's
    // "artwork" is just whichever of its rows happened to be first.
    var drillDownArt by remember { mutableStateOf<String?>(null) }

    val inDrillDown = drillDownLabel != null

    val leaveDrillDown = {
        drillDownLabel = null
        drillDownSongs = emptyList()
        drillDownArt = null
        drillDownIsArtist = false
    }

    LaunchedEffect(songs) {
        val label = drillDownLabel ?: return@LaunchedEffect
        if (drillDownIsArtist) {
            val updated = songs.filter { com.music.bitchord.data.LocalMediaRepository.splitArtists(it.artist).any { a: String -> a.equals(label, ignoreCase = true) } }
            if (updated.isNotEmpty()) drillDownSongs = updated
        } else {
            val updated = songs.filter { it.albumName?.equals(label, ignoreCase = true) == true }
            if (updated.isNotEmpty()) {
                drillDownSongs = updated
                val newArt = updated.firstOrNull { it.thumbnailUrl?.startsWith("audio:") == false }?.thumbnailUrl
                    ?: updated.firstNotNullOfOrNull { it.thumbnailUrl }
                if (newArt != null) drillDownArt = newArt
            }
        }
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
                onUpload = onUploadToWebDav?.let { upload ->
                    {
                        val chosen = songs.filter { it.videoId in selectedDownloadIds }
                        selectedDownloadIds = emptySet()
                        selectedAlbumKeys = emptySet()
                        upload(chosen)
                    }
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
            isDownloads = isDownloads,
            onRefresh = onRefresh,
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

        if (!isDownloads) {
            AnimatedVisibility(
                visible = isIndexing,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PAGE_GUTTER, vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (indexedCount > 0)
                            stringResource(R.string.indexing_library_progress, indexedCount)
                        else
                            stringResource(R.string.indexing_library_searching),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

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
                sortedSongs.isEmpty() && !isIndexing && emptyMessage != null -> {
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
                        isArtist = drillDownIsArtist,
                        onOpenOnlineArtist = onOpenOnlineArtist,
                        onOpenOnlineAlbum = onOpenOnlineAlbum,
                        onOpenAlbum = onOpenAlbum,
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
                        // Downloads redefines the hold as "select", so the ⋮ has to
                        // carry the actions sheet itself or the page loses it.
                        onSongMore = onSongLongPress,
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
                    SongsTab(
                        songs = filteredSongs,
                        viewType = viewType,
                        listState = songsListState,
                        gridState = songsGridState,
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
                        // Downloads redefines the hold as "select", so the ⋮ has to
                        // carry the actions sheet itself or the page loses it.
                        onSongMore = onSongLongPress,
                        onSongSwipe = onSongSwipe,
                        onShuffle = onShuffle,
                        contentPadding = bodyContentPadding,
                    )
                }

                key == "tab:$LOCAL_TAB_ARTISTS" -> {
                    ArtistsTab(
                        artists = artists,
                        viewType = viewType,
                        listState = artistsListState,
                        gridState = artistsGridState,
                        onArtistClick = { artist, artistSongs ->
                            if (onOpenArtist != null) {
                                onOpenArtist(artist, artistSongs)
                            } else {
                                drillDownLabel = artist
                                drillDownSongs = artistSongs
                                drillDownArt = null
                                drillDownIsArtist = true
                            }
                        },
                        onArtistLongPress = onCollectionLongPress,
                        contentPadding = bodyContentPadding,
                    )
                }

                else -> {
                    // LOCAL_TAB_ALBUMS
                    AlbumsTab(
                        albums = albums,
                        viewType = viewType,
                        listState = albumsListState,
                        gridState = albumsGridState,
                        selectedKeys = selectedAlbumKeys,
                        onAlbumClick = { entry ->
                            if (selectingDownloads) {
                                toggleAlbumSelection(entry)
                            } else {
                                if (onOpenAlbum != null) {
                                    onOpenAlbum(entry)
                                } else {
                                    drillDownLabel = entry.title
                                    drillDownSongs = entry.songs
                                    drillDownArt = entry.thumbnailUrl
                                    drillDownIsArtist = false
                                }
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

private fun groupSongsBySplitArtists(songs: List<Song>): Map<String, List<Song>> {
    val map = LinkedHashMap<String, MutableList<Song>>()
    for (song in songs) {
        val artists = LocalMediaRepository.splitArtists(song.artist)
        if (artists.isEmpty()) {
            map.getOrPut(song.artist.ifBlank { "Unknown Artist" }) { mutableListOf() }.add(song)
        } else {
            for (artist in artists) {
                map.getOrPut(artist) { mutableListOf() }.add(song)
            }
        }
    }
    return map
}

// ── Songs tab ─────────────────────────────────────────────────────────────────

@Composable
private fun SongsTab(
    songs: List<Song>,
    viewType: LibraryViewType,
    listState: LazyListState = rememberLazyListState(),
    gridState: LazyGridState = rememberLazyGridState(),
    selectedIds: Set<String> = emptySet(),
    currentSong: Song? = null,
    isPlaying: Boolean = false,
    onSongClick: (List<Song>, Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
    /** The row's ⋮, where holding it does something else — see [SongRow]. */
    onSongMore: ((Song) -> Unit)? = null,
    onSongSwipe: (Song) -> Unit,
    onShuffle: ((List<Song>) -> Unit)? = null,
    contentPadding: PaddingValues,
) {
    if (viewType == LibraryViewType.GRID) {
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LibraryMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = pluralStringResource(R.plurals.song_count_plural, songs.size, songs.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (onShuffle != null && songs.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .clickable { onShuffle(songs) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = BitChordIcons.Shuffle,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(R.string.shuffle),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            itemsIndexed(
                items = songs,
                key = { _, song -> song.localUri ?: song.localPath ?: song.videoId },
                contentType = { _, _ -> "song_grid" },
            ) { index, song ->
                SongGridCard(
                    song = song,
                    selected = song.videoId in selectedIds,
                    isCurrent = song.isSameTrackAs(currentSong),
                    isPlaying = song.isSameTrackAs(currentSong) && isPlaying,
                    searchPlayingStyle = true,
                    onClick = { onSongClick(songs, index) },
                    onLongPress = { onSongLongPress(song) },
                )
            }
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PAGE_GUTTER, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LibraryMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = pluralStringResource(R.plurals.song_count_plural, songs.size, songs.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (onShuffle != null && songs.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .clickable { onShuffle(songs) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = BitChordIcons.Shuffle,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(R.string.shuffle),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            itemsIndexed(
                items = songs,
                key = { _, song -> song.localUri ?: song.localPath ?: song.videoId },
                contentType = { _, _ -> "song_row" },
            ) { index, song ->
                SongRow(
                    song = song,
                    selected = song.videoId in selectedIds,
                    isCurrent = song.isSameTrackAs(currentSong),
                    isPlaying = song.isSameTrackAs(currentSong) && isPlaying,
                    searchPlayingStyle = true,
                    activeTint = PlayingAccent,
                    downloadedTint = null,
                    onClick = { onSongClick(songs, index) },
                    onLongPress = { onSongLongPress(song) },
                    onMore = onSongMore?.let { more -> { more(song) } },
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
    isPlaying: Boolean = false,
    searchPlayingStyle: Boolean = false,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
            .longPressMenuClickable(onClick = onClick, onLongClick = onLongPress)
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
            val resolvedArt = song.thumbnailUrl
            if (resolvedArt != null) {
                AsyncImage(
                    model = resolvedArt.artworkAt(CARD_ART_PX),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (isCurrent && searchPlayingStyle && isPlaying) {
                SearchPlayingBars(Modifier.align(Alignment.Center))
            } else if (isCurrent) {
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
            color = if (isCurrent && searchPlayingStyle) PlayingAccent else MaterialTheme.colorScheme.onBackground,
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
    listState: LazyListState = rememberLazyListState(),
    gridState: LazyGridState = rememberLazyGridState(),
    onArtistClick: (String, List<Song>) -> Unit,
    onArtistLongPress: ((String, List<Song>) -> Unit)?,
    contentPadding: PaddingValues,
) {
    if (viewType == LibraryViewType.GRID) {
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
    var artistArtUrl by remember(name) {
        mutableStateOf(AppleArtistArtRepository.cached(name)?.heroUrl ?: thumbnailUrl)
    }
    LaunchedEffect(name) {
        val art = AppleArtistArtRepository.artFor(name)
        if (art != null) {
            artistArtUrl = art.heroUrl
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .longPressMenuClickable(onClick = onClick, onLongClick = onLongPress)
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
            if (artistArtUrl != null) {
                AsyncImage(
                    model = artistArtUrl.artworkAt(ROW_ART_PX),
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
    var artistArtUrl by remember(name) {
        mutableStateOf(AppleArtistArtRepository.cached(name)?.heroUrl ?: thumbnailUrl)
    }
    LaunchedEffect(name) {
        val art = AppleArtistArtRepository.artFor(name)
        if (art != null) {
            artistArtUrl = art.heroUrl
        }
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .longPressMenuClickable(onClick = onClick, onLongClick = onLongPress)
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
            if (artistArtUrl != null) {
                AsyncImage(
                    model = artistArtUrl.artworkAt(CARD_ART_PX),
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
data class AlbumEntry(
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    /** Billed as a playlist rather than by artist; see [AlbumRow]. */
    val playlist: Boolean = false,
    /** Kept in the order it was downloaded in, which is the release's own. */
    val songs: List<Song>,
    /** Whether this is a release the user asked for, or a grouping inferred. */
    val asked: Boolean = false,
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
    isDownloads: Boolean = false,
    noLocalAlbums: Boolean = true,
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
    // In Downloads, when noLocalAlbums is turned on (default), hide loose singles and only
    // display whole releases that were explicitly downloaded as albums/playlists.
    if (isDownloads && noLocalAlbums) {
        return asked.sortedWith(
            compareByDescending<AlbumEntry> { it.asked }.thenBy { it.title.lowercase(Locale.ROOT) },
        )
    }
    val claimed = asked.mapTo(HashSet()) { it.title.lowercase(Locale.ROOT) }
    val derived = songs
        .groupBy { song ->
            val raw = song.albumName ?: return@groupBy null
            LocalMediaRepository.cleanDiscSuffix(raw)
        }
        .mapNotNull { (name, group) ->
            // Null is every track that never said what release it was off, and
            // there is no row to draw for "no album" — those are the Songs tab's
            // and nothing else's.
            if (name == null || name.isBlank() || name.lowercase(Locale.ROOT) in claimed) return@mapNotNull null
            val albumCover = group.firstOrNull {
                val thumb = it.thumbnailUrl
                thumb != null && !thumb.startsWith("audio:")
            }?.thumbnailUrl ?: group.firstNotNullOfOrNull { it.thumbnailUrl }

            val sortedGroup = group.sortedWith(
                compareBy<Song> { it.discNumber ?: 1 }
                    .thenBy { it.trackNumber ?: 0 }
                    .thenBy { it.title.lowercase(Locale.ROOT) },
            )

            val updatedSongs = if (albumCover != null) {
                sortedGroup.map { s ->
                    val thumb = s.thumbnailUrl
                    if (thumb == null || thumb.startsWith("audio:")) {
                        s.copy(thumbnailUrl = albumCover)
                    } else s
                }
            } else sortedGroup

            AlbumEntry(
                title = name,
                artist = group.firstOrNull()?.artist.orEmpty(),
                thumbnailUrl = albumCover,
                playlist = false,
                songs = updatedSongs,
                asked = false,
                key = "tagged:$name",
            )
        }
    return (asked + derived).sortedWith(
        compareByDescending<AlbumEntry> { it.asked }.thenBy { it.title.lowercase(Locale.ROOT) },
    )
}

/**
 * In-memory cache for derived artist groupings and album entries.
 * Prevents list state reset to index 0 on recompositions and drill-down navigation exits.
 */
internal object LocalMusicDerivedCache {
    private var lastSongs: List<Song>? = null
    private var lastCollections: List<DownloadedCollection>? = null
    private var lastArtistSort: LocalMusicSort? = null
    private var lastAlbumSort: LocalMusicSort? = null
    private var lastIsDownloads: Boolean? = null
    private var lastNoLocalAlbums: Boolean? = null
    var cachedArtists: List<Map.Entry<String, List<Song>>> = emptyList()
    var cachedAlbums: List<AlbumEntry> = emptyList()

    // Local Music scroll positions
    var lastSongIndex = 0
    var lastSongOffset = 0
    var lastSongGridIndex = 0
    var lastSongGridOffset = 0

    var lastArtistIndex = 0
    var lastArtistOffset = 0
    var lastArtistGridIndex = 0
    var lastArtistGridOffset = 0

    var lastAlbumIndex = 0
    var lastAlbumOffset = 0
    var lastAlbumGridIndex = 0
    var lastAlbumGridOffset = 0

    // Downloads folder scroll positions
    var lastDlSongIndex = 0
    var lastDlSongOffset = 0
    var lastDlSongGridIndex = 0
    var lastDlSongGridOffset = 0

    var lastDlArtistIndex = 0
    var lastDlArtistOffset = 0
    var lastDlArtistGridIndex = 0
    var lastDlArtistGridOffset = 0

    var lastDlAlbumIndex = 0
    var lastDlAlbumOffset = 0
    var lastDlAlbumGridIndex = 0
    var lastDlAlbumGridOffset = 0

    fun resetScroll(isDownloads: Boolean = false) {
        if (isDownloads) {
            lastDlSongIndex = 0
            lastDlSongOffset = 0
            lastDlSongGridIndex = 0
            lastDlSongGridOffset = 0
            lastDlArtistIndex = 0
            lastDlArtistOffset = 0
            lastDlArtistGridIndex = 0
            lastDlArtistGridOffset = 0
            lastDlAlbumIndex = 0
            lastDlAlbumOffset = 0
            lastDlAlbumGridIndex = 0
            lastDlAlbumGridOffset = 0
        } else {
            lastSongIndex = 0
            lastSongOffset = 0
            lastSongGridIndex = 0
            lastSongGridOffset = 0
            lastArtistIndex = 0
            lastArtistOffset = 0
            lastArtistGridIndex = 0
            lastArtistGridOffset = 0
            lastAlbumIndex = 0
            lastAlbumOffset = 0
            lastAlbumGridIndex = 0
            lastAlbumGridOffset = 0
        }
    }

    fun invalidate() {
        lastSongs = null
        lastCollections = null
        cachedArtists = emptyList()
        cachedAlbums = emptyList()
    }

    fun getArtists(songs: List<Song>, sort: LocalMusicSort = LocalMusicSort.TITLE_ASC): List<Map.Entry<String, List<Song>>> {
        val sameSongs = songs === lastSongs
        if (sameSongs && sort == lastArtistSort && cachedArtists.isNotEmpty()) return cachedArtists
        val grouped = groupSongsBySplitArtists(songs)
        val result = when (sort) {
            LocalMusicSort.TITLE_DESC -> grouped.entries.sortedByDescending { it.key.lowercase(Locale.ROOT) }
            LocalMusicSort.MOST_PLAYED, LocalMusicSort.DURATION_DESC -> grouped.entries.sortedByDescending { it.value.size }
            LocalMusicSort.DURATION_ASC -> grouped.entries.sortedBy { it.value.size }
            LocalMusicSort.DATE_ADDED -> grouped.entries.sortedByDescending { entry ->
                entry.value.maxOfOrNull { it.localDateAddedSeconds ?: Long.MIN_VALUE } ?: Long.MIN_VALUE
            }
            LocalMusicSort.DATE_MODIFIED -> grouped.entries.sortedByDescending { entry ->
                entry.value.maxOfOrNull { it.localDateModifiedSeconds ?: Long.MIN_VALUE } ?: Long.MIN_VALUE
            }
            else -> grouped.entries.sortedBy { it.key.lowercase(Locale.ROOT) }
        }
        cachedArtists = result
        lastSongs = songs
        lastArtistSort = sort
        return result
    }

    fun getAlbums(
        songs: List<Song>,
        collections: List<DownloadedCollection>,
        sort: LocalMusicSort = LocalMusicSort.TITLE_ASC,
        isDownloads: Boolean = false,
        noLocalAlbums: Boolean = true,
    ): List<AlbumEntry> {
        val sameSongs = songs === lastSongs && collections === lastCollections
        if (sameSongs && sort == lastAlbumSort && isDownloads == lastIsDownloads && noLocalAlbums == lastNoLocalAlbums && cachedAlbums.isNotEmpty()) return cachedAlbums
        val base = albumEntries(songs, collections, isDownloads = isDownloads, noLocalAlbums = noLocalAlbums)
        val result = when (sort) {
            LocalMusicSort.TITLE_DESC -> base.sortedWith(compareByDescending { it.title.lowercase(Locale.ROOT) })
            LocalMusicSort.DATE_ADDED -> base.sortedWith(compareByDescending { it.songs.maxOfOrNull { s -> s.localDateAddedSeconds ?: Long.MIN_VALUE } ?: Long.MIN_VALUE })
            LocalMusicSort.DATE_MODIFIED -> base.sortedWith(compareByDescending { it.songs.maxOfOrNull { s -> s.localDateModifiedSeconds ?: Long.MIN_VALUE } ?: Long.MIN_VALUE })
            LocalMusicSort.MOST_PLAYED, LocalMusicSort.DURATION_DESC -> base.sortedWith(compareByDescending { it.songs.size })
            LocalMusicSort.DURATION_ASC -> base.sortedWith(compareBy { it.songs.size })
            LocalMusicSort.ARTIST_ASC -> base.sortedWith(compareBy<AlbumEntry> { it.artist.lowercase(Locale.ROOT) }.thenBy { it.title.lowercase(Locale.ROOT) })
            else -> base.sortedWith(compareByDescending<AlbumEntry> { it.asked }.thenBy { it.title.lowercase(Locale.ROOT) })
        }
        cachedAlbums = result
        lastSongs = songs
        lastCollections = collections
        lastAlbumSort = sort
        lastIsDownloads = isDownloads
        lastNoLocalAlbums = noLocalAlbums
        return result
    }
}

@Composable
private fun AlbumsTab(
    albums: List<AlbumEntry>,
    viewType: LibraryViewType,
    listState: LazyListState = rememberLazyListState(),
    gridState: LazyGridState = rememberLazyGridState(),
    selectedKeys: Set<String> = emptySet(),
    onAlbumClick: (AlbumEntry) -> Unit,
    onAlbumLongPress: ((AlbumEntry) -> Unit)?,
    contentPadding: PaddingValues,
) {
    if (viewType == LibraryViewType.GRID) {
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
            .longPressMenuClickable(onClick = onClick, onLongClick = onLongPress)
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
            // Albums grouped off tags have no cover of their own; the first
            // track's embedded picture stands in until the real one exists.
            val cardArt = entry.thumbnailUrl ?: entry.songs.firstNotNullOfOrNull { it.thumbnailUrl }
            if (cardArt != null) {
                AsyncImage(
                    model = cardArt.artworkAt(CARD_ART_PX),
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
    val art = entry.thumbnailUrl ?: entry.songs.firstNotNullOfOrNull { it.thumbnailUrl }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent, RoundedCornerShape(10.dp))
            .longPressMenuClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = PAGE_GUTTER, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CollectionArtwork(
            url = art,
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


/**
 * Aspect ratio for artist hero photos — very slightly taller than wide,
 * matching the online DetailScreen's ARTIST_PHOTO_RATIO constant.
 */
private const val DRILL_ARTIST_RATIO = 0.95f

/**
 * Aspect ratio for album/playlist hero banners — one-to-one square cover
 * displayed cinematic: just enough height for impact without hiding songs.
 */
private const val DRILL_ALBUM_RATIO = 0.72f

/**
 * The full-bleed hero photo pinned *behind* the scroll list.
 *
 * This is the offline equivalent of DetailScreen's [PageBackground]: drawn as a
 * sibling of the list with [Modifier.matchParentSize], it covers the full device
 * width without being subject to any horizontal padding the list may apply.
 * A parallax [IntOffset] keeps it in step with the list exactly as the online
 * artist page does — the photo scrolls away as the track list scrolls up.
 *
 * The gradient scrim matches the online page's wash stops exactly.
 */
@Composable
private fun DrillDownHeroBackground(
    label: String,
    artworkUrl: String?,
    isArtist: Boolean,
    heroHeight: androidx.compose.ui.unit.Dp,
    listFirstVisibleIndex: Int,
    listFirstVisibleScrollOffset: Int,
    modifier: Modifier = Modifier,
) {
    var resolvedArt by remember(label, artworkUrl, isArtist) {
        mutableStateOf(
            if (isArtist) AppleArtistArtRepository.cached(label)?.heroUrl ?: artworkUrl
            else artworkUrl,
        )
    }
    LaunchedEffect(label, isArtist) {
        if (isArtist) {
            val art = AppleArtistArtRepository.artFor(label)
            if (art != null) resolvedArt = art.heroUrl
        }
    }

    val bgColor = MaterialTheme.colorScheme.background
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heroHeight)
            .clipToBounds()
            // Parallax: when the header item is first (index 0), offset by how
            // far it has scrolled; once it leaves, park the image off-screen.
            .offset {
                val offsetPx = if (listFirstVisibleIndex == 0)
                    -listFirstVisibleScrollOffset
                else
                    -(with(density) { heroHeight.toPx() } * 2f).toInt()
                IntOffset(0, offsetPx)
            },
    ) {
        // Neutral placeholder behind the image.
        Box(
            Modifier
                .fillMaxSize()
                .background(surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isArtist) Icons.Rounded.Person else Icons.Rounded.Album,
                contentDescription = null,
                tint = onSurfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.size(80.dp),
            )
        }

        // Hero image — crop-fills, subject never letterboxed.
        resolvedArt?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Gradient scrim — matches the online DetailScreen's PageBackground
        // wash stops exactly: transparent top half dissolving to page colour.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to Color.Transparent,
                        0.45f to Color.Transparent,
                        0.65f to bgColor.copy(alpha = 0.30f),
                        0.82f to bgColor.copy(alpha = 0.72f),
                        0.94f to bgColor.copy(alpha = 0.95f),
                        1.00f to bgColor,
                    ),
                ),
        )
    }
}

/**
 * The in-list header item for an artist / album drill-down.
 *
 * Only holds a Spacer the same height as the hero, the artist/album name and
 * subtitle overlaid at the bottom of that space, and floating back / ⋮ buttons
 * at the top. The actual photo is drawn by [DrillDownHeroBackground] pinned
 * behind the scroll list — keeping this item free of any horizontal constraints.
 */
@Composable
private fun DrillDownHeroListItem(
    label: String,
    songs: List<Song>,
    isArtist: Boolean,
    heroHeight: androidx.compose.ui.unit.Dp,
    onBack: () -> Unit,
    onMore: (() -> Unit)?,
    onOpenOnlineArtist: ((String) -> Unit)?,
    onOpenOnlineAlbum: ((String, String) -> Unit)?,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant

    // Outer box sized to heroHeight so the list reserves exactly the right room.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight),
    ) {
        // ── Back arrow — top-left, floating over the image ───────────────────
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 8.dp, top = 8.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.38f))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }

        // ── ⋮ — top-right, floating over the image ──────────────────────────
        onMore?.let { more ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 8.dp, top = 8.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.38f))
                    .clickable(onClick = more),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.MoreHoriz,
                    contentDescription = stringResource(R.string.more),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // ── Name + subtitle — bottom-start, over gradient ────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = PAGE_GUTTER, end = PAGE_GUTTER, bottom = 16.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = buildString {
                    if (isArtist) append(stringResource(R.string.artist)).append(" · ")
                    append(pluralStringResource(R.plurals.track_count_plural, songs.size, songs.size))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.82f),
            )
        }
    }

    // ── Online bridge pill — below the hero ─────────────────────────────────
    val albumArtist = songs.firstOrNull()?.artist.orEmpty()
    val showOnlinePill = (isArtist && onOpenOnlineArtist != null) ||
        (!isArtist && onOpenOnlineAlbum != null)
    if (showOnlinePill) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, start = PAGE_GUTTER, end = PAGE_GUTTER),
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(surfaceVariant.copy(alpha = 0.72f))
                    .border(0.5.dp, outlineVariant.copy(alpha = 0.5f), CircleShape)
                    .clickable {
                        if (isArtist) onOpenOnlineArtist?.invoke(label)
                        else onOpenOnlineAlbum?.invoke(label, albumArtist)
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isArtist) Icons.Rounded.Person else Icons.Rounded.Album,
                        contentDescription = null,
                        tint = primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(
                            if (isArtist) R.string.view_online_artist
                            else R.string.view_online_album,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = onSurface,
                    )
                }
            }
        }
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
    isArtist: Boolean = false,
    onOpenOnlineArtist: ((String) -> Unit)? = null,
    onOpenOnlineAlbum: ((String, String) -> Unit)? = null,
    onOpenAlbum: ((AlbumEntry) -> Unit)? = null,
    viewType: LibraryViewType,
    selectedIds: Set<String> = emptySet(),
    currentSong: Song? = null,
    isPlaying: Boolean = false,
    onSongClick: (List<Song>, Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
    /** The row's ⋮, where holding it does something else — see [SongRow]. */
    onSongMore: ((Song) -> Unit)? = null,
    onSongSwipe: (Song) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    /** The ⋮ in the header, acting on the whole artist or album. */
    onMore: (() -> Unit)?,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
) {
    // For artist drill-downs, we first show the artist's albums and allow a
    // secondary "album within artist" drill-down.  Only album drill-downs (or
    // the secondary album within artist) jump straight to the flat track list.
    var albumDrillDown by remember { mutableStateOf<AlbumEntry?>(null) }

    LaunchedEffect(songs) {
        val current = albumDrillDown ?: return@LaunchedEffect
        val updatedGroup = songs.filter {
            val cleaned = it.albumName?.let { name -> com.music.bitchord.data.LocalMediaRepository.cleanDiscSuffix(name) }
            (cleaned?.ifBlank { null } ?: "Singles & Other Tracks").equals(current.title, ignoreCase = true)
        }
        if (updatedGroup.isNotEmpty()) {
            val sortedGroup = updatedGroup.sortedWith(
                compareBy<Song> { it.discNumber ?: 1 }
                    .thenBy { it.trackNumber ?: Int.MAX_VALUE }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            )
            val art = sortedGroup.firstOrNull { it.thumbnailUrl?.startsWith("audio:") == false }?.thumbnailUrl
                ?: sortedGroup.firstNotNullOfOrNull { it.thumbnailUrl } ?: current.thumbnailUrl
            albumDrillDown = current.copy(thumbnailUrl = art, songs = sortedGroup)
        }
    }

    // Back press from secondary album drill-down returns to the albums grid.
    BackHandler(enabled = albumDrillDown != null) { albumDrillDown = null }

    // Decide which songs and label to display. When an album is open within the
    // artist drill-down we show its tracks; otherwise the full artist songs.
    val displaySongs = albumDrillDown?.songs ?: songs
    val displayLabel = albumDrillDown?.title ?: label
    val displayArt = albumDrillDown?.thumbnailUrl ?: artworkUrl
    val displayIsArtist = albumDrillDown == null && isArtist
    val hasMultipleDiscs = !displayIsArtist && remember(displaySongs) {
        displaySongs.mapNotNull { it.discNumber }.filter { it > 0 }.distinct().size > 1
    }

    // Albums grouped from this artist's songs — only needed for the artist view.
    val artistAlbums = remember(songs, isArtist) {
        if (!isArtist) emptyList()
        else songs
            .groupBy {
                val cleaned = it.albumName?.let { name -> com.music.bitchord.data.LocalMediaRepository.cleanDiscSuffix(name) }
                cleaned?.ifBlank { null } ?: "Singles & Other Tracks"
            }
            .map { (name, group) ->
                val sortedGroup = group.sortedWith(
                    compareBy<Song> { it.discNumber ?: 1 }
                        .thenBy { it.trackNumber ?: Int.MAX_VALUE }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
                )
                val art = sortedGroup.firstOrNull { it.thumbnailUrl?.startsWith("audio:") == false }?.thumbnailUrl
                    ?: sortedGroup.firstNotNullOfOrNull { it.thumbnailUrl }
                AlbumEntry(
                    title = name,
                    artist = sortedGroup.firstOrNull()?.artist.orEmpty(),
                    thumbnailUrl = art,
                    playlist = false,
                    songs = sortedGroup,
                    asked = false,
                    key = "artist-album:$name",
                )
            }
            .sortedBy { if (it.title == "Singles & Other Tracks") "zzz" else it.title.lowercase(Locale.ROOT) }
    }

    // When inside an album-within-artist, pretend we're rendering a normal album
    // drill-down (the flat track list). When viewing an artist's top level, show
    // the albums grid.  Non-artist drill-downs always show the flat list.
    val showAlbumsGrid = isArtist && albumDrillDown == null

    // ── Two-layer layout: pinned background + scrollable list ────────────────
    // The hero image is drawn as a sibling of the scroll list (matchParentSize),
    // exactly like DetailScreen's PageBackground.  The list item only holds a
    // Spacer of the same height — nothing is constrained by the list's padding.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val ratio = if (displayIsArtist) DRILL_ARTIST_RATIO else DRILL_ALBUM_RATIO
        val heroHeight = (maxWidth * ratio).coerceAtMost(maxHeight * 0.65f)

        if (viewType == LibraryViewType.GRID && !showAlbumsGrid) {
            val gridState = rememberLazyGridState()
            // ── Pinned hero background ────────────────────────────────────────
            DrillDownHeroBackground(
                label = displayLabel,
                artworkUrl = displayArt,
                isArtist = displayIsArtist,
                heroHeight = heroHeight,
                listFirstVisibleIndex = gridState.firstVisibleItemIndex,
                listFirstVisibleScrollOffset = gridState.firstVisibleItemScrollOffset,
                modifier = Modifier.matchParentSize(),
            )
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
                    DrillDownHeroListItem(
                        label = displayLabel,
                        songs = displaySongs,
                        isArtist = displayIsArtist,
                        heroHeight = heroHeight,
                        onBack = if (albumDrillDown != null) { { albumDrillDown = null } } else onBack,
                        onMore = if (albumDrillDown != null) null else onMore,
                        onOpenOnlineArtist = onOpenOnlineArtist,
                        onOpenOnlineAlbum = onOpenOnlineAlbum,
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    DrillDownActionRow(
                        songs = displaySongs,
                        onSongClick = onSongClick,
                        onShuffle = onShuffle,
                    )
                }
                itemsIndexed(displaySongs, key = { _, song -> song.localUri ?: song.localPath ?: song.videoId }) { index, song ->
                    SongGridCard(
                        song = song,
                        selected = song.videoId in selectedIds,
                        isCurrent = song.isSameTrackAs(currentSong),
                        isPlaying = song.isSameTrackAs(currentSong) && isPlaying,
                        searchPlayingStyle = true,
                        onClick = { onSongClick(displaySongs, index) },
                        onLongPress = { onSongLongPress(song) },
                    )
                }
            }
        } else if (showAlbumsGrid) {
            // ── Artist top-level: albums grid followed by all songs ───────────
            val gridState = rememberLazyGridState()
            DrillDownHeroBackground(
                label = label,
                artworkUrl = artworkUrl,
                isArtist = true,
                heroHeight = heroHeight,
                listFirstVisibleIndex = gridState.firstVisibleItemIndex,
                listFirstVisibleScrollOffset = gridState.firstVisibleItemScrollOffset,
                modifier = Modifier.matchParentSize(),
            )
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
                    DrillDownHeroListItem(
                        label = label,
                        songs = songs,
                        isArtist = true,
                        heroHeight = heroHeight,
                        onBack = onBack,
                        onMore = onMore,
                        onOpenOnlineArtist = onOpenOnlineArtist,
                        onOpenOnlineAlbum = onOpenOnlineAlbum,
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    DrillDownActionRow(
                        songs = songs,
                        onSongClick = onSongClick,
                        onShuffle = onShuffle,
                    )
                }
                if (artistAlbums.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(
                            icon = Icons.Rounded.Album,
                            title = pluralStringResource(R.plurals.album_count, artistAlbums.size, artistAlbums.size),
                            modifier = Modifier.padding(horizontal = 0.dp, vertical = 10.dp),
                        )
                    }
                    items(artistAlbums, key = { it.key }) { entry ->
                        AlbumGridCard(
                            entry = entry,
                            selected = false,
                            onClick = {
                                if (onOpenAlbum != null) {
                                    onOpenAlbum(entry)
                                } else {
                                    albumDrillDown = entry
                                }
                            },
                            onLongPress = {},
                        )
                    }
                }
                if (songs.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(
                            icon = Icons.Rounded.MusicNote,
                            title = pluralStringResource(R.plurals.track_count_plural, songs.size, songs.size),
                            modifier = Modifier.padding(horizontal = 0.dp, vertical = 10.dp),
                        )
                    }
                    itemsIndexed(songs, key = { _, song -> "artist_song:${song.localUri ?: song.localPath ?: song.videoId}" }) { index, song ->
                        SongGridCard(
                            song = song,
                            selected = song.videoId in selectedIds,
                            isCurrent = song.isSameTrackAs(currentSong),
                            isPlaying = song.isSameTrackAs(currentSong) && isPlaying,
                            searchPlayingStyle = true,
                            onClick = { onSongClick(songs, index) },
                            onLongPress = { onSongLongPress(song) },
                        )
                    }
                }
            }
        } else {
            // ── Non-artist (or album-within-artist): flat track list ──────────
            val listState = rememberLazyListState()
            DrillDownHeroBackground(
                label = displayLabel,
                artworkUrl = displayArt,
                isArtist = displayIsArtist,
                heroHeight = heroHeight,
                listFirstVisibleIndex = listState.firstVisibleItemIndex,
                listFirstVisibleScrollOffset = listState.firstVisibleItemScrollOffset,
                modifier = Modifier.matchParentSize(),
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
            ) {
                item {
                    DrillDownHeroListItem(
                        label = displayLabel,
                        songs = displaySongs,
                        isArtist = displayIsArtist,
                        heroHeight = heroHeight,
                        onBack = if (albumDrillDown != null) { { albumDrillDown = null } } else onBack,
                        onMore = if (albumDrillDown != null) null else onMore,
                        onOpenOnlineArtist = onOpenOnlineArtist,
                        onOpenOnlineAlbum = onOpenOnlineAlbum,
                    )
                }
                item {
                    DrillDownActionRow(
                        songs = displaySongs,
                        onSongClick = onSongClick,
                        onShuffle = onShuffle,
                    )
                }
                itemsIndexed(displaySongs, key = { _, song -> song.localUri ?: song.localPath ?: song.videoId }) { index, song ->
                    val currentDisc = song.discNumber ?: 1
                    val prevDisc = if (index > 0) displaySongs[index - 1].discNumber ?: 1 else null
                    val showDiscHeader = hasMultipleDiscs && (index == 0 || currentDisc != prevDisc)

                    if (showDiscHeader) {
                        DiscSectionHeader(
                            discNumber = currentDisc,
                            modifier = Modifier.padding(
                                start = NUMBERED_ROW_DIVIDER_INSET,
                                top = if (index == 0) 12.dp else 24.dp,
                                bottom = 8.dp,
                                end = PAGE_GUTTER,
                            ),
                        )
                    }

                    SongRow(
                        song = song,
                        selected = song.videoId in selectedIds,
                        isCurrent = song.isSameTrackAs(currentSong),
                        isPlaying = song.isSameTrackAs(currentSong) && isPlaying,
                        searchPlayingStyle = true,
                        activeTint = PlayingAccent,
                        trackNumber = song.trackNumber ?: (index + 1),
                        downloadedTint = null,
                        onClick = { onSongClick(displaySongs, index) },
                        onLongPress = { onSongLongPress(song) },
                        onMore = onSongMore?.let { more -> { more(song) } },
                        onSwipeToQueue = { onSongSwipe(song) },
                    )
                    val nextDisc = if (index < displaySongs.lastIndex) displaySongs[index + 1].discNumber ?: 1 else null
                    val nextHasHeader = hasMultipleDiscs && nextDisc != currentDisc
                    if (index < displaySongs.lastIndex && !nextHasHeader) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = NUMBERED_ROW_DIVIDER_INSET),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscSectionHeader(
    discNumber: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Album,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Disc $discNumber",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.5.sp,
        )
    }
}


// ── Shared helpers ────────────────────────────────────────────────────────────

/**
 * Whether this track is a hit for a query typed into [LocalSearchField] — or
 * into the Search tab with its Library source picked, which has to find the
 * same tracks this folder would.
 */
internal fun Song.matchesSearch(query: String): Boolean =
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
    LocalMusicSort.MOST_PLAYED -> {
        val playCounts = com.music.bitchord.data.stats.ListeningStats.allTrackPlayCounts()
        sortedWith(
            compareByDescending<Song> { playCounts[it.videoId] ?: 0 }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
        )
    }
    LocalMusicSort.RECENTLY_PLAYED -> {
        val lastPlayed = com.music.bitchord.data.stats.ListeningStats.allTrackLastPlayed()
        sortedWith(
            compareByDescending<Song> { lastPlayed[it.videoId] ?: 0L }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
        )
    }
    LocalMusicSort.ARTIST_ASC -> sortedWith(
        compareBy<Song> { it.artist.lowercase(Locale.ROOT) }
            .thenBy { it.title.lowercase(Locale.ROOT) },
    )
    LocalMusicSort.DURATION_ASC -> sortedWith(
        compareBy<Song> { com.music.bitchord.data.sources.TrackMatcher.secondsOf(it.durationText) ?: 0 }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
    )
    LocalMusicSort.DURATION_DESC -> sortedWith(
        compareByDescending<Song> { com.music.bitchord.data.sources.TrackMatcher.secondsOf(it.durationText) ?: 0 }
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
    isDownloads: Boolean = false,
    onRefresh: (() -> Unit)? = null,
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
        if (!isDownloads && onRefresh != null) {
            Spacer(Modifier.width(2.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable {
                        haptics.play(Haptic.Select)
                        onRefresh()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.refresh_library),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp),
                )
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
    LocalMusicSort.MOST_PLAYED -> stringResource(R.string.sort_most_played)
    LocalMusicSort.RECENTLY_PLAYED -> stringResource(R.string.sort_recently_played)
    LocalMusicSort.ARTIST_ASC -> stringResource(R.string.sort_artist_name)
    LocalMusicSort.DURATION_ASC -> stringResource(R.string.sort_duration_shortest)
    LocalMusicSort.DURATION_DESC -> stringResource(R.string.sort_duration_longest)
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
    /** Copies the selection to WebDAV; null (no server configured) hides the action. */
    onUpload: (() -> Unit)? = null,
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
        onUpload?.let { upload ->
            TextButton(onClick = upload, enabled = count > 0) {
                Icon(
                    Icons.Rounded.FileUpload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(R.string.upload_to_webdav),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
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
    modifier: Modifier = Modifier.fillMaxWidth().padding(horizontal = PAGE_GUTTER, vertical = 14.dp),
) {
    Row(
        modifier = modifier,
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
