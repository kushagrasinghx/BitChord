package com.music.bitchord.mcp

import java.security.MessageDigest

internal object McpPlaylistId {
    fun scope(account: String, profile: String): String {
        require(account.isNotBlank() && profile.isNotBlank())
        val raw = "v1:${account.length}:$account:${profile.length}:$profile"
        return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
    fun parseYouTube(id: String): Pair<String, String> {
        val p = id.split(':')
        require(p.size == 3 && p[0] == "youtube" && Regex("[a-f0-9]{64}").matches(p[1]) &&
            Regex("VL[A-Za-z0-9_-]{1,200}").matches(p[2])) { "Invalid account playlist ID" }
        return p[1] to p[2]
    }
}
