package com.music.bitchord.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.extension

/**
 * The media this app keeps on disk so it is not fetched twice — motion-artwork clips today.
 *
 * Android bounds the same thing with `AudioCache`'s LRU evictor and a limit the Storage settings
 * set. Desktop's audio is demuxed straight from the source by FFmpeg rather than written through a
 * cache, so what accumulates here is the clips, which loop dozens of times behind one track and
 * without this were re-downloaded on every loop.
 */
internal object DesktopMediaCache {

    /** Where the files live, created on first use. */
    val directory: Path by lazy {
        val state = System.getenv("XDG_CACHE_HOME")?.takeIf(String::isNotBlank)
            ?: "${System.getProperty("user.home")}/.cache"
        Path.of(state, "bitchord", "media").also { runCatching { Files.createDirectories(it) } }
    }

    private val known = ConcurrentHashMap<String, Path>()

    /** The cached file for [url], or null when nothing has been kept for it. */
    fun existing(url: String, extension: String): Path? {
        val target = pathFor(url, extension)
        return target.takeIf { Files.isReadable(it) && runCatching { Files.size(it) > 0 }.getOrDefault(false) }
    }

    /** Where [url] would be written, whether or not it has been. */
    fun pathFor(url: String, extension: String): Path =
        known.getOrPut("$url|$extension") { directory.resolve("${digest(url)}.$extension") }

    /** What the cache is currently holding, in bytes. */
    fun sizeBytes(): Long = files().sumOf { runCatching { Files.size(it) }.getOrDefault(0L) }

    /** Everything in it, thrown away. Returns how many bytes were freed. */
    fun clear(): Long {
        val freed = sizeBytes()
        files().forEach { runCatching { Files.deleteIfExists(it) } }
        known.clear()
        return freed
    }

    /**
     * Drops the least recently used files until the cache fits inside the configured limit.
     *
     * Called after a write rather than on a timer: the only moment the cache can be over its limit
     * is just after something was added to it.
     */
    fun trim() {
        val limit = limitBytes()
        var total = sizeBytes()
        if (total <= limit) return
        files()
            .sortedBy { path ->
                runCatching {
                    Files.readAttributes(path, BasicFileAttributes::class.java).lastAccessTime().toMillis()
                }.getOrDefault(0L)
            }
            .forEach { path ->
                if (total <= limit) return
                val size = runCatching { Files.size(path) }.getOrDefault(0L)
                if (runCatching { Files.deleteIfExists(path) }.getOrDefault(false)) total -= size
            }
    }

    /** The cap the Storage settings set, in bytes. */
    fun limitBytes(): Long =
        DesktopPersistence().int(KEY_CACHE_LIMIT_MB, DEFAULT_LIMIT_MB).toLong() * 1024 * 1024

    fun setLimitMb(megabytes: Int) {
        DesktopPersistence().saveInt(KEY_CACHE_LIMIT_MB, megabytes.coerceIn(MIN_LIMIT_MB, MAX_LIMIT_MB))
        trim()
    }

    fun limitMb(): Int = DesktopPersistence().int(KEY_CACHE_LIMIT_MB, DEFAULT_LIMIT_MB)

    private fun files(): List<Path> = runCatching {
        Files.list(directory).use { stream ->
            stream.filter(Files::isRegularFile).toList()
        }
    }.getOrDefault(emptyList())

    private fun digest(url: String): String = MessageDigest.getInstance("SHA-256")
        .digest(url.toByteArray())
        .joinToString("") { "%02x".format(it) }
        .take(32)

    internal const val KEY_CACHE_LIMIT_MB = "media_cache_limit_mb"

    /** Small on purpose: a clip is a few seconds of video, not a library. */
    internal const val DEFAULT_LIMIT_MB = 512
    internal const val MIN_LIMIT_MB = 128
    internal const val MAX_LIMIT_MB = 10 * 1024

    /** Past this the Storage row warns, because it is a noticeable share of a disk. */
    internal const val WARNING_MB = 2048
}

/** Formats a cache size the way the Storage row shows it. */
internal fun formatCacheSize(megabytes: Int): String =
    if (megabytes >= 1024) {
        val gigabytes = megabytes / 1024.0
        if (gigabytes % 1.0 == 0.0) "${gigabytes.toInt()} GB" else "%.1f GB".format(gigabytes)
    } else {
        "$megabytes MB"
    }
