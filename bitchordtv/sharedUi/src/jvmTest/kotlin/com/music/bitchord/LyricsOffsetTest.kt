package com.music.bitchord

import com.music.bitchord.ui.player.adjustedLyricsPosition
import com.music.bitchord.ui.player.adjustedLyricsSeekTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsOffsetTest {
    @Test
    fun `positive offset delays lyrics`() {
        assertEquals(8_500L, adjustedLyricsPosition(positionMs = 10_000L, offsetMs = 1_500))
    }

    @Test
    fun `negative offset advances lyrics`() {
        assertEquals(11_500L, adjustedLyricsPosition(positionMs = 10_000L, offsetMs = -1_500))
    }

    @Test
    fun `offset never produces a negative lyric clock`() {
        assertEquals(0L, adjustedLyricsPosition(positionMs = 500L, offsetMs = 1_500))
    }

    @Test
    fun `seeking a lyric includes its offset`() {
        assertEquals(11_500L, adjustedLyricsSeekTarget(lineTimeMs = 10_000L, offsetMs = 1_500))
        assertEquals(8_500L, adjustedLyricsSeekTarget(lineTimeMs = 10_000L, offsetMs = -1_500))
    }
}
