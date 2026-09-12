package com.music.bitchord.desktop

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Locale

/** Desktop counterpart of Android's NewPipe-backed YouTube URL unlocker. */
internal object DesktopYouTubeUnlocker {
    private val lock = Any()
    private val initialized = lazy {
        NewPipe.init(DesktopDownloader())
    }

    fun deobfuscate(videoId: String, url: String): String {
        if (!url.contains("n=")) return url
        return synchronized(lock) {
            runCatching {
                initialized.value
                YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(videoId, url)
            }.getOrElse { url }
        }
    }

    /** The watch page, scraped, as the last thing to try. */
    fun extractAudio(videoId: String): List<ExtractedAudio> = synchronized(lock) {
        runCatching {
            initialized.value
            val extractor = ServiceList.YouTube.getStreamExtractor(
                "https://www.youtube.com/watch?v=$videoId",
            )
            extractor.fetchPage()
            extractor.audioStreams
                // Progressive only: a DASH or HLS entry carries a manifest rather than a URL, and
                // the decoder is handed URLs.
                .filter { !it.content.isNullOrBlank() && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
                .map {
                    ExtractedAudio(
                        url = it.content,
                        kbps = it.averageBitrate.takeIf { rate -> rate > 0 },
                        // The container, which is all NewPipe's mime type reports: Opus arrives as
                        // `audio/webm`, identical to the Vorbis entry beside it.
                        mimeType = it.format?.mimeType,
                    )
                }
        }.getOrElse {
            DesktopTrackLog.log("  youtube: the watch page would not give up a stream — ${it.message}")
            emptyList()
        }
    }

    /** One progressive audio rendition the watch page offered. */
    data class ExtractedAudio(val url: String, val kbps: Int?, val mimeType: String?)

    /** Returns the current player-script timestamp for ciphered clients. */
    fun signatureTimestamp(videoId: String): Int? = synchronized(lock) {
        runCatching {
            initialized.value
            YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId)
        }.getOrNull()
    }

    /** Resolves either a direct player URL or a signature-ciphered format. */
    fun urlFor(videoId: String, directUrl: String?, cipher: String?): String? {
        val direct = directUrl?.takeIf(String::isNotBlank)
        if (direct != null) return deobfuscate(videoId, direct)
        if (cipher.isNullOrBlank()) return null

        val parameters = cipher.split('&')
            .mapNotNull { part ->
                val separator = part.indexOf('=').takeIf { it > 0 } ?: return@mapNotNull null
                URLDecoder.decode(part.substring(0, separator), StandardCharsets.UTF_8) to
                    URLDecoder.decode(part.substring(separator + 1), StandardCharsets.UTF_8)
            }
            .toMap()
        val base = parameters["url"] ?: return null
        val signature = parameters["s"] ?: return null
        val parameter = parameters["sp"] ?: "signature"
        val solved = synchronized(lock) {
            runCatching {
                initialized.value
                YoutubeJavaScriptPlayerManager.deobfuscateSignature(videoId, signature)
            }.getOrNull()
        } ?: return null
        val separator = if ('?' in base) '&' else '?'
        val unlocked = base + separator + parameter + "=" +
            URLEncoder.encode(solved, StandardCharsets.UTF_8)
        return deobfuscate(videoId, unlocked)
    }

    private class DesktopDownloader : Downloader() {
        private val client = DesktopYouTubeHttpClient.client

        override fun execute(request: Request): Response {
            val builder = HttpRequest.newBuilder(URI.create(request.url()))
                .timeout(Duration.ofSeconds(30))
            var hasUserAgent = false
            request.headers().forEach { (name, values) ->
                if (name.equals("User-Agent", ignoreCase = true)) hasUserAgent = true
                values.forEach { value -> runCatching { builder.header(name, value) } }
            }
            if (!hasUserAgent) builder.header("User-Agent", USER_AGENT)
            if (request.headers().keys.none { it.equals("Cookie", ignoreCase = true) }) {
                (DesktopYouTubeAuth.session?.cookie ?: DesktopYouTubeAuth.environmentCookie())
                    ?.let { builder.header("Cookie", it) }
            }

            val method = request.httpMethod().uppercase(Locale.ROOT)
            val body = request.dataToSend()
            when (method) {
                "GET" -> builder.GET()
                "HEAD" -> builder.method("HEAD", HttpRequest.BodyPublishers.noBody())
                else -> builder.method(
                    method,
                    body?.let(HttpRequest.BodyPublishers::ofByteArray)
                        ?: HttpRequest.BodyPublishers.noBody(),
                )
            }
            val response = client.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8),
            )
            if (response.statusCode() == 429) {
                throw ReCaptchaException("reCaptcha Challenge requested", request.url())
            }
            return Response(
                response.statusCode(),
                response.headers().firstValue(":status").orElse(""),
                response.headers().map(),
                response.body(),
                response.uri().toString(),
            )
        }
    }

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
}
