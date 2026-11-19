package com.music.bitchord.playback

import com.music.bitchord.data.model.Song
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * The songs of a queue link ([LinkRequest.Queue]): each query's best match, found by [search],
 * in the link's order. A query that finds nothing (or fails) is left out, and so is a song that
 * already came up for an earlier query. At most [parallel] searches run at once, so a 50-song
 * link does not fire 50 requests together.
 */
internal suspend fun resolveQueueLink(
    queries: List<String>,
    parallel: Int = 4,
    search: suspend (String) -> Song?,
): List<Song> = coroutineScope {
    val gate = Semaphore(parallel.coerceAtLeast(1))
    queries.map { query ->
        async {
            gate.withPermit {
                try {
                    search(query)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            }
        }
    }.awaitAll().filterNotNull().distinctBy { it.videoId }
}
