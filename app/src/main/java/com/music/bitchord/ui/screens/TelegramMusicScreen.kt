package com.music.bitchord.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.telegram.TelegramAuthStatus
import com.music.bitchord.data.telegram.TelegramChat
import com.music.bitchord.data.telegram.TelegramClientManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TelegramMusicScreen(contentPadding: androidx.compose.foundation.layout.PaddingValues) {
    val auth by TelegramClientManager.auth.collectAsStateWithLifecycle()
    val chats by TelegramClientManager.chats.collectAsStateWithLifecycle()
    val indexingStatus by TelegramClientManager.indexingStatus.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var recoverTo by remember { mutableStateOf<TelegramAuthStatus>(TelegramAuthStatus.CredentialsRequired) }
    var apiId by remember { mutableStateOf("") }
    var apiHash by remember { mutableStateOf("") }
    var showApiHash by remember { mutableStateOf(false) }
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var emailCode by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var operation by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var indexedTracks by remember { mutableStateOf(0) }

    LaunchedEffect(auth) {
        if (auth is TelegramAuthStatus.Error) {
            // An initialization error must return to the credential form; retaining
            // Starting here would leave the spinner visible even after timeout.
            if (recoverTo == TelegramAuthStatus.Starting) {
                recoverTo = TelegramAuthStatus.CredentialsRequired
            }
        } else {
            recoverTo = auth
        }
    }
    LaunchedEffect(chats) {
        while (true) {
            indexedTracks = TelegramClientManager.indexDatabase.countTracks(TelegramClientManager.selectedChatIds)
            delay(2_000)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Telegram Music", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Connect BitChord directly to Telegram. Your account session, message index, and playback cache stay on this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (auth is TelegramAuthStatus.Error) {
            Text((auth as TelegramAuthStatus.Error).message, color = MaterialTheme.colorScheme.error)
            if (TelegramClientManager.hasSavedCredentials) {
                OutlinedButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        runCatching { TelegramClientManager.retryInitialization() }
                            .onFailure { notice = it.message ?: "Could not retry Telegram initialization." }
                        busy = false
                    }
                }) { Text("Retry with saved credentials") }
            }
            OutlinedButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    runCatching { TelegramClientManager.logOut() }
                        .onSuccess { notice = "Local Telegram setup cleared. You can reconnect." }
                        .onFailure { notice = it.message }
                    recoverTo = TelegramAuthStatus.CredentialsRequired
                    busy = false
                }
            }) { Text("Clear Telegram session and reconnect") }
        }
        notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        operation?.let { activeOperation ->
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(activeOperation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        when (val step = if (auth is TelegramAuthStatus.Error) recoverTo else auth) {
            TelegramAuthStatus.CredentialsRequired -> {
                Text("Telegram API credentials", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(apiId, { apiId = it.filter(Char::isDigit) }, label = { Text("API ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    apiHash,
                    { apiHash = it.filter(Char::isLetterOrDigit).take(32) },
                    label = { Text("API hash") },
                    visualTransformation = if (showApiHash) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showApiHash = !showApiHash }) {
                            Text(if (showApiHash) "Hide" else "Show")
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(enabled = !busy, onClick = {
                    busy = true
                    notice = null
                    scope.launch {
                        runCatching { TelegramClientManager.connect(apiId, apiHash) }
                            .onFailure { notice = it.message ?: "Could not start Telegram." }
                        busy = false
                    }
                }) { Text(if (busy) "Connecting…" else if (auth is TelegramAuthStatus.Error) "Retry Telegram connection" else "Connect") }
                Text("Get an API ID and hash from my.telegram.org. BitChord never sends these to a BitChord server.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TelegramAuthStatus.Starting -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text("Initializing TDLib and waiting for Telegram authorization…")
                }
            }
            TelegramAuthStatus.PhoneNumberRequired -> {
                Text("Sign in to Telegram", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone number with country code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(enabled = !busy, onClick = {
                    busy = true
                    notice = null
                    scope.launch {
                        runCatching { TelegramClientManager.submitPhoneNumber(phone) }.onFailure { notice = it.message }
                        busy = false
                    }
                }) { Text("Send verification code") }
            }
            TelegramAuthStatus.CodeRequired -> {
                Text("Enter the code Telegram sent to your account.", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(code, { code = it.filter(Char::isDigit) }, label = { Text("Verification code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(enabled = !busy, onClick = {
                    busy = true
                    notice = null
                    scope.launch {
                        runCatching { TelegramClientManager.submitCode(code) }.onFailure { notice = it.message }
                        busy = false
                    }
                }) { Text("Continue") }
            }
            TelegramAuthStatus.EmailAddressRequired -> {
                Text("Telegram needs to verify your email address.", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(email, { email = it }, label = { Text("Email address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        runCatching { TelegramClientManager.submitEmailAddress(email) }.onFailure { notice = it.message }
                        busy = false
                    }
                }) { Text("Continue") }
            }
            TelegramAuthStatus.EmailCodeRequired -> {
                Text("Enter the code Telegram sent to your email.", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(emailCode, { emailCode = it.filter(Char::isDigit) }, label = { Text("Email verification code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        runCatching { TelegramClientManager.submitEmailCode(emailCode) }.onFailure { notice = it.message }
                        busy = false
                    }
                }) { Text("Continue") }
            }
            TelegramAuthStatus.PasswordRequired -> {
                Text("Two-step verification", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(password, { password = it }, label = { Text("Telegram password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(enabled = !busy, onClick = {
                    busy = true
                    notice = null
                    scope.launch {
                        runCatching { TelegramClientManager.submitPassword(password) }.onFailure { notice = it.message }
                        password = ""
                        busy = false
                    }
                }) { Text("Sign in") }
            }
            is TelegramAuthStatus.Ready -> {
                Text("Connected as ${step.accountName}${step.phoneNumber.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}", style = MaterialTheme.typography.titleMedium)
                Text("Choose the groups and channels BitChord should scan for music.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(modifier = Modifier.weight(1f), enabled = !busy, onClick = {
                        busy = true
                        operation = "Refreshing Telegram chats…"
                        notice = null
                        scope.launch {
                            runCatching { TelegramClientManager.refreshChats() }
                                .onSuccess { notice = "Chat list refreshed." }
                                .onFailure { notice = it.message }
                            busy = false
                            operation = null
                        }
                    }) { Text("Refresh chats") }
                    OutlinedButton(modifier = Modifier.weight(1f), enabled = !busy && TelegramClientManager.selectedChatIds.isNotEmpty(), onClick = {
                        busy = true
                        operation = "Reindexing selected sources…"
                        notice = null
                        scope.launch {
                            runCatching { TelegramClientManager.reindexSelected() }
                                .onSuccess { notice = "Indexing queued. It resumes after interruptions." }
                                .onFailure { notice = it.message }
                            busy = false
                            operation = null
                        }
                    }) { Text("Reindex music") }
                }
                Text("$indexedTracks tracks indexed · ${TelegramClientManager.selectedChatIds.size} selected sources")
                indexingStatus?.let { status ->
                    val activelyIndexing = status.startsWith("Indexing ") &&
                        !status.startsWith("Indexing queued") && !status.contains("paused", ignoreCase = true)
                    if (activelyIndexing) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(search, { search = it }, label = { Text("Filter chats") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (chats.isEmpty()) {
                    Text("No groups or channels loaded yet. Refresh the chat list after connecting.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val filteredChats = chats.filter { chat ->
                        chat.displayTitle.contains(search, ignoreCase = true) || chat.typeLabel.contains(search, ignoreCase = true)
                    }
                    if (filteredChats.isEmpty()) {
                        Text("No chats match ‘$search’.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            items(filteredChats, key = { it.id }) { chat ->
                                ChatSelectionRow(chat) { selected ->
                                    scope.launch {
                                        runCatching { TelegramClientManager.setChatSelected(chat.id, selected) }
                                            .onFailure { notice = it.message }
                                    }
                                }
                            }
                        }
                    }
                }
                HorizontalDivider()
                OutlinedButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        runCatching { TelegramClientManager.logOut() }
                            .onSuccess { notice = "Telegram session and local Telegram data cleared." }
                            .onFailure { notice = it.message }
                        busy = false
                    }
                }) { Text("Log out and clear local session") }
            }
            is TelegramAuthStatus.Error -> Unit
        }
    }
}

@Composable
private fun ChatSelectionRow(chat: TelegramChat, onCheckedChange: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(if (chat.selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .toggleable(value = chat.selected, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = chat.selected, onCheckedChange = null)
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(chat.displayTitle, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(chat.typeLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
