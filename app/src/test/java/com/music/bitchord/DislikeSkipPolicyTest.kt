package com.music.bitchord

import com.music.bitchord.data.model.LikeStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DislikeSkipPolicyTest {

    @Test
    fun `indifferent current track skips`() {
        assertTrue(shouldSkipAfterDislike(LikeStatus.INDIFFERENT, "target", "target"))
    }

    @Test
    fun `liked current track skips`() {
        assertTrue(shouldSkipAfterDislike(LikeStatus.LIKE, "target", "target"))
    }

    @Test
    fun `undoing dislike on current track does not skip`() {
        assertFalse(shouldSkipAfterDislike(LikeStatus.DISLIKE, "target", "target"))
    }

    @Test
    fun `indifferent non-current track does not skip`() {
        assertFalse(shouldSkipAfterDislike(LikeStatus.INDIFFERENT, "target", "current"))
    }

    @Test
    fun `liked non-current track does not skip`() {
        assertFalse(shouldSkipAfterDislike(LikeStatus.LIKE, "target", "current"))
    }

    @Test
    fun `missing current track does not skip`() {
        assertFalse(shouldSkipAfterDislike(LikeStatus.INDIFFERENT, "target", null))
    }
}
