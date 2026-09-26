package com.music.bitchord.desktop

import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.QueueBuilder

/** Most Autoplay-suggested tracks kept queued ahead of the current one at once. */
const val MAX_QUEUED_AUTOPLAY = 10

/** The station that keeps playing when the queue runs out. */
internal object DesktopAutoplay {

    /** The YouTube id to seed a station on. */
    suspend fun youtubeSeedFor(song: Song): String? {
        if (DesktopMusicSources.hasYouTubeOriginal(song)) return song.videoId
        val query = DesktopTrackMatcher.queries(song).firstOrNull() ?: return null
        // YouTube specifically, not the configured sources.
        return DesktopSearchClient.search(query, SearchFilter.SONGS)
            .getOrNull()
            ?.mapNotNull { result ->
                when (result) {
                    is SearchResult.Track -> result.song
                    is SearchResult.TopTrack -> result.song
                    is SearchResult.Browse -> null
                }
            }
            ?.let { DesktopTrackMatcher.best(it, song) }
            ?.videoId
    }

    /** One batch of station tracks to append after [existing]. */
    suspend fun tracksFor(
        existing: List<Song>,
        seedSong: Song,
        limit: Int = MAX_QUEUED_AUTOPLAY,
    ): Result<List<Song>> {
        val seed = youtubeSeedFor(seedSong)
        if (seed == null) {
            DesktopTrackLog.log("autoplay: no YouTube match to seed a station on for '${seedSong.title}'")
            return Result.success(emptyList())
        }
        val related = DesktopSearchClient.radio(seed).getOrElse { return Result.failure(it) }
        DesktopTrackLog.log("autoplay: station for '${seedSong.title}' offered ${related.size} tracks")
        val extra = QueueBuilder.extend(existing, related, limit)
        if (extra.isEmpty()) return Result.success(emptyList())
        return Result.success(extra.map { it.copy(queueTier = QueueTier.AUTOPLAY, radioName = seedSong.radioName) })
    }
}
