/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class MusicMultiRowListItemRenderer(
    val title: Runs? = null,
    val subtitle: Runs? = null,
    val description: Runs? = null,
    val thumbnail: ThumbnailRenderer? = null,
    val onTap: NavigationEndpoint? = null,
    val overlay: MusicResponsiveListItemRenderer.Overlay? = null,
    val playbackProgress: PlaybackProgress? = null,
) {
    @Serializable
    data class PlaybackProgress(
        val musicPlaybackProgressRenderer: MusicPlaybackProgressRenderer? = null,
    ) {
        @Serializable
        data class MusicPlaybackProgressRenderer(
            val durationText: Runs? = null,
            val playbackProgressPercentage: Int? = null,
        )
    }
}
