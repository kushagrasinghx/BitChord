/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

val LocalMenuState = compositionLocalOf { MenuState() }

val LocalMenuDialogDismissal = staticCompositionLocalOf<(() -> Unit)?> { null }

@Stable
class MenuState(
    isVisible: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    var isVisible by mutableStateOf(isVisible)
    var content by mutableStateOf(content)
    internal var dialogContent by mutableStateOf<(@Composable () -> Unit)?>(null)
        private set

    fun show(content: @Composable ColumnScope.() -> Unit) {
        dialogContent = null
        isVisible = true
        this.content = content
    }

    fun dismiss() {
        isVisible = false
    }

    fun showDialog(content: @Composable () -> Unit) {
        isVisible = false
        dialogContent = content
    }

    fun dismissDialog() {
        dialogContent = null
    }
}

@Composable
fun BottomSheetMenu(
    modifier: Modifier = Modifier,
    state: MenuState,
    background: Color = Color.Unspecified,
) {
    val focusManager = LocalFocusManager.current

    state.dialogContent?.invoke()

    var renderState by remember { mutableStateOf(false) }
    val enterProgress = remember { Animatable(0f) }

    val scrimInteractionSource = remember { MutableInteractionSource() }
    val popupInteractionSource = remember { MutableInteractionSource() }

    LaunchedEffect(state.isVisible) {
        if (state.isVisible) {
            renderState = true
            enterProgress.snapTo(0f)
            enterProgress.animateTo(
                targetValue = 1f,
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
            )
        } else if (renderState) {
            enterProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 200),
            )
            focusManager.clearFocus()
            renderState = false
        }
    }

    val menuPredictiveBack = rememberPredictiveSheetBackState()
    PredictiveStyleBackHandler(
        backState = menuPredictiveBack,
        enabled = state.dialogContent != null,
        onBack = state::dismissDialog,
    )

    if (!renderState) return

    PredictiveStyleBackHandler(
        backState = menuPredictiveBack,
        enabled = renderState,
        onBack = { state.isVisible = false },
    )

    val alpha = enterProgress.value

    val menuGlassBackdrop = LocalMenuGlassBackdrop.current
    val liquidGlassBackdrop = menuGlassBackdrop ?: LocalLiquidGlassBackdrop.current
    val glassTuning = LocalLiquidGlassTuning.current
    val glassModifier =
        remember(liquidGlassBackdrop, glassTuning) {
            if (liquidGlassBackdrop != null && background.isUnspecified) {
                Modifier.drawBackdrop(
                    backdrop = liquidGlassBackdrop,
                    effects = {
                        colorControls(saturation = glassTuning.saturation)

                        blur((20f * glassTuning.blurFactor).dp.toPx())

                        lens(
                            refractionHeight = minOf(
                                (16f * glassTuning.refractionHeightFactor).dp.toPx(),
                                size.minDimension / 2f,
                            ),
                            refractionAmount = (40f * glassTuning.refractionAmountFactor).dp.toPx(),
                            depthEffect = glassTuning.depth3D,
                            chromaticAberration = glassTuning.chromaticAberration,
                        )
                    },
                    onDrawBackdrop = { drawBackdrop ->
                        drawBackdrop()
                    },
                    shape = { FloatingMenuShape },
                )
            } else {
                null
            }
        }

    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val glassTint =
        if (dark) {
            Color(0x8C1C1C1E).copy(alpha = (0.55f * glassTuning.tintFactor).coerceIn(0f, 1f))
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = (0.42f * glassTuning.tintFactor).coerceIn(0f, 1f))
        }

    val fallbackColor =
        when {
            !background.isUnspecified -> background
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        }

    val fallbackBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    val contentInk =
        if (dark) {
            Color.White
        } else {
            Color(0xFF1C1B1F)
        }

    val glassColorScheme =
        MaterialTheme.colorScheme.copy(
            onSurface = contentInk,
            onBackground = contentInk,
            onSurfaceVariant = contentInk.copy(alpha = 0.72f),
            surfaceContainerLow = Color.Transparent,
            surfaceContainer = Color.Transparent,
            surfaceContainerHigh =
                if (dark) {
                    Color.White.copy(alpha = 0.08f)
                } else {
                    Color.Black.copy(alpha = 0.05f)
                },
            surfaceContainerHighest =
                if (dark) {
                    Color.White.copy(alpha = 0.14f)
                } else {
                    Color.Black.copy(alpha = 0.08f)
                },
            outlineVariant = contentInk.copy(alpha = 0.12f),
            error = Color(0xFFFF453A),
        )

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    val maxPopupHeight = configuration.screenHeightDp.dp * 0.40f
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { this.alpha = alpha }
                    .background(Color.Black.copy(alpha = 0.50f))
                    .clickable(
                        interactionSource = scrimInteractionSource,
                        indication = null,
                    ) {
                        state.isVisible = false
                    },
        )

        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = bottomInset + 12.dp)
                    .widthIn(max = 640.dp)
                    .heightIn(max = maxPopupHeight)
                    .fillMaxWidth()
                    .graphicsLayer {
                        this.alpha = alpha

                        translationY = with(density) { (1f - alpha) * 48.dp.toPx() }
                    }
                    .shadow(
                        elevation = (24f * glassTuning.shadowFactor).dp,
                        shape = FloatingMenuShape,
                        clip = false,
                    )
                    .then(
                        if (glassModifier != null) {
                            glassModifier.background(glassTint)
                        } else {
                            Modifier

                                .background(fallbackColor, FloatingMenuShape)
                                .border(1.dp, fallbackBorderColor, FloatingMenuShape)
                        },
                    )
                    .clip(FloatingMenuShape)
                    .clickable(
                        interactionSource = popupInteractionSource,
                        indication = null,
                    ) {
                    },
        ) {
            val unglassedColorScheme = MaterialTheme.colorScheme

            val useGlassInk = glassModifier != null || !background.isUnspecified
            val menuContent: @Composable () -> Unit = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    state.content(this)
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                if (glassModifier == null) {
                    Box(
                        modifier =
                            Modifier
                                .padding(top = 10.dp, bottom = 6.dp)
                                .size(width = 32.dp, height = 4.dp)
                                .clip(RoundedCornerShape(percent = 50))
                                .background(
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                )
                                .align(Alignment.CenterHorizontally),
                    )
                }

                CompositionLocalProvider(
                    LocalContentColor provides if (useGlassInk) contentInk else unglassedColorScheme.onSurface,

                    LocalGlassMenuContent provides (glassModifier != null),

                    LocalUnglassColorScheme provides unglassedColorScheme,

                    LocalMenuDialogDismissal provides ({ state.isVisible = false }),
                ) {
                    if (useGlassInk) {
                        MaterialTheme(colorScheme = glassColorScheme) {
                            menuContent()
                        }
                    } else {
                        menuContent()
                    }
                }
            }
        }
    }
}

private val FloatingMenuShape = RoundedCornerShape(28.dp)
