package com.music.bitchord.desktop

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.music.bitchord.playback.EqLayout
import com.music.bitchord.playback.EqualizerPreset
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The app's own equaliser, in the two shapes people have already been taught to read one in.
 *
 * **Dynamic** is a tone pad: one puck over a dot grid, tilting the spectrum left-to-right and
 * shaping the middle of it bottom-to-top, with Broad and Focused choosing how wide each move is.
 *
 * **Manual** is the seven-band graphic equaliser, with a preset list beside it.
 *
 * Both drive the same filters — [EqLayout], shared with the Android build — so the tab is a way of
 * describing a curve rather than a different piece of audio machinery.
 *
 * Laid out for a window rather than transcribed from the phone: the two tabs sit in a dialog wide
 * enough that the preset list stands beside the bands instead of behind a sheet, and every control
 * is driven by a pointer, so the haptic ticks Android leans on are carried by hover and the snap
 * instead.
 */
@Composable
internal fun DesktopEqualizerDialog(onDismiss: () -> Unit) {
    val enabled by DesktopEqualizerSettings.enabled.collectAsState()
    val mode by DesktopEqualizerSettings.mode.collectAsState()
    val balance by DesktopEqualizerSettings.balance.collectAsState()

    DesktopDialogPanel(onDismiss = onDismiss, maxWidth = 560) {
        Row(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 18.dp, top = 18.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    DesktopStrings["equalizer", "Equalizer"],
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                )
                Text(
                    DesktopStrings["equalizer_footer", "Shapes the sound inside BitChord, before it reaches the system."],
                    style = MaterialTheme.typography.bodySmall,
                    color = DesktopSecondary,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = DesktopEqualizerSettings::setEnabled,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = DesktopAccent,
                    checkedBorderColor = DesktopAccent,
                ),
            )
        }

        // Off, the apparatus below reads as unavailable rather than merely idle: dimmed, and it
        // stops answering the pointer, so nobody drags a band wondering why nothing changed.
        val alpha by animateFloatAsState(if (enabled) 1f else 0.38f, label = "equalizerEnabled")
        Column(
            Modifier
                .graphicsLayer { this.alpha = alpha }
                .then(if (enabled) Modifier else Modifier.pointerInput(Unit) {})
                .verticalScroll(rememberScrollState())
                .heightIn(max = 520.dp),
        ) {
            Spacer(Modifier.height(14.dp))
            ModeTabs(mode, DesktopEqualizerSettings::setMode)

            when (mode) {
                DesktopEqualizerMode.DYNAMIC -> DynamicTab()
                DesktopEqualizerMode.MANUAL -> ManualTab()
            }

            SectionLabel(DesktopStrings["equalizer_balance", "Balance"])
            BalanceControl(balance, DesktopEqualizerSettings::setBalance)
            Text(
                DesktopStrings["equalizer_balance_footer", "Shifts the sound towards one ear by quietening the other."],
                style = MaterialTheme.typography.bodySmall,
                color = DesktopSecondary,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
            )
            Spacer(Modifier.height(8.dp))
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(DesktopStrings["done", "Done"], color = Color.White) }
        }
    }
}

@Composable
private fun ModeTabs(mode: DesktopEqualizerMode, onSelect: (DesktopEqualizerMode) -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 22.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DesktopEqualizerMode.entries.forEach { option ->
            val selected = option == mode
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (selected) Color.White.copy(alpha = 0.92f) else Color.Transparent)
                    .clickable { onSelect(option) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when (option) {
                        DesktopEqualizerMode.DYNAMIC -> DesktopStrings["equalizer_dynamic", "Dynamic"]
                        DesktopEqualizerMode.MANUAL -> DesktopStrings["equalizer_manual", "Manual"]
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) Color.Black else Color.White,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(Locale.ROOT),
        style = MaterialTheme.typography.labelSmall,
        color = DesktopSecondary,
        modifier = Modifier.padding(start = 22.dp, top = 18.dp, bottom = 6.dp),
    )
}

// ---- Dynamic ---------------------------------------------------------------

@Composable
private fun DynamicTab() {
    val x by DesktopEqualizerSettings.toneX.collectAsState()
    val y by DesktopEqualizerSettings.toneY.collectAsState()
    val focused by DesktopEqualizerSettings.focused.collectAsState()

    SectionLabel(DesktopStrings["equalizer_tone", "Tone"])
    TonePad(x, y, DesktopEqualizerSettings::setTone)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Readout(DesktopStrings["equalizer_tilt", "Tilt"], x)
        Spacer(Modifier.width(28.dp))
        Readout(DesktopStrings["equalizer_contour", "Contour"], y)
    }
    Row(Modifier.padding(horizontal = 22.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WidthChoice(DesktopStrings["equalizer_broad", "Broad"], !focused) {
            DesktopEqualizerSettings.setFocused(false)
        }
        WidthChoice(DesktopStrings["equalizer_focused", "Focused"], focused) {
            DesktopEqualizerSettings.setFocused(true)
        }
    }
    Text(
        if (focused) {
            DesktopStrings["equalizer_focused_footer", "Drag left for a warmer sound and right for a brighter one; down scoops the middle, up brings it forward. Focused works on narrower stretches, so it changes less around what it moves."]
        } else {
            DesktopStrings["equalizer_broad_footer", "Drag left for a warmer sound and right for a brighter one; down scoops the middle, up brings it forward. Broad moves wide stretches of the spectrum at once."]
        },
        style = MaterialTheme.typography.bodySmall,
        color = DesktopSecondary,
        modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
    )
}

@Composable
private fun Readout(label: String, value: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = DesktopSecondary)
        Spacer(Modifier.width(8.dp))
        Text(
            if (value > 0) "+$value" else value.toString(),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
        )
    }
}

@Composable
private fun WidthChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color.White.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else DesktopSecondary,
        )
        if (selected) {
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Rounded.Check, null, tint = DesktopAccent, modifier = Modifier.size(15.dp))
        }
    }
}

/**
 * The dot grid and its puck.
 *
 * Drawn rather than composed: eleven by eleven is 121 dots, and 121 layout nodes re-measured on
 * every frame of a drag is a stutter on the exact gesture that has to feel direct.
 */
@Composable
private fun TonePad(x: Int, y: Int, onChange: (Int, Int) -> Unit) {
    val steps = EqLayout.TONE_STEPS
    // The gesture block is keyed on something that never changes, so it captures whatever was in
    // scope then. Read through these and a second drag starts from where the puck actually is.
    val latestX by rememberUpdatedState(x)
    val latestY by rememberUpdatedState(y)

    // Spring, not tween: the puck has to keep up with a fast drag and still settle rather than
    // arrive and stop dead.
    val animatedX by animateFloatAsState(
        x.toFloat(),
        spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessHigh),
        label = "tonePadX",
    )
    val animatedY by animateFloatAsState(
        y.toFloat(),
        spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessHigh),
        label = "tonePadY",
    )

    Canvas(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
            .height(200.dp)
            .pointerInput(steps) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    var lastX = latestX
                    var lastY = latestY
                    fun report(offset: Offset) {
                        val (newX, newY) = stepAt(offset, size.toSize(), PUCK_RADIUS.toPx(), steps)
                        if (newX != lastX || newY != lastY) {
                            lastX = newX
                            lastY = newY
                            onChange(newX, newY)
                        }
                    }
                    report(down.position)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        if (change.positionChanged()) {
                            change.consume()
                            report(change.position)
                        }
                    }
                }
            },
    ) {
        val inset = PUCK_RADIUS.toPx()
        val usableWidth = size.width - inset * 2
        val usableHeight = size.height - inset * 2
        val columns = steps * 2

        for (column in 0..columns) {
            for (row in 0..columns) {
                // The row and column through dead centre are drawn up, so the origin is findable
                // without a reading.
                val onAxis = column == steps || row == steps
                drawCircle(
                    color = Color.White.copy(alpha = if (onAxis) 0.45f else 0.22f),
                    radius = DOT_RADIUS.toPx(),
                    center = Offset(
                        inset + usableWidth * column / columns,
                        inset + usableHeight * row / columns,
                    ),
                )
            }
        }

        val centre = Offset(
            inset + usableWidth * (animatedX + steps) / columns,
            inset + usableHeight * (steps - animatedY) / columns,
        )
        // Three soft rings instead of a blur: a shadow pass on something this small costs more than
        // the circles it would be shading.
        for (ring in 3 downTo 1) {
            drawCircle(
                color = Color.Black.copy(alpha = 0.16f / ring),
                radius = inset + ring * 2f,
                center = centre.copy(y = centre.y + ring),
            )
        }
        drawCircle(color = Color.White, radius = inset, center = centre)
    }
}

/** Which step the pointer is over, clamped to the grid. */
private fun stepAt(offset: Offset, size: Size, inset: Float, steps: Int): Pair<Int, Int> {
    val usableWidth = (size.width - inset * 2).coerceAtLeast(1f)
    val usableHeight = (size.height - inset * 2).coerceAtLeast(1f)
    val fractionX = ((offset.x - inset) / usableWidth).coerceIn(0f, 1f)
    val fractionY = ((offset.y - inset) / usableHeight).coerceIn(0f, 1f)
    val x = (fractionX * steps * 2).roundToInt() - steps
    // Screen coordinates grow downwards and the control does not: up is more.
    return x to (steps - (fractionY * steps * 2).roundToInt())
}

// ---- Manual ----------------------------------------------------------------

@Composable
private fun ManualTab() {
    val bands by DesktopEqualizerSettings.bands.collectAsState()
    val preset by DesktopEqualizerSettings.preset.collectAsState()

    SectionLabel(DesktopStrings["equalizer_bands", "Bands"])
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
        BandSliders(bands, Modifier.weight(1f)) { DesktopEqualizerSettings.setBands(it) }
        Spacer(Modifier.width(14.dp))
        // Beside the bands rather than behind a sheet: a window has the width for the list, and
        // seeing the curve move as a preset is picked is the point of having them.
        PresetList(preset, Modifier.width(168.dp))
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            DesktopStrings["equalizer_bands_footer", "Moving a band leaves the preset behind and the curve becomes your own."],
            style = MaterialTheme.typography.bodySmall,
            color = DesktopSecondary,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { DesktopEqualizerSettings.setPreset(EqualizerPreset.FLAT) }) {
            Text(DesktopStrings["equalizer_reset", "Reset all bands"], color = DesktopAccent)
        }
    }
}

/**
 * Seven vertical faders, drawn.
 *
 * A Material slider rotated a quarter turn keeps its horizontal geometry and its horizontal thumb,
 * which is why every Compose equaliser built that way feels wrong. The fill runs from the centre
 * because the value is signed: −6 dB and +6 dB are equal and opposite, and a bar growing from the
 * floor would draw them as a short one and a tall one.
 */
@Composable
private fun BandSliders(bands: List<Float>, modifier: Modifier = Modifier, onChange: (List<Float>) -> Unit) {
    Row(modifier, horizontalArrangement = Arrangement.SpaceBetween) {
        EqLayout.MANUAL_BANDS_HZ.forEachIndexed { index, hz ->
            val value = bands.getOrElse(index) { 0f }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    formatGain(value),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (abs(value) < 0.05f) DesktopSecondary else DesktopAccent,
                    maxLines = 1,
                )
                Spacer(Modifier.height(6.dp))
                BandFader(value) { updated ->
                    onChange(bands.toMutableList().also { it[index] = updated })
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    formatFrequency(hz),
                    style = MaterialTheme.typography.labelSmall,
                    color = DesktopSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun BandFader(value: Float, onChange: (Float) -> Unit) {
    val range = EqLayout.MANUAL_RANGE_DB
    val latest by rememberUpdatedState(value)

    Canvas(
        Modifier
            .width(30.dp)
            .height(150.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    var last = latest
                    fun report(offset: Offset) {
                        val inset = KNOB_RADIUS.toPx()
                        val usable = (size.height - inset * 2).coerceAtLeast(1f)
                        val fraction = ((offset.y - inset) / usable).coerceIn(0f, 1f)
                        val snapped = snapGain((1f - fraction * 2f) * range, range)
                        if (abs(snapped - last) > 0.001f) {
                            last = snapped
                            onChange(snapped)
                        }
                    }
                    report(down.position)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        if (change.positionChanged()) {
                            change.consume()
                            report(change.position)
                        }
                    }
                }
            },
    ) {
        val inset = KNOB_RADIUS.toPx()
        val usable = size.height - inset * 2
        val centreY = inset + usable / 2
        val trackWidth = TRACK_WIDTH.toPx()
        val centreX = size.width / 2
        val knobY = inset + usable * (1f - (value / range + 1f) / 2f)

        drawRoundRect(
            color = Color.White.copy(alpha = 0.18f),
            topLeft = Offset(centreX - trackWidth / 2, inset),
            size = Size(trackWidth, usable),
            cornerRadius = CornerRadius(trackWidth / 2),
        )
        if (abs(value) > 0.05f) {
            drawRoundRect(
                color = DesktopAccent,
                topLeft = Offset(centreX - trackWidth / 2, minOf(centreY, knobY)),
                size = Size(trackWidth, abs(centreY - knobY)),
                cornerRadius = CornerRadius(trackWidth / 2),
            )
        }
        // The zero line, over the track so it survives a full-scale fill.
        drawLine(
            color = Color.White.copy(alpha = 0.35f),
            start = Offset(centreX - trackWidth, centreY),
            end = Offset(centreX + trackWidth, centreY),
            strokeWidth = 1.dp.toPx(),
        )
        drawCircle(
            color = Color.Black.copy(alpha = 0.25f),
            radius = inset + 1.5f,
            center = Offset(centreX, knobY + 1.5f),
        )
        drawCircle(color = Color.White, radius = inset, center = Offset(centreX, knobY))
    }
}

@Composable
private fun PresetList(selected: EqualizerPreset, modifier: Modifier = Modifier) {
    Column(modifier.heightIn(max = 186.dp).verticalScroll(rememberScrollState())) {
        EqualizerPreset.entries.forEach { preset ->
            // Custom is a state the bands can be in, not a curve anyone can choose.
            if (preset == EqualizerPreset.CUSTOM && selected != EqualizerPreset.CUSTOM) return@forEach
            val chosen = preset == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (chosen) Color.White.copy(alpha = 0.09f) else Color.Transparent)
                    .clickable(enabled = preset != EqualizerPreset.CUSTOM) {
                        DesktopEqualizerSettings.setPreset(preset)
                    }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    presetLabel(preset),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                if (chosen) {
                    Icon(Icons.Rounded.Check, null, tint = DesktopAccent, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

// ---- Balance ---------------------------------------------------------------

/**
 * L and R with the trim between them, and a detent at centre: dead centre is where this spends its
 * life, and it is the one position a drag cannot land on by aim alone.
 */
@Composable
private fun BalanceControl(balance: Float, onChange: (Float) -> Unit) {
    val latest by rememberUpdatedState(balance)
    Column(Modifier.padding(horizontal = 22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(DesktopStrings["equalizer_balance_left", "L"], style = MaterialTheme.typography.bodyMedium, color = Color.White)
            Text(
                String.format(Locale.ROOT, "%.2f", balance),
                style = MaterialTheme.typography.bodyMedium,
                color = DesktopSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Text(DesktopStrings["equalizer_balance_right", "R"], style = MaterialTheme.typography.bodyMedium, color = Color.White)
        }
        Spacer(Modifier.height(6.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(KNOB_RADIUS * 2 + 8.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        var last = latest
                        fun report(offset: Offset) {
                            val inset = KNOB_RADIUS.toPx()
                            val usable = (size.width - inset * 2).coerceAtLeast(1f)
                            val raw = ((offset.x - inset) / usable).coerceIn(0f, 1f) * 2f - 1f
                            val snapped = if (abs(raw) < BALANCE_DETENT) 0f else raw
                            if (abs(snapped - last) > 0.004f) {
                                last = snapped
                                onChange(snapped)
                            }
                        }
                        report(down.position)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            if (change.positionChanged()) {
                                change.consume()
                                report(change.position)
                            }
                        }
                    }
                },
        ) {
            val inset = KNOB_RADIUS.toPx()
            val usable = size.width - inset * 2
            val centreY = size.height / 2
            val centreX = inset + usable / 2
            val trackHeight = TRACK_WIDTH.toPx()
            val knobX = inset + usable * (balance + 1f) / 2f

            drawRoundRect(
                color = Color.White.copy(alpha = 0.18f),
                topLeft = Offset(inset, centreY - trackHeight / 2),
                size = Size(usable, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2),
            )
            if (abs(balance) > 0.004f) {
                drawRoundRect(
                    color = DesktopAccent,
                    topLeft = Offset(minOf(centreX, knobX), centreY - trackHeight / 2),
                    size = Size(abs(centreX - knobX), trackHeight),
                    cornerRadius = CornerRadius(trackHeight / 2),
                )
            }
            drawCircle(
                color = Color.Black.copy(alpha = 0.25f),
                radius = inset + 1.5f,
                center = Offset(knobX, centreY + 1.5f),
            )
            drawCircle(color = Color.White, radius = inset, center = Offset(knobX, centreY))
        }
    }
}

// ---- Formatting ------------------------------------------------------------

/**
 * Half-decibel steps, with a wider one at zero.
 *
 * Flat is the value a band gets dragged back to far more often than any other, so it is given a
 * detent to fall into rather than a coordinate to find.
 */
private fun snapGain(raw: Float, range: Float): Float {
    if (abs(raw) < ZERO_DETENT_DB) return 0f
    return ((raw * 2f).roundToInt() / 2f).coerceIn(-range, range)
}

private fun formatGain(value: Float): String = when {
    abs(value) < 0.05f -> "0"
    value > 0 -> "+" + trimGain(value)
    else -> trimGain(value)
}

/** "+6" rather than "+6.0", but "+6.5" when the half matters. */
private fun trimGain(value: Float): String =
    if (abs(value - value.roundToInt()) < 0.05f) {
        value.roundToInt().toString()
    } else {
        String.format(Locale.ROOT, "%.1f", value)
    }

/** "60", "2.5k", "14k" — the labels a hardware equaliser prints. */
private fun formatFrequency(hz: Float): String = when {
    hz < 1_000f -> hz.roundToInt().toString()
    hz % 1_000f == 0f -> "${(hz / 1_000f).roundToInt()}k"
    else -> String.format(Locale.ROOT, "%.1fk", hz / 1_000f)
}

private fun presetLabel(preset: EqualizerPreset): String = when (preset) {
    EqualizerPreset.FLAT -> DesktopStrings["eq_preset_flat", "Flat"]
    EqualizerPreset.ACOUSTIC -> DesktopStrings["eq_preset_acoustic", "Acoustic"]
    EqualizerPreset.BASS_BOOST -> DesktopStrings["eq_preset_bass_boost", "Bass boost"]
    EqualizerPreset.BASS_CUT -> DesktopStrings["eq_preset_bass_cut", "Bass cut"]
    EqualizerPreset.VOCAL -> DesktopStrings["eq_preset_vocal", "Vocal"]
    EqualizerPreset.TREBLE_BOOST -> DesktopStrings["eq_preset_treble_boost", "Treble boost"]
    EqualizerPreset.TREBLE_CUT -> DesktopStrings["eq_preset_treble_cut", "Treble cut"]
    EqualizerPreset.LOUDNESS -> DesktopStrings["eq_preset_loudness", "Loudness"]
    EqualizerPreset.SPOKEN_WORD -> DesktopStrings["eq_preset_spoken_word", "Spoken word"]
    EqualizerPreset.ELECTRONIC -> DesktopStrings["eq_preset_electronic", "Electronic"]
    EqualizerPreset.ROCK -> DesktopStrings["eq_preset_rock", "Rock"]
    EqualizerPreset.HIP_HOP -> DesktopStrings["eq_preset_hip_hop", "Hip-hop"]
    EqualizerPreset.JAZZ -> DesktopStrings["eq_preset_jazz", "Jazz"]
    EqualizerPreset.CLASSICAL -> DesktopStrings["eq_preset_classical", "Classical"]
    EqualizerPreset.SMALL_SPEAKERS -> DesktopStrings["eq_preset_small_speakers", "Small speakers"]
    EqualizerPreset.LATE_NIGHT -> DesktopStrings["eq_preset_late_night", "Late night"]
    EqualizerPreset.CUSTOM -> DesktopStrings["eq_preset_custom", "Custom"]
}

private val PUCK_RADIUS = 16.dp
private val DOT_RADIUS = 2.2.dp
private val TRACK_WIDTH = 5.dp
private val KNOB_RADIUS = 9.dp

/** How close to flat a band has to be dragged before it snaps there. */
private const val ZERO_DETENT_DB = 0.6f

/** Same, for the balance trim's own centre. */
private const val BALANCE_DETENT = 0.04f
