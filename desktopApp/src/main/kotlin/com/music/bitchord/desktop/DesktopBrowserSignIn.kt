package com.music.bitchord.desktop

import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException

/**
 * Interactive Windows sign-in for Chromium browsers.
 *
 * Current Chrome protects account cookies with App-Bound Encryption, deliberately preventing a
 * different executable from decrypting the main profile. Google also rejects account login while
 * remote debugging is active. We therefore sign in normally in a separate browser profile first;
 * after that window closes, the same profile is opened headlessly with loopback debugging only
 * long enough for Chrome to hand the completed session back to BitChord.
 */
internal object DesktopBrowserSignIn {
    data class Browser(val name: String, val executable: Path) {
        val label: String get() = "Sign in with $name"
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** The first installed browser we can use for the interactive Windows path. */
    fun preferred(): Browser? {
        if (!DesktopPlatform.isWindows) return null
        val local = environmentPath("LOCALAPPDATA", "AppData/Local")
        val programFiles = System.getenv("ProgramFiles")?.let(Paths::get)
        val programFilesX86 = System.getenv("ProgramFiles(x86)")?.let(Paths::get)
        return listOfNotNull(
            candidate("Chrome", local.resolve("Google/Chrome/Application/chrome.exe")),
            programFiles?.resolve("Google/Chrome/Application/chrome.exe")?.let { candidate("Chrome", it) },
            programFilesX86?.resolve("Google/Chrome/Application/chrome.exe")?.let { candidate("Chrome", it) },
            candidate("Edge", local.resolve("Microsoft/Edge/Application/msedge.exe")),
            programFiles?.resolve("Microsoft/Edge/Application/msedge.exe")?.let { candidate("Edge", it) },
            programFilesX86?.resolve("Microsoft/Edge/Application/msedge.exe")?.let { candidate("Edge", it) },
            candidate("Brave", local.resolve("BraveSoftware/Brave-Browser/Application/brave.exe")),
            candidate("Vivaldi", local.resolve("Vivaldi/Application/vivaldi.exe")),
        ).firstOrNull()
    }

    /** Opens [browser] normally for sign-in, then reads the finished session in a headless pass. */
    suspend fun capture(browser: Browser): String = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val profile = environmentPath("LOCALAPPDATA", "AppData/Local")
            .resolve("BitChord")
            .resolve("Browser Sign In")
            .resolve(browser.name)
        Files.createDirectories(profile)
        val portFile = profile.resolve(DEVTOOLS_ACTIVE_PORT)
        Files.deleteIfExists(portFile)

        var signInProcess: Process? = launch(
            browser,
            profile,
            "--disable-background-mode",
            "--no-first-run",
            "--no-default-browser-check",
            "--new-window",
            MUSIC_URL,
        )
        var captureProcess: Process? = null
        var cdp: DevTools? = null
        try {
            // Google sees an ordinary Chrome launch here. The close is the listener's explicit
            // signal that login is finished and the cookie database has been flushed to disk.
            while (signInProcess?.isAlive == true) delay(BROWSER_CLOSE_POLL_MS)
            signInProcess = null
            Files.deleteIfExists(portFile)

            val readerProcess = launch(
                browser,
                profile,
                "--headless=new",
                "--disable-background-mode",
                "--remote-debugging-port=0",
                "--remote-debugging-address=127.0.0.1",
                "about:blank",
            )
            captureProcess = readerProcess
            val endpoint = waitForEndpoint(portFile, readerProcess)
            cdp = DevTools(endpoint)
            repeat(CAPTURE_POLLS) {
                val header = cdp.youtubeCookieHeader()
                if (DesktopBrowserCookies.hasSigningSecret(header)) return@withContext header
                delay(POLL_INTERVAL_MS)
            }
            error("Chrome did not contain a signed-in YouTube session. Try again and close Chrome only after YouTube Music has opened.")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            cdp?.closeBrowser()
            signInProcess?.stop()
            captureProcess?.stop()
        }
    }

    private fun launch(browser: Browser, profile: Path, vararg arguments: String): Process =
        ProcessBuilder(
            browser.executable.toString(),
            "--user-data-dir=${profile.toAbsolutePath()}",
            *arguments,
        )
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()

    private fun Process.stop() {
        descendants().forEach { child -> runCatching { child.destroy() } }
        runCatching { destroy() }
    }

    private suspend fun waitForEndpoint(portFile: Path, process: Process): URI {
        repeat(STARTUP_POLLS) {
            val lines = runCatching { Files.readAllLines(portFile) }.getOrNull()
            if (lines != null && lines.size >= 2) {
                val port = lines[0].trim().toIntOrNull()
                val path = lines[1].trim()
                if (port != null && path.startsWith("/")) return URI("ws://127.0.0.1:$port$path")
            }
            if (!process.isAlive) error("The browser closed before YouTube Music opened.")
            delay(STARTUP_POLL_MS)
        }
        error("Could not connect to ${portFile.parent.fileName}. Close its other sign-in window and try again.")
    }

    private fun candidate(name: String, path: Path): Browser? =
        path.takeIf(Files::isRegularFile)?.let { Browser(name, it) }

    private fun environmentPath(variable: String, fallback: String): Path =
        System.getenv(variable)?.takeIf(String::isNotBlank)?.let(Paths::get)
            ?: Paths.get(System.getProperty("user.home")).resolve(fallback)

    /** A minimal request/response client for the browser-level Chrome DevTools Protocol socket. */
    private class DevTools(endpoint: URI) : WebSocket.Listener {
        private val sequence = AtomicInteger()
        private val waiting = ConcurrentHashMap<Int, CompletableFuture<JsonObject>>()
        private val text = StringBuilder()
        private val socket: WebSocket = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()
            .newWebSocketBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .buildAsync(endpoint, this)
            .get(10, TimeUnit.SECONDS)

        override fun onOpen(webSocket: WebSocket) {
            webSocket.request(1)
        }

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            text.append(data)
            if (last) {
                val message = runCatching { json.parseToJsonElement(text.toString()).jsonObject }.getOrNull()
                text.setLength(0)
                val id = message?.get("id")?.jsonPrimitive?.content?.toIntOrNull()
                if (id != null) waiting.remove(id)?.complete(message)
            }
            webSocket.request(1)
            return null
        }

        override fun onError(webSocket: WebSocket, error: Throwable) {
            waiting.values.forEach { it.completeExceptionally(error) }
            waiting.clear()
        }

        fun command(method: String): JsonObject {
            val id = sequence.incrementAndGet()
            val response = CompletableFuture<JsonObject>()
            waiting[id] = response
            val request = buildJsonObject {
                put("id", id)
                put("method", method)
            }
            try {
                socket.sendText(request.toString(), true).get(10, TimeUnit.SECONDS)
                val message = response.get(10, TimeUnit.SECONDS)
                message["error"]?.let { error(it.toString()) }
                return message
            } finally {
                waiting.remove(id)
            }
        }

        fun youtubeCookieHeader(): String {
            val cookies = command("Storage.getCookies")["result"]
                ?.jsonObject
                ?.get("cookies")
                ?.jsonArray
                .orEmpty()
            val jar = LinkedHashMap<String, String>()
            cookies.forEach { element ->
                val cookie = element.jsonObject
                val domain = cookie["domain"]?.jsonPrimitive?.content.orEmpty().removePrefix(".")
                if (domain != "youtube.com" && !domain.endsWith(".youtube.com")) return@forEach
                val name = cookie["name"]?.jsonPrimitive?.content.orEmpty()
                val value = cookie["value"]?.jsonPrimitive?.content.orEmpty()
                if (name.isNotBlank() && value.isNotBlank()) jar[name] = value
            }
            return jar.entries.joinToString("; ") { "${it.key}=${it.value}" }
        }

        fun closeBrowser() {
            val request = buildJsonObject {
                put("id", sequence.incrementAndGet())
                put("method", "Browser.close")
            }
            runCatching { socket.sendText(request.toString(), true).get(2, TimeUnit.SECONDS) }
            runCatching { socket.abort() }
        }
    }

    private const val MUSIC_URL = "https://music.youtube.com/"
    private const val DEVTOOLS_ACTIVE_PORT = "DevToolsActivePort"
    private const val STARTUP_POLLS = 200
    private const val STARTUP_POLL_MS = 100L
    private const val BROWSER_CLOSE_POLL_MS = 250L
    private const val CAPTURE_POLLS = 15
    private const val POLL_INTERVAL_MS = 1_000L
}
