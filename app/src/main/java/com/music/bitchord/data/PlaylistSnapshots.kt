package com.music.bitchord.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.concurrent.CancellationException

/**
 * Build a replacement playlist from all of its pages before it can replace a saved snapshot.
 * Partial progress is optional, for a playlist that has no previous content to show.
 */
suspend fun completePlaylistSnapshot(
    first: YtMusicRepository.SongPage,
    fetchMore: suspend (String) -> Result<YtMusicRepository.SongPage>,
    shouldContinue: () -> Boolean = { true },
    onPartial: (YtMusicRepository.SongPage) -> Unit = {},
): Result<YtMusicRepository.SongPage> = runCatching {
    suspend fun checkActive() {
        currentCoroutineContext().ensureActive()
        if (!shouldContinue()) throw CancellationException("Playlist refresh no longer active")
    }

    checkActive()
    val songs = first.songs.distinctBy { it.setVideoId ?: it.videoId }.toMutableList()
    val knownEntries = songs.mapTo(HashSet()) { it.setVideoId ?: it.videoId }
    val suggestions = first.suggested.distinctBy { it.videoId }.toMutableList()
    val knownSuggestions = suggestions.mapTo(HashSet()) { it.videoId }
    val tokens = HashSet<String>()
    var continuation = first.continuation

    fun snapshot(): YtMusicRepository.SongPage = first.copy(
        songs = songs.toList(), suggested = suggestions.toList(), continuation = continuation,
    )

    onPartial(snapshot())
    while (continuation != null) {
        checkActive()
        val token = continuation
        check(tokens.add(token)) { "Playlist continuation cycle" }
        val next = fetchMore(token).getOrThrow()
        checkActive()
        var changed = false
        next.songs.forEach { song ->
            if (knownEntries.add(song.setVideoId ?: song.videoId)) {
                songs.add(song)
                changed = true
            }
        }
        next.suggested.forEach { song ->
            if (knownSuggestions.add(song.videoId)) {
                suggestions.add(song)
                changed = true
            }
        }
        continuation = next.continuation
        if (changed) onPartial(snapshot())
    }
    checkActive()
    val actualVideos = songs.mapTo(HashSet()) { it.videoId }
    snapshot().copy(suggested = suggestions.filter { it.videoId !in actualVideos })
}
