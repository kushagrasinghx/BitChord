package com.music.bitchord.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.applemusic.AppleMusicAlbum
import com.music.bitchord.data.applemusic.AppleMusicArtist
import com.music.bitchord.data.applemusic.AppleMusicBrowseController
import com.music.bitchord.data.applemusic.AppleMusicBrowseError
import com.music.bitchord.data.applemusic.AppleMusicBrowseSelection
import com.music.bitchord.data.applemusic.AppleMusicBrowseState
import com.music.bitchord.data.applemusic.AppleMusicLibraryItem
import com.music.bitchord.data.applemusic.AppleMusicLibrarySession
import com.music.bitchord.data.applemusic.AppleMusicPlaylist
import com.music.bitchord.data.applemusic.AppleMusicTrack
import com.music.bitchord.data.applemusic.AppleMusicTrackMapping
import com.music.bitchord.data.applemusic.AppleMusicTrackMapper
import com.music.bitchord.data.applemusic.AppleMusicTrackMatchStatus
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.PlaybackProvider
import com.music.bitchord.playback.applemusic.AppleMusicPlayback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The signed-in Apple Music account's library, as a way in rather than a page
 * of its own: each section reads Apple's REST catalogue directly, and every
 * row that has a YouTube Music counterpart is queued as an ordinary song.
 *
 * A row Apple Music has and YouTube Music does not (`APPLE_ONLY`) stays
 * playable while Apple Music is the chosen provider — its catalog id travels
 * in the queue as an `am-miss:` id, which the Apple Music speaker plays
 * directly and the fallback path declines.
 */
@Composable
fun AppleMusicLibraryScreen(
    contentPadding: PaddingValues,
    onConnect: () -> Unit,
    onOpenImport: (AppleMusicPlaylist?) -> Unit,
    onPlay: (songs: List<Song>, index: Int, title: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val connected by com.music.bitchord.data.applemusic.AppleMusicTokens.connected
        .collectAsStateWithLifecycle()
    val provider by AppSettings.playbackProvider.collectAsStateWithLifecycle()
    val storefront by AppSettings.appleMusicStorefront.collectAsStateWithLifecycle()

    val session = remember { AppleMusicLibrarySession(scope) }
    var controller by remember { mutableStateOf<AppleMusicBrowseController?>(null) }
    var browse by remember { mutableStateOf(AppleMusicBrowseState(session.refresh())) }
    var section by remember { mutableStateOf(AppleMusicSection.PLAYLISTS) }
    var query by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        onDispose {
            controller?.close()
            session.close()
        }
    }
    LaunchedEffect(Unit) {
        val matcher = withContext(Dispatchers.IO) { AppleMusicPlayback.matcher(context) }
        controller = AppleMusicBrowseController(session, AppleMusicTrackMapper(matcher))
    }
    LaunchedEffect(controller) {
        // Cancelled and replaced when this screen's request is superseded.
        controller?.state?.collect { browse = it }
    }
    LaunchedEffect(controller, section, connected) {
        val current = controller ?: return@LaunchedEffect
        if (!connected) return@LaunchedEffect
        current.load(section.selection)
    }

    val appleSelected = provider == PlaybackProvider.APPLE_MUSIC
    // Which row of the visible list starts which song of the queue handed to
    // the player: -1 for a row that cannot be played at all right now.
    val queueIndex = remember(browse.tracks, connected, appleSelected) {
        var cursor = 0
        browse.tracks.map { row ->
            if (row.isPlayable(connected, appleSelected)) cursor++ else -1
        }
    }
    val playableSongs = remember(browse.tracks, connected, appleSelected) {
        browse.tracks.filter { it.isPlayable(connected, appleSelected) }.map { it.song }
    }
    val openedList = when (val selection = browse.selection) {
        is AppleMusicBrowseSelection.PlaylistTracks -> selection.playlist.name
        is AppleMusicBrowseSelection.AlbumTracks -> selection.album.name
        is AppleMusicBrowseSelection.ArtistAlbums -> selection.artist.name
        is AppleMusicBrowseSelection.Search -> selection.query
        else -> null
    }
    val playTitle = openedList ?: stringResource(R.string.apple_music)

    fun show(selection: AppleMusicBrowseSelection) {
        scope.launch { controller?.load(selection) }
    }

    fun play(rowIndex: Int, title: String) {
        val index = queueIndex.getOrNull(rowIndex)?.takeIf { it >= 0 } ?: return
        onPlay(playableSongs, index, title)
    }

    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(34.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.apple_music),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        if (!connected) {
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text = stringResource(R.string.apple_music_connect_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onConnect) {
                        Text(stringResource(R.string.apple_music_connect))
                    }
                }
            }
            return@LazyColumn
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (storefront.isBlank()) {
                        stringResource(R.string.connected)
                    } else {
                        stringResource(R.string.apple_music_connected_storefront, storefront.uppercase())
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onOpenImport(null) }) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.apple_music_import))
                }
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.apple_music_search_hint)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                if (query.isNotBlank()) {
                    TextButton(
                        onClick = {
                            val term = query.trim()
                            if (term.isNotEmpty()) {
                                scope.launch { controller?.search(term) }
                            }
                        },
                    ) {
                        Text(stringResource(R.string.apple_music_search))
                    }
                }
            }
        }

        if (openedList == null) {
            item {
                AppleMusicSections(
                    selected = section,
                    onSelect = { picked ->
                        query = ""
                        section = picked
                        show(picked.selection)
                    },
                )
                Spacer(Modifier.height(10.dp))
            }
        }

        if (openedList != null) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { show(section.selection) }
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.apple_music_back),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        when {
            browse.isLoading -> item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            browse.error != null -> item {
                Text(
                    text = browse.error!!.message(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }

        if (browse.tracks.isNotEmpty()) {
            if (browse.isMatching) {
                item {
                    Text(
                        text = stringResource(R.string.apple_music_matching, browse.matchedCount, browse.tracks.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
            }
            items(browse.tracks.size) { index ->
                val row = browse.tracks[index]
                AppleMusicTrackRow(
                    row = row,
                    playable = queueIndex.getOrNull(index)?.let { it >= 0 } == true,
                    onClick = { play(index, playTitle) },
                )
            }
        } else if (browse.items.isNotEmpty()) {
            items(browse.items.size) { index ->
                when (val item = browse.items[index]) {
                    is AppleMusicPlaylist -> AppleMusicItemRow(
                        title = item.name,
                        subtitle = item.owner.orEmpty(),
                        imageUrl = item.imageUrl,
                        onClick = { show(AppleMusicBrowseSelection.PlaylistTracks(item)) },
                        onImport = { onOpenImport(item) },
                    )
                    is AppleMusicAlbum -> AppleMusicItemRow(
                        title = item.name,
                        subtitle = item.artist,
                        imageUrl = item.imageUrl,
                        onClick = { show(AppleMusicBrowseSelection.AlbumTracks(item)) },
                    )
                    is AppleMusicTrack -> AppleMusicItemRow(
                        title = item.title.ifBlank { item.id },
                        subtitle = item.artist,
                        imageUrl = item.imageUrl,
                        onClick = null,
                    )
                    is AppleMusicArtist -> AppleMusicItemRow(
                        title = item.name,
                        subtitle = stringResource(R.string.apple_music_artist),
                        imageUrl = item.imageUrl,
                        onClick = { show(AppleMusicBrowseSelection.ArtistAlbums(item)) },
                    )
                }
            }
        } else if (!browse.isLoading && browse.error == null) {
            item {
                Text(
                    text = stringResource(R.string.apple_music_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

/**
 * Library destinations; each reads a different Apple endpoint.
 */
@Composable
private fun AppleMusicSections(
    selected: AppleMusicSection?,
    onSelect: (AppleMusicSection) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(AppleMusicSection.entries.size) { index ->
            val tile = AppleMusicSection.entries[index]
            val active = tile == selected
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                    )
                    .clickable { onSelect(tile) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text = stringResource(tile.label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (active) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun AppleMusicItemRow(
    title: String,
    subtitle: String,
    imageUrl: String?,
    onClick: (() -> Unit)?,
    onImport: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onImport != null) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.apple_music_import),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(22.dp)
                    .clickable(onClick = onImport),
            )
        }
    }
}

@Composable
private fun AppleMusicTrackRow(
    row: AppleMusicTrackMapping,
    playable: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = playable, onClick = onClick)
            .alpha(if (playable) 1f else 0.45f)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AsyncImage(
            model = row.song.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = row.song.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    row.song.artist.takeIf { it.isNotBlank() },
                    when (row.status) {
                        AppleMusicTrackMatchStatus.APPLE_ONLY -> stringResource(R.string.apple_music_only)
                        AppleMusicTrackMatchStatus.PENDING -> stringResource(R.string.apple_music_matching_one)
                        else -> null
                    },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private enum class AppleMusicSection(val label: Int) {
    PLAYLISTS(R.string.apple_music_playlists),
    SONGS(R.string.apple_music_library_songs),
    FAVORITES(R.string.apple_music_favorite_songs),
    ALBUMS(R.string.apple_music_albums),
    ARTISTS(R.string.apple_music_artists),
    RECENT(R.string.apple_music_recently_added),
    ;

    val selection: AppleMusicBrowseSelection
        get() = when (this) {
            PLAYLISTS -> AppleMusicBrowseSelection.Playlists
            SONGS -> AppleMusicBrowseSelection.Songs
            FAVORITES -> AppleMusicBrowseSelection.FavoriteSongs
            ALBUMS -> AppleMusicBrowseSelection.Albums
            ARTISTS -> AppleMusicBrowseSelection.Artists
            RECENT -> AppleMusicBrowseSelection.RecentlyAdded
        }
}

/** A redacted presentation code, never an exception message from Apple or the transport. */
@Composable
private fun AppleMusicBrowseError.message(): String = stringResource(
    when (this) {
        AppleMusicBrowseError.NOT_CONNECTED -> R.string.apple_music_not_connected
        AppleMusicBrowseError.STOREFRONT_REQUIRED -> R.string.apple_music_storefront_required
        AppleMusicBrowseError.FAVORITES_UNAVAILABLE -> R.string.apple_music_favorites_unavailable
        AppleMusicBrowseError.LOAD_FAILED -> R.string.failed
    },
)
