package com.music.bitchord.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Main-thread writer shared by the playback service and the in-process player host. */
internal object AutoMixVisualFeed {
    private val mutableState = MutableStateFlow<AutoMixVisualState?>(null)
    val state: StateFlow<AutoMixVisualState?> = mutableState.asStateFlow()
    private var generation = 0L

    fun arm(outgoing: AutoMixVisualTrack, incoming: AutoMixVisualTrack): Long {
        val id = ++generation
        mutableState.value = AutoMixVisualState(id, outgoing, incoming)
        return id
    }

    fun pending(id: Long?): AutoMixVisualState? = mutableState.value?.takeIf {
        it.generation == id && (it.mixActive || it.phase == AutoMixVisualPhase.ARMED)
    }

    fun start(id: Long?, audioSeconds: Double) {
        val previous = pending(id) ?: return
        if (previous.mixActive) return
        mutableState.value = previous.copy(
            phase = AutoMixVisualPhase.MIXING,
            overlapStartSeconds = 0.0,
            audioSeconds = audioSeconds,
        )
    }

    fun swap(id: Long?, audioSeconds: Double, endSeconds: Double) {
        val previous = pending(id)?.takeIf { it.mixActive } ?: return
        val at = maxOf(previous.audioSeconds, audioSeconds)
        mutableState.value = previous.copy(
            audioSeconds = at,
            swapSeconds = at,
            artworkWindow = windowAfter(at, endSeconds),
        )
    }

    private fun windowAfter(swap: Double, end: Double): AutoMixArtworkWindow? {
        val remaining = end - swap
        if (remaining < AutoMixArtworkTuning.MIN_WINDOW_SECONDS) return null
        // Sacrifice lag first, then duration; never schedule before the observed swap.
        val duration = minOf(AutoMixArtworkTuning.WINDOW_SECONDS, remaining)
        val lag = minOf(AutoMixArtworkTuning.START_LAG_AFTER_SWAP, remaining - duration)
        val start = swap + lag
        return AutoMixArtworkWindow(start, minOf(end, start + duration))
    }

    fun advance(id: Long?, audioSeconds: Double, endSeconds: Double) {
        val previous = pending(id)?.takeIf { it.mixActive } ?: return
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
        mutableState.value = previous.copy(audioSeconds = at, artworkWindow = window, artworkProgress = progress)
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
