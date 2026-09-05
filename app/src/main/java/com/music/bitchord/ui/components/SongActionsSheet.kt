package com.music.bitchord.ui.components

import com.music.bitchord.R

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.PlaylistRemove
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbDownOffAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.download.DownloadState
import com.music.bitchord.download.Downloads
import com.music.bitchord.playback.SleepTimer
import com.music.bitchord.ui.components.thumbnailBorder
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.ui.theme.ArtworkPalette
import com.music.bitchord.ui.theme.rememberArtworkPalette
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
 * false rather than shown and refused. The same goes for a track that is
 * playing from a local file or a finished download (`song.localUri != null`):
 * rating, playlists, downloading it again and sharing all assume a YouTube
 * identity the file doesn't carry, so those rows drop out regardless of
 * [signedIn].
 *
 * [showSleepTimer] and [onShare] are the player's extras: a sleep timer isn't a
 * property of some row in a list, so it only appears where it means something.
 *
 * [onDownload] is only the *start* of a download — cancelling one and deleting
 * a saved file are answered here, because neither needs anything the caller
 * has. Starting one might: below API 29 it needs a storage permission that only
 * an Activity can ask for.
 *
 * The sheet is painted in the track's own colours, the same way its album page
 * is — it is opened *from* that artwork, usually with it still on screen behind
 * the scrim, and a slab of flat grey in front of a coloured page reads as
 * something borrowed from another app. The host supplies no container colour
 * and no drag handle; both are drawn here, over the tint.
 */
@Composable
fun SongActionsSheet(
    song: Song,
    signedIn: Boolean,
    likeStatus: LikeStatus,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onStartRadio: () -> Unit,
    onDownload: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleDislike: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    modifier: Modifier = Modifier,
    onRemoveFromPlaylist: (() -> Unit)? = null,
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
    onShare: (() -> Unit)? = null,
    /**
     * Copies what the app logged while starting this track. Null everywhere
     * except the player, where "this track" means something.
     */
    onCopyLog: (() -> Unit)? = null,
    /**
     * True while a lookup for this track's album/artist ids is still in
     * flight, so it isn't yet known whether "Open album" and "Open artist"
     * belong on this sheet at all. Only the player ever opens a sheet before
     * it knows; everywhere else this is simply false, and the two rows behave
     * as before — present when the id is there, absent when it never was.
     */
    resolvingLinks: Boolean = false,
) {
    var pickingSleepTimer by remember { mutableStateOf(false) }
    // Read from the thumbnail the row that opened this sheet was already
    // showing, not a larger copy of it: the tint is a blur and a handful of
    // swatches, neither of which a bigger image improves, and going back for
    // one is what had the sheet opening grey and colouring in afterwards.
    val palette = rememberArtworkPalette(song.thumbnailUrl, artPx = ROW_ART_PX)
    val liked = likeStatus == LikeStatus.LIKE
    val disliked = likeStatus == LikeStatus.DISLIKE
    // A local file or a finished download has no YouTube identity behind it to
    // rate, save, queue into a playlist, fetch again, or share a link for.
    val isOffline = song.localUri != null

    TintedSheet(palette = palette, imageUrl = song.thumbnailUrl, modifier = modifier) {
        if (pickingSleepTimer) {
            SleepTimerPicker(palette = palette, onBack = { pickingSleepTimer = false })
            return@TintedSheet
        }

        SheetTrackHeader(song, subtitleColor = palette.onBackgroundVariant)
        HorizontalDivider(thickness = 0.5.dp, color = palette.divider)

        // Leads the list whenever it's available: which recording is playing
        // is the one question that has to be answered before any of the rows
        // below mean anything — there is no point rating, queueing or
        // downloading the wrong version of a song.
        //
        // The two are never both present — one is offered for a track playing
        // a substituted copy, the other for a track held on YouTube's own —
        // and between them they are the whole of the choice, which is why they
        // sit in the same place under the same divider.
        (onRollbackToOriginal ?: onUpgradeQuality)?.let {
            ActionRow(
                icon = if (onRollbackToOriginal != null) {
                    Icons.AutoMirrored.Rounded.Undo
                } else {
                    Icons.Rounded.HighQuality
                },
                label = stringResource(
                    if (onRollbackToOriginal != null) {
                        R.string.revert_to_original
                    } else {
                        R.string.upgrade_quality
                    },
                ),
                accent = palette.accent,
                onClick = it,
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                thickness = 0.5.dp,
                color = palette.divider,
            )
        }

        if (signedIn && !isOffline) {
            ActionRow(
                icon = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                label = if (liked) stringResource(R.string.remove_from_liked) else stringResource(R.string.like),
                tint = if (liked) palette.accent else null,
                accent = palette.accent,
                onClick = onToggleLike,
            )
            ActionRow(
                icon = if (disliked) Icons.Rounded.ThumbDown else Icons.Rounded.ThumbDownOffAlt,
                label = if (disliked) stringResource(R.string.undo_dislike) else stringResource(R.string.dislike),
                tint = if (disliked) palette.accent else null,
                accent = palette.accent,
                onClick = onToggleDislike,
            )
            ActionRow(
                icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                label = stringResource(R.string.add_to_playlist),
                accent = palette.accent,
                onClick = onAddToPlaylist,
            )
            onRemoveFromPlaylist?.let {
                ActionRow(
                    icon = Icons.Rounded.PlaylistRemove,
                    label = stringResource(R.string.remove_from_playlist),
                    accent = palette.accent,
                    onClick = it,
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                thickness = 0.5.dp,
                color = palette.divider,
            )
        }

        DownloadRow(song, palette, isOffline, onDownload)
        ActionRow(
            icon = Icons.Rounded.Radio,
            label = stringResource(R.string.start_radio),
            accent = palette.accent,
            onClick = onStartRadio,
        )
        ActionRow(
            icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
            label = stringResource(R.string.play_next),
            accent = palette.accent,
            onClick = onPlayNext,
        )
        ActionRow(
            icon = Icons.AutoMirrored.Rounded.QueueMusic,
            label = stringResource(R.string.add_to_queue),
            accent = palette.accent,
            onClick = onAddToQueue,
        )
        when (val id = song.albumId) {
            null -> if (resolvingLinks) {
                LoadingActionRow(Icons.Rounded.Album, stringResource(R.string.open_album), palette)
            }
            else -> ActionRow(
                Icons.Rounded.Album,
                stringResource(R.string.open_album),
                accent = palette.accent,
            ) { onOpenAlbum(id) }
        }
        when (val id = song.artistId) {
            null -> if (resolvingLinks) {
                LoadingActionRow(Icons.Rounded.Person, stringResource(R.string.open_artist), palette)
            }
            else -> ActionRow(
                Icons.Rounded.Person,
                stringResource(R.string.open_artist),
                accent = palette.accent,
            ) { onOpenArtist(id) }
        }
        if (showSleepTimer) {
            ActionRow(
                icon = Icons.Rounded.Bedtime,
                label = stringResource(R.string.sleep_timer),
                value = sleepTimerStatus(),
                accent = palette.accent,
            ) { pickingSleepTimer = true }
        }
        if (!isOffline) {
            onShare?.let {
                ActionRow(Icons.Rounded.Share, stringResource(R.string.share), accent = palette.accent, onClick = it)
            }
        }
        // Last, and only from the player: it is about the track playing right
        // now rather than about the song as a thing in a library, and it is
        // the one row here nobody reaches for by accident.
        onCopyLog?.let {
            ActionRow(Icons.Rounded.BugReport, stringResource(R.string.copy_log), accent = palette.accent, onClick = it)
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * A bottom sheet wearing the artwork's colours: the tint and its blurred wash
 * behind, the rounded top corners and the drag handle drawn over it.
 *
 * The corners and the handle are this composable's job rather than
 * `ModalBottomSheet`'s because the host has to pass a transparent container for
 * the wash to be visible at all — and a transparent container has nothing left
 * to clip or to hang a handle on.
 *
 * The row list is capped to a fraction of the screen and scrolls internally.
 * Left unbounded, a track with every optional row present — playlist removal,
 * revert, share, the log — runs past the bottom of the screen with no way to
 * reach what's cut off: a plain Column neither scrolls nor shrinks, so the
 * sheet just grows past the window and sits there stuck at full height.
 */
@Composable
private fun TintedSheet(
    palette: ArtworkPalette,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.85f
    Box(
        modifier
            .fillMaxWidth()
            .clip(SHEET_SHAPE),
    ) {
        ArtworkBackdrop(
            palette = palette,
            imageUrl = imageUrl,
            modifier = Modifier.matchParentSize(),
            // A sheet is a fraction of the height of a page, so the wash has
            // to resolve over a much shorter run to read the same way.
            washFraction = 0.75f,
            artPx = ROW_ART_PX,
        )
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
            // Drawn rather than taken from BottomSheetDefaults, whose handle
            // carries 22dp of padding on each side — half a row's worth of
            // nothing between the grip and the track it is about. Kept outside
            // the scrolling rows below so it stays put as a grab target rather
            // than travelling with the list.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(width = 34.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(palette.onBackground.copy(alpha = 0.35f)),
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                content = content,
            )
        }
    }
}

private val SHEET_SHAPE = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

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
 * changes while the sheet is open, and threading a flow through the sheet's
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
    // reporting last week's dead connection the next time the sheet is opened.
    DisposableEffect(song.videoId) {
        onDispose { Downloads.dismissFailure(song.videoId) }
    }

    when (val state = active[song.videoId]) {
        is DownloadState.Queued -> ActionRow(
            icon = Icons.Rounded.Downloading,
            label = stringResource(R.string.queued),
            value = stringResource(R.string.cancel),
            accent = palette.accent,
        ) { Downloads.cancel(song.videoId) }

        is DownloadState.Running -> ActionRow(
            icon = Icons.Rounded.Downloading,
            label = stringResource(R.string.download_notification_title),
            // Indeterminate until the first response names a length; a
            // stuck "0%" reads as broken where a bare label reads as starting.
            value = if (state.fraction > 0f) "${(state.fraction * 100).toInt()}%" else null,
            tint = palette.accent,
            accent = palette.accent,
        ) { Downloads.cancel(song.videoId) }

        is DownloadState.Failed -> ActionRow(
            icon = Icons.Rounded.ErrorOutline,
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
                icon = Icons.Rounded.DownloadDone,
                label = stringResource(R.string.saved_to_downloads),
                value = stringResource(R.string.delete),
                tint = palette.accent,
                accent = palette.accent,
            ) { scope.launch { Downloads.delete(context, song.videoId) } }
            if (!isOffline && !Downloads.hasLyrics(song.videoId)) {
                val lyricsActive by Downloads.lyricsActive.collectAsStateWithLifecycle()
                val isDownloadingLyrics = song.videoId in lyricsActive
                ActionRow(
                    icon = BitChordIcons.Lyrics,
                    label = if (isDownloadingLyrics) "Downloading lyrics..." else "Download lyrics",
                    accent = palette.accent,
                    onClick = {
                        if (!isDownloadingLyrics) {
                            Downloads.enqueueLyrics(context, song)
                        }
                    },
                )
            }
        } else if (!isOffline) {
            ActionRow(
                icon = Icons.Rounded.Download,
                label = stringResource(R.string.download),
                accent = palette.accent,
                onClick = onDownload,
            )
        }
    }
}

/** End of track or a duration, plus a way out once one is running. */
@Composable
private fun SleepTimerPicker(palette: ArtworkPalette, onBack: () -> Unit) {
    val chosen by SleepTimer.minutes.collectAsStateWithLifecycle()
    val afterTrack by SleepTimer.afterTrack.collectAsStateWithLifecycle()
    val countdown = sleepTimerCountdown()

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 22.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.sleep_timer),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = when {
                        countdown != null -> stringResource(R.string.pause_countdown, countdown)
                        afterTrack -> stringResource(R.string.pause_after_song)
                        else -> stringResource(R.string.pause_after_a_while)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
        if (chosen != null || afterTrack) {
            ActionRow(Icons.Rounded.Close, stringResource(R.string.turn_off_timer), accent = palette.accent) {
                SleepTimer.cancel()
                onBack()
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SleepOption(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = stringResource(R.string.running),
                tint = accent,
                modifier = Modifier.size(22.dp),
            )
        }
    }
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
 * One line of a bottom sheet's menu. Shared with the playlist picker so the
 * two sheets read as the same control rather than as two lists that happen to
 * look alike.
 *
 * [tint] is for rows whose icon carries state — a filled heart on a liked
 * track — and is otherwise the ordinary foreground. [accent] colours the
 * trailing [value], and defaults to the app's own red: a sheet tinted from
 * artwork passes the artwork's accent instead, so the row belongs to the sheet
 * it is drawn on.
 */
@Composable
internal fun ActionRow(
    icon: ImageVector,
    label: String,
    value: String? = null,
    tint: Color? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint ?: MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(18.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = accent,
                maxLines = 1,
            )
        }
    }
}

/**
 * Stands in for [ActionRow] while whether it belongs on the sheet at all is
 * still unknown — "Open album" or "Open artist" before the lookup for their
 * ids has come back. A spinner rather than the row simply being missing, so
 * it doesn't read as decided against until it actually is.
 */
@Composable
private fun LoadingActionRow(icon: ImageVector, label: String, palette: ArtworkPalette) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = palette.onBackground.copy(alpha = 0.4f),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(18.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = palette.onBackground.copy(alpha = 0.4f),
            modifier = Modifier.weight(1f),
        )
        CircularProgressIndicator(
            color = palette.onBackgroundVariant,
            strokeWidth = 2.dp,
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * The track a sheet is about, drawn at its head. Shared by the actions menu
 * and the playlist picker, which is the same track two taps later.
 *
 * [subtitleColor] exists because the two sheets stand on different ground: the
 * picker's is the flat theme background, where the usual dim grey is right,
 * while the actions sheet is tinted from this very artwork and needs the
 * credit brighter to stay off the wash.
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
            modifier = Modifier
                .size(52.dp)
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
