package com.music.bitchord.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import com.music.bitchord.data.proxy.ProxyConfig
import com.music.bitchord.data.proxy.ProxyMode
import com.music.bitchord.data.proxy.ProxyProtocol
import com.music.bitchord.data.proxy.ProxyTester
import com.music.bitchord.data.proxy.ProxyValidationError
import com.music.bitchord.data.proxy.SourceProxy
import com.music.bitchord.data.proxy.isValidProxyHost
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.sources.SourceConfig
import com.music.bitchord.data.sources.SourceKind
import com.music.bitchord.data.sources.SourceRegistry
import com.music.bitchord.data.sources.addon.AddonClient
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import kotlinx.coroutines.launch

/**
 * The app-wide proxy editor, opened from Settings > Network.
 *
 * It owns a draft until Save, so an abandoned edit never changes routing. The
 * password is part of that draft but is only written to [AppSettings], which
 * puts it in the encrypted credential store.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun GlobalProxyEditorAlert(
    hazeState: HazeState,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved = AppSettings.proxyConfig
    var draft by remember { mutableStateOf(GlobalProxyDraft.from(saved)) }
    var testing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<ProxyStatus?>(null) }

    val validation = draft.validationError()
    val canSave = !testing && validation == null
    val canTest = canSave && draft.enabled

    AlertScaffold(hazeState = hazeState, onDismiss = { if (!testing) onDismiss() }) {
        ProxyAlertBody(
            title = stringResource(R.string.proxy),
            description = stringResource(R.string.proxy_subtitle),
            status = status,
        ) {
            ProxyToggleRow(
                checked = draft.enabled,
                onCheckedChange = { draft = draft.copy(enabled = it); status = null },
            )
            Spacer(Modifier.height(12.dp))
            ProxyEndpointFields(
                protocol = draft.protocol,
                onProtocol = { draft = draft.copy(protocol = it); status = null },
                host = draft.host,
                onHost = { draft = draft.copy(host = it); status = null },
                port = draft.port,
                onPort = { draft = draft.copy(port = it.filter(Char::isDigit)); status = null },
                username = draft.username,
                onUsername = { draft = draft.copy(username = it); status = null },
                password = draft.password,
                onPassword = { draft = draft.copy(password = it); status = null },
                enabled = !testing,
            )
            if (draft.protocol == ProxyProtocol.HTTPS) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.proxy_https_note),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            validation?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = validationText(it),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        AlertRule()
        AlertAction(
            label = if (testing) stringResource(R.string.proxy_testing) else stringResource(R.string.proxy_test),
            emphasised = false,
            enabled = canTest,
            onClick = {
                testing = true
                status = null
                scope.launch {
                    val result = ProxyTester.test(draft.toConfig())
                    testing = false
                    status = result.toStatus(context)
                }
            },
        )
        AlertRule()
        AlertAction(
            label = stringResource(R.string.proxy_save),
            emphasised = true,
            enabled = canSave,
            onClick = {
                AppSettings.setProxyConfig(draft.toConfig())
                onDismiss()
            },
        )
        AlertRule()
        AlertAction(
            label = stringResource(R.string.proxy_reset),
            emphasised = false,
            enabled = !testing,
            onClick = {
                draft = GlobalProxyDraft()
                status = null
            },
        )
        AlertRule()
        AlertAction(
            label = stringResource(R.string.cancel),
            emphasised = false,
            enabled = !testing,
            onClick = onDismiss,
        )
    }
}

/**
 * One addon's proxy editor, opened from its row on Sources.
 *
 * INHERIT and DIRECT are routing choices, not missing endpoints, so they are
 * offered as first-class modes rather than by leaving the fields blank.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun SourceProxyEditorAlert(
    hazeState: HazeState,
    config: SourceConfig,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by remember(config.id) { mutableStateOf(SourceProxyDraft.from(config.proxy)) }
    var testing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<ProxyStatus?>(null) }

    val validation = draft.validationError()
    val canSave = !testing && validation == null
    val canTest = !testing && validation == null

    AlertScaffold(hazeState = hazeState, onDismiss = { if (!testing) onDismiss() }) {
        ProxyAlertBody(
            title = stringResource(R.string.source_proxy),
            description = config.displayName,
            status = status,
        ) {
            Text(
                text = stringResource(R.string.proxy_mode),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.W600),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            PillSegmentedControl(
                options = listOf(
                    modeLabel(ProxyMode.INHERIT),
                    modeLabel(ProxyMode.DIRECT),
                    modeLabel(ProxyMode.CUSTOM),
                ),
                selectedIndex = draft.mode.ordinal,
                onSelect = {
                    draft = draft.copy(mode = ProxyMode.entries[it])
                    status = null
                },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = modeDetail(draft.mode),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (draft.mode == ProxyMode.CUSTOM) {
                Spacer(Modifier.height(12.dp))
                ProxyEndpointFields(
                    protocol = draft.protocol,
                    onProtocol = { draft = draft.copy(protocol = it); status = null },
                    host = draft.host,
                    onHost = { draft = draft.copy(host = it); status = null },
                    port = draft.port,
                    onPort = { draft = draft.copy(port = it.filter(Char::isDigit)); status = null },
                    username = draft.username,
                    onUsername = { draft = draft.copy(username = it); status = null },
                    password = draft.password,
                    onPassword = { draft = draft.copy(password = it); status = null },
                    enabled = !testing,
                )
                if (draft.protocol == ProxyProtocol.HTTPS) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.proxy_https_note),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                validation?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = validationText(it),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        AlertRule()
        AlertAction(
            label = if (testing) stringResource(R.string.proxy_testing) else stringResource(R.string.source_proxy_test),
            emphasised = false,
            enabled = canTest,
            onClick = {
                testing = true
                status = null
                val target = testTarget(config)
                scope.launch {
                    val result = when (draft.mode) {
                        ProxyMode.INHERIT ->
                            ProxyTester.test(AppSettings.proxyConfig.takeIf { it.enabled }, target)
                        ProxyMode.DIRECT -> ProxyTester.test(null, target)
                        ProxyMode.CUSTOM -> ProxyTester.test(draft.toSourceProxy().customConfig(), target)
                    }
                    testing = false
                    status = result.toStatus(context)
                }
            },
        )
        AlertRule()
        AlertAction(
            label = stringResource(R.string.proxy_save),
            emphasised = true,
            enabled = canSave,
            onClick = {
                // Re-read the row: the source may have learned a manifest or
                // changed a policy while this editor was open, and saving a
                // stale copy would undo that.
                SourceRegistry.config(config.id)?.let { latest ->
                    SourceRegistry.update(latest.copy(proxy = draft.toSourceProxy()))
                }
                onSaved()
            },
        )
        AlertRule()
        AlertAction(
            label = stringResource(R.string.proxy_reset),
            emphasised = false,
            enabled = !testing,
            onClick = {
                draft = SourceProxyDraft()
                status = null
            },
        )
        AlertRule()
        AlertAction(
            label = stringResource(R.string.cancel),
            emphasised = false,
            enabled = !testing,
            onClick = onDismiss,
        )
    }
}

private data class ProxyStatus(val message: String, val good: Boolean)

private data class GlobalProxyDraft(
    val enabled: Boolean = false,
    val protocol: ProxyProtocol = ProxyProtocol.HTTP,
    val host: String = "",
    val port: String = "",
    val username: String = "",
    val password: String = "",
) {
    fun toConfig(): ProxyConfig = ProxyConfig(
        enabled = enabled,
        protocol = protocol,
        host = host.trim(),
        port = port.toIntOrNull() ?: 0,
        username = username.trim(),
        password = password,
    )

    fun validationError(): ProxyValidationError? {
        if (!enabled) return null
        if (host.isBlank()) return ProxyValidationError.HOST_REQUIRED
        if (!isValidProxyHost(host)) return ProxyValidationError.HOST_INVALID
        if (port.isBlank()) return ProxyValidationError.PORT_REQUIRED
        val number = port.toIntOrNull() ?: return ProxyValidationError.PORT_INVALID
        if (number !in 1..65535) return ProxyValidationError.PORT_INVALID
        return null
    }

    companion object {
        fun from(config: ProxyConfig) = GlobalProxyDraft(
            enabled = config.enabled,
            protocol = config.protocol,
            host = config.host,
            port = config.port.takeIf { it != 0 }?.toString().orEmpty(),
            username = config.username,
            password = config.password,
        )
    }
}

private data class SourceProxyDraft(
    val mode: ProxyMode = ProxyMode.INHERIT,
    val protocol: ProxyProtocol = ProxyProtocol.HTTP,
    val host: String = "",
    val port: String = "",
    val username: String = "",
    val password: String = "",
) {
    fun toSourceProxy(): SourceProxy = SourceProxy(
        mode = mode,
        protocol = protocol,
        host = host.trim(),
        port = port.toIntOrNull() ?: 0,
        username = username.trim(),
        password = password,
    )

    fun validationError(): ProxyValidationError? {
        if (mode != ProxyMode.CUSTOM) return null
        if (host.isBlank()) return ProxyValidationError.HOST_REQUIRED
        if (!isValidProxyHost(host)) return ProxyValidationError.HOST_INVALID
        if (port.isBlank()) return ProxyValidationError.PORT_REQUIRED
        val number = port.toIntOrNull() ?: return ProxyValidationError.PORT_INVALID
        if (number !in 1..65535) return ProxyValidationError.PORT_INVALID
        return null
    }

    companion object {
        fun from(proxy: SourceProxy) = SourceProxyDraft(
            mode = proxy.mode,
            protocol = proxy.protocol,
            host = proxy.host,
            port = proxy.port.takeIf { it != 0 }?.toString().orEmpty(),
            username = proxy.username,
            password = proxy.password,
        )
    }
}

@Composable
private fun ProxyAlertBody(
    title: String,
    description: String,
    status: ProxyStatus?,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 19.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.W600),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = status?.message ?: description,
            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
            color = when {
                status == null -> MaterialTheme.colorScheme.onSurface
                status.good -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.error
            },
            textAlign = TextAlign.Center,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 380.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            content()
        }
    }
}

@Composable
private fun ProxyToggleRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.proxy_enable),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.proxy_enable_subtitle),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        IosSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ProxyEndpointFields(
    protocol: ProxyProtocol,
    onProtocol: (ProxyProtocol) -> Unit,
    host: String,
    onHost: (String) -> Unit,
    port: String,
    onPort: (String) -> Unit,
    username: String,
    onUsername: (String) -> Unit,
    password: String,
    onPassword: (String) -> Unit,
    enabled: Boolean,
) {
    Text(
        text = stringResource(R.string.proxy_protocol),
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.W600),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp),
    )
    PillSegmentedControl(
        options = listOf(
            protocolLabel(ProxyProtocol.HTTP),
            protocolLabel(ProxyProtocol.HTTPS),
            protocolLabel(ProxyProtocol.SOCKS5),
        ),
        selectedIndex = protocol.ordinal,
        onSelect = { onProtocol(ProxyProtocol.entries[it]) },
    )
    Spacer(Modifier.height(10.dp))
    ProxyField(
        label = stringResource(R.string.proxy_host),
        value = host,
        placeholder = stringResource(R.string.proxy_host_hint),
        enabled = enabled,
        onChange = onHost,
    )
    Spacer(Modifier.height(8.dp))
    ProxyField(
        label = stringResource(R.string.proxy_port),
        value = port,
        placeholder = stringResource(R.string.proxy_port_hint),
        keyboardType = KeyboardType.Number,
        enabled = enabled,
        onChange = onPort,
    )
    Spacer(Modifier.height(8.dp))
    ProxyField(
        label = stringResource(R.string.proxy_username),
        value = username,
        placeholder = stringResource(R.string.proxy_username),
        enabled = enabled,
        onChange = onUsername,
    )
    Spacer(Modifier.height(8.dp))
    ProxyField(
        label = stringResource(R.string.proxy_password),
        value = password,
        placeholder = stringResource(R.string.proxy_password),
        isPassword = true,
        enabled = enabled,
        onChange = onPassword,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        text = stringResource(R.string.proxy_credentials_hint),
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ProxyField(
    label: String,
    value: String,
    placeholder: String,
    enabled: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    onChange: (String) -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.W600),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp),
    )
    PillTextField(
        value = value,
        onValueChange = onChange,
        placeholder = placeholder,
        enabled = enabled,
        isPassword = isPassword,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = ImeAction.Next,
        ),
        keyboardActions = KeyboardActions.Default,
    )
}

@Composable
private fun modeLabel(mode: ProxyMode): String = when (mode) {
    ProxyMode.INHERIT -> stringResource(R.string.source_proxy_inherit)
    ProxyMode.DIRECT -> stringResource(R.string.source_proxy_direct)
    ProxyMode.CUSTOM -> stringResource(R.string.source_proxy_custom)
}

@Composable
private fun modeDetail(mode: ProxyMode): String = when (mode) {
    ProxyMode.INHERIT -> stringResource(R.string.source_proxy_inherit_detail)
    ProxyMode.DIRECT -> stringResource(R.string.source_proxy_direct_detail)
    ProxyMode.CUSTOM -> stringResource(R.string.source_proxy_custom_detail)
}

@Composable
private fun protocolLabel(protocol: ProxyProtocol): String = when (protocol) {
    ProxyProtocol.HTTP -> stringResource(R.string.proxy_protocol_http)
    ProxyProtocol.HTTPS -> stringResource(R.string.proxy_protocol_https)
    ProxyProtocol.SOCKS5 -> stringResource(R.string.proxy_protocol_socks5)
}

@Composable
private fun validationText(error: ProxyValidationError): String = when (error) {
    ProxyValidationError.HOST_REQUIRED -> stringResource(R.string.proxy_host_required)
    ProxyValidationError.HOST_INVALID -> stringResource(R.string.proxy_invalid_host)
    ProxyValidationError.PORT_REQUIRED -> stringResource(R.string.proxy_port_required)
    ProxyValidationError.PORT_INVALID -> stringResource(R.string.proxy_invalid_port)
}

private fun Result<Unit>.toStatus(context: android.content.Context): ProxyStatus =
    fold(
        onSuccess = { ProxyStatus(context.getString(R.string.proxy_test_ok), good = true) },
        onFailure = { error ->
            val message = error.message.orEmpty()
            val text = if (message.contains("authentication", ignoreCase = true) ||
                message.contains("407")
            ) {
                context.getString(R.string.proxy_auth_failed)
            } else {
                context.getString(
                    R.string.proxy_test_failed,
                    message.ifBlank { context.getString(R.string.proxy_unavailable) },
                )
            }
            ProxyStatus(text, good = false)
        },
    )

private fun testTarget(config: SourceConfig): String {
    if (config.baseUrl.isBlank()) return ProxyTester.DEFAULT_TEST_URL
    return if (config.kind == SourceKind.ADDON) {
        AddonClient.manifestUrl(AddonClient.normalizeBase(config.baseUrl))
    } else {
        config.baseUrl
    }
}
