package com.music.bitchord.playback

import android.content.SharedPreferences
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.Executors

internal data class PlaybackProgress(
    val index: Int = 0,
    val entryId: String? = null,
    val positionMs: Long = 0L,
)

internal data class SavedPlayback<T>(val snapshot: T, val progress: PlaybackProgress)

/**
 * Queue snapshots and progress have separate files: a progress tick must never
 * rewrite a playlist's JSON. The caller updates memory only; encoding and both
 * files' synchronous commits run on one writer, outside the state lock.
 *
 * A token binds each progress file to its snapshot. A process death between
 * the two commits therefore restores the new snapshot's initial position,
 * rather than applying the previous queue's progress to it.
 */
internal class PlaybackSnapshotStore<T>(
    private val snapshots: SharedPreferences,
    private val progress: SharedPreferences,
    private val snapshotKey: String,
    private val encode: (T) -> String?,
    private val decode: (String) -> T?,
    private val initialProgress: (T) -> PlaybackProgress,
    private val legacyProgress: (SharedPreferences, T) -> PlaybackProgress,
    private val writer: Executor = playbackPersistenceWriter,
) {
    private data class Record<T>(val token: String, val value: SavedPlayback<T>)
    private data class Write<T>(val record: Record<T>?, val snapshotChanged: Boolean)

    private val lock = Any()
    private var loaded = false
    private var record: Record<T>? = null
    private var migrateOnWrite = false
    private var pending: Write<T>? = null
    private var scheduled = false
    private var lastWriteSucceeded = true

    fun load(): SavedPlayback<T>? = synchronized(lock) {
        if (!loaded) {
            loaded = true
            val raw = runCatching { snapshots.getString(snapshotKey, null) }.getOrNull()
            val snapshot = raw?.let(decode) ?: return@synchronized null
            val token = runCatching { snapshots.getString(KEY_TOKEN, null) }.getOrNull()
            val restored = runCatching {
                if (token == null) {
                    migrateOnWrite = true
                    legacyProgress(snapshots, snapshot)
                } else if (progress.getString(KEY_TOKEN, null) == token) {
                    PlaybackProgress(
                        index = progress.getInt(KEY_INDEX, 0),
                        entryId = progress.getString(KEY_ENTRY_ID, null),
                        positionMs = progress.getLong(KEY_POSITION, 0L).coerceAtLeast(0L),
                    )
                } else {
                    initialProgress(snapshot)
                }
            }.getOrElse { initialProgress(snapshot) }
            record = Record(token ?: UUID.randomUUID().toString(), SavedPlayback(snapshot, restored))
        }
        record?.value
    }

    fun save(snapshot: T) {
        val next = Record(
            UUID.randomUUID().toString(),
            SavedPlayback(snapshot, initialProgress(snapshot)),
        )
        val start = synchronized(lock) {
            loaded = true
            migrateOnWrite = false
            record = next
            pending = Write(next, snapshotChanged = true)
            scheduleLocked()
        }
        if (start) writer.execute { drain() }
    }

    fun updateProgress(update: (SavedPlayback<T>) -> PlaybackProgress?) {
        val start = synchronized(lock) {
            val current = load() ?: return
            val next = update(current) ?: return
            if (next == current.progress && !migrateOnWrite) return
            val updated = requireNotNull(record).copy(value = current.copy(progress = next))
            record = updated
            pending = Write(updated, snapshotChanged = migrateOnWrite || pending?.snapshotChanged == true)
            migrateOnWrite = false
            scheduleLocked()
        }
        if (start) writer.execute { drain() }
    }

    fun clear() {
        val start = synchronized(lock) {
            loaded = true
            record = null
            migrateOnWrite = false
            pending = Write(null, snapshotChanged = true)
            scheduleLocked()
        }
        if (start) writer.execute { drain() }
    }

    /** A durable barrier the session can await without blocking its main thread. */
    fun flush(): ListenableFuture<Boolean> {
        val result = SettableFuture.create<Boolean>()
        writer.execute {
            try {
                result.set(drain())
            } catch (error: Throwable) {
                result.setException(error)
            }
        }
        return result
    }

    private fun scheduleLocked(): Boolean {
        if (scheduled) return false
        scheduled = true
        return true
    }

    private fun drain(): Boolean {
        while (true) {
            val write = synchronized(lock) {
                val next = pending
                pending = null
                if (next == null) {
                    scheduled = false
                    return lastWriteSucceeded
                }
                next
            }
            val success = runCatching { persist(write) }.getOrDefault(false)
            synchronized(lock) { lastWriteSucceeded = success }
        }
    }

    private fun persist(write: Write<T>): Boolean {
        val next = write.record
        if (next == null) {
            // The writer is serial, so a clear cannot be undone by an older
            // encode finishing later. No disk I/O holds the caller's lock.
            val snapshotCleared = snapshots.edit().clear().commit()
            val progressCleared = progress.edit().clear().commit()
            return snapshotCleared && progressCleared
        }
        if (write.snapshotChanged) {
            val encoded = encode(next.value.snapshot) ?: return false
            // Coalesce a queue replaced while its predecessor was encoding.
            if (synchronized(lock) { record?.token != next.token }) return true
            if (!snapshots.edit()
                    .clear()
                    .putString(snapshotKey, encoded)
                    .putString(KEY_TOKEN, next.token)
                    .commit()
            ) return false
        } else if (snapshots.getString(KEY_TOKEN, null) != next.token) {
            return false
        }
        val position = next.value.progress
        return progress.edit()
            .clear()
            .putString(KEY_TOKEN, next.token)
            .putInt(KEY_INDEX, position.index)
            .putString(KEY_ENTRY_ID, position.entryId)
            .putLong(KEY_POSITION, position.positionMs.coerceAtLeast(0L))
            .commit()
    }

    private companion object {
        const val KEY_TOKEN = "snapshot_token"
        const val KEY_INDEX = "index"
        const val KEY_ENTRY_ID = "current_entry_id"
        const val KEY_POSITION = "position"
    }
}

/** Shared so a session barrier covers queue clears and replacements in order. */
internal val playbackPersistenceWriter = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "playback-persistence").apply { isDaemon = true }
}
