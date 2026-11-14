package com.music.bitchord.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.MediaStore
import com.music.bitchord.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * On-device crash capture, ported from ArchiveTune's CrashReporter
 * (moe.rukamori.archivetune.utils.CrashReporter + the handler in its App.kt).
 *
 * When an uncaught exception reaches the handler [install] sets, a report is
 * written synchronously, before the process dies:
 *  - a header (app version and flavour, device, Android version, ABI, pid),
 *  - the exception with its whole cause chain,
 *  - [CrashLog]'s ring buffer — the app's own recent log, fed by DebugLog and
 *    by the Timber tree the ported ArchiveTune player stack logs through,
 *  - this process's own logcat tail (an app may read its own pid's log
 *    without READ_LOGS).
 *
 * Where it lands:
 *  - always: Android/data/<package>/files/crash/neiro-crash-<time>.log
 *    (app-specific external storage, reachable over USB or a file manager;
 *    falls back to internal files/crash), newest [MAX_REPORTS] kept;
 *  - Android 10+: a copy in Download/Neiro/ through MediaStore, which needs
 *    no storage permission. Below Android 10 no public copy is made, so no
 *    permission is ever requested.
 *
 * A marker file records an unseen crash so the next launch can say where the
 * log went ([consumeUnseenCrash]); if the public copy could not be made while
 * dying it is retried then.
 *
 * Everything here is defensive and coroutine-free: crash reporting must never
 * be the thing that crashes, and it must always hand the exception on to the
 * previous handler so the system's own crash flow (and process death) runs.
 */
object CrashReporter {
    private const val DIR_NAME = "crash"
    private const val PUBLIC_DIR_NAME = "Neiro"
    private const val FILE_PREFIX = "neiro-crash-"
    private const val MARKER = "last_crash_unseen"
    private const val MAX_REPORTS = 10
    private const val LOGCAT_LINES = 3000
    private const val LOGCAT_TIMEOUT_MS = 2_000L
    private const val LOGCAT_MAX_CHARS = 512 * 1024

    @Volatile
    private var installed = false

    /** Installs the uncaught-exception handler. Call first thing in Application.onCreate. */
    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeJavaCrashReport(app, thread, throwable)
            } catch (_: Throwable) {
                // Never let the reporter mask the original crash.
            } finally {
                if (previous != null) {
                    previous.uncaughtException(thread, throwable)
                } else {
                    Process.killProcess(Process.myPid())
                    exitProcess(10)
                }
            }
        }
    }

    /** The app-specific crash folder: Android/data/<package>/files/crash. */
    fun crashDir(context: Context): File {
        val base = runCatching { context.getExternalFilesDir(DIR_NAME) }.getOrNull()
        val dir = base ?: File(context.filesDir, DIR_NAME)
        runCatching { dir.mkdirs() }
        return dir
    }

    /**
     * Writes the report for [throwable] and returns its file (null if even
     * that failed). Synchronous: runs on the dying thread.
     */
    fun writeJavaCrashReport(context: Context, thread: Thread?, throwable: Throwable): File? {
        val dir = crashDir(context)
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(dir, "$FILE_PREFIX$stamp.log")
        CrashLog.writeReport(
            file = file,
            thread = thread,
            throwable = throwable,
            header = header(context),
            appendix = { out -> appendLogcat(out) },
        )
        val publicCopy =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching { copyToDownloads(context, file) }.getOrDefault(false)
            } else {
                false
            }
        runCatching {
            File(dir, MARKER).writeText(file.name + "\n" + (if (publicCopy) "public" else "local") + "\n")
        }
        prune(dir)
        return file
    }

    /** The newest report on disk, if any. */
    fun latestReport(context: Context): File? =
        crashDir(context)
            .listFiles { f -> f.isFile && f.name.startsWith(FILE_PREFIX) }
            ?.maxByOrNull { it.lastModified() }

    /**
     * True once after a crash: clears the marker, and if the public copy
     * could not be made while the process was dying, makes it now. Does
     * file and MediaStore I/O — call it off the main thread.
     */
    fun consumeUnseenCrash(context: Context): Boolean {
        val dir = crashDir(context)
        val marker = File(dir, MARKER)
        if (!marker.isFile) return false
        val lines = runCatching { marker.readLines() }.getOrDefault(emptyList())
        runCatching { marker.delete() }
        val name = lines.getOrNull(0)?.trim().orEmpty()
        val copied = lines.getOrNull(1)?.trim() == "public"
        if (!copied && name.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val report = File(dir, name)
            if (report.isFile) runCatching { copyToDownloads(context, report) }
        }
        return true
    }

    /** Where the user should look, for the next-launch notice. */
    fun displayLocation(context: Context): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${Environment.DIRECTORY_DOWNLOADS}/$PUBLIC_DIR_NAME"
        } else {
            "Android/data/${context.packageName}/files/$DIR_NAME"
        }

    private fun header(context: Context): List<String> =
        buildList {
            add("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.FLAVOR}/${BuildConfig.BUILD_TYPE}")
            add("Package: ${context.packageName}")
            add("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            add("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            add("ABI: ${Build.SUPPORTED_ABIS.firstOrNull() ?: "?"}")
            add("Process: ${Process.myPid()}")
        }

    /**
     * This process's own logcat tail. Read on a helper thread with a hard
     * timeout, so a stuck logcat can never hold up the crash flow.
     */
    private fun appendLogcat(out: PrintWriter) {
        out.println()
        out.println("=== logcat (this process, last $LOGCAT_LINES lines) ===")
        val text = StringBuilder()
        val process =
            runCatching {
                ProcessBuilder(
                    "logcat", "-d", "-v", "threadtime",
                    "-t", LOGCAT_LINES.toString(),
                    "--pid=${Process.myPid()}",
                ).redirectErrorStream(true).start()
            }.getOrElse {
                out.println("(logcat unavailable: $it)")
                return
            }
        val reader =
            Thread({
                runCatching {
                    process.inputStream.bufferedReader().use { r ->
                        val buf = CharArray(8192)
                        while (true) {
                            val n = r.read(buf)
                            if (n < 0) break
                            synchronized(text) {
                                if (text.length < LOGCAT_MAX_CHARS) text.append(buf, 0, n)
                            }
                        }
                    }
                }
            }, "crash-logcat").apply { isDaemon = true }
        reader.start()
        runCatching { process.waitFor(LOGCAT_TIMEOUT_MS, TimeUnit.MILLISECONDS) }
        runCatching { reader.join(500) }
        runCatching { process.destroy() }
        synchronized(text) { out.println(text) }
    }

    private fun copyToDownloads(context: Context, report: File): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val resolver = context.contentResolver
        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, report.name)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + PUBLIC_DIR_NAME)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            resolver.openOutputStream(uri)?.use { out -> report.inputStream().use { it.copyTo(out) } }
                ?: error("no output stream")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (_: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            false
        }
    }

    private fun prune(dir: File) {
        runCatching {
            dir.listFiles { f -> f.isFile && f.name.startsWith(FILE_PREFIX) }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(MAX_REPORTS)
                ?.forEach { it.delete() }
        }
    }
}
