package com.music.bitchord.data.sources.module

import android.util.Log
import com.music.bitchord.data.TrackLog
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.AsyncFunctionBinding
import com.dokar.quickjs.binding.FunctionBinding
import com.dokar.quickjs.binding.define
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Sandboxed QuickJS engine pool for executing module JS.
 *
 * Modules commonly stash session/auth state in top-level variables set up
 * once at load time (e.g. an eager token pre-fetch). The old one-shot
 * executor re-evaluated the whole script on every function call, so that
 * state never survived from `searchTracks()` to `getStreamUrl()` — each
 * ran in its own throwaway VM. Keeping one engine alive per loaded module,
 * reused across calls, is what a normal module host does and what these
 * modules assume. LRU-capped at [MAX_CONCURRENT]; least-recently-used is
 * evicted to make room rather than refusing to load past the cap.
 *
 * Ported verbatim from Convx's `QuickJsExecutor`.
 */
internal object QuickJsExecutor {

    private const val TAG = "BitChord"
    private const val MAX_CONCURRENT = 4

    private val syncHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val engineLock = Any()

    /** LRU map: access-ordered so the oldest-used entry is first. */
    private val engines = LinkedHashMap<String, QuickJs>(16, 0.75f, true)

    // ── Load ─────────────────────────────────────────────────────────────

    suspend fun loadModule(moduleId: String, jsCode: String, fetchBase: String = ""): Result<Unit> {
        synchronized(engineLock) {
            if (engines.containsKey(moduleId)) {
                TrackLog.d(TAG, "QuickJsExecutor.loadModule($moduleId) — ENGINE CACHE HIT")
                return Result.success(Unit)
            }
            while (engines.size >= MAX_CONCURRENT) {
                val lruId = engines.keys.firstOrNull() ?: break
                TrackLog.d(TAG, "  Evicting LRU module engine: $lruId")
                engines.remove(lruId)?.close()
            }
        }

        TrackLog.d(TAG, "QuickJsExecutor.loadModule($moduleId) fetchBase=$fetchBase jsCodeLength=${jsCode.length}")
        return withContext(Dispatchers.Default) {
            val qjs = QuickJs.create(Dispatchers.Default)
            qjs.maxStackSize = 512 * 1024L
            runCatching {
                bindConsole(qjs)
                bindAsyncFetch(qjs, fetchBase)

                qjs.evaluate<String>(POLYFILLS)
                val cleanCode = preprocessModuleCode(jsCode)

                qjs.evaluate<String>(
                    """
                    var __spine_iife_error = null;
                    var __spine_mod = (function() {
                        try {
                            var module = { exports: {} };
                            var exports = module.exports;
                            var self = {};
                            $cleanCode
                            if (module.exports && (module.exports.searchTracks || module.exports.getTrackStreamUrl)) {
                                return module.exports;
                            }
                            return {};
                        } catch(e) {
                            __spine_iife_error = e && e.message ? e.message : String(e);
                            return {};
                        }
                    })();
                    'ok'
                    """.trimIndent()
                )

                val iifeError = qjs.evaluate<String>("__spine_iife_error || 'none'")
                if (iifeError != "none") throw IllegalStateException("Module init error: $iifeError")

                val keys = qjs.evaluate<String>("Object.keys(__spine_mod).join(', ')")
                TrackLog.d(TAG, "  Module exports: [$keys]")
            }.onSuccess {
                synchronized(engineLock) { engines[moduleId] = qjs }
            }.onFailure {
                TrackLog.e(TAG, "  ✗ loadModule FAILED for $moduleId: ${it.message}", it)
                qjs.close()
            }.map { }
        }
    }

    // ── Call ─────────────────────────────────────────────────────────────

    suspend fun callExport(moduleId: String, functionName: String, args: List<String>): Result<String> {
        val qjs = synchronized(engineLock) { engines[moduleId] }
            ?: return Result.failure(IllegalStateException("Module $moduleId is not loaded"))

        TrackLog.d(TAG, "QuickJsExecutor.callExport($moduleId, $functionName) args=$args")
        return withContext(Dispatchers.Default) {
            runCatching {
                val hasFn = qjs.evaluate<String>("typeof __spine_mod['$functionName']")
                if (hasFn != "function") {
                    throw IllegalStateException("$functionName is not a function on module $moduleId")
                }

                val argsStr = args.joinToString(",")

                // evaluate() converts a Promise via toString() instead of awaiting it.
                // Workaround: store the resolved JSON in a global var inside the async
                // IIFE, then read it in a second evaluate() call.
                qjs.evaluate<String>(
                    """
                    var __spine_resolved_json = undefined;
                    (async function() {
                        var __fn = __spine_mod['$functionName'];
                        if (!__fn) {
                            __spine_resolved_json = JSON.stringify({ error: '$functionName not found' });
                            return;
                        }
                        try {
                            var r = await __fn($argsStr);
                            __spine_resolved_json = typeof r === 'string' ? r : JSON.stringify(r);
                        } catch(e) {
                            __spine_resolved_json = JSON.stringify({ error: e && e.message ? e.message : String(e) });
                        }
                    })();
                    """.trimIndent()
                )

                val rawResult = qjs.evaluate<String>("__spine_resolved_json")
                TrackLog.d(TAG, "  callExport result (${rawResult.length} chars): ${rawResult.take(500)}")
                rawResult
            }.onFailure {
                TrackLog.e(TAG, "  ✗ callExport FAILED for $moduleId.$functionName: ${it.message}", it)
            }
        }
    }

    // ── Unload ───────────────────────────────────────────────────────────

    fun unload(moduleId: String) {
        synchronized(engineLock) { engines.remove(moduleId) }?.close()
        TrackLog.d(TAG, "QuickJsExecutor.unload($moduleId)")
    }

    fun unloadAll() {
        val all = synchronized(engineLock) {
            val values = engines.values.toList()
            engines.clear()
            values
        }
        all.forEach { it.close() }
        TrackLog.d(TAG, "QuickJsExecutor.unloadAll()")
    }

    // ── Code pre-processing ──────────────────────────────────────────────

    /**
     * Strips ES-module `export` keywords so the code runs inside an IIFE
     * that wraps it in a CommonJS-style `module.exports` object.
     *
     * Also handles the template-literal export format some Spine sources use:
     * `export const x = \`…actual JS…\``
     */
    private fun preprocessModuleCode(jsCode: String): String {
        val code = jsCode.trim()

        val exportPattern = Regex("""^export\s+const\s+\w+\s*=\s*`""")
        val exportMatch = exportPattern.find(code)
        if (exportMatch != null) {
            val contentStart = exportMatch.range.last + 1
            var i = contentStart
            while (i < code.length) {
                if (code[i] == '\\' && i + 1 < code.length) { i += 2; continue }
                if (code[i] == '`') {
                    return code.substring(contentStart, i).trim()
                }
                i++
            }
            TrackLog.w(TAG, "  Template literal not closed, falling through to regex preprocess")
        }

        var result = code
        result = result.replace(Regex("""\bexport\s+default\s+(?=function|class|const|let|var|async)"""), "")
        result = result.replace(Regex("""\bexport\s+(const|let|var|function|class|async)\b"""), "$1")
        result = result.replace(Regex("""\bexport\s*\{[^}]*\}\s*;?"""), "")
        return result
    }

    // ── Console binding ──────────────────────────────────────────────────

    private fun bindConsole(qjs: QuickJs) {
        qjs.define("console") {
            function("log", object : FunctionBinding<Unit> {
                override fun invoke(args: Array<Any?>) {
                    TrackLog.d(TAG, "[JS] ${args.joinToString(" ") { it?.toString() ?: "null" }}")
                }
            })
            function("error", object : FunctionBinding<Unit> {
                override fun invoke(args: Array<Any?>) {
                    TrackLog.e(TAG, "[JS-ERR] ${args.joinToString(" ") { it?.toString() ?: "null" }}")
                }
            })
            function("warn", object : FunctionBinding<Unit> {
                override fun invoke(args: Array<Any?>) {
                    TrackLog.w(TAG, "[JS-WARN] ${args.joinToString(" ") { it?.toString() ?: "null" }}")
                }
            })
            function("info", object : FunctionBinding<Unit> {
                override fun invoke(args: Array<Any?>) {
                    TrackLog.i(TAG, "[JS-INFO] ${args.joinToString(" ") { it?.toString() ?: "null" }}")
                }
            })
        }
    }

    // ── Fetch binding ────────────────────────────────────────────────────

    private suspend fun bindAsyncFetch(qjs: QuickJs, fetchBase: String) {
        qjs.define("__spine") {
            asyncFunction("fetch", object : AsyncFunctionBinding<String> {
                override suspend fun invoke(args: Array<Any?>): String {
                    val rawUrl = args[0]?.toString()
                        ?: throw IllegalArgumentException("fetch requires a URL")
                    val method = args[1]?.toString() ?: "GET"
                    val headersJson = args[2]?.toString() ?: "{}"
                    val body = args[3]?.toString()
                    val url = resolveUrl(rawUrl, fetchBase)

                    TrackLog.d(TAG, "  → fetch $method $url")
                    val (statusCode, responseBody) = fetchUrlSync(url, method, headersJson, body)
                    TrackLog.d(TAG, "    HTTP $statusCode (${responseBody.length} bytes)")

                    val respObj = JSONObject().apply {
                        put("status", statusCode)
                        put("ok", statusCode in 200..299)
                        put("body", responseBody)
                    }
                    return respObj.toString()
                }
            })
            asyncFunction("setTimeout", object : AsyncFunctionBinding<String> {
                override suspend fun invoke(args: Array<Any?>): String {
                    val ms = args.getOrNull(1)?.toString()?.toLongOrNull() ?: 0L
                    kotlinx.coroutines.delay(ms)
                    return "0"
                }
            })
            asyncFunction("clearTimeout", object : AsyncFunctionBinding<String> {
                override suspend fun invoke(args: Array<Any?>): String = "ok"
            })
        }

        // Expose a web-compatible fetch() surface over the native binding.
        qjs.evaluate<Unit>(
            """
            var fetch = async function(url, options) {
                var method = 'GET';
                var headers = '{}';
                var body = null;
                if (options) {
                    method = options.method || 'GET';
                    if (options.headers) {
                        if (typeof options.headers === 'string') {
                            headers = options.headers;
                        } else {
                            try { headers = JSON.stringify(options.headers); } catch(e) { headers = '{}'; }
                        }
                    }
                    if (options.body !== undefined && options.body !== null) {
                        body = typeof options.body === 'string' ? options.body : JSON.stringify(options.body);
                    }
                    if (options.signal && options.signal.aborted) {
                        throw new Error('Aborted');
                    }
                }
                var raw = JSON.parse(await __spine.fetch(url, method, headers, body));
                var respBody = raw.body;
                return {
                    ok: raw.ok,
                    status: raw.status,
                    statusText: raw.ok ? 'OK' : 'Error',
                    json: function() { try { return JSON.parse(respBody); } catch(e) { throw new Error('Invalid JSON: ' + respBody.substring(0, 200)); } },
                    text: function() { return respBody; },
                    arrayBuffer: function() { throw new Error('Not implemented'); },
                    clone: function() { return this; },
                    headers: { get: function(k) { return null; } }
                };
            };

            var setTimeout = async function(fn, ms) {
                await __spine.setTimeout(null, ms || 0);
                if (typeof fn === 'function') fn();
                return 0;
            };
            var clearTimeout = function(id) {};
            """.trimIndent()
        )
    }

    // ── URL resolution ───────────────────────────────────────────────────

    private fun resolveUrl(url: String, base: String): String {
        if (url.startsWith("http://") || url.startsWith("https://")) return url
        if (base.isEmpty()) return url
        return if (url.startsWith("/")) {
            val scheme = base.substringBefore("://")
            val host = base.substringAfter("://").substringBefore("/")
            "$scheme://$host$url"
        } else {
            "$base/$url"
        }
    }

    private fun fetchUrlSync(
        url: String,
        method: String,
        headersJson: String,
        body: String?,
    ): Pair<Int, String> {
        val builder = Request.Builder().url(url)

        var hasUserAgent = false
        try {
            val headersObj = JSONObject(headersJson)
            for (key in headersObj.keys()) {
                val value = headersObj.optString(key, "")
                builder.header(key, value)
                if (key.equals("user-agent", ignoreCase = true)) hasUserAgent = true
            }
        } catch (_: Exception) {
        }

        if (!hasUserAgent) {
            builder.header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
            )
        }

        when (method.uppercase()) {
            "POST" -> builder.post((body ?: "").toRequestBody("application/json; charset=utf-8".toMediaType()))
            "PUT" -> builder.put((body ?: "").toRequestBody("application/json; charset=utf-8".toMediaType()))
            "DELETE" -> builder.delete()
            "HEAD" -> builder.head()
            else -> builder.get()
        }

        syncHttpClient.newCall(builder.build()).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            return response.code to responseBody
        }
    }

    // ── Polyfills ────────────────────────────────────────────────────────

    private const val POLYFILLS = """
        if (typeof AbortController === 'undefined') {
            var AbortController = function() { this.signal = { aborted: false }; };
            AbortController.prototype.abort = function() { this.signal.aborted = true; };
        }

        if (typeof Object.assign !== 'function') {
            Object.assign = function(target) {
                if (target == null) throw new TypeError('Cannot convert undefined or null to object');
                var to = Object(target);
                for (var i = 1; i < arguments.length; i++) {
                    var source = arguments[i];
                    if (source != null) {
                        for (var key in source) {
                            if (Object.prototype.hasOwnProperty.call(source, key)) {
                                to[key] = source[key];
                            }
                        }
                    }
                }
                return to;
            };
        }

        if (typeof Promise.any !== 'function') {
            Promise.any = function(promises) {
                return new Promise(function(resolve, reject) {
                    var errors = [];
                    var remaining = promises.length;
                    if (remaining === 0) { reject(new AggregateError([], 'All promises were rejected')); return; }
                    promises.forEach(function(p, i) {
                        Promise.resolve(p).then(resolve, function(e) {
                            errors[i] = e;
                            remaining--;
                            if (remaining === 0) reject(new AggregateError(errors, 'All promises were rejected'));
                        });
                    });
                });
            };
        }

        if (typeof Promise.allSettled !== 'function') {
            Promise.allSettled = function(promises) {
                return Promise.all(promises.map(function(p) {
                    return Promise.resolve(p).then(
                        function(value) { return { status: 'fulfilled', value: value }; },
                        function(reason) { return { status: 'rejected', reason: reason }; }
                    );
                }));
            };
        }

        if (typeof AggregateError === 'undefined') {
            var AggregateError = function(errors, message) {
                this.errors = errors;
                this.message = message || '';
                this.name = 'AggregateError';
            };
            AggregateError.prototype = Object.create(Error.prototype);
        }

        if (typeof atob === 'undefined') {
            var atob = function(input) {
                var chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=';
                var str = String(input).replace(/=+${'$'}/, '');
                var output = '';
                for (var bc = 0, bs, buffer, idx = 0; buffer = str.charAt(idx++); ~buffer && (bs = bc % 4 ? bs * 64 + buffer : buffer, bc++ % 4) ? output += String.fromCharCode(255 & bs >> (-2 * bc & 6)) : 0) {
                    buffer = chars.indexOf(buffer);
                }
                return output;
            };
        }

        if (typeof setTimeout === 'undefined') {
            var __spine_timers = {};
            var __spine_timer_id = 0;
            var setTimeout = function(fn, ms) {
                var id = ++__spine_timer_id;
                __spine_timers[id] = { fn: fn, ms: ms || 0 };
                return id;
            };
            var clearTimeout = function(id) {
                if (__spine_timers[id]) delete __spine_timers[id];
            };
        }
        if (typeof clearTimeout === 'undefined') {
            var clearTimeout = function(id) {
                if (typeof __spine_timers !== 'undefined' && __spine_timers[id]) delete __spine_timers[id];
            };
        }

        if (typeof URL === 'undefined') {
            var URL = function(url, base) {
                this.href = url;
                try {
                    var a = url.replace(/^[^:]+:/, 'http:');
                    var match = a.match(/^\/\/([^/]+)(\/.*)?$/);
                    if (match) {
                        this.hostname = match[1];
                        this.pathname = match[2] || '/';
                    } else {
                        var m2 = a.match(/^https?:\/\/([^/]+)(\/.*)?$/);
                        if (m2) { this.hostname = m2[1]; this.pathname = m2[2] || '/'; }
                    }
                } catch(e) {}
            };
        }
    """
}
