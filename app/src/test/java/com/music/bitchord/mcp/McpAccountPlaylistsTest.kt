package com.music.bitchord.mcp

import com.music.bitchord.data.model.PlaylistPrivacy
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class McpAccountPlaylistsTest {
    private val id = "youtube:${McpPlaylistId.scope("account", "profile")}:VLPLabc"
    private fun args(vararg pairs: Pair<String, JsonElement>) = JsonObject(mapOf(*pairs))
    private fun rejects(name: String, value: JsonObject) {
        try { McpAccountPlaylists.validate(name, value); fail("accepted invalid arguments") }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun privateByDefaultAndExplicitPrivacy() {
        assertEquals(PlaylistPrivacy.PRIVATE, McpAccountPlaylists.validate("playlist_create", args("title" to JsonPrimitive(" Title "))).privacy)
        assertEquals(PlaylistPrivacy.PUBLIC, McpAccountPlaylists.validate("playlist_create", args("title" to JsonPrimitive("Title"), "privacy" to JsonPrimitive("public"))).privacy)
    }
    @Test fun titlesAndTypesAreValidated() {
        listOf(JsonPrimitive(" "), JsonPrimitive("a\nb"), JsonPrimitive("x".repeat(201)), JsonPrimitive(123), JsonNull).forEach {
            rejects("playlist_create", args("title" to it))
        }
        rejects("playlist_create", args("title" to JsonPrimitive("Title"), "privacy" to JsonPrimitive("secret")))
    }
    @Test fun accountOnlyIds() {
        listOf("local:playlist:abc", "spotify:abc", "youtube:wrong:VLabc").forEach {
            rejects("playlist_delete", args("playlist_id" to JsonPrimitive(it)))
        }
        McpAccountPlaylists.validate("playlist_delete", args("playlist_id" to JsonPrimitive(id)))
    }
    @Test fun batchBoundsAndElementTypes() {
        fun batch(values: List<JsonElement>) = args("playlist_id" to JsonPrimitive(id), "video_ids" to JsonArray(values))
        rejects("playlist_batch_add", batch(emptyList()))
        rejects("playlist_batch_add", batch(List(51) { JsonPrimitive("abcdefghijk") }))
        rejects("playlist_batch_add", batch(listOf(JsonPrimitive(123))))
        rejects("playlist_batch_add", batch(listOf(JsonPrimitive("bad"))))
        rejects("playlist_batch_add", batch(List(2) { JsonPrimitive("abcdefghijk") }))
        assertEquals(listOf("abcdefghijk"), McpAccountPlaylists.validate("playlist_batch_add", batch(listOf(JsonPrimitive("abcdefghijk")))).ids)
    }
    @Test fun reorderRequiresEntryAndRejectsSelfAnchor() {
        rejects("playlist_reorder", args("playlist_id" to JsonPrimitive(id)))
        rejects("playlist_reorder", args("playlist_id" to JsonPrimitive(id), "entry_id" to JsonPrimitive("entry"), "before_entry_id" to JsonPrimitive("entry")))
        assertNull(McpAccountPlaylists.validate("playlist_reorder", args("playlist_id" to JsonPrimitive(id), "entry_id" to JsonPrimitive("entry"))).before)
    }
}
