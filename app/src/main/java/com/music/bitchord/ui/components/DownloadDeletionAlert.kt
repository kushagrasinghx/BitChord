package com.music.bitchord.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.music.bitchord.R
import com.music.bitchord.data.model.Song
import dev.chrisbanes.haze.HazeState

/**
 * What a pending download deletion is about to remove.
 *
 * A track and a whole release reach the same confirmation, but not the same
 * wording: the first has a title and an artist, the second a release title and
 * its own subtitle. Keeping both here means the dialog cannot drift from what
 * the menu row said it would delete.
 */
data class DownloadDeletion(
    val songs: List<Song>,
    /** Set when this is a whole downloaded album or playlist rather than a track. */
    val collectionId: String? = null,
    val collectionTitle: String? = null,
    val collectionSubtitle: String? = null,
) {
    /** The exact records a confirmation will delete, in the order they were selected. */
    val videoIds: List<String> get() = songs.map { it.videoId }.distinct()

    val isCollection: Boolean get() = collectionId != null

    /** True when the dialog has more than one file to warn about. */
    val isBulk: Boolean get() = isCollection || videoIds.size > 1
}

/**
 * The names a multi-track confirmation lists, capped so the warning stays a
 * warning rather than becoming the library.
 */
internal fun downloadDeletionLines(songs: List<Song>, limit: Int = 3): String {
    val distinct = songs.distinctBy { it.videoId }
    val lines = distinct.take(limit).mapNotNull { song ->
        listOf(song.title, song.artist)
            .filter { it.isNotBlank() }
            .joinToString(" — ")
            .takeIf { it.isNotBlank() }
    }
    val more = if (distinct.size > limit) "\n…" else ""
    return lines.joinToString("\n") + more
}

/**
 * Confirmation for deleting downloaded audio.
 *
 * The file is the thing being destroyed, so the dialog names it and says so
 * before the Delete row is live. Cancel is the same weight as every other
 * alert's dismiss, and Delete is the error colour; the scrim and Back both
 * dismiss without touching the file.
 */
@Composable
fun DownloadDeletionAlert(
    hazeState: HazeState,
    deletion: DownloadDeletion,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = when {
        deletion.isCollection -> deletion.collectionTitle?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.delete_download)
        deletion.songs.size == 1 -> deletion.songs.first().title.ifBlank {
            stringResource(R.string.delete_download)
        }
        else -> stringResource(R.string.delete_downloads_title, deletion.videoIds.size)
    }
    val details = when {
        deletion.isCollection -> deletion.collectionSubtitle.orEmpty()
        deletion.songs.size == 1 -> deletion.songs.first().artist
        else -> downloadDeletionLines(deletion.songs)
    }
    val warning = if (deletion.isBulk) {
        stringResource(R.string.delete_downloads_warning)
    } else {
        stringResource(R.string.delete_download_warning)
    }
    ConfirmationAlert(
        hazeState = hazeState,
        title = title,
        description = listOf(details, warning).filter { it.isNotBlank() }.joinToString("\n\n"),
        confirmLabel = stringResource(R.string.delete),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        destructive = true,
    )
}
