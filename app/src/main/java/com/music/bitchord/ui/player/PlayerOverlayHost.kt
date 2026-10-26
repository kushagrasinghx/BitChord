package com.music.bitchord.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.music.bitchord.ui.performance.FrameDiagnostics
import com.music.bitchord.ui.utils.guardSheetFromContentTouches
import com.music.bitchord.R

/**
 * A full-screen player in the activity's existing window. Reusable UI nodes
 * survive visits; deactivation forgets remembered effects and video sessions.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeApi::class)
@Composable
internal fun PlayerOverlayHost(
    visible: Boolean,
    dock: PlayerDock,
    motion: PlayerSheetMotion,
    content: @Composable () -> Unit,
) {
    var visited by remember { mutableStateOf(false) }
    SideEffect { if (visible) visited = true }
    if (!visible && !visited) return

    val density = LocalDensity.current
    // Only this small gesture state restarts. Its hidden/expanded anchors are
    // available immediately; no second hide animation follows our own slide.
    val sheet = remember(visible, density) {
        SheetState(
            skipPartiallyExpanded = true,
            density = density,
            initialValue = if (visible) SheetValue.Expanded else SheetValue.Hidden,
            confirmValueChange = { target ->
                if (target == SheetValue.Hidden && visible) {
                    motion.close()
                    false
                } else true
            },
            skipHiddenState = false,
        )
    }
    DisposableEffect(visible, sheet) {
        if (visible) {
            motion.sheet = sheet
            dock.sheetOffset = motion::position
            motion.onOverlayShown()
            FrameDiagnostics.mark("BitChord.playerShown")
        }
        onDispose {
            if (visible) {
                dock.sheetOffset = null
                motion.onSheetGone()
                FrameDiagnostics.mark("BitChord.playerHidden")
            }
        }
    }

    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(visible) {
        if (visible) {
            focus.clearFocus(force = true)
            keyboard?.hide()
        }
    }
    // Child handlers (lyrics/queue/drawers) register after this one.
    BackHandler(enabled = visible) { motion.close() }
    val title = stringResource(R.string.now_playing)

    Box(
        Modifier.fillMaxSize().semantics {
            if (visible) {
                paneTitle = title
                isTraversalGroup = true
                dismiss { motion.close(); true }
            }
        }.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            // A transparent Material Surface still participates in hit testing.
            // Leaving the whole host unplaced also removes drawing, semantics
            // and input while closed, rather than hiding it with alpha alone.
            layout(placeable.width, placeable.height) {
                if (visible) placeable.place(0, 0)
            }
        },
        propagateMinConstraints = true,
    ) {
        BottomSheetScaffold(
            scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheet),
            sheetPeekHeight = 0.dp,
            sheetMaxWidth = Dp.Unspecified,
            sheetShape = PlayerSheetShape,
            sheetContainerColor = Color.Transparent,
            sheetTonalElevation = 0.dp,
            sheetShadowElevation = 0.dp,
            sheetDragHandle = null,
            sheetSwipeEnabled = visible,
            snackbarHost = {},
            containerColor = Color.Transparent,
            sheetContent = {
                Layout(
                    modifier = Modifier.fillMaxSize()
                        .offset { IntOffset(0, motion.contentOffsetPx()) }
                        .guardSheetFromContentTouches(sheet)
                        .layout { measurable, constraints ->
                            FrameDiagnostics.measure("BitChord.playerMeasure") {
                                val placeable = measurable.measure(constraints)
                                if (visible) {
                                    dock.sheetTravel = placeable.height.toFloat()
                                    motion.laidOut = true
                                }
                                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                            }
                        },
                    content = {
                        ReusableContentHost(active = visible) {
                            CompositionLocalProvider(LocalPlayerDock provides dock) { content() }
                        }
                    },
                    measurePolicy = remember(visible) { PlayerContentMeasurePolicy(visible) },
                )
            },
        ) {
            Box(
                Modifier.fillMaxSize()
                    .drawBehind {
                        val open = dock.openFraction()
                        if (open > 0f && !(open >= 1f && dock.attached)) {
                            drawRect(Color.Black.copy(alpha = 0.32f * open))
                        }
                    }
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = motion::close,
                    ),
            )
        }
    }
}

/** The flying artwork can extend above the sheet while it docks into the bar. */
internal object PlayerSheetShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rectangle(Rect(0f, -size.height, size.width, size.height))
}
