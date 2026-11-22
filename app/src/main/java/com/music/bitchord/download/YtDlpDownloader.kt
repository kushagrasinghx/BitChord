package com.music.bitchord.download

import android.content.Context
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.data.DebugLog as Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Downloads audio via yt-dlp and converts to high-quality MP3 directly within Android.
 */
object YtDlpDownloader {

    private const val TAG = "BitChord"

    @Volatile
    private var initialized = false
    private val initLock = Any()

    fun init(context: Context) {
        if (initialized) return
        synchronized(initLock) {
            if (!initialized) {
                try {
                    val app = context.applicationContext
                    // Ensure the latest bundled yt-dlp binary is installed in no_backup/youtubedl-android/yt-dlp
                    runCatching {
                        val ytdlpDir = File(app.noBackupFilesDir, "youtubedl-android/yt-dlp")
                        val ytdlpFile = File(ytdlpDir, "yt-dlp")
                        val rawStream = app.resources.openRawResource(com.music.bitchord.R.raw.ytdlp)
                        val rawBytes = rawStream.readBytes()
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
                    android.util.Log.e(TAG, "Failed to initialize yt-dlp / FFmpeg", e)
                    Log.e(TAG, "Failed to initialize yt-dlp / FFmpeg", e)
                }
            }
        }
    }

    private fun buildRequest(
        context: Context,
        url: String,
        outputTemplate: String,
        playerClient: String,
    ): YoutubeDLRequest {
        return YoutubeDLRequest(url).apply {
            addOption("-f", "ba/b") // Best audio only - avoid downloading video
            addOption("-x")
            addOption("--audio-format", "mp3")
            addOption("--audio-quality", "0") // 320kbps / best audio quality
            addOption("--no-playlist")
            addOption("--no-check-certificates")
            addOption("--no-update")
            addOption("--extractor-args", "youtube:player_client=$playerClient")
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
        init(context)

        val tempDir = File(context.cacheDir, "ytdlp_downloads").apply { mkdirs() }
        val outputTemplate = "${tempDir.absolutePath}/$videoId.%(ext)s"
        val expectedMp3 = File(tempDir, "$videoId.mp3")
        expectedMp3.delete()

        val url = if (videoId.startsWith("http://") || videoId.startsWith("https://")) {
            videoId
        } else {
            "https://www.youtube.com/watch?v=$videoId"
        }

        try {
            var response: YoutubeDLResponse? = null
            try {
                // Primary attempt with android,web client (bypasses SABR 403 on standard streams)
                response = executeYtDlp(buildRequest(context, url, outputTemplate, "android,web"), videoId, onProgress)
            } catch (e: YoutubeDLException) {
                val msg = e.message.orEmpty()
                if (msg.contains("403") || msg.contains("SABR") || msg.contains("Forbidden")) {
                    android.util.Log.w(TAG, "yt-dlp primary client hit 403/SABR; retrying with ios,tv: $msg")
                    Log.w(TAG, "yt-dlp primary client hit 403/SABR; retrying with ios,tv: $msg")
                    response = executeYtDlp(buildRequest(context, url, outputTemplate, "ios,tv"), videoId, onProgress)
                } else {
                    throw e
                }
            }
            android.util.Log.i(TAG, "yt-dlp download completed for $videoId: exit code ${response?.exitCode}")
            Log.d(TAG, "yt-dlp download completed for $videoId: exit code ${response?.exitCode}")
        } catch (e: YoutubeDLException) {
            expectedMp3.delete()
            android.util.Log.e(TAG, "yt-dlp failed for $videoId: ${e.message}", e)
            Log.w(TAG, "yt-dlp failed for $videoId: ${e.message}")
            throw e
        } catch (e: Exception) {
            expectedMp3.delete()
            android.util.Log.e(TAG, "Download execution error for $videoId: ${e.message}", e)
            Log.w(TAG, "Download execution error for $videoId: ${e.message}")
            throw e
        }

        if (!expectedMp3.exists() || expectedMp3.length() == 0L) {
            // Check if yt-dlp named it slightly differently or left part
            val candidates = tempDir.listFiles { _, name -> name.startsWith(videoId) && name.endsWith(".mp3") }
            val found = candidates?.firstOrNull { it.length() > 0 }
            if (found != null) {
                return@withContext found
            }
            error("yt-dlp finished but no MP3 output was found for $videoId")
        }

        expectedMp3
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
