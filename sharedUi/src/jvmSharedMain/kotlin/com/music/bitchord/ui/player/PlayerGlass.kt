package com.music.bitchord.ui.player

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Liquid Glass for the player's own controls, lent by the phone's app, where
 * the glass is drawn. Null wherever there is none — the desktop, the glass
 * switched off, dynamic blur reduced — and the controls keep their flat
 * plus-lighter discs.
 */
@Stable
interface PlayerGlass {
    /**
     * Records what this draws as what the glass bends. Goes on the player's
     * background, under everything else it holds: glass sampling the whole
     * player would be sampling the layer it is itself drawn into.
     */
    fun Modifier.glassBackdrop(): Modifier

    /**
     * [content], with the backdrop under it sampled, saturated and blurred
     * once for every [glassSurface] in it rather than once for each.
     */
    @Composable
    fun GlassGroup(content: @Composable () -> Unit)

    /** A glass surface cut to [shape], bending its own piece of its group's sample. */
    @Composable
    fun Modifier.glassSurface(shape: CornerBasedShape): Modifier

    /**
     * Whether glyphs in a [GlassGroup] turn dark over what its glass shows under
     * them, as the app's glass glyphs do; null where nothing reads it.
     */
    @Composable
    fun rememberGlyphTone(): PlayerGlyphTone?
}

/** Glyphs in the player's glass, turning dark over light content under them. */
@Stable
interface PlayerGlyphTone {
    /** Whether the glass under the glyphs shows something light enough for them to go dark. */
    val dark: Boolean

    /** Marks this as the glyphs the tone reads under: them, not the control round them. */
    fun Modifier.glyphArea(): Modifier
}

/** The phone's [PlayerGlass], provided around the player. */
val LocalPlayerGlass = staticCompositionLocalOf<PlayerGlass?> { null }

/** The glass of the [GlassControlsRow] a control is in; null outside one. */
internal val LocalGroupGlass = staticCompositionLocalOf<PlayerGlass?> { null }

/**
 * A row of the player's controls drawn in its glass, sharing one sample of
 * the backdrop between them — or a plain row, where there is no glass.
 */
@Composable
internal fun GlassControlsRow(content: @Composable RowScope.() -> Unit) {
    val glass = LocalPlayerGlass.current
    if (glass == null) {
        Row(verticalAlignment = Alignment.CenterVertically, content = content)
        return
    }
    glass.GlassGroup {
        CompositionLocalProvider(LocalGroupGlass provides glass) {
            Row(verticalAlignment = Alignment.CenterVertically, content = content)
        }
    }
}
