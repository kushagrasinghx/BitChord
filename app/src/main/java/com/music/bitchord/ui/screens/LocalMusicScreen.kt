package com.music.bitchord.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.model.Song
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.ROW_DIVIDER_INSET
import com.music.bitchord.ui.components.SongRow

private const val LOCAL_TAB_SONGS = 0
private const val LOCAL_TAB_ARTISTS = 1
private const val LOCAL_TAB_ALBUMS = 2

/**
 * Local Music folder view with three tabs: Songs (default), Artists, Albums.
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
    modifier: Modifier = Modifier,
) {
    // Which top-level tab is selected.
    var selectedTab by rememberSaveable { mutableIntStateOf(LOCAL_TAB_SONGS) }

    // When non-null, we are showing a drill-down list for that artist or album.
    var drillDownLabel by remember { mutableStateOf<String?>(null) }
    var drillDownSongs by remember { mutableStateOf<List<Song>>(emptyList()) }

    val inDrillDown = drillDownLabel != null

    BackHandler(enabled = inDrillDown) {
        drillDownLabel = null
        drillDownSongs = emptyList()
    }

    // The tab row is fixed above the scrolling content, so its own top
    // padding has to clear the frosted top bar / status bar that the
    // LazyColumns beneath it would otherwise scroll under.
    val bodyContentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding())

    // contentPadding.top carries extra breathing room meant for scrolling
    // content resting under the glass bar; the tab row is fixed and sits
    // right below the bar, so it only needs to clear the bar itself
    // (status bar inset + the bar's own 52dp) — see FrostedTopBar.
    val topBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 52.dp

    Column(modifier = modifier.fillMaxSize()) {
        // ── Tab row ──────────────────────────────────────────────────────────
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = topBarHeight),
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = MaterialTheme.colorScheme.primary,
                )
            },
        ) {
            LocalTab(
                icon = Icons.Rounded.MusicNote,
                label = "Songs",
                selected = selectedTab == LOCAL_TAB_SONGS,
                onClick = {
                    selectedTab = LOCAL_TAB_SONGS
                    drillDownLabel = null
                },
            )
            LocalTab(
                icon = Icons.Rounded.Person,
                label = "Artists",
                selected = selectedTab == LOCAL_TAB_ARTISTS,
                onClick = {
                    selectedTab = LOCAL_TAB_ARTISTS
                    drillDownLabel = null
                },
            )
            LocalTab(
                icon = Icons.Rounded.Album,
                label = "Albums",
                selected = selectedTab == LOCAL_TAB_ALBUMS,
                onClick = {
                    selectedTab = LOCAL_TAB_ALBUMS
                    drillDownLabel = null
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
                key.startsWith("drill:") -> {
                    // Drill-down song list for artist / album
                    DrillDownSongList(
                        label = drillDownLabel ?: "",
                        songs = drillDownSongs,
                        onSongClick = onSongClick,
                        onSongLongPress = onSongLongPress,
                        onSongSwipe = onSongSwipe,
                        onShuffle = onShuffle,
                        onBack = {
                            drillDownLabel = null
                            drillDownSongs = emptyList()
                        },
                        contentPadding = bodyContentPadding,
                    )
                }

                key == "tab:$LOCAL_TAB_SONGS" -> {
                    SongsTab(
                        songs = songs,
                        onSongClick = onSongClick,
                        onSongLongPress = onSongLongPress,
                        onSongSwipe = onSongSwipe,
                        contentPadding = bodyContentPadding,
                    )
                }

                key == "tab:$LOCAL_TAB_ARTISTS" -> {
                    val artists = remember(songs) {
                        songs.groupBy { it.artist }
                            .entries
                            .sortedBy { it.key.lowercase() }
                    }
                    ArtistsTab(
                        artists = artists,
                        onArtistClick = { artist, artistSongs ->
                            drillDownLabel = artist
                            drillDownSongs = artistSongs
                        },
                        contentPadding = bodyContentPadding,
                    )
                }

                else -> {
                    // LOCAL_TAB_ALBUMS
                    val albums = remember(songs) {
                        songs.filter { it.albumName != null }
                            .groupBy { it.albumName!! }
                            .entries
                            .sortedBy { it.key.lowercase() }
                    }
                    AlbumsTab(
                        albums = albums,
                        onAlbumClick = { album, albumSongs ->
                            drillDownLabel = album
                            drillDownSongs = albumSongs
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
    onSongClick: (List<Song>, Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onSongSwipe: (Song) -> Unit,
    contentPadding: PaddingValues,
) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item {
            SectionHeader(
                icon = Icons.Rounded.LibraryMusic,
                title = "${songs.size} songs",
            )
        }
        itemsIndexed(songs) { index, song ->
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
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}

// ── Artists tab ───────────────────────────────────────────────────────────────

@Composable
private fun ArtistsTab(
    artists: List<Map.Entry<String, List<Song>>>,
    onArtistClick: (String, List<Song>) -> Unit,
    contentPadding: PaddingValues,
) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item {
            SectionHeader(
                icon = Icons.Rounded.Person,
                title = "${artists.size} artists",
            )
        }
        items(artists) { (artist, artistSongs) ->
            ArtistRow(
                name = artist,
                songCount = artistSongs.size,
                onClick = { onArtistClick(artist, artistSongs) },
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = ROW_DIVIDER_INSET),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun ArtistRow(name: String, songCount: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
                text = "$songCount ${if (songCount == 1) "song" else "songs"}",
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

// ── Albums tab ────────────────────────────────────────────────────────────────

@Composable
private fun AlbumsTab(
    albums: List<Map.Entry<String, List<Song>>>,
    onAlbumClick: (String, List<Song>) -> Unit,
    contentPadding: PaddingValues,
) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item {
            SectionHeader(
                icon = Icons.Rounded.Album,
                title = "${albums.size} albums",
            )
        }
        items(albums) { (album, albumSongs) ->
            AlbumRow(
                name = album,
                artist = albumSongs.firstOrNull()?.artist ?: "",
                songCount = albumSongs.size,
                onClick = { onAlbumClick(album, albumSongs) },
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = ROW_DIVIDER_INSET),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun AlbumRow(name: String, artist: String, songCount: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = PAGE_GUTTER, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Album icon square
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Album,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(26.dp),
            )
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
                text = buildString {
                    if (artist.isNotBlank()) append("$artist · ")
                    append("$songCount ${if (songCount == 1) "song" else "songs"}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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

// ── Drill-down song list ───────────────────────────────────────────────────────

@Composable
private fun DrillDownSongList(
    label: String,
    songs: List<Song>,
    onSongClick: (List<Song>, Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onSongSwipe: (Song) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        // Back + title header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = PAGE_GUTTER, top = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Play / Shuffle action row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PAGE_GUTTER, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Play button
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { if (songs.isNotEmpty()) onSongClick(songs, 0) }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Play",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                // Shuffle button
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable { if (songs.isNotEmpty()) onShuffle(songs) }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Shuffle",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        // Song rows
        itemsIndexed(songs) { index, song ->
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
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}

// ── Shared helpers ────────────────────────────────────────────────────────────

@Composable
private fun LocalTab(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Tab(
        selected = selected,
        onClick = onClick,
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
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_GUTTER, vertical = 14.dp),
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
