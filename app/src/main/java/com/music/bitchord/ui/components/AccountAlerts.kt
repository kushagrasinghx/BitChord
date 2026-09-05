package com.music.bitchord.ui.components

import com.music.bitchord.R

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/**
 * Same UIAlertController shape as [UpdateAvailableDialog] — frosted card,
 * hairline rules, full-width stacked actions — but with a text field for the
 * one bit of input this alert needs.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun ListenBrainzTokenAlert(
    hazeState: HazeState,
    tokenInput: String,
    onTokenInputChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertScaffold(hazeState = hazeState, onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 19.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.listenbrainz_token),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.W600),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.listenbrainz_token_description),
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            AlertTextField(
                value = tokenInput,
                onValueChange = onTokenInputChange,
                placeholder = stringResource(R.string.api_token),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave() }),
            )
        }
        AlertRule()
        AlertAction(label = stringResource(R.string.save), emphasised = true, onClick = onSave)
        AlertRule()
        AlertAction(label = stringResource(R.string.cancel), emphasised = false, onClick = onDismiss)
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun LastfmLoginAlert(
    hazeState: HazeState,
    usernameInput: String,
    onUsernameInputChange: (String) -> Unit,
    passwordInput: String,
    onPasswordInputChange: (String) -> Unit,
    error: String?,
    loading: Boolean,
    onSignIn: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertScaffold(hazeState = hazeState, onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 19.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.lastfm_login),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.W600),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = error ?: stringResource(R.string.lastfm_login_description),
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
                color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            AlertTextField(
                value = usernameInput,
                onValueChange = onUsernameInputChange,
                placeholder = stringResource(R.string.username),
                enabled = !loading,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            Spacer(Modifier.height(8.dp))
            AlertTextField(
                value = passwordInput,
                onValueChange = onPasswordInputChange,
                placeholder = stringResource(R.string.password),
                enabled = !loading,
                isPassword = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { if (usernameInput.isNotBlank() && passwordInput.isNotBlank()) onSignIn() },
                ),
            )
        }
        AlertRule()
        AlertAction(
            label = if (loading) stringResource(R.string.signing_in) else stringResource(R.string.sign_in),
            emphasised = true,
            onClick = onSignIn,
            enabled = !loading && usernameInput.isNotBlank() && passwordInput.isNotBlank(),
        )
        AlertRule()
        AlertAction(
            label = stringResource(R.string.cancel),
            emphasised = false,
            onClick = onDismiss,
            enabled = !loading,
        )
    }
}

/**
 * Manual token entry, for when the in-app login can't run — a WebView an OEM
 * has broken, or a token lifted from a desktop client.
 *
 * [error] carries back what the verification attempt said, because a token that
 * was mistyped or has expired is indistinguishable from one that works until
 * Discord is asked about it.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun DiscordTokenAlert(
    hazeState: HazeState,
    tokenInput: String,
    onTokenInputChange: (String) -> Unit,
    error: String?,
    loading: Boolean,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertScaffold(hazeState = hazeState, onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 19.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.discord_token),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.W600),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = error ?: stringResource(R.string.discord_token_description),
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
                color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            AlertTextField(
                value = tokenInput,
                onValueChange = onTokenInputChange,
                placeholder = stringResource(R.string.token),
                enabled = !loading,
                isPassword = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (tokenInput.isNotBlank()) onSave() }),
            )
        }
        AlertRule()
        AlertAction(
            label = if (loading) stringResource(R.string.checking) else stringResource(R.string.save),
            emphasised = true,
            onClick = onSave,
            enabled = !loading && tokenInput.isNotBlank(),
        )
        AlertRule()
        AlertAction(
            label = stringResource(R.string.cancel),
            emphasised = false,
            onClick = onDismiss,
            enabled = !loading,
        )
    }
}

/**
 * One free-text presence field — an activity name, a button label.
 *
 * [message] is where the caller explains the field, including which `{...}`
 * variables it accepts, since that is the only place a user would find out.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun TextValueAlert(
    hazeState: HazeState,
    title: String,
    message: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    /** False greys Save out — for a field that isn't worth saving empty. */
    saveEnabled: Boolean = true,
    /** A third action between Save and Cancel, for a value that can be cleared. */
    onRemove: (() -> Unit)? = null,
) {
    AlertScaffold(hazeState = hazeState, onDismiss = onDismiss) {
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
                text = message,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            AlertTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = placeholder,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave() }),
            )
        }
        AlertRule()
        AlertAction(
            label = stringResource(R.string.save),
            emphasised = true,
            onClick = onSave,
            enabled = saveEnabled,
        )
        if (onRemove != null) {
            AlertRule()
            AlertAction(label = stringResource(R.string.remove), emphasised = false, onClick = onRemove)
        }
        AlertRule()
        AlertAction(label = stringResource(R.string.cancel), emphasised = false, onClick = onDismiss)
    }
}

/**
 * Add or edit a source that has an address — the addon editor.
 *
 * The same frosted card every other alert in this app uses, rather than the
 * Material `AlertDialog` this replaced. That one put a filled `OutlinedTextField`
 * and a row of cramped text buttons in the middle of a screen where nothing
 * else looks like that, and it read as a stock widget dropped into somebody
 * else's design.
 *
 * One field and up to four stacked actions. There is deliberately no name
 * field: an addon states its own name in its manifest, so asking the user to
 * invent one is asking for information the addon is about to supply anyway —
 * and a blank field would leave the row showing a bare hostname next to a
 * perfectly good published name.
 *
 * [status] is the one thing here that no other alert in the file needs:
 * testing an address has *three* outcomes rather than the usual two, and "it
 * answered, but not with something this app can use" is the one worth reading
 * — so a result replaces the description in place, coloured by [statusIsGood],
 * the way [LastfmLoginAlert] surfaces a failed sign-in.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun AddonEditorAlert(
    hazeState: HazeState,
    title: String,
    description: String,
    urlValue: String,
    onUrlChange: (String) -> Unit,
    urlPlaceholder: String,
    /** What the last test said, or null before one has been run. */
    status: String?,
    statusIsGood: Boolean,
    testing: Boolean,
    /** Whether there is enough typed in to be worth testing or saving. */
    canSubmit: Boolean,
    onTest: () -> Unit,
    onSave: () -> Unit,
    /** Offered only for a source already stored — there is nothing to remove otherwise. */
    onRemove: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    AlertScaffold(hazeState = hazeState, onDismiss = { if (!testing) onDismiss() }) {
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
                text = status ?: description,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
                color = when {
                    status == null -> MaterialTheme.colorScheme.onSurface
                    statusIsGood -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.error
                },
                textAlign = TextAlign.Center,
            )
            AlertTextField(
                value = urlValue,
                onValueChange = onUrlChange,
                placeholder = urlPlaceholder,
                enabled = !testing,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { if (canSubmit && !testing) onSave() }),
            )
        }
        AlertRule()
        // Above Save rather than beside it: an address is worth checking before
        // it is stored, and a row of three cramped buttons is what the Material
        // dialog did badly.
        AlertAction(
            label = if (testing) stringResource(R.string.testing) else stringResource(R.string.test),
            emphasised = false,
            onClick = onTest,
            enabled = canSubmit && !testing,
        )
        AlertRule()
        AlertAction(
            label = stringResource(R.string.save),
            emphasised = true,
            onClick = onSave,
            enabled = canSubmit && !testing,
        )
        if (onRemove != null) {
            AlertRule()
            AlertAction(
                label = stringResource(R.string.remove_source),
                emphasised = false,
                destructive = true,
                onClick = onRemove,
                enabled = !testing,
            )
        }
        AlertRule()
        AlertAction(
            label = stringResource(R.string.cancel),
            emphasised = false,
            onClick = onDismiss,
            enabled = !testing,
        )
    }
}

/**
 * Single-select list, ticked like [LyricsSourcesDialog] rather than with radio
 * buttons — same reasoning: a column of Material radios would be the one
 * Material thing left on an otherwise Apple-shaped alert.
 *
 * Picking commits immediately and closes, so there is no Save action to reach
 * for; Cancel is the only one, and it's the dismiss.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun <T> ChoiceAlert(
    hazeState: HazeState,
    title: String,
    message: String?,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    detail: (T) -> String? = { null },
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertScaffold(hazeState = hazeState, onDismiss = onDismiss) {
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
            if (message != null) {
                Text(
                    text = message,
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
        }
        options.forEach { option ->
            AlertRule()
            ChoiceRow(
                label = label(option),
                detail = detail(option),
                checked = option == selected,
                onClick = { onSelect(option) },
            )
        }
        AlertRule()
        AlertAction(label = stringResource(R.string.cancel), emphasised = false, onClick = onDismiss)
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    detail: String?,
    checked: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ACTION_HEIGHT)
            // iOS washes the whole row instead of drawing a ripple inside it.
            .background(
                if (pressed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f) else Color.Transparent,
            )
            .clickable(
                indication = null,
                interactionSource = interactionSource,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 15.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        if (checked) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = stringResource(R.string.selected),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

/** The scrim + frosted card frame shared by every UIAlertController-style dialog. */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
private fun AlertScaffold(
    hazeState: HazeState,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val shape = RoundedCornerShape(ALERT_CORNER)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SCRIM_COLOR)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(ALERT_WIDTH)
                .clip(shape)
                .then(
                    if (reduceDynamicBlur) {
                        Modifier.background(MaterialTheme.colorScheme.surface)
                    } else {
                        Modifier.optimizedHazeEffect(
                            state = hazeState,
                            style = HazeMaterials.regular(MaterialTheme.colorScheme.surface),
                        )
                    },
                )
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                ),
            content = content,
        )
    }
}

/** The narrow, pill-shaped field iOS alerts and this app's search bar both use. */
@Composable
private fun AlertTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPassword: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(11.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onBackground,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
