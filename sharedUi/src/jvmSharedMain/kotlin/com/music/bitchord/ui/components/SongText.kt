package com.music.bitchord.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.data.model.Song
import com.music.bitchord.ui.graphics.fadingEdges

fun Modifier.thumbnailBorder(shape: Shape): Modifier = composed {
    this.border(
        width = 1.dp,
        color = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.15f),
        shape = shape
    )
}

/**
 * A song title with the catalogue-standard explicit badge before it, so the
 * mark sits in the same place on every row however long the title runs.
 *
 * The badge is drawn in the secondary line's colour — the artist's — rather
 * than the title's, as Apple Music draws it; [badgeColor] is that colour. With
 * [fadeOverflow] a title too long for its line fades out at its end, as Apple's
 * players let it, rather than ending in an ellipsis, as its lists do. Either
 * way the badge keeps its place ahead of whatever of the title is shown.
 */
@Composable
fun ExplicitSongTitle(
    song: Song,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    badgeColor: Color = color,
    fadeOverflow: Boolean = false,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (song.isExplicit == true) {
            ExplicitBadge(badgeColor)
            Spacer(Modifier.width(6.dp))
        }
        if (fadeOverflow) {
            FadingLine(text = song.title, style = style, color = color, modifier = Modifier.weight(1f, fill = false))
        } else {
            Text(
                text = song.title,
                style = style,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

/**
 * One line of text that, where it is too long for its width, fades out at its
 * end rather than ending in an ellipsis — see [fadingEdges].
 */
@Composable
fun FadingLine(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    var overflowing by remember { mutableStateOf(false) }
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        onTextLayout = { overflowing = it.didOverflowWidth },
        // Only where it overflows: the fade is a layer of its own, and a line
        // that fits has nothing to fade.
        modifier = modifier.then(if (overflowing) Modifier.fadingEdges(LINE_FADE, end = FullFade) else Modifier),
    )
}

/**
 * Text over at most [maxLines] lines that, where it is too long for them, fades
 * out at the end of its last line rather than ending in an ellipsis.
 *
 * A clipped paragraph would wrap its last line short at a word, leaving it
 * nowhere near the edge it fades at. So the lines before the last are set as
 * the paragraph sets them and the rest runs on unbroken as a [FadingLine].
 * Give [style] a line height trimmed of nothing (`LineHeightStyle.Trim.None`)
 * so the lines set apart sit the same distance apart as lines set together.
 */
@Composable
fun FadingText(text: String, style: TextStyle, color: Color, maxLines: Int, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val maxWidth = constraints.maxWidth
        val layout = remember(text, style, maxWidth) {
            measurer.measure(text, style, constraints = Constraints(maxWidth = maxWidth))
        }
        if (layout.lineCount <= maxLines) {
            Text(text = text, style = style, color = color)
        } else {
            val lastStart = layout.getLineStart(maxLines - 1)
            Column {
                Text(text = text.substring(0, lastStart).trimEnd(), style = style, color = color, maxLines = maxLines - 1)
                FadingLine(text = text.substring(lastStart), style = style, color = color)
            }
        }
    }
}

/**
 * How far a line too long for its space takes to fade out: measured off Apple
 * Music's mini player, about 37pt, ending at the edge of the line's space.
 */
val LINE_FADE = 36.dp

private val FullFade = { 1f }

/**
 * The explicit mark, as Apple Music draws it: a filled tile with the E cut
 * out of it, so whatever sits behind — a list row, the player's artwork —
 * shows through the letter. Used everywhere a track is marked explicit.
 *
 * A square tile with the capital centred in it, as Apple's is. The cut-out is
 * a real hole rather than the letter painted in a background colour, which is
 * why it draws into its own offscreen layer: DstOut can then only erase the
 * tile, never what's under it.
 */
@Composable
fun ExplicitBadge(color: Color, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    // W800, the Bold cut in this app's family: a step up from the Semibold
    // the label's Bold draws. No tracking, which sat the letter left of centre.
    val style = MaterialTheme.typography.labelSmall.copy(
        fontSize = BADGE_LETTER_SIZE,
        fontWeight = FontWeight.W800,
        letterSpacing = 0.sp,
    )
    val letter = remember(measurer, style) { measurer.measure("E", style) }
    val density = LocalDensity.current
    val capPx = with(density) { style.fontSize.toPx() } * CAP_HEIGHT
    // Square, as Apple's is, sized off the capital so it scales with the text.
    val side = with(density) { (capPx / BADGE_CAP_SHARE).toDp() }
    Canvas(
        modifier
            .size(side)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        drawRoundRect(color = color, cornerRadius = CornerRadius(2.dp.toPx()))
        drawText(
            textLayoutResult = letter,
            // Centred on the capital's own height rather than on its line box,
            // whose ascent and descent are not the letter's.
            topLeft = Offset(
                (this.size.width - letter.size.width) / 2f,
                (this.size.height + capPx) / 2f - letter.firstBaseline,
            ),
            blendMode = BlendMode.DstOut,
        )
    }
}

/**
 * The badge's letter, and how much of the tile's height its capital takes —
 * 0.52, measured off Apple Music's — which with SF Pro's capital height, 0.705
 * of the size, makes the tile about the 13dp it has always been.
 */
private val BADGE_LETTER_SIZE = 9.5.sp
private const val BADGE_CAP_SHARE = 0.52f
private const val CAP_HEIGHT = 0.705f
