package com.music.bitchord

import com.music.bitchord.data.canvas.normalizeForMatch
import com.music.bitchord.data.model.isExactArtistMatch
import com.music.bitchord.data.model.normalizedArtistName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule that decides whether a credited name may be opened by name.
 *
 * YouTube links the credited artists inconsistently: on a real two-artist track
 * the byline linked only the first, and on the same album another row linked two
 * of three names, leaving out the third, who has a channel, which a fourth row on
 * a different album duly stated. So a name without an id is ordinary rather than
 * broken, and the only thing left to go on is the name itself.
 *
 * Which is exactly what makes this rule necessary. Asking for `2115` returns
 * White 2115, Bedoes 2115, Blacha 2115 and Kuqe 2115, four other people, so a
 * name is accepted only when the answer's own title is the name asked for.
 *
 * Nothing here is written for one language. The folding is case, accents and
 * spacing and nothing else, which behaves the same in every script, and the
 * cases below cover the ones that fold differently or not at all.
 */
class ArtistNameMatchTest {
    /** The rule the repository applies, tested rather than re-stated. */
    private fun matches(answer: String, wanted: String) = isExactArtistMatch(answer, wanted)

    @Test
    fun `the same name spelled the same way matches`() {
        assertTrue(matches("Monday Waxie", "Monday Waxie"))
        assertTrue(matches("White 2115", "White 2115"))
        assertTrue(matches("Bedoes 2115", "Bedoes 2115"))
        assertTrue(matches("vkie", "vkie"))
        assertTrue(matches("slowez", "slowez"))
    }

    @Test
    fun `case and surrounding air do not decide it`() {
        // YouTube's byline and its channel titles spell the same artist the same
        // way, but a row read from a different endpoint may not, and a reader
        // does not care about capitalisation.
        assertTrue(matches("mata", "Mata"))
        assertTrue(matches("MONDAY WAXIE", "Monday Waxie"))
        assertTrue(matches("  Monday Waxie  ", "Monday Waxie"))
    }

    @Test
    fun `spacing inside a name does not decide it`() {
        // A locale that wraps a line, or an upload that doubles a space, is not
        // a different artist.
        assertTrue(matches("Monday  Waxie", "Monday Waxie"))
        assertTrue(matches("Jay   Z", "Jay Z"))
    }

    @Test
    fun `accents are folded away`() {
        // Only the marks a locale puts on its letters, which say nothing about
        // who wrote it. This is the one piece of decoration let go.
        assertTrue(matches("Beyonce", "Beyoncé"))
        assertTrue(matches("Björk", "Bjork"))
        assertTrue(matches("Mötley Crüe", "Motley Crue"))
    }

    @Test
    fun `punctuation is not folded away`() {
        // Deliberately the opposite of the canvas matcher, which turns every
        // non-alphanumeric character into a space. That is how `AC/DC` and
        // `AC DC` come to mean one thing there; here they stay two, because the
        // cost of a false match is opening somebody else's page. Trimming is
        // enough for the trailing punctuation a name actually turns up with.
        assertFalse(matches("Jay-Z", "Jay Z"))
        assertFalse(matches("Mata.", "Mata"))
        assertTrue(matches("  Mata  ", "Mata"))
    }

    @Test
    fun `a name that is a fragment of the answer does not match`() {
        // The case this exists for. Every one of these came back as a result for
        // `2115`, and every one of them is somebody else.
        for (other in listOf(
            "White 2115",
            "Bedoes 2115",
            "Blacha 2115",
            "Kuqe 2115",
        )) {
            assertFalse("opened $other for a tap on 2115", matches(other, "2115"))
        }
    }

    @Test
    fun `a longer name is not accepted for a shorter one`() {
        assertFalse(matches("Mate", "Mata"))
        assertFalse(matches("Mata", "Mate"))
        assertFalse(matches("vkie fan", "vkie"))
    }

    @Test
    fun `a name written without spaces is compared whole`() {
        // The scripts that do not separate names with spaces are the ones where a
        // substring rule would do the most damage: these are not the same artist,
        // and none of them is cut up, so none has to be put back together.
        assertFalse(matches("美嘉", "中島美嘉"))
        assertFalse(matches("春樹", "村上春樹"))
        assertFalse(matches("林檎", "椎名林檎"))
        assertTrue(matches("中島美嘉", "中島美嘉"))
    }

    @Test
    fun `a name in another script is not folded into another`() {
        assertFalse(matches("Мата", "Мета"))
        assertFalse(matches("माता", "मेता"))
        assertFalse(matches("Μάτα", "Μέτα"))
        assertTrue(matches("Мата", "Мата"))
    }

    @Test
    fun `the normaliser does not collapse two real artists into one`() {
        // Guards the rule above from being defeated by over-eager folding: if
        // these ever compared equal, an exact-name check would stop protecting
        // anything.
        assertNotEquals(normalizedArtistName("Mata"), normalizedArtistName("Mate"))
        assertNotEquals(normalizedArtistName("2115"), normalizedArtistName("White 2115"))
        assertNotEquals(normalizedArtistName("Bedoes 2115"), normalizedArtistName("2115"))
    }

    @Test
    fun `a blank name is refused rather than folded`() {
        assertEquals("", normalizedArtistName("  "))
        // Both sides fold to nothing, so the comparison on its own would accept
        // either blank for the other — and, past that, a blank answer for a blank
        // question. Refusing the blank name is what stops that, and it has to be
        // the rule itself rather than a guard the caller might forget.
        assertFalse(matches("", ""))
        assertFalse(matches("   ", ""))
        assertFalse(matches("Monday Waxie", "  "))
        assertFalse(matches("  ", "Monday Waxie"))
    }

    @Test
    fun `a name in a script the canvas matcher cannot read is still readable here`() {
        // The reason this has its own folding rather than reusing the canvas
        // matcher's. That one reduces everything outside a-z0-9 to a space, so
        // all four of these fold to the same empty string, and an exact-name
        // check built on it would accept any of them for any of the others.
        val empty = ""
        for (name in listOf("Мата", "Мета", "中島美嘉", "माता")) {
            assertEquals("$name folds to nothing there", empty, name.normalizeForMatch())
        }

        assertNotEquals(normalizedArtistName("Мата"), normalizedArtistName("Мета"))
        assertNotEquals(normalizedArtistName("中島美嘉"), normalizedArtistName("椎名林檎"))
        assertNotEquals(normalizedArtistName("माता"), normalizedArtistName("मेता"))
        assertTrue(matches("Мата", "Мата"))
    }
}