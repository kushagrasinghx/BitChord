package com.music.bitchord.ui.tv.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import com.music.bitchord.ui.tv.theme.appleSpring
import com.music.bitchord.ui.tv.theme.AppleSpringPreset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MusicNote
import com.music.bitchord.ui.tv.components.TvLiquidGlassSwitch
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.BuildConfig
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.MainViewModel
import com.music.bitchord.ui.tv.audio.TvSpatialAudioEngine
import com.music.bitchord.ui.tv.components.TvDialog
import com.music.bitchord.ui.tv.focus.tvButtonFocus
import com.music.bitchord.ui.tv.personalization.AppThemeOption
import com.music.bitchord.ui.tv.theme.LocalTvFontFamily
import com.music.bitchord.ui.tv.theme.TvColors
import com.music.bitchord.ui.tv.theme.TvDimensions
import com.music.bitchord.ui.tv.theme.TvSFProDisplay

/**
 * 1:1 Apple TV Settings Layout matching the exact reference image.
 *
 * - Left Pane: Red Squircle BitChord Music Icon card and dynamic explanatory description text.
 * - Right Pane: Grouped Pill Menu (LIBRARY, HOME SCREEN, AUDIO, CANVAS, ABOUT).
 * - Full color inversion on focus/hover (Solid White Pill, Pure Black Text).
 */
@Composable
fun TvSettingsScreen(
    viewModel: MainViewModel,
    onBack: (() -> Unit)? = null,
    onOpenAccountDialog: () -> Unit,
    onOpenDiscordDialog: () -> Unit,
    onOpenScrobbleDialog: () -> Unit,
    onOpenSourcesDialog: () -> Unit,
    onOpenRefreshRateDialog: () -> Unit,
    onOpenNicknameDialog: () -> Unit,
    onOpenThemeDialog: () -> Unit,
    onRunSetupAgain: () -> Unit,
    onOpenAboutDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentFont = LocalTvFontFamily.current

    val tvNickname by AppSettings.tvNickname.collectAsState()
    val tvThemeId by AppSettings.tvTheme.collectAsState()
    val currentTheme = AppThemeOption.fromId(tvThemeId)

    val spatialAudio by AppSettings.spatialAudioEnabled.collectAsState()
    val soundCheck by AppSettings.soundCheckEnabled.collectAsState()
    val liveCanvas by AppSettings.animatedCanvas.collectAsState()
    val automixEnabled by AppSettings.smartFadeEnabled.collectAsState()
    val syncedLyrics by AppSettings.syncedLyrics.collectAsState()
    val showNerdStats by AppSettings.showNerdStats.collectAsState()
    val addPlaylistSongs by AppSettings.addPlaylistSongsToLibrary.collectAsState()
    val addFavoriteSongs by AppSettings.addFavoriteSongsToLibrary.collectAsState()
    val skipSilence by AppSettings.skipSilence.collectAsState()
    val loudnessNormalization by AppSettings.loudnessNormalization.collectAsState()
    val preferUsbDac by AppSettings.preferUsbDac.collectAsState()
    val lyricsBlur by AppSettings.lyricsBlur.collectAsState()
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsState()
    val reduceAnimation by AppSettings.reduceAnimation.collectAsState()
    val autoplay by AppSettings.autoplay.collectAsState()
    val crossfadeSeconds by AppSettings.crossfadeSeconds.collectAsState()
    val tvUiScale by AppSettings.tvUiScale.collectAsState()
    val tvLiquidGlassEnabled by AppSettings.tvLiquidGlassEnabled.collectAsState()
    val tvBlurIntensity by AppSettings.tvBlurIntensity.collectAsState()
    val tvLyricsCanvasEnabled by AppSettings.tvLyricsCanvasEnabled.collectAsState()
    val tvNavLayout by AppSettings.tvNavLayout.collectAsState()
    val isLeftRail = tvNavLayout == "left_rail"

    var activeDescription by remember {
        mutableStateOf("Configure audio, spatial sound, video canvas, visual themes, and personalized living room preferences.")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = TvDimensions.SafeMarginHorizontal,
                end = TvDimensions.SafeMarginHorizontal,
                top = 22.dp,
                bottom = 24.dp,
            ),
    ) {
        val palette = com.music.bitchord.ui.tv.theme.TvThemeColors.current

        // Top Header with optional Back button & centered Title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .tvButtonFocus(
                            shape = CircleShape,
                            focusedScale = 1.15f,
                            onClick = onBack,
                        )
                        .background(palette.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = palette.textPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
            }

            Text(
                text = "Music & TV Settings",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = currentFont,
                color = palette.textPrimary,
            )
        }

        // Split 2-Pane Apple TV Layout
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ── LEFT HERO PANE (1:1 Apple TV Squircle & Description) ───────────
            Column(
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // Apple TV Luxury Squircle Card (Monochrome Glass & Pure White)
                Box(
                    modifier = Modifier
                        .size(width = 240.dp, height = 160.dp)
                        .shadow(28.dp, RoundedCornerShape(32.dp), spotColor = Color.White.copy(alpha = 0.20f))
                        .clip(RoundedCornerShape(32.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF2C2C2E),
                                    Color(0xFF1C1C1E),
                                    Color(0xFF141416),
                                ),
                            ),
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(32.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_logo),
                        contentDescription = "BitChord Logo",
                        tint = Color.White,
                        modifier = Modifier.size(76.dp),
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Dynamic Caption / Explanation
                Text(
                    text = activeDescription,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    fontFamily = currentFont,
                    color = palette.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }

            // ── RIGHT PANE: CATEGORIZED APPLE TV PILL LIST ───────────────────
            LazyColumn(
                modifier = Modifier
                    .weight(0.58f)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // ── SECTION: LIBRARY ──
                item {
                    TvSettingsSectionHeader(title = "LIBRARY")
                }
                item {
                    TvApplePillSwitchOption(
                        title = "Add Playlist Songs to Library",
                        checked = addPlaylistSongs,
                        onCheckedChange = { AppSettings.setAddPlaylistSongsToLibrary(it) },
                        onFocus = {
                            activeDescription = "Songs will be automatically added to your library when you add them to your playlists."
                        },
                    )
                }
                item {
                    TvApplePillSwitchOption(
                        title = "Add Favorite Songs to Library",
                        checked = addFavoriteSongs,
                        onCheckedChange = { AppSettings.setAddFavoriteSongsToLibrary(it) },
                        onFocus = {
                            activeDescription = "Liked tracks and heart favorites will be saved to your primary music library."
                        },
                    )
                }

                // ── SECTION: AUDIO & SPATIAL ──
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    TvSettingsSectionHeader(title = "AUDIO & SPATIAL")
                }
                item {
                    TvApplePillSwitchOption(
                        title = "Spatial Audio & 3D Virtualization",
                        checked = spatialAudio,
                        onCheckedChange = { next ->
                            AppSettings.setSpatialAudioEnabled(next)
                            TvSpatialAudioEngine.setEnabled(next)
                        },
                        onFocus = {
                            activeDescription = "Expands stereo music into an immersive 3D Dolby Atmos soundstage for TV speakers and soundbars."
                        },
                    )
                }
                item {
                    TvApplePillOption(
                        title = "Audio Quality",
                        value = "Lossless >",
                        onFocus = {
                            activeDescription = "Streams bit-exact Hi-Res FLAC and studio master quality audio from configured sources."
                        },
                        onClick = onOpenSourcesDialog,
                    )
                }
                item {
                    TvApplePillSwitchOption(
                        title = "Sound Check (Loudness Match)",
                        checked = soundCheck,
                        onCheckedChange = { AppSettings.setSoundCheckEnabled(it) },
                        onFocus = {
                            activeDescription = "Maintains consistent loudness volume across all albums and music sources."
                        },
                    )
                }
                item {
                    TvApplePillSwitchOption(
                        title = "EBU R128 Normalization",
                        checked = loudnessNormalization,
                        onCheckedChange = { AppSettings.setLoudnessNormalization(it) },
                        onFocus = {
                            activeDescription = "Standardizes perceived audio loudness according to international broadcast standards."
                        },
                    )
                }
                item {
                    TvApplePillSwitchOption(
                        title = "Skip Silence",
                        checked = skipSilence,
                        onCheckedChange = { AppSettings.setSkipSilence(it) },
                        onFocus = {
                            activeDescription = "Intelligently skips silent gaps at the beginning and end of music tracks."
                        },
                    )
                }
                item {
                    TvApplePillOption(
                        title = "Crossfade Duration",
                        value = if (crossfadeSeconds > 0) "${crossfadeSeconds}s" else "Off",
                        onFocus = {
                            activeDescription = "Crossfades between songs so playback flows continuously without interruptions."
                        },
                        onClick = {
                            val next = when (crossfadeSeconds) {
                                0 -> 3
                                3 -> 6
                                6 -> 10
                                else -> 0
                            }
                            AppSettings.setCrossfadeSeconds(next)
                        },
                    )
                }
                item {
                    TvApplePillSwitchOption(
                        title = "Automix DJ Transitions",
                        checked = automixEnabled,
                        onCheckedChange = { AppSettings.setSmartFadeEnabled(it) },
                        onFocus = {
                            activeDescription = "Beat-matches and smoothly mixes transitions between consecutive songs using on-device DSP."
                        },
                    )
                }
                item {
                    TvApplePillSwitchOption(
                        title = "Bit-Perfect USB DAC",
                        checked = preferUsbDac,
                        onCheckedChange = { AppSettings.setPreferUsbDac(it) },
                        onFocus = {
                            activeDescription = "Sends bit-exact PCM stream directly to external USB DACs, bypassing the Android TV mixer."
                        },
                    )
                }

                // ── SECTION: LIVE VIDEO CANVAS ──
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    TvSettingsSectionHeader(title = "CANVAS & VISUALS")
                }
                item {
                    TvApplePillSwitchOption(
                        title = "Live Video Canvas",
                        checked = liveCanvas,
                        onCheckedChange = { AppSettings.setAnimatedCanvas(it) },
                        onFocus = {
                            activeDescription = "Plays looping artist motion video artwork behind the player. Shows notification when unavailable."
                        },
                    )
                }
                item {
                    TvApplePillSwitchOption(
                        title = "Canvas in Lyrics Mode",
                        checked = tvLyricsCanvasEnabled,
                        onCheckedChange = { AppSettings.setTvLyricsCanvasEnabled(it) },
                        onFocus = {
                            activeDescription = "Displays live looping canvas video artwork behind synchronized lyrics in the Now Playing overlay."
                        },
                    )
                }
                item {
                    TvApplePillSwitchOption(
                        title = "Synchronized Lyrics",
                        checked = syncedLyrics,
                        onCheckedChange = { AppSettings.setSyncedLyrics(it) },
                        onFocus = {
                            activeDescription = "Real-time word-by-word and syllable flowing highlight lyrics with background vocal support."
                        },
                    )
                }
                item {
                    TvApplePillSwitchOption(
                        title = "Autoplay Similar Music",
                        checked = autoplay,
                        onCheckedChange = { AppSettings.setAutoplay(it) },
                        onFocus = {
                            activeDescription = "Keeps the music playing by recommending similar artists and songs when your queue ends."
                        },
                    )
                }
                item {
                    TvApplePillSwitchOption(
                        title = "Lyrics Backdrop Blur",
                        checked = lyricsBlur,
                        onCheckedChange = { AppSettings.setLyricsBlur(it) },
                        onFocus = {
                            activeDescription = "Renders an atmospheric frosted glass reflection behind the synchronized lyrics panel."
                        },
                    )
                }
                item {
                    TvApplePillSwitchOption(
                        title = "Stats for Nerds HUD",
                        checked = showNerdStats,
                        onCheckedChange = { AppSettings.setShowNerdStats(it) },
                        onFocus = {
                            activeDescription = "Displays live codec, bitrate, sample rate, and audio resolution telemetry on the player."
                        },
                    )
                }

                // ── SECTION: PERFORMANCE & LOW-END TV OPTIMIZATION ──
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    TvSettingsSectionHeader(title = "PERFORMANCE & TV HARDWARE")
                }
                item {
                    TvApplePillSwitchOption(
                        title = "Low-End TV GPU Optimization",
                        checked = reduceDynamicBlur,
                        onCheckedChange = { AppSettings.setReduceDynamicBlur(it) },
                        onFocus = {
                            activeDescription = "Replaces expensive full-frame GPU blur shaders with high-speed acrylic scrims for smooth 60fps on budget TVs."
                        },
                    )
                }
                item {
                    TvApplePillSwitchOption(
                        title = "Reduce Motion",
                        checked = reduceAnimation,
                        onCheckedChange = { AppSettings.setReduceAnimation(it) },
                        onFocus = {
                            activeDescription = "Simplifies decorative animations to conserve processor cycles on low-power TV chipsets."
                        },
                    )
                }

                // ── SECTION: THEME & DISPLAY ──
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    TvSettingsSectionHeader(title = "THEME & DISPLAY")
                }
                item {
                    TvApplePillSwitchOption(
                        title = "Spotify Sidebar Navigation",
                        checked = isLeftRail,
                        onCheckedChange = { isChecked ->
                            AppSettings.setTvNavLayout(if (isChecked) "left_rail" else "top")
                        },
                        onFocus = {
                            activeDescription = "Displays a sleek vertical navigation sidebar on the left side (Spotify TV style). Turn off for classic Apple TV top pill navigation."
                        },
                    )
                }
                item {
                    TvApplePillOption(
                        title = "Visual Theme",
                        value = "${currentTheme.title} >",
                        onFocus = {
                            activeDescription = "Select Dynamic Artwork mesh gradient, Midnight Dark, or True #000000 OLED Pure Black."
                        },
                        onClick = onOpenThemeDialog,
                    )
                }
                item {
                    TvApplePillSwitchOption(
                        title = "Liquid Glass Aesthetics",
                        checked = tvLiquidGlassEnabled,
                        onCheckedChange = { AppSettings.setTvLiquidGlassEnabled(it) },
                        onFocus = {
                            activeDescription = "Translucent frosted acrylics with glowing specular highlights and Apple spring physics."
                        },
                    )
                }
                item {
                    TvApplePillOption(
                        title = "Blur Intensity",
                        value = "${(tvBlurIntensity * 100).toInt()}%",
                        onFocus = {
                            activeDescription = "Adjust global frosted acrylic blur intensity from 0% (solid performance fallback) to 100% (deep glass blur)."
                        },
                        onClick = {
                            val next = when {
                                tvBlurIntensity < 0.20f -> 0.25f
                                tvBlurIntensity < 0.45f -> 0.50f
                                tvBlurIntensity < 0.70f -> 0.75f
                                tvBlurIntensity < 0.84f -> 0.85f
                                tvBlurIntensity < 0.95f -> 1.00f
                                else -> 0.0f
                            }
                            AppSettings.setTvBlurIntensity(next)
                        },
                    )
                }
                item {
                    TvApplePillOption(
                        title = "TV Display Refresh Rate",
                        value = "Configure >",
                        onFocus = {
                            activeDescription = "Adjust display refresh rate mode: 120Hz Ultra-Performance, 60Hz, or Cinematic Match."
                        },
                        onClick = onOpenRefreshRateDialog,
                    )
                }

                // ── SECTION: DPI / UI SCALE ──
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    TvSettingsSectionHeader(title = "DPI & UI SCALE")
                }
                item {
                    val scaleLabel = when {
                        tvUiScale <= 0.85f -> "80% (Small)"
                        tvUiScale <= 0.95f -> "90% (Small-Normal)"
                        tvUiScale <= 1.05f -> "100% (Standard)"
                        tvUiScale <= 1.15f -> "110% (Large)"
                        tvUiScale <= 1.25f -> "120% (Large Plus)"
                        tvUiScale <= 1.35f -> "130% (Extra Large)"
                        else -> "140% (Maximum)"
                    }
                    TvApplePillOption(
                        title = "UI Scale (Cycle 80% – 140%)",
                        value = scaleLabel,
                        onFocus = {
                            activeDescription = "Unified scale for all text, cards, icons, and menus. Click to cycle through scale steps."
                        },
                        onClick = {
                            val next = when {
                                tvUiScale < 0.85f -> 0.90f
                                tvUiScale < 0.95f -> 1.00f
                                tvUiScale < 1.05f -> 1.10f
                                tvUiScale < 1.15f -> 1.20f
                                tvUiScale < 1.25f -> 1.30f
                                tvUiScale < 1.35f -> 1.40f
                                else -> 0.80f
                            }
                            AppSettings.setTvUiScale(next)
                        },
                    )
                }
                item {
                    TvApplePillOption(
                        title = "TV Nickname",
                        value = "\"$tvNickname\" >",
                        onFocus = {
                            activeDescription = "Change the living room name and greeting for this TV."
                        },
                        onClick = onOpenNicknameDialog,
                    )
                }

                // ── SECTION: ACCOUNT & ABOUT ──
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    TvSettingsSectionHeader(title = "ACCOUNT & ABOUT")
                }
                item {
                    TvApplePillOption(
                        title = "Google & YouTube Account",
                        value = "Sign In >",
                        onFocus = {
                            activeDescription = "Sign in directly with your Google Account on TV or via phone to access your personalized library, playlists, and recommendations."
                        },
                        onClick = onOpenAccountDialog,
                    )
                }
                item {
                    TvApplePillOption(
                        title = "Discord & Scrobbling",
                        value = "Manage >",
                        onFocus = {
                            activeDescription = "Broadcast playing tracks to Discord Rich Presence and scrobble to Last.fm / ListenBrainz."
                        },
                        onClick = onOpenDiscordDialog,
                    )
                }
                item {
                    TvApplePillOption(
                        title = "Run Setup Assistant Again",
                        value = "Start >",
                        onFocus = {
                            activeDescription = "Reconfigure nickname, theme, and first-run TV setup."
                        },
                        onClick = onRunSetupAgain,
                    )
                }
                item {
                    TvApplePillOption(
                        title = "About BitChord TV",
                        value = "v${BuildConfig.VERSION_NAME} >",
                        onFocus = {
                            activeDescription = "BitChord TV v${BuildConfig.VERSION_NAME} • Main Dev: Kushagra Singh (@kushagrasinghx) • TV Architecture: Nithyanantha (@nimalanrao)."
                        },
                        onClick = onOpenAboutDialog,
                    )
                }
            }
        }
    }
}

/**
 * Apple TV Section Header in uppercase letter-spaced small text.
 */
@Composable
private fun TvSettingsSectionHeader(title: String) {
    val palette = com.music.bitchord.ui.tv.theme.TvThemeColors.current
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        fontFamily = LocalTvFontFamily.current,
        color = palette.textMuted,
        modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 4.dp),
    )
}

/**
 * 1:1 Apple TV Settings Pill Option with high-contrast sharp color inversion.
 * (Unfocused: theme surface with primary text; Focused: high-contrast inverted pill).
 */
@Composable
private fun TvApplePillOption(
    title: String,
    value: String,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = com.music.bitchord.ui.tv.theme.TvThemeColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    androidx.compose.runtime.LaunchedEffect(isFocused) {
        if (isFocused) onFocus()
    }

    val targetScale = when {
        isPressed -> 0.96f
        isFocused -> 1.03f
        else -> 1.0f
    }

    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = appleSpring(AppleSpringPreset.Snappy),
        label = "pillScale",
    )

    val unfocusedBg = if (palette.isDark) Color.White.copy(alpha = 0.12f) else palette.surfaceVariant
    val focusedBg = if (palette.isDark) Color.White else Color(0xFF1C1C1E)

    val animatedBg by animateColorAsState(
        targetValue = if (isFocused) focusedBg else unfocusedBg,
        animationSpec = appleSpring(AppleSpringPreset.Snappy),
        label = "pillBg",
    )

    val titleColor = if (isFocused) {
        if (palette.isDark) Color.Black else Color.White
    } else {
        palette.textPrimary
    }

    val valueColor = if (isFocused) {
        if (palette.isDark) Color(0xFF222222) else Color(0xFFD1D1D6)
    } else {
        palette.textSecondary
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(24.dp))
            .background(animatedBg)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
            fontFamily = LocalTvFontFamily.current,
            color = titleColor,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = value,
            fontSize = 15.sp,
            fontFamily = LocalTvFontFamily.current,
            fontWeight = if (isFocused) FontWeight.W800 else FontWeight.Normal,
            color = valueColor,
        )
    }
}

/**
 * Apple TV Pill Option with integrated Liquid Glass Toggle Switch.
 */
@Composable
private fun TvApplePillSwitchOption(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = com.music.bitchord.ui.tv.theme.TvThemeColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    androidx.compose.runtime.LaunchedEffect(isFocused) {
        if (isFocused) onFocus()
    }

    val targetScale = when {
        isPressed -> 0.97f
        isFocused -> 1.03f
        else -> 1.0f
    }

    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = appleSpring(AppleSpringPreset.Snappy),
        label = "switchPillScale",
    )

    val unfocusedBg = if (palette.isDark) Color.White.copy(alpha = 0.10f) else palette.surfaceVariant
    val focusedBg = if (palette.isDark) Color.White.copy(alpha = 0.22f) else Color(0xFF1C1C1E)

    val animatedBg by animateColorAsState(
        targetValue = if (isFocused) focusedBg else unfocusedBg,
        animationSpec = appleSpring(AppleSpringPreset.Snappy),
        label = "switchPillBg",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(24.dp))
            .background(animatedBg)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(24.dp),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onCheckedChange(!checked) },
            )
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
            fontFamily = LocalTvFontFamily.current,
            color = Color.White,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        TvLiquidGlassSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

