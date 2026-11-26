package com.music.bitchord.data.smb

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.protocol.commons.socket.ProxySocketFactory
import com.music.bitchord.data.remote.ArtworkReadCancellation
import com.music.bitchord.data.remote.cancellableArtworkRead
import com.music.bitchord.data.remote.remoteCoverScope
import com.music.bitchord.data.remote.resolveArtworkHost
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.EnumSet
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Listing (and only listing) over [SmbConnection]: the recursive walk that
 * turns a share into audio rows plus the pictures filed beside them.
 *
 * Server paths are backslash-joined — the wire's own separator. Anything with
 * a forward slash in it is a client-side display path and never goes out.
 */
object SmbClient {

    data class Entry(
        /** Display path with forward slashes, relative to the share. */
        val path: String,
        val isDirectory: Boolean,
        val size: Long,
    )

    data class Listing(val audio: List<Entry>, val images: List<Entry>)

    suspend fun testConnection(
        host: String,
        share: String,
        basePath: String,
        username: String,
        password: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(SmbConfig.isConfigured(host, share)) { "Enter a server and a share" }
            val (server, port) = SmbConfig.splitHostPort(host)
            SmbConnection.withIndependentShare(server, port, share.trim().trim('/'), username.trim(), password) { diskShare ->
                diskShare.list(smbPath(basePath.trim().trim('/')))
            }
        }.map { }
    }

    internal suspend fun listLibrary(server: SmbAuth.ArtworkServer, maxFiles: Int = 10_000): Result<Listing> = withContext(Dispatchers.IO) {
        runCatching {
            require(server.host.isNotBlank() && server.share.isNotBlank()) { "SMB is not configured" }
            val audio = ArrayList<Entry>()
            val images = ArrayList<Entry>()
            SmbConnection.withArtworkShare(server) { diskShare ->
                val stack = ArrayDeque<String>()
                stack.add(server.basePath)
                val visited = HashSet<String>()
                while (stack.isNotEmpty() && audio.size < maxFiles) {
                    val dir = stack.removeFirst()
                    if (!visited.add(dir.lowercase())) continue
                    for (child in listDir(diskShare, dir)) {
                        val childPath = if (dir.isBlank()) child.path else "$dir/${child.path}"
                        if (child.isDirectory) {
                            if (!visited.contains(childPath.lowercase())) stack.add(childPath)
                        } else {
                            val name = child.path.substringAfterLast('\\').substringAfterLast('/')
                            when {
                                SmbConfig.isAudioFile(name) ->
                                    audio.add(Entry(childPath, false, child.size))
                                SmbConfig.isImageFile(name) ->
                                    images.add(Entry(childPath, false, child.size))
                            }
                        }
                    }
                }
            }
            Listing(audio, images)
        }
    }

    /**
     * Opens [path] (display form) for reading. The handle is the caller's to
     * close — one per player open, so seeks never share a cursor.
     */
    fun openFile(path: String): com.hierynomus.smbj.share.File =
        SmbConnection.withShare { openFile(it, path) }

    private fun openFile(share: DiskShare, path: String): com.hierynomus.smbj.share.File = share.openFile(
        smbPath(path), EnumSet.of(AccessMask.GENERIC_READ), null,
        SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null,
    )

    internal fun <T> withPooledArtworkFile(
        streamUrl: String, server: SmbAuth.ArtworkServer, block: (com.hierynomus.smbj.share.File) -> T,
    ): T {
        val path = artworkPath(streamUrl, server)
        val file = SmbConnection.withArtworkShare(server) { openFile(it, path) }
        return try { block(file) } finally { runCatching { file.close() } }
    }

    private fun artworkPath(streamUrl: String, server: SmbAuth.ArtworkServer): String {
        val (host, port) = SmbConfig.splitHostPort(streamUrl)
        if (!SmbConfig.isSmbUrl(streamUrl) || host != server.host || port != server.port) {
            throw SmbConnection.SmbException("Artwork no longer belongs to this server")
        }
        return SmbConfig.relativePath(streamUrl, server.share)
            ?: throw SmbConnection.SmbException("Artwork no longer belongs to this share")
    }

    internal suspend fun <T> withSpeculativeArtworkFile(
        streamUrl: String,
        server: SmbAuth.ArtworkServer,
        block: (com.hierynomus.smbj.share.File) -> T,
    ): T {
        val path = artworkPath(streamUrl, server)
        val address = resolveArtworkHost(server.host).firstOrNull()?.hostAddress
            ?: throw SmbConnection.SmbException("Server address not available for read-ahead")
        return cancellableArtworkRead { cancellation ->
            withArtworkFile(server, path, address, cancellation, block)
        }
    }

    private fun <T> withArtworkFile(
        server: SmbAuth.ArtworkServer, path: String, address: String, cancellation: ArtworkReadCancellation,
        block: (com.hierynomus.smbj.share.File) -> T,
    ): T {
        // SMBJ's direct transport calls this overload; register before connect so
        // aborting artwork never requires shutting down the pooled playback session.
        val sockets = object : ProxySocketFactory() {
            override fun createSocket(host: String, port: Int): Socket = createSocket().also { socket ->
                cancellation.closeOnCancel(socket::close)
                try {
                    socket.connect(InetSocketAddress(host, port), DEFAULT_CONNECT_TIMEOUT)
                } catch (e: Exception) {
                    runCatching { socket.close() }
                    throw e
                }
            }
        }
        // SMBJ resolves the host before it calls the socket factory; use the numeric address.
        return SmbConnection.withIndependentShare(address, server.port, server.share, server.username, server.password, sockets) {
            openFile(it, path).use(block)
        }
    }

    /**
     * Reads a whole small file — a cover, never audio. Capped so a
     * mislabeled video file can't balloon the heap on a list scroll.
     */
    suspend fun readFully(streamUrl: String, speculative: Boolean, maxBytes: Long = 16 * 1024 * 1024): ByteArray {
        val scope = remoteCoverScope(streamUrl)
        val server = if (scope == null) SmbAuth.artworkServer else {
            AppSettings.remoteArtworkAuth(scope)?.smb ?: throw SmbConnection.SmbException("Artwork source changed")
        }
        fun read(file: com.hierynomus.smbj.share.File): ByteArray {
            val length = runCatching { file.length }.getOrNull() ?: -1
            val out = java.io.ByteArrayOutputStream(
                length.takeIf { it > 0 }?.coerceAtMost(maxBytes)?.toInt() ?: 8192,
            )
            val chunk = ByteArray(64 * 1024)
            var offset = 0L
            // Bounded by the known length: some servers answer a read past
            // EOF with bytes no parser accepts instead of an empty one, so
            // the end of the file is taken from the entry, never probed for.
            while (length < 0 || offset < length) {
                val read = file.read(chunk, offset, 0, chunk.size)
                if (read <= 0) break
                offset += read
                if (offset > maxBytes) throw SmbConnection.SmbException("File too large for a cover")
                out.write(chunk, 0, read)
            }
            return out.toByteArray()
        }
        return if (speculative) withSpeculativeArtworkFile(streamUrl, server, ::read)
        else withContext(Dispatchers.IO) { withPooledArtworkFile(streamUrl, server, ::read) }
    }

    /** Display path back to the wire form. */
    fun smbPath(displayPath: String): String =
        displayPath.replace('/', '\\').trim('\\').ifBlank { "" }

    private fun listDir(diskShare: DiskShare, dir: String): List<Entry> {
        return diskShare.list(smbPath(dir)).mapNotNull { info ->
            val name = info.fileName
            if (name == "." || name == "..") return@mapNotNull null
            val isDir = (info.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L
            Entry(name, isDir, info.endOfFile)
        }
    }
}
