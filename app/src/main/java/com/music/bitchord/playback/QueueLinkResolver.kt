package com.music.bitchord.playback

import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.sources.TrackMatcher
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * The songs of a queue link ([LinkRequest.Queue]): each song's match, found by [find], in the
 * link's order. A song that finds nothing (or fails) is left out, and so is a song that already
 * came up for an earlier one. At most [parallel] lookups run at once, so a 50-song link does not
 * fire 50 searches together.
 */
internal suspend fun resolveQueueLink(
    songs: List<LinkRequest.QueueSong>,
    parallel: Int = 4,
    find: suspend (LinkRequest.QueueSong) -> Song?,
): List<Song> = coroutineScope {
    val gate = Semaphore(parallel.coerceAtLeast(1))
    songs.map { song ->
        async {
            gate.withPermit {
                try {
                    find(song)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            }
        }
    }.awaitAll().filterNotNull().distinctBy { it.videoId }
}

/**
 * The song another app asked for.
 *
 * With only words ([title] null) it is the top song for [query], as "play X" always was.
 * With a title it is *that recording*: the [TrackMatcher] best match among the Songs results,
 * else among the Videos (a TV show's production music is often only an upload), else nothing —
 * a wrong song under the right name is worse than none, and the caller says so or shows results.
 * [search] returns the track rows of one search; a failed search counts as no rows.
 */
internal suspend fun findAskedSong(
    query: String,
    title: String?,
    artist: String?,
    search: suspend (String, SearchFilter) -> List<Song>,
): Song? {
    suspend fun rows(filter: SearchFilter): List<Song> = try {
        search(query, filter)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        emptyList()
    }
    if (title.isNullOrBlank()) return rows(SearchFilter.SONGS).firstOrNull()
    val target = TrackMatcher.Target(title = title, artist = artist.orEmpty())
    TrackMatcher.best(rows(SearchFilter.SONGS), target)?.let { return it }
    return TrackMatcher.best(rows(SearchFilter.VIDEOS), target)
}
