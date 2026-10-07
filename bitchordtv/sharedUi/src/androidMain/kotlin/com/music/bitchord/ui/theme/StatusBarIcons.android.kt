package com.music.bitchord.ui.theme

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

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
