package com.music.bitchord

import com.music.bitchord.data.model.ArtistCredit
import com.music.bitchord.data.model.ArtistNameIndex
import com.music.bitchord.data.model.ArtistRef
import com.music.bitchord.data.model.completeArtistCredits
import com.music.bitchord.data.model.creditSegments
import com.music.bitchord.data.scrobbling.primaryArtist
import com.music.bitchord.ui.player.artistCredits
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The credit line's names, where each one's page comes from, and how the line is
 * cut into the stretches that are drawn.
 *
 * Two rules. A track states one navigation endpoint per credited artist, so
 * those names open their own pages with nothing guessed. A row that states none
 * has only a string, and splitting it can produce a name that somebody else
 * also answers to — so those credits carry no id and are not offered as links
 * rather than searched for.
 */
class ArtistCreditsTest {
    @Before
    fun forgetWhatOtherTestsLearned() = ArtistNameIndex.forget()

    private fun stated(vararg refs: ArtistRef) =
        artistCredits(artist = "", artists = refs.toList(), primaryArtistId = null)

    private fun statedNames(vararg names: String) =
        artistCredits("", names.map { name -> ArtistRef(name) }, null).map { it.name }

    private fun split(line: String, primaryId: String? = null) =
        artistCredits(artist = line, artists = emptyList(), primaryArtistId = primaryId)

    // --- where the names and their pages come from

    @Test
    fun `every stated artist keeps its own page`() {
        val credits = stated(
            ArtistRef("BedeoSa", "UC_bedeosa"),
            ArtistRef("2115", "UC_2115"),
            ArtistRef("Flexxy", "UC_flexxy"),
        )
        assertEquals(listOf("BedeoSa", "2115", "Flexxy"), credits.map { it.name })
        assertEquals(
            listOf("UC_bedeosa", "UC_2115", "UC_flexxy"),
            credits.map { it.browseId },
        )
    }

    @Test
    fun `a name stated without a channel is kept but has nothing to open`() {
        val credits = stated(
            ArtistRef("BedeoSa", "UC_a"),
            ArtistRef("2115"),
        )
        assertEquals(listOf("BedeoSa", "2115"), credits.map { it.name })
        assertNull("a channelless name must not invent one", credits[1].browseId)
    }

    @Test
    fun `a blank stated name is dropped rather than drawn`() {
        assertEquals(
            listOf("BedeoSa", "2115"),
            statedNames("BedeoSa", "   ", "2115"),
        )
    }

    @Test
    fun `stated credits are used even when the credit line says something else`() {
        // The line is the display string; the endpoints are the truth about who
        // is on the track, and they are what has to be opened.
        val credits = artistCredits(
            artist = "BedeoSa, 2115, Flexxy",
            artists = listOf(ArtistRef("BedeoSa", "UC_a"), ArtistRef("2115", "UC_b")),
            primaryArtistId = "UC_stale",
        )
        assertEquals(listOf("BedeoSa", "2115"), credits.map { it.name })
        assertEquals(listOf("UC_a", "UC_b"), credits.map { it.browseId })
    }

    @Test
    fun `the lead credit of a split is the same name primaryArtist picks`() {
        for (line in listOf(
            "Anitta & Shakira",
            "Anitta, Shakira",
            "Drake ＆ 21 Savage",
            "Beyoncé, Jay-Z & Kanye West",
            "AC/DC",
            "Florence and the Machine",
            "Simon & Garfunkel",
            "Tyler, The Creator",
            "  spaced  &  out  ",
        )) {
            val credits = split(line)
            assertTrue("no credits parsed from: $line", credits.isNotEmpty())
            assertEquals(
                "lead credit disagrees with primaryArtist for: $line",
                line.primaryArtist(),
                credits.first().name,
            )
        }
    }

    @Test
    fun `a split hands the track's own id to the lead and to nobody else`() {
        val credits = split("Anitta & Shakira", primaryId = "UC_anitta")
        assertEquals(listOf("Anitta", "Shakira"), credits.map { it.name })
        assertEquals("UC_anitta", credits[0].browseId)
        assertNull(
            "the lead's id must not be handed to a different name",
            credits[1].browseId,
        )
    }

    @Test
    fun `a name the project rule leaves whole is left whole here`() {
        assertEquals(listOf("AC/DC"), split("AC/DC").map { it.name })
        assertEquals(listOf("Florence and the Machine"), split("Florence and the Machine").map { it.name })
    }

    @Test
    fun `a blank line is no credits rather than one empty one`() {
        assertEquals(emptyList<String>(), split("").map { it.name })
        assertEquals(emptyList<String>(), split("   ").map { it.name })
    }

    @Test
    fun `a dangling separator does not produce an empty credit`() {
        for (line in listOf("Anitta &", "Anitta,", "& Shakira", ", ,")) {
            assertTrue(
                "empty credit in: '$line' -> ${split(line).map { it.name }}",
                split(line).none { it.name.isBlank() },
            )
        }
    }

    // --- how the line is cut into the stretches that get drawn

    @Test
    fun `the stretches redraw the credit line character for character`() {
        // The whole point of cutting the line rather than joining the names: what
        // is drawn is what the source said, so a run that joins its artists with
        // the word draws as the word, in it.
        for (line in listOf(
            "2115, Bedoes 2115, White 2115",
            "Bedoes 2115 and Lanek",
            "White 2115, VVSimon, Palar and PMBTZ",
            "Anitta & Shakira",
            "BedeoSa",
        )) {
            val segments = creditSegments(line, split(line))
            assertEquals("stretches redrew the line differently for: $line", line, segments.joinToString("") { it.text })
        }
    }

    @Test
    fun `a live collaboration names every credit and every one is a link`() {
        // Real search-row text: `2115` is a channel of its own, distinct from
        // both `Bedoes 2115` and `White 2115`, and reading it as a fragment of
        // either is how a tap used to land on somebody else entirely.
        val credits = listOf(
            ArtistCredit("2115", "UCeL9Nc_StEiSMV9HVYhkMMA"),
            ArtistCredit("Bedoes 2115", "UCLLUIzfV9aOdnZN9ViIfSDQ"),
            ArtistCredit("White 2115", "UC8Yuo0-S4BMCXWzYajCbMug"),
        )
        val segments = creditSegments("2115, Bedoes 2115, White 2115", credits)

        assertEquals(
            listOf("2115", ", ", "Bedoes 2115", ", ", "White 2115"),
            segments.map { it.text },
        )
        assertEquals(
            listOf("UCeL9Nc_StEiSMV9HVYhkMMA", "UCLLUIzfV9aOdnZN9ViIfSDQ", "UC8Yuo0-S4BMCXWzYajCbMug"),
            segments.filter { it.credit != null }.map { it.credit?.browseId },
        )
    }

    @Test
    fun `a longer name wins over a shorter one that starts inside it`() {
        val credits = listOf(ArtistCredit("2115", "UC_a"), ArtistCredit("Bedoes 2115", "UC_b"))
        val segments = creditSegments("2115 i Bedoes 2115", credits)
        assertEquals(listOf("2115", " i ", "Bedoes 2115"), segments.map { it.text })
    }

    @Test
    fun `the punctuation between credits is drawn but is not tappable`() {
        val segments = creditSegments(
            "White 2115, PMBTZ",
            listOf(ArtistCredit("White 2115", "UC_w"), ArtistCredit("PMBTZ", "UC_p")),
        )
        val separator = segments.single { it.text == ", " }
        assertNull("a comma has no page behind it", separator.credit)
    }

    @Test
    fun `a name the line spells differently is drawn as the line spells it`() {
        // Case is not what tells two artists apart, so `Bedeosa` and `BedeoSa`
        // are one artist — but the drawing is still the line's own spelling, and
        // the tap opens the credit that matched rather than a substring of it.
        val segments = creditSegments(
            "Bedeosa, 2115",
            listOf(ArtistCredit("BedeoSa", "UC_a"), ArtistCredit("2115", "UC_b")),
        )
        assertEquals("Bedeosa, 2115", segments.joinToString("") { it.text })
        assertEquals(
            listOf("UC_a", "UC_b"),
            segments.filter { it.credit != null }.map { it.credit?.browseId },
        )
    }

    @Test
    fun `a credit with no channel is drawn but carries no link`() {
        val segments = creditSegments(
            "Anitta & Shakira",
            split("Anitta & Shakira", primaryId = "UC_anitta"),
        )
        assertEquals(listOf("Anitta", " & ", "Shakira"), segments.map { it.text })
        assertEquals("UC_anitta", segments[0].credit?.browseId)
        assertNull(segments[2].credit?.browseId)
    }

    @Test
    fun `a line with nothing credited is one plain stretch`() {
        val segments = creditSegments("BedeoSa, 2115", emptyList())
        assertEquals(listOf("BedeoSa, 2115"), segments.map { it.text })
        assertTrue("nothing on the line can be tapped", segments.none { it.credit != null })
    }

    @Test
    fun `an empty line is no stretches at all`() {
        assertEquals(emptyList<String>(), creditSegments("", split("")).map { it.text })
    }
}