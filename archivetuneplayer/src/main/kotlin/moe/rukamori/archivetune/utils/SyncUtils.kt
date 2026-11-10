/*
 * BRIDGE (replaces ArchiveTune's utils/SyncUtils.kt).
 *
 * The member the copied player calls through is likeSong (TikTok's rail
 * hands it the toggled SongEntity, exactly as ArchiveTune's own sources
 * do); the rest of the sync entry points are not reachable from the
 * ported surfaces. Likes are the host's to store.
 */

package moe.rukamori.archivetune.utils

import moe.rukamori.archivetune.bridge.ArchiveTuneBridge
import moe.rukamori.archivetune.db.entities.SongEntity

class SyncUtils {
    private val host get() = ArchiveTuneBridge.get()

    fun likeSong(s: SongEntity) {
        host?.likeSong(s.id, s.liked)
    }

    fun likeSongs(songs: Collection<SongEntity>) {
        songs.forEach { s -> host?.likeSong(s.id, s.liked) }
    }

    fun syncAutoSyncPlaylists() {}
}
