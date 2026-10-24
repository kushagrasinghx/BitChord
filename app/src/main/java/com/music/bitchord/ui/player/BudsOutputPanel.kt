package com.music.bitchord.ui.player

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.device.DeviceFamily
import com.music.bitchord.device.DeviceRegistry
import com.music.bitchord.device.GalaxyBudsManager
import com.music.bitchord.playback.AudioOutputStatus
import com.music.bitchord.ui.screens.SegmentedControl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class PendingBudsAction(
    val action: () -> Boolean,
    val onSuccess: () -> Unit,
    val onFailure: () -> Unit,
)

@SuppressLint("MissingPermission")
private fun findBuds(
    context: Context,
    family: DeviceFamily,
    preferredName: String?,
): BluetoothDevice? {
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return null
    val candidates = adapter.bondedDevices.filter { DeviceRegistry.identify(it.name)?.family == family }
    return candidates.firstOrNull { it.name.equals(preferredName, ignoreCase = true) }
        ?: candidates.singleOrNull()
}

@Composable
fun BudsOutputPanel() {
    val status by AudioOutputStatus.current.collectAsStateWithLifecycle()
    val profile = status.bluetoothTelemetry?.deviceProfile ?: return
    if (!profile.family.isGalaxyBuds) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var preset by remember(profile.family) { mutableStateOf<Int?>(null) }
    var ancHigh by remember(profile.family) { mutableStateOf<Boolean?>(null) }
    var message by remember(profile.family) { mutableStateOf<String?>(null) }
    var busy by remember(profile.family) { mutableStateOf(false) }
    var busyMessage by remember(profile.family) { mutableStateOf("Conectando con los audífonos…") }
    var connected by remember(profile.family) { mutableStateOf(false) }
    var statusTimedOut by remember(profile.family) { mutableStateOf(false) }
    val spatialAudio by AppSettings.spatialAudio.collectAsStateWithLifecycle()
    var pendingNoise by remember(profile.family) { mutableStateOf<Byte?>(null) }
    val receivedStatus by GalaxyBudsManager.noiseStatus.collectAsStateWithLifecycle()
    val confirmedNoise = receivedStatus?.takeIf { it.family == profile.family }?.mode

    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        val device = findBuds(context, profile.family, status.bluetoothTelemetry?.deviceName)
            ?: return@withContext false
        GalaxyBudsManager.isConnectedTo(device, profile.family) ||
            GalaxyBudsManager.connectToHardware(device, profile.family)
    }

    fun reconnect() {
        val allowed = Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
        if (!allowed) {
            message = "Se necesita permiso de Bluetooth para conectar y leer el estado."
            return
        }
        scope.launch {
            busy = true
            busyMessage = "Conectando con los audífonos…"
            statusTimedOut = false
            message = null
            connected = try {
                connect()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("BudsOutputPanel", "Could not connect to Galaxy Buds", e)
                false
            } finally {
                busy = false
            }
            if (!connected) {
                message = "No se logró conectar. Comprueba Bluetooth y vuelve a intentarlo."
            }
        }
    }

    LaunchedEffect(profile.family) {
        val allowed = Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
        if (!allowed) {
            message = "Se necesita permiso de Bluetooth para conectar y leer el estado."
            return@LaunchedEffect
        }

        busy = true
        busyMessage = "Conectando con los audífonos…"
        connected = try {
            connect()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("BudsOutputPanel", "Could not connect to Galaxy Buds", e)
            false
        } finally {
            busy = false
        }
        if (!connected) {
            message = "No se logró conectar. Comprueba Bluetooth y vuelve a intentarlo."
        }
    }

    LaunchedEffect(connected, confirmedNoise) {
        statusTimedOut = false
        if (connected && confirmedNoise == null) {
            delay(5000)
            if (GalaxyBudsManager.isConnectedToHardware) {
                statusTimedOut = true
            }
        }
    }

    LaunchedEffect(pendingNoise, confirmedNoise) {
        if (pendingNoise != null && pendingNoise == confirmedNoise) pendingNoise = null
    }

    LaunchedEffect(pendingNoise) {
        if (pendingNoise != null) {
            delay(3000)
            pendingNoise = null
        }
    }

    fun run(action: () -> Boolean, onOk: () -> Unit, onFailure: () -> Unit) {
        scope.launch {
            busy = true
            busyMessage = "Conectando y aplicando cambio…"
            val ok = try {
                withContext(Dispatchers.IO) {
                    val device = findBuds(context, profile.family, status.bluetoothTelemetry?.deviceName)
                        ?: return@withContext false
                    if (!GalaxyBudsManager.isConnectedTo(device, profile.family) &&
                        !GalaxyBudsManager.connectToHardware(device, profile.family)
                    ) {
                        return@withContext false
                    }
                    action()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("BudsOutputPanel", "Could not send Galaxy Buds control", e)
                false
            } finally {
                busy = false
            }
            if (ok) {
                onOk()
                message = null
            } else {
                onFailure()
                message = "No se pudo aplicar el cambio. Comprueba que los audífonos sigan conectados e inténtalo otra vez."
            }
        }
    }

    var queued by remember(profile.family) { mutableStateOf<PendingBudsAction?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val pending = queued
        if (granted && pending != null) run(pending.action, pending.onSuccess, pending.onFailure)
        else if (!granted) {
            pending?.onFailure?.invoke()
            message = "Falta el permiso de Bluetooth."
        }
    }

    fun request(action: () -> Boolean, onOk: () -> Unit, onFailure: () -> Unit = {}) {
        val allowed = Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
        if (allowed) run(action, onOk, onFailure)
        else {
            queued = PendingBudsAction(action, onOk, onFailure)
            launcher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    val family = profile.family
    val shownNoise = pendingNoise ?: confirmedNoise
    val modes = buildList {
        add("Apagado" to GalaxyBudsManager.NoiseControl.OFF)
        if (family.supportsAnc) add("ANC" to GalaxyBudsManager.NoiseControl.ANC)
        if (family.supportsAmbientMode) add("Ambiente" to GalaxyBudsManager.NoiseControl.AMBIENT)
        if (family.supportsAdaptiveMode) add("Adapt." to GalaxyBudsManager.NoiseControl.ADAPTIVE)
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = profile.displayName,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Text(
            text = "Control de ruido",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        )
        SegmentedControl(
            options = modes.map { it.first },
            selectedIndex = modes.indexOfFirst { it.second == shownNoise },
            onSelect = { index ->
                val mode = modes[index].second
                pendingNoise = mode
                message = null
                request(
                    { GalaxyBudsManager.setNoiseControlMode(mode, family) },
                    {
                        if (mode != GalaxyBudsManager.NoiseControl.ANC) ancHigh = null
                    },
                    { pendingNoise = null },
                )
            },
            enabled = !busy,
        )
        Text(
            text = when (confirmedNoise) {
                GalaxyBudsManager.NoiseControl.OFF -> "Activo en los audífonos: Apagado"
                GalaxyBudsManager.NoiseControl.ANC -> "Activo en los audífonos: ANC"
                GalaxyBudsManager.NoiseControl.AMBIENT -> "Activo en los audífonos: Ambiente"
                GalaxyBudsManager.NoiseControl.ADAPTIVE -> "Activo en los audífonos: Adaptativo"
                else -> if (statusTimedOut) {
                    "Conectado, pero los audífonos no enviaron su estado."
                } else if (connected) {
                    "Conectado; leyendo el estado…"
                } else {
                    "Aún no hay conexión con los audífonos."
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        if (!connected || statusTimedOut) {
            Text(
                text = "Reintentar conexión",
                modifier = Modifier
                    .clickable(enabled = !busy, onClick = ::reconnect)
                    .padding(start = 4.dp, top = 2.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (pendingNoise != null) {
            Text(
                text = "Aplicando selección…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        if (family.supportsAncLevel) {
            Text(
                text = "Intensidad de ANC",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
            SegmentedControl(
                options = listOf("Suave", "Intensa"),
                selectedIndex = when (ancHigh) {
                    false -> 0
                    true -> 1
                    null -> -1
                },
                onSelect = { index ->
                    val high = index == 1
                    request(
                        { GalaxyBudsManager.setAncLevel(high, family) },
                        { ancHigh = high },
                    )
                },
                enabled = !busy && shownNoise == GalaxyBudsManager.NoiseControl.ANC,
            )
        }

        if (family.supportsAudio360) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Audio 360",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Efecto espacial BitChord; no activa el 360 nativo de Samsung.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = spatialAudio,
                    onCheckedChange = AppSettings::setSpatialAudio,
                )
            }
        }

        if (family.supportsPresetEqualizer) {
            Text(
                text = "Ecualizador de los audífonos",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
            Text(
                text = "La selección solo refleja cambios enviados desde BitChord.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val presets = listOf(
                    "Normal" to -1,
                    "Bass Boost" to GalaxyBudsManager.EqPreset.BASS_BOOST,
                    "Soft" to GalaxyBudsManager.EqPreset.SOFT,
                    "Dynamic" to GalaxyBudsManager.EqPreset.DYNAMIC,
                    "Clear" to GalaxyBudsManager.EqPreset.CLEAR,
                    "Treble Boost" to GalaxyBudsManager.EqPreset.TREBLE_BOOST,
                )
                presets.forEach { (name, id) ->
                    FilterChip(
                        selected = preset == id,
                        onClick = {
                            if (id == -1) {
                                request(
                                    { GalaxyBudsManager.setEqualizerPreset(false, 0, family) },
                                    { preset = id },
                                )
                            } else {
                                request(
                                    { GalaxyBudsManager.setEqualizerPreset(true, id, family) },
                                    { preset = id },
                                )
                            }
                        },
                        enabled = !busy,
                        label = { Text(name, maxLines = 1) },
                    )
                }
            }
        }

        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(4.dp),
            )
        }
        if (busy) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = busyMessage,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
