package com.music.bitchord.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where a track is when a blend hands over to it.
 *
 * A crossfade plays the incoming track underneath the outgoing one for the
 * whole length of the fade, so by the time it becomes the current track it is
 * already seconds in. Reporting its start instead left the scrubber and the
 * lyrics a fade-length behind the audio — audible as words arriving late, and
 * fixable only by dragging the scrubber.
 *
 * The arithmetic that converts what the blend consumed into source time is what
 * this pins; it is the half that was wrong.
 */
class DesktopCrossfadePositionTest {

    /**
     * The engine's own function, not a copy of it.
     *
     * This test used to restate the arithmetic, which proved the formula and nothing about whether
     * playback used it.
     */
    private fun elapsedUs(samples: Long, channels: Int, sampleRate: Int): Long =
        blendElapsedUs(samples, channels, sampleRate)

    @Test
    fun aFullFadeLeavesTheIncomingTrackAFadeIn() {
        // Eight seconds of stereo at 44.1kHz, which is what a fade of that
        // length feeds the incoming decoder.
        val channels = 2
        val rate = 44_100
        val samples = 8L * rate * channels
        assertEquals(8_000_000L, elapsedUs(samples, channels, rate))
    }

    @Test
    fun theHandoverPositionIsTheTrackStartPlusWhatWasPlayed() {
        val channels = 2
        val rate = 48_000
        val startUs = 0L
        val consumed = 6L * rate * channels
        assertEquals(6_000_000L, startUs + elapsedUs(consumed, channels, rate))
    }

    @Test
    fun aTrackThatWasItselfSeekedKeepsThatOffset() {
        // Starting a blend into a track that begins at 30s must land at 30s
        // plus the fade, not at the fade alone.
        val channels = 2
        val rate = 44_100
        val startUs = 30_000_000L
        val consumed = 5L * rate * channels
        assertEquals(35_000_000L, startUs + elapsedUs(consumed, channels, rate))
    }

    @Test
    fun monoAndSurroundConvertByChannelCountRatherThanAssumingStereo() {
        val rate = 44_100
        assertEquals(1_000_000L, elapsedUs(1L * rate * 1, 1, rate))
        assertEquals(1_000_000L, elapsedUs(1L * rate * 6, 6, rate))
    }

    @Test
    fun aSinkThatHasNotStartedYieldsNothingRatherThanDividingByZero() {
        assertEquals(0L, elapsedUs(1_000, 2, 0))
        assertEquals(0L, elapsedUs(1_000, 0, 44_100))
    }

    @Test
    fun theOldBehaviourWouldHaveBeenBehindByTheWholeFade() {
        // What the bug looked like: the position published at handover was the
        // track's start, so it trailed the audio by the fade length.
        val channels = 2
        val rate = 44_100
        val fadeSeconds = 8L
        val consumed = fadeSeconds * rate * channels
        val reportedBefore = 0L
        val reportedAfter = elapsedUs(consumed, channels, rate)
        assertTrue(reportedAfter - reportedBefore == fadeSeconds * 1_000_000L)
    }

    @Test
    fun `nothing consumed and nonsense formats are zero rather than a crash`() {
        assertEquals(0L, blendElapsedUs(0L, 2, 44_100))
        assertEquals(0L, blendElapsedUs(-1L, 2, 44_100))
        assertEquals(0L, blendElapsedUs(1_000L, 0, 44_100))
        assertEquals(0L, blendElapsedUs(1_000L, 2, 0))
    }

    @Test
    fun `a mono blend counts every sample as a frame`() {
        assertEquals(1_000_000L, blendElapsedUs(48_000L, 1, 48_000))
    }

    // ── The cue Automix enters the incoming track on ──────────────────────

    /**
     * What the handover publishes: where the incoming track was entered, plus what the blend has
     * already played of it.
     */
    private fun handoverUs(cueUs: Long, samples: Long, channels: Int, rate: Int): Long =
        cueUs + blendElapsedUs(samples, channels, rate)

    @Test
    fun `a cued transition resumes at the cue, not at the start of the track`() {
        // Automix enters the incoming track on a downbeat — 12.1s in, in the logged case — and
        // fades for 7.4s. Reporting 7.4s instead of 19.5s left every position for the rest of the
        // track short by the whole cue, which is what put the lyrics behind the audio.
        val rate = 44_100
        val channels = 2
        val cueUs = 12_100_000L
        val blended = (7.4 * rate * channels).toLong()
        // Sample counts are integral, so the blend lands within a sample of 7.4s.
        val published = handoverUs(cueUs, blended, channels, rate)
        assertTrue(kotlin.math.abs(published - 19_500_000L) < 1_000L, "published ${published}us")
    }

    @Test
    fun `an uncued crossfade still resumes at the length of the fade`() {
        val rate = 48_000
        val channels = 2
        assertEquals(6_000_000L, handoverUs(0L, 6L * rate * channels, channels, rate))
    }
}
