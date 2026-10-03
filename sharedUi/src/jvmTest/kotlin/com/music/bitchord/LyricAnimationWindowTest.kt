package com.music.bitchord

import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricSyllable
import com.music.bitchord.data.lyrics.LyricWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A line outside [LyricLine.animatesFromMs]..[LyricLine.animatesUntilMs] stops
 * reading the clock and is drawn at one fixed position either side. That is only
 * invisible if nothing the draw passes ask of the line changes out there.
 */
class LyricAnimationWindowTest {
    private val lines = listOf(
        // Plain words, one split into syllables.
        LyricLine(
            timeMs = 10_000,
            text = "long enough",
            words = listOf(
                LyricWord(10_000, 10_400, "long"),
                LyricWord(10_400, 11_000, "enough", listOf(
                    LyricSyllable(10_400, 10_550, 0, 1),
                    LyricSyllable(10_550, 11_000, 1, 6),
                )),
            ),
        ),
        // A held word, animated letter by letter past its own end.
        LyricLine(
            timeMs = 20_000,
            text = "oh hold",
            words = listOf(LyricWord(20_000, 20_200, "oh"), LyricWord(20_200, 22_600, "hold")),
        ),
        // A lead line stamped before its first word, with a backing vocal outlasting it.
        LyricLine(
            timeMs = 29_800,
            text = "call me",
            words = listOf(LyricWord(30_000, 30_300, "call"), LyricWord(30_300, 30_700, "me")),
            background = LyricLine(30_500, "back", listOf(LyricWord(30_500, 32_000, "back"))),
        ),
    )

    private fun LyricLine.pictureAt(positionMs: Long): List<Any> = listOf(
        // The sweep's own gates, then what it reveals.
        positionMs >= endMs,
        positionMs <= timeMs,
        revealedChars(positionMs),
        isLifted(positionMs),
        isGrowing(positionMs),
    ) + words.indices.flatMap { listOf(wordLift(it, positionMs), wordFall(it, positionMs)) }

    @Test fun nothingMovesBeforeTheWindow() {
        for (line in lines) {
            val drawn = line.pictureAt(line.animatesFromMs - 1)
            for (at in listOf(0L, line.animatesFromMs - 5_000, line.animatesFromMs - 2)) {
                assertEquals("'${line.text}' at $at", drawn, line.pictureAt(at))
            }
            assertFalse(line.isLifted(line.animatesFromMs - 1))
        }
    }

    @Test fun nothingMovesAfterTheWindow() {
        for (line in lines) {
            val drawn = line.pictureAt(line.animatesUntilMs + 1)
            for (at in listOf(line.animatesUntilMs + 2, line.animatesUntilMs + 5_000, 600_000L)) {
                assertEquals("'${line.text}' at $at", drawn, line.pictureAt(at))
            }
            assertFalse(line.isLifted(line.animatesUntilMs + 1))
            assertFalse(line.isGrowing(line.animatesUntilMs + 1))
        }
    }

    @Test fun theWindowCoversTheHeldWordAndTheBackingVocal() {
        val held = lines[1]
        assertTrue(held.growingWords.isNotEmpty())
        assertTrue(held.animatesUntilMs >= held.growingWords.maxOf { it.restsAtMs })
        val withBacking = lines[2]
        assertTrue(withBacking.animatesUntilMs >= withBacking.background!!.endMs)
        assertEquals(withBacking.timeMs, withBacking.animatesFromMs)
    }
}
