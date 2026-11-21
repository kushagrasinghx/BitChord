package com.music.bitchord.mcp

import org.junit.Assert.*
import org.junit.Test

class McpPlaylistPolicyTest {
    @Test fun allowlistDeniesUnselectedAndFuturePlaylists() { assertFalse(McpPlaylistPolicy.allowed("allowlist", false)) }
    @Test fun allowlistAllowsSelected() { assertTrue(McpPlaylistPolicy.allowed("allowlist", true)) }
    @Test fun blocklistDeniesSelected() { assertFalse(McpPlaylistPolicy.allowed("blocklist", true)) }
    @Test fun blocklistAllowsUnselectedAndFuturePlaylists() { assertTrue(McpPlaylistPolicy.allowed("blocklist", false)) }
    @Test fun invalidModeFailsClosed() { assertFalse(McpPlaylistPolicy.allowed("invalid", false)) }
}
