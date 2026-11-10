/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.component

import android.os.SystemClock
import android.view.ViewConfiguration
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarArrangement
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import moe.rukamori.archivetune.utils.isLowEndDevice
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.utils.ImageBlurUtils
import moe.rukamori.archivetune.constants.DisableAnimationsKey
import moe.rukamori.archivetune.constants.FloatingNavigationBarMaxWidth
import moe.rukamori.archivetune.constants.HideNavigationBarLabelsKey
import moe.rukamori.archivetune.constants.NAVIGATION_BAR_CORNER_RADIUS_DEFAULT
import moe.rukamori.archivetune.constants.NAVIGATION_BAR_HEIGHT_DEFAULT
import moe.rukamori.archivetune.constants.NAVIGATION_BAR_LABEL_SPACING_DEFAULT
import moe.rukamori.archivetune.constants.NAVIGATION_BAR_OPACITY_DEFAULT
import moe.rukamori.archivetune.constants.NAVIGATION_BAR_TRANSPARENCY_DEFAULT
import moe.rukamori.archivetune.constants.NAVIGATION_BAR_WIDTH_DEFAULT
import moe.rukamori.archivetune.constants.NavigationBarCornerRadiusKey
import moe.rukamori.archivetune.constants.NavigationBarHeight
import moe.rukamori.archivetune.constants.NavigationBarHeightKey
import moe.rukamori.archivetune.constants.NavigationBarLabelSpacingKey
import moe.rukamori.archivetune.constants.NavigationBarOpacityKey
import moe.rukamori.archivetune.constants.NavigationBarTransparencyKey
import moe.rukamori.archivetune.constants.NavigationBarWidthKey
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.utils.rememberPreference
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.shadow.Shadow as KyantShadow
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class NavigationBarBackdrop(
    val layer: GraphicsLayer,
) {
    var contentOffsetInRoot: Offset = Offset.Zero
}

val LocalNavigationBarBackdrop = compositionLocalOf<NavigationBarBackdrop?> { null }

private val NavigationItemsMaxWidth = 360.dp
private val NavigationItemVerticalPadding = 8.dp

private val SukiSUBarHeight = 64.dp
private val SukiSUItemPadding = 4.dp

private const val FrostedNavBarBlurRadiusPx = 60f

private const val FrostedNavBarOverlayAlpha = 0.30f

private const val TintFrostedLightBaseBlend = 0.26f

private const val TintedDarkBaseBlend = 0.30f

private const val TintFrostedContentBlend = 0.55f

private const val TintedDarkContentBlend = 0.45f

private val FloatingNavigationIndicatorWidth = 64.dp
private val FloatingNavigationIndicatorHeight = 42.dp

private object FullMotionDurationScale : MotionDurationScale {
    override val scaleFactor: Float = 1f
}

@Composable
fun FloatingNavigationToolbar(
    items: List<Screens>,
    pureBlack: Boolean,
    modifier: Modifier = Modifier,
    isPairedWithMiniPlayer: Boolean = false,
    frostedBlur: Boolean = false,
    tintFrostedBlur: Boolean = false,
    frostedBackdrop: NavigationBarBackdrop? = null,
    liquidGlass: Boolean = false,
    liquidGlassBackdrop: Backdrop? = null,
    glowStrength: Float = 0f,
    isSelected: (Screens) -> Boolean,
    onItemClick: (Screens, Boolean) -> Unit,
    onSearchItemDoubleClick: (() -> Unit)? = null,
) {
    val isDarkScheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val tintedNavBarBaseColor =
        if (isDarkScheme) {
            lerp(Color.Black, MaterialTheme.colorScheme.primary, TintedDarkBaseBlend)
        } else {
            lerp(Color.White, MaterialTheme.colorScheme.primary, TintFrostedLightBaseBlend)
        }
    val tintedNavBarContentColor =
        if (isDarkScheme) {
            lerp(Color.White, MaterialTheme.colorScheme.primary, TintedDarkContentBlend)
        } else {
            lerp(MaterialTheme.colorScheme.primary, Color.Black, TintFrostedContentBlend)
        }
    val tintedNavBarUnselectedContentColor =
        if (isDarkScheme) Color.White.copy(alpha = 0.62f) else Color.Black.copy(alpha = 0.62f)

    val (navBarWidthFraction) =
        rememberPreference(NavigationBarWidthKey, defaultValue = NAVIGATION_BAR_WIDTH_DEFAULT)
    val (navBarHeightMultiplier) =
        rememberPreference(NavigationBarHeightKey, defaultValue = NAVIGATION_BAR_HEIGHT_DEFAULT)
    val (navBarOpacity) =
        rememberPreference(NavigationBarOpacityKey, defaultValue = NAVIGATION_BAR_OPACITY_DEFAULT)
    val (navBarTransparency) =
        rememberPreference(NavigationBarTransparencyKey, defaultValue = NAVIGATION_BAR_TRANSPARENCY_DEFAULT)
    val (navBarLabelSpacing) =
        rememberPreference(NavigationBarLabelSpacingKey, defaultValue = NAVIGATION_BAR_LABEL_SPACING_DEFAULT)
    val (navBarCornerRadius) =
        rememberPreference(NavigationBarCornerRadiusKey, defaultValue = NAVIGATION_BAR_CORNER_RADIUS_DEFAULT)
    val isPreS = Build.VERSION.SDK_INT < Build.VERSION_CODES.S

    val canBlurBackdrop = frostedBlur && !tintFrostedBlur && frostedBackdrop != null && !isPreS

    val canLiquidGlass = liquidGlass && liquidGlassBackdrop != null && !isPreS
    val glassTuning = LocalLiquidGlassTuning.current
    val resolvedBarHeight =
        if (canLiquidGlass) SukiSUBarHeight else NavigationBarHeight * navBarHeightMultiplier

    val itemVerticalPadding =
        if (canLiquidGlass) SukiSUItemPadding else NavigationItemVerticalPadding
    val itemHorizontalPadding = if (canLiquidGlass) SukiSUItemPadding else 0.dp
    val navigationShape =
        if (canLiquidGlass) {
            RoundedCornerShape(percent = 50)
        } else {
            remember(isPairedWithMiniPlayer, navBarCornerRadius) {
                RoundedCornerShape(navBarCornerRadius.dp)
            }
        }
    val navigationContainerColor =
        if (canLiquidGlass) {
            Color.Transparent
        } else if (canBlurBackdrop) {
            if (pureBlack) {
                Color.Black.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        } else if (pureBlack) {
            if (tintFrostedBlur) {
                tintedNavBarBaseColor
            } else {
                Color.Black
            }
        } else if (tintFrostedBlur) {
            tintedNavBarBaseColor
        } else {
            val baseColor = MaterialTheme.colorScheme.surfaceContainer
            val effectiveAlpha =
                navBarOpacity * (1f - navBarTransparency)
            baseColor.copy(alpha = effectiveAlpha.coerceIn(0.05f, 1f))
        }
    val motionScheme = MaterialTheme.motionScheme
    val (disableAnimations) = rememberPreference(DisableAnimationsKey, defaultValue = false)
    val (hideNavigationLabels) = rememberPreference(HideNavigationBarLabelsKey, defaultValue = false)
    val density = LocalDensity.current

    val indicatorColor =
        when {
            canLiquidGlass -> Color.Transparent

            else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
        }
    val indicatorWidth = FloatingNavigationIndicatorWidth
    val indicatorHeight = FloatingNavigationIndicatorHeight

    val itemColors =
        when {
            canLiquidGlass -> {
                val glassIsNight = isSystemInDarkTheme()
                val glassSelectedColor =
                    if (glassIsNight) Color.White else MaterialTheme.colorScheme.onSurface
                val glassUnselectedColor =
                    if (glassIsNight) {
                        Color.White
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                ShortNavigationBarItemDefaults.colors(
                    selectedIndicatorColor = Color.Transparent,
                    selectedIconColor = glassSelectedColor,
                    selectedTextColor = glassSelectedColor,
                    unselectedIconColor = glassUnselectedColor,
                    unselectedTextColor = glassUnselectedColor,
                )
            }

            else ->
                ShortNavigationBarItemDefaults.colors(
                    selectedIndicatorColor = Color.Transparent,
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor =
                        if (pureBlack) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor =
                        if (pureBlack) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }

    val selectedIndex = items.indexOfFirst { isSelected(it) }

    val liquidGlassTransparentColors =
        ShortNavigationBarItemDefaults.colors(
            selectedIndicatorColor = Color.Transparent,
            selectedIconColor = Color.Transparent,
            selectedTextColor = Color.Transparent,
            unselectedIconColor = Color.Transparent,
            unselectedTextColor = Color.Transparent,
        )

    val iconCenters = remember { mutableStateMapOf<Int, Offset>() }
    val itemBounds = remember { mutableStateMapOf<Int, Rect>() }

    val containerPosState = remember { mutableStateOf(Offset.Zero) }
    var containerPos by containerPosState

    val indicatorX = remember { Animatable(0f) }
    var indicatorY by remember { mutableFloatStateOf(0f) }
    var indicatorPlaced by remember { mutableStateOf(false) }

    var liquidGlassPillWidth by remember { mutableStateOf(0.dp) }
    var liquidGlassPillHeight by remember { mutableStateOf(0.dp) }

    val animationScope = rememberCoroutineScope()
    val isLtr = true

    val tabWidthPxState = remember { mutableFloatStateOf(0f) }
    val totalWidthPxState = remember { mutableFloatStateOf(0f) }
    val itemsRowLeftInContainerState = remember { mutableFloatStateOf(0f) }
    var tabWidthPx by tabWidthPxState
    var totalWidthPx by totalWidthPxState

    var itemsRowLeftInContainer by itemsRowLeftInContainerState
    val rubberBandPx = with(density) { 4.dp.toPx() }

    val rubberBandOffset = remember { Animatable(0f) }
    val panelOffset by remember(rubberBandPx, totalWidthPx) {
        derivedStateOf {
            if (totalWidthPx == 0f) {
                0f
            } else {
                val fraction = (rubberBandOffset.value / totalWidthPx).fastCoerceIn(-1f, 1f)
                rubberBandPx * fraction.sign * EaseOut.transform(abs(fraction))
            }
        }
    }

    val tabsCount = items.size
    val selectedIndexUpdated = rememberUpdatedState(selectedIndex)
    val onItemClickUpdated = rememberUpdatedState(onItemClick)
    val dampedDragAnimation =
        remember(tabsCount, canLiquidGlass) {
            if (canLiquidGlass && tabsCount > 0) {
                LiquidGlassDragAnimation(
                    animationScope = animationScope,
                    initialValue = selectedIndex.coerceIn(0, tabsCount - 1).toFloat(),
                    valueRange = 0f..(tabsCount - 1).toFloat(),
                    visibilityThreshold = 0.001f,
                    initialScale = 1f,
                    pressedScale = 78f / 56f,
                    canDrag = { offset ->

                        if (totalWidthPx == 0f) return@LiquidGlassDragAnimation false
                        offset.x in 0f..totalWidthPx && offset.y >= 0f
                    },
                    onDragStarted = {  },
                    onDragStopped = {
                        val targetIndex = targetValue.roundToInt().coerceIn(0, tabsCount - 1)
                        animationScope.launch {
                            rubberBandOffset.animateTo(0f, spring(1f, 300f, 0.5f))
                        }

                        if (targetIndex != selectedIndexUpdated.value) {
                            onItemClickUpdated.value(items[targetIndex], false)
                        }
                        animateToValue(targetIndex.toFloat())
                    },
                    onDrag = { _, dragAmount ->
                        if (tabWidthPx > 0f) {
                            updateValue(
                                (targetValue + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
                                    .fastCoerceIn(0f, (tabsCount - 1).toFloat()),
                            )
                            animationScope.launch {
                                rubberBandOffset.snapTo(rubberBandOffset.value + dragAmount.x)
                            }
                        }
                    },
                )
            } else {
                null
            }
        }

    val displayIndex by remember(dampedDragAnimation) {
        derivedStateOf {
            dampedDragAnimation?.value?.roundToInt()?.coerceIn(0, items.lastIndex) ?: selectedIndex
        }
    }

    val isSelectedTracker = rememberUpdatedState(isSelected)
    val itemsTracker = rememberUpdatedState(items)
    LaunchedEffect(dampedDragAnimation) {
        val anim = dampedDragAnimation ?: return@LaunchedEffect
        snapshotFlow {
            val itemsList = itemsTracker.value
            val sel = isSelectedTracker.value
            itemsList.indexOfFirst { sel(it) }
        }.drop(1).collect { idx ->

            if (idx in 0..(items.size - 1)) {
                anim.animateToValue(idx.toFloat())
            }
        }
    }

    val selectedCenter = if (selectedIndex >= 0) iconCenters[selectedIndex] else null
    val selectedItemBounds = if (selectedIndex >= 0) itemBounds[selectedIndex] else null

    val itemsRowTopInContainerState = remember { mutableFloatStateOf(0f) }
    var itemsRowTopInContainer by itemsRowTopInContainerState
    LaunchedEffect(selectedIndex, selectedCenter, selectedItemBounds, containerPos, disableAnimations, indicatorWidth, indicatorHeight, canLiquidGlass, tabWidthPx, itemsRowTopInContainer, itemVerticalPadding) {
        if (canLiquidGlass) {
            if (tabWidthPx <= 0f) return@LaunchedEffect
            liquidGlassPillWidth = with(density) { tabWidthPx.toDp() }
            liquidGlassPillHeight = SukiSUBarHeight - SukiSUItemPadding * 2

            indicatorY = itemsRowTopInContainer + with(density) { itemVerticalPadding.toPx() }
            indicatorPlaced = true
        } else {
            val center = selectedCenter ?: return@LaunchedEffect
            val widthPx = with(density) { indicatorWidth.toPx() }
            val heightPx = with(density) { indicatorHeight.toPx() }
            val targetX = (center.x - containerPos.x) - widthPx / 2f
            indicatorY = (center.y - containerPos.y) - heightPx / 2f
            val firstPlacement = !indicatorPlaced
            if (disableAnimations || firstPlacement) {
                indicatorX.snapTo(targetX)
                indicatorPlaced = true
            } else {
                withContext(FullMotionDurationScale) {
                    indicatorX.animateTo(
                        targetValue = targetX,
                        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
                    )
                }
            }
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
        contentAlignment = Alignment.Center,
    ) {
        val barPositionInRootState = remember { mutableStateOf(Offset.Zero) }
        val barSizeState = remember { mutableStateOf(IntSize.Zero) }
        val barPositionInRoot by barPositionInRootState
        val barSize by barSizeState
        Box(
            modifier =
                Modifier
                    .widthIn(max = FloatingNavigationBarMaxWidth)
                    .fillMaxWidth(navBarWidthFraction.coerceIn(0.5f, 1f))
                    .height(resolvedBarHeight),
            contentAlignment = Alignment.CenterStart,
        ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxSize()

                    .onGloballyPositioned(
                        remember(barPositionInRootState, barSizeState) {
                            { coordinates ->
                                barPositionInRootState.value = coordinates.positionInRoot()
                                barSizeState.value = coordinates.size
                            }
                        },
                    )
                    .graphicsLayer {
                        if (canLiquidGlass) {
                            translationX = panelOffset
                        }
                    }
                    .then(
                        if (canLiquidGlass && liquidGlassBackdrop != null) {
                            Modifier.liquidGlass(
                                backdrop = liquidGlassBackdrop,
                                shape = navigationShape,
                                interactive = false,
                                baseColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            )
                        } else {
                            Modifier
                        },
                    ).then(

                        if (glowStrength > 0.01f) {
                            Modifier.glassGlowOverlay(
                                strength = glowStrength,
                                shape = navigationShape,
                            )
                        } else {
                            Modifier
                        },
                    ),
            shape = navigationShape,
            color = navigationContainerColor,
            tonalElevation = if (canLiquidGlass) 0.dp else NavigationBarDefaults.Elevation,
            shadowElevation = if (canLiquidGlass) 0.dp else (8f * glassTuning.shadowFactor).dp,
        ) {
            if (canBlurBackdrop && frostedBackdrop != null) {
                if (isPreS) {
                    val blurredBitmap = rememberPreSFrostedBitmap(
                        backdrop = frostedBackdrop,
                        barPositionInRoot = barPositionInRoot,
                        barSize = barSize,
                        blurRadiusPx = FrostedNavBarBlurRadiusPx,
                        updateIntervalMs = if (LocalContext.current.isLowEndDevice()) 160L else 80L,
                    )
                    if (blurredBitmap != null) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        alpha = FrostedNavBarOverlayAlpha
                                        clip = true
                                    }.drawBehind {
                                        drawImage(blurredBitmap)
                                    },
                        )
                    }
                } else {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    renderEffect =
                                        BlurEffect(
                                            radiusX = FrostedNavBarBlurRadiusPx,
                                            radiusY = FrostedNavBarBlurRadiusPx,
                                            edgeTreatment = TileMode.Clamp,
                                        )
                                    alpha = FrostedNavBarOverlayAlpha
                                    clip = true
                                }.drawBehind {
                                    val offset = frostedBackdrop.contentOffsetInRoot - barPositionInRoot
                                    translate(offset.x, offset.y) {
                                        runCatching { drawLayer(frostedBackdrop.layer) }
                                    }
                                },
                    )
                }
            }
            val transparentRipple = remember { ripple(color = Color.Transparent) }
            androidx.compose.runtime.CompositionLocalProvider(
                LocalIndication provides transparentRipple,
            ) {
                ShortNavigationBar(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Transparent,
                    contentColor =
                        when {
                            tintFrostedBlur -> tintedNavBarContentColor
                            pureBlack -> Color.White
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    arrangement = ShortNavigationBarArrangement.EqualWeight,
                ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .onGloballyPositioned(

                        remember(containerPosState) {
                            { coordinates -> containerPosState.value = coordinates.positionInRoot() }
                        },
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selectedIndex >= 0 && indicatorPlaced && !canLiquidGlass) {
                        val pillWidth = indicatorWidth
                        val pillHeight = indicatorHeight
                        if (pillWidth > 0.dp && pillHeight > 0.dp) {
                            val pillShape = RoundedCornerShape(percent = 50)
                            Box(
                                modifier =
                                    Modifier
                                        .align(Alignment.TopStart)
                                        .offset {
                                            IntOffset(
                                                indicatorX.value.roundToInt(),
                                                indicatorY.roundToInt(),
                                            )
                                        }
                                        .width(pillWidth)
                                        .height(pillHeight)
                                        .clip(pillShape)
                                        .background(indicatorColor),
                            )
                        }
                    }

                    Row(
                        modifier =
                            Modifier
                                .widthIn(max = NavigationItemsMaxWidth)
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .padding(
                                    vertical = itemVerticalPadding,
                                    horizontal = itemHorizontalPadding,
                                )
                                .onGloballyPositioned(

                                    remember(itemsRowLeftInContainerState, itemsRowTopInContainerState, totalWidthPxState, tabWidthPxState, containerPosState, density, itemHorizontalPadding, tabsCount) {
                                        { coordinates ->
                                            val rowPosInRoot = coordinates.positionInRoot()
                                            itemsRowLeftInContainerState.value = rowPosInRoot.x - containerPosState.value.x
                                            itemsRowTopInContainerState.value = rowPosInRoot.y - containerPosState.value.y
                                            totalWidthPxState.value = coordinates.size.width.toFloat()
                                            val horizontalPaddingPx = with(density) { itemHorizontalPadding.toPx() }
                                            val contentWidthPx = (totalWidthPxState.value - 2f * horizontalPaddingPx).coerceAtLeast(0f)
                                            tabWidthPxState.value = if (tabsCount > 0) (contentWidthPx / tabsCount).coerceAtLeast(0f) else 0f
                                        }
                                    },
                                )
                                .then(
                                    if (canLiquidGlass && dampedDragAnimation != null) {
                                        dampedDragAnimation.modifier
                                    } else {
                                        Modifier
                                    },
                                ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        items.forEachIndexed { index, screen ->
                            val selected = isSelected(screen)

                            val iconScale = remember(screen) { Animatable(1f) }
                            LaunchedEffect(selected, disableAnimations) {
                                if (disableAnimations) {
                                    iconScale.snapTo(1f)
                                } else if (selected) {
                                    iconScale.snapTo(0.8f)

                                    withContext(FullMotionDurationScale) {
                                        iconScale.animateTo(
                                            targetValue = 1f,
                                            animationSpec =
                                                spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMediumLow,
                                                ),
                                        )
                                    }
                                } else {
                                    iconScale.snapTo(1f)
                                }
                            }
                            val onDoubleClick =
                                remember(screen, onSearchItemDoubleClick) {
                                    if (screen == Screens.Search) onSearchItemDoubleClick else null
                                }
                            val lastClickTime = remember(screen) { mutableLongStateOf(0L) }
                            val onClick =
                                remember(screen, selected, onItemClick, onDoubleClick) {
                                    {
                                        val currentTime = SystemClock.uptimeMillis()
                                        val isDoubleClick =
                                            onDoubleClick != null &&
                                                currentTime - lastClickTime.longValue <= ViewConfiguration.getDoubleTapTimeout()
                                        lastClickTime.longValue = if (isDoubleClick) 0L else currentTime
                                        if (isDoubleClick) {
                                            onDoubleClick?.invoke()
                                            Unit
                                        } else {
                                            onItemClick(screen, selected)
                                        }
                                    }
                                }

                            ShortNavigationBarItem(
                                selected = selected,
                                onClick = onClick,
                                colors = if (canLiquidGlass && index == displayIndex) liquidGlassTransparentColors else itemColors,
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .onGloballyPositioned(

                                            remember(itemBounds, index) {
                                                { coordinates ->

                                                    val pos = coordinates.positionInRoot()
                                                    itemBounds[index] =
                                                        Rect(
                                                            pos.x,
                                                            pos.y,
                                                            pos.x + coordinates.size.width,
                                                            pos.y + coordinates.size.height,
                                                        )
                                                }
                                            },
                                        ),
                                icon = {
                                    Box(
                                        modifier =
                                            Modifier.onGloballyPositioned(

                                                remember(iconCenters, index) {
                                                    { coordinates ->
                                                        val pos = coordinates.positionInRoot()
                                                        iconCenters[index] =
                                                            Offset(
                                                                pos.x + coordinates.size.width / 2f,
                                                                pos.y + coordinates.size.height / 2f,
                                                            )
                                                    }
                                                },
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Crossfade(
                                            targetState = selected,
                                            animationSpec = motionScheme.fastEffectsSpec(),
                                            label = "navigationItemIcon",
                                        ) { isSelected ->
                                            Icon(
                                                painter =
                                                    painterResource(
                                                        if (isSelected) screen.iconIdActive else screen.iconIdInactive,
                                                    ),
                                                contentDescription = null,
                                                modifier =
                                                    Modifier.graphicsLayer {
                                                        scaleX = iconScale.value
                                                        scaleY = iconScale.value
                                                    },
                                            )
                                        }
                                    }
                                },
                                label = if (hideNavigationLabels) {
                                    null
                                } else {
                                    {
                                        if (canLiquidGlass) {
                                            val nightGlassLabelStyle =
                                                if (isSystemInDarkTheme()) {
                                                    LocalTextStyle.current.copy(
                                                        shadow =
                                                            Shadow(
                                                                color = Color.Black.copy(alpha = 0.8f),
                                                                blurRadius = 10f,
                                                            ),
                                                    )
                                                } else {
                                                    LocalTextStyle.current
                                                }
                                            Text(
                                                text = stringResource(screen.titleId),
                                                maxLines = 1,
                                                modifier = Modifier.offset(y = (-4).dp),

                                                fontWeight = FontWeight.Normal,
                                                style = nightGlassLabelStyle,
                                            )
                                        } else {
                                            Spacer(Modifier.height(navBarLabelSpacing.dp))
                                            Text(
                                                text = stringResource(screen.titleId),
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
            }

        }

        if (canLiquidGlass && selectedIndex >= 0 && indicatorPlaced && liquidGlassBackdrop != null) {
            val pillWidth = liquidGlassPillWidth
            val pillHeight = liquidGlassPillHeight
            val dragAnim = dampedDragAnimation
            if (pillWidth > 0.dp && pillHeight > 0.dp && dragAnim != null && tabWidthPx > 0f) {
                val pillShape = RoundedCornerShape(percent = 50)
                val isDark = isSystemInDarkTheme()

                val pillFallbackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                Box(
                    modifier =
                        Modifier
                            .offset {
                                val pillWidthPx = pillWidth.toPx()
                                val hPaddingPx = itemHorizontalPadding.toPx()

                                val tabSlotCenterForSelected =
                                    containerPos.x +
                                        itemsRowLeftInContainer +
                                        hPaddingPx +
                                        selectedIndex * tabWidthPx +
                                        tabWidthPx / 2f
                                val iconOffsetX =
                                    selectedCenter?.let { it.x - tabSlotCenterForSelected } ?: 0f
                                val xInRoot =
                                    containerPos.x +
                                        itemsRowLeftInContainer +
                                        hPaddingPx +
                                        dragAnim.value * tabWidthPx +
                                        (tabWidthPx - pillWidthPx) / 2f +
                                        panelOffset +
                                        iconOffsetX

                                val xRelativeToWrapper = xInRoot - barPositionInRoot.x
                                IntOffset(xRelativeToWrapper.roundToInt(), 0)
                            }
                            .width(pillWidth)
                            .height(pillHeight)
                            .graphicsLayer {
                                val pressScale = lerp(1f, 78f / 56f, dragAnim.pressProgress)
                                val velocityStretch = (dragAnim.velocity / 10f).fastCoerceIn(-0.2f, 0.2f)
                                scaleX = pressScale / (1f - velocityStretch * 0.75f)
                                scaleY = pressScale * (1f - velocityStretch * 0.25f)
                            }
                            .drawBackdrop(
                                backdrop = liquidGlassBackdrop,
                                effects = {
                                    colorControls(saturation = glassTuning.saturation)
                                    blur((4f * glassTuning.blurFactor).dp.toPx())
                                    lens(
                                        refractionHeight = minOf(
                                            (28f * glassTuning.refractionHeightFactor).dp.toPx(),
                                            size.minDimension / 2f,
                                        ),
                                        refractionAmount = size.minDimension * (glassTuning.refractionAmountFactor / 3.2f),
                                        depthEffect = glassTuning.depth3D,
                                        chromaticAberration = glassTuning.chromaticAberration,
                                    )
                                },

                                shadow = { if (isDark) KyantShadow.Default else null },
                                onDrawBackdrop = { drawBackdrop -> drawBackdrop() },
                                shape = { pillShape },

                                onDrawBehind = {
                                    drawRect(pillFallbackColor)
                                },
                                onDrawSurface = {
                                    val progress = dragAnim.pressProgress

                                    val tintColor = if (isDark) Color.Black else Color.White
                                    val restAlpha = (if (isDark) 0.28f else 0.1f) * glassTuning.tintFactor
                                    drawRect(
                                        color = tintColor,
                                        alpha = (restAlpha * (1f - progress)).coerceIn(0f, 1f),
                                    )
                                    drawRect(
                                        color = Color.Black,
                                        alpha = 0.03f * progress,
                                    )
                                },
                            )

                            .innerShadow(
                                shape = pillShape,
                                shadow = remember(dragAnim, pillShape) {
                                    {
                                        if (dragAnim.pressProgress > 0f) {
                                            InnerShadow(
                                                radius = 8.dp * dragAnim.pressProgress,
                                                color = Color.Black.copy(alpha = 0.15f),
                                                alpha = dragAnim.pressProgress,
                                            )
                                        } else {
                                            null
                                        }
                                    }
                                },
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    val displayScreen = items[displayIndex]
                    val pillContentColor =
                        if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                    ) {
                        Icon(
                            painter = painterResource(displayScreen.iconIdActive),
                            contentDescription = null,

                            tint = pillContentColor,
                            modifier =
                                Modifier.graphicsLayer {
                                    val scale = lerp(1f, 1.2f, dragAnim.pressProgress)
                                    scaleX = scale
                                    scaleY = scale
                                },
                        )
                        if (!hideNavigationLabels) {
                            Text(
                                text = stringResource(displayScreen.titleId),

                                color = pillContentColor,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style =
                                    if (isDark) {
                                        LocalTextStyle.current.copy(
                                            shadow =
                                                Shadow(
                                                    color = Color.Black.copy(alpha = 0.9f),
                                                    blurRadius = 12f,
                                                ),
                                        )
                                    } else {
                                        LocalTextStyle.current
                                    },
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
internal fun rememberPreSFrostedBitmap(
    backdrop: NavigationBarBackdrop?,
    barPositionInRoot: Offset,
    barSize: IntSize,
    blurRadiusPx: Float,
    updateIntervalMs: Long = 80L,
): ImageBitmap? {
    if (backdrop == null) return null
    var blurred by remember(backdrop, blurRadiusPx, updateIntervalMs) {
        mutableStateOf<ImageBitmap?>(null)
    }

    val barPositionState = rememberUpdatedState(barPositionInRoot)
    val barSizeState = rememberUpdatedState(barSize)

    LaunchedEffect(backdrop, blurRadiusPx, updateIntervalMs) {
        while (isActive) {
            val layer = backdrop.layer
            val layerW = layer.size.width
            val layerH = layer.size.height
            if (layerW > 0 && layerH > 0) {
                try {
                    val next = withContext(Dispatchers.Default) {
                        val pos = barPositionState.value
                        val size = barSizeState.value
                        if (size.width <= 0 || size.height <= 0) return@withContext null

                        val contentOffset = backdrop.contentOffsetInRoot
                        val rawX = (pos.x - contentOffset.x).toInt()
                        val rawY = (pos.y - contentOffset.y).toInt()

                        val pad = blurRadiusPx.toInt().coerceIn(8, 64)

                        val paddedX = rawX - pad
                        val paddedY = rawY - pad
                        val paddedW = size.width + 2 * pad
                        val paddedH = size.height + 2 * pad
                        val clampedX = paddedX.coerceIn(0, layerW - 1)
                        val clampedY = paddedY.coerceIn(0, layerH - 1)
                        val clampedRight = (paddedX + paddedW).coerceIn(1, layerW)
                        val clampedBottom = (paddedY + paddedH).coerceIn(1, layerH)
                        val clampedW = clampedRight - clampedX
                        val clampedH = clampedBottom - clampedY
                        if (clampedW <= 0 || clampedH <= 0) return@withContext null

                        val imageBitmap = layer.toImageBitmap()
                        val fullBitmap = imageBitmap.asAndroidBitmap()

                        val sliceBitmap = Bitmap.createBitmap(
                            fullBitmap,
                            clampedX,
                            clampedY,
                            clampedW,
                            clampedH,
                        )

                        val blurredSlice = ImageBlurUtils.blur(sliceBitmap, blurRadiusPx)

                        val barXInSlice = (rawX - clampedX).coerceIn(0, blurredSlice.width - 1)
                        val barYInSlice = (rawY - clampedY).coerceIn(0, blurredSlice.height - 1)
                        val barW = size.width.coerceAtMost(blurredSlice.width - barXInSlice)
                        val barH = size.height.coerceAtMost(blurredSlice.height - barYInSlice)
                        if (barW <= 0 || barH <= 0) {
                            blurredSlice.asImageBitmap()
                        } else if (barXInSlice == 0 && barYInSlice == 0 &&
                            blurredSlice.width == size.width && blurredSlice.height == size.height
                        ) {
                            blurredSlice.asImageBitmap()
                        } else {
                            Bitmap.createBitmap(blurredSlice, barXInSlice, barYInSlice, barW, barH)
                                .asImageBitmap()
                        }
                    }
                    if (next != null) blurred = next
                } catch (_: Throwable) {
                }
            }
            delay(updateIntervalMs)
        }
    }
    return blurred
}
