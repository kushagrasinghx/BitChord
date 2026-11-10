/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.spotify.models

import kotlinx.serialization.Serializable

@Serializable
sealed class SpotifyLibraryItem {
    abstract val uri: String

    @Serializable
    data class Playlist(
        val playlist: SpotifyPlaylist,
    ) : SpotifyLibraryItem() {
        override val uri: String get() = playlist.uri ?: "spotify:playlist:${playlist.id}"
    }

    @Serializable
    data class Folder(
        val folder: SpotifyLibraryFolder,
    ) : SpotifyLibraryItem() {
        override val uri: String get() = folder.uri
    }
}

@Serializable
data class SpotifyLibraryFolder(
    val uri: String,
    val name: String,
    val totalChildren: Int = 0,
)
