package com.music.bitchord

import com.music.bitchord.data.HistorySelection
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.selectHistory
import org.junit.Assert.assertEquals
import org.junit.Test

class HistorySelectionTest {

    private fun song(id: String) = Song(
        videoId = id,
        title = id,
        artist = "Artist",
        thumbnailUrl = null,
    )

    @Test
    fun `signed-in cloud history wins over local records`() {
        val cloud = listOf(song("cloud"))

        val selection = selectHistory(true, Result.success(cloud), listOf(song("local")))

        assertEquals(HistorySelection.Songs(cloud, onDevice = false), selection)
    }

    @Test
    fun `empty cloud history falls back to local`() {
        val local = listOf(song("local"))

        val selection = selectHistory(true, Result.success(emptyList()), local)

        assertEquals(HistorySelection.Songs(local, onDevice = true), selection)
    }

    @Test
    fun `failed cloud fetch falls back to local`() {
        val local = listOf(song("local"))

        val selection = selectHistory(true, Result.failure(IllegalStateException("offline")), local)

        assertEquals(HistorySelection.Songs(local, onDevice = true), selection)
    }

    @Test
    fun `failed cloud fetch with no local records surfaces the error`() {
        val error = IllegalStateException("offline")

        val selection = selectHistory(true, Result.failure(error), emptyList())

        assertEquals(HistorySelection.Failed(error), selection)
    }

    @Test
    fun `signed-in listener with no cloud or local records is empty`() {
        val selection = selectHistory(true, Result.success(emptyList()), emptyList())

        assertEquals(HistorySelection.Empty, selection)
    }

    @Test
    fun `guest sees local records without asking the network`() {
        val local = listOf(song("local"))

        val selection = selectHistory(false, remote = null, local = local)

        assertEquals(HistorySelection.Songs(local, onDevice = true), selection)
    }

    @Test
    fun `guest with no local records is empty`() {
        val selection = selectHistory(false, remote = null, local = emptyList())

        assertEquals(HistorySelection.Empty, selection)
    }
}
