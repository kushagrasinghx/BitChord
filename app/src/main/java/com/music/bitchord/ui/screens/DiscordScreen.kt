package com.music.bitchord.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.SmartButton
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.discord.DiscordRPC
import com.music.bitchord.data.discord.SuperProperties
import com.music.bitchord.data.model.CARD_ART_PX
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.components.ChoiceAlert
import com.music.bitchord.ui.components.DiscordTokenAlert
import com.music.bitchord.ui.components.TextValueAlert
import com.music.bitchord.ui.components.thumbnailBorder
import com.my.kizzy.rpc.KizzyRPC
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Which dot Discord draws on the avatar, and what it tells other people. */
enum class DiscordPresenceStatus(val value: String, val label: String, val detail: String) {
    ONLINE("online", "Online", "Green dot"),
    IDLE("idle", "Idle", "Amber crescent — as if away"),
    DND("dnd", "Do not disturb", "Red dash — suppresses their notifications too"),
}

/**
 * The verb on the profile. All four render the same card; only the line above it
 * changes, so this is purely how you'd rather have it read.
 */
enum class DiscordActivityKind(val value: String, val verb: String, val label: String) {
    LISTENING("listening", "Listening to", "Listening"),
    PLAYING("playing", "Playing", "Playing"),
    WATCHING("watching", "Watching", "Watching"),
    COMPETING("competing", "Competing in", "Competing"),
}

/**
 * The dialogs the Discord screen opens. Hoisted out to an enum because they are
 * rendered by the activity, above the tab bar and mini player, rather than
 * inside the scrolling screen where a full-screen scrim would be trapped.
 */
enum class DiscordDialog { TOKEN, STATUS, ACTIVITY_TYPE, ACTIVITY_NAME, BUTTON_1, BUTTON_2 }

private fun statusOf(value: String) =
    DiscordPresenceStatus.entries.firstOrNull { it.value == value } ?: DiscordPresenceStatus.ONLINE

private fun kindOf(value: String) =
    DiscordActivityKind.entries.firstOrNull { it.value == value } ?: DiscordActivityKind.LISTENING

@Composable
private fun localizedStatusCopy(): Map<DiscordPresenceStatus, Pair<String, String>> = mapOf(
    DiscordPresenceStatus.ONLINE to (
        stringResource(R.string.status_online) to stringResource(R.string.status_online_detail)
        ),
    DiscordPresenceStatus.IDLE to (
        stringResource(R.string.status_idle) to stringResource(R.string.status_idle_detail)
        ),
    DiscordPresenceStatus.DND to (
        stringResource(R.string.status_dnd) to stringResource(R.string.status_dnd_detail)
        ),
)

@Composable
private fun localizedActivityCopy(): Map<DiscordActivityKind, Pair<String, String>> = mapOf(
    DiscordActivityKind.LISTENING to (
        stringResource(R.string.activity_listening) to stringResource(R.string.verb_listening)
        ),
    DiscordActivityKind.PLAYING to (
        stringResource(R.string.activity_playing) to stringResource(R.string.verb_playing)
        ),
    DiscordActivityKind.WATCHING to (
        stringResource(R.string.activity_watching) to stringResource(R.string.verb_watching)
        ),
    DiscordActivityKind.COMPETING to (
        stringResource(R.string.activity_competing) to stringResource(R.string.verb_competing)
        ),
)

@Composable
private fun DiscordPresenceStatus.localizedLabel(): String =
    localizedStatusCopy().getValue(this).first

@Composable
private fun DiscordActivityKind.localizedLabel(): String =
    localizedActivityCopy().getValue(this).first

@Composable
private fun DiscordActivityKind.localizedVerb(): String =
    localizedActivityCopy().getValue(this).second

/**
 * Discord Rich Presence: the account it posts as, what the card says, and a
 * live preview of it.
 *
 * The preview is the point of the screen. Every field here changes something
 * about a card the user cannot see from inside this app — so it draws the card
 * as Discord will, from the track actually playing, and updates as they type.
 */
@Composable
fun DiscordScreen(
    song: Song?,
    positionMs: Long,
    durationMs: Long,
    onOpenLogin: () -> Unit,
    onOpenDialog: (DiscordDialog) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val token by AppSettings.discordToken.collectAsStateWithLifecycle()
    val username by AppSettings.discordUsername.collectAsStateWithLifecycle()
    val name by AppSettings.discordName.collectAsStateWithLifecycle()
    val avatar by AppSettings.discordAvatar.collectAsStateWithLifecycle()
    val rpcEnabled by AppSettings.discordRpcEnabled.collectAsStateWithLifecycle()
    val useDetails by AppSettings.discordUseDetails.collectAsStateWithLifecycle()
    val advancedMode by AppSettings.discordAdvancedMode.collectAsStateWithLifecycle()
    val status by AppSettings.discordStatus.collectAsStateWithLifecycle()
    val activityType by AppSettings.discordActivityType.collectAsStateWithLifecycle()
    val activityName by AppSettings.discordActivityName.collectAsStateWithLifecycle()
    val button1Text by AppSettings.discordButton1Text.collectAsStateWithLifecycle()
    val button1Visible by AppSettings.discordButton1Visible.collectAsStateWithLifecycle()
    val button2Text by AppSettings.discordButton2Text.collectAsStateWithLifecycle()
    val button2Visible by AppSettings.discordButton2Visible.collectAsStateWithLifecycle()
    val infoDismissed by AppSettings.discordInfoDismissed.collectAsStateWithLifecycle()

    val connected = token.isNotEmpty()
    val discordIcon = ImageVector.vectorResource(R.drawable.ic_discord)

    // Refreshes the cached profile whenever the connected account changes. A
    // failure is left to stand rather than clearing the cache: the usual reason
    // for one is being offline, and blanking the name every time the phone loses
    // signal would read as having been signed out.
    LaunchedEffect(token) {
        if (token.isEmpty()) {
            AppSettings.setDiscordAccount("", "", null)
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            KizzyRPC.getUserInfo(
                token,
                SuperProperties.userAgent,
                SuperProperties.superPropertiesBase64,
            ).onSuccess {
                AppSettings.setDiscordAccount(it.username, it.name, it.avatar)
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
            text = stringResource(R.string.discord),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp),
        )

        DiscordAccountCard(
            connected = connected,
            name = name,
            username = username,
            avatar = avatar,
            status = statusOf(status),
            icon = discordIcon,
            onConnect = onOpenLogin,
        )

        if (!connected) {
            SettingsGroup(
                footer = stringResource(R.string.discord_sign_in_footer),
            ) {
                SettingsRow(
                    icon = Icons.Rounded.Key,
                    title = stringResource(R.string.enter_token_instead),
                    subtitle = stringResource(R.string.enter_token_instead_subtitle),
                    onClick = { onOpenDialog(DiscordDialog.TOKEN) },
                )
            }
        }

        AnimatedVisibility(visible = !infoDismissed) {
            NoticeCard(
                text = stringResource(R.string.discord_token_warning),
                onDismiss = { AppSettings.setDiscordInfoDismissed(true) },
            )
        }

        SettingsGroup(
            header = stringResource(R.string.rich_presence),
            footer = stringResource(R.string.discord_presence_footer),
        ) {
            SettingsRow(
                icon = discordIcon,
                title = stringResource(R.string.show_what_playing),
                subtitle = if (connected) null else stringResource(R.string.connect_account_first),
                enabled = connected,
                trailing = {
                    Switch(
                        checked = rpcEnabled && connected,
                        onCheckedChange = AppSettings::setDiscordRpcEnabled,
                        enabled = connected,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            checkedBorderColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                },
                onClick = { AppSettings.setDiscordRpcEnabled(!rpcEnabled) },
            )
            RowDivider()
            SettingsRow(
                icon = Icons.AutoMirrored.Rounded.Label,
                title = stringResource(R.string.lead_with_song),
                subtitle = stringResource(R.string.lead_with_song_subtitle),
                enabled = connected && rpcEnabled,
                trailing = {
                    Switch(
                        checked = useDetails,
                        onCheckedChange = AppSettings::setDiscordUseDetails,
                        enabled = connected && rpcEnabled,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            checkedBorderColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                },
                onClick = { AppSettings.setDiscordUseDetails(!useDetails) },
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.Tune,
                title = stringResource(R.string.customize_card),
                subtitle = stringResource(R.string.customize_card_subtitle),
                enabled = connected && rpcEnabled,
                trailing = {
                    Switch(
                        checked = advancedMode,
                        onCheckedChange = AppSettings::setDiscordAdvancedMode,
                        enabled = connected && rpcEnabled,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            checkedBorderColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                },
                onClick = { AppSettings.setDiscordAdvancedMode(!advancedMode) },
            )
        }

        AnimatedVisibility(visible = connected && rpcEnabled && advancedMode) {
            Column(Modifier.fillMaxWidth()) {
                SettingsGroup(header = stringResource(R.string.presence)) {
                    SettingsRow(
                        icon = Icons.Rounded.RadioButtonChecked,
                        title = stringResource(R.string.status),
                        value = statusOf(status).localizedLabel(),
                        onClick = { onOpenDialog(DiscordDialog.STATUS) },
                    )
                    RowDivider()
                    SettingsRow(
                        icon = Icons.Rounded.Tune,
                        title = stringResource(R.string.activity),
                        value = kindOf(activityType).localizedLabel(),
                        onClick = { onOpenDialog(DiscordDialog.ACTIVITY_TYPE) },
                    )
                    RowDivider()
                    SettingsRow(
                        icon = Icons.AutoMirrored.Rounded.Label,
                        title = stringResource(R.string.name),
                        subtitle = activityName.ifEmpty { appName() },
                        onClick = { onOpenDialog(DiscordDialog.ACTIVITY_NAME) },
                    )
                }

                SettingsGroup(
                    header = stringResource(R.string.buttons),
                    footer = stringResource(R.string.discord_buttons_footer),
                ) {
                    SettingsRow(
                        icon = Icons.Rounded.SmartButton,
                        title = stringResource(R.string.first_button),
                        subtitle = button1Text.ifEmpty { DiscordRPC.DEFAULT_BUTTON_1 },
                        trailing = {
                            Switch(
                                checked = button1Visible,
                                onCheckedChange = AppSettings::setDiscordButton1Visible,
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                    checkedBorderColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        },
                        onClick = { onOpenDialog(DiscordDialog.BUTTON_1) },
                    )
                    RowDivider()
                    SettingsRow(
                        icon = Icons.Rounded.SmartButton,
                        title = stringResource(R.string.second_button),
                        subtitle = button2Text.ifEmpty { DiscordRPC.DEFAULT_BUTTON_2 },
                        trailing = {
                            Switch(
                                checked = button2Visible,
                                onCheckedChange = AppSettings::setDiscordButton2Visible,
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                    checkedBorderColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        },
                        onClick = { onOpenDialog(DiscordDialog.BUTTON_2) },
                    )
                }
            }
        }

        SettingsGroup(
            header = stringResource(R.string.preview),
            footer = if (song == null) stringResource(R.string.play_to_preview) else null,
        ) {
            RichPresencePreview(
                song = song,
                positionMs = positionMs,
                durationMs = durationMs,
                heading = activityName.ifEmpty { appName() },
                verb = kindOf(activityType).localizedVerb(),
                useDetails = useDetails,
                button1Text = button1Text,
                button1Visible = button1Visible,
                button2Text = button2Text,
                button2Visible = button2Visible,
            )
        }

        if (connected) {
            SettingsGroup {
                DestructiveRow(
                    label = stringResource(R.string.disconnect),
                    onClick = { AppSettings.clearDiscordAccount() },
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** Who this posts as. Same frame as [AccountCard], with Discord's status dot. */
@Composable
private fun DiscordAccountCard(
    connected: Boolean,
    name: String,
    username: String,
    avatar: String,
    status: DiscordPresenceStatus,
    icon: ImageVector,
    onConnect: () -> Unit,
) {
    val cardColor = MaterialTheme.colorScheme.surfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GROUP_INSET)
            .clip(GroupShape)
            .background(cardColor)
            .then(if (connected) Modifier else Modifier.clickable(onClick = onConnect))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp)) {
            if (avatar.isNotEmpty()) {
                AsyncImage(
                    model = avatar,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(52.dp).clip(CircleShape).thumbnailBorder(CircleShape),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outline),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            if (connected) {
                Box(
                    modifier = Modifier
                        .size(15.dp)
                        .align(Alignment.BottomEnd)
                        // Discord rings the dot in the card's own colour so it
                        // reads as punched out of the avatar rather than on it.
                        .border(2.5.dp, cardColor, CircleShape)
                        .padding(2.5.dp)
                        .clip(CircleShape)
                        .background(
                            when (status) {
                                DiscordPresenceStatus.IDLE -> MaterialTheme.colorScheme.tertiary
                                DiscordPresenceStatus.DND -> MaterialTheme.colorScheme.error
                                DiscordPresenceStatus.ONLINE -> MaterialTheme.colorScheme.primary
                            },
                        ),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (connected) name.ifEmpty { stringResource(R.string.connected) }
                else stringResource(R.string.not_connected),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = when {
                    username.isNotEmpty() -> "@$username"
                    connected -> stringResource(R.string.discord_account)
                    else -> stringResource(R.string.tap_to_sign_in_discord)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!connected) {
            Spacer(Modifier.width(8.dp))
            Chevron()
        }
    }
}

/** A one-off explanation with its own dismiss, shaped like a settings group. */
@Composable
private fun NoticeCard(text: String, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Same 26dp gap SettingsGroup leaves above itself, so this sits in
            // the rhythm of the groups either side of it.
            .padding(start = GROUP_INSET, end = GROUP_INSET, top = 26.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(GroupShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.got_it),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.End)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
            )
        }
    }
}

/**
 * The card as Discord will draw it: heading, sleeve, three lines of text, a
 * countdown, and up to two buttons.
 *
 * Deliberately not built from our own row primitives — this is a picture of
 * another app's UI, and the only way it does its job is by looking like one.
 * The buttons work, so it doubles as a way to check the links land.
 */
@Composable
private fun RichPresencePreview(
    song: Song?,
    positionMs: Long,
    durationMs: Long,
    heading: String,
    verb: String,
    useDetails: Boolean,
    button1Text: String,
    button1Visible: Boolean,
    button2Text: String,
    button2Visible: Boolean,
) {
    val context = LocalContext.current
    val title = song?.title ?: stringResource(R.string.widget_preview_title)
    val artist = song?.artist ?: stringResource(R.string.widget_preview_artist)

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = "$verb $heading".uppercase(Locale.ROOT),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.W700,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.Top) {
            val artShape = RoundedCornerShape(6.dp)
            if (song?.thumbnailUrl != null) {
                AsyncImage(
                    model = song.artworkAt(CARD_ART_PX),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(84.dp).clip(artShape).thumbnailBorder(artShape),
                )
            } else {
                Box(
                    Modifier
                        .size(84.dp)
                        .clip(artShape)
                        .background(MaterialTheme.colorScheme.outline),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                // Discord bolds whichever line `status_display_type` names, and
                // that is the one it also repeats next to the user's name in a
                // member list — so which of these is emphasised is the whole
                // point of the "Lead with the song" switch.
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                    fontWeight = if (useDetails) FontWeight.W700 else FontWeight.W400,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    fontWeight = if (useDetails) FontWeight.W400 else FontWeight.W700,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                song?.albumName?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                ProgressLine(positionMs = positionMs, durationMs = durationMs)
            }
        }

        val resolved = { text: String, fallback: String ->
            val chosen = text.ifEmpty { fallback }
            if (song != null) DiscordRPC.resolveVariables(chosen, song) else chosen
        }
        if (button1Visible) {
            Spacer(Modifier.height(12.dp))
            PresenceButton(
                label = resolved(button1Text, DiscordRPC.DEFAULT_BUTTON_1),
                enabled = song != null,
                onClick = {
                    song?.let {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, DiscordRPC.watchUrl(it).toUri()),
                        )
                    }
                },
            )
        }
        if (button2Visible) {
            Spacer(Modifier.height(8.dp))
            PresenceButton(
                label = resolved(button2Text, DiscordRPC.DEFAULT_BUTTON_2),
                enabled = true,
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, DiscordRPC.PROJECT_URL.toUri()),
                    )
                },
            )
        }
    }
}

/** Discord's flat, full-width secondary button. */
@Composable
private fun PresenceButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.outline.copy(alpha = if (enabled) 0.9f else 0.4f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (enabled) 1f else 0.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Elapsed and total, either side of a plain track.
 *
 * Deliberately plain: Discord's own bar is a flat 4px rule, and the wavy
 * indicator this is otherwise shaped like would be this app's idiom leaking
 * into a picture of somewhere else.
 */
@Composable
private fun ProgressLine(positionMs: Long, durationMs: Long) {
    val fraction = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.outline),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatClock(positionMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatClock(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(Locale.ROOT, total / 60, total % 60)
}

/**
 * The Discord screen's dialogs, rendered by the activity so their scrim covers
 * the tab bar and mini player like every other alert in the app.
 */
@Composable
fun DiscordDialogHost(
    which: DiscordDialog,
    hazeState: HazeState,
    onDismiss: () -> Unit,
) {
    val rejectedToken = stringResource(R.string.discord_token_rejected)
    when (which) {
        DiscordDialog.TOKEN -> {
            val scope = rememberCoroutineScope()
            var input by remember { mutableStateOf("") }
            var error by remember { mutableStateOf<String?>(null) }
            var checking by remember { mutableStateOf(false) }
            DiscordTokenAlert(
                hazeState = hazeState,
                tokenInput = input,
                onTokenInputChange = { input = it },
                error = error,
                loading = checking,
                // Verified before it is saved, because a token that doesn't work
                // fails silently later: the presence simply never appears, with
                // nothing on this screen to say why.
                onSave = {
                    checking = true
                    error = null
                    scope.launch {
                        val trimmed = input.trim()
                        val info = withContext(Dispatchers.IO) {
                            KizzyRPC.getUserInfo(
                                trimmed,
                                SuperProperties.userAgent,
                                SuperProperties.superPropertiesBase64,
                            )
                        }
                        info.onSuccess {
                            AppSettings.setDiscordAccount(it.username, it.name, it.avatar)
                            AppSettings.setDiscordToken(trimmed)
                            onDismiss()
                        }.onFailure {
                            error = rejectedToken
                            checking = false
                        }
                    }
                },
                onDismiss = onDismiss,
            )
        }

        DiscordDialog.STATUS -> {
            val current by AppSettings.discordStatus.collectAsStateWithLifecycle()
            val copy = localizedStatusCopy()
            ChoiceAlert(
                hazeState = hazeState,
                title = stringResource(R.string.status),
                message = stringResource(R.string.status_dialog_message),
                options = DiscordPresenceStatus.entries,
                selected = statusOf(current),
                label = { copy.getValue(it).first },
                detail = { copy.getValue(it).second },
                onSelect = {
                    AppSettings.setDiscordStatus(it.value)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }

        DiscordDialog.ACTIVITY_TYPE -> {
            val current by AppSettings.discordActivityType.collectAsStateWithLifecycle()
            // Read out here: `detail` is a plain lambda, so the composable
            // lookup can't happen inside it.
            val name = AppSettings.discordActivityName.value.ifEmpty { appName() }
            val copy = localizedActivityCopy()
            ChoiceAlert(
                hazeState = hazeState,
                title = stringResource(R.string.activity),
                message = stringResource(R.string.activity_dialog_message),
                options = DiscordActivityKind.entries,
                selected = kindOf(current),
                label = { copy.getValue(it).first },
                detail = { "\"${copy.getValue(it).second} $name\"" },
                onSelect = {
                    AppSettings.setDiscordActivityType(it.value)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }

        DiscordDialog.ACTIVITY_NAME -> {
            val current by AppSettings.discordActivityName.collectAsStateWithLifecycle()
            var input by remember { mutableStateOf(current) }
            TextValueAlert(
                hazeState = hazeState,
                title = stringResource(R.string.name),
                message = stringResource(R.string.activity_name_message, appName()),
                placeholder = appName(),
                value = input,
                onValueChange = { input = it },
                onSave = {
                    AppSettings.setDiscordActivityName(input.trim())
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }

        DiscordDialog.BUTTON_1, DiscordDialog.BUTTON_2 -> {
            val first = which == DiscordDialog.BUTTON_1
            val flow = if (first) AppSettings.discordButton1Text else AppSettings.discordButton2Text
            val current by flow.collectAsStateWithLifecycle()
            var input by remember { mutableStateOf(current) }
            TextValueAlert(
                hazeState = hazeState,
                title = if (first) stringResource(R.string.first_button)
                else stringResource(R.string.second_button),
                message = stringResource(R.string.discord_button_variables),
                placeholder = if (first) DiscordRPC.DEFAULT_BUTTON_1 else DiscordRPC.DEFAULT_BUTTON_2,
                value = input,
                onValueChange = { input = it },
                onSave = {
                    if (first) {
                        AppSettings.setDiscordButton1Text(input.trim())
                    } else {
                        AppSettings.setDiscordButton2Text(input.trim())
                    }
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }
    }
}

/** The app's own label, minus the dev flavor's suffix. Matches [DiscordRPC]. */
@Composable
private fun appName(): String =
    LocalContext.current.getString(R.string.app_name).removeSuffix(" Dev")
