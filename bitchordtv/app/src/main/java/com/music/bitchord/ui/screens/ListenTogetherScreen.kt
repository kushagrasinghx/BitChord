package com.music.bitchord.ui.screens

import android.content.Intent
import android.text.format.DateFormat
import android.widget.Toast
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
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.listentogether.JamInviteLink
import com.music.bitchord.data.listentogether.ListenTogether
import com.music.bitchord.data.listentogether.PartyActivity
import com.music.bitchord.data.listentogether.PartyMember
import com.music.bitchord.data.listentogether.ServerConnectionState
import com.music.bitchord.data.listentogether.ServerUrlError
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal fun ServerUrlError.toMessageRes(): Int = when (this) {
    ServerUrlError.Whitespace -> R.string.listen_together_err_whitespace
    ServerUrlError.InvalidScheme -> R.string.listen_together_invalid_server_url
    ServerUrlError.InvalidHost -> R.string.listen_together_invalid_server_url
    ServerUrlError.InvalidPort -> R.string.listen_together_err_invalid_port
    ServerUrlError.InvalidPath -> R.string.listen_together_err_invalid_path
    ServerUrlError.HasPath -> R.string.listen_together_err_no_path
    ServerUrlError.HasQuery -> R.string.listen_together_err_no_query
    ServerUrlError.HasFragment -> R.string.listen_together_err_no_fragment
    ServerUrlError.Malformed -> R.string.listen_together_invalid_server_url
}

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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenTogetherScreen(
    signedIn: Boolean,
    inviteCode: String? = null,
    inviteServer: String? = null,
    onInviteHandled: () -> Unit = {},
    onSignIn: () -> Unit,
    contentPadding: PaddingValues,
    /**
     * Opens the server editor, which is mounted at the root rather than here.
     *
     * @see PartyServerEditor for why it cannot live on this screen.
     */
    onEditServer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val state by ListenTogether.state.collectAsStateWithLifecycle()
    val customServer by ListenTogether.customServerUrl.collectAsStateWithLifecycle()
    val connectionState by ListenTogether.serverConnectionState.collectAsStateWithLifecycle()
    val activity by ListenTogether.activity.collectAsStateWithLifecycle()

    var codeInput by remember(inviteCode) { mutableStateOf(inviteCode.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var nickname by remember { mutableStateOf(ListenTogether.nickname()) }
    var maxMembers by remember { mutableIntStateOf(5) }
    var pendingServerSwitchInvite by remember { mutableStateOf<Pair<String, String>?>(null) }
    var sheet by remember { mutableStateOf<PartySheet?>(null) }

    // Hoisted so a sheet can be *animated* away rather than dropped out of the
    // composition, which is the only way one sheet can hand over to another:
    // see the invite that follows a create.
    val sheetState = rememberModalBottomSheetState()

    /**
     * Hands the create sheet over to the invite sheet, with the party page
     * visible in between.
     *
     * Three separate things, and the order is the whole point. The create sheet
     * is *animated* down rather than dropped out of the composition, because
     * swapping one sheet's contents for another's under a drawer that never
     * moved is a swap, and a swap is the one thing that does not read as a new
     * sheet arriving. Then a beat with nothing over the page at all: what is
     * underneath has just become a different page — a code, a member list — and
     * an invite that rises immediately means nobody ever sees that it did. Only
     * then the invite, from a fresh mount, so it slides.
     */
    val showInviteAfterCreate: suspend () -> Unit = {
        runCatching { sheetState.hide() }
        sheet = null
        delay(INVITE_SHEET_DELAY_MS)
        // A second is long enough to have gone and opened something else in,
        // and being interrupted by a drawer nobody asked for is worse than not
        // being offered the link.
        if (sheet == null) sheet = PartySheet.Invite
    }

    /** The invite URL for a code, pointing at whichever server holds the party. */
    val inviteLinkFor: (String) -> String = { partyCode ->
        val host = ListenTogether.activePartyServerBase()
        if (host == null || host == ListenTogether.defaultServer) {
            JamInviteLink.url(partyCode, null)
        } else {
            JamInviteLink.url(partyCode, host)
        }
    }

    /** The system chooser, with the link and the spoken-aloud code together. */
    val shareInvite: () -> Unit = {
        state.code?.let { partyCode ->
            val message = inviteLinkFor(partyCode) +
                System.lineSeparator() + System.lineSeparator() +
                context.getString(R.string.listen_together_share_text, partyCode)
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, message)
                    },
                    null,
                ),
            )
        }
    }

    /**
     * Looks a party up and puts its faces on screen, rather than joining it.
     *
     * Both doors in — a tapped link and six characters typed — come through
     * here, so what the listener agrees to is the same picture either way.
     */
    val openPartyPreview: (String, String?) -> Unit = { previewCode, previewServer ->
        busy = true
        failure = null
        scope.launch {
            ListenTogether.previewParty(previewCode, previewServer)
                .onSuccess { sheet = PartySheet.Confirm(it, previewServer) }
                .onFailure { failure = it.message }
            busy = false
        }
    }

    // A membership outlives the process; the socket does not. Opening it when
    // the screen is looked at — rather than on every cold start — is what keeps
    // a feature nobody is currently using off the radio.
    LaunchedEffect(Unit) { ListenTogether.ensureConnected() }
    DisposableEffect(Unit) {
        ListenTogether.setScreenActive(true)
        onDispose {
            ListenTogether.setScreenActive(false)
        }
    }

    // A link tap is already an explicit request to join. Signed-out users keep
    // the populated code while the sign-in page is open.
    LaunchedEffect(inviteCode, inviteServer, signedIn) {
        val code = inviteCode ?: return@LaunchedEffect
        if (!signedIn || busy) return@LaunchedEffect

        val activeServer = (ListenTogether.activePartyServerBase() ?: ListenTogether.effectiveIdleServerBase()).trim().trimEnd('/')
        val targetServer = inviteServer?.trim()?.trimEnd('/')

        if (!targetServer.isNullOrBlank()) {
            if (!targetServer.equals(activeServer, ignoreCase = true)) {
                pendingServerSwitchInvite = code to targetServer
                return@LaunchedEffect
            }
        } else if (customServer.isNotBlank()) {
            pendingServerSwitchInvite = code to ""
            return@LaunchedEffect
        }

        if (state.code.equals(code, ignoreCase = true)) {
            onInviteHandled()
            return@LaunchedEffect
        }

        // A link says which party, not that this device has agreed to join it.
        // The confirm sheet is what actually joins — the same one a typed
        // code goes through, whether or not this device is already in a
        // party.
        openPartyPreview(code, targetServer)
        onInviteHandled()
    }

    pendingServerSwitchInvite?.let { (codeToJoin, serverToSet) ->
        val isSwitchToOfficial = serverToSet.isBlank()
        AlertDialog(
            onDismissRequest = {
                pendingServerSwitchInvite = null
                onInviteHandled()
            },
            title = {
                Text(
                    stringResource(
                        if (isSwitchToOfficial) {
                            R.string.listen_together_official_server_dialog_title
                        } else {
                            R.string.listen_together_custom_server_dialog_title
                        }
                    ),
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Text(
                    if (isSwitchToOfficial) {
                        stringResource(R.string.listen_together_official_server_dialog_message, codeToJoin)
                    } else {
                        stringResource(
                            R.string.listen_together_custom_server_dialog_message,
                            serverToSet,
                            codeToJoin,
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toJoin = codeToJoin
                        val toSet = serverToSet
                        pendingServerSwitchInvite = null
                        // Same next step as any other invite: the confirm
                        // sheet, not an immediate join. This dialog is only
                        // consent to talk to a different server at all.
                        openPartyPreview(toJoin, toSet.ifBlank { null })
                        onInviteHandled()
                    },
                ) {
                    Text(
                        stringResource(R.string.listen_together_custom_server_switch_and_join),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingServerSwitchInvite = null
                        onInviteHandled()
                    },
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }

    sheet?.let { open ->
        ModalBottomSheet(
            onDismissRequest = {
                sheet = null
                // Closed with the message unread is still read: leaving it set
                // would surface it on the page underneath, as a line about a
                // sheet that is no longer there.
                failure = null
            },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            when (open) {
                PartySheet.Create -> CreatePartySheet(
                    avatarUrl = ListenTogether.myAvatarUrl(),
                    nickname = nickname,
                    onNicknameChange = {
                        nickname = it.take(80)
                        ListenTogether.setNickname(nickname)
                    },
                    maxMembers = maxMembers,
                    onMaxMembersChange = { maxMembers = it.coerceIn(2, 10) },
                    busy = busy,
                    enabled = signedIn && ListenTogether.hasServer,
                    error = failure,
                    onCreate = {
                        busy = true
                        failure = null
                        scope.launch {
                            failure = ListenTogether.createParty(nickname, maxMembers)
                                .exceptionOrNull()?.message
                            busy = false
                            if (failure == null) showInviteAfterCreate()
                        }
                    },
                )

                PartySheet.JoinCode -> JoinPartySheet(
                    code = codeInput,
                    onCodeChange = { typed ->
                        codeInput = typed.filter(Char::isLetterOrDigit)
                            .uppercase()
                            .take(ListenTogether.CODE_LENGTH)
                        // Typing is the retry. Keeping "no party with that
                        // code" on screen while the code is being corrected
                        // makes it read as a verdict on what is there now.
                        failure = null
                    },
                    busy = busy,
                    enabled = signedIn && ListenTogether.hasServer,
                    error = failure,
                    onSubmit = { openPartyPreview(codeInput, null) },
                )

                is PartySheet.Confirm -> JoinConfirmSheet(
                    preview = open.preview,
                    avatarUrl = ListenTogether.myAvatarUrl(),
                    nickname = nickname,
                    onNicknameChange = {
                        nickname = it.take(80)
                        ListenTogether.setNickname(nickname)
                    },
                    busy = busy,
                    error = failure,
                    onDismiss = {
                        sheet = null
                        failure = null
                    },
                    onJoin = {
                        busy = true
                        failure = null
                        scope.launch {
                            // A server named on the invite itself always wins,
                            // whether this device is idle or already live: it
                            // is the one place the target the preview came
                            // from is actually known. Otherwise, already being
                            // in a party means a switch, which keeps this
                            // device where it is if the new party turns it
                            // away.
                            val problem = if (open.server != null || state.inParty) {
                                val target = ListenTogether.resolveSwitchTarget(open.server, customServer)
                                when (
                                    val switched = ListenTogether.switchPartyWithRecovery(
                                        target,
                                        open.preview.code,
                                        nickname,
                                    )
                                ) {
                                    is ListenTogether.SwitchPartyResult.Success -> null
                                    is ListenTogether.SwitchPartyResult.TargetFailedRecovered ->
                                        switched.targetError
                                    is ListenTogether.SwitchPartyResult.TargetFailedNoParty ->
                                        switched.targetError
                                }
                            } else {
                                ListenTogether.joinParty(open.preview.code, nickname)
                                    .exceptionOrNull()?.message
                            }
                            failure = problem
                            busy = false
                            if (problem == null) {
                                codeInput = ""
                                sheet = null
                            }
                        }
                    },
                )

                PartySheet.Invite -> InviteSheet(
                    code = state.code.orEmpty(),
                    link = inviteLinkFor(state.code.orEmpty()),
                    onShareLink = {
                        sheet = null
                        shareInvite()
                    },
                    onCopyCode = {
                        clipboard.setText(AnnotatedString(state.code.orEmpty()))
                        Toast.makeText(
                            context,
                            context.getString(R.string.copy_code),
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                )
            }
        }
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
            PartyLanding(
                avatarUrl = ListenTogether.myAvatarUrl(),
                enabled = signedIn && ListenTogether.hasServer && !busy,
                // The last attempt's message does not belong on the next one.
                onCreate = {
                    failure = null
                    sheet = PartySheet.Create
                },
                onJoin = {
                    failure = null
                    sheet = PartySheet.JoinCode
                },
            )
        } else {
            InAParty(
                state = state,
                onCopy = { clipboard.setText(AnnotatedString(state.code.orEmpty())) },
                onShare = { sheet = PartySheet.Invite },
                onShareLink = shareInvite,
                onLeave = { scope.launch { ListenTogether.leaveParty() } },
                onSetCapacity = ListenTogether::setMaxMembers,
                onSetHostOnlyControl = ListenTogether::setHostOnlyControl,
                onKick = ListenTogether::kick,
            )
            // Inside the party branch on purpose. A log of who skipped what is
            // a thing to look back over while a party is running; on the page
            // that offers to start one it is a list of somebody else's evening,
            // under a button that has not been pressed yet.
            PartyActivityList(activity)
        }

        // Only when nothing is covering it. A sheet is a drawer over the bottom
        // of this page, and every failure it can produce is already shown
        // inside it — printing the same line down here as well means printing
        // it where it cannot be read.
        if (sheet == null) {
            (failure ?: state.error)?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = GROUP_INSET + 4.dp, end = GROUP_INSET + 4.dp, top = 12.dp),
                )
            }
        }

        // Last, and empty by default. Nobody setting up a party needs to think
        // about an address — there is one built in — so this is where somebody
        // running their own server comes looking, rather than the first thing
        // everybody else has to read past.
        //
        // A row and a dialog rather than a box sitting open on the page. An
        // address is typed once and then never again, and a field left on
        // screen has to explain itself continuously: what the Save beside it
        // applies to, why it greys out, what becomes of a half-typed address
        // when the page is scrolled away from. Behind a row there is nothing
        // half-typed to explain — the same shape as the ListenBrainz token.
        SettingsGroup(
            header = stringResource(R.string.listen_together_custom_server),
            footer = stringResource(R.string.listen_together_custom_server_footer),
        ) {
            SettingsRow(
                icon = Icons.Rounded.Dns,
                title = stringResource(R.string.listen_together_custom_server),
                subtitleContent = {
                    Text(
                        text = customServer.ifBlank {
                            stringResource(R.string.listen_together_using_default)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (customServer.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        ServerConnectionLine(connectionState)
                    }
                },
                // Locked while in a party: changing the address under a live
                // membership would leave this device holding a token for a
                // server it no longer talks to, and the party unable to say
                // why it went quiet.
                enabled = !state.inParty && !busy,
                onClick = onEditServer,
                trailing = if (busy) ({ Spinner() }) else null,
            )
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
                    if (status.isFallback) {
                        stringResource(R.string.listen_together_server_online_fallback, status.latencyMs)
                    } else {
                        stringResource(R.string.listen_together_server_online, status.latencyMs)
                    }
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

/**
 * Whether the address that was typed is actually answering.
 *
 * Under the address rather than in a toast, because the interesting case is the
 * one nobody is watching for: a server that answered when it was saved and has
 * since gone quiet. A toast for that would have to fire at a moment nobody
 * asked anything, so it does not fire at all — this line simply reads
 * differently the next time the page is opened.
 */
@Composable
private fun ServerConnectionLine(connection: ServerConnectionState) {
    val (icon, tint, label) = when (connection) {
        ServerConnectionState.Checking -> Triple(
            null,
            MaterialTheme.colorScheme.onSurfaceVariant,
            stringResource(R.string.listen_together_server_checking),
        )
        is ServerConnectionState.CustomOnline -> Triple(
            Icons.Rounded.CloudDone,
            MaterialTheme.colorScheme.primary,
            stringResource(R.string.listen_together_status_custom_online, connection.latencyMs),
        )
        is ServerConnectionState.CustomFallback -> Triple(
            Icons.Rounded.CloudOff,
            MaterialTheme.colorScheme.error,
            stringResource(R.string.listen_together_status_custom_fallback, connection.latencyMs),
        )
        ServerConnectionState.Offline -> Triple(
            Icons.Rounded.CloudOff,
            MaterialTheme.colorScheme.error,
            stringResource(R.string.listen_together_status_all_offline),
        )
        // Nothing to say: the row above already reads "using the built-in one".
        is ServerConnectionState.DefaultOnline -> return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon == null) {
            Spinner(modifier = Modifier.size(14.dp))
        } else {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * How long the newly made party sits on screen before the invite rises over it.
 *
 * Long enough to be a page that was arrived at rather than a frame that flashed
 * past, short enough that nobody has started reading the member list yet.
 */
private const val INVITE_SHEET_DELAY_MS = 1_000L

/** The six cells of [PartyCodeField], and the Join button under them. */
internal val CODE_CELL_SHAPE = RoundedCornerShape(12.dp)

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
internal fun PartyCodeField(
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
    //
    // Every cell keeps a ring; only its colour changes. Lighting one and
    // leaving the other five with nothing at all is what turned six boxes into
    // a single floating square — there has to be a row of somewhere-to-type
    // first, or the lit one is not "the cell you are on", it is the only cell.
    val ring by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.outline
            active -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outline
        },
        label = "party code cell ring",
    )
    Box(
        modifier = modifier
            .height(52.dp)
            // surfaceVariant, because these now live in a sheet whose container
            // is the page background: a cell painted in that colour is a cell
            // nobody can see.
            .background(MaterialTheme.colorScheme.surfaceVariant, CODE_CELL_SHAPE)
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
    onShareLink: () -> Unit,
    onLeave: () -> Unit,
    onSetCapacity: (Int) -> Unit,
    onSetHostOnlyControl: (Boolean) -> Unit,
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
            SettingsRow(
                icon = Icons.Rounded.Lock,
                title = stringResource(R.string.listen_together_host_only),
                subtitle = stringResource(R.string.listen_together_host_only_subtitle),
                trailing = {
                    Switch(
                        checked = state.hostOnlyControl,
                        onCheckedChange = onSetHostOnlyControl,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            checkedBorderColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                },
                onClick = { onSetHostOnlyControl(!state.hostOnlyControl) },
            )
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

/**
 * This device's own party picture, drawn in the slot the rows around it give
 * their glyphs — so the field beside it starts on the same line as every other
 * field on the screen.
 */
@Composable
private fun PartyAvatar(url: String?, size: Dp = ICON_SIZE) {
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(CircleShape),
        )
    } else {
        Icon(
            Icons.Rounded.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(size),
        )
    }
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
private fun Spinner(modifier: Modifier = Modifier.size(18.dp)) {
    CircularProgressIndicator(
        strokeWidth = 2.dp,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}

private fun elapsed(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val minutes = total / 60
    val seconds = total % 60
    return "%d:%02d".format(minutes, seconds)
}
