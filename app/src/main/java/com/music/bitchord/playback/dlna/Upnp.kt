package com.music.bitchord.playback.dlna

import android.util.Log
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.DatagramSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import java.nio.charset.StandardCharsets

/** A UPnP AV MediaRenderer on the local network: what the output drawer lists and [DlnaSpeaker] drives. */
data class DlnaDevice(
    /** The device's UDN, stable across restarts and IP changes. */
    val id: String,
    val name: String,
    val avTransportUrl: String,
    val renderingControlUrl: String?,
)

/**
 * The little of UPnP a renderer needs: finding one (SSDP), reading what it is
 * (its description) and asking it to do things (SOAP).
 *
 * Plain sockets rather than an HTTP client on purpose: renderers only speak
 * plain HTTP on the LAN, and the app keeps `usesCleartextTraffic="false"` for
 * everything else. Every call blocks; callers run them off the main thread.
 */
internal object Upnp {
    private const val TAG = "BitChordDlna"
    private const val SSDP_ADDRESS = "239.255.255.250"
    private const val SSDP_PORT = 1900
    const val RENDERER = "urn:schemas-upnp-org:device:MediaRenderer:1"
    const val AV_TRANSPORT = "urn:schemas-upnp-org:service:AVTransport:1"
    const val RENDERING_CONTROL = "urn:schemas-upnp-org:service:RenderingControl:1"

    // ---- discovery ------------------------------------------------------------

    /** Every renderer that answers an M-SEARCH within [windowMs]. */
    fun search(windowMs: Long = 2_500): List<DlnaDevice> {
        val locations = LinkedHashSet<String>()
        try {
            // Answers come back unicast to this socket's port, so a plain socket
            // does: no multicast group to join and no multicast lock to hold.
            DatagramSocket().use { socket ->
                socket.soTimeout = 300
                val group = InetAddress.getByName(SSDP_ADDRESS)
                val request = (
                    "M-SEARCH * HTTP/1.1\r\n" +
                        "HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n" +
                        "MAN: \"ssdp:discover\"\r\n" +
                        "MX: 2\r\n" +
                        "ST: $RENDERER\r\n\r\n"
                    ).toByteArray(StandardCharsets.US_ASCII)
                val end = System.currentTimeMillis() + windowMs
                var sent = 0
                val buffer = ByteArray(2048)
                while (System.currentTimeMillis() < end) {
                    // UDP gets lost on busy Wi-Fi; ask three times across the window.
                    if (sent < 3 && System.currentTimeMillis() >= end - windowMs + sent * 600L) {
                        socket.send(DatagramPacket(request, request.size, group, SSDP_PORT))
                        sent++
                    }
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    val text = String(packet.data, 0, packet.length, StandardCharsets.UTF_8)
                    header(text, "location")?.let(locations::add)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "SSDP search failed", e)
        }
        return locations.mapNotNull { location ->
            runCatching { describe(location) }
                .onFailure { Log.w(TAG, "could not read $location", it) }
                .getOrNull()
        }.distinctBy { it.id }
    }

    /** Reads a device description and keeps it only if it is a renderer we can drive. */
    fun describe(location: String): DlnaDevice? {
        val (code, xml) = http("GET", location)
        if (code != 200) return null
        if (!xml.contains(RENDERER)) return null
        val base = tag(xml, "URLBase")?.takeIf { it.isNotBlank() } ?: location
        val udn = tag(xml, "UDN") ?: return null
        val name = tag(xml, "friendlyName")?.let(::unescape) ?: "DLNA"
        val avt = controlUrl(xml, AV_TRANSPORT)?.let { resolve(base, it) } ?: return null
        val rcs = controlUrl(xml, RENDERING_CONTROL)?.let { resolve(base, it) }
        return DlnaDevice(id = udn.trim(), name = name.trim(), avTransportUrl = avt, renderingControlUrl = rcs)
    }

    private fun controlUrl(xml: String, serviceType: String): String? {
        val services = Regex("<service>(.*?)</service>", RegexOption.DOT_MATCHES_ALL).findAll(xml)
        val service = services.firstOrNull { tag(it.groupValues[1], "serviceType")?.trim() == serviceType } ?: return null
        return tag(service.groupValues[1], "controlURL")?.trim()
    }

    private fun resolve(base: String, path: String): String = URI(base).resolve(path).toString()

    // ---- SOAP -------------------------------------------------------------------

    class SoapError(val code: Int, message: String) : Exception(message)

    /** Calls [action] and returns its out-arguments, unescaped. Throws [SoapError] on a UPnP fault. */
    fun soap(controlUrl: String, serviceType: String, action: String, args: List<Pair<String, String>>): Map<String, String> {
        val body = buildString {
            append("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
            append("<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" ")
            append("s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body>")
            append("<u:$action xmlns:u=\"$serviceType\">")
            for ((name, value) in args) append("<$name>").append(escape(value)).append("</$name>")
            append("</u:$action></s:Body></s:Envelope>")
        }
        val (code, response) = http(
            "POST",
            controlUrl,
            mapOf(
                "Content-Type" to "text/xml; charset=\"utf-8\"",
                "SOAPACTION" to "\"$serviceType#$action\"",
            ),
            body,
        )
        if (code != 200) {
            val upnpCode = tag(response, "errorCode")?.trim()?.toIntOrNull() ?: code
            throw SoapError(upnpCode, "$action failed: $upnpCode ${tag(response, "errorDescription").orEmpty()}")
        }
        val out = HashMap<String, String>()
        val inner = response.substringAfter("${action}Response", response)
        Regex("<(?:[A-Za-z0-9_]+:)?([A-Za-z0-9_]+)>(.*?)</(?:[A-Za-z0-9_]+:)?\\1>", RegexOption.DOT_MATCHES_ALL)
            .findAll(inner)
            .forEach { out[it.groupValues[1]] = unescape(it.groupValues[2]) }
        return out
    }

    // ---- HTTP over a plain socket ---------------------------------------------

    fun http(method: String, url: String, headers: Map<String, String> = emptyMap(), body: String? = null): Pair<Int, String> {
        val uri = URI(url)
        require(uri.scheme == "http") { "renderers speak plain http: $url" }
        val port = if (uri.port == -1) 80 else uri.port
        val path = (uri.rawPath?.ifEmpty { "/" } ?: "/") + (uri.rawQuery?.let { "?$it" } ?: "")
        val payload = body?.toByteArray(StandardCharsets.UTF_8)
        Socket().use { socket ->
            socket.connect(InetSocketAddress(uri.host, port), 3_000)
            socket.soTimeout = 6_000
            val head = buildString {
                append("$method $path HTTP/1.1\r\n")
                append("Host: ${uri.host}:$port\r\n")
                append("User-Agent: BitChord UPnP/1.0\r\n")
                append("Connection: close\r\n")
                headers.forEach { (k, v) -> append("$k: $v\r\n") }
                if (payload != null) append("Content-Length: ${payload.size}\r\n")
                append("\r\n")
            }
            val out = socket.getOutputStream()
            out.write(head.toByteArray(StandardCharsets.UTF_8))
            payload?.let(out::write)
            out.flush()

            val input = BufferedInputStream(socket.getInputStream())
            val status = readLine(input) ?: return -1 to ""
            val code = status.split(' ').getOrNull(1)?.toIntOrNull() ?: -1
            val responseHeaders = HashMap<String, String>()
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) responseHeaders[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
            }
            val bytes = when {
                responseHeaders["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true -> readChunked(input)
                responseHeaders["content-length"] != null -> readExactly(input, responseHeaders["content-length"]!!.toInt())
                else -> input.readBytes()
            }
            return code to String(bytes, StandardCharsets.UTF_8)
        }
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val c = input.read()
            if (c == -1) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString()
            if (c != '\r'.code) sb.append(c.toChar())
        }
    }

    private fun readExactly(input: InputStream, n: Int): ByteArray {
        val buf = ByteArray(n.coerceIn(0, 4 shl 20))
        var off = 0
        while (off < buf.size) {
            val r = input.read(buf, off, buf.size - off)
            if (r < 0) break
            off += r
        }
        return if (off == buf.size) buf else buf.copyOf(off)
    }

    private fun readChunked(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        while (true) {
            val size = readLine(input)?.substringBefore(';')?.trim()?.toIntOrNull(16) ?: break
            if (size == 0) break
            out.write(readExactly(input, size))
            readLine(input)
        }
        return out.toByteArray()
    }

    // ---- small XML helpers --------------------------------------------------------

    private fun header(message: String, name: String): String? =
        message.lineSequence().firstOrNull { it.substringBefore(':').trim().equals(name, ignoreCase = true) }
            ?.substringAfter(':')?.trim()

    private fun tag(xml: String, name: String): String? =
        Regex("<(?:[A-Za-z0-9_]+:)?$name(?:\\s[^>]*)?>(.*?)</(?:[A-Za-z0-9_]+:)?$name>", RegexOption.DOT_MATCHES_ALL)
            .find(xml)?.groupValues?.get(1)

    fun escape(s: String): String = buildString(s.length + 16) {
        for (c in s) when (c) {
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '&' -> append("&amp;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> append(c)
        }
    }

    private val entity = Regex("&(#[0-9]+|#[xX][0-9a-fA-F]+|lt|gt|amp|quot|apos);")

    /** One level of XML unescaping, numeric character references included. */
    fun unescape(s: String): String = entity.replace(s) { m ->
        when (val e = m.groupValues[1]) {
            "lt" -> "<"
            "gt" -> ">"
            "amp" -> "&"
            "quot" -> "\""
            "apos" -> "'"
            else -> {
                val cp = if (e[1] == 'x' || e[1] == 'X') e.substring(2).toIntOrNull(16) else e.substring(1).toIntOrNull()
                if (cp != null && Character.isValidCodePoint(cp)) String(Character.toChars(cp)) else m.value
            }
        }
    }

    /** "H:MM:SS(.fff)" → ms; 0 when the renderer says NOT_IMPLEMENTED or nothing. */
    fun parseTime(t: String?): Long {
        if (t.isNullOrBlank()) return 0
        return runCatching {
            t.trim().split(':').fold(0.0) { acc, part -> acc * 60 + part.toDouble() }.times(1000).toLong()
        }.getOrDefault(0)
    }

    fun formatTime(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60)
    }
}
