package com.music.bitchord.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.foundation.border
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

// The look every modal card in the app shares.

/** The card fill. */
internal val DesktopCardFill = Color(0xFA1A1A1D)

/** A block inside a card — a settings group — lifted just off the fill. */
internal val DesktopCardInsetFill = Color(0x14FFFFFF)

/** The hairline that bounds a card, and separates one row from the next. */
internal val DesktopCardEdge = Color(0x1FFFFFFF)

/** What a row reads at while the pointer is on it. */
internal val DesktopRowHover = Color(0x1AFFFFFF)

/** The scrim a modal card sits on. */
internal val DesktopScrim = Color(0x8C000000)

/** Card, hairline and all. */
internal fun Modifier.desktopCard(shape: Shape): Modifier =
    clip(shape).background(DesktopCardFill).border(0.5.dp, DesktopCardEdge, shape)

/** A block of rows inside a card. */
internal fun Modifier.desktopCardInset(shape: Shape): Modifier =
    clip(shape).background(DesktopCardInsetFill).border(0.5.dp, DesktopCardEdge, shape)

/** The rule between two rows of a card. */
@Composable
internal fun DesktopCardRule(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = 0.5.dp, color = DesktopCardEdge)
}

/** The dialog shell: the same card the song menu and Settings are drawn on. */
@Composable
internal fun DesktopDialogPanel(
    onDismiss: () -> Unit,
    maxWidth: Int,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .background(DesktopScrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        val shape = RoundedCornerShape(20.dp)
        Box(
            Modifier
                .widthIn(max = maxWidth.dp)
                .fillMaxWidth()
                .desktopCard(shape),
        ) {
            // Behind the content rather than around it, so it swallows clicks on the panel's own
            // background without eating the rows' own.
            Box(
                Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            )
            Column(
                Modifier.padding(bottom = 4.dp),
                verticalArrangement = Arrangement.Top,
            ) {
                content()
            }
        }
    }
}
