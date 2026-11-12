package com.music.bitchord.data.webdav

import com.music.bitchord.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.Source
import okio.buffer
import okio.source
import org.w3c.dom.Element
import java.io.IOException
import java.io.InputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Minimal WebDAV client: PROPFIND directory listings over the shared
 * [Http.client], with HTTP Basic auth.
 *
 * Depth 1 is used per directory with manual recursion rather than
 * `Depth: infinity`, which many servers (notably Nextcloud) reject. XML is
 * parsed with the platform DOM parser so unit tests run on the JVM without
 * Android.
 */
object WebDavClient {

    data class Entry(
        val url: String,
        val displayName: String,
        val isCollection: Boolean,
        val contentType: String? = null,
    )

    private val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:">
  <d:prop>
    <d:displayname/>
    <d:resourcetype/>
    <d:getcontenttype/>
  </d:prop>
</d:propfind>
""".trimIndent().toRequestBody("application/xml; charset=utf-8".toMediaType())

    suspend fun testConnection(url: String, username: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val target = WebDavConfig.normalizeUrl(url)
                require(WebDavConfig.isConfigured(target)) { "Enter a http(s) server URL" }
                val request = propfindRequest(target, depth = "0", username, password)
                Http.client.newCall(request).execute().use { response ->
                    if (response.code !in 200..299 && response.code != 207) {
                        throw WebDavException("Server answered ${response.code}")
                    }
                }
            }
        }

    /**
     * Everything a library view needs: the audio plus the pictures filed
     * beside it. Covers travel as `cover.jpg`/`folder.jpg` next to the music
     * on every conventional server layout, and the listing that finds the
     * tracks already walks past them — a second pass would pay the whole
     * traversal again for files already in hand.
     */
    data class Listing(val audio: List<Entry>, val images: List<Entry>)

    suspend fun listLibrary(
        baseUrl: String,
        username: String,
        password: String,
        maxFiles: Int = 10_000,
    ): Result<Listing> = withContext(Dispatchers.IO) {
        runCatching {
            val root = WebDavConfig.normalizeUrl(baseUrl)
            require(WebDavConfig.isConfigured(root)) { "WebDAV is not configured" }
            val foundAudio = LinkedHashMap<String, Entry>()
            val foundImages = LinkedHashMap<String, Entry>()
            val dirs = ArrayDeque<String>()
            dirs.add(root)
            val visited = HashSet<String>()
            while (dirs.isNotEmpty() && foundAudio.size < maxFiles) {
                val dir = dirs.removeFirst()
                if (!visited.add(dir.lowercase())) continue
                val entries = propfind(dir, username, password).getOrThrow()
                for (entry in entries) {
                    if (entry.isCollection) {
                        if (!visited.contains(entry.url.lowercase())) dirs.add(entry.url)
                    } else {
                        val name = entry.displayName.ifBlank { entry.url }
                        when {
                            WebDavConfig.isAudioFile(name) -> foundAudio.putIfAbsent(entry.url, entry)
                            isImageFile(name) -> foundImages.putIfAbsent(entry.url, entry)
                        }
                    }
                }
            }
            Listing(foundAudio.values.toList(), foundImages.values.toList())
        }
    }

    suspend fun listAudioFiles(
        baseUrl: String,
        username: String,
        password: String,
        maxFiles: Int = 10_000,
    ): Result<List<Entry>> = listLibrary(baseUrl, username, password, maxFiles).map { it.audio }

    fun isImageFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return ext == "jpg" || ext == "jpeg" || ext == "png" || ext == "webp"
    }

    private fun propfindRequest(
        url: String,
        depth: String,
        username: String,
        password: String,
    ): Request {
        val builder = Request.Builder()
            .url(url)
            .header("Depth", depth)
            .method("PROPFIND", PROPFIND_BODY)
        WebDavConfig.basicAuthHeader(username, password)?.let { builder.header("Authorization", it) }
        return builder.build()
    }

    /**
     * Whether [fileUrl] already exists. Any answer other than 200/404 is a
     * failure rather than a guess — treating a 500 as "absent" is how an
     * upload silently eats a file it was told not to touch.
     */
    suspend fun exists(fileUrl: String, username: String, password: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            runCatching {
                val builder = Request.Builder().url(fileUrl).head()
                WebDavConfig.basicAuthHeader(username, password)?.let { builder.header("Authorization", it) }
                Http.client.newCall(builder.build()).execute().use { response ->
                    when (response.code) {
                        200 -> true
                        404 -> false
                        else -> throw WebDavException("Exists check failed with ${response.code}")
                    }
                }
            }
        }

    sealed interface PutResult {
        data object Uploaded : PutResult
        /** The server refused a non-overwriting PUT because the file is there. */
        data object AlreadyExists : PutResult
    }

    /**
     * Streams [stream] to [fileUrl] without buffering it in memory — a FLAC
     * can be a hundred megabytes, and holding two copies (ours plus OkHttp's)
     * is what gets a background upload killed.
     *
     * [contentLength] may be -1 when the size isn't knowable up front (a
     * `content://` row that won't say); OkHttp then sends chunked, which
     * Nextcloud and every RFC-compliant server accepts for PUT.
     *
     * Without [overwrite], `If-None-Match: *` makes the absence check atomic:
     * a 412 comes back instead of a clobbered file when something else won
     * the race between [exists] and this call.
     */
    suspend fun putFile(
        fileUrl: String,
        stream: InputStream,
        contentLength: Long,
        mimeType: String,
        username: String,
        password: String,
        overwrite: Boolean,
        onProgress: ((bytesWritten: Long) -> Unit)? = null,
    ): Result<PutResult> = withContext(Dispatchers.IO) {
        runCatching {
            val body = StreamBody(stream, contentLength, mimeType.toMediaType(), onProgress)
            val builder = Request.Builder().url(fileUrl).put(body)
            if (!overwrite) builder.header("If-None-Match", "*")
            WebDavConfig.basicAuthHeader(username, password)?.let { builder.header("Authorization", it) }
            Http.client.newCall(builder.build()).execute().use { response ->
                when (response.code) {
                    in 200..299 -> PutResult.Uploaded
                    412 -> PutResult.AlreadyExists
                    else -> throw WebDavException("Upload failed with ${response.code}")
                }
            }
        }
    }

    /**
     * Creates the collection at [dirUrl], if it isn't one already. 405 means
     * something is already there, which is the happy path wearing an error
     * code; anything else out of 2xx is a real failure.
     */
    suspend fun ensureCollection(dirUrl: String, username: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val builder = Request.Builder().url(dirUrl).method("MKCOL", null)
                WebDavConfig.basicAuthHeader(username, password)?.let { builder.header("Authorization", it) }
                Http.client.newCall(builder.build()).execute().use { response ->
                    if (response.code !in 200..299 && response.code != 405) {
                        throw WebDavException("Could not create folder (${response.code})")
                    }
                }
            }
        }

    private fun propfind(dirUrl: String, username: String, password: String): Result<List<Entry>> {
        return runCatching {
            val request = propfindRequest(dirUrl, depth = "1", username, password)
            Http.client.newCall(request).execute().use { response ->
                if (response.code != 207 && response.code !in 200..299) {
                    throw WebDavException("Listing failed with ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@runCatching emptyList()
                parseMultistatus(body, dirUrl)
            }
        }
    }

    /**
     * Parses a PROPFIND multistatus response. Pure function of the XML and the
     * directory it was asked of — the entry the server echoes back for the
     * directory itself is dropped.
     */
    fun parseMultistatus(xml: String, dirUrl: String): List<Entry> {
        if (xml.isBlank()) return emptyList()
        val doc = runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            factory.newDocumentBuilder().parse(xml.byteInputStream(Charsets.UTF_8))
        }.getOrNull() ?: return emptyList()
        val responses = doc.getElementsByTagNameNS("*", "response")
        val out = ArrayList<Entry>(responses.length)
        for (i in 0 until responses.length) {
            val node = responses.item(i) as? Element ?: continue
            val href = node.getElementsByTagNameNS("*", "href").item(0)?.textContent?.trim().orEmpty()
            if (href.isEmpty()) continue
            val url = resolveHref(dirUrl, href)
            if (isSameUrl(url, dirUrl)) continue
            val displayName = node.getElementsByTagNameNS("*", "displayname")
                .item(0)?.textContent?.trim().orEmpty()
                .ifBlank { decodedName(url) }
            val resourceType = node.getElementsByTagNameNS("*", "resourcetype").item(0) as? Element
            val isCollection = resourceType
                ?.getElementsByTagNameNS("*", "collection")?.length?.let { it > 0 } == true
            val contentType = node.getElementsByTagNameNS("*", "getcontenttype")
                .item(0)?.textContent?.trim()?.takeIf { it.isNotBlank() }
            out.add(Entry(url = url, displayName = displayName, isCollection = isCollection, contentType = contentType))
        }
        return out
    }

    fun resolveHref(baseUrl: String, href: String): String {
        val trimmed = href.trim()
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return trimmed
        }
        val base = WebDavConfig.normalizeUrl(baseUrl)
        val schemeHost = base.substringBefore('/', "").let {
            // Keep "https://host" (and port) only.
            val schemeEnd = base.indexOf("://")
            if (schemeEnd < 0) return trimmed
            val pathStart = base.indexOf('/', schemeEnd + 3)
            if (pathStart < 0) base else base.substring(0, pathStart)
        }
        return if (trimmed.startsWith("/")) "$schemeHost$trimmed" else "$base/$trimmed"
    }

    private fun isSameUrl(a: String, b: String): Boolean {
        fun norm(u: String) = u.trim().trimEnd('/').lowercase()
        return norm(a) == norm(b)
    }

    private fun decodedName(url: String): String = runCatching {
        URLDecoder.decode(url.trimEnd('/').substringAfterLast('/'), "UTF-8")
    }.getOrDefault(url.trimEnd('/').substringAfterLast('/'))

    /**
     * `root/<segment>`, with the segment percent-encoded. `URLEncoder` is a
     * form encoder, so `+` (space) is rewritten to `%20` — a path is not a
     * query string.
     */
    fun joinUrl(root: String, segment: String): String {
        val encoded = URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
        return "${root.trimEnd('/')}/$encoded"
    }

    /**
     * The name a track is stored under: `Artist - Title.ext`, or just the
     * title when there is no artist worth naming. Filesystem-hostile
     * characters never reach the server — a `/` in a title would otherwise
     * file it into a folder nobody asked for.
     */
    fun uploadFileName(title: String, artist: String, extension: String): String {
        val rawBase = if (artist.isNotBlank() && artist != "Unknown Artist") "$artist - $title" else title
        val clean = rawBase
            .map { c -> if (c == '/' || c == '\\' || c.isISOControl()) '_' else c }
            .joinToString("")
            .trim().trim('.')
            .take(120)
            .ifBlank { "track" }
        val ext = extension.lowercase(Locale.ROOT).trimStart('.').takeIf { it.isNotBlank() } ?: "mp3"
        return "$clean.$ext"
    }

    /**
     * First name in `base`, `base (1)`, `base (2)`, … that isn't in [taken].
     * Compared case-insensitively: servers that ignore case would otherwise
     * hand back a "new" name that collides on disk.
     */
    fun resolveNumberedName(base: String, extension: String, taken: Set<String>): String {
        val lowered = taken.map { it.lowercase(Locale.ROOT) }.toSet()
        var candidate = "$base.$extension"
        var n = 1
        while (candidate.lowercase(Locale.ROOT) in lowered && n < 1000) {
            candidate = "$base ($n).$extension"
            n++
        }
        return candidate
    }

    class WebDavException(message: String) : Exception(message)

    private class StreamBody(
        private val stream: InputStream,
        private val length: Long,
        private val mime: MediaType?,
        private val onProgress: ((Long) -> Unit)?,
    ) : RequestBody() {
        override fun contentType(): MediaType? = mime
        override fun contentLength(): Long = length

        override fun writeTo(sink: BufferedSink) {
            var written = 0L
            stream.use { input ->
                val source: Source = input.source()
                source.buffer().use { buffered ->
                    var read: Long
                    while (buffered.read(sink.buffer, 8192).also { read = it } != -1L) {
                        written += read
                        sink.flush()
                        onProgress?.invoke(written)
                    }
                }
            }
            if (length >= 0 && written != length) {
                throw IOException("Short upload: wrote $written of $length bytes")
            }
        }
    }
}
