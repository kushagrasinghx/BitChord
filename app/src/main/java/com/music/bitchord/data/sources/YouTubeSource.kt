package com.music.bitchord.data.sources

import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.innertube.StreamResolver
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song

/**
 * YouTube Music, wrapped so it can be ordered alongside everything else.
 *
 * A thin adapter over machinery that already exists and stays where it is:
 * [StreamResolver] keeps its client walk, its URL cache and its 403 handling,
 * and nothing about how a YouTube track resolves changes by virtue of this
 * class. What changes is that YouTube is now *one of* the sources rather than
 * the assumption underneath all of them.
 *
 * Tracks keep their bare video ids rather than being wrapped in a
 * [SourceRegistry.trackKey]. Half the app knows what a YouTube video id is —
 * the like button, the lyrics lookup keyed on it, the radio endpoint, the
 * canvas lookup, the scrobbler — and re-keying them here would break every one
 * of those for no gain, since an unwrapped id already routes to exactly this
 * source by default.
 */
class YouTubeSource(
    override val config: SourceConfig,
) : MusicSource, SourceRegistry.ConfigBacked {

    override val configId: String get() = config.id
    override val kind: SourceKind get() = SourceKind.YOUTUBE
    override val displayName: String get() = config.label.ifBlank { SourceKind.YOUTUBE.label }

    /**
     * Always Ok.
     *
     * There is no cheap probe worth making here: the endpoints that would
     * answer are the same ones a search is about to hit anyway, and a failing
     * YouTube shows up as a failed resolve with a real reason attached rather
     * than as a red dot on a settings row.
     */
    override suspend fun health(): SourceHealth = SourceHealth.Ok()

    /** [waitForAll] is moot: there is one endpoint here, and it is always waited for. */
    override suspend fun search(query: String, limit: Int, waitForAll: Boolean): List<Song> =
        YtMusicRepository.search(query, SearchFilter.SONGS)
            .getOrDefault(emptyList())
            .filterIsInstance<SearchResult.Track>()
            .map { it.song }
            .take(limit)

    /**
     * [request] is not honoured here, and cannot be.
     *
     * [StreamRequest.Lossless] has no answer on this source — YouTube publishes
     * no lossless rendition of anything, which is why [SourceKind.YOUTUBE]
     * declares `canServeLossless = false` and why [SourceResolver] does not ask
     * this source for one unless it is the last one left. The bitrate ceiling
     * is applied inside [StreamResolver] from the same settings, at the point
     * where the format list is actually in hand.
     */
    override suspend fun stream(trackId: String, request: StreamRequest): SourceStream? {
        val url = StreamResolver.resolve(trackId)
        return SourceStream(url = url, format = StreamFormat(codec = "opus"))
    }
}
