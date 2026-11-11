package com.music.bitchord.data.playlist

import com.music.bitchord.data.model.Song

/**
 * Stable, cache-local insertion order, not an invented date. Playlist browse
 * rows do not contain an added-at timestamp. Existing entries retain their
 * sequence even if YouTube changes its custom row order. Unseen entries
 * receive newer sequence numbers, preserving their relative server order.
 *
 * First snapshots (including cache schema-v1 upgrades) use their received
 * order as a best-effort baseline: first row newest, last oldest.
 */
fun preservePlaylistAddedOrder(previous: List<Song>?, incoming: List<Song>): List<Song> {
    val baseline = previous.orEmpty().mapIndexed { index, song ->
        song.copy(playlistAddedOrdinal =
            song.playlistAddedOrdinal ?: (previous!!.size - index).toLong())
    }
    val known = baseline.associateBy(::playlistEntryKey)
    var highest = baseline.maxOfOrNull { it.playlistAddedOrdinal ?: 0L } ?: 0L
    val unseen = incoming.filter { playlistEntryKey(it) !in known }
    val assigned = HashMap<String, Long>(unseen.size)
    // The service normally lists recent additions before older entries.
    unseen.asReversed().forEach { assigned[playlistEntryKey(it)] = ++highest }
    return incoming.map { song ->
        val key = playlistEntryKey(song)
        song.copy(playlistAddedOrdinal = known[key]?.playlistAddedOrdinal ?: assigned[key])
    }
}
