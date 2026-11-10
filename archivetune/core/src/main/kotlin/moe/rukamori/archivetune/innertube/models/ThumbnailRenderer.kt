/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.innertube.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ThumbnailRenderer(
    @JsonNames("croppedSquareThumbnailRenderer")
    val musicThumbnailRenderer: MusicThumbnailRenderer?,
    val musicAnimatedThumbnailRenderer: MusicAnimatedThumbnailRenderer?,
    val croppedSquareThumbnailRenderer: MusicThumbnailRenderer?,
) {
    @Serializable
    data class MusicThumbnailRenderer(
        val thumbnail: Thumbnails,
        val thumbnailCrop: String?,
        val thumbnailScale: String?,
    ) {
        fun getBestThumbnail(): Thumbnail? {
            val thumbnails = thumbnail.thumbnails
            return thumbnails
                .filter { candidate ->
                    candidate.normalizedUrl.isNotBlank() &&
                        (candidate.width ?: 0) > 0 &&
                        (candidate.height ?: 0) > 0
                }.maxByOrNull { candidate ->
                    val width = candidate.width ?: 0
                    val height = candidate.height ?: 0
                    width.toLong() * height.toLong()
                } ?: thumbnails.lastOrNull { it.normalizedUrl.isNotBlank() }
        }

        fun getThumbnailUrl() = getBestThumbnail()?.normalizedUrl
    }

    @Serializable
    data class MusicAnimatedThumbnailRenderer(
        val animatedThumbnail: Thumbnails,
        val backupRenderer: MusicThumbnailRenderer,
    )
}
