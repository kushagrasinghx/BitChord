package com.music.bitchord.mcp

import org.junit.Assert.*
import org.junit.Test

class McpPlaylistIdTest {
    @Test fun isolatesAccountAndProfileAndDelimiters() {
        assertNotEquals(McpPlaylistId.scope("a", "p"), McpPlaylistId.scope("b", "p"))
        assertNotEquals(McpPlaylistId.scope("a", "p"), McpPlaylistId.scope("a", "q"))
        assertNotEquals(McpPlaylistId.scope("a:b", "c"), McpPlaylistId.scope("a", "b:c"))
        assertEquals(McpPlaylistId.scope("a", "p"), McpPlaylistId.scope("a", "p"))
    }
    @Test fun likedAndPlaylistRoundtrip() {
        val scope = McpPlaylistId.scope("a", "personal")
        assertEquals(scope to "VLLM", McpPlaylistId.parseYouTube("youtube:$scope:VLLM"))
        assertEquals(scope to "VLPL_a-1", McpPlaylistId.parseYouTube("youtube:$scope:VLPL_a-1"))
    }
    @Test fun rejectsPathsMalformedScopeAndExtraSegments() {
        val scope = McpPlaylistId.scope("a", "p")
        listOf("youtube:$scope:file:///tmp", "youtube:$scope:VL../x", "youtube:bad:VLLM",
            "youtube:$scope:VLLM:extra", "spotify:$scope:VLLM", "youtube:$scope:VL").forEach {
            try { McpPlaylistId.parseYouTube(it); fail("Accepted $it") } catch (_: IllegalArgumentException) { }
        }
    }
}
