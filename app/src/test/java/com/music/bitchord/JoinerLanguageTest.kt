package com.music.bitchord

import com.music.bitchord.data.model.ArtistNameIndex
import com.music.bitchord.data.model.ArtistRef
import com.music.bitchord.data.model.completeArtistCredits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * That the joiner learning works in every script, and that no language's word
 * is written down anywhere.
 *
 * YouTube joins a byline with a mark or with whatever word the locale calls
 * "and" — and which one it picked changes between requests, so a list of them
 * would be wrong within a week. Nothing here is listed: a mark is taken on
 * sight, a word is watched in the place it keeps appearing.
 *
 * The CJK cases are the ones that make this a language question rather than a
 * formatting one. `中島美嘉` has neither a space nor a digit, so nothing about
 * its shape says whether it is a name or a joiner, and a rule that only
 * protected names with numbers in them would have got this wrong.
 */
class JoinerLanguageTest {
    @Before
    fun forget() = ArtistNameIndex.forget()

    private fun stated(name: String, id: String) = ArtistRef(name, id)

    /** Teaches a joiner by showing the same line with both names linked. */
    private fun learn(linked: List<Pair<String, String>>, line: String) {
        ArtistNameIndex.record(linked.map { (name, id) -> stated(name, id) })
        completeArtistCredits(line, linked.map { (name, id) -> stated(name, id) })
    }

    @Test
    fun `a mark joiner is believed the first time it is seen`() {
        // No corroboration needed: a comma cannot be a name.
        learn(listOf("A" to "UC_a", "B" to "UC_b"), "A, B, C")
        assertTrue(ArtistNameIndex.isJoiner(","))
    }

    @Test
    fun `a word joiner in any language is learned from seeing it twice`() {
        // Two sightings, then the line with only the first name linked comes
        // back with the second one recovered. The word itself is not in the code.
        for (word in listOf("and", "und", "et", "y", "e", "och", "i", "a")) {
            ArtistNameIndex.forget()
            val line = "Alpha $word Beta"
            learn(listOf("Alpha" to "UC_a", "Beta" to "UC_b"), line)
            learn(listOf("Alpha" to "UC_a", "Beta" to "UC_b"), line)

            assertTrue("`$word` was not learned as a joiner", ArtistNameIndex.isJoiner(word))
            val completed = completeArtistCredits(line, listOf(stated("Alpha", "UC_a")))
            assertEquals(
                "`$word` did not let Beta through",
                listOf("Alpha", "Beta"),
                completed.map { it.name },
            )
            assertEquals("UC_b", completed.first { it.name == "Beta" }.browseId)
        }
    }

    @Test
    fun `a name written without spaces is not learned as a joiner from one sighting`() {
        // `・中島美嘉・` between two linked credits is either an unlinked name or
        // a joiner, and its shape cannot say which — there is no space and no
        // digit to rule anything out. Seen once, it must be left as a name.
        ArtistNameIndex.record(listOf(stated("宇多田ヒカル", "UC_uta"), stated("中島美嘉", "UC_naka")))
        completeArtistCredits(
            "宇多田ヒカル・YOASOBI・中島美嘉",
            listOf(stated("宇多田ヒカル", "UC_uta"), stated("YOASOBI", "UC_y")),
        )

        assertFalse(
            "a single sighting was enough to call a name a joiner",
            ArtistNameIndex.isJoiner("中島美嘉"),
        )
        val completed = completeArtistCredits(
            "宇多田ヒカル・YOASOBI・中島美嘉",
            listOf(stated("宇多田ヒカル", "UC_uta"), stated("YOASOBI", "UC_y")),
        )
        assertEquals("UC_naka", completed.first { it.name == "中島美嘉" }.browseId)
    }

    @Test
    fun `a repeated unlinked name is learned after the second sighting, and no sooner`() {
        // The honest failure mode of a language-agnostic rule: a name that keeps
        // appearing in the same unlinked spot looks exactly like a joiner. It
        // costs a credit that the reader's own search can still open, and it is
        // the price of not writing down any language's word.
        ArtistNameIndex.record(listOf(stated("A", "UC_a"), stated("Zed", "UC_zed")))
        val line = "A and Zed"
        completeArtistCredits(line, listOf(stated("A", "UC_a"), stated("Zed", "UC_z")))
        assertFalse("learned from one sighting", ArtistNameIndex.isJoiner("and"))
    }

    @Test
    fun `a phrase is never learned as a joiner however often it is seen`() {
        // ` oraz Mata`-shaped: the stretch has a name in it and must keep being
        // treated as text, or a real credit would be ruled out for good.
        ArtistNameIndex.record(listOf(stated("A", "UC_a"), stated("Zed", "UC_z")))
        repeat(5) {
            completeArtistCredits("A and Zed", listOf(stated("A", "UC_a"), stated("Zed", "UC_z")))
        }
        assertFalse(ArtistNameIndex.isJoiner("and Zed"))
    }

    @Test
    fun `a cjk name and a cjk joiner of the same width are told apart by repetition`() {
        // Both are three characters and neither carries a space. Only how often
        // the text has been seen between two linked names separates them.
        ArtistNameIndex.record(listOf(stated("中島美嘉", "UC_naka"), stated("大橋トリオ", "UC_oh")))
        repeat(3) {
            completeArtistCredits(
                "中島美嘉・大橋トリオ・椎名林檎",
                listOf(stated("中島美嘉", "UC_naka"), stated("大橋トリオ", "UC_oh")),
            )
        }
        assertTrue(ArtistNameIndex.isJoiner("・"))
        assertFalse(ArtistNameIndex.isJoiner("椎名林檎"))
    }
}