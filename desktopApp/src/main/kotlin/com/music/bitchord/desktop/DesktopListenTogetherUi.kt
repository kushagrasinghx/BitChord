package com.music.bitchord.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.data.listentogether.JamInvite
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.data.listentogether.PartyMember
import kotlinx.coroutines.launch

/**
 * Listening together: create a party or join one, and see who is in it.
 *
 * A dialog rather than a page, which is where the desktop puts everything of this shape — the
 * equaliser, the sources, the account switcher. Android gives it a screen because a phone has
 * nowhere else to put it.
 */
@Composable
internal fun DesktopListenTogetherDialog(onDismiss: () -> Unit) {
    val state by DesktopListenTogether.state.collectAsState()
    val server by DesktopListenTogether.customServerUrl.collectAsState()
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    DesktopDialogPanel(onDismiss = onDismiss, maxWidth = 460) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                DesktopStrings["listen_together", "Listen together"],
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            if (!DesktopListenTogether.canJoin()) {
                Text(
                    DesktopStrings["d_listen_together_sign_in", "Sign in to listen together."],
                    color = DesktopSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            state.error?.let {
                Text(it, color = DesktopDestructive, style = MaterialTheme.typography.bodySmall)
            }

            if (state.inParty) {
                InAParty(state, busy) {
                    busy = true
                    scope.launch {
                        DesktopListenTogether.leaveParty()
                        busy = false
                    }
                }
            } else {
                NotInAParty(
                    code = code,
                    onCodeChange = { code = it.uppercase().filter(Char::isLetterOrDigit).take(JamInvite.CODE_LENGTH) },
                    busy = busy,
                    enabled = DesktopListenTogether.canJoin() && DesktopListenTogether.hasServer,
                    onCreate = {
                        busy = true
                        scope.launch {
                            DesktopListenTogether.createParty()
                            busy = false
                        }
                    },
                    onJoin = {
                        busy = true
                        scope.launch {
                            DesktopListenTogether.joinParty(code)
                            busy = false
                        }
                    },
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    DesktopStrings["listen_together_server", "Party server"],
                    color = DesktopSecondary,
                    style = MaterialTheme.typography.labelMedium,
                )
                DesktopSearchField(
                    query = server,
                    onQueryChange = DesktopListenTogether::setCustomServerUrl,
                    onSearch = {},
                    placeholder = DesktopStrings["listen_together_custom_server", "Custom server address"],
                )
                Text(
                    DesktopStrings[
                        "listen_together_custom_server_footer",
                        "Leave blank to use the address this build ships with.",
                    ],
                    color = DesktopSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun NotInAParty(
    code: String,
    onCodeChange: (String) -> Unit,
    busy: Boolean,
    enabled: Boolean,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            DesktopStrings["listen_together_create_subtitle", "Start a party and share the code."],
            color = DesktopSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        Button(
            onClick = onCreate,
            enabled = enabled && !busy,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = DesktopAccent, contentColor = Color.Black),
        ) { Text(DesktopStrings["listen_together_create", "Start a party"]) }

        Spacer(Modifier.height(2.dp))
        Text(
            DesktopStrings["listen_together_code_hint", "Or enter a code someone shared with you"],
            color = DesktopSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            DesktopSearchField(
                query = code,
                onQueryChange = onCodeChange,
                onSearch = onJoin,
                placeholder = DesktopStrings["listen_together_code", "Party code"],
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            TextButton(
                onClick = onJoin,
                enabled = enabled && !busy && code.length == JamInvite.CODE_LENGTH,
            ) { Text(DesktopStrings["listen_together_join_action", "Join"]) }
        }
    }
}

@Composable
private fun InAParty(
    state: DesktopListenTogether.State,
    busy: Boolean,
    onLeave: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    state.code.orEmpty(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 6.sp,
                )
                Text(connectionLine(state), color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { DesktopExternalLinks.copy(JamInvite.url(state.code.orEmpty())) }) {
                Text(DesktopStrings["copy_code", "Copy link"])
            }
        }

        val track = state.playback.track
        Column(Modifier.desktopCardInset(RoundedCornerShape(12.dp)).padding(14.dp)) {
            Text(
                DesktopStrings["listen_together_now_playing", "Now playing"],
                color = DesktopSecondary,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(4.dp))
            if (track == null) {
                Text(
                    DesktopStrings["listen_together_nothing_playing", "Nothing playing yet"],
                    color = DesktopSecondary,
                )
            } else {
                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Text(
                    track.artist,
                    color = DesktopSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Text(
            DesktopStrings["listen_together_listening", "Listening"],
            color = DesktopSecondary,
            style = MaterialTheme.typography.labelMedium,
        )
        state.members.forEach { member ->
            MemberRow(member, isYou = member.memberId == state.you?.memberId)
        }

        TextButton(onClick = onLeave, enabled = !busy) {
            Text(DesktopStrings["listen_together_leave", "Leave party"], color = DesktopDestructive)
        }
    }
}

@Composable
private fun MemberRow(member: PartyMember, isYou: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DesktopArtwork(
            member.avatarUrl,
            Modifier.size(28.dp).clip(CircleShape),
            px = 96,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            member.displayName.ifBlank { DesktopStrings["d_someone", "Someone"] },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (member.isHost) {
            Text(
                DesktopStrings["listen_together_host", "Host"],
                color = DesktopSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.width(8.dp))
        }
        // Away rather than gone: a member whose socket dropped is still in the party.
        if (!member.connected) {
            Text(
                DesktopStrings["listen_together_away", "Away"],
                color = DesktopSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
        } else if (isYou) {
            Icon(BitChordIcons.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

/** What the party's connection is doing, in the words Android uses for it. */
@Composable
private fun connectionLine(state: DesktopListenTogether.State): String = when {
    state.connection == DesktopListenTogether.Connection.CONNECTING ->
        DesktopStrings["listen_together_reconnecting", "Reconnecting…"]
    !state.clockSynced -> DesktopStrings["listen_together_server_checking", "Syncing…"]
    else -> DesktopStrings["listen_together_in_sync", "In sync"]
}
