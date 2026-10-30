package com.music.bitchord.playback.cast

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two decisions [CastPlayback] makes by reading numbers and ids: whether a
 * receiver's PLAYING status is news about the phone's track, and where the bar
 * should be between the receiver's three-second reports.
 *
 * Both were the sort of code that only failed on a device — a ghost playing
 * after a stop, a bar that jumped to the end on connect — and neither could be
 * checked without a session. As plain functions they can.
 */
class CastStateTest {

    // ---- adopting the receiver's PLAYING -------------------------------------

    @Test
    fun `playing is taken as a resume while the session is live`() {
        assertTrue(adoptsReceiverPlaying(finished = false, phoneId = "a", receiverId = "a"))
        assertTrue(adoptsReceiverPlaying(finished = false, phoneId = null, receiverId = null))
    }

    /**
     * The ghost: told to stop, the receiver keeps reporting PLAYING for the
     * track before. Adopting it put the phone's new metadata on the old song.
     */
    @Test
    fun `playing is ignored after the session ended when it is another track`() {
        assertFalse(adoptsReceiverPlaying(finished = true, phoneId = "b", receiverId = "a"))
    }

    @Test
    fun `playing is ignored after the session ended when it lost its ids`() {
        assertFalse(adoptsReceiverPlaying(finished = true, phoneId = null, receiverId = "a"))
        assertFalse(adoptsReceiverPlaying(finished = true, phoneId = "a", receiverId = null))
    }

    /** Starting the phone's own track again is a resume, not a ghost. */
    @Test
    fun `playing the phone's own track is a resume even after the session ended`() {
        assertTrue(adoptsReceiverPlaying(finished = true, phoneId = "a", receiverId = "a"))
    }

    // ---- the position --------------------------------------------------------

    private val switchTimeout = 15_000L
    private val seekGrace = 1_000L

    private fun position(
        liveMs: Long? = 10_000L,
        askedMs: Long = 0L,
        requestedAtMs: Long = 0L,
        nowMs: Long = 10_000L,
        handoverOutstanding: Boolean = false,
        playing: Boolean = true,
        anchorPositionMs: Long = 10_000L,
        anchorAtMs: Long = 9_000L,
        durationMs: Long = 200_000L,
    ) = castPositionMs(
        CastPositionInputs(
            liveMs = liveMs,
            askedMs = askedMs,
            requestedAtMs = requestedAtMs,
            nowMs = nowMs,
            handoverOutstanding = handoverOutstanding,
            switchTimeoutMs = switchTimeout,
            seekGraceMs = seekGrace,
            playing = playing,
            anchorPositionMs = anchorPositionMs,
            anchorAtMs = anchorAtMs,
            durationMs = durationMs,
        ),
    )

    /**
     * While the receiver is still reporting the track before the one it was
     * handed, its clock is not this track's. The bar shows where the load was
     * asked to start instead of springing back to the old song's position.
     */
    @Test
    fun `during a handover the bar shows the position asked for`() {
        assertEquals(
            42_000L,
            position(
                askedMs = 42_000L,
                requestedAtMs = 10_000L,
                nowMs = 20_000L, // 10 s in, under the 15 s switch timeout
                handoverOutstanding = true,
                anchorPositionMs = 1_000L,
            ),
        )
    }

    /** A receiver that never moves on must not freeze the bar forever. */
    @Test
    fun `after the switch timeout the receiver is believed again`() {
        assertEquals(
            20_000L, // not the asked-for 42 s: the handover stopped being trusted
            position(
                askedMs = 42_000L,
                requestedAtMs = 10_000L,
                nowMs = 30_000L, // 20 s in, past the 15 s timeout
                handoverOutstanding = true,
                anchorPositionMs = 9_000L,
                anchorAtMs = 19_000L,
            ),
        )
    }

    /** A seek is answered a moment later; until then the receiver still reads the old place. */
    @Test
    fun `just after a seek the bar keeps the requested position`() {
        assertEquals(
            80_000L,
            position(
                liveMs = 5_000L,
                askedMs = 80_000L,
                requestedAtMs = 10_000L,
                nowMs = 10_500L, // inside the 1 s grace
            ),
        )
    }

    @Test
    fun `with no receiver position the bar shows the requested position`() {
        assertEquals(80_000L, position(liveMs = null, askedMs = 80_000L, requestedAtMs = 0L, nowMs = 10_000L))
    }

    @Test
    fun `paused shows the receiver's own position`() {
        assertEquals(
            12_345L,
            position(liveMs = 12_345L, requestedAtMs = 0L, playing = false, anchorPositionMs = 99_000L),
        )
    }

    @Test
    fun `paused never reports a negative position`() {
        assertEquals(0L, position(liveMs = -42L, requestedAtMs = 0L, playing = false))
    }

    /**
     * The first status is up to three seconds away and the anchor starts at
     * zero. Extrapolating from it measured from the phone's boot, which carried
     * the bar to the end of the track the moment a receiver was picked.
     */
    @Test
    fun `playing without an anchor reported shows the receiver's position`() {
        assertEquals(
            7_000L,
            position(liveMs = 7_000L, requestedAtMs = 0L, playing = true, anchorAtMs = 0L),
        )
    }

    /** Between statuses the receiver keeps playing, so the bar advances with it. */
    @Test
    fun `playing advances the last reported position by the time since`() {
        assertEquals(
            12_500L,
            position(
                liveMs = 10_000L,
                requestedAtMs = 0L,
                nowMs = 10_000L,
                playing = true,
                anchorPositionMs = 10_000L,
                anchorAtMs = 7_500L,
            ),
        )
    }

    @Test
    fun `playing is clamped to the track's duration`() {
        assertEquals(
            200_000L,
            position(
                requestedAtMs = 0L,
                nowMs = 300_000L,
                playing = true,
                anchorPositionMs = 199_000L,
                anchorAtMs = 290_000L,
                durationMs = 200_000L,
            ),
        )
    }

    @Test
    fun `an unknown duration is not a clamp`() {
        assertEquals(
            300_000L,
            position(
                requestedAtMs = 0L,
                nowMs = 300_000L,
                playing = true,
                anchorPositionMs = 290_000L,
                anchorAtMs = 290_000L,
                durationMs = C.TIME_UNSET,
            ),
        )
    }
}
