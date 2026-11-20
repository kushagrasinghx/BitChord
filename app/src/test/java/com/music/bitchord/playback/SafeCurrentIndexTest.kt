package com.music.bitchord.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class SafeCurrentIndexTest {

    @Test
    fun `empty player returns zero instead of throwing`() {
        assertEquals(0, safeCurrentIndex(current = 0, count = 0))
        assertEquals(0, safeCurrentIndex(current = -1, count = 0))
    }

    @Test
    fun `index is clamped into the item range`() {
        assertEquals(0, safeCurrentIndex(current = -4, count = 10))
        assertEquals(9, safeCurrentIndex(current = 99, count = 10))
        assertEquals(4, safeCurrentIndex(current = 4, count = 10))
    }
}
