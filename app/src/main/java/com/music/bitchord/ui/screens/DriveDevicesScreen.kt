package com.music.bitchord.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.backup.DriveBackup
import com.music.bitchord.data.backup.RemoteDevice
import com.music.bitchord.ui.icons.SfIcons
import kotlinx.coroutines.launch

/**
 * Settings → Backup and Restore → Google Drive backup → which device this phone is.
 *
 * Asked once, when an account with devices in it is connected, and again
 * whenever the listener wants to restore: back up as a **new device**, whose
 * listening adds to every other device's in the Replay, or **restore as** one
 * already in Drive, which this phone then becomes — its listening and settings
 * come down, and this phone carries on writing them.
 *
 * A page of its own on the app's navigator, so Back leaves it like any other;
 * restoring is confirmed in the app's frosted alert, raised to
 * [com.music.bitchord.MainActivity] through [onConfirmRestore].
 */
@Composable
fun DriveDevicesScreen(
    contentPadding: PaddingValues,
    onConfirmRestore: (RemoteDevice) -> Unit,
    /** Leaves the page: the choice is made. */
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by DriveBackup.settings.collectAsStateWithLifecycle()
    val devices by DriveBackup.devices.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { DriveBackup.refreshDevices(context) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        SettingsPageTitle(stringResource(R.string.drive_devices_title))

        if (settings.needsDevice) {
            SettingsGroup(topSpacing = 0.dp, footer = stringResource(R.string.drive_new_device_footer)) {
                SettingsRow(
                    icon = SfIcons.CloudUpload,
                    title = stringResource(R.string.drive_new_device),
                    subtitle = DriveBackup.deviceName(),
                    onClick = {
                        DriveBackup.chooseNewDevice(context)
                        onDone()
                    },
                )
            }
        }

        SettingsGroup(
            header = stringResource(R.string.drive_restore_as_header),
            footer = stringResource(R.string.drive_restore_as_footer),
        ) {
            when (val d = devices) {
                DriveBackup.Devices.Unknown, DriveBackup.Devices.Checking -> SettingsRow(
                    icon = SfIcons.Cloud,
                    title = stringResource(R.string.drive_devices_checking),
                    trailing = { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) },
                )
                is DriveBackup.Devices.Failed -> SettingsRow(
                    icon = SfIcons.ErrorOutline,
                    title = stringResource(R.string.drive_devices_failed, d.message),
                    onClick = { scope.launch { DriveBackup.refreshDevices(context) } },
                )
                is DriveBackup.Devices.Loaded -> {
                    val others = d.list.filter { it.deviceId != settings.identity.deviceId }
                    if (others.isEmpty()) {
                        SettingsRow(icon = SfIcons.Cloud, title = stringResource(R.string.drive_devices_none_other))
                    } else {
                        others.forEachIndexed { index, device ->
                            if (index > 0) RowDivider()
                            SettingsRow(
                                icon = SfIcons.Phone,
                                title = device.name,
                                subtitle = describe(device),
                                value = stringResource(R.string.drive_restore_action),
                                onClick = { onConfirmRestore(device) },
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}
