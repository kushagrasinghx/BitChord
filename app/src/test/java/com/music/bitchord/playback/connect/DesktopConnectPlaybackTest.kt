package com.music.bitchord.playback.connect

import androidx.media3.common.Player
import com.music.bitchord.data.connect.ConnectProtocol
import com.music.bitchord.data.connect.ConnectTrack
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.connect.DesktopConnectPlayback.Companion.isLocalOnlySong
import com.music.bitchord.playback.connect.DesktopConnectPlayback.Companion.toAndroidSong
import com.music.bitchord.playback.connect.DesktopConnectPlayback.Companion.toConnectTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopConnectPlaybackTest {

    @Test
    fun trackConversionRoundTripsMetadata() {
        val song = Song(
            videoId = "dQw4w9WgXcQ",
            title = "Never Gonna Give You Up",
            artist = "Rick Astley",
            albumName = "Whenever You Need Somebody",
            thumbnailUrl = "https://example.com/art.jpg",
            durationText = "3:33",
            isVideo = true,
            queueTier = QueueTier.AUTOPLAY,
        )
        val wire = song.toConnectTrack()
        assertEquals("dQw4w9WgXcQ", wire.videoId)
        assertEquals(213_000L, wire.durationMs)
        assertTrue(wire.isVideo)
        assertTrue(wire.fromAutoplay)

        val restored = wire.toAndroidSong()
        assertEquals(song.videoId, restored.videoId)
        assertEquals(song.title, restored.title)
        assertEquals(song.artist, restored.artist)
        assertEquals("3:33", restored.durationText)
        assertEquals(QueueTier.AUTOPLAY, restored.queueTier)
    }

    @Test
    fun localOnlySongsAreDetected() {
        val localFile = Song(
            videoId = "local:1234",
            title = "Voice Memo",
            artist = "Unknown",
            thumbnailUrl = null,
            localUri = "content://media/external/audio/media/1234",
        )
        val downloadedYoutube = Song(
            videoId = "dQw4w9WgXcQ",
            title = "Downloaded Song",
            artist = "Artist",
            thumbnailUrl = null,
            localPath = "/data/downloads/dQw4w9WgXcQ.m4a",
        )
        assertTrue(isLocalOnlySong(localFile))
        assertFalse(isLocalOnlySong(downloadedYoutube))
    }

    @Test
    fun repeatModeMapsToWireProtocol() {
        assertEquals(ConnectProtocol.REPEAT_OFF, DesktopConnectPlayback.toWireRepeat(Player.REPEAT_MODE_OFF))
        assertEquals(ConnectProtocol.REPEAT_ONE, DesktopConnectPlayback.toWireRepeat(Player.REPEAT_MODE_ONE))
        assertEquals(ConnectProtocol.REPEAT_ALL, DesktopConnectPlayback.toWireRepeat(Player.REPEAT_MODE_ALL))
    }
}
