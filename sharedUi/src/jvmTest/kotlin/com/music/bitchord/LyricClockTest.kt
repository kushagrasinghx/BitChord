package com.music.bitchord

import androidx.compose.runtime.mutableStateOf
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricWord
import com.music.bitchord.playback.PlaybackPosition
import com.music.bitchord.ui.player.LyricClock
import com.music.bitchord.ui.player.LyricPlayhead
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

    @Test fun laterResumeAnchorCannotReverseAnAutomaticCatchUpGlide() {
        val clock = LyricClock(10_000)
        clock.hold(10_000, 0.0, discontinuity = 0)
        clock.restart()
        assertEquals(10_000L, clock.frame(1_000.0, 9_900, 1_000.0, 0))
        // Two agreeing forward samples establish a new anchor, then a frame starts catch-up.
        clock.frame(1_016.0, 10_800, 1_016.0, 0)
        clock.frame(1_032.0, 10_816, 1_032.0, 0)
        clock.frame(1_048.0, 10_816, 1_032.0, 0)
        // Ordinary noise lowers the target while that automatic glide is still active.
        var previous = clock.frame(1_064.0, 10_700, 1_064.0, 0)
        val after = clock.frame(1_080.0, 10_700, 1_064.0, 0)
        assertTrue("later resumed frame reused an obsolete glide offset", after >= previous)
        previous = after
        for (step in 6..40) {
            val now = 1_000.0 + step * 16.0
            val next = clock.frame(now, 10_700 + (step - 4) * 16L, now, 0)
            assertTrue("catch-up regressed at $now", next >= previous)
            previous = next
        }
        assertTrue("catch-up did not converge", abs(previous - (10_700 + 36 * 16L)) < 40)
    }

    @Test fun announcedNearSeeksKeepTheirGlideWhenANormalSampleLowersTheTarget() {
        for (landing in listOf(9_000L, 11_000L)) {
            val clock = LyricClock(10_000)
            clock.frame(0.0, 10_000, 0.0, 0)
            clock.frame(16.0, landing, 16.0, 1)
            clock.frame(32.0, landing, 16.0, 1)
            clock.frame(48.0, landing - 20, 48.0, 1)
            // Both directions must still land within the existing 260 ms seek glide.
            assertEquals(landing - 20 + 252, clock.frame(300.0, landing - 20, 48.0, 1))
        }
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

    /** The captured resume sample reached the old paused callback before recomposition removed it. */
    @Test fun resumedSampleCannotOverwriteThePausedClockBeforeRecomposition() {
        val position = PlaybackPosition()
        val playhead = LyricPlayhead(position, mutableStateOf(0))
        val clock = LyricClock(30_867)
        var shown = 30_867L
        // Keep the old compositional false throughout, just as in the captured callback race.
        fun pausedObserver(nowMs: Double) {
            clock.observe(playhead.reading(isPlaying = false), nowMs)?.let { shown = it }
        }

        position.report(30_867, sampledAtNanos = 1_000_000_000L, isPlaying = false)
        pausedObserver(1_000.0)
        position.report(30_746, sampledAtNanos = 5_000_000_000L, isPlaying = true)
        pausedObserver(5_001.0)
        assertEquals(30_867L, shown)
        assertEquals(30_867.0, clock.displayedMs, 0.0)

        // The first running frame must not reintroduce the rejected handoff regression.
        clock.restart()
        assertEquals(30_867L, clock.frame(5_002.0, playhead.reading(isPlaying = false)))
        position.report(30_992, sampledAtNanos = 5_034_000_000L, isPlaying = true)
        pausedObserver(5_035.0)
        assertEquals(30_867L, shown)
        var previous = clock.frame(5_035.0, playhead.reading(isPlaying = false))
        assertTrue(previous >= shown)
        for (step in 1..600) {
            val now = 5_034.0 + step * frameMs
            val reported = 30_992 + (step * frameMs).toLong()
            position.report(reported, sampledAtNanos = (now * 1_000_000).toLong(), isPlaying = true)
            val next = clock.frame(now, playhead.reading(isPlaying = false))
            assertTrue("went back after resume at $now", next >= previous)
            previous = next
        }
        assertTrue(abs(previous - position.positionMs) < 40)
    }

    @Test fun pausedSeeksStillLandBeforeResuming() {
        val position = PlaybackPosition()
        val playhead = LyricPlayhead(position, mutableStateOf(0))
        val clock = LyricClock(30_867)
        position.report(30_867, sampledAtNanos = 1_000_000_000L, isPlaying = false)
        clock.observe(playhead.reading(isPlaying = false), 1_000.0)

        position.seeks++
        position.report(20_000, sampledAtNanos = 2_000_000_000L, isPlaying = false)
        assertEquals(20_000L, clock.observe(playhead.reading(isPlaying = false), 2_000.0))
        position.seeks++
        position.report(35_000, sampledAtNanos = 3_000_000_000L, isPlaying = false)
        assertEquals(35_000L, clock.observe(playhead.reading(isPlaying = false), 3_000.0))

        position.report(34_990, sampledAtNanos = 5_000_000_000L, isPlaying = true)
        assertTrue(clock.observe(playhead.reading(isPlaying = false), 5_000.0) == null)
        clock.restart()
        assertEquals(35_000L, clock.frame(5_000.0, playhead.reading(isPlaying = true)))
    }

    /** A seek can land after the last paused callback, immediately before the resumed frame. */
    @Test fun seekDuringTheResumeHandoffIsNotSuppressed() {
        val position = PlaybackPosition()
        val playhead = LyricPlayhead(position, mutableStateOf(0))
        val clock = LyricClock(30_867)
        position.report(30_867, sampledAtNanos = 1_000_000_000L, isPlaying = false)
        clock.observe(playhead.reading(isPlaying = false), 1_000.0)

        position.seeks++
        position.report(20_000, sampledAtNanos = 5_000_000_000L, isPlaying = true)
        assertTrue(clock.observe(playhead.reading(isPlaying = false), 5_000.0) == null)
        clock.restart()
        assertEquals(20_000L, clock.frame(5_000.0, playhead.reading(isPlaying = true)))
    }

    @Test fun seekIdentityIsPublishedWithTheLandingPosition() {
        val position = PlaybackPosition()
        val playhead = LyricPlayhead(position, mutableStateOf(0))
        position.report(30_867, sampledAtNanos = 1_000_000_000L, isPlaying = false)
        val before = playhead.reading(isPlaying = false)
        position.seeks++
        val pending = playhead.reading(isPlaying = false)
        assertEquals(before, pending)

        position.report(20_000, sampledAtNanos = 2_000_000_000L, isPlaying = false)
        val landed = playhead.reading(isPlaying = false)
        assertEquals(20_000L, landed.positionMs)
        assertTrue(landed.discontinuity != before.discontinuity)
    }

    @Test fun stoppedSampleStopsAnObsoleteRunningFrameWithoutReplacingItsDisplay() {
        val position = PlaybackPosition()
        val playhead = LyricPlayhead(position, mutableStateOf(0))
        val clock = LyricClock(30_867)
        position.report(30_867, sampledAtNanos = 1_000_000_000L, isPlaying = true)
        clock.frame(1_000.0, playhead.reading(isPlaying = true))

        // Both pause and Media3 buffering publish isPlaying=false, before the UI effect stops.
        position.report(30_880, sampledAtNanos = 1_100_000_000L, isPlaying = false)
        assertEquals(30_867L, clock.frame(1_116.0, playhead.reading(isPlaying = true)))
        assertEquals(30_867L, clock.frame(1_132.0, playhead.reading(isPlaying = true)))
    }

    @Test fun stoppedReportsFreezeRenderedProgressUntilASeekOrResume() {
        val position = PlaybackPosition()
        val playhead = LyricPlayhead(position, mutableStateOf(0))
        val clock = LyricClock(30_000)
        val line = LyricLine(30_000, "abcdefghij", listOf(LyricWord(30_000, 50_000, "abcdefghij")))
        position.report(30_000, sampledAtNanos = 1_000_000_000L, isPlaying = true)
        clock.frame(1_000.0, playhead.reading(isPlaying = true))
        val beforePause = clock.frame(1_200.5, playhead.reading(isPlaying = true))
        val frozen = clock.displayedMs
        val revealed = line.revealedChars(beforePause)

        position.report(30_170, sampledAtNanos = 1_210_000_000L, isPlaying = false)
        assertEquals(beforePause, clock.observe(playhead.reading(isPlaying = true), 1_210.0))
        position.report(30_150, sampledAtNanos = 1_500_000_000L, isPlaying = false)
        assertEquals(beforePause, clock.observe(playhead.reading(isPlaying = false), 1_500.0))
        assertEquals(frozen, clock.displayedMs, 0.0)
        assertEquals(revealed, line.revealedChars(clock.displayedMs.toLong()), 0f)

        position.report(30_100, sampledAtNanos = 5_000_000_000L, isPlaying = true)
        assertTrue(clock.observe(playhead.reading(isPlaying = false), 5_000.0) == null)
        clock.restart()
        assertEquals(beforePause, clock.frame(5_000.0, playhead.reading(isPlaying = true)))
        var previous = beforePause
        for (step in 1..600) {
            val elapsed = step * frameMs
            val now = 5_000.0 + elapsed
            position.report(30_100 + elapsed.toLong(), sampledAtNanos = (now * 1_000_000).toLong(), isPlaying = true)
            val next = clock.frame(now, playhead.reading(isPlaying = true))
            assertTrue("regressed after a frozen pause at $now", next >= previous)
            previous = next
        }
        assertTrue("kept a permanent resume offset", abs(previous - position.positionMs) < 40)
    }

    @Test fun bufferingAfterTheFirstResumedFrameCannotLowerTheHeldDisplay() {
        val position = PlaybackPosition()
        val playhead = LyricPlayhead(position, mutableStateOf(0))
        val clock = LyricClock(30_867)
        position.report(30_867, sampledAtNanos = 1_000_000_000L, isPlaying = false)
        clock.observe(playhead.reading(isPlaying = false), 1_000.0)
        position.report(30_746, sampledAtNanos = 5_000_000_000L, isPlaying = true)
        clock.restart()
        assertEquals(30_867L, clock.frame(5_000.0, playhead.reading(isPlaying = true)))
        val resumed = clock.frame(5_016.0, playhead.reading(isPlaying = true))

        // A real subsequent stopped sample is not the stale-UI race fixed by 8302fec2.
        position.report(30_762, sampledAtNanos = 5_020_000_000L, isPlaying = false)
        assertEquals(resumed, clock.frame(5_032.0, playhead.reading(isPlaying = true)))
        assertEquals(resumed, clock.observe(playhead.reading(isPlaying = true), 5_033.0))
        position.report(30_992, sampledAtNanos = 5_048_000_000L, isPlaying = true)
        clock.restart()
        assertTrue(clock.frame(5_048.0, playhead.reading(isPlaying = true)) >= resumed)
    }

    @Test fun stoppedHiddenClockStillFollowsTheActualPosition() {
        val position = PlaybackPosition()
        val playhead = LyricPlayhead(position, mutableStateOf(0))
        val clock = LyricClock(30_000)
        position.report(30_000, sampledAtNanos = 1_000_000_000L, isPlaying = true)
        clock.frame(1_000.0, playhead.reading(isPlaying = true))
        clock.frame(1_200.0, playhead.reading(isPlaying = true))
        position.report(30_170, sampledAtNanos = 1_210_000_000L, isPlaying = false)
        assertEquals(30_170L, clock.observe(playhead.reading(isPlaying = false), 1_210.0, active = false))
    }

    @Test fun hiddenRunningClockExtrapolatesWhenShownAgain() {
        val position = PlaybackPosition()
        val playhead = LyricPlayhead(position, mutableStateOf(0))
        val clock = LyricClock(10_000)
        position.report(10_000, sampledAtNanos = 1_000_000_000L, isPlaying = false)
        clock.observe(playhead.reading(isPlaying = false), 1_000.0)
        position.report(10_000, sampledAtNanos = 2_000_000_000L, isPlaying = true)
        // Covers both an inactive panel and the same foreground gate used while backgrounded.
        assertEquals(10_000L, clock.observe(playhead.reading(isPlaying = false), 2_000.0, active = false))
        clock.restart()
        assertEquals(40_000L, clock.frame(32_000.0, playhead.reading(isPlaying = true)))
    }

    @Test fun backgroundReturnDoesNotKeepAnObsoletePausedDisplay() {
        val position = PlaybackPosition()
        val playhead = LyricPlayhead(position, mutableStateOf(0))
        val clock = LyricClock(10_000)
        position.report(10_000, sampledAtNanos = 1_000_000_000L, isPlaying = false)
        clock.observe(playhead.reading(isPlaying = false), 1_000.0)
        // Even if background observations were conflated, a forward target is accepted on return.
        position.report(40_000, sampledAtNanos = 32_000_000_000L, isPlaying = true)
        clock.restart()
        assertEquals(40_000L, clock.frame(32_000.0, playhead.reading(isPlaying = true)))
    }

    @Test fun pausedOffsetChangeIsAppliedWithoutANewPositionReport() {
        val position = PlaybackPosition()
        val offset = mutableStateOf(0)
        val playhead = LyricPlayhead(position, offset)
        val clock = LyricClock(30_867)
        position.report(30_867, sampledAtNanos = 1_000_000_000L, isPlaying = false)
        clock.observe(playhead.reading(isPlaying = false), 1_000.0)
        offset.value = 500
        val adjusted = position.positionMs - offset.value
        assertEquals(adjusted, clock.observe(playhead.reading(isPlaying = false), 2_000.0))
        position.report(30_867, sampledAtNanos = 5_000_000_000L, isPlaying = true)
        clock.restart()
        assertEquals(adjusted, clock.frame(5_000.0, playhead.reading(isPlaying = true)))
    }

    @Test fun repeatedLongPausesDoNotAccumulateElapsedTimeOnResume() {
        val position = PlaybackPosition()
        val playhead = LyricPlayhead(position, mutableStateOf(0))
        val clock = LyricClock(30_867)
        for (cycle in 1..5) {
            val stopped = 30_867L + cycle * 1_000L
            val stoppedAt = cycle * 60_000_000_000L
            // Each cycle starts at a new landing, not an unannounced change to a frozen clock.
            position.seeks++
            position.report(stopped, sampledAtNanos = stoppedAt, isPlaying = false)
            clock.observe(playhead.reading(isPlaying = true), stoppedAt / 1_000_000.0)
            val resumedAt = stoppedAt + 30_000_000_000L
            position.report(stopped - 121, sampledAtNanos = resumedAt, isPlaying = true)
            assertTrue(clock.observe(playhead.reading(isPlaying = false), resumedAt / 1_000_000.0) == null)
            clock.restart()
            assertEquals(stopped, clock.frame(resumedAt / 1_000_000.0, playhead.reading(isPlaying = true)))
        }
    }

    @Test fun rapidPauseResumeReadsTransportEvenWhenThePositionDoesNotChange() {
        val position = PlaybackPosition()
        val playhead = LyricPlayhead(position, mutableStateOf(0))
        val clock = LyricClock(30_867)
        position.report(30_867, sampledAtNanos = 1_000_000_000L, isPlaying = false)
        clock.observe(playhead.reading(isPlaying = false), 1_000.0)
        position.report(30_867, sampledAtNanos = 1_001_000_000L, isPlaying = true)
        assertTrue(playhead.reading(isPlaying = false).isPlaying)
        assertTrue(clock.observe(playhead.reading(isPlaying = false), 1_001.0) == null)
        clock.restart()
        clock.frame(1_001.0, playhead.reading(isPlaying = true))
        position.report(30_867, sampledAtNanos = 1_002_000_000L, isPlaying = false)
        assertEquals(30_867L, clock.frame(1_002.0, playhead.reading(isPlaying = true)))
        position.report(30_746, sampledAtNanos = 1_003_000_000L, isPlaying = true)
        assertTrue(clock.observe(playhead.reading(isPlaying = false), 1_003.0) == null)
        clock.restart()
        assertEquals(30_867L, clock.frame(1_003.0, playhead.reading(isPlaying = true)))
    }

    @Test fun unstatedTransportFallsBackToUiStateAndRespectsAdvancing() {
        val position = PlaybackPosition()
        val playhead = LyricPlayhead(position, mutableStateOf(0))
        position.report(30_867, sampledAtNanos = 1_000_000_000L)
        assertTrue(!playhead.reading(isPlaying = false).isPlaying)
        assertTrue(playhead.reading(isPlaying = true).isPlaying)
        position.advancing = false
        assertTrue(!playhead.reading(isPlaying = true).isPlaying)
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
