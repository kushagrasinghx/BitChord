package com.music.bitchord

import com.music.bitchord.ui.player.SHARE_CARD_CHAR_BUDGET
import com.music.bitchord.ui.player.fitsOnCard
import org.junit.Assert.assertFalse
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
}
