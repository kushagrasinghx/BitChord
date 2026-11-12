package com.music.bitchord.ui.theme

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Draws just the status bar glyphs dark or light, leaving the navigation bar
 * exactly as the page underneath already set it.
 *
 * The player's artwork luminance is only sampled from the top of the cover,
 * under the status bar — it says nothing about the navigation bar. Driving
 * [isAppearanceLightNavigationBars] off it anyway used to also trip Android's
 * automatic nav-bar contrast scrim on light artwork, painting the transparent,
 * page-colored navigation bar solid white.
 */
@Composable
actual fun StatusBarIcons(dark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = findWindow(view) ?: return
    SideEffect {
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = dark
    }
}

@Composable
actual fun HideStatusBar(hidden: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = findWindow(view) ?: return
    DisposableEffect(window, hidden) {
        if (!hidden) return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, view)
        val behavior = controller.systemBarsBehavior
        // Before Android 15 a window left on the default cutout mode is only
        // let into the cutout while the status bar is there to cover it: hide
        // the bar and the whole window is pushed down below the camera, a black
        // band where the artwork was. 15+ lays every window out edge to edge
        // regardless, and the default there already reaches further than
        // short edges would, so it is left alone.
        val intoCutout = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM &&
            window.attributes.layoutInDisplayCutoutMode ==
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
        if (intoCutout && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.statusBars())
        onDispose {
            controller.show(WindowInsetsCompat.Type.statusBars())
            controller.systemBarsBehavior = behavior
            if (intoCutout && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                }
            }
        }
    }
}

@Composable
actual fun HideSystemBars(hidden: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = findWindow(view) ?: return
    DisposableEffect(window, hidden) {
        if (!hidden) return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, view)
        val behavior = controller.systemBarsBehavior
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = behavior
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
actual fun stableStatusBarInsets(): WindowInsets = WindowInsets.statusBarsIgnoringVisibility

// Walks up the Compose view hierarchy to find a DialogWindowProvider (e.g. modal player) before falling back to Activity context.
fun findWindow(view: android.view.View): android.view.Window? {
    var parent = view.parent
    while (parent != null) {
        if (parent is androidx.compose.ui.window.DialogWindowProvider) {
            return parent.window
        }
        parent = parent.parent
    }
    var context = view.context
    while (context is android.content.ContextWrapper) {
        if (context is Activity) {
            return context.window
        }
        context = context.baseContext
    }
    return null
}
