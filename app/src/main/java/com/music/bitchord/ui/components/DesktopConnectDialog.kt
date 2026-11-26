package com.music.bitchord.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.connect.ConnectProtocol
import com.music.bitchord.playback.connect.DesktopConnectController
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics
import dev.chrisbanes.haze.HazeState

/**
 * Receiver picker for BitChord PC instances on the local network (mDNS) or reachable by IP
 * (including personal VPNs like Tailscale). Drawn in [AudioPopupCard] alongside [CastDialog].
 */
@Composable
fun DesktopConnectDialog(
    hazeState: HazeState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by DesktopConnectController.state.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    var picked by remember { mutableStateOf(false) }
    var pairingDevice by remember { mutableStateOf<DesktopConnectController.Device?>(null) }
    var pinDraft by remember { mutableStateOf("") }
    var showManual by remember { mutableStateOf(false) }
    var manualHost by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf(ConnectProtocol.DEFAULT_PORT.toString()) }
    var manualPin by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        val stop = DesktopConnectController.discover()
        onDispose(stop)
    }

    LaunchedEffect(state.connectedDeviceId, picked) {
        if (picked && state.connected) {
            pairingDevice = null
            onDismiss()
        }
    }

    AudioPopupCard(
        hazeState = hazeState,
        title = "BitChord PC",
        subtitle = "Play on your computer over Wi-Fi or local network",
        doneLabel = "Done",
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxWidth()) {
            PipelineRule(Modifier)

            state.error?.let { err ->
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                    color = Color(0xFFFF6B63),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
                PipelineRule(Modifier)
            }

            if (state.devices.isEmpty()) {
                DesktopSearchingRow()
            } else {
                state.devices.forEach { device ->
                    DesktopDeviceRow(
                        device = device,
                        onClick = {
                            if (device.connected) return@DesktopDeviceRow
                            haptics.play(Haptic.Select)
                            DesktopConnectController.clearError()
                            if (device.paired) {
                                picked = true
                                DesktopConnectController.connect(device.id)
                            } else {
                                pairingDevice = device
                                pinDraft = ""
                            }
                        },
                        onForget = if (device.paired && !device.connected) {
                            { DesktopConnectController.forgetDevice(device.id) }
                        } else {
                            null
                        },
                    )
                    if (pairingDevice?.id == device.id) {
                        InlinePinPrompt(
                            deviceName = device.name,
                            pin = pinDraft,
                            busy = state.pairing,
                            onPinChange = { pinDraft = it.filter(Char::isDigit).take(6) },
                            onSubmit = {
                                picked = true
                                DesktopConnectController.pairAndConnect(device.host, device.port, pinDraft)
                            },
                            onCancel = { pairingDevice = null },
                        )
                    }
                    PipelineRule(Modifier.padding(start = 56.dp))
                }
            }

            // Manual IP / Tailscale / Pairing Link section
            DesktopExpandRow(
                label = if (showManual) "Hide manual connection" else "Connect by IP or pairing link",
                onClick = { showManual = !showManual },
            )
            if (showManual) {
                ManualConnectForm(
                    hostOrLink = manualHost,
                    port = manualPort,
                    pin = manualPin,
                    busy = state.pairing,
                    onHostChange = { manualHost = it },
                    onPortChange = { manualPort = it.filter(Char::isDigit).take(5) },
                    onPinChange = { manualPin = it.filter(Char::isDigit).take(6) },
                    onSubmit = {
                        picked = true
                        DesktopConnectController.pairFromManualInput(manualHost, manualPort, manualPin)
                    },
                )
                PipelineRule(Modifier)
            }

            if (state.connected) {
                DesktopActionRow(
                    label = "Switch playback back to phone",
                    accent = Color.White,
                    onClick = {
                        haptics.play(Haptic.Select)
                        DesktopConnectController.disconnect(resumeHere = true)
                        onDismiss()
                    },
                )
                PipelineRule(Modifier)
                DesktopActionRow(
                    label = "Disconnect (keep playing on PC)",
                    accent = Color(0xFFFF6B63),
                    onClick = {
                        haptics.play(Haptic.Select)
                        DesktopConnectController.disconnect(resumeHere = false)
                        onDismiss()
                    },
                )
            }
        }
    }
}

@Composable
private fun DesktopDeviceRow(
    device: DesktopConnectController.Device,
    onClick: () -> Unit,
    onForget: (() -> Unit)?,
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
                imageVector = Icons.Rounded.Computer,
                contentDescription = null,
                tint = Color.White.copy(alpha = if (device.connected) 1f else 0.8f),
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = device.name,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 15.sp,
                    fontWeight = if (device.connected) FontWeight.W600 else FontWeight.Normal,
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = when {
                device.connecting -> "Connecting…"
                device.connected -> "Playing on PC"
                device.paired && device.online -> "${device.host} · Paired"
                device.paired -> "${device.host}:${device.port} · Saved"
                else -> "${device.host} · Tap to pair with PIN"
            }
            Text(
                text = subtitle,
                modifier = Modifier.padding(top = 1.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            device.connecting -> CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = Color.White.copy(alpha = 0.8f),
                strokeWidth = 1.75.dp,
            )
            device.connected -> Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
            onForget != null -> IconButton(onClick = onForget, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Forget PC",
                    tint = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun InlinePinPrompt(
    deviceName: String,
    pin: String,
    busy: Boolean,
    onPinChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Enter 6-digit PIN from $deviceName",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
            color = Color.White.copy(alpha = 0.85f),
        )
        OutlinedTextField(
            value = pin,
            onValueChange = onPinChange,
            placeholder = { Text("123456", color = Color.White.copy(alpha = 0.35f)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = darkPopupFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onCancel, enabled = !busy) {
                Text("Cancel", color = Color.White.copy(alpha = 0.7f))
            }
            Spacer(Modifier.width(6.dp))
            Button(
                onClick = onSubmit,
                enabled = !busy && pin.length >= 4,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                ),
            ) {
                Text(if (busy) "Pairing…" else "Pair")
            }
        }
    }
}

@Composable
private fun ManualConnectForm(
    hostOrLink: String,
    port: String,
    pin: String,
    busy: Boolean,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onPinChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = hostOrLink,
            onValueChange = onHostChange,
            label = { Text("PC IP or pairing link", color = Color.White.copy(alpha = 0.65f)) },
            placeholder = { Text("192.168.1.50 or bitchord-connect://…", color = Color.White.copy(alpha = 0.35f)) },
            singleLine = true,
            colors = darkPopupFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = port,
                onValueChange = onPortChange,
                label = { Text("Port", color = Color.White.copy(alpha = 0.65f)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = darkPopupFieldColors(),
                modifier = Modifier.weight(0.45f),
            )
            OutlinedTextField(
                value = pin,
                onValueChange = onPinChange,
                label = { Text("PIN", color = Color.White.copy(alpha = 0.65f)) },
                placeholder = { Text("6 digits", color = Color.White.copy(alpha = 0.35f)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = darkPopupFieldColors(),
                modifier = Modifier.weight(0.55f),
            )
        }
        Button(
            onClick = onSubmit,
            enabled = !busy && hostOrLink.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color.Black,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (busy) "Connecting…" else "Pair & Connect")
        }
    }
}

@Composable
private fun DesktopSearchingRow() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = Color.White.copy(alpha = 0.7f),
            strokeWidth = 2.dp,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Looking for BitChord PC…",
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
            color = Color.White,
        )
        Text(
            text = "Open BitChord on your computer on the same Wi-Fi, or connect manually below.",
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DesktopExpandRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Wifi,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = Color.White.copy(alpha = 0.85f),
        )
    }
}

@Composable
private fun DesktopActionRow(label: String, accent: Color, onClick: () -> Unit) {
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
                fontSize = 14.5.sp,
                fontWeight = FontWeight.W600,
            ),
            color = accent,
        )
    }
}

@Composable
private fun darkPopupFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = Color.White.copy(alpha = 0.75f),
    unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
    cursorColor = Color.White,
)
