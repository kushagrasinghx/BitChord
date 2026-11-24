package com.music.bitchord

import com.music.bitchord.ui.player.LyricsShareLine
import com.music.bitchord.ui.player.SHARE_CARD_CHAR_BUDGET
import com.music.bitchord.ui.player.fitsOnCard
import com.music.bitchord.ui.player.isRtl
import com.music.bitchord.ui.player.shareTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule that keeps a share card from being handed a verse it could only set
 * in type too small to read — the card draws everything it is given, so the
 * pick is the one place a limit can sit.
 */
class LyricsShareCardTest {
    /** At the length a line of a song usually has. */
    private val shortLine = "Nie dzwoń do mnie teraz, nie przeszkadzaj mi"

    @Test fun fiveShortLinesStillFit() {
        var taken = 0
        repeat(5) {
            assertTrue("line ${it + 1} must be pickable", fitsOnCard(taken, shortLine))
            taken += shortLine.length
        }
    }

    @Test fun theSixthLineIsRefusedRatherThanShrinkingTheType() {
        val taken = shortLine.length * 5
        assertFalse(fitsOnCard(taken, shortLine))
    }

    @Test fun aLineOverTheBudgetOnItsOwnIsRefused() {
        assertFalse(fitsOnCard(0, "x".repeat(SHARE_CARD_CHAR_BUDGET + 1)))
    }

    @Test fun theBudgetIsInclusiveByOneCharacter() {
        assertTrue(fitsOnCard(SHARE_CARD_CHAR_BUDGET, ""))
        assertFalse(fitsOnCard(SHARE_CARD_CHAR_BUDGET, "x"))
    }

    @Test fun aLineThePanelHasNoWordsForCostsNothing() {
        assertTrue(fitsOnCard(SHARE_CARD_CHAR_BUDGET, null))
    }

    @Test fun arabicAndRtlScriptDetectedCorrectly() {
        assertTrue("Arabic text must be detected as RTL", isRtl("حبيبي يا نور العين"))
        assertTrue("Hebrew text must be detected as RTL", isRtl("שלום עולם"))
        assertTrue("Arabic with leading numbers must be detected as RTL", isRtl("1. حبيبي يا نور العين"))
        assertTrue("Arabic with brackets must be detected as RTL", isRtl("(حبيبي)"))
        assertFalse("Latin text must be detected as LTR", isRtl("Hello world"))
        assertFalse("Empty string must not be detected as RTL", isRtl(""))
    }

    @Test fun arabicWordSyncedLineFitsAndDetected() {
        val arabicText = "حبيبي يا نور العين يا ساكن خيالي"
        assertTrue(fitsOnCard(0, arabicText))
        assertTrue(isRtl(arabicText))
    }

    @Test fun clipTimelineNotLimitedToOneMinuteAndIncludesAllPickedLines() {
        val lines = listOf(
            LyricsShareLine(text = "Line 1", subText = null, startMs = 0L, endMs = 40_000L),
            LyricsShareLine(text = "Line 2", subText = null, startMs = 40_000L, endMs = 80_000L),
            LyricsShareLine(text = "Line 3", subText = null, startMs = 80_000L, endMs = 110_000L),
        )
        val timeline = shareTimeline(lines)
        assertNotNull(timeline)
        assertEquals(110_000L, timeline!!.durationMs)
        assertEquals(3, timeline.windows.size)
        assertEquals(0L, timeline.windows[0].fromUs)
        assertEquals(40_000_000L, timeline.windows[0].toUs)
        assertEquals(40_000_000L, timeline.windows[1].fromUs)
        assertEquals(80_000_000L, timeline.windows[1].toUs)
        assertEquals(80_000_000L, timeline.windows[2].fromUs)
        assertEquals(110_000_000L, timeline.windows[2].toUs)
    }

    @Test fun englishPronunciationOrTranslationUnderArabicLineIsNotRtl() {
        val arabicLine = "حبيبي يا نور العين"
        val englishPronunciation = "Habibi ya nour el ain"
        val englishTranslation = "My darling, you are the light of my eyes"
        assertTrue(isRtl(arabicLine))
        assertFalse(isRtl(englishPronunciation))
        assertFalse(isRtl(englishTranslation))
    }
}
