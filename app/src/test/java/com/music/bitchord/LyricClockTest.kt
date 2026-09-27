package com.music.bitchord

import com.music.bitchord.ui.player.LyricClockReconciler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricClockTest {
    @Test fun delayedPollDoesNotReactivatePreviousLine() {
        val clock = LyricClockReconciler(9_500, 0, initialPlaying = true)
        // A line starts at 10 seconds; rendering already crossed its boundary before a
        // delayed position report reached composition.
        val reconciled = clock.reconcile(10_018, 9_985, 500, isPlaying = true)
        assertEquals(10_018L, reconciled)
        assertEquals(1, listOf(0L, 10_000L).indexOfLast { it <= reconciled })
    }

    @Test fun delayedReportsNeverMoveWordsOrLinesBackwards() {
        val lineStarts = listOf(44_754L, 48_000L, 51_104L, 54_158L)
        val clock = LyricClockReconciler(44_500L, 0, initialPlaying = true)
        var displayed = 44_500L
        var previousLine = 0

        // Position samples are occasionally delivered several hundred milliseconds late.
        // This reproduces the dense handovers around 45-55 seconds of the reported track.
        val reports = listOf(
            Triple(500L, 44_980L, 45_080L),
            Triple(1_000L, 45_470L, 45_690L),
            Triple(3_500L, 47_930L, 48_180L),
            Triple(4_000L, 48_430L, 48_790L),
            Triple(6_500L, 50_900L, 51_240L),
            Triple(9_500L, 53_900L, 54_260L),
        )
        for ((observedAt, report, rendered) in reports) {
            displayed = maxOf(displayed, rendered)
            displayed = clock.reconcile(displayed, report, observedAt, isPlaying = true)
            val line = lineStarts.indexOfLast { it <= displayed }
            assertTrue(line >= previousLine)
            previousLine = line
        }
    }

    @Test fun backwardSeekResetsImmediately() {
        val clock = LyricClockReconciler(9_500, 0, initialPlaying = true)
        assertEquals(4_000L, clock.reconcile(10_018, 4_000, 500, isPlaying = true))
    }

    @Test fun forwardSeekResetsImmediately() {
        val clock = LyricClockReconciler(9_500, 0, initialPlaying = true)
        assertEquals(20_000L, clock.reconcile(10_018, 20_000, 500, isPlaying = true))
    }

    @Test fun pollAheadOfDisplayCatchesUp() {
        val clock = LyricClockReconciler(9_500, 0, initialPlaying = true)
        assertEquals(10_050L, clock.reconcile(10_018, 10_050, 500, isPlaying = true))
    }

    @Test fun pausedClockAcceptsASeekWithoutWaitingForFrames() {
        val clock = LyricClockReconciler(10_000, 0, initialPlaying = false)
        assertEquals(30_000L, clock.reconcile(10_000, 30_000, 250, isPlaying = false))
    }

    @Test fun pausingSettlesAFrameClockThatWasSlightlyAhead() {
        val clock = LyricClockReconciler(10_000, 0, initialPlaying = true)
        assertEquals(10_400L, clock.reconcile(10_650, 10_400, 400, isPlaying = false))
    }
}
