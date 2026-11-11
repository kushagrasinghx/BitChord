package com.music.bitchord

import com.music.bitchord.download.shouldKeepDraining
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadShutdownTest {

    @Test
    fun `shutdown restarts when work arrived during teardown`() {
        assertTrue(shouldKeepDraining(busy = true))
    }

    @Test
    fun `shutdown proceeds when the queue is genuinely empty`() {
        assertFalse(shouldKeepDraining(busy = false))
    }
}
