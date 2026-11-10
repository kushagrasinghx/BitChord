package com.music.bitchord.data.playlist

import com.music.bitchord.data.model.Song

/** A playlist entry is not the same as a recording: one video can be added twice. */
fun playlistEntryKey(song: Song): String = song.setVideoId?.takeIf { it.isNotBlank() }
    ?.let { "entry:$it" } ?: "video:${song.videoId}"

data class PlaylistDelta(
    val added: List<Song>,
    val removed: List<Song>,
    val reordered: Boolean,
    val metadataChanged: Boolean,
) {
    val changed: Boolean get() = added.isNotEmpty() || removed.isNotEmpty() || reordered || metadataChanged
}

fun comparePlaylistSnapshots(previous: List<Song>, incoming: List<Song>): PlaylistDelta {
    val before = previous.associateBy(::playlistEntryKey)
    val after = incoming.associateBy(::playlistEntryKey)
    val beforeKeys = previous.map(::playlistEntryKey)
    val afterKeys = incoming.map(::playlistEntryKey)
    val common = beforeKeys.filter { it in after }
    val updatedCommon = afterKeys.filter { it in before }
    return PlaylistDelta(
        added = incoming.filter { playlistEntryKey(it) !in before },
        removed = previous.filter { playlistEntryKey(it) !in after },
        reordered = common != updatedCommon,
        metadataChanged = incoming.any { song ->
            before[playlistEntryKey(song)]?.let { it != song } ?: false
        },
    )
}

/**
 * Builds a complete, ordered playlist only if every page has arrived.
 * A repeated token, looping page, or partial fetch cannot become a cache snapshot.
 */
class PlaylistIndexBuilder {
    private val songs = LinkedHashMap<String, Song>()
    private val continuationTokens = HashSet<String>()
    private var failed = false
    private var ended = false

    fun addPage(entries: List<Song>, nextToken: String?): Boolean {
        if (failed || ended) return false
        val countBefore = songs.size
        entries.forEach { song -> songs.putIfAbsent(playlistEntryKey(song), song) }
        if (nextToken != null &&
            (nextToken.isBlank() || !continuationTokens.add(nextToken) || songs.size == countBefore)
        ) {
            failed = true
            return false
        }
        if (nextToken == null) ended = true
        return true
    }

    fun progress(): List<Song> = songs.values.toList()
    fun completeSnapshot(): List<Song>? = if (!failed && ended) progress() else null
}
