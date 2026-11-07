package com.music.bitchord

import com.music.bitchord.data.shazam.ShazamLookupResult
import com.music.bitchord.data.shazam.parseShazamResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShazamClientTest {

    @Test
    fun `reads the top-level track into a searchable match`() {
        val result = parseShazamResponse(
            """
            {
              "matches": [],
              "track": {
                "title": "Blinding Lights",
                "subtitle": "The Weeknd",
                "images": {
                  "coverart": "https://example.com/small.jpg",
                  "coverarthq": "https://example.com/large.jpg"
                },
                "share": { "href": "https://www.shazam.com/track/1" }
              }
            }
            """.trimIndent(),
        )

        assertTrue(result is ShazamLookupResult.Found)
        val track = (result as ShazamLookupResult.Found).track
        assertEquals("Blinding Lights", track.title)
        assertEquals("The Weeknd", track.artist)
        assertEquals("https://example.com/large.jpg", track.artworkUrl)
        assertEquals("https://www.shazam.com/track/1", track.shareUrl)
        assertEquals("Blinding Lights - The Weeknd", track.searchQuery)
    }

    @Test
    fun `a response without a track is a normal no-match`() {
        assertEquals(
            ShazamLookupResult.NoMatch,
            parseShazamResponse("""{"matches":[],"tagid":"abc"}"""),
        )
    }

    @Test
    fun `a track without a title is treated as no match`() {
        assertEquals(
            ShazamLookupResult.NoMatch,
            parseShazamResponse("""{"track":{"subtitle":"The Weeknd"}}"""),
        )
    }

    @Test
    fun `a missing artist does not leave a double space in the query`() {
        val result = parseShazamResponse("""{"track":{"title":"Unknown Song"}}""")

        val track = (result as ShazamLookupResult.Found).track
        assertEquals("", track.artist)
        assertEquals("Unknown Song", track.searchQuery)
        assertNull(track.artworkUrl)
        assertNull(track.shareUrl)
    }

    @Test
    fun `malformed JSON is a failed lookup rather than a no-match`() {
        assertEquals(
            ShazamLookupResult.Failed,
            parseShazamResponse("<html>not json</html>"),
        )
    }
}
