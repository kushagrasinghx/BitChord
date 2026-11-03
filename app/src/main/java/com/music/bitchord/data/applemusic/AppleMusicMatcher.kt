package com.music.bitchord.data.applemusic

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.APPLE_MISSING_PREFIX
import com.music.bitchord.data.sources.TrackMatcher
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Phone-side metadata matching only; this class never owns credentials or playback.
 *
 * Supply a file under the application's private files/cache directory to retain mappings.
 * Construction and cache mutations perform bounded, best-effort file IO, so callers using
 * persistence should construct/use this instance off the main thread. Share one instance
 * per cache file; concurrent processes/writers to the same file are not supported.
 */
class AppleMusicMatcher(
    private val catalog: Catalog = LibraryCatalog,
    cacheFile: File? = null,
    maxCacheEntries: Int = 1_000,
    negativeTtlMs: Long = 5 * 60_000L,
    private val lookupTimeoutMs: Long = 2_500L,
    private val clock: () -> Long = System::currentTimeMillis,
    prefetchParallelism: Int = 2,
) {
    interface Catalog {
        /** Empty means a completed lookup with no results; failures must throw. */
        suspend fun searchSongs(storefront: String, query: String): List<AppleMusicTrack>
        suspend fun songsByIsrc(storefront: String, isrc: String): List<AppleMusicTrack>
    }

    data class Match(val catalogId: String, val isrc: String?)

    private val cache = AppleMusicMatchCache(cacheFile, maxCacheEntries, negativeTtlMs, clock)
    // Fixed stripes bound lock storage even when a queue visits arbitrarily many ids.
    private val locks = Array(32) { Mutex() }
    private val prefetchSlots = Semaphore(prefetchParallelism.also { require(it in 1..3) })

    init {
        require(lookupTimeoutMs > 0)
    }

    /**
     * Returns a song catalog id, never a library id. A supplied ISRC is tried before search.
     * Lock waiting and all catalog calls share one [lookupTimeoutMs] deadline.
     * Direct and cache hits bypass the lock. Caller cancellation propagates;
     * catalog failures and our own timeout return null without recording a negative hit.
     */
    suspend fun match(song: Song, storefront: String, knownIsrc: String? = null): String? {
        currentCoroutineContext().ensureActive()
        if (song.videoId.startsWith(DIRECT_CATALOG_PREFIX)) {
            return directCatalogId(song.videoId)
        }
        val key = key(storefront, song.videoId) ?: return null
        val isrc = normalizeIsrc(knownIsrc)
        cachedEntry(key, isrc)?.let { return it.catalogId }
        val lock = locks[(key.hashCode() and Int.MAX_VALUE) % locks.size]
        return withTimeoutOrNull(lookupTimeoutMs) {
            lock.withLock {
                cachedEntry(key, isrc)?.let { return@withLock it.catalogId }
                val match = try {
                    find(song, key.storefront, isrc)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Authentication, transport and server errors are not evidence of absence.
                    currentCoroutineContext().ensureActive()
                    return@withLock null
                }
                currentCoroutineContext().ensureActive()
                val entry = AppleMusicMatchCache.Entry(
                    catalogId = match?.catalogId,
                    isrc = match?.isrc ?: isrc,
                    checkedAt = clock(),
                )
                // Only the caller's ISRC, not a heuristic candidate, can contradict a library seed.
                cache.put(key, entry, keepPositive = true, knownIsrc = isrc).catalogId
            }
        }
    }

    /** Positive cached metadata only; no catalog IO, and expired misses are not exposed. */
    fun cached(storefront: String, videoId: String): Match? =
        key(storefront, videoId)?.let { cache.get(it) }?.toMatch()

    fun cachedCatalogId(storefront: String, videoId: String): String? =
        directCatalogId(videoId) ?: cached(storefront, videoId)?.catalogId

    fun cachedIsrc(storefront: String, videoId: String): String? = cached(storefront, videoId)?.isrc

    /** Stores a trusted, already established catalog mapping, replacing any cached miss. */
    fun store(storefront: String, videoId: String, catalogId: String, isrc: String? = null): Boolean {
        val key = key(storefront, videoId) ?: return false
        if (!isCatalogId(catalogId)) return false
        if (videoId.startsWith(DIRECT_CATALOG_PREFIX) && directCatalogId(videoId) != catalogId) return false
        cache.put(key, AppleMusicMatchCache.Entry(catalogId, normalizeIsrc(isrc), clock()))
        return true
    }

    /** Library-only uploads are deliberately not promoted from [AppleMusicTrack.id]. */
    fun seed(storefront: String, videoId: String, track: AppleMusicTrack): Boolean =
        !track.isVideo && track.catalogId?.let { store(storefront, videoId, it, track.isrc) } == true

    /** Structured, cancellable warming of at most the next three queue entries. */
    suspend fun prefetch(
        songs: List<Song>,
        storefront: String,
        knownIsrc: (Song) -> String? = { null },
    ) {
        coroutineScope {
            songs.take(3).distinctBy { it.videoId }.map { song ->
                async {
                    prefetchSlots.withPermit { match(song, storefront, knownIsrc(song)) }
                }
            }.awaitAll()
        }
    }

    private fun cachedEntry(key: AppleMusicMatchCache.Key, knownIsrc: String?): AppleMusicMatchCache.Entry? =
        cache.get(key, knownIsrc)?.takeIf {
            // New ISRC evidence must not be suppressed by an earlier text-search miss.
            it.catalogId != null || knownIsrc == null || it.isrc == knownIsrc
        }

    private suspend fun find(song: Song, storefront: String, isrc: String?): Match? {
        val target = TrackMatcher.targetOf(song)
        if (isrc != null) {
            best(catalog.songsByIsrc(storefront, isrc), target, isrc)?.let { return it }
        }
        for (query in TrackMatcher.queries(target)) {
            currentCoroutineContext().ensureActive()
            best(catalog.searchSongs(storefront, query), target, isrc)?.let { return it }
        }
        return null
    }

    private fun best(tracks: List<AppleMusicTrack>, target: TrackMatcher.Target, knownIsrc: String?): Match? =
        tracks.mapNotNull { track ->
            val id = track.catalogId?.takeIf(::isCatalogId) ?: return@mapNotNull null
            if (track.isVideo || !TrackMatcher.sharesArtist(target.artist, track.artist)) return@mapNotNull null
            val isrc = normalizeIsrc(track.isrc)
            if (knownIsrc != null && isrc != null && knownIsrc != isrc) return@mapNotNull null
            val drift = target.durationSec?.let { expected ->
                if (track.durationMs <= 0) return@mapNotNull null
                abs(expected * 1_000L - track.durationMs.toLong())
            }
            // TrackMatcher permits video intros and runtime-identical different credits;
            // neither exception is safe when choosing an Apple catalog recording.
            if (drift != null && drift > DURATION_SLACK_MS) return@mapNotNull null
            val candidate = Song(
                videoId = id,
                title = track.title,
                artist = track.artist,
                thumbnailUrl = track.imageUrl,
                durationText = track.durationMs.takeIf { it > 0 }?.let {
                    val seconds = it / 1_000
                    "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
                },
                albumName = track.album,
                isExplicit = track.explicit,
            )
            val score = TrackMatcher.score(candidate, target) ?: return@mapNotNull null
            Ranked(
                Match(id, isrc ?: knownIsrc),
                explicitMatch = target.isExplicit != null && target.isExplicit == track.explicit,
                score = score,
                drift = drift ?: Long.MAX_VALUE,
            )
        }.sortedWith(
            compareByDescending<Ranked> { it.explicitMatch }
                .thenByDescending { it.score }
                .thenBy { it.drift },
        ).firstOrNull()?.match

    private fun AppleMusicMatchCache.Entry.toMatch(): Match? = catalogId?.let { Match(it, isrc) }


    private data class Ranked(val match: Match, val explicitMatch: Boolean, val score: Int, val drift: Long)

    private object LibraryCatalog : Catalog {
        override suspend fun searchSongs(storefront: String, query: String) =
            AppleMusicLibrary.searchSongs(storefront, query)

        override suspend fun songsByIsrc(storefront: String, isrc: String) =
            AppleMusicLibrary.songsByIsrc(storefront, isrc)
    }

    companion object {
        const val DIRECT_CATALOG_PREFIX = APPLE_MISSING_PREFIX
        const val DURATION_SLACK_MS = 5_000L
        private val CATALOG_ID = Regex("[1-9][0-9]{0,31}")
        private val ISRC = Regex("[A-Z]{2}[A-Z0-9]{3}[0-9]{7}")

        internal fun isCatalogId(id: String): Boolean = CATALOG_ID.matches(id)

        internal fun normalizeIsrc(isrc: String?): String? =
            isrc?.trim()?.replace("-", "")?.uppercase(Locale.ROOT)?.takeIf(ISRC::matches)

        private fun directCatalogId(videoId: String): String? =
            videoId.takeIf { it.startsWith(DIRECT_CATALOG_PREFIX) }
                ?.removePrefix(DIRECT_CATALOG_PREFIX)?.takeIf(::isCatalogId)

        private fun key(storefront: String, videoId: String): AppleMusicMatchCache.Key? {
            val normalized = storefront.trim().lowercase(Locale.ROOT)
            if (normalized.length != 2 || normalized.any { it !in 'a'..'z' }) return null
            if (videoId.isBlank() || videoId.length > 512) return null
            return AppleMusicMatchCache.Key(normalized, videoId)
        }
    }
}
