package com.music.bitchord.mcp

/** Pure access decision; global read/write permission is checked separately. */
internal object McpPlaylistPolicy {
    fun allowed(mode: String, selected: Boolean): Boolean = when (mode) {
        "blocklist" -> !selected
        "allowlist" -> selected
        else -> false
    }
}
