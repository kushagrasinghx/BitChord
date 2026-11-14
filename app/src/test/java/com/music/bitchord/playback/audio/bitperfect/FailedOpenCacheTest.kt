package com.music.bitchord.playback.audio.bitperfect

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FailedOpenCacheTest {

    @Test
    fun aFailureExpiresAfterTheRetryWindow() {
        var now = 1_000L
        val cache = FailedOpenCache(ttlMs = 30_000L) { now }
        cache.mark("usb/1", "48k/2/24")

        assertTrue(cache.contains("usb/1", "48k/2/24"))
        now += 29_999L
        assertTrue(cache.contains("usb/1", "48k/2/24"))
        now += 1L
        assertFalse(cache.contains("usb/1", "48k/2/24"))
    }

    @Test
    fun changingTheDeviceOrModeDropsTheOldFailures() {
        var now = 1_000L
        val cache = FailedOpenCache(ttlMs = 30_000L) { now }
        cache.mark("usb/1", "48k/2/24")

        assertFalse(cache.contains("usb/2", "48k/2/24"))
        assertFalse(cache.contains("usb/1", "48k/2/24"))
    }

    @Test
    fun aNewFailureRestartsTheWindow() {
        var now = 1_000L
        val cache = FailedOpenCache(ttlMs = 30_000L) { now }
        cache.mark("usb/1", "48k/2/24")
        now += 20_000L
        cache.mark("usb/1", "48k/2/24")
        now += 29_999L
        assertTrue(cache.contains("usb/1", "48k/2/24"))
    }
}
