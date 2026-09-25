package com.music.bitchord

import com.music.bitchord.data.remote.RemoteSong
import com.music.bitchord.data.webdav.WebDavConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteSongTest {

    private fun credit(path: String) = RemoteSong.credit(path).let { Triple(it.title, it.artist, it.album) }

    @Test
    fun discFoldersBelongToTheAlbum() {
        assertEquals(Triple("T", "A", "B"), credit("A/B (2001)/CD2/03 - T.flac"))
        assertEquals(Triple("T", "A", "B"), credit("A/B/Disc 1/03 - T.flac"))
        assertEquals(Triple("T", "A", "B"), credit("A/B/disk 01/03 - T.flac"))
    }

    @Test
    fun albumDropsItsYearInParenthesesOrBrackets() {
        assertEquals("B", RemoteSong.credit("A/B [2020]/01 - T.flac").album)
        assertEquals("Live at Wembley", RemoteSong.credit("Live at Wembley (2007)/01 - T.flac").album)
        assertEquals("Luxury Disease (Japanese version)", RemoteSong.credit("A/Luxury Disease (Japanese version) (2022)/01 - T.flac").album)
    }

    @Test
    fun trackNumbersComeOffTheTitle() {
        assertEquals("T", RemoteSong.credit("A/B/1-01 - T.flac").title)
        assertEquals("Title", RemoteSong.credit("A/B/03.Title.mp3").title)
        assertEquals("Title", RemoteSong.credit("A/B/03_Title.mp3").title)
        assertEquals("Title", RemoteSong.credit("A/B/14 Title.mp3").title)
    }

    @Test
    fun looseNumberIsOnlyATrackInsideAnAlbum() {
        assertEquals("21 Guns", RemoteSong.credit("21 Guns.mp3").title)
        assertEquals("Title", RemoteSong.credit("A/B/14 Title.mp3").title)
    }

    @Test
    fun folderArtistWhenTheFileNamesNone() {
        assertEquals(Triple("T", "A", "B"), credit("A/B/05 - T.mp3"))
        assertEquals(Triple("Title", "A", "B"), credit("A/B/Title"))
    }

    @Test
    fun onlyTheTwoFoldersAboveTheFileCount() {
        assertEquals(Triple("T", "A", "B"), credit("music/lossless/A/B/01 - T.flac"))
    }

    @Test
    fun fileNameCreditsWinOnlyWhenTheyAgreeWithTheFolder() {
        assertEquals(Triple("T", "A feat. C", "B"), credit("A/B/01 A feat. C - T.flac"))
        assertEquals(Triple("Some - Dashed Title", "A", "B"), credit("A/B/01 - Some - Dashed Title.flac"))
        assertEquals(Triple("T", "A", null), credit("A - T.mp3"))
    }

    @Test
    fun nothingToReadLeavesTheFileName() {
        val bare = RemoteSong.credit("track.flac")
        assertEquals("track", bare.title)
        assertNull(bare.artist)
        assertNull(bare.album)
    }

    @Test
    fun webDavPathsAreReadBelowTheBaseUrlWhateverItsCase() {
        assertEquals(
            "A/B/01 - T.flac",
            WebDavConfig.libraryPath("https://h.example/Files/Music/A/B/01%20-%20T.flac", "https://h.example/files/music"),
        )
        assertEquals("My Song.mp3", WebDavConfig.libraryPath("https://h.example/Music/My Song.mp3", "https://h.example/Music"))
        assertEquals("Music/T.mp3", WebDavConfig.libraryPath("https://h.example/Music/T.mp3", ""))
    }

    @Test
    fun webDavDisplayNameReplacesTheUrlSegment() {
        val song = WebDavConfig.songFor(
            "https://h.example/Music/A/B/01%20-%20T.flac",
            baseUrl = "https://h.example/Music",
            displayName = "01 - Proper Title.flac",
        )
        assertEquals("Proper Title", song.title)
        assertEquals("A", song.artist)
        assertEquals("B", song.albumName)
    }
}
