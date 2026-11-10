/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.innertube.models.response

import kotlinx.serialization.Serializable

@Serializable
public data class AddItemYouTubePlaylistResponse(
    public val status: String,
    public val playlistEditResults: List<PlaylistEditResult> = emptyList(),
) {
    @Serializable
    public data class PlaylistEditResult(
        public val playlistEditVideoAddedResultData: PlaylistEditVideoAddedResultData? = null,
    ) {
        @Serializable
        public data class PlaylistEditVideoAddedResultData(
            public val setVideoId: String? = null,
            public val videoId: String? = null,
        )
    }
}
