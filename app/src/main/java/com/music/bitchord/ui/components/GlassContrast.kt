package com.music.bitchord.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Stable foreground tint for glass labels and icons: black in the light theme,
 * white in the dark theme. The material still samples its visual backdrop,
 * but foreground tinting does not capture or read GPU pixels.
 */
@Composable
fun glassContentColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) Color.Black else Color.White
