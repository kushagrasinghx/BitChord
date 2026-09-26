package com.music.bitchord.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import java.util.concurrent.ConcurrentHashMap

/** One in-flight-or-recent answer per key, shared by everyone who asks for it. */
internal class DesktopSharedCalls<T : Any>(
    private val ttlMs: Long,
    private val scope: CoroutineScope,
) {
    private class Entry<T>(val deferred: Deferred<Result<T>>) {
        @Volatile
        var completedAtMs: Long = 0L
    }

    private val entries = ConcurrentHashMap<String, Entry<T>>()

    suspend fun get(key: String, block: suspend () -> Result<T>): Result<T> {
        while (true) {
            entries[key]?.let { existing ->
                val completed = existing.completedAtMs
                // Zero means still running, which is always worth joining.
                if (completed == 0L || System.currentTimeMillis() - completed < ttlMs) {
                    return existing.deferred.await()
                }
                entries.remove(key, existing)
            }

            // Started lazily so a caller that loses the race below never runs the work it is about
            // to discard.
            val entry = Entry(scope.async(start = CoroutineStart.LAZY) { block() })
            val raced = entries.putIfAbsent(key, entry)
            if (raced != null) {
                entry.deferred.cancel()
                continue
            }
            entry.deferred.invokeOnCompletion {
                val answer = runCatching { entry.deferred.getCompleted() }.getOrNull()
                if (answer?.isSuccess == true) {
                    entry.completedAtMs = System.currentTimeMillis()
                } else {
                    entries.remove(key, entry)
                }
            }
            return entry.deferred.await()
        }
    }

    fun clear() = entries.clear()
}
