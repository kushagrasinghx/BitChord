package com.music.bitchord.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.music.bitchord.data.canvas.AppleArtworkRepository
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.isYouTubeThumbnailUrl
import com.music.bitchord.data.remote.RemoteArtworkStore
import com.music.bitchord.data.settings.AppSettings

/**
 * A drawable art URL for [song]: official studio album artwork (Apple / Spotify)
 * when available, with clean suppression of low-resolution YouTube video stills
 * when [AppSettings.hideYoutubeThumbnails] is enabled.
 */
@Composable
fun rememberRemoteArtworkUrl(song: Song?): String? {
    val context = LocalContext.current
    val key = song?.videoId
    val hideYoutubeThumbnails by AppSettings.hideYoutubeThumbnails.collectAsState()

    val cached = AppleArtworkRepository.cached(song)
    var resolved by remember(key, hideYoutubeThumbnails) {
        val initial = when {
            !cached?.artworkUrl.isNullOrBlank() -> cached.artworkUrl
            !hideYoutubeThumbnails || !isYouTubeThumbnailUrl(song?.thumbnailUrl) -> song?.thumbnailUrl
            else -> null
        }
        mutableStateOf(initial)
    }

    LaunchedEffect(key, hideYoutubeThumbnails) {
        if (song != null) {
            val art = AppleArtworkRepository.artworkFor(song)
            if (!art?.artworkUrl.isNullOrBlank()) {
                resolved = art.artworkUrl
            } else if (song.thumbnailUrl == null &&
                (song.videoId.startsWith("webdav:") || song.videoId.startsWith("smb:"))
            ) {
                resolved = RemoteArtworkStore.resolveArt(context, song)
            } else if (!hideYoutubeThumbnails || !isYouTubeThumbnailUrl(song.thumbnailUrl)) {
                resolved = song.thumbnailUrl
            } else {
                resolved = null
            }
        }
    }
    return resolved
}
