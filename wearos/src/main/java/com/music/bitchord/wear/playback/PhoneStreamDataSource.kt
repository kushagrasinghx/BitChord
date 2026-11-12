package com.music.bitchord.wear.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import com.music.bitchord.wear.WearProtocol as P

/**
 * Reads a track through the phone.
 *
 * Each [open] is one Wearable channel: the phone resolves the URI with its own
 * player's chain — YouTube, addons, downloads, SMB, its cache — and sends the
 * bytes back. So the watch needs no network, no account and no resolver of its
 * own, and plays whatever the phone could. A seek is a new channel at the new
 * offset, the way an HTTP source would make a new range request.
 */
@UnstableApi
class PhoneStreamDataSource(context: Context) : BaseDataSource(true) {

    private val app = context.applicationContext
    private val channels = Wearable.getChannelClient(app)
    private var channel: ChannelClient.Channel? = null
    private var input: InputStream? = null
    private var uri: Uri? = null
    private var remaining = C.LENGTH_UNSET.toLong()

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val node = phoneNode(app)
            ?: throw DataSourceException(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        try {
            val opened = Tasks.await(channels.openChannel(node, P.PATH_STREAM), OPEN_TIMEOUT_S, TimeUnit.SECONDS)
            channel = opened
            DataOutputStream(Tasks.await(channels.getOutputStream(opened), OPEN_TIMEOUT_S, TimeUnit.SECONDS)).apply {
                writeUTF(dataSpec.uri.toString())
                writeLong(dataSpec.position)
                writeLong(dataSpec.length)
                flush()
            }
            val stream = DataInputStream(
                BufferedInputStream(Tasks.await(channels.getInputStream(opened), OPEN_TIMEOUT_S, TimeUnit.SECONDS), BUFFER),
            )
            // The phone may spend seconds resolving before it answers.
            if (stream.readUnsignedByte() != P.STREAM_OK) {
                throw DataSourceException(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)
            }
            val length = stream.readLong()
            input = stream
            remaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else length
        } catch (e: DataSourceException) {
            close()
            throw e
        } catch (e: Exception) {
            close()
            phoneNode = null
            throw DataSourceException(e, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        }
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val stream = input ?: return C.RESULT_END_OF_INPUT
        val want = if (remaining == C.LENGTH_UNSET.toLong()) length else minOf(length.toLong(), remaining).toInt()
        val read = try {
            stream.read(buffer, offset, want)
        } catch (e: Exception) {
            throw DataSourceException(e, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        }
        if (read == -1) return C.RESULT_END_OF_INPUT
        if (remaining != C.LENGTH_UNSET.toLong()) remaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        runCatching { input?.close() }
        input = null
        channel?.let { runCatching { channels.close(it) } }
        if (channel != null) transferEnded()
        channel = null
        uri = null
    }

    class Factory(private val context: Context) : DataSource.Factory {
        override fun createDataSource(): DataSource = PhoneStreamDataSource(context)
    }

    companion object {
        private const val OPEN_TIMEOUT_S = 25L
        private const val BUFFER = 64 * 1024

        @Volatile private var phoneNode: String? = null

        /** The phone with this build of BitChord on it. Blocking: loader threads only. */
        fun phoneNode(context: Context): String? {
            phoneNode?.let { return it }
            val info = runCatching {
                Tasks.await(
                    Wearable.getCapabilityClient(context)
                        .getCapability(P.CAPABILITY_PHONE, CapabilityClient.FILTER_REACHABLE),
                    10, TimeUnit.SECONDS,
                )
            }.getOrNull() ?: return null
            return (info.nodes.firstOrNull { it.isNearby } ?: info.nodes.firstOrNull())?.id?.also { phoneNode = it }
        }
    }
}
