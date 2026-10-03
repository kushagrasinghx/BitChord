package com.music.bitchord

import com.music.bitchord.ui.player.LyricClock
import kotlin.math.abs
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricClockTest {
    private val frameMs = 1000.0 / 60

    /**
     * A song playing at [rate] from [startMs], read every 500 ms. Each reading
     * is stamped when it is taken, and then reaches the frame loop up to
     * [maxDelayMs] later — the way a reading did when the main thread was busy
     * with the recomposition that reading itself had caused.
     */
    private class Playback(
        val startMs: Long = 44_500L,
        val rate: Double = 1.0,
        val maxDelayMs: Double = 60.0,
        val stamped: Boolean = true,
        seed: Int = 7,
    ) {
        private val random = Random(seed)
        private var nextPollMs = 0.0
        private val inFlight = ArrayDeque<Pair<Double, Pair<Long, Double>>>()
        var report: Long = startMs
            private set
        var sampledAt: Double = if (stamped) 0.0 else Double.NaN
            private set

        fun trueAt(nowMs: Double) = startMs + nowMs * rate

        /** Advances deliveries to [nowMs]; afterwards [report] and [sampledAt] are what the frame sees. */
        fun deliver(nowMs: Double) {
            while (nextPollMs <= nowMs) {
                val reading = trueAt(nextPollMs).toLong()
                val arrives = nextPollMs + random.nextDouble() * maxDelayMs
                inFlight.addLast(arrives to (reading to nextPollMs))
                nextPollMs += 500.0
            }
            while (inFlight.isNotEmpty() && inFlight.first().first <= nowMs) {
                val (reading, at) = inFlight.removeFirst().second
                report = reading
                if (stamped) sampledAt = at
            }
        }
    }

    private class Run(val frames: List<Double>, val shown: List<Long>, val truth: List<Double>)

    /** Frames at 60 Hz, with a stall of up to [maxStallMs] — no frames at all — now and then. */
    private fun run(playback: Playback, seconds: Double, maxStallMs: Double = 0.0, seed: Int = 11): Run {
        val random = Random(seed)
        val clock = LyricClock(playback.startMs)
        val frames = ArrayList<Double>()
        val shown = ArrayList<Long>()
        val truth = ArrayList<Double>()
        var now = 0.0
        while (now <= seconds * 1000) {
            playback.deliver(now)
            frames += now
            shown += clock.frame(now, playback.report, playback.sampledAt, discontinuity = 0)
            truth += playback.trueAt(now)
            now += frameMs
            if (maxStallMs > 0 && random.nextDouble() < 0.02) now += random.nextDouble() * maxStallMs
        }
        return Run(frames, shown, truth)
    }

    private fun assertNeverBack(run: Run) {
        for (i in 1 until run.shown.size) {
            assertTrue("went back at ${run.frames[i]}", run.shown[i] >= run.shown[i - 1])
        }
    }

    /** The bug: readings held up behind a busy main thread rolled the words back. */
    @Test fun lateReadingsNeverMoveTheWordsBack() {
        val run = run(Playback(maxDelayMs = 800.0), seconds = 90.0, maxStallMs = 600.0)
        assertNeverBack(run)
    }

    @Test fun lateReadingsDoNotPullTheWordsOffTheSong() {
        val run = run(Playback(maxDelayMs = 800.0), seconds = 60.0, maxStallMs = 600.0)
        for (i in run.frames.indices) {
            if (run.frames[i] < 2_000) continue
            val error = run.shown[i] - run.truth[i]
            assertTrue("off by $error at ${run.frames[i]}", abs(error) < 40.0)
        }
    }

    /** The earlier bug: a `maxOf` ratchet froze the words for up to a second. */
    @Test fun lateReadingsNeverStallTheWords() {
        val run = run(Playback(maxDelayMs = 800.0), seconds = 60.0)
        val window = 15
        for (i in window until run.shown.size) {
            val moved = run.shown[i] - run.shown[i - window]
            val elapsed = run.frames[i] - run.frames[i - window]
            assertTrue("stalled near ${run.frames[i]}: $moved ms in $elapsed", moved >= elapsed * 0.5)
        }
    }

    /** A source that cannot stamp its readings still never goes backwards. */
    @Test fun unstampedReadingsNeverMoveTheWordsBack() {
        val run = run(Playback(maxDelayMs = 800.0, stamped = false), seconds = 60.0, maxStallMs = 600.0)
        assertNeverBack(run)
    }

    @Test fun learnsAFasterPlaybackRate() {
        val run = run(Playback(rate = 1.25, maxDelayMs = 300.0), seconds = 30.0)
        for (i in run.frames.indices) {
            if (run.frames[i] < 4_000) continue
            val error = run.shown[i] - run.truth[i]
            assertTrue("off by $error at ${run.frames[i]}", abs(error) < 40.0)
        }
        assertNeverBack(run)
    }

    /** Drives a clock on a song at 1x from 10 s, with [readingAt] deciding what each 500 ms reading says. */
    private fun steady(
        until: Double,
        clock: LyricClock = LyricClock(10_000),
        discontinuity: (Double) -> Long = { 0L },
        readingAt: (Double) -> Long = { 10_000 + it.toLong() },
        onFrame: (now: Double, shown: Long) -> Unit = { _, _ -> },
    ): Long {
        var now = 0.0
        var shown = 0L
        while (now <= until) {
            val taken = (now / 500).toLong() * 500.0
            shown = clock.frame(now, readingAt(taken), taken, discontinuity(now))
            onFrame(now, shown)
            now += frameMs
        }
        return shown
    }

    @Test fun oneWrongReadingIsIgnored() {
        var previous = Long.MIN_VALUE
        val last = steady(until = 8_000.0, readingAt = { taken ->
            10_000 + taken.toLong() + when (taken) {
                3_000.0 -> 1_500
                5_000.0 -> -1_500
                else -> 0
            }
        }) { now, shown ->
            assertTrue("went back at $now", shown >= previous)
            if (now > 1_000) assertTrue("followed the bad reading at $now", abs(shown - (10_000 + now)) < 40)
            previous = shown
        }
        assertTrue(abs(last - 18_000) < 40)
    }

    /** Tapping the line before this one: an announced jump, rolled back to rather than cut. */
    @Test fun announcedBackwardSeekGlidesBackAndLands() {
        var shown = 0L
        var largestStep = 0L
        val seekAt = 4_000.0
        steady(
            until = 5_000.0,
            discontinuity = { if (it >= seekAt) 1L else 0L },
            readingAt = { taken -> 10_000 + taken.toLong() - if (taken >= seekAt) 1_000 else 0 },
        ) { now, next ->
            if (now > seekAt) largestStep = maxOf(largestStep, abs(next - shown))
            shown = next
        }
        assertTrue("cut rather than glided: $largestStep ms in a frame", largestStep < 150)
        assertTrue("never landed: $shown", abs(shown - 14_000) < 40)
    }

    @Test fun announcedFarSeekJumpsAtOnce() {
        val clock = LyricClock(30_000)
        clock.frame(0.0, 30_000, 0.0, 0)
        clock.frame(16.0, 30_000, 0.0, 0)
        assertEquals(4_003L, clock.frame(33.0, 4_000, 30.0, 1))
    }

    /** The song really moved back without saying so: the words slow down to meet it, never reverse. */
    @Test fun unannouncedSmallRewindSlowsDownInsteadOfReversing() {
        var previous = Long.MIN_VALUE
        val last = steady(
            until = 12_000.0,
            readingAt = { taken -> 10_000 + taken.toLong() - if (taken >= 4_000) 800 else 0 },
        ) { now, shown ->
            assertTrue("went back at $now", shown >= previous)
            previous = shown
        }
        assertTrue("never met the song: $last", abs(last - (22_000 - 800)) < 40)
    }

    @Test fun unannouncedFarJumpIsFollowed() {
        val last = steady(
            until = 8_000.0,
            readingAt = { taken -> 10_000 + taken.toLong() - if (taken >= 4_000) 20_000 else 0 },
        )
        assertTrue("stayed lost: $last", abs(last - (18_000 - 20_000)) < 40)
    }

    @Test fun unannouncedForwardJumpCatchesUp() {
        var previous = Long.MIN_VALUE
        val last = steady(
            until = 8_000.0,
            readingAt = { taken -> 10_000 + taken.toLong() + if (taken >= 4_000) 1_000 else 0 },
        ) { now, shown ->
            assertTrue("went back at $now", shown >= previous)
            previous = shown
        }
        assertTrue(abs(last - 19_000) < 40)
    }

    @Test fun holdSettlesAtOnce() {
        val clock = LyricClock(10_000)
        clock.frame(0.0, 10_000, 0.0, 0)
        clock.frame(400.0, 10_400, 400.0, 0)
        clock.hold(10_380, 420.0)
        assertEquals(10_380.0, clock.displayedMs, 0.0)
    }

    /** A reading taken before a pause is where the song still is when it resumes, however long ago. */
    @Test fun resumingAfterAPauseStartsWhereItStopped() {
        val clock = LyricClock(10_000)
        clock.frame(0.0, 10_000, 0.0, 0)
        clock.hold(10_000, 5.0)
        clock.restart()
        assertEquals(10_000L, clock.frame(60_000.0, 10_000, 0.0, 0))
        assertEquals(10_016L, clock.frame(60_016.0, 10_000, 0.0, 0))
    }

    /** Coming back to the app while it played on: the last reading has moved on by its age. */
    @Test fun returningToTheAppExtrapolatesFromTheLastReading() {
        val clock = LyricClock(10_000)
        clock.frame(0.0, 10_000, 0.0, 0)
        clock.restart()
        assertEquals(40_000L, clock.frame(30_000.0, 10_000, 0.0, 0))
    }

    /** Moving the lyrics offset shifts every reading at once; announced, it is a jump and not a fault. */
    @Test fun movingTheOffsetIsFollowed() {
        val last = steady(
            until = 5_000.0,
            discontinuity = { if (it >= 3_000) 1L else 0L },
            readingAt = { taken -> 10_000 + taken.toLong() - if (taken >= 3_000) 1_500 else 0 },
        )
        assertTrue(abs(last - (15_000 - 1_500)) < 40)
    }
}
