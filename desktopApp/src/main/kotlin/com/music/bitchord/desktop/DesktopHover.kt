package com.music.bitchord.desktop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * What a card does when the pointer is over it: rises a little, and casts a little more shadow.
 *
 * A desktop app answers the pointer before it is clicked — it is most of what separates one from a
 * page in a browser, and the cards here answered nothing at all. The movement is small on purpose:
 * a shelf of them is a lot of motion if each one leaps.
 *
 * The scale is applied about the card's own centre, so a row of them does not shuffle sideways as
 * one grows, and the shadow is animated with it rather than switched on, which would read as a
 * flicker at the boundary.
 */
@Composable
internal fun Modifier.desktopHoverLift(
    shape: Shape,
    scale: Float = HOVER_SCALE,
    elevation: androidx.compose.ui.unit.Dp = HOVER_ELEVATION,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val lift by animateFloatAsState(
        targetValue = if (hovered) 1f else 0f,
        animationSpec = tween(HOVER_MS),
        label = "hoverLift",
    )
    this
        .hoverable(interaction)
        .graphicsLayer {
            val grown = 1f + (scale - 1f) * lift
            scaleX = grown
            scaleY = grown
        }
        .shadow(elevation * lift, shape)
}

/** Barely more than a nudge — enough to acknowledge the pointer, not enough to jostle the shelf. */
private const val HOVER_SCALE = 1.035f

private val HOVER_ELEVATION = 18.dp

/** Quick enough to feel like a response rather than an animation. */
private const val HOVER_MS = 140


/**
 * A round button's acknowledgement of the pointer: the faintest wash behind the glyph.
 *
 * The toolbar and player buttons were bare clickable boxes, so pointing at one did nothing until it
 * was pressed. On a desktop that reads as a web page. Kept very light — these sit over artwork and
 * a frosted bar, and anything stronger would look like a selected state rather than a hovered one.
 */
@Composable
internal fun Modifier.desktopHoverWash(): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val wash by animateFloatAsState(
        targetValue = if (hovered) 1f else 0f,
        animationSpec = tween(WASH_MS),
        label = "hoverWash",
    )
    this
        .hoverable(interaction)
        .drawBehind {
            if (wash <= 0.01f) return@drawBehind
            drawCircle(
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.12f * wash),
                radius = size.minDimension / 2f,
            )
        }
}

private const val WASH_MS = 120
