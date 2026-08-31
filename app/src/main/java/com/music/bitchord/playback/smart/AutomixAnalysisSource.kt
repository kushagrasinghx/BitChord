package com.music.bitchord.playback.smart

import com.music.bitchord.data.sources.SourceKind

/**
 * Keeps Automix analysis on the canonical YouTube Opus rendition.
 *
 * Playback may deliberately use a source substitute (including JioSaavn), but
 * its cache entry is not interchangeable with the YouTube recording that the
 * analysis fetcher owns. The plain video id is the cache key pinned to that
 * YouTube copy; suffixed keys (`#alt`, `#hifi`, ...) belong to substitutes or
 * upgrades.
 */
internal object AutomixAnalysisSource {
    fun canAnalyzeSourceBackedTrack(kind: SourceKind?): Boolean = kind != SourceKind.JIOSAAVN

    fun isCanonicalYouTubeRendition(videoId: String?, cacheKey: String): Boolean =
        videoId == null || cacheKey == videoId
}
