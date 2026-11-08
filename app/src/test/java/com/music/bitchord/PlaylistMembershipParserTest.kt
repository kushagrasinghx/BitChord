package com.music.bitchord

import com.music.bitchord.data.innertube.InnertubeParser
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Membership reads must retain occurrences and must never infer absence from unreadable rows. */
class PlaylistMembershipParserTest {
    private fun page(json: String) = InnertubeParser.parsePlaylistShelf(
        Json.parseToJsonElement(json), preserveDuplicates = true,
    )

    private fun row(index: Int, videoId: String = "song$index") =
        """{"musicResponsiveListItemRenderer":{"playlistItemData":{"videoId":"$videoId","playlistSetVideoId":"entry$index"},"flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Track $index"}]}}}]}}"""

    private fun next(token: String) =
        """{"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"$token"}}}}"""

    private fun initial(rows: List<String>, token: String? = null) =
        """{"contents":{"twoColumnBrowseResultsRenderer":{"secondaryContents":{"sectionListRenderer":{"contents":[{"musicPlaylistShelfRenderer":{"contents":[${(rows + listOfNotNull(token?.let(::next))).joinToString(",")}]}}]}}}}}"""

    private fun appended(rows: List<String>, token: String? = null) =
        """{"onResponseReceivedActions":[{"appendContinuationItemsAction":{"continuationItems":[${(rows + listOfNotNull(token?.let(::next))).joinToString(",")}]}}]}"""

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

    @Test
    fun `boundary-sized playlists retain every entry across appended pages`() {
        for (size in listOf(0, 4, 43, 100, 101, 113, 253, 289)) {
            val ids = mutableListOf<String>()
            val pages = (0 until size).chunked(100).ifEmpty { listOf(emptyList()) }
            pages.forEachIndexed { index, positions ->
                val more = if (index < pages.lastIndex) "page${index + 1}" else null
                val json = if (index == 0) initial(positions.map(::row), more)
                    else appended(positions.map(::row), more)
                val parsed = page(json)!!
                ids += parsed.songs.map { it.videoId }
                assertEquals(more, parsed.continuation)
            }
            assertEquals("size=$size", (0 until size).map { "song$it" }, ids)
        }
    }

    @Test
    fun `append continuation retains distinct occurrences and subsequent token`() {
        val result = page(appended(listOf(row(101, "same"), row(102, "same")), "THIRD"))!!
        assertEquals(listOf("entry101", "entry102"), result.songs.map { it.setVideoId })
        assertEquals("THIRD", result.continuation)
    }

    @Test
    fun `empty appended page may still continue`() {
        val result = page(appended(emptyList(), "NEXT"))!!
        assertTrue(result.songs.isEmpty())
        assertEquals("NEXT", result.continuation)
    }

    @Test
    fun `voting command does not replace browse continuation`() {
        val continuation = """{"continuationItemRenderer":{"continuationEndpoint":{"commandExecutorCommand":{"commands":[{"continuationCommand":{"request":"CONTINUATION_REQUEST_TYPE_PLAYLIST_VOTING","token":"VOTE"}},{"continuationCommand":{"request":"CONTINUATION_REQUEST_TYPE_BROWSE","token":"NEXT"}}]}}}}"""
        assertEquals("NEXT", page(appended(listOf(row(101), continuation)))!!.continuation)
    }

    @Test
    fun `legacy shelf continuation remains supported`() {
        val json = """{"continuationContents":{"musicPlaylistShelfContinuation":{"contents":[${row(101)},${next("NEXT")}]}}}"""
        assertEquals("NEXT", page(json)!!.continuation)
    }

    @Test
    fun `unreadable appended entries and missing actions cannot prove absence`() {
        assertThrows(IllegalStateException::class.java) {
            page(appended(listOf("""{"unknownRenderer":{}}""")))
        }
        assertNull(page("""{"onResponseReceivedActions":[{"otherAction":{}}]}"""))
        assertNull(page("""{"onResponseReceivedActions":[{"appendContinuationItemsAction":{}}]}"""))
    }
}
