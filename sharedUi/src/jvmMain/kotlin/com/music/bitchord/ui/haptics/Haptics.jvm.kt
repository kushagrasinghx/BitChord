package com.music.bitchord.ui.haptics

import androidx.compose.runtime.Composable

/** A desktop has no motor; every haptic is a no-op. */
actual class Haptics {
    actual fun play(haptic: Haptic) = Unit
}

private val NoHaptics = Haptics()

@Composable
actual fun rememberHaptics(): Haptics = NoHaptics
