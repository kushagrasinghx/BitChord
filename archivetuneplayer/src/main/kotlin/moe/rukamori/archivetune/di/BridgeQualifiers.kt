/*
 * BRIDGE (replaces the qualifiers and entry points that lived in
 * ArchiveTune's di/AppModule.kt).
 *
 * The copied canvas and video players reach the media caches through Hilt
 * entry points. There is no Hilt here — the annotations are shims (see the
 * dagger/hilt source directories in this module) and the accessor below
 * hands out the caches the host installed (or lazily-built local ones).
 */

package moe.rukamori.archivetune.di

import androidx.media3.datasource.cache.Cache
import moe.rukamori.archivetune.bridge.ArchiveTuneCaches

@javax.inject.Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PlayerCache

@javax.inject.Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DownloadCache

@javax.inject.Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class VideoCache

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface CanvasCacheEntryPoint {
    @PlayerCache
    fun playerCache(): Cache
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface VideoCacheEntryPoint {
    @VideoCache
    fun videoCache(): Cache
}

/** Serves the entry points the copied players ask for. */
internal object BridgeEntryPoints {
    val canvasCacheEntryPoint: CanvasCacheEntryPoint =
        object : CanvasCacheEntryPoint {
            override fun playerCache(): Cache {
                val ctx = context ?: error("no context registered with BridgeEntryPoints")
                ArchiveTuneCaches.ensure(ctx)
                return ArchiveTuneCaches.playerCache!!
            }
        }

    val videoCacheEntryPoint: VideoCacheEntryPoint =
        object : VideoCacheEntryPoint {
            override fun videoCache(): Cache {
                val ctx = context ?: error("no context registered with BridgeEntryPoints")
                ArchiveTuneCaches.ensure(ctx)
                return ArchiveTuneCaches.videoCache!!
            }
        }

    @Volatile
    var context: android.content.Context? = null
}

/** The dagger.hilt.android.EntryPointAccessors shim's backing registry. */
object BridgeEntryPointRegistry {
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> resolve(entryPoint: Class<T>): T =
        when (entryPoint) {
            CanvasCacheEntryPoint::class.java -> BridgeEntryPoints.canvasCacheEntryPoint
            VideoCacheEntryPoint::class.java -> BridgeEntryPoints.videoCacheEntryPoint
            else -> error("No bridge entry point registered for ${entryPoint.name}")
        } as T
}
