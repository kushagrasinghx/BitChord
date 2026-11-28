package com.music.bitchord.data.playlist

import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uTrackResolverTest {

    private val librarySongs = listOf(
        Song(
            videoId = "ytId1234567",
            title = "Downloaded Song",
            artist = "Artist A",
            thumbnailUrl = null,
            durationText = "3:20",
            localUri = "content://media/external/audio/media/10",
            localPath = "/storage/emulated/0/Music/BitVinyl/Downloaded Song.flac",
        ),
        Song(
            videoId = "content://media/external/audio/media/20",
            title = "Local Track",
            artist = "Artist B",
            thumbnailUrl = null,
            durationText = "4:05",
            localUri = "content://media/external/audio/media/20",
            localPath = "/storage/emulated/0/Music/Album/Local Track.mp3",
        ),
        Song(
            videoId = "content://media/external/audio/media/30",
            title = "夜に駆ける",
            artist = "YOASOBI",
            thumbnailUrl = null,
            durationText = "4:21",
            localUri = "content://media/external/audio/media/30",
            localPath = "/storage/emulated/0/Music/夜に駆ける.m4a",
        ),
    )

    private val index = M3uTrackResolver.LocalTrackIndex(librarySongs)

    @Test
    fun `resolve relative paths, absolute paths, and file URIs`() {
        val raw = """
            #EXTM3U
            #EXTINF:200,Artist A - Downloaded Song
            BitVinyl/Downloaded Song.flac
            #EXTINF:245,Artist B - Local Track
            /storage/emulated/0/Music/Album/Local Track.mp3
            #EXTINF:261,YOASOBI - 夜に駆ける
            file:///storage/emulated/0/Music/%E5%A4%9C%E3%81%AB%E9%A7%86%E3%81%91%E3%82%8B.m4a
        """.trimIndent()

        val preview = M3uTrackResolver.resolve(
            document = M3uCodec.parse(raw),
            suggestedTitle = "Test Mix",
            index = index,
            baseDir = "/storage/emulated/0/Music",
        )

        assertEquals(3, preview.totalEntries)
        assertEquals(3, preview.matchedCount)
        assertEquals(0, preview.missingCount)
        assertEquals(0, preview.unsupportedCount)
        assertEquals(0, preview.duplicateCount)
        assertEquals(
            listOf(
                "content://media/external/audio/media/10",
                "content://media/external/audio/media/20",
                "content://media/external/audio/media/30",
            ),
            preview.matchedSongs.map { it.localUri },
        )
    }

    @Test
    fun `resolve detects missing, duplicate, unsupported file types, and HTTP entries`() {
        val raw = """
            #EXTM3U
            /storage/emulated/0/Music/BitVinyl/Downloaded Song.flac
            /storage/emulated/0/Music/BitVinyl/Downloaded Song.flac
            /storage/emulated/0/Music/DoesNotExist.mp3
            /storage/emulated/0/Download/malware.apk
            https://example.com/audio.mp3
            https://music.youtube.com/watch?v=ytId1234567
            https://music.youtube.com/watch?v=dQw4w9WgXcQ
        """.trimIndent()

        val preview = M3uTrackResolver.resolve(
            document = M3uCodec.parse(raw),
            suggestedTitle = "Mixed",
            index = index,
        )

        assertEquals(7, preview.totalEntries)
        assertEquals(1, preview.matchedCount) // ytId1234567 resolved to already-matched Downloaded Song -> counted as duplicate!
        assertEquals(2, preview.duplicateCount) // repeated path + downloaded YT URL matching same localUri
        assertEquals(1, preview.missingCount)
        assertEquals("/storage/emulated/0/Music/DoesNotExist.mp3", preview.missingEntries.first().location)
        assertEquals(3, preview.unsupportedCount) // .apk, example.com, notDownloaded YT URL
        assertTrue(preview.unsupportedEntries.any { it.kind == M3uImportIssueKind.UNSUPPORTED && it.location.endsWith(".apk") })
        assertTrue(preview.unsupportedEntries.any { it.kind == M3uImportIssueKind.UNSUPPORTED && it.location.contains("example.com") })
        assertTrue(preview.unsupportedEntries.any { it.kind == M3uImportIssueKind.REMOTE_UNSUPPORTED && it.location.contains("dQw4w9WgXcQ") })
    }

    @Test
    fun `round trip export and import preserves count, ordering, identities, and metadata`() {
        val original = librarySongs.toList()
        val exported = M3uCodec.serialize(
            songs = original,
            format = M3uFormat.M3U8,
            playlistTitle = "Portable Mix",
        )
        val parsed = M3uCodec.parse(exported.content)
        val imported = M3uTrackResolver.resolve(
            document = parsed,
            suggestedTitle = "Fallback",
            index = index,
        )

        assertEquals("Portable Mix", imported.suggestedTitle)
        assertEquals(original.size, imported.matchedCount)
        assertEquals(0, imported.missingCount)
        assertEquals(0, imported.unsupportedCount)
        assertEquals(original.map { it.localUri }, imported.matchedSongs.map { it.localUri })
        assertEquals(original.map { it.title }, imported.matchedSongs.map { it.title })
        assertEquals(original.map { it.artist }, imported.matchedSongs.map { it.artist })
        assertEquals(original.map { it.durationText }, imported.matchedSongs.map { it.durationText })
    }
}
