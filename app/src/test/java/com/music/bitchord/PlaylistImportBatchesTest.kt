package com.music.bitchord

import com.music.bitchord.ui.playlistImportBatches
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistImportBatchesTest {
    @Test
    fun aVideoMatchedTwiceIsSentOnce() {
        // Two Spotify tracks matched to "v7": sent as-is, the second batch
        // repeats a video the first already put in the playlist.
        val ids = (1..50).map { "v$it" } + "v7" + listOf("x", "y")
        val batches = playlistImportBatches(ids)
        assertEquals(listOf(50, 2), batches.map { it.size })
        assertEquals(ids.toSet(), batches.flatten().toSet())
        assertEquals(batches.flatten().size, batches.flatten().toSet().size)
    }

    @Test
    fun batchesHoldFiftyAtMost() {
        val batches = playlistImportBatches((1..120).map { "v$it" })
        assertEquals(listOf(50, 50, 20), batches.map { it.size })
    }
}
