package com.music.bitchord.playback

import android.content.Context
import android.media.MediaDataSource
import android.net.Uri
import android.os.SystemClock
import com.music.bitchord.data.TrackLog
import com.music.bitchord.playback.smart.AutomixAnalysisSource
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
import androidx.media3.datasource.cache.ContentMetadataMutations
import androidx.media3.datasource.cache.SimpleCache
import java.io.IOException
import com.music.bitchord.data.innertube.StreamResolver
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.sources.SourceRegistry
import com.music.bitchord.data.sources.SourceResolver
import com.music.bitchord.data.sources.TrackMatcher
import com.music.bitchord.download.Downloads
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
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
     * What [cacheWholeOnce] risks before committing to a whole [CHUNK_BYTES].
     *
     * Sized to answer one question — did this write land at all — as cheaply
     * as that question can be asked, not to be worth anything on its own.
     */
    private const val LOCK_PROBE_BYTES = 64L * 1024

    /**
     * How far into a rendition [cachedPrefixBytes] looks when its real length
     * isn't known yet. Only an upper bound on the answer, so it costs nothing
     * to set well past the half-minute of audio any caller actually wants —
     * eight megabytes covers that even for lossless.
     */
    private const val HEAD_PROBE_BYTES = 8L * 1024 * 1024

    /**
     * What [requestAnalysisHead] asks for when the track's real size can't be
     * had.
     *
     * Twelve seconds of audio is what the head pass needs and four megabytes
     * clears that for anything short of lossless. Only reached when
     * [StreamResolver] cannot say how long the file is, which is rare — it has
     * just resolved the stream — and a blind request is the one case where
     * spending more would be the listener's data spent on a guess.
     */
    private const val MAX_ANALYSIS_HEAD_BYTES = 4L * 1024 * 1024

    /**
     * The most [requestAnalysisHead] will pull for one track when its size *is*
     * known.
     *
     * A bound rather than "the whole file, always": a substituted lossless
     * rendition runs to thirty or forty megabytes, and the analyzer only ever
     * reads the head and the tail. Sixteen covers every YouTube Opus stream in
     * full — a ten-minute track at 160 kbps is twelve — which is the case this
     * exists for.
     *
     * Taking the whole file in one request is also what keeps the entry
     * single-sourced; see [analysisHeadSize].
     */
    private const val MAX_ANALYSIS_TRACK_BYTES = 16L * 1024 * 1024

    /**
     * How long [renditionKeysFor]'s answer is reused. Short enough that a
     * rendition which just started downloading is picked up well inside the
     * several seconds an analysis takes anyway.
     */
    private const val RENDITION_KEYS_TTL_MS = 5_000L

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
        // Builds before album/explicit/video-aware and credit-aware matching
        // may have cached a completely different recording under a YouTube
        // track's `#alt` key.
        // Those bytes otherwise outlive the matcher fix: playback serves them
        // without resolving, then refuses the correct 320kbps replacement as
        // no quality gain. Drop only the substitution-capable entries once;
        // ordinary YouTube cache entries remain warm.
        val state = context.getSharedPreferences(CACHE_STATE_PREFS, Context.MODE_PRIVATE)
        if (state.getInt(KEY_MATCHING_SCHEMA, 0) < MATCHING_SCHEMA) {
            val stale = cache.keys.filter { it.endsWith(ALT_SUFFIX) }
            stale.forEach { runCatching { cache.removeResource(it) } }
            state.edit().putInt(KEY_MATCHING_SCHEMA, MATCHING_SCHEMA).apply()
            TrackLog.d(TAG, "invalidated ${stale.size} source cache entries after matcher upgrade")
        }
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
        val about = mediaIdIn(uri)
        val family = uri.getQueryParameter("v")?.let { videoId ->
            cache.keys.filter { it == videoId || it.startsWith("$videoId#") }
        } ?: emptyList()
        (family + exact).distinct().forEach { key ->
            runCatching { cache.removeResource(key) }
                .onSuccess { TrackLog.d(TAG, "discarded cache entry $key", about = about) }
                .onFailure { TrackLog.d(TAG, "cache entry $key still in use: ${it.message}", about = about) }
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
            .onSuccess { TrackLog.d(TAG, "discarded unused rendition $key", about = mediaIdIn(uri)) }
            .onFailure { TrackLog.d(TAG, "rendition $key still in use: ${it.message}", about = mediaIdIn(uri)) }
    }

    /**
     * Throws away one named rendition of [uri] because its bytes cannot be
     * decoded, so the next attempt starts from a clean copy.
     *
     * The analyzer can tell a corrupt file from a merely incomplete one — a
     * container that reports two and a half minutes and decodes fourteen seconds
     * is not still downloading — but until this existed, knowing that led
     * nowhere. It recorded the rendition as bad and moved on, the bytes stayed on
     * disk looking complete, and every later attempt (including after a restart,
     * which clears that memory) re-read the same file and reached the same
     * answer. Two tracks were observed stuck that way permanently.
     *
     * Refuses to touch the rendition the player is currently reading from, whose
     * bytes are by definition fine — the decode that failed was of a *sibling*
     * copy. [Cache.removeResource] would also throw on the live entry's lock, but
     * relying on that would mean asking for playback's bytes to be deleted and
     * being saved by a race.
     *
     * Runs on the caller's thread; call it off the main one.
     *
     * @return true when the bytes are actually gone, which the caller must treat
     *   as "stop holding this key against the track" — a copy that no longer
     *   exists cannot be the reason to refuse the one replacing it.
     */
    fun discardBadRendition(uri: Uri, key: String): Boolean {
        if (!::cache.isInitialized) return false
        val about = mediaIdIn(uri)
        if (key == keyFactory.buildCacheKey(DataSpec(uri))) {
            TrackLog.d(TAG, "keeping undecodable rendition $key: it is the one playing", about = about)
            return false
        }
        return runCatching { cache.removeResource(key) }
            .onSuccess {
                TrackLog.d(TAG, "discarded undecodable rendition $key", about = about)
                // Deleting the bytes is only half of it. The head fetch runs once
                // per track per session, so without this the entry it just made
                // room for is never refilled and the discard buys nothing.
                uri.getQueryParameter("v")?.let(analysisHeads::remove)
            }
            .onFailure {
                TrackLog.d(TAG, "undecodable rendition $key still in use: ${it.message}", about = about)
            }
            .isSuccess
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

    private const val CACHE_STATE_PREFS = "audio_cache_state"
    private const val KEY_MATCHING_SCHEMA = "matching_schema"
    private const val MATCHING_SCHEMA = 2
    private const val ALT_SUFFIX = "#alt"

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

    /**
     * As [cacheFactory], minus [CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR] —
     * for [fetch] alone, never for playback.
     *
     * That flag exists so a playback read whose *write* fails still serves
     * the listener their audio; a read-ahead fetch has no listener to serve,
     * so hiding the same failure just spends their data reading bytes onto
     * the floor. Measured: read-ahead for a track the player had already
     * reached — its cache entry locked by the real reader, exactly the "lost
     * race" [fetchWhole] is meant to give up on cheaply — instead read a
     * full [CHUNK_BYTES] from the network on every one of [MAX_ATTEMPTS]
     * retries, because the flag turned the lock exception into a silent,
     * uncached pass-through rather than the failure [fetch]'s own
     * `runCatching` is written to catch. Nine megabytes on one ordinary,
     * unskipped track change, for a fetch that cached nothing and was always
     * going to. Without the flag, losing the race throws before a byte is
     * read, and every attempt past the first costs nothing.
     */
    private fun readAheadCacheFactory(upstream: DataSource.Factory) = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(upstream)
        .setCacheKeyFactory(keyFactory)

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
    fun prefetchQueue(upcoming: List<Upcoming>) {
        val mediaIds = upcoming.map { it.mediaId }
        if (mediaIds == pendingQueue) return
        android.util.Log.d("BCFetchDebug", "prefetchQueue: head ${pendingQueue.firstOrNull()} -> ${mediaIds.firstOrNull()}")
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
            // A track already on disk needs no reading ahead, and read-ahead
            // speaks only to googlevideo: warming one would spend mobile data
            // fetching a second copy of a file the listener deliberately saved,
            // then cache it under a key playback is never going to ask for —
            // it plays the download instead. See [Song.toMediaItem].
            .filter { it !in Downloads.saved.value }
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
        val substitutable = SourceResolver.canSubstituteForYouTube()
        job = videoIds.firstOrNull()?.let { next ->
            val target = upcoming.firstOrNull { it.mediaId == next }?.target
            scope.launch {
                // With substitution on, the *bytes* half above used to be
                // switched off outright, and the paragraph explaining why is
                // still correct as far as it goes: read-ahead resolving to
                // YouTube on its own would write Opus into the entry a
                // higher-ranked source is about to fill.
                //
                // What it treated as impossible was knowing the answer in
                // advance. A quick source can be asked *here* — see
                // [SourceResolver.prefetchSubstitute] — and once its stream is
                // recorded in [StreamChoice], the question stops being open:
                // every later resolve for this track, read-ahead's own included,
                // is held to that one stream. Both writers then agree on the
                // file and on the `#alt` key it lands under, which is exactly
                // the condition the byte half was missing.
                //
                // A source that is disabled, doesn't have the track, or fails
                // leaves nothing pinned, and this falls through to the same
                // URL-only warm-up it did before — YouTube resolves the track at
                // playback time as usual.
                val warmed = if (substitutable && target != null) {
                    runCatching { SourceResolver.prefetchSubstitute(target) }
                        .onFailure { TrackLog.d(TAG, "warm-up substitute failed for $next: ${it.message}", about = next) }
                        .getOrNull()
                        ?.also { StreamChoice.remember(next, it, substituted = true) }
                } else {
                    null
                }
                // Safe to fill for the same reason in both cases: either nothing
                // outranks YouTube and read-ahead is the only writer, or a
                // source has been pinned and every writer now resolves to it.
                val cacheBytes = !substitutable || warmed != null
                if (cacheBytes) {
                    launch(TrackLog.about(next)) {
                        delay(PREFETCH_DELAY_MS)
                        fetch(next, 0, PRELOAD_BYTES)
                        fetchWhole(next)
                    }
                }
                launch {
                    delay(PREFETCH_DELAY_MS)
                    for (id in videoIds.take(QUEUE_LOOKAHEAD + 1).let { if (cacheBytes) it.drop(1) else it }) {
                        // A track already pinned to another source has no use
                        // for a YouTube URL: nothing will ask for one, and
                        // minting it spends a client walk to fill a cache entry
                        // that is never read.
                        if (id == next && warmed != null) continue
                        runCatching { StreamResolver.resolve(id) }
                            .onFailure { TrackLog.d(TAG, "queue warm-up skipped $id: ${it.message}", about = id) }
                        delay(QUEUE_RESOLVE_STAGGER_MS)
                    }
                }
            }
        }
    }

    /**
     * A queued track as read-ahead needs it.
     *
     * [target] is what a cross-source match is made on, and read-ahead cannot
     * reach it any other way: it runs for tracks that are not the current item,
     * so the session's metadata is the wrong track's, and the plain
     * `bitchord://watch?v=…` URI it builds for itself carries an id and nothing
     * else. It rides along from the queue instead — see
     * [PlaybackService.prefetchAround][com.music.bitchord.playback.PlaybackService].
     */
    data class Upcoming(val mediaId: String, val target: TrackMatcher.Target)

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
            // The race this retry loop exists to cover is the *queue's own*:
            // cancelling [job] tells a blocking network read to stop, but that
            // takes until its next checkpoint, not instantly — so the walk
            // that lost the entry to the player can still be a retry or two
            // into asking for it again by the time [prefetchQueue] has moved
            // this track's job on to a different one. Re-checking here is
            // what makes that overlap cost one interrupted read instead of
            // up to four full ones: once this videoId is no longer the track
            // [pendingQueue] actually wants read ahead, every further attempt
            // is spent on a track something else now owns, and asking again
            // in five seconds would only be wrong for longer.
            if (pendingQueue.firstOrNull() != videoId) {
                TrackLog.d(TAG, "$videoId is no longer the read-ahead target; stopping", about = videoId)
                return
            }
            if (cacheWholeOnce(videoId)) return
            delay(RETRY_DELAY_MS)
        }
        TrackLog.d(TAG, "stopped short of caching $videoId in full", about = videoId)
    }

    /** @return true once every range of [videoId] is on disk. */
    private suspend fun cacheWholeOnce(videoId: String): Boolean {
        val total = runCatching { StreamResolver.contentLength(videoId) }.getOrNull()
            ?: return false

        var position = 0L
        while (position < total) {
            // Checked per chunk, not just once per pass: a track long enough
            // to need several chunks can lose the race partway through one,
            // and a queue change mid-pass is exactly the "the player has it
            // now" case the guard in [fetchWhole] exists for.
            if (pendingQueue.firstOrNull() != videoId) return false
            val length = minOf(CHUNK_BYTES, total - position)
            if (cache.getCachedBytes(videoId, position, length) < length) {
                fetch(videoId, position, length)
                // Written nowhere means the entry is held elsewhere; the rest
                // of this pass would be just as wasted. See [fetch] for why
                // this can be true even though the fetch just above returned
                // without error.
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
     * Video ids whose analysis copy has already been asked for this session.
     *
     * A set, not a size: the fetch is sized once from the track's real length
     * rather than grown into over several rounds — see [analysisHeadSize].
     * Cleared for a track whose copy turns out to be undecodable, so discarding
     * it leads to a fresh pull rather than to nothing.
     */
    private val analysisHeads = ConcurrentHashMap<String, Boolean>()

    /** Video ids with a head fetch in the air, so a tick cannot stack another on top. */
    private val analysisHeadsInFlight = ConcurrentHashMap.newKeySet<String>()

    /**
     * Pulls [uri]'s recording onto disk under the plain YouTube key, so Smart
     * Fade has something to measure; sized by [analysisHeadSize].
     *
     * ## Why the analyzer has to ask for this
     *
     * Nothing else fetches a track's opening early enough. The analyzer can only
     * read bytes some other part of the app happened to write, at the offsets it
     * happened to need, and neither of the two writers produces a head in time:
     *
     *  - Read-ahead's *byte* half is switched off outright whenever source
     *    substitution is on — see [prefetchQueue] — so a track that has never
     *    been played holds nothing at all until it starts playing. Measured, the
     *    next track's analysis then lands eleven to forty seconds late, which is
     *    after the transition it was meant to inform and sometimes after the
     *    track has started.
     *  - A quality upgrade fetches its rendition from the swap point onward, so
     *    the upgraded copy's first seconds are a region nothing downloads on its
     *    own, and the copy it replaced is discarded. A track that plays lossless
     *    from the start is therefore permanently unmeasurable however long it
     *    sits in the cache.
     *
     * ## Why this one is safe when [prefetchQueue]'s bytes are not
     *
     * The hazard read-ahead was disabled over is a key that disagrees with its
     * contents. [keyFactory] decides between `videoId` and `videoId#alt` from
     * the *global* substitution setting rather than from what a request actually
     * resolved to, so a fetch built from an id alone — which always resolves to
     * YouTube, carrying none of the title and artist a substitution is matched
     * on — wrote Opus bytes into the entry playback was filling from another
     * source. That is what produced `No valid varint length mask found` at the
     * seam.
     *
     * Here the key is pinned to the plain videoId rather than derived, so the
     * bytes and the entry they land in are both unambiguously YouTube's, and the
     * `#alt` and `#<rendition>` entries are untouched. The analyzer reaches this
     * copy through [renditionsOf] and cross-checks its container duration before
     * borrowing a beat grid across renditions, so a substituted source that
     * turns out to be a different cut is caught there.
     *
     * ## Why it fetches once, at full size
     *
     * An earlier version started at a megabyte and grew the request on each
     * later call, so that a track whose first attempt did not decode was not
     * stuck forever. That cured the dead end and caused a worse fault: every
     * round resolves the stream again, and the second round skips what is
     * already cached and writes the *remainder of a different rendition* into the
     * same entry. See [analysisHeadSize].
     *
     * The dead end it was solving is now answered from the other side. A copy
     * that cannot be decoded is deleted rather than remembered — see
     * [discardBadRendition] — which both frees the entry and re-arms this, so the
     * retry is a clean pull instead of a larger read of the same bad bytes.
     *
     * A no-op for anything that isn't a YouTube-backed track.
     */
    fun requestAnalysisHead(uri: Uri) {
        if (!::cache.isInitialized) return
        if (upstreamFactory == null) return
        val videoId = uri.getQueryParameter("v") ?: return
        // One round per track per session, and it is sized correctly up front
        // rather than grown into. See [analysisHeadSize] for why growing it was
        // the wrong shape.
        if (analysisHeads.putIfAbsent(videoId, true) != null) return
        // Checked before the round is claimed, so the playback thread pays a set
        // lookup per tick rather than queueing coroutines four times a second on
        // top of a fetch that is still running.
        if (!analysisHeadsInFlight.add(videoId)) return
        scope.launch {
            try {
                val total = runCatching { StreamResolver.contentLength(videoId) }.getOrNull() ?: 0L
                val want = analysisHeadSize(total)
                if (!clearPartialHead(videoId, want)) return@launch
                fetch(
                    cacheKey = videoId,
                    // Do not inherit a JioSaavn/lossless StreamChoice from
                    // playback. The base key is reserved for the lightweight
                    // YouTube Opus copy used by Automix analysis.
                    uri = Uri.parse(AutomixAnalysisSource.opusUri(videoId)),
                    position = 0,
                    length = want,
                    pinKey = true,
                )
                if (total > 0) recordContentLength(videoId, total)
            } finally {
                analysisHeadsInFlight.remove(videoId)
            }
        }
    }

    /**
     * How much of a track to pull for the analyzer, decided once from its real
     * size.
     *
     * This used to start at a megabyte and grow by [HEAD_ESCALATION] on each
     * later call, which was the wrong shape for a reason this file documents
     * elsewhere — see [discardRendition]. Each round resolves the stream again,
     * and YouTube does not promise the same rendition twice. Round one wrote a
     * megabyte of one encoding; round two skipped what was already cached and
     * wrote the *remainder of a different one* into the same entry. The result is
     * a contiguous, correctly sized, complete-looking file that decodes for a few
     * seconds and then stops at the seam — which is exactly what two tracks were
     * observed doing, one decoding 14.4s of a 153s container and another 35.5s of
     * 211.5s, both while the cache called them complete.
     *
     * One resolve, one range, one encoding. [StreamResolver] has the length
     * already, from resolving the stream, so asking first costs nothing.
     */
    private fun analysisHeadSize(total: Long): Long =
        if (total > 0) minOf(total, MAX_ANALYSIS_TRACK_BYTES) else MAX_ANALYSIS_HEAD_BYTES

    /**
     * Makes sure [videoId]'s entry is either empty or already covers [want]
     * before anything writes to it.
     *
     * A part-filled entry is the seam hazard in [analysisHeadSize] waiting to
     * happen: whatever is there came from an earlier resolve — a fetch this
     * session cancelled, or a round from a build that still escalated — and
     * filling the gap would splice a second encoding onto it. Cheaper to throw
     * the prefix away and pull one clean copy.
     *
     * @return false when the caller should not fetch: either the entry is
     *   already complete enough, or it is held by a live reader and cannot be
     *   cleared, in which case writing would create the very seam this avoids.
     */
    private fun clearPartialHead(videoId: String, want: Long): Boolean {
        val held = cache.getCachedBytes(videoId, 0, want)
        if (held <= 0L) return true
        if (held >= want) return false
        return runCatching { cache.removeResource(videoId) }
            .onSuccess {
                TrackLog.d(TAG, "cleared partial analysis head for $videoId ($held of $want)", about = videoId)
            }
            .onFailure {
                TrackLog.d(TAG, "partial head for $videoId is in use: ${it.message}", about = videoId)
            }
            .isSuccess
    }

    /**
     * Writes [videoId]'s real size into its cache metadata, which a bounded
     * fetch does not.
     *
     * Media3 records a resource's length from a response that describes the
     * whole resource. [requestAnalysisHead] asks for a megabyte, so the response
     * describes a megabyte, and the entry is left with no length at all — which
     * is not a cosmetic gap. Everything downstream divides by it: the analyzer
     * ranks renditions by how much *audio* each holds, so a length of zero makes
     * a freshly fetched head score zero seconds and lose to any sibling holding
     * a sliver of an unusable one. That is how a track whose opening had just
     * been downloaded went on reading as having nothing.
     *
     * Cheap because [StreamResolver] has the number already: it was read to
     * resolve the stream this fetch just pulled from.
     */
    private fun recordContentLength(videoId: String, total: Long) {
        if (ContentMetadata.getContentLength(cache.getContentMetadata(videoId)) > 0) return
        if (total <= 0) return
        runCatching {
            cache.applyContentMetadataMutations(
                videoId,
                ContentMetadataMutations().apply { ContentMetadataMutations.setContentLength(this, total) },
            )
        }.onFailure { TrackLog.d(TAG, "could not record length for $videoId: ${it.message}", about = videoId) }
    }

    /**
     * True once every byte of [uri]'s rendition is on disk.
     *
     * Automix's analyzer needs this before it can safely decode a track: a
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
     * One rendition of a recording, as Automix's analyzer sees it.
     *
     * [key] is a cache key, not a URI: the point of this type is to name a
     * rendition the *player* is not using, which no URI in hand refers to.
     */
    data class Rendition(val key: String, val contentLength: Long, val cachedPrefix: Long) {
        val isComplete: Boolean get() = contentLength > 0 && cachedPrefix >= contentLength
    }

    /**
     * Every rendition of [uri]'s recording that is on disk, cheapest first.
     *
     * One videoId owns up to three cache entries — the Opus stream YouTube
     * serves, a substituted source's copy (`#alt`), and a quality upgrade
     * (`#<rendition>`); see [keyFactory]. They hold the same *music*, so an
     * analysis of any of them describes all of them, and analysing the smallest
     * one costs a fraction of what the largest does. This is what lets a track
     * be analysed off a 3 MB Opus stream that finished downloading a minute ago
     * instead of waiting on the 40 MB FLAC that replaced it.
     *
     * Ordered by size because decode time tracks bytes, and because the
     * lightest complete rendition is the one that was available earliest.
     *
     * Callers must still check the durations agree before treating two
     * renditions as interchangeable — see [TrackAnalyzer]. A `#alt` entry comes
     * from a different source and can be a different cut of the same song,
     * where a shared beat grid would put every anchor seconds off.
     */
    fun renditionsOf(uri: Uri): List<Rendition> {
        if (!::cache.isInitialized) return emptyList()
        val videoId = uri.getQueryParameter("v")
            ?: return listOfNotNull(renditionFor(keyFactory.buildCacheKey(DataSpec(uri))))
        return renditionKeysFor(videoId)
            .mapNotNull(::renditionFor)
            .sortedBy { it.contentLength }
    }

    /**
     * Which cache keys belong to [videoId], memoized.
     *
     * [SimpleCache.getKeys] copies the entire key set on every call, and this
     * runs on the main thread twice per crossfade tick — four times a second,
     * against a cache holding every track ever played. Which *renditions* exist
     * changes only when a new one starts downloading, so it is safe to hold for
     * a few seconds; how much of each is on disk is not memoized and is still
     * read fresh every time.
     */
    private fun renditionKeysFor(videoId: String): List<String> {
        val now = SystemClock.elapsedRealtime()
        renditionKeys[videoId]?.let { (at, keys) ->
            if (now - at < RENDITION_KEYS_TTL_MS) return keys
        }
        val keys = cache.keys.filter { it == videoId || it.startsWith("$videoId#") }
        renditionKeys[videoId] = now to keys
        return keys
    }

    private val renditionKeys = ConcurrentHashMap<String, Pair<Long, List<String>>>()

    /** The cache key [uri] itself resolves to right now — the rendition the player is using. */
    fun cacheKeyOf(uri: Uri): String = keyFactory.buildCacheKey(DataSpec(uri))

    private fun renditionFor(key: String): Rendition? {
        val contentLength =
            ContentMetadata.getContentLength(cache.getContentMetadata(key)).coerceAtLeast(0L)
        val probe = contentLength.takeIf { it > 0 } ?: HEAD_PROBE_BYTES
        val prefix = cache.getCachedLength(key, 0, probe).coerceAtLeast(0L)
        return if (prefix <= 0L) null else Rendition(key, contentLength, prefix)
    }

    /**
     * A reader over one named [Rendition], whatever the player is currently
     * using.
     *
     * The key is pinned rather than derived, because [keyFactory] resolves a
     * YouTube URI to whichever rendition is live *now* — which is precisely the
     * heavy one this exists to avoid reading. The URI is still passed along for
     * [CacheDataSource] to open against; only the key decides which bytes come
     * back.
     */
    fun renditionDataSource(uri: Uri, rendition: Rendition): MediaDataSource =
        CacheMediaDataSource(
            CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(NoUpstream)
                .setCacheKeyFactory { rendition.key }
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                .createDataSource(),
            uri,
        )

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
                throw IOException("Automix analysis reads only cached bytes; no upstream is wired up")
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                throw IOException("Automix analysis reads only cached bytes; no upstream is wired up")
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

    /**
     * [pinKey] forces the write to land under [cacheKey] instead of wherever
     * [keyFactory] would put it.
     *
     * Worth spelling out because the two are not otherwise the same thing:
     * [cacheKey] is only consulted for the "is this already here" check below,
     * while the entry actually written is chosen by [keyFactory] from the spec —
     * and [keyFactory] answers partly from the global substitution setting, not
     * from what this request resolves to. A caller that knows exactly what it is
     * fetching, as [requestAnalysisHead] does, is better off saying so than
     * letting a global decide on its behalf.
     */
    private suspend fun fetch(
        cacheKey: String,
        uri: Uri,
        position: Long,
        length: Long,
        pinKey: Boolean = false,
    ) {
        if (upstreamFactory == null) return
        if (cache.getCachedBytes(cacheKey, position, length) >= length) return

        // A cheap first knock rather than the whole range on the door.
        // Losing this entry to another writer isn't something
        // [CacheDataSource] surfaces as a failure — it quietly falls through
        // to the network and hands the bytes to nobody, which looks exactly
        // like a real fetch until the write is checked afterwards, because
        // that check has always been the only way to tell "nobody's home"
        // from "got it". Measured without this: a read-ahead fetch that had
        // lost that race read a full [CHUNK_BYTES] from the network, found
        // nothing had landed, and paid that again on every one of
        // [MAX_ATTEMPTS] retries — nine megabytes for a track that was never
        // going to cache, because whoever held the entry held it the whole
        // time. A small probe reaches the same verdict for a fraction of
        // the cost, and only a probe that actually lands is worth following
        // with the rest of the range.
        if (length > LOCK_PROBE_BYTES) {
            pull(cacheKey, uri, position, LOCK_PROBE_BYTES, pinKey)
            if (cache.getCachedBytes(cacheKey, position, LOCK_PROBE_BYTES) < LOCK_PROBE_BYTES) return
        }
        pull(cacheKey, uri, position, length, pinKey)
    }

    /** The actual network pull behind [fetch], unconditional and unchecked. */
    private suspend fun pull(cacheKey: String, uri: Uri, position: Long, length: Long, pinKey: Boolean) {
        val upstream = upstreamFactory ?: return

        // Whose track this is, taken off the URI rather than off [cacheKey]:
        // the key splits a track's renditions apart on purpose, and reading
        // ahead is the clearest case there is of work logged nowhere near the
        // track it is for. See [TrackLog.about].
        val about = mediaIdIn(uri)
        // Read-ahead is the app's largest consumer of bandwidth and, until this
        // line existed, its most invisible: whole tracks were pulled down while
        // a listener waited on a resolve for the track in front of them, and
        // nothing in the log said so. Bracketing it is what makes the overlap
        // between "reading ahead" and "waiting for sound" readable at all.
        val fetchStart = SystemClock.elapsedRealtime()
        TrackLog.d(TAG, "read-ahead fetching $cacheKey [$position, ${position + length})", about = about)

        val source = readAheadCacheFactory(upstream)
            .apply { if (pinKey) setCacheKeyFactory { cacheKey } }
            .createDataSource()
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
            // Expected on a skip, and never worth failing playback over — see
            // [readAheadCacheFactory] for why this is now also the ordinary
            // shape of losing the race to the player.
            TrackLog.d(TAG, "read-ahead stopped for $cacheKey: ${it.message}", about = about)
        }.onSuccess {
            TrackLog.d(
                TAG,
                "read-ahead fetched $cacheKey [$position, ${position + length}) in " +
                    "${SystemClock.elapsedRealtime() - fetchStart}ms",
                about = about,
            )
        }
    }
}
