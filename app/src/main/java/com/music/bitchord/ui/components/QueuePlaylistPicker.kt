package com.music.bitchord.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.music.bitchord.R
import com.music.bitchord.data.OfflinePlaylists
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UserPlaylist
import com.music.bitchord.ui.MainViewModel

/**
 * One picker transaction over a frozen queue selection. Submission removes the
 * sheet, not this host: only an acknowledged result calls [onComplete]. The
 * base sheet's asynchronous hide callback must never turn submission into a
 * cancellation while account writes are still in flight.
 */
@Composable
fun QueuePlaylistPicker(
    songs: List<Song>,
    viewModel: MainViewModel,
    onNotice: (String) -> Unit,
    onComplete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val selected = remember { songs.toList() }
    val device = remember { selected.isNotEmpty() && selected.all(::usesDevicePlaylist) }
    val compatible = remember { if (device) selected else selected.filterNot(::usesDevicePlaylist) }
    val incompatible = selected.size - compatible.size
    var submitted by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    val cancel: () -> Unit = {
        if (!submitted && !finished) {
            finished = true
            onDismiss()
        }
    }
    val complete: (Int, Int, Int) -> Unit = { added, alreadyThere, failed ->
        if (!finished) {
            finished = true
            val base = context.getString(R.string.queue_playlist_added, added, selected.size)
            onNotice(
                if (incompatible == 0 && alreadyThere == 0 && failed == 0) base else context.getString(
                    R.string.queue_playlist_partial, base, incompatible, alreadyThere, failed,
                ),
            )
            onComplete()
        }
    }

    LaunchedEffect(Unit) {
        if (selected.isEmpty()) cancel() else if (!device) viewModel.loadPlaylists()
    }
    // Keep the transaction composed even though the picker is no longer drawn.
    if (submitted || finished || selected.isEmpty()) return

    val choices = if (device) {
        val playlists by OfflinePlaylists.playlists.collectAsState()
        remember(playlists, context) {
            playlists.map { playlist ->
                UserPlaylist(
                    playlistId = playlist.id,
                    title = playlist.title,
                    subtitle = context.resources.getQuantityString(
                        R.plurals.song_count_plural, playlist.songs.size, playlist.songs.size,
                    ),
                    thumbnailUrl = playlist.songs.firstNotNullOfOrNull { it.thumbnailUrl }
                        .takeUnless { playlist.isLiked },
                )
            }
        }
    } else {
        viewModel.playlists.collectAsState().value
    }
    val loading = if (device) false else viewModel.playlistsLoading.collectAsState().value

    PlaylistPickerSheet(
        playlists = choices,
        loading = loading,
        songs = selected,
        offline = device,
        onDismiss = cancel,
        onAdd = { picked ->
            if (!submitted && !finished) {
                submitted = true
                when {
                    compatible.isEmpty() -> complete(0, 0, 0)
                    device -> {
                        val counts = addToDevicePlaylists(picked, compatible)
                        complete(counts.first, counts.second, counts.third)
                    }
                    else -> viewModel.addToPlaylists(picked, compatible, complete)
                }
            }
        },
        onCreate = { title, privacy ->
            if (!submitted && !finished) {
                submitted = true
                when {
                    compatible.isEmpty() -> complete(0, 0, 0)
                    device -> {
                        val created = runCatching { OfflinePlaylists.create(title, compatible) }.getOrNull()
                        val stored = created?.let { OfflinePlaylists.get(it.browseId) }
                            ?.songs?.mapNotNullTo(HashSet()) { it.localUri }.orEmpty()
                        val counted = HashSet<String>()
                        var added = 0
                        var alreadyThere = 0
                        var failed = 0
                        compatible.forEach { song ->
                            val uri = song.localUri
                            when {
                                uri == null || uri !in stored -> failed++
                                counted.add(uri) -> added++
                                else -> alreadyThere++
                            }
                        }
                        complete(added, alreadyThere, failed)
                    }
                    else -> viewModel.createPlaylist(title, privacy, compatible, complete)
                }
            }
        },
    )
}

/** Match MainActivity's existing single-song dispatcher, not generic locality. */
private fun usesDevicePlaylist(song: Song): Boolean = song.localUri != null &&
    (song.videoId.startsWith("content://") || song.videoId.startsWith("file://"))

/**
 * The store's AddResult counts playlists, not songs. Snapshot membership before
 * its existing mutation, then verify the stored result. Repeat queue rows count
 * as already present, never as another insertion of the same local URI.
 */
private fun addToDevicePlaylists(
    picked: List<UserPlaylist>,
    songs: List<Song>,
): Triple<Int, Int, Int> {
    val ids = picked.map { it.playlistId }.distinct()
    if (ids.isEmpty()) return Triple(0, 0, songs.size)
    val before = OfflinePlaylists.playlists.value.associateBy { it.id }
    val known = ids.associateWith { id ->
        before[id]?.songs?.mapNotNullTo(HashSet()) { it.localUri }
    }
    // Simulate the same local-URI deduplication in selection order, before add.
    val expectedFresh = songs.map { song ->
        ids.associateWith { id ->
            song.localUri?.let { known[id]?.add(it) } == true
        }
    }
    runCatching { OfflinePlaylists.add(ids, songs) }
    val after = OfflinePlaylists.playlists.value.associate { playlist ->
        playlist.id to playlist.songs.mapNotNullTo(HashSet()) { it.localUri }
    }
    var added = 0
    var alreadyThere = 0
    var failed = 0
    songs.forEachIndexed { index, song ->
        val uri = song.localUri
        var anyAdded = false
        var anyFailed = false
        ids.forEach { id ->
            when {
                uri == null || before[id] == null || after[id]?.contains(uri) != true -> anyFailed = true
                expectedFresh[index].getValue(id) -> anyAdded = true
            }
        }
        if (anyAdded) added++
        if (anyFailed) failed++
        if (!anyAdded && !anyFailed) alreadyThere++
    }
    // As in the account helper, partial destination failures overlap added.
    return Triple(added, alreadyThere, failed)
}
