package com.music.bitchord.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import bitchord.desktopapp.generated.resources.Res
import bitchord.desktopapp.generated.resources.logo
import org.jetbrains.compose.resources.painterResource

fun main() = application {
    // Closing puts the window away rather than ending the process, while there is a tray icon to
    // bring it back from — see [DesktopWindowVisibility].
    val visible by DesktopWindowVisibility.visible.collectAsState()
    // Hoisted so the player can fill the screen and the caption buttons can maximize — see
    // [DesktopWindowMode].
    val placement by DesktopWindowMode.placement.collectAsState()
    val state = rememberWindowState(width = 1_220.dp, height = 780.dp)
    LaunchedEffect(placement) { state.placement = placement }
    // ...and back, for the times the window is moved between placements by something that is not
    // us; see [DesktopWindowMode.adopt].
    LaunchedEffect(state) {
        snapshotFlow { state.placement }.collect(DesktopWindowMode::adopt)
    }
    Window(
        onCloseRequest = { if (DesktopWindowVisibility.onCloseRequest()) exitApplication() },
        visible = visible,
        title = "BitChord",
        icon = painterResource(Res.drawable.logo),
        state = state,
        // No system title bar on Windows, where the application draws its own instead — see
        // [DesktopWindowChrome].
        undecorated = DesktopPlatform.drawsOwnWindowFrame,
        resizable = true,
    ) {
        val actions = remember {
            DesktopWindowActions(
                minimize = { state.isMinimized = true },
                toggleMaximize = DesktopWindowMode::toggleMaximized,
                // The same door the system's close button went through, so the tray keeps the
                // process alive exactly as it did before.
                close = { if (DesktopWindowVisibility.onCloseRequest()) exitApplication() },
            )
        }
        // The clipping region is a fixed shape, so it is re-applied on every resize, and dropped
        // while the window fills the screen. Re-asked whenever the window is shown again too: one
        // restored from the tray is a new handle as far as the compositor is concerned.
        LaunchedEffect(state) {
            snapshotFlow { Triple(visible, state.size, state.placement) }.collect { (shown, _, where) ->
                if (shown) DesktopWindowCorners.update("BitChord", where == WindowPlacement.Floating)
            }
        }
        CompositionLocalProvider(
            LocalDesktopWindowActions provides actions,
            LocalDesktopWindowScope provides this,
        ) {
            BitChordDesktopApp()
        }
    }
}
