package com.music.bitchord.download

import com.music.bitchord.data.model.Song

/**
 * A `SavedCollection` (app/.../download/Downloads.kt) with its surviving
 * tracks attached, ready to draw. In commonMain only because
 * [com.music.bitchord.data.LocalGroups.albumsOf] takes it; the record and the
 * downloader stay on Android.
 */
data class DownloadedCollection(
    val id: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val playlist: Boolean,
    val songs: List<Song>,
)
