package com.music.bitchord.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.data.settings.AndroidSyncManager
import com.music.bitchord.sync.SyncDevice
import com.music.bitchord.sync.SyncLocalState
import com.music.bitchord.ui.components.QrCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun SyncSettingsScreen(contentPadding: androidx.compose.foundation.layout.PaddingValues) {
    val context = LocalContext.current
    val manager = remember(context) { AndroidSyncManager(context) }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<SyncLocalState?>(null) }
    var linkedDevices by remember { mutableStateOf<List<SyncDevice>>(emptyList()) }
    var devicesLoading by remember { mutableStateOf(false) }
    var devicesError by remember { mutableStateOf<String?>(null) }
    var deviceToRevoke by remember { mutableStateOf<SyncDevice?>(null) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var localStateUnavailable by remember { mutableStateOf(false) }
    var serverUrl by rememberSaveable { mutableStateOf("") }
    var invitationCode by remember { mutableStateOf("") }
    var shownInvitation by remember { mutableStateOf<String?>(null) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var isError by rememberSaveable { mutableStateOf(false) }
    var busy by rememberSaveable { mutableStateOf(false) }
    val readyMessage = stringResource(R.string.sync_ready)
    val pairingReadyMessage = stringResource(R.string.sync_pairing_ready)
    val copiedMessage = stringResource(R.string.sync_copied)
    val revokedMessage = stringResource(R.string.sync_device_revoked)
    val devicesRefreshedMessage = stringResource(R.string.sync_devices_refreshed)
    val resetDoneMessage = stringResource(R.string.sync_reset_done)

    LaunchedEffect(manager) {
        try {
            state = manager.localState()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            localStateUnavailable = true
            message = error.message ?: error::class.simpleName.orEmpty()
            isError = true
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(state?.profileId) {
        if (state == null) return@LaunchedEffect
        devicesLoading = true
        try {
            linkedDevices = manager.listDevices()
            devicesError = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            devicesError = error.message ?: error::class.simpleName.orEmpty()
        } finally {
            devicesLoading = false
        }
    }

    fun launchSyncAction(success: String, action: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            message = null
            isError = false
            try {
                action()
                message = success
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                message = error.message ?: error::class.simpleName.orEmpty()
                isError = true
            } finally {
                try {
                    state = manager.localState()
                } catch (error: Exception) {
                    localStateUnavailable = true
                    message = message ?: error.message ?: error::class.simpleName.orEmpty()
                    isError = true
                }
                busy = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.cross_device_sync_subtitle))
        Text(stringResource(R.string.sync_service_note))

        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let {
            Text(if (isError) stringResource(R.string.sync_error, it) else it)
        }

        if (isLoading) {
            CircularProgressIndicator()
        } else if (localStateUnavailable) {
            message?.let { Text(stringResource(R.string.sync_error, it)) }
            OutlinedButton(
                onClick = { showResetConfirm = true },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.sync_reset_local)) }
        } else if (state == null) {
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text(stringResource(R.string.sync_server_url)) },
                singleLine = true,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    launchSyncAction(readyMessage) {
                        state = manager.createProfile(serverUrl.trim())
                    }
                },
                enabled = !busy && serverUrl.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.sync_create_profile))
            }
            OutlinedTextField(
                value = invitationCode,
                onValueChange = { invitationCode = it.take(4_096) },
                label = { Text(stringResource(R.string.sync_invitation_code)) },
                singleLine = true,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = {
                    launchSyncAction(readyMessage) {
                        state = manager.acceptPairingInvitation(invitationCode.trim())
                        invitationCode = ""
                    }
                },
                enabled = !busy && invitationCode.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.sync_join_profile))
            }
        } else {
            val linkedState = state!!
            Text(stringResource(if (linkedState.enabled) R.string.sync_enabled_label else R.string.sync_paused_label))
            Text(stringResource(R.string.sync_server_url) + ": " + linkedState.serverUrl)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { launchSyncAction(readyMessage) { manager.syncNow() } },
                    enabled = !busy && linkedState.enabled,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.sync_now)) }
                OutlinedButton(
                    onClick = {
                        launchSyncAction(readyMessage) {
                            state = manager.setEnabled(!linkedState.enabled).let { manager.localState() }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(if (linkedState.enabled) R.string.sync_pause else R.string.sync_resume))
                }
            }

            OutlinedButton(
                onClick = {
                    launchSyncAction(pairingReadyMessage) {
                        shownInvitation = manager.createPairingInvitation()
                    }
                },
                enabled = !busy && linkedState.enabled,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.sync_create_pairing)) }

            Text(stringResource(R.string.sync_devices_header))
            if (devicesLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            devicesError?.let { Text(stringResource(R.string.sync_error, it)) }
            val otherDevices = linkedDevices.filterNot { it.current }
            if (!devicesLoading && otherDevices.isEmpty()) {
                Text(stringResource(R.string.sync_no_other_devices))
            }
            linkedDevices.forEach { device ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(if (device.current) R.string.sync_this_device else R.string.sync_other_device))
                        Text(stringResource(R.string.sync_device_id, device.deviceId.take(8)))
                    }
                    if (!device.current) {
                        TextButton(onClick = { deviceToRevoke = device }, enabled = !busy) {
                            Text(stringResource(R.string.sync_revoke_device))
                        }
                    }
                }
            }
            OutlinedButton(
                onClick = {
                    launchSyncAction(devicesRefreshedMessage) {
                        linkedDevices = manager.listDevices()
                        devicesError = null
                    }
                },
                enabled = !busy && !devicesLoading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.sync_refresh_devices)) }

            shownInvitation?.let { code ->
                Text(stringResource(R.string.sync_pairing_warning))
                QrCode(content = code, modifier = Modifier.fillMaxWidth(), size = 220.dp)
                SelectionContainer { Text(code, modifier = Modifier.fillMaxWidth()) }
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(code))
                        message = copiedMessage
                        isError = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.sync_copy_code)) }
                TextButton(onClick = { shownInvitation = null }) {
                    Text(stringResource(R.string.close))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    deviceToRevoke?.let { device ->
        AlertDialog(
            onDismissRequest = { deviceToRevoke = null },
            title = { Text(stringResource(R.string.sync_revoke_title)) },
            text = { Text(stringResource(R.string.sync_revoke_body, device.deviceId.take(8))) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targetId = device.deviceId
                        deviceToRevoke = null
                        launchSyncAction(revokedMessage) {
                            manager.revokeDevice(targetId)
                            linkedDevices = manager.listDevices()
                        }
                    },
                    enabled = !busy,
                ) { Text(stringResource(R.string.sync_revoke_device)) }
            },
            dismissButton = {
                TextButton(onClick = { deviceToRevoke = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(R.string.sync_reset_title)) },
            text = { Text(stringResource(R.string.sync_reset_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirm = false
                        launchSyncAction(resetDoneMessage) {
                            manager.clearLocalSyncData()
                            localStateUnavailable = false
                            state = null
                            shownInvitation = null
                            invitationCode = ""
                        }
                    },
                    enabled = !busy,
                ) { Text(stringResource(R.string.sync_reset_local)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
