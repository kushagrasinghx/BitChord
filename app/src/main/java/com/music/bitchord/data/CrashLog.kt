package com.music.bitchord.data

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A ring buffer of the app's own recent log lines, so a crash can be
 * reconstructed without needing adb.
 *
 * The phone's logcat is gone the moment the process dies, and the ported
 * ArchiveTune player stack is only ever mounted when a style is switched to —
 * exactly when it crashes. What survives is whatever the app logged in the
 * seconds before the death, and this is what gets written to disk.
 *
 * Fed by [DebugLog]'s sink (every build) and by the Timber tree planted in
 * [com.music.bitchord.BitChordApplication] (the ported ArchiveTune stack logs
 * through Timber). Drained by [CrashReporter] when an uncaught exception
 * reaches its handler.
 *
 * Deliberately free of android.* so it stays testable on the JVM: the
 * Android-specific header (app version, device, pid) and the logcat tail are
 * [CrashReporter]'s to supply.
 */
object CrashLog {
    internal const val CAPACITY = 4000

    private val buffer: Array<String?> = arrayOfNulls(CAPACITY)
    private var index = 0
    private var count = 0

    @Synchronized
    fun record(level: Char, tag: String, message: String, error: Throwable?) {
        val line = buildString {
            append(timestamp())
            append(' ')
            append(level)
            append(' ')
            append(tag)
            append(": ")
            append(message)
            if (error != null) {
                append('\n')
                append(error.stackTraceString())
            }
        }
        buffer[index] = line
        index = (index + 1) % CAPACITY
        if (count < CAPACITY) count++
    }

    /** The buffered lines, oldest first. */
    @Synchronized
    fun snapshot(): List<String> {
        val n = count
        if (n == 0) return emptyList()
        val result = ArrayList<String>(n)
        val start = if (n >= CAPACITY) index else 0
        for (i in 0 until n) {
            val line = buffer[(start + i) % CAPACITY]
            if (line != null) result.add(line)
        }
        return result
    }

    /** Empties the buffer. For tests. */
    @Synchronized
    internal fun clear() {
        buffer.fill(null)
        index = 0
        count = 0
    }

    /**
     * Writes a crash report to [file]: [header] lines, the uncaught exception
     * with its whole cause chain, the buffered log, then whatever [appendix]
     * adds (the logcat tail, on a device). Synchronous on purpose — it runs
     * inside the uncaught-exception handler, where nothing else may be
     * relied on to still be scheduled.
     */
    fun writeReport(
        file: File,
        thread: Thread?,
        throwable: Throwable,
        header: List<String> = emptyList(),
        appendix: (PrintWriter) -> Unit = {},
    ) {
        file.parentFile?.mkdirs()
        val lines = snapshot()
        file.printWriter().use { out ->
            out.println("=== Neiro crash report ===")
            out.println("Time: ${timestamp()}")
            out.println("Thread: ${thread?.name ?: "?"}")
            for (line in header) out.println(line)
            out.println()
            out.println("=== Uncaught exception ===")
            throwable.printStackTrace(out)
            out.println()
            out.println("=== Recent app log (last ${lines.size} lines, oldest first) ===")
            for (line in lines) out.println(line)
            out.flush()
            runCatching { appendix(out) }
        }
    }

    internal fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())

    private fun Throwable.stackTraceString(): String =
        StringWriter().also { PrintWriter(it).use { pw -> printStackTrace(pw) } }.toString()
}
