package com.music.bitchord.data

import com.music.bitchord.data.model.Song

/**
 * What the History page should show, decided without touching Android or the
 * network so the fallback order can be tested on its own.
 */
internal sealed interface HistorySelection {

    /** [onDevice] is true when the rows came from [LocalHistoryRepository]. */
    data class Songs(val songs: List<Song>, val onDevice: Boolean) : HistorySelection

    data class Failed(val error: Throwable?) : HistorySelection

    data object Empty : HistorySelection
}

/**
 * Cloud history when there is one, then the device's own list, then an error.
 *
 * A signed-in listener whose watch history is paused gets an empty cloud
 * response, which is exactly the case local history exists for. A failed fetch
 * also falls through to it rather than losing the page to a network message;
 * the remote error is only shown when there is no local list to show instead.
 */
internal fun selectHistory(
    signedIn: Boolean,
    remote: Result<List<Song>>?,
    local: List<Song>,
): HistorySelection {
    if (!signedIn) {
        return if (local.isEmpty()) HistorySelection.Empty
        else HistorySelection.Songs(local, onDevice = true)
    }
    val cloud = remote?.getOrNull().orEmpty()
    if (cloud.isNotEmpty()) return HistorySelection.Songs(cloud, onDevice = false)
    if (local.isNotEmpty()) return HistorySelection.Songs(local, onDevice = true)
    if (remote?.isFailure == true) return HistorySelection.Failed(remote.exceptionOrNull())
    return HistorySelection.Empty
}
