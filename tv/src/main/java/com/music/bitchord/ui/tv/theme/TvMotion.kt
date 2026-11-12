package com.music.bitchord.ui.tv.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable

/**
 * Apple Design System Animation and Motion Tokens.
 * Derived from Apple macOS 26 / tvOS Design Specifications.
 */
enum class AppleDuration(val millis: Int) {
    Instant(0),
    Fast(100),
    Normal(150),
    Slow(200),
    Reveal(400),
    LyricsScroll(500),
}

enum class AppleSpringPreset(val stiffness: Float, val dampingRatio: Float) {
    /** Snappy response for buttons, cards, D-pad focus state transitions (400f, 0.65f) */
    Snappy(400f, 0.65f),
    /** Smooth fluid motion for tabs, navigation pills, progress bars (200f, 0.75f) */
    Smooth(200f, 0.75f),
    /** Gentle motion for screen transitions, lyrics scale, backdrop shifts (120f, 0.85f) */
    Gentle(120f, 0.85f),
}

@Immutable
data class AppleMotionTokens(
    val instant: Int = AppleDuration.Instant.millis,
    val fast: Int = AppleDuration.Fast.millis,
    val normal: Int = AppleDuration.Normal.millis,
    val slow: Int = AppleDuration.Slow.millis,
    val reveal: Int = AppleDuration.Reveal.millis,
)

/**
 * Creates an Apple-spec spring animation spec.
 */
fun <T> appleSpring(
    preset: AppleSpringPreset = AppleSpringPreset.Smooth,
    visibilityThreshold: T? = null,
): SpringSpec<T> = spring(
    dampingRatio = preset.dampingRatio,
    stiffness = preset.stiffness,
    visibilityThreshold = visibilityThreshold,
)

/**
 * Creates an Apple-spec tween animation spec.
 */
fun <T> appleTween(
    duration: AppleDuration = AppleDuration.Normal,
    easing: Easing = FastOutSlowInEasing,
): TweenSpec<T> = tween(
    durationMillis = duration.millis,
    easing = easing,
)
