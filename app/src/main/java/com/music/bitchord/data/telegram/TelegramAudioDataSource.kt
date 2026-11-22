package com.music.bitchord.data.telegram

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import kotlinx.coroutines.runBlocking
import org.drinkless.tdlib.TdApi
import java.io.EOFException
import java.io.IOException
import java.io.RandomAccessFile
import java.util.Collections

/** Media3 reader over bounded TDLib downloads. Each DataSpec (including a seek) starts at its offset. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class TelegramAudioDataSource : BaseDataSource(true) {
    private var reader: Reader? = null
    private var currentUri: Uri? = null
    private var transferStarted = false

    override fun open(dataSpec: DataSpec): Long {
        if (reader != null) throw IOException("Telegram data source is already open")
        transferInitializing(dataSpec)
        try {
            val id = dataSpec.uri.getQueryParameter("id") ?: throw IOException("Missing Telegram track id")
            val track = TelegramClientManager.indexDatabase.track(id) ?: throw IOException("Telegram track is no longer indexed")
            val total = track.size.takeIf { it > 0 }
                ?: runBlocking { TelegramClientManager.getFile(track.fileId).size }
            if (total < 0 || dataSpec.position > total) throw EOFException("Seek position is outside the audio file")
            if (total == 0L) throw IOException("Telegram has not reported this audio file's size yet")
            val requested = if (dataSpec.length == C.LENGTH_UNSET.toLong()) total - dataSpec.position else minOf(dataSpec.length, total - dataSpec.position)
            reader = Reader(track.fileId, dataSpec.position, requested, total)
            currentUri = dataSpec.uri
            transferStarted(dataSpec)
            transferStarted = true
            return requested
        } catch (error: Throwable) {
            close()
            if (error is IOException) throw error
            throw IOException("Could not open Telegram audio", error)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val active = reader ?: throw IOException("Telegram data source is not open")
        val bytes = try {
            runBlocking { TelegramClientManager.withFileReadLock(active.fileId) { active.read(buffer, offset, length) } }
        } catch (error: Throwable) {
            if (error is IOException) throw error
            throw IOException("Telegram file read failed", error)
        }
        if (bytes > 0) bytesTransferred(bytes)
        return bytes
    }

    override fun getUri(): Uri? = currentUri
    override fun getResponseHeaders(): Map<String, List<String>> = reader?.let {
        mapOf("Content-Length" to listOf(it.requested.toString()))
    } ?: emptyMap()

    override fun close() {
        reader?.close()
        reader = null
        currentUri = null
        if (transferStarted) {
            transferStarted = false
            transferEnded()
        }
    }

    private class Reader(
        val fileId: Int,
        start: Long,
        val requested: Long,
        private val total: Long,
    ) {
        private val end = start + requested
        private var position = start
        private var file: TdApiFileHandle? = null
        private var closed = false

        suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (closed) throw IOException("Telegram reader is closed")
            if (position >= end) return C.RESULT_END_OF_INPUT
            var attempts = 0
            while (attempts++ < 5) {
                val state = TelegramClientManager.getFile(fileId)
                val local = state.local
                val full = local.isDownloadingCompleted && local.path.isNotBlank()
                val isCovered = full || (
                    local.path.isNotBlank() && position >= local.downloadOffset &&
                        position - local.downloadOffset < local.downloadedPrefixSize
                    )
                if (!isCovered) {
                    val limit = minOf(CHUNK_BYTES, end - position)
                    val next = try {
                        TelegramClientManager.downloadWindow(fileId, position, limit)
                        TelegramClientManager.awaitFile(fileId) { candidate ->
                            val part = candidate.local
                            part.isDownloadingCompleted || (
                                part.path.isNotBlank() && part.downloadOffset <= position &&
                                    position < part.downloadOffset + part.downloadedPrefixSize
                                )
                        }
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (rangeError: Exception) {
                        // Ranged access is a playback optimization. If TDLib
                        // cannot provide that window, ask it for the complete
                        // file and let Media3 read the private local copy.
                        val full = TelegramClientManager.downloadCompleteFile(fileId)
                        if (full.local.isDownloadingCompleted && full.local.path.isNotBlank()) full
                        else TelegramClientManager.awaitFile(fileId) { it.local.isDownloadingCompleted && it.local.path.isNotBlank() }
                    }
                    if (!next.local.isDownloadingCompleted && next.local.downloadedPrefixSize <= 0) continue
                    file?.close()
                    file = TdApiFileHandle(next.local.path)
                } else {
                    if (file?.path != local.path) {
                        file?.close()
                        file = TdApiFileHandle(local.path)
                    }
                }
                val latest = TelegramClientManager.getFile(fileId).local
                val availableEnd = if (latest.isDownloadingCompleted) total else latest.downloadOffset + latest.downloadedPrefixSize
                val available = minOf(length.toLong(), end - position, availableEnd - position)
                if (available <= 0) continue
                val reader = file ?: throw IOException("TDLib did not provide a local audio path")
                reader.seek(if (latest.isDownloadingCompleted) position else position - latest.downloadOffset)
                val count = reader.read(buffer, offset, available.toInt())
                if (count <= 0) continue
                position += count
                return count
            }
            throw IOException("Telegram did not provide the requested audio range")
        }

        fun close() {
            closed = true
            file?.close()
            file = null
        }

        companion object { private const val CHUNK_BYTES = 512L * 1024L }
    }

    private class TdApiFileHandle(path: String) {
        private val file = RandomAccessFile(path, "r")
        val path: String = path
        fun seek(position: Long) = file.seek(position)
        fun read(target: ByteArray, offset: Int, length: Int) = file.read(target, offset, length)
        fun close() = file.close()
    }

    companion object { private const val UNUSED = 0 }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class TelegramDataSourceFactory(private val upstream: DataSource.Factory) : DataSource.Factory {
    override fun createDataSource(): DataSource = RoutingDataSource(upstream.createDataSource())

    private class RoutingDataSource(private val upstream: DataSource) : DataSource {
        private val telegram = TelegramAudioDataSource()
        private var active: DataSource? = null

        override fun addTransferListener(transferListener: TransferListener) {
            upstream.addTransferListener(transferListener)
            telegram.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            if (active != null) throw IOException("Data source already open")
            active = if (dataSpec.uri.scheme == "telegram") telegram else upstream
            return active!!.open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            active?.read(buffer, offset, length) ?: throw IOException("Data source is not open")

        override fun getUri(): Uri? = active?.uri
        override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: Collections.emptyMap()

        override fun close() {
            try { active?.close() } finally { active = null }
        }
    }
}
