package com.music.bitchord.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class McpDownloadToolsTest {
    @Test fun rejectsInvalidArguments() {
        listOf("download_enqueue" to "{\"video_id\":\"https://secret/path\"}",
            "download_delete" to "{}", "playlist_download" to "{\"playlist_id\":\"local:playlist:x\"}",
            "playlist_download_cancel" to "{\"playlist_id\":\"x\",\"extra\":true}").forEach { (name, args) ->
            assertThrows(IllegalArgumentException::class.java) {
                McpDownloadTools.validate(name, Json.parseToJsonElement(args).jsonObject)
            }
        }
    }
    @Test fun allNamesHaveStrictSchemas() {
        McpDownloadTools.names.forEach {
            if (it == "downloads_list") {
                McpDownloadTools.validate(it, Json.parseToJsonElement("{\"limit\":50}").jsonObject)
                return@forEach
            }
            val key = if (it.startsWith("playlist_")) "playlist_id" else "video_id"
            McpDownloadTools.validate(it, Json.parseToJsonElement("{\"$key\":\"abcdefghijk\"}").jsonObject)
        }
        assertEquals(9, McpDownloadTools.names.size)
    }
    @Test fun listLimitIsBounded() {
        listOf("0", "51", "\"2\"", "null", "1.5").forEach { limit ->
            assertThrows(IllegalArgumentException::class.java) {
                McpDownloadTools.validate("downloads_list", Json.parseToJsonElement("{\"limit\":$limit}").jsonObject)
            }
        }
        McpDownloadTools.validate("downloads_list", Json.parseToJsonElement("{}").jsonObject)
    }
    @Test fun neverExposesRawFailure() {
        val secret = "https://secret/token /private/path"
        assertFalse(McpDownloadTools.safeFailure(secret).second.contains(secret))
        assertEquals("download_failed", McpDownloadTools.safeFailure(secret).first)
        assertEquals("wifi_required", McpDownloadTools.safeFailure("Downloads are set to Wi-Fi only").first)
        assertEquals("start_unavailable", McpDownloadTools.safeFailure("Downloads can't start right now").first)
    }
    @Test fun protectsSharedIdsAndFileAliases() {
        val saved = mapOf("a" to "file1", "alias" to "file1", "b" to "file2")
        assertEquals(listOf("b", "pending"), McpDownloadTools.exclusive(listOf("a", "b", "pending"), setOf("alias"), saved))
        assertEquals(emptyList<String>(), McpDownloadTools.exclusive(listOf("pending"), setOf("pending"), saved))
    }
}
