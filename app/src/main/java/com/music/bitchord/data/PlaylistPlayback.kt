package com.music.bitchord.data

import com.music.bitchord.data.model.Song

data class PlaylistPlayback(val songs: List<Song>, val index: Int)

/**
 * Keeps a cached playlist's full membership separate from the queue it can play
 * offline. [saved] is a snapshot of readable downloaded files supplied by the
 * caller; this function does no filesystem work. Source-prefixed rows retain
 * their existing playback behavior, since an SMB or WebDAV server may remain
 * reachable without an internet connection.
 */
fun playlistPlayback(
    songs: List<Song>,
    index: Int,
    online: Boolean,
    saved: Map<String, String>,
): PlaylistPlayback? {
    if (index !in songs.indices) return null
    if (online) return PlaylistPlayback(songs, index)

    fun available(song: Song): Boolean = song.localUri != null ||
        ':' in song.videoId || !saved[song.videoId].isNullOrBlank()

    if (!available(songs[index])) return null
    val playable = ArrayList<Song>(songs.size)
    var playableIndex = 0
    songs.forEachIndexed { originalIndex, song ->
        if (available(song)) {
            if (originalIndex < index) playableIndex++
            playable += if (song.localUri == null && !saved[song.videoId].isNullOrBlank()) {
                song.copy(localUri = saved[song.videoId])
            } else {
                song
            }
        }
    }
    return PlaylistPlayback(playable, playableIndex)
}
