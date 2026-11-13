package com.music.bitchord

import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryShelfOrderingTest {

    private fun item(id: String, title: String) = ShelfItem(
        title = title,
        subtitle = "Playlist",
        thumbnailUrl = null,
        videoId = null,
        browseId = id,
    )

    @Test
    fun `pinned playlists appear at the start of the shelf items`() {
        val p1 = item("VL1", "Chill Beats")
        val p2 = item("VL2", "Workout Mix")
        val p3 = item("VL3", "Study Session")
        val shelf = HomeShelf("Playlists", listOf(p1, p2, p3))

        val pinned = listOf("VL3")
        val byId = shelf.items.filter { it.browseId != null }.associateBy { it.browseId }
        val pinnedItems = pinned.mapNotNull { byId[it] }
        val reordered = pinnedItems + shelf.items.filter { it !in pinnedItems.toSet() }

        assertEquals(listOf(p3, p1, p2), reordered)
    }

    @Test
    fun `empty pinned list leaves original playlist order unchanged`() {
        val p1 = item("VL1", "Rock")
        val p2 = item("VL2", "Jazz")
        val shelf = HomeShelf("Playlists", listOf(p1, p2))

        val pinned = emptyList<String>()
        val byId = shelf.items.filter { it.browseId != null }.associateBy { it.browseId }
        val pinnedItems = pinned.mapNotNull { byId[it] }
        val reordered = if (pinnedItems.isEmpty()) shelf.items else pinnedItems + shelf.items.filter { it !in pinnedItems.toSet() }

        assertEquals(listOf(p1, p2), reordered)
    }
}
