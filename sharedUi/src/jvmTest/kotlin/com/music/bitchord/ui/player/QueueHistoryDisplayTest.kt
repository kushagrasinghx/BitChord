package com.music.bitchord.ui.player

import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueHistoryDisplayTest {
    private fun song(id: String, tier: QueueTier = QueueTier.CONTEXT) =
        Song(videoId = id, title = id, artist = "Artist", thumbnailUrl = null, queueTier = tier)

    @Test
    fun `history is hidden by default without changing upcoming sections`() {
        val queue = listOf(song("old"), song("current"), song("manual", QueueTier.USER_QUEUE),
            song("album"), song("radio", QueueTier.AUTOPLAY))
        val hidden = splitQueue(queue, 1)
        val visible = splitQueue(queue, 1, showHistory = true)

        assertTrue(hidden.history.isEmpty())
        assertEquals(listOf(0), visible.history.map { it.timelineIndex })
        assertEquals(hidden.nowPlaying, visible.nowPlaying)
        assertEquals(hidden.user, visible.user)
        assertEquals(hidden.context, visible.context)
        assertEquals(hidden.autoplay, visible.autoplay)
    }

    @Test
    fun `advancing adds the old current song and going back removes future history`() {
        val queue = listOf(song("a"), song("b"), song("c"))
        assertEquals(listOf("a", "b"), splitQueue(queue, 2, true).history.map { it.song.videoId })
        val back = splitQueue(queue, 1, true)
        assertEquals(listOf("a"), back.history.map { it.song.videoId })
        assertEquals("b", back.nowPlaying?.song?.videoId)
        assertEquals(listOf(2), back.context.map { it.timelineIndex })
    }

    @Test
    fun `duplicates have distinct keys and retain their playback indices`() {
        val queue = List(4) { song("same") }
        val tracks = splitQueue(queue, 2, true)
        val rows = tracks.history + listOfNotNull(tracks.nowPlaying) + tracks.context
        assertEquals(listOf(0, 1, 2, 3), rows.map { it.timelineIndex })
        assertEquals(rows.size, rows.map { it.key }.toSet().size)
    }

    @Test
    fun `trimmed history uses the new timeline indices`() {
        val queue = List(28) { song("$it") }
        val tracks = splitQueue(queue.drop(2), 25, true)
        assertEquals(25, tracks.history.size)
        assertEquals("2", tracks.history.first().song.videoId)
        assertEquals(0, tracks.history.first().timelineIndex)
        assertEquals("27", tracks.nowPlaying?.song?.videoId)
    }

    @Test
    fun `empty invalid and first-song queues have no history`() {
        assertTrue(splitQueue(emptyList(), 0, true).history.isEmpty())
        assertTrue(splitQueue(listOf(song("a")), -1, true).history.isEmpty())
        assertTrue(splitQueue(listOf(song("a")), 1, true).history.isEmpty())
        assertTrue(splitQueue(listOf(song("a")), 0, true).history.isEmpty())
    }
}
