package com.music.bitchord.alarm

import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.playback.allowsAutoplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmSongRequestTest {

    @Test
    fun `song identity and metadata map to canonical BitChord model`() {
        val selection = alarmSong()
        val song = requireNotNull(AlarmSongRequest.toSong(selection))

        assertEquals(selection.videoId, song.videoId)
        assertEquals(selection.title, song.title)
        assertEquals(selection.artist, song.artist)
        assertEquals(selection.artworkUrl, song.thumbnailUrl)
        assertEquals(selection.durationText, song.durationText)
        assertEquals(PlaybackSourceType.ALARM, song.playbackSourceType)
    }

    @Test
    fun `alarm queue contains only the selected song`() {
        val queue = AlarmSongRequest.queue(alarmSong())
        assertEquals(1, queue.size)
        assertEquals("video123", queue.single().videoId)
        assertFalse(queue.single().playbackSourceType.allowsAutoplay())
    }

    @Test
    fun `missing or invalid selection produces no playable queue`() {
        assertTrue(AlarmSongRequest.queue(null).isEmpty())
        assertTrue(AlarmSongRequest.queue(alarmSong().copy(videoId = " ")).isEmpty())
        assertTrue(AlarmSongRequest.queue(alarmSong().copy(title = "")).isEmpty())
    }

    private fun alarmSong() = AlarmSong(
        videoId = "video123",
        title = "Wake up",
        artist = "BitChord Artist",
        artworkUrl = "https://example.invalid/art",
        durationText = "3:42",
    )
}
