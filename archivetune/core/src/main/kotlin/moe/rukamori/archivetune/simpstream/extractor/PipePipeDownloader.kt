/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Ported from SimpMusic (https://github.com/maxrave-dev/SimpMusic),
 * core/service/kotlinYtmusicScraper extractor/NewPipeUtils.kt
 * (NewPipeDownloaderImpl — the PipePipe downloader) — GPL-3.0, © maxrave-dev.
 * Logic kept byte-for-byte; only the package name changed.
 */

package moe.rukamori.archivetune.simpstream.extractor

import moe.rukamori.archivetune.innertube.models.YouTubeClient
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import dev.maxrave.pipepipe.extractor.downloader.CancellableCall
import dev.maxrave.pipepipe.extractor.downloader.Downloader
import dev.maxrave.pipepipe.extractor.downloader.Request
import dev.maxrave.pipepipe.extractor.downloader.Response
import dev.maxrave.pipepipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.net.Proxy

class NewPipeDownloaderImpl(
    proxy: Proxy?,
) : Downloader() {
    private val client =
        OkHttpClient
            .Builder()
            .proxy(proxy)
            .build()

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val response = client.newCall(buildOkHttpRequest(request)).execute()

        if (response.code == 429) {
            response.close()
            throw ReCaptchaException("reCaptcha Challenge requested", request.url())
        }

        return response.toNewPipeResponse()
    }

    @Throws(IOException::class, ReCaptchaException::class)
    override fun executeAsync(
        request: Request,
        callback: AsyncCallback?,
    ): CancellableCall {
        val call = client.newCall(buildOkHttpRequest(request))
        val cancellable = CancellableCall(call)
        call.enqueue(
            object : okhttp3.Callback {
                override fun onFailure(
                    call: okhttp3.Call,
                    e: IOException,
                ) {
                    cancellable.setFinished()
                    callback?.onError(e)
                }

                override fun onResponse(
                    call: okhttp3.Call,
                    response: okhttp3.Response,
                ) {
                    try {
                        if (response.code == 429) {
                            response.close()
                            callback?.onError(
                                ReCaptchaException("reCaptcha Challenge requested", request.url()),
                            )
                            return
                        }
                        callback?.onSuccess(response.toNewPipeResponse())
                    } catch (e: Exception) {
                        callback?.onError(e)
                    } finally {
                        cancellable.setFinished()
                    }
                }
            },
        )
        return cancellable
    }

    private fun okhttp3.Response.toNewPipeResponse(): Response {
        val rawBytes = body?.bytes() ?: ByteArray(0)
        return Response(
            code,
            message,
            headers.toMultimap(),
            rawBytes.toString(Charsets.UTF_8),
            rawBytes,
            request.url.toString(),
        )
    }

    private fun buildOkHttpRequest(request: Request): okhttp3.Request {
        val builder =
            okhttp3.Request
                .Builder()
                .method(request.httpMethod(), request.dataToSend()?.toRequestBody())
                .url(request.url())
                .addHeader("User-Agent", YouTubeClient.USER_AGENT_WEB)

        request.headers().forEach { (headerName, headerValueList) ->
            if (headerValueList.size > 1) {
                builder.removeHeader(headerName)
                headerValueList.forEach { headerValue ->
                    builder.addHeader(headerName, headerValue)
                }
            } else if (headerValueList.size == 1) {
                builder.header(headerName, headerValueList[0])
            }
        }
        return builder.build()
    }
}
