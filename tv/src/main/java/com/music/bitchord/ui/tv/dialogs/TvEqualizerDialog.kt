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
import com.music.bitchord.ui.tv.components.TvListRow
import com.music.bitchord.ui.tv.components.TvSegmented
import com.music.bitchord.ui.tv.components.tvInitialFocus
import com.music.bitchord.ui.tv.theme.TvGlass
import com.music.bitchord.ui.tv.theme.TvType
import androidx.compose.foundation.layout.heightIn
import com.music.bitchord.ui.tv.theme.AppleSpringPreset
import com.music.bitchord.ui.tv.theme.TvSFProDisplay
import com.music.bitchord.ui.tv.theme.TvThemeColors
import com.music.bitchord.ui.tv.theme.appleSpring
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class EqTab(val label: String) {
    BANDS("Bands"),
    PRESETS("Presets"),
}

/**
 * The equalizer as a tvOS sheet: on/off at the top, then either seven band
 * faders (left and right pick a band, up and down set its gain, select resets
 * it) or the list of presets.
 */
@Composable
fun TvEqualizerDialog(
    onDismiss: () -> Unit,
) {
    val enabled by AppSettings.equalizerEnabled.collectAsState()
    val currentPreset by AppSettings.equalizerPreset.collectAsState()
    val bands by AppSettings.equalizerBands.collectAsState()
    var tab by remember { mutableStateOf(EqTab.BANDS) }

    val presets: List<Pair<EqualizerPreset, String>> = remember {
        listOf(
            EqualizerPreset.FLAT to "Flat",
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

    TvDialog(title = "Equalizer", width = 640.dp, onDismissRequest = onDismiss) {
        TvListRow(
            title = "Equalizer",
            value = if (enabled) "On" else "Off",
            modifier = Modifier.tvInitialFocus(),
            onClick = { AppSettings.setEqualizerEnabled(!enabled) },
        )
        Spacer(modifier = Modifier.height(18.dp))
        TvSegmented(
            options = EqTab.entries.map { it.label },
            selectedIndex = tab.ordinal,
            onSelect = { tab = EqTab.entries[it] },
        )
        Spacer(modifier = Modifier.height(18.dp))
        when (tab) {
            EqTab.BANDS -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EqLayout.MANUAL_BANDS_HZ.forEachIndexed { index, hz ->
                        TvBandFader(
                            hz = hz,
                            gainDb = bands.getOrElse(index) { 0f },
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
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Up and down set a band. Select resets it.",
                    style = TvType.Caption,
                    color = TvGlass.TextTertiary,
                )
                Spacer(modifier = Modifier.height(18.dp))
                TvButton(
                    text = "Reset All",
                    onClick = {
                        AppSettings.setEqualizerBands(List(EqLayout.MANUAL_COUNT) { 0f })
                        AppSettings.setEqualizerPreset(EqualizerPreset.FLAT)
                    },
                )
            }
            EqTab.PRESETS -> LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(presets) { (preset, label) ->
                    val selected = if (preset == EqualizerPreset.FLAT) {
                        !enabled || currentPreset == preset
                    } else {
                        enabled && currentPreset == preset
                    }
                    TvListRow(
                        title = label,
                        trailingIcon = if (selected) Icons.Default.Check else null,
                        onClick = {
                            AppSettings.setEqualizerEnabled(preset != EqualizerPreset.FLAT)
                            AppSettings.setEqualizerPreset(preset)
                        },
                    )
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
