package com.music.bitchord.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.model.Song

fun Modifier.thumbnailBorder(shape: Shape): Modifier = composed {
    this.border(
        width = 1.dp,
        color = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.15f),
        shape = shape
    )
}

/** A song title with the catalogue-standard explicit badge in front of it. */
@Composable
fun ExplicitSongTitle(
    song: Song,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
) = ExplicitTitle(song.title, song.isExplicit == true, style, color, modifier, maxLines)

/** [ExplicitSongTitle] for a title that isn't a [Song]'s, such as a Home card's. */
@Composable
fun ExplicitTitle(
    title: String,
    explicit: Boolean,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (explicit) {
            ExplicitBadge(color)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = title,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The explicit mark, as Apple Music draws it: a filled tile with the E cut
 * out of it, so whatever sits behind — a list row, the player's artwork —
 * shows through the letter. Used everywhere a track is marked explicit.
 *
 * The cut-out is a real hole rather than the letter painted in a background
 * colour, which is why it draws into its own offscreen layer: DstOut can then
 * only erase the tile, never what's under it.
 */
@Composable
fun ExplicitBadge(color: Color, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
    val letter = remember(measurer, style) { measurer.measure("E", style) }
    // Same footprint the outlined badge had — the letter's line box plus 3dp
    // a side — so no row shifts.
    val size = with(LocalDensity.current) {
        DpSize(
            letter.size.width.toDp() + 6.dp,
            letter.size.height.toDp(),
        )
    }
    Canvas(
        modifier
            .size(size)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        drawRoundRect(color = color, cornerRadius = CornerRadius(2.dp.toPx()))
        drawText(
            textLayoutResult = letter,
            topLeft = Offset(
                (this.size.width - letter.size.width) / 2f,
                (this.size.height - letter.size.height) / 2f,
            ),
            blendMode = BlendMode.DstOut,
        )
    }
}
