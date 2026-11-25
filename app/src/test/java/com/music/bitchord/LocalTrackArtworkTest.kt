package com.music.bitchord

import com.music.bitchord.data.LocalMediaRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LocalTrackArtworkTest {

    @Test
    fun artwork_isReadOffTheTrackItself() {
        assertEquals(
            "content://media/external/audio/media/2787/albumart",
            LocalMediaRepository.trackArtworkUrl("content://media/external/audio/media/2787"),
        )
    }

    @Test
    fun tracksOfOneUntaggedFolder_keepTheirOwnCovers() {
        // Both of these sit in MediaStore's catch-all "Music" album; each still
        // has to point at its own picture rather than the album's one.
        assertNotEquals(
            LocalMediaRepository.trackArtworkUrl("content://media/external/audio/media/2787"),
            LocalMediaRepository.trackArtworkUrl("content://media/external/audio/media/2799"),
        )
    }
}
