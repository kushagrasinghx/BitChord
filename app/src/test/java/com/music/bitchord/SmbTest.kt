package com.music.bitchord

import com.music.bitchord.data.remote.RemoteArtwork
import com.music.bitchord.data.remote.RemoteSong
import com.music.bitchord.data.smb.SmbAuth
import com.music.bitchord.data.smb.SmbConfig
import com.music.bitchord.playback.SmbDataSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmbTest {

    @Test
    fun normalizesHost() {
        assertEquals("nas.local", SmbConfig.normalizeHost("nas.local"))
        assertEquals("nas.local", SmbConfig.normalizeHost("smb://nas.local/Music"))
        assertEquals("nas", SmbConfig.normalizeHost("\\\\NAS\\Music"))
        assertEquals("192.168.1.10", SmbConfig.normalizeHost("192.168.1.10:445"))
        assertEquals("", SmbConfig.normalizeHost("  "))
    }

    @Test
    fun splitsHostPort() {
        assertEquals("nas.local" to 445, SmbConfig.splitHostPort("nas.local"))
        assertEquals("nas.local" to 4445, SmbConfig.splitHostPort("nas.local:4445"))
        assertEquals("h" to 1445, SmbConfig.splitHostPort("smb://h:1445/x"))
        assertEquals("::1" to 1445, SmbConfig.splitHostPort("[::1]:1445"))
        assertEquals("::1" to 445, SmbConfig.splitHostPort("[::1]"))
        assertEquals("nas" to 445, SmbConfig.splitHostPort("nas:99999"))
        assertEquals("nas" to 445, SmbConfig.splitHostPort("nas:abc"))
    }

    @Test
    fun detectsConfiguration() {
        assertTrue(SmbConfig.isConfigured("nas.local", "Music"))
        assertFalse(SmbConfig.isConfigured("", "Music"))
        assertFalse(SmbConfig.isConfigured("nas.local", ""))
        assertFalse(SmbConfig.isConfigured("  ", "  "))
    }

    @Test
    fun detectsMediaFiles() {
        assertTrue(SmbConfig.isAudioFile("song.flac"))
        assertTrue(SmbConfig.isAudioFile("Song.MP3"))
        assertFalse(SmbConfig.isAudioFile("cover.jpg"))
        assertTrue(SmbConfig.isImageFile("cover.jpg"))
        assertTrue(SmbConfig.isImageFile("folder.PNG"))
        assertFalse(SmbConfig.isImageFile("song.mp3"))
    }

    @Test
    fun buildsStableIdsAndUrls() {
        val song = SmbConfig.songFor("NAS.local", "Music", "Artist/Album/Artist - Title.flac")
        assertEquals("smb:nas.local/Music/Artist/Album/Artist - Title.flac", song.videoId)
        assertEquals("smb://nas.local/Music/Artist/Album/Artist - Title.flac", song.localUri)
        assertEquals("Title", song.title)
        assertEquals("Artist", song.artist)
        assertEquals("Album", song.albumName)
        assertTrue(SmbConfig.isSmbId(song.videoId))
        assertFalse(SmbConfig.isSmbId("abc123"))
    }

    @Test
    fun creditsFromLibraryLayout() {
        val featured = RemoteSong.credit(
            "ONE OK ROCK/35xxxv (Deluxe Edition) (2015)/08 ONE OK ROCK feat. Tyler Carter - Decision.flac",
        )
        assertEquals("Decision", featured.title)
        assertEquals("ONE OK ROCK feat. Tyler Carter", featured.artist)
        assertEquals("35xxxv (Deluxe Edition)", featured.album)

        val numbered = RemoteSong.credit("Dance Gavin Dance/Tree City Sessions 2 (2020)/14 Strawberry's Wake.flac")
        assertEquals("Strawberry's Wake", numbered.title)
        assertEquals("Dance Gavin Dance", numbered.artist)
        assertEquals("Tree City Sessions 2", numbered.album)

        val disc = RemoteSong.credit("[Alexandros]/Where's My History! (2021)/Disc 01/02 - Song.flac")
        assertEquals("Song", disc.title)
        assertEquals("[Alexandros]", disc.artist)
        assertEquals("Where's My History!", disc.album)

        val dashed = RemoteSong.credit("Set It Off/Cinematics (2012)/16 - I'll Sleep When I'm Dead - Mira Remix.flac")
        assertEquals("I'll Sleep When I'm Dead - Mira Remix", dashed.title)
        assertEquals("Set It Off", dashed.artist)
        assertEquals("Cinematics", dashed.album)

        val loose = RemoteSong.credit("21 Guns.mp3")
        assertEquals("21 Guns", loose.title)
        assertNull(loose.artist)
        assertNull(loose.album)
    }

    @Test
    fun songFor_readsCreditsBelowBasePath() {
        val song = SmbConfig.songFor(
            "nas",
            "data",
            "media/music/Muse/Will of the People (2022)/09 - Euphoria.flac",
            basePath = "media/music",
        )
        assertEquals("Euphoria", song.title)
        assertEquals("Muse", song.artist)
        assertEquals("Will of the People", song.albumName)
        assertEquals("smb:nas/data/media/music/Muse/Will of the People (2022)/09 - Euphoria.flac", song.videoId)
    }

    @Test
    fun relativePath_stripsHostAndShare() {
        assertEquals(
            "Album/song.mp3",
            SmbConfig.relativePath("smb://nas/Music/Album/song.mp3", "Music"),
        )
        // A URL baked under another share fails loudly instead of opening a
        // same-named stranger.
        assertNull(SmbConfig.relativePath("smb://nas/Other/song.mp3", "Music"))
        assertNull(SmbConfig.relativePath("smb://nas/Music", "Music"))
        assertNull(SmbConfig.relativePath("not a url", "Music"))
    }

    @Test
    fun dataSourceDisplayPath_matchesConfig() {
        SmbAuth.share = "Music"
        try {
            val ds = SmbDataSource()
            assertEquals("Album/song.mp3", ds.displayPath("smb://nas/Music/Album/song.mp3"))
            assertNull(ds.displayPath("smb://nas/Other/song.mp3"))
        } finally {
            SmbAuth.share = ""
        }
    }

    @Test
    fun artworkPrefersConventionalCovers() {
        assertEquals(
            "smb://nas/Music/Album/folder.png",
            RemoteArtwork.pick(
                listOf(
                    "smb://nas/Music/Album/IMG_1.jpg",
                    "smb://nas/Music/Album/folder.png",
                ),
            ),
        )
        assertNull(RemoteArtwork.pick(emptyList()))
    }
}
