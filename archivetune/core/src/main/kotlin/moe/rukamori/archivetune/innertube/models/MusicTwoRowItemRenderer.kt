/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.innertube.models

import kotlinx.serialization.Serializable
import moe.rukamori.archivetune.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_ALBUM
import moe.rukamori.archivetune.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_ARTIST
import moe.rukamori.archivetune.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_AUDIOBOOK
import moe.rukamori.archivetune.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_PLAYLIST

/**
 * Two row: a big thumbnail, a title, and a subtitle
 * Used in [GridRenderer] and [MusicCarouselShelfRenderer]
 * Item type: song, video, album, playlist, artist
 */
@Serializable
data class MusicTwoRowItemRenderer(
    val title: Runs,
    val subtitle: Runs?,
    val subtitleBadges: List<Badges>?,
    val menu: Menu?,
    val thumbnailRenderer: ThumbnailRenderer,
    val navigationEndpoint: NavigationEndpoint,
    val thumbnailOverlay: MusicResponsiveListItemRenderer.Overlay?,
) {
    val watchEndpoint: WatchEndpoint?
        get() =
            navigationEndpoint.anyWatchEndpoint
                ?: thumbnailOverlay
                    ?.musicItemThumbnailOverlayRenderer
                    ?.content
                    ?.musicPlayButtonRenderer
                    ?.playNavigationEndpoint
                    ?.anyWatchEndpoint
    val isEpisode: Boolean
        get() =
            watchEndpoint?.isPodcastEpisodeEndpoint == true ||
                navigationEndpoint.browseEndpoint?.isPodcastEpisodeEndpoint == true ||
                title.runs?.any { it.navigationEndpoint?.browseEndpoint?.isPodcastEpisodeEndpoint == true } == true
    val isPodcast: Boolean
        get() = navigationEndpoint.browseEndpoint?.isPodcastShowEndpoint == true
    val isSong: Boolean
        get() = !isEpisode && navigationEndpoint.endpoint is WatchEndpoint
    val isPlaylist: Boolean
        get() =
            navigationEndpoint.browseEndpoint
                ?.browseEndpointContextSupportedConfigs
                ?.browseEndpointContextMusicConfig
                ?.pageType ==
                MUSIC_PAGE_TYPE_PLAYLIST
    val isAlbum: Boolean
        get() =
            navigationEndpoint.browseEndpoint
                ?.browseEndpointContextSupportedConfigs
                ?.browseEndpointContextMusicConfig
                ?.pageType ==
                MUSIC_PAGE_TYPE_ALBUM ||
                navigationEndpoint.browseEndpoint
                    ?.browseEndpointContextSupportedConfigs
                    ?.browseEndpointContextMusicConfig
                    ?.pageType ==
                MUSIC_PAGE_TYPE_AUDIOBOOK
    val isArtist: Boolean
        get() =
            navigationEndpoint.browseEndpoint
                ?.browseEndpointContextSupportedConfigs
                ?.browseEndpointContextMusicConfig
                ?.pageType ==
                MUSIC_PAGE_TYPE_ARTIST
}
