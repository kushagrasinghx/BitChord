package com.music.bitchord.ui.tv.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import com.music.bitchord.data.LikeState
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.PlaylistPrivacy
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.playback.SleepTimer
import com.music.bitchord.playback.beginRadioQueue
import com.music.bitchord.playback.commitRadioQueue
import com.music.bitchord.playback.playSongs
import com.music.bitchord.playback.toMediaItem
import com.music.bitchord.ui.MainViewModel
import com.music.bitchord.ui.tv.components.TvActivityIndicator
import com.music.bitchord.ui.tv.components.TvArtwork
import com.music.bitchord.ui.tv.components.TvDialog
import com.music.bitchord.ui.tv.components.TvDialogButton
import com.music.bitchord.ui.tv.components.TvListRow
import com.music.bitchord.ui.tv.components.TvSongRow
import com.music.bitchord.ui.tv.components.TvTextField
import com.music.bitchord.ui.tv.components.tvInitialFocus
import com.music.bitchord.ui.tv.theme.TvGlass
import com.music.bitchord.ui.tv.theme.TvType
import kotlinx.coroutines.launch

/**
 * A song's actions from anywhere outside the player: works out whether it's
 * liked and where its album and artist pages are, then shows the menu.
 */
@Composable
fun TvSongMenu(
    song: Song,
    viewModel: MainViewModel,
    mediaController: MediaController?,
    onNavigateToDetail: (browseId: String, title: String, subtitle: String, thumbnailUrl: String?, type: BrowseType) -> Unit,
    onDismiss: () -> Unit,
) {
    val overrides by LikeState.overrides.collectAsState()
    val isLiked = overrides[song.videoId] == LikeStatus.LIKE
    TvSongActionMenuDialog(
        song = song,
        isLiked = isLiked,
        viewModel = viewModel,
        mediaController = mediaController,
        onToggleLike = { viewModel.toggleLike(song.videoId) },
        onOpenAlbum = song.albumId?.let { id ->
            { _: String -> onNavigateToDetail(id, song.albumName.orEmpty(), song.artist, song.thumbnailUrl, BrowseType.ALBUM) }
        },
        onOpenArtist = song.artistId?.let { id ->
            { _: String -> onNavigateToDetail(id, song.artist, "", null, BrowseType.ARTIST) }
        },
        onDismiss = onDismiss,
    )
}

/**
 * The tvOS action sheet for a track: the song itself at the top, then a short
 * list of plain actions — one line each, a glyph on the right, the white
 * platter on whichever has focus.
 */
@Composable
fun TvSongActionMenuDialog(
    song: Song,
    isLiked: Boolean,
    viewModel: MainViewModel,
    mediaController: MediaController?,
    onToggleLike: () -> Unit,
    onOpenAlbum: ((String) -> Unit)? = null,
    onOpenArtist: ((String) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var sheet by remember { mutableStateOf(SongSheet.Actions) }
    val sleepDeadline by SleepTimer.deadline.collectAsState()
    val sleepAfterTrack by SleepTimer.afterTrack.collectAsState()
    val nerdStats by AppSettings.showNerdStats.collectAsState()

    when (sheet) {
        SongSheet.Playlist -> {
            TvAddToPlaylistDialog(song = song, viewModel = viewModel, onDismiss = onDismiss)
            return
        }
        SongSheet.Sleep -> {
            TvSleepTimerDialog(onDismiss = onDismiss)
            return
        }
        SongSheet.Equalizer -> {
            TvEqualizerDialog(onDismiss = onDismiss)
            return
        }
        SongSheet.Actions -> Unit
    }

    TvDialog(title = song.title, message = song.artist.ifBlank { null }, onDismissRequest = onDismiss) {
        TvArtwork(
            url = song.thumbnailUrl,
            px = 320,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .padding(bottom = 22.dp)
                .size(96.dp),
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                TvListRow(
                    title = "Play Next",
                    trailingIcon = Icons.AutoMirrored.Rounded.QueueMusic,
                    modifier = Modifier.tvInitialFocus(),
                    onClick = {
                        mediaController?.let { it.addMediaItem(it.currentMediaItemIndex + 1, song.toMediaItem()) }
                        onDismiss()
                    },
                )
            }
            item {
                TvListRow(
                    title = "Play Last",
                    trailingIcon = Icons.Rounded.PlaylistPlay,
                    onClick = {
                        mediaController?.addMediaItem(song.toMediaItem())
                        onDismiss()
                    },
                )
            }
            item {
                TvListRow(
                    title = "Start Station",
                    trailingIcon = Icons.Rounded.Radio,
                    onClick = {
                        val controller = mediaController
                        if (controller != null) scope.launch { controller.startStation(song) }
                        onDismiss()
                    },
                )
            }
            item {
                TvListRow(
                    title = if (isLiked) "Unlove" else "Love",
                    trailingIcon = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    onClick = {
                        onToggleLike()
                        onDismiss()
                    },
                )
            }
            item {
                TvListRow(
                    title = "Add to Playlist…",
                    trailingIcon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    onClick = { sheet = SongSheet.Playlist },
                )
            }
            if (onOpenAlbum != null) {
                item {
                    TvListRow(
                        title = "Go to Album",
                        trailingIcon = Icons.Rounded.Album,
                        onClick = {
                            onDismiss()
                            onOpenAlbum(song.albumId.orEmpty())
                        },
                    )
                }
            }
            if (onOpenArtist != null) {
                item {
                    TvListRow(
                        title = "Go to Artist",
                        trailingIcon = Icons.Rounded.Person,
                        onClick = {
                            onDismiss()
                            onOpenArtist(song.artistId.orEmpty())
                        },
                    )
                }
            }
            item {
                TvListRow(
                    title = "Equalizer",
                    trailingIcon = Icons.Rounded.Equalizer,
                    onClick = { sheet = SongSheet.Equalizer },
                )
            }
            item {
                TvListRow(
                    title = "Sleep Timer",
                    value = if (sleepDeadline != null || sleepAfterTrack) "On" else "Off",
                    trailingIcon = Icons.Rounded.Timer,
                    onClick = { sheet = SongSheet.Sleep },
                )
            }
            item {
                TvListRow(
                    title = "Stats for Nerds",
                    value = if (nerdStats) "On" else "Off",
                    trailingIcon = Icons.Rounded.Info,
                    onClick = { AppSettings.setShowNerdStats(!nerdStats) },
                )
            }
        }
    }
}

private enum class SongSheet { Actions, Playlist, Sleep, Equalizer }

/**
 * A station seeded from [song], as the phone builds one: YouTube's radio for
 * it, with the song itself first. If it's already the one playing, it keeps
 * playing and only the rest of the queue is replaced.
 */
private suspend fun MediaController.startStation(song: Song) {
    val related = YtMusicRepository.radio(song.videoId).getOrNull()
        ?.filterNot { it.videoId == song.videoId }
        ?: return
    beginRadioQueue()
    val index = currentMediaItemIndex
    if (index >= 0 && currentMediaItem?.mediaId == song.videoId) {
        if (index + 1 < mediaItemCount) removeMediaItems(index + 1, mediaItemCount)
        if (index > 0) removeMediaItems(0, index)
        addMediaItems(1, related.map { it.toMediaItem() })
    } else {
        playSongs(listOf(song) + related, 0)
    }
    commitRadioQueue()
}

/** Picks one of the account's playlists for the song, or names a new one for it. */
@Composable
fun TvAddToPlaylistDialog(
    song: Song,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
) {
    val playlists by viewModel.playlists.collectAsState()
    val loading by viewModel.playlistsLoading.collectAsState()
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { if (playlists.isEmpty()) viewModel.loadPlaylists() }

    val done = result
    when {
        done != null -> TvDialog(title = done, onDismissRequest = onDismiss) {
            TvDialogButton(text = "OK", onClick = onDismiss, initialFocus = true)
        }
        creating -> TvDialog(
            title = "New Playlist",
            message = "It will be private, with this song in it.",
            onDismissRequest = { creating = false },
        ) {
            val create = {
                if (name.isNotBlank()) {
                    viewModel.createPlaylist(name.trim(), PlaylistPrivacy.PRIVATE, song)
                    result = "Added to “${name.trim()}”"
                }
            }
            TvTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = "Playlist Name",
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
                onImeAction = create,
                modifier = Modifier.tvInitialFocus(),
            )
            Spacer(modifier = Modifier.size(20.dp))
            TvDialogButton(text = "Create", enabled = name.isNotBlank(), onClick = create)
            Spacer(modifier = Modifier.size(8.dp))
            TvDialogButton(text = "Cancel", onClick = { creating = false })
        }
        else -> TvDialog(title = "Add to Playlist", message = song.title, onDismissRequest = onDismiss) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    TvListRow(
                        title = "New Playlist…",
                        trailingIcon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                        modifier = Modifier.tvInitialFocus(),
                        onClick = { creating = true },
                    )
                }
                if (loading && playlists.isEmpty()) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.Center) {
                            TvActivityIndicator(size = 26.dp)
                        }
                    }
                }
                items(playlists, key = { it.playlistId }) { playlist ->
                    TvListRow(
                        title = playlist.title,
                        value = playlist.subtitle.takeIf { it.isNotBlank() },
                        onClick = {
                            viewModel.addToPlaylists(listOf(playlist), song) { added, already, _ ->
                                result = when {
                                    added > 0 -> "Added to “${playlist.title}”"
                                    already > 0 -> "Already in “${playlist.title}”"
                                    else -> "Couldn't add to “${playlist.title}”"
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

/** Stop playback after a while, or at the end of this song. */
@Composable
fun TvSleepTimerDialog(onDismiss: () -> Unit) {
    val deadline by SleepTimer.deadline.collectAsState()
    val afterTrack by SleepTimer.afterTrack.collectAsState()
    val active = deadline != null || afterTrack

    TvDialog(title = "Sleep Timer", message = "Stop playing music after", onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(15, 30, 45, 60).forEachIndexed { index, minutes ->
                TvListRow(
                    title = "$minutes Minutes",
                    modifier = if (index == 0) Modifier.tvInitialFocus() else Modifier,
                    onClick = {
                        SleepTimer.start(minutes)
                        onDismiss()
                    },
                )
            }
            TvListRow(
                title = "End of Current Song",
                trailingIcon = if (afterTrack) Icons.Rounded.Check else null,
                onClick = {
                    SleepTimer.startAfterTrack()
                    onDismiss()
                },
            )
            if (active) {
                TvListRow(
                    title = "Turn Off Timer",
                    destructive = true,
                    onClick = {
                        SleepTimer.cancel()
                        onDismiss()
                    },
                )
            }
        }
    }
}
