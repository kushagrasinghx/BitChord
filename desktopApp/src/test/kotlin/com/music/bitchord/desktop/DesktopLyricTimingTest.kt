package com.music.bitchord.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The two questions the lyrics panel gets wrong invisibly: what time it is, and which rows are
 * being sung.
 */
class DesktopLyricTimingTest {

    // ---- The clock ---------------------------------------------------------

    @Test
    fun aLatePollDoesNotReactivateThePreviousLine() {
        // The display has already crossed a line that starts at ten seconds; the report lands 33ms
        // behind it.
        val reconciled = reconcileLyricPosition(displayedMs = 10_018, reportedMs = 9_985)
        assertEquals(10_018L, reconciled)
        assertEquals(1, listOf(0L, 10_000L).indexOfLast { it <= reconciled })
    }

    @Test
    fun repeatedCorrectionsDoNotAccumulateDrift() {
        // Advancing from the *report* rather than from the held value is what keeps a run of small
        // corrections from adding up into a permanent lead.
        var displayed = 10_018L
        for (report in listOf(9_985L, 10_485L, 10_985L)) {
            displayed = reconcileLyricPosition(displayed, report)
            displayed = maxOf(displayed, report + 500L)
        }
        assertEquals(11_485L, displayed)
    }

    @Test
    fun seeksAndTrackChangesResetImmediately() {
        // A large disagreement is news, not jitter, in either direction.
        assertEquals(4_000L, reconcileLyricPosition(10_018, 4_000))
        assertEquals(20_000L, reconcileLyricPosition(10_018, 20_000))
        assertEquals(0L, reconcileLyricPosition(120_000, 0))
    }

    @Test
    fun aPollAheadOfTheDisplayIsFollowed() {
        assertEquals(10_050L, reconcileLyricPosition(10_018, 10_050))
    }

    // ---- Which rows are being sung ----------------------------------------

    private val overlapping = listOf(
        line(1_000, "upper", until = 4_000),
        line(2_000, "middle", until = 5_000),
        line(3_000, "lower", until = 6_000),
    )

    @Test
    fun everyUnfinishedVocalStaysLit() {
        // A duet or a call-and-response has two or three going at once, and a panel that lights
        // only the latest cuts the one underneath it off mid-word.
        assertEquals(listOf(0, 1, 2), activeLyricRows(overlapping, 3_500))
    }

    @Test
    fun aRowGoesOutExactlyWhenItsOwnVocalEnds() {
        assertEquals(listOf(1, 2), activeLyricRows(overlapping, 4_000))
        assertEquals(listOf(2), activeLyricRows(overlapping, 5_000))
    }

    @Test
    fun aFinishedMiddleRowDoesNotCutOffALongerOneAboveIt() {
        val lines = overlapping.toMutableList()
        lines[1] = lines[1].copy(sungUntilMs = 2_900)
        assertEquals(listOf(0, 2), activeLyricRows(lines, 3_500))
    }

    @Test
    fun aLineSyncedSourceLightsOnlyTheLatestRow() {
        // Nothing said when these lines stop, so claiming any of them is still being sung would be
        // an invention.
        val lines = overlapping.map { it.copy(sungUntilMs = null) }
        assertEquals(listOf(1), activeLyricRows(lines, 2_500))
    }

    @Test
    fun seekingBackwardsRecomputesTheAnchor() {
        assertEquals(listOf(2), activeLyricRows(overlapping, 5_500))
        assertEquals(listOf(0), activeLyricRows(overlapping, 1_500))
        assertEquals(emptyList<Int>(), activeLyricRows(overlapping, 500))
    }

    @Test
    fun aBreakIsNeverHeldOpenBesideALineThatIsStillBeingSung() {
        // A gap row carries no vocal, so it can only ever be the latest row — it must not linger as
        // "still active" over the verse after it.
        val lines = listOf(
            line(1_000, "sung", until = 9_000),
            DesktopLyricLine(timeMs = 2_000, text = "", sungUntilMs = 8_000),
            line(3_000, "next", until = 6_000),
        )
        assertEquals(listOf(0, 2), activeLyricRows(lines, 4_000))
    }

    // ---- The run-up --------------------------------------------------------

    @Test
    fun theScrollStartsDuringTheSilenceBeforeTheNextLine() {
        // The lead is the gap between one line's last word and the next line's first, so the panel
        // arrives as the line lands rather than after it.
        val lines = listOf(line(0, "one", until = 1_000), line(1_420, "two", until = 2_000))
        assertEquals(420L, lyricScrollLead(lines, 500))
    }

    @Test
    fun theRunUpIsBoundedEitherSide() {
        // A held breath in one song is half a verse in another, and neither the snap nor the drift
        // is what anyone wants to read against.
        val tight = listOf(line(0, "one", until = 1_000), line(1_010, "two", until = 2_000))
        assertEquals(SCROLL_LEAD_MIN_MS, lyricScrollLead(tight, 500))
        val loose = listOf(line(0, "one", until = 1_000), line(40_000, "two", until = 41_000))
        assertEquals(SCROLL_LEAD_MAX_MS, lyricScrollLead(loose, 500))
        // Nothing after the last line to lead towards.
        assertEquals(SCROLL_LEAD_MIN_MS, lyricScrollLead(tight, 30_000))
    }

    // ---- The word lift -----------------------------------------------------

    @Test
    fun aWordRisesAsItLandsAndSettlesBackOnceItIsPast() {
        val sung = line(0, "hold on", until = 2_000).copy(
            words = listOf(
                DesktopLyricWord(0, 1_000, "hold"),
                DesktopLyricWord(1_000, 2_000, "on"),
            ),
        )
        // Nothing has moved before the first word starts, and nothing is left moving well after the
        // last one ends.
        assertTrue(!sung.isLifted(0))
        assertTrue(sung.isLifted(1_200))
        assertTrue(!sung.isLifted(2_000 + WORD_RISE_MS.toLong() + 1))
        // The first word is at rest by the time the second is at its peak.
        assertTrue(sung.wordLift(1, 1_800) > sung.wordLift(0, 1_800))
    }

    @Test
    fun wordSpansFollowRepeatsRatherThanTheFirstMatch() {
        // "on" appears twice; the second word must line up with its own occurrence, or the lift
        // jumps back to the start of the line.
        val sung = line(0, "on and on", until = 3_000).copy(
            words = listOf(
                DesktopLyricWord(0, 1_000, "on"),
                DesktopLyricWord(1_000, 2_000, "and"),
                DesktopLyricWord(2_000, 3_000, "on"),
            ),
        )
        assertEquals(listOf(0 until 2, 3 until 6, 7 until 9), sung.wordSpans)
    }

    private fun line(timeMs: Long, text: String, until: Long) =
        DesktopLyricLine(timeMs = timeMs, text = text, sungUntilMs = until)
}
