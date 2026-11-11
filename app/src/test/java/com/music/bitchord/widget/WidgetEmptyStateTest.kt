package com.music.bitchord.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetEmptyStateTest {

    @Test
    fun `empty snapshot hides the track and disables transport`() {
        val empty = MediaWidgetSnapshot.EMPTY
        assertFalse(empty.hasTrack)
        assertNull(empty.mediaId)
        assertFalse(empty.isPlaying)
        assertFalse(empty.hasPrevious)
        assertFalse(empty.hasNext)
        assertFalse(empty.isLiked)
        assertFalse(empty.canLike)
    }
}
