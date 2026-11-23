package com.music.bitchord

import com.music.bitchord.data.model.SearchFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFilterTest {

    @Test
    fun `library search filter defines correct params and requires auth`() {
        val filter = SearchFilter.LIBRARY
        assertEquals("Library", filter.label)
        assertEquals("agIYBA==", filter.params)
        assertTrue(filter.requiresAuth)
    }

    @Test
    fun `public catalog search filters do not require auth`() {
        val publicFilters = listOf(
            SearchFilter.ALL,
            SearchFilter.SONGS,
            SearchFilter.VIDEOS,
            SearchFilter.ALBUMS,
            SearchFilter.ARTISTS,
            SearchFilter.PLAYLISTS,
        )
        for (filter in publicFilters) {
            assertFalse("${filter.name} should not require auth", filter.requiresAuth)
        }
    }

    @Test
    fun `all filters have distinct non-empty labels`() {
        val labels = SearchFilter.entries.map { it.label }
        assertEquals(labels.distinct().size, labels.size)
        assertTrue(labels.all { it.isNotBlank() })
    }
}
