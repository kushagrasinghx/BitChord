/*
* ArchiveTune (2026)
* © Rukamori — github.com/rukamori
* GPL-3.0 License | Contributors: see git history
* Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
*/

package moe.rukamori.archivetune.innertube

import io.ktor.http.URLBuilder
import io.ktor.http.parseQueryString
import kotlinx.coroutines.CancellationException
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.innertube.models.response.PlayerResponse
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import java.io.IOException
import java.net.Proxy
import java.util.concurrent.TimeUnit

private class NewPipeDownloaderImpl(
    proxy: Proxy?,
) : Downloader() {
    private val client =
        OkHttpClient
            .Builder()
            .proxy(proxy ?: Proxy.NO_PROXY)
            .retryOnConnectionFailure(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .build()

    private fun buildRequest(request: Request): okhttp3.Request {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        return okhttp3.Request
            .Builder()
            .method(httpMethod, dataToSend?.toRequestBody())
            .url(url)
            .also { requestBuilder ->
                var hasUserAgent = false
                headers.forEach { (headerName, headerValueList) ->
                    if (headerName.equals("User-Agent", ignoreCase = true) && headerValueList.isNotEmpty()) {
                        hasUserAgent = true
                    }
                    if (headerValueList.size > 1) {
                        requestBuilder.removeHeader(headerName)
                        headerValueList.forEach { headerValue ->
                            requestBuilder.addHeader(headerName, headerValue)
                        }
                    } else if (headerValueList.size == 1) {
                        requestBuilder.header(headerName, headerValueList[0])
                    }
                }
                if (!hasUserAgent) {
                    requestBuilder.header("User-Agent", YouTubeClient.USER_AGENT_WEB)
                }
            }.build()
    }

    private fun processResponse(response: okhttp3.Response, url: String): Response {
        if (response.code == 429) {
            response.close()
            throw ReCaptchaException("reCaptcha Challenge requested", url)
        }
        val responseBodyStr = response.body.string()
        val latestUrl = response.request.url.toString()
        // BravePipe's Response constructor takes 5 args (no rawBytes) — one of the
        // two API adaptations made when BravePipeExtractor replaced
        // MetrolistExtractor for the SimpMusic stream-resolution port.
        return Response(
            response.code,
            response.message,
            response.headers.toMultimap(),
            responseBodyStr,
            latestUrl,
        )
    }

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val url = request.url()
        val call = client.newCall(buildRequest(request))
        val response = call.execute()
        return processResponse(response, url)
    }

    // NOTE: MetrolistExtractor's Downloader exposed executeAsync with an
    // AsyncCallback/CancellableCall pair; BravePipe's Downloader (the
    // org.schabi.newpipe.extractor artifact used since the SimpMusic port)
    // has no such method — the extractor framework drives it synchronously
    // through execute(), so the async override was dropped rather than ported.
}

object NewPipeUtils {
    init {
        NewPipe.init(NewPipeDownloaderImpl(YouTube.streamProxy))
    }

    /**
     * Forces this object's initialization (which installs the shared proxy-aware
     * OkHttp downloader). Callers that only need the NewPipe extractor machinery —
     * e.g. [NewPipeWatchPageExtractor]'s watch-page stream resolution — call this
     * instead of reaching into the private downloader.
     */
    fun ensureInitialized() = Unit

    suspend fun getSignatureTimestamp(videoId: String): Result<Int> = runCatching {
        withJavaScriptPlayerCacheRecovery {
            YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId)
        }
    }

    /**
     * Echo-Music's stream-URL harvest (Echo's `YouTube.getNewPipeStreamUrls` /
     * `NewPipeExtractor.newPipePlayer`, https://github.com/EchoMusicApp/Echo-Music, GPL-3.0).
     *
     * One `StreamInfo.getInfo` extraction over the plain watch page returns `(itag, url)` pairs
     * for EVERY stream NewPipe can see — audio, video and video-only — with the signature and
     * n-param already deobfuscated by NewPipe's own player-code machinery. Echo substitutes these
     * URLs into the InnerTube player response by itag (see [YouTube.newPipePlayer]) so playback
     * does not depend on solving YouTube's cipher client-side.
     *
     * Blocking (NewPipe's extractor is synchronous), so callers must already be off the main
     * thread — same contract as every other NewPipe entry point in this file.
     */
    fun newPipeStreamUrls(videoId: String): List<Pair<Int, String>> {
        val streamsList = mutableListOf<org.schabi.newpipe.extractor.stream.Stream>()
        try {
            val streamInfo =
                org.schabi.newpipe.extractor.stream.StreamInfo.getInfo(
                    NewPipe.getService(0),
                    "https://www.youtube.com/watch?v=$videoId",
                )
            streamsList.addAll(streamInfo.audioStreams + streamInfo.videoStreams + streamInfo.videoOnlyStreams)
        } catch (e: Exception) {
            // Echo's behaviour verbatim: the three extractors (BravePipe / NewPipe / PipePipe)
            // share the exact same org.schabi.newpipe package so D8 merges them into one — only
            // the first loaded JAR's classes exist at runtime. A failure here is caught and
            // ignored; the caller falls back to the format's own URL / signatureCipher.
            e.printStackTrace()
        }

        return try {
            streamsList.mapNotNull { stream ->
                (stream.itagItem?.id ?: return@mapNotNull null) to stream.content
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Resolves a playable stream URL for [format].
     *
     * The n-param (throttling) transform is applied here, and the GVS PO token for [client] is
     * appended to the result, so callers can hand the URL straight to the player. [authState]
     * defaults to the live session state; playback paths pass the state they resolved the player
     * response with so the token matches that request.
     */
    suspend fun getStreamUrl(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        client: YouTubeClient? = null,
        authState: PlaybackAuthState = YouTube.currentPlaybackAuthState(),
    ): Result<String> {
        try {
            val directUrl = format.url
            if (directUrl != null) {
                val resolvedDirectUrl =
                    if (directUrl.toHttpUrlOrNull()?.queryParameter("n")?.isNotBlank() == true) {
                        getUrlWithThrottlingParameterDeobfuscated(videoId, directUrl)
                    } else {
                        directUrl
                    }

                return Result.success(
                    YouTube.appendGvsPoToken(
                        url = resolvedDirectUrl,
                        client = client,
                        videoId = videoId,
                        authState = authState,
                    ),
                )
            }

            val cipherString =
                format.signatureCipher ?: format.cipher
                    ?: return Result.failure(ParsingException("Could not find format url"))

            val params = parseQueryString(cipherString)
            val obfuscatedSignature = params["s"] ?: throw ParsingException("Could not parse cipher signature")
            val signatureParam = params["sp"]?.takeIf { it.isNotBlank() } ?: "signature"
            val urlString = params["url"] ?: throw ParsingException("Could not parse cipher url")

            val urlBuilder = URLBuilder(urlString)

            val deobfuscatedSig =
                withJavaScriptPlayerCacheRecovery {
                    YoutubeJavaScriptPlayerManager.deobfuscateSignature(videoId, obfuscatedSignature)
                }

            urlBuilder.parameters[signatureParam] = deobfuscatedSig

            val resolvedUrl = getUrlWithThrottlingParameterDeobfuscated(videoId, urlBuilder.buildString())

            return Result.success(
                YouTube.appendGvsPoToken(
                    url = resolvedUrl,
                    client = client,
                    videoId = videoId,
                    authState = authState,
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            return Result.failure(error)
        }
    }

    private fun getUrlWithThrottlingParameterDeobfuscated(
        videoId: String,
        url: String,
    ): String =
        withJavaScriptPlayerCacheRecovery {
            YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(videoId, url)
        }

    private inline fun <T> withJavaScriptPlayerCacheRecovery(block: () -> T): T {
        try {
            return block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            if (!error.isStalePlayerJavaScriptFailure()) {
                throw error
            }

            runCatching { YoutubeJavaScriptPlayerManager.clearAllCaches() }
            try {
                return block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (retryFailure: Exception) {
                retryFailure.addSuppressed(error)
                throw retryFailure
            }
        }
    }

    private fun Throwable.isStalePlayerJavaScriptFailure(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (
                current is ParsingException &&
                current.message?.contains("deobfuscation function", ignoreCase = true) == true
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }
}
