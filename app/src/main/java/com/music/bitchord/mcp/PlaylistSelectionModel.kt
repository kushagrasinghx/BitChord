package com.music.bitchord.mcp

import java.util.Locale

/** Pure presentation model. Bulk operations intentionally use the unfiltered ID list. */
internal data class PlaylistSelectionModel(
    val allIds: List<String>,
    val selectedIds: Set<String>,
) {
    val selectedCount: Int get() = allIds.count { it in selectedIds }
    val allSelected: Boolean get() = allIds.isNotEmpty() && selectedCount == allIds.size
    val partiallySelected: Boolean get() = selectedCount > 0 && !allSelected
    fun bulkSelection(): Pair<List<String>, Boolean> = allIds to !allSelected

    companion object {
        fun <T> filterAndSort(
            playlists: List<T>, query: String, locale: Locale, title: (T) -> String,
        ): List<T> {
            val normalized = query.trim().lowercase(locale)
            return playlists.filter { title(it).lowercase(locale).contains(normalized) }
                .sortedBy { title(it).lowercase(locale) }
        }
    }
}
