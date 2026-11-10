/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

val LocalUnglassColorScheme: ProvidableCompositionLocal<ColorScheme?> =
    compositionLocalOf { null }

@Composable
fun UnglassedDialogTheme(content: @Composable () -> Unit) {
    val unglassed = LocalUnglassColorScheme.current
    MaterialTheme(
        colorScheme = unglassed ?: MaterialTheme.colorScheme,
        content = content,
    )
}

@Composable
private fun DialogIconChip(content: @Composable () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
        modifier = Modifier.size(34.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                content()
            }
        }
    }
}

@Composable
private fun DialogExpressiveEntrance(content: @Composable () -> Unit) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        entered = true
    }
    val progress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
                visibilityThreshold = 0.01f,
            ),
        label = "dialogEntrance",
    )
    Box(
        modifier =
            Modifier.graphicsLayer {
                val p = progress.coerceIn(0f, 1f)
                alpha = p
                scaleX = 0.94f + 0.06f * p
                scaleY = 0.94f + 0.06f * p
                translationY = (1f - p) * 12f
            },
    ) {
        content()
    }
}

@Composable
private fun DialogHeaderRow(
    icon: (@Composable () -> Unit)?,
    title: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (icon != null) {
            CompositionLocalProvider(
                LocalContentColor provides AlertDialogDefaults.iconContentColor,
            ) {
                DialogIconChip(icon)
            }
        }
        ProvideTextStyle(MaterialTheme.typography.headlineSmall) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .align(Alignment.CenterVertically),
            ) {
                title()
            }
        }
    }
}

@Composable
fun DefaultDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    buttons: (@Composable RowScope.() -> Unit)? = null,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    contentScrollable: Boolean = false,
    constrainContentHeight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dialogShowingState = LocalSettingsDialogShowing.current
    DisposableEffect(Unit) {
        dialogShowingState.value = true
        onDispose { dialogShowingState.value = false }
    }

    val menuDialogDismissal = LocalMenuDialogDismissal.current

    Dialog(
        onDismissRequest = {
            menuDialogDismissal?.invoke()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        UnglassedDialogTheme {
            KeepStatusBarHiddenInDialog()

            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .imePadding()
                        .navigationBarsPadding(),
                contentAlignment = Alignment.Center,
            ) {
                DialogExpressiveEntrance {
                Surface(
                    modifier = Modifier.heightIn(max = maxHeight),

                    shape = MaterialTheme.shapes.extraLarge,
                    color = AlertDialogDefaults.containerColor,
                    tonalElevation = AlertDialogDefaults.TonalElevation,
                    shadowElevation = 6.dp,
                ) {
                    Column(
                        modifier = modifier.padding(24.dp),
                    ) {
                        val bodyModifier =
                            when {
                                contentScrollable ->
                                    Modifier
                                        .weight(1f, fill = false)
                                        .verticalScroll(rememberScrollState())
                                constrainContentHeight -> Modifier.weight(1f, fill = false)
                                else -> Modifier
                            }

                        Column(
                            horizontalAlignment = horizontalAlignment,
                            modifier = bodyModifier,
                        ) {
                            if (icon != null || title != null) {
                                CompositionLocalProvider(
                                    LocalContentColor provides AlertDialogDefaults.titleContentColor,
                                ) {
                                    DialogHeaderRow(
                                        icon = icon,
                                        title = title ?: {},
                                    )
                                }

                                Spacer(Modifier.height(16.dp))
                            }

                            content()
                        }

                        if (buttons != null) {
                            Spacer(Modifier.height(24.dp))

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                                modifier = Modifier.fillMaxWidth(),
                            ) flowRowScope@{
                                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                                    ProvideTextStyle(
                                        value = MaterialTheme.typography.labelLarge,
                                    ) {
                                        this@flowRowScope.buttons()
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionPromptDialog(
    title: String? = null,
    titleBar: @Composable (RowScope.() -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onReset: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val dialogShowingState = LocalSettingsDialogShowing.current
    DisposableEffect(Unit) {
        dialogShowingState.value = true
        onDispose { dialogShowingState.value = false }
    }

    val menuDialogDismissal = LocalMenuDialogDismissal.current

    Dialog(
        onDismissRequest = {
            menuDialogDismissal?.invoke()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        UnglassedDialogTheme {
            KeepStatusBarHiddenInDialog()

            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .imePadding()
                        .navigationBarsPadding(),
                contentAlignment = Alignment.Center,
            ) {
                DialogExpressiveEntrance {
                Surface(
                    modifier = Modifier.heightIn(max = maxHeight),

                    shape = MaterialTheme.shapes.extraLarge,
                    color = AlertDialogDefaults.containerColor,
                    tonalElevation = AlertDialogDefaults.TonalElevation,
                    shadowElevation = 6.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (titleBar != null) {
                                Row {
                                    titleBar()
                                }
                            } else if (title != null || icon != null) {
                                CompositionLocalProvider(
                                    LocalContentColor provides AlertDialogDefaults.titleContentColor,
                                ) {
                                    DialogHeaderRow(
                                        icon = icon,
                                        title = {
                                            if (title != null) {
                                                Text(
                                                    text = title,
                                                    overflow = TextOverflow.Ellipsis,
                                                    maxLines = 2,
                                                    style = MaterialTheme.typography.headlineSmall,
                                                )
                                            }
                                        },
                                    )
                                }
                                Spacer(Modifier.height(16.dp))
                            }

                            content()
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (onReset != null) {
                                Row(modifier = Modifier.weight(1f)) {
                                    TextButton(
                                        onClick = { onReset() },
                                        shapes = ButtonDefaults.shapes(),
                                    ) {
                                        Text(stringResource(R.string.reset))
                                    }
                                }
                            }

                            if (onCancel != null) {
                                OutlinedButton(
                                    onClick = { onCancel() },
                                    shape = RoundedCornerShape(18.dp),
                                ) {
                                    Text(stringResource(android.R.string.cancel))
                                }
                            }

                            FilledTonalButton(
                                onClick = { onConfirm() },
                                shape = RoundedCornerShape(18.dp),
                            ) {
                                Text(stringResource(android.R.string.ok))
                            }
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable
fun ListDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val dialogShowingState = LocalSettingsDialogShowing.current
    DisposableEffect(Unit) {
        dialogShowingState.value = true
        onDispose { dialogShowingState.value = false }
    }

    val menuDialogDismissal = LocalMenuDialogDismissal.current

    Dialog(
        onDismissRequest = {
            menuDialogDismissal?.invoke()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        UnglassedDialogTheme {
            KeepStatusBarHiddenInDialog()

            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .imePadding()
                        .navigationBarsPadding(),
                contentAlignment = Alignment.Center,
            ) {
                DialogExpressiveEntrance {
                Surface(
                    modifier = Modifier.heightIn(max = maxHeight),

                    shape = MaterialTheme.shapes.extraLarge,
                    color = AlertDialogDefaults.containerColor,
                    tonalElevation = AlertDialogDefaults.TonalElevation,
                    shadowElevation = 6.dp,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = modifier.padding(vertical = 24.dp),
                    ) {
                        if (icon != null || title != null) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp)
                                        .padding(top = 4.dp),
                            ) {
                                CompositionLocalProvider(
                                    LocalContentColor provides AlertDialogDefaults.titleContentColor,
                                ) {
                                    DialogHeaderRow(
                                        icon = icon,
                                        title = title ?: {},
                                    )
                                }
                            }
                        }
                        LazyColumn(content = content)
                    }
                }
                }
            }
        }
    }
}

@Composable
fun InfoLabel(text: String) =
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 8.dp),
    ) {
        Icon(
            painter = painterResource(id = R.drawable.info),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(4.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }

@Composable
fun TextFieldDialog(
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    initialTextFieldValue: TextFieldValue = TextFieldValue(),
    textFieldValue: String? = null,
    onTextFieldValueChange: ((String) -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    autoFocus: Boolean = true,
    enabled: Boolean = true,
    dismissOnDone: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 10,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    isInputValid: (String) -> Boolean = { it.isNotEmpty() },

    masked: Boolean = false,
    onDone: (String) -> Unit = {},

    textFields: List<Pair<String, TextFieldValue>>? = null,
    onTextFieldsChange: ((Int, TextFieldValue) -> Unit)? = null,
    onDoneMultiple: ((List<String>) -> Unit)? = null,
    onDismiss: () -> Unit,
    extraContent: (@Composable () -> Unit)? = null,
) {
    val legacyFieldState = remember { mutableStateOf(initialTextFieldValue) }
    val currentLegacyValue = textFieldValue ?: legacyFieldState.value.text

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (autoFocus) {
            delay(300)
            focusRequester.requestFocus()
        }
    }

    DefaultDialog(
        onDismiss = onDismiss,
        modifier = modifier,
        icon = icon,
        title = title,
        contentScrollable = true,
        buttons = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(text = stringResource(android.R.string.cancel))
            }

            val isValid =
                textFields?.all { isInputValid(it.second.text) }
                    ?: isInputValid(currentLegacyValue)

            FilledTonalButton(
                enabled = enabled && isValid,
                onClick = {
                    if (textFields != null && onDoneMultiple != null) {
                        onDoneMultiple(textFields.map { it.second.text })
                    } else {
                        onDone(currentLegacyValue)
                    }
                    if (dismissOnDone) {
                        onDismiss()
                    }
                },
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
    ) {
        Column {
            if (textFields != null) {
                textFields.forEachIndexed { index, (label, value) ->
                    TextField(
                        value = value,
                        onValueChange = { onTextFieldsChange?.invoke(index, it) },
                        placeholder = { Text(label) },
                        enabled = enabled,
                        singleLine = singleLine,
                        maxLines = maxLines,
                        colors = OutlinedTextFieldDefaults.colors(),
                        keyboardOptions = keyboardOptions,
                        shape = RoundedCornerShape(14.dp),
                        keyboardActions =
                            KeyboardActions(
                                onDone = {
                                    val areFieldsValid = textFields.all { isInputValid(it.second.text) }
                                    if (enabled && areFieldsValid && onDoneMultiple != null) {
                                        onDoneMultiple(textFields.map { it.second.text })
                                        if (dismissOnDone) {
                                            onDismiss()
                                        }
                                    }
                                },
                            ),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .then(if (index == 0) Modifier.focusRequester(focusRequester) else Modifier),
                    )
                }
            } else {
                TextField(
                    value = currentLegacyValue,
                    onValueChange = { value ->
                        if (onTextFieldValueChange != null) {
                            onTextFieldValueChange(value)
                        } else {
                            legacyFieldState.value = TextFieldValue(value)
                        }
                    },
                    placeholder = placeholder,
                    enabled = enabled,
                    singleLine = singleLine,
                    maxLines = maxLines,
                    visualTransformation =
                        if (masked) PasswordVisualTransformation() else VisualTransformation.None,
                    colors = OutlinedTextFieldDefaults.colors(),
                    keyboardOptions = keyboardOptions,
                    shape = RoundedCornerShape(14.dp),
                    keyboardActions =
                        KeyboardActions(
                            onDone = {
                                if (enabled && isInputValid(currentLegacyValue)) {
                                    onDone(currentLegacyValue)
                                    if (dismissOnDone) {
                                        onDismiss()
                                    }
                                }
                            },
                        ),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                )
            }

            extraContent?.invoke()
        }
    }
}

@Composable
fun EditPlaylistDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (name: String) -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    var nameField by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialName, TextRange(initialName.length)))
    }

    val canSave by remember {
        derivedStateOf { nameField.text.isNotBlank() }
    }

    DefaultDialog(
        onDismiss = onDismiss,
        icon = { Icon(painter = painterResource(R.drawable.solar_pen_linear), contentDescription = null) },
        title = { Text(text = stringResource(R.string.edit_playlist)) },
        contentScrollable = true,
        buttons = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(text = stringResource(android.R.string.cancel))
            }
            FilledTonalButton(
                enabled = canSave,
                onClick = {
                    keyboardController?.hide()
                    onSave(nameField.text.trim())
                    onDismiss()
                },
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(text = stringResource(R.string.save))
            }
        },
    ) {
        TextField(
            value = nameField,
            onValueChange = { nameField = it },
            placeholder = { Text(text = stringResource(R.string.playlist_name)) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            shape = RoundedCornerShape(14.dp),
            keyboardActions =
                KeyboardActions(
                    onDone = {
                        if (!canSave) return@KeyboardActions
                        keyboardController?.hide()
                        onSave(nameField.text.trim())
                        onDismiss()
                    },
                ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
