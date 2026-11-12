package com.music.bitchord.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TvPinThrottleTest {

    private lateinit var fakePrefs: FakeSharedPreferences

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        TvSettings.init(fakePrefs)
        TvPinThrottle.resetAll()
    }

    @Test
    fun testDelayIncreasesProgressivelyWithoutLockout() {
        // First 4 attempts have no lockout delay
        assertEquals(0L, TvPinThrottle.calculateDelayMs(0))
        assertEquals(0L, TvPinThrottle.calculateDelayMs(1))
        assertEquals(0L, TvPinThrottle.calculateDelayMs(2))
        assertEquals(0L, TvPinThrottle.calculateDelayMs(3))
        assertEquals(0L, TvPinThrottle.calculateDelayMs(4))

        // Lockout starts at 5 attempts
        assertEquals(10_000L, TvPinThrottle.calculateDelayMs(5))
        assertEquals(30_000L, TvPinThrottle.calculateDelayMs(6))
        assertEquals(60_000L, TvPinThrottle.calculateDelayMs(7))
        assertEquals(120_000L, TvPinThrottle.calculateDelayMs(8))
        assertEquals(180_000L, TvPinThrottle.calculateDelayMs(9))
        assertEquals(180_000L, TvPinThrottle.calculateDelayMs(20))
        assertEquals("User is never locked out permanently, delay is capped at 3 minutes", 180_000L, TvPinThrottle.calculateDelayMs(10_000))
    }

    @Test
    fun testFormatDurationAppleStyle() {
        assertEquals("1 second", TvPinThrottle.formatDuration(0))
        assertEquals("1 second", TvPinThrottle.formatDuration(1))
        assertEquals("2 seconds", TvPinThrottle.formatDuration(2))
        assertEquals("10 seconds", TvPinThrottle.formatDuration(10))
        assertEquals("30 seconds", TvPinThrottle.formatDuration(30))
        assertEquals("1 minute", TvPinThrottle.formatDuration(60))
        assertEquals("2 minutes", TvPinThrottle.formatDuration(120))
        assertEquals("3 minutes", TvPinThrottle.formatDuration(180))
    }

    @Test
    fun testCooldownTrackingAndExpiry() {
        val key = "profile_test_cooldown"
        val start = 100_000L

        // Attempts 1-4 have no delay
        for (i in 1..4) {
            assertEquals(0L, TvPinThrottle.recordFailure(key, now = start))
            assertEquals(i, TvPinThrottle.getFailedAttempts(key))
            assertEquals(0L, TvPinThrottle.getDelayRemainingMs(key, now = start))
        }

        // 5th attempt triggers 10s lockout
        val delay5 = TvPinThrottle.recordFailure(key, now = start)
        assertEquals(10_000L, delay5)
        assertEquals(5, TvPinThrottle.getFailedAttempts(key))

        // In the middle of cooldown
        assertEquals(5_000L, TvPinThrottle.getDelayRemainingMs(key, now = start + 5_000L))

        // At exact expiry
        assertEquals(0L, TvPinThrottle.getDelayRemainingMs(key, now = start + 10_000L))

        // After expiry
        assertEquals(0L, TvPinThrottle.getDelayRemainingMs(key, now = start + 12_000L))

        // 6th attempt increases delay to 30s
        val delay6 = TvPinThrottle.recordFailure(key, now = start + 12_000L)
        assertEquals(30_000L, delay6)
        assertEquals(6, TvPinThrottle.getFailedAttempts(key))
        assertEquals(15_000L, TvPinThrottle.getDelayRemainingMs(key, now = start + 27_000L))
    }

    @Test
    fun testSuccessResetsThrottle() {
        val key = "profile_success"
        val start = 50_000L

        repeat(5) { TvPinThrottle.recordFailure(key, now = start) }
        assertEquals(5, TvPinThrottle.getFailedAttempts(key))
        assertTrue(TvPinThrottle.getDelayRemainingMs(key, now = start) > 0)

        TvPinThrottle.recordSuccess(key)
        assertEquals(0, TvPinThrottle.getFailedAttempts(key))
        assertEquals(0L, TvPinThrottle.getDelayRemainingMs(key, now = start))
    }

    @Test
    fun testProfileThrottlesAreIndependent() {
        val profileA = "profile_a"
        val profileB = "profile_b"
        val now = 1_000L

        TvPinThrottle.recordFailure(profileA, now = now)
        TvPinThrottle.recordFailure(profileA, now = now)

        assertEquals(2, TvPinThrottle.getFailedAttempts(profileA))
        assertEquals(0, TvPinThrottle.getFailedAttempts(profileB))
        assertEquals(0L, TvPinThrottle.getDelayRemainingMs(profileB, now = now))
    }

    @Test
    fun testTvSettingsWipeAllPinsResetsThrottle() {
        val profileA = "profile_wipe_a"
        TvPinThrottle.recordFailure(profileA, now = 10_000L)
        assertEquals(1, TvPinThrottle.getFailedAttempts(profileA))

        TvSettings.wipeAllPins()
        assertEquals(0, TvPinThrottle.getFailedAttempts(profileA))
        assertEquals(0L, TvPinThrottle.getDelayRemainingMs(profileA, now = 10_000L))
    }

    @Test
    fun testTvSettingsSetProfilePinResetsThrottle() {
        val profileId = "profile_reset_on_pin_change"
        TvPinThrottle.recordFailure(profileId, now = 20_000L)
        assertEquals(1, TvPinThrottle.getFailedAttempts(profileId))

        TvSettings.setProfilePin(profileId, "1234")
        assertEquals(0, TvPinThrottle.getFailedAttempts(profileId))
        assertEquals(0L, TvPinThrottle.getDelayRemainingMs(profileId, now = 20_000L))
    }

    @Test
    fun testRebootRestartsFullCooldown() {
        val key = "profile_reboot"
        // Fifth failure late in a long uptime: 10s lockout.
        repeat(5) { TvPinThrottle.recordFailure(key, now = 5_000_000L) }

        // After a reboot the monotonic clock restarts near zero, far below the recorded reading.
        assertEquals(10_000L, TvPinThrottle.getDelayRemainingMs(key, now = 3_000L))
    }
}
