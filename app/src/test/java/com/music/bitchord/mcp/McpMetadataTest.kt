package com.music.bitchord.mcp

import com.music.bitchord.data.model.Song
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class McpMetadataTest {
    private val song = Song("id", "Title", "Artist", null, durationText = "3:20", isExplicit = null)
    @Test fun whitelist() {
        assertTrue("title" in McpMetadata.select(emptyList()))
        assertEquals(setOf("lyrics"), McpMetadata.select(listOf("lyrics")))
        try { McpMetadata.select(listOf("local_path")); fail("must reject") } catch (_: IllegalArgumentException) { }
    }
    @Test fun durationAndUnknowns() {
        assertEquals(200L, McpMetadata.duration("3:20"))
        assertNull(McpMetadata.duration("3:99"))
        assertNull(McpMetadata.match(song, "explicit", JsonPrimitive(false)))
        assertNull(McpMetadata.match(song, "year", JsonPrimitive(2025)))
        assertEquals(true, McpMetadata.match(song, "duration_min", JsonPrimitive(200)))
        assertEquals(false, McpMetadata.match(song, "duration_max", JsonPrimitive(199)))
        assertEquals(true, McpMetadata.match(song, "artist", JsonPrimitive("ART")))
    }
    @Test fun criteriaValidation() {
        McpMetadata.validateCriteria(buildJsonObject { put("explicit", false); put("year", 2025) })
        for (c in listOf(buildJsonObject { put("bpm", 0) }, buildJsonObject { put("explicit", "false") },
            buildJsonObject { put("duration_min", 100); put("duration_max", 10) })) {
            try { McpMetadata.validateCriteria(c); fail("must reject") } catch (_: IllegalArgumentException) { }
        }
    }
}
