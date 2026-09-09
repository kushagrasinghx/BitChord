package com.music.bitchord

import android.app.Application
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.music.bitchord.data.canvas.CanvasCache
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.SearchHistory
import com.music.bitchord.playback.AudioCache
import com.music.bitchord.playback.LastPlayed
import com.music.bitchord.download.Downloads
import com.music.bitchord.data.stats.ListeningStats

class BitChordApplication : Application(), SingletonImageLoader.Factory {

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // Offline edition: no account/session bootstrap and no remote service
        // initialization. All state below is local device state.
        AppSettings.init(this)
        SearchHistory.init(this)
        LastPlayed.init(this)
        Downloads.init(this)
        ListeningStats.init(this)
        AudioCache.init(this)
        CanvasCache.init(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            .crossfade(200)
            .build()
}
