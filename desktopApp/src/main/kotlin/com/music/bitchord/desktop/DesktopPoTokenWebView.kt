package com.music.bitchord.desktop

import com.music.bitchord.data.Http
import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.innertube.potoken.PO_TOKEN_HTML
import com.music.bitchord.data.innertube.potoken.PoTokenException
import com.music.bitchord.data.innertube.potoken.PoTokenMinter
import com.music.bitchord.data.innertube.potoken.buildExceptionForJsError
import com.music.bitchord.data.innertube.potoken.parseChallengeData
import com.music.bitchord.data.innertube.potoken.parseIntegrityTokenData
import com.music.bitchord.data.innertube.potoken.stringToU8
import com.music.bitchord.data.innertube.potoken.u8ToBase64
import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.scene.web.WebEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import netscape.javascript.JSObject
import okhttp3.Headers.Companion.toHeaders
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The phone's `PoTokenWebView`, on the desktop: BotGuard run in JavaFX's
 * WebView (WebKit) instead of Android's.
 *
 * The same page ([PO_TOKEN_HTML]), the same steps and the same timeouts: fetch
 * the challenge from `jnn/v1/Create`, run BotGuard, trade its answer for an
 * integrity token at `GenerateIT`, build the minter, then mint a PoToken per
 * identifier. What differs is only the browser's API — a WebEngine has no
 * base-URL load and no JavaScript console callback, so the bridge is installed
 * once the page has loaded and `console` is routed to it.
 */
class DesktopPoTokenWebView private constructor(
    // to be used exactly once only during initialization!
    private val continuation: Continuation<DesktopPoTokenWebView>,
) : PoTokenMinter {
    private lateinit var engine: WebEngine
    private val scope = CoroutineScope(SupervisorJob() + JavaFx)

    // Guards the single-shot init continuation: an error can arrive from the page, from a
    // BotGuard request, or from a timeout, and a second resume would throw.
    private val initResumed = AtomicBoolean(false)

    @Volatile
    private var closed = false

    @Volatile
    override var isDead: Boolean = false
        private set

    private val poTokenContinuations =
        Collections.synchronizedMap(HashMap<String, Continuation<String>>())
    private val requestCounter = AtomicLong()
    private val exceptionHandler = CoroutineExceptionHandler { _, t ->
        onInitializationErrorCloseAndCancel(t)
    }
    private lateinit var expirationInstant: Instant

    /**
     * What the page calls. JavaFX holds bridge objects weakly, so this one is
     * kept as a field for the page's whole life.
     */
    inner class Bridge {
        fun downloadAndRunBotguard() = this@DesktopPoTokenWebView.downloadAndRunBotguard()
        fun onJsInitializationError(error: String) = this@DesktopPoTokenWebView.onJsInitializationError(error)
        fun onRunBotguardResult(botguardResponse: String) = this@DesktopPoTokenWebView.onRunBotguardResult(botguardResponse)
        fun onMinterCreated() = this@DesktopPoTokenWebView.onMinterCreated()
        fun onObtainPoTokenError(requestKey: String, error: String) =
            this@DesktopPoTokenWebView.onObtainPoTokenError(requestKey, error)
        fun onObtainPoTokenResult(requestKey: String, poTokenU8: String) =
            this@DesktopPoTokenWebView.onObtainPoTokenResult(requestKey, poTokenU8)
        fun log(level: String, message: String) = this@DesktopPoTokenWebView.onConsoleMessage(level, message)
    }

    private val bridge = Bridge()

    private fun onConsoleMessage(level: String, msg: String) {
        when (level) {
            "error" -> TrackLog.e(TAG, "JS: $msg")
            "warn" -> TrackLog.w(TAG, "JS: $msg")
            else -> TrackLog.d(TAG, "JS: $msg")
        }
    }

    /** An exception the page did not catch — what the phone's WebView reports as "Uncaught". */
    private fun onUncaught(message: String) {
        if (initResumed.get()) {
            TrackLog.e(TAG, "Uncaught JavaScript error after initialization")
            isDead = true
            val exception = PoTokenException(message)
            close()
            popAllPoTokenContinuations().forEach { (_, cont) ->
                runCatching { cont.resumeWithException(exception) }
            }
        } else {
            val exception = buildExceptionForJsError(message)
            onInitializationErrorCloseAndCancel(exception)
            popAllPoTokenContinuations().forEach { (_, cont) ->
                runCatching { cont.resumeWithException(exception) }
            }
        }
    }

    /** Builds the page and starts BotGuard; on the FX thread. */
    private fun loadHtmlAndObtainBotguard() {
        TrackLog.d(TAG, "loadHtmlAndObtainBotguard() called")
        engine = WebEngine()
        engine.userAgent = USER_AGENT
        engine.setOnError { event -> onUncaught(event.message ?: "WebEngine error") }
        engine.loadWorker.stateProperty().addListener { _, _, state ->
            when (state) {
                Worker.State.SUCCEEDED -> scope.launch(exceptionHandler) {
                    val window = engine.executeScript("window") as JSObject
                    window.setMember(JS_INTERFACE, bridge)
                    // The page's own console, and its uncaught errors, into the track log.
                    engine.executeScript(
                        """(function() {
                            function route(level) {
                                return function() {
                                    var parts = [];
                                    for (var i = 0; i < arguments.length; i++) parts.push(String(arguments[i]));
                                    $JS_INTERFACE.log(level, parts.join(' '));
                                };
                            }
                            console.log = route('log');
                            console.info = route('log');
                            console.warn = route('warn');
                            console.error = route('error');
                            window.onerror = function(message, source, line) {
                                $JS_INTERFACE.onJsInitializationError('Uncaught ' + message + ' (' + line + ')');
                            };
                        })()""",
                    )
                    engine.executeScript("$JS_INTERFACE.downloadAndRunBotguard()")
                }
                Worker.State.FAILED -> onInitializationErrorCloseAndCancel(
                    PoTokenException("BotGuard page failed to load: ${engine.loadWorker.exception?.message}"),
                )
                else -> Unit
            }
        }
        engine.loadContent(PO_TOKEN_HTML, "text/html")
    }

    private fun downloadAndRunBotguard() {
        TrackLog.d(TAG, "downloadAndRunBotguard() called")

        makeBotguardServiceRequest(
            "https://www.youtube.com/api/jnn/v1/Create",
            "[ \"$REQUEST_KEY\" ]",
        ) { responseBody ->
            val parsedChallengeData = parseChallengeData(responseBody)
            engine.executeScript(
                """try {
                    data = $parsedChallengeData
                    runBotGuard(data).then(function (result) {
                        this.webPoSignalOutput = result.webPoSignalOutput
                        $JS_INTERFACE.onRunBotguardResult(result.botguardResponse)
                    }, function (error) {
                        $JS_INTERFACE.onJsInitializationError(error + "\n" + error.stack)
                    })
                } catch (error) {
                    $JS_INTERFACE.onJsInitializationError(error + "\n" + error.stack)
                }""",
            )
        }
    }

    private fun onJsInitializationError(error: String) {
        TrackLog.e(TAG, "PO-token JavaScript initialization failed: $error")
        onInitializationErrorCloseAndCancel(buildExceptionForJsError(error))
    }

    private fun onRunBotguardResult(botguardResponse: String) {
        TrackLog.d(TAG, "botguardResponse received")
        makeBotguardServiceRequest(
            "https://www.youtube.com/api/jnn/v1/GenerateIT",
            "[ \"$REQUEST_KEY\", \"$botguardResponse\" ]",
        ) { responseBody ->
            TrackLog.d(TAG, "GenerateIT response received")
            try {
                val (integrityToken, expirationTimeInSeconds) = parseIntegrityTokenData(responseBody)
                TrackLog.d(TAG, "Parsed integrity token; expires in $expirationTimeInSeconds sec")

                expirationInstant = Instant.now().plusSeconds(expirationTimeInSeconds).minus(10, ChronoUnit.MINUTES)

                engine.executeScript(
                    """try {
                        this.integrityToken = $integrityToken
                        createPoTokenMinter(webPoSignalOutput, integrityToken).then(function() {
                            $JS_INTERFACE.onMinterCreated()
                        }).catch(function(error) {
                            $JS_INTERFACE.onJsInitializationError(error + "\n" + (error.stack || ''))
                        })
                    } catch (error) {
                        $JS_INTERFACE.onJsInitializationError(error + "\n" + error.stack)
                    }""",
                )
            } catch (e: Exception) {
                TrackLog.e(TAG, "Failed to parse integrity token data type=${e::class.simpleName ?: "unknown"}")
                onInitializationErrorCloseAndCancel(PoTokenException("parseIntegrityTokenData failed: ${e.message}"))
            }
        }
    }

    private fun onMinterCreated() {
        TrackLog.d(TAG, "poToken minter created successfully, initialization complete")
        if (initResumed.compareAndSet(false, true)) {
            continuation.resume(this)
        }
    }

    override suspend fun generatePoToken(identifier: String): String {
        if (isDead || closed) {
            throw PoTokenException("PoToken WebView is dead/closed — instance must be recreated")
        }
        val requestKey = "$identifier#${requestCounter.incrementAndGet()}"
        return try {
            withTimeout(GENERATE_TIMEOUT_MS) {
                generatePoTokenInternal(identifier, requestKey)
            }
        } catch (e: TimeoutCancellationException) {
            isDead = true
            popPoTokenContinuation(requestKey)
            TrackLog.e(TAG, "PO-token generation timed out after ${GENERATE_TIMEOUT_MS}ms")
            throw PoTokenException("poToken generation timed out after ${GENERATE_TIMEOUT_MS}ms")
        }
    }

    private suspend fun generatePoTokenInternal(identifier: String, requestKey: String): String =
        withContext(JavaFx) {
            suspendCancellableCoroutine { cont ->
                TrackLog.d(TAG, "PO-token generation requested")
                poTokenContinuations[requestKey] = cont
                engine.executeScript(
                    """(function() {
                        var requestKey = "$requestKey"
                        try {
                            var u8Identifier = ${stringToU8(identifier)}
                            obtainPoToken(u8Identifier).then(function(poTokenU8) {
                                $JS_INTERFACE.onObtainPoTokenResult(requestKey, poTokenU8.join(","))
                            }).catch(function(error) {
                                $JS_INTERFACE.onObtainPoTokenError(requestKey, error + "\n" + (error.stack || ''))
                            })
                        } catch (error) {
                            $JS_INTERFACE.onObtainPoTokenError(requestKey, error + "\n" + error.stack)
                        }
                    })()""",
                )
            }
        }

    private fun onObtainPoTokenError(requestKey: String, error: String) {
        TrackLog.e(TAG, "PO-token JavaScript callback failed")
        popPoTokenContinuation(requestKey)?.resumeWithException(PoTokenException(error))
    }

    private fun onObtainPoTokenResult(requestKey: String, poTokenU8: String) {
        TrackLog.d(TAG, "Encoded PO-token result received")
        val poToken = try {
            u8ToBase64(poTokenU8)
        } catch (t: Throwable) {
            popPoTokenContinuation(requestKey)?.resumeWithException(t)
            return
        }
        TrackLog.d(TAG, "PO token decoded")
        popPoTokenContinuation(requestKey)?.resume(poToken)
    }

    override val isExpired: Boolean
        get() = Instant.now().isAfter(expirationInstant)

    private fun popPoTokenContinuation(identifier: String): Continuation<String>? =
        poTokenContinuations.remove(identifier)

    private fun popAllPoTokenContinuations(): Map<String, Continuation<String>> {
        val result = synchronized(poTokenContinuations) { poTokenContinuations.toMap() }
        poTokenContinuations.clear()
        return result
    }

    private fun makeBotguardServiceRequest(
        url: String,
        data: String,
        handleResponseBody: (String) -> Unit,
    ) {
        scope.launch(exceptionHandler) {
            val request = okhttp3.Request.Builder()
                .post(data.toRequestBody())
                .headers(
                    mapOf(
                        "User-Agent" to USER_AGENT,
                        "Accept" to "application/json",
                        "Content-Type" to "application/json+protobuf",
                        "x-goog-api-key" to GOOGLE_API_KEY,
                        "x-user-agent" to "grpc-web-javascript/0.1",
                    ).toHeaders(),
                )
                .url(url)
                .build()
            val (httpCode, body) = withContext(Dispatchers.IO) {
                Http.client.newCall(request).execute().use { response ->
                    response.code to if (response.code == 200) response.body?.string() else null
                }
            }
            if (body.isNullOrEmpty()) {
                onInitializationErrorCloseAndCancel(PoTokenException("Invalid botguard response (code=$httpCode, empty body)"))
            } else {
                handleResponseBody(body)
            }
        }
    }

    private fun onInitializationErrorCloseAndCancel(error: Throwable) {
        close()
        if (initResumed.compareAndSet(false, true)) {
            runCatching { continuation.resumeWithException(error) }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        scope.cancel()
        runOnFx {
            runCatching { if (::engine.isInitialized) engine.load("about:blank") }
                .onFailure { TrackLog.w(TAG, "PO-token WebView teardown failed type=${it::class.simpleName ?: "unknown"}") }
        }
    }

    companion object {
        private const val TAG = "PoTokenWebView"
        private const val GOOGLE_API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
        private const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3"
        private const val JS_INTERFACE = "PoTokenWebView"

        private const val INIT_TIMEOUT_MS = 45_000L

        private const val GENERATE_TIMEOUT_MS = 15_000L

        /** Whether JavaFX could be started in this process; checked once. */
        val available: Boolean by lazy { startFx() }

        suspend fun getNewPoTokenGenerator(): DesktopPoTokenWebView {
            var created: DesktopPoTokenWebView? = null
            try {
                return withTimeout(INIT_TIMEOUT_MS) {
                    withContext(JavaFx) {
                        suspendCancellableCoroutine { cont ->
                            val potWv = DesktopPoTokenWebView(cont)
                            created = potWv
                            potWv.loadHtmlAndObtainBotguard()
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                TrackLog.e(TAG, "PoTokenWebView init timed out after ${INIT_TIMEOUT_MS}ms")
                closeQuietly(created)
                throw PoTokenException("PoTokenWebView init timed out after ${INIT_TIMEOUT_MS}ms")
            } catch (e: CancellationException) {
                closeQuietly(created)
                throw e
            }
        }

        private suspend fun closeQuietly(potWv: DesktopPoTokenWebView?) {
            if (potWv == null) return
            withContext(NonCancellable) {
                potWv.initResumed.set(true)
                potWv.close()
            }
        }

        /** Starts the JavaFX toolkit without a window; it stays up for the process. */
        private fun startFx(): Boolean = runCatching {
            val started = CountDownLatch(1)
            try {
                Platform.startup { started.countDown() }
            } catch (_: IllegalStateException) {
                // Already running.
                started.countDown()
            }
            Platform.setImplicitExit(false)
            started.await()
            true
        }.onFailure { TrackLog.w(TAG, "JavaFX unavailable, no PoTokens on this desktop: ${it.message}") }
            .getOrDefault(false)

        private fun runOnFx(block: () -> Unit) {
            if (Platform.isFxApplicationThread()) block() else Platform.runLater(block)
        }
    }

    /** The JavaFX application thread, as a dispatcher — where a WebEngine must be touched. */
    private object JavaFx : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) = runOnFx { block.run() }

        override fun isDispatchNeeded(context: CoroutineContext): Boolean = !Platform.isFxApplicationThread()
    }
}
