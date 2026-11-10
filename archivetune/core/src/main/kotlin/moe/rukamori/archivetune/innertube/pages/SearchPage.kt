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
import moe.rukamori.archivetune.innertube.models.EpisodeItem
import moe.rukamori.archivetune.innertube.models.MusicResponsiveListItemRenderer
import moe.rukamori.archivetune.innertube.models.PODCAST_SHOW_BROWSE_PREFIX
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.PodcastItem
import moe.rukamori.archivetune.innertube.models.Run
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.models.clean
import moe.rukamori.archivetune.innertube.models.splitBySeparator
import moe.rukamori.archivetune.innertube.models.toArtists
import moe.rukamori.archivetune.innertube.utils.parseTime

data class SearchResult(
    val items: List<YTItem>,
    val continuation: String? = null,
)

object SearchPage {
    fun toYTItem(renderer: MusicResponsiveListItemRenderer): YTItem? {
        val title = renderer.titleText ?: return null
        val thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getBestThumbnail()
        val metadata = renderer.metadataGroups()
        return when {
            renderer.isEpisode -> {
                val endpoint = renderer.watchEndpoint() ?: return null
                val itemThumbnail = thumbnail ?: return null
                val durationText = metadata.durationText()
                EpisodeItem(
                    id = renderer.playlistItemData?.videoId ?: endpoint.videoId ?: return null,
                    browseId = renderer.episodeBrowseId(),
                    title = title,
                    podcast = metadata.podcastArtist(),
                    description = null,
                    dateText = metadata.firstTextExcludingDuration(),
                    durationText = durationText,
                    duration = durationText?.parseTime(),
                    thumbnail = itemThumbnail.normalizedUrl,
                    endpoint = endpoint,
                    thumbnailWidth = itemThumbnail.width,
                    thumbnailHeight = itemThumbnail.height,
                )
            }

            renderer.isPodcast -> {
                val endpoint = renderer.navigationEndpoint?.browseEndpoint ?: return null
                PodcastItem(
                    browseId = endpoint.browseId,
                    playlistId =
                        renderer.watchEndpoint()?.playlistId
                            ?: endpoint.browseId.removePrefix(PODCAST_SHOW_BROWSE_PREFIX).takeIf(String::isNotBlank),
                    title = title,
                    author = metadata.podcastArtist(),
                    thumbnail = thumbnail?.normalizedUrl,
                    thumbnailWidth = thumbnail?.width,
                    thumbnailHeight = thumbnail?.height,
                )
            }

            renderer.isSong -> {
                val endpoint = renderer.watchEndpoint()
                val itemThumbnail = thumbnail ?: return null
                SongItem(
                    id = renderer.playlistItemData?.videoId ?: endpoint?.videoId ?: return null,
                    title = title,
                    artists =
                        metadata
                            .asSequence()
                            .map { it.toArtists() }
                            .firstOrNull { it.isNotEmpty() }
                            .orEmpty(),
                    album =
                        metadata.getOrNull(1)?.firstOrNull()?.takeIf { it.navigationEndpoint?.browseEndpoint != null }?.let {
                            Album(
                                name = it.text,
                                id = it.navigationEndpoint?.browseEndpoint?.browseId!!,
                            )
                        },
                    duration = metadata.duration(),
                    viewCountText = metadata.viewCountText(),
                    viewCount = metadata.viewCount(),
                    thumbnail = itemThumbnail.normalizedUrl,
                    thumbnailWidth = itemThumbnail.width,
                    thumbnailHeight = itemThumbnail.height,
                    explicit = renderer.isExplicit,
                    endpoint = endpoint,
                )
            }

            renderer.isArtist -> {
                ArtistItem(
                    id = renderer.navigationEndpoint?.browseEndpoint?.browseId ?: return null,
                    title = title,
                    thumbnail = thumbnail?.normalizedUrl,
                    thumbnailWidth = thumbnail?.width,
                    thumbnailHeight = thumbnail?.height,
                    playEndpoint = renderer.watchEndpoint(),
                    shuffleEndpoint =
                        renderer.menu
                            ?.menuRenderer
                            ?.items
                            ?.find { it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE" }
                            ?.menuNavigationItemRenderer
                            ?.navigationEndpoint
                            ?.watchPlaylistEndpoint,
                    radioEndpoint =
                        renderer.menu
                            ?.menuRenderer
                            ?.items
                            ?.find { it.menuNavigationItemRenderer?.icon?.iconType == "MIX" }
                            ?.menuNavigationItemRenderer
                            ?.navigationEndpoint
                            ?.watchPlaylistEndpoint,
                )
            }

            renderer.isAlbum -> {
                val itemThumbnail = thumbnail ?: return null
                AlbumItem(
                    browseId = renderer.navigationEndpoint?.browseEndpoint?.browseId ?: return null,
                    playlistId =
                        renderer
                            .watchEndpoint()
                            ?.playlistId
                            ?: return null,
                    title = title,
                    artists = metadata.getOrNull(0)?.toArtists()?.takeIf { it.isNotEmpty() },
                    year = metadata.year(),
                    thumbnail = itemThumbnail.normalizedUrl,
                    thumbnailWidth = itemThumbnail.width,
                    thumbnailHeight = itemThumbnail.height,
                    explicit = renderer.isExplicit,
                )
            }

            renderer.isPlaylist -> {
                val playlistMetadata = renderer.metadataGroups(clean = false)
                PlaylistItem(
                    id =
                        renderer.navigationEndpoint
                            ?.browseEndpoint
                            ?.browseId
                            ?.removePrefix("VL")
                            ?: renderer.watchEndpoint()?.playlistId?.removePrefix("VL")
                            ?: return null,
                    title = title,
                    author = playlistMetadata.playlistAuthor(),
                    songCountText = playlistMetadata.lastText(),
                    thumbnail = thumbnail?.normalizedUrl,
                    thumbnailWidth = thumbnail?.width,
                    thumbnailHeight = thumbnail?.height,
                    playEndpoint =
                        renderer.watchEndpoint(),
                    shuffleEndpoint =
                        renderer.menu
                            ?.menuRenderer
                            ?.items
                            ?.find { it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE" }
                            ?.menuNavigationItemRenderer
                            ?.navigationEndpoint
                            ?.watchPlaylistEndpoint,
                    radioEndpoint =
                        renderer.menu
                            ?.menuRenderer
                            ?.items
                            ?.find { it.menuNavigationItemRenderer?.icon?.iconType == "MIX" }
                            ?.menuNavigationItemRenderer
                            ?.navigationEndpoint
                            ?.watchPlaylistEndpoint,
                )
            }

            else -> {
                null
            }
        }
    }
}

private val MusicResponsiveListItemRenderer.titleText: String?
    get() =
        flexColumns
            .firstOrNull()
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text
            ?.runs
            ?.joinToString(separator = "") { it.text }
            ?.takeIf { it.isNotBlank() }

private val MusicResponsiveListItemRenderer.isExplicit: Boolean
    get() =
        badges?.any {
            it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
        } == true

private fun MusicResponsiveListItemRenderer.metadataGroups(clean: Boolean = true): List<List<Run>> {
    val groups =
        flexColumns
            .drop(1)
            .flatMap {
                it.musicResponsiveListItemFlexColumnRenderer.text
                    ?.runs
                    ?.splitBySeparator()
                    .orEmpty()
            }
    return if (clean) groups.clean() else groups
}

private fun MusicResponsiveListItemRenderer.watchEndpoint(): WatchEndpoint? = resolvedWatchEndpoint

private fun MusicResponsiveListItemRenderer.episodeBrowseId(): String? =
    flexColumns
        .asSequence()
        .flatMap { it.musicResponsiveListItemFlexColumnRenderer.text?.runs.orEmpty().asSequence() }
        .mapNotNull { it.navigationEndpoint?.browseEndpoint }
        .firstOrNull { it.isPodcastEpisodeEndpoint }
        ?.browseId

private fun List<List<Run>>.duration(): Int? {
    for (group in asReversed()) {
        for (run in group.asReversed()) {
            run.text.parseTime()?.let { return it }
        }
    }
    return null
}

private fun List<List<Run>>.durationText(): String? {
    for (group in asReversed()) {
        for (run in group.asReversed()) {
            if (run.text.parseTime() != null) return run.text
        }
    }
    return null
}

private fun List<List<Run>>.podcastArtist(): Artist? =
    asSequence()
        .flatten()
        .firstNotNullOfOrNull { run ->
            run.navigationEndpoint
                ?.browseEndpoint
                ?.takeIf { it.isPodcastShowEndpoint || it.isArtistEndpoint || it.browseId.startsWith("UC") }
                ?.let { Artist(name = run.text, id = it.browseId) }
        }

private fun List<List<Run>>.firstTextExcludingDuration(): String? =
    asSequence()
        .flatten()
        .map { it.text.trim() }
        .firstOrNull { it.isNotBlank() && it.parseTime() == null }

private fun List<List<Run>>.year(): Int? {
    for (group in asReversed()) {
        for (run in group.asReversed()) {
            run.text.toIntOrNull()?.let { return it }
        }
    }
    return null
}

private fun List<List<Run>>.viewCountText(): String? =
    firstNotNullOfOrNull { group ->
        val text = group.joinToString(separator = "") { it.text }.trim()
        text.takeIf {
            group.none { run -> run.navigationEndpoint != null } &&
                it.parseTime() == null &&
                it.toIntOrNull()?.let { value -> value !in 1900..2100 } != false &&
                parseViewCount(it) != null
        }
    }

private fun List<List<Run>>.viewCount(): Long? = viewCountText()?.let(::parseViewCount)

private fun parseViewCount(text: String): Long? {
    val match = ViewCountRegex.find(text) ?: return null
    val numberText = match.groupValues[1]
    val suffix = match.groupValues[2].uppercase()
    val value =
        if (suffix.isNotEmpty()) {
            numberText.replace(',', '.').toDoubleOrNull()
        } else {
            numberText.filter(Char::isDigit).toDoubleOrNull()
        } ?: return null
    val multiplier =
        when (suffix) {
            "K" -> 1_000.0
            "M" -> 1_000_000.0
            "B" -> 1_000_000_000.0
            else -> 1.0
        }
    return (value * multiplier).toLong()
}

private fun List<List<Run>>.playlistAuthor(): Artist? {
    val authorIndex = if (size >= 3) 1 else 0
    return getOrNull(authorIndex)
        ?.firstOrNull()
        ?.let {
            Artist(
                name = it.text,
                id = it.navigationEndpoint?.browseEndpoint?.browseId,
            )
        }
}

private fun List<List<Run>>.lastText(): String? =
    lastOrNull()
        ?.joinToString(separator = "") { it.text }
        ?.takeIf { it.isNotBlank() }

private val ViewCountRegex = Regex("""([\d.,]+)\s*([KMB]?)""", RegexOption.IGNORE_CASE)
