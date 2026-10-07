package com.music.bitchord.ui.tv.dialogs

import android.view.KeyEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.playback.EqLayout
import com.music.bitchord.playback.EqualizerPreset
import com.music.bitchord.ui.tv.components.TvButton
import com.music.bitchord.ui.tv.components.TvDialog
import com.music.bitchord.ui.tv.focus.tvButtonFocus
import com.music.bitchord.ui.tv.theme.AppleSpringPreset
import com.music.bitchord.ui.tv.theme.TvSFProDisplay
import com.music.bitchord.ui.tv.theme.TvThemeColors
import com.music.bitchord.ui.tv.theme.appleSpring
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class EqTab(val label: String) {
    MANUAL("7-Band Graphic EQ"),
    PRESETS("Apple TV Presets"),
}

/**
 * 1:1 Apple TV Pro Equalizer Dialog.
 *
 * Implements:
 * 1. 7-Band Graphic Equalizer with vertical Apple TV glass faders, center-baseline fill,
 *    D-pad step control (Up/Down adjusts dB gain, Left/Right changes frequency band).
 * 2. Full Apple TV Genre Presets with pure basic white selection checkmarks (Zero pink).
 * 3. Segmented Apple TV top tab switcher with tactile press bounce animations.
 * 4. High-contrast typography and instant persistent sync with AndroidX audio DSP pipeline.
 */
@Composable
fun TvEqualizerDialog(
    onDismiss: () -> Unit,
) {
    val palette = TvThemeColors.current
    val enabled by AppSettings.equalizerEnabled.collectAsState()
    val currentPreset by AppSettings.equalizerPreset.collectAsState()
    val bands by AppSettings.equalizerBands.collectAsState()

    var selectedTab by remember { mutableStateOf(EqTab.MANUAL) }

    val presets: List<Pair<EqualizerPreset, String>> = remember {
        listOf(
            EqualizerPreset.FLAT to "Off (Flat)",
            EqualizerPreset.ACOUSTIC to "Acoustic",
            EqualizerPreset.BASS_BOOST to "Bass Booster",
            EqualizerPreset.BASS_CUT to "Bass Reducer",
            EqualizerPreset.CLASSICAL to "Classical",
            EqualizerPreset.ELECTRONIC to "Electronic",
            EqualizerPreset.HIP_HOP to "Hip-Hop",
            EqualizerPreset.JAZZ to "Jazz",
            EqualizerPreset.LOUDNESS to "Loudness",
            EqualizerPreset.ROCK to "Rock",
            EqualizerPreset.SPOKEN_WORD to "Spoken Word",
            EqualizerPreset.TREBLE_BOOST to "Treble Booster",
            EqualizerPreset.TREBLE_CUT to "Treble Reducer",
            EqualizerPreset.VOCAL to "Vocal Booster",
            EqualizerPreset.SMALL_SPEAKERS to "Small Speakers",
            EqualizerPreset.LATE_NIGHT to "Late Night",
        )
    }

    TvDialog(
        title = "Equalizer",
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Top Row: Apple Segmented Pill Tabs & Status Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Segmented Tabs [ 7-Band Graphic EQ | Apple TV Presets ]
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    EqTab.entries.forEach { tab ->
                        val isSelected = selectedTab == tab
                        val interactionSource = remember { MutableInteractionSource() }
                        val isFocused by interactionSource.collectIsFocusedAsState()
                        val isPressed by interactionSource.collectIsPressedAsState()

                        val targetScale = when {
                            isPressed -> 0.95f
                            isFocused -> 1.04f
                            else -> 1.0f
                        }
                        val tabScale by animateFloatAsState(
                            targetValue = targetScale,
                            animationSpec = appleSpring(AppleSpringPreset.Snappy),
                            label = "eqTabScale",
                        )

                        val tabBg by animateColorAsState(
                            targetValue = when {
                                isFocused && isSelected -> Color.White
                                isFocused -> Color.White.copy(alpha = 0.25f)
                                isSelected -> Color.White
                                else -> Color.Transparent
                            },
                            animationSpec = appleSpring(AppleSpringPreset.Snappy),
                            label = "eqTabBg",
                        )

                        val textColor = when {
                            isSelected -> Color.Black
                            isFocused -> Color.White
                            else -> Color.White.copy(alpha = 0.70f)
                        }

                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = tabScale
                                    scaleY = tabScale
                                }
                                .clip(RoundedCornerShape(12.dp))
                                .background(tabBg)
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = { selectedTab = tab },
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = tab.label,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontFamily = TvSFProDisplay,
                                color = textColor,
                            )
                        }
                    }
                }

                // EQ Status Pill (Active / Bypass)
                val interactionSource = remember { MutableInteractionSource() }
                val isFocused by interactionSource.collectIsFocusedAsState()
                val isPressed by interactionSource.collectIsPressedAsState()

                val targetScale = when {
                    isPressed -> 0.95f
                    isFocused -> 1.04f
                    else -> 1.0f
                }
                val scale by animateFloatAsState(
                    targetValue = targetScale,
                    animationSpec = appleSpring(AppleSpringPreset.Snappy),
                    label = "eqStatusScale",
                )

                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (enabled) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f))
                        .tvButtonFocus(
                            shape = RoundedCornerShape(12.dp),
                            focusedScale = 1.0f,
                            onClick = { AppSettings.setEqualizerEnabled(!enabled) },
                        )
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = if (enabled) "EQ Active • ${currentPreset.name}" else "EQ Bypassed",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = TvSFProDisplay,
                        color = if (enabled) Color.White else palette.textMuted,
                    )
                }
            }

            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "eqTabTransition",
            ) { tab ->
                when (tab) {
                    EqTab.MANUAL -> {
                        // 7-BAND MANUAL GRAPHIC EQUALIZER
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(
                                text = "Use D-pad \u2190 / \u2192 to select frequency, \u2191 / \u2193 to adjust dB gain (-12dB to +12dB).",
                                fontSize = 13.sp,
                                color = palette.textSecondary,
                                fontFamily = TvSFProDisplay,
                            )

                            // 7 Faders Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = 0.04f))
                                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                                    .padding(vertical = 18.dp, horizontal = 12.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                EqLayout.MANUAL_BANDS_HZ.forEachIndexed { index, hz ->
                                    val currentDb = bands.getOrElse(index) { 0f }
                                    TvBandFader(
                                        hz = hz,
                                        gainDb = currentDb,
                                        enabled = enabled,
                                        onGainChange = { newDb ->
                                            val updated = bands.toMutableList().also {
                                                while (it.size <= index) it.add(0f)
                                                it[index] = newDb
                                            }
                                            AppSettings.setEqualizerBands(updated)
                                            if (!enabled) AppSettings.setEqualizerEnabled(true)
                                        },
                                        onReset = {
                                            val updated = bands.toMutableList().also {
                                                if (it.size > index) it[index] = 0f
                                            }
                                            AppSettings.setEqualizerBands(updated)
                                        },
                                    )
                                }
                            }

                            // Quick Action Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TvButton(
                                    text = "Reset All (Flat)",
                                    icon = Icons.Default.RestartAlt,
                                    isPrimary = false,
                                    onClick = {
                                        AppSettings.setEqualizerBands(List(EqLayout.MANUAL_COUNT) { 0f })
                                        AppSettings.setEqualizerPreset(EqualizerPreset.FLAT)
                                    },
                                )

                                TvButton(
                                    text = "Bass Boost",
                                    icon = Icons.Default.Tune,
                                    isPrimary = false,
                                    onClick = {
                                        AppSettings.setEqualizerPreset(EqualizerPreset.BASS_BOOST)
                                        AppSettings.setEqualizerEnabled(true)
                                    },
                                )

                                TvButton(
                                    text = "Vocal Boost",
                                    icon = Icons.Default.GraphicEq,
                                    isPrimary = false,
                                    onClick = {
                                        AppSettings.setEqualizerPreset(EqualizerPreset.VOCAL)
                                        AppSettings.setEqualizerEnabled(true)
                                    },
                                )

                                Spacer(modifier = Modifier.weight(1f))

                                TvButton(
                                    text = "Done",
                                    isPrimary = true,
                                    onClick = onDismiss,
                                )
                            }
                        }
                    }

                    EqTab.PRESETS -> {
                        // APPLE TV GENRE PRESET LIST
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = "Select an Apple TV preset curve to optimize audio response for your listening setup.",
                                fontSize = 13.sp,
                                color = palette.textSecondary,
                                fontFamily = TvSFProDisplay,
                            )

                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxHeight(0.72f),
                            ) {
                                items(presets) { (preset, label) ->
                                    val isSelected = if (preset == EqualizerPreset.FLAT) {
                                        !enabled || currentPreset == preset
                                    } else {
                                        enabled && currentPreset == preset
                                    }

                                    val interactionSource = remember { MutableInteractionSource() }
                                    val isFocused by interactionSource.collectIsFocusedAsState()
                                    val isPressed by interactionSource.collectIsPressedAsState()

                                    val targetScale = when {
                                        isPressed -> 0.96f
                                        isFocused -> 1.02f
                                        else -> 1.0f
                                    }
                                    val itemScale by animateFloatAsState(
                                        targetValue = targetScale,
                                        animationSpec = appleSpring(AppleSpringPreset.Snappy),
                                        label = "presetItemScale",
                                    )

                                    val itemBg by animateColorAsState(
                                        targetValue = when {
                                            isFocused -> Color.White
                                            isSelected -> Color.White.copy(alpha = 0.16f)
                                            else -> Color.Transparent
                                        },
                                        animationSpec = appleSpring(AppleSpringPreset.Snappy),
                                        label = "presetItemBg",
                                    )

                                    val itemTextColor = when {
                                        isFocused -> Color.Black
                                        isSelected -> Color.White
                                        else -> palette.textSecondary
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .graphicsLayer {
                                                scaleX = itemScale
                                                scaleY = itemScale
                                            }
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(itemBg)
                                            .tvButtonFocus(
                                                shape = RoundedCornerShape(12.dp),
                                                focusedScale = 1.0f,
                                                onClick = {
                                                    if (preset == EqualizerPreset.FLAT) {
                                                        AppSettings.setEqualizerEnabled(false)
                                                        AppSettings.setEqualizerPreset(preset)
                                                    } else {
                                                        AppSettings.setEqualizerEnabled(true)
                                                        AppSettings.setEqualizerPreset(preset)
                                                    }
                                                },
                                            )
                                            .padding(horizontal = 20.dp, vertical = 13.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 16.sp,
                                                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
                                                fontFamily = TvSFProDisplay,
                                                color = itemTextColor,
                                            )

                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = if (isFocused) Color.Black else Color.White,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TvButton(
                                    text = "Done",
                                    isPrimary = true,
                                    onClick = onDismiss,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 1:1 Apple TV Vertical Band Fader with center-zero baseline fill,
 * crisp white knob, and tactile remote D-pad controls.
 */
@Composable
private fun TvBandFader(
    hz: Float,
    gainDb: Float,
    enabled: Boolean,
    onGainChange: (Float) -> Unit,
    onReset: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val targetScale = when {
        isPressed -> 0.94f
        isFocused -> 1.08f
        else -> 1.0f
    }
    val faderScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = appleSpring(AppleSpringPreset.Snappy),
        label = "bandFaderScale",
    )

    val maxDb = EqLayout.MANUAL_RANGE_DB // 12 dB

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .graphicsLayer {
                scaleX = faderScale
                scaleY = faderScale
            }
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) Color.White.copy(alpha = 0.12f) else Color.Transparent)
            .focusable(interactionSource = interactionSource)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        val next = (gainDb + 1.0f).coerceIn(-maxDb, maxDb)
                        onGainChange(next)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        val next = (gainDb - 1.0f).coerceIn(-maxDb, maxDb)
                        onGainChange(next)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        onReset()
                        true
                    }
                    else -> false
                }
            }
            .padding(horizontal = 6.dp, vertical = 8.dp),
    ) {
        // Gain Readout (+4 dB, 0 dB, -2 dB)
        val formattedGain = when {
            abs(gainDb) < 0.2f -> "0 dB"
            gainDb > 0 -> String.format(Locale.US, "+%.0f dB", gainDb)
            else -> String.format(Locale.US, "%.0f dB", gainDb)
        }

        Text(
            text = formattedGain,
            fontSize = 12.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            fontFamily = TvSFProDisplay,
            color = if (isFocused) Color.White else if (abs(gainDb) >= 0.5f) Color.White else Color.White.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
        )

        // Vertical Fader Canvas Track
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(150.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                val trackWidth = 6.dp.toPx()
                val trackRadius = trackWidth / 2f
                val centerX = size.width / 2f
                val topY = 12.dp.toPx()
                val bottomY = size.height - 12.dp.toPx()
                val centerY = (topY + bottomY) / 2f
                val trackHeight = bottomY - topY

                // 1. Background Groove
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.15f),
                    topLeft = Offset(centerX - trackRadius, topY),
                    size = Size(trackWidth, trackHeight),
                    cornerRadius = CornerRadius(trackRadius, trackRadius),
                )

                // 2. Center 0dB Reference Tick
                drawLine(
                    color = Color.White.copy(alpha = 0.40f),
                    start = Offset(centerX - 8.dp.toPx(), centerY),
                    end = Offset(centerX + 8.dp.toPx(), centerY),
                    strokeWidth = 1.5.dp.toPx(),
                )

                // 3. Normalized Position (-12dB at bottom, +12dB at top)
                val fraction = ((gainDb - (-maxDb)) / (2f * maxDb)).coerceIn(0f, 1f)
                val knobY = bottomY - (fraction * trackHeight)

                // 4. Fill bar from center baseline to knob
                val fillTop = kotlin.math.min(centerY, knobY)
                val fillBottom = kotlin.math.max(centerY, knobY)
                val fillHeight = kotlin.math.max(fillBottom - fillTop, 2.dp.toPx())

                drawRoundRect(
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.85f),
                    topLeft = Offset(centerX - trackRadius, fillTop),
                    size = Size(trackWidth, fillHeight),
                    cornerRadius = CornerRadius(trackRadius, trackRadius),
                )

                // 5. White Fader Thumb Knob
                val knobRadius = if (isFocused) 10.dp.toPx() else 8.dp.toPx()
                drawCircle(
                    color = Color.White,
                    radius = knobRadius,
                    center = Offset(centerX, knobY),
                )

                // Subtle inner shadow ring
                if (isFocused) {
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.25f),
                        radius = knobRadius * 0.4f,
                        center = Offset(centerX, knobY),
                    )
                }
            }
        }

        // Frequency Label (60Hz, 1kHz, etc.)
        val freqLabel = when {
            hz >= 1000f -> "${(hz / 1000f).roundToInt()}k"
            else -> "${hz.roundToInt()}"
        }

        Text(
            text = freqLabel,
            fontSize = 12.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
            fontFamily = TvSFProDisplay,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
        )
    }
}
