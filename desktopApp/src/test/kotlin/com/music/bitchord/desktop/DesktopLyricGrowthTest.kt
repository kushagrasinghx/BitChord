package com.music.bitchord.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Which words are animated a letter at a time, and how far each letter gets. */
class DesktopLyricGrowthTest {

    @Test
    fun ordinarySyllablesAreNotAnimated() {
        val line = wordSynced("run fast now", spanMs = 260)
        assertTrue(line.growingWords.isEmpty())
        assertTrue(!line.isGrowing(line.timeMs + 100))
    }

    @Test
    fun aHeldWordIsAnimated() {
        val line = wordSynced("ohh", spanMs = 1_800)
        assertEquals(listOf(0), line.growingWords.map { it.index })
        assertTrue(line.isGrowing(line.timeMs + 100))
    }

    @Test
    fun aLongWordSungAtPaceIsNotAHeldNote() {
        // Seven letters over a second and a half is an ordinary pace; the same hold on a two-letter
        // word is a note being carried.
        assertTrue(wordSynced("holding", spanMs = 1_300).growingWords.isEmpty())
        assertTrue(wordSynced("oh", spanMs = 1_400).growingWords.isNotEmpty())
    }

    @Test
    fun wordsTooLongToRunAWaveThroughAreLeftAlone() {
        assertTrue(wordSynced("everything", spanMs = 4_000).growingWords.isEmpty())
    }

    @Test
    fun scriptsThatDoNotLayOutLetterByLetterAreLeftAlone() {
        // Han, kana and Hangul draw as blocks and Arabic joins up, so moving one letter of a word
        // would come apart rather than swell.
        assertTrue(wordSynced("ありがと", spanMs = 2_000).growingWords.isEmpty())
        assertTrue(wordSynced("사랑", spanMs = 2_000).growingWords.isEmpty())
        // A hyphenated word is really two words and would break at the hyphen.
        assertTrue(wordSynced("oh-oh", spanMs = 2_000).growingWords.isEmpty())
    }

    @Test
    fun everyLetterSwellsAndComesBackToTheOrdinaryLift() {
        val line = wordSynced("ohh", spanMs = 2_000)
        val word = line.growingWords.single()
        val growth = DesktopCharGrowth()

        // Partway up: swollen, lifted and lit.
        word.sampleInto(0, word.startMs + 300, growth)
        assertTrue(growth.scale > 1f, "expected a swell, got ${growth.scale}")
        assertTrue(growth.rise > 0f)
        assertTrue(growth.bloom > 0f)

        // Well past the move: back to the flat lift every sung word carries, so the cheaper
        // single-slice path can take over without a step.
        word.sampleInto(0, word.restsAtMs + 1_000, growth)
        assertEquals(1f, growth.scale)
        assertEquals(0f, growth.bloom)
    }

    @Test
    fun theMoveTravelsAlongTheWordRatherThanPulsing() {
        val word = wordSynced("ohh", spanMs = 2_000).growingWords.single()
        val first = DesktopCharGrowth().also { word.sampleInto(0, word.startMs + 200, it) }.scale
        val last = DesktopCharGrowth().also { word.sampleInto(2, word.startMs + 200, it) }.scale
        assertTrue(first > last, "the wave should lead at the first letter: $first vs $last")
    }

    @Test
    fun aWordStillGrowingKeepsTheLineOffTheFloor() {
        // The last letter only starts moving as the word ends, so a line that stopped lifting at
        // the ordinary fall would drop it back mid-swell.
        val line = wordSynced("ohh", spanMs = 2_000)
        val word = line.growingWords.single()
        assertTrue(line.isLifted(word.restsAtMs - 1))
        assertNull(line.growingAt(1))
    }

    private fun wordSynced(text: String, spanMs: Long): DesktopLyricLine {
        var at = 1_000L
        val words = text.split(' ').map { word ->
            DesktopLyricWord(at, at + spanMs, word).also { at += spanMs }
        }
        return DesktopLyricLine(words.first().startMs, text, words)
    }
}
