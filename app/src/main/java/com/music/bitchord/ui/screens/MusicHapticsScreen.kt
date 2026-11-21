package com.music.bitchord.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.playback.haptics.HapticEventKind
import com.music.bitchord.playback.haptics.HapticMotorTier
import com.music.bitchord.playback.haptics.MusicHapticsConfig
import com.music.bitchord.playback.haptics.MusicHapticsProfile
import com.music.bitchord.playback.haptics.MusicHapticsRuntime
import com.music.bitchord.playback.haptics.MusicHapticsSnapshot
import com.music.bitchord.playback.haptics.MusicHapticsTelemetry
import com.music.bitchord.ui.components.IosSwitch
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics
import com.music.bitchord.ui.icons.SfIcons
import kotlin.math.roundToInt

/**
 * Dedicated Music Haptics configuration screen accessible directly from the main
 * Settings page. Controls master intensity, per-element tactile mix (Bass, Vocals,
 * Percussion), sensitivity, Bluetooth/wired sync offset, player button visibility,
 * headphones-only mode, and screen-off battery saver.
 */
@Composable
fun MusicHapticsScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val enabled by AppSettings.musicHapticsEnabled.collectAsStateWithLifecycle()
    val profile by AppSettings.musicHapticsProfile.collectAsStateWithLifecycle()
    val intensity by AppSettings.musicHapticsIntensity.collectAsStateWithLifecycle()
    val sensitivity by AppSettings.musicHapticsSensitivity.collectAsStateWithLifecycle()
    val syncOffsetMs by AppSettings.musicHapticsSyncOffsetMs.collectAsStateWithLifecycle()
    val bassWeight by AppSettings.musicHapticsBassWeight.collectAsStateWithLifecycle()
    val vocalWeight by AppSettings.musicHapticsVocalWeight.collectAsStateWithLifecycle()
    val percussionWeight by AppSettings.musicHapticsPercussionWeight.collectAsStateWithLifecycle()
    val showOnPlayer by AppSettings.showMusicHapticsButton.collectAsStateWithLifecycle()
    val headphonesOnly by AppSettings.musicHapticsHeadphonesOnly.collectAsStateWithLifecycle()
    val pauseScreenOff by AppSettings.musicHapticsPauseScreenOff.collectAsStateWithLifecycle()
    val telemetry by MusicHapticsTelemetry.state.collectAsStateWithLifecycle()

    val uiHaptics = rememberHaptics()

    DisposableEffect(Unit) {
        MusicHapticsTelemetry.uiObserving = true
        onDispose { MusicHapticsTelemetry.uiObserving = false }
    }

    val currentConfig = MusicHapticsConfig(
        enabled = enabled,
        profile = profile,
        intensity = intensity,
        sensitivity = sensitivity,
        syncOffsetMs = syncOffsetMs,
        bassWeight = bassWeight,
        vocalWeight = vocalWeight,
        percussionWeight = percussionWeight,
        headphonesOnly = headphonesOnly,
        pauseWhenScreenOff = pauseScreenOff,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        SettingsPageTitle(stringResource(R.string.music_haptics))

        SettingsGroup(
            footer = stringResource(R.string.music_haptics_footer),
            topSpacing = 0.dp,
        ) {
            SettingsRow(
                icon = SfIcons.Waves,
                title = stringResource(R.string.music_haptics),
                subtitle = motorTierLabel(telemetry.motorTier),
                trailing = {
                    IosSwitch(
                        checked = enabled,
                        onCheckedChange = {
                            uiHaptics.play(if (it) Haptic.ToggleOn else Haptic.ToggleOff)
                            AppSettings.setMusicHapticsEnabled(it)
                        },
                    )
                },
                onClick = {
                    uiHaptics.play(if (!enabled) Haptic.ToggleOn else Haptic.ToggleOff)
                    AppSettings.setMusicHapticsEnabled(!enabled)
                },
            )
        }

        // Player UI toggle stays always interactive even when haptics is currently off
        SettingsGroup(header = stringResource(R.string.music_haptics_player_header)) {
            SettingsRow(
                icon = SfIcons.PlayCircle,
                title = stringResource(R.string.music_haptics_show_on_player),
                subtitle = stringResource(R.string.music_haptics_show_on_player_subtitle),
                trailing = {
                    IosSwitch(
                        checked = showOnPlayer,
                        onCheckedChange = {
                            uiHaptics.play(if (it) Haptic.ToggleOn else Haptic.ToggleOff)
                            AppSettings.setShowMusicHapticsButton(it)
                        },
                    )
                },
                onClick = {
                    uiHaptics.play(if (!showOnPlayer) Haptic.ToggleOn else Haptic.ToggleOff)
                    AppSettings.setShowMusicHapticsButton(!showOnPlayer)
                },
            )
        }

        val controlsAlpha by animateFloatAsState(
            targetValue = if (enabled) 1f else 0.42f,
            animationSpec = tween(220),
            label = "musicHapticsEnabled",
        )

        Column(modifier = Modifier.graphicsLayer { alpha = controlsAlpha }) {
            // Live 5-band tactile response & test groove
            SettingsGroup(header = stringResource(R.string.music_haptics_live_monitor)) {
                LiveHapticsVisualizerCard(
                    snapshot = telemetry,
                    enabled = enabled,
                )
                RowDivider()
                DestructiveRow(
                    label = stringResource(R.string.music_haptics_test_feel),
                    enabled = telemetry.motorTier != HapticMotorTier.UNAVAILABLE,
                    onClick = {
                        MusicHapticsRuntime.playTestGroove(
                            context = context,
                            config = currentConfig.copy(enabled = true),
                        )
                    },
                )
            }

            // Feel profile selector
            SettingsGroup(
                header = stringResource(R.string.music_haptics_profile_header),
                footer = profile.descriptionString(),
            ) {
                MusicHapticsProfile.entries.forEachIndexed { index, entry ->
                    if (index > 0) RowDivider()
                    HapticsProfileRow(
                        title = entry.titleString(),
                        subtitle = entry.shortHintString(),
                        selected = profile == entry,
                        enabled = enabled,
                        onClick = {
                            uiHaptics.play(Haptic.Tick)
                            AppSettings.setMusicHapticsProfile(entry)
                        },
                    )
                }
            }

            // Master tuning
            SettingsGroup(
                header = stringResource(R.string.music_haptics_tuning_header),
                footer = stringResource(R.string.music_haptics_sync_footer),
            ) {
                SliderRow(
                    icon = SfIcons.GraphicEq,
                    title = stringResource(R.string.music_haptics_intensity),
                    value = "${(intensity * 100f).roundToInt()}%",
                    sliderValue = intensity,
                    onSliderValue = { AppSettings.setMusicHapticsIntensity(it) },
                    valueRange = 0.15f..1.0f,
                    steps = 16,
                    subtitle = stringResource(R.string.music_haptics_intensity_subtitle),
                )
                RowDivider()
                SliderRow(
                    icon = SfIcons.Tune,
                    title = stringResource(R.string.music_haptics_sensitivity),
                    value = "${(sensitivity * 100f).roundToInt()}%",
                    sliderValue = sensitivity,
                    onSliderValue = { AppSettings.setMusicHapticsSensitivity(it) },
                    valueRange = 0.0f..1.0f,
                    steps = 19,
                    subtitle = stringResource(R.string.music_haptics_sensitivity_subtitle),
                )
                RowDivider()
                val syncLabel = when {
                    syncOffsetMs > 0 -> "+${syncOffsetMs} ms"
                    syncOffsetMs < 0 -> "$syncOffsetMs ms"
                    else -> "0 ms"
                }
                SliderRow(
                    icon = SfIcons.MusicNote,
                    title = stringResource(R.string.music_haptics_sync_offset),
                    value = syncLabel,
                    sliderValue = syncOffsetMs.toFloat(),
                    onSliderValue = { AppSettings.setMusicHapticsSyncOffsetMs((it / 10f).roundToInt() * 10) },
                    valueRange = -150f..250f,
                    steps = 39,
                    subtitle = stringResource(R.string.music_haptics_sync_offset_subtitle),
                )
            }

            // Per-element vibration mix
            SettingsGroup(
                header = stringResource(R.string.music_haptics_mix_header),
                footer = stringResource(R.string.music_haptics_mix_footer),
            ) {
                SliderRow(
                    icon = SfIcons.Waves,
                    title = stringResource(R.string.music_haptics_mix_bass),
                    value = "${(bassWeight * 100f).roundToInt()}%",
                    sliderValue = bassWeight,
                    onSliderValue = { AppSettings.setMusicHapticsBassWeight(it) },
                    valueRange = 0.0f..1.0f,
                    steps = 19,
                    subtitle = stringResource(R.string.music_haptics_mix_bass_subtitle),
                )
                RowDivider()
                SliderRow(
                    icon = SfIcons.MusicNote,
                    title = stringResource(R.string.music_haptics_mix_vocals),
                    value = "${(vocalWeight * 100f).roundToInt()}%",
                    sliderValue = vocalWeight,
                    onSliderValue = { AppSettings.setMusicHapticsVocalWeight(it) },
                    valueRange = 0.0f..1.0f,
                    steps = 19,
                    subtitle = stringResource(R.string.music_haptics_mix_vocals_subtitle),
                )
                RowDivider()
                SliderRow(
                    icon = SfIcons.GraphicEq,
                    title = stringResource(R.string.music_haptics_mix_percussion),
                    value = "${(percussionWeight * 100f).roundToInt()}%",
                    sliderValue = percussionWeight,
                    onSliderValue = { AppSettings.setMusicHapticsPercussionWeight(it) },
                    valueRange = 0.0f..1.0f,
                    steps = 19,
                    subtitle = stringResource(R.string.music_haptics_mix_percussion_subtitle),
                )
            }

            // Playback behavior
            SettingsGroup(header = stringResource(R.string.music_haptics_behavior_header)) {
                SettingsRow(
                    icon = SfIcons.Bluetooth,
                    title = stringResource(R.string.music_haptics_headphones_only),
                    subtitle = stringResource(R.string.music_haptics_headphones_only_subtitle),
                    trailing = {
                        IosSwitch(
                            checked = headphonesOnly,
                            onCheckedChange = {
                                uiHaptics.play(if (it) Haptic.ToggleOn else Haptic.ToggleOff)
                                AppSettings.setMusicHapticsHeadphonesOnly(it)
                            },
                        )
                    },
                    onClick = {
                        uiHaptics.play(if (!headphonesOnly) Haptic.ToggleOn else Haptic.ToggleOff)
                        AppSettings.setMusicHapticsHeadphonesOnly(!headphonesOnly)
                    },
                )
                RowDivider()
                SettingsRow(
                    icon = SfIcons.Performance,
                    title = stringResource(R.string.music_haptics_pause_screen_off),
                    subtitle = stringResource(R.string.music_haptics_pause_screen_off_subtitle),
                    trailing = {
                        IosSwitch(
                            checked = pauseScreenOff,
                            onCheckedChange = {
                                uiHaptics.play(if (it) Haptic.ToggleOn else Haptic.ToggleOff)
                                AppSettings.setMusicHapticsPauseScreenOff(it)
                            },
                        )
                    },
                    onClick = {
                        uiHaptics.play(if (!pauseScreenOff) Haptic.ToggleOn else Haptic.ToggleOff)
                        AppSettings.setMusicHapticsPauseScreenOff(!pauseScreenOff)
                    },
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LiveHapticsVisualizerCard(
    snapshot: MusicHapticsSnapshot,
    enabled: Boolean,
) {
    val subLevel by animateFloatAsState(
        targetValue = if (enabled && snapshot.active) snapshot.subLevel else 0.08f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "visSub",
    )
    val kickLevel by animateFloatAsState(
        targetValue = if (enabled && snapshot.active) snapshot.kickLevel else 0.08f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "visKick",
    )
    val snareLevel by animateFloatAsState(
        targetValue = if (enabled && snapshot.active) snapshot.snareLevel else 0.08f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "visSnare",
    )
    val vocalLevel by animateFloatAsState(
        targetValue = if (enabled && snapshot.active) snapshot.vocalLevel else 0.08f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "visVocal",
    )
    val airLevel by animateFloatAsState(
        targetValue = if (enabled && snapshot.active) snapshot.airLevel else 0.08f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "visAir",
    )

    val primary = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val labels = listOf(
        stringResource(R.string.music_haptics_band_sub),
        stringResource(R.string.music_haptics_band_kick),
        stringResource(R.string.music_haptics_band_snare),
        stringResource(R.string.music_haptics_band_melody),
        stringResource(R.string.music_haptics_band_crisp),
    )
    val levels = listOf(subLevel, kickLevel, snareLevel, vocalLevel, airLevel)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (enabled && snapshot.active) primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        ),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when {
                        !enabled -> stringResource(R.string.music_haptics_status_off)
                        !snapshot.active -> stringResource(R.string.music_haptics_status_idle)
                        snapshot.lastEventKind != null -> eventKindLabel(snapshot.lastEventKind)
                        else -> stringResource(R.string.music_haptics_status_listening)
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = if (enabled && snapshot.active && snapshot.estimatedBpm > 0) {
                    "${snapshot.estimatedBpm} BPM · ${snapshot.currentLeadMs} ms"
                } else {
                    "-- BPM"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(14.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        ) {
            val count = levels.size
            val gap = 10.dp.toPx()
            val barWidth = (size.width - gap * (count - 1)) / count
            val radius = CornerRadius(7.dp.toPx(), 7.dp.toPx())

            levels.forEachIndexed { idx, value ->
                val x = idx * (barWidth + gap)
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(x, 0f),
                    size = Size(barWidth, size.height),
                    cornerRadius = radius,
                )
                val barHeight = (size.height * value.coerceIn(0.08f, 1f)).coerceAtLeast(8.dp.toPx())
                val topY = size.height - barHeight
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(primary, primary.copy(alpha = 0.45f)),
                        startY = topY,
                        endY = size.height,
                    ),
                    topLeft = Offset(x, topY),
                    size = Size(barWidth, barHeight),
                    cornerRadius = radius,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            labels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HapticsProfileRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Spacer(Modifier.width(12.dp))
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun motorTierLabel(tier: HapticMotorTier): String = when (tier) {
    HapticMotorTier.HD_PRIMITIVES -> stringResource(R.string.music_haptics_motor_hd)
    HapticMotorTier.AMPLITUDE_ENVELOPE -> stringResource(R.string.music_haptics_motor_amplitude)
    HapticMotorTier.BASIC_PULSE -> stringResource(R.string.music_haptics_motor_basic)
    HapticMotorTier.UNAVAILABLE -> stringResource(R.string.music_haptics_motor_unavailable)
}

@Composable
private fun eventKindLabel(kind: HapticEventKind): String = when (kind) {
    HapticEventKind.SUB_DROP -> stringResource(R.string.music_haptics_event_drop)
    HapticEventKind.KICK_PUNCH -> stringResource(R.string.music_haptics_event_kick)
    HapticEventKind.BASS_GROOVE -> stringResource(R.string.music_haptics_event_swell)
    HapticEventKind.SNARE_CRACK -> stringResource(R.string.music_haptics_event_snare)
    HapticEventKind.CLAP_SNAP -> stringResource(R.string.music_haptics_event_clap)
    HapticEventKind.VOCAL_SYLLABLE -> stringResource(R.string.music_haptics_event_vocal)
    HapticEventKind.VOCAL_SWELL -> stringResource(R.string.music_haptics_event_vocal_swell)
    HapticEventKind.HIHAT_SHIMMER -> stringResource(R.string.music_haptics_event_tick)
    HapticEventKind.CRASH_BUILD -> stringResource(R.string.music_haptics_event_crash)
}

@Composable
internal fun MusicHapticsProfile.titleString(): String = when (this) {
    MusicHapticsProfile.BALANCED -> stringResource(R.string.music_haptics_profile_balanced)
    MusicHapticsProfile.BASS_BEAT -> stringResource(R.string.music_haptics_profile_bass)
    MusicHapticsProfile.CRISP_PERCUSSION -> stringResource(R.string.music_haptics_profile_crisp)
    MusicHapticsProfile.IMMERSIVE -> stringResource(R.string.music_haptics_profile_immersive)
}

@Composable
private fun MusicHapticsProfile.shortHintString(): String = when (this) {
    MusicHapticsProfile.BALANCED -> stringResource(R.string.music_haptics_profile_balanced_hint)
    MusicHapticsProfile.BASS_BEAT -> stringResource(R.string.music_haptics_profile_bass_hint)
    MusicHapticsProfile.CRISP_PERCUSSION -> stringResource(R.string.music_haptics_profile_crisp_hint)
    MusicHapticsProfile.IMMERSIVE -> stringResource(R.string.music_haptics_profile_immersive_hint)
}

@Composable
private fun MusicHapticsProfile.descriptionString(): String = when (this) {
    MusicHapticsProfile.BALANCED -> stringResource(R.string.music_haptics_profile_balanced_desc)
    MusicHapticsProfile.BASS_BEAT -> stringResource(R.string.music_haptics_profile_bass_desc)
    MusicHapticsProfile.CRISP_PERCUSSION -> stringResource(R.string.music_haptics_profile_crisp_desc)
    MusicHapticsProfile.IMMERSIVE -> stringResource(R.string.music_haptics_profile_immersive_desc)
}
