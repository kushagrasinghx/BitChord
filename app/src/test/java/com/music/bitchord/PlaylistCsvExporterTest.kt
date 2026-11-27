package com.music.bitchord

import com.music.bitchord.data.PlaylistCsvExporter
import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistCsvExporterTest {

    @Test
    fun `buildCsv escapes commas and quotes properly`() {
        val songs = listOf(
            Song(
                videoId = "abc123",
                title = "Hello, \"World\"",
                artist = "Artist One",
                thumbnailUrl = null,
                durationText = "3:45",
                albumName = "Album, Vol. 1",
            ),
            Song(
                videoId = "local:1",
                title = "Local Track",
                artist = "Local Artist",
                thumbnailUrl = null,
            ),
        )
        val csv = PlaylistCsvExporter.buildCsv(songs)
        val lines = csv.trimEnd().lines()
        assertEquals("Title,Artist,Album,Duration,Video ID,URL", lines[0])
        assertEquals(
            "\"Hello, \"\"World\"\"\",Artist One,\"Album, Vol. 1\",3:45,abc123,https://music.youtube.com/watch?v=abc123",
            lines[1],
        )
        assertEquals(
            "Local Track,Local Artist,,,local:1,",
            lines[2],
        )
    }

    @Test
    fun `sanitizeFileName strips invalid filesystem characters`() {
        assertEquals("My_Playlist_ 2026.csv", PlaylistCsvExporter.sanitizeFileName("My/Playlist: 2026"))
        assertEquals("playlist.csv", PlaylistCsvExporter.sanitizeFileName("   "))
    }
}
