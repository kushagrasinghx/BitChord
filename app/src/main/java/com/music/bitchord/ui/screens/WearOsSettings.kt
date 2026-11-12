package com.music.bitchord.ui.screens

import com.music.bitchord.ui.icons.SfIcons
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.ui.components.PillTextField
import com.music.bitchord.wear.WatchAppStatus
import com.music.bitchord.wear.WatchEntry
import com.music.bitchord.wear.WatchInstaller
import com.music.bitchord.wear.WatchPairingState
import com.music.bitchord.wear.WatchUpdateState

/**
 * Settings' way in: one row, with what is true of the watches right now on the
 * right, opening [WearOsScreen].
 */
@Composable
internal fun WearOsSettingsRow(onClick: () -> Unit) {
    val context = LocalContext.current
    val watches by WatchInstaller.watches.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { WatchInstaller.refresh(context) }
    val list = watches.orEmpty()
    SettingsRow(
        icon = SfIcons.Watch,
        title = stringResource(R.string.wear_os_section),
        value = when {
            watches == null -> null
            list.isEmpty() -> stringResource(R.string.wear_os_value_none)
            list.any { it.status == WatchAppStatus.UPDATE_AVAILABLE } -> stringResource(R.string.wear_os_update_available)
            list.any { it.status == WatchAppStatus.NOT_INSTALLED } -> stringResource(R.string.wear_os_value_not_installed)
            list.all { it.status == WatchAppStatus.UP_TO_DATE } -> stringResource(R.string.wear_os_value_up_to_date)
            else -> null
        },
        onClick = onClick,
    )
}

/**
 * Settings → BitChord for Wear OS, a page of its own: every watch paired with
 * this phone, whether it has this build of BitChord, and the one action that
 * makes it so. Install switches the page to the steps for it; Back steps out of
 * them before it leaves the page.
 */
@Composable
fun WearOsScreen(contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val watches by WatchInstaller.watches.collectAsStateWithLifecycle()
    val update by WatchInstaller.update.collectAsStateWithLifecycle()
    var installingNode by rememberSaveable { mutableStateOf<String?>(null) }
    var installingName by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) { WatchInstaller.refresh(context) }
    BackHandler(enabled = installingNode != null) { installingNode = null }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        SettingsPageTitle(
            stringResource(if (installingNode != null) R.string.wear_os_install_title else R.string.wear_os_page_title),
        )

        val node = installingNode
        if (node != null) {
            InstallSteps(
                nodeId = node,
                watchName = installingName,
                onDone = { installingNode = null },
            )
            return@Column
        }

        SettingsGroup(header = stringResource(R.string.wear_os_your_watches)) {
            val list = watches
            when {
                list == null -> SettingsRow(
                    icon = SfIcons.Watch,
                    title = stringResource(R.string.wear_os_looking),
                )
                list.isEmpty() -> SettingsRow(
                    icon = SfIcons.Watch,
                    title = stringResource(R.string.wear_os_no_watch),
                    subtitle = stringResource(R.string.wear_os_no_watch_subtitle),
                )
                else -> list.forEachIndexed { index, watch ->
                    if (index > 0) RowDivider()
                    WatchRow(
                        watch = watch,
                        update = update,
                        onInstall = {
                            installingName = watch.name
                            installingNode = watch.nodeId
                        },
                        onUpdate = { WatchInstaller.sendUpdate(context, watch.nodeId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchRow(
    watch: WatchEntry,
    update: WatchUpdateState,
    onInstall: () -> Unit,
    onUpdate: () -> Unit,
) {
    val mine = when (update) {
        is WatchUpdateState.Sending -> update.nodeId == watch.nodeId
        is WatchUpdateState.Sent -> update.nodeId == watch.nodeId
        is WatchUpdateState.Failed -> update.nodeId == watch.nodeId
        WatchUpdateState.Idle -> false
    }
    val subtitle = when {
        mine && update is WatchUpdateState.Sending -> stringResource(R.string.wear_os_sending)
        mine && update is WatchUpdateState.Sent && watch.status != WatchAppStatus.UP_TO_DATE ->
            stringResource(R.string.wear_os_sent)
        mine && update is WatchUpdateState.Failed -> stringResource(R.string.wear_os_send_failed, update.reason)
        else -> when (watch.status) {
            WatchAppStatus.CHECKING -> stringResource(R.string.wear_os_checking)
            WatchAppStatus.NOT_INSTALLED -> stringResource(R.string.wear_os_not_installed)
            WatchAppStatus.UP_TO_DATE -> stringResource(R.string.wear_os_up_to_date, watch.versionName.orEmpty())
            WatchAppStatus.UPDATE_AVAILABLE -> stringResource(R.string.wear_os_update_available)
            WatchAppStatus.UNKNOWN -> stringResource(R.string.wear_os_unknown)
        }
    }
    SettingsRow(
        icon = SfIcons.Watch,
        title = watch.name,
        subtitle = subtitle,
        trailing = {
            when {
                mine && update is WatchUpdateState.Sending || watch.status == WatchAppStatus.CHECKING ->
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                watch.status == WatchAppStatus.NOT_INSTALLED ->
                    ActionPill(stringResource(R.string.wear_os_install), onInstall)
                watch.status == WatchAppStatus.UPDATE_AVAILABLE || watch.status == WatchAppStatus.UNKNOWN ->
                    ActionPill(stringResource(R.string.wear_os_update), onUpdate)
                else -> Icon(
                    SfIcons.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
    )
}

/** The trailing action, as the App Store draws GET and UPDATE: a small filled capsule. */
@Composable
private fun ActionPill(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/**
 * The first install: what to switch on, on the watch, and the six digits.
 * Everything else — finding the watch on the Wi-Fi, pairing, installing,
 * opening it — is done here, the way `adb pair` and `adb install` would from a
 * computer.
 */
@Composable
private fun InstallSteps(nodeId: String, watchName: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val state by WatchInstaller.pairing.collectAsStateWithLifecycle()
    var code by rememberSaveable { mutableStateOf("") }

    DisposableEffect(Unit) {
        WatchInstaller.startPairing(context)
        onDispose { WatchInstaller.stopPairing() }
    }

    SettingsGroup(header = watchName) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Step(1, stringResource(R.string.wear_os_step_wifi))
            Spacer(Modifier.height(12.dp))
            Step(2, stringResource(R.string.wear_os_step_developer))
            Spacer(Modifier.height(12.dp))
            Step(3, stringResource(R.string.wear_os_step_debugging))
            Spacer(Modifier.height(12.dp))
            Step(4, stringResource(R.string.wear_os_step_pair))
        }
    }

    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp)) {
        StatusLine(state)
        Spacer(Modifier.height(14.dp))
        val busy = state is WatchPairingState.Pairing || state is WatchPairingState.Installing
        val installed = state is WatchPairingState.Installed
        if (!installed) {
            PillTextField(
                value = code,
                onValueChange = { typed -> code = typed.filter(Char::isDigit).take(6) },
                placeholder = stringResource(R.string.wear_os_code_hint),
                enabled = !busy,
                container = MaterialTheme.colorScheme.surfaceVariant,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            )
            Spacer(Modifier.height(16.dp))
        }
        val ready = code.length == 6 && !busy &&
            (state is WatchPairingState.Found || state is WatchPairingState.Failed)
        PrimaryButton(
            text = stringResource(if (installed) R.string.wear_os_done else R.string.wear_os_install),
            enabled = ready || installed,
            onClick = { if (installed) onDone() else WatchInstaller.pairAndInstall(context, nodeId, code) },
        )
    }
}

@Composable
private fun Step(number: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatusLine(state: WatchPairingState) {
    val (text, spinning) = when (state) {
        WatchPairingState.Idle, WatchPairingState.Searching -> stringResource(R.string.wear_os_searching) to true
        is WatchPairingState.Found -> stringResource(R.string.wear_os_found) to false
        WatchPairingState.Pairing -> stringResource(R.string.wear_os_pairing) to true
        WatchPairingState.Installing -> stringResource(R.string.wear_os_installing) to true
        WatchPairingState.Installed -> stringResource(R.string.wear_os_installed) to false
        is WatchPairingState.Failed -> if (state.reason == "unsupported") {
            stringResource(R.string.wear_os_unsupported) to false
        } else {
            stringResource(R.string.wear_os_failed, state.reason) to false
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (spinning) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
        } else if (state is WatchPairingState.Found || state is WatchPairingState.Installed) {
            Icon(
                SfIcons.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The page's one big action: full width, fully rounded. */
@Composable
private fun PrimaryButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(RoundedCornerShape(25.dp))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}
