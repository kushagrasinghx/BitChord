/*
 * BRIDGE (replaces ArchiveTune's utils/SyncUtils.kt).
 *
 * The one member the copied player calls through is likeSong; the playlist
 * sync entry points are not reachable from the ported surfaces. Likes are
 * the host's to store.
 */

package moe.rukamori.archivetune.utils

import moe.rukamori.archivetune.bridge.ArchiveTuneBridge

class SyncUtils {
    private val host get() = ArchiveTuneBridge.get()

    fun likeSong(mediaId: String, liked: Boolean) {
        host?.likeSong(mediaId, liked)
    }

    fun syncAutoSyncPlaylists() {}
}
