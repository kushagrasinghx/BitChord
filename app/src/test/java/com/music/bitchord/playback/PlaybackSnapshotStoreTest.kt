package com.music.bitchord.playback

import org.junit.Assert.*
import org.junit.Test

class PlaybackSnapshotStoreTest {
    private val snapshots = PlaybackTestPreferences()
    private val progress = PlaybackTestPreferences()
    private val writer = QueuedPlaybackWriter()

    private fun store(encode: (String) -> String? = { it }) = PlaybackSnapshotStore(
        snapshots, progress, "queue", encode, { it },
        initialProgress = { PlaybackProgress(entryId = it) },
        legacyProgress = { prefs, item -> PlaybackProgress(entryId = item, positionMs = prefs.getLong("position", 0L)) },
        writer = writer,
    )

    @Test
    fun `progress bursts write only the small file and never encode the queue`() {
        var encodes = 0
        val store = store { encodes++; it }
        store.save("queue-a")
        assertEquals(0, snapshots.commits)
        assertEquals("queue-a", store.load()?.snapshot)
        writer.runAll()
        val snapshotWrites = snapshots.commits
        val progressWrites = progress.commits

        repeat(100) { at -> store.updateProgress { it.progress.copy(positionMs = at.toLong() + 1L) } }
        assertEquals(100L, store.load()?.progress?.positionMs)
        writer.runAll()

        assertEquals(1, encodes)
        assertEquals(snapshotWrites, snapshots.commits)
        assertEquals(progressWrites + 1, progress.commits)
        assertEquals(0, snapshots.applies + progress.applies)
        assertEquals(100L, store().load()?.progress?.positionMs)
    }

    @Test
    fun `clear during an encode cannot resurrect the old queue`() {
        lateinit var store: PlaybackSnapshotStore<String>
        store = store { item -> store.clear(); item }
        store.save("old")
        val durable = store.flush()
        assertFalse(durable.isDone)
        writer.runAll()
        assertTrue(durable.get())
        assertNull(store.load())
        assertNull(store().load())
        assertTrue(snapshots.getAll().isEmpty())
        assertTrue(progress.getAll().isEmpty())
    }

    @Test
    fun `replacement during an encode persists the latest queue and progress`() {
        lateinit var store: PlaybackSnapshotStore<String>
        store = store { item ->
            if (item == "old") {
                store.save("new")
                store.updateProgress { it.progress.copy(positionMs = 42L) }
            }
            item
        }
        store.save("old")
        val durable = store.flush()
        writer.runAll()
        assertTrue(durable.get())
        assertEquals("new", store().load()?.snapshot)
        assertEquals(42L, store().load()?.progress?.positionMs)
        assertEquals(1, snapshots.commits)
    }

    @Test
    fun `process death between snapshot and progress commits ignores old progress`() {
        val store = store()
        store.save("old")
        store.updateProgress { it.progress.copy(positionMs = 99_000L) }
        writer.runAll()
        progress.allowCommit = false
        store.save("new")
        val durable = store.flush()
        writer.runAll()
        assertFalse(durable.get())
        val restored = requireNotNull(store().load())
        assertEquals("new", restored.snapshot)
        assertEquals(0L, restored.progress.positionMs)
    }

    @Test
    fun `progress arriving during snapshot commit follows it without another encode`() {
        lateinit var store: PlaybackSnapshotStore<String>
        var encodes = 0
        store = store { item ->
            encodes++
            store.updateProgress { it.progress.copy(positionMs = 12_345L) }
            item
        }
        store.save("queue")
        writer.runAll()
        assertEquals(1, encodes)
        assertEquals(1, snapshots.commits)
        assertEquals(12_345L, store().load()?.progress?.positionMs)
    }
}
