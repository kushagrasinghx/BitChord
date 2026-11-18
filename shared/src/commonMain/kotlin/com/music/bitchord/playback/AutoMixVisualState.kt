package com.music.bitchord.playback

import com.music.bitchord.data.model.QueueEntryIdentity

object AutoMixArtworkTuning {
    const val START_LAG_AFTER_SWAP = 1.0
    const val WINDOW_SECONDS = 2.0
    const val MIN_WINDOW_SECONDS = 0.8
    const val OUTGOING_END_SCALE = 1f
    const val INCOMING_START_SCALE = 1f
    const val BLUR_START_DP = 20f
    const val BLUR_END_PROGRESS = 0.85f
    const val BLUR_RADIUS_STEPS = 64
    const val NOISE_SCALE = 3f
    const val NOISE_SOFTNESS = 0.22f
    const val NOISE_DRIFT = 0.16f
    const val NOISE_OCTAVES = 3
    const val SLEEVE_MASK_STRENGTH = 0.30f
    const val HERO_MASK_STRENGTH = 0.90f
    const val HERO_MASK_FULL_ASPECT = 1.5f
    const val MIXING_BADGE_FADE_MS = 400
    const val MIXING_BADGE_SWEEP_MS = 1_800
    const val MIXING_BADGE_SWEEP_PAUSE_MS = 3_000L
    const val MIXING_BADGE_DIM_ALPHA = 0.55f
    const val MIXING_BADGE_BRIGHT_ALPHA = 0.95f
    const val MIXING_BADGE_HALO_RADIUS_DP = 3f
    const val MIXING_BADGE_HALO_ALPHA = 0.40f
    const val MIXING_BADGE_HALF_BAND_FRACTION = 0.32f
    const val SEEK_GLOW_ATTACK_SECONDS = 0.8
    const val SEEK_GLOW_RELEASE_SECONDS = 1.0
    const val SEEK_GLOW_REDUCED_FADE_SECONDS = 0.15
    const val SEEK_GLOW_FILL_ALPHA = 0.98f
    const val SEEK_GLOW_EXTRA_HEIGHT_DP = 1.25f
    const val SEEK_GLOW_HALO_OUTSET_DP = 3f
    const val SEEK_GLOW_HALO_ALPHA = 0.40f

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
