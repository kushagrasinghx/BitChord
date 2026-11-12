package com.music.bitchord

import com.music.bitchord.playback.ManualUpgradeThrottle
import org.junit.Assert.assertEquals
import org.junit.Test

class ManualUpgradeThrottleTest {

    @Test
    fun `manual retries are accepted only once per cooldown`() {
        val throttle = ManualUpgradeThrottle(cooldownMs = 30_000L)

        assertEquals(0L, throttle.tryAcquire(nowMs = 1_000L))
        assertEquals(30_000L, throttle.tryAcquire(nowMs = 1_000L))
        assertEquals(1L, throttle.tryAcquire(nowMs = 30_999L))
        assertEquals(0L, throttle.tryAcquire(nowMs = 31_000L))
    }

    @Test
    fun `time before the first manual request does not consume the cooldown`() {
        val throttle = ManualUpgradeThrottle(cooldownMs = 30_000L)

        // Automatic upgrade activity never calls tryAcquire. However long the
        // app has been playing, the first explicit request is accepted.
        assertEquals(0L, throttle.tryAcquire(nowMs = 9_000_000L))
    }
}
