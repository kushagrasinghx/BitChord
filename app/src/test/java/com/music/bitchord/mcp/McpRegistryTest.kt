package com.music.bitchord.mcp

import org.junit.Assert.*
import org.junit.Test

class McpRegistryTest {
    @Test fun allUtilityAndDownloadNamesAreDiscoverable() {
        val names = McpTools.registeredNames()
        assertTrue(names.containsAll(McpUtilities.fields.keys))
        assertTrue(names.containsAll(McpDownloadTools.names))
        assertTrue(names.containsAll(setOf("catalog_search", "catalog_read", "playlist_create", "playlist_rename", "playlist_delete", "playlist_batch_add", "playlist_batch_remove", "playlist_reorder")))
        assertTrue(McpSettings.permissions.containsAll(McpTools.registeredPermissions()))
    }
}
