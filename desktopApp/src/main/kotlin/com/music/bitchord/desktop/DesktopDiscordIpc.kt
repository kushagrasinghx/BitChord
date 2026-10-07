package com.music.bitchord.desktop

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SocketChannel
import java.util.UUID

/**
 * Discord's local Rich Presence socket.
 *
 * The official way for a desktop application to set a presence, and the one
 * Android has no equivalent of: the Discord client, running on the same
 * machine, listens on a named pipe, and an application connects to it with
 * nothing but its own application id. No account token, no login, no
 * impersonation — the presence is attributed to the application and the client
 * already knows who is signed in.
 *
 * Framing is a 32-bit little-endian opcode, a 32-bit little-endian length, and
 * that many bytes of JSON.
 */
internal class DesktopDiscordIpc private constructor(
    private val channel: SocketChannel?,
    private val pipe: RandomAccessFile?,
) : Closeable {

    companion object {
        private const val OP_HANDSHAKE = 0
        private const val OP_FRAME = 1
        private const val OP_CLOSE = 2

        /**
         * Connects to the first Discord that answers.
         *
         * Discord numbers its sockets 0-9 so several clients — stable, PTB,
         * canary — can run at once, and sandboxed builds put theirs inside
         * their own runtime directory rather than at the top of it.
         */
        fun connect(applicationId: String): DesktopDiscordIpc? {
            for (candidate in candidates()) {
                val ipc = runCatching { open(candidate) }.getOrNull() ?: continue
                val ready = runCatching {
                    ipc.send(OP_HANDSHAKE, """{"v":1,"client_id":"$applicationId"}""")
                    ipc.read() != null
                }.getOrDefault(false)
                if (ready) return ipc
                ipc.close()
            }
            return null
        }

        private fun open(path: String): DesktopDiscordIpc =
            if (DesktopPlatform.isWindows) {
                DesktopDiscordIpc(null, RandomAccessFile(path, "rw"))
            } else {
                val socket = SocketChannel.open(StandardProtocolFamily.UNIX)
                socket.connect(UnixDomainSocketAddress.of(path))
                DesktopDiscordIpc(socket, null)
            }

        private fun candidates(): List<String> {
            if (DesktopPlatform.isWindows) {
                return (0..9).map { """\\.\pipe\discord-ipc-$it""" }
            }
            val roots = listOfNotNull(
                System.getenv("XDG_RUNTIME_DIR"),
                System.getenv("TMPDIR"),
                "/tmp",
            ).distinct()
            // Flatpak and Snap builds keep their socket a level down.
            val nested = listOf("", "app/com.discordapp.Discord", "snap.discord", ".flatpak/dev.vencord.Vesktop/xdg-run")
            return roots.flatMap { root ->
                nested.flatMap { sub ->
                    val dir = if (sub.isEmpty()) root else "$root/$sub"
                    (0..9).map { "$dir/discord-ipc-$it" }
                }
            }.filter { File(it).exists() || DesktopPlatform.isWindows }
        }
    }

    /** The SET_ACTIVITY payload, as its own function so a test can read it. */
    internal fun activityPayload(activity: JsonObject?): String = buildString {
        append("""{"cmd":"SET_ACTIVITY","args":{"pid":""")
        append(ProcessHandle.current().pid())
        append(""","activity":""")
        append(activity?.let { Json.encodeToString(JsonObject.serializer(), it) } ?: "null")
        append("""},"nonce":"${UUID.randomUUID()}"}""")
    }

    /** Publishes an activity, or takes the presence down when given null. */
    fun setActivity(activity: JsonObject?) {
        send(OP_FRAME, activityPayload(activity))
    }

    private fun send(opcode: Int, payload: String) {
        val frame = ByteBuffer.wrap(discordFrame(opcode, payload))
        frame.position(frame.limit())
        frame.flip()
        if (channel != null) {
            while (frame.hasRemaining()) channel.write(frame)
        } else {
            pipe!!.write(frame.array())
        }
    }

    private fun read(): String? {
        val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        if (!fill(header)) return null
        header.flip()
        header.int
        val length = header.int
        if (length <= 0 || length > 1 shl 20) return null
        val body = ByteBuffer.allocate(length)
        if (!fill(body)) return null
        return String(body.array(), Charsets.UTF_8)
    }

    private fun fill(buffer: ByteBuffer): Boolean {
        if (channel != null) {
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) < 0) return false
            }
            return true
        }
        val bytes = ByteArray(buffer.remaining())
        pipe!!.readFully(bytes)
        buffer.put(bytes)
        return true
    }

    override fun close() {
        runCatching { send(OP_CLOSE, "{}") }
        runCatching { channel?.close() }
        runCatching { pipe?.close() }
    }
}

/**
 * One framed message: a little-endian opcode, a little-endian length, and the
 * JSON. Discord drops the connection on a frame it cannot parse, with nothing
 * said about why, so the byte order is worth pinning down in a test.
 */
internal fun discordFrame(opcode: Int, payload: String): ByteArray {
    val body = payload.toByteArray(Charsets.UTF_8)
    return ByteBuffer.allocate(8 + body.size)
        .order(ByteOrder.LITTLE_ENDIAN)
        .putInt(opcode)
        .putInt(body.size)
        .put(body)
        .array()
}
