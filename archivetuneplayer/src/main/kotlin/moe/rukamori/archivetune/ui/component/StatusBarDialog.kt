/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

val LocalImmersiveStatusBarsHidden = compositionLocalOf { false }

@Composable
fun EdgeToEdgeDialogWindow() {
    val view = LocalView.current
    DisposableEffect(view) {
        val dialogWindow = (view.parent as? DialogWindowProvider)?.window
        if (dialogWindow != null) {
            WindowCompat.setDecorFitsSystemWindows(dialogWindow, false)
        }
        onDispose { }
    }
}

@Composable
fun KeepStatusBarHiddenInDialog() {
    val hidden = LocalImmersiveStatusBarsHidden.current
    val view = LocalView.current
    DisposableEffect(view, hidden) {
        val dialogWindow = (view.parent as? DialogWindowProvider)?.window
        if (dialogWindow != null && hidden) {
            val controller =
                WindowCompat.getInsetsController(dialogWindow, dialogWindow.decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.statusBars())
        }
        onDispose { }
    }
}
