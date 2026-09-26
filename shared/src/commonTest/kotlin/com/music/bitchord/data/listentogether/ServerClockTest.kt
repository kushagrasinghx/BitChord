package com.music.bitchord.data.listentogether

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The translation from this device's clock to the party's.
 *
 * What matters is which sample is believed: the shortest round trip, not the newest and not an
 * average, because one request stuck behind a radio wake-up is wrong by hundreds of milliseconds
 * and averaging folds that error in rather than discarding it.
 */
class ServerClockTest {

    private var now = 1_000L
    private fun clock() = ServerClock { now }

    @Test
    fun `it knows nothing until a round trip completes`() {
        val clock = clock()
        assertFalse(clock.synced)
        assertNull(clock.serverNowMs())
    }

    @Test
    fun `one exchange gives the offset at the midpoint of the round trip`() {
        val clock = clock()
        // Out at 100, back at 200; the server read 5_150 in between.
        clock.record(sentAtLocalMs = 100, serverMs = 5_150, receivedAtLocalMs = 200)
        assertTrue(clock.synced)
        assertEquals(5_000, clock.offsetMs)
        assertEquals(100, clock.roundTripMs)
    }

    @Test
    fun `the shortest round trip wins, not the most recent`() {
        val clock = clock()
        clock.record(sentAtLocalMs = 0, serverMs = 5_010, receivedAtLocalMs = 20)
        // A later, far slower exchange carrying a worse estimate must not displace it.
        clock.record(sentAtLocalMs = 100, serverMs = 5_400, receivedAtLocalMs = 700)
        assertEquals(5_000, clock.offsetMs)
        assertEquals(20, clock.roundTripMs)
    }

    @Test
    fun `a stale best sample is dropped rather than pinning the session`() {
        val clock = clock()
        // A lucky 20ms exchange, offset 5_000.
        clock.record(sentAtLocalMs = 0, serverMs = 5_010, receivedAtLocalMs = 20)
        // Well past the sample lifetime, slower, and the clocks have drifted to 6_000. Were the
        // stale sample still eligible its shorter round trip would win and pin the old offset.
        clock.record(sentAtLocalMs = 300_000, serverMs = 306_200, receivedAtLocalMs = 300_400)
        assertEquals(6_000, clock.offsetMs)
        assertEquals(400, clock.roundTripMs)
    }

    @Test
    fun `the server reading is this device's clock plus the offset`() {
        val clock = clock()
        clock.record(sentAtLocalMs = 100, serverMs = 5_150, receivedAtLocalMs = 200)
        now = 900
        assertEquals(5_900, clock.serverNowMs())
    }

    @Test
    fun `a reset forgets everything`() {
        val clock = clock()
        clock.record(sentAtLocalMs = 100, serverMs = 5_150, receivedAtLocalMs = 200)
        clock.reset()
        assertFalse(clock.synced)
        assertEquals(0, clock.roundTripMs)
    }
}
