package com.music.bitchord.data.smb

import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

/**
 * The one open session to the configured server. Single-server, like the rest
 * of this app's remote libraries: [SmbAuth] publishes the parameters and
 * drops this pool on every edit, so a changed credential can never keep
 * reading on the old session.
 *
 * All share access funnels through [withShare], which reconnects once when
 * the wire died mid-flight. Only transport failures ([IOException]) retry —
 * a status answer from the server (bad password, no such share) is final and
 * fails fast instead of authenticating twice against a lockout counter.
 */
object SmbConnection {

    private var client: SMBClient? = null
    private var connection: Connection? = null
    private var share: DiskShare? = null
    private var openedFor: SmbAuth.ArtworkServer? = null

    @Synchronized
    fun invalidate() {
        runCatching { share?.close() }
        runCatching { connection?.close() }
        runCatching { client?.close() }
        share = null
        connection = null
        client = null
        openedFor = null
    }

    fun <T> withShare(block: (DiskShare) -> T): T {
        return try {
            block(open())
        } catch (e: IOException) {
            invalidate()
            block(open())
        }
    }

    internal fun <T> withArtworkShare(server: SmbAuth.ArtworkServer, block: (DiskShare) -> T): T =
        try {
            block(open(server))
        } catch (e: IOException) {
            invalidateArtworkShare(server)
            block(open(server))
        }

    @Synchronized
    private fun invalidateArtworkShare(server: SmbAuth.ArtworkServer) {
        if (openedFor === server) invalidate()
    }

    /** A test login or artwork transfer must not replace or close the playback pool. */
    internal fun <T> withIndependentShare(
        host: String, port: Int, shareName: String, username: String, password: String,
        socketFactory: SocketFactory? = null,
        block: (DiskShare) -> T,
    ): T = SMBClient(configBuilder().apply {
        if (socketFactory != null) withSocketFactory(socketFactory)
    }.build()).use { independent ->
        val disk = independent.connect(host, port).authenticate(authFor(username, password)).connectShare(shareName) as? DiskShare
            ?: throw SmbException("\"$shareName\" is not a file share")
        block(disk)
    }

    private fun configBuilder() = SmbConfig.builder()
        .withTimeout(20, TimeUnit.SECONDS)
        .withReadTimeout(30, TimeUnit.SECONDS)
        .withWriteTimeout(30, TimeUnit.SECONDS)
        .withTransactTimeout(30, TimeUnit.SECONDS)

    @Synchronized
    private fun open(artworkServer: SmbAuth.ArtworkServer? = null): DiskShare {
        val server = SmbAuth.artworkServer
        if (artworkServer != null && server !== artworkServer) throw SmbException("Artwork server changed")
        val live = share
        if (live != null && connection?.isConnected == true && (artworkServer == null || openedFor === artworkServer)) return live
        invalidate()
        val host = server.host
        val shareName = server.share
        require(host.isNotBlank() && shareName.isNotBlank()) { "SMB is not configured" }
        val newClient = SMBClient(configBuilder().build())
        try {
            val newConnection = newClient.connect(host, server.port)
            val session: Session = newConnection.authenticate(authFor(server.username, server.password))
            val diskShare = session.connectShare(shareName) as? DiskShare
                ?: throw SmbException("\"$shareName\" is not a file share")
            client = newClient
            connection = newConnection
            share = diskShare
            openedFor = server
            return diskShare
        } catch (e: Throwable) {
            runCatching { newClient.close() }
            throw e
        }
    }

    /**
     * Never anonymous(): smbj 0.15 derives SMB3 keys from a session key an
     * anonymous login has none of, and crashes on servers that do not flag
     * the session as null (Samba) - hierynomus/smbj#872. Guest sends a real
     * NTLMv2 exchange and lands on the guest path smbj handles.
     */
    internal fun authFor(username: String, password: String): AuthenticationContext =
        if (username.isBlank() && password.isBlank()) {
            AuthenticationContext.guest()
        } else {
            AuthenticationContext(username, password.toCharArray(), "")
        }

    class SmbException(message: String) : IOException(message)
}
