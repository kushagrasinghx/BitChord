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
 * Installed as a [DebugLog.Sink] alongside the logcat sink, and drained by
 * [recordCrash] when an uncaught exception reaches the handler installed in
 * [BitChordApplication].
 */
object CrashLog {
    private const val CAPACITY = 4000

    @Volatile
    private var buffer: Array<String?> = arrayOfNulls(CAPACITY)
    @Volatile
    private var index = 0
    @Volatile
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

    /** Writes the crash and the buffered log to a fresh file in [dir]. */
    fun dump(dir: File, throwable: Throwable) {
        dump(File(dir, "neiro-crash-" + timestamp() + ".log"), throwable)
    }

    /** Writes the crash and the buffered log to [file]. */
    fun dump(file: File, throwable: Throwable) {
        file.parentFile?.mkdirs()
        file.printWriter().use { out ->
            out.println("=== Neiro crash report ===")
            out.println("Time: ${timestamp()}")
            out.println("Process: ${android.os.Process.myPid()}")
            out.println("Thread: ${Thread.currentThread().name}")
            out.println()
            out.println("=== Uncaught exception ===")
            throwable.printStackTrace(out)
            out.println()
            out.println("=== Recent log (last $count lines) ===")
            for (line in snapshot()) out.println(line)
        }
    }

    private fun snapshot(): List<String> {
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

    internal fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())

    private fun Throwable.stackTraceString(): String =
        StringWriter().also { PrintWriter(it).use { pw -> printStackTrace(pw) } }.toString()
}
