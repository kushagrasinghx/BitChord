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

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readAvailable

/** Reads a response body while enforcing a byte limit before and during streaming. */
public suspend fun HttpResponse.bodyAsTextLimited(maxBytes: Int): String {
    require(maxBytes > 0) { "Maximum response size must be positive" }
    val channel = bodyAsChannel()
    val declaredLength = contentLength()
    if (declaredLength != null && declaredLength > maxBytes) {
        channel.cancel(null)
        throw IllegalStateException("Response exceeded the $maxBytes byte limit")
    }

    val readBuffer = ByteArray(minOf(DEFAULT_READ_BUFFER_SIZE, maxBytes))
    var bytes = ByteArray(minOf(declaredLength?.toInt() ?: DEFAULT_READ_BUFFER_SIZE, maxBytes))
    var size = 0
    try {
        while (true) {
            val count = channel.readAvailable(readBuffer, 0, readBuffer.size)
            if (count == -1) break
            if (count == 0) continue
            if (size > maxBytes - count) {
                throw IllegalStateException("Response exceeded the $maxBytes byte limit")
            }
            if (size + count > bytes.size) {
                bytes = bytes.copyOf(minOf(maxBytes, maxOf(size + count, bytes.size * 2)))
            }
            readBuffer.copyInto(bytes, destinationOffset = size, endIndex = count)
            size += count
        }
    } catch (error: Throwable) {
        channel.cancel(error)
        throw error
    }
    return bytes.decodeToString(endIndex = size)
}

private const val DEFAULT_READ_BUFFER_SIZE = 8 * 1024
