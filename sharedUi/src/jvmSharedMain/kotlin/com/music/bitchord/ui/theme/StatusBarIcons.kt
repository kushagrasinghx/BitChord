package com.music.bitchord.ui.theme

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable

/**
 * Draws just the status bar glyphs dark or light, leaving the navigation bar
 * as the page underneath set it. Nothing on a platform without one.
 */
@Composable
expect fun StatusBarIcons(dark: Boolean)

/**
 * Takes the status bar off the window this is composed in while [hidden], and
 * puts it back when this leaves composition. A swipe down from the top edge
 * still shows it for a moment. Nothing on a platform without one.
 */
@Composable
expect fun HideStatusBar(hidden: Boolean)

/**
 * Takes both system bars off the window while [hidden], for a video playing
 * full screen; a swipe in from an edge shows them for a moment. Puts them back
 * when this leaves composition.
 */
@Composable
expect fun HideSystemBars(hidden: Boolean)

/**
 * The status bar's insets whether it is showing or not. Anything laid out
 * under it reads this rather than [WindowInsets.Companion.statusBars], which
 * drops to zero the moment [HideStatusBar] hides it — the content would jump
 * up into the camera cutout instead of the icons simply going away.
 */
@Composable
expect fun stableStatusBarInsets(): WindowInsets
