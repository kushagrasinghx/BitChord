@file:OptIn(ExperimentalJsExport::class)

package com.music.bitchord.web

import com.music.bitchord.data.model.KeyedMix
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.SongSort
import com.music.bitchord.ui.screens.sortedForDetail
import com.music.bitchord.ui.screens.groupMixes as screenGroupMixes

/** MixGroups.kt `MoodMixes`: one mood's mixes, as positions in the arrays given to [groupMixes]. */
@JsExport
class MixMood(val key: String, val name: String, val items: Array<Int>)

/** MixGroups.kt `MixSections`: the Radio page's named mixes, the listener's own, and a row per mood. */
@JsExport
class MixGroups(val stations: Array<Int>, val yours: Array<Int>, val moods: Array<MixMood>)

/**
 * MixGroups.kt `groupMixes`: the "Mixed for you" cards sorted by their English
 * titles into the Radio page's three kinds. The arrays run in parallel, one entry
 * per card: its playlist id, the title it is shown with, and its English title.
 */
@JsExport
fun groupMixes(ids: Array<String>, titles: Array<String>, englishTitles: Array<String>): MixGroups {
    val items = ids.indices.map { i ->
        ShelfItem(title = titles[i], subtitle = "", thumbnailUrl = null, videoId = null, browseId = ids[i].ifEmpty { null })
    }
    val sections = screenGroupMixes(items.mapIndexed { i, item -> KeyedMix(item, englishTitles[i]) })
    fun List<ShelfItem>.positions() = map(items::indexOf).toTypedArray()
    return MixGroups(
        stations = sections.stations.positions(),
        yours = sections.yours.positions(),
        moods = sections.moods.map { MixMood(it.key, it.name, it.items.positions()) }.toTypedArray(),
    )
}

/** SongSort.kt, by name: the orders an album or playlist page can show its songs in. */
@JsExport
fun songSorts(): Array<String> = SongSort.entries.map { it.name }.toTypedArray()

/**
 * DetailSort.kt `sortedForDetail` on a page of catalogue rows, which carry a title
 * and a position and nothing else it sorts by: the order to show [titles] in, as
 * positions. An unknown [sort] is the default order, as the app reads a stale setting.
 */
@JsExport
fun detailSortOrder(titles: Array<String>, sort: String): Array<Int> {
    val songs = titles.mapIndexed { i, title -> Song(videoId = i.toString(), title = title, artist = "", thumbnailUrl = null) }
    val order = SongSort.entries.firstOrNull { it.name == sort } ?: SongSort.DEFAULT
    return songs.sortedForDetail(order).map { it.videoId.toInt() }.toTypedArray()
}
