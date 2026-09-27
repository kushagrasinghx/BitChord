package com.music.bitchord.data.sources.module

import android.util.Log
import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.Http
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Request

/**
 * A module/index URL with its host hidden, for logging.
 *
 * These URLs point at the module index the build secret supplies —
 * [SourceRegistry][com.music.bitchord.data.sources.SourceRegistry] never
 * shows it on screen (see the sources settings screen), so debug logcat
 * shouldn't hand it out either. Only the host is sensitive; the path is kept
 * so a fetch failure or cache hit is still traceable to which endpoint it was.
 */
internal fun redactModuleUrl(url: String): String =
    runCatching {
        val uri = java.net.URI(url)
        "${uri.scheme}://***${uri.rawPath.orEmpty()}"
    }.getOrDefault("***")

/**
 * Fetches a module index, downloads and loads module JS, and calls the
 * module's exported search/stream functions.
 *
 * Ported from Convx's `ModuleManager`, adapted to use BitChord's shared
 * [Http.client] OkHttp instance rather than a separate Ktor client.
 *
 * One instance should be held per [ModuleSource] config so that loaded
 * engines survive across successive search calls.
 */
class ModuleManager {

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /**
     * JS engines, keyed by module id. Delegates to [QuickJsExecutor]'s LRU pool.
     *
     * Concurrent because [ModuleSource.search][com.music.bitchord.data.sources.ModuleSource.search]
     * loads every module in an index at the same time, and a plain HashMap
     * resizing under two of those at once corrupts quietly rather than loudly.
     */
    private val loadedModules = java.util.concurrent.ConcurrentHashMap<String, LoadedModule>()

    data class LoadedModule(
        val module: SpineModule,
        val jsCode: String,
        val baseUrl: String,
    )

    // ── Sharing one answer between every caller that wants it ──────────────

    /**
     * Where every module call this class makes is started, deliberately *not*
     * the caller's scope, and why the losers of a race no longer cost the
     * module's server a wasted answer. See [SharedCalls].
     */
    private val calls = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * A cache key from its parts, each one length-prefixed.
     *
     * Not a separator character, because there isn't a safe one: a query is
     * arbitrary user-adjacent text and a track id is whatever the module's
     * backend uses, so any byte picked as a delimiter is a byte one of them
     * may legitimately contain. Length prefixes make the parts unambiguous
     * whatever they hold. A collision here would not fail — it would serve one
     * track's stream URL for another.
     */
    private fun keyOf(vararg parts: String) = parts.joinToString("|") { "${it.length}:$it" }

    private val loads = SharedCalls<LoadedModule>(ttlMs = 0, scope = calls, log = ::logReuse)
    private val searches = SharedCalls<ModuleSearchResponse>(SEARCH_TTL_MS, calls, ::logReuse)
    private val streams = SharedCalls<ModuleStreamResponse>(STREAM_TTL_MS, calls, ::logReuse)

    private fun logReuse(line: String) = TrackLog.d(TAG, line)

    // ── Index ─────────────────────────────────────────────────────────────

    private class CachedIndex(val modules: List<SpineModule>, val fetchedAtMs: Long)

    /**
     * Parsed indexes, keyed by source URL.
     *
     * Substituting one track asks for the index twice — once to search, once
     * to turn the match into a stream URL — and every track after it asks
     * again. That is two network round trips per play for a document that
     * changes when someone publishes a module, which is to say hardly ever:
     * measured at ~460ms of a ~2.1s substitution, or roughly a fifth of the
     * wait before audio starts, spent re-fetching bytes already in hand.
     *
     * Held behind [indexLock] rather than a plain map because the search and
     * the stream call can overlap across tracks, and two coroutines missing
     * the cache together would each start their own fetch.
     */
    private val indexCache = mutableMapOf<String, CachedIndex>()
    private val indexLock = Mutex()

    /**
     * GETs [sourceUrl], parses every `"category:*"` key, returns all modules.
     *
     * Served from [indexCache] while an earlier answer is still inside
     * [INDEX_TTL_MS]. A failed fetch is never cached — a source that was
     * briefly unreachable should be retried on the next track, not written
     * off for the rest of the window.
     */
    suspend fun fetchIndex(sourceUrl: String): Result<List<SpineModule>> =
        withContext(Dispatchers.IO) {
            indexLock.withLock {
                val cached = indexCache[sourceUrl]
                if (cached != null &&
                    System.currentTimeMillis() - cached.fetchedAtMs < INDEX_TTL_MS
                ) {
                    TrackLog.d(TAG, "▶ fetchIndex(${redactModuleUrl(sourceUrl)}) — CACHE HIT (${cached.modules.size} modules)")
                    return@withContext Result.success(cached.modules)
                }

                TrackLog.d(TAG, "▶ fetchIndex(${redactModuleUrl(sourceUrl)})")
                runCatching {
                    val request = Request.Builder().url(sourceUrl).build()
                    Http.client.newCall(request).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            throw Exception("HTTP ${resp.code} from ${redactModuleUrl(sourceUrl)}")
                        }
                        val body = resp.body?.string()
                            ?: throw Exception("Empty body from ${redactModuleUrl(sourceUrl)}")
                        val modules = ModuleIndex.parseModules(json, body)
                        TrackLog.d(TAG, "  Parsed ${modules.size} modules")
                        modules
                    }
                }.onSuccess {
                    indexCache[sourceUrl] = CachedIndex(it, System.currentTimeMillis())
                }.onFailure {
                    TrackLog.e(TAG, "  ✗ fetchIndex FAILED for ${redactModuleUrl(sourceUrl)}: ${it.message}", it)
                }
            }
        }

    // ── Load ──────────────────────────────────────────────────────────────

    /**
     * Downloads a module's JS and initialises a QuickJS engine for it.
     *
     * [resolveBaseUrl] turns a relative `module.download` filename into an
     * absolute base — callers pass `{ sourceUrl.substringBeforeLast("/") }`.
     *
     * Results are cached; a second call for the same id returns immediately.
     *
     * The cache is checked against the executor rather than on its own. Engines
     * are LRU-capped over there and this map is not told when one is evicted,
     * so a hit here could name a module whose engine had already been closed —
     * and the caller then went straight to a `callExport` that could only fail
     * with "not loaded". Re-initialising costs a JS evaluation, but not the
     * download: the source is what this map is really holding.
     */
    suspend fun loadModule(
        module: SpineModule,
        resolveBaseUrl: suspend (String) -> String = { it },
    ): Result<LoadedModule> = loads.get(
        key = module.id,
        describe = { "▶ loadModule(${module.id})" },
    ) {
        loadModuleNow(module, resolveBaseUrl)
    }

    /**
     * The download and engine initialisation itself.
     *
     * Shared only while it is running, never by time: what it returns depends
     * on whether [QuickJsExecutor] still holds an engine for the module, and
     * that pool evicts on its own schedule. Caching the answer across time
     * would hand back a [LoadedModule] whose engine had since been closed —
     * the exact failure the `isLoaded` check below exists to catch. Sharing the
     * *download* between two callers who miss together is the part worth
     * having, and it is the part that reaches the network.
     */
    private suspend fun loadModuleNow(
        module: SpineModule,
        resolveBaseUrl: suspend (String) -> String,
    ): Result<LoadedModule> = withContext(Dispatchers.IO) {
        val cached = loadedModules[module.id]
        if (cached != null) {
            if (QuickJsExecutor.isLoaded(module.id)) {
                TrackLog.d(TAG, "▶ loadModule(${module.id}) — CACHE HIT")
                return@withContext Result.success(cached)
            }
            val revived = QuickJsExecutor
                .loadModule(module.id, cached.jsCode, cached.baseUrl)
                .map { cached }
            if (revived.isSuccess) return@withContext revived
            loadedModules.remove(module.id)
        }

        TrackLog.d(TAG, "▶ loadModule(${module.id}) download=${module.download}")
        runCatching {
            val downloadUrl = if (module.download.startsWith("http")) {
                module.download
            } else {
                val base = resolveBaseUrl(module.download)
                "$base/${module.download}"
            }

            TrackLog.d(TAG, "  Resolved download URL: ${redactModuleUrl(downloadUrl)}")
            val request = Request.Builder().url(downloadUrl).build()
            val jsCode = Http.client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw Exception("HTTP ${resp.code} downloading module ${module.id}")
                }
                resp.body?.string() ?: throw Exception("Empty body for module ${module.id}")
            }
            val baseUrl = downloadUrl.substringBeforeLast("/")

            QuickJsExecutor.loadModule(module.id, jsCode, baseUrl).getOrThrow()

            val loaded = LoadedModule(module = module, jsCode = jsCode, baseUrl = baseUrl)
            loadedModules[module.id] = loaded
            TrackLog.d(TAG, "  ✓ Loaded module ${module.id}: ${jsCode.length} chars, baseUrl=${redactModuleUrl(baseUrl)}")
            loaded
        }.onFailure {
            TrackLog.e(TAG, "  ✗ loadModule FAILED for ${module.id}: ${it.message}", it)
        }
    }

    // ── Search ────────────────────────────────────────────────────────────

    suspend fun searchTracks(
        loaded: LoadedModule,
        query: String,
        limit: Int = 50,
        settings: Map<String, String> = emptyMap(),
    ): Result<ModuleSearchResponse> = searches.get(
        // Everything the module is told, because everything the module is told
        // can change the answer. The settings belong in the key as much as the
        // query does: the same search under a different quality tier is a
        // different question.
        key = keyOf(loaded.module.id, query, limit.toString(), contextArg(settings)),
        describe = { "▶ searchTracks() module=${loaded.module.id} query=\"$query\" limit=$limit" },
    ) {
        searchTracksNow(loaded, query, limit, settings)
    }

    private suspend fun searchTracksNow(
        loaded: LoadedModule,
        query: String,
        limit: Int,
        settings: Map<String, String>,
    ): Result<ModuleSearchResponse> = withContext(Dispatchers.IO) {
        val contextArg = contextArg(settings)
        TrackLog.d(TAG, "▶ searchTracks() module=${loaded.module.id} query=\"$query\" limit=$limit")
        runCatching {
            val result = QuickJsExecutor.callExport(
                moduleId = loaded.module.id,
                functionName = "searchTracks",
                args = listOf("\"$query\"", limit.toString(), contextArg),
            ).getOrThrow()
            json.decodeFromString<ModuleSearchResponse>(result).also {
                TrackLog.d(TAG, "  ✓ Parsed ${it.tracks.size} tracks (total=${it.total})")
            }
        }.onCancellation().onFailure {
            TrackLog.e(TAG, "  ✗ searchTracks FAILED for ${loaded.module.id} query='$query': ${it.message}", it)
        }
    }

    // ── Stream ────────────────────────────────────────────────────────────

    /**
     * @param quality the tier to ask for — `LOSSLESS`, `HIGH` or `LOW`.
     *   Passed as the export's second argument *and* as a setting, because
     *   modules read it from whichever of the two they were written against:
     *   `getTrackStreamUrl(id, preferredQuality, context)` takes the argument,
     *   while the multi-source ones prefer `context.settings.quality.value`
     *   and treat the argument as a fallback. Sending only one of them left
     *   the better-featured modules on their own default, which is how a
     *   request for lossless arrived at the server as no request at all.
     */
    suspend fun getStreamUrl(
        loaded: LoadedModule,
        trackId: String,
        quality: String = "",
        settings: Map<String, String> = emptyMap(),
    ): Result<ModuleStreamResponse> = streams.get(
        key = keyOf(loaded.module.id, trackId, quality, contextArg(settings)),
        describe = { "▶ getStreamUrl() module=${loaded.module.id} trackId=$trackId quality=$quality" },
    ) {
        getStreamUrlNow(loaded, trackId, quality, settings)
    }

    private suspend fun getStreamUrlNow(
        loaded: LoadedModule,
        trackId: String,
        quality: String,
        settings: Map<String, String>,
    ): Result<ModuleStreamResponse> = withContext(Dispatchers.IO) {
        val contextArg = contextArg(settings)
        TrackLog.d(TAG, "▶ getStreamUrl() module=${loaded.module.id} trackId=$trackId quality=$quality")
        runCatching {
            val result = QuickJsExecutor.callExport(
                moduleId = loaded.module.id,
                functionName = "getTrackStreamUrl",
                args = listOf("\"$trackId\"", "\"$quality\"", contextArg),
            ).getOrThrow()
            json.decodeFromString<ModuleStreamResponse>(result).also {
                TrackLog.d(TAG, "  ✓ streamUrl=${it.streamUrl?.take(100) ?: "<none>"} quality=${it.track?.audioQuality}")
            }
        }.onCancellation().onFailure {
            TrackLog.e(TAG, "  ✗ getStreamUrl FAILED for ${loaded.module.id} trackId=$trackId: ${it.message}", it)
        }
    }

    // ── Failure handling ──────────────────────────────────────────────────

    /**
     * Re-throws a cancellation that [runCatching] caught.
     *
     * `runCatching` catches `Throwable`, which includes the
     * `CancellationException` a coroutine is cancelled with — so a caller
     * giving up on a lookup came back through here as a *module failure*,
     * logged with a stack trace as though somebody's server had misbehaved.
     * It sent debugging in the wrong direction more than once: a lookup
     * abandoned 66ms short of its answer reads identically to one the server
     * refused. Worse, it lets the rest of the block carry on doing work for a
     * coroutine that is already dead.
     */
    private fun <T> Result<T>.onCancellation(): Result<T> = also {
        (exceptionOrNull() as? CancellationException)?.let { throw it }
    }

    // ── Context ───────────────────────────────────────────────────────────

    /**
     * The `context` argument every export takes.
     *
     * A module reads a setting as `context.settings.<key>.value` — the extra
     * `value` wrapper is there because a module's own settings schema
     * describes each key as an object with a type, a label and a current
     * value, and the host hands back the same shape it was given. This was
     * previously built as `{settings:{value:{…}}}`, one level short and with
     * the wrapper on the wrong side, so *no* module could read *any* setting
     * out of it: every lookup landed on undefined and fell through to the
     * module's own default. Silent, and worth exactly one misplaced brace.
     */
    private fun contextArg(settings: Map<String, String>): String =
        settings.entries.joinToString(
            separator = ",",
            prefix = "{settings:{",
            postfix = "}}",
        ) { (key, value) -> "\"$key\":{\"value\":\"$value\"}" }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    fun unloadModule(moduleId: String) {
        loadedModules.remove(moduleId)
        QuickJsExecutor.unload(moduleId)
    }

    /**
     * Everything this manager is holding, dropped.
     *
     * The shared answers go with the engines rather than outliving them. This
     * is reached when a source is edited or removed — the module index, and so
     * every answer that came out of it, is about a configuration that no longer
     * exists, and serving one from cache afterwards would be answering a
     * question about the old server with the new one selected.
     */
    fun unloadAll() {
        loadedModules.clear()
        loads.clear()
        searches.clear()
        streams.clear()
        QuickJsExecutor.unloadAll()
    }

    private companion object {
        const val TAG = "BitChord"

        /**
         * How long a fetched index is trusted. Long enough that a run of
         * tracks costs one fetch between them, short enough that a module
         * published or pulled today is picked up without restarting the app.
         */
        const val INDEX_TTL_MS = 10 * 60 * 1000L

        /**
         * How long a search answer is trusted, on the same reasoning as
         * [INDEX_TTL_MS] and for a question that changes even less often:
         * whether a catalogue holds a given recording is not a fact that turns
         * over minute to minute. Every query that reaches here is a
         * [TrackMatcher][com.music.bitchord.data.sources.TrackMatcher] one —
         * title and artist for a track being matched, never anything a user
         * typed — so nobody is ever looking at these results waiting for them
         * to refresh.
         */
        const val SEARCH_TTL_MS = 10 * 60 * 1000L

        /**
         * How long a stream URL is reused.
         *
         * Shorter than the rest, because this one has an expiry that is not
         * ours to set: a module's URL is signed, at around five hours on the
         * catalogues in use here. Five minutes is far enough inside that to be
         * certain a reused URL still works, and long enough to cover what
         * actually repeats — the two candidates one match tries in turn, and
         * the second look arriving at the same track moments later. The same
         * trade [StreamChoice][com.music.bitchord.playback.StreamChoice] makes,
         * with a wider margin because nothing here can tell whether the bytes
         * behind the URL were ever successfully read.
         */
        const val STREAM_TTL_MS = 5 * 60 * 1000L

        /**
         * How many shared answers to keep. A queue's worth of tracks at two
         * queries and a stream call each, several times over, with room to
         * spare — the point is a bound, not a budget.
         */
        const val MAX_SHARED = 128
    }
}
