package com.music.bitchord.download

import android.content.Context
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.data.DebugLog as Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * What the downloader asks for, and from which clients, when a video refuses
 * the first answer.
 *
 * Kept out of [YtDlpDownloader] itself so the policy is a value that can be
 * read and tested without starting yt-dlp. This is the whole fix for the
 * videos that used to fail with "Requested format is not available": ask for
 * the best audio the extractor has, and if that client cannot provide it, ask
 * another one rather than retrying the same request.
 */
internal object YtDlpFormatSelection {

    /**
     * Deliberately without a format id: `bestaudio` is the best audio-only
     * stream the extractor has, and `best` is the best combined one for videos
     * that publish no audio-only stream at all. The two cover every video that
     * has any audio; a video with neither is a real format failure and is
     * reported as one rather than retried forever.
     */
    const val SELECTOR = "bestaudio/best"

    /**
     * Clients to ask, in order, when the first one cannot serve a video.
     *
     * `visionos` is yt-dlp's no-JavaScript client and the only one on Android
     * that returns the adaptive audio-only streams without a PO token. `android`
     * is the fallback for the videos visionos refuses (made-for-kids uploads
     * are the common case) and returns YouTube's combined 360p stream, which is
     * still the song. The null attempt lets yt-dlp choose its own defaults for
     * anything both of those miss.
     */
    val CLIENTS: List<String?> = listOf("visionos,android", "android", null)

    /** Failures a different client or format expression can plausibly fix. */
    fun retryable(kind: YtDlpFailureKind): Boolean =
        kind == YtDlpFailureKind.FORMAT || kind == YtDlpFailureKind.EXTRACTOR

    /** True when [kind] at 0-based [attempt] leaves another client to ask. */
    fun shouldTryNext(kind: YtDlpFailureKind, attempt: Int): Boolean =
        retryable(kind) && attempt < CLIENTS.lastIndex
}

/**
 * Downloads audio via yt-dlp and converts to high-quality MP3 directly within Android.
 */
object YtDlpDownloader {

    private const val TAG = "BitChord"

    @Volatile
    private var initialized = false
    private val initLock = Any()

    @Volatile
    var lastInitError: Throwable? = null
        private set

    /**
     * Ensures native binaries and zip archives exist on disk.
     * On devices or custom ROMs where PackageManager did not extract .zip.so files into nativeLibraryDir,
     * this extracts them directly from the APK into internal storage and points nativeLibraryDir to it.
     */
    private fun ensureNativeLibraries(app: Context) {
        val nativeLibDir = File(app.applicationInfo.nativeLibraryDir)
        val pythonZip = File(nativeLibDir, "libpython.zip.so")
        val ffmpegZip = File(nativeLibDir, "libffmpeg.zip.so")
        if (pythonZip.exists() && pythonZip.length() > 0 && ffmpegZip.exists() && ffmpegZip.length() > 0) {
            return
        }

        // Fallback: extract directly from the APK file(s) into no_backup/youtubedl-bin
        val fallbackBinDir = File(app.noBackupFilesDir, "youtubedl-bin").apply { mkdirs() }
        val fallbackPythonZip = File(fallbackBinDir, "libpython.zip.so")
        val fallbackFfmpegZip = File(fallbackBinDir, "libffmpeg.zip.so")

        if (!fallbackPythonZip.exists() || fallbackPythonZip.length() == 0L ||
            !fallbackFfmpegZip.exists() || fallbackFfmpegZip.length() == 0L
        ) {
            val apkPaths = mutableListOf<String>()
            app.applicationInfo.sourceDir?.let { apkPaths.add(it) }
            app.applicationInfo.splitSourceDirs?.let { apkPaths.addAll(it) }

            val supportedAbis = android.os.Build.SUPPORTED_ABIS
            val neededLibs = listOf(
                "libpython.zip.so",
                "libffmpeg.zip.so",
                "libpython.so",
                "libffmpeg.so",
                "libqjs.so",
            )

            for (apkPath in apkPaths) {
                val apkFile = File(apkPath)
                if (!apkFile.exists()) continue
                runCatching {
                    java.util.zip.ZipFile(apkFile).use { zip ->
                        for (libName in neededLibs) {
                            val targetFile = File(fallbackBinDir, libName)
                            if (targetFile.exists() && targetFile.length() > 0) continue

                            for (abi in supportedAbis) {
                                val entry = zip.getEntry("lib/$abi/$libName")
                                if (entry != null) {
                                    zip.getInputStream(entry).use { input ->
                                        targetFile.outputStream().use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                    targetFile.setExecutable(true, false)
                                    break
                                }
                            }
                        }
                    }
                }.onFailure {
                    android.util.Log.w(TAG, "Fallback extraction from $apkPath failed: ${it.message}")
                }
            }
        }

        if (fallbackPythonZip.exists() && fallbackPythonZip.length() > 0) {
            app.applicationInfo.nativeLibraryDir = fallbackBinDir.absolutePath
            runCatching {
                val field = YoutubeDL::class.java.getDeclaredField("binDir").apply { isAccessible = true }
                field.set(null, fallbackBinDir)
            }
            runCatching {
                val field = FFmpeg::class.java.getDeclaredField("binDir").apply { isAccessible = true }
                field.set(null, fallbackBinDir)
            }
            android.util.Log.i(TAG, "Using fallback native library directory: ${fallbackBinDir.absolutePath}")
        }
    }

    fun init(context: Context) {
        if (initialized) return
        synchronized(initLock) {
            if (!initialized) {
                try {
                    val app = context.applicationContext
                    // Ensure native libraries exist before initializing
                    ensureNativeLibraries(app)

                    // Ensure the latest bundled yt-dlp binary is installed in no_backup/youtubedl-android/yt-dlp
                    runCatching {
                        val ytdlpDir = File(app.noBackupFilesDir, "youtubedl-android/yt-dlp")
                        val ytdlpFile = File(ytdlpDir, "yt-dlp")
                        val rawStream = app.resources.openRawResource(com.music.bitchord.R.raw.ytdlp)
                        val rawBytes = rawStream.use { it.readBytes() }
                        if (!ytdlpFile.exists() || ytdlpFile.length() != rawBytes.size.toLong()) {
                            ytdlpDir.mkdirs()
                            ytdlpFile.writeBytes(rawBytes)
                            ytdlpFile.setExecutable(true, false)
                            android.util.Log.i(TAG, "Synced bundled yt-dlp (${rawBytes.size} bytes) to ${ytdlpFile.absolutePath}")
                        }
                    }.onFailure {
                        android.util.Log.w(TAG, "Could not sync bundled yt-dlp: ${it.message}")
                    }

                    YoutubeDL.getInstance().init(app)
                    FFmpeg.getInstance().init(app)
                    initialized = true
                    lastInitError = null
                    android.util.Log.i(TAG, "yt-dlp and FFmpeg successfully initialized")
                    Log.d(TAG, "yt-dlp and FFmpeg successfully initialized")

                    // Background update check for yt-dlp nightly
                    CoroutineScope(Dispatchers.IO).launch {
                        runCatching {
                            YoutubeDL.getInstance().updateYoutubeDL(app, YoutubeDL.UpdateChannel.NIGHTLY)
                        }.onFailure {
                            Log.d(TAG, "yt-dlp background update check: ${it.message}")
                        }
                    }
                } catch (e: Throwable) {
                    lastInitError = e
                    android.util.Log.e(TAG, "Failed to initialize yt-dlp / FFmpeg", e)
                    Log.e(TAG, "Failed to initialize yt-dlp / FFmpeg", e)
                    throw e
                }
            }
        }
    }

    private fun buildRequest(
        context: Context,
        url: String,
        outputTemplate: String,
        playerClient: String?,
    ): YoutubeDLRequest {
        return YoutubeDLRequest(url).apply {
            addOption("-f", YtDlpFormatSelection.SELECTOR)
            addOption("-x")
            addOption("--audio-format", "mp3")
            // Best VBR MP3. The source is the ceiling, so this never claims a
            // bitrate the stream did not have; it only avoids throwing away
            // quality the stream did have.
            addOption("--audio-quality", "0")
            addOption("--no-playlist")
            addOption("--no-check-certificates")
            addOption("--no-update")
            if (playerClient != null) {
                addOption("--extractor-args", "youtube:player_client=$playerClient")
            }
            val cookie = runCatching { AuthStore(context).cookie }.getOrNull()
            if (!cookie.isNullOrBlank()) {
                addOption("--add-header", "Cookie: ${cookie.trim()}")
            }
            addOption("-o", outputTemplate)
        }
    }

    private fun executeYtDlp(
        request: YoutubeDLRequest,
        videoId: String,
        onProgress: (fraction: Float) -> Unit,
    ): YoutubeDLResponse {
        return YoutubeDL.getInstance().execute(request, videoId) { progress, _, _ ->
            if (progress >= 0f) {
                val fraction = (progress / 100f).coerceIn(0f, 1f)
                onProgress(fraction)
            }
        }
    }

    /**
     * Downloads YouTube/YouTube Music video with [videoId] as MP3 using yt-dlp.
     *
     * @param onProgress reports progress from 0f to 1f.
     * @return File pointing to the completed temporary MP3 file.
     */
    suspend fun downloadMp3(
        context: Context,
        videoId: String,
        onProgress: (fraction: Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        try {
            init(context)
        } catch (e: Throwable) {
            val msg = e.message ?: e.javaClass.simpleName
            throw YtDlpFailure(YtDlpFailureKind.INIT, "The downloader could not start: $msg", e)
        }

        if (!initialized) {
            val msg = lastInitError?.message ?: lastInitError?.javaClass?.simpleName ?: "initialization not complete"
            throw YtDlpFailure(YtDlpFailureKind.INIT, "The downloader could not start: $msg", lastInitError)
        }

        val tempDir = File(context.cacheDir, "ytdlp_downloads").apply { mkdirs() }
        // A URL is a legal input too; its slashes and colons are not legal in a
        // file name, so the file is named after a stable digest of it instead.
        val stem = if (videoId.startsWith("http://") || videoId.startsWith("https://")) {
            "url-" + videoId.hashCode().toUInt().toString(16)
        } else {
            videoId
        }
        val outputTemplate = "${tempDir.absolutePath}/$stem.%(ext)s"

        val url = if (videoId.startsWith("http://") || videoId.startsWith("https://")) {
            videoId
        } else {
            "https://www.youtube.com/watch?v=$videoId"
        }

        var last: YtDlpFailure? = null
        YtDlpFormatSelection.CLIENTS.forEachIndexed { index, client ->
            // A cancellation stops the blocking yt-dlp process, but the
            // coroutine has to stop before the next client is asked — a
            // retry after the listener called the download off is not a retry.
            if (!isActive) throw kotlinx.coroutines.CancellationException("download cancelled")
            clearTemp(tempDir, stem)
            onProgress(0f)
            try {
                executeYtDlp(buildRequest(context, url, outputTemplate, client), videoId, onProgress)
                val mp3 = findMp3(tempDir, stem)
                    ?: throw YtDlpFailure(
                        YtDlpFailureKind.OUTPUT,
                        YtDlpFailureKind.OUTPUT.userMessage(null),
                    )
                android.util.Log.i(TAG, "yt-dlp download completed for $videoId with client ${client ?: "default"}")
                Log.d(TAG, "yt-dlp download completed for $videoId with client ${client ?: "default"}")
                return@withContext mp3
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                val classified = if (failure is YtDlpFailure) failure else failureFrom(failure)
                last = classified
                val retry = YtDlpFormatSelection.shouldTryNext(classified.kind, index)
                android.util.Log.e(
                    TAG,
                    "yt-dlp failed for $videoId (${classified.kind}, client ${client ?: "default"}): ${classified.message}",
                    classified,
                )
                Log.w(TAG, "yt-dlp failed for $videoId (${classified.kind}): ${classified.message}")
                if (!retry) throw classified
                android.util.Log.w(TAG, "retrying $videoId with the next player client")
            }
        }
        // The loop either returns an MP3 or throws; this is here so the function
        // cannot fall through with a null result if that ever changes.
        throw last ?: YtDlpFailure(YtDlpFailureKind.INIT, YtDlpFailureKind.INIT.userMessage(null))
    }

    /** The MP3 a completed run left behind, newest first, ignoring partial files. */
    private fun findMp3(tempDir: File, stem: String): File? {
        val exact = File(tempDir, "$stem.mp3")
        if (exact.isFile && exact.length() > 0L) return exact
        // The dot keeps a longer video id ("abcde") from answering for a
        // shorter one ("abc") whose download is running beside it.
        return tempDir.listFiles { _, name ->
            name.startsWith("$stem.") && name.endsWith(".mp3")
        }?.filter { it.isFile && it.length() > 0L }
            ?.maxByOrNull { it.lastModified() }
    }

    /** Removes a previous attempt's output so a stale file cannot be mistaken for this one. */
    private fun clearTemp(tempDir: File, stem: String) {
        // Only the dot-delimited names this download's template produces. A
        // bare prefix would also match a longer id ("abc-def" for "abc") and
        // delete a track downloading beside it.
        tempDir.listFiles { _, name -> name.startsWith("$stem.") }
            ?.forEach { runCatching { it.delete() } }
    }

    private fun failureFrom(failure: Throwable): YtDlpFailure {
        val raw = failure.message.orEmpty()
        val kind = classifyYtDlpFailure(raw)
        val detail = ytDlpDetail(raw)
        return YtDlpFailure(kind, kind.userMessage(detail), failure)
    }

    /**
     * Cancels an ongoing yt-dlp download process.
     */
    fun cancel(videoId: String) {
        runCatching {
            YoutubeDL.getInstance().destroyProcessById(videoId)
        }
    }
}
