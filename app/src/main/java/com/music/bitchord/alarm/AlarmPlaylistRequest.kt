package com.music.bitchord.alarm

/** The abstract media id already understood by BitChord's MediaLibrarySession. */
object AlarmPlaylistRequest {
    fun mediaId(playlistId: String): String? =
        playlistId.trim().takeIf { it.isNotEmpty() }?.let { "playlist:$it" }
}
