package com.music.bitchord.mcp

import org.junit.Assert.*
import org.junit.Test

class McpPlaylistIdTest {
    @Test fun scopeIsStableAndAccountProfileBound() {
        val a = McpPlaylistId.scope("acct-1", "profile-1")
        assertEquals(a, McpPlaylistId.scope("acct-1", "profile-1"))
        assertEquals(64, a.length)
        assertNotEquals(a, McpPlaylistId.scope("acct-1", "profile-2"))
        assertNotEquals(a, McpPlaylistId.scope("acct-2", "profile-1"))
    }
    @Test fun scopeAvoidsDelimiterCollision() {
        assertNotEquals(McpPlaylistId.scope("a:b", "c"), McpPlaylistId.scope("a", "b:c"))
    }
    @Test fun parseAcceptsOnlyCanonicalShape() {
        val scope = McpPlaylistId.scope("acct", "profile")
        assertEquals(scope to "VLPLabc_-123", McpPlaylistId.parseYouTube("youtube:$scope:VLPLabc_-123"))
    }
    @Test fun parseRejectsPathsAndAliases() {
        val scope = McpPlaylistId.scope("acct", "profile")
        listOf(
            "local:playlist:sp_local_1",
            "youtube:$scope:PLabc",
            "youtube:$scope:VLabc:extra",
            "youtube:${scope.uppercase()}:VLabc",
            "youtube:$scope:VL",
            "youtube:$scope:VL../../etc",
            "spotify:$scope:VLabc",
            "youtube:$scope:VL${"a".repeat(201)}",
        ).forEach { id ->
            try { McpPlaylistId.parseYouTube(id); fail("accepted $id") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
