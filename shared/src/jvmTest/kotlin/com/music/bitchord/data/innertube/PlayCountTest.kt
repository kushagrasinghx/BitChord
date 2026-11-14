package com.music.bitchord.data.innertube

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A track's play count, as an album page gives it a flex column of its own and
 * an artist's Top songs fold it into the subtitle. Shapes trimmed from live
 * responses.
 */
class PlayCountTest {

    private fun row(videoId: String, vararg columns: String) = """
        { "musicResponsiveListItemRenderer": {
          "playlistItemData": { "videoId": "$videoId" },
          "flexColumns": [ ${columns.joinToString(",") { column(it) }} ]
        } }"""

    /** One flex column; " • " splits it into runs, as YouTube sends a subtitle. */
    private fun column(text: String): String {
        val runs = text.split(" • ").joinToString(""", { "text": " • " }, """) { part ->
            if (part == "Artist") {
                """{ "text": "Artist", "navigationEndpoint": { "browseEndpoint": { "browseId": "UCartist",
                  "browseEndpointContextSupportedConfigs": { "browseEndpointContextMusicConfig":
                  { "pageType": "MUSIC_PAGE_TYPE_ARTIST" } } } } }"""
            } else {
                """{ "text": "$part" }"""
            }
        }
        return """{ "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ $runs ] } } }"""
    }

    private fun songs(vararg rows: String) =
        InnertubeParser.collectSongsDeep(Json.parseToJsonElement("""{ "contents": [ ${rows.joinToString(",")} ] }"""))

    @Test
    fun `an album row's own play-count column is read, and isn't taken for the artist`() {
        val song = songs(row("aaaaaaaaaaa", "Track", "", "12M plays")).single()
        assertEquals("12M plays", song.plays)
        assertEquals("Unknown artist", song.artist)
    }

    @Test
    fun `a Top songs row's count comes out of its subtitle`() {
        val song = songs(row("bbbbbbbbbbb", "Track", "Artist • 1.4B plays", "Album")).single()
        assertEquals("1.4B plays", song.plays)
        assertEquals("Artist", song.artist)
    }

    @Test
    fun `a video counts views`() {
        assertEquals("903K views", songs(row("ccccccccccc", "Clip", "Artist • 903K views")).single().plays)
    }

    @Test
    fun `a row without a tally has no play count, and an artist named with a number keeps it`() {
        assertNull(songs(row("ddddddddddd", "Track", "Artist", "Album")).single().plays)
        assertNull(songs(row("eeeeeeeeeee", "Track", "50 Cent", "Album")).single().plays)
    }

    @Test
    fun `an album listed from its backing playlist keeps the album rows' play counts`() {
        val album = songs(row("aaaaaaaaaaa", "Burn the Witch", "", "52M plays"), row("bbbbbbbbbbb", "Daydreaming", "", "81M plays"))
        val playlist = songs(row("aaaaaaaaaaa", "Burn the Witch", "Radiohead"), row("bbbbbbbbbbb", "Daydreaming", "Radiohead"))
        val merged = com.music.bitchord.data.YtMusicRepository.withPlaysFrom(album, playlist)
        assertEquals(listOf("52M plays", "81M plays"), merged.map { it.plays })
        assertEquals(listOf("Radiohead", "Radiohead"), merged.map { it.artist })
    }
}
