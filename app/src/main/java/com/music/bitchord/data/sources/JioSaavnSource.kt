package com.music.bitchord.data.sources

import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.jiosaavn.JioSaavnService
import com.music.bitchord.data.jiosaavn.prioritizeExplicit
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.async

private const val TAG = "BitChord"

class JioSaavnSource(
    override val config: SourceConfig,
) : MusicSource, SourceRegistry.ConfigBacked {

    override val configId: String get() = config.id
    override val kind: SourceKind get() = SourceKind.JIOSAAVN
    override val displayName: String get() = config.label.ifBlank { SourceKind.JIOSAAVN.label }

    /** Always Ok since the API endpoints don't need authentication to search. */
    override suspend fun health(): SourceHealth = SourceHealth.Ok()

    override suspend fun search(query: String, limit: Int, waitForAll: Boolean): List<Song> {
        TrackLog.d(TAG, "▶ JioSaavn searchSongs() query=\"$query\" limit=$limit")
        val results = prioritizeExplicit(JioSaavnService.searchSongs(query))
        TrackLog.d(TAG, "  ✓ JioSaavn returned ${results.size} tracks" + results.take(5)
            .joinToString(prefix = ": ", separator = "; ") { "'${it.title}' by '${
                it.moreInfo.artistMap.primaryArtists.joinToString(", ") { a -> a.name }
            }' ${it.moreInfo.duration}s id=${it.id} album='${it.moreInfo.album}' " +
                "explicit=${it.explicitContent}" }.takeIf { results.isNotEmpty() }.orEmpty())
        return results.take(limit).map { raw ->
            val primaryArtists = raw.moreInfo.artistMap.primaryArtists.joinToString(", ") { it.name }
            val artistName = primaryArtists.ifBlank { "Unknown Artist" }
            
            // Generate higher quality thumbnail link (e.g. 500x500)
            val thumbnail = raw.image
                .replace(Regex("150x150|50x50"), "500x500")
                .replace(Regex("^http://"), "https://")

            Song(
                videoId = SourceRegistry.trackKey(config.id, raw.id),
                title = raw.title,
                artist = artistName,
                // Keep the release identity JioSaavn already gave us. Its
                // catalogue contains different recordings under the same
                // title and artist (notably the two "Brown Rang" rows), so
                // dropping this left duration to choose between them.
                albumName = raw.moreInfo.album.ifBlank { null },
                thumbnailUrl = thumbnail,
                // JioSaavn provides duration in seconds, but Song expects durationText ("M:SS")
                // Alternatively, Song.durationMillis() will parse durationText. Let's just 
                // format it since BitChord uses string duration.
                durationText = raw.moreInfo.duration.toIntOrNull()?.let { seconds ->
                    val m = seconds / 60
                    val s = seconds % 60
                    String.format("%d:%02d", m, s)
                },
                sourceQuality = "HIGH",
                isExplicit = raw.isExplicit,
            )
        }
    }

    override suspend fun homeFeed(): List<com.music.bitchord.data.model.HomeShelf> = kotlinx.coroutines.coroutineScope {
        val trendingJob = async { JioSaavnService.searchSongs("top hits") }
        val newReleasesJob = async { JioSaavnService.searchSongs("latest hits") }
        val bollywoodJob = async { JioSaavnService.searchSongs("bollywood hits") }
        val popJob = async { JioSaavnService.searchSongs("pop hits") }

        val trending = trendingJob.await()
        val newReleases = newReleasesJob.await()
        val bollywood = bollywoodJob.await()
        val pop = popJob.await()

        val shelves = mutableListOf<com.music.bitchord.data.model.HomeShelf>()
        
        fun addShelf(title: String, subtitle: String, rawItems: List<com.music.bitchord.data.jiosaavn.RawSongItem>) {
            if (rawItems.isEmpty()) return
            val items = rawItems.map { raw ->
                val primaryArtists = raw.moreInfo.artistMap.primaryArtists.joinToString(", ") { it.name }
                val artistName = primaryArtists.ifBlank { "Unknown Artist" }
                val thumbnail = raw.image
                    .replace(Regex("150x150|50x50"), "500x500")
                    .replace(Regex("^http://"), "https://")

                com.music.bitchord.data.model.ShelfItem(
                    title = raw.title,
                    subtitle = artistName,
                    thumbnailUrl = thumbnail,
                    videoId = SourceRegistry.trackKey(config.id, raw.id),
                    browseId = null
                )
            }
            shelves.add(com.music.bitchord.data.model.HomeShelf(title = title, subtitle = subtitle, items = items))
        }

        addShelf("Trending", "Top Hits", trending)
        addShelf("New Releases", "Latest hits right now", newReleases)
        addShelf("Bollywood Hits", "Top trending Hindi songs", bollywood)
        addShelf("Global Pop", "International hits", pop)

        return@coroutineScope shelves
    }

    override suspend fun stream(trackId: String, request: StreamRequest): SourceStream? {
        TrackLog.d(TAG, "▶ JioSaavn getStreamUrl() trackId=$trackId request=$request")
        val stream = JioSaavnService.getStreamUrl(trackId)
        if (stream == null || stream.url.isBlank()) {
            TrackLog.w(TAG, "  ✗ JioSaavn had no stream URL for $trackId")
            return null
        }
        // A rendition this thin is worse than the YouTube stream it would be
        // replacing — see [SourceKind.YOUTUBE], which lands around 160kbps
        // Opus. Refused here rather than handed up and left to
        // [SourceResolver.worthSwapping], because a miss lets the resolver step
        // over this source to the next one, whereas a stream returned and then
        // rejected is simply the track not being upgraded at all.
        if (stream.kbps != null && stream.kbps <= MIN_USABLE_KBPS) {
            TrackLog.w(
                TAG,
                "  ✗ JioSaavn only offered ${stream.kbps}kbps for $trackId; not worth playing",
            )
            return null
        }
        TrackLog.d(TAG, "  ✓ JioSaavn ${stream.kbps ?: "?"}kbps ${stream.url.take(96)}")
        return SourceStream(
            url = stream.url,
            // The rate the URL will really serve, not a flat 320 — see
            // [JioSaavnService.bestStream]. `mp4` is the container; the codec
            // inside is AAC, which the decoder reports for itself.
            format = StreamFormat(codec = "mp4", kbps = stream.kbps),
        )
    }

    private companion object {
        /**
         * The lowest rendition worth taking over YouTube.
         *
         * JioSaavn files a track at 48, 96, 160 or 320. The first two are below
         * what YouTube already serves, so taking one is a downgrade dressed as
         * an upgrade.
         */
        const val MIN_USABLE_KBPS = 96
    }
}
