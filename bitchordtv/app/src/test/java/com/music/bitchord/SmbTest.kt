package com.music.bitchord

import com.music.bitchord.data.remote.RemoteArtwork
import com.music.bitchord.data.smb.SmbAuth
import com.music.bitchord.data.smb.SmbConfig
import com.music.bitchord.data.smb.SmbConnection
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
        val song = SmbConfig.songFor("NAS.local", "Music", "Artist/Album/Artist - Title.flac", "Album")
        assertEquals("smb:nas.local/Music/Artist/Album/Artist - Title.flac", song.videoId)
        assertEquals("smb://nas.local/Music/Artist/Album/Artist - Title.flac", song.localUri)
        assertEquals("Title", song.title)
        assertEquals("Artist", song.artist)
        assertEquals("Album", song.albumName)
        assertTrue(SmbConfig.isSmbId(song.videoId))
        assertFalse(SmbConfig.isSmbId("abc123"))
    }

    @Test
    fun blankCredentialsLogInAsGuestNotAnonymous() {
        val guest = SmbConnection.authFor("", "")
        assertTrue(guest.isGuest)
        assertFalse(guest.isAnonymous)
        assertTrue(SmbConnection.authFor("  ", "").isGuest)
    }

    @Test
    fun credentialsLogInAsThemselves() {
        val user = SmbConnection.authFor("rairulyle", "secret")
        assertEquals("rairulyle", user.username)
        assertEquals("secret", String(user.password))
        assertEquals("", user.domain)
        assertFalse(user.isGuest)
        assertFalse(user.isAnonymous)
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
