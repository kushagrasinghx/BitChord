package com.music.bitchord.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PartyPublishIndexTest {

    @Test
    fun aSongQueuedAgainIsTheCopyThePlayerIsOn() {
        // A played, then B, then A queued again and now playing.
        assertEquals(2, indexWithoutDeviceFiles(listOf("A", "B", "A", "C"), currentIndex = 2))
    }

    @Test
    fun deviceFilesBeforeTheCurrentItemDoNotCount() {
        assertEquals(1, indexWithoutDeviceFiles(listOf("A", "content://media/1", "B"), currentIndex = 2))
        assertEquals(-1, indexWithoutDeviceFiles(listOf("A", "file:///x.mp3"), currentIndex = 1))
    }
}
