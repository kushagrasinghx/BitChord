package com.music.bitchord.playback

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import java.util.TreeSet

/**
 * [androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor], except the
 * ceiling is a `var` rather than baked into the constructor.
 *
 * [SimpleCache] can only be opened once per process, so a settings change
 * can't just build a new one with a bigger evictor — this is what lets the
 * limit move without tearing the cache down and reopening it, which would
 * orphan the [CacheDataSource][androidx.media3.datasource.cache.CacheDataSource]
 * the player already holds a reference to.
 */
@UnstableApi
class DynamicLruCacheEvictor(@Volatile var maxBytes: Long) : CacheEvictor {

    private val leastRecentlyUsed = TreeSet<CacheSpan>(::compare)
    private var currentSize = 0L

    override fun requiresCacheSpanTouches() = true

    override fun onCacheInitialized() = Unit

    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        if (length != C.LENGTH_UNSET.toLong()) {
            evictCache(cache, length)
        }
    }

    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        leastRecentlyUsed.add(span)
        currentSize += span.length
        evictCache(cache, 0)
    }

    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        leastRecentlyUsed.remove(span)
        currentSize -= span.length
    }

    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        onSpanRemoved(cache, oldSpan)
        onSpanAdded(cache, newSpan)
    }

    /**
     * Reclaims space right away when [maxBytes] drops, rather than waiting for
     * the next write to notice — otherwise a lowered limit only takes effect
     * whenever the listener next happens to play something.
     */
    fun applyNow(cache: Cache) = evictCache(cache, 0)

    private fun evictCache(cache: Cache, requiredSpace: Long) {
        while (currentSize + requiredSpace > maxBytes && leastRecentlyUsed.isNotEmpty()) {
            cache.removeSpan(leastRecentlyUsed.first())
        }
    }

    private companion object {
        fun compare(lhs: CacheSpan, rhs: CacheSpan): Int {
            val delta = lhs.lastTouchTimestamp - rhs.lastTouchTimestamp
            return when {
                delta == 0L -> lhs.compareTo(rhs)
                delta < 0L -> -1
                else -> 1
            }
        }
    }
}
