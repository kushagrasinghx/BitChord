/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.innertube.models.body

import kotlinx.serialization.Serializable
import moe.rukamori.archivetune.innertube.models.Context

@Serializable
data class PlayerBody(
    val context: Context,
    val videoId: String,
    val playlistId: String?,
    val racyCheckOk: Boolean = true,
    val contentCheckOk: Boolean = true,
    // Content playback nonce — the random 16-char token SimpMusic's YouTube.player()
    // generates per request and threads through the response's playback-tracking URLs
    // (see simpstream/SimpMusicPlayer.kt). Ported with the SimpMusic stream resolution.
    val cpn: String? = null,
    val playbackContext: PlaybackContext? = null,
    val serviceIntegrityDimensions: ServiceIntegrityDimensions? = null,
) {
    @Serializable
    data class PlaybackContext(
        val contentPlaybackContext: ContentPlaybackContext,
    ) {
        @Serializable
        data class ContentPlaybackContext(
            val signatureTimestamp: Int? = null,
            val vis: Int = 0,
            val splay: Boolean = false,
            val lactMilliseconds: String = "-1",
        )
    }

    @Serializable
    data class ServiceIntegrityDimensions(
        val poToken: String,
    )
}
