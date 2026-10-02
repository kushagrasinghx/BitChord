package com.music.bitchord.ui

import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.RecommendationFeedbackAction
import com.music.bitchord.data.model.ShelfItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeRecommendationFeedbackTest {
    private fun item(title: String) = ShelfItem(
        title = title,
        subtitle = "Artist",
        thumbnailUrl = null,
        videoId = "video-$title",
        browseId = null,
        dontRecommendArtist = RecommendationFeedbackAction("Hide", "forward", "undo"),
    )

    @Test
    fun `success removes only the exact originating card`() {
        val duplicate = item("same")
        val shelves = listOf(HomeShelf("First", listOf(duplicate, item("other"))), HomeShelf("Second", listOf(duplicate)))
        val target = HomeRecommendationTarget(HomeItemLocation(1, 0, "Second"), duplicate)

        val (updated, removed) = removeHomeRecommendation(shelves, target)!!

        assertEquals(listOf("same", "other"), updated[0].items.map { it.title })
        assertEquals(emptyList<ShelfItem>(), updated[1].items)
        assertEquals(HomeItemLocation(1, 0, "Second"), removed.location)
    }

    @Test
    fun `stale location never removes a different card`() {
        val shelves = listOf(HomeShelf("First", listOf(item("new"))))
        val target = HomeRecommendationTarget(HomeItemLocation(0, 0, "First"), item("old"))
        assertNull(removeHomeRecommendation(shelves, target))
    }

    @Test
    fun `undo restores the card at its original position`() {
        val original = listOf(HomeShelf("First", listOf(item("a"), item("b"), item("c"))))
        val target = HomeRecommendationTarget(HomeItemLocation(0, 1, "First"), original[0].items[1])
        val (removedShelves, removed) = removeHomeRecommendation(original, target)!!

        assertEquals(original, restoreHomeRecommendation(removedShelves, removed))
    }

    @Test
    fun `restore inserts without overwriting items that arrived later`() {
        val removed = RemovedHomeRecommendation(HomeItemLocation(0, 1, "First"), item("restored"))
        val current = listOf(HomeShelf("First", listOf(item("a"), item("later"))))

        val restored = restoreHomeRecommendation(current, removed)!!

        assertEquals(listOf("a", "restored", "later"), restored.single().items.map { it.title })
    }

    @Test
    fun `independent completions still remove two different cards after indices shift`() {
        val first = item("a")
        val second = item("b")
        val original = listOf(HomeShelf("First", listOf(first, second, item("c"))))
        val firstTarget = HomeRecommendationTarget(HomeItemLocation(0, 0, "First"), first)
        val secondTarget = HomeRecommendationTarget(HomeItemLocation(0, 1, "First"), second)

        val afterFirst = removeHomeRecommendation(original, firstTarget)!!.first
        val afterSecond = removeHomeRecommendation(afterFirst, secondTarget)!!.first

        assertEquals(listOf("c"), afterSecond.single().items.map { it.title })
    }

    @Test
    fun `undo cannot restore into a shelf that no longer exists`() {
        val removed = RemovedHomeRecommendation(HomeItemLocation(2, 0, "Gone"), item("gone"))
        assertNull(restoreHomeRecommendation(listOf(HomeShelf("Only", emptyList())), removed))
    }
}
