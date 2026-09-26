package com.music.bitchord.ui.player

import androidx.compose.runtime.Composable

/** Milliseconds on the clock `SystemClock.uptimeMillis` reads on the phone. */
internal expect fun uptimeMillis(): Long

/** Milliseconds on a monotonic clock that keeps counting through sleep. */
internal expect fun elapsedRealtimeMillis(): Long

/** Whether `Modifier.blur` actually blurs here, rather than being a no-op. */
internal expect val renderEffectBlurSupported: Boolean

/** Holds off the display's idle timeout for as long as [enabled]. */
@Composable
internal expect fun KeepScreenOn(enabled: Boolean)

/** The app's language, as a BCP 47 tag. */
@Composable
internal expect fun appLanguageTag(): String

/**
 * Back, for one of the player's own layers. Call order is priority order: a
 * later call is the one back reaches first while both are enabled. On the
 * desktop, back is Escape.
 */
@Composable
internal expect fun PlayerBackHandler(enabled: Boolean, onBack: () -> Unit)
