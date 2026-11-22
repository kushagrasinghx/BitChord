package com.music.bitchord.data.remote

import com.music.bitchord.data.Http
import com.music.bitchord.data.smb.SmbClient
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.webdav.WebDavAuth
import java.io.InterruptedIOException
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * [EmbeddedArt.Reader] backends, one per remote protocol. Both stay dumb
 * byte fetchers — all format knowledge lives in [EmbeddedArt].
 */
object RemoteArtReader {

    /**
     * Reads [url] over HTTP byte ranges through the shared client, so the
     * server's auth interceptor applies exactly as it does for playback.
     * Servers that ignore Range answer 200 with the whole file; the read is
     * sliced down rather than failing, because a cover at the front is still
     * reachable that way.
     */
    private fun http(
        url: String, client: OkHttpClient, auth: WebDavAuth.Snapshot, cancellation: ArtworkReadCancellation,
    ): EmbeddedArt.Reader = object : EmbeddedArt.Reader {
        override val size: Long = Long.MAX_VALUE

        override fun read(offset: Long, length: Int): ByteArray {
            if (length <= 0 || offset < 0) return ByteArray(0)
            val request = Request.Builder()
                .url(url)
                .header("Range", "bytes=$offset-${offset + length - 1}")
                .tag(WebDavAuth.Snapshot::class.java, auth)
                .get()
                .build()
            val call = client.newCall(request)
            cancellation.closeOnCancel(call::cancel)
            call.execute().use { response ->
                if (response.code != 206 && response.code != 200) {
                    throw java.io.IOException("Range read failed with ${response.code}")
                }
                if (response.code == 200 && offset > 0) {
                    // No range support: only the front is usable.
                    return ByteArray(0)
                }
                val body = response.body ?: return ByteArray(0)
                // Bounded either way: a 200 answer carries the whole file,
                // and only the requested window is ever kept.
                val buffer = okio.Buffer()
                var remaining = length.toLong()
                body.source().use { source ->
                    while (remaining > 0) {
                        val read = source.read(buffer, remaining)
                        if (read == -1L) break
                        remaining -= read
                    }
                }
                return buffer.readByteArray()
            }
        }
    }

    /** This handle belongs to the artwork transfer, not the playback pool. */
    private fun smb(file: com.hierynomus.smbj.share.File): EmbeddedArt.Reader = object : EmbeddedArt.Reader {
        override val size: Long = Long.MAX_VALUE

        override fun read(offset: Long, length: Int): ByteArray {
            if (length <= 0 || offset < 0) return ByteArray(0)
            val buf = ByteArray(length)
            var at = 0
            var pos = offset
            while (at < length) {
                if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Artwork read cancelled")
                val read = file.read(buf, pos, at, length - at)
                if (read <= 0) break
                at += read
                pos += read
            }
            return if (at == length) buf else buf.copyOf(at)
        }
    }

    /** Extracts the embedded picture at [url], or null when there is none. */
    internal suspend fun picture(
        url: String, isSmb: Boolean, speculative: Boolean, auth: AppSettings.RemoteArtworkAuth,
    ): EmbeddedArt.Picture? {
        if (isSmb) {
            return if (speculative) SmbClient.withSpeculativeArtworkFile(url, auth.smb) { EmbeddedArt.extract(smb(it)) }
            else withContext(Dispatchers.IO) {
                SmbClient.withPooledArtworkFile(url, auth.smb) { EmbeddedArt.extract(smb(it)) }
            }
        }
        val client = if (speculative) {
            val host = url.toHttpUrlOrNull()?.host ?: return null
            val addresses = resolveArtworkHost(host).takeIf { it.isNotEmpty() } ?: return null
            Http.client.newBuilder().dns { requestedHost ->
                if (!requestedHost.equals(host, ignoreCase = true)) throw UnknownHostException(requestedHost)
                addresses
            }.build()
        } else Http.client
        return cancellableArtworkRead { cancellation ->
            EmbeddedArt.extract(http(url, client, auth.webDav, cancellation))
        }
    }
}
