package com.music.bitchord

import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.withoutBlocked
import org.junit.Assert.assertEquals
import org.junit.Test

class BlockedFeedFilterTest {

    @Test
    fun `withoutBlocked removes matching videos, browse ids, and artists and drops empty shelves`() {
        val shelves = listOf(
            HomeShelf(
                title = "Quick picks",
                items = listOf(
                    ShelfItem("Keep Song", "Good Artist", null, "v1", null),
                    ShelfItem("Blocked Track", "Good Artist", null, "v2", null),
                    ShelfItem("Artist Track", "Blocked Artist", null, "v3", null),
                ),
            ),
            HomeShelf(
                title = "Recommended albums",
                items = listOf(
                    ShelfItem("Unwanted Album", "Someone", null, null, "MPREb_123"),
                ),
            ),
        )

        val filtered = shelves.withoutBlocked(
            blockedKeys = setOf("video:v2", "browse:MPREb_123"),
            blockedArtists = setOf("blocked artist"),
        )

        assertEquals(1, filtered.size)
        assertEquals("Quick picks", filtered[0].title)
        assertEquals(listOf("Keep Song"), filtered[0].items.map { it.title })
    }
}
