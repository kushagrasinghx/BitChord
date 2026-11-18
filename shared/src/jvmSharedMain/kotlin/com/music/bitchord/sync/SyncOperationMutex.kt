package com.music.bitchord.sync

import kotlinx.coroutines.sync.Mutex

/**
 * Process-local serialization for sync state mutations. The Android client links one profile per device,
 * so a single mutex safely covers WorkManager, foreground sync, pause/resume, and local reset.
 */
object SyncOperationMutex {
    private val lock = Mutex()

    suspend fun <T> withProfileLock(profileId: String, action: suspend () -> T): T {
        require(profileId.isNotBlank()) { "Sync profile ID is required" }
        lock.lock()
        try {
            return action()
        } finally {
            lock.unlock()
        }
    }
}
