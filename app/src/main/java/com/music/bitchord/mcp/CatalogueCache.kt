package com.music.bitchord.mcp

import java.net.UnknownHostException
import java.util.Collections
import java.util.IdentityHashMap

/** Small synchronized access-order cache; monotonic TTL, no disk or Android dependencies. */
internal class CatalogueCache<K, V>(private val capacity: Int, private val ttlMs: Long,
                                    private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) {
    init { require(capacity > 0 && ttlMs > 0) }
    private val entries = LinkedHashMap<K, Pair<Long, V>>(16, 0.75f, true)
    @Synchronized fun put(key: K, value: V) {
        entries.entries.removeAll { clock() - it.value.first >= ttlMs }
        entries[key] = clock() to value
        while (entries.size > capacity) entries.remove(entries.keys.first())
    }
    @Synchronized fun get(key: K): V? {
        val entry = entries[key] ?: return null
        if (clock() - entry.first >= ttlMs) { entries.remove(key); return null }
        return entry.second
    }
}

internal fun Throwable.isCatalogueDnsFailure(): Boolean {
    val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    var current: Throwable? = this
    while (current != null && seen.add(current)) {
        if (current is UnknownHostException) return true
        current = current.cause
    }
    return false
}
