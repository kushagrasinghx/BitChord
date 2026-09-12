package com.music.bitchord.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Apple's TTML, read the way the panel needs it. */
class DesktopTtmlLyricsTest {

    @Test
    fun syllablesOfOneWordAreOneWord() {
        // Apple writes "enough" as two adjacent spans with no whitespace between them.
        val lines = DesktopTtmlLyrics.parse(
            ttml("""<p begin="1.0" end="2.0"><span begin="1.0" end="1.4">e</span><span begin="1.4" end="2.0">nough </span></p>"""),
        )
        assertEquals(1, lines.size)
        assertEquals("enough", lines.single().text)
        assertEquals(1, lines.single().words.size)
        assertEquals(1_000L, lines.single().words.single().startMs)
        assertEquals(2_000L, lines.single().words.single().endMs)
    }

    @Test
    fun whitespaceBetweenSpansSeparatesWords() {
        val lines = DesktopTtmlLyrics.parse(
            ttml(
                """<p begin="1.0" end="3.0">""" +
                    """<span begin="1.0" end="1.5">I</span> <span begin="1.6" end="3.0">been</span>""" +
                    """</p>""",
            ),
        )
        assertEquals(listOf("I", "been"), lines.single().words.map { it.text })
        assertEquals("I been", lines.single().text)
    }

    @Test
    fun translationsAreNotStackedOnTheLineTheyTranslate() {
        val lines = DesktopTtmlLyrics.parse(
            ttml(
                """<p begin="1.0" end="2.0">""" +
                    """<span begin="1.0" end="2.0">Hello</span>""" +
                    """<span ttm:role="x-translation">Bonjour</span>""" +
                    """</p>""",
            ),
        )
        assertEquals("Hello", lines.single().text)
    }

    @Test
    fun theAnsweringVocalIsCarriedApartFromTheLead() {
        // The backing vocal runs past the lead's last word.
        val lines = DesktopTtmlLyrics.parse(
            ttml(
                """<p begin="1.0" end="2.0">""" +
                    """<span begin="1.0" end="2.0">Hold on</span>""" +
                    """<span ttm:role="x-bg" begin="1.8" end="4.0">""" +
                    """<span begin="1.8" end="4.0">(ooh)</span></span>""" +
                    """</p>""",
            ),
        )
        val line = lines.single()
        assertEquals("Hold on", line.text)
        val backing = assertNotNull(line.background)
        assertEquals("(ooh)", backing.text)
        // The line is not over until the answering voice stops.
        assertEquals(4_000L, line.endMs)
    }

    @Test
    fun aDuetIsLaidOutOnBothSides() {
        val lines = DesktopTtmlLyrics.parse(
            ttml(
                """<p begin="1.0" end="2.0" ttm:agent="v1"><span begin="1.0" end="2.0">Mine</span></p>""" +
                    """<p begin="2.0" end="3.0" ttm:agent="v2"><span begin="2.0" end="3.0">Yours</span></p>""" +
                    """<p begin="3.0" end="4.0" ttm:agent="v1"><span begin="3.0" end="4.0">Mine</span></p>""",
                head = """<ttm:agent xml:id="v1" type="person"/><ttm:agent xml:id="v2" type="person"/>""",
            ),
        )
        assertEquals(
            listOf(
                DesktopLyricAlignment.Start,
                DesktopLyricAlignment.End,
                DesktopLyricAlignment.Start,
            ),
            lines.map { it.alignment },
        )
    }

    @Test
    fun aSingleVoiceStaysOnOneSide() {
        val lines = DesktopTtmlLyrics.parse(
            ttml(
                """<p begin="1.0" end="2.0" ttm:agent="v1"><span begin="1.0" end="2.0">One</span></p>""" +
                    """<p begin="2.0" end="3.0" ttm:agent="v1"><span begin="2.0" end="3.0">Two</span></p>""",
                head = """<ttm:agent xml:id="v1" type="person"/>""",
            ),
        )
        assertTrue(lines.all { it.alignment == DesktopLyricAlignment.Start })
    }

    @Test
    fun aLineSyncedDocumentKeepsItsOwnEnd() {
        // No spans at all: the paragraph's end is the only thing that says when the singing stops,
        // and without it an interlude cannot be told from a line sung slowly.
        val line = DesktopTtmlLyrics.parse(ttml("""<p begin="1.0" end="6.5">Just words</p>""")).single()
        assertEquals("Just words", line.text)
        assertTrue(line.words.isEmpty())
        assertTrue(line.hasKnownEnd)
        assertEquals(6_500L, line.endMs)
    }

    @Test
    fun aParagraphStampedBeforeItsFirstSyllableOpensAtTheStamp() {
        // Apple sets the paragraph a hair early on lines opening with a soft consonant, and that
        // lead-in is when the line should appear.
        val line = DesktopTtmlLyrics.parse(
            ttml("""<p begin="0.900" end="2.0"><span begin="1.000" end="2.0">So</span></p>"""),
        ).single()
        assertEquals(900L, line.timeMs)
    }

    @Test
    fun malformedDocumentsComeBackEmptyRatherThanThrowing() {
        assertEquals(emptyList(), DesktopTtmlLyrics.parse("<tt><body>"))
        assertEquals(emptyList(), DesktopTtmlLyrics.parse(""))
    }

    @Test
    fun clockValuesAreReadInEveryShapeTheFormatAllows() {
        assertEquals(27_395L, DesktopTtmlTime.of("27.395"))
        assertEquals(65_200L, DesktopTtmlTime.of("1:05.20"))
        assertEquals(3_723_400L, DesktopTtmlTime.of("1:02:03.4"))
        assertEquals(1_500L, DesktopTtmlTime.of("1.5s"))
        assertEquals(1_500L, DesktopTtmlTime.of("1500ms"))
        assertNull(DesktopTtmlTime.of(""))
        assertNull(DesktopTtmlTime.of(null))
    }

    private fun ttml(body: String, head: String = ""): String =
        """<?xml version="1.0" encoding="UTF-8"?>""" +
            """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">""" +
            """<head><metadata>$head</metadata></head><body><div>$body</div></body></tt>"""
}
