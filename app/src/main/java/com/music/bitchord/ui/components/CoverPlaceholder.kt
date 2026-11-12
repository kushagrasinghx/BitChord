package com.music.bitchord.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * What stands in for a cover that isn't there: a device folder, a playlist
 * with nothing on it yet, an album whose files carry no picture.
 *
 * Apple Music's answer rather than a flat hole the size of a sleeve — a soft
 * diagonal gradient off the surface colour with a glyph for what the thing is.
 * Callers draw it behind the image, so a cover that loads late simply covers
 * it and nothing changes size.
 */
@Composable
fun CoverPlaceholder(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    /** The surface the gradient is cut from — a release page passes its palette's. */
    base: Color = MaterialTheme.colorScheme.surfaceVariant,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    /** The glyph's share of the tile. */
    iconFraction: Float = 0.36f,
) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(listOf(lerp(base, tint, 0.16f), base)),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxSize(iconFraction),
        )
    }
}
