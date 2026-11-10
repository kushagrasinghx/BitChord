/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.innertube.pages

import moe.rukamori.archivetune.innertube.models.Album
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.Artist
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.BrowseEndpoint
import moe.rukamori.archivetune.innertube.models.EpisodeItem
import moe.rukamori.archivetune.innertube.models.MusicCarouselShelfRenderer
import moe.rukamori.archivetune.innertube.models.MusicCardShelfRenderer
import moe.rukamori.archivetune.innertube.models.MusicMultiRowListItemRenderer
import moe.rukamori.archivetune.innertube.models.MusicShelfRenderer
import moe.rukamori.archivetune.innertube.models.MusicTwoRowItemRenderer
import moe.rukamori.archivetune.innertube.models.PODCAST_SHOW_BROWSE_PREFIX
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.PodcastItem
import moe.rukamori.archivetune.innertube.models.SectionListRenderer
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.models.filterExplicit
import moe.rukamori.archivetune.innertube.models.oddElements
import moe.rukamori.archivetune.innertube.models.toArtists
import moe.rukamori.archivetune.innertube.utils.parseTime

data class HomePage(
    val chips: List<Chip>?,
    val sections: List<Section>,
    val continuation: String? = null,
) {
    data class Chip(
        val title: String,
        val endpoint: BrowseEndpoint?,
        val deselectEndPoint: BrowseEndpoint?,
    ) {
        companion object {
            fun fromChipCloudChipRenderer(renderer: SectionListRenderer.Header.ChipCloudRenderer.Chip): Chip? {
                return Chip(
                    title =
                        renderer.chipCloudChipRenderer.text
                            ?.runs
                            ?.firstOrNull()
                            ?.text ?: return null,
                    endpoint = renderer.chipCloudChipRenderer.navigationEndpoint.browseEndpoint,
                    deselectEndPoint = renderer.chipCloudChipRenderer.onDeselectedCommand?.browseEndpoint,
                )
            }
        }
    }

    data class Section(
        val title: String,
        val label: String?,
        val thumbnail: String?,
        val endpoint: BrowseEndpoint?,
        val items: List<YTItem>,
        val numItemsPerColumn: Int? = null,
    ) {
        companion object {
            fun fromMusicCarouselShelfRenderer(renderer: MusicCarouselShelfRenderer): Section? {
                return Section(
                    title =
                        renderer.header
                            ?.musicCarouselShelfBasicHeaderRenderer
                            ?.title
                            ?.runs
                            ?.firstOrNull()
                            ?.text ?: return null,
                    label =
                        renderer.header.musicCarouselShelfBasicHeaderRenderer.strapline
                            ?.runs
                            ?.firstOrNull()
                            ?.text,
                    thumbnail =
                        renderer.header.musicCarouselShelfBasicHeaderRenderer.thumbnail
                            ?.musicThumbnailRenderer
                            ?.getThumbnailUrl(),
                    endpoint =
                        renderer.header.musicCarouselShelfBasicHeaderRenderer.moreContentButton
                            ?.buttonRenderer
                            ?.navigationEndpoint
                            ?.browseEndpoint,
                    items =
                        renderer.contents
                            .mapNotNull { content ->
                                content.musicTwoRowItemRenderer?.let { fromMusicTwoRowItemRenderer(it) }
                                    ?: content.musicResponsiveListItemRenderer?.let { SearchPage.toYTItem(it) }
                                    ?: content.musicMultiRowListItemRenderer?.let { fromMusicMultiRowListItemRenderer(it) }
                            }.ifEmpty {
                                return null
                            },
                    numItemsPerColumn = renderer.numItemsPerColumn,
                )
            }

            fun fromMusicShelfRenderer(renderer: MusicShelfRenderer): Section? {
                val title = renderer.title?.runs?.firstOrNull()?.text ?: return null
                val items =
                    renderer.contents.orEmpty().mapNotNull { content ->
                        content.musicResponsiveListItemRenderer?.let { SearchPage.toYTItem(it) }
                            ?: content.musicMultiRowListItemRenderer?.let { fromMusicMultiRowListItemRenderer(it) }
                    }
                if (items.isEmpty()) return null

                return Section(
                    title = title,
                    label = null,
                    thumbnail = null,
                    endpoint =
                        renderer.moreContentButton
                            ?.buttonRenderer
                            ?.navigationEndpoint
                            ?.browseEndpoint,
                    items = items,
                )
            }

            fun fromMusicCardShelfRenderer(renderer: MusicCardShelfRenderer): Section? {
                val items =
                    renderer.contents.orEmpty().mapNotNull { content ->
                        content.musicResponsiveListItemRenderer?.let { SearchPage.toYTItem(it) }
                    }
                if (items.isEmpty()) return null

                val title =
                    renderer.header
                        ?.musicCardShelfHeaderBasicRenderer
                        ?.title
                        ?.runs
                        ?.joinToString(separator = "") { it.text }
                        ?.takeIf(String::isNotBlank)
                        ?: renderer.title.runs
                            ?.joinToString(separator = "") { it.text }
                            ?.takeIf(String::isNotBlank)
                        ?: return null

                return Section(
                    title = title,
                    label = renderer.subtitle.runs?.joinToString(separator = "") { it.text }?.takeIf(String::isNotBlank),
                    thumbnail = renderer.thumbnail.musicThumbnailRenderer?.getThumbnailUrl(),
                    endpoint = renderer.onTap.browseEndpoint,
                    items = items,
                )
            }

            private fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer): YTItem? {
                return when {
                    renderer.isEpisode -> {
                        val endpoint = renderer.watchEndpoint ?: return null
                        val thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getBestThumbnail() ?: return null
                        val subtitleRuns = renderer.subtitle?.runs.orEmpty()
                        val podcast = subtitleRuns.toPodcastArtist()
                        val durationText = subtitleRuns.asReversed().firstNotNullOfOrNull { it.text.takeIf { text -> text.parseTime() != null } }
                        EpisodeItem(
                            id = endpoint.videoId ?: return null,
                            browseId =
                                renderer.title.runs
                                    ?.firstNotNullOfOrNull { it.navigationEndpoint?.browseEndpoint }
                                    ?.takeIf { it.isPodcastEpisodeEndpoint }
                                    ?.browseId,
                            title = renderer.title.runs?.joinToString(separator = "") { it.text }?.takeIf(String::isNotBlank) ?: return null,
                            podcast = podcast,
                            description = null,
                            dateText = subtitleRuns.episodeDateText(podcast?.name),
                            durationText = durationText,
                            duration = durationText?.parseTime(),
                            thumbnail = thumbnail.normalizedUrl,
                            endpoint = endpoint,
                            thumbnailWidth = thumbnail.width,
                            thumbnailHeight = thumbnail.height,
                        )
                    }

                    renderer.isPodcast -> {
                        val endpoint = renderer.navigationEndpoint.browseEndpoint ?: return null
                        val thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getBestThumbnail()
                        PodcastItem(
                            browseId = endpoint.browseId,
                            playlistId =
                                renderer.watchEndpoint?.playlistId
                                    ?: endpoint.browseId.removePrefix(PODCAST_SHOW_BROWSE_PREFIX).takeIf(String::isNotBlank),
                            title = renderer.title.runs?.joinToString(separator = "") { it.text }?.takeIf(String::isNotBlank) ?: return null,
                            author = renderer.subtitle?.runs.orEmpty().toPodcastAuthor(),
                            thumbnail = thumbnail?.normalizedUrl,
                            thumbnailWidth = thumbnail?.width,
                            thumbnailHeight = thumbnail?.height,
                        )
                    }

                    renderer.isSong -> {
                        val subtitleRuns = renderer.subtitle?.runs ?: return null
                        val artists = subtitleRuns.toArtists()
                        val thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getBestThumbnail() ?: return null
                        val endpoint =
                            renderer.navigationEndpoint.anyWatchEndpoint
                                ?: renderer.thumbnailOverlay
                                    ?.musicItemThumbnailOverlayRenderer
                                    ?.content
                                    ?.musicPlayButtonRenderer
                                    ?.playNavigationEndpoint
                                    ?.anyWatchEndpoint
                        SongItem(
                            id = endpoint?.videoId ?: return null,
                            title =
                                renderer.title.runs
                                    ?.firstOrNull()
                                    ?.text ?: return null,
                            artists = artists,
                            album =
                                subtitleRuns
                                    .firstOrNull { run ->
                                        run.navigationEndpoint
                                            ?.browseEndpoint
                                            ?.browseId
                                            ?.startsWith("MPREb_") == true
                                    }?.let { run ->
                                        val endpoint = run.navigationEndpoint?.browseEndpoint ?: return null
                                        Album(
                                            name = run.text,
                                            id = endpoint.browseId,
                                        )
                                    },
                            duration = null,
                            thumbnail = thumbnail.normalizedUrl,
                            thumbnailWidth = thumbnail.width,
                            thumbnailHeight = thumbnail.height,
                            explicit =
                                renderer.subtitleBadges?.any {
                                    it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                                } == true,
                            endpoint = endpoint,
                        )
                    }

                    renderer.isAlbum -> {
                        val thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getBestThumbnail() ?: return null
                        AlbumItem(
                            browseId = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
                            playlistId =
                                renderer.thumbnailOverlay
                                    ?.musicItemThumbnailOverlayRenderer
                                    ?.content
                                    ?.musicPlayButtonRenderer
                                    ?.playNavigationEndpoint
                                    ?.watchPlaylistEndpoint
                                    ?.playlistId ?: return null,
                            title =
                                renderer.title.runs
                                    ?.firstOrNull()
                                    ?.text ?: return null,
                            artists =
                                renderer.subtitle?.runs?.oddElements()?.drop(1)?.map {
                                    Artist(
                                        name = it.text,
                                        id = it.navigationEndpoint?.browseEndpoint?.browseId,
                                    )
                                },
                            year = null,
                            thumbnail = thumbnail.normalizedUrl,
                            thumbnailWidth = thumbnail.width,
                            thumbnailHeight = thumbnail.height,
                            explicit =
                                renderer.subtitleBadges?.find {
                                    it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                                } != null,
                        )
                    }

                    renderer.isPlaylist -> {
                        val thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getBestThumbnail() ?: return null
                        PlaylistItem(
                            id =
                                renderer.navigationEndpoint.browseEndpoint
                                    ?.browseId
                                    ?.removePrefix("VL") ?: return null,
                            title =
                                renderer.title.runs
                                    ?.firstOrNull()
                                    ?.text ?: return null,
                            author =
                                Artist(
                                    name =
                                        renderer.subtitle
                                            ?.runs
                                            ?.lastOrNull()
                                            ?.text ?: return null,
                                    id = null,
                                ),
                            songCountText = null,
                            thumbnail = thumbnail.normalizedUrl,
                            thumbnailWidth = thumbnail.width,
                            thumbnailHeight = thumbnail.height,
                            playEndpoint =
                                renderer.thumbnailOverlay
                                    ?.musicItemThumbnailOverlayRenderer
                                    ?.content
                                    ?.musicPlayButtonRenderer
                                    ?.playNavigationEndpoint
                                    ?.watchPlaylistEndpoint ?: return null,
                            shuffleEndpoint =
                                renderer.menu
                                    ?.menuRenderer
                                    ?.items
                                    ?.find {
                                        it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE"
                                    }?.menuNavigationItemRenderer
                                    ?.navigationEndpoint
                                    ?.watchPlaylistEndpoint ?: return null,
                            radioEndpoint =
                                renderer.menu.menuRenderer.items
                                    .find {
                                        it.menuNavigationItemRenderer?.icon?.iconType == "MIX"
                                    }?.menuNavigationItemRenderer
                                    ?.navigationEndpoint
                                    ?.watchPlaylistEndpoint,
                        )
                    }

                    renderer.isArtist -> {
                        val thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getBestThumbnail() ?: return null
                        ArtistItem(
                            id = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
                            title =
                                renderer.title.runs
                                    ?.lastOrNull()
                                    ?.text ?: return null,
                            thumbnail = thumbnail.normalizedUrl,
                            thumbnailWidth = thumbnail.width,
                            thumbnailHeight = thumbnail.height,
                            shuffleEndpoint =
                                renderer.menu
                                    ?.menuRenderer
                                    ?.items
                                    ?.find {
                                        it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE"
                                    }?.menuNavigationItemRenderer
                                    ?.navigationEndpoint
                                    ?.watchPlaylistEndpoint ?: return null,
                            radioEndpoint =
                                renderer.menu.menuRenderer.items
                                    .find {
                                        it.menuNavigationItemRenderer?.icon?.iconType == "MIX"
                                    }?.menuNavigationItemRenderer
                                    ?.navigationEndpoint
                                    ?.watchPlaylistEndpoint ?: return null,
                        )
                    }

                    else -> {
                        null
                    }
                }
            }

            private fun List<moe.rukamori.archivetune.innertube.models.Run>.toPodcastArtist(): Artist? =
                firstNotNullOfOrNull { run ->
                    run.navigationEndpoint
                        ?.browseEndpoint
                        ?.takeIf { it.isPodcastShowEndpoint || it.isArtistEndpoint || it.browseId.startsWith("UC") }
                        ?.let { Artist(name = run.text, id = it.browseId) }
                }

            private fun List<moe.rukamori.archivetune.innertube.models.Run>.toPodcastAuthor(): Artist? =
                toPodcastArtist()
                    ?: firstOrNull { run -> run.isPodcastMetadataText() && run.text.parseTime() == null }
                        ?.let { run -> Artist(name = run.text.trim(), id = run.navigationEndpoint?.browseEndpoint?.browseId) }

            private fun List<moe.rukamori.archivetune.innertube.models.Run>.episodeDateText(podcastName: String?): String? =
                firstOrNull { run ->
                    run.isPodcastMetadataText() &&
                        run.text.parseTime() == null &&
                        run.text.trim() != podcastName
                }?.text?.trim()

            private fun moe.rukamori.archivetune.innertube.models.Run.isPodcastMetadataText(): Boolean {
                val normalizedText = text.trim()
                return normalizedText.isNotBlank() && normalizedText.any(Char::isLetterOrDigit)
            }

            private fun fromMusicMultiRowListItemRenderer(renderer: MusicMultiRowListItemRenderer): YTItem? {
                val episode = renderer.toEpisodeItem()
                if (episode != null && (episode.endpoint.isPodcastEpisodeEndpoint || episode.browseId != null)) {
                    return episode
                }
                val endpoint =
                    renderer.onTap?.anyWatchEndpoint
                        ?: renderer.overlay
                            ?.musicItemThumbnailOverlayRenderer
                            ?.content
                            ?.musicPlayButtonRenderer
                            ?.playNavigationEndpoint
                            ?.anyWatchEndpoint
                val thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getBestThumbnail()
                val title = renderer.title?.runs?.joinToString(separator = "") { it.text }
                if (endpoint == null || thumbnail == null || title.isNullOrBlank()) return null

                return SongItem(
                    id = endpoint.videoId ?: return null,
                    title = title,
                    artists = renderer.subtitle?.runs?.toArtists().orEmpty(),
                    album = null,
                    duration = null,
                    thumbnail = thumbnail.normalizedUrl,
                    thumbnailWidth = thumbnail.width,
                    thumbnailHeight = thumbnail.height,
                    explicit = false,
                    endpoint = endpoint,
                )
            }
        }
    }

    fun filterExplicit(enabled: Boolean = true) =
        if (enabled) {
            copy(
                sections =
                    sections.map {
                        it.copy(items = it.items.filterExplicit())
                    },
            )
        } else {
            this
        }
}
