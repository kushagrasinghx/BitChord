package com.music.bitchord.ui

import com.music.bitchord.data.model.Song

/**
 * Songs picked on one playlist or album page, to act on together — the web
 * player's Shift/⌘-click selection, as a phone does it: started from a song's
 * menu or the page's, then a tap adds or takes out a song.
 *
 * Held by video id, and kept in the page's own order when read back, so the
 * picked songs queue and land in a playlist in the order they're listed.
 */
data class SongSelection(
    /** The page it was made on; another page's rows are never part of it. */
    val browseId: String,
    val videoIds: Set<String> = emptySet(),
) {
    val count: Int get() = videoIds.size

    operator fun contains(song: Song): Boolean = song.videoId in videoIds

    /** In or out, as a tap on the row does. */
    fun toggled(song: Song): SongSelection =
        copy(videoIds = if (song.videoId in videoIds) videoIds - song.videoId else videoIds + song.videoId)

    fun withAll(songs: List<Song>): SongSelection = copy(videoIds = songs.mapTo(LinkedHashSet()) { it.videoId })

    /** The picked songs of [songs], in its order. */
    fun of(songs: List<Song>): List<Song> = songs.filter { it.videoId in videoIds }
}
