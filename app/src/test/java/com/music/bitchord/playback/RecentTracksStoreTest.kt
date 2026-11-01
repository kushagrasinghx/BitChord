package com.music.bitchord.playback

import com.music.bitchord.data.RecentTracksStore
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RecentTracksStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun setUp() {
        RecentTracksStore.initForTesting(tempFolder.root)
    }

    @Test
    fun testRecordPlayAndLoad() = runBlocking {
        val song1 = Song(
            videoId = "vid_1",
            title = "Starboy",
            artist = "The Weeknd",
            thumbnailUrl = "https://example.com/art1.jpg",
            durationText = "3:50",
        )
        val song2 = Song(
            videoId = "vid_2",
            title = "Blinding Lights",
            artist = "The Weeknd",
            thumbnailUrl = "https://example.com/art2.jpg",
            durationText = "3:20",
        )

        RecentTracksStore.recordPlaySynchronous(null, song1)
        RecentTracksStore.recordPlaySynchronous(null, song2)

        val recent = RecentTracksStore.getRecentSongs(null)
        assertEquals(2, recent.size)
        assertEquals("vid_2", recent[0].videoId)
        assertEquals("vid_1", recent[1].videoId)

        // Play song1 again -> should move to index 0 without duplicates
        RecentTracksStore.recordPlaySynchronous(null, song1)

        val updated = RecentTracksStore.getRecentSongs(null)
        assertEquals(2, updated.size)
        assertEquals("vid_1", updated[0].videoId)
        assertEquals("vid_2", updated[1].videoId)

        // Test conversion to HomeShelf
        val shelf = RecentTracksStore.asHomeShelf(null)
        assertNotNull(shelf)
        assertEquals("Recents", shelf?.title)
        assertEquals(2, shelf?.items?.size)
        assertEquals("Starboy", shelf?.items?.get(0)?.title)
        assertTrue(shelf?.items?.get(0)?.subtitle?.contains("The Weeknd") == true)
    }

    @Test
    fun testTrimMaxTracks() = runBlocking {
        for (i in 1..35) {
            val song = Song(
                videoId = "vid_$i",
                title = "Song $i",
                artist = "Artist $i",
                thumbnailUrl = null,
                fromAutoplay = false,
            )
            RecentTracksStore.recordPlaySynchronous(null, song)
        }

        val recent = RecentTracksStore.getRecentSongs(null)
        assertEquals(30, recent.size)
        assertEquals("vid_35", recent[0].videoId)
        assertEquals("vid_6", recent[29].videoId)
    }
}
