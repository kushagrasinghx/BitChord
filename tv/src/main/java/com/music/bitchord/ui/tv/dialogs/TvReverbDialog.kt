package com.music.bitchord.ui.tv.dialogs

import android.view.KeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.playback.ReverbPreset
import com.music.bitchord.playback.ReverbSettings
import com.music.bitchord.ui.tv.components.TvButton
import com.music.bitchord.ui.tv.components.TvDialog
import com.music.bitchord.ui.tv.components.TvListRow
import com.music.bitchord.ui.tv.components.tvInitialFocus
import com.music.bitchord.ui.tv.theme.TvGlass
import com.music.bitchord.ui.tv.theme.TvType
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The reverb as a tvOS sheet, with the phone's controls over the same
 * [ReverbSettings]: on/off, the room, then each parameter as a row that left
 * and right step down and up — a slider is no use to a remote. Select steps
 * up as well. Every step is clamped by [AppSettings.setReverb].
 */
@Composable
fun TvReverbDialog(onDismiss: () -> Unit) {
    val enabled by AppSettings.reverbEnabled.collectAsState()
    val settings by AppSettings.reverb.collectAsState()

    TvDialog(title = "Reverb", width = 512.dp, onDismissRequest = onDismiss) {
        TvListRow(
            title = "Reverb",
            value = if (enabled) "On" else "Off",
            modifier = Modifier.tvInitialFocus(),
            onClick = { AppSettings.setReverbEnabled(!enabled) },
        )
        Spacer(modifier = Modifier.height(10.dp))
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            item {
                val presets = ReverbPreset.entries
                val current = settings.preset
                TvStepRow(
                    title = "Room",
                    value = current?.label ?: "Custom",
                    onStep = { direction ->
                        val at = current?.ordinal ?: if (direction > 0) -1 else 0
                        val next = presets[(at + direction).mod(presets.size)]
                        AppSettings.setReverb(settings.withPreset(next))
                    },
                )
            }
            item {
                TvStepRow(
                    title = "Decay",
                    value = String.format(Locale.US, "%.1f s", settings.decayMs / 1_000f),
                    onStep = { AppSettings.setReverb(settings.copy(decayMs = settings.decayMs + it * 100)) },
                )
            }
            item {
                TvStepRow(
                    title = "Pre-delay",
                    value = "${settings.preDelayMs} ms",
                    onStep = { AppSettings.setReverb(settings.copy(preDelayMs = settings.preDelayMs + it * 5)) },
                )
            }
            item {
                TvStepRow(
                    title = "Damping",
                    value = String.format(Locale.US, "%.1f kHz", settings.dampingHz / 1_000f),
                    onStep = { AppSettings.setReverb(settings.copy(dampingHz = settings.dampingHz + it * 500)) },
                )
            }
            item {
                TvStepRow(
                    title = "Mix",
                    value = "${(settings.mix * 100).roundToInt()}%",
                    onStep = { AppSettings.setReverb(settings.copy(mix = ((settings.mix * 100).roundToInt() + it * 5) / 100f)) },
                )
            }
            item {
                TvStepRow(
                    title = "Width",
                    value = "${(settings.width * 100).roundToInt()}%",
                    onStep = { AppSettings.setReverb(settings.copy(width = ((settings.width * 100).roundToInt() + it * 10) / 100f)) },
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Left and right adjust a setting. Select steps it up.",
            style = TvType.Caption,
            color = TvGlass.TextTertiary,
        )
        Spacer(modifier = Modifier.height(14.dp))
        TvButton(text = "Reset All", onClick = { AppSettings.setReverb(ReverbSettings.DEFAULT) })
    }
}

/**
 * A row whose value left and right step through. Select is a step up: the
 * phone's sliders have no remote equivalent, and a row that only reacted to
 * left and right would look broken to anyone pressing the centre button.
 */
@Composable
private fun TvStepRow(title: String, value: String, onStep: (Int) -> Unit) {
    TvListRow(
        title = title,
        value = "‹  $value  ›",
        modifier = Modifier.onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
            when (event.nativeKeyEvent.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { onStep(-1); true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { onStep(1); true }
                else -> false
            }
        },
        onClick = { onStep(1) },
    )
}

private val ReverbPreset.label: String
    get() = when (this) {
        ReverbPreset.ROOM -> "Room"
        ReverbPreset.HALL -> "Hall"
        ReverbPreset.PLATE -> "Plate"
        ReverbPreset.CATHEDRAL -> "Cathedral"
    }
