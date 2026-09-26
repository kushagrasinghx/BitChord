package com.music.bitchord.data

/**
 * Milliseconds on a monotonic clock. The phone installs
 * `SystemClock.elapsedRealtime`, which keeps counting through deep sleep; the
 * desktop keeps the JVM's own.
 */
object MonotonicClock {
    private val origin = System.nanoTime()

    @Volatile
    var nowMs: () -> Long = { (System.nanoTime() - origin) / 1_000_000L }
}
