package com.music.bitchord.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.remote.RemoteArtworkStore

/**
 * A drawable art URL for [song]: its own thumbnail when it has one, else the
 * embedded picture pulled out of the remote file and cached — else null.
 *
 * The extraction only ever runs for remote-library tracks with no thumbnail;
 * every other row resolves synchronously to what it already had, so this is
 * safe to call from shared components like [SongRow] that draw every library
 * in the app.
 *
 * Keyed on the track id *and* the thumbnail it came with, so a recycled row
 * re-resolves instead of flashing a stranger's cover. The id alone is not
 * enough: a downloaded copy of a track is filed under the id of the one it was
 * downloaded from, so the same id turns up twice in a queue with a local
 * `content://` cover on one row and the streaming `https://` one on the other.
 * Keyed on the id alone, the cover of whichever came first stayed on screen
 * while the title and the audio moved on to the other.
 */
@Composable
fun rememberRemoteArtworkUrl(song: Song?): String? {
    val context = LocalContext.current
    val key = song?.let { it.videoId to it.thumbnailUrl }
    var resolved by remember(key) { mutableStateOf(song?.thumbnailUrl) }
    LaunchedEffect(key) {
        if (song != null && song.thumbnailUrl == null &&
            (song.videoId.startsWith("webdav:") || song.videoId.startsWith("smb:"))
        ) {
            resolved = RemoteArtworkStore.resolveArt(context, song)
        }
    }
    return resolved
}
