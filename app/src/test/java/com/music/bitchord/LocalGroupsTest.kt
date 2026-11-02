package com.music.bitchord

import com.music.bitchord.data.LocalGroups
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.Song
import com.music.bitchord.download.DownloadedCollection
import com.music.bitchord.download.Downloads
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalGroupsTest {

    private fun song(id: String, title: String, artist: String = "Artist", album: String? = "Album", track: Int? = null) =
        Song(videoId = id, title = title, artist = artist, thumbnailUrl = null, albumName = album, trackNumber = track)

    @Test
    fun pageIds_roundTripNamesWithColons() {
        val id = LocalGroups.albumPageId("local:downloads", "Live: At The Hall")
        assertEquals("local:downloads", LocalGroups.folderOf(id))
        assertEquals(BrowseType.ALBUM, LocalGroups.typeOf(id))
        assertFalse(LocalGroups.isArtist(id))

        val artist = LocalGroups.artistPageId("local:all", "AC/DC")
        assertEquals("local:all", LocalGroups.folderOf(artist))
        assertEquals(BrowseType.OTHER, LocalGroups.typeOf(artist))
        assertTrue(LocalGroups.isArtist(artist))
    }

    @Test
    fun folderOf_ignoresEverythingElse() {
        assertNull(LocalGroups.folderOf("local:downloads"))
        assertNull(LocalGroups.folderOf(Downloads.pageIdFor("MPREb_x")))
        assertNull(LocalGroups.folderOf("local:group:bogus:all:x"))
        assertNull(LocalGroups.folderOf(null))
    }

    @Test
    fun songsOf_rebuildsTheGroupInReleaseOrder() {
        val folder = listOf(
            song("b", "B", album = "Live: At The Hall", track = 2),
            song("x", "X", album = "Other"),
            song("a", "A", album = "Live: At The Hall", track = 1),
        )
        val id = LocalGroups.albumPageId("local:all", "Live: At The Hall")
        assertEquals(listOf("a", "b"), LocalGroups.songsOf(id, folder).map { it.videoId })
    }

    @Test
    fun albumsOf_namedReleaseWinsOverItsOwnTags() {
        val tracks = listOf(song("a", "A", album = "Record"), song("b", "B", album = "Tagged"))
        val downloaded = DownloadedCollection(
            id = "MPREb_1",
            title = "record",
            subtitle = "",
            thumbnailUrl = "art",
            playlist = false,
            songs = listOf(tracks[0]),
        )
        val albums = LocalGroups.albumsOf("local:downloads", tracks, listOf(downloaded))
        assertEquals(listOf("record", "Tagged"), albums.map { it.title })
        assertEquals(Downloads.pageIdFor("MPREb_1"), albums[0].browseId)
        assertEquals("MPREb_1", albums[0].downloadId)
        assertEquals("Artist", albums[0].subtitle)
        assertNull(albums[1].downloadId)
    }

    private fun file(id: String, title: String, path: String?) =
        Song(videoId = id, title = title, artist = "Artist", thumbnailUrl = null, localUri = "content://$id", localPath = path)

    @Test
    fun directoryPageIds_roundTripPaths() {
        val id = LocalGroups.directoryPageId("local:all", "/storage/emulated/0/Music/Live: 2019")
        assertEquals("local:all", LocalGroups.folderOf(id))
        assertEquals(BrowseType.PLAYLIST, LocalGroups.typeOf(id))
        assertTrue(LocalGroups.isDirectory(id))
        assertFalse(LocalGroups.isArtist(id))
        assertFalse(LocalGroups.isDirectory(LocalGroups.albumPageId("local:all", "Live: 2019")))
    }

    @Test
    fun foldersOf_groupsByDirectoryAndSkipsPathless() {
        val tracks = listOf(
            file("b", "B", "/storage/emulated/0/Music/Rock/b.mp3"),
            file("a", "A", "/storage/emulated/0/Music/Rock/a.mp3"),
            file("c", "C", "/storage/1A2B-3C4D/Music/c.flac"),
            file("d", "D", "/storage/emulated/0/Download/d.mp3"),
            file("x", "X", null),
        )
        val folders = LocalGroups.foldersOf("local:all", tracks)
        assertEquals(listOf("Download", "Music", "Rock"), folders.map { it.title })
        assertEquals(listOf("", "1A2B-3C4D", "Music"), folders.map { it.subtitle })
        val rock = folders.last()
        assertEquals(listOf("a", "b"), rock.songs.map { it.videoId })
        // The page reads the same rows back off a fresh listing.
        assertEquals(listOf("a", "b"), LocalGroups.songsOf(rock.browseId, tracks).map { it.videoId })
    }
}
