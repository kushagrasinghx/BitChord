package com.music.bitchord.data.connect

import kotlinx.serialization.Serializable

/**
 * Shared wire contract for BitChord LAN Connect (Spotify Connect-style remote playback).
 *
 * The desktop app runs a lightweight HTTP + WebSocket server on the local network and advertises
 * itself over mDNS (`_bitchord-connect._tcp`). Android discovers it via NsdManager (or manual
 * host/IP for VPNs such as Tailscale), pairs once with a 6-digit PIN to receive a bearer token,
 * and then controls playback over a single persistent WebSocket.
 *
 * Audio is never streamed from phone to PC: only metadata, queue order, transport commands, and
 * playback state travel over the socket. The PC resolves and decodes the audio locally.
 */
object ConnectProtocol {
    const val VERSION = 1
    const val DEFAULT_PORT = 17420
    const val MDNS_SERVICE_TYPE = "_bitchord-connect._tcp"
    const val MDNS_SERVICE_TYPE_LOCAL = "_bitchord-connect._tcp.local."
    const val LINK_SCHEME = "bitchord-connect"
    const val MAX_QUEUE_ITEMS = 100

    // Server -> Client frame types
    const val FRAME_WELCOME = "welcome"
    const val FRAME_STATE = "state"
    const val FRAME_QUEUE = "queue"
    const val FRAME_PONG = "pong"
    const val FRAME_ERROR = "error"

    // Client -> Server frame types
    const val FRAME_PING = "ping"
    const val FRAME_SYNC = "sync"
    const val FRAME_CONTROL = "control"

    // Control actions (Client -> Server)
    const val ACTION_PLAY = "play"
    const val ACTION_PAUSE = "pause"
    const val ACTION_TOGGLE_PLAY_PAUSE = "togglePlayPause"
    const val ACTION_SEEK = "seek"
    const val ACTION_NEXT = "next"
    const val ACTION_PREVIOUS = "previous"
    const val ACTION_SET_VOLUME = "setVolume"
    const val ACTION_SET_REPEAT = "setRepeat"
    const val ACTION_SET_SHUFFLE = "setShuffle"
    const val ACTION_LOAD_QUEUE = "loadQueue"
    const val ACTION_UPDATE_UPCOMING = "updateUpcoming"
    const val ACTION_JUMP_TO = "jumpTo"
    const val ACTION_STOP = "stop"

    // Repeat mode values
    const val REPEAT_OFF = "OFF"
    const val REPEAT_ONE = "ONE"
    const val REPEAT_ALL = "ALL"
}

@Serializable
data class ConnectTrack(
    val videoId: String,
    val title: String = "",
    val artist: String = "",
    val albumName: String? = null,
    val thumbnailUrl: String? = null,
    val durationMs: Long? = null,
    val isVideo: Boolean = false,
    val fromAutoplay: Boolean = false,
)

@Serializable
data class ConnectHostInfo(
    val protocolVersion: Int = ConnectProtocol.VERSION,
    val deviceId: String,
    val deviceName: String,
    val platform: String = "pc",
    val appVersion: String = "",
    val port: Int = ConnectProtocol.DEFAULT_PORT,
    val pairedClientsCount: Int = 0,
)

@Serializable
data class ConnectPairRequest(
    val pin: String,
    val clientId: String,
    val clientName: String,
    val platform: String = "android",
)

@Serializable
data class ConnectPairResponse(
    val token: String,
    val deviceId: String,
    val deviceName: String,
    val protocolVersion: Int = ConnectProtocol.VERSION,
)

@Serializable
data class ConnectApiError(
    val error: String,
    val message: String = "",
)

@Serializable
data class ConnectPlaybackState(
    val seq: Long = 0L,
    val queueSeq: Long = 0L,
    val queueIndex: Int = -1,
    val track: ConnectTrack? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0L,
    /** Server wall clock (ms) at which [positionMs] was sampled. */
    val anchorMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 1f,
    val repeatMode: String = ConnectProtocol.REPEAT_OFF,
    val shuffle: Boolean = false,
) {
    /** Extrapolates the current playhead on the server clock without requiring continuous polling. */
    fun positionAt(serverNowMs: Long): Long {
        if (!isPlaying || isLoading || buffering) return positionMs.coerceAtLeast(0L)
        val elapsed = (serverNowMs - anchorMs).coerceAtLeast(0L)
        val estimated = positionMs.coerceAtLeast(0L) + elapsed
        return if (durationMs > 0L) estimated.coerceAtMost(durationMs) else estimated
    }
}

@Serializable
data class ConnectQueueState(
    val seq: Long = 0L,
    val index: Int = -1,
    val items: List<ConnectTrack> = emptyList(),
)

@Serializable
data class ConnectServerFrame(
    val type: String,
    val deviceId: String? = null,
    val deviceName: String? = null,
    val serverMs: Long = 0L,
    val clientMs: Long? = null,
    val state: ConnectPlaybackState? = null,
    val queue: ConnectQueueState? = null,
    val error: String? = null,
    val message: String? = null,
)

@Serializable
data class ConnectClientFrame(
    val type: String,
    val clientMs: Long? = null,
    val action: String? = null,
    val positionMs: Long? = null,
    val volume: Float? = null,
    val repeatMode: String? = null,
    val shuffle: Boolean? = null,
    val tracks: List<ConnectTrack>? = null,
    val index: Int? = null,
    val videoId: String? = null,
    val play: Boolean? = null,
) {
    companion object {
        fun ping(clientMs: Long) = ConnectClientFrame(type = ConnectProtocol.FRAME_PING, clientMs = clientMs)

        fun sync() = ConnectClientFrame(type = ConnectProtocol.FRAME_SYNC)

        fun control(
            action: String,
            positionMs: Long? = null,
            volume: Float? = null,
            repeatMode: String? = null,
            shuffle: Boolean? = null,
            tracks: List<ConnectTrack>? = null,
            index: Int? = null,
            videoId: String? = null,
            play: Boolean? = null,
        ) = ConnectClientFrame(
            type = ConnectProtocol.FRAME_CONTROL,
            action = action,
            positionMs = positionMs,
            volume = volume,
            repeatMode = repeatMode,
            shuffle = shuffle,
            tracks = tracks,
            index = index,
            videoId = videoId,
            play = play,
        )
    }
}

/**
 * Compact pairing link encoded into the desktop QR code and copyable into Android's manual setup:
 * `bitchord-connect://pair?host=192.168.1.50&port=17420&pin=482910&id=...&name=My+PC`
 */
data class ConnectPairLink(
    val host: String,
    val port: Int = ConnectProtocol.DEFAULT_PORT,
    val pin: String? = null,
    val deviceId: String? = null,
    val deviceName: String? = null,
) {
    fun toUriString(): String = buildString {
        append(ConnectProtocol.LINK_SCHEME)
        append("://pair?host=")
        append(percentEncode(host))
        append("&port=")
        append(port)
        pin?.takeIf { it.isNotBlank() }?.let {
            append("&pin=")
            append(percentEncode(it))
        }
        deviceId?.takeIf { it.isNotBlank() }?.let {
            append("&id=")
            append(percentEncode(it))
        }
        deviceName?.takeIf { it.isNotBlank() }?.let {
            append("&name=")
            append(percentEncode(it))
        }
    }

    companion object {
        fun parse(raw: String): ConnectPairLink? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            if (trimmed.startsWith("${ConnectProtocol.LINK_SCHEME}://", ignoreCase = true)) {
                val query = trimmed.substringAfter('?', "")
                if (query.isEmpty()) return null
                val params = query.split('&').mapNotNull { part ->
                    val eq = part.indexOf('=')
                    if (eq <= 0) return@mapNotNull null
                    val key = percentDecode(part.substring(0, eq))
                    val value = percentDecode(part.substring(eq + 1))
                    key to value
                }.toMap()
                val host = params["host"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
                val port = params["port"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: ConnectProtocol.DEFAULT_PORT
                val pin = params["pin"]?.filter(Char::isDigit)?.takeIf { it.isNotEmpty() }
                val deviceId = params["id"]?.trim()?.takeIf { it.isNotEmpty() }
                val deviceName = params["name"]?.trim()?.takeIf { it.isNotEmpty() }
                return ConnectPairLink(host = host, port = port, pin = pin, deviceId = deviceId, deviceName = deviceName)
            }
            // Fallback: plain `host:port` or `http://host:port` or bare IP/hostname.
            val stripped = trimmed
                .removePrefix("http://")
                .removePrefix("https://")
                .removePrefix("ws://")
                .removePrefix("wss://")
                .substringBefore('/')
                .trim()
            if (stripped.isEmpty()) return null
            val (host, port) = if (stripped.startsWith("[")) {
                val endBracket = stripped.indexOf(']')
                if (endBracket <= 1) return null
                val h = stripped.substring(1, endBracket)
                val rest = stripped.substring(endBracket + 1)
                val p = if (rest.startsWith(":")) rest.substring(1).toIntOrNull() else ConnectProtocol.DEFAULT_PORT
                h to (p ?: return null)
            } else {
                val colon = stripped.lastIndexOf(':')
                if (colon > 0 && stripped.indexOf(':') == colon) {
                    val h = stripped.substring(0, colon).trim()
                    val p = stripped.substring(colon + 1).trim().toIntOrNull() ?: return null
                    h to p
                } else {
                    stripped to ConnectProtocol.DEFAULT_PORT
                }
            }
            if (host.isEmpty() || port !in 1..65535) return null
            return ConnectPairLink(host = host, port = port)
        }

        private fun percentEncode(value: String): String {
            val bytes = value.encodeToByteArray()
            val out = StringBuilder(bytes.size)
            for (b in bytes) {
                val c = b.toInt() and 0xFF
                val unreserved = (c in 'A'.code..'Z'.code) ||
                    (c in 'a'.code..'z'.code) ||
                    (c in '0'.code..'9'.code) ||
                    c == '-'.code || c == '_'.code || c == '.'.code || c == '~'.code
                if (unreserved) {
                    out.append(c.toChar())
                } else {
                    out.append('%')
                    out.append(HEX[c ushr 4])
                    out.append(HEX[c and 0x0F])
                }
            }
            return out.toString()
        }

        private fun percentDecode(value: String): String {
            val out = ByteArray(value.length)
            var outLen = 0
            var i = 0
            while (i < value.length) {
                val ch = value[i]
                if (ch == '%' && i + 2 < value.length) {
                    val hi = hexDigit(value[i + 1])
                    val lo = hexDigit(value[i + 2])
                    if (hi >= 0 && lo >= 0) {
                        out[outLen++] = ((hi shl 4) or lo).toByte()
                        i += 3
                        continue
                    }
                }
                if (ch == '+') {
                    out[outLen++] = ' '.code.toByte()
                    i++
                } else {
                    val encoded = ch.toString().encodeToByteArray()
                    for (b in encoded) {
                        if (outLen < out.size) out[outLen++] = b
                    }
                    i++
                }
            }
            return out.decodeToString(0, outLen)
        }

        private fun hexDigit(c: Char): Int = when (c) {
            in '0'..'9' -> c - '0'
            in 'a'..'f' -> c - 'a' + 10
            in 'A'..'F' -> c - 'A' + 10
            else -> -1
        }

        private const val HEX = "0123456789ABCDEF"
    }
}
