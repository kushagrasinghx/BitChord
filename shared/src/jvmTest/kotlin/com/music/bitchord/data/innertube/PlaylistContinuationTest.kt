package com.music.bitchord.data.innertube

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * A playlist's second page as YouTube Music sends it now: rows appended by an
 * `onResponseReceivedActions` action rather than under `continuationContents`.
 * Shape trimmed from a live response for a 200-song playlist (#947).
 */
class PlaylistContinuationTest {

    private fun row(videoId: String, setVideoId: String) = """
        { "musicResponsiveListItemRenderer": {
          "playlistItemData": { "videoId": "$videoId", "playlistSetVideoId": "$setVideoId" },
          "flexColumns": [
            { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ { "text": "Song $videoId" } ] } } },
            { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ { "text": "Artist" } ] } } }
          ]
        } }"""

    private val appendedPage = Json.parseToJsonElement(
        """
        { "onResponseReceivedActions": [ { "appendContinuationItemsAction": { "continuationItems": [
          ${row("aaaaaaaaaaa", "SET1")},
          ${row("bbbbbbbbbbb", "SET2")},
          { "continuationItemRenderer": { "continuationEndpoint": { "continuationCommand": { "token": "PAGE3" } } } }
        ] } } ] }
        """,
    )

    @Test
    fun `an appended continuation page reads as the playlist's next rows`() {
        val page = assertNotNull(InnertubeParser.parsePlaylistShelf(appendedPage))
        assertEquals(listOf("SET1", "SET2"), page.songs.map { it.setVideoId })
        assertEquals("PAGE3", page.continuation)
    }
}
