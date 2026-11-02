package com.music.bitchord.ui.components

import android.provider.Settings
import com.music.bitchord.ui.player.PlayerBackHandler
import com.music.bitchord.ui.utils.containSheetGestures
import com.music.bitchord.ui.components.backdrop.Backdrop
import com.music.bitchord.ui.components.backdrop.drawBackdrop
import com.music.bitchord.ui.components.backdrop.effects.blur
import com.music.bitchord.ui.components.backdrop.effects.colorControls
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.components.backdrop.backdrops.SnapshotBackdrop
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * The colours of an iOS-style menu panel: a charcoal or frost slab with its
 * own label greys, rather than the theme's surface roles — the panel floats
 * over artwork as often as over a page, and the theme's containers were tuned
 * for neither.
 */
@Immutable
data class IosMenuColors(
    val dark: Boolean,
    val fill: Color,
    val rim: Color,
    val label: Color,
    val secondaryLabel: Color,
    val separator: Color,
    /** The rounded fill under the row a finger is on. */
    val pressed: Color,
)

private val DarkMenu = IosMenuColors(
    dark = true,
    fill = Color(0xFF1C1C1E),
    rim = Color.White.copy(alpha = 0.10f),
    label = Color.White,
    secondaryLabel = Color.White.copy(alpha = 0.70f),
    separator = Color.White.copy(alpha = 0.10f),
    pressed = Color.White.copy(alpha = 0.10f),
)

private val LightMenu = IosMenuColors(
    dark = false,
    fill = Color(0xFFF6F6F7),
    rim = Color.Black.copy(alpha = 0.06f),
    label = Color(0xFF1A1A1A),
    secondaryLabel = Color(0xFF1A1A1A).copy(alpha = 0.55f),
    separator = Color.Black.copy(alpha = 0.09f),
    pressed = Color.Black.copy(alpha = 0.07f),
)

/** The panel colours for the theme in force. */
@Composable
fun iosMenuColors(): IosMenuColors =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) DarkMenu else LightMenu

/** The panel's silhouette: a large, continuously curved corner. */
val IosMenuShape: Shape = RoundedCornerShape(30.dp).continuous()

/** [IosMenuShape]'s plain rounded-rect twin, for a shadow drawn while it scales. */
private val IosMenuShadowShape: Shape = RoundedCornerShape(30.dp)

/** Wide enough for "Remove from listening history" on one line or two. */
val IosMenuWidth = 256.dp

/**
 * Shadow, slab and rim — the menu's material, without its contents.
 *
 * [translucent] lets what is behind show through the slab, as iOS menus do.
 * Something behind has to be blurred for that to read as frost rather than as
 * a page seen through tinted glass, so it is only translucent when it can blur
 * what is under it, under the slab only and for as long as the panel is up:
 * [backdrop] — the player recorded live, blurred with the page's hue drawn up —
 * or else [hazeState], the page's own haze, which every page is a source of.
 *
 * "Reduce dynamic blur" turns the frost off and the slab opaque: an unblurred
 * page seen through it is exactly what the frost was there to prevent.
 */
@Composable
fun Modifier.iosMenuPanel(
    colors: IosMenuColors = iosMenuColors(),
    translucent: Boolean = false,
    backdrop: Backdrop? = null,
    hazeState: HazeState? = null,
    /** Paints the slab in this instead, as the held-item menu does to match its item's plate. */
    fillOverride: Color? = null,
    rimOverride: Color? = null,
    /** False where the caller casts [iosMenuShadow] itself, apart from the panel. */
    castsShadow: Boolean = true,
): Modifier {
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val blurs = translucent && !reduceDynamicBlur
    val backdropFrost = blurs && backdrop != null && isGlassSupported()
    val hazeFrost = blurs && !backdropFrost && hazeState != null
    val fill = fillOverride ?: if (backdropFrost || hazeFrost) {
        colors.fill.copy(alpha = if (colors.dark) 0.80f else 0.72f)
    } else {
        colors.fill
    }
    val shadowed = if (castsShadow) iosMenuShadow() else this
    val surface = if (hazeFrost) {
        val style = remember(colors, fill) { menuFrost(colors.fill, alpha = fill.alpha) }
        shadowed.clip(IosMenuShape).optimizedHazeEffect(state = hazeState, style = style)
    } else if (backdropFrost) {
        shadowed.menuBackdropFrost(backdrop, IosMenuShape, fill)
    } else {
        shadowed.clip(IosMenuShape).background(fill)
    }
    return surface
        .clip(IosMenuShape)
        .border(0.5.dp, rimOverride ?: colors.rim, IosMenuShape)
}

/** The shadow the menu panel casts. */
fun Modifier.iosMenuShadow(shape: Shape = IosMenuShape): Modifier = shadow(
    elevation = 24.dp,
    shape = shape,
    ambientColor = Color.Black.copy(alpha = 0.30f),
    spotColor = Color.Black.copy(alpha = 0.30f),
)

/**
 * The menu panel's frost drawn from a recorded [backdrop], in [shape] under
 * [fill] — the panel's own, and the player's drawers' and popups', so the two
 * are one material (see [com.music.bitchord.ui.player.PlayerFrost]).
 */
fun Modifier.menuBackdropFrost(backdrop: Backdrop, shape: Shape, fill: Color): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
        // iOS menus carry the page's hue through more strongly than the page
        // has it.
        colorControls(saturation = 1.8f)
        blur(MENU_BLUR.toPx() * MENU_BACKDROP_SCALE)
    },
    highlight = null,
    shadow = null,
    onDrawSurface = { drawRect(fill) },
    // Sampled small: the blur hides the upscale, and the effect then runs over
    // a fraction of the pixels.
    backdropScale = MENU_BACKDROP_SCALE,
)

private val MENU_BLUR = 24.dp
private const val MENU_BACKDROP_SCALE = 0.25f

/** The longest a menu waits for its frost's still before growing without it. */
private const val STILL_WAIT_MS = 150L

/** The break between groups: a hairline inset from the panel's edges, with air either side. */
@Composable
fun IosMenuSeparator(colors: IosMenuColors = iosMenuColors()) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(17.dp)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.separator),
        )
    }
}

/** The tiles across the top of a menu: the actions reached for most, side by side. */
@Composable
fun IosMenuQuickActions(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(66.dp)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

/** One tile of [IosMenuQuickActions]: the glyph over a short label. */
@Composable
fun RowScope.IosMenuQuickAction(
    icon: ImageVector,
    label: String,
    tint: Color? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = iosMenuColors()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val color = if (enabled) colors.label else colors.secondaryLabel
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .background(if (pressed) colors.pressed else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint ?: color,
            modifier = Modifier.size(23.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = color,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One line of an iOS-style menu: the glyph leading in a fixed column, so the
 * labels line up whether a row has one or not, then the label with an
 * optional dimmer line under it, and an optional value at the far end.
 *
 * Pressing paints a rounded fill under the row rather than a ripple, the way
 * the panel's own platform does it.
 */
@Composable
fun IosMenuRow(
    label: String,
    icon: ImageVector?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    description: String? = null,
    value: String? = null,
    iconTint: Color? = null,
    labelColor: Color? = null,
    valueColor: Color? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = iosMenuColors()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val alpha = if (enabled) 1f else 0.4f
    val foreground = labelColor ?: colors.label
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (pressed) colors.pressed else Color.Transparent)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        enabled = enabled,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .heightIn(min = 44.dp)
            .padding(start = 12.dp, end = 14.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = (iconTint ?: foreground).copy(alpha = alpha),
                    modifier = Modifier.size(21.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = label,
                color = foreground.copy(alpha = alpha),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 17.sp,
                    lineHeight = 21.sp,
                    letterSpacing = (-0.3).sp,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (description != null) {
                Text(
                    text = description,
                    color = colors.secondaryLabel.copy(alpha = colors.secondaryLabel.alpha * alpha),
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (value != null) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = value,
                color = (valueColor ?: colors.secondaryLabel).copy(alpha = alpha),
                fontSize = 15.sp,
                maxLines = 1,
            )
        }
        trailing?.invoke()
    }
}

/**
 * A menu hung off a button rather than lifted out of a list — every ⋮ and ⋯ in
 * the app opens its menu this way. The iOS panel lines its trailing edge up
 * with [anchor]'s and grows out of the corner nearest the button: for one in
 * the lower half of the screen, as the player's ⋯ is, it settles at the bottom
 * and grows from its bottom corner; for one higher up, a row's ⋮ near the top
 * of a list, it hangs down from the button's top and grows from its top
 * corner. With no anchor it takes the bottom end.
 *
 * Drawn in place, over whatever it is composed on top of — the player puts it
 * inside its own sheet, so [backdrop], the player recorded live, can be
 * blurred under the panel; over a page, [hazeState] is the page's haze and
 * does the same (see [iosMenuPanel]). Both of the app's accessibility
 * switches hold: "Reduce animation" (or the system's animator scale at zero)
 * makes the growth a short fade, and "Reduce dynamic blur" an opaque slab.
 *
 * [item] is non-null while the menu is wanted. The last one is kept so the
 * panel can play its way out after the caller has already let go of it, the
 * same arrangement [HeldContextMenu] uses.
 */
@Composable
fun <T : Any> IosMenuPopover(
    item: T?,
    anchor: Rect?,
    onDismiss: () -> Unit,
    backdrop: Backdrop? = null,
    hazeState: HazeState? = null,
    content: @Composable (T) -> Unit,
) {
    var last by remember { mutableStateOf<Pair<T, Rect?>?>(null) }
    val requested = item?.let { it to anchor }
    if (requested != null && requested.first != last?.first) {
        SideEffect { last = requested }
    }
    val shown = requested ?: last ?: return
    val (menuItem, menuAnchor) = shown
    val open = item != null

    val context = LocalContext.current
    val appReduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val systemAnimationsOff = remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
    val reduceMotion = appReduceAnimation || systemAnimationsOff

    val progress = remember { Animatable(0f) }
    LaunchedEffect(open) {
        if (open) {
            // The opening frame is the heaviest the menu has: every row
            // composed and measured, the player pausing its canvas, and the
            // still the frost blurs taken and read back. Grown from that frame,
            // the spring — which runs on the clock — jumps across it. So the
            // panel waits, unseen, for that frame and the still to be done;
            // not when it is coming back from part-way out. Only a still
            // the panel will frost with is waited for: with no glass, or the
            // blur reduced, none is taken.
            if (progress.value == 0f) {
                withFrameNanos { }
                withFrameNanos { }
                (backdrop as? SnapshotBackdrop)?.takeIf {
                    isGlassSupported() && !AppSettings.reduceDynamicBlur.value
                }?.let { still ->
                    withTimeoutOrNull(STILL_WAIT_MS) { snapshotFlow { still.hasStill }.first { it } }
                }
            }
            progress.animateTo(
                1f,
                if (reduceMotion) {
                    tween(durationMillis = 120, easing = LinearEasing)
                } else {
                    // Critically damped: it settles without overshooting.
                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 600f)
                },
            )
        } else {
            progress.animateTo(0f, tween(durationMillis = if (reduceMotion) 100 else 170, easing = FastOutSlowInEasing))
            last = null
        }
    }

    // Not a plain BackHandler: inside the player's sheet that loses to the
    // sheet's own dismiss, and back would take the player down under the menu.
    PlayerBackHandler(enabled = open, onBack = onDismiss)

    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val scrim = Color.Black.copy(alpha = if (dark) 0.30f else 0.14f)
    val insets = WindowInsets.safeDrawing
    var overlayOffset by remember { mutableStateOf(Offset.Zero) }

    Layout(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayOffset = it.positionInWindow() }
            // Nothing under the menu moves while it is up: a drag that the
            // rows didn't take would otherwise reach the sheet the player is
            // in and pull the whole player down. Taps still go through.
            .containSheetGestures(),
        content = {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }
                    .background(scrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = open,
                        onClick = onDismiss,
                    ),
            )
            // The panel's shadow, cast by a plate of its own behind it: the
            // panel is drawn into a layer of its own size (see the placement
            // below), which would cut a shadow of its own off at its edges.
            // Cast from a plain rounded rect: a shadow that size is a blur
            // deep enough that the continuous corner can't be told from it,
            // and the GPU draws a rounded rect's straight off, where a path's
            // is a mask the size of the panel rasterized on the CPU — again
            // at each new scale, every frame of the growth.
            Box(Modifier.iosMenuShadow(IosMenuShadowShape))
            Box(
                Modifier
                    // Taps between rows land on the panel, not the scrim under it.
                    .pointerInput(Unit) {}
                    .iosMenuPanel(
                        translucent = true,
                        backdrop = backdrop,
                        hazeState = hazeState,
                        castsShadow = false,
                    ),
                propagateMinConstraints = true,
            ) {
                // The rows in a layer of their own. The frost under them is
                // drawn again on every frame of the growth — it holds the
                // still in place while the panel scales over it — and drawn
                // in the same layer, every label and glyph went with it, each
                // frame at a new scale: rasterized again and uploaded again on
                // the render thread. Apart, they are drawn once, full size,
                // and only scaled.
                Column(
                    Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .padding(vertical = 8.dp),
                ) {
                    content(menuItem)
                }
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val margin = 14.dp.roundToPx()
        val top = insets.getTop(this) + 8.dp.roundToPx()
        val bottom = height - insets.getBottom(this) - 12.dp.roundToPx()

        val scrimPlaceable = measurables[0].measure(Constraints.fixed(width, height))
        val panelWidth = IosMenuWidth.roundToPx().coerceAtMost(width - margin * 2)
        val panel = measurables[2].measure(
            Constraints(minWidth = panelWidth, maxWidth = panelWidth, maxHeight = (bottom - top).coerceAtLeast(0)),
        )
        val shadowPlate = measurables[1].measure(Constraints.fixed(panel.width, panel.height))
        val anchorBounds = menuAnchor?.translate(-overlayOffset)
        val right = anchorBounds?.right?.roundToInt() ?: (width - margin)
        val panelX = (right - panel.width)
            .coerceIn(margin, (width - margin - panel.width).coerceAtLeast(margin))
        val lowest = (bottom - panel.height).coerceAtLeast(top)
        val hangsDown = anchorBounds != null && anchorBounds.center.y < height / 2f
        val panelY = if (hangsDown) anchorBounds.top.roundToInt().coerceIn(top, lowest) else lowest

        val growth: GraphicsLayerScope.() -> Unit = {
            val p = progress.value
            alpha = (p * 2.5f).coerceIn(0f, 1f)
            if (!reduceMotion) {
                transformOrigin = TransformOrigin(1f, if (hangsDown) 0f else 1f)
                val scale = lerp(0.3f, 1f, p)
                scaleX = scale
                scaleY = scale
            }
        }
        layout(width, height) {
            scrimPlaceable.place(0, 0)
            shadowPlate.placeWithLayer(panelX, panelY, layerBlock = growth)
            panel.placeWithLayer(panelX, panelY) {
                growth()
                // Drawn full size into a layer of its own, and only that
                // picture scaled. Scaled as it was drawn, the panel's
                // continuous corners — a path, not a rounded rect — were
                // rasterized again on the CPU for its clips and rim at every
                // new scale, and uploaded again, frame after frame of the
                // growth. At one size the masks are made once and kept.
                compositingStrategy = CompositingStrategy.Offscreen
            }
        }
    }
}
