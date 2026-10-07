package com.music.bitchord.data.canvas

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Disk cache for canvas clips — the looping video some releases publish
 * alongside a track, played over the cover art by
 * [CanvasArtworkPlayer][com.music.bitchord.ui.player.CanvasArtworkPlayer].
 *
 * Without this, that player fetched a clip straight off the network through
 * a bare OkHttp data source, repeating for as long as the track playing over
 * it does ([androidx.media3.common.Player.REPEAT_MODE_ONE]). ExoPlayer frees
 * a sample's buffer as soon as the renderer has consumed it — there is no
 * back buffer configured, nor should there be one sized for an entire clip —
 * so once a loop finishes, nothing of it is left in memory to loop back to,
 * and reaching position zero again means the data source is asked for those
 * bytes again. A five-second clip behind a four-minute track loops around
 * fifty times, which without this cache was fifty downloads of the same few
 * seconds of video rather than one: the "40MB for one song" and multi-
 * gigabyte-day reports trace to exactly this repeat, not to audio bitrate.
 *
 * Wrapping the upstream in [CacheDataSource] means only the first loop of a
 * clip ever reaches the network; every loop after it, and every replay of
 * the same track later in the session, is served from disk instead.
 */
@UnstableApi
object CanvasCache {

    /**
     * Small on purpose — a clip is a few seconds of video, not a song, and
     * this only needs to outlive one player screen's worth of looping, not
     * a library. [SimpleCache]'s own evictor reclaims the rest.
     */
    private const val CACHE_LIMIT_BYTES = 150L * 1024 * 1024

    private lateinit var cache: SimpleCache

    /** Opened once per process, alongside [com.music.bitchord.playback.AudioCache.init]. */
    fun init(context: Context) {
        cache = SimpleCache(
            File(context.cacheDir, "canvas"),
            LeastRecentlyUsedCacheEvictor(CACHE_LIMIT_BYTES),
            StandaloneDatabaseProvider(context),
        )
    }

    /**
     * [upstream] wrapped so a clip already on disk never touches the
     * network again — see the class doc for why that is the whole point.
     * A cache write that fails (full disk, evicted mid-write) drops back to
     * plain streaming rather than surfacing as a playback error, the same
     * choice [com.music.bitchord.playback.AudioCache] makes for audio.
     */
    fun dataSourceFactory(upstream: DataSource.Factory): DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
}
