package com.music.bitchord.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicLong

/** Desktop equivalent of Android's elapsed-realtime sleep timer. */
object DesktopSleepTimer {
    private val deadlineNs = AtomicLong(0L)

    val minutes = MutableStateFlow<Int?>(null)
    val afterTrack = MutableStateFlow(false)

    val presets = listOf(15, 30, 45, 60)

    fun start(minutes: Int) {
        require(minutes > 0) { "Sleep timer duration must be positive" }
        afterTrack.value = false
        this.minutes.value = minutes
        deadlineNs.set(System.nanoTime() + minutes * 60L * 1_000_000_000L)
    }

    fun startAfterTrack() {
        minutes.value = null
        deadlineNs.set(0L)
        afterTrack.value = true
    }

    fun cancel() {
        minutes.value = null
        deadlineNs.set(0L)
        afterTrack.value = false
    }

    fun remainingMs(): Long? = deadlineNs.get().takeIf { it != 0L }
        ?.let { (it - System.nanoTime()).coerceAtLeast(0L) / 1_000_000L }

    fun isExpired(): Boolean = deadlineNs.get() != 0L && remainingMs() == 0L
}
