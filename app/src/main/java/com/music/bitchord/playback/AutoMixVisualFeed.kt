package com.music.bitchord.playback

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Main-thread writer shared by the playback service and the in-process player host. */
internal object AutoMixVisualFeed {
    private val mutableState = MutableStateFlow<AutoMixVisualState?>(null)
    val state: StateFlow<AutoMixVisualState?> = mutableState.asStateFlow()
    private var generation = 0L
    private var armedAtMs = 0L

    fun arm(
        outgoing: AutoMixVisualTrack,
        incoming: AutoMixVisualTrack,
        remoteArtworkScope: Long?,
        bypassArtwork: Boolean,
        artworkDecisionPending: Boolean = false,
    ): Long {
        val id = ++generation
        armedAtMs = SystemClock.elapsedRealtime()
        mutableState.value = AutoMixVisualState(
            id, outgoing, incoming, remoteArtworkScope = remoteArtworkScope, bypassArtwork = bypassArtwork,
            artworkDecisionPending = artworkDecisionPending,
        )
        return id
    }

    fun pending(id: Long?): AutoMixVisualState? = mutableState.value?.takeIf {
        it.generation == id && (it.mixActive || it.phase == AutoMixVisualPhase.ARMED)
    }

    fun resolveArtwork(id: Long, bypass: Boolean) {
        val armed = pending(id)?.takeIf { it.phase == AutoMixVisualPhase.ARMED && it.artworkDecisionPending } ?: return
        mutableState.value = armed.copy(bypassArtwork = bypass, artworkDecisionPending = false)
    }

    fun start(id: Long?, audioSeconds: Double) {
        val previous = pending(id) ?: return
        if (previous.mixActive) return
        mutableState.value = previous.copy(
            phase = AutoMixVisualPhase.MIXING,
            artworkDecisionPending = false,
            overlapStartSeconds = 0.0,
            audioSeconds = audioSeconds,
            sampledAtNanos = System.nanoTime(),
        )
    }

    fun swap(id: Long?, audioSeconds: Double, endSeconds: Double) {
        val previous = pending(id)?.takeIf { it.mixActive } ?: return
        val at = maxOf(previous.audioSeconds, audioSeconds)
        mutableState.value = previous.copy(
            audioSeconds = at,
            swapSeconds = at,
            artworkWindow = if (previous.bypassArtwork) null else windowAfter(at, endSeconds),
            sampledAtNanos = System.nanoTime(),
        )
    }

    private fun windowAfter(swap: Double, end: Double): AutoMixArtworkWindow? =
        AutoMixArtworkTuning.windowAfter(swap, end, (SystemClock.elapsedRealtime() - armedAtMs) / 1000.0)

    fun advance(id: Long?, audioSeconds: Double, endSeconds: Double) {
        val previous = pending(id)?.takeIf { it.mixActive && !it.bypassArtwork } ?: return
        val at = maxOf(previous.audioSeconds, audioSeconds)
        val window = previous.artworkWindow?.let {
            when {
                previous.artworkProgress >= 1f || it.endSeconds <= endSeconds -> it
                // A late decoder duration can shorten the effective fade. Before the window,
                // consume the lag first; once started, keep its start or drop the dissolve.
                previous.artworkProgress == 0f && at <= it.startSeconds -> windowAfter(at, endSeconds)
                endSeconds - it.startSeconds >= AutoMixArtworkTuning.MIN_WINDOW_SECONDS ->
                    it.copy(endSeconds = endSeconds)
                else -> null
            }
        }
        val progress = if (window == null) 0f else maxOf(
            previous.artworkProgress,
            ((at - window.startSeconds) / (window.endSeconds - window.startSeconds)).coerceIn(0.0, 1.0).toFloat(),
        )
        mutableState.value = previous.copy(
            audioSeconds = at,
            artworkWindow = window,
            artworkProgress = progress,
            sampledAtNanos = System.nanoTime(),
        )
    }

    fun complete(id: Long?) {
        val previous = pending(id) ?: return
        if (!previous.mixActive || previous.swapSeconds == null) {
            cancel(id)
            return
        }
        mutableState.value = previous.copy(phase = AutoMixVisualPhase.COMPLETED)
    }

    fun cancel(id: Long? = mutableState.value?.generation) {
        val previous = pending(id) ?: return
        mutableState.value = previous.copy(
            phase = AutoMixVisualPhase.CANCELLED,
            artworkWindow = null,
            artworkProgress = 0f,
        )
    }
}
