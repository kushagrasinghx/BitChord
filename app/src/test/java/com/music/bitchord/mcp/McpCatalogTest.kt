package com.music.bitchord.mcp

import com.music.bitchord.data.model.Song
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class McpCatalogTest {
    private fun rejects(block: () -> Unit) {
        try { block(); fail("must reject") } catch (_: IllegalArgumentException) { }
    }
    @Test fun kindsAndPermissionBoundary() {
        assertEquals("album", McpCatalog.type(buildJsonObject { put("type", "album") }))
        rejects { McpCatalog.type(buildJsonObject { put("type", "video") }) }
        rejects { McpCatalog.validateReadId("playlist", "VLprivate") }
        rejects { McpCatalog.validateReadId("album", "VLprivate") }
        rejects { McpCatalog.validateReadId("artist", "MPREbalbum") }
        McpCatalog.validateReadId("album", "MPREbalbum")
        McpCatalog.validateReadId("artist", "UC" + "a".repeat(22))
    }
    @Test fun projectionAndBounds() {
        assertEquals(setOf("title"), McpCatalog.fields("album", buildJsonObject { put("fields", buildJsonArray { add("title") }) }))
        rejects { McpCatalog.fields("artist", buildJsonObject { put("fields", buildJsonArray { add("local_path") }) }) }
        rejects { McpCatalog.number(buildJsonObject { put("limit", "10") }, "limit", 10, 1..20) }
        rejects { McpCatalog.number(buildJsonObject { put("limit", 21) }, "limit", 10, 1..20) }
    }
    @Test fun productionFilters() {
        val song = Song("id", "Title", "Artist", null)
        val criteria = buildJsonObject {
            put("year_min", 2020); put("year_max", 2025)
            put("include_artists", buildJsonArray { add("artist") })
            put("exclude_genres", buildJsonArray { add("nonexistent") })
        }
        McpMetadata.validateCriteria(criteria)
        assertEquals(true, McpMetadata.matchCriterion(song, "include_artists", criteria.getValue("include_artists")))
        assertEquals(true, McpMetadata.matchCriterion(song, "year_min", JsonPrimitive(2020), "2021"))
        assertEquals(false, McpMetadata.matchCriterion(song, "year_max", JsonPrimitive(2020), "2021"))
        assertNull(McpMetadata.matchCriterion(song, "year_min", JsonPrimitive(2020)))
        rejects { McpMetadata.validateCriteria(buildJsonObject { put("year_min", 2025); put("year_max", 2020) }) }
        rejects { McpMetadata.validateCriteria(buildJsonObject { put("include_artists", "artist") }) }
    }
}
