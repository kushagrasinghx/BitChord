package com.music.bitchord

import com.music.bitchord.data.lyrics.EnhancedLrc
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricWord
import com.music.bitchord.data.lyrics.LyricsPlus
import com.music.bitchord.data.lyrics.TtmlLyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WordSyncTest {

    private fun List<LyricLine>.sung() = filterNot { it.isGap }

    // ---- Apple TTML, as BetterLyrics serves it ------------------------------

    /** Trimmed from a live lyrics-api.boidu.dev response. */
    private val ttml = """
        <tt xmlns="http://www.w3.org/ns/ttml" itunes:timing="Word" xml:lang="en">
          <body dur="3:21.570">
            <div begin="27.395" end="32.529" itunes:songPart="Verse">
              <p begin="27.395" end="28.960" itunes:key="L1" ttm:agent="v1">
                <span begin="27.395" end="27.549">I</span> <span begin="27.549" end="27.740">been</span> <span begin="27.740" end="28.077">tryna</span> <span begin="28.077" end="28.960">call</span>
              </p>
              <p begin="30.189" end="32.529" itunes:key="L2" ttm:agent="v1">
                <span begin="30.189" end="30.396">long</span> <span begin="31.839" end="31.996">e</span><span begin="31.996" end="32.529">nough</span>
              </p>
            </div>
          </body>
        </tt>
    """.trimIndent()

    @Test
    fun `reads word timings out of apple ttml`() {
        val lines = TtmlLyrics.parse(ttml).sung()
        assertEquals(2, lines.size)
        assertEquals("I been tryna call", lines[0].text)
        assertEquals(27_395L, lines[0].timeMs)
        assertTrue(lines[0].isWordSynced)
        assertEquals(
            listOf("I", "been", "tryna", "call"),
            lines[0].words.map { it.text },
        )
        assertEquals(27_549L, lines[0].words[0].endMs)
        assertEquals(28_960L, lines[0].endMs)
    }

    @Test
    fun `merges adjacent spans with no space between them into one word`() {
        // "e" + "nough" are separate spans; split, they would render "e nough".
        val line = TtmlLyrics.parse(ttml).sung()[1]
        assertEquals("long enough", line.text)
        assertEquals(listOf("long", "enough"), line.words.map { it.text })
        val enough = line.words[1]
        assertEquals(31_839L, enough.startMs)
        assertEquals(32_529L, enough.endMs)
    }

    @Test
    fun `skips translations and background vocals`() {
        val lines = TtmlLyrics.parse(
            """
            <tt><body><div>
              <p begin="1.0" end="2.0">
                <span begin="1.0" end="2.0">hello</span>
                <span ttm:role="x-bg" begin="1.5" end="2.0"><span begin="1.5" end="2.0">(ooh)</span></span>
                <span ttm:role="x-translation" xml:lang="es">hola</span>
              </p>
            </div></body></tt>
            """.trimIndent(),
        ).sung()
        assertEquals("hello", lines.single().text)
    }

    @Test
    fun `falls back to plain text for line-synced ttml`() {
        val lines = TtmlLyrics.parse(
            """<tt><body><div><p begin="00:12.50">just a line</p></div></body></tt>""",
        ).sung()
        assertEquals("just a line", lines.single().text)
        assertEquals(12_500L, lines.single().timeMs)
        assertTrue(lines.single().words.isEmpty())
    }

    @Test
    fun `parses every ttml clock shape`() {
        assertEquals(27_395L, TtmlLyrics.time("27.395"))
        assertEquals(65_200L, TtmlLyrics.time("1:05.20"))
        assertEquals(3_723_400L, TtmlLyrics.time("1:02:03.4"))
        assertEquals(1_500L, TtmlLyrics.time("1.5s"))
        assertEquals(250L, TtmlLyrics.time("250ms"))
        assertNull(TtmlLyrics.time(""))
        assertNull(TtmlLyrics.time(null))
    }

    @Test
    fun `bad xml yields nothing rather than throwing`() {
        assertEquals(emptyList<LyricLine>(), TtmlLyrics.parse("<tt><body><p begin="))
    }

    // ---- Enhanced LRC, as SimpMusic serves rich sync ------------------------

    @Test
    fun `reads word timings out of enhanced lrc`() {
        val lines = EnhancedLrc.parse(
            """
            [00:27.39]<00:27.39>I <00:27.54>been <00:27.74>tryna <00:28.07>call
            [00:30.18]<00:30.18>on <00:30.39>my <00:30.64>own
            """.trimIndent(),
        ).sung()
        assertEquals(2, lines.size)
        assertEquals("I been tryna call", lines[0].text)
        assertEquals(listOf("I", "been", "tryna", "call"), lines[0].words.map { it.text })
        // Only starts are written down, so a word ends where the next begins...
        assertEquals(27_540L, lines[0].words[0].endMs)
        // ...and the last word of a line ends where the next line starts.
        assertEquals(30_180L, lines[0].words.last().endMs)
    }

    @Test
    fun `plain lrc is left for the line-synced parser`() {
        assertEquals(
            emptyList<LyricLine>(),
            EnhancedLrc.parse("[00:12.00] no word stamps here\n[00:15.00] none here either"),
        )
    }

    @Test
    fun `decodes the html entities simpmusic escapes`() {
        val line = EnhancedLrc.parse("[00:01.00]<00:01.00>don&#x27;t <00:01.50>stop").sung().single()
        assertEquals("don't stop", line.text)
    }

    @Test
    fun `does not double-decode an escaped ampersand`() {
        assertEquals("&#x27;", EnhancedLrc.decodeEntities("&amp;#x27;"))
    }

    // ---- LyricsPlus / YouLy+ syllables --------------------------------------

    @Test
    fun `merges lyricsplus syllables on their trailing space`() {
        val lines = LyricsPlus.parse(
            LyricsPlus.Response(
                type = "Word",
                lyrics = listOf(
                    LyricsPlus.Line(
                        time = 30_189,
                        duration = 2_340,
                        text = "long enough",
                        syllabus = listOf(
                            LyricsPlus.Syllable(time = 30_189, duration = 341, text = "long "),
                            LyricsPlus.Syllable(time = 31_839, duration = 157, text = "e"),
                            LyricsPlus.Syllable(time = 31_996, duration = 533, text = "nough"),
                        ),
                    ),
                ),
            ),
        ).sung()
        val line = lines.single()
        assertEquals("long enough", line.text)
        assertEquals(listOf("long", "enough"), line.words.map { it.text })
        assertEquals(30_189L, line.words[0].startMs)
        assertEquals(32_529L, line.words[1].endMs)
    }

    // ---- The sweep's own arithmetic -----------------------------------------

    private val line = LyricLine(
        timeMs = 1_000,
        text = "one two",
        words = listOf(
            LyricWord(1_000, 1_500, "one"),
            LyricWord(2_000, 2_400, "two"),
        ),
    )

    @Test
    fun `reveal runs across a word over that word's own span`() {
        assertEquals(0f, line.revealedChars(500), 0.01f)
        assertEquals(0f, line.revealedChars(1_000), 0.01f)
        // Halfway through "one".
        assertEquals(1.5f, line.revealedChars(1_250), 0.01f)
        assertEquals(3f, line.revealedChars(1_500), 0.01f)
    }

    @Test
    fun `the pause between words fills the space between them`() {
        // 1500..2000 is silence; the space at index 3 fills across it rather
        // than the highlight sitting still on the end of "one".
        assertEquals(3.5f, line.revealedChars(1_750), 0.01f)
        assertEquals(4f, line.revealedChars(2_000), 0.01f)
    }

    @Test
    fun `reveal covers the whole line once the last word is done`() {
        assertEquals(7f, line.revealedChars(2_400), 0.01f)
        assertEquals(7f, line.revealedChars(99_000), 0.01f)
    }

    @Test
    fun `a repeated word lines up with its own occurrence`() {
        val repeated = LyricLine(
            timeMs = 0,
            text = "go go go",
            words = listOf(
                LyricWord(0, 100, "go"),
                LyricWord(1_000, 1_100, "go"),
                LyricWord(2_000, 2_100, "go"),
            ),
        )
        // Start of the third "go" is character 6, not character 0.
        assertEquals(6f, repeated.revealedChars(2_000), 0.01f)
    }

    // ---- Glow intensity ------------------------------------------------------

    /** Peak intensity reached anywhere inside a word of the given length. */
    private fun peakGlowFor(heldMs: Long): Float {
        val held = LyricLine(
            timeMs = 0,
            text = "ah",
            words = listOf(LyricWord(0, heldMs, "ah")),
        )
        return (0..heldMs step 5).maxOf { held.glowIntensity(it) }
    }

    @Test
    fun `a held note blooms and patter barely does`() {
        val slow = peakGlowFor(900)
        val quick = peakGlowFor(120)
        assertEquals(1f, slow, 0.02f)
        assertTrue("patter should stay dim, was $quick", quick < 0.3f)
        assertTrue("a held note should far outglow patter", slow > quick * 3f)
    }

    @Test
    fun `intensity climbs with how long the word is held`() {
        val steps = listOf(150L, 300L, 500L, 800L).map { peakGlowFor(it) }
        steps.zipWithNext { lower, higher ->
            assertTrue("$lower should not exceed $higher", lower <= higher + 0.001f)
        }
        assertTrue("the shortest and longest should differ", steps.last() - steps.first() > 0.5f)
    }

    @Test
    fun `each word blooms and lets go rather than staying lit`() {
        val word = LyricLine(0, "ah", listOf(LyricWord(0, 1_000, "ah")))
        // Dark at both ends of the word, brightest somewhere in the middle.
        assertEquals(0f, word.glowIntensity(0), 0.01f)
        assertEquals(0f, word.glowIntensity(1_000), 0.01f)
        assertTrue(word.glowIntensity(500) > 0.9f)
    }

    @Test
    fun `pauses between and after words are dark`() {
        val gapped = LyricLine(
            timeMs = 0,
            text = "one two",
            words = listOf(LyricWord(0, 200, "one"), LyricWord(900, 1_100, "two")),
        )
        assertEquals(0f, gapped.glowIntensity(500), 0.001f)
        assertEquals(0f, gapped.glowIntensity(5_000), 0.001f)
    }

    @Test
    fun `a line with no word timings never glows`() {
        val plain = LyricLine(0, "no timings here")
        assertEquals(0f, plain.glowIntensity(500), 0.001f)
    }

    @Test
    fun `a line with no word timings reveals whole`() {
        val plain = LyricLine(1_000, "no timings here")
        assertEquals(0f, plain.revealedChars(999), 0.01f)
        assertEquals(15f, plain.revealedChars(1_000), 0.01f)
    }
}
