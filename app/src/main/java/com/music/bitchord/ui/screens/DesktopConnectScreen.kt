package com.music.bitchord.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.connect.ConnectProtocol
import com.music.bitchord.playback.connect.DesktopConnectController
import com.music.bitchord.ui.components.PillTextField

/**
 * Top-level Settings row right below Listen Together, opening [DesktopConnectScreen].
 */
@Composable
internal fun DesktopConnectSettingsRow(onClick: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { DesktopConnectController.ensureStarted(context) }
    val state by DesktopConnectController.state.collectAsStateWithLifecycle()

    SettingsRow(
        icon = Icons.Rounded.Computer,
        title = "Connect (LAN Remote)",
        subtitle = when {
            state.connectedName != null -> "Playing on ${state.connectedName}"
            state.connecting || state.pairing -> "Connecting…"
            else -> "Control BitChord PC playback over Wi-Fi or Tailscale"
        },
        value = if (state.connected) "Connected" else null,
        onClick = onClick,
    )
}

/**
 * Settings → Connect (LAN Remote): discover BitChord PCs on the local Wi-Fi (mDNS),
 * pair with a 6-digit PIN or manual IP / Tailscale / pairing link, and manage paired PCs.
 */
@Composable
fun DesktopConnectScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { DesktopConnectController.ensureStarted(context) }

    val state by DesktopConnectController.state.collectAsStateWithLifecycle()
    var pairingDeviceId by remember { mutableStateOf<String?>(null) }
    var pinDraft by rememberSaveable { mutableStateOf("") }
    var manualHost by rememberSaveable { mutableStateOf("") }
    var manualPort by rememberSaveable { mutableStateOf(ConnectProtocol.DEFAULT_PORT.toString()) }
    var manualPin by rememberSaveable { mutableStateOf("") }

    DisposableEffect(Unit) {
        val stop = DesktopConnectController.discover()
        onDispose(stop)
    }

    LaunchedEffect(state.connectedDeviceId) {
        if (state.connected) {
            pairingDeviceId = null
            pinDraft = ""
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        SettingsPageTitle("Connect (LAN Remote)")

        state.error?.let { err ->
            SettingsGroup {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = DesktopConnectController::clearError) {
                        Text("Dismiss")
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        if (state.connected) {
            SettingsGroup(
                header = "Active speaker",
                footer = "Audio plays directly on your PC. Controls, queue, progress, and hardware volume buttons on this phone stay synced.",
            ) {
                SettingsRow(
                    icon = Icons.Rounded.Computer,
                    title = state.connectedName ?: "BitChord PC",
                    subtitle = "Playing on PC",
                    trailing = {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                )
                RowDivider()
                SettingsRow(
                    icon = Icons.Rounded.PhoneAndroid,
                    title = "Switch playback back to phone",
                    subtitle = "Pause PC and continue listening here at the current position",
                    onClick = { DesktopConnectController.disconnect(resumeHere = true) },
                )
                RowDivider()
                SettingsRow(
                    icon = Icons.Rounded.PowerSettingsNew,
                    title = "Disconnect",
                    subtitle = "Leave music playing on PC",
                    onClick = { DesktopConnectController.disconnect(resumeHere = false) },
                )
            }
            Spacer(Modifier.height(18.dp))
        }

        SettingsGroup(
            header = "BitChord PCs on network",
            footer = "Open BitChord on your computer and go to Settings → Connect (LAN Remote) to view its 6-digit PIN.",
        ) {
            if (state.devices.isEmpty()) {
                SettingsRow(
                    icon = Icons.Rounded.Computer,
                    title = if (state.discovering) "Looking for BitChord PC…" else "No PC found yet",
                    subtitle = "Make sure your phone and PC are on the same Wi-Fi, or pair manually below.",
                    trailing = if (state.discovering) {
                        { CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) }
                    } else {
                        null
                    },
                )
            } else {
                state.devices.forEachIndexed { index, device ->
                    if (index > 0) RowDivider()
                    val subtitle = when {
                        device.connecting -> "Connecting…"
                        device.connected -> "Connected · ${device.host}"
                        device.paired && device.online -> "${device.host} · Paired"
                        device.paired -> "${device.host}:${device.port} · Saved"
                        else -> "${device.host} · Tap to pair with PIN"
                    }
                    SettingsRow(
                        icon = Icons.Rounded.Computer,
                        title = device.name,
                        subtitle = subtitle,
                        onClick = if (device.connected) {
                            null
                        } else {
                            {
                                DesktopConnectController.clearError()
                                if (device.paired) {
                                    DesktopConnectController.connect(device.id)
                                } else {
                                    pairingDeviceId = if (pairingDeviceId == device.id) null else device.id
                                    pinDraft = ""
                                }
                            }
                        },
                        trailing = {
                            when {
                                device.connecting -> CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                device.connected -> Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                device.paired -> Row(verticalAlignment = Alignment.CenterVertically) {
                                    ConnectActionPill("Connect") {
                                        DesktopConnectController.clearError()
                                        DesktopConnectController.connect(device.id)
                                    }
                                    Spacer(Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { DesktopConnectController.forgetDevice(device.id) },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = "Forget PC",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                                else -> ConnectActionPill("Pair") {
                                    DesktopConnectController.clearError()
                                    pairingDeviceId = device.id
                                    pinDraft = ""
                                }
                            }
                        },
                    )
                    if (pairingDeviceId == device.id) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = "Enter the 6-digit PIN shown on ${device.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            PillTextField(
                                value = pinDraft,
                                onValueChange = { pinDraft = it.filter(Char::isDigit).take(6) },
                                placeholder = "6-digit PIN",
                                enabled = !state.pairing,
                                container = MaterialTheme.colorScheme.surface,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                            PrimaryButton(
                                text = if (state.pairing) "Pairing…" else "Pair & Connect",
                                enabled = !state.pairing && pinDraft.length >= 4,
                                onClick = {
                                    DesktopConnectController.pairAndConnect(device.host, device.port, pinDraft)
                                },
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        SettingsGroup(
            header = "Connect manually (IP / Tailscale / Link)",
            footer = "Use your PC's local IP (e.g. 192.168.1.50), Tailscale IP (100.x.y.z), or paste the pairing link copied from BitChord PC.",
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PillTextField(
                    value = manualHost,
                    onValueChange = { manualHost = it },
                    placeholder = "IP address or bitchord-connect://pair?…",
                    enabled = !state.pairing,
                    container = MaterialTheme.colorScheme.surfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(0.42f)) {
                        PillTextField(
                            value = manualPort,
                            onValueChange = { manualPort = it.filter(Char::isDigit).take(5) },
                            placeholder = "Port (${ConnectProtocol.DEFAULT_PORT})",
                            enabled = !state.pairing,
                            container = MaterialTheme.colorScheme.surfaceVariant,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                    Box(Modifier.weight(0.58f)) {
                        PillTextField(
                            value = manualPin,
                            onValueChange = { manualPin = it.filter(Char::isDigit).take(6) },
                            placeholder = "6-digit PIN",
                            enabled = !state.pairing,
                            container = MaterialTheme.colorScheme.surfaceVariant,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                PrimaryButton(
                    text = if (state.pairing) "Connecting…" else "Pair & Connect",
                    enabled = !state.pairing && manualHost.isNotBlank(),
                    onClick = {
                        DesktopConnectController.pairFromManualInput(manualHost, manualPort, manualPin)
                    },
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ConnectActionPill(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}
