package com.music.bitchord.wear.ui

import android.content.Context
import android.util.LruCache
import com.music.bitchord.shared.wear.SyncedLyricsPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Offline cache of parsed [SyncedLyricsPayload]s on the watch.
 * A repeated track (or one the phone already delivered earlier) never needs another Bluetooth transfer.
 *
 * - Disk: gzip JSON, one file per track, LRU by last-modified, bounded by entries and bytes.
 * - Memory: tiny LRU for instant re-entry.
 */
class LyricsCache(
    context: Context,
    private val maxEntries: Int = 80,
    private val maxBytes: Long = 12L * 1024 * 1024,
) {
    private val dir = File(context.filesDir, "lyrics_cache").apply { mkdirs() }
    private val memory = LruCache<String, SyncedLyricsPayload>(4)
    private val mutex = Mutex()

    /** Returns the cached payload, or null if missing or (when [expectedHash] != null) outdated. */
    suspend fun get(trackId: String, expectedHash: String? = null): SyncedLyricsPayload? =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                memory.get(trackId)?.let { if (matches(it, expectedHash)) return@withLock it }

                val file = fileFor(trackId)
                if (!file.exists()) return@withLock null
                val payload = try {
                    SyncedLyricsPayload.fromGzipBytes(file.readBytes())
                } catch (e: Exception) {
                    file.delete()
                    return@withLock null
                }
                if (!matches(payload, expectedHash)) return@withLock null

                file.setLastModified(System.currentTimeMillis())
                memory.put(trackId, payload)
                payload
            }
        }

    suspend fun put(payload: SyncedLyricsPayload) = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val target = fileFor(payload.trackId)
                val tmp = File(dir, target.name + ".tmp")
                tmp.writeBytes(payload.toGzipBytes())
                if (!tmp.renameTo(target)) {
                    target.delete()
                    tmp.renameTo(target)
                }
                memory.put(payload.trackId, payload)
                trim()
            } catch (_: Exception) {
                // Cache is best-effort.
            }
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            memory.evictAll()
            dir.listFiles()?.forEach { it.delete() }
        }
    }

    private fun matches(p: SyncedLyricsPayload, expectedHash: String?) =
        expectedHash == null || p.contentHash == expectedHash

    private fun fileFor(trackId: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(trackId.toByteArray(Charsets.UTF_8))
        val name = digest.take(12).joinToString("") { "%02x".format(it) }
        return File(dir, "$name.lyr")
    }

    private fun trim() {
        val files = dir.listFiles { f -> f.extension == "lyr" }?.sortedBy { it.lastModified() }?.toMutableList()
            ?: return
        var total = files.sumOf { it.length() }
        while (files.isNotEmpty() && (files.size > maxEntries || total > maxBytes)) {
            val oldest = files.removeAt(0)
            total -= oldest.length()
            oldest.delete()
        }
    }
}