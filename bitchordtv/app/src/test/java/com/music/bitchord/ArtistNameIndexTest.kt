package com.music.bitchord

import com.music.bitchord.data.model.ArtistNameIndex
import com.music.bitchord.data.model.ArtistRef
import com.music.bitchord.data.model.completeArtistCredits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Completing a byline from what the app has already been told.
 *
 * YouTube links the credited artists inconsistently, and it does it row by row.
 * On one real album a row read `CBW, White 2115, slowez` and linked CBW and
 * slowez, leaving out White 2115 — whose channel a row on a different album
 * stated outright. So the gap is in the response, not in the artist, and the
 * fix is to have kept what the other rows said.
 *
 * The second thing this has to get right is the leftover text. YouTube joins a
 * collaboration joined by a word rather than a comma - which sits in the
 * middle of the line looking exactly like a name that was never linked.
 */
class ArtistNameIndexTest {
    @Before
    fun forget() = ArtistNameIndex.forget()

    private fun stated(name: String, id: String) = ArtistRef(name, id)

    @Test
    fun `a channel stated by one row fills a name another row left unlinked`() {
        ArtistNameIndex.record(listOf(stated("White 2115", "UC_white")))

        // The album row: linked CBW and slowez, only named White 2115.
        val completed = completeArtistCredits(
            "CBW, White 2115, slowez",
            listOf(stated("CBW", "UC_cbw"), stated("slowez", "UC_slowez")),
        )

        assertEquals(
            listOf("CBW", "White 2115", "slowez"),
            completed.map { it.name },
        )
        assertEquals("UC_white", completed.first { it.name == "White 2115" }.browseId)
    }

    @Test
    fun `a word joining two credited artists is punctuation and stays punctuation`() {
        // A byline may join its artists with a word instead of a comma. Which word
        // depends on the locale and changes between requests, so it is learned
        // rather than listed — and, being a word, it takes a second sighting to be
        // believed, because an unlinked name in the same place looks identical.
        // JoinerLanguageTest covers the same rule across eight languages.
        ArtistNameIndex.record(
            listOf(stated("Bedoes 2115", "UC_b"), stated("Lanek", "UC_l")),
        )
        repeat(2) {
            completeArtistCredits(
                "Bedoes 2115 and Lanek",
                listOf(stated("Bedoes 2115", "UC_b"), stated("Lanek", "UC_l")),
            )
        }
        assertTrue(ArtistNameIndex.isJoiner("and"))

        // And a later row that links only the first is completed with the second,
        // leaving the joiner alone.
        val completed = completeArtistCredits(
            "Bedoes 2115 and Lanek",
            listOf(stated("Bedoes 2115", "UC_b")),
        )

        assertEquals(listOf("Bedoes 2115", "Lanek"), completed.map { it.name })
        assertEquals("UC_l", completed.first { it.name == "Lanek" }.browseId)
    }

    @Test
    fun `a leftover nobody has ever linked is left for the reader to search`() {
        // Nothing is invented: a name the app has no channel for stays out, and
        // the tap falls through to a search that opens only an exact match.
        val completed = completeArtistCredits("CBW and Monday Waxie", listOf(stated("CBW", "UC_cbw")))
        assertEquals(listOf("CBW"), completed.map { it.name })
    }

    @Test
    fun `a name already carrying a channel is not looked up again`() {
        ArtistNameIndex.record(listOf(stated("Monday Waxie", "UC_stale")))
        val completed = completeArtistCredits(
            "CBW, Monday Waxie",
            listOf(stated("CBW", "UC_cbw"), stated("Monday Waxie", "UC_fresh")),
        )
        assertEquals(2, completed.size)
        assertEquals("UC_fresh", completed.first { it.name == "Monday Waxie" }.browseId)
    }

    @Test
    fun `two channels under one name are never resolved to either`() {
        // Two artists can share a name, and which one a row meant is unknowable
        // from the row. The name is kept and never used.
        ArtistNameIndex.record(listOf(stated("Mate", "UC_one")))
        ArtistNameIndex.record(listOf(stated("Mate", "UC_two")))

        assertNull(ArtistNameIndex.lookup("Mate"))
        val completed = completeArtistCredits("Bedoes & Mate", listOf(stated("Bedoes", "UC_b")))
        assertEquals(listOf("Bedoes"), completed.map { it.name })
    }

    @Test
    fun `the same channel seen twice is still one answer`() {
        ArtistNameIndex.record(listOf(stated("Mata", "UC_mata")))
        ArtistNameIndex.record(listOf(stated("Mata", "UC_mata")))
        assertEquals("UC_mata", ArtistNameIndex.lookup("Mata"))
    }

    @Test
    fun `lookup ignores case and surrounding air`() {
        ArtistNameIndex.record(listOf(stated("Monday Waxie", "UC_mw")))
        assertEquals("UC_mw", ArtistNameIndex.lookup("monday waxie"))
        assertEquals("UC_mw", ArtistNameIndex.lookup("  Monday Waxie "))
    }

    @Test
    fun `nothing learned means nothing offered`() {
        assertNull(ArtistNameIndex.lookup("Monday Waxie"))
        assertFalse(ArtistNameIndex.isJoiner("and"))
    }

    @Test
    fun `a blank line keeps what it was given rather than dropping it`() {
        // Nothing to complete, so nothing is removed: the credits a row stated
        // are the row's own and are not this function's to discard.
        ArtistNameIndex.record(listOf(stated("CBW", "UC_cbw")))
        assertEquals(
            listOf("CBW"),
            completeArtistCredits("   ", listOf(stated("CBW", "UC_cbw"))).map { it.name },
        )
        assertEquals(emptyList<ArtistRef>(), completeArtistCredits("", emptyList()))
    }

    @Test
    fun `an album and a credit never borrow each other's channel`() {
        // A row's album name can sit in the same byline. It is not an artist, and
        // nothing in the index ever holds it, so it stays out.
        ArtistNameIndex.record(listOf(stated("CBW", "UC_cbw")))
        val completed = completeArtistCredits("CBW • Undercover", listOf(stated("CBW", "UC_cbw")))
        assertEquals(listOf("CBW"), completed.map { it.name })
    }

    @Test
    fun `a completion never invents a second name`() {
        ArtistNameIndex.record(listOf(stated("A", "UC_a"), stated("B", "UC_b")))
        val completed = completeArtistCredits("A", listOf(stated("A", "UC_a")))
        assertTrue("completed past what the line says", completed.all { it.name in listOf("A") })
    }
}