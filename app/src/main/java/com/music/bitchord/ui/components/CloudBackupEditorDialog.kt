package com.music.bitchord.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.backup.CloudBackupSettings
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.webdav.WebDavConfig
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import kotlinx.coroutines.launch

/**
 * Configures the cloud backup destination.
 *
 * The same frosted card as every other server editor rather than the
 * Material dialog this replaced. One dialog rather than two editors
 * because the provider choice changes which fields exist; keeping both
 * sets in one place means switching from WebDAV to S3 cannot leave a
 * half-saved destination behind.
 *
 * WebDAV has no fields of its own here: the backup writes through the
 * WebDAV source the listener already configured, so the dialog shows
 * that server read-only and only asks for the folder inside it. S3
 * secrets are write-only from the UI's point of view: the stored ones
 * are shown back in their fields only so a test can use them, which is
 * the same amount of exposure the other server editors already accept.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun CloudBackupEditorDialog(
    hazeState: HazeState,
    initial: CloudBackupSettings.Config,
    initialS3SecretKey: String,
    initialS3SessionToken: String,
    onDismiss: () -> Unit,
    onSave: (CloudBackupSettings.Config, String, String) -> Unit,
    onTest: suspend (CloudBackupSettings.Config, String, String) -> Result<Unit>,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val webDavServer by AppSettings.webdavUrl.collectAsStateWithLifecycle()
    val webDavReady = WebDavConfig.isConfigured(webDavServer)

    var provider by remember { mutableStateOf(initial.provider) }
    var webDavFolder by remember { mutableStateOf(initial.webDavFolder) }
    var s3Endpoint by remember { mutableStateOf(initial.s3Endpoint) }
    var s3Region by remember { mutableStateOf(initial.s3Region) }
    var s3Bucket by remember { mutableStateOf(initial.s3Bucket) }
    var s3Prefix by remember { mutableStateOf(initial.s3Prefix) }
    var s3AccessKey by remember { mutableStateOf(initial.s3AccessKey) }
    var s3SecretKey by remember { mutableStateOf(initialS3SecretKey) }
    var s3SessionToken by remember { mutableStateOf(initialS3SessionToken) }
    var s3PathStyle by remember { mutableStateOf(initial.s3PathStyle) }
    var testing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var statusIsGood by remember { mutableStateOf(false) }

    val config = CloudBackupSettings.Config(
        enabled = initial.enabled,
        provider = provider,
        webDavFolder = webDavFolder,
        s3Endpoint = s3Endpoint,
        s3Region = s3Region,
        s3Bucket = s3Bucket,
        s3Prefix = s3Prefix,
        s3AccessKey = s3AccessKey,
        s3PathStyle = s3PathStyle,
    ).normalized()

    val canSave = when (provider) {
        CloudBackupSettings.Provider.WEBDAV -> webDavReady
        CloudBackupSettings.Provider.S3 ->
            s3Endpoint.isNotBlank() && s3Bucket.isNotBlank() &&
                s3AccessKey.isNotBlank() && s3SecretKey.isNotBlank()
    }

    // The server line is a field wearing disabled state: it reads like the
    // other editors' address rows but there is nothing in it to change here.
    val fields: List<EditorField> = when (provider) {
        CloudBackupSettings.Provider.WEBDAV -> listOf(
            EditorField(
                value = if (webDavReady) webDavServer else "",
                onChange = {},
                placeholder = if (webDavReady) {
                    stringResource(R.string.cloud_backup_server_url)
                } else {
                    stringResource(R.string.cloud_backup_not_configured)
                },
                keyboardType = KeyboardType.Uri,
            ),
            EditorField(
                value = webDavFolder,
                onChange = { webDavFolder = it; status = null },
                placeholder = stringResource(R.string.cloud_backup_folder),
            ),
        )
        CloudBackupSettings.Provider.S3 -> listOf(
            EditorField(
                value = s3Endpoint,
                onChange = { s3Endpoint = it; status = null },
                placeholder = stringResource(R.string.cloud_backup_s3_endpoint),
                keyboardType = KeyboardType.Uri,
            ),
            EditorField(
                value = s3Region,
                onChange = { s3Region = it; status = null },
                placeholder = stringResource(R.string.cloud_backup_s3_region),
            ),
            EditorField(
                value = s3Bucket,
                onChange = { s3Bucket = it; status = null },
                placeholder = stringResource(R.string.cloud_backup_s3_bucket),
            ),
            EditorField(
                value = s3Prefix,
                onChange = { s3Prefix = it; status = null },
                placeholder = stringResource(R.string.cloud_backup_s3_prefix),
            ),
            EditorField(
                value = s3AccessKey,
                onChange = { s3AccessKey = it; status = null },
                placeholder = stringResource(R.string.cloud_backup_s3_access_key),
            ),
            EditorField(
                value = s3SecretKey,
                onChange = { s3SecretKey = it; status = null },
                placeholder = stringResource(R.string.cloud_backup_s3_secret_key),
                isPassword = true,
            ),
            EditorField(
                value = s3SessionToken,
                onChange = { s3SessionToken = it; status = null },
                placeholder = stringResource(R.string.cloud_backup_s3_session_token),
                isPassword = true,
            ),
        )
    }

    fun testNow() {
        testing = true
        status = null
        scope.launch {
            onTest(config, s3SecretKey, s3SessionToken).fold(
                onSuccess = {
                    status = context.getString(R.string.connected)
                    statusIsGood = true
                },
                onFailure = {
                    status = context.getString(
                        R.string.cloud_backup_test_failed,
                        it.message ?: context.getString(R.string.unknown_error),
                    )
                    statusIsGood = false
                },
            )
            testing = false
        }
    }

    fun saveNow() = onSave(config, s3SecretKey, s3SessionToken)

    AlertScaffold(hazeState = hazeState, onDismiss = { if (!testing) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 19.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.cloud_backup),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.W600),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = status ?: stringResource(R.string.cloud_backup_description),
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
                color = when {
                    status == null -> MaterialTheme.colorScheme.onSurface
                    statusIsGood -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.error
                },
                textAlign = TextAlign.Center,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                RadioButton(
                    selected = provider == CloudBackupSettings.Provider.WEBDAV,
                    onClick = { provider = CloudBackupSettings.Provider.WEBDAV; status = null },
                    enabled = !testing,
                )
                Text(
                    text = stringResource(R.string.cloud_backup_webdav),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(8.dp))
                RadioButton(
                    selected = provider == CloudBackupSettings.Provider.S3,
                    onClick = { provider = CloudBackupSettings.Provider.S3; status = null },
                    enabled = !testing,
                )
                Text(
                    text = stringResource(R.string.cloud_backup_s3),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(8.dp))
            Column(Modifier.verticalScroll(rememberScrollState())) {
                fields.forEachIndexed { index, field ->
                    if (index > 0) Spacer(Modifier.height(8.dp))
                    PillTextField(
                        value = field.value,
                        onValueChange = field.onChange,
                        placeholder = field.placeholder,
                        // The server row is display-only: enabled only when
                        // there is something in it to change.
                        enabled = !testing && (provider != CloudBackupSettings.Provider.WEBDAV || index > 0),
                        isPassword = field.isPassword,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = field.keyboardType,
                            imeAction = if (index == fields.lastIndex) ImeAction.Done else ImeAction.Next,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { if (canSave && !testing) saveNow() },
                        ),
                    )
                }
                if (provider == CloudBackupSettings.Provider.S3) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = s3PathStyle,
                            onCheckedChange = { s3PathStyle = it },
                            enabled = !testing,
                        )
                        Text(
                            text = stringResource(R.string.cloud_backup_s3_path_style),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
        AlertRule()
        // Above Save rather than beside it: an address is worth checking
        // before it is stored, and a row of three cramped buttons is what
        // the Material dialog did badly.
        AlertAction(
            label = if (testing) stringResource(R.string.testing) else stringResource(R.string.test),
            emphasised = false,
            onClick = ::testNow,
            enabled = canSave && !testing,
        )
        AlertRule()
        AlertAction(
            label = stringResource(R.string.save),
            emphasised = true,
            onClick = ::saveNow,
            enabled = canSave && !testing,
        )
        AlertRule()
        AlertAction(
            label = stringResource(R.string.cancel),
            emphasised = false,
            onClick = onDismiss,
            enabled = !testing,
        )
    }
}
