package com.music.bitchord.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.music.bitchord.device.DeviceFamily
import com.music.bitchord.device.DeviceProfile
import com.music.bitchord.device.DeviceRegistry
import com.music.bitchord.device.GalaxyBudsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("MissingPermission")
private fun findBuds(context: Context, family: DeviceFamily): BluetoothDevice? {
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return null
    return adapter.bondedDevices.firstOrNull { DeviceRegistry.identify(it.name)?.family == family }
}

/** Controles de ruido disponibles para el modelo Galaxy Buds conectado. */
@Composable
fun BudsNoiseControlSection(profile: DeviceProfile?) {
    if (profile == null) return
    if (!profile.family.isGalaxyBuds || !profile.family.supportsAnyNoiseControl) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by remember(profile.family) { mutableStateOf<Byte?>(null) }
    var message by remember(profile.family) { mutableStateOf<String?>(null) }
    var pendingMode by remember(profile.family) { mutableStateOf<Byte?>(null) }

    fun sendMode(mode: Byte) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                val device = findBuds(context, profile.family) ?: return@withContext false
                if (!GalaxyBudsManager.isConnectedTo(device, profile.family) &&
                    !GalaxyBudsManager.connectToHardware(device, profile.family)
                ) {
                    return@withContext false
                }
                GalaxyBudsManager.setNoiseControlMode(mode, profile.family)
            }
            if (ok) {
                selected = mode
                message = null
            } else {
                message = "No se pudo conectar o aplicar el cambio. Cierra Galaxy Wearable e intenta de nuevo."
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val mode = pendingMode
        if (granted && mode != null) sendMode(mode) else if (!granted) message = "Falta el permiso de Bluetooth."
    }

    fun request(mode: Byte) {
        val allowed = Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
        if (allowed) {
            sendMode(mode)
        } else {
            pendingMode = mode
            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    val modes = buildList {
        add("Apagado" to GalaxyBudsManager.NoiseControl.OFF)
        if (profile.family.supportsAnc) add("Cancelación de ruido (ANC)" to GalaxyBudsManager.NoiseControl.ANC)
        if (profile.family.supportsAmbientMode) add("Sonido ambiente" to GalaxyBudsManager.NoiseControl.AMBIENT)
        if (profile.family.supportsAdaptiveMode) add("Cancelación adaptativa" to GalaxyBudsManager.NoiseControl.ADAPTIVE)
    }

    SettingsGroup(
        footer = message ?: "Control Bluetooth. El estado se conoce después de elegir un modo.",
        topSpacing = 8.dp,
    ) {
        SettingsRow(icon = Icons.Rounded.Headset, title = profile.displayName)
        modes.forEach { (label, mode) ->
            RowDivider()
            ChoiceRow(
                label = label,
                selected = selected == mode,
                onClick = { request(mode) },
                enabled = true,
            )
        }
    }
}