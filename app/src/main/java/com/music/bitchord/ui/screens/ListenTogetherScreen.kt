package com.music.bitchord.ui.screens

import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.listentogether.JamInviteLink
import com.music.bitchord.data.listentogether.ListenTogether
import com.music.bitchord.data.listentogether.PartyMember
import com.music.bitchord.data.listentogether.PartyActivity
import com.music.bitchord.ui.components.PillTextField
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Listen together: one party, one code, up to five signed-in devices.
 *
 * A deliberately plain screen — the designed one comes later. What it is for
 * right now is the two things the feature cannot be built without: proving that
 * a code created on one phone admits another, and that both then agree, to the
 * millisecond, on where the music is. The party position under **Now playing**
 * is that second proof, and it is why this screen ticks: two devices side by
 * side should show the same number.
 *
 * There is no player binding yet. Controls sent from here move the party's
 * state and every other device sees them; nothing starts playing. That join is
 * the next piece of work and is the reason
 * [ListenTogether.partyPositionMs][com.music.bitchord.data.listentogether.ListenTogether.partyPositionMs]
 * exists.
 */
@Composable
fun ListenTogetherScreen(
    signedIn: Boolean,
    inviteCode: String? = null,
    onInviteJoined: () -> Unit = {},
    onSignIn: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val state by ListenTogether.state.collectAsStateWithLifecycle()
    val customServer by ListenTogether.customServerUrl.collectAsStateWithLifecycle()
    val activity by ListenTogether.activity.collectAsStateWithLifecycle()

    var serverInput by remember(customServer) { mutableStateOf(customServer) }
    var codeInput by remember(inviteCode) { mutableStateOf(inviteCode.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var nickname by remember { mutableStateOf(ListenTogether.nickname()) }
    var maxMembers by remember { mutableIntStateOf(5) }

    // A membership outlives the process; the socket does not. Opening it when
    // the screen is looked at — rather than on every cold start — is what keeps
    // a feature nobody is currently using off the radio.
    LaunchedEffect(Unit) { ListenTogether.ensureConnected() }
    // This page owns the health polling: the latency badge belongs to Listen
    // together, so it refreshes while this page is visible and stops when the
    // composable leaves the screen.
    LaunchedEffect(customServer) {
        while (true) {
            ListenTogether.refreshServerHealth()
            delay(10_000)
        }
    }

    // A link tap is already an explicit request to join. Signed-out users keep
    // the populated code while the sign-in page is open. joinParty switches an
    // existing membership without dropping it first if the invite is invalid.
    LaunchedEffect(inviteCode, signedIn) {
        val code = inviteCode ?: return@LaunchedEffect
        if (!signedIn) return@LaunchedEffect
        if (state.code.equals(code, ignoreCase = true)) {
            onInviteJoined()
            return@LaunchedEffect
        }

        busy = true
        failure = null
        val result = ListenTogether.joinParty(code)
        failure = result.exceptionOrNull()?.message
        if (result.isSuccess) {
            codeInput = ""
            onInviteJoined()
        }
        busy = false
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        Text(
            text = stringResource(R.string.listen_together),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp),
        )

        if (!signedIn) {
            SettingsGroup(footer = stringResource(R.string.listen_together_sign_in_footer)) {
                SettingsRow(
                    icon = Icons.Rounded.Login,
                    title = stringResource(R.string.sign_in),
                    subtitle = stringResource(R.string.not_signed_in),
                    onClick = onSignIn,
                )
            }
        }

        if (!state.inParty) {
            NotInAParty(
                signedIn = signedIn,
                hasServer = ListenTogether.hasServer,
                codeInput = codeInput,
                onCodeInput = { typed ->
                    codeInput = typed.filter(Char::isLetterOrDigit)
                        .uppercase()
                        .take(ListenTogether.CODE_LENGTH)
                },
                busy = busy,
                nickname = nickname,
                onNicknameChange = { nickname = it.take(80); ListenTogether.setNickname(nickname) },
                maxMembers = maxMembers,
                onMaxMembersChange = { maxMembers = it.coerceIn(2, 10) },
                onCreate = {
                    busy = true
                    failure = null
                    scope.launch {
                        failure = ListenTogether.createParty(nickname, maxMembers).exceptionOrNull()?.message
                        busy = false
                    }
                },
                onJoin = {
                    busy = true
                    failure = null
                    scope.launch {
                        failure = ListenTogether.joinParty(codeInput, nickname).exceptionOrNull()?.message
                        if (failure == null) codeInput = ""
                        busy = false
                    }
                },
            )
        } else {
            InAParty(
                state = state,
                onCopy = { clipboard.setText(AnnotatedString(state.code.orEmpty())) },
                onShare = {
                    val code = state.code ?: return@InAParty
                    val link = JamInviteLink.url(code)
                    val message = "$link\n\n${context.getString(R.string.listen_together_share_text, code)}"
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, message)
                            },
                            null,
                        ),
                    )
                },
                onLeave = { scope.launch { ListenTogether.leaveParty() } },
                onSetCapacity = ListenTogether::setMaxMembers,
                onKick = ListenTogether::kick,
            )
        }

        (failure ?: state.error)?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = GROUP_INSET + 4.dp, end = GROUP_INSET + 4.dp, top = 12.dp),
            )
        }

        PartyActivityList(activity)

        // Last, and empty by default. Nobody setting up a party needs to think
        // about an address — there is one built in — so this is where somebody
        // running their own server comes looking, rather than the first thing
        // everybody else has to read past.
        SettingsGroup(
            header = stringResource(R.string.listen_together_custom_server),
            footer = stringResource(R.string.listen_together_custom_server_footer),
        ) {
            Column(Modifier.padding(horizontal = ROW_INSET, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Dns,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(ICON_SIZE),
                    )
                    Spacer(Modifier.width(ICON_GAP))
                    PillTextField(
                        value = serverInput,
                        onValueChange = { serverInput = it },
                        // Never the built-in address, even as a hint: this box
                        // exists to take somebody else's server, and the one
                        // this build uses is not shown anywhere.
                        placeholder = stringResource(R.string.listen_together_using_builtin),
                        // Not the field's own default: the card it is sitting in
                        // is surfaceVariant too, so the default would paint the
                        // box in exactly the colour behind it.
                        container = MaterialTheme.colorScheme.background,
                        // Locked while in a party: changing the address under a
                        // live membership would leave this device holding a
                        // token for a server it no longer talks to, and the
                        // party unable to say why it went quiet.
                        enabled = !state.inParty,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { ListenTogether.setCustomServerUrl(serverInput) },
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (serverInput.trim().trimEnd('/') != customServer) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { ListenTogether.setCustomServerUrl(serverInput) }) {
                            Text(stringResource(R.string.save))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

/**
 * Whether the party server is answering, before anything else on the screen.
 *
 * Every other failure in this feature looks the same from the outside — a code
 * that will not create, a join that sits there — and most of the time the
 * answer is simply that the server is asleep or unreachable. Saying so up front
 * is the difference between a feature that looks broken and one that is waiting.
 *
 * Deliberately says nothing about *where* the server is. The address this build
 * uses is not shown here, on the row below, or in any log — see
 * [ListenTogether.customServerUrl].
 */
@Composable
private fun ServerHealthRow(
    status: ListenTogether.ServerStatus,
    onRecheck: () -> Unit,
) {
    SettingsGroup {
        SettingsRow(
            icon = when (status.health) {
                ListenTogether.Health.ONLINE -> Icons.Rounded.CloudDone
                ListenTogether.Health.OFFLINE -> Icons.Rounded.CloudOff
                else -> Icons.Rounded.Cloud
            },
            title = stringResource(R.string.listen_together_server),
            subtitle = when (status.health) {
                ListenTogether.Health.ONLINE ->
                    stringResource(R.string.listen_together_server_online, status.latencyMs)
                ListenTogether.Health.OFFLINE ->
                    stringResource(R.string.listen_together_server_offline)
                ListenTogether.Health.CHECKING ->
                    stringResource(R.string.listen_together_server_checking)
                ListenTogether.Health.UNKNOWN ->
                    stringResource(R.string.listen_together_server_unknown)
            },
            trailing = if (status.health == ListenTogether.Health.CHECKING) ({ Spinner() }) else null,
            onClick = onRecheck,
        )
    }
}

@Composable
private fun NotInAParty(
    signedIn: Boolean,
    hasServer: Boolean,
    codeInput: String,
    onCodeInput: (String) -> Unit,
    busy: Boolean,
    nickname: String,
    onNicknameChange: (String) -> Unit,
    maxMembers: Int,
    onMaxMembersChange: (Int) -> Unit,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
) {
    val ready = signedIn && hasServer && !busy

    SettingsGroup(header = stringResource(R.string.listen_together_profile)) {
        Column(Modifier.padding(horizontal = ROW_INSET, vertical = 14.dp)) {
            PillTextField(
                value = nickname,
                onValueChange = onNicknameChange,
                placeholder = stringResource(R.string.listen_together_nickname_hint),
                container = MaterialTheme.colorScheme.background,
                enabled = ready,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
        }
    }

    SettingsGroup(footer = stringResource(R.string.listen_together_create_footer, maxMembers)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = ROW_INSET, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.listen_together_party_size),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(stringResource(R.string.listen_together_party_size_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { onMaxMembersChange(maxMembers - 1) }, enabled = ready && maxMembers > 2) { Text("−", color = MaterialTheme.colorScheme.onSurface) }
            Text("$maxMembers", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
            TextButton(onClick = { onMaxMembersChange(maxMembers + 1) }, enabled = ready && maxMembers < 10) { Text("+", color = MaterialTheme.colorScheme.onSurface) }
        }
        RowDivider()
        SettingsRow(
            icon = Icons.Rounded.GroupAdd,
            title = stringResource(R.string.listen_together_create),
            subtitle = stringResource(R.string.listen_together_create_subtitle),
            enabled = ready,
            onClick = onCreate,
            trailing = if (busy) ({ Spinner() }) else null,
        )
    }

    // The hint moved out of the box and under the card. Six cells already say
    // how many characters are wanted; what they cannot say is what may go in
    // them, and there is no longer a placeholder line to put that on.
    SettingsGroup(
        header = stringResource(R.string.listen_together_join),
        footer = stringResource(R.string.listen_together_code_hint),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = ROW_INSET, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PartyCodeField(
                code = codeInput,
                onCodeChange = onCodeInput,
                enabled = ready,
                onSubmit = onJoin,
            )
            // Full width and filled, because on this card it is the one thing
            // to press: the row above it is a keyboard target rather than a
            // control, and a text button tucked into the corner gave the
            // section no obvious end.
            Button(
                onClick = onJoin,
                enabled = ready && codeInput.length == ListenTogether.CODE_LENGTH,
                shape = CODE_CELL_SHAPE,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Text(
                    text = stringResource(R.string.listen_together_join_action),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

/** The six cells of [PartyCodeField], and the Join button under them. */
private val CODE_CELL_SHAPE = RoundedCornerShape(12.dp)

/**
 * The party code, entered as six cells rather than one box.
 *
 * A code that gets read out loud and typed in by somebody else is a sequence of
 * characters, not a word. Six cells say so without a hint line: they show how
 * many are wanted, which one is being typed, and how far in the reading has
 * got — see [listen_together_code_footer][R.string.listen_together_code_footer]
 * for why the alphabet avoids O and I.
 *
 * One [BasicTextField] underneath, not six. Six fields means six focus targets
 * to hand along on every keystroke and back again on every backspace, and a
 * pasted code that lands entirely in the first one. So the real field is
 * invisible, laid over the cells at [Modifier.matchParentSize], and the cells
 * are only ever a picture of what it holds.
 */
@Composable
private fun PartyCodeField(
    code: String,
    onCodeChange: (String) -> Unit,
    enabled: Boolean,
    onSubmit: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    // Rebuilt on every change so the selection is pinned back to the end. The
    // field spans the whole row and shows nothing, so without this a tap
    // anywhere along it would drop the caret into the middle of the code and
    // the next character would appear in a cell that isn't the lit one.
    val field = remember(code) { TextFieldValue(code, TextRange(code.length)) }

    Box(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(ListenTogether.CODE_LENGTH) { index ->
                CodeCell(
                    char = code.getOrNull(index),
                    // Only ever one cell, and only while the keyboard is up: a
                    // ring left lit on a field nobody is typing into reads as
                    // something being wrong with it.
                    active = focused && index == code.length.coerceAtMost(ListenTogether.CODE_LENGTH - 1),
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        BasicTextField(
            value = field,
            onValueChange = { onCodeChange(it.text) },
            enabled = enabled,
            singleLine = true,
            // Both invisible: the cells are where the typing shows up, and a
            // second caret drifting along behind them would be the giveaway
            // that this is one field wearing a costume.
            textStyle = TextStyle(color = Color.Transparent),
            cursorBrush = SolidColor(Color.Transparent),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = { if (code.length == ListenTogether.CODE_LENGTH) onSubmit() },
            ),
            modifier = Modifier
                .matchParentSize()
                .onFocusChanged { focused = it.isFocused },
        )
    }
}

/** One character's worth of [PartyCodeField]. */
@Composable
private fun CodeCell(
    char: Char?,
    active: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    // Animated because the ring moves cell to cell as the code is typed, and a
    // border that simply appears one box to the right on each keystroke reads
    // as flicker rather than as travel.
    val ring by animateColorAsState(
        targetValue = when {
            !enabled -> Color.Transparent
            active -> MaterialTheme.colorScheme.primary
            char != null -> MaterialTheme.colorScheme.outline
            else -> Color.Transparent
        },
        label = "party code cell ring",
    )
    Box(
        modifier = modifier
            .height(52.dp)
            // The page background, not surfaceVariant: the card these sit in is
            // surfaceVariant, so six cells in that colour would be six cells
            // nobody can see.
            .background(MaterialTheme.colorScheme.background, CODE_CELL_SHAPE)
            .border(1.5.dp, ring, CODE_CELL_SHAPE),
        contentAlignment = Alignment.Center,
    ) {
        if (char != null) {
            Text(
                text = char.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        } else if (active) {
            // A caret, and only in the empty cell being typed into — once there
            // is a character to show, the character already answers "where am I".
            val blink = rememberInfiniteTransition(label = "party code caret")
            val alpha by blink.animateFloat(
                initialValue = 1f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "party code caret alpha",
            )
            Box(
                Modifier
                    .size(width = 2.dp, height = 22.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                        RoundedCornerShape(1.dp),
                    ),
            )
        }
    }
}

@Composable
private fun InAParty(
    state: ListenTogether.State,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onLeave: () -> Unit,
    onSetCapacity: (Int) -> Unit,
    onKick: (String) -> Unit,
) {
    SettingsGroup(
        header = stringResource(R.string.listen_together_code),
        footer = stringResource(R.string.listen_together_code_footer),
    ) {
        Text(
            text = state.code.orEmpty(),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            // Wide tracking because this is a string to be read out loud and
            // typed by somebody else, not a word to be scanned.
            letterSpacing = 8.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp, bottom = 14.dp),
        )
        RowDivider()
        SettingsRow(
            icon = Icons.Rounded.ContentCopy,
            title = stringResource(R.string.copy_code),
            onClick = onCopy,
        )
        RowDivider()
        SettingsRow(
            icon = Icons.Rounded.Share,
            title = stringResource(R.string.share),
            onClick = onShare,
        )
    }

    NowPlayingInTheParty(state)

    SettingsGroup(
        header = stringResource(
            R.string.listen_together_listening,
            state.members.size,
            state.maxMembers,
        ),
        footer = stringResource(R.string.listen_together_members_footer, state.maxMembers),
    ) {
        if (state.you?.isHost == true) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = ROW_INSET, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.listen_together_party_size),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(stringResource(R.string.listen_together_host_controls), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { onSetCapacity(state.maxMembers - 1) }, enabled = state.maxMembers > maxOf(2, state.members.size)) { Text("−", color = MaterialTheme.colorScheme.onSurface) }
                Text("${state.maxMembers}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
                TextButton(onClick = { onSetCapacity(state.maxMembers + 1) }, enabled = state.maxMembers < 10) { Text("+", color = MaterialTheme.colorScheme.onSurface) }
            }
            RowDivider()
        }
        state.members.forEachIndexed { index, member ->
            if (index > 0) RowDivider()
            MemberRow(member = member, isYou = member.memberId == state.you?.memberId, canKick = state.you?.isHost == true && !member.isHost, onKick = { onKick(member.memberId) })
        }
    }

    SettingsGroup {
        SettingsRow(
            icon = Icons.Rounded.Logout,
            title = stringResource(R.string.listen_together_leave),
            subtitle = stringResource(R.string.listen_together_leave_subtitle),
            onClick = onLeave,
        )
    }
}

/**
 * What the party is playing, and where its playhead is right now.
 *
 * The position is recomputed on a tick rather than read out of the last frame,
 * because that *is* the feature: between server updates each device advances the
 * same anchored position on its own clock, and two phones side by side should
 * show the same number. A value that only moved when a frame arrived would
 * prove nothing.
 */
@Composable
private fun NowPlayingInTheParty(state: ListenTogether.State) {
    val track = state.playback.track
    var positionMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(state.playback.seq, state.playback.isPlaying) {
        while (true) {
            positionMs = ListenTogether.partyPositionMs() ?: 0L
            delay(250)
        }
    }

    // Both read before the row is built: they are [stringResource] lookups, and
    // a composable call belongs at a composable call site rather than buried
    // inside the string building below it.
    val connection = connectionLine(state)
    val nothingPlaying = stringResource(R.string.listen_together_nothing_playing)

    SettingsGroup(header = stringResource(R.string.listen_together_now_playing)) {
        SettingsRow(
            icon = Icons.Rounded.MusicNote,
            title = track?.title?.takeIf { it.isNotBlank() } ?: nothingPlaying,
            subtitle = if (track == null) {
                connection
            } else {
                buildString {
                    if (track.artist.isNotBlank()) {
                        append(track.artist)
                        append(" · ")
                    }
                    append(elapsed(positionMs))
                    track.durationMs?.let {
                        append(" / ")
                        append(elapsed(it))
                    }
                    append("\n")
                    append(connection)
                }
            },
        )
    }
}

/** One line saying whether this device is actually keeping up, and how well. */
@Composable
private fun connectionLine(state: ListenTogether.State): String = when {
    state.connection != ListenTogether.Connection.LIVE ->
        stringResource(R.string.listen_together_reconnecting)
    // Live, but the clock has not been measured yet — so the position above is
    // the server's last word on it rather than this device's own reckoning, and
    // saying so is more use than a spinner.
    !state.clockSynced -> stringResource(R.string.listen_together_syncing_clock)
    else -> stringResource(R.string.listen_together_in_sync, state.roundTripMs)
}

@Composable
private fun MemberRow(member: PartyMember, isYou: Boolean, canKick: Boolean = false, onKick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = ROW_INSET, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            if (member.avatarUrl != null) {
                AsyncImage(
                    model = member.avatarUrl,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp).clip(CircleShape),
                )
            } else {
                Text(
                    text = member.displayName.take(1).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = member.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isYou) {
                    Spacer(Modifier.width(8.dp))
                    Badge(stringResource(R.string.listen_together_you))
                }
            }
            // "Away" rather than "offline": the slot is still theirs, and the
            // server holds it through a grace period precisely so a tunnel or a
            // locked screen does not read to everyone else as leaving.
            if (!member.connected) {
                Text(
                    text = stringResource(R.string.listen_together_away),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (member.isHost) {
            Spacer(Modifier.width(8.dp))
            Badge(stringResource(R.string.listen_together_host))
        } else if (canKick) {
            TextButton(onClick = onKick) { Text(stringResource(R.string.listen_together_remove)) }
        }
    }
}

@Composable
private fun PartyActivityList(entries: List<PartyActivity>) {
    if (entries.isEmpty()) return
    SettingsGroup(
        header = stringResource(R.string.listen_together_activity),
        footer = stringResource(R.string.listen_together_activity_footer),
    ) {
        val logScroll = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(156.dp)
                .background(MaterialTheme.colorScheme.surface)
                .verticalScroll(logScroll)
                .padding(horizontal = ROW_INSET, vertical = 8.dp),
        ) {
            entries.forEach { entry ->
                val timestamp = DateFormat.format("HH:mm:ss", entry.atMs)
                Text(
                    text = "[$timestamp] ${entry.by}: ${entry.detail.ifBlank { entry.action }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Spinner() {
    CircularProgressIndicator(
        strokeWidth = 2.dp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(18.dp),
    )
}

private fun elapsed(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val minutes = total / 60
    val seconds = total % 60
    return "%d:%02d".format(minutes, seconds)
}
