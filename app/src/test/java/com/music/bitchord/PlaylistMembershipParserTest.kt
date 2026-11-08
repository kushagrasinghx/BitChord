package com.music.bitchord

import com.music.bitchord.data.innertube.InnertubeParser
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Membership reads must retain occurrences and must never infer absence from unreadable rows. */
class PlaylistMembershipParserTest {
    private fun page(json: String) = InnertubeParser.parsePlaylistShelf(
        Json.parseToJsonElement(json), preserveDuplicates = true,
    )

    @Test
    fun `an empty playlist shelf proves absence`() {
        val result = page("""{"contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"musicPlaylistShelfRenderer":{"contents":[]}}]}}}""")
        assertNotNull(result)
        assertEquals(emptyList<String>(), result!!.songs.map { it.videoId })
        assertNull(result.continuation)
    }

    @Test
    fun `suggestions alone cannot prove playlist absence`() {
        assertNull(page("""{"continuationContents":{"sectionListContinuation":{"contents":[{"musicShelfRenderer":{"title":{"runs":[{"text":"Suggestions"}]}}}]}}}"""))
    }

    @Test
    fun `duplicate songs keep each distinct entry id`() {
        fun row(id: String) = """{"musicResponsiveListItemRenderer":{"playlistItemData":{"videoId":"song","playlistSetVideoId":"$id"},"flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Song"}]}}}]}}"""
        val result = page("""{"continuationContents":{"musicPlaylistShelfContinuation":{"contents":[${row("one")},${row("two")}]}}}""")
        assertEquals(listOf("one", "two"), result!!.songs.map { it.setVideoId })
        assertEquals(listOf("song", "song"), result.songs.map { it.videoId })
    }

    @Test
    fun `unreadable entry cannot establish absence`() {
        val json = """{"continuationContents":{"musicPlaylistShelfContinuation":{"contents":[{"musicResponsiveListItemRenderer":{"playlistItemData":{"videoId":"song"},"flexColumns":[]}}]}}}"""
        assertThrows(IllegalStateException::class.java) { page(json) }
    }

    @Test
    fun `unrecognized row type cannot establish absence`() {
        val json = """{"continuationContents":{"musicPlaylistShelfContinuation":{"contents":[{"unknownPlaylistRowRenderer":{}}]}}}"""
        assertThrows(IllegalStateException::class.java) { page(json) }
    }

    @Test
    fun `missing contents cannot masquerade as an empty playlist`() {
        val json = """{"continuationContents":{"musicPlaylistShelfContinuation":{}}}"""
        assertThrows(IllegalStateException::class.java) { page(json) }
    }

    @Test
    fun `complete shelf retains its continuation`() {
        val json = """{"continuationContents":{"musicPlaylistShelfContinuation":{"contents":[],"continuations":[{"nextContinuationData":{"continuation":"MORE"}}]}}}"""
        assertEquals("MORE", page(json)!!.continuation)
    }
}
