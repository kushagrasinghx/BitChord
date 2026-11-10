/*
 * ArchiveTune player port — the seam between the vendored stack and this app.
 *
 * The player sources under moe/rukamori.archivetune are byte-for-byte
 * ArchiveTune's; everything they used to get from ArchiveTune's own app
 * layer arrives here instead. This file is the app-facing half: the hooks
 * the hosting app installs before mounting the player, plus the small
 * registry the in-module bridges read them from.
 */

package moe.rukamori.archivetune.bridge

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.flow.StateFlow
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.db.entities.SongEntity
import moe.rukamori.archivetune.models.MediaMetadata

/**
 * What the hosting app answers for the player stack. Every member has a
 * default that makes the corresponding surface inert (empty data, no-ops),
 * so an app that installs nothing gets a player that plays and looks right
 * but whose library-backed actions do nothing.
 */
interface ArchiveTuneHostBridge {
    /** The controller the player drives — BitChord's Media3 session. */
    fun player(): Player?

    /** Pause from the sleep timer's expiry — the host pauses its session. */
    fun pauseFromSleepTimer() {}

    /**
     * Swap in a queue: the player hands the items it resolved (radio seeds,
     * "play next" chains, search picks) as ArchiveTune-shaped media items.
     * The host maps them to its own songs and starts them.
     */
    fun playItems(items: List<MediaItem>, startIndex: Int) {}

    /** Insert into the queue after the current item. */
    fun playNext(items: List<MediaItem>) {}

    /** Append to the end of the queue. */
    fun addToQueue(items: List<MediaItem>) {}

    /** Insert after [index] in the current queue. */
    fun addAfterQueueIndex(index: Int, items: List<MediaItem>) {}

    /** Start a radio from the seed currently playing. */
    fun startRadio(seed: MediaMetadata) {}

    /** Toggle the like on the current song, however the host stores likes. */
    fun toggleLike() {}

    /** Like/unlike an arbitrary song by the host's own notion of id. */
    fun likeSong(mediaId: String, liked: Boolean) {}

    /** Stop playback and clear the queue. */
    fun stopAndClearPlayback() {}

    /** The song row behind an id, or null when the host doesn't know it. */
    fun song(mediaId: String): SongEntity? = null

    /** Live lyrics for the current song, in ArchiveTune's entity shape. */
    fun currentLyrics(): StateFlow<LyricsEntity?>? = null

    /** Audio format details for the current song, if the host knows them. */
    fun currentFormat(): StateFlow<FormatEntity?>? = null

    /** The playlists offered by the add-to-playlist dialog. */
    fun playlists(): List<PlaylistEntity> = emptyList()

    /** Add [mediaId] to a playlist; the host stores it however it stores its own. */
    fun addToPlaylist(playlistId: String, mediaId: String) {}

    /** Download a song, however the host downloads. */
    fun download(metadata: MediaMetadata) {}

    /** Show the host's own artist page for this id, if it has one. */
    fun openArtist(browseId: String) {}

    /** Show the host's own album page for this id, if it has one. */
    fun openAlbum(browseId: String) {}

    /** Switch to the host's search. */
    fun openSearch() {}

    /** A user-visible message for actions the bridge cannot complete. */
    fun showMessage(message: String) {}
}

/** The installed bridge. Present once the host mounts the player. */
object ArchiveTuneBridge {
    @Volatile
    private var host: ArchiveTuneHostBridge? = null

    fun install(bridge: ArchiveTuneHostBridge) {
        host = bridge
    }

    fun uninstall(bridge: ArchiveTuneHostBridge) {
        if (host === bridge) host = null
    }

    fun get(): ArchiveTuneHostBridge? = host
}

/**
 * Media caches the copied canvas/video components expect to be handed. The
 * host can install real ones; otherwise lazily-built local caches serve.
 */
object ArchiveTuneCaches {
    @Volatile
    var playerCache: androidx.media3.datasource.cache.Cache? = null

    @Volatile
    var videoCache: androidx.media3.datasource.cache.Cache? = null

    @Volatile
    var downloadCache: androidx.media3.datasource.cache.Cache? = null

    /** Real caches, built lazily against [context] if the host never set any. */
    fun ensure(context: Context) {
        if (playerCache != null && videoCache != null && downloadCache != null) return
        synchronized(this) {
            if (playerCache != null && videoCache != null && downloadCache != null) return
            val provider =
                androidx.media3.database.StandaloneDatabaseProvider(context.applicationContext)

            fun cache(name: String, bytes: Long) =
                androidx.media3.datasource.cache.SimpleCache(
                    java.io.File(context.cacheDir, name),
                    androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor(bytes),
                    provider,
                )
            if (playerCache == null) playerCache = cache("at_player_cache", 256L * 1024 * 1024)
            if (videoCache == null) videoCache = cache("at_video_cache", 128L * 1024 * 1024)
            if (downloadCache == null) downloadCache = cache("at_download_cache", 512L * 1024 * 1024)
        }
    }
}
