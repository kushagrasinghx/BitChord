package com.music.bitchord.ui.tv.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.collectAsState
import com.music.bitchord.ui.tv.focus.tvButtonFocus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import com.music.bitchord.ui.tv.components.TvLiquidGlassSwitch
import androidx.compose.material3.Text
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.TvSettings
import com.music.bitchord.ui.tv.components.TvButton
import com.music.bitchord.ui.tv.focus.onTvKeyEvent
import com.music.bitchord.ui.tv.focus.tvButtonFocus
import com.music.bitchord.ui.tv.personalization.AppThemeOption
import com.music.bitchord.ui.tv.personalization.NicknamePolicy
import com.music.bitchord.ui.tv.personalization.getPalette
import com.music.bitchord.ui.tv.theme.LocalTvFontFamily
import com.music.bitchord.ui.tv.theme.TvColors
import com.music.bitchord.ui.tv.theme.TvDimensions
import com.music.bitchord.ui.tv.theme.TvSFProDisplay

enum class SetupStep {
    WELCOME,
    LOGIN,
    NICKNAME,
    THEME,
    CUSTOMIZATION,
    COMPLETE
}

@Composable
fun TvSetupScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentStep by remember { mutableStateOf(SetupStep.WELCOME) }
    var draftNickname by remember { mutableStateOf(TvSettings.tvNickname.value.ifBlank { "Living Room TV" }) }
    var draftTheme by remember { mutableStateOf(AppThemeOption.fromId(TvSettings.tvTheme.value)) }

    val activePalette = draftTheme.getPalette()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(activePalette.background)
            .padding(
                start = TvDimensions.SafeMarginHorizontal,
                end = TvDimensions.SafeMarginHorizontal,
                top = TvDimensions.SafeMarginVertical,
                bottom = TvDimensions.SafeMarginVertical,
            )
            .onTvKeyEvent(
                onBack = {
                    when (currentStep) {
                        SetupStep.WELCOME -> false
                        SetupStep.LOGIN -> { currentStep = SetupStep.WELCOME; true }
                        SetupStep.NICKNAME -> { currentStep = SetupStep.LOGIN; true }
                        SetupStep.THEME -> { currentStep = SetupStep.NICKNAME; true }
                        SetupStep.CUSTOMIZATION -> { currentStep = SetupStep.THEME; true }
                        SetupStep.COMPLETE -> { currentStep = SetupStep.CUSTOMIZATION; true }
                    }
                },
            ),
    ) {
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                if (targetState.ordinal > initialState.ordinal) {
                    (slideInHorizontally { it / 2 } + fadeIn()).togetherWith(slideOutHorizontally { -it / 2 } + fadeOut())
                } else {
                    (slideInHorizontally { -it / 2 } + fadeIn()).togetherWith(slideOutHorizontally { it / 2 } + fadeOut())
                }
            },
            label = "setupStepTransition",
            modifier = Modifier.fillMaxSize(),
        ) { step ->
            when (step) {
                SetupStep.WELCOME -> {
                    TvWelcomeStep(
                        onStartSetup = { currentStep = SetupStep.LOGIN },
                        onUseDefaults = {
                            try {
                                TvSettings.setTvPersonalization(
                                    nickname = NicknamePolicy.DEFAULT_NICKNAME,
                                    themeId = AppThemeOption.DYNAMIC_ARTWORK.id,
                                    version = 1,
                                )
                            } catch (e: Exception) {
                                android.util.Log.e("TvSetup", "Failed to save personalization", e)
                            }
                            onComplete()
                        },
                    )
                }
                SetupStep.LOGIN -> {
                    TvLoginStep(
                        onContinue = { currentStep = SetupStep.NICKNAME }, // Real login logic would go here
                        onSkip = { currentStep = SetupStep.NICKNAME },
                    )
                }
                SetupStep.NICKNAME -> {
                    TvNicknameStep(
                        nickname = draftNickname,
                        onNicknameSelect = { draftNickname = it },
                        onContinue = { currentStep = SetupStep.THEME },
                        onSkip = {
                            draftNickname = NicknamePolicy.DEFAULT_NICKNAME
                            currentStep = SetupStep.THEME
                        },
                    )
                }
                SetupStep.THEME -> {
                    TvThemeStep(
                        selectedTheme = draftTheme,
                        onThemeSelect = { draftTheme = it },
                        onContinue = { currentStep = SetupStep.CUSTOMIZATION },
                    )
                }
                SetupStep.CUSTOMIZATION -> {
                    TvCustomizationStep(
                        onContinue = { currentStep = SetupStep.COMPLETE },
                        onBack = { currentStep = SetupStep.THEME },
                    )
                }
                SetupStep.COMPLETE -> {
                    TvSetupCompleteStep(
                        nickname = draftNickname,
                        theme = draftTheme,
                        onFinish = {
                            try {
                                TvSettings.setTvPersonalization(
                                    nickname = draftNickname.ifBlank { NicknamePolicy.DEFAULT_NICKNAME },
                                    themeId = draftTheme.id,
                                    version = 1,
                                )
                            } catch (e: Exception) {
                                android.util.Log.e("TvSetup", "Failed to save personalization", e)
                            }
                            onComplete()
                        },
                        onChangeChoices = { currentStep = SetupStep.NICKNAME },
                    )
                }
            }
        }
    }
}

@Composable
private fun TvWelcomeStep(
    onStartSetup: () -> Unit,
    onUseDefaults: () -> Unit,
) {
    val currentFont = LocalTvFontFamily.current

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left Column: Branding & Value Proposition
        Column(
            modifier = Modifier.weight(0.55f),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // BitChord Logo Badge (No pink, sleek white icon)
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_logo),
                    contentDescription = "BitChord Logo",
                    tint = Color.White,
                    modifier = Modifier.size(38.dp),
                )
            }

            Text(
                text = "Welcome to BitChord TV",
                fontSize = 38.sp,
                fontWeight = FontWeight.W800,
                fontFamily = currentFont,
                color = Color.White,
            )

            Text(
                text = "Experience YouTube Music crafted exclusively for television screens with 120Hz smooth animations, live video canvas, and spatial audio.",
                fontSize = 17.sp,
                lineHeight = 24.sp,
                fontFamily = currentFont,
                color = Color.White.copy(alpha = 0.70f),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TvButton(
                    text = "Start Setup",
                    icon = Icons.Default.ArrowForward,
                    isPrimary = true,
                    onClick = onStartSetup,
                )
                TvButton(
                    text = "Use Defaults",
                    isPrimary = false,
                    onClick = onUseDefaults,
                )
            }
        }

        // Right Column: Feature Preview Card
        Box(
            modifier = Modifier
                .weight(0.45f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .padding(28.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "SETUP HIGHLIGHTS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = Color.White.copy(alpha = 0.60f),
                    fontFamily = currentFont,
                )

                TvFeatureBullet(icon = Icons.Default.Person, title = "TV Nickname", desc = "Personalized greetings on your home screen")
                TvFeatureBullet(icon = Icons.Default.Palette, title = "Visual Themes", desc = "Dynamic Artwork, Midnight, and OLED Pure Black")
                TvFeatureBullet(icon = Icons.Default.Headphones, title = "Spatial Audio", desc = "3D Virtualizer soundstage and lossless streams")
            }
        }
    }
}

@Composable
private fun TvFeatureBullet(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String,
) {
    val currentFont = LocalTvFontFamily.current

    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Column {
            Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = currentFont)
            Text(text = desc, fontSize = 12.sp, color = Color.White.copy(alpha = 0.60f), fontFamily = currentFont)
        }
    }
}

@Composable
private fun TvNicknameStep(
    nickname: String,
    onNicknameSelect: (String) -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    val currentFont = LocalTvFontFamily.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val presets = listOf("Living Room TV", "Bedroom TV", "Studio TV", "Family Room", "Theater", "Listener")
    var isInputFocused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Choose TV Nickname",
                fontSize = 32.sp,
                fontWeight = FontWeight.W800,
                fontFamily = currentFont,
                color = Color.White,
            )
            Text(
                text = "Type a custom name using your TV remote keyboard or pick a preset below.",
                fontSize = 15.sp,
                color = Color.White.copy(alpha = 0.70f),
                fontFamily = currentFont,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left Column: Custom Nickname Input & Presets
            Column(
                modifier = Modifier.weight(0.60f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "CUSTOM NICKNAME",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = currentFont,
                    color = Color.White.copy(alpha = 0.55f),
                    letterSpacing = 1.sp,
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .border(
                            width = if (isInputFocused) 3.dp else 1.dp,
                            color = if (isInputFocused) Color.White else Color.White.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(16.dp),
                        )
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (nickname.isBlank()) {
                        Text(
                            text = "Enter TV nickname...",
                            color = Color.White.copy(alpha = 0.40f),
                            fontSize = 18.sp,
                            fontFamily = currentFont,
                        )
                    }

                    BasicTextField(
                        value = nickname,
                        onValueChange = { if (it.length <= 32) onNicknameSelect(it) },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = currentFont,
                        ),
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
                                if (nickname.isNotBlank()) onContinue()
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isInputFocused = it.isFocused },
                    )
                }

                Text(
                    text = "OR CHOOSE A PRESET",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = currentFont,
                    color = Color.White.copy(alpha = 0.55f),
                    letterSpacing = 1.sp,
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(presets) { preset ->
                        val isSelected = preset == nickname
                        Box(
                            modifier = Modifier
                                .tvButtonFocus(
                                    shape = RoundedCornerShape(20.dp),
                                    focusedScale = 1.06f,
                                    focusedBorderColor = Color.White,
                                    onClick = { onNicknameSelect(preset) },
                                )
                                .background(if (isSelected) Color.White else Color.White.copy(alpha = 0.12f))
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = preset,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = currentFont,
                                color = if (isSelected) Color.Black else Color.White,
                            )
                        }
                    }
                }
            }

            // Right Column: Apple TV Illustrated Device Card
            Box(
                modifier = Modifier
                    .weight(0.40f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.06f))
                        ),
                        RoundedCornerShape(24.dp),
                    )
                    .padding(24.dp),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(30.dp),
                        )
                    }

                    Text(
                        text = "HOME SCREEN GREETING",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = Color.White.copy(alpha = 0.55f),
                        fontFamily = currentFont,
                    )

                    Text(
                        text = "Good Evening, ${nickname.ifBlank { "Living Room" }}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = currentFont,
                        color = Color.White,
                    )

                    Text(
                        text = "Your customized greeting appears in the top navigation bar and recommendations shelf.",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.65f),
                        fontFamily = currentFont,
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TvButton(
                text = "Continue",
                isPrimary = true,
                onClick = onContinue,
            )
            TvButton(
                text = "Skip",
                onClick = onSkip,
            )
        }
    }
}

@Composable
private fun TvThemeStep(
    selectedTheme: AppThemeOption,
    onThemeSelect: (AppThemeOption) -> Unit,
    onContinue: () -> Unit,
) {
    val currentFont = LocalTvFontFamily.current

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Choose Your Look",
                fontSize = 32.sp,
                fontWeight = FontWeight.W800,
                fontFamily = currentFont,
                color = Color.White,
            )
            Text(
                text = "Select a visual aesthetic for BitChord TV. You can change this anytime in Settings.",
                fontSize = 15.sp,
                color = Color.White.copy(alpha = 0.70f),
                fontFamily = currentFont,
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(AppThemeOption.entries.toTypedArray()) { theme ->
                val isSelected = theme == selectedTheme
                val palette = theme.getPalette()

                Box(
                    modifier = Modifier
                        .size(width = 250.dp, height = 200.dp)
                        .tvButtonFocus(
                            shape = RoundedCornerShape(20.dp),
                            focusedScale = 1.05f,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = if (isSelected) Color.White else Color.Transparent,
                            onClick = { onThemeSelect(theme) },
                        )
                        .clip(RoundedCornerShape(20.dp))
                        .background(palette.surface)
                        .padding(18.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                text = theme.title,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontFamily = currentFont,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = theme.description,
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.65f),
                                fontFamily = currentFont,
                            )
                        }

                        if (isSelected) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Text(text = "Selected", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = currentFont)
                            }
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TvButton(
                text = "Continue",
                isPrimary = true,
                onClick = onContinue,
            )
        }
    }
}

/**
 * Step 4: TV Audio & Experience Personalization.
 * 10 real TV remote-friendly customization toggles.
 */
@Composable
private fun TvCustomizationStep(
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    val currentFont = LocalTvFontFamily.current
    val palette = com.music.bitchord.ui.tv.theme.TvThemeColors.current

    val spatialAudio by TvSettings.spatialAudioEnabled.collectAsState()
    val smartFade by AppSettings.smartFadeEnabled.collectAsState()
    val soundCheck by TvSettings.soundCheckEnabled.collectAsState()
    val liveCanvas by AppSettings.animatedCanvas.collectAsState()
    val syncedLyrics by AppSettings.syncedLyrics.collectAsState()
    val highPerformance by AppSettings.highPerformanceMode.collectAsState()
    val skipSilence by AppSettings.skipSilence.collectAsState()
    val addPlaylistSongs by TvSettings.addPlaylistSongsToLibrary.collectAsState()
    val showNerdStats by AppSettings.showNerdStats.collectAsState()
    val discordPresence by AppSettings.discordRpcEnabled.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Audio & TV Personalization",
                fontSize = 32.sp,
                fontWeight = FontWeight.W800,
                fontFamily = currentFont,
                color = palette.textPrimary,
            )
            Text(
                text = "Customize 10 features tailored for your living room setup. Press D-pad to toggle anytime.",
                fontSize = 15.sp,
                fontFamily = currentFont,
                color = palette.textSecondary,
            )
        }

        // 10 Interactive Customization Options (Scrollable 2-Column Grid)
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 1. Spatial Audio
            item {
                TvCustomizationTile(
                    title = "Spatial Audio Virtualizer",
                    desc = "3D Dolby Atmos soundstage for TV soundbars",
                    enabled = spatialAudio,
                    onToggle = { TvSettings.setSpatialAudioEnabled(!spatialAudio) },
                )
            }
            // 2. Automix
            item {
                TvCustomizationTile(
                    title = "Automix DJ Transitions",
                    desc = "Beat-matched crossfade between tracks",
                    enabled = smartFade,
                    onToggle = { AppSettings.setSmartFadeEnabled(!smartFade) },
                )
            }
            // 3. Sound Check
            item {
                TvCustomizationTile(
                    title = "Sound Check (Loudness Match)",
                    desc = "ReplayGain volume normalization",
                    enabled = soundCheck,
                    onToggle = { TvSettings.setSoundCheckEnabled(!soundCheck) },
                )
            }
            // 4. Live Canvas
            item {
                TvCustomizationTile(
                    title = "Motion Video Canvas",
                    desc = "Looping artist background video sleeve",
                    enabled = liveCanvas,
                    onToggle = { AppSettings.setAnimatedCanvas(!liveCanvas) },
                )
            }
            // 5. Synced Lyrics
            item {
                TvCustomizationTile(
                    title = "Synchronized Flowing Lyrics",
                    desc = "Apple Music 1:1 real-time word sweep",
                    enabled = syncedLyrics,
                    onToggle = { AppSettings.setSyncedLyrics(!syncedLyrics) },
                )
            }
            // 6. High Performance Mode
            item {
                TvCustomizationTile(
                    title = "120Hz High-Performance Mode",
                    desc = "Silky frame pacing for high-end TV panels",
                    enabled = highPerformance,
                    onToggle = { AppSettings.setHighPerformanceMode(!highPerformance) },
                )
            }
            // 7. Skip Silence
            item {
                TvCustomizationTile(
                    title = "Skip Silence",
                    desc = "Skip silent intros and outros seamlessly",
                    enabled = skipSilence,
                    onToggle = { AppSettings.setSkipSilence(!skipSilence) },
                )
            }
            // 8. Auto-Add Playlist Tracks
            item {
                TvCustomizationTile(
                    title = "Add Playlist Songs to Library",
                    desc = "Automatically sync playlist additions to library",
                    enabled = addPlaylistSongs,
                    onToggle = { TvSettings.setAddPlaylistSongsToLibrary(!addPlaylistSongs) },
                )
            }
            // 9. Stats for Nerds
            item {
                TvCustomizationTile(
                    title = "Stream Stats for Nerds",
                    desc = "Audio codec, FLAC bitrate & sink HUD",
                    enabled = showNerdStats,
                    onToggle = { AppSettings.setShowNerdStats(!showNerdStats) },
                )
            }
            // 10. Discord Rich Presence
            item {
                TvCustomizationTile(
                    title = "Discord Rich Presence",
                    desc = "Broadcast now playing track to Discord",
                    enabled = discordPresence,
                    onToggle = { AppSettings.setDiscordRpcEnabled(!discordPresence) },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TvButton(
                text = "Continue",
                isPrimary = true,
                onClick = onContinue,
            )
            TvButton(
                text = "Back",
                onClick = onBack,
            )
        }
    }
}

@Composable
private fun TvCustomizationTile(
    title: String,
    desc: String,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val currentFont = LocalTvFontFamily.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(16.dp),
            )
            .tvButtonFocus(
                shape = RoundedCornerShape(16.dp),
                focusedScale = 1.02f,
                focusedBorderColor = Color.White,
                borderWidth = 2.5.dp,
                onClick = onToggle,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = currentFont,
                color = Color.White,
                maxLines = 1,
            )
            Text(
                text = desc,
                fontSize = 12.sp,
                fontFamily = currentFont,
                color = Color.White.copy(alpha = 0.65f),
                maxLines = 1,
            )
        }

        TvLiquidGlassSwitch(
            checked = enabled,
            onCheckedChange = { onToggle() },
        )
    }
}

@Composable
private fun TvSetupCompleteStep(
    nickname: String,
    theme: AppThemeOption,
    onFinish: () -> Unit,
    onChangeChoices: () -> Unit,
) {
    val currentFont = LocalTvFontFamily.current

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(0.55f),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            }

            Text(
                text = "You're All Set!",
                fontSize = 38.sp,
                fontWeight = FontWeight.W800,
                fontFamily = currentFont,
                color = Color.White,
            )

            Text(
                text = "BitChord TV is ready for \"$nickname\" with the ${theme.title} theme.",
                fontSize = 17.sp,
                lineHeight = 24.sp,
                fontFamily = currentFont,
                color = Color.White.copy(alpha = 0.70f),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TvButton(
                    text = "Start Listening",
                    icon = Icons.Default.PlayArrow,
                    isPrimary = true,
                    onClick = onFinish,
                )
                TvButton(
                    text = "Change",
                    onClick = onChangeChoices,
                )
            }
        }
    }
}

@Composable
private fun TvLoginStep(
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    val currentFont = LocalTvFontFamily.current

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "Sign in to YouTube Music",
                fontSize = 32.sp,
                fontWeight = FontWeight.W800,
                fontFamily = currentFont,
                color = Color.White,
            )
            Text(
                text = "Link your Google Account to access your playlists, history, and tailored recommendations on the big screen.",
                fontSize = 17.sp,
                lineHeight = 24.sp,
                color = Color.White.copy(alpha = 0.70f),
                fontFamily = currentFont,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TvButton(
                text = "Sign In",
                isPrimary = true,
                onClick = onContinue, // Just mock it for now by skipping to next step
            )
            TvButton(
                text = "Skip for Now",
                onClick = onSkip,
            )
        }
    }
}
