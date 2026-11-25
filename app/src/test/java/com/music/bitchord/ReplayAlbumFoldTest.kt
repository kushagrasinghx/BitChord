package com.music.bitchord

import com.music.bitchord.data.stats.DayStats
import com.music.bitchord.data.stats.ListeningStats
import com.music.bitchord.data.stats.NameEntry
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The same album filed under two keys — by name in a month before the track
 * carried an id, by id afterwards, or under two YouTube ids — reached the Replay
 * page as two rows with the same title and artist, and the list crashed on the
 * duplicate key. These pin the fold that turns them back into one row.
 */
class ReplayAlbumFoldTest {

    private fun album(id: String?, name: String, sub: String, ms: Long, plays: Int, art: String? = null) =
        NameEntry(name = name, sub = sub, art = art, id = id, ms = ms, plays = plays)

    @Test
    fun `album filed by name and by id becomes one row keeping the id`() {
        val byName = album(null, "Positions (Deluxe)", "Ariana Grande", ms = 4_000, plays = 2)
        val byId = album("MPREb_1", "Positions (Deluxe)", "Ariana Grande", ms = 6_000, plays = 3, art = "cover")

        val folded = ListeningStats.foldAlbumsByTitle(listOf(byName, byId))

        assertEquals(1, folded.size)
        assertEquals("MPREb_1", folded.single().id)
        assertEquals("cover", folded.single().art)
        assertEquals(10_000L, folded.single().ms)
        assertEquals(5, folded.single().plays)
    }

    @Test
    fun `two ids for the same record fold, matching title and artist case-insensitively`() {
        val a = album("MPREb_a", "Mexico", "Karan Aujla", ms = 3_000, plays = 1)
        val b = album("MPREb_b", "MEXICO", "karan aujla", ms = 1_000, plays = 1)

        val folded = ListeningStats.foldAlbumsByTitle(listOf(b, a))

        assertEquals(1, folded.size)
        assertEquals("MPREb_a", folded.single().id)
        assertEquals(4_000L, folded.single().ms)
    }

    @Test
    fun `different albums stay apart`() {
        val folded = ListeningStats.foldAlbumsByTitle(
            listOf(
                album("1", "Positions", "Ariana Grande", 1, 1),
                album("2", "Positions (Deluxe)", "Ariana Grande", 1, 1),
                album("3", "Positions", "Someone Else", 1, 1),
            ),
        )
        assertEquals(3, folded.size)
    }

    @Test
    fun `day breakdown is summed for the weekly view without touching the inputs`() {
        val byName = album(null, "Dunki", "Arijit Singh", 1_000, 1).apply {
            days["2026-11-16"] = DayStats(ms = 1_000, plays = 1)
        }
        val byId = album("MPREb_d", "Dunki", "Arijit Singh", 2_000, 1).apply {
            days["2026-11-16"] = DayStats(ms = 2_000, plays = 1)
        }

        val folded = ListeningStats.foldAlbumsByTitle(listOf(byName, byId)).single()

        assertEquals(DayStats(ms = 3_000, plays = 2), folded.days["2026-11-16"])
        assertEquals(DayStats(ms = 2_000, plays = 1), byId.days["2026-11-16"])
        assertEquals(2_000L, byId.ms)
    }
}
