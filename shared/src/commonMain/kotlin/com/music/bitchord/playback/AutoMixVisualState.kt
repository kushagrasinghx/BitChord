package com.music.bitchord.playback

import com.music.bitchord.data.model.QueueEntryIdentity

object AutoMixArtworkTuning {
    const val START_LAG_AFTER_SWAP = 1.0
    const val WINDOW_SECONDS = 2.0
    const val MIN_WINDOW_SECONDS = 0.8
    const val OUTGOING_END_SCALE = 1f
    const val INCOMING_START_SCALE = 1f

    fun ease(progress: Float): Float = progress * progress * (3f - 2f * progress)
}

/** The index is the arming index, not a live index; use the entry identity where available. */
data class AutoMixVisualTrack(
    val identity: QueueEntryIdentity,
    val armedIndex: Int,
    val artworkUrl: String?,
)

enum class AutoMixVisualPhase { ARMED, MIXING, COMPLETED, CANCELLED }

data class AutoMixArtworkWindow(val startSeconds: Double, val endSeconds: Double)

/**
 * One pair across the session swap. All times are on this generation's audio axis, not wall time.
 * A null artwork window after the swap means the pair cannot fit a dissolve. On cancellation,
 * discard both layers and use the live session item (the accepted transport may still be settling).
 */
data class AutoMixVisualState(
    val generation: Long,
    val outgoing: AutoMixVisualTrack,
    val incoming: AutoMixVisualTrack,
    val phase: AutoMixVisualPhase = AutoMixVisualPhase.ARMED,
    val overlapStartSeconds: Double? = null,
    val audioSeconds: Double = 0.0,
    val swapSeconds: Double? = null,
    val artworkWindow: AutoMixArtworkWindow? = null,
    val artworkProgress: Float = 0f,
) {
    val mixActive: Boolean get() = phase == AutoMixVisualPhase.MIXING
}
