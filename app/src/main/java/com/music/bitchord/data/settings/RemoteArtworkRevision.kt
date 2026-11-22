package com.music.bitchord.data.settings

import java.security.MessageDigest
import java.security.SecureRandom

/** Stored in AuthStore, outside artwork keys and backup exports. */
internal fun remoteArtworkSourceFingerprint(parts: List<String>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    parts.forEach {
        val bytes = it.toByteArray(Charsets.UTF_8)
        digest.update("${bytes.size}:".toByteArray(Charsets.US_ASCII))
        digest.update(bytes)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

internal fun restoredRemoteArtworkScope(
    record: String?, fingerprint: String, freshScope: () -> Long = { SecureRandom().nextLong() and (Long.MAX_VALUE ushr 1) },
): Long = record?.takeIf { it.substringAfter(':') == fingerprint }
    ?.substringBefore(':')?.toLongOrNull() ?: freshScope()

/** Couples saved remote-source changes with their artwork cache revision. */
internal class RemoteArtworkRevision<T : Any> {
    private var current: T? = null
    private var scope = 0L

    @Synchronized
    fun update(
        persistedScope: (T) -> Long,
        persistScope: (Long, T) -> Unit,
        editAndRead: () -> T,
        publish: (Long, T, T?) -> Unit,
    ) {
        val previous = current
        val next = editAndRead()
        if (next == previous) return
        val nextScope = if (previous == null) persistedScope(next) else scope + 1
        persistScope(nextScope, next)
        current = next
        scope = nextScope
        publish(nextScope, next, previous)
    }
}
