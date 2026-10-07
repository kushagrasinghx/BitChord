package com.music.bitchord.ui.tv.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.ui.tv.TvDestination
import com.music.bitchord.ui.tv.theme.AppleSpringPreset
import com.music.bitchord.ui.tv.theme.TvThemeColors
import com.music.bitchord.ui.tv.theme.appleSpring

/**
 * 1:1 Spotify TV style ultra-clean slim vertical navigation sidebar rail.
 *
 * Placed on the left edge of the screen:
 * - Top: BitChord TV monochrome logo squircle
 * - Center: Vertical icon stack (Home, Search, Library, Settings)
 * - Bottom: Equalizer / Now Playing indicator icon
 *
 * Remote TV Navigation:
 * - Up/Down navigates between navigation items
 * - Right exits the sidebar into the main content screen
 * - Focused state: Inverted high-contrast pure white pill with snappy Apple spring physics
 */
@Composable
fun TvLeftNavRail(
    activeDestination: TvDestination,
    hasNowPlaying: Boolean,
    isPlaying: Boolean,
    onDestinationSelected: (TvDestination) -> Unit,
    onOpenNowPlaying: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = TvThemeColors.current

    Box(
        modifier = modifier
            .width(76.dp)
            .fillMaxHeight()
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF090A0F).copy(alpha = 0.98f),
                        Color(0xFF0C0E14).copy(alpha = 0.92f),
                    )
                )
            ),
    ) {
        // Subtle 1px right glass border separator
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .align(Alignment.CenterEnd)
                .background(Color.White.copy(alpha = 0.08f))
        )

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(76.dp)
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // ── TOP: APP LOGO SQUIRCLE ──
            val logoInteraction = remember { MutableInteractionSource() }
            val logoFocused by logoInteraction.collectIsFocusedAsState()

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        if (logoFocused) Color.White else Color.White.copy(alpha = 0.12f)
                    )
                    .border(
                        width = if (logoFocused) 2.dp else 1.dp,
                        color = if (logoFocused) Color.White else Color.White.copy(alpha = 0.15f),
                        shape = CircleShape,
                    )
                    .focusable(interactionSource = logoInteraction)
                    .clickable(
                        interactionSource = logoInteraction,
                        indication = null,
                        onClick = { onDestinationSelected(TvDestination.FOR_YOU) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_logo),
                    contentDescription = "BitChord Logo",
                    tint = if (logoFocused) Color(0xFF090A0F) else Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }

            // ── CENTER: NAVIGATION ITEMS ──
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TvRailIconItem(
                    icon = Icons.Default.Home,
                    contentDescription = "Home",
                    isSelected = activeDestination == TvDestination.FOR_YOU,
                    onClick = { onDestinationSelected(TvDestination.FOR_YOU) },
                )

                TvRailIconItem(
                    icon = Icons.Default.Search,
                    contentDescription = "Search",
                    isSelected = activeDestination == TvDestination.SEARCH,
                    onClick = { onDestinationSelected(TvDestination.SEARCH) },
                )

                TvRailIconItem(
                    icon = Icons.Default.LibraryMusic,
                    contentDescription = "Library",
                    isSelected = activeDestination == TvDestination.LIBRARY,
                    onClick = { onDestinationSelected(TvDestination.LIBRARY) },
                )

                TvRailIconItem(
                    icon = Icons.Default.Settings,
                    contentDescription = "Settings",
                    isSelected = activeDestination == TvDestination.SETTINGS,
                    onClick = { onDestinationSelected(TvDestination.SETTINGS) },
                )
            }

            // ── BOTTOM: NOW PLAYING / EQUALIZER SHORTCUT ──
            val eqInteraction = remember { MutableInteractionSource() }
            val eqFocused by eqInteraction.collectIsFocusedAsState()
            val eqPressed by eqInteraction.collectIsPressedAsState()

            val eqScale by animateFloatAsState(
                targetValue = when {
                    eqPressed -> 0.94f
                    eqFocused -> 1.12f
                    else -> 1.0f
                },
                animationSpec = appleSpring(AppleSpringPreset.Snappy),
                label = "eqScale",
            )

            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = eqScale
                        scaleY = eqScale
                    }
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        when {
                            eqFocused -> Color.White
                            hasNowPlaying -> Color.White.copy(alpha = 0.16f)
                            else -> Color.Transparent
                        }
                    )
                    .border(
                        width = if (eqFocused) 2.dp else 1.dp,
                        color = when {
                            eqFocused -> Color.White
                            hasNowPlaying -> Color.White.copy(alpha = 0.20f)
                            else -> Color.Transparent
                        },
                        shape = RoundedCornerShape(14.dp),
                    )
                    .focusable(interactionSource = eqInteraction)
                    .clickable(
                        interactionSource = eqInteraction,
                        indication = null,
                        onClick = onOpenNowPlaying,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (hasNowPlaying && isPlaying) {
                    TvMiniEqualizer(
                        isPlaying = true,
                        barColor = if (eqFocused) Color(0xFF090A0F) else Color.White,
                        maxHeight = 16.dp,
                        barWidth = 3.dp,
                        barSpacing = 2.5.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Equalizer,
                        contentDescription = "Now Playing",
                        tint = if (eqFocused) Color(0xFF090A0F) else Color.White.copy(alpha = if (hasNowPlaying) 0.9f else 0.45f),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

/**
 * Individual Spotify TV style icon button in the left navigation sidebar.
 */
@Composable
private fun TvRailIconItem(
    icon: ImageVector,
    contentDescription: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.94f
            isFocused -> 1.14f
            else -> 1.0f
        },
        animationSpec = appleSpring(AppleSpringPreset.Snappy),
        label = "railItemScale",
    )

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.White
            isSelected -> Color.White.copy(alpha = 0.22f)
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 150),
        label = "railItemBg",
    )

    val iconColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color(0xFF090A0F) // High-contrast inverted dark on white
            isSelected -> Color.White
            else -> Color.White.copy(alpha = 0.45f)
        },
        animationSpec = tween(durationMillis = 150),
        label = "railItemIconColor",
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .size(46.dp)
            .shadow(
                elevation = if (isFocused) 12.dp else 0.dp,
                shape = RoundedCornerShape(14.dp),
                spotColor = Color.White.copy(alpha = 0.35f),
            )
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundColor)
            .border(
                width = if (isFocused) 2.dp else if (isSelected) 1.dp else 0.dp,
                color = if (isFocused) Color.White else if (isSelected) Color.White.copy(alpha = 0.25f) else Color.Transparent,
                shape = RoundedCornerShape(14.dp),
            )
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(24.dp),
        )
    }
}
