package com.music.bitchord.mcp

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ToolArgumentsTest {
    private fun validate(raw: String) = validateToolArguments(Json.parseToJsonElement(raw).jsonObject,
        mapOf("limit" to "integer", "offset" to "integer", "position_ms" to "integer", "video_id" to "string"), emptyList())
    @Test fun rejectsWrongTypesAndRanges() {
        listOf("\"2\"", "2.0", "2e0", "true", "null", "[]", "{}", "-1", "201", "9223372036854775808").forEach {
            try { validate("{\"limit\":$it}"); fail("accepted $it") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun validBoundaries() {
        validate("{\"limit\":200,\"offset\":2147483647,\"position_ms\":9223372036854775807,\"video_id\":\"abcdefghij_\"}")
    }
    @Test fun rejectsPathAndUnknownFieldAndMissingRequired() {
        listOf("{\"video_id\":\"file:///tmp\"}", "{\"unexpected\":1}", "{\"offset\":2147483648}").forEach {
            try { validate(it); fail("accepted $it") } catch (_: IllegalArgumentException) { }
        }
        try { validateToolArguments(JsonObject(emptyMap()), mapOf("query" to "string"), listOf("query")); fail() }
        catch (_: IllegalArgumentException) { }
    }
}
