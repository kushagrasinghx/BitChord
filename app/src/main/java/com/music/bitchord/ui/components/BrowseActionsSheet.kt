package com.music.bitchord.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UserPlaylist
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.download.DownloadState
import com.music.bitchord.download.Downloads
import com.music.bitchord.ui.icons.BitChordIcons
import java.util.Locale

/**
 * The album or playlist a long-press is acting on.
 *
 * Deliberately not one of the browse models: every surface in the app describes
 * a release with a different type — a [com.music.bitchord.data.model.ShelfItem]
 * on the home feed, a [com.music.bitchord.data.model.BrowseItem] in search, a
 * [com.music.bitchord.data.model.DetailPage] on the page itself, a plain album
 * name on the Local Music tab — and the menu needs the same five things from
 * all of them.
 */
data class BrowseTarget(
    /**
     * What to fetch the track list with, or null when there is nothing to
     * fetch: a Local Music album is a grouping of rows already on screen, not
     * a page anything can be asked for.
     */
    val browseId: String?,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String? = null,
    val type: BrowseType = BrowseType.OTHER,
    /**
     * The tracks already in hand. Empty for a card whose page was never
     * opened — the queue actions then fetch the listing themselves, which is
     * why they are offered either way.
     */
    val songs: List<Song> = emptyList(),
    /**
     * Set when this is one of the account's own playlists, which is the only
     * case where renaming and deleting are things that can be done to it.
     *
     * Filled in where the sheet is raised rather than by whatever built the
     * target: only the playlist's own page states who made it, so for a card
     * this has to be sent for and arrives after the sheet is already up. Left
     * null by every caller, and null is also the honest answer while the
     * question is still out.
     */
    val playlist: UserPlaylist? = null,
    /**
     * False when the menu was opened from the page it would otherwise navigate
     * to — the release page's own overflow. Play, Shuffle and Open are already
     * on that page, so there they are left off the sheet.
     */
    val fromCard: Boolean = true,
    /**
     * The id this release is recorded under in [com.music.bitchord.download.Downloads.collections],
     * when it was downloaded whole — set so [onDelete] in the caller can offer
     * "Delete download" alongside (or instead of) deleting the account's own
     * copy.
     *
     * Left for the caller to resolve rather than derived here: a card off the
     * Local Music screen has no browse id to derive it from at all (see
     * `albumEntries` in `LocalMusicScreen`), while a real page's id needs
     * `Downloads.recordIdOf` run on it first for a downloaded playlist. Both are
     * questions about the download record, not about what this sheet is.
     */
    val downloadId: String? = null,
)

/**
 * Long-press menu for an album or playlist — the collection-level counterpart
 * to [SongActionsSheet].
 *
 * Every action is optional, and which ones a caller passes is how the same
 * sheet serves a card on the home feed and the page that card opens. A card
 * has no other way to play its album without navigating to it, so it gets Play
 * and Shuffle; the page's own header already carries both, so there they are
 * null and the sheet is the queue rows and the download.
 *
 * The queue rows are always offered. They are the reason this menu exists: a
 * release is exactly the kind of thing someone wants *after* what is playing
 * rather than instead of it, and until now the only way to queue one was to
 * open it and long-press its tracks one at a time.
 *
 * Deleting asks a second time, in place. A playlist is the only thing in this
 * app whose loss can't be undone by tapping the same row again, and a
 * mis-tapped card in a shelf is exactly how it would happen.
 */
@Composable
fun BrowseActionsSheet(
    target: BrowseTarget,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    modifier: Modifier = Modifier,
    onPlay: (() -> Unit)? = null,
    onShuffle: (() -> Unit)? = null,
    /** Null where the sheet was opened from the page it would navigate to. */
    onOpen: (() -> Unit)? = null,
    onDownloadAll: (() -> Unit)? = null,
    /**
     * Set whenever [target] is a playlist, regardless of who owns it — pinning
     * doesn't touch the account, only what sits at the top of this device's
     * Library tab. Null everywhere else (albums, artists), where "pin" has
     * nothing to mean.
     */
    isPinned: Boolean = false,
    onTogglePin: (() -> Unit)? = null,
    onRename: ((String) -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    /**
     * Removes the files this release was downloaded as, when it was downloaded
     * whole — see [BrowseTarget.downloadId]. Independent of [onDelete]: that one
     * deletes the playlist from the account, this one only ever touches what's
     * on the device, so both can be offered together for an owned playlist that
     * also happens to be downloaded.
     */
    onDeleteDownload: (() -> Unit)? = null,
) {
    var renaming by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var confirmingDeleteDownload by remember { mutableStateOf(false) }

    val playlist = target.playlist
    if (renaming && playlist != null && onRename != null) {
        RenamePlaylistForm(
            playlist = playlist,
            onBack = { renaming = false },
            onRename = onRename,
            modifier = modifier,
        )
        return
    }

    Column(modifier.fillMaxWidth()) {
        BrowseSheetHeader(target)
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)

        onPlay?.let { ActionRow(Icons.Rounded.PlayArrow, stringResource(R.string.play), onClick = it) }
        onShuffle?.let { ActionRow(BitChordIcons.Shuffle, stringResource(R.string.shuffle), onClick = it) }
        ActionRow(
            Icons.AutoMirrored.Rounded.PlaylistPlay,
            stringResource(R.string.play_next),
            onClick = onPlayNext,
        )
        ActionRow(
            Icons.AutoMirrored.Rounded.QueueMusic,
            stringResource(R.string.add_to_queue),
            onClick = onAddToQueue,
        )
        onDownloadAll?.let { download ->
            // Saying which of the three it is, rather than offering the same row
            // whatever the state — this is where a release is asked for now that
            // its page's header spends that spot on the search, and a menu that
            // can't say "already on the device" leaves the question open. The
            // tap is left live in every state: [Downloads.enqueue] leaves a
            // track that is saved, queued or running alone.
            val active by Downloads.active.collectAsStateWithLifecycle()
            val requested by Downloads.requested.collectAsStateWithLifecycle()
            val saved by Downloads.saved.collectAsStateWithLifecycle()
            // Only the tracks *this release* asked for — two releases can share
            // a track, and reading the whole queue would show this release
            // waiting on a download some other one started. Failed entries stay
            // in [Downloads.active] until dismissed, and a failure is not a wait.
            val waiting = target.browseId?.let { requested[it] }.orEmpty().any { id ->
                when (active[id]) {
                    is DownloadState.Queued, is DownloadState.Running -> true
                    else -> false
                }
            }
            // The same reading [DownloadedBadge] does per row: a release counts
            // as downloaded once every one of its tracks is in the saved set,
            // not from any record of the release itself. Empty for a card whose
            // page was never opened, which is not an answer either way.
            val ids = remember(target.songs) { target.songs.mapTo(HashSet()) { it.videoId } }
            val downloaded = !waiting && ids.isNotEmpty() && ids.all { it in saved }
            ActionRow(
                icon = when {
                    waiting -> BitChordIcons.Clock
                    downloaded -> BitChordIcons.Check
                    else -> BitChordIcons.Download
                },
                label = stringResource(R.string.download_all),
                value = when {
                    waiting -> stringResource(R.string.downloading)
                    downloaded -> stringResource(R.string.downloaded)
                    else -> null
                },
                onClick = download,
            )
        }
        onOpen?.let {
            ActionRow(
                BitChordIcons.ChevronRight,
                target.type.localizedOpenLabel(),
                onClick = it,
            )
        }
        onTogglePin?.let {
            ActionRow(
                BitChordIcons.Pin,
                stringResource(if (isPinned) R.string.unpin else R.string.pin),
                onClick = it,
            )
        }
        if (onRename != null) {
            ActionRow(Icons.Rounded.Edit, stringResource(R.string.rename)) { renaming = true }
        }
        if (onDelete != null) {
            if (confirmingDelete) {
                ActionRow(
                    icon = Icons.Rounded.DeleteForever,
                    label = stringResource(R.string.delete_playlist_confirmation, target.title),
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onDelete,
                )
            } else {
                ActionRow(Icons.Rounded.Delete, stringResource(R.string.delete_playlist)) { confirmingDelete = true }
            }
        }
        if (onDeleteDownload != null) {
            if (confirmingDeleteDownload) {
                ActionRow(
                    icon = Icons.Rounded.DeleteForever,
                    label = stringResource(R.string.delete_download_confirmation, target.title),
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onDeleteDownload,
                )
            } else {
                ActionRow(Icons.Rounded.Delete, stringResource(R.string.delete_download)) {
                    confirmingDeleteDownload = true
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Which release the sheet is about: the same row the shelf card was. */
@Composable
private fun BrowseSheetHeader(target: BrowseTarget) {
    val shape = if (target.type == BrowseType.ARTIST) CircleShape else RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = target.thumbnailUrl.artworkAt(ROW_ART_PX),
            contentDescription = null,
            modifier = Modifier
                .size(52.dp)
                .clip(shape)
                .thumbnailBorder(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = target.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                // A card frequently has no subtitle at all, and the kind of
                // thing it is beats a blank line under the title. A card of
                // unknown kind has neither, and gets the track count instead —
                // which by then is the one thing actually known about it.
                text = target.subtitle.ifBlank {
                    target.type.localizedNoun().replaceFirstChar { it.uppercase(Locale.getDefault()) }.ifBlank {
                        target.songs.size.takeIf { it > 0 }
                            ?.let { pluralStringResource(R.plurals.song_count_plural, it, it) }
                            .orEmpty()
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * What to call the thing the sheet is about, in a sentence. [BrowseType.OTHER]
 * is a home card nobody has identified yet — "Open" without a noun is still a
 * true label for it, and guessing "album" would not be.
 */
@Composable
private fun BrowseType.localizedNoun(): String = when (this) {
    BrowseType.ALBUM -> stringResource(R.string.album)
    BrowseType.PLAYLIST -> stringResource(R.string.playlist)
    BrowseType.ARTIST -> stringResource(R.string.artist)
    BrowseType.OTHER -> ""
}

@Composable
private fun BrowseType.localizedOpenLabel(): String = when (this) {
    BrowseType.ALBUM -> stringResource(R.string.open_album)
    BrowseType.ARTIST -> stringResource(R.string.open_artist)
    BrowseType.PLAYLIST -> stringResource(R.string.open_playlist)
    BrowseType.OTHER -> stringResource(R.string.open)
}
