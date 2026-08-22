package com.music.bitchord.playback

import android.content.Context
import android.media.MediaDataSource
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.music.bitchord.data.TrackLog
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.SimpleCache
import java.io.IOException
import com.music.bitchord.data.innertube.StreamResolver
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.sources.SourceRegistry
import com.music.bitchord.data.sources.SourceResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * On-disk cache of the audio itself, and the read-ahead that fills it.
 *
 * Two problems, one cache:
 *
 *  - **Seeking.** Everything played is written to disk on the way through, so
 *    seeking back is always a file read. Seeking *forward* past what the
 *    player has buffered is the gap, and it closes once a track is on disk in
 *    full — which is why read-ahead fetches whole tracks rather than openings.
 *  - **Track changes.** The next track needs a stream URL resolved (an
 *    Innertube round trip, plus running YouTube's player JavaScript to
 *    de-obfuscate the `n` parameter) before its first byte can even be asked
 *    for. Fetching its opening ahead of time moves all of that off the gap
 *    between songs.
 *
 * The cache is keyed by videoId, not by URL: googlevideo URLs are single-use,
 * expire within hours, and differ between resolves of the same track, so
 * keying on them would cache every track afresh on every play. Because
 * [CacheDataSource] sits *outside* the resolving data source it sees the
 * original `bitchord://watch?v=<id>` request, and a cache hit never resolves a
 * URL at all.
 */
@UnstableApi
object AudioCache {

    private const val TAG = "BitChord"

    /**
     * The disk budget, straight from [AppSettings] — 512MB by default, roughly
     * 150 tracks at the highest bitrate offered, adjustable up to 10GB from
     * Settings. Least-recently-used entries are dropped past it, so it's a
     * ceiling rather than something the listener has to manage day to day.
     */
    private val evictor = DynamicLruCacheEvictor(AppSettings.DEFAULT_CACHE_LIMIT_BYTES)

    /**
     * How much of the next track to fetch. About 50 seconds at 160kbps — long
     * enough that playback starts instantly and keeps going while the rest
     * streams, without spending the listener's data on a track they may well
     * skip past.
     */
    private const val PRELOAD_BYTES = 1L * 1024 * 1024

    /**
     * Size of each range the whole-track fetch asks for.
     *
     * Ranges, not one long read, because googlevideo paces a continuous
     * response down to roughly playback speed after the first megabyte or so —
     * a track fetched that way finishes caching around the time it finishes
     * playing, which is far too late to be worth anything to a seek. Bounded
     * ranges are served at line rate: two megabytes lands in about a third of a
     * second on this connection, against seventy seconds streamed.
     */
    private const val CHUNK_BYTES = 2L * 1024 * 1024

    /**
     * How far into a rendition [cachedPrefixBytes] looks when its real length
     * isn't known yet. Only an upper bound on the answer, so it costs nothing
     * to set well past the half-minute of audio any caller actually wants —
     * eight megabytes covers that even for lossless.
     */
    private const val HEAD_PROBE_BYTES = 8L * 1024 * 1024

    /**
     * Grace period before reading ahead. The seconds just after a track starts
     * are when the player is filling its own buffer and the listener is waiting
     * on sound; read-ahead competing for bandwidth there would trade the gap
     * between songs for a gap at the start of one. It also collapses a burst of
     * skips into a single fetch of wherever the listener lands, and leaves the
     * player's opening burst holding the cache entry alone — see [fetchWhole].
     */
    private const val PREFETCH_DELAY_MS = 8_000L

    /** How long to leave the player alone with an entry before trying again. */
    private const val RETRY_DELAY_MS = 5_000L

    /** Enough to cover a hand-over, not enough to keep chasing a lost race. */
    private const val MAX_ATTEMPTS = 4

    /**
     * How many tracks past the immediate next one get their stream URL warmed
     * ahead of time. Only the very next track is worth spending bytes on — see
     * [prefetchQueue] — but resolving a URL costs a handful of small round
     * trips, not a stream's worth of data, so paying that cost several tracks
     * early is worth it purely to keep a fast run of skips from ever landing
     * on a track that has to resolve cold.
     *
     * One, not three, and the difference is not the round trips. While every
     * player client is being refused, *every* warm-up falls through to NewPipe
     * extraction — the one step in this app that does not share out when it is
     * run concurrently, but collapses: 1.8s alone against 30.3s with three in
     * flight. Warming three tracks ahead therefore did not cost three cheap
     * resolves in the background, it cost the track the listener was waiting on
     * a thirty-second start. See
     * [StreamResolver][com.music.bitchord.data.innertube.StreamResolver]'s
     * extraction gate, which serialises what is left of that.
     */
    private const val QUEUE_LOOKAHEAD = 1

    /** Spacing between queued resolves, so warming the queue never competes with the track actually playing. */
    private const val QUEUE_RESOLVE_STAGGER_MS = 500L

    /** How many upcoming tracks are worth gathering for [prefetchQueue] — the caller doesn't need to know why. */
    const val QUEUE_DEPTH = QUEUE_LOOKAHEAD + 1

    private lateinit var cache: SimpleCache

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Kept in the app's cache directory: this is disposable by definition, and
     * that is where the system and the "clear cache" button expect to reclaim
     * it from. [SimpleCache] copes with files disappearing underneath it by
     * dropping the spans that named them.
     */
    fun init(context: Context) {
        evictor.maxBytes = AppSettings.audioCacheLimitBytes.value
        cache = SimpleCache(
            File(context.cacheDir, "audio"),
            evictor,
            StandaloneDatabaseProvider(context),
        )
        // A SimpleCache can only be opened once per process, so the ceiling
        // moves by mutating this evictor rather than reopening the cache —
        // see [DynamicLruCacheEvictor].
        scope.launch {
            AppSettings.audioCacheLimitBytes.collect { maxBytes ->
                evictor.maxBytes = maxBytes
                evictor.applyNow(cache)
            }
        }
    }

    /** Drops everything on disk. The listener asked; no grace period. */
    fun clear(onComplete: () -> Unit = {}) {
        cancel()
        scope.launch {
            cache.keys.toList().forEach { cache.removeResource(it) }
            withContext(Dispatchers.Main) { onComplete() }
        }
    }

    /**
     * Throws away everything held for the track [uri] plays, so the next open
     * fetches it again from the top.
     *
     * For when what is on disk is the problem rather than the network: a
     * half-written entry, or one filled from two different files and now
     * unreadable at the seam. Nothing here can tell which of those it is
     * looking at, so every rendition of the track goes — the `#alt` and
     * `#hifi` siblings as well as the entry named — and the cost is a
     * re-download rather than a track that cannot be played at all.
     *
     * A key still locked by a live reader can't be removed; that throw is
     * caught rather than prevented, because the alternative is holding a lock
     * of our own across the player's teardown.
     *
     * Runs on the caller's thread rather than off in [scope], so that a caller
     * about to re-open the track can be sure the old bytes are gone first.
     * Call it off the main thread.
     */
    fun discard(uri: Uri) {
        val exact = keyFactory.buildCacheKey(DataSpec(uri))
        val family = uri.getQueryParameter("v")?.let { videoId ->
            cache.keys.filter { it == videoId || it.startsWith("$videoId#") }
        } ?: emptyList()
        (family + exact).distinct().forEach { key ->
            runCatching { cache.removeResource(key) }
                .onSuccess { TrackLog.d(TAG, "discarded cache entry $key") }
                .onFailure { TrackLog.d(TAG, "cache entry $key still in use: ${it.message}") }
        }
    }

    /**
     * Throws away only the rendition [uri] names, leaving the track's other
     * entries where they are.
     *
     * [discard]'s scorched-earth pass is right when what is on disk cannot be
     * trusted and there is no telling which entry is at fault. This is for the
     * case where there is: an upgrade that was fetched and then not used — an
     * audition that failed to prove itself, a swap the player put back — has
     * written a prefix of one file under the `#hifi` key and stopped. Left
     * there, the *next* upgrade of the same track keys to that same `#hifi`
     * entry, is served the abandoned prefix, and streams a different file into
     * the middle of it. Taking the whole family instead would throw away the
     * bytes of the stream still playing, which is the one thing that is
     * definitely fine.
     *
     * Runs on the caller's thread; call it off the main one.
     */
    fun discardRendition(uri: Uri) {
        val key = keyFactory.buildCacheKey(DataSpec(uri))
        runCatching { cache.removeResource(key) }
            .onSuccess { TrackLog.d(TAG, "discarded unused rendition $key") }
            .onFailure { TrackLog.d(TAG, "rendition $key still in use: ${it.message}") }
    }

    /**
     * The videoId behind a request. Playback asks through the custom scheme;
     * read-ahead builds the same URI, so both land on one cache entry.
     */
    private val keyFactory = CacheKeyFactory { spec ->
        spec.uri.getQueryParameter("v")
            // A YouTube id can name several different recordings on disk: the
            // Opus rendition YouTube serves, whatever a source ranked above it
            // hands over instead — see [SourceResolver.substituteForYouTube] —
            // and the better copy that replaces *that* mid-track when one
            // turns up, see [QualityUpgrade]. Sharing one entry between them
            // survives neither a reorder nor a half-cached track: the next
            // play would serve a FLAC prefix and then stream Opus into the
            // middle of it. Each gets its own entry, and the duplication costs
            // a re-download rather than a corrupt file.
            //
            // Written as a `when` rather than a chain of `?.let`: the previous
            // form ended `cacheTag(...)?.let { return@let "$videoId#$it" }`,
            // where `return@let` binds to the *inner* lambda, not the outer
            // one it was meant for. The upgraded key was built, discarded as
            // an unused expression, and every upgraded track fell through to
            // the `#alt` entry belonging to the stream it had just replaced —
            // so a 320kbps AAC was written into the middle of a half-cached
            // WebM, which is the exact corruption the paragraph above exists
            // to prevent. It cost `IllegalStateException: No valid varint
            // length mask found` at the seam, and eight-second stalls before
            // that, when the swap blocked on a cache lock the outgoing reader
            // still held.
            ?.let { videoId ->
                val rendition = QualityUpgrade.cacheTag(spec.uri)
                when {
                    rendition != null -> "$videoId#$rendition"
                    SourceResolver.canSubstituteForYouTube() -> "$videoId#alt"
                    else -> videoId
                }
            }
            // A source-backed track keys on the source and its track id alone.
            // The full URI would work but carries the title and artist used
            // for cross-source matching, and the same track queued from a row
            // that spelled either of them differently would then occupy a
            // second copy of itself on disk.
            ?: spec.uri.takeIf { it.authority == "source" }?.let { uri ->
                val source = uri.getQueryParameter("s")
                val track = uri.getQueryParameter("t")
                if (source != null && track != null) "$source|$track" else null
            }
            ?: spec.key
            ?: spec.uri.toString()
    }

    /**
     * Wraps [upstream] so everything played is written to disk on the way
     * through, and anything already there is served without a request.
     * Local file and content URIs bypass disk caching to prevent redundant writes.
     */
    fun playbackFactory(upstream: DataSource.Factory): DataSource.Factory = DataSource.Factory {
        val cacheDs = cacheFactory(upstream).createDataSource()
        val upstreamDs = upstream.createDataSource()
        object : DataSource {
            private var activeDs: DataSource = cacheDs

            override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
                cacheDs.addTransferListener(transferListener)
                upstreamDs.addTransferListener(transferListener)
            }

            override fun open(dataSpec: DataSpec): Long {
                val scheme = dataSpec.uri.scheme
                activeDs = if (scheme == "file" || scheme == "content") {
                    upstreamDs
                } else {
                    cacheDs
                }
                return activeDs.open(dataSpec)
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                activeDs.read(buffer, offset, length)

            override fun getUri(): Uri? = activeDs.uri

            override fun close() {
                activeDs.close()
            }
        }
    }

    private fun cacheFactory(upstream: DataSource.Factory) = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(upstream)
        .setCacheKeyFactory(keyFactory)
        // A cache write that fails (full disk, evicted mid-write) should drop
        // to streaming, not surface as a playback error.
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /** Set once the player exists; read-ahead resolves streams the same way. */
    private var upstreamFactory: DataSource.Factory? = null

    fun setUpstream(factory: DataSource.Factory) {
        upstreamFactory = factory
    }

    private var job: Job? = null
    private var pendingQueue: List<String> = emptyList()

    /**
     * Gets the queue ahead of the one playing warmed up, in play order.
     *
     * The first id gets the full treatment: its opening onto disk first, so
     * it can start the moment it's reached, then the rest of it, so that
     * seeking around it is a disk read from the first second it plays. Only
     * that one track — never the one playing, and never bytes for anything
     * further out. Media3 locks a cache entry to a single writer and the
     * player holds that lock for as long as it is streaming the track — a
     * fetch aimed at the same entry is quietly served from the network and
     * written nowhere, spending the listener's data to cache precisely
     * nothing. Caching a track before it is reached gets the same result
     * without the contention. And full-track bytes for tracks that may never
     * be reached would spend real mobile data on nothing.
     *
     * The next [QUEUE_LOOKAHEAD] ids past that one get a lighter treatment:
     * just their stream URL resolved and held in [StreamResolver]'s own
     * cache, not their bytes. That's the gap a fast run of skips actually
     * falls into — the queue moving faster than a single-track read-ahead can
     * follow it — and a resolve is cheap enough that warming several at once
     * costs nothing worth guarding.
     *
     * Called freely; a call naming the same queue as the one already running
     * is left alone, and a different one replaces it outright, since on a run
     * of skips only wherever the listener actually lands is worth chasing.
     */
    fun prefetchQueue(mediaIds: List<String>) {
        if (mediaIds == pendingQueue) return
        pendingQueue = mediaIds
        job?.cancel()
        // Both halves of the read-ahead below go through [StreamResolver],
        // which speaks YouTube ids and nothing else. A source-backed track
        // handed to it resolves to a failure, so filtering here saves a dead
        // round trip per queued track rather than changing any outcome —
        // read-ahead for those is a separate job, and their servers are
        // typically a good deal closer than googlevideo anyway.
        //
        val videoIds = mediaIds.filter { SourceRegistry.parseTrackKey(it) == null }
        // With substitution possible, only the *bytes* half drops out. Read-
        // ahead builds its own spec below from an id alone and carries none of
        // the title and artist a substitution is matched on — so it resolves
        // to YouTube and would write Opus bytes into the very entry playback
        // is about to fill from a higher-ranked source, under the same key, at
        // whatever offset each of them happened to reach. Reading ahead for a
        // track and then corrupting it is worse than not reading ahead at all.
        //
        // The URL half is a different matter and was thrown out with it, at
        // real cost. Warming [StreamResolver]'s own cache writes nothing to
        // disk and cannot corrupt anything, and it is the difference between
        // the fallback starting instantly and starting with a full client walk
        // — measured at 7.9s. Since the fallback now races the module lookup
        // rather than waiting behind it, that walk is what a track waits on
        // whenever the modules are slow, and warming it here is what makes the
        // race worth running at all.
        val cacheBytes = !SourceResolver.canSubstituteForYouTube()
        job = videoIds.firstOrNull()?.let { next ->
            scope.launch {
                if (cacheBytes) {
                    launch {
                        delay(PREFETCH_DELAY_MS)
                        fetch(next, 0, PRELOAD_BYTES)
                        fetchWhole(next)
                    }
                }
                launch {
                    delay(PREFETCH_DELAY_MS)
                    for (id in videoIds.take(QUEUE_LOOKAHEAD + 1).let { if (cacheBytes) it.drop(1) else it }) {
                        runCatching { StreamResolver.resolve(id) }
                            .onFailure { TrackLog.d(TAG, "queue warm-up skipped $id: ${it.message}") }
                        delay(QUEUE_RESOLVE_STAGGER_MS)
                    }
                }
            }
        }
    }

    /**
     * Nothing to read ahead for once playback stops. The queue is cleared with
     * the job, so resuming starts the fetch again rather than being mistaken
     * for one already in hand.
     */
    fun cancel() {
        pendingQueue = emptyList()
        job?.cancel()
        job = null
    }

    /**
     * Gets the whole of [videoId] onto disk, a range at a time.
     *
     * Progress is measured rather than assumed: a pass that caches nothing
     * means the entry is held by another writer — the listener has skipped
     * ahead and the player now owns this track — so there is no point hammering
     * it. A few spaced retries cover the hand-over, and then it is left alone.
     */
    private suspend fun fetchWhole(videoId: String) {
        repeat(MAX_ATTEMPTS) {
            if (cacheWholeOnce(videoId)) return
            delay(RETRY_DELAY_MS)
        }
        TrackLog.d(TAG, "stopped short of caching $videoId in full")
    }

    /** @return true once every range of [videoId] is on disk. */
    private suspend fun cacheWholeOnce(videoId: String): Boolean {
        val total = runCatching { StreamResolver.contentLength(videoId) }.getOrNull()
            ?: return false

        var position = 0L
        while (position < total) {
            val length = minOf(CHUNK_BYTES, total - position)
            if (cache.getCachedBytes(videoId, position, length) < length) {
                fetch(videoId, position, length)
                // Written nowhere means the entry is held elsewhere; the rest
                // of this pass would be just as wasted.
                if (cache.getCachedBytes(videoId, position, length) < length) return false
            }
            position += length
        }
        return true
    }

    /**
     * Pulls [length] bytes of whatever [uri] names into the cache, under [uri]'s
     * own key rather than the plain videoId.
     *
     * For the opening of a rendition that is about to be swapped in — see
     * [PlaybackService][com.music.bitchord.playback.PlaybackService]'s audition.
     * A player preparing a progressive source has to parse the container from
     * byte zero before it can seek anywhere, and for a FLAC that is not a few
     * bytes: STREAMINFO, the seek table, the tags and an embedded cover can run
     * to hundreds of kilobytes. Measured here, the audition itself cached only
     * `[0, 8192)` before seeking away to the playing position, so the real
     * player's very first read after the swap — the one nothing can start
     * without — was a cache miss and a round trip to the CDN, in silence.
     *
     * Call it *before* the audition rather than alongside: Media3 locks a cache
     * entry to one writer, and two writers on the same rendition means one of
     * them spends the listener's data caching nothing.
     */
    /**
     * What is actually on disk for [uri]'s rendition, as a log line.
     *
     * Here because "the audition cached it" is an assumption that has already
     * been wrong once, and the only place it can be checked is against the
     * cache itself: a swap that lands on bytes the audition was supposed to
     * have fetched looks, from the player's side, exactly like one that lands
     * on bytes it never reached.
     */
    fun cachedSummary(uri: Uri): String {
        val key = keyFactory.buildCacheKey(DataSpec(uri))
        val spans = cache.getCachedSpans(key).filter { it.isCached }
        if (spans.isEmpty()) return "$key holds nothing"
        val total = spans.sumOf { it.length }
        val ranges = spans.sortedBy { it.position }
            .joinToString(" ") { "[${it.position},${it.position + it.length})" }
        return "$key holds ${total / 1024}kB in ${spans.size} spans: $ranges"
    }

    suspend fun warmRange(uri: Uri, position: Long, length: Long) {
        val key = keyFactory.buildCacheKey(DataSpec(uri))
        fetch(key, uri, position, length)
    }

    /**
     * True once every byte of [uri]'s rendition is on disk.
     *
     * Smart Fade's analyzer needs this before it can safely decode a track: a
     * partially fetched file may not even have a parsable container, and
     * analysing the head of a track whose tail hasn't arrived would produce a
     * grid for audio the listener will never reach through that transition.
     *
     * Reads the content length Media3 already recorded against this cache key
     * (from the upstream response, the first time anything read this rendition)
     * rather than re-deriving it per source type — unlike [cacheWholeOnce],
     * which only knows how to ask [StreamResolver] for a YouTube videoId's
     * length, this works for anything that has ever been opened through
     * [cacheFactory], YouTube or not.
     */
    fun isFullyCached(uri: Uri): Boolean {
        val contentLength = contentLengthOf(uri)
        if (contentLength <= 0) return false
        // [Cache.getCachedLength], not [Cache.getCachedBytes]. The latter counts
        // every cached byte in the span *however it is scattered*, so a
        // rendition with holes in it — which is the normal result of seeking
        // around a track while read-ahead fills the rest in behind you — reports
        // the same total as a complete one. The decoder does not skip holes: it
        // stops at the first, and the analyzer then reads the entire missing
        // remainder as trailing silence and places the mix-out anchor there.
        // That is how a four-minute track came to be analysed as ending at
        // 61 seconds and transitioned out of at 56. Contiguous-from-zero is what
        // "fully cached" was always meant to mean.
        return cachedPrefixBytes(uri) >= contentLength
    }

    /**
     * The full size of [uri]'s rendition in bytes, or 0 when it isn't known yet.
     *
     * Read from the content metadata Media3 recorded from the upstream response
     * the first time anything opened this rendition, so it is available well
     * before the bytes are.
     */
    fun contentLengthOf(uri: Uri): Long {
        if (!::cache.isInitialized) return 0L
        val key = keyFactory.buildCacheKey(DataSpec(uri))
        return ContentMetadata.getContentLength(cache.getContentMetadata(key)).coerceAtLeast(0L)
    }

    /**
     * How many bytes of [uri]'s rendition are on disk *contiguously from the
     * start*, which is the only measure a head-only decode can act on: a
     * rendition holding its last megabyte and nothing else has plenty of cached
     * bytes and no parsable beginning.
     *
     * Zero when the rendition's length isn't known yet, when nothing is cached,
     * or when the cached region doesn't start at byte 0.
     */
    fun cachedPrefixBytes(uri: Uri): Long {
        if (!::cache.isInitialized) return 0L
        val key = keyFactory.buildCacheKey(DataSpec(uri))
        // Probed over a fixed span rather than the content length. The length is
        // only recorded once something has *opened* the rendition, and the whole
        // point of this measurement is a track nothing has opened yet — read-
        // ahead has written its opening bytes and nothing else has touched it.
        // Requiring the length here made this return zero for exactly the
        // tracks it exists to describe.
        val probe = contentLengthOf(uri).takeIf { it > 0 } ?: HEAD_PROBE_BYTES
        // Negative means "this many bytes of hole", i.e. position 0 isn't cached at all.
        return cache.getCachedLength(key, 0, probe).coerceAtLeast(0L)
    }

    /**
     * A random-access reader over [uri]'s cached bytes, for the analyzer to hand
     * to [android.media.MediaExtractor]. Null when the rendition isn't fully
     * cached yet — analysis always treats that as "not ready" rather than
     * reading a partial file.
     *
     * The returned source only ever reads from disk: its upstream throws if
     * touched at all, which should never happen once [isFullyCached] is true.
     * Callers must [MediaDataSource.close] it.
     */
    fun mediaDataSource(uri: Uri): MediaDataSource? {
        if (!isFullyCached(uri)) return null
        return CacheMediaDataSource(cacheFactory(NoUpstream).createDataSource(), uri)
    }

    /**
     * The same reader over a rendition that is still downloading, for Smart
     * Fade's head-only pass.
     *
     * Nothing here truncates explicitly: a read into a region that hasn't
     * arrived reaches [NoUpstream], which throws, and [CacheMediaDataSource]
     * turns that into an end-of-stream. So a partially cached rendition presents
     * itself to [android.media.MediaExtractor] as a short file that stops where
     * the cache does, which is exactly what a head-only decode wants. Its
     * declared size is still the real one, so a container whose header describes
     * the whole track parses normally.
     *
     * Callers must check [cachedPrefixBytes] first — this only refuses the case
     * where the rendition has no beginning on disk at all. Callers must
     * [MediaDataSource.close] it.
     */
    fun headMediaDataSource(uri: Uri): MediaDataSource? {
        if (cachedPrefixBytes(uri) <= 0L) return null
        return CacheMediaDataSource(cacheFactory(NoUpstream).createDataSource(), uri)
    }

    /**
     * Never fetches. For a fully cached rendition nothing should reach this; for
     * a partially cached one, throwing is how a read past the cached prefix
     * becomes an end-of-stream instead of a download.
     */
    private object NoUpstream : DataSource.Factory {
        override fun createDataSource(): DataSource = object : DataSource {
            override fun addTransferListener(transferListener: TransferListener) {}
            override fun open(dataSpec: DataSpec): Long =
                throw IOException("Smart Fade analysis reads only cached bytes; no upstream is wired up")
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                throw IOException("Smart Fade analysis reads only cached bytes; no upstream is wired up")
            override fun getUri(): Uri? = null
            override fun close() {}
        }
    }

    /**
     * Adapts a Media3 [DataSource] (reading only from [cache]) to the
     * [MediaDataSource] interface [android.media.MediaExtractor] wants.
     *
     * Keeps the underlying source open across consecutive sequential reads —
     * the pattern MediaExtractor actually uses — and only reopens at a new
     * position when the read pattern jumps, e.g. a seek.
     */
    private class CacheMediaDataSource(
        private val dataSource: DataSource,
        private val uri: Uri,
    ) : MediaDataSource() {
        private var isOpen = false
        private var openPosition = -1L

        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (size == 0) return 0
            if (!isOpen || position != openPosition) {
                closeUpstream()
                val available = try {
                    dataSource.open(DataSpec.Builder().setUri(uri).setPosition(position).build())
                } catch (error: IOException) {
                    return -1
                }
                isOpen = true
                openPosition = position
                if (available == 0L) return -1
            }
            val count = try {
                dataSource.read(buffer, offset, size)
            } catch (error: IOException) {
                closeUpstream()
                return -1
            }
            if (count == C.RESULT_END_OF_INPUT) {
                closeUpstream()
                return -1
            }
            openPosition += count
            return count
        }

        override fun getSize(): Long {
            closeUpstream()
            return try {
                dataSource.open(DataSpec(uri))
            } catch (error: IOException) {
                -1L
            } finally {
                closeUpstream()
            }
        }

        override fun close() {
            closeUpstream()
        }

        private fun closeUpstream() {
            if (isOpen) {
                runCatching { dataSource.close() }
                isOpen = false
            }
        }
    }

    /**
     * Pulls [length] bytes of [videoId] from [position] into the cache.
     * [CacheWriter] fetches only the gaps, so a range already partly on disk —
     * from a track played earlier, or skipped back to — costs only the rest.
     */
    private suspend fun fetch(videoId: String, position: Long, length: Long) =
        fetch(videoId, Uri.parse("bitchord://watch?v=$videoId"), position, length)

    private suspend fun fetch(cacheKey: String, uri: Uri, position: Long, length: Long) {
        val upstream = upstreamFactory ?: return
        if (cache.getCachedBytes(cacheKey, position, length) >= length) return

        // Read-ahead is the app's largest consumer of bandwidth and, until this
        // line existed, its most invisible: whole tracks were pulled down while
        // a listener waited on a resolve for the track in front of them, and
        // nothing in the log said so. Bracketing it is what makes the overlap
        // between "reading ahead" and "waiting for sound" readable at all.
        val fetchStart = SystemClock.elapsedRealtime()
        TrackLog.d(TAG, "read-ahead fetching $cacheKey [$position, ${position + length})")

        val source = cacheFactory(upstream).createDataSource()
        val spec = DataSpec.Builder()
            .setUri(uri)
            .setPosition(position)
            .setLength(length)
            .build()
        val writer = CacheWriter(source, spec, /* temporaryBuffer = */ null, /* listener = */ null)

        runCatching {
            withContext(Dispatchers.IO) {
                // CacheWriter blocks in a read loop and checks this flag between
                // reads; cancelling the coroutine alone would leave it running.
                val handle = coroutineContext.job.invokeOnCompletion { writer.cancel() }
                try {
                    writer.cache()
                } finally {
                    handle.dispose()
                }
            }
        }.onFailure {
            // Expected on a skip, and never worth failing playback over.
            TrackLog.d(TAG, "read-ahead stopped for $cacheKey: ${it.message}")
        }.onSuccess {
            TrackLog.d(
                TAG,
                "read-ahead fetched $cacheKey [$position, ${position + length}) in " +
                    "${SystemClock.elapsedRealtime() - fetchStart}ms",
            )
        }
    }
}
