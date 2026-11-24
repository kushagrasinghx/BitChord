package com.music.bitchord.ui.utils

import androidx.compose.ui.input.pointer.PointerId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetTouchGuardTest {

    @Test
    fun `marks and clears false-grab pointer id accurately`() {
        val guard = SheetTouchGuard()
        val first = PointerId(1L)
        val second = PointerId(2L)

        assertFalse(guard.isFalseGrab(first))

        guard.markFalseGrab(first)
        assertTrue(guard.isFalseGrab(first))
        assertFalse(guard.isFalseGrab(second))

        // Clearing a different pointer id leaves the active false grab intact.
        guard.clearFalseGrab(second)
        assertTrue(guard.isFalseGrab(first))

        // Clearing the matching pointer id resets the guard.
        guard.clearFalseGrab(first)
        assertFalse(guard.isFalseGrab(first))
    }

    @Test
    fun `unconditional clear resets any active false grab`() {
        val guard = SheetTouchGuard()
        val pointer = PointerId(7L)

        guard.markFalseGrab(pointer)
        assertTrue(guard.isFalseGrab(pointer))

        guard.clearFalseGrab()
        assertFalse(guard.isFalseGrab(pointer))
    }
}
