package com.music.bitchord.playback.smart

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpokenOrLiveTest {
    @Test
    fun ordinarySongsWithLiveInTheTitleStillMix() {
        assertFalse(isSpokenOrLiveForTest("Live It Up (feat. Pitbull)", "Nicky Jam, Will Smith & Era Istrefi"))
        assertFalse(isSpokenOrLiveForTest("Live Your Life", "T.I."))
        assertFalse(isSpokenOrLiveForTest("Live While We're Young", "One Direction"))
        assertFalse(isSpokenOrLiveForTest("Live Forever", "Oasis", "Definitely Maybe"))
        assertFalse(isSpokenOrLiveForTest("Lightning Crashes", "Live", "Throwing Copper"))
        assertFalse(isSpokenOrLiveForTest("Performance", "Some Artist"))
    }

    @Test
    fun labelledLiveRecordingsAreLeftAlone() {
        assertTrue(isSpokenOrLiveForTest("Bohemian Rhapsody (Live)", "Queen"))
        assertTrue(isSpokenOrLiveForTest("Creep [Live]", "Radiohead"))
        assertTrue(isSpokenOrLiveForTest("Yellow (Live at Glastonbury 2016)", "Coldplay"))
        assertTrue(isSpokenOrLiveForTest("Song (Acoustic Live)", "Artist"))
        assertTrue(isSpokenOrLiveForTest("Hello - Live", "Adele"))
        assertTrue(isSpokenOrLiveForTest("Hello – Live from the Royal Albert Hall", "Adele"))
        assertTrue(isSpokenOrLiveForTest("Song", "Artist", "Live at Wembley"))
        assertTrue(isSpokenOrLiveForTest("Song", "Artist", "Live: At The Hall"))
    }

    @Test
    fun spokenWordIsLeftAlone() {
        assertTrue(isSpokenOrLiveForTest("Episode 12: Interview", "Some Podcast"))
        assertTrue(isSpokenOrLiveForTest("Chapter 1", "Narrator", "The Hobbit (Audiobook)"))
    }
}
