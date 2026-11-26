package com.music.bitchord.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Coalesces embedded-art reads for one scoped source key. */
internal class RemoteArtworkReads {
    private class Read(val speculative: Boolean) {
        val result = CompletableDeferred<String?>()
        var foregroundWaiters = 0
        var missed = false
        var foregroundFallback: Read? = null
    }

    private val inFlight = mutableMapOf<String, Read>()
    private val lock = Mutex()

    suspend fun read(key: String, speculative: Boolean, load: suspend () -> String?): String? =
        read(key, speculative, retryForegroundCancellation = true, recoverSpeculation = true, load)

    private suspend fun read(
        key: String, speculative: Boolean, retryForegroundCancellation: Boolean,
        recoverSpeculation: Boolean, load: suspend () -> String?,
        missedSpeculation: Read? = null,
    ): String? {
        val (entry, ownsRead) = lock.withLock {
            missedSpeculation?.foregroundFallback?.let { return@withLock it to false }
            val existing = inFlight[key]
            if (existing == null || (!speculative && existing.speculative && existing.missed)) {
                Read(speculative).also {
                    if (existing != null) existing.foregroundFallback = it
                    inFlight[key] = it
                } to true
            } else {
                if (!speculative && existing.speculative) existing.foregroundWaiters++
                existing to false
            }
        }
        if (!ownsRead) {
            val joinedSpeculation = !speculative && entry.speculative
            return try {
                val result = try {
                    entry.result.await()
                } catch (_: CancellationException) {
                    currentCoroutineContext().ensureActive()
                    return when {
                        speculative -> null
                        joinedSpeculation && recoverSpeculation ->
                            read(key, false, retryForegroundCancellation, false, load, entry)
                        !entry.speculative && retryForegroundCancellation ->
                            read(key, false, false, recoverSpeculation, load)
                        else -> null
                    }
                }
                if (result == null && joinedSpeculation && recoverSpeculation) {
                    read(key, false, retryForegroundCancellation, false, load, entry)
                } else result
            } finally {
                if (joinedSpeculation) withContext(NonCancellable) {
                    lock.withLock {
                        entry.foregroundWaiters--
                        if (entry.foregroundWaiters == 0 && entry.result.isCompleted) inFlight.remove(key, entry)
                    }
                }
            }
        }
        var result: String? = null
        var cancelled = false
        try {
            result = load()
            return result
        } catch (e: CancellationException) {
            cancelled = true
            throw e
        } catch (_: Exception) {
            return null
        } finally {
            withContext(NonCancellable) {
                lock.withLock {
                    entry.missed = result == null
                    // Keep a failed speculative slot until foreground waiters can replace it.
                    // A new speculative request cannot interpose during their handoff.
                    if (!entry.speculative || !entry.missed || entry.foregroundWaiters == 0) inFlight.remove(key, entry)
                    if (cancelled) entry.result.cancel() else entry.result.complete(result)
                }
            }
        }
    }
}
