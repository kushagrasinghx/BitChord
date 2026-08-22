package com.music.bitchord.data.sources.module

import android.util.Log
import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.Http
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Request

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
                    TrackLog.d(TAG, "▶ fetchIndex($sourceUrl) — CACHE HIT (${cached.modules.size} modules)")
                    return@withContext Result.success(cached.modules)
                }

                TrackLog.d(TAG, "▶ fetchIndex($sourceUrl)")
                runCatching {
                    val request = Request.Builder().url(sourceUrl).build()
                    Http.client.newCall(request).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            throw Exception("HTTP ${resp.code} from $sourceUrl")
                        }
                        val body = resp.body?.string()
                            ?: throw Exception("Empty body from $sourceUrl")
                        val modules = ModuleIndex.parseModules(json, body)
                        TrackLog.d(TAG, "  Parsed ${modules.size} modules")
                        modules
                    }
                }.onSuccess {
                    indexCache[sourceUrl] = CachedIndex(it, System.currentTimeMillis())
                }.onFailure {
                    TrackLog.e(TAG, "  ✗ fetchIndex FAILED for $sourceUrl: ${it.message}", it)
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
     */
    suspend fun loadModule(
        module: SpineModule,
        resolveBaseUrl: suspend (String) -> String = { it },
    ): Result<LoadedModule> = withContext(Dispatchers.IO) {
        val cached = loadedModules[module.id]
        if (cached != null) {
            TrackLog.d(TAG, "▶ loadModule(${module.id}) — CACHE HIT")
            return@withContext Result.success(cached)
        }

        TrackLog.d(TAG, "▶ loadModule(${module.id}) download=${module.download}")
        runCatching {
            val downloadUrl = if (module.download.startsWith("http")) {
                module.download
            } else {
                val base = resolveBaseUrl(module.download)
                "$base/${module.download}"
            }

            TrackLog.d(TAG, "  Resolved download URL: $downloadUrl")
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
            TrackLog.d(TAG, "  ✓ Loaded module ${module.id}: ${jsCode.length} chars, baseUrl=$baseUrl")
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
                TrackLog.d(TAG, "  ✓ streamUrl=${it.streamUrl.take(100)} quality=${it.track?.audioQuality}")
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

    fun unloadAll() {
        loadedModules.clear()
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
    }
}
