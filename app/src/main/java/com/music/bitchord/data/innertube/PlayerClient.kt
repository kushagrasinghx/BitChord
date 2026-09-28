package com.music.bitchord.data.innertube

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.util.Locale

/**
 * The identity a googlevideo URL says minted it, as far as its media fetch
 * has to repeat it.
 *
 * googlevideo bakes the client into the URL as `c=`/`cver=` and compares it
 * against the headers of the request that comes back for the bytes, so the
 * fetch has to be dressed as that client. URLs InnerTubeX mints carry their
 * own headers (see [InnerTubeXResolver.headersFor]); this covers the rest,
 * chiefly the NewPipe failsafe's, which picks a client of its own choosing.
 */
data class PlayerClient(
    val clientName: String,
    val clientVersion: String,
    val userAgent: String,
    /**
     * The host this client runs on, for browser-shaped clients only. Native app
     * clients send none, and sending one anyway is as wrong as omitting it from
     * a web client.
     */
    val origin: String? = null,
) {
    val referer: String? get() = origin?.let { "$it/" }

    /**
     * Headers the *media* request must carry for a URL this client minted.
     *
     * googlevideo treats a mismatch between the request that produced the URL
     * and the one fetching it as reason enough to throttle the response to a
     * crawl or refuse it with 403.
     */
    fun mediaHeaders(): Map<String, String> = buildMap {
        put("User-Agent", userAgent)
        origin?.let { put("Origin", it) }
        referer?.let { put("Referer", it) }
    }

    companion object {
        /**
         * The client a stream URL says minted it, so the media fetch can be
         * dressed as that client. Answered from the imported service file's
         * client table — see
         * [com.music.bitchord.data.service.ServiceConfig] — which is why this
         * file holds no client versions or user agents of its own.
         *
         * The one exception names no version either: NewPipe's extraction
         * mints its URLs itself, so that client is rebuilt below with the
         * agent read live from NewPipe rather than pinned here.
         */
        fun forStreamUrl(url: String): PlayerClient {
            val parsed = url.toHttpUrlOrNull()
            val name = parsed?.queryParameter("c")?.uppercase(Locale.ROOT)
            if (name?.startsWith("VISIONOS") == true) {
                return visionOs(parsed.queryParameter("cver"))
            }
            return com.music.bitchord.data.service.ServiceConfig.clientForUrl(url)
        }

        /** NewPipe's extraction mints with this; its agent is read from NewPipe so the two can't drift. */
        private fun visionOs(version: String?) = PlayerClient(
            clientName = "VISIONOS",
            clientVersion = version ?: "1.02",
            userAgent = YoutubeParsingHelper.getVisionOsUserAgent(NewPipe.getPreferredLocalization()),
        )

        /** Largest single range googlevideo reliably serves for [url]'s client; mirrors InnerTubeX's `mediaRangeChunkSize`. */
        fun rangeBytesFor(url: String): Long {
            val parsed = url.toHttpUrlOrNull() ?: return Long.MAX_VALUE
            if (!parsed.host.endsWith("googlevideo.com")) return Long.MAX_VALUE
            val name = parsed.queryParameter("c")?.uppercase(Locale.ROOT)
            return if (name == "ANDROID_VR" || name?.startsWith("TVHTML5_SIMPLY") == true) {
                NARROW_RANGE_BYTES
            } else {
                RANGE_BYTES
            }
        }

        private const val RANGE_BYTES = 1024L * 1024
        private const val NARROW_RANGE_BYTES = 512L * 1024
    }
}
