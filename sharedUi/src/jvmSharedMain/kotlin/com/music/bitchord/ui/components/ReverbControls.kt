package com.music.bitchord.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.music.bitchord.playback.ReverbPreset
import com.music.bitchord.playback.ReverbSettings
import com.music.bitchord.sharedui.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The reverb's room, for a settings page: the four presets, every parameter
 * as a slider, and a reset. Shared by the phone's settings sheet and the
 * desktop's settings page, so the two offer the same controls over the same
 * [ReverbSettings].
 *
 * Every move is handed straight to [onChange]; the processor glides to it, so
 * there is no need to wait for the drag to end before it is heard.
 *
 * Decay and damping snap to 100 ms and 100 Hz — fine enough to tune by ear,
 * and coarse enough that a slider dragged back to where a preset left it lands
 * on that preset's number and the chip lights up again.
 */
@Composable
fun ReverbControls(
    settings: ReverbSettings,
    onChange: (ReverbSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val current = settings.preset
            ReverbPreset.entries.forEach { preset ->
                FilterChip(
                    selected = current == preset,
                    onClick = { onChange(settings.withPreset(preset)) },
                    label = { Text(stringResource(preset.label())) },
                )
            }
            if (current == null) {
                FilterChip(
                    selected = true,
                    onClick = {},
                    label = { Text(stringResource(Res.string.reverb_preset_custom)) },
                )
            }
        }

        ReverbSlider(
            title = Res.string.reverb_decay,
            help = Res.string.reverb_decay_help,
            value = settings.decayMs.toFloat(),
            range = ReverbSettings.DECAY_RANGE_MS.toFloatRange(),
            readout = String.format(Locale.ROOT, "%.1f s", settings.decayMs / 1_000f),
            onChange = { onChange(settings.copy(decayMs = it.snap(100))) },
        )
        ReverbSlider(
            title = Res.string.reverb_pre_delay,
            help = Res.string.reverb_pre_delay_help,
            value = settings.preDelayMs.toFloat(),
            range = ReverbSettings.PRE_DELAY_RANGE_MS.toFloatRange(),
            readout = "${settings.preDelayMs} ms",
            onChange = { onChange(settings.copy(preDelayMs = it.roundToInt())) },
        )
        ReverbSlider(
            title = Res.string.reverb_damping,
            help = Res.string.reverb_damping_help,
            value = settings.dampingHz.toFloat(),
            range = ReverbSettings.DAMPING_RANGE_HZ.toFloatRange(),
            readout = String.format(Locale.ROOT, "%.1f kHz", settings.dampingHz / 1_000f),
            onChange = { onChange(settings.copy(dampingHz = it.snap(100))) },
        )
        ReverbSlider(
            title = Res.string.reverb_mix,
            help = Res.string.reverb_mix_help,
            value = settings.mix,
            range = 0f..1f,
            readout = "${(settings.mix * 100).roundToInt()}%",
            onChange = { onChange(settings.copy(mix = (it * 100).roundToInt() / 100f)) },
        )
        ReverbSlider(
            title = Res.string.reverb_width,
            help = Res.string.reverb_width_help,
            value = settings.width,
            range = 0f..1f,
            readout = "${(settings.width * 100).roundToInt()}%",
            onChange = { onChange(settings.copy(width = (it * 100).roundToInt() / 100f)) },
        )

        TextButton(
            onClick = { onChange(ReverbSettings.DEFAULT) },
            enabled = settings != ReverbSettings.DEFAULT,
        ) {
            Text(stringResource(Res.string.reverb_reset))
        }
    }
}

@Composable
private fun ReverbSlider(
    title: StringResource,
    help: StringResource,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    readout: String,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = readout,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = stringResource(help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range)
    }
}

/** The chip label for [this] preset. */
fun ReverbPreset.label(): StringResource = when (this) {
    ReverbPreset.ROOM -> Res.string.reverb_preset_room
    ReverbPreset.HALL -> Res.string.reverb_preset_hall
    ReverbPreset.PLATE -> Res.string.reverb_preset_plate
    ReverbPreset.CATHEDRAL -> Res.string.reverb_preset_cathedral
}

private fun IntRange.toFloatRange() = first.toFloat()..last.toFloat()

private fun Float.snap(step: Int) = (this / step).roundToInt() * step
