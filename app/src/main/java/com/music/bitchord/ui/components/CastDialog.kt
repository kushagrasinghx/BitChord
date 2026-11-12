package com.music.bitchord.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.playback.cast.CastController
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics
import dev.chrisbanes.haze.HazeState

/**
 * Where to send the music: the receivers on this network, opened from the
 * output drawer's Cast row.
 *
 * Drawn in [AudioPopupCard], the card the audio pipeline readout uses, so the
 * two popups off the same drawer are visibly one family. The list is live —
 * receivers are found, and lost, while it is open — and the router is asked to
 * scan for as long as it is, which is the only time that is worth its cost.
 */
@Composable
fun CastDialog(
    hazeState: HazeState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cast by CastController.state.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    // Set by tapping a receiver, so the popup closes itself when that receiver
    // connects — but not when somebody opens it while already casting.
    var picked by remember { mutableStateOf(false) }

    DisposableEffect(cast.supported) {
        val stop = CastController.discover(active = true)
        onDispose(stop)
    }
    LaunchedEffect(cast.connectedName, picked) {
        if (picked && cast.connectedName != null) onDismiss()
    }

    AudioPopupCard(
        hazeState = hazeState,
        title = stringResource(R.string.cast_dialog_title),
        subtitle = stringResource(R.string.cast_dialog_subtitle),
        doneLabel = stringResource(R.string.done),
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxWidth()) {
            PipelineRule(Modifier)
            if (cast.devices.isEmpty()) {
                SearchingRow()
            } else {
                cast.devices.forEach { device ->
                    CastDeviceRow(
                        name = device.name,
                        connecting = device.connecting,
                        connected = cast.casting && device.connected,
                        onClick = {
                            if (!device.connected) {
                                haptics.play(Haptic.Select)
                                picked = true
                                CastController.connect(device.id)
                            }
                        },
                    )
                    PipelineRule(Modifier.padding(start = 56.dp))
                }
            }
            if (cast.casting) {
                CastActionRow(
                    label = stringResource(R.string.cast_stop),
                    onClick = {
                        haptics.play(Haptic.Select)
                        CastController.disconnect(resumeHere = true)
                        onDismiss()
                    },
                )
            }
        }
    }
}

@Composable
private fun CastDeviceRow(
    name: String,
    connecting: Boolean,
    connected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (pressed) Color.White.copy(alpha = 0.09f) else Color.Transparent)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (connected) Icons.Rounded.CastConnected else Icons.Rounded.Cast,
                contentDescription = null,
                tint = Color.White.copy(alpha = if (connected) 1f else 0.8f),
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 15.sp,
                    fontWeight = if (connected) FontWeight.W600 else FontWeight.Normal,
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (connecting || connected) {
                Text(
                    text = stringResource(
                        if (connecting) R.string.cast_device_connecting else R.string.cast_device_connected,
                    ),
                    modifier = Modifier.padding(top = 1.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                    color = Color.White.copy(alpha = 0.6f),
                )
            }
        }
        when {
            connecting -> CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = Color.White.copy(alpha = 0.8f),
                strokeWidth = 1.75.dp,
            )
            connected -> Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Shown until the first receiver turns up; it is also what an empty network looks like. */
@Composable
private fun SearchingRow() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = Color.White.copy(alpha = 0.7f),
            strokeWidth = 2.dp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.cast_searching),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
            color = Color.White,
        )
        Text(
            text = stringResource(R.string.cast_searching_hint),
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
            color = Color.White.copy(alpha = 0.6f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun CastActionRow(label: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (pressed) Color.White.copy(alpha = 0.09f) else Color.Transparent)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.W600,
            ),
            color = Color(0xFFFF6B63),
        )
    }
}
