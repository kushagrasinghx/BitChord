package com.music.bitchord.download

import android.content.Context
import com.music.bitchord.data.LikeState
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.AutoDownloadNetMode
import com.music.bitchord.data.settings.AutoDownloadSource
import com.music.bitchord.data.settings.DownloadQuality
import com.music.bitchord.data.stats.ListeningStats
import com.music.bitchord.data.stats.ReplayPeriod
import com.music.bitchord.data.stats.TrackEntry
import com.music.bitchord.playback.AudioCache
import java.io.File
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Automatic downloads: pro-active queue filling without background workers.
 *
 * Runs only while the app lives (foreground triggers). The `WIFI_CHARGING`
 * background path is a separate worker that calls [sync] — see the plan.
 *
 * Selection is Replay + liked, no per-artist cap:
 * - `plays < MIN_PLAYS` never qualifies (1-3 listens are not a habit yet),
 * - more listens score higher, recent listens score higher, skips subtract,
 * - liked tracks get a base bonus (`Forgotten favorites` included).
 */
object AutoDownloads {

    /** Minimum plays before a Replay track becomes a candidate. */
    const val MIN_PLAYS = 4

    /**
     * Tracks per sync, by connection — the battery bargain. Mobile data is
     * the stingiest (radio + bill), plain Wi-Fi sits in the middle, and
     * Wi-Fi + charging is uncapped (see [sync]): with power connected there
     * is no battery to drain. [AutoDownloadIntensity] scales the two capped
     * rungs, never the charging one.
     */
    const val DATA_PER_SYNC = 10
    const val WIFI_PER_SYNC = 25

    /** Floor so Light on data still moves at all. */
    const val MIN_PER_SYNC = 5

    /** Flat score for radio-tail candidates: below habits, above nothing. */
    const val RADIO_SCORE = 0.35

    /** Flat score for playlist-tail candidates. */
    const val PLAYLIST_SCORE = 0.5

    /**
     * Bonus for tracks playback actually read into the stream cache: bytes
     * already on disk from real listening, so their download is the cheapest
     * and most likely wanted. Points only — cached tracks still have to
     * clear the play floor like everyone else.
     */
    const val CACHED_BONUS = 0.2

    /** System free-space floor: evict auto files until 1GB is free. */
    const val MIN_FREE_BYTES = 1L * 1024 * 1024 * 1024

    /** How long the paginated liked list is trusted before re-crawling it. */
    private const val LIKED_CACHE_TTL_MS = 30L * 60 * 1000

    /** How long an evicted track stays out of the queue. */
    private const val EVICT_TTL_MS = 7L * 24 * 60 * 60 * 1000

    /**
     * Evicted ids mapped to the timestamp they may be reconsidered at.
     *
     * Without this the cap is a churn machine: over the GB budget the quota
     * evicts the lowest-score file, and the next sync — same scores — queues
     * the very same track again. Downloaded, evicted, downloaded, forever,
     * on the listener's battery. A manual tap still downloads immediately;
     * only the automatic path looks away, and only for a week.
     */
    private val evictedUntil = java.util.concurrent.ConcurrentHashMap<String, Long>()
    @Volatile
    private var evictLoaded = false

    private fun evictPrefs(context: Context) =
        context.applicationContext.getSharedPreferences("bitchord_autoevict", Context.MODE_PRIVATE)

    private fun ensureEvictLoaded(context: Context) {
        if (evictLoaded) return
        synchronized(evictedUntil) {
            if (evictLoaded) return
            val now = System.currentTimeMillis()
            evictPrefs(context).getString("evicted", null)
                ?.lineSequence()
                ?.mapNotNull { line ->
                    val id = line.substringBefore("=").trim()
                    val until = line.substringAfter("=", "").trim().toLongOrNull()
                    if (id.isEmpty() || until == null || until <= now) null else id to until
                }
                ?.forEach { (id, until) -> evictedUntil[id] = until }
            evictLoaded = true
        }
    }

    private fun persistEvicted(context: Context) {
        val now = System.currentTimeMillis()
        val live = evictedUntil.entries
            .filter { it.value > now }
            .joinToString("\n") { (id, until) -> "$id=$until" }
        evictedUntil.entries.removeIf { it.value <= now }
        evictPrefs(context).edit().putString("evicted", live).apply()
    }

    private fun rememberEvicted(context: Context, videoId: String) {
        ensureEvictLoaded(context)
        evictedUntil[videoId] = System.currentTimeMillis() + EVICT_TTL_MS
        persistEvicted(context)
    }

    private fun isEvicted(context: Context, videoId: String): Boolean {
        ensureEvictLoaded(context)
        val until = evictedUntil[videoId] ?: return false
        if (until > System.currentTimeMillis()) return true
        evictedUntil.remove(videoId, until)
        return false
    }

    /** RecencyDQ: fresh <7d, warm ~30d, stale beyond. */
    private const val DAY_MS = 24L * 60 * 60 * 1000

    data class Candidate(val song: Song, val score: Double, val reason: String)

    sealed interface SyncResult {
        data object Disabled : SyncResult
        data object NotAllowedNow : SyncResult
        data class Done(val enqueued: Int, val evicted: Int) : SyncResult
        /** Stopped early: the GB budget, the track cap, or disk space ran out. */
        data class Capped(
            val enqueued: Int,
            val evicted: Int,
            val reason: CapReason,
        ) : SyncResult
        data class Failed(val message: String) : SyncResult
    }

    /** Why a sync stopped early — the row reports each differently. */
    enum class CapReason { BUDGET, TRACKS, DISK }

    /**
     * One sync: evict over-quota auto files, then enqueue top candidates
     * while the GB budget allows. Safe to call from any foreground trigger.
     */
    suspend fun sync(context: Context): SyncResult = withContext(Dispatchers.IO) {
        if (!AppSettings.autoDownloadEnabled.value) return@withContext SyncResult.Disabled
        if (!AppSettings.autoDownloadsAllowedNow) return@withContext SyncResult.NotAllowedNow
        try {
            // The estimate matches what the files will actually resolve at
            // (see Downloads.effectiveAutoQuality).
            val maxBytes = (AppSettings.autoDownloadMaxGb.value * 1024 * 1024 * 1024).toLong()
            val estimate = estimateBytes(Downloads.effectiveAutoQuality())
            // Disk-full short-circuit before any network: crawling two dozen
            // liked pages for candidates that cannot be queued is pure cost.
            // Only when eviction could actually help (below).
            val freeNow = freeBytes(context)
            if (freeNow - estimate < MIN_FREE_BYTES) {
                val usedNow = Downloads.autoBytes(context)
                if (freeNow + usedNow < MIN_FREE_BYTES) {
                    return@withContext SyncResult.Capped(0, 0, CapReason.DISK)
                }
            }
            // Fetched once per sync and shared: the account's likes are the
            // strongest signal it owns — session taps alone would miss all
            // of them, and fetching twice per sync doubles the cost.
            // Paginated in full, not just the first page: library() only
            // carries ~100 rows, and a 2.5k Liked Music would otherwise
            // refill from the same head forever.
            val libraryLiked = libraryLikedFull()
            val libraryLikedIds = libraryLiked.mapTo(HashSet()) { it.videoId }
            // Tracks playback actually read: bytes already local from real
            // listening. One walk per sync, shared by queueing and eviction
            // so both rank the same way.
            val cachedIds = runCatching {
                AudioCache.cachedSongs().mapTo(HashSet()) { it.song.videoId }
            }.getOrDefault(emptySet())
            val evicted = enforceQuota(context, libraryLikedIds, cachedIds)
            // Plugged into Wi-Fi: fill without a track cap, the GB budget and
            // the free-space floor are still the limit. Anywhere else the
            // per-sync cap keeps one run from hanging for hours — and from
            // draining the battery: data sips, plain Wi-Fi drinks, each
            // scaled by the listener's intensity.
            val onChargerUnmetered = AppSettings.autoDownloadNetMode.value ==
                AutoDownloadNetMode.WIFI_CHARGING &&
                AppSettings.isCharging.value == true &&
                AppSettings.meteredConnection.value != true
            val base = when {
                onChargerUnmetered -> Int.MAX_VALUE
                AppSettings.meteredConnection.value == true -> DATA_PER_SYNC
                else -> WIFI_PER_SYNC
            }
            val cap = if (base == Int.MAX_VALUE) {
                base
            } else {
                (base * AppSettings.autoDownloadIntensity.value.factor)
                    .toInt().coerceAtLeast(MIN_PER_SYNC)
            }
            // The estimate matches what the files will actually resolve at
            // (see Downloads.effectiveAutoQuality).
            var used = Downloads.autoBytes(context)
            var enqueued = 0
            var capped: CapReason? = null
            val candidates = collectCandidates(context, libraryLiked, cachedIds)
            if (candidates.isEmpty()) return@withContext SyncResult.Done(0, evicted)
            val freeLeft = { freeBytes(context) }
            for (c in candidates) {
                // The budget was already settled above; what remains is the
                // per-sync track cap and the disk itself.
                if (enqueued >= cap) {
                    capped = CapReason.TRACKS
                    break
                }
                if (used + estimate > maxBytes) {
                    capped = CapReason.BUDGET
                    break
                }
                if (freeLeft() - estimate < MIN_FREE_BYTES) {
                    capped = CapReason.DISK
                    break
                }
                // enqueue is a no-op for duplicates: only a track actually
                // queued consumes budget and counts, or overlapping syncs
                // spend the same bytes twice and the report lies.
                if (Downloads.enqueue(context, c.song, from = "auto", auto = true)) {
                    used += estimate
                    enqueued++
                }
            }
            val reason = capped
            if (reason != null) SyncResult.Capped(enqueued, evicted, reason)
            else SyncResult.Done(enqueued, evicted)
        } catch (e: Exception) {
            SyncResult.Failed(e.message ?: "sync failed")
        }
    }

    /**
     * Delete lowest-score auto files until under the GB cap and above the
     * free-space floor. Manual files (`auto=false`) are never touched.
     *
     * Refuses a futile eviction outright: when even deleting every auto file
     * would not reach the floor, the floor is the partition's problem, not
     * the queue's, and emptying the downloads for nothing is pure loss.
     *
     * @return number of files removed.
     */
    suspend fun enforceQuota(
        context: Context,
        libraryLikedIds: Set<String> = emptySet(),
        cachedIds: Set<String> = emptySet(),
    ): Int = withContext(Dispatchers.IO) {
        val maxBytes = (AppSettings.autoDownloadMaxGb.value * 1024 * 1024 * 1024).toLong()
        val free = freeBytes(context)
        val held = Downloads.autoBytes(context)
        if (free < MIN_FREE_BYTES && free + held < MIN_FREE_BYTES) return@withContext 0
        val stats = statsById()
        // Score victims the way candidates are scored: liked files keep
        // their +1.0 base, or the liked pool would empty first. Library
        // likes count, not just this session's taps.
        val likedIds = LikeState.overrides.value
            .filterValues { it == LikeStatus.LIKE }
            .keys + libraryLikedIds
        // Sizes once, not once per victim: the old loop re-opened every auto
        // file per eviction, which at a few hundred files is hundreds of
        // thousands of descriptor opens per sync. Scores are static across
        // the loop, so the victims are simply the ascending order.
        val sizes = HashMap<String, Long>()
        for (meta in Downloads.savedMetadata.value.values) {
            if (!meta.auto || meta.uri in sizes) continue
            sizes[meta.uri] = runCatching {
                context.contentResolver.openFileDescriptor(meta.uri.toUri(), "r")
                    ?.use { it.statSize.takeIf { s -> s > 0 } }
            }.getOrNull() ?: continue
        }
        var used = sizes.values.sum()
        val victims = Downloads.savedMetadata.value.values
            .filter { it.auto }
            .distinctBy { it.uri }
            .sortedBy { meta ->
                val entry = stats[meta.videoId]
                // Lowest score first: stale, rarely played, heavily skipped.
                score(entry, liked = meta.videoId in likedIds, cached = meta.videoId in cachedIds)
            }
        var removed = 0
        for (victim in victims) {
            if (used <= maxBytes && freeBytes(context) >= MIN_FREE_BYTES) break
            if (Downloads.delete(context, victim.videoId)) {
                used -= sizes[victim.uri] ?: 0L
                removed++
                rememberEvicted(context, victim.videoId)
                if (removed > 500) break
            } else {
                break
            }
        }
        removed
    }

    suspend fun collectCandidates(
        context: Context,
        libraryLiked: List<Song> = emptyList(),
        cachedIds: Set<String> = emptySet(),
    ): List<Candidate> =
        withContext(Dispatchers.IO) {
            if (!AppSettings.autoDownloadEnabled.value) return@withContext emptyList()
            // Liked songs qualify in both modes — they are the strongest
            // signal the account owns. The modes differ by what rides along:
            // playlist tails for LIKED, Replay top plus radio for ALGORITHM.
            val wantLiked = AppSettings.autoDownloadSource.value == AutoDownloadSource.LIKED
            val wantTop = AppSettings.autoDownloadSource.value == AutoDownloadSource.ALGORITHM
            if (!wantLiked && !wantTop) return@withContext emptyList()

            val saved = Downloads.saved.value.keys
            val active = Downloads.active.value.keys
            val stats = statsById()
            val likedIds = LikeState.overrides.value
                .filterValues { it == LikeStatus.LIKE }
                .keys + libraryLiked.mapTo(HashSet()) { it.videoId }

            val out = LinkedHashMap<String, Candidate>()
            // Filter before bounding: take(200) on the raw list replays the
            // same saved head every sync while the rest of the library never
            // surfaces. Post-filter take walks to fresh tracks as they save.
            val freshLiked = libraryLiked.asSequence()
                .filter {
                    it.videoId.isNotBlank() && it.videoId !in saved && it.videoId !in active &&
                        !isEvicted(context, it.videoId)
                }
                .take(200)
                .toList()

            if (wantLiked) {
                freshLiked.forEach { song ->
                    val id = song.videoId
                    val entry = stats[id]
                    // Liked always qualifies; score still reflects habit.
                    val s = 1.0 + score(entry, liked = true, cached = id in cachedIds)
                    out.putIfAbsent(id, Candidate(song, s, reason(entry, liked = true)))
                }
            }

            if (wantTop) {
                freshLiked.forEach { song ->
                    val id = song.videoId
                    if (id in out) return@forEach
                    val entry = stats[id]
                    val s = 1.0 + score(entry, liked = true, cached = id in cachedIds)
                    out.putIfAbsent(id, Candidate(song, s, reason(entry, liked = true)))
                }
                val summarySongs = runCatching {
                    ListeningStats.summary(ReplayPeriod.ALL_TIME).songs
                }.getOrDefault(emptyList()).associateBy { it.song.videoId }
                val merged = stats.values.sortedByDescending {
                    score(it, liked = it.id in likedIds, cached = it.id in cachedIds)
                }
                for (entry in merged) {
                    val id = entry.id
                    if (id.isBlank() || id in saved || id in active || id in out) continue
                    if (isEvicted(context, id)) continue
                    // Floor before rank: fresh one-play tracks outscore stale
                    // four-play ones, and bounding first would let them crowd
                    // every qualifier out of the window.
                    if (entry.plays < MIN_PLAYS) continue
                    val cached = id in cachedIds
                    val song = summarySongs[id]?.song
                        ?: Song(
                            videoId = id,
                            title = entry.title.ifBlank { id },
                            artist = entry.artist,
                            thumbnailUrl = entry.art,
                            albumName = entry.album,
                            albumId = entry.albumId,
                            artistId = entry.artistId,
                        )
                    val liked = id in likedIds
                    out[id] = Candidate(song, score(entry, liked, cached), reason(entry, liked))
                }

                // Phase 2, radio tail: what would play next after the three
                // heaviest tracks. One request per seed, capped — discovery
                // without crawling the catalogue.
                merged.filter { it.plays >= MIN_PLAYS }.take(3).forEach { seed ->
                    runCatching { YtMusicRepository.radio(seed.id).getOrNull().orEmpty() }
                        .getOrDefault(emptyList())
                        .drop(1)
                        .take(8)
                        .forEach { song ->
                            val id = song.videoId
                            if (id.isBlank() || id in saved || id in active) return@forEach
                            if (isEvicted(context, id)) return@forEach
                            out.putIfAbsent(id, Candidate(song, RADIO_SCORE, "radio"))
                        }
                }
            }

            if (wantLiked) {
                // Phase 2, playlist tail: the first rows of the first library
                // playlists. Bounded (3 playlists × 10 rows) so a huge
                // library cannot turn one sync into a crawl.
                runCatching { YtMusicRepository.libraryPlaylists().getOrNull().orEmpty() }
                    .getOrDefault(emptyList())
                    .mapNotNull { it.browseId }
                    .take(3)
                    .forEach { browseId ->
                        runCatching { YtMusicRepository.browseSongs(browseId).getOrNull()?.songs.orEmpty() }
                            .getOrDefault(emptyList())
                            .take(10)
                            .forEach { song ->
                                val id = song.videoId
                                if (id.isBlank() || id in saved || id in active) return@forEach
                                if (isEvicted(context, id)) return@forEach
                                out.putIfAbsent(id, Candidate(song, PLAYLIST_SCORE, "playlist"))
                            }
                    }
            }

            out.values.sortedByDescending { it.score }
        }

    // ── Liked library ─────────────────────────────────────────────────────

    private var likedCacheAt = 0L
    private var likedCache: List<Song> = emptyList()

    /**
     * Every liked track, not just the first page `library()` carries (~100
     * rows). Follows continuations to the end, guarded against a token
     * repeating. Cached for [LIKED_CACHE_TTL_MS]: a 2.5k library is two dozen
     * round trips, and tapping Download now twice in a row should not pay
     * that twice — likes barely move on that timescale anyway.
     *
     * A failed refresh keeps the stale list rather than blinding the sync;
     * signed out (or never fetched) reads as empty, same as before.
     */
    private suspend fun libraryLikedFull(): List<Song> {
        // On mobile data a stale list beats a fresh crawl: two dozen round
        // trips per sync is exactly the drain the stingy cap is there to
        // avoid. Unmetered always re-crawls past the TTL.
        if (AppSettings.meteredConnection.value == true && likedCache.isNotEmpty()) {
            return likedCache
        }
        val now = System.currentTimeMillis()
        if (now - likedCacheAt < LIKED_CACHE_TTL_MS && likedCache.isNotEmpty()) {
            return likedCache
        }
        val first = runCatching {
            YtMusicRepository.browseSongs(YtMusicRepository.LIKED_MUSIC).getOrNull()
        }.getOrNull()
        if (first == null) return likedCache
        val out = first.songs.toMutableList()
        val seenTokens = HashSet<String>()
        var next = first.continuation
        while (next != null && seenTokens.add(next)) {
            val page = runCatching { YtMusicRepository.moreSongs(next).getOrNull() }.getOrNull()
            if (page == null) break
            out.addAll(page.songs)
            next = page.continuation
        }
        likedCache = out
        likedCacheAt = now
        return out
    }

    /**
     * A rating changed the liked set out from under the cache: a fresh like
     * has no [Song] here to slot in, so the whole list re-crawls next sync;
     * an unlike just drops its rows, which is exact.
     */
    fun noteRatingChanged(videoId: String, status: LikeStatus) {
        if (status == LikeStatus.LIKE) {
            likedCacheAt = 0L
        } else {
            likedCache = likedCache.filterNot { it.videoId == videoId }
        }
    }

    /** Ratings belong to the account that made them; sign-out drops the list. */
    fun clearLikedCache() {
        likedCache = emptyList()
        likedCacheAt = 0L
    }

    // ── Scoring ───────────────────────────────────────────────────────────

    private suspend fun statsById(): Map<String, TrackEntry> = withContext(Dispatchers.IO) {
        val merged = LinkedHashMap<String, TrackEntry>()
        runCatching { ListeningStats.exportAll() }.getOrDefault(emptyList()).forEach { bucket ->
            bucket.tracks.forEach { t ->
                val e = merged[t.id]
                if (e == null) merged[t.id] = t.copy() else e.absorb(t)
            }
        }
        merged
    }

    /**
     * Same shape as the research scoring: frequency + recency bonus for
     * liked, minus skips. More listening = more points, skip = fewer.
     * Liked tracks unplayed for over two weeks get a "forgotten favorite"
     * bump: those are exactly the ones a listener is glad to rediscover.
     */
    internal fun score(entry: TrackEntry?, liked: Boolean, cached: Boolean = false): Double {
        if (entry == null) return (if (liked) 1.0 else 0.0) + (if (cached) CACHED_BONUS else 0.0)
        val playsNorm = (entry.plays / 10.0).coerceIn(0.0, 1.0)
        val ageDays = ((System.currentTimeMillis() - entry.last) / DAY_MS.toDouble()).coerceAtLeast(0.0)
        val recency = when {
            ageDays <= 7 -> 1.0
            ageDays <= 30 -> 0.5
            ageDays <= 90 -> 0.25
            else -> 0.1
        }
        val skipPenalty = entry.skips * 0.15
        val forgotten = if (liked && ageDays > 14) 0.3 else 0.0
        return (if (liked) 1.0 else 0.0) + playsNorm * 0.6 + recency * 0.4 + forgotten - skipPenalty +
            (if (cached) CACHED_BONUS else 0.0)
    }

    private fun reason(entry: TrackEntry?, liked: Boolean): String = when {
        entry == null && liked -> "polubione"
        entry == null -> "propozycja"
        liked && entry.plays >= MIN_PLAYS -> "${entry.plays}× odsłuchań · polubione"
        liked -> "polubione"
        else -> "${entry.plays}× odsłuchań"
    }

    private fun estimateBytes(quality: DownloadQuality): Long = when (quality) {
        DownloadQuality.STANDARD -> 4L * 1024 * 1024
        DownloadQuality.HIGH -> 8L * 1024 * 1024
        DownloadQuality.LOSSLESS -> 35L * 1024 * 1024
    }

    private fun freeBytes(context: Context): Long =
        runCatching { File(context.filesDir, ".").usableSpace }.getOrDefault(Long.MAX_VALUE)
}
