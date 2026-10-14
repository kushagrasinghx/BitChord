package com.music.bitchord.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.buffer
import okio.source
import java.io.File
import java.io.FileNotFoundException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance, throttled extractor for embedded cover art from local audio files for Coil.
 *
 * Prevents freezing and UI jank during fling scrolls by:
 * 1. Maintaining a fast thread-safe negative cache for audio files without embedded art.
 * 2. Caching extracted JPEG bitmaps to disk so MediaMetadataRetriever only runs once per track.
 * 3. Limiting concurrent native MediaMetadataRetriever operations via a Semaphore to avoid Binder starvation.
 */
class LocalAudioCoverFetcher(
    private val context: Context,
    private val uriStr: String,
) : Fetcher {

    private val diskCacheDir by lazy {
        File(context.cacheDir, CACHE_SUBDIR).apply { mkdirs() }
    }

    private fun getCacheFile(rawUri: String): File {
        val hash = (rawUri.hashCode().toLong() and 0xFFFFFFFFL).toString(16)
        return File(diskCacheDir, "$hash.jpg")
    }

    override suspend fun fetch(): FetchResult {
        val rawUri = uriStr.removePrefix("audio:")
        if (negativeCache.contains(rawUri)) {
            throw FileNotFoundException("Negative cache hit for $rawUri")
        }

        val cachedFile = getCacheFile(rawUri)
        if (cachedFile.exists() && cachedFile.length() > 0) {
            return SourceFetchResult(
                source = ImageSource(cachedFile.inputStream().source().buffer(), FileSystem.SYSTEM),
                mimeType = "image/jpeg",
                dataSource = DataSource.DISK,
            )
        }

        val pic: ByteArray? = extractSemaphore.withPermit {
            // Re-check after acquiring semaphore in case another coroutine extracted it
            if (cachedFile.exists() && cachedFile.length() > 0) {
                return@withPermit null
            }
            withContext(Dispatchers.IO) {
                val uri = Uri.parse(rawUri)
                val retriever = MediaMetadataRetriever()
                try {
                    val directPath = if (uri.scheme == "file") {
                        uri.path
                    } else if (uri.scheme.isNullOrBlank() || rawUri.startsWith("/")) {
                        rawUri
                    } else {
                        null
                    }

                    if (directPath != null) {
                        retriever.setDataSource(directPath)
                    } else {
                        retriever.setDataSource(context, uri)
                    }
                    retriever.embeddedPicture
                } catch (_: Throwable) {
                    null
                } finally {
                    try { retriever.release() } catch (_: Throwable) {}
                }
            }
        }

        if (cachedFile.exists() && cachedFile.length() > 0) {
            return SourceFetchResult(
                source = ImageSource(cachedFile.inputStream().source().buffer(), FileSystem.SYSTEM),
                mimeType = "image/jpeg",
                dataSource = DataSource.DISK,
            )
        }

        if (pic == null) {
            negativeCache.add(rawUri)
            throw FileNotFoundException("No embedded artwork found for $rawUri")
        }

        // Persist to disk cache asynchronously so future scroll passes load in sub-millisecond time
        withContext(Dispatchers.IO) {
            runCatching {
                val tmp = File(diskCacheDir, "${cachedFile.name}.tmp")
                tmp.writeBytes(pic)
                tmp.renameTo(cachedFile)
            }
        }

        val mimeType = if (pic.size >= 8 &&
            pic[0] == 0x89.toByte() &&
            pic[1] == 0x50.toByte() &&
            pic[2] == 0x4E.toByte() &&
            pic[3] == 0x47.toByte()
        ) {
            "image/png"
        } else {
            "image/jpeg"
        }

        return SourceFetchResult(
            source = ImageSource(pic.inputStream().source().buffer(), FileSystem.SYSTEM),
            mimeType = mimeType,
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<String> {
        override fun create(data: String, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.startsWith("audio:")) {
                return LocalAudioCoverFetcher(options.context, data)
            }
            return null
        }
    }

    companion object {
        private const val CACHE_SUBDIR = "local_audio_covers"
        private val negativeCache: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())
        private val extractSemaphore = Semaphore(3)

        fun invalidate(rawUriOrAudioUri: String, context: Context? = null) {
            val raw = rawUriOrAudioUri.removePrefix("audio:")
            negativeCache.remove(raw)
            if (context != null) {
                val hash = (raw.hashCode().toLong() and 0xFFFFFFFFL).toString(16)
                File(context.cacheDir, "$CACHE_SUBDIR/$hash.jpg").delete()
            }
        }

        fun clearAll(context: Context) {
            negativeCache.clear()
            File(context.cacheDir, CACHE_SUBDIR).deleteRecursively()
        }
    }
}
