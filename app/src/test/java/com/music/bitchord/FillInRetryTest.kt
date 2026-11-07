package com.music.bitchord

import com.music.bitchord.ui.retryingOrNull
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A page's fill-in rides out a dropped continuation request (#1178). */
class FillInRetryTest {

    @Test
    fun `a page that fails twice is fetched on the third try`() = runTest {
        var tries = 0
        val page = retryingOrNull(longArrayOf(2_000L, 5_000L, 15_000L)) { if (++tries < 3) null else "page" }
        assertEquals("page", page)
        assertEquals(3, tries)
        assertEquals(7_000L, currentTime)
    }

    @Test
    fun `a page that never comes gives up after the last wait`() = runTest {
        var tries = 0
        assertNull(retryingOrNull(longArrayOf(2_000L, 5_000L)) { tries++; null })
        assertEquals(3, tries)
    }

    @Test
    fun `a page that comes at once is not waited on`() = runTest {
        assertEquals("page", retryingOrNull(longArrayOf(2_000L)) { "page" })
        assertEquals(0L, currentTime)
    }
}
