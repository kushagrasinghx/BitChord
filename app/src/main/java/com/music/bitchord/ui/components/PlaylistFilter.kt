package com.music.bitchord.ui.components

import java.text.Normalizer
import java.util.Locale

/** Lower case with accents and marks dropped, so "Beyoncé" is found by "beyonce". */
private fun folded(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFKD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)

/**
 * Add to playlist's Find a playlist: the playlists whose name holds every word
 * of [query], in any order, ignoring case and accents. All of them for a blank
 * query. Kept in the order they came in, which is the library's.
 */
fun <T> filterPlaylists(playlists: List<T>, query: String, name: (T) -> String): List<T> {
    val words = folded(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return playlists
    return playlists.filter { playlist ->
        val title = folded(name(playlist))
        words.all { it in title }
    }
}
