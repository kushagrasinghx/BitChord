package com.music.bitchord

import org.junit.Assert.assertEquals
import org.junit.Test

/** What a release page's own menu hands Download and the queue rows (#1178). */
class LoadedWholeTest {

    @Test
    fun `a page that has loaded every row hands them over`() {
        assertEquals(listOf("a", "b"), loadedWhole(listOf("a", "b"), continuation = null))
    }

    @Test
    fun `a page with rows still to load hands none, so the whole list is fetched`() {
        assertEquals(emptyList<String>(), loadedWhole(List(599) { "track-$it" }, continuation = "next-page"))
    }
}
