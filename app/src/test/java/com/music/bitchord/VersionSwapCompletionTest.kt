package com.music.bitchord

import com.music.bitchord.playback.finishVersionSwap
import org.junit.Assert.assertEquals
import org.junit.Test

class VersionSwapCompletionTest {

    @Test
    fun `a swap that paused a playing track resumes after the swap command`() {
        val calls = mutableListOf<String>()

        finishVersionSwap(
            resumeAfterResolution = true,
            swap = { calls += "swap" },
            play = { calls += "play" },
        )

        assertEquals(listOf("swap", "play"), calls)
    }

    @Test
    fun `a swap of a track that was already paused stays paused`() {
        val calls = mutableListOf<String>()

        finishVersionSwap(
            resumeAfterResolution = false,
            swap = { calls += "swap" },
            play = { calls += "play" },
        )

        assertEquals(listOf("swap"), calls)
    }
}
