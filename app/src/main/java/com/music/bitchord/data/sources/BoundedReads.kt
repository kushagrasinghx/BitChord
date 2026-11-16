package com.music.bitchord.data.sources

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Reads at most [maxBytes] from an untrusted response. Short bodies are
 * normal; only exceeding the cap is an error.
 */
internal fun readBounded(input: InputStream, maxBytes: Long): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    var total = 0L
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        total += read
        if (total > maxBytes) throw IOException("Response exceeded the $maxBytes byte limit")
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
