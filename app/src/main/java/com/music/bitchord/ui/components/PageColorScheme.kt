package com.music.bitchord.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.music.bitchord.ui.theme.ArtworkPalette

/**
 * Re-reads the theme's text, surface and outline colours off an album page's
 * own [palette] for everything drawn inside [content].
 *
 * An album page takes its cover's colour, light or dark — see
 * [com.music.bitchord.ui.theme.rememberAlbumPagePalette] — whatever the app's
 * theme is. A pale page in a dark app would leave every surface that reaches
 * for `onSurface`, or decides black-or-white from the surface's luminance
 * ([glassContentColor]), drawing white on beige: the track titles, the tab
 * labels, the bar's icons. Putting the page's colours in the theme for just
 * that subtree is what makes all of them follow without each being told.
 *
 * A null [palette] — any page that is not coloured this way — leaves the theme
 * as it is.
 */
@Composable
fun PageColorScheme(palette: ArtworkPalette?, content: @Composable () -> Unit) {
    val base = MaterialTheme.colorScheme
    // Always a MaterialTheme, even with nothing to change: swapping between
    // "content on its own" and "content inside a theme" would be a different
    // composition structure, and the bars that are wrapped in this would be
    // torn down and rebuilt — pill, animations and all — every time a page that
    // is coloured this way opened or closed.
    val scheme = remember(base, palette) {
        if (palette == null) {
            base
        } else {
            base.copy(
                background = palette.background,
                surface = palette.background,
                onBackground = palette.onBackground,
                onSurface = palette.onBackground,
                onSurfaceVariant = palette.onBackgroundVariant,
                surfaceVariant = palette.elevated,
                outline = palette.divider,
                outlineVariant = palette.divider,
            )
        }
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}
