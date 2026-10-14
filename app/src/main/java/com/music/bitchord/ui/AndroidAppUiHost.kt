package com.music.bitchord.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import com.music.bitchord.data.LocalMediaRepository
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.LibraryViewType
import com.music.bitchord.download.Downloads

/** The shared pages' settings and downloads, answered from the phone's own stores. */
object AndroidAppUiHost : AppUiHost {
    override val swipeToPlayNext = AppSettings.swipeToPlayNext
    override val homeRecentsViewType = AppSettings.homeRecentsViewType
    override fun setHomeRecentsViewType(value: LibraryViewType) = AppSettings.setHomeRecentsViewType(value)
    override val pinnedPlaylists = AppSettings.pinnedPlaylists
    override val librarySort = AppSettings.librarySort

    @Composable
    override fun downloadedIds(): Set<String> {
        val saved by Downloads.saved.collectAsStateWithLifecycle()
        val liveSongs by LocalMediaRepository.liveDiscoveredSongs.collectAsStateWithLifecycle()
        return remember(saved, liveSongs) {
            val allIds = HashSet<String>(saved.keys)
            for (song in liveSongs) {
                if (song.videoId.length == 11 && !song.videoId.startsWith("local:")) {
                    allIds.add(song.videoId)
                }
            }
            allIds
        }
    }

    override fun isTrackDownloaded(song: Song): Boolean {
        if (song.localUri != null || song.localPath != null || song.videoId.startsWith("local:")) return true
        if (song.videoId in Downloads.saved.value) return true
        return LocalMediaRepository.hasMatchingTrack(song.artist, song.title)
    }
}
