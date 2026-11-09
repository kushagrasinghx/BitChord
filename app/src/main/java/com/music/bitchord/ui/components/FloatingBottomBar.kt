package com.music.bitchord.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlin.math.abs
import kotlin.math.roundToInt

data class BottomTab(
    val label: String,
    val icon: ImageVector,
)

/**
 * The gap between the pill's glass edge and the tabs inside it.
 *
 * Apple's selection sits about 4pt in from its tab bar's edge, measured off
 * the WWDC Liquid Glass frames; it was 8 here, then 6.
 *
 * Shared with [GlassNavBar], which is meant to measure the same as this bar
 * rather than merely near it.
 */
internal val PILL_INSET = 4.dp

/**
 * The tab bar's margin at either side of the screen: Apple's is 19pt, wider
 * than the page's own [BAR_GUTTER].
 */
internal val NAV_BAR_GUTTER = 19.dp

/**
 * How far above [floatingBarInsets] the bar sits: on a gesture inset of 15dp
 * this puts its foot 21dp off the screen's edge, where Apple's is.
 */
internal val NAV_BAR_LIFT = 6.dp

/**
 * How far the floating bars stand off the bottom edge: the system navigation
 * bar's inset, but never less than a gesture bar's.
 *
 * With the gesture hint (or the navigation bar itself) switched off that inset
 * is zero, and the tab bar dropped onto the screen's very edge. It keeps the
 * height it has with the hint showing instead. Three-button navigation is
 * taller than the floor, so it still wins there.
 */
internal val floatingBarInsets: WindowInsets
    @Composable get() = WindowInsets.navigationBars.union(WindowInsets(bottom = GESTURE_BAR_FLOOR))

/** A gesture bar's inset — 45px at the test phone's 3x density. */
private val GESTURE_BAR_FLOOR = 15.dp

/**
 * Each tab's own vertical padding, and the counterweight to [PILL_INSET].
 *
 * The pill has no height of its own — it is whatever its contents come to — so
 * taking 2dp off the inset above would have shortened the whole bar by 4. The
 * same 2dp is added back here instead, which leaves the bar's outer height
 * exactly where it was and moves the boundary rather than the bar. The two
 * numbers are a pair: change one and the bar's height moves unless the other
 * moves against it.
 */
internal val TAB_VERTICAL_PADDING = 6.5.dp

/** The gap between a tab's glyph and its label, in both bars. */
internal val TAB_ICON_LABEL_GAP = 1.dp

/**
 * A tab's glyph box. Apple's glyphs stand 21 to 24pt in a bar 61pt tall, a
 * larger share of it than these had at 25dp in 70; at 28 they come out at
 * Apple's sizes, and with the padding above the bar is 62dp.
 */
internal val TAB_ICON_SIZE = 28.dp

/** A tab's label: Apple's 10pt, letter-spaced as SF Pro Text sets it. */
@Composable
internal fun tabLabelStyle(): TextStyle =
    MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.5.sp)

/**
 * The spring the selection indicator and the tab glyphs both travel on.
 *
 * Damping 0.72 rather than the 0.5 it was: half-damped overshoots two or three
 * times, and a run of diminishing bounces is what makes a control read as a
 * spring rather than as a material. This settles on the second approach — one
 * soft pass beyond the mark and done — which is the difference between bouncy
 * and alive.
 *
 * Stiffness 320 puts the whole movement at roughly a third of a second, quick
 * enough that the tap and the arrival feel like one event.
 */
internal val GlassSpring = spring<Float>(dampingRatio = 0.72f, stiffness = 320f)

/**
 * How far the indicator elongates along its travel, at full stride.
 *
 * This is the part that reads as liquid rather than as a sliding rectangle. A
 * shape crossing a gap under its own momentum does not stay the shape it was:
 * it draws out along the direction it is going and gathers itself back at the
 * end. Driven off how far there is still to go, so it is widest in the middle
 * of the trip and exactly itself once it arrives — no state to keep, and it
 * falls out of a drag for free, since dragging is nothing but a long way still
 * to go.
 *
 * Sixteen percent is enough to be felt and not enough to be caught at: past
 * about a fifth the pill starts reading as a stretched image of itself.
 */
internal const val STRETCH = 0.16f

/**
 * How much of the stretch is taken back out of the indicator's height.
 *
 * Half, not all. Conserving area exactly is what a drop of water does, and it
 * is too much here — the indicator sits behind a glyph that is not deforming
 * with it, and a full counter-squash reads as the pill being crushed rather
 * than drawn. Half keeps the sense of something with a volume to redistribute
 * while leaving the glyph its ground.
 */
internal const val SQUASH = 0.5f

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun FloatingBottomBar(
    tabs: List<BottomTab>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val container = MaterialTheme.colorScheme.surface
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val useGlass = LocalLiquidGlassEnabled.current && isGlassSupported()
    val reduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    // The glass settle is exactly the motion "reduce animation" promises to
    // drop — snapping both the indicator's travel and the glyph's pop to
    // their target leaves the tap itself instant rather than eased.
    val glassSpec: AnimationSpec<Float> = if (reduceAnimation) snap() else GlassSpring

    // ---- The navigation bar settings, ported from ArchiveTune's page ----
    // Frosted, tint and Liquid Glass are the three surfaces the standard bar
    // can wear, one at a time; the dimensions are the user's own while the
    // glass bar's fixed shape is not the one in force.
    val navBarFrosted by AppSettings.navBarFrosted.collectAsStateWithLifecycle()
    val navBarTint by AppSettings.navBarTint.collectAsStateWithLifecycle()
    val liquidGlassNavBar by AppSettings.liquidGlassNavBar.collectAsStateWithLifecycle()
    val navBarWidth by AppSettings.navBarWidth.collectAsStateWithLifecycle()
    val navBarHeight by AppSettings.navBarHeight.collectAsStateWithLifecycle()
    val navBarOpacity by AppSettings.navBarOpacity.collectAsStateWithLifecycle()
    val navBarTransparency by AppSettings.navBarTransparency.collectAsStateWithLifecycle()
    val navBarCornerRadius by AppSettings.navBarCornerRadius.collectAsStateWithLifecycle()
    val navBarGlassGlow by AppSettings.navBarGlassGlow.collectAsStateWithLifecycle()
    val navBarGlassGlowIntensity by AppSettings.navBarGlassGlowIntensity.collectAsStateWithLifecycle()
    val navBarShowLabels by AppSettings.navBarShowLabels.collectAsStateWithLifecycle()

    val glassStyle = useGlass && liquidGlassNavBar
    val dimensionsEnabled = !glassStyle
    // The glass bar's own geometry is a half-height pill whatever the radius
    // dial says — and so is the stock one, which is the shape the shipped 28
    // stands for. Anything else on the dial is drawn as itself.
    val pillShape = if (abs(navBarCornerRadius - 28f) < 0.01f || !dimensionsEnabled) {
        RoundedCornerShape(percent = 50)
    } else {
        RoundedCornerShape(navBarCornerRadius.dp)
    }
    // The surface's fill, thinned by the transparency dial — the frosted tint
    // is blended the same way so the dial breathes through the frost too.
    val surfaceAlpha = (navBarOpacity * (1f - navBarTransparency)).coerceIn(0.05f, 1f)
    val light = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    // The tint style's colors, ported from ArchiveTune: a deep tinted dark in
    // the dark theme, a light pastel in the light one.
    val tintBase = if (light) {
        lerp(Color.White, MaterialTheme.colorScheme.primary, 0.26f)
    } else {
        lerp(Color.Black, MaterialTheme.colorScheme.primary, 0.30f)
    }
    val glassGlowStrength by animateFloatAsState(
        targetValue = if (navBarGlassGlow) navBarGlassGlowIntensity else 0f,
        animationSpec = tween(420),
        label = "navBarGlassGlow",
    )

    var dragOffset by remember { mutableFloatStateOf(0f) }
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)

    var rowSize by remember { mutableStateOf(IntSize.Zero) }
    val gapPx = with(density) { 6.dp.toPx() }
    val n = tabs.size

    // With weight(1f) + spacedBy(gap):
    //   tabWidth = (rowWidth - gap*(n-1)) / n
    //   tab i left edge = i * (tabWidth + gap) = i * (rowWidth + gap) / n
    val tabWidthPx = if (rowSize.width > 0 && n > 0) {
        (rowSize.width - gapPx * (n - 1)) / n
    } else 0f
    val tabStepPx = if (rowSize.width > 0 && n > 0) {
        (rowSize.width + gapPx) / n
    } else 0f

    // The drag below runs in one coroutine for the life of the bar, so it
    // reads everything that can change under it through these. Read plainly,
    // each would stay what it was on the first touch.
    val currentTabStepPx by rememberUpdatedState(tabStepPx)
    val currentLastIndex by rememberUpdatedState(tabs.lastIndex)
    val currentOnTabSelected by rememberUpdatedState(onTabSelected)

    val pillTargetPx = if (tabStepPx > 0f) {
        selectedIndex * tabStepPx + dragOffset
    } else 0f

    val animatedPillOffset by animateFloatAsState(
        targetValue = pillTargetPx,
        animationSpec = glassSpec,
        label = "pillOffset",
    )

    // How much of a tab's stride is still ahead of the indicator: 0 at rest,
    // toward 1 in the middle of a move or under a drag that has run away from
    // it. The stretch below is a function of this and nothing else, which is
    // what keeps it honest — the shape can only be deformed while it is
    // actually behind where it is going.
    val lag = if (tabStepPx > 0f) {
        (abs(pillTargetPx - animatedPillOffset) / tabStepPx).coerceIn(0f, 1f)
    } else {
        0f
    }

    var lastHapticTab by remember { mutableIntStateOf(selectedIndex) }

    LaunchedEffect(selectedIndex) {
        dragOffset = 0f
        lastHapticTab = selectedIndex
    }

    Box(
        modifier = modifier
            .windowInsetsPadding(floatingBarInsets)
            .padding(horizontal = NAV_BAR_GUTTER)
            .padding(bottom = NAV_BAR_LIFT)
            .fillMaxWidth((if (dimensionsEnabled) navBarWidth else 1f).coerceIn(0.5f, 1f))
            .clip(pillShape)
            .then(
                when {
                    // "Reduce dynamic blur" keeps the bar's shape and answers
                    // with the plain surface, as every glassy thing here does.
                    reduceDynamicBlur -> Modifier.background(container.copy(alpha = surfaceAlpha))
                    // The tinted bar: no blur, the accent washed over it.
                    navBarTint -> Modifier.background(tintBase.copy(alpha = surfaceAlpha))
                    // The glass bar: the lens itself, at its own fixed geometry.
                    glassStyle -> Modifier.liquidGlass(shape = pillShape)
                    // The frosted bar this app has always drawn — the default.
                    navBarFrosted -> Modifier.optimizedHazeEffect(
                        state = hazeState,
                        style = HazeMaterials.regular(container.copy(alpha = surfaceAlpha)),
                    )
                    else -> Modifier.background(container.copy(alpha = surfaceAlpha))
                },
            )
            .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, pillShape)
            // The glow, over whichever surface is in force — a rim of light
            // and a sheen, ported from ArchiveTune's glass glow.
            .glassGlowOverlay(glassGlowStrength, pillShape)
            .padding(horizontal = PILL_INSET, vertical = PILL_INSET),
    ) {
        if (tabWidthPx > 0f) {
            Box(
                modifier = Modifier
                    .width(with(density) { tabWidthPx.toDp() })
                    .height(with(density) { rowSize.height.toDp() })
                    .graphicsLayer {
                        translationX = animatedPillOffset
                        // Around its own centre, so the indicator draws out
                        // both ways rather than growing a tail off one edge —
                        // a leading edge that ran ahead of the glyph it is
                        // meant to be behind would read as two things moving,
                        // not one thing stretching.
                        scaleX = 1f + lag * STRETCH
                        scaleY = 1f - lag * STRETCH * SQUASH
                    }
                    .clip(pillShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { rowSize = it }
                .pointerInput(Unit) {
                    // Every drag measures from the tab selected when it starts.
                    // A stale index here measured them all from the tab the bar
                    // first opened on: a drag right of it still landed, while
                    // one to the left clamped back onto it and read as no change.
                    var totalDrag = 0f
                    var dragStartIndex = currentSelectedIndex
                    detectHorizontalDragGestures(
                        onDragStart = {
                            totalDrag = 0f
                            dragStartIndex = currentSelectedIndex
                        },
                        onDragCancel = { dragOffset = 0f },
                        onDragEnd = {
                            val step = currentTabStepPx
                            if (step > 0f) {
                                val absoluteIndexFloat = (dragStartIndex + totalDrag / step)
                                    .coerceIn(0f, currentLastIndex.toFloat())
                                val newIndex = absoluteIndexFloat.roundToInt()
                                if (newIndex != currentSelectedIndex) {
                                    currentOnTabSelected(newIndex)
                                }
                            }
                            dragOffset = 0f
                        },
                        onHorizontalDrag = { _, delta ->
                            val step = currentTabStepPx
                            if (step <= 0f) return@detectHorizontalDragGestures
                            totalDrag += delta
                            val absoluteIndexFloat = (dragStartIndex + totalDrag / step)
                                .coerceIn(0f, currentLastIndex.toFloat())
                            dragOffset = absoluteIndexFloat * step - currentSelectedIndex * step

                            val approxTab = absoluteIndexFloat.roundToInt()
                            if (approxTab != lastHapticTab) {
                                haptics.play(Haptic.Tick)
                                lastHapticTab = approxTab
                            }
                        },
                    )
                },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A real glass pill samples whatever artwork is behind it, not the
            // theme's surface color, so a fixed onSurfaceVariant gray can lose
            // contrast against it. Glass mode reads luminance off the surface
            // color instead and picks pure black or white, same as the tint
            // Echo's floating nav bar uses for its own liquid glass.
            val glassTint = glassContentColor()
            val adaptiveTint = if (useGlass) glassTint else null
            val itemVerticalPadding = if (dimensionsEnabled) TAB_VERTICAL_PADDING * navBarHeight else TAB_VERTICAL_PADDING
            tabs.forEachIndexed { index, tab ->
                BottomBarItem(
                    tab = tab,
                    selected = index == selectedIndex,
                    glassSpec = glassSpec,
                    selectedTint = if (navBarTint && !useGlass) {
                        if (light) lerp(MaterialTheme.colorScheme.primary, Color.Black, 0.55f)
                        else lerp(Color.White, MaterialTheme.colorScheme.primary, 0.45f)
                    } else {
                        adaptiveTint
                    },
                    unselectedTint = if (navBarTint && !useGlass) {
                        (if (light) Color.Black else Color.White).copy(alpha = 0.62f)
                    } else {
                        adaptiveTint?.copy(alpha = 0.65f)
                    },
                    labelSpacing = if (dimensionsEnabled) navBarLabelSpacing.dp else TAB_ICON_LABEL_GAP,
                    showLabel = navBarShowLabels,
                    verticalPadding = itemVerticalPadding,
                    onClick = { onTabSelected(index) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    tab: BottomTab,
    selected: Boolean,
    glassSpec: AnimationSpec<Float>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Overrides the theme's primary/onSurfaceVariant tint — see the glass branch above. */
    selectedTint: Color? = null,
    unselectedTint: Color? = null,
    /** The glyph-to-label gap, from the navigation bar's dimensions. */
    labelSpacing: Dp = TAB_ICON_LABEL_GAP,
    showLabel: Boolean = true,
    /** The tab's own vertical padding — the height dial scales through it. */
    verticalPadding: Dp = TAB_VERTICAL_PADDING,
) {
    // The same spring the indicator rides, so the glyph arriving and the glass
    // arriving are one movement rather than two that nearly agree.
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = glassSpec,
        label = "tabScale",
    )
    val haptics = rememberHaptics()
    // The label under the glyph, ported from ArchiveTune's navigation bar
    // settings: off, the glyph stands alone and the icon's own padding keeps
    // the bar's height — a shorter bar would jump the page's bottom inset.
    val tint by animateColorAsState(
        targetValue = if (selected) {
            selectedTint ?: MaterialTheme.colorScheme.primary
        } else {
            unselectedTint ?: MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(200),
        label = "tabTint",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                if (!selected) haptics.play(Haptic.Select)
                onClick()
            }
            .padding(vertical = verticalPadding),
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = tint,
            modifier = Modifier
                .size(TAB_ICON_SIZE)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        )
        if (showLabel) {
            Spacer(Modifier.height(labelSpacing))
            Text(
                text = tab.label,
                style = tabLabelStyle(),
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---- The compact control circles, ported from ArchiveTune ----

/**
 * The two circles that flank the mini player once the compact behavior has
 * folded the bottom stack, ported from ArchiveTune's CompactControlCircle: the
 * left one carries Home — Library, while Library is the tab in force — and the
 * right one carries Search. They slide up out of the fold as the navigation
 * bar falls away, glass where the glass is on, and wear the same glow.
 */
@Composable
internal fun CompactControlCircles(
    /** How far the stack has folded, 0..1 — the circles ride it in and out. */
    fraction: Float,
    leftIsLibrary: Boolean,
    onLeft: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .matchParentSize()
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompactControlCircle(
            icon = if (leftIsLibrary) Icons.Rounded.LibraryMusic else Icons.Rounded.Home,
            onClick = onLeft,
            fraction = fraction,
        )
        CompactControlCircle(
            icon = Icons.Rounded.Search,
            onClick = onSearch,
            fraction = fraction,
        )
    }
}

/** One 56dp circle, glass where it can run and plain where it cannot. */
@Composable
private fun CompactControlCircle(
    icon: ImageVector,
    onClick: () -> Unit,
    fraction: Float,
) {
    val useGlass = LocalLiquidGlassEnabled.current && isGlassSupported()
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val navBarGlassGlow by AppSettings.navBarGlassGlow.collectAsStateWithLifecycle()
    val navBarGlassGlowIntensity by AppSettings.navBarGlassGlowIntensity.collectAsStateWithLifecycle()
    val glow by animateFloatAsState(
        targetValue = if (navBarGlassGlow) navBarGlassGlowIntensity else 0f,
        animationSpec = tween(420),
        label = "circleGlassGlow",
    )
    val shape = CircleShape
    val haptics = rememberHaptics()
    Box(
        modifier = Modifier
            .size(56.dp)
            .graphicsLayer {
                alpha = fraction
                translationY = (1f - fraction) * 40.dp.toPx()
            }
            .clip(shape)
            .then(
                if (useGlass && !reduceDynamicBlur) {
                    Modifier.liquidGlass(shape = shape)
                } else {
                    Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                },
            )
            .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, shape)
            .glassGlowOverlay(glow, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptics.play(Haptic.Select)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (useGlass) glassContentColor() else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(24.dp),
        )
    }
}
