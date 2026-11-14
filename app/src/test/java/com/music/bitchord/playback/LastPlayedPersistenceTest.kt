package com.music.bitchord.playback

import com.music.bitchord.data.model.Song
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LastPlayedPersistenceTest {
    private val snapshots = PlaybackTestPreferences()
    private val progress = PlaybackTestPreferences()
    private val writer = QueuedPlaybackWriter()

    @Before
    fun setUp() = LastPlayed.initForTesting(snapshots, progress, writer)

    @Test
    fun `bounded snapshot and separate progress survive recreation`() {
        val tracks = List(1_000) { Song(videoId = "id-$it", title = "Track $it", artist = "Artist", thumbnailUrl = null) }
        LastPlayed.saveQueue(tracks, 500, 8_000L)
        val durable = LastPlayed.flush()
        assertFalse(durable.isDone)
        writer.runAll()
        assertTrue(durable.get())
        val writes = snapshots.commits
        LastPlayed.savePlaybackState(26, 19_000L)
        writer.runAll()
        assertEquals(writes, snapshots.commits)
        LastPlayed.initForTesting(snapshots, progress)
        val restored = requireNotNull(LastPlayed.load())
        assertEquals(76, restored.songs.size)
        assertEquals("id-501", restored.songs[restored.index].videoId)
        assertEquals(19_000L, restored.positionMs)
    }

    @Test
    fun `legacy scalar values migrate on the first progress update`() {
        snapshots.edit().putString("queue", """
            {"tracks":[{"id":"one","title":"One","artist":"Artist"},{"id":"two","title":"Two","artist":"Artist"}]}
        """.trimIndent()).putInt("index", 1).putLong("position", 23_000L).commit()
        assertEquals(23_000L, LastPlayed.load()?.positionMs)
        LastPlayed.savePlaybackState(1, 24_000L)
        writer.runAll()
        assertFalse(snapshots.contains("position"))
        LastPlayed.initForTesting(snapshots, progress)
        assertEquals(1, LastPlayed.load()?.index)
        assertEquals(24_000L, LastPlayed.load()?.positionMs)
    }

    @Test
    fun `durable clear orders after pending saves and removes both files`() {
        LastPlayed.saveQueue(listOf(Song(videoId = "old", title = "Old", artist = "Artist", thumbnailUrl = null)), 0)
        LastPlayed.clear()
        val durable = LastPlayed.flush()
        assertNull(LastPlayed.load())
        assertFalse(durable.isDone)
        writer.runAll()
        assertTrue(durable.get())
        LastPlayed.initForTesting(snapshots, progress)
        assertNull(LastPlayed.load())
        assertTrue(snapshots.getAll().isEmpty())
        assertTrue(progress.getAll().isEmpty())
    }
}
