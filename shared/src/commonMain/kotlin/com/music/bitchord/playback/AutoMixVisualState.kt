package com.music.bitchord.playback

import com.music.bitchord.data.model.QueueEntryIdentity

object AutoMixArtworkTuning {
    const val START_LAG_AFTER_SWAP = 0.6
    const val WINDOW_SECONDS = 2.6
    const val MIN_WINDOW_SECONDS = 0.8
    private const val LINEAR_REVEAL_WEIGHT = 0.25f
    const val OUTGOING_END_SCALE = 1f
    const val INCOMING_START_SCALE = 1f
    const val BLUR_START_DP = 20f
    const val BLUR_END_PROGRESS = 0.85f
    const val BLUR_RADIUS_STEPS = 64
    const val NOISE_SCALE = 2.6f
    const val NOISE_SOFTNESS = 0.20f
    const val NOISE_LOW = 0.18f
    const val NOISE_HIGH = 0.82f
    const val NOISE_SEED_SPAN = 32f
    const val NOISE_OCTAVES = 3
    const val CANVAS_PREPARE_TIMEOUT_MS = 4_000L
    // Leave one audio-clock sample of margin so a late UI callback cannot join the window.
    const val CANVAS_READY_MARGIN_SECONDS = 0.05
    const val MIXING_BADGE_FADE_MS = 400
    const val MIXING_BADGE_ILLUMINATE_MS = 950
    const val MIXING_BADGE_FULL_HOLD_MS = 120L
    const val MIXING_BADGE_EXTINGUISH_MS = 950
    const val MIXING_BADGE_DIM_HOLD_MS = 1_100L
    const val MIXING_BADGE_DIM_ALPHA = 0.55f
    const val MIXING_BADGE_BRIGHT_ALPHA = 0.95f
    const val MIXING_BADGE_HALO_RADIUS_DP = 4.5f
    const val MIXING_BADGE_HALO_ALPHA = 0.62f
    const val MIXING_BADGE_EDGE_FEATHER_FRACTION = 0.34f
    const val SEEK_GLOW_ATTACK_SECONDS = 0.8
    const val SEEK_GLOW_RELEASE_SECONDS = 1.0
    const val SEEK_GLOW_REDUCED_FADE_SECONDS = 0.15
    const val SEEK_IDLE_FILL_ALPHA = 0.88f
    const val SEEK_GLOW_EXTRA_HEIGHT_FRACTION = 0.12f
    const val SEEK_GLOW_HALO_OUTSET_DP = 4f
    const val SEEK_GLOW_HALO_ALPHA = 0.26f

    fun ease(progress: Float): Float = progress * progress * (3f - 2f * progress)

    fun revealProgress(progress: Float): Float {
        // Keep a gentle start and finish without hiding most of a short window in the tails.
        val smooth = ease(progress)
        return smooth + (progress - smooth) * LINEAR_REVEAL_WEIGHT
    }
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

    /** A prepared still may join the pair only before the reveal starts, not mid-dissolve. */
    val canPrepareArtwork: Boolean get() = when (phase) {
        AutoMixVisualPhase.ARMED -> true
        AutoMixVisualPhase.MIXING -> artworkWindow?.let { audioSeconds < it.startSeconds && artworkProgress == 0f }
            ?: (swapSeconds == null)
        AutoMixVisualPhase.COMPLETED, AutoMixVisualPhase.CANCELLED -> false
    }
}
