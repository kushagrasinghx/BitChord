package com.music.bitchord.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowScope

// The window's own frame, drawn by the application rather than by the system.

/** The strip where the system's title bar would have been. */
@Composable
internal fun DesktopTitleBar() {
    // Collected rather than read: [DesktopTitleBarSetting.active] is a plain call, so a composable
    // that only asks it never learns the answer changed.
    val enabled by DesktopTitleBarSetting.enabled.collectAsState()
    if (!DesktopPlatform.drawsOwnWindowFrame || !enabled) return
    DesktopTitleBarDragArea(
        Modifier
            .fillMaxWidth()
            .height(CAPTION_HEIGHT)
            .desktopChromeGlass(),
    ) {
        Box(Modifier.fillMaxSize()) {
            DesktopWindowButtons(Modifier.align(Alignment.CenterEnd))
        }
    }
}

/** Whether there is a title bar at all. */
internal object DesktopTitleBarSetting {

    internal const val KEY = "window_title_bar"

    private val _enabled = MutableStateFlow(DesktopPersistence().boolean(KEY, true))

    /** What Settings shows and writes. */
    val enabled: StateFlow<Boolean> = _enabled

    fun set(value: Boolean) {
        DesktopPersistence().saveBoolean(KEY, value)
        _enabled.value = value
    }
}

/** What the caption buttons act on. */
internal class DesktopWindowActions(
    val minimize: () -> Unit,
    val toggleMaximize: () -> Unit,
    val close: () -> Unit,
)

internal val LocalDesktopWindowActions = staticCompositionLocalOf<DesktopWindowActions?> { null }

/** The window, for the drag area. */
internal val LocalDesktopWindowScope = staticCompositionLocalOf<WindowScope?> { null }

/** A region that moves the window, and maximizes it on a double click. */
@Composable
internal fun DesktopTitleBarDragArea(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    val scope = LocalDesktopWindowScope.current
    val actions = LocalDesktopWindowActions.current
    if (!DesktopPlatform.drawsOwnWindowFrame || scope == null) {
        Box(modifier) { content() }
        return
    }
    with(scope) {
        WindowDraggableArea(
            modifier = modifier.doubleClickToMaximize(actions),
            content = content,
        )
    }
}

/** The caption buttons, in the corner they belong in. */
@Composable
internal fun DesktopWindowButtons(modifier: Modifier = Modifier) {
    val enabled by DesktopTitleBarSetting.enabled.collectAsState()
    if (!DesktopPlatform.drawsOwnWindowFrame || !enabled) return
    val actions = LocalDesktopWindowActions.current ?: return
    val maximized by DesktopWindowMode.maximized.collectAsState()
    Row(modifier.height(CAPTION_HEIGHT)) {
        CaptionButton("Minimize", onClick = actions.minimize) { stroke ->
            drawLine(
                color = stroke,
                start = Offset(center.x - GLYPH_HALF.toPx(), center.y),
                end = Offset(center.x + GLYPH_HALF.toPx(), center.y),
                strokeWidth = HAIRLINE.toPx(),
                cap = StrokeCap.Square,
            )
        }
        CaptionButton(
            if (maximized) "Restore" else "Maximize",
            onClick = actions.toggleMaximize,
        ) { stroke ->
            val side = GLYPH_HALF.toPx() * 2f
            val line = HAIRLINE.toPx()
            if (maximized) {
                // Two overlapping panes, which is how every platform says "there is a smaller
                // window underneath this one".
                val inset = line * 2f
                drawRect(
                    color = stroke,
                    topLeft = Offset(center.x - side / 2f + inset, center.y - side / 2f - inset),
                    size = Size(side - inset, side - inset),
                    style = Stroke(width = line),
                )
                drawRect(
                    color = stroke,
                    topLeft = Offset(center.x - side / 2f - inset, center.y - side / 2f + inset),
                    size = Size(side - inset, side - inset),
                    style = Stroke(width = line),
                )
            } else {
                drawRect(
                    color = stroke,
                    topLeft = Offset(center.x - side / 2f, center.y - side / 2f),
                    size = Size(side, side),
                    style = Stroke(width = line),
                )
            }
        }
        CaptionButton("Close", onClick = actions.close, hover = CLOSE_HOVER) { stroke ->
            val reach = GLYPH_HALF.toPx()
            val line = HAIRLINE.toPx()
            drawLine(
                color = stroke,
                start = Offset(center.x - reach, center.y - reach),
                end = Offset(center.x + reach, center.y + reach),
                strokeWidth = line,
                cap = StrokeCap.Square,
            )
            drawLine(
                color = stroke,
                start = Offset(center.x + reach, center.y - reach),
                end = Offset(center.x - reach, center.y + reach),
                strokeWidth = line,
                cap = StrokeCap.Square,
            )
        }
    }
}

@Composable
private fun CaptionButton(
    label: String,
    onClick: () -> Unit,
    hover: Color = DesktopRowHover,
    glyph: androidx.compose.ui.graphics.drawscope.DrawScope.(Color) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        Modifier
            .size(CAPTION_WIDTH, CAPTION_HEIGHT)
            .background(if (hovered) hover else Color.Transparent)
            .hoverable(interaction)
            // The arrow, not the hand: this is window furniture, and the pointer says so before the
            // click does.
            .pointerHoverIcon(PointerIcon.Default)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .semantics { contentDescription = label }
            .drawBehind { glyph(if (hovered) Color.White else DesktopSecondary) },
        contentAlignment = Alignment.Center,
    ) {}
}

/**
 * The platform's own caption metrics, which is what makes these read as the window's controls
 * rather than the application's.
 */
private val CAPTION_WIDTH = 46.dp
private val CAPTION_HEIGHT = 32.dp

/** Half the width of a glyph, and the weight every one of them is drawn at. */
private val GLYPH_HALF = 5.dp
private val HAIRLINE = 1.dp

/** Windows' own close-button red, which is the one everybody now reads as close. */
private val CLOSE_HOVER = Color(0xFFC42B1C)

/** Answers a double click the way the system caption always has. */
private fun Modifier.doubleClickToMaximize(actions: DesktopWindowActions?): Modifier =
    if (actions == null) this else pointerInput(actions) {
        detectTapGestures(onDoubleTap = { actions.toggleMaximize() })
    }
