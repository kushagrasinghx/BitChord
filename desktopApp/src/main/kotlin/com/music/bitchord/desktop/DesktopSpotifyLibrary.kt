package com.music.bitchord.desktop

import com.music.bitchord.data.model.SPOTIFY_MISSING_PREFIX
import com.music.bitchord.data.model.SPOTIFY_PENDING_PREFIX
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.spotify.SPOTIFY_PAGE_PREFIX
import com.music.bitchord.data.spotify.SpotifyImporter
import com.music.bitchord.data.spotify.SpotifyLibrary
import com.music.bitchord.data.spotify.SpotifyPlaylist
import com.music.bitchord.data.spotify.SpotifyTrack
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** The Library row that opens the Spotify account's playlists; the phone's browse id. */
internal const val SPOTIFY_LIBRARY_ID = "app:spotify"

/** The Library grid's title for those playlists, which is also how the grid is told apart. */
internal const val SPOTIFY_SHELF = "Spotify"

/** Matches looked up at once; the phone's figure, gentle enough on YouTube Music's search. */
private const val MATCH_PARALLELISM = 4

/** A card on the Spotify grid, opening a [DesktopCollection] of its tracks. */
internal fun SpotifyPlaylist.toShelfItem() = ShelfItem(
    title = name,
    subtitle = owner ?: SPOTIFY_SHELF,
    thumbnailUrl = imageUrl,
    videoId = null,
    browseId = SPOTIFY_PAGE_PREFIX + id,
)

/** A row for a Spotify track whose YouTube Music version has not been found yet. */
internal fun SpotifyTrack.asPendingSong() = Song(
    videoId = SPOTIFY_PENDING_PREFIX + id,
    title = title,
    artist = artist,
    thumbnailUrl = imageUrl,
    durationText = durationMs.takeIf { it > 0 }?.let { ms -> "%d:%02d".format(ms / 60000, ms / 1000 % 60) },
    albumName = album,
)

/**
 * Fills a Spotify playlist page the way the phone's `MainViewModel` does: Spotify's tracks as each
 * page of them arrives, then every row swapped in place for its YouTube Music match, or marked as
 * having none. [update] applies a change to the page if it is still the one open.
 */
internal suspend fun loadSpotifyPlaylist(
    playlistId: String,
    update: ((DesktopCollection) -> DesktopCollection) -> Unit,
) = coroutineScope {
    launch {
        // The library card only had a thumbnail.
        runCatching { SpotifyLibrary.cover(playlistId) }.getOrNull()
            ?.let { cover -> update { it.copy(thumbnailUrl = cover) } }
    }
    val tracks = SpotifyLibrary.tracks(playlistId) { soFar ->
        update { it.copy(songs = soFar.map(SpotifyTrack::asPendingSong), loading = false) }
    }
    check(tracks.isNotEmpty()) { "This Spotify playlist has no tracks." }
    val gate = Semaphore(MATCH_PARALLELISM)
    tracks.forEachIndexed { index, track ->
        launch {
            gate.withPermit {
                val match = runCatching { SpotifyImporter.matchTrack(track) }.getOrNull()
                val row = match?.copy(thumbnailUrl = match.thumbnailUrl ?: track.imageUrl)
                    ?: track.asPendingSong().copy(videoId = SPOTIFY_MISSING_PREFIX + track.id)
                update { page ->
                    if (index < page.songs.size) {
                        page.copy(songs = page.songs.toMutableList().also { it[index] = row })
                    } else {
                        page
                    }
                }
            }
        }
    }
}
