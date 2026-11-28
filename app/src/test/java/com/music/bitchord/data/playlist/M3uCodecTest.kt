package com.music.bitchord.data.playlist

import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class M3uCodecTest {

    private fun localSong(
        id: String,
        title: String,
        artist: String = "Artist",
        durationText: String? = "3:33",
        localPath: String? = "/storage/emulated/0/Music/BitVinyl/$id.mp3",
        localUri: String? = "content://media/external/audio/media/$id",
    ) = Song(
        videoId = id,
        title = title,
        artist = artist,
        thumbnailUrl = null,
        durationText = durationText,
        localUri = localUri,
        localPath = localPath,
    )

    @Test
    fun `export empty playlist emits header only`() {
        val result = M3uCodec.serialize(emptyList(), M3uFormat.M3U8)
        assertEquals("#EXTM3U\n", result.content)
        assertEquals(0, result.exportedCount)
        assertEquals(0, result.localCount)
        assertEquals(0, result.remoteCount)
    }

    @Test
    fun `export single track playlist preserves metadata and path`() {
        val song = localSong(
            id = "101",
            title = "Track Name",
            artist = "Artist Name",
            durationText = "3:33",
            localPath = "/storage/emulated/0/Music/BitVinyl/track01.mp3",
        )
        val result = M3uCodec.serialize(listOf(song), M3uFormat.M3U8)
        assertEquals(
            "#EXTM3U\n#EXTINF:213,Artist Name - Track Name\n/storage/emulated/0/Music/BitVinyl/track01.mp3\n",
            result.content,
        )
        assertEquals(1, result.exportedCount)
        assertEquals(1, result.localCount)
    }

    @Test
    fun `export multiple tracks preserves exact playlist order`() {
        val songs = listOf(
            localSong("1", "First", "A", "1:00", "/Music/first.mp3"),
            localSong("2", "Second", "B", "2:00", "/Music/second.mp3"),
            localSong("3", "Third", "C", "3:00", "/Music/third.mp3"),
        )
        val originalSnapshot = songs.toList()
        val result = M3uCodec.serialize(songs, M3uFormat.M3U)
        val parsed = M3uCodec.parse(result.content)
        assertEquals(listOf("/Music/first.mp3", "/Music/second.mp3", "/Music/third.mp3"), parsed.entries.map { it.location })
        assertEquals(listOf(60, 120, 180), parsed.entries.map { it.durationSeconds })
        // Original list remains completely unchanged
        assertEquals(originalSnapshot, songs)
    }

    @Test
    fun `export handles missing metadata gracefully`() {
        val song = Song(
            videoId = "content://media/external/audio/media/5",
            title = "",
            artist = "Unknown Artist",
            thumbnailUrl = null,
            durationText = null,
            localUri = "content://media/external/audio/media/5",
            localPath = "/storage/emulated/0/Music/mystery_track.flac",
        )
        val result = M3uCodec.serialize(listOf(song), M3uFormat.M3U8)
        assertTrue(result.content.contains("#EXTINF:-1,mystery_track\n"))
        assertTrue(result.content.contains("/storage/emulated/0/Music/mystery_track.flac\n"))
    }

    @Test
    fun `export handles unicode filenames, titles, and spaces in M3U8 UTF-8`() {
        val song = localSong(
            id = "99",
            title = "夜に駆ける (Remix)",
            artist = "YOASOBI & 친구",
            durationText = "4:21",
            localPath = "/storage/emulated/0/Music/BitVinyl/夜に駆ける - Track 01.flac",
        )
        val result = M3uCodec.serialize(listOf(song), M3uFormat.M3U8)
        val utf8Bytes = result.content.toByteArray(StandardCharsets.UTF_8)
        val decoded = String(utf8Bytes, StandardCharsets.UTF_8)
        assertTrue(decoded.contains("#EXTINF:261,YOASOBI & 친구 - 夜に駆ける (Remix)\n"))
        assertTrue(decoded.contains("/storage/emulated/0/Music/BitVinyl/夜に駆ける - Track 01.flac\n"))
    }

    @Test
    fun `export tracks missing local files without corrupting playlist`() {
        val songs = listOf(
            localSong("1", "Present", localPath = "/Music/present.mp3"),
            localSong("2", "Missing", localPath = "/Music/missing.mp3"),
        )
        val result = M3uCodec.serialize(
            songs = songs,
            format = M3uFormat.M3U8,
            isLocalMissing = { it.videoId == "2" },
        )
        assertEquals(2, result.exportedCount)
        assertEquals(1, result.missingLocalCount)
        assertTrue(result.content.contains("/Music/missing.mp3"))
    }

    @Test
    fun `export YouTube Music track uses canonical watch URL and never leaks tokens`() {
        val ytSong = Song(
            videoId = "dQw4w9WgXcQ",
            title = "Never Gonna Give You Up",
            artist = "Rick Astley",
            thumbnailUrl = "https://example.com/thumb.jpg",
            durationText = "3:33",
        )
        val result = M3uCodec.serialize(listOf(ytSong), M3uFormat.M3U8)
        assertEquals(1, result.remoteCount)
        assertEquals(0, result.localCount)
        assertTrue(result.content.contains("https://music.youtube.com/watch?v=dQw4w9WgXcQ\n"))
    }

    @Test
    fun `parse valid M3U8 with UTF-8 BOM, CRLF, and PLAYLIST directive`() {
        val raw = "\uFEFF#EXTM3U\r\n" +
            "#PLAYLIST:My Mix\r\n" +
            "#EXTINF:213,Artist Name - Track Name\r\n" +
            "/storage/emulated/0/Music/BitVinyl/track 01.mp3\r\n" +
            "#EXTINF:185,東京事変 - 群青日和\r\n" +
            "file:///storage/emulated/0/Music/%E6%9D%B1%E4%BA%AC.flac\r\n"

        val doc = M3uCodec.parse(raw)
        assertTrue(doc.hasExtM3uHeader)
        assertEquals("My Mix", doc.playlistTitle)
        assertEquals(2, doc.entries.size)
        assertEquals(M3uEntryKind.ABSOLUTE_PATH, doc.entries[0].kind)
        assertEquals("Artist Name", doc.entries[0].artist)
        assertEquals("Track Name", doc.entries[0].title)
        assertEquals(213, doc.entries[0].durationSeconds)
        assertEquals(M3uEntryKind.FILE_URI, doc.entries[1].kind)
        assertEquals("東京事変", doc.entries[1].artist)
        assertEquals("群青日和", doc.entries[1].title)
    }

    @Test
    fun `parse handles malformed lines gracefully`() {
        val raw = """
            #NOT_EXTM3U
            #EXTINF:not_a_number_no_comma
            relative/path/song.mp3
            #EXTINF:123.7,Single Title Without Dash
            https://example.com/stream.mp3
            # random comment

            /abs/path/with spaces/track.ogg
        """.trimIndent()

        val doc = M3uCodec.parse(raw)
        assertFalse(doc.hasExtM3uHeader)
        assertEquals(3, doc.entries.size)
        assertNull(doc.entries[0].durationSeconds)
        assertEquals(M3uEntryKind.RELATIVE_PATH, doc.entries[0].kind)
        assertEquals(123, doc.entries[1].durationSeconds)
        assertEquals("Single Title Without Dash", doc.entries[1].title)
        assertEquals(M3uEntryKind.HTTP_URL, doc.entries[1].kind)
        assertEquals(M3uEntryKind.ABSOLUTE_PATH, doc.entries[2].kind)
    }
}
