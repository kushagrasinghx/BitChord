package com.music.bitchord.mcp

import org.junit.Assert.*
import org.junit.Test

class McpSpotifyPlaylistsTest {
    @Test fun stableSha256Scope() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", McpSpotifyPlaylists.scope("abc"))
        assertNotEquals(McpSpotifyPlaylists.scope("user-a"), McpSpotifyPlaylists.scope("user-b"))
    }

    @Test fun playlistAndLikedRoundTrip() {
        val scope = McpSpotifyPlaylists.scope("account")
        listOf("liked", "37i9dQZF1DXcBWIGoYBM5").forEach { raw ->
            val encoded = McpSpotifyPlaylists.encode(scope, raw)
            assertEquals("spotify:$scope:$raw", encoded)
            assertEquals(raw, McpSpotifyPlaylists.decode(encoded, scope))
            assertFalse(encoded.contains("account"))
        }
    }

    @Test fun rejectsOtherAccount() {
        val id = McpSpotifyPlaylists.encode(McpSpotifyPlaylists.scope("one"), "liked")
        try {
            McpSpotifyPlaylists.decode(id, McpSpotifyPlaylists.scope("two"))
            fail("Cross-account playlist accepted")
        } catch (_: SecurityException) { }
    }

    @Test fun rejectsMalformedIds() {
        val scope = McpSpotifyPlaylists.scope("account")
        listOf("liked", "youtube:$scope:liked", "spotify:abc:liked", "spotify:$scope:",
            "spotify:$scope:../me", "spotify:$scope:liked:extra", "spotify:$scope:bad?id").forEach { id ->
            try {
                McpSpotifyPlaylists.decode(id, scope)
                fail("Malformed playlist accepted")
            } catch (_: IllegalArgumentException) { }
        }
    }
}
