/*
 * The glass rendering itself is Kyant0/backdrop (Apache-2.0), vendored at
 * [com.music.bitchord.ui.components.backdrop] — see that package for the
 * upstream attribution. This file is the integration glue, adapted from
 * EchoMusicApp/Echo-Music's GlassEffectConfig/Modifier.liquidGlass
 * (GPL-3.0), cut down from Echo's full per-component/vibrancy-slider config
 * to the single on/off switch BitChord exposes in Settings. It is used by the
 * floating nav bar and, while enabled, the app-wide floating top controls.
 */
package com.music.bitchord.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.LiquidGlassIntensity
import com.music.bitchord.ui.components.backdrop.Backdrop
import com.music.bitchord.ui.components.backdrop.backdrops.LayerBackdrop
import com.music.bitchord.ui.components.backdrop.drawBackdrop
import com.music.bitchord.ui.components.backdrop.effects.blur
import com.music.bitchord.ui.components.backdrop.effects.colorControls
import com.music.bitchord.ui.components.backdrop.effects.lens
import com.music.bitchord.ui.components.backdrop.highlight.Highlight
import com.music.bitchord.ui.components.backdrop.highlight.HighlightElement
import com.music.bitchord.ui.components.backdrop.internal.ShapeProvider
import com.music.bitchord.ui.components.backdrop.shadow.Shadow
import androidx.compose.ui.unit.dp

/** Whether the liquid glass nav bar is turned on — see [AppSettings.liquidGlass]. */
val LocalLiquidGlassEnabled = staticCompositionLocalOf { false }

/** The backdrop content (app UI) that a liquid glass surface samples from. */
val LocalAppBackdrop = staticCompositionLocalOf<Backdrop> { error("No AppBackdrop provided") }

/**
 * Where a [liquidGlass] surface composed under it also records the glass it
 * draws — the sampled, blurred and tinted backdrop, without its rim or shadow —
 * so a lens above it can refract that surface rather than the page behind it.
 * The tab bar's travelling selection pill is the one reader; see
 * [com.music.bitchord.ui.components.floatingtabbar.GlassSelectionPill].
 */
internal val LocalGlassExport = compositionLocalOf<LayerBackdrop?> { null }

/**
 * The backdrop blur pipeline requires [android.graphics.RenderEffect] on a
 * [android.graphics.RenderNode], available from Android 12 (API 31).
 */
fun isGlassSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt >= Build.VERSION_CODES.S

/** Apple-matched defaults (Echo's GlassEffectConfig()), fixed rather than user sliders. */
private const val VIBRANCY = 1f
private const val BLUR_RADIUS_DP = 8f
private const val LENS_HEIGHT = 0.5f
private const val LENS_AMOUNT = 0.5f
private const val LENS_MAX_DP = 48f
private const val SURFACE_OPACITY = 0.4f

/**
 * The user's say over the glass, ported from ArchiveTune's LiquidGlassTuning:
 * each control is a multiplier around the fixed values above, and every
 * default multiplies by exactly 1 — the stock look is one of the settings.
 *
 * [intensity] is the preset tier; the per-surface fractions are what the
 * Liquid Glass page's sliders write. [adaptiveLuminance] scales the surface
 * tint down (0.44 rather than 1) when off, the same ratio between the dimming
 * ArchiveTune draws with it on and off.
 */
data class LiquidGlassTuning(
    val intensity: LiquidGlassIntensity = LiquidGlassIntensity.STANDARD,
    val refractionHeightFraction: Float = AppSettings.DEFAULT_LIQUID_GLASS_REFRACTION_HEIGHT,
    val refractionAmountFraction: Float = AppSettings.DEFAULT_LIQUID_GLASS_REFRACTION_AMOUNT,
    val blurFraction: Float = AppSettings.DEFAULT_LIQUID_GLASS_BLUR_RADIUS,
    val tintFraction: Float = AppSettings.DEFAULT_LIQUID_GLASS_TINT_OPACITY,
    val shadowFraction: Float = AppSettings.DEFAULT_LIQUID_GLASS_SHADOW_DEPTH,
    val depth3D: Boolean = true,
    val chromaticAberration: Boolean = true,
    val backdropVibrancy: Boolean = true,
    val adaptiveLuminance: Boolean = true,
) {
    private val presetRefraction: Float =
        when (intensity) {
            LiquidGlassIntensity.SUBTLE -> 0.55f
            LiquidGlassIntensity.STANDARD -> 1f
            LiquidGlassIntensity.VIVID -> 1.45f
        }

    private val presetBlur: Float =
        when (intensity) {
            LiquidGlassIntensity.SUBTLE -> 0.70f
            LiquidGlassIntensity.STANDARD -> 1f
            LiquidGlassIntensity.VIVID -> 1.30f
        }

    val refractionHeightFactor: Float =
        (refractionHeightFraction / AppSettings.DEFAULT_LIQUID_GLASS_REFRACTION_HEIGHT) * presetRefraction

    val refractionAmountFactor: Float =
        (refractionAmountFraction / AppSettings.DEFAULT_LIQUID_GLASS_REFRACTION_AMOUNT) * presetRefraction

    val blurFactor: Float = (blurFraction / AppSettings.DEFAULT_LIQUID_GLASS_BLUR_RADIUS) * presetBlur

    val tintFactor: Float = tintFraction / AppSettings.DEFAULT_LIQUID_GLASS_TINT_OPACITY

    val shadowFactor: Float = shadowFraction / AppSettings.DEFAULT_LIQUID_GLASS_SHADOW_DEPTH

    /** 1 while vibrancy is on — the saturation the app has always drawn — and 0 off. */
    val vibrancyFactor: Float = if (backdropVibrancy) 1f else 0f

    /** The tint ArchiveTune draws with adaptive luminance on, relative to off. */
    val adaptiveLuminanceFactor: Float = if (adaptiveLuminance) 1f else 0.44f
}

/** Collects the user's Liquid Glass tuning from [AppSettings]. */
@Composable
internal fun rememberLiquidGlassTuning(): LiquidGlassTuning {
    val intensity by AppSettings.liquidGlassIntensity.collectAsStateWithLifecycle()
    val refractionHeight by AppSettings.liquidGlassRefractionHeight.collectAsStateWithLifecycle()
    val refractionAmount by AppSettings.liquidGlassRefractionAmount.collectAsStateWithLifecycle()
    val blurRadius by AppSettings.liquidGlassBlurRadius.collectAsStateWithLifecycle()
    val tintOpacity by AppSettings.liquidGlassTintOpacity.collectAsStateWithLifecycle()
    val shadowDepth by AppSettings.liquidGlassShadowDepth.collectAsStateWithLifecycle()
    val depth3D by AppSettings.liquidGlassDepth3D.collectAsStateWithLifecycle()
    val chromaticAberration by AppSettings.liquidGlassChromaticAberration.collectAsStateWithLifecycle()
    val backdropVibrancy by AppSettings.liquidGlassBackdropVibrancy.collectAsStateWithLifecycle()
    val adaptiveLuminance by AppSettings.liquidGlassAdaptiveLuminance.collectAsStateWithLifecycle()
    return remember(
        intensity,
        refractionHeight,
        refractionAmount,
        blurRadius,
        tintOpacity,
        shadowDepth,
        depth3D,
        chromaticAberration,
        backdropVibrancy,
        adaptiveLuminance,
    ) {
        LiquidGlassTuning(
            intensity = intensity,
            refractionHeightFraction = refractionHeight,
            refractionAmountFraction = refractionAmount,
            blurFraction = blurRadius,
            tintFraction = tintOpacity,
            shadowFraction = shadowDepth,
            depth3D = depth3D,
            chromaticAberration = chromaticAberration,
            backdropVibrancy = backdropVibrancy,
            adaptiveLuminance = adaptiveLuminance,
        )
    }
}

/**
 * Resolution fraction the glass surface records and processes its backdrop at.
 *
 * A third, which is what Echo's `glassResolutionScale` comes to at this blur
 * radius. It was briefly raised to a half while the sharp-text bug was being
 * chased — wrongly, as it turned out: the cause was a transparent backdrop, not
 * the resample (see MainActivity's `paintBackdrop`). A third is nine times fewer
 * pixels than full resolution through the colour matrix, the blur and the lens
 * shader, on as many as six surfaces at once in the middle of a fold, and the
 * blur is what hides the upscale.
 */
private const val GLASS_RESOLUTION_SCALE = 0.33f

/**
 * The blur and resolution under [AppSettings.glassPerformance]. With no lens to
 * bend what is behind it, the frost alone has to break up a page's text, so it
 * is twice the radius — and that wider blur hides a smaller recording, which
 * takes the surface from about 11% of its pixels to about 6%.
 */
private const val FROST_BLUR_RADIUS_DP = 16f
private const val FROST_RESOLUTION_SCALE = 0.25f

/**
 * The hairline along a bar's edge, and what stands in for the glass rim
 * wherever the glass itself is not drawn.
 *
 * A surface filled with the theme's own `surface` colour has no edge of its own
 * against a dark page — it is the same near-black the page is. The glass gets
 * its edge from [Highlight], and this is that edge for everything that does not.
 */
internal val GLASS_EDGE_WIDTH = 0.5.dp
internal val GLASS_EDGE_COLOR = Color.White.copy(alpha = 0.10f)

/**
 * Icon and label colour for content sitting on a glass surface.
 *
 * Glass shows whatever is behind it rather than the theme's surface colour, so
 * the usual onSurface greys have nothing dependable to sit against. Pure black
 * or white off the theme's luminance is the only tint that holds against
 * arbitrary artwork, and it is what Echo's own glass nav bar uses.
 */
@Composable
fun glassContentColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) Color.Black else Color.White

/**
 * Selected-tab indicator colour for a glass surface: white in both themes.
 *
 * In light theme that is the inverse of [glassContentColor], a bright lift
 * behind a dark glyph. In dark theme it is a faint white lift over the bar's
 * dark glass rather than a black scrim pressed into it — the selected tab is
 * raised out of the bar, as on iOS, not sunk.
 */
@Composable
fun glassIndicatorColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        Color.White.copy(alpha = 0.5f)
    } else {
        Color.White.copy(alpha = DARK_INDICATOR_OPACITY)
    }

/** The selected tab's pill in dark theme, a step whiter than the bar. */
private const val DARK_INDICATOR_OPACITY = 0.14f

/**
 * A lightweight visual match for liquid glass over a stable background.
 *
 * This keeps the same translucent tint, directional highlight and hairline as
 * [liquidGlass], but intentionally performs no backdrop capture, blur, lens
 * refraction or shadow rendering. When Liquid Glass is disabled or unsupported,
 * [fallbackColor] preserves the control's existing filled appearance.
 */
@Composable
fun Modifier.lightweightLiquidGlass(
    shape: CornerBasedShape,
    fallbackColor: Color,
): Modifier {
    val glassHeaderPills by AppSettings.glassHeaderPills.collectAsStateWithLifecycle()
    val useGlass = LocalLiquidGlassEnabled.current && glassHeaderPills && isGlassSupported()
    val glassTint = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        Color(0xFFFAFAFA)
    } else {
        Color(0xFF121212)
    }
    val shapeProvider = ShapeProvider { shape }

    return clip(shape)
        .background(
            color = if (useGlass) glassTint.copy(alpha = SURFACE_OPACITY) else fallbackColor,
            shape = shape,
        )
        .then(
            if (useGlass) {
                HighlightElement(
                    shapeProvider = shapeProvider,
                    highlight = { Highlight.Default },
                )
            } else {
                Modifier
            },
        )
        .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, shape)
}

/**
 * Just the rim of liquid glass — the directional [Highlight] that catches the
 * light along a glass edge — for a control that keeps its own fill.
 *
 * Over a flat, stable page the blur and lens of [liquidGlass] would sample a
 * single colour and change nothing a reader could see; the rim is the part
 * that reads as glass. No backdrop capture, so it costs a stroke. Nothing at
 * all while Liquid Glass is off or unsupported.
 */
@Composable
fun Modifier.liquidGlassRim(shape: CornerBasedShape): Modifier {
    val glassHeaderPills by AppSettings.glassHeaderPills.collectAsStateWithLifecycle()
    if (!LocalLiquidGlassEnabled.current || !glassHeaderPills || !isGlassSupported()) return this
    val shapeProvider = remember(shape) { ShapeProvider { shape } }
    return then(
        HighlightElement(
            shapeProvider = shapeProvider,
            highlight = { Highlight.Default },
        ),
    )
}

/**
 * Renders this composable as a liquid glass surface sampling [LocalAppBackdrop]:
 * vibrancy, blur and lens refraction, then a theme-adaptive surface tint (light
 * glass on light theme, dark on dark). Returns the receiver unchanged on devices
 * without RenderEffect support — callers should still gate on [isGlassSupported]
 * to fall back to the regular Haze treatment there.
 *
 * Under "reduce dynamic blur" the surface is filled solid instead, which is what
 * that setting promises everywhere else in the app. It is checked here rather
 * than at each call site so there is one answer to it: [MainActivity] also stops
 * recording the backdrop layer when it is on, and a surface that still tried to
 * sample would be sampling a layer nothing is drawing into.
 *
 * Under [AppSettings.glassPerformance] the lens goes and the blur frosts instead,
 * for phones that can't spare the refraction shader. The vibrancy, tint, rim and
 * shadow are the same either way.
 *
 * [shape] is restricted to [CornerBasedShape] because the backdrop's lens effect
 * throws for any other shape type.
 */
@Composable
fun Modifier.liquidGlass(
    shape: CornerBasedShape,
    /** Drawn over the glass in place of the theme's default tint, when set. */
    surfaceTint: Color? = null,
): Modifier {
    if (!isGlassSupported()) return this
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    if (reduceDynamicBlur) {
        return background(MaterialTheme.colorScheme.surface, shape)
            .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, shape)
    }
    val glassPerformance by AppSettings.glassPerformance.collectAsStateWithLifecycle()
    val tuning = rememberLiquidGlassTuning()
    val backdrop = LocalAppBackdrop.current
    val exportedBackdrop = LocalGlassExport.current
    val density = LocalDensity.current
    val resolutionScale = if (glassPerformance) FROST_RESOLUTION_SCALE else GLASS_RESOLUTION_SCALE
    val blurRadiusDp = (if (glassPerformance) FROST_BLUR_RADIUS_DP else BLUR_RADIUS_DP) * tuning.blurFactor
    val blurPx = with(density) { blurRadiusDp.dp.toPx() } * resolutionScale
    val lensHeightPx = with(density) { (LENS_HEIGHT * LENS_MAX_DP * tuning.refractionHeightFactor).dp.toPx() } * resolutionScale
    val lensAmountPx = with(density) { (LENS_AMOUNT * LENS_MAX_DP * tuning.refractionAmountFactor).dp.toPx() } * resolutionScale
    val themeTint = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        Color(0xFFFAFAFA)
    } else {
        Color(0xFF121212)
    }
    val surfaceTintColor = surfaceTint
        ?: themeTint.copy(alpha = (SURFACE_OPACITY * tuning.tintFactor * tuning.adaptiveLuminanceFactor).coerceIn(0f, 1f))

    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            colorControls(saturation = 1f + 0.5f * VIBRANCY * tuning.vibrancyFactor)
            blur(blurPx)
            if (!glassPerformance && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                lens(
                    refractionHeight = lensHeightPx,
                    refractionAmount = lensAmountPx,
                    depthEffect = tuning.depth3D,
                    chromaticAberration = tuning.chromaticAberration,
                )
            }
        },
        highlight = { Highlight.Default },
        shadow = { Shadow.Default.copy(radius = Shadow.Default.radius * tuning.shadowFactor) },
        onDrawSurface = {
            drawRect(color = surfaceTintColor, size = size)
        },
        exportedBackdrop = exportedBackdrop,
        backdropScale = resolutionScale,
    )
}

// ---- Glass glow, ported from ArchiveTune's CompactBottomControls ----

/**
 * The Nuvio-style rim light and soft sheen ArchiveTune lays over its navigation
 * bar and compact control circles: a thin bright stroke around the shape's
 * outline, a wash of light down from the top edge, and a line of it gathered
 * along the bottom — three passes of the same [shape], each scaled by
 * [strength] so the setting's intensity slider can breathe through them.
 *
 * Pure draw-time: it samples nothing and costs no layers, so it reads the same
 * over the glass bar, the frosted bar and the plain surface alike.
 */
fun Modifier.glassGlowOverlay(
    strength: Float,
    shape: Shape,
): Modifier = drawWithContent {
    if (strength <= 0.01f) {
        drawContent()
        return@drawWithContent
    }
    drawContent()
    val outline = shape.createOutline(size, layoutDirection, this)
    val strokePx = GlowEdgeStrokeWidth.toPx()
    val halfStroke = strokePx / 2f
    val insetSize = Size(size.width - strokePx, size.height - strokePx)
    val insetOutline = shape.createOutline(insetSize, layoutDirection, this)

    // The rim: brightest at the top, gone by the bottom. Drawn into the shape
    // inset by half the stroke so the line's own width stays inside the edge.
    drawOutline(
        outline = insetOutline,
        brush = Brush.verticalGradient(
            0f to GlowRimStart,
            1f to GlowRimEnd,
            startY = halfStroke,
            endY = size.height - halfStroke,
        ),
        topLeft = Offset(halfStroke, halfStroke),
        alpha = strength,
        style = Stroke(strokePx),
    )
    // The sheen: a soft fill of light from the top edge, out by nearly half.
    drawOutline(
        outline = outline,
        brush = Brush.verticalGradient(
            0f to GlowSheenTop,
            0.45f to GlowSheenMid,
            1f to GlowSheenEnd,
        ),
        alpha = strength,
        style = Fill,
    )
    // The bottom edge: light gathered along the foot, dimmer than the rim.
    drawOutline(
        outline = outline,
        brush = Brush.verticalGradient(
            0.72f to Color.Transparent,
            1f to GlowBottomEnd,
        ),
        alpha = strength * 0.7f,
        style = Fill,
    )
}

/** The rim's width — a hairline, like the glass edge it sits beside. */
private val GlowEdgeStrokeWidth = 0.75.dp

private val GlowRimStart = Color.White.copy(alpha = 0.27f)
private val GlowRimEnd = Color.White.copy(alpha = 0.02f)
private val GlowSheenTop = Color.White.copy(alpha = 0.10f)
private val GlowSheenMid = Color.Transparent
private val GlowSheenEnd = Color.White.copy(alpha = 0.05f)
private val GlowBottomEnd = Color.White.copy(alpha = 0.14f)
