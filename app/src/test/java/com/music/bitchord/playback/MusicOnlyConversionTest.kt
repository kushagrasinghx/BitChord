package com.music.bitchord.playback

import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MusicOnlyConversionTest {

    private val video = Song(
        videoId = "video",
        title = "Song (Official Video)",
        artist = "Artist",
        thumbnailUrl = null,
        isVideo = true,
        queueTier = QueueTier.USER_QUEUE,
        queueEntryId = "entry",
        radioName = "Radio",
        playbackSource = "Playlist",
        playbackSourceType = PlaybackSourceType.BROWSE,
        playbackSourceId = "PL1",
    )
    private val audio = Song(videoId = "audio", title = "Song", artist = "Artist", thumbnailUrl = null)

    @Test
    fun convertedRowIsAnOrdinaryAudioRow() {
        val converted = audio.copy(isVideoOrigin = true).asMusicOnlyOf(video)

        // Both flags off is what lets Automix and the source upgrade take it.
        assertFalse(converted.isVideo)
        assertFalse(converted.isVideoOrigin)
        assertEquals("audio", converted.videoId)
        assertEquals("Song", converted.title)
    }

    @Test
    fun convertedRowKeepsTheVideosPlaceInTheQueue() {
        val converted = audio.asMusicOnlyOf(video)

        assertEquals(QueueTier.USER_QUEUE, converted.queueTier)
        assertEquals("entry", converted.queueEntryId)
        assertEquals("Radio", converted.radioName)
        assertEquals("Playlist", converted.playbackSource)
        assertEquals(PlaybackSourceType.BROWSE, converted.playbackSourceType)
        assertEquals("PL1", converted.playbackSourceId)
    }
}
