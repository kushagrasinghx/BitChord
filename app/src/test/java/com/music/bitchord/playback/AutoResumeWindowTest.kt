package com.music.bitchord.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoResumeWindowTest {

    private fun assertWindow(count: Int, index: Int, expectedSize: Int) {
        val window = LastPlayed.window(count, index)
        val items = window.toList()
        val resumeIndex = index - window.first
        assertEquals(expectedSize, items.size)
        assertTrue(index in items)
        assertTrue(resumeIndex in items.indices)
        assertTrue(items.size <= 76)
    }

    @Test
    fun `one item queue resumes at zero`() = assertWindow(count = 1, index = 0, expectedSize = 1)

    @Test
    fun `large queue is capped around the current item`() =
        assertWindow(count = 1_000, index = 500, expectedSize = 76)

    @Test
    fun `start of a large queue keeps only upcoming items`() =
        assertWindow(count = 1_000, index = 0, expectedSize = 51)

    @Test
    fun `end of a large queue keeps only history`() =
        assertWindow(count = 1_000, index = 999, expectedSize = 26)

    @Test
    fun `small queue is returned whole`() = assertWindow(count = 40, index = 10, expectedSize = 40)
}
