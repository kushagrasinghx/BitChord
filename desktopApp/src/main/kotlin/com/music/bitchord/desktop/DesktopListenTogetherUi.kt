package com.music.bitchord.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.data.listentogether.JamInvite
import com.music.bitchord.data.listentogether.PartyActivity
import com.music.bitchord.data.listentogether.PartyMember
import com.music.bitchord.data.listentogether.PartyPreview
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Desktop counterpart of Android's complete Listen Together page. */
@Composable
internal fun DesktopListenTogetherDialog(autoplayEnabled: Boolean, onDismiss: () -> Unit) {
    val state by DesktopListenTogether.state.collectAsState()
    val activity by DesktopListenTogether.activity.collectAsState()
    val server by DesktopListenTogether.customServerUrl.collectAsState()
    val serverStatus by DesktopListenTogether.serverStatus.collectAsState()
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf(DesktopListenTogether.nickname()) }
    var maxMembers by remember { mutableStateOf(5) }
    var serverDraft by remember { mutableStateOf(server) }
    var preview by remember { mutableStateOf<PartyPreview?>(null) }
    var busy by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(server) {
        if (serverDraft == server || serverDraft.isBlank()) serverDraft = server
    }

    DesktopDialogPanel(onDismiss = onDismiss, maxWidth = 920) {
        Column(desktopPanelBody(cardMax = 760.dp, cardMin = 560.dp, fill = true)) {
            Row(
                Modifier.fillMaxWidth().padding(start = panelInset(28.dp), end = panelInset(18.dp), top = 22.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        DesktopStrings["listen_together", "Listen together"],
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (state.inParty) "Everyone hears the same song at the same moment"
                        else "Start a room or join friends with a six-character code",
                        color = DesktopSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                // A page is left by the window's back, as every other page is.
                if (!LocalDesktopPanelIsPage.current) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close") }
                }
            }
            if (!LocalDesktopPanelIsPage.current) HorizontalDivider(color = DesktopCardEdge)

            when {
                state.inParty -> PartyRoom(
                    state = state,
                    activity = activity,
                    busy = busy,
                    onLeave = {
                        busy = true
                        scope.launch {
                            DesktopListenTogether.leaveParty()
                            busy = false
                        }
                    },
                )
                preview != null -> JoinConfirmation(
                    preview = preview!!,
                    nickname = nickname,
                    onNicknameChange = {
                        nickname = it.take(80)
                        DesktopListenTogether.setNickname(nickname)
                    },
                    busy = busy,
                    error = localError ?: state.error,
                    onBack = { preview = null; localError = null },
                    onJoin = {
                        busy = true
                        localError = null
                        scope.launch {
                            DesktopListenTogether.joinParty(preview!!.code, nickname)
                                .onFailure { localError = it.message }
                            busy = false
                        }
                    },
                )
                else -> PartyLanding(
                    signedIn = DesktopListenTogether.canJoin(),
                    enabled = DesktopListenTogether.canJoin() && DesktopListenTogether.hasServer,
                    avatarUrl = DesktopListenTogether.myAvatarUrl(),
                    nickname = nickname,
                    onNicknameChange = {
                        nickname = it.take(80)
                        DesktopListenTogether.setNickname(nickname)
                    },
                    maxMembers = maxMembers,
                    onMaxMembersChange = { maxMembers = it.coerceIn(2, 10) },
                    code = code,
                    onCodeChange = {
                        code = JamInvite.parse(it)
                            ?: it.filter(Char::isLetterOrDigit).uppercase().take(DesktopListenTogether.CODE_LENGTH)
                        localError = null
                    },
                    server = serverDraft,
                    serverStatus = serverStatus,
                    onServerChange = { serverDraft = it },
                    onSaveServer = {
                        localError = null
                        DesktopListenTogether.normalizeServerUrl(serverDraft)
                            .onSuccess { normalized ->
                                DesktopListenTogether.setCustomServerUrl(normalized)
                                    .onSuccess { serverDraft = normalized }
                                    .onFailure { localError = it.message }
                            }
                            .onFailure { localError = it.message }
                    },
                    onRefreshServer = DesktopListenTogether::refreshServerHealth,
                    busy = busy,
                    error = localError ?: state.error,
                    onCreate = {
                        busy = true
                        localError = null
                        scope.launch {
                            DesktopListenTogether.createParty(nickname, maxMembers, autoplayEnabled)
                                .onFailure { localError = it.message }
                            busy = false
                        }
                    },
                    onPreview = {
                        busy = true
                        localError = null
                        scope.launch {
                            DesktopListenTogether.previewParty(code)
                                .onSuccess { preview = it }
                                .onFailure { localError = it.message }
                            busy = false
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun PartyLanding(
    signedIn: Boolean,
    enabled: Boolean,
    avatarUrl: String?,
    nickname: String,
    onNicknameChange: (String) -> Unit,
    maxMembers: Int,
    onMaxMembersChange: (Int) -> Unit,
    code: String,
    onCodeChange: (String) -> Unit,
    server: String,
    serverStatus: DesktopListenTogether.ServerStatus,
    onServerChange: (String) -> Unit,
    onSaveServer: () -> Unit,
    onRefreshServer: () -> Unit,
    busy: Boolean,
    error: String?,
    onCreate: () -> Unit,
    onPreview: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                PartyChoiceCard(
                    title = "Start a party",
                    subtitle = "Choose how friends see you and how many can join.",
                    modifier = Modifier.weight(1f),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PartyAvatar(avatarUrl, 44)
                        Spacer(Modifier.width(12.dp))
                        OutlinedTextField(
                            value = nickname,
                            onValueChange = onNicknameChange,
                            label = { Text("Nickname") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Party size", fontWeight = FontWeight.Medium)
                            Text("2–10 people", color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Stepper("−", enabled && maxMembers > 2) { onMaxMembersChange(maxMembers - 1) }
                        Text("$maxMembers", modifier = Modifier.width(34.dp), textAlign = TextAlign.Center)
                        Stepper("+", enabled && maxMembers < 10) { onMaxMembersChange(maxMembers + 1) }
                    }
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton("Start party", busy, enabled, onCreate)
                }

                PartyChoiceCard(
                    title = "Join a party",
                    subtitle = "Enter the code first. You’ll see who is inside before joining.",
                    modifier = Modifier.weight(1f),
                ) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = onCodeChange,
                        label = { Text("Party code") },
                        placeholder = { Text("ABC123") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 5.sp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(18.dp))
                    PrimaryButton(
                        label = "Look up party",
                        busy = busy,
                        enabled = enabled && code.length == DesktopListenTogether.CODE_LENGTH,
                        onClick = onPreview,
                    )
                }
            }
        }
        if (!signedIn) item { Notice("Sign in to YouTube Music before creating or joining a party.", true) }
        error?.takeIf(String::isNotBlank)?.let { item { Notice(it, true) } }
        item {
            Column(Modifier.desktopCardInset(RoundedCornerShape(14.dp)).padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Settings, null, tint = DesktopSecondary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Party server", fontWeight = FontWeight.Medium)
                        Text("Leave blank to use the server included with this build.", color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(12.dp))
                DesktopSearchField(
                    query = server,
                    onQueryChange = onServerChange,
                    onSearch = {},
                    placeholder = "Custom server address",
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        serverStatusLine(serverStatus),
                        color = when (serverStatus.health) {
                            DesktopListenTogether.Health.ONLINE -> DesktopSecondary
                            DesktopListenTogether.Health.CHECKING -> DesktopSecondary
                            DesktopListenTogether.Health.OFFLINE -> DesktopDestructive
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRefreshServer) { Text("Test") }
                    Button(
                        onClick = onSaveServer,
                        colors = ButtonDefaults.buttonColors(containerColor = DesktopCardInsetFill),
                    ) { Text(if (server.isBlank()) "Use built-in" else "Save") }
                }
            }
        }
    }
}

@Composable
private fun PartyChoiceCard(title: String, subtitle: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.desktopCardInset(RoundedCornerShape(16.dp)).padding(20.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = DesktopSecondary, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(20.dp))
        content()
    }
}

@Composable
private fun JoinConfirmation(
    preview: PartyPreview,
    nickname: String,
    onNicknameChange: (String) -> Unit,
    busy: Boolean,
    error: String?,
    onBack: () -> Unit,
    onJoin: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = panelInset(36.dp), vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.Groups, null, tint = DesktopAccent, modifier = Modifier.size(54.dp))
        Spacer(Modifier.height(14.dp))
        Text(
            if (preview.hostName.isBlank()) "Join this party?" else "Join ${preview.hostName}’s party?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text("${preview.memberCount} of ${preview.maxMembers} people · ${preview.code}", color = DesktopSecondary)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
            preview.members.take(5).forEach { PartyAvatar(it.avatarUrl, 50, it.displayName) }
        }
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = nickname,
            onValueChange = onNicknameChange,
            label = { Text("Nickname") },
            singleLine = true,
            modifier = Modifier.width(360.dp),
        )
        error?.let { Spacer(Modifier.height(10.dp)); Text(it, color = DesktopDestructive) }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = onBack, enabled = !busy) { Text("Back") }
            PrimaryButton("Join party", busy, !preview.isFull, onJoin, Modifier.width(190.dp))
        }
    }
}

@Composable
private fun PartyRoom(state: DesktopListenTogether.State, activity: List<PartyActivity>, busy: Boolean, onLeave: () -> Unit) {
    Row(Modifier.fillMaxSize().padding(horizontal = panelInset(24.dp), vertical = 24.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        LazyColumn(Modifier.weight(1.15f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { RoomHero(state) }
            item { NowPlayingCard(state) }
            item { QueueCard(state) }
        }
        LazyColumn(Modifier.weight(.85f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { MembersCard(state) }
            if (state.you?.isHost == true) item { HostControls(state) }
            if (activity.isNotEmpty()) item { ActivityCard(activity) }
            state.error?.let { item { Notice(it, true) } }
            item {
                TextButton(onClick = onLeave, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Leave party", color = DesktopDestructive)
                }
            }
        }
    }
}

@Composable
private fun RoomHero(state: DesktopListenTogether.State) {
    val invite = DesktopListenTogether.inviteUrl(state.code.orEmpty())
    Row(Modifier.fillMaxWidth().desktopCardInset(RoundedCornerShape(16.dp)).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("PARTY CODE", color = DesktopSecondary, style = MaterialTheme.typography.labelSmall)
            Text(state.code.orEmpty(), fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp)
            Text(connectionLine(state), color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Text("Scan from a phone or copy the invite link", color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
        }
        DesktopQrCode(invite)
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TextButton(onClick = { DesktopExternalLinks.copy(invite) }) {
                Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
                Text("Copy invite")
            }
        }
    }
}

@Composable
private fun NowPlayingCard(state: DesktopListenTogether.State) {
    val track = state.playback.track
    Column(Modifier.fillMaxWidth().desktopCardInset(RoundedCornerShape(16.dp)).padding(18.dp)) {
        Text("NOW PLAYING", color = DesktopSecondary, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(10.dp))
        if (track == null) {
            Text("Nothing playing yet", color = DesktopSecondary)
            if (state.you?.isHost == true) Text("Play a song and it will appear for everyone.", color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DesktopArtwork(track.thumbnailUrl, Modifier.size(62.dp).clip(RoundedCornerShape(8.dp)), px = 160)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, color = DesktopSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    state.playback.startedByName?.let { Text("Started by $it", color = DesktopSecondary, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun QueueCard(state: DesktopListenTogether.State) {
    Column(Modifier.fillMaxWidth().desktopCardInset(RoundedCornerShape(16.dp)).padding(18.dp)) {
        Text("UP NEXT", color = DesktopSecondary, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(8.dp))
        val upcoming = state.queue.items.drop((state.queue.index + 1).coerceAtLeast(0)).take(5)
        if (upcoming.isEmpty()) Text("The shared queue is empty", color = DesktopSecondary)
        upcoming.forEachIndexed { index, track ->
            if (index > 0 && !LocalDesktopPanelIsPage.current) HorizontalDivider(color = DesktopCardEdge)
            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}", color = DesktopSecondary, modifier = Modifier.width(28.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, color = DesktopSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun MembersCard(state: DesktopListenTogether.State) {
    Column(Modifier.fillMaxWidth().desktopCardInset(RoundedCornerShape(16.dp)).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("LISTENING", color = DesktopSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            Text("${state.members.size}/${state.maxMembers}", color = DesktopSecondary, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(8.dp))
        state.members.forEachIndexed { index, member ->
            if (index > 0 && !LocalDesktopPanelIsPage.current) HorizontalDivider(color = DesktopCardEdge)
            MemberRow(member, member.memberId == state.you?.memberId, state.you?.isHost == true && !member.isHost) {
                DesktopListenTogether.kick(member.memberId)
            }
        }
    }
}

@Composable
private fun HostControls(state: DesktopListenTogether.State) {
    Column(Modifier.fillMaxWidth().desktopCardInset(RoundedCornerShape(16.dp)).padding(18.dp)) {
        Text("HOST CONTROLS", color = DesktopSecondary, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (state.hostOnlyControl) Icons.Rounded.Lock else Icons.Rounded.LockOpen, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Only I can control playback")
                Text("Guests can still pause on their own device.", color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Switch(state.hostOnlyControl, onCheckedChange = DesktopListenTogether::setHostOnlyControl)
        }
        if (LocalDesktopPanelIsPage.current) {
            Spacer(Modifier.height(20.dp))
        } else {
            HorizontalDivider(color = DesktopCardEdge, modifier = Modifier.padding(vertical = 10.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Party size")
                Text("Never removes people already inside.", color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Stepper("−", state.maxMembers > maxOf(2, state.members.size)) { DesktopListenTogether.setMaxMembers(state.maxMembers - 1) }
            Text("${state.maxMembers}", modifier = Modifier.width(30.dp), textAlign = TextAlign.Center)
            Stepper("+", state.maxMembers < 10) { DesktopListenTogether.setMaxMembers(state.maxMembers + 1) }
        }
    }
}

@Composable
private fun ActivityCard(entries: List<PartyActivity>) {
    val clock = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    Column(Modifier.fillMaxWidth().desktopCardInset(RoundedCornerShape(16.dp)).padding(18.dp)) {
        Text("ACTIVITY", color = DesktopSecondary, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(8.dp))
        entries.take(8).forEach { entry ->
            Text(
                "${clock.format(Date(entry.atMs))}  ${entry.by}: ${entry.detail.ifBlank { entry.action }}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun MemberRow(member: PartyMember, isYou: Boolean, canKick: Boolean, onKick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        PartyAvatar(member.avatarUrl, 34, member.displayName)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(member.displayName.ifBlank { "Someone" } + if (isYou) " · You" else "", maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when { member.isHost -> "Host"; !member.connected -> "Away"; else -> "Connected" },
                color = DesktopSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (canKick) IconButton(onClick = onKick, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Rounded.RemoveCircleOutline, "Remove", tint = DesktopDestructive, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun PartyAvatar(url: String?, size: Int, fallback: String = "") {
    if (url != null) {
        DesktopArtwork(url, Modifier.size(size.dp).clip(CircleShape), px = size * 3)
    } else {
        Box(
            Modifier.size(size.dp).clip(CircleShape).background(DesktopCardInsetFill).border(0.5.dp, DesktopCardEdge, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (fallback.isNotBlank()) Text(fallback.take(1).uppercase(), fontWeight = FontWeight.Bold)
            else Icon(Icons.Rounded.Person, null, tint = DesktopSecondary, modifier = Modifier.size((size * .55f).dp))
        }
    }
}

@Composable
private fun Stepper(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(DesktopCardInsetFill).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (enabled) Color.White else DesktopSecondary) }
}

@Composable
private fun PrimaryButton(
    label: String,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = DesktopAccent, contentColor = Color.Black),
        modifier = modifier.height(46.dp),
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.Black)
        else Text(label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Notice(text: String, destructive: Boolean) {
    Text(
        text,
        color = if (destructive) DesktopDestructive else DesktopSecondary,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().desktopCardInset(RoundedCornerShape(12.dp)).padding(14.dp),
    )
}

@Composable
private fun connectionLine(state: DesktopListenTogether.State): String = when {
    state.connection == DesktopListenTogether.Connection.CONNECTING -> "Reconnecting…"
    state.connection == DesktopListenTogether.Connection.OFFLINE -> "Offline"
    !state.clockSynced -> "Syncing clocks…"
    else -> "In sync · ${state.roundTripMs} ms"
}

private fun serverStatusLine(status: DesktopListenTogether.ServerStatus): String = when {
    status.health == DesktopListenTogether.Health.CHECKING -> "Checking the party server…"
    status.health == DesktopListenTogether.Health.OFFLINE -> "Party server is offline"
    status.isFallback -> "Custom server is offline · built-in server online · ${status.latencyMs} ms"
    else -> "Party server online · ${status.latencyMs} ms"
}
