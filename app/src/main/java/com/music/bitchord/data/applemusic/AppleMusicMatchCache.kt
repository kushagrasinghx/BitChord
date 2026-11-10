package com.music.bitchord.data.applemusic

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Single-instance, bounded LRU. Recency is checkpointed with each mutation. */
internal class AppleMusicMatchCache(
    private val file: File?,
    private val capacity: Int,
    private val negativeTtlMs: Long,
    private val clock: () -> Long,
) {
    data class Key(val storefront: String, val videoId: String) {
        init {
            require(storefront.length == 2 && storefront.all { it in 'a'..'z' })
            require(videoId.isNotBlank() && videoId.length <= 512)
        }
    }

    data class Entry(val catalogId: String?, val isrc: String?, val checkedAt: Long) {
        init {
            require(catalogId == null || AppleMusicMatcher.isCatalogId(catalogId))
            require(isrc == null || AppleMusicMatcher.normalizeIsrc(isrc) == isrc)
            require(checkedAt >= 0)
        }
    }

    private val entries = LinkedHashMap<Key, Entry>(16, 0.75f, true)

    init {
        require(capacity in 1..MAX_ENTRIES)
        require(negativeTtlMs >= 0)
        load()
    }

    @Synchronized
    fun get(key: Key, knownIsrc: String? = null): Entry? {
        val entry = entries[key] ?: return null
        if (entry.conflictsWith(knownIsrc)) {
            entries.remove(key)
            // Contradictory evidence stays invalid even if the lookup fails or the app restarts.
            persist()
            return null
        }
        if (expired(entry)) {
            entries.remove(key)
            return null
        }
        return entry
    }

    @Synchronized
    fun put(
        key: Key,
        entry: Entry,
        keepPositive: Boolean = false,
        knownIsrc: String? = null,
    ): Entry {
        if (keepPositive) entries[key]?.takeIf {
            it.catalogId != null && !it.conflictsWith(knownIsrc)
        }?.let { return it }
        entries[key] = entry
        trim()
        persist()
        return entry
    }

    private fun Entry.conflictsWith(knownIsrc: String?): Boolean =
        isrc != null && knownIsrc != null && isrc != knownIsrc

    private fun expired(entry: Entry): Boolean {
        if (entry.catalogId != null) return false
        val now = clock()
        // A wall-clock rollback must not make a negative hit live indefinitely.
        return now < entry.checkedAt || now - entry.checkedAt >= negativeTtlMs
    }

    private fun trim() {
        val iterator = entries.entries.iterator()
        while (entries.size > capacity && iterator.hasNext()) {
            iterator.next()
            iterator.remove()
        }
    }

    private fun load() {
        val source = file ?: return
        try {
            if (!source.isFile || source.length() > MAX_FILE_BYTES) return
            val loaded = LinkedHashMap<Key, Entry>()
            DataInputStream(BufferedInputStream(FileInputStream(source))).use { input ->
                require(input.readInt() == MAGIC && input.readInt() == VERSION)
                val count = input.readInt()
                require(count in 0..MAX_ENTRIES)
                repeat(count) {
                    val key = Key(input.readUTF(), input.readUTF())
                    val entry = Entry(
                        catalogId = if (input.readBoolean()) input.readUTF() else null,
                        isrc = if (input.readBoolean()) input.readUTF() else null,
                        checkedAt = input.readLong(),
                    )
                    if (!expired(entry)) loaded[key] = entry
                }
                require(input.read() == -1)
            }
            entries.putAll(loaded)
            trim()
            if (loaded.size > capacity) persist()
        } catch (_: Exception) {
            // A partial, obsolete or corrupt cache is disposable, not an app-start failure.
        }
    }

    private fun persist() {
        val destination = file?.absoluteFile ?: return
        var temporary: File? = null
        try {
            val parent = destination.parentFile ?: return
            if (!parent.isDirectory && !parent.mkdirs()) return
            temporary = File.createTempFile(".am-match-", ".tmp", parent)
            FileOutputStream(temporary).use { stream ->
                val output = DataOutputStream(BufferedOutputStream(stream))
                output.writeInt(MAGIC)
                output.writeInt(VERSION)
                output.writeInt(entries.size)
                entries.forEach { (key, entry) ->
                    output.writeUTF(key.storefront)
                    output.writeUTF(key.videoId)
                    output.writeBoolean(entry.catalogId != null)
                    entry.catalogId?.let(output::writeUTF)
                    output.writeBoolean(entry.isrc != null)
                    entry.isrc?.let(output::writeUTF)
                    output.writeLong(entry.checkedAt)
                }
                output.flush()
                stream.fd.sync()
            }
            // Same-directory rename: a failed write/replace leaves the prior snapshot intact.
            Files.move(
                temporary.toPath(), destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: Exception) {
            // Persistence is optional; keep the in-memory result if storage is unavailable.
        } finally {
            try {
                temporary?.delete()
            } catch (_: SecurityException) {
                // A denied cleanup must not turn an optional cache write into a failure.
            }
        }
    }

    private companion object {
        const val MAGIC = 0x414D4D43 // AMMC
        const val VERSION = 1
        const val MAX_ENTRIES = 10_000
        const val MAX_FILE_BYTES = 20 * 1024 * 1024L
    }
}
