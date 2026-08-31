package com.music.bitchord.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.fletchmckee.liquid.LiquidState

/**
 * The optional sampling state for Liquid Glass surfaces. A null value means
 * that regular Haze glass remains in charge. Keeping this separate from the
 * dynamic-blur preference lets users choose either visual treatment safely.
 */
val LocalLiquidGlassState = staticCompositionLocalOf<LiquidState?> { null }
