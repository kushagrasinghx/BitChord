package com.music.bitchord.ui.screens

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.SongSort

fun List<Song>.sortedForDetail(sort: SongSort): List<Song> = when (sort) {
    SongSort.DEFAULT -> this
    // Untagged rows go after the tagged ones, in the order they already had,
    // rather than interleaving at the head; a page with no tags at all is
    // left exactly as it came.
    SongSort.TRACK_NUMBER -> if (none { it.trackNumber != null }) {
        this
    } else {
        withIndex()
            .sortedWith(
                compareBy<IndexedValue<Song>> { (_, song) -> song.trackNumber ?: Int.MAX_VALUE }
                    .thenBy { (position, _) -> position },
            )
            .map { it.value }
    }
    SongSort.TITLE_ASC -> sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    SongSort.TITLE_DESC -> sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title })
    // A catalogue row carries no added date, so its place in the running
    // order stands in for one: a playlist is appended to as songs are added,
    // and reversed that puts the most recent addition on top. Rows that do
    // carry a MediaStore timestamp — device tracks, downloads — are dated
    // properly, with the position order left to break the ties.
    SongSort.DATE_ADDED_ASC -> withIndex()
        .sortedWith(
            compareBy<IndexedValue<Song>> { (_, song) ->
                song.localDateAddedSeconds ?: Long.MAX_VALUE
            }.thenBy { (position, _) -> position },
        )
        .map { it.value }
    SongSort.DATE_ADDED_DESC -> withIndex()
        .sortedWith(
            compareByDescending<IndexedValue<Song>> { (_, song) ->
                song.localDateAddedSeconds ?: Long.MIN_VALUE
            }.thenByDescending { (position, _) -> position },
        )
        .map { it.value }
}
