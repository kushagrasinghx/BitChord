package com.music.bitchord.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.components.GlassTone
import com.music.bitchord.ui.components.LocalGlassStrip
import com.music.bitchord.ui.components.SquirclePillShape
import com.music.bitchord.ui.components.glassToneArea
import com.music.bitchord.ui.components.rememberGlassTone
import com.music.bitchord.ui.components.backdrop.backdrops.LayerBackdrop
import com.music.bitchord.ui.components.backdrop.backdrops.layerBackdrop
import com.music.bitchord.ui.components.backdrop.backdrops.rememberLayerBackdrop
import com.music.bitchord.ui.components.isLiquidGlassActive
import com.music.bitchord.ui.components.liquidGlass
import com.music.bitchord.ui.components.liquidGlassStrip
import com.music.bitchord.ui.components.rememberGlassStrip

/**
 * The app's Liquid Glass, lent to the player — see [PlayerGlass] — over a
 * recording of the player's own background. The app's backdrop and its page
 * sample are the app under the player, not the player; and as on a page with
 * glass in it (see DetailScreen), only the background is recorded, since the
 * player as a whole holds the glass.
 *
 * Each group is a [com.music.bitchord.ui.components.GlassStrip]: the
 * background sampled, saturated and blurred once for the group, each surface
 * only bending its piece of it, in the one-pass shader glass.
 */
@Stable
private class LiquidPlayerGlass(private val backdrop: LayerBackdrop) : PlayerGlass {
    override fun Modifier.glassBackdrop(): Modifier = layerBackdrop(backdrop)

    @Composable
    override fun GlassGroup(content: @Composable () -> Unit) {
        val strip = rememberGlassStrip()
        Box(Modifier.liquidGlassStrip(strip, page = backdrop)) {
            CompositionLocalProvider(LocalGlassStrip provides strip, content = content)
        }
    }

    @Composable
    override fun Modifier.glassSurface(shape: CornerBasedShape): Modifier =
        // Dark glass in either theme: the player is dark in both.
        liquidGlass(shape, light = false)

    @Composable
    override fun Modifier.glassPillSurface(): Modifier = liquidGlass(PillShape, light = false)

    @Composable
    override fun rememberGlyphTone(): PlayerGlyphTone? {
        // Over the group's strip, as the surfaces are, and on dark glass.
        val tone = rememberGlassTone(light = false) ?: return null
        return remember(tone) { GlassGlyphTone(tone) }
    }
}

/** Liquid Glass's capsule, for the player's pills as for the app's. */
private val PillShape = SquirclePillShape()

/** A [GlassTone] as the player reads one. */
@Stable
private class GlassGlyphTone(private val tone: GlassTone) : PlayerGlyphTone {
    override val darkness: Float get() = tone.darkness

    override fun Modifier.glyphArea(): Modifier = glassToneArea(tone)
}

/**
 * [content], the player, with the app's glass lent to it while the glass is on
 * and sampling. Under "reduce dynamic blur" glass is filled solid, which the
 * player has no use for: its controls keep their flat discs then.
 */
@Composable
internal fun ProvidePlayerGlass(content: @Composable () -> Unit) {
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val glass = if (isLiquidGlassActive() && !reduceDynamicBlur) {
        val backdrop = rememberLayerBackdrop()
        remember(backdrop) { LiquidPlayerGlass(backdrop) }
    } else {
        null
    }
    CompositionLocalProvider(LocalPlayerGlass provides glass, content = content)
}
