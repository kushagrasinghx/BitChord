/*
 * BRIDGE (replaces ArchiveTune's lyrics/LyricsHelper.kt).
 *
 * The lyrics the player displays arrive live from the host bridge (see
 * PlayerConnection.currentLyrics). What this class answers is the lyrics
 * *menu* — the provider search and the provider picker — which this port
 * leaves to whatever the host cares to surface later; every search comes
 * back empty for now, and saves land in the bridge database's store.
 */

package moe.rukamori.archivetune.lyrics

import moe.rukamori.archivetune.models.MediaMetadata

data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)

class LyricsHelper {
    suspend fun getLyrics(
        mediaMetadata: MediaMetadata,
        preferredProviderOnly: Boolean = false,
        forceRefresh: Boolean = false,
    ): String =
        getLyricsWithProvider(
            mediaMetadata = mediaMetadata,
            preferredProviderOnly = preferredProviderOnly,
            forceRefresh = forceRefresh,
        ).lyrics

    suspend fun getLyricsWithProvider(
        mediaMetadata: MediaMetadata,
        preferredProviderOnly: Boolean = false,
        forceRefresh: Boolean = false,
    ): LyricsResult = LyricsResult(providerName = "", lyrics = "")

    suspend fun getAllLyrics(
        mediaId: String,
        songTitle: String,
        songArtists: String,
        songAlbum: String?,
        duration: Int,
        forceRefresh: Boolean = false,
        callback: (LyricsResult) -> Unit,
    ) {
    }
}
