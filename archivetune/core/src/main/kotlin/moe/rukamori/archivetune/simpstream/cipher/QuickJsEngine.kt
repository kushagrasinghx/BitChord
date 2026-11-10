/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Ported from SimpMusic (https://github.com/maxrave-dev/SimpMusic),
 * core/service/kotlinYtmusicScraper cipher package — GPL-3.0, © maxrave-dev.
 * Logic kept byte-for-byte; only the package name and imports changed.
 */

package moe.rukamori.archivetune.simpstream.cipher

import com.dokar.quickjs.QuickJs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Wrapper around the QuickJS engine for executing JavaScript code.
 * This is used to run the YouTube player cipher deobfuscation logic.
 *
 * Based on yt-dlp's QuickJS integration approach.
 */
internal class QuickJsEngine(
    /**
     * The one thread this runtime lives on. Supplied from outside because it must be created with a
     * large stack: QuickJS runs YouTube's n-transform on the *native* stack, and the JVM's default
     * ~1 MB thread is not enough for it.
     */
    private val jsThread: CoroutineDispatcher,
) {
    companion object {
        private const val EVALUATION_TIMEOUT_MS = 10_000L

        // Current ~2.6 MiB player scripts need more than 128 MiB while EJS builds their AST.
        private const val NATIVE_MEMORY_LIMIT_BYTES = 192L * 1024L * 1024L

        /**
         * QuickJS defaults this to 256 KB and YouTube's n-transform goes deeper, at which point the
         * engine does NOT reliably raise a JS error — it walks off the stack and the process dies
         * with SIGSEGV, below the JVM and with no hs_err file. Kept well under the stack the thread
         * is actually created with, so the limit is hit before the real stack ends.
         */
        private const val NATIVE_STACK_LIMIT_BYTES = 8L * 1024L * 1024L
        private const val MAX_EVALUATION_RESULT_LENGTH = 16 * 1024 * 1024
        private const val MAX_FUNCTION_INPUT_LENGTH = 64 * 1024
        private const val MAX_FUNCTION_RESULT_LENGTH = 256 * 1024
        private val JS_IDENTIFIER = Regex("[A-Za-z_$][A-Za-z0-9_$]*")

        /** Valid JavaScript double-quoted string literal for passing into evaluated calls. */
        internal fun jsStringLiteral(s: String): String =
            buildString(s.length + 2) {
                append('"')
                for (c in s) {
                    when (c) {
                        '\\' -> {
                            append("\\\\")
                        }

                        '"' -> {
                            append("\\\"")
                        }

                        '\n' -> {
                            append("\\n")
                        }

                        '\r' -> {
                            append("\\r")
                        }

                        '\t' -> {
                            append("\\t")
                        }

                        else -> {
                            if (c.code < 0x20) {
                                append("\\u%04x".format(c.code))
                            } else {
                                append(c)
                            }
                        }
                    }
                }
                append('"')
            }
    }

    private val mutex = Mutex()
    private var quickJs: QuickJs? = null

    /**
     * Initialize the QuickJS runtime.
     */
    suspend fun initialize() =
        withContext(jsThread) {
            mutex.withLock {
                if (quickJs == null) {
                    quickJs =
                        QuickJs
                            .create(jsThread)
                            .also {
                                it.evaluationTimeoutMillis = EVALUATION_TIMEOUT_MS
                                it.memoryLimit = NATIVE_MEMORY_LIMIT_BYTES
                                it.maxStackSize = NATIVE_STACK_LIMIT_BYTES
                            }
                }
            }
        }

    /**
     * Execute JavaScript code and return the result.
     *
     * @param code The JavaScript code to execute
     * @return The result of the execution as a string
     */
    suspend fun evaluate(
        code: String,
        maxResultLength: Int,
    ): String =
        withContext(jsThread) {
            require(maxResultLength in 1..MAX_EVALUATION_RESULT_LENGTH) { "Invalid QuickJS result limit" }
            mutex.withLock {
                val runtime = quickJs ?: throw IllegalStateException("QuickJS not initialized")
                val boundedCode =
                    """
                    (function() {
                      const value = ($code);
                      if (value == null) return "";
                      const text = String(value);
                      return text.length <= $maxResultLength ? text : "";
                    })()
                    """.trimIndent()
                runtime.evaluate<String?>(boundedCode).orEmpty()
            }
        }

    /** Execute JavaScript for side effects without asking QuickJS to marshal the final value. */
    suspend fun execute(code: String) {
        withContext(jsThread) {
            mutex.withLock {
                val runtime = quickJs ?: throw IllegalStateException("QuickJS not initialized")
                runtime.evaluate<Any?>("$code\n;undefined;")
            }
        }
    }

    /**
     * Execute a JavaScript function with parameters.
     *
     * @param functionName The name of the function to call
     * @param input The string parameter to pass to the function
     * @return The bounded string result, or null when the input or result is too large
     */
    suspend fun callFunction(
        functionName: String,
        input: String,
    ): String? =
        withContext(jsThread) {
            require(JS_IDENTIFIER.matches(functionName)) { "Invalid JavaScript function name" }
            if (input.length > MAX_FUNCTION_INPUT_LENGTH) return@withContext null
            mutex.withLock {
                val runtime = quickJs ?: throw IllegalStateException("QuickJS not initialized")
                val inputLiteral = jsStringLiteral(input)
                runtime.evaluate<String?>(
                    """
                    (function() {
                      const value = $functionName($inputLiteral);
                      if (value == null) return null;
                      const text = String(value);
                      return text.length <= $MAX_FUNCTION_RESULT_LENGTH ? text : null;
                    })()
                    """.trimIndent(),
                )
            }
        }

    /**
     * Set up the global environment for YouTube player execution.
     * This creates necessary globals like XMLHttpRequest, URL, location, etc.
     */
    suspend fun setupYoutubeGlobals() =
        withContext(jsThread) {
            val setupCode =
                """
                if (typeof globalThis.XMLHttpRequest === "undefined") {
                    globalThis.XMLHttpRequest = { prototype: {} };
                }
                if (typeof URL === "undefined") {
                    globalThis.location = {
                        hash: "",
                        host: "www.youtube.com",
                        hostname: "www.youtube.com",
                        href: "https://www.youtube.com/watch?v=yt-dlp-wins",
                        origin: "https://www.youtube.com",
                        password: "",
                        pathname: "/watch",
                        port: "",
                        protocol: "https:",
                        search: "?v=yt-dlp-wins",
                        username: "",
                    };
                } else {
                    globalThis.location = new URL("https://www.youtube.com/watch?v=yt-dlp-wins");
                }
                if (typeof globalThis.document === "undefined") {
                    globalThis.document = Object.create(null);
                }
                if (typeof globalThis.navigator === "undefined") {
                    globalThis.navigator = Object.create(null);
                }
                if (typeof globalThis.self === "undefined") {
                    globalThis.self = globalThis;
                }
                if (typeof globalThis.window === "undefined") {
                    globalThis.window = globalThis;
                }
                if (typeof globalThis.Intl === "undefined") {
                    const NumberFormat = function(locale, options) {
                        this.options = options || {};
                    };
                    NumberFormat.supportedLocalesOf = function(locales) {
                        return Array.isArray(locales) ? locales : [locales];
                    };
                    NumberFormat.prototype.format = function(value) {
                        let formatted = String(value);
                        const minimumDigits = this.options.minimumIntegerDigits || 0;
                        while (formatted.length < minimumDigits) formatted = "0" + formatted;
                        return formatted;
                    };
                    const DateTimeFormat = function() {};
                    DateTimeFormat.prototype.resolvedOptions = function() {
                        return { timeZone: "UTC" };
                    };
                    DateTimeFormat.prototype.format = function(value) {
                        return String(value);
                    };
                    globalThis.Intl = { NumberFormat, DateTimeFormat };
                }
                """.trimIndent()

            execute(setupCode)
        }

    /**
     * Load the YouTube player JavaScript code into the engine.
     *
     * @param playerCode The YouTube player JavaScript code
     */
    suspend fun loadPlayerScript(playerCode: String) =
        withContext(jsThread) {
            // First setup globals
            setupYoutubeGlobals()

            // Then load the player code
            execute(playerCode)
        }

    /**
     * Clean up and release resources.
     */
    suspend fun dispose() {
        withContext(jsThread) {
            mutex.withLock {
                quickJs?.close()
                quickJs = null
            }
        }
    }
}
