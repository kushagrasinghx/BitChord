/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.innertube.models

import moe.rukamori.archivetune.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_OMV
import moe.rukamori.archivetune.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_UGC
import java.util.Locale

sealed class YTItem {
    abstract val id: String
    abstract val title: String
    abstract val thumbnail: String?
    abstract val thumbnailWidth: Int?
    abstract val thumbnailHeight: Int?
    abstract val explicit: Boolean
    abstract val shareLink: String
}

data class Artist(
    val name: String,
    val id: String?,
)

data class Album(
    val name: String,
    val id: String,
)

enum class AlbumReleaseType {
    ALBUM,
    SINGLE,
    EP,
    ;

    companion object {
        fun fromLabel(label: String?): AlbumReleaseType =
            when (label?.trim()?.lowercase(Locale.ROOT)) {
                "single", "singles" -> SINGLE
                "ep", "eps" -> EP
                else -> ALBUM
            }
    }
}

data class SongItem(
    override val id: String,
    override val title: String,
    val artists: List<Artist>,
    val album: Album? = null,
    val duration: Int? = null,
    val chartPosition: Int? = null,
    val chartChange: String? = null,
    override val thumbnail: String,
    override val explicit: Boolean = false,
    val endpoint: WatchEndpoint? = null,
    val setVideoId: String? = null,
    val viewCountText: String? = null,
    val viewCount: Long? = null,
    val isPodcast: Boolean = false,
    override val thumbnailWidth: Int? = null,
    override val thumbnailHeight: Int? = null,
) : YTItem() {
    override val shareLink: String
        get() = "https://music.youtube.com/watch?v=$id"
}

fun Iterable<SongItem>.distinctByPlaylistEntry(): List<SongItem> =
    distinctBy { song -> song.setVideoId?.takeIf(String::isNotBlank) ?: song.id }

data class AlbumItem(
    val browseId: String,
    val playlistId: String,
    override val id: String = browseId,
    override val title: String,
    val artists: List<Artist>?,
    val year: Int? = null,
    override val thumbnail: String,
    override val explicit: Boolean = false,
    val releaseType: AlbumReleaseType = AlbumReleaseType.ALBUM,
    override val thumbnailWidth: Int? = null,
    override val thumbnailHeight: Int? = null,
) : YTItem() {
    override val shareLink: String
        get() = "https://music.youtube.com/playlist?list=$playlistId"
}

data class PlaylistItem(
    override val id: String,
    override val title: String,
    val author: Artist?,
    val songCountText: String?,
    override val thumbnail: String?,
    val playEndpoint: WatchEndpoint?,
    val shuffleEndpoint: WatchEndpoint?,
    val radioEndpoint: WatchEndpoint?,
    val isEditable: Boolean = false,
    val description: String? = null,
    override val thumbnailWidth: Int? = null,
    override val thumbnailHeight: Int? = null,
) : YTItem() {
    override val explicit: Boolean
        get() = false
    override val shareLink: String
        get() = "https://music.youtube.com/playlist?list=$id"
}

data class PodcastItem(
    val browseId: String,
    val playlistId: String?,
    override val title: String,
    val author: Artist?,
    override val thumbnail: String?,
    override val thumbnailWidth: Int? = null,
    override val thumbnailHeight: Int? = null,
) : YTItem() {
    override val id: String
        get() = browseId
    override val explicit: Boolean
        get() = false
    override val shareLink: String
        get() = playlistId?.let { "https://music.youtube.com/playlist?list=$it" } ?: "https://music.youtube.com/browse/$browseId"
}

data class EpisodeItem(
    override val id: String,
    val browseId: String?,
    override val title: String,
    val podcast: Artist?,
    val description: String?,
    val dateText: String?,
    val durationText: String?,
    val duration: Int?,
    override val thumbnail: String,
    val endpoint: WatchEndpoint,
    override val thumbnailWidth: Int? = null,
    override val thumbnailHeight: Int? = null,
) : YTItem() {
    override val explicit: Boolean
        get() = false
    override val shareLink: String
        get() = "https://music.youtube.com/watch?v=$id"
}

data class ArtistItem(
    override val id: String,
    override val title: String,
    override val thumbnail: String?,
    val channelId: String? = null,
    val playEndpoint: WatchEndpoint? = null,
    val shuffleEndpoint: WatchEndpoint?,
    val radioEndpoint: WatchEndpoint?,
    val subscriberCountText: String? = null,
    val monthlyListenerCountText: String? = null,
    override val thumbnailWidth: Int? = null,
    override val thumbnailHeight: Int? = null,
) : YTItem() {
    override val explicit: Boolean
        get() = false
    override val shareLink: String
        get() = "https://music.youtube.com/channel/$id"
}

fun <T : YTItem> List<T>.filterExplicit(enabled: Boolean = true) =
    if (enabled) {
        filter { !it.explicit }
    } else {
        this
    }

fun <T : YTItem> List<T>.filterVideo(enabled: Boolean = true) =
    if (enabled) {
        filter {
            when (it) {
                is SongItem -> {
                    val musicVideoType =
                        it.endpoint
                            ?.watchEndpointMusicSupportedConfigs
                            ?.watchEndpointMusicConfig
                            ?.musicVideoType
                    val isMusicVideo = musicVideoType == MUSIC_VIDEO_TYPE_OMV || musicVideoType == MUSIC_VIDEO_TYPE_UGC
                    !isMusicVideo
                }

                else -> {
                    true
                }
            }
        }
    } else {
        this
    }

