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
 * Interactive Windows and macOS sign-in for Chromium browsers.
 *
 * Current Chrome protects account cookies with App-Bound Encryption on Windows and the login
 * Keychain on macOS, deliberately preventing a different executable from decrypting the main
 * profile. Google also rejects account login while remote debugging is active. We therefore sign in
 * normally in a separate browser profile first; after that browser quits, the same profile is
 * opened headlessly with loopback debugging only long enough for Chrome to hand the completed
 * session back to BitChord.
 */
internal object DesktopBrowserSignIn {
    /** @param isDefault whether this is the browser macOS opens links in */
    data class Browser(val name: String, val executable: Path, val isDefault: Boolean = false) {
        val label: String get() = "Sign in with $name"

        /** Closing the last window ends a Windows browser; a macOS one keeps running until quit. */
        val finish: String get() = if (DesktopPlatform.isMac) "quit $name (⌘Q)" else "close $name"

        /** On macOS the browser is closed for the listener as soon as the session appears. */
        val closesItself: Boolean get() = DesktopPlatform.isMac
    }

    /** What a sign-in is for: where it starts, and the cookies that show it has finished. */
    enum class Service(
        val title: String,
        internal val startUrl: String,
        internal val cookieDomain: String,
        internal val signingCookies: Set<String>,
    ) {
        YOUTUBE_MUSIC(
            "YouTube Music",
            "https://music.youtube.com/",
            "youtube.com",
            DesktopBrowserCookies.SIGNING_COOKIES,
        ),

        /** `sp_dc` is the cookie Spotify Canvas runs on; see [DesktopSpotifyToken]. */
        SPOTIFY(
            "Spotify",
            "https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F",
            "spotify.com",
            setOf(SPOTIFY_COOKIE),
        ),
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** The first installed browser we can use for the interactive path. */
    fun preferred(): Browser? = when {
        DesktopPlatform.isWindows -> preferredWindows()
        DesktopPlatform.isMac -> preferredMac()
        else -> null
    }

    private fun preferredWindows(): Browser? {
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

    /**
     * The listener's default browser when it is one Chromium we can drive, else the first one
     * installed. Safari and Firefox speak no Chrome DevTools Protocol, so they cannot hand back a
     * session this way.
     */
    private fun preferredMac(): Browser? {
        val roots = listOf(Paths.get("/Applications"), Paths.get(System.getProperty("user.home"), "Applications"))
        val installed = MAC_BROWSERS.mapNotNull { (bundleId, name, executable) ->
            roots.firstNotNullOfOrNull { candidate(name, it.resolve(executable)) }?.let { bundleId to it }
        }
        val default = macDefaultBrowserId()
        return installed.firstOrNull { it.first == default }?.second?.copy(isDefault = true)
            ?: installed.firstOrNull()?.second
    }

    /**
     * The bundle id LaunchServices opens https links with, lower-cased. Null when it cannot be read
     * or the listener never changed it from Safari, which leaves no https entry at all.
     */
    private fun macDefaultBrowserId(): String? = runCatching {
        val plist = Paths.get(System.getProperty("user.home"))
            .resolve("Library/Preferences/com.apple.LaunchServices/com.apple.launchservices.secure.plist")
        if (!Files.isRegularFile(plist)) return null
        val process = ProcessBuilder("/usr/bin/plutil", "-extract", "LSHandlers", "json", "-o", "-", plist.toString())
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        val text = process.inputStream.bufferedReader().use { it.readText() }
        if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly()
        httpsHandlerId(text)
    }.getOrNull()

    /** The https entry's bundle id from LaunchServices' `LSHandlers` array, as JSON. */
    internal fun httpsHandlerId(handlers: String): String? = runCatching {
        json.parseToJsonElement(handlers).jsonArray
            .map { it.jsonObject }
            .firstOrNull { it["LSHandlerURLScheme"]?.jsonPrimitive?.content == "https" }
            ?.get("LSHandlerRoleAll")?.jsonPrimitive?.content?.lowercase()
    }.getOrNull()

    /** The `sp_dc` value from a Spotify sign-in in [browser]. */
    suspend fun captureSpotify(browser: Browser): String =
        capture(browser, Service.SPOTIFY).split(';')
            .map { it.trim() }
            .first { it.substringBefore('=') == SPOTIFY_COOKIE }
            .substringAfter('=')

    /**
     * Opens [browser] normally for sign-in to [service], then reads the finished session in a
     * headless pass. Returns the cookies for the service's domain as a `Cookie` header value.
     */
    suspend fun capture(
        browser: Browser,
        service: Service = Service.YOUTUBE_MUSIC,
    ): String = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val profile = signInRoot()
            .resolve("BitChord")
            .resolve("Browser Sign In")
            // YouTube keeps the folder it always had (Windows keeps that profile between sign-ins);
            // the others get their own, so two sign-ins at once never share one browser instance.
            .resolve(if (service == Service.YOUTUBE_MUSIC) browser.name else "${browser.name} ${service.title}")
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
            *macKeychainArgs(),
            service.startUrl,
        )
        var captureProcess: Process? = null
        var cdp: DevTools? = null
        try {
            // Google sees an ordinary Chrome launch here. The close is the listener's explicit
            // signal that login is finished and the cookie database has been flushed to disk.
            var nextSessionCheck = 0L
            while (signInProcess?.isAlive == true) {
                if (browser.closesItself && System.currentTimeMillis() >= nextSessionCheck) {
                    nextSessionCheck = System.currentTimeMillis() + SESSION_POLL_MS
                    if (DesktopBrowserCookies.chromiumHasCookie(profile, service.cookieDomain, service.signingCookies)) {
                        DesktopTrackLog.log("sign-in: ${browser.name} holds the session; closing it")
                        signInProcess.quit()
                        break
                    }
                }
                delay(BROWSER_CLOSE_POLL_MS)
            }
            DesktopTrackLog.log("sign-in: ${browser.name} quit; reading the session headlessly")
            signInProcess = null
            Files.deleteIfExists(portFile)

            val readerProcess = launch(
                browser,
                profile,
                "--headless=new",
                "--disable-background-mode",
                "--remote-debugging-port=0",
                "--remote-debugging-address=127.0.0.1",
                *macKeychainArgs(),
                "about:blank",
            )
            captureProcess = readerProcess
            val endpoint = waitForEndpoint(portFile, readerProcess)
            cdp = DevTools(endpoint)
            repeat(CAPTURE_POLLS) {
                val header = cdp.cookieHeader(service.cookieDomain)
                if (it == CAPTURE_POLLS - 1) {
                    val names = header.split(';').map { cookie -> cookie.substringBefore('=').trim() }
                    DesktopTrackLog.log("sign-in: no signing cookie among ${names.size}: $names")
                }
                if (header.holdsAny(service.signingCookies)) {
                    DesktopTrackLog.log("sign-in: ${service.title} session captured from ${browser.name}")
                    return@withContext header
                }
                delay(POLL_INTERVAL_MS)
            }
            error(
                if (browser.closesItself) {
                    "${browser.name} closed before the sign-in finished. Try again and leave it " +
                        "open; it closes by itself once you are signed in."
                } else {
                    "${browser.name} did not contain a signed-in ${service.title} session. Try again and " +
                        "${browser.finish} only after ${service.title} has opened."
                },
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            cdp?.closeBrowser()
            signInProcess?.stop()
            captureProcess?.stop()
            if (DesktopPlatform.isMac) forget(profile, signInProcess, captureProcess)
        }
    }

    /** Whether a `Cookie` header value carries a non-empty value for any of [names]. */
    private fun String.holdsAny(names: Set<String>): Boolean = split(';').any { entry ->
        entry.substringBefore('=').trim() in names && entry.substringAfter('=', "").isNotBlank()
    }

    /**
     * Deletes the macOS sign-in profile once the attempt is over, captured or not. Its cookies sit
     * under the mock Keychain key, so effectively in the clear, and BitChord keeps its own copy of
     * the session; the cost is a full Google sign-in next time, which is rare.
     */
    private fun forget(profile: Path, vararg processes: Process?) {
        // The browser still writes into the profile while it shuts down.
        processes.filterNotNull().forEach { runCatching { it.waitFor(PROFILE_RELEASE_SECONDS, TimeUnit.SECONDS) } }
        if (!profile.toFile().deleteRecursively()) {
            DesktopTrackLog.log("sign-in: could not fully delete ${profile.fileName} profile")
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

    /**
     * Quits the browser the way ⌘Q would: Chromium treats SIGTERM as a normal shutdown and writes
     * its cookies out first. Forced only if it has not gone in time.
     */
    private fun Process.quit() {
        destroy()
        if (!waitFor(QUIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) stop()
    }

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

    private fun signInRoot(): Path = if (DesktopPlatform.isMac) {
        Paths.get(System.getProperty("user.home"), "Library", "Application Support")
    } else {
        environmentPath("LOCALAPPDATA", "AppData/Local")
    }

    /**
     * Headless Chrome on macOS reads cookies with a mock Keychain key, so cookies the windowed run
     * sealed with the real login Keychain would come back unreadable. Both runs use the mock key
     * instead; the profile is BitChord's own, holds nothing but this sign-in, and is deleted after it.
     */
    private fun macKeychainArgs(): Array<String> =
        if (DesktopPlatform.isMac) arrayOf("--use-mock-keychain") else emptyArray()

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

        fun cookieHeader(domain: String): String {
            val cookies = command("Storage.getCookies")["result"]
                ?.jsonObject
                ?.get("cookies")
                ?.jsonArray
                .orEmpty()
            val jar = LinkedHashMap<String, String>()
            cookies.forEach { element ->
                val cookie = element.jsonObject
                val host = cookie["domain"]?.jsonPrimitive?.content.orEmpty().removePrefix(".")
                if (host != domain && !host.endsWith(".$domain")) return@forEach
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

    /** Bundle id (lower-cased, as LaunchServices may store it either way), name, executable. */
    private val MAC_BROWSERS = listOf(
        Triple("com.google.chrome", "Chrome", "Google Chrome.app/Contents/MacOS/Google Chrome"),
        Triple("com.microsoft.edgemac", "Edge", "Microsoft Edge.app/Contents/MacOS/Microsoft Edge"),
        Triple("com.brave.browser", "Brave", "Brave Browser.app/Contents/MacOS/Brave Browser"),
        Triple("com.vivaldi.vivaldi", "Vivaldi", "Vivaldi.app/Contents/MacOS/Vivaldi"),
        Triple("org.chromium.chromium", "Chromium", "Chromium.app/Contents/MacOS/Chromium"),
    )

    private const val SPOTIFY_COOKIE = "sp_dc"
    private const val PROFILE_RELEASE_SECONDS = 5L
    private const val QUIT_TIMEOUT_SECONDS = 10L

    /** Chromium writes cookies out every 30 seconds or so, so checking faster gains little. */
    private const val SESSION_POLL_MS = 2_000L
    private const val DEVTOOLS_ACTIVE_PORT = "DevToolsActivePort"
    private const val STARTUP_POLLS = 200
    private const val STARTUP_POLL_MS = 100L
    private const val BROWSER_CLOSE_POLL_MS = 250L
    private const val CAPTURE_POLLS = 15
    private const val POLL_INTERVAL_MS = 1_000L
}
