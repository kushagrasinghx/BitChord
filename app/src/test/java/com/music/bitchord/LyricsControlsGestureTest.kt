package com.music.bitchord

import com.music.bitchord.ui.player.shouldRevealLyricsControls
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsControlsGestureTest {
    @Test
    fun `handled lyric tap does not reveal controls`() {
        assertFalse(shouldRevealLyricsControls(dragged = false, claimed = true))
    }

    @Test
    fun `unhandled tap reveals controls`() {
        assertTrue(shouldRevealLyricsControls(dragged = false, claimed = false))
    }
}
