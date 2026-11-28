package com.music.bitchord.playback

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossfadeSelfTransitionTest {

    @Test
    fun `repeat-all with one item resolves next to current and must not crossfade`() {
        assertTrue(isSelfTransition(currentIndex = 0, nextIndex = 0))
    }

    @Test
    fun `repeat-all with several items has a distinct next item`() {
        assertFalse(isSelfTransition(currentIndex = 0, nextIndex = 1))
        assertFalse(isSelfTransition(currentIndex = 2, nextIndex = 0))
    }

    @Test
    fun `unset next index is not a self transition`() {
        assertFalse(isSelfTransition(currentIndex = 0, nextIndex = -1))
    }

    @Test
    fun `queue replacement and seek abandon the stale standby tail`() {
        assertTrue(shouldBailOnTransition(Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED))
        assertTrue(shouldBailOnTransition(Player.MEDIA_ITEM_TRANSITION_REASON_SEEK))
    }

    @Test
    fun `automatic advance and append are reconciled, not abandoned`() {
        assertFalse(shouldBailOnTransition(Player.MEDIA_ITEM_TRANSITION_REASON_AUTO))
        assertFalse(shouldBailOnTransition(Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT))
    }
}
