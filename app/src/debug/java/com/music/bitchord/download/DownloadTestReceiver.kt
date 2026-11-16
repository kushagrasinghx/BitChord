package com.music.bitchord.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.provider.MediaStore
import androidx.work.WorkManager
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Properties
import java.util.concurrent.TimeUnit

/**
 * Debug-only adb entry point for the process-death integration test.
 *
 * Instrumentation cannot own phase 1: Android force-stops the target app when
 * an instrumentation run finishes, which also cancels its WorkManager jobs.
 * This receiver seeds the same work from a normal app process, so the host can
 * SIGKILL that process and leave the scheduler untouched.
 */
class DownloadTestReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        when (intent.action) {
            ACTION_SEED -> scope.launch {
                try {
                    seed(app)
                } finally {
                    pending.finish()
                }
            }
            ACTION_VERIFY -> {
                // Verification waits for WorkManager, so it must not hold the
                // broadcast open: a goAsync result kept for longer than the
                // broadcast timeout is an ANR even though it is not on main.
                pending.finish()
                scope.launch { verify(app) }
            }
            ACTION_CLEAN -> scope.launch {
                try {
                    clean(app)
                } finally {
                    pending.finish()
                }
            }
            else -> pending.finish()
        }
    }

    private suspend fun seed(context: Context) {
        cancelMonitor()
        val song = testSong()
        Downloads.cancel(VIDEO_ID)
        runCatching { Downloads.delete(context, VIDEO_ID) }
        removeMediaRows(context, DownloadStore.fileNameFor(song, EXTENSION))
        clearJournal(context)
        deleteTestState(context)
        val fixture = writeFixture(context)
        AppSettings.setExportDownloads(true)

        val initial = Properties().apply {
            setProperty("phase", "1")
            setProperty("pid", Process.myPid().toString())
            setProperty("videoId", VIDEO_ID)
            setProperty("startedAt", System.currentTimeMillis().toString())
            setProperty("fixtureSha256", sha256(fixture))
            setProperty("fixtureBytes", fixture.length().toString())
            setProperty("uniqueWork", RECOVERY_WORK)
        }
        writeProperties(context, RESULT, initial)
        Downloads.enqueue(context, song)

        monitorJob = scope.launch {
            while (true) {
                val state = Downloads.active.value[VIDEO_ID]
                val progress = Properties().apply {
                    setProperty("phase", "1-running")
                    setProperty("pid", Process.myPid().toString())
                    setProperty("videoId", VIDEO_ID)
                    setProperty("startedAt", initial.getProperty("startedAt"))
                    setProperty("fixtureSha256", initial.getProperty("fixtureSha256"))
                    setProperty("fixtureBytes", initial.getProperty("fixtureBytes"))
                    setProperty("activeState", state?.javaClass?.simpleName ?: "none")
                    setProperty("progress", (state as? DownloadState.Running)?.fraction?.toString() ?: "0")
                    setProperty("journalEntries", pendingJournal(context).size.toString())
                    setProperty("uniqueWork", RECOVERY_WORK)
                }
                writeProperties(context, PROGRESS, progress)
                if (state is DownloadState.Running && state.fraction > 0f) {
                    writeProperties(context, RESULT, progress)
                }
                if (state is DownloadState.Failed) {
                    progress.setProperty("failure", state.reason)
                    writeProperties(context, PROGRESS, progress)
                    return@launch
                }
                delay(100L)
            }
        }
    }

    private suspend fun verify(context: Context) {
        val before = readProperties(context, RESULT)
        val oldPid = before.getProperty("pid")?.toIntOrNull()
            ?: error("phase 1 did not record a PID")
        val deadline = System.currentTimeMillis() + 120_000L
        var saved: Uri? = null
        var failure: String? = null
        while (System.currentTimeMillis() < deadline) {
            saved = Downloads.savedUri(context, VIDEO_ID)
            if (saved != null) break
            failure = (Downloads.active.value[VIDEO_ID] as? DownloadState.Failed)?.reason
            if (failure != null) break
            delay(500L)
        }
        val result = Properties().apply {
            setProperty("phase", if (saved != null) "2-complete" else "2-failed")
            setProperty("pid", Process.myPid().toString())
            setProperty("oldPid", oldPid.toString())
            setProperty("videoId", VIDEO_ID)
            setProperty("startedAt", before.getProperty("startedAt"))
            setProperty("fixtureSha256", before.getProperty("fixtureSha256"))
            setProperty("fixtureBytes", before.getProperty("fixtureBytes"))
            setProperty("uniqueWork", RECOVERY_WORK)
            failure?.let { setProperty("failure", it) }
        }
        if (saved != null) {
            val bytes = context.contentResolver.openInputStream(saved)?.use { it.readBytes() }
                ?: error("could not read $saved")
            val name = DownloadStore.fileNameFor(testSong(), EXTENSION)
            val rows = mediaRows(context, name)
            val journal = pendingJournal(context)
            val work = runCatching {
                WorkManager.getInstance(context)
                    .getWorkInfosForUniqueWork(RECOVERY_WORK)
                    .get(10, TimeUnit.SECONDS)
            }.getOrDefault(emptyList())
            result.apply {
                setProperty("finalBytes", bytes.size.toString())
                setProperty("finalSha256", sha256(bytes))
                setProperty("mediaStoreRows", rows.size.toString())
                setProperty("mediaStorePending", rows.joinToString(",") { it.pending.toString() })
                setProperty("mediaStoreNames", rows.joinToString(",") { it.name })
                setProperty("journalEntries", journal.size.toString())
                setProperty("workStates", work.joinToString(",") { it.state.name })
                setProperty("workIds", work.joinToString(",") { it.id.toString() })
            }
        }
        writeProperties(context, RESULT, result)
    }

    private suspend fun clean(context: Context) {
        cancelMonitor()
        Downloads.cancel(VIDEO_ID)
        runCatching { Downloads.delete(context, VIDEO_ID) }
        removeMediaRows(context, DownloadStore.fileNameFor(testSong(), EXTENSION))
        clearJournal(context)
        deleteTestState(context)
    }

    private fun writeFixture(context: Context): File {
        val fixture = File(context.filesDir, FIXTURE)
        val dataBytes = 4 * 1024 * 1024
        FileOutputStream(fixture).use { out ->
            out.write(wavHeader(dataBytes))
            val silence = ByteArray(8 * 1024)
            repeat(dataBytes / silence.size) { out.write(silence) }
        }
        File(context.filesDir, CONFIG).writeText(
            """
            videoId=$VIDEO_ID
            path=${fixture.absolutePath}
            extension=$EXTENSION
            mimeType=audio/wav
            chunkDelayMs=20
            """.trimIndent(),
        )
        return fixture
    }

    private fun testSong() = Song(
        videoId = VIDEO_ID,
        title = "Process Death Fixture",
        artist = "BitChord Test",
        thumbnailUrl = null,
        durationText = "0:01",
    )

    private fun clearJournal(context: Context) {
        val prefs = context.getSharedPreferences("bitchord_settings", Context.MODE_PRIVATE)
        val current = DownloadJobCodec.decode(prefs.getString(KEY_PENDING, null))
        prefs.edit()
            .putString(KEY_PENDING, DownloadJobCodec.encode(current.filterNot { it.videoId == VIDEO_ID }))
            .commit()
    }

    private fun pendingJournal(context: Context) = DownloadJobCodec.decode(
        context.getSharedPreferences("bitchord_settings", Context.MODE_PRIVATE)
            .getString(KEY_PENDING, null),
    )

    private data class MediaRow(val pending: Int, val name: String)

    private fun mediaRows(context: Context, name: String): List<MediaRow> {
        val rows = mutableListOf<MediaRow>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.MediaColumns.IS_PENDING, MediaStore.MediaColumns.DISPLAY_NAME),
            "${MediaStore.MediaColumns.DISPLAY_NAME}=?",
            arrayOf(name),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) rows += MediaRow(cursor.getInt(0), cursor.getString(1))
        }
        return rows
    }

    private fun removeMediaRows(context: Context, name: String) {
        context.contentResolver.delete(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            "${MediaStore.MediaColumns.DISPLAY_NAME}=?",
            arrayOf(name),
        )
    }

    private fun deleteTestState(context: Context) {
        listOf(FIXTURE, CONFIG, PROGRESS, RESULT).forEach { File(context.filesDir, it).delete() }
    }

    private fun writeProperties(context: Context, name: String, values: Properties) {
        File(context.filesDir, name).outputStream().use { values.store(it, null) }
    }

    private fun readProperties(context: Context, name: String): Properties = Properties().apply {
        File(context.filesDir, name).inputStream().use { load(it) }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").also { digest ->
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
        }.digest().joinToString("") { "%02x".format(it) }

    private fun wavHeader(dataBytes: Int): ByteArray {
        val header = ByteArray(44)
        fun le(offset: Int, value: Int, bytes: Int) {
            for (i in 0 until bytes) header[offset + i] = ((value shr (8 * i)) and 0xff).toByte()
        }
        "RIFF".forEachIndexed { i, c -> header[i] = c.code.toByte() }
        le(4, 36 + dataBytes, 4)
        "WAVEfmt ".forEachIndexed { i, c -> header[8 + i] = c.code.toByte() }
        le(16, 16, 4)
        le(20, 1, 2)
        le(22, 1, 2)
        le(24, 44_100, 4)
        le(28, 88_200, 4)
        le(32, 2, 2)
        le(34, 16, 2)
        "data".forEachIndexed { i, c -> header[36 + i] = c.code.toByte() }
        le(40, dataBytes, 4)
        return header
    }

    private fun cancelMonitor() {
        monitorJob?.cancel()
        monitorJob = null
    }

    companion object {
        const val ACTION_SEED = "com.music.bitchord.debug.SEED_DOWNLOAD_DEATH_TEST"
        const val ACTION_VERIFY = "com.music.bitchord.debug.VERIFY_DOWNLOAD_DEATH_TEST"
        const val ACTION_CLEAN = "com.music.bitchord.debug.CLEAN_DOWNLOAD_DEATH_TEST"

        private const val VIDEO_ID = "bc-process-death-fixture"
        private const val EXTENSION = "wav"
        private const val FIXTURE = "bc-download-fixture.wav"
        private const val CONFIG = "download-test-fixture.properties"
        private const val PROGRESS = "download-process-death-progress.properties"
        private const val RESULT = "download-process-death-result.properties"
        private const val KEY_PENDING = "pending_download_jobs_v1"
        private const val RECOVERY_WORK = "download-recovery"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var monitorJob: Job? = null
    }
}
