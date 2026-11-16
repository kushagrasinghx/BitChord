package com.music.bitchord.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSupported
import androidx.compose.ui.graphics.layer.drawLayer

/**
 * Whether the player's glyphs and secondary text are blended into the backdrop
 * rather than laid over it. Off wherever it isn't provided, and while a music
 * video fills the landscape player: the video is free to go bright under the controls.
 */
internal val LocalPlayerChrome = compositionLocalOf { false }

/**
 * How strong a control's "full" white is while the blend is on. Solid white comes
 * out of [additiveChrome] as solid white, with none of the backdrop in it.
 */
internal const val CHROME_FULL_ALPHA = 0.73f

/** How far below its plain strength blended content is drawn, so more of the backdrop comes through. */
private const val CHROME_ALPHA_DROP = 0.15f

/** White at [alpha], or that less [CHROME_ALPHA_DROP] while the blend is on. */
@Composable
@ReadOnlyComposable
internal fun chromeWhite(alpha: Float): Color =
    Color.White.copy(alpha = if (LocalPlayerChrome.current) alpha - CHROME_ALPHA_DROP else alpha)

/**
 * What [vibrantChrome] content is drawn in: a neutral ink, so the hue is all the backdrop's.
 * Its grey sets the dodge's gain, 1 / (1 - grey): 0x99 is about 2.5x. At 0xB4 (3.4x) an
 * already bright or saturated patch of artwork clipped to a flat, loud colour.
 */
internal val VibrantChromeInk = Color(0xFF999999).copy(alpha = 1f - CHROME_ALPHA_DROP)

/**
 * Dodge multiplies, and nothing times black is still black. Laid under the dodge
 * so a glyph on a pure black backdrop is lifted with it rather than vanishing.
 */
private const val VIBRANT_CHROME_FLOOR = 0.12f

/**
 * Adds the content onto the backdrop, so the artwork's colour comes through the
 * glyph as a tinted white instead of the glyph covering it.
 *
 * The blend only reaches what is drawn in the same layer, so this goes outside
 * anything that fades or masks its content in a layer of its own. Solid white
 * stays solid white under it: the effect is in content drawn below full strength.
 */
fun Modifier.additiveChrome(enabled: Boolean): Modifier =
    if (!enabled) {
        this
    } else {
        drawWithCache {
            val layer = obtainGraphicsLayer().apply { blendMode = BlendMode.Plus }
            onDrawWithContent {
                layer.record { this@onDrawWithContent.drawContent() }
                drawLayer(layer)
            }
        }
    }

/**
 * Colour-dodges content drawn in [VibrantChromeInk], about 2.5x the backdrop, so
 * the artwork's colour comes back brighter and more saturated rather than paler.
 * Where the platform has no dodge the ink is added instead, as [additiveChrome].
 */
internal fun Modifier.vibrantChrome(enabled: Boolean): Modifier =
    if (!enabled || !BlendMode.ColorDodge.isSupported()) {
        additiveChrome(enabled)
    } else {
        drawWithCache {
            val floor = obtainGraphicsLayer().apply {
                blendMode = BlendMode.Plus
                alpha = VIBRANT_CHROME_FLOOR
            }
            val dodge = obtainGraphicsLayer().apply { blendMode = BlendMode.ColorDodge }
            onDrawWithContent {
                floor.record { this@onDrawWithContent.drawContent() }
                dodge.record { this@onDrawWithContent.drawContent() }
                drawLayer(floor)
                drawLayer(dodge)
            }
        }
    }
