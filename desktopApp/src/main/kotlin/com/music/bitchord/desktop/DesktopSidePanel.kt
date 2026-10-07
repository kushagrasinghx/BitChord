package com.music.bitchord.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** What the column beside the page is showing. */
internal enum class DesktopSidePanel { LYRICS, QUEUE }

/**
 * Apple Music's right-hand sidebar: the lyrics or the queue in a column beside the page, while the
 * player itself stays closed. The page narrows to make room rather than being covered, so whatever
 * was being browsed stays usable next to the words.
 */
@Composable
internal fun DesktopSidePanelColumn(
    panel: DesktopSidePanel?,
    onClose: () -> Unit,
    content: @Composable (DesktopSidePanel) -> Unit,
) {
    // Held through the exit, so the column slides out with what it had in it rather than empty.
    var shown by remember { mutableStateOf(panel) }
    if (panel != null) shown = panel
    AnimatedVisibility(
        visible = panel != null,
        enter = expandHorizontally(tween(SLIDE_MS, easing = FastOutSlowInEasing), expandFrom = Alignment.Start) +
            fadeIn(tween(SLIDE_MS)),
        exit = shrinkHorizontally(tween(SLIDE_MS, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Start) +
            fadeOut(tween(SLIDE_MS / 2)),
    ) {
        Row(Modifier.width(SIDE_PANEL_WIDTH).fillMaxHeight()) {
            Box(Modifier.width(1.dp).fillMaxHeight().background(DesktopDivider))
            Crossfade(
                targetState = shown ?: return@Row,
                animationSpec = tween(CROSSFADE_MS),
                label = "sidePanel",
            ) { which ->
                Box(Modifier.fillMaxSize()) {
                    // The lyrics stand on the window itself, as in Apple Music; the queue is a list
                    // and takes the sidebar's glass.
                    if (which == DesktopSidePanel.QUEUE) {
                        Box(Modifier.fillMaxSize().desktopChromeGlass())
                    }
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (which == DesktopSidePanel.LYRICS) HEADER_HEIGHT else QUEUE_HEADER_HEIGHT)
                                .padding(start = 18.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // The queue heads itself ("Queue", then its sections); only the lyrics
                            // need a name over them.
                            Text(
                                text = if (which == DesktopSidePanel.LYRICS) DesktopStrings["lyrics", "Lyrics"] else "",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            DesktopToolbarButton(onClick = onClose, size = 28.dp) {
                                Icon(
                                    Icons.Rounded.Close,
                                    DesktopStrings["close", "Close"],
                                    tint = DesktopSecondary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(
                                    start = if (which == DesktopSidePanel.LYRICS) 18.dp else 14.dp,
                                    end = if (which == DesktopSidePanel.LYRICS) 18.dp else 4.dp,
                                    bottom = 14.dp,
                                ),
                        ) {
                            content(which)
                        }
                    }
                }
            }
        }
    }
}

private val SIDE_PANEL_WIDTH = 360.dp
private val HEADER_HEIGHT = 46.dp
private val QUEUE_HEADER_HEIGHT = 30.dp
private const val SLIDE_MS = 260
private const val CROSSFADE_MS = 180
