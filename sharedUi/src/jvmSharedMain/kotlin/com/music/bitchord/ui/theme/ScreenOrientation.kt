package com.music.bitchord.ui.theme

import androidx.compose.runtime.Composable

/**
 * Turns the screen for a music video, as YouTube does: the full-screen button
 * asks for landscape, leaving full screen asks for portrait. Either is only a
 * nudge — once the phone is physically held that way the screen follows the
 * sensor again, so turning it back undoes it. Let go of, and the screen handed
 * back to the sensor, when this leaves composition.
 */
interface ScreenOrientation {
    fun landscape()
    fun portrait()
}

@Composable
expect fun rememberScreenOrientation(): ScreenOrientation
