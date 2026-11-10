/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Ported from SimpMusic (https://github.com/maxrave-dev/SimpMusic),
 * core/service/kotlinYtmusicScraper extractor package — GPL-3.0, © maxrave-dev.
 * Logic kept byte-for-byte; only the package name and imports changed.
 */

package moe.rukamori.archivetune.simpstream.extractor

import kotlin.concurrent.Volatile

/**
 * Which extractor, and which cipher decoder, produced the stream URLs for a video.
 *
 * Deliberately NOT stored on `NewFormatEntity`: a format row is cached and re-read for as long as it
 * is valid, so a persisted source would keep naming the path taken the first time, long after a
 * later play went down a different one. This is about a single extraction, so it lives only as long
 * as the process does.
 *
 * Bounded to the last [MAX_ENTRIES] videos — the only reader is the info sheet for whatever is
 * playing, so there is nothing to gain by remembering further back.
 */
object ExtractSource {
    private const val MAX_ENTRIES = 32

    /**
     * Replaced wholesale rather than mutated, so a reader never observes a half-written map. Writes
     * happen once per extraction and reads once per sheet opening, so the copying costs nothing.
     */
    @Volatile
    private var sources: Map<String, String> = emptyMap()

    fun record(
        videoId: String,
        source: String,
    ) {
        if (videoId.isEmpty()) return
        val updated = LinkedHashMap<String, String>(sources)
        updated.remove(videoId)
        updated[videoId] = source
        while (updated.size > MAX_ENTRIES) {
            updated.remove(updated.keys.firstOrNull() ?: break)
        }
        sources = updated
    }

    fun of(videoId: String): String? = sources[videoId]
}
