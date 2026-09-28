package com.music.bitchord.playback

import android.os.Bundle
import androidx.media3.common.MediaItem

/**
 * The one-way escape from a higher-quality source that failed after it had
 * already been selected.
 *
 * A normal retry is deliberately free to resolve again, which is useful for a
 * transient YouTube URL and exactly wrong for an addon/Catalogue failure: the
 * same deterministic catalogue lookup wins again and hands the player the same
 * broken stream. A failed alternative therefore becomes an explicit YouTube
 * request for the rest of this playback.
 */
internal object PlaybackFallback {

    /** Whether [uri] is currently allowed to resolve to something above YouTube. */
    fun isAlternative(uri: String, substitutedYouTube: Boolean): Boolean {
        if (parameter(uri, DIRECT_YOUTUBE_PARAMETER) == "1") return false
        if (uri.substringBefore('?').substringBefore('#') == "bitchord://source") return true
        val rendition = parameter(uri, QualityUpgrade.MARKER)
        return substitutedYouTube || rendition == "hifi" || rendition?.startsWith("hifi-") == true
    }

    /**
     * Marks a source-backed item as a direct YouTube fallback while preserving
     * the title/artist/duration query used to find the corresponding upload.
     */
    fun directYouTubeSourceUri(uri: String): String {
        val fragmentAt = uri.indexOf('#')
        val withoutFragment = if (fragmentAt >= 0) uri.substring(0, fragmentAt) else uri
        val fragment = if (fragmentAt >= 0) uri.substring(fragmentAt) else ""
        val queryAt = withoutFragment.indexOf('?')
        val base = if (queryAt >= 0) withoutFragment.substring(0, queryAt) else withoutFragment
        val kept = if (queryAt >= 0) {
            withoutFragment.substring(queryAt + 1)
                .split('&')
                .filter { part ->
                    val key = part.substringBefore('=')
                    key != DIRECT_YOUTUBE_PARAMETER &&
                        key != QualityUpgrade.MARKER &&
                        key != MANIFEST_REOPEN_PARAMETER
                }
                .filter { it.isNotBlank() }
        } else {
            emptyList()
        }
        val params = kept + "$DIRECT_YOUTUBE_PARAMETER=1" + "${QualityUpgrade.MARKER}=original"
        return "$base?${params.joinToString("&")}$fragment"
    }

    private fun parameter(uri: String, wanted: String): String? {
        val query = uri.substringAfter('?', missingDelimiterValue = "").substringBefore('#')
        return query.split('&')
            .firstOrNull { it.substringBefore('=') == wanted }
            ?.substringAfter('=', missingDelimiterValue = "")
    }

    private const val MANIFEST_REOPEN_PARAMETER = "manifest_reopen"
}

/**
 * Rebuilds this item as a direct YouTube request with no stale manifest type.
 * Returns null only for an item that has neither a YouTube id nor a source URI
 * carrying enough metadata to find its YouTube equivalent.
 */
internal fun MediaItem.toYouTubeFallbackMediaItem(): MediaItem? {
    val song = toSong()
    if (song.hasYouTubeOriginal()) return song.toDirectYouTubeMediaItem()

    val currentUri = localConfiguration?.uri ?: return null
    if (currentUri.authority != "source") return null
    val cleanMetadata = mediaMetadata.buildUpon()
        .setExtras(Bundle(mediaMetadata.extras ?: Bundle()).apply {
            remove(EXTRA_QUALITY_UPGRADED)
        })
        .build()
    return buildUpon()
        .setUri(PlaybackFallback.directYouTubeSourceUri(currentUri.toString()))
        // The failed source may have been DASH/HLS. YouTube audio is a
        // progressive WebM/MP4 and must be sniffed again from a clean item.
        .setMimeType(null)
        .setMediaMetadata(cleanMetadata)
        .build()
}
