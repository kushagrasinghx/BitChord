package com.music.bitchord

import com.music.bitchord.data.lyrics.CharGrowth
import com.music.bitchord.data.lyrics.EnhancedLrc
import com.music.bitchord.data.lyrics.GrowingWord
import com.music.bitchord.data.lyrics.LyricAlignment
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricWord
import com.music.bitchord.data.lyrics.LyricsPlus
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import com.music.bitchord.data.lyrics.TtmlLyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `skips translations and hangs background vocals under the lead`() {
        val lines = TtmlLyrics.parse(
            """
            <tt><body><div>
              <p begin="1.0" end="2.0">
                <span begin="1.0" end="2.0">hello</span>
                <span ttm:role="x-bg" begin="1.5" end="2.4"><span begin="1.5" end="2.4">(ooh)</span></span>
                <span ttm:role="x-translation" xml:lang="es">hola</span>
              </p>
            </div></body></tt>
            """.trimIndent(),
        ).sung()
        val line = lines.single()
        assertEquals("hello", line.text)
        assertEquals("(ooh)", line.background?.text)
        // Its own stamp, not the lead's — it starts partway through the line.
        assertEquals(1_500L, line.background?.timeMs)
        assertTrue(line.background!!.isWordSynced)
        // And the line is not over until the answer is.
        assertEquals(2_400L, line.endMs)
    }

    /** A backing span written as a single timed leaf, with no syllables in it. */
    @Test
    fun `reads a background vocal that is one timed span`() {
        val line = TtmlLyrics.parse(
            """
            <tt><body><div>
              <p begin="1.0" end="2.0">
                <span begin="1.0" end="2.0">hello</span>
                <span ttm:role="x-bg" begin="1.5" end="2.0">(ooh)</span>
              </p>
            </div></body></tt>
            """.trimIndent(),
        ).sung().single()
        assertEquals("hello", line.text)
        assertEquals("(ooh)", line.background?.text)
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

    /** Two named voices trading lines, as Apple writes a duet. */
    private val duet = """
        <tt xmlns="http://www.w3.org/ns/ttml">
          <head><metadata>
            <ttm:agent type="person" xml:id="v1"/>
            <ttm:agent type="person" xml:id="v2"/>
            <ttm:agent type="group" xml:id="v1000"/>
          </metadata></head>
          <body><div>
            <p begin="1.0" end="2.0" ttm:agent="v1">mine</p>
            <p begin="2.0" end="3.0" ttm:agent="v2">yours</p>
            <p begin="3.0" end="4.0" ttm:agent="v2">still yours</p>
            <p begin="4.0" end="5.0" ttm:agent="v1">mine again</p>
            <p begin="5.0" end="6.0" ttm:agent="v1000">both of us</p>
          </div></body>
        </tt>
    """.trimIndent()

    @Test
    fun `lays a duet out on alternating sides`() {
        val sides = TtmlLyrics.parse(duet).sung().map { it.alignment }
        assertEquals(
            listOf(
                LyricAlignment.Start,
                LyricAlignment.End,
                // The same voice twice running stays where it was: the side
                // changes when the singer does, not once per line.
                LyricAlignment.End,
                LyricAlignment.Start,
                // Sung by everyone, so it belongs to neither side.
                LyricAlignment.Start,
            ),
            sides,
        )
    }

    @Test
    fun `a song with one voice is all on one side`() {
        val sides = TtmlLyrics.parse(ttml).sung().map { it.alignment }
        assertEquals(listOf(LyricAlignment.Start, LyricAlignment.Start), sides)
    }

    @Test
    fun `a song that would land entirely on the right is flipped back`() {
        // One voice, declared `other`, which is the side the walk starts away
        // from — left alone this reads as a song sung entirely down the
        // right-hand margin.
        val sides = TtmlLyrics.parse(
            """
            <tt><head><metadata><ttm:agent type="other" xml:id="v2"/></metadata></head>
            <body><div>
              <p begin="1.0" end="2.0" ttm:agent="v2">one</p>
              <p begin="2.0" end="3.0" ttm:agent="v2">two</p>
            </div></body></tt>
            """.trimIndent(),
        ).sung().map { it.alignment }
        assertEquals(listOf(LyricAlignment.Start, LyricAlignment.Start), sides)
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

    private fun singer(id: String) = buildJsonObject { put("singer", JsonPrimitive(id)) }

    /**
     * The same duet the TTML tests cover, as LyricsPlus states it: the voices
     * are declared in the payload's metadata and each line names the one that
     * sang it. Field names taken from am-lyrics, which is the only description
     * of this part of the API there is.
     */
    @Test
    fun `lays out a lyricsplus duet from the voices it names`() {
        val sides = LyricsPlus.parse(
            LyricsPlus.Response(
                metadata = LyricsPlus.Metadata(
                    agents = mapOf(
                        "v1" to LyricsPlus.Agent(type = "person"),
                        "v2" to LyricsPlus.Agent(type = "person"),
                        "v1000" to LyricsPlus.Agent(type = "group"),
                    ),
                ),
                lyrics = listOf(
                    LyricsPlus.Line(time = 1_000, duration = 1_000, text = "mine", element = singer("v1")),
                    LyricsPlus.Line(time = 2_000, duration = 1_000, text = "yours", element = singer("v2")),
                    LyricsPlus.Line(time = 3_000, duration = 1_000, text = "still yours", element = singer("v2")),
                    LyricsPlus.Line(time = 4_000, duration = 1_000, text = "mine again", element = singer("v1")),
                    LyricsPlus.Line(time = 5_000, duration = 1_000, text = "both", element = singer("v1000")),
                ),
            ),
        ).filterNot { it.isGap }.map { it.alignment }
        assertEquals(
            listOf(
                LyricAlignment.Start,
                LyricAlignment.End,
                LyricAlignment.End,
                LyricAlignment.Start,
                LyricAlignment.Start,
            ),
            sides,
        )
    }

    @Test
    fun `a voice named by alias is matched to its declaration`() {
        val sides = LyricsPlus.parse(
            LyricsPlus.Response(
                metadata = LyricsPlus.Metadata(
                    // Declared under one key, referred to by another.
                    agents = mapOf("agent1" to LyricsPlus.Agent(type = "group", alias = "v1")),
                ),
                lyrics = listOf(
                    LyricsPlus.Line(time = 1_000, duration = 500, text = "everyone", element = singer("v1")),
                ),
            ),
        ).filterNot { it.isGap }.map { it.alignment }
        // A group line stays left; without the alias it would read as a person
        // and start the alternation.
        assertEquals(listOf(LyricAlignment.Start), sides)
    }

    @Test
    fun `the older tag list still marks the answering side`() {
        val sides = LyricsPlus.parse(
            LyricsPlus.Response(
                lyrics = listOf(
                    LyricsPlus.Line(time = 1_000, duration = 500, text = "mine"),
                    LyricsPlus.Line(
                        time = 2_000,
                        duration = 500,
                        text = "yours",
                        element = buildJsonArray { add(JsonPrimitive("opposite")) },
                    ),
                ),
            ),
        ).filterNot { it.isGap }.map { it.alignment }
        assertEquals(listOf(LyricAlignment.Start, LyricAlignment.End), sides)
    }

    @Test
    fun `a payload that says nothing about voices stays on one side`() {
        val sides = LyricsPlus.parse(
            LyricsPlus.Response(
                lyrics = listOf(
                    LyricsPlus.Line(time = 1_000, duration = 500, text = "one"),
                    LyricsPlus.Line(time = 2_000, duration = 500, text = "two"),
                ),
            ),
        ).filterNot { it.isGap }.map { it.alignment }
        assertEquals(listOf(LyricAlignment.Start, LyricAlignment.Start), sides)
    }

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

    // ---- Instrumental breaks -------------------------------------------------

    private fun lineSynced(vararg rows: Triple<Long, Long?, String>) = LyricsPlus.parse(
        LyricsPlus.Response(
            type = "Line",
            lyrics = rows.map { (time, duration, text) ->
                LyricsPlus.Line(time = time, duration = duration, text = text, syllabus = emptyList())
            },
        ),
    )

    /**
     * "Qayde Se", as LyricsPlus actually serves it: `type: Line`, empty
     * syllabus, and each line's duration running right up to the next stamp.
     * Ten seconds between stamps is one line sung over ten seconds, not a
     * ten-second break, so nothing but the intro should be marked.
     */
    @Test
    fun `a slowly sung line-synced song gets no break between its lines`() {
        val lines = lineSynced(
            Triple(19_740L, 10_020L, "दिल जला के मुस्कुराने की जो आदत हुई है मुझे"),
            Triple(29_760L, 9_910L, "लग रहा है, क़ायदे से अब मोहब्बत हुई है मुझे"),
            Triple(39_670L, 10_000L, "मेरी तुम्हीं से है जवाब-दारी"),
        )
        // Only the 19.7s run-up before the first word.
        assertEquals(1, lines.count { it.isGap })
        assertTrue(lines.first().isGap)
        assertEquals(3, lines.sung().size)
    }

    /**
     * The bug this guards: a break stamped at the same millisecond as a line
     * shadows it forever, because the cursor takes the *last* line whose stamp
     * has passed. Every line would show as a note and none would light up.
     */
    @Test
    fun `no break ever shares a stamp with the line it follows`() {
        val lines = lineSynced(
            Triple(19_740L, 10_020L, "one"),
            Triple(29_760L, 9_910L, "two"),
            Triple(39_670L, 10_000L, "three"),
        )
        val sungStamps = lines.sung().map { it.timeMs }.toSet()
        val clashes = lines.filter { it.isGap && it.timeMs in sungStamps }
        assertEquals(emptyList<LyricLine>(), clashes)
    }

    /** With no duration to go on, the distance to the next stamp proves nothing. */
    @Test
    fun `a line-synced source with no durations gets no synthesised breaks`() {
        val lines = lineSynced(
            Triple(1_000L, null, "one"),
            Triple(20_000L, null, "two"),
        )
        assertEquals(0, lines.count { it.isGap })
    }

    /** A stated end well short of the next line is a real break, and is drawn. */
    @Test
    fun `a stated line end short of the next stamp is marked as a break`() {
        val lines = lineSynced(
            Triple(1_000L, 2_000L, "before the solo"),
            Triple(30_000L, 2_000L, "after the solo"),
        )
        val gap = lines.single { it.isGap && it.timeMs > 0 }
        // The note lands when the singing stopped, not when the next line was due.
        assertEquals(3_000L, gap.timeMs)
    }

    /** Same for line-synced TTML, where the end lives on the `<p>`. */
    @Test
    fun `line-synced ttml takes its break from the paragraph end`() {
        val lines = TtmlLyrics.parse(
            """
            <tt><body><div>
              <p begin="1.0" end="3.0">before the solo</p>
              <p begin="30.0" end="32.0">after the solo</p>
            </div></body></tt>
            """.trimIndent(),
        )
        assertEquals(2, lines.sung().size)
        assertEquals(3_000L, lines.single { it.isGap && it.timeMs > 0 }.timeMs)
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

    // ---- Words held long enough to animate letter by letter -----------------

    private fun held(text: String, heldMs: Long) = LyricLine(
        timeMs = 0,
        text = text,
        words = listOf(LyricWord(0, heldMs, text)),
    )

    private fun GrowingWord.at(charIndex: Int, positionMs: Long) =
        CharGrowth().also { sampleInto(charIndex, positionMs, it) }

    @Test
    fun `a note carried earns the letter-by-letter treatment and patter does not`() {
        assertEquals(1, held("hold", 1_500).growingWords.size)
        assertTrue(held("hold", 300).growingWords.isEmpty())
    }

    @Test
    fun `how long a word must be held depends on how long it is`() {
        // Two letters over a second and a half is unmistakably a held note; the
        // same second and a half spread over seven letters is ordinary singing.
        assertTrue(held("ah", 1_500).growingWords.isNotEmpty())
        assertTrue(held("holding", 900).growingWords.isEmpty())
        assertTrue(held("holding", 1_500).growingWords.isNotEmpty())
        // Past seven letters there is no wave left to run, however long it goes.
        assertTrue(held("standing", 4_000).growingWords.isEmpty())
    }

    @Test
    fun `words that do not come apart into letters are left alone`() {
        assertTrue(held("一二三", 2_000).growingWords.isEmpty())
        assertTrue(held("re-do", 2_000).growingWords.isEmpty())
    }

    @Test
    fun `the swell travels along the word rather than pulsing at once`() {
        val word = held("golden", 2_000).growingWords.single()
        // Early on, the first letter is well up and the last has not started.
        assertTrue(word.at(0, 500).scale > word.at(5, 500).scale)
        assertEquals(1f, word.at(5, 500).scale, 0.001f)
        // Later the order reverses: the front of the word is settling back
        // while the tail of it is still coming up.
        assertTrue(word.at(5, 2_600).scale > word.at(0, 2_600).scale)
    }

    @Test
    fun `every letter comes to rest at the lift an ordinary sung word carries`() {
        val word = held("golden", 2_000).growingWords.single()
        val settled = word.at(0, word.restsAtMs)
        assertEquals(1f, settled.scale, 0.001f)
        assertEquals(0f, settled.shift, 0.001f)
        assertEquals(1f, settled.rise, 0.001f)
        assertEquals(0f, settled.bloom, 0.001f)
    }

    @Test
    fun `the bloom peaks partway up and is gone before the letter settles`() {
        val word = held("golden", 2_000).growingWords.single()
        assertEquals(0f, word.at(0, 0).bloom, 0.001f)
        assertTrue(word.at(0, 800).bloom > 0.3f)
        assertEquals(0f, word.at(0, 2_400).bloom, 0.001f)
    }

    @Test
    fun `letters lean away from the middle of the word as it swells`() {
        val word = held("golden", 2_000).growingWords.single()
        assertTrue(word.at(0, 800).shift < 0f)
        assertTrue(word.at(5, 2_300).shift > 0f)
    }

    @Test
    fun `a line of ordinary syllables has nothing to animate`() {
        val patter = LyricLine(
            timeMs = 0,
            text = "one two three",
            words = listOf(
                LyricWord(0, 200, "one"),
                LyricWord(200, 400, "two"),
                LyricWord(400, 700, "three"),
            ),
        )
        assertTrue(patter.growingWords.isEmpty())
        assertFalse(patter.isGrowing(300))
    }

    @Test
    fun `a line with no word timings has nothing to animate`() {
        assertTrue(LyricLine(0, "no timings here").growingWords.isEmpty())
        assertFalse(LyricLine(0, "no timings here").isGrowing(500))
    }

    @Test
    fun `a line with no word timings reveals whole`() {
        val plain = LyricLine(1_000, "no timings here")
        assertEquals(0f, plain.revealedChars(999), 0.01f)
        assertEquals(15f, plain.revealedChars(1_000), 0.01f)
    }
}
