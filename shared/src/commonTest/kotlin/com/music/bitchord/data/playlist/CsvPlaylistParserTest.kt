package com.music.bitchord.data.playlist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CsvPlaylistParserTest {

    @Test
    fun parsesStandardFormatWithQuotesAndCommas() {
        val csv = """
            Title,Artist,Album,YouTube Video ID
            "Mind Over Matter (Reprise)","Young the Giant","",LpRby_Osyjc
            "Lovers Rock","TV Girl","",j_sG_Juncn8
            "Where We Are (Intel Inside) - One Direction (Lyrics Video) | Emotional & Nostalgic 💫🎤| Tiktok Song","Into You Lyrics","",1W4A9LJHzSI
            "unhappy","s0rrow","",-ryNfrDCpmI
        """.trimIndent()

        val result = CsvPlaylistParser.parse(csv, defaultTitle = "My Imported Playlist")

        assertEquals("My Imported Playlist", result.title)
        assertEquals(4, result.songs.size)

        assertEquals("LpRby_Osyjc", result.songs[0].videoId)
        assertEquals("Mind Over Matter (Reprise)", result.songs[0].title)
        assertEquals("Young the Giant", result.songs[0].artist)

        assertEquals("j_sG_Juncn8", result.songs[1].videoId)
        assertEquals("TV Girl", result.songs[1].artist)

        assertEquals("1W4A9LJHzSI", result.songs[2].videoId)
        assertTrue(result.songs[2].title.contains("One Direction"))

        assertEquals("-ryNfrDCpmI", result.songs[3].videoId)
        assertEquals("unhappy", result.songs[3].title)
        assertEquals("s0rrow", result.songs[3].artist)
    }

    @Test
    fun extractsVideoIdFromFullYouTubeUrls() {
        val csv = """
            Track Name,Artist Name,YouTube Link
            "Song 1","Artist 1",https://www.youtube.com/watch?v=dQw4w9WgXcQ
            "Song 2","Artist 2",https://youtu.be/oHg5SJYRHA0
        """.trimIndent()

        val result = CsvPlaylistParser.parse(csv)
        assertEquals(2, result.songs.size)
        assertEquals("dQw4w9WgXcQ", result.songs[0].videoId)
        assertEquals("oHg5SJYRHA0", result.songs[1].videoId)
    }

    @Test
    fun parsesCsvWithEscapedQuotes() {
        val csv = """
            Title,Artist,Video ID
            "Song with ""quotes"" in title","Artist Name",dQw4w9WgXcQ
        """.trimIndent()

        val result = CsvPlaylistParser.parse(csv)
        assertEquals(1, result.songs.size)
        assertEquals("""Song with "quotes" in title""", result.songs[0].title)
    }

    @Test
    fun handlesEmptyOrBlankCsvGracefully() {
        val result = CsvPlaylistParser.parse("", "Empty")
        assertEquals(0, result.songs.size)

        val resultWhitespace = CsvPlaylistParser.parse("   \n\n  ", "Whitespace")
        assertEquals(0, resultWhitespace.songs.size)
    }

    @Test
    fun parsesHeaderlessCsvByPositionalHeuristic() {
        val csv = """
            "Alone","Alan Walker","No Album",dQw4w9WgXcQ
        """.trimIndent()

        val result = CsvPlaylistParser.parse(csv)
        assertEquals(1, result.songs.size)
        assertEquals("Alone", result.songs[0].title)
        assertEquals("Alan Walker", result.songs[0].artist)
        assertEquals("dQw4w9WgXcQ", result.songs[0].videoId)
    }
}
