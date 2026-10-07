package com.music.bitchord.ui.player

import kotlin.math.abs

/**
 * The lyric playhead: a clock that runs on its own frames and is *steered* by
 * the player's readings rather than set by them.
 *
 * Three rules, each the answer to a way this has gone wrong before:
 *
 *  1. **A reading is dated by when it was taken, not when it was seen.** The
 *     player is read twice a second and the reading then waits on the main
 *     thread; a busy frame holds it back by as long as the frame takes. Dated
 *     on arrival, a reading from before a half-second stall looked half a
 *     second old. Snapping to it moved the words back; refusing to go back (a
 *     `maxOf` ratchet) froze them until the song caught up; easing back to it
 *     rolled them back a line. All three were the same mistake. Every reading
 *     now carries its own [System.nanoTime] stamp — see `PlaybackPosition` —
 *     and is compared with where the song was *at that moment*.
 *  2. **Nothing but an announced jump moves the words backwards.** The player
 *     says when it seeks, skips or starts over (the `discontinuity` handed to
 *     [frame]); that, and only that, may roll the words back. A reading that
 *     disagrees without one is held back until the next confirms it, and a
 *     confirmed gap behind the words is absorbed by running slower — never by
 *     stopping and never by reversing.
 *  3. **Readings steer, they don't set.** The newest reading, carried forward
 *     at the learned playback rate, is where the song is; the words close the
 *     gap to it by running up to [MAX_SLEW] faster or [MAX_SLOW] slower. The
 *     rate is learned from the readings themselves, which is how a song at
 *     1.25x stays in step without being told.
 *
 * This is what am-lyrics gets from the browser for free: its wipes run on the
 * compositor's own clock, and the host's timestamp only touches them when the
 * two disagree by a lot.
 *
 * All times are milliseconds on the [System.nanoTime] clock. Plain arithmetic
 * with no Compose in it, so it can be tested frame by frame;
 * `rememberLyricClock` is the composable that drives it.
 */
internal class LyricClock(startMs: Long) {
    /** Where the lyrics are drawn, in song milliseconds. */
    var displayedMs: Double = startMs.toDouble()
        private set

    private var lastNowMs = Double.NaN

    // The newest reading seen, to tell a new one from the same one again.
    private var lastReportMs = Long.MIN_VALUE
    private var lastSampledAtMs = Double.NaN

    /** When playback was last seen stopped; a reading taken before then was taken standing still. */
    private var heldAtMs = Double.NEGATIVE_INFINITY

    private var lastDiscontinuity: Long? = null
    private var jumpPending = false

    // The newest reading believed: where the song was, and when.
    private var anchorAtMs = Double.NaN
    private var anchorMs = 0.0

    /** The playback rate, learned from how far the song moves between readings. */
    private var rate = 1.0

    // Readings believed since the playhead last jumped, oldest first: what the
    // rate is measured across. See [learnRate].
    private val historyAt = DoubleArray(HISTORY)
    private val historyMs = DoubleArray(HISTORY)
    private var historySize = 0

    /** Whether the rate has been measured across a long enough stretch to be trusted. */
    private var rateSettled = false

    // A reading that disagreed with the anchor, kept until the next one says
    // whether the song really moved or the reading was wrong.
    private var suspectAtMs = Double.NaN
    private var suspectMs = 0L
    private var rejections = 0

    private var glideStartMs = Double.NaN
    private var glideOffsetMs = 0.0

    /**
     * Forget the run so far: frames stopped arriving — playback paused, or the
     * app left the screen — and the next [frame] starts a new one.
     */
    fun restart() {
        lastNowMs = Double.NaN
        jumpPending = false
        glideStartMs = Double.NaN
        clearSuspect()
    }

    /**
     * Playback is not moving: settle on [positionMs] outright, as of [nowMs].
     * Called again for every reading that arrives while stopped, which is how a
     * seek made while paused still moves the words.
     */
    fun hold(positionMs: Long, nowMs: Double) {
        restart()
        displayedMs = positionMs.toDouble()
        heldAtMs = nowMs
    }

    /**
     * Advance to the frame at [nowMs] and return where the lyrics should be
     * drawn.
     *
     * [reportedMs] is the player's latest reading and [sampledAtMs] when it was
     * taken, or NaN where nobody said — it is then dated by the first frame that
     * sees it, the best that can be done. [discontinuity] is any value that
     * changes when the playhead jumps on purpose; only its changing matters.
     */
    fun frame(nowMs: Double, reportedMs: Long, sampledAtMs: Double, discontinuity: Long): Long {
        val firstOfRun = lastNowMs.isNaN()
        if (discontinuity != lastDiscontinuity) {
            if (lastDiscontinuity != null && !firstOfRun) jumpPending = true
            lastDiscontinuity = discontinuity
        }
        val isNew = reportedMs != lastReportMs ||
            (!sampledAtMs.isNaN() && sampledAtMs != lastSampledAtMs)
        lastReportMs = reportedMs
        lastSampledAtMs = sampledAtMs

        if (firstOfRun) {
            lastNowMs = nowMs
            // Taken while playback stood still — the last reading before a
            // pause, or one made during it — it is where the song still is now,
            // however long ago it was taken. Taken while playing, the song has
            // moved on since by exactly how long ago that was.
            val takenAt = when {
                sampledAtMs.isNaN() || sampledAtMs <= heldAtMs -> nowMs
                else -> minOf(sampledAtMs, nowMs)
            }
            anchor(takenAt, reportedMs)
            displayedMs = targetAt(nowMs)
            return displayedMs.toLong()
        }

        // Carried up to this frame on what was known before it, and only then
        // corrected by anything new: a jump folded in first would be advanced
        // by this frame a second time.
        val dt = (nowMs - lastNowMs).coerceAtLeast(0.0)
        lastNowMs = nowMs
        advance(nowMs, dt)

        if (isNew) {
            // A stamp from the future is a frame clock running a hair behind
            // the reading; one from before the anchor is out of order, and says
            // nothing the anchor does not.
            val takenAt = if (sampledAtMs.isNaN()) nowMs else minOf(sampledAtMs, nowMs)
            if (takenAt >= anchorAtMs || jumpPending) take(nowMs, takenAt, reportedMs)
        }
        return displayedMs.toLong()
    }

    /** Folds one new reading in — or holds it back, if it disagrees with the song so far. */
    private fun take(nowMs: Double, takenAt: Double, reportedMs: Long) {
        if (jumpPending) {
            // The reading the jump was announced with: where the song landed.
            jumpPending = false
            clearSuspect()
            anchor(takenAt, reportedMs)
            jumpTo(nowMs)
            return
        }
        val elapsed = takenAt - anchorAtMs
        if (abs(reportedMs - (anchorMs + elapsed * rate)) <= tolerance(elapsed)) {
            clearSuspect()
            anchor(takenAt, reportedMs, continues = true)
            return
        }
        // Off with nothing announced. One reading like that is a bad reading;
        // a second agreeing with it is the song somewhere else. And a run of
        // them, however they disagree, means the picture so far is what is
        // wrong — taken rather than refused forever, which is a clock that has
        // stopped listening.
        val sinceSuspect = takenAt - suspectAtMs
        val agrees = !suspectAtMs.isNaN() && sinceSuspect >= 0 &&
            abs(reportedMs - (suspectMs + sinceSuspect * rate)) <= tolerance(sinceSuspect)
        rejections++
        if (agrees) {
            // The two agree with each other, so between them they are the
            // start of the song's new stretch — and its first rate measurement.
            val firstAt = suspectAtMs
            val first = suspectMs
            clearSuspect()
            anchor(firstAt, first)
            anchor(takenAt, reportedMs, continues = true)
        } else if (rejections > MAX_REJECTIONS) {
            clearSuspect()
            anchor(takenAt, reportedMs)
            // From here [advance] closes the gap: ahead of the words it glides
            // forward; behind them it slows down — still never backwards.
        } else {
            suspectAtMs = takenAt
            suspectMs = reportedMs
        }
    }

    /**
     * How far a reading [elapsedMs] after the last may stray before it is
     * doubted: a fixed allowance for the player's own noise, and a little more
     * the longer the gap, for a rate not yet learned exactly.
     */
    private fun tolerance(elapsedMs: Double): Double =
        OUTLIER_MS + abs(elapsedMs) * if (rateSettled) RATE_SLACK else UNSETTLED_RATE_SLACK

    /**
     * Believes a reading. [continues] says it carries on from the one before —
     * the song has not jumped between them — so the two can measure the rate.
     */
    private fun anchor(atMs: Double, positionMs: Long, continues: Boolean = false) {
        anchorAtMs = atMs
        anchorMs = positionMs.toDouble()
        if (!continues) historySize = 0
        if (historySize == HISTORY) {
            historyAt.copyInto(historyAt, 0, 1, HISTORY)
            historyMs.copyInto(historyMs, 0, 1, HISTORY)
            historySize--
        }
        historyAt[historySize] = atMs
        historyMs[historySize] = anchorMs
        historySize++
        learnRate()
    }

    /**
     * The rate, measured from the oldest reading within [RATE_WINDOW_MS] to the
     * newest. Measured between neighbours half a second apart, a few
     * milliseconds of the player's own jitter was a few percent of rate, and
     * smoothing that away took long enough that a song at 1.5x had drifted a
     * third of a second before the clock agreed. Across seconds, the same jitter
     * is a fraction of a percent; and a rate that is really changing — Automix
     * easing a tempo back — is still followed within the window.
     */
    private fun learnRate() {
        val newest = historySize - 1
        if (newest < 1) return
        var oldest = 0
        while (oldest < newest && historyAt[newest] - historyAt[oldest] > RATE_WINDOW_MS) oldest++
        val span = historyAt[newest] - historyAt[oldest]
        if (span >= MIN_RATE_SPAN_MS) {
            rate = ((historyMs[newest] - historyMs[oldest]) / span).coerceIn(MIN_RATE, MAX_RATE)
            rateSettled = true
            return
        }
        // Not enough of a stretch yet: a rough step from the last two, so the
        // first second of a song at 1.5x is not spent believing it is at 1x.
        val pairSpan = historyAt[newest] - historyAt[newest - 1]
        if (!rateSettled && pairSpan >= MIN_PAIR_SPAN_MS) {
            val measured = ((historyMs[newest] - historyMs[newest - 1]) / pairSpan)
                .coerceIn(MIN_RATE, MAX_RATE)
            rate += (measured - rate) * PAIR_LEARNING
        }
    }

    private fun clearSuspect() {
        suspectAtMs = Double.NaN
        rejections = 0
    }

    private fun advance(nowMs: Double, dt: Double) {
        val target = targetAt(nowMs)
        // What this frame moves the words by on its own, and how far from the
        // song that leaves them.
        val step = dt * rate
        val error = target - (displayedMs + step)
        when {
            !glideStartMs.isNaN() -> {
                val progress = (nowMs - glideStartMs) / GLIDE_MS
                if (progress >= 1.0) {
                    displayedMs = target
                    glideStartMs = Double.NaN
                } else {
                    // The offset eases out while the target keeps moving, so the
                    // glide lands on a playhead that is still running.
                    displayedMs = target + glideOffsetMs * (1.0 - smoothstep(progress))
                }
            }
            // Confirmed, unannounced, and far: the song is somewhere else
            // entirely and no amount of slowing down will get there.
            error <= -LOST_MS || error >= LOST_MS -> displayedMs = target
            error >= GLIDE_FROM_MS -> {
                startGlide(nowMs - dt, displayedMs - (target - step))
                displayedMs = target + glideOffsetMs * (1.0 - smoothstep(dt / GLIDE_MS))
            }
            else -> {
                // Behind runs fast, ahead runs slow — and slow is floored well
                // above zero, so the words never stop to wait. The share of the
                // gap closed grows with the frame but never reaches all of it, so
                // a long frame after a stall cannot carry the words past the song.
                val closing = error * (dt / (dt + CORRECTION_MS))
                displayedMs += step + closing.coerceIn(-MAX_SLOW * step, MAX_SLEW * step)
            }
        }
    }

    /** An announced jump: rolled to over [GLIDE_MS] when it is near, taken at once when it is not. */
    private fun jumpTo(nowMs: Double) {
        val offset = displayedMs - targetAt(nowMs)
        if (abs(offset) > GLIDE_MAX_MS) {
            displayedMs = targetAt(nowMs)
            glideStartMs = Double.NaN
        } else {
            startGlide(nowMs, offset)
        }
    }

    private fun startGlide(startMs: Double, offsetMs: Double) {
        glideStartMs = startMs
        glideOffsetMs = offsetMs
    }

    /** Where the song is at [atMs]: the newest reading believed, carried forward. */
    private fun targetAt(atMs: Double): Double = anchorMs + (atMs - anchorAtMs) * rate
}

private fun smoothstep(fraction: Double): Double {
    val t = fraction.coerceIn(0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)
}

/**
 * A dated reading this far off where the song should be is not noise. Dated
 * properly, readings land within a few tens of milliseconds; this is several
 * times that, and well inside anything a listener would call out of sync.
 */
private const val OUTLIER_MS = 250.0

/**
 * Extra allowance per millisecond between readings, for a rate not known
 * exactly — and a wider one until it has been measured at all, or a song at
 * 1.5x looks like a run of jumps while the clock still thinks it is at 1x.
 */
private const val RATE_SLACK = 0.2
private const val UNSETTLED_RATE_SLACK = 0.75

/** Readings refused in a row before the next is taken whatever it says. */
private const val MAX_REJECTIONS = 2

/**
 * The rate is measured across readings at least [MIN_RATE_SPAN_MS] and at most
 * [RATE_WINDOW_MS] apart: closer, the player's own jitter is most of what the
 * measurement says; further, a real change of rate is followed too late.
 */
private const val MIN_RATE_SPAN_MS = 900.0
private const val RATE_WINDOW_MS = 4_000.0
private const val HISTORY = 16

/** Before then, a rough step from each pair at least this far apart. */
private const val MIN_PAIR_SPAN_MS = 300.0
private const val PAIR_LEARNING = 0.5

/** Playback rates the clock is allowed to conclude; a measurement outside these is noise. */
private const val MIN_RATE = 0.25
private const val MAX_RATE = 4.0

/**
 * The gap closes with a time constant of [CORRECTION_MS] — 60 ms behind runs
 * about 10 % fast — capped at [MAX_SLEW] faster and [MAX_SLOW] slower. A
 * sweep's speed changes far more than that from one word to the next, so the
 * correction never reads as anything; and at half speed at the slowest, the
 * words never read as stopped.
 */
private const val CORRECTION_MS = 600.0
private const val MAX_SLEW = 0.25
private const val MAX_SLOW = 0.5

/** Behind by this much, the words catch up over [GLIDE_MS] rather than by running fast. */
private const val GLIDE_FROM_MS = 300.0

/** am-lyrics eases its rewinds over 260 ms with the same smoothstep. */
private const val GLIDE_MS = 260.0

/** An announced jump further than this is taken at once rather than rolled through. */
private const val GLIDE_MAX_MS = 2_000.0

/** A confirmed gap this wide either way is another part of the song, and is jumped to. */
private const val LOST_MS = 3_000.0
