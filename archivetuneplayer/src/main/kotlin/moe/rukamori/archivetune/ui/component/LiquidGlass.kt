/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Liquid glass / backdrop blur effect, ported from SimpMusic
 * (https://github.com/maxrave-dev/SimpMusic) and simplified for the
 * Android-only ArchiveTune build. The original KMP expect/actual
 * pattern is collapsed into a single file because ArchiveTune does
 * not have a JVM/iOS target.
 */

package moe.rukamori.archivetune.ui.component

import android.os.SystemClock
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton as Material3IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toIntSize
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop as kyantLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow
import moe.rukamori.archivetune.constants.LIQUID_GLASS_ADAPTIVE_LUMINANCE_DEFAULT
import moe.rukamori.archivetune.constants.LIQUID_GLASS_BACKDROP_VIBRANCY_DEFAULT
import moe.rukamori.archivetune.constants.LIQUID_GLASS_BLUR_RADIUS_DEFAULT
import moe.rukamori.archivetune.constants.LIQUID_GLASS_CHROMATIC_ABERRATION_DEFAULT
import moe.rukamori.archivetune.constants.LIQUID_GLASS_DEPTH_3D_DEFAULT
import moe.rukamori.archivetune.constants.LIQUID_GLASS_REFRACTION_AMOUNT_DEFAULT
import moe.rukamori.archivetune.constants.LIQUID_GLASS_REFRACTION_HEIGHT_DEFAULT
import moe.rukamori.archivetune.constants.LIQUID_GLASS_SHADOW_DEPTH_DEFAULT
import moe.rukamori.archivetune.constants.LIQUID_GLASS_TINT_OPACITY_DEFAULT
import moe.rukamori.archivetune.constants.LiquidGlassChromaticAberrationKey
import moe.rukamori.archivetune.constants.LiquidGlassAdaptiveLuminanceKey
import moe.rukamori.archivetune.constants.LiquidGlassBackdropVibrancyKey
import moe.rukamori.archivetune.constants.LiquidGlassBlurRadiusKey
import moe.rukamori.archivetune.constants.LiquidGlassDepth3DKey
import moe.rukamori.archivetune.constants.LiquidGlassIntensity
import moe.rukamori.archivetune.constants.LiquidGlassIntensityKey
import moe.rukamori.archivetune.constants.LiquidGlassRefractionAmountKey
import moe.rukamori.archivetune.constants.LiquidGlassRefractionHeightKey
import moe.rukamori.archivetune.constants.LiquidGlassShadowDepthKey
import moe.rukamori.archivetune.constants.LiquidGlassTintOpacityKey
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.ui.player.LocalPlayerSheetOverlayFraction
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

typealias PlatformBackdrop = ThrottledLayerBackdrop

@Composable
fun rememberLayerBackdropSettled(@Suppress("UNUSED_PARAMETER") delayMillis: Long = 0L): Boolean = true

@Composable
fun rememberBackdrop(color: Color): PlatformBackdrop =

    rememberThrottledBackdrop(color)

@Composable
fun rememberThrottledBackdrop(
    color: Color,
    minIntervalMillis: Long = ThrottledLayerBackdropDefaultIntervalMillis,
): ThrottledLayerBackdrop {
    val graphicsLayer = rememberGraphicsLayer()
    val backdrop = remember(graphicsLayer, minIntervalMillis, color) {
        ThrottledLayerBackdrop(
            graphicsLayer = graphicsLayer,
            minIntervalMillis = minIntervalMillis,
            contentPrefix = { drawRect(color) },
        )
    }
    DisposableEffect(backdrop) {
        onDispose { backdrop.layerCoordinates = null }
    }
    return backdrop
}

fun Modifier.layerBackdrop(backdrop: Backdrop): Modifier =
    when (backdrop) {
        is ThrottledLayerBackdrop -> throttledLayerBackdrop(backdrop)

        is LayerBackdrop -> this.kyantLayerBackdrop(backdrop)
        else -> this
    }

fun Modifier.glassSource(backdrop: Backdrop): Modifier =
    when (backdrop) {
        is ThrottledLayerBackdrop -> throttledLayerBackdrop(backdrop)
        is LayerBackdrop -> layerBackdrop(backdrop)
        else -> this
    }

val LocalLiquidGlassBackdrop = compositionLocalOf<Backdrop?> { null }

val LocalMenuGlassBackdrop = compositionLocalOf<Backdrop?> { null }

@Stable
data class LiquidGlassTuning(
    val intensity: LiquidGlassIntensity = LiquidGlassIntensity.STANDARD,
    val refractionHeightFraction: Float = LIQUID_GLASS_REFRACTION_HEIGHT_DEFAULT,
    val refractionAmountFraction: Float = LIQUID_GLASS_REFRACTION_AMOUNT_DEFAULT,
    val blurFraction: Float = LIQUID_GLASS_BLUR_RADIUS_DEFAULT,
    val tintFraction: Float = LIQUID_GLASS_TINT_OPACITY_DEFAULT,
    val shadowFraction: Float = LIQUID_GLASS_SHADOW_DEPTH_DEFAULT,
    val depth3D: Boolean = LIQUID_GLASS_DEPTH_3D_DEFAULT,
    val chromaticAberration: Boolean = LIQUID_GLASS_CHROMATIC_ABERRATION_DEFAULT,
    val backdropVibrancy: Boolean = LIQUID_GLASS_BACKDROP_VIBRANCY_DEFAULT,
    val adaptiveLuminance: Boolean = LIQUID_GLASS_ADAPTIVE_LUMINANCE_DEFAULT,
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
        (refractionHeightFraction / LIQUID_GLASS_REFRACTION_HEIGHT_DEFAULT) * presetRefraction

    val refractionAmountFactor: Float =
        (refractionAmountFraction / LIQUID_GLASS_REFRACTION_AMOUNT_DEFAULT) * presetRefraction

    val blurFactor: Float = (blurFraction / LIQUID_GLASS_BLUR_RADIUS_DEFAULT) * presetBlur

    val tintFactor: Float = tintFraction / LIQUID_GLASS_TINT_OPACITY_DEFAULT

    val shadowFactor: Float = shadowFraction / LIQUID_GLASS_SHADOW_DEPTH_DEFAULT

    val saturation: Float = if (backdropVibrancy) 1.7f else 1.0f

    companion object {
        val STOCK = LiquidGlassTuning()
    }
}

val LocalLiquidGlassTuning = compositionLocalOf { LiquidGlassTuning.STOCK }

@Composable
fun rememberLiquidGlassTuning(): LiquidGlassTuning {
    val intensity by rememberEnumPreference(LiquidGlassIntensityKey, LiquidGlassIntensity.STANDARD)
    val refractionHeight by rememberPreference(LiquidGlassRefractionHeightKey, LIQUID_GLASS_REFRACTION_HEIGHT_DEFAULT)
    val refractionAmount by rememberPreference(LiquidGlassRefractionAmountKey, LIQUID_GLASS_REFRACTION_AMOUNT_DEFAULT)
    val blurRadius by rememberPreference(LiquidGlassBlurRadiusKey, LIQUID_GLASS_BLUR_RADIUS_DEFAULT)
    val tintOpacity by rememberPreference(LiquidGlassTintOpacityKey, LIQUID_GLASS_TINT_OPACITY_DEFAULT)
    val shadowDepth by rememberPreference(LiquidGlassShadowDepthKey, LIQUID_GLASS_SHADOW_DEPTH_DEFAULT)
    val depth3D by rememberPreference(LiquidGlassDepth3DKey, LIQUID_GLASS_DEPTH_3D_DEFAULT)
    val chromaticAberration by rememberPreference(
        LiquidGlassChromaticAberrationKey,
        LIQUID_GLASS_CHROMATIC_ABERRATION_DEFAULT,
    )
    val backdropVibrancy by rememberPreference(LiquidGlassBackdropVibrancyKey, LIQUID_GLASS_BACKDROP_VIBRANCY_DEFAULT)
    val adaptiveLuminance by rememberPreference(
        LiquidGlassAdaptiveLuminanceKey,
        LIQUID_GLASS_ADAPTIVE_LUMINANCE_DEFAULT,
    )
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

internal const val ThrottledLayerBackdropDefaultIntervalMillis = 32L

private val liveRecorderBackdrops =
    java.util.Collections.synchronizedMap(
        java.util.WeakHashMap<ThrottledLayerBackdrop, Boolean>(),
    )

@Stable
class ThrottledLayerBackdrop internal constructor(
    val graphicsLayer: GraphicsLayer,
    internal val minIntervalMillis: Long,
    internal val contentPrefix: DrawScope.() -> Unit = {},
) : Backdrop {
    override val isCoordinatesDependent: Boolean get() = true

    internal var layerCoordinates: LayoutCoordinates? by mutableStateOf(null)

    internal var consumerInvalidationTick by mutableStateOf(0)

    internal var recordingInProgress by mutableStateOf(false)

    internal var recordTick by mutableStateOf(0)

    fun requestRecord() {
        recordTick++
    }

    internal fun notifyRecorderAttached() {
        consumerInvalidationTick++
    }

    fun notifyContentRestore() {
        consumerInvalidationTick++
        notifyAllContentRestored()
    }

    private companion object {
        fun notifyAllContentRestored() {
            val snapshots: List<ThrottledLayerBackdrop>
            synchronized(liveRecorderBackdrops) {
                snapshots = liveRecorderBackdrops.keys.toList()
            }
            snapshots.forEach { it.consumerInvalidationTick++ }
        }
    }

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        @Suppress("UNUSED_VARIABLE") val tick = consumerInvalidationTick
        if (recordingInProgress) return
        val coordinates = coordinates ?: return
        val layerCoordinates = layerCoordinates ?: return

        if (!layerCoordinates.isAttached || !coordinates.isAttached) return
        val offset =
            try {
                layerCoordinates.localPositionOf(coordinates)
            } catch (_: Exception) {
                runCatching {
                    coordinates.positionInWindow() - layerCoordinates.positionInWindow()
                }.getOrNull()
            } ?: return
        withTransform({
            translate(-offset.x, -offset.y)
        }) {
            runCatching { drawLayer(graphicsLayer) }
        }
    }
}

@Composable
fun rememberThrottledLayerBackdrop(
    graphicsLayer: GraphicsLayer = rememberGraphicsLayer(),
    minIntervalMillis: Long = ThrottledLayerBackdropDefaultIntervalMillis,
): ThrottledLayerBackdrop = remember(graphicsLayer) {
    ThrottledLayerBackdrop(graphicsLayer, minIntervalMillis)
}

fun Modifier.throttledLayerBackdrop(backdrop: ThrottledLayerBackdrop): Modifier =
    this then ThrottledLayerBackdropElement(backdrop)

private class ThrottledLayerBackdropElement(
    val backdrop: ThrottledLayerBackdrop,
) : ModifierNodeElement<ThrottledLayerBackdropNode>() {
    override fun create() = ThrottledLayerBackdropNode(backdrop)

    override fun update(node: ThrottledLayerBackdropNode) {
        if (node.backdrop !== backdrop) {
            node.backdrop.layerCoordinates = null
            node.backdrop = backdrop
        }
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "throttledLayerBackdrop"
        properties["backdrop"] = backdrop
        properties["minIntervalMillis"] = backdrop.minIntervalMillis
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ThrottledLayerBackdropElement) return false
        return backdrop === other.backdrop
    }

    override fun hashCode(): Int = backdrop.hashCode()
}

private class ThrottledLayerBackdropNode(
    var backdrop: ThrottledLayerBackdrop,
) : DrawModifierNode, GlobalPositionAwareModifierNode, Modifier.Node() {
    private var lastRecordUptimeMillis = 0L

    override fun onAttach() {
        super.onAttach()
        liveRecorderBackdrops[backdrop] = true
        lastRecordUptimeMillis = 0L
        backdrop.notifyRecorderAttached()
    }

    override fun ContentDrawScope.draw() {

        @Suppress("UNUSED_VARIABLE") val tick = backdrop.recordTick
        val now = SystemClock.uptimeMillis()
        if (now - lastRecordUptimeMillis >= backdrop.minIntervalMillis) {
            lastRecordUptimeMillis = now
            val density = requireDensity()

            val recorded =
                runCatching {
                    backdrop.recordingInProgress = true
                    try {
                        backdrop.graphicsLayer.record(size.toIntSize()) {
                            val previousDensity = drawContext.density
                            drawContext.density = density
                            try {
                                backdrop.contentPrefix(this@draw)
                                this@draw.drawContent()
                            } finally {
                                drawContext.density = previousDensity
                            }
                        }
                    } finally {
                        backdrop.recordingInProgress = false
                    }
                }.isSuccess
            if (recorded && backdrop.graphicsLayer.size == size.toIntSize()) {
                runCatching { drawLayer(backdrop.graphicsLayer) }
                return
            }
        }
        drawContent()
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (coordinates.isAttached) {
            backdrop.layerCoordinates = coordinates
        }
    }

    override fun onDetach() {
        liveRecorderBackdrops.remove(backdrop)

        if (backdrop.layerCoordinates?.isAttached == false) {
            backdrop.layerCoordinates = null
            backdrop.consumerInvalidationTick++
        }
    }
}

val LiquidGlassPillBlurRadius = 18.dp

private val LiquidGlassLightContentColor = Color(0xFF1C1B1F)

@Composable
fun liquidGlassContentColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color.White else LiquidGlassLightContentColor

@Composable
fun Modifier.liquidGlass(
    backdrop: Backdrop,
    shape: Shape = CircleShape,
    interactive: Boolean = true,
    baseColor: Color = Color.Unspecified,
    blurRadius: Dp = 8.dp,
    scrim: Color? = null,
): Modifier {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val tuning = LocalLiquidGlassTuning.current

    return remember(backdrop, shape, interactive, baseColor, blurRadius, isDark, scrim, tuning) {

        val lensScale = if (isDark) 1f else 0.5f
        val lensAmountScale = if (isDark) 1f else 0.6f
        this.drawBackdrop(
            backdrop = backdrop,
            effects = {
                val l = 0f

                colorControls(saturation = tuning.saturation)
                blur(
                    if (l > 0f) {
                        lerp(blurRadius.toPx() * 2f, blurRadius.toPx() * 4f, l)
                    } else {
                        blurRadius.toPx() * tuning.blurFactor
                    },
                )

                lens(
                    refractionHeight = minOf(
                        (28f * lensScale * tuning.refractionHeightFactor).dp.toPx(),
                        size.minDimension / 2f,
                    ),
                    refractionAmount = size.minDimension * (tuning.refractionAmountFactor / 3.2f) * lensAmountScale,
                    depthEffect = tuning.depth3D && isDark,
                    chromaticAberration = tuning.chromaticAberration && isDark,
                )
            },

            shadow = { if (isDark) Shadow.Default else null },
            onDrawBackdrop = { drawBackdrop ->
                drawBackdrop()
            },
            shape = { shape },
            onDrawBehind =
                if (baseColor != Color.Unspecified) {

                    {
                        drawOutline(
                            outline = shape.createOutline(size, layoutDirection, this),
                            color = baseColor,
                        )
                    }
                } else {
                    null
                },
            onDrawSurface = {

                val surfaceOutline = shape.createOutline(size, layoutDirection, this)
                if (scrim != null) {
                    drawOutline(
                        outline = surfaceOutline,
                        color = scrim.copy(alpha = (scrim.alpha * tuning.tintFactor).coerceIn(0f, 1f)),
                    )
                } else {
                    val darken =
                        if (tuning.adaptiveLuminance) {
                            val luminanceAnimation = 0.5f
                            lerp(
                                0.12f,
                                0.5f,
                                ((luminanceAnimation - 0.3f) / 0.5f).coerceIn(0f, 1f),
                            )
                        } else {
                            0.12f
                        }
                    drawOutline(
                        outline = surfaceOutline,
                        color =
                            (if (isDark) Color.Black else Color.White)
                                .copy(alpha = (darken * tuning.tintFactor).coerceIn(0f, 1f)),
                    )
                }
            },
        )
    }
}

@Composable
fun LiquidGlassContainer(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    interactive: Boolean = false,
    blurRadius: Dp = LiquidGlassPillBlurRadius,
    scrim: Color? = null,
    baseColor: Color = Color.Unspecified,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier =
            modifier.liquidGlass(
                backdrop,
                shape,
                interactive,
                baseColor = baseColor,
                blurRadius = blurRadius,
                scrim = scrim,
            ),
        contentAlignment = contentAlignment,
        content = content,
    )
}

@Composable
fun LiquidGlassActionPill(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    interactive: Boolean = false,
    blurRadius: Dp = LiquidGlassPillBlurRadius,
    scrim: Color? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val sheetOverlayFraction = LocalPlayerSheetOverlayFraction.current
    Row(
        modifier =
            modifier
                .graphicsLayer { alpha = 1f - sheetOverlayFraction.value }
                .height(48.dp)
                .liquidGlass(
                    backdrop = backdrop,
                    shape = RoundedCornerShape(24.dp),
                    interactive = interactive,
                    blurRadius = blurRadius,
                    scrim = scrim,
                ),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

private const val GlassPillTitleMaxWidthFraction = 0.42f

@Composable
fun GlassPillTitleText(
    text: String,
    modifier: Modifier = Modifier,
) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    Text(
        text = text,
        color = liquidGlassContentColor(),
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier =
            modifier
                .widthIn(max = (screenWidthDp * GlassPillTitleMaxWidthFraction).dp)
                .basicMarquee()
                .padding(end = 12.dp),
    )
}

@Composable
fun LiquidGlassIconButton(
    backdrop: Backdrop,
    painter: Painter,
    modifier: Modifier = Modifier.size(48.dp),
    shape: Shape = CircleShape,

    tint: Color = Color.Unspecified,
    contentDescription: String? = null,
    interactive: Boolean = false,
    onClick: () -> Unit,
) {
    val resolvedTint = if (tint == Color.Unspecified) liquidGlassContentColor() else tint

    val glassBase = MaterialTheme.colorScheme.surfaceContainerHigh
    LiquidGlassContainer(
        backdrop = backdrop,
        modifier = modifier,
        shape = shape,
        interactive = interactive,
        baseColor = glassBase,
    ) {
        Material3IconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                painter = painter,
                contentDescription = contentDescription,
                tint = resolvedTint,
            )
        }
    }
}

@Composable
fun LiquidGlassIconButton(
    backdrop: Backdrop,
    imageVector: ImageVector,
    modifier: Modifier = Modifier.size(48.dp),
    shape: Shape = CircleShape,

    tint: Color = Color.Unspecified,
    contentDescription: String? = null,
    interactive: Boolean = false,
    onClick: () -> Unit,
) {
    val resolvedTint = if (tint == Color.Unspecified) liquidGlassContentColor() else tint

    val glassBase = MaterialTheme.colorScheme.surfaceContainerHigh
    LiquidGlassContainer(
        backdrop = backdrop,
        modifier = modifier,
        shape = shape,
        interactive = interactive,
        baseColor = glassBase,
    ) {
        Material3IconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = resolvedTint,
            )
        }
    }
}

@Composable
fun GlassPipelinePrewarm(
    backdrop: Backdrop?,
    active: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    if (!active || backdrop == null) return
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(1.dp)

                .graphicsLayer { alpha = 0.02f }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { androidx.compose.ui.graphics.RectangleShape },
                    effects = {
                        vibrancy()
                        blur(32.dp.toPx())
                    },
                ),
    ) {
        content()
    }
}
