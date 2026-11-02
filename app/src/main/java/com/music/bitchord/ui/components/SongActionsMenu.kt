package com.music.bitchord.ui.components

import com.music.bitchord.ui.icons.BrokenHeart
import com.music.bitchord.ui.icons.SfIcons
import com.music.bitchord.ui.components.trackArtSize
import androidx.compose.ui.layout.ContentScale
import com.music.bitchord.R

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.music.bitchord.ui.player.ThinSlider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.webdav.WebDavUploads
import com.music.bitchord.download.DownloadState
import com.music.bitchord.download.Downloads
import com.music.bitchord.playback.SleepTimer
import com.music.bitchord.ui.components.thumbnailBorder
import com.music.bitchord.ui.theme.ArtworkPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Long-press menu for a track, in the shape music apps normally use.
 *
 * The account actions lead — rating, playlists, library — because they are
 * what the menu is opened for; the queue and navigation rows below it were
 * always the fallback for "I meant to do something with this song".
 *
 * Everything that writes to the account is hidden outright when [signedIn] is
 * false rather than shown and refused. A track playing from a local file or a
 * finished download (`song.localUri != null`) carries no YouTube identity to
 * rate, fetch again or share, so it gets the offline player's rows instead:
 * Like and Add to playlist against the device's own playlists (see
 * [com.music.bitchord.data.OfflinePlaylists]), signed in or not.
 *
 * [showSleepTimer] and [onShare] are the player's extras: a sleep timer isn't a
 * property of some row in a list, so it only appears where it means something.
 *
 * [onDownload] is only the *start* of a download — cancelling one and deleting
 * a saved file are answered here, because neither needs anything the caller
 * has. Starting one might: below API 29 it needs a storage permission that only
 * an Activity can ask for.
 *
 * Only the rows, in the iOS panel's own colours: the frame is the host's —
 * [IosMenuPopover] hung off the ⋮ or the player's ⋯ that opened it, or
 * [HeldContextMenu] under the row a hold lifted.
 */
@Composable
fun SongActionsMenu(
    song: Song,
    signedIn: Boolean,
    likeStatus: LikeStatus,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onStartRadio: () -> Unit,
    onDownload: () -> Unit,
    /**
     * Copies this track to the WebDAV server. Null hides the row — the caller
     * passes one only for a readable device file while a server is
     * configured. Uploading while another upload of the same track runs just
     * re-runs it; the conflict dialog keeps that idempotent.
     */
    onUploadToWebDav: (() -> Unit)? = null,
    onToggleLike: () -> Unit,
    onToggleDislike: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    modifier: Modifier = Modifier,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    /**
     * Takes this track out of the song cache. Null hides the row — the caller
     * passes one only while the cache actually holds some of it.
     */
    onRemoveFromCache: (() -> Unit)? = null,
    /**
     * Removes this track from the account's listening history. Null hides the
     * row — the History screen passes one only when the menu is opened there.
     */
    onRemoveFromHistory: (() -> Unit)? = null,
    showSleepTimer: Boolean = false,
    /**
     * Sends the playing track back to YouTube's own upload, and keeps it there.
     * Offered for any player item that has one behind it — a substituted copy
     * can be the wrong recording from the first second, not only after a
     * visible upgrade. See
     * [com.music.bitchord.playback.hasYouTubeOriginal].
     */
    onRollbackToOriginal: (() -> Unit)? = null,
    /**
     * The way back from a revert: available only for a player item the listener
     * has pinned to YouTube's own upload, where nothing will go looking for a
     * better copy again until they ask.
     * See [com.music.bitchord.playback.OriginalVersion].
     */
    onUpgradeQuality: (() -> Unit)? = null,
    /** Keeps the upgrade row visible but untappable while its lookup is running. */
    upgradeQualityInProgress: Boolean = false,
    /**
     * Switches the playing track between its video and audio-only cuts. Null
     * hides the row — offered for the playing track only. See
     * [com.music.bitchord.playback.PlaybackService.smoothSwapCurrentTrackVersion].
     */
    onToggleAudioVersion: (() -> Unit)? = null,
    /** Which cut is playing now, so the row can offer the other one. */
    isAudioVersion: Boolean = false,
    /**
     * A switch already looked for the other cut and found none: the row says
     * so, greyed out, rather than offering the same dead end again.
     */
    audioVersionMissing: Boolean = false,
    onShare: (() -> Unit)? = null,
    /**
     * Copies what the app logged while starting this track. Null everywhere
     * except the player, where "this track" means something.
     */
    onCopyLog: (() -> Unit)? = null,
    /** Opens the timing control offered only by the main player's menu. */
    onLyricsOffset: (() -> Unit)? = null,
    /**
     * True while a lookup for this track's album/artist ids is still in
     * flight, so it isn't yet known whether "Open album" and "Open artist"
     * belong on this menu at all. Only the player ever opens a menu before
     * it knows; everywhere else this is simply false, and the two rows behave
     * as before — present when the id is there, absent when it never was.
     */
    resolvingLinks: Boolean = false,
) {
    var showingSongInfo by remember(song.videoId) { mutableStateOf(false) }
    var pickingSleepTimer by remember { mutableStateOf(false) }
    // A menu has no pages to swap, so Song Info opens as a dialog.
    if (showingSongInfo) SongInfoDialog(song) { showingSongInfo = false }
    // Neutral rather than the sleeve's colours: the panel floats over the page
    // or the player, and an accent pitched against the artwork's tint is not
    // guaranteed to read against the panel.
    val palette = neutralMenuPalette()
    val liked = likeStatus == LikeStatus.LIKE
    val disliked = likeStatus == LikeStatus.DISLIKE
    // A local file or a finished download has no YouTube identity behind it to
    // rate, save, queue into a playlist, fetch again, or share a link for.
    val isOffline = song.localUri != null

    val rows: @Composable ColumnScope.() -> Unit = {
        // Groups are drawn as they turn out to have something in them, and the
        // separator goes *before* a group, never after — so a menu whose last
        // group is empty doesn't end on a stray line.
        var drawnAny = false
        fun startsGroup(): Boolean = drawnAny.also { drawnAny = true }
        val canRate = isOffline || signedIn
        val shareAction = onShare?.takeIf { !isOffline }

        // Leads with the actions it is opened for most, as tiles: the rating
        // — kept up after a tap, so the new state shows in place — and Share.
        if (canRate || shareAction != null) {
            if (startsGroup()) IosMenuSeparator()
            IosMenuQuickActions {
                if (canRate) {
                    IosMenuQuickAction(
                        icon = if (liked) SfIcons.StarFill else SfIcons.Star,
                        label = stringResource(R.string.like),
                        tint = if (liked) palette.accent else null,
                        onClick = onToggleLike,
                    )
                }
                // No dislike for a device file: there is no recommendation
                // feed on the device for one to steer.
                if (signedIn && !isOffline) {
                    IosMenuQuickAction(
                        icon = if (disliked) BrokenHeart.Fill else BrokenHeart.Outline,
                        label = stringResource(R.string.dislike),
                        tint = if (disliked) palette.accent else null,
                        onClick = onToggleDislike,
                    )
                }
                shareAction?.let {
                    IosMenuQuickAction(icon = SfIcons.Share, label = stringResource(R.string.share), onClick = it)
                }
            }
        }

        // Leads the list whenever it's available: which recording is playing
        // is the one question that has to be answered before any of the rows
        // below mean anything — there is no point rating, queueing or
        // downloading the wrong version of a song.
        //
        // The two are never both present — one is offered for a track playing
        // a substituted copy, the other for a track held on YouTube's own —
        // and between them they are the whole of the choice, which is why they
        // sit in the same group.
        if (onRollbackToOriginal != null || onUpgradeQuality != null || onToggleAudioVersion != null) {
            if (startsGroup()) IosMenuSeparator()
            (onRollbackToOriginal ?: onUpgradeQuality)?.let {
                ActionRow(
                    icon = if (onRollbackToOriginal != null) SfIcons.Revert else SfIcons.Upgrade,
                    label = stringResource(
                        if (onRollbackToOriginal != null) {
                            R.string.revert_to_original
                        } else {
                            R.string.upgrade_quality
                        },
                    ),
                    accent = palette.accent,
                    enabled = onRollbackToOriginal != null || !upgradeQualityInProgress,
                    onClick = it,
                )
            }
            // Which recording, not which quality — a different question from
            // the row above, so it sits right under it rather than merged
            // into it.
            onToggleAudioVersion?.let {
                ActionRow(
                    icon = if (isAudioVersion) SfIcons.Video else SfIcons.MusicNote,
                    label = stringResource(
                        when {
                            audioVersionMissing && isAudioVersion -> R.string.no_video_version
                            audioVersionMissing -> R.string.no_audio_version
                            isAudioVersion -> R.string.convert_to_video
                            else -> R.string.convert_to_audio
                        },
                    ),
                    accent = palette.accent,
                    enabled = !audioVersionMissing,
                    onClick = it,
                )
            }
        }

        // The account's rows, or for a local file or download the offline
        // player's own pair: a heart that files the track under this device's
        // Liked playlist, and the device's playlists. [likeStatus] is that
        // playlist's answer there, not YouTube's, and neither needs an account.
        if (canRate) {
            if (startsGroup()) IosMenuSeparator()
            ActionRow(
                icon = SfIcons.AddToPlaylist,
                label = stringResource(R.string.add_to_playlist),
                accent = palette.accent,
                onClick = onAddToPlaylist,
            )
            onRemoveFromPlaylist?.let {
                ActionRow(
                    icon = SfIcons.RemoveFromPlaylist,
                    label = stringResource(R.string.remove_from_playlist),
                    accent = palette.accent,
                    onClick = it,
                )
            }
        }

        // Where the track goes next, and what is kept of it. The file rows
        // ride in this group rather than one of their own because for a
        // device file they can all be absent, and a group may not be empty.
        if (startsGroup()) IosMenuSeparator()
        ActionRow(
            icon = SfIcons.Radio,
            label = stringResource(R.string.start_radio),
            accent = palette.accent,
            onClick = onStartRadio,
        )
        ActionRow(
            icon = SfIcons.PlayNext,
            label = stringResource(R.string.play_next),
            accent = palette.accent,
            onClick = onPlayNext,
        )
        ActionRow(
            icon = SfIcons.PlayLast,
            label = stringResource(R.string.add_to_queue),
            accent = palette.accent,
            onClick = onAddToQueue,
        )
        DownloadRow(song, palette, isOffline, onDownload)
        onRemoveFromCache?.let {
            ActionRow(
                icon = SfIcons.Trash,
                label = stringResource(R.string.remove_from_cache),
                accent = palette.accent,
                onClick = it,
            )
        }
        WebDavUploadRow(song, palette, isOffline, onUploadToWebDav)
        onRemoveFromHistory?.let {
            ActionRow(
                icon = SfIcons.History,
                label = stringResource(R.string.remove_from_history),
                accent = palette.accent,
                onClick = it,
            )
        }

        if (startsGroup()) IosMenuSeparator()
        // Names where each one leads under it, as a menu's "Go to" does.
        when (val id = song.albumId) {
            null -> if (resolvingLinks) {
                LoadingActionRow(SfIcons.Album, stringResource(R.string.open_album), palette)
            }
            else -> ActionRow(
                SfIcons.Album,
                stringResource(R.string.open_album),
                accent = palette.accent,
                description = song.albumName,
            ) { onOpenAlbum(id) }
        }
        when (val id = song.artistId) {
            null -> if (resolvingLinks) {
                LoadingActionRow(SfIcons.Artist, stringResource(R.string.open_artist), palette)
            }
            else -> ActionRow(
                SfIcons.Artist,
                stringResource(R.string.open_artist),
                accent = palette.accent,
                description = song.artist.takeIf { it.isNotBlank() },
            ) { onOpenArtist(id) }
        }
        ActionRow(SfIcons.Info, stringResource(R.string.song_info), accent = palette.accent) {
            showingSongInfo = true
        }

        // The player's own: about the track playing right now rather than
        // about the song as a thing in a library.
        if (showSleepTimer || onLyricsOffset != null || onCopyLog != null) {
            if (startsGroup()) IosMenuSeparator()
            if (showSleepTimer) {
                ActionRow(
                    icon = SfIcons.SleepTimer,
                    label = stringResource(R.string.sleep_timer),
                    value = sleepTimerStatus(),
                    accent = palette.accent,
                ) { pickingSleepTimer = true }
            }
            // Local files and completed downloads use the same player lyric
            // clock (including embedded synced lyrics), so they need this
            // control just as much as streamed songs do.
            onLyricsOffset?.let {
                ActionRow(
                    icon = SfIcons.Sliders,
                    label = stringResource(R.string.lyrics_offset),
                    accent = palette.accent,
                    onClick = it,
                )
            }
            // Last: the one row here nobody reaches for by accident.
            onCopyLog?.let {
                ActionRow(SfIcons.Bug, stringResource(R.string.copy_log), accent = palette.accent, onClick = it)
            }
        }
    }

    CompositionLocalProvider(LocalActionRowStyle provides ActionRowStyle.Menu) {
        Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            // The player's menu has the sleep timer, and swaps its rows for
            // the picker in place.
            if (pickingSleepTimer) {
                SleepTimerPicker(palette = palette, onBack = { pickingSleepTimer = false })
            } else {
                rows()
            }
        }
    }
}

/**
 * Row density and layout, set by whichever surface the rows are drawn on: the
 * playlist picker's sheet, or a menu panel.
 */
internal enum class ActionRowStyle { Sheet, Menu }

internal val LocalActionRowStyle = staticCompositionLocalOf { ActionRowStyle.Sheet }

/**
 * The popup's colours: the iOS menu panel's own, so the menu reads the same in
 * light and dark whatever the artwork is. Shaped as an [ArtworkPalette] so every
 * row below takes it exactly as it takes a sleeve's.
 */
@Composable
private fun neutralMenuPalette(): ArtworkPalette {
    val accent = MaterialTheme.colorScheme.primary
    val colors = iosMenuColors()
    return remember(accent, colors) {
        ArtworkPalette(
            background = colors.fill,
            wash = colors.fill,
            elevated = colors.fill,
            accent = accent,
            onBackground = colors.label,
            onBackgroundVariant = colors.secondaryLabel,
            divider = colors.separator,
        )
    }
}

/**
 * One row carrying the whole life of a download: start it, watch it, cancel it,
 * and delete what it produced.
 *
 * A row rather than a screen because that is the size of the decision. The
 * files land in the device's own Music folder, which already has a manager
 * — the Files app — and building a second one inside this app would be
 * duplicating it in a worse place. What this app uniquely knows is which *song*
 * a file belongs to, and that is exactly what this row says.
 *
 * The state comes straight from [Downloads] rather than through the caller: it
 * changes while the menu is open, and threading a flow through the menu's
 * signature would buy nothing over reading it where it's drawn — the same
 * arrangement the sleep timer row already uses.
 */
@Composable
private fun DownloadRow(song: Song, palette: ArtworkPalette, isOffline: Boolean, onDownload: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val active by Downloads.active.collectAsStateWithLifecycle()
    val saved by Downloads.saved.collectAsStateWithLifecycle()

    // The record is a claim about a folder the user manages themselves, so it
    // is checked against the disk rather than trusted — re-checked whenever the
    // record for this track changes, which is what makes the row settle onto
    // "Saved" the moment a download finishes.
    //
    // Seeded with the record rather than with null so that moment isn't a
    // flicker: a finished download clears the running state and writes the
    // record in the same breath, and starting pessimistic would show "Download"
    // again for as long as the check off the main thread takes.
    val recorded = saved[song.videoId]
    val file by produceState(recorded?.let(Uri::parse), song.videoId, recorded) {
        value = Downloads.savedUri(context, song.videoId)
    }

    // A failure is worth stating once. Leaving it set would have the row still
    // reporting last week's dead connection the next time the menu is opened.
    DisposableEffect(song.videoId) {
        onDispose { Downloads.dismissFailure(song.videoId) }
    }

    when (val state = active[song.videoId]) {
        is DownloadState.Queued -> ActionRow(
            icon = SfIcons.Download,
            label = stringResource(R.string.queued),
            value = stringResource(R.string.cancel),
            accent = palette.accent,
        ) { Downloads.cancel(song.videoId) }

        is DownloadState.Running -> ActionRow(
            icon = SfIcons.Download,
            label = stringResource(R.string.download_notification_title),
            // Indeterminate until the first response names a length; a
            // stuck "0%" reads as broken where a bare label reads as starting.
            value = if (state.fraction > 0f) "${(state.fraction * 100).toInt()}%" else null,
            tint = palette.accent,
            accent = palette.accent,
        ) { Downloads.cancel(song.videoId) }

        is DownloadState.Failed -> ActionRow(
            icon = SfIcons.ErrorOutline,
            label = state.reason,
            value = stringResource(R.string.retry),
            // Not the artwork's colour: a failure has to stay legible as a
            // failure whatever the sleeve happens to be tinted.
            tint = MaterialTheme.colorScheme.error,
            accent = MaterialTheme.colorScheme.error,
            onClick = onDownload,
        )

        null -> if (file != null) {
            ActionRow(
                icon = SfIcons.Downloaded,
                label = stringResource(R.string.saved_to_downloads),
                value = stringResource(R.string.delete),
                tint = palette.accent,
                accent = palette.accent,
            ) { scope.launch { Downloads.delete(context, song.videoId) } }
        } else if (!isOffline) {
            ActionRow(
                icon = SfIcons.Download,
                label = stringResource(R.string.download),
                accent = palette.accent,
                onClick = onDownload,
            )
        }
    }
}

/**
 * Sends a device file to the WebDAV server. Sits beside the download row
 * because it is the same idea in the other direction — and like that row it
 * reads its state where it is drawn rather than threading it through the
 * menu's signature.
 *
 * Shown only for readable local files. A track already on the server, or a
 * stream with no bytes on this device, has nothing to send up.
 */
@Composable
private fun WebDavUploadRow(
    song: Song,
    palette: ArtworkPalette,
    isOffline: Boolean,
    onUpload: (() -> Unit)?,
) {
    onUpload ?: return
    if (!isOffline || !WebDavUploads.isUploadable(song)) return
    val active by WebDavUploads.active.collectAsStateWithLifecycle()
    when (val state = active[song.videoId]) {
        is WebDavUploads.TrackState.Running -> ActionRow(
            icon = SfIcons.Upload,
            label = stringResource(R.string.upload_to_webdav),
            value = "${(state.fraction * 100).toInt()}%",
            tint = palette.accent,
            accent = palette.accent,
            // In flight already; there is no cancel, so the row only reports.
            onClick = {},
        )
        is WebDavUploads.TrackState.Failed -> ActionRow(
            icon = SfIcons.Upload,
            label = state.reason,
            value = stringResource(R.string.try_again),
            tint = MaterialTheme.colorScheme.error,
            accent = MaterialTheme.colorScheme.error,
            onClick = onUpload,
        )
        else -> ActionRow(
            icon = SfIcons.Upload,
            label = stringResource(R.string.upload_to_webdav),
            accent = palette.accent,
            onClick = onUpload,
        )
    }
}

/** End of track or a duration, plus a way out once one is running. */
@Composable
private fun SleepTimerPicker(palette: ArtworkPalette, onBack: () -> Unit) {
    val chosen by SleepTimer.minutes.collectAsStateWithLifecycle()
    val afterTrack by SleepTimer.afterTrack.collectAsStateWithLifecycle()
    val countdown = sleepTimerCountdown()
    // A running timer no preset matches is a custom one: the row ticks and names it.
    val custom = chosen?.takeIf { it !in SleepTimer.PRESETS }
    var pickingCustom by remember { mutableStateOf(false) }

    if (pickingCustom) {
        CustomSleepTimerPage(
            palette = palette,
            initialMinutes = custom ?: 90,
            onBack = { pickingCustom = false },
            onStart = { minutes ->
                SleepTimer.start(minutes)
                onBack()
            },
        )
        return
    }

    Column(Modifier.fillMaxWidth()) {
        SleepTimerHeader(
            title = stringResource(R.string.sleep_timer),
            subtitle = when {
                countdown != null -> stringResource(R.string.pause_countdown, countdown)
                afterTrack -> stringResource(R.string.pause_after_song)
                else -> stringResource(R.string.pause_after_a_while)
            },
            onBack = onBack,
        )
        HorizontalDivider(thickness = 0.5.dp, color = palette.divider)

        // Finishing the song is the one people reach for at the end of a
        // listen, so it leads rather than sitting under the durations.
        SleepOption(
            label = stringResource(R.string.after_this_song),
            selected = afterTrack,
            accent = palette.accent,
        ) {
            SleepTimer.startAfterTrack()
            onBack()
        }
        SleepTimer.PRESETS.forEach { minutes ->
            SleepOption(
                label = if (minutes == 60) {
                    stringResource(R.string.one_hour)
                } else {
                    pluralStringResource(R.plurals.minute_count, minutes, minutes)
                },
                selected = minutes == chosen,
                accent = palette.accent,
            ) {
                SleepTimer.start(minutes)
                onBack()
            }
        }
        SleepOption(
            label = custom?.let {
                stringResource(R.string.sleep_timer_custom) + " · " + sleepDuration(it)
            } ?: stringResource(R.string.sleep_timer_custom),
            selected = custom != null,
            accent = palette.accent,
        ) { pickingCustom = true }
        if (chosen != null || afterTrack) {
            ActionRow(SfIcons.Close, stringResource(R.string.turn_off_timer), accent = palette.accent) {
                SleepTimer.cancel()
                onBack()
            }
        }
    }
}

/**
 * Back arrow, title and a line under it — the top of both sleep timer pages.
 * The title at the size of a menu row's label, as the heading of a menu's own
 * sub-page, not a page's.
 */
@Composable
private fun SleepTimerHeader(title: String, subtitle: String, onBack: () -> Unit) {
    val colors = iosMenuColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 22.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                SfIcons.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = colors.label,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 17.sp,
                    lineHeight = 21.sp,
                    letterSpacing = (-0.3).sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = colors.label,
            )
            Text(
                text = subtitle,
                fontSize = 14.sp,
                lineHeight = 17.sp,
                color = colors.secondaryLabel,
            )
        }
    }
}

/**
 * Any length up to twelve hours, set the way the player's lyrics offset is:
 * a large readout over the player's own thin slider, with − and + for a
 * single step either way. A page of this menu rather than an alert on top
 * of it, so it stays on the panel like the presets it sits with.
 */
@Composable
private fun CustomSleepTimerPage(
    palette: ArtworkPalette,
    initialMinutes: Int,
    onBack: () -> Unit,
    onStart: (Int) -> Unit,
) {
    var minutes by remember { mutableIntStateOf(initialMinutes) }
    val foreground = MaterialTheme.colorScheme.onBackground

    Column(Modifier.fillMaxWidth()) {
        SleepTimerHeader(
            title = stringResource(R.string.sleep_timer_custom),
            subtitle = stringResource(R.string.pause_after_a_while),
            onBack = onBack,
        )
        HorizontalDivider(thickness = 0.5.dp, color = palette.divider)

        Text(
            text = sleepDuration(minutes),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
            color = foreground,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 8.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StepButton(
                label = "−",
                contentDescription = stringResource(R.string.less),
                enabled = minutes > SleepTimer.STEP_MINUTES,
            ) { minutes -= SleepTimer.STEP_MINUTES }
            ThinSlider(
                value = SleepTimer.fractionOf(minutes),
                onValueChange = { minutes = SleepTimer.minutesAt(it) },
                idleHeight = 6.dp,
                activeHeight = 10.dp,
                activeColor = foreground.copy(alpha = 0.92f),
                inactiveColor = foreground.copy(alpha = 0.26f),
                modifier = Modifier.weight(1f),
            )
            StepButton(
                label = "+",
                contentDescription = stringResource(R.string.more),
                enabled = minutes < SleepTimer.MAX_MINUTES,
            ) { minutes += SleepTimer.STEP_MINUTES }
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(thickness = 0.5.dp, color = palette.divider)
        ActionRow(SfIcons.SleepTimer, stringResource(R.string.start_sleep_timer), accent = palette.accent) {
            onStart(minutes)
        }
    }
}

@Composable
private fun StepButton(
    label: String,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.semantics { this.contentDescription = contentDescription },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (enabled) 0.85f else 0.25f),
        )
    }
}

/** "45 min", or "1h 25m" from an hour up. */
@Composable
private fun sleepDuration(minutes: Int): String = if (minutes >= 60) {
    stringResource(R.string.hours_minutes_short, minutes / 60, minutes % 60)
} else {
    stringResource(R.string.minutes_short, minutes)
}

@Composable
private fun SleepOption(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    // The tick leads, in the glyph column, as a menu's picker marks its choice.
    IosMenuRow(
        label = label,
        icon = if (selected) SfIcons.Check else null,
        onClick = onClick,
        iconTint = accent,
    )
}

/** What the sleep timer row shows on the right, or null when none is armed. */
@Composable
private fun sleepTimerStatus(): String? {
    val afterTrack by SleepTimer.afterTrack.collectAsStateWithLifecycle()
    return sleepTimerCountdown() ?: stringResource(R.string.after_this_song).takeIf { afterTrack }
}

/** Live "m:ss" until the sleep timer fires, or null when none is running. */
@Composable
private fun sleepTimerCountdown(): String? {
    val deadline by SleepTimer.deadline.collectAsStateWithLifecycle()
    val remaining by produceState<Long?>(initialValue = SleepTimer.remainingMs(), deadline) {
        while (deadline != null) {
            value = SleepTimer.remainingMs()
            delay(1_000)
        }
        value = null
    }
    return remaining?.let {
        val seconds = it / 1000
        "%d:%02d".format(Locale.ROOT, seconds / 60, seconds % 60)
    }
}

/**
 * One line of an actions list: an iOS menu row on a menu panel, a full-width
 * row on the playlist picker's sheet — so the two read as the same control
 * rather than as two lists that happen to look alike.
 *
 * [tint] is for rows whose icon carries state — a filled heart on a liked
 * track — and is otherwise the ordinary foreground. [accent] colours the
 * trailing [value], and defaults to the app's own red; a surface with an
 * accent of its own passes that instead, so the row belongs to what it is
 * drawn on.
 */
@Composable
internal fun ActionRow(
    icon: ImageVector,
    label: String,
    value: String? = null,
    tint: Color? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    /** A dimmer second line — only the popup draws it, as iOS menus do. */
    description: String? = null,
    onClick: () -> Unit,
) {
    if (LocalActionRowStyle.current == ActionRowStyle.Menu) {
        IosMenuRow(
            label = label,
            icon = icon,
            onClick = onClick,
            description = description,
            value = value,
            iconTint = tint,
            valueColor = accent,
            enabled = enabled,
        )
        return
    }
    val foreground = MaterialTheme.colorScheme.onBackground
    val alpha = if (enabled) 1f else 0.4f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = (tint ?: foreground).copy(alpha = alpha),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(18.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = foreground.copy(alpha = alpha),
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = accent.copy(alpha = alpha),
                maxLines = 1,
            )
        }
    }
}

/**
 * Stands in for [ActionRow] while whether it belongs on the menu at all is
 * still unknown — "Open album" or "Open artist" before the lookup for their
 * ids has come back. A spinner rather than the row simply being missing, so
 * it doesn't read as decided against until it actually is.
 */
@Composable
private fun LoadingActionRow(icon: ImageVector, label: String, palette: ArtworkPalette) {
    IosMenuRow(label = label, icon = icon, onClick = null, enabled = false) {
        CircularProgressIndicator(
            color = palette.onBackgroundVariant,
            strokeWidth = 2.dp,
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * The track the playlist picker is adding, drawn at its head — the same track
 * the menu it was opened from was about.
 *
 * [subtitleColor] is for ground other than the flat theme background, where
 * the usual dim grey may not hold.
 */
@Composable
internal fun SheetTrackHeader(
    song: Song,
    modifier: Modifier = Modifier,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = song.artworkAt(ROW_ART_PX),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .trackArtSize(52.dp, song.isVideo)
                .clip(RoundedCornerShape(8.dp))
                .thumbnailBorder(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            ExplicitSongTitle(
                song = song,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = subtitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The heading over a sheet's second half — "Add to playlist". */
@Composable
internal fun SheetHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 4.dp),
    )
}
