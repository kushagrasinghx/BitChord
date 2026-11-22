package com.music.bitchord.ui.player

import coil3.Image
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.Uri
import coil3.decode.DataSource
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Precision
import coil3.size.Scale
import coil3.size.Size
import com.music.bitchord.data.model.PLAYER_ART_MEMORY_CACHE_KEY_EXTRA
import com.music.bitchord.data.model.PLAYER_ART_PX
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.coroutines.runBlocking

class PlayerArtworkPrefetchTest {
    private val context = PlatformContext.INSTANCE

    @Test
    fun fullSizeRequestUsesTheSameCropAndPrecisionWithoutAComposeTarget() {
        val art = PlayerArtwork("content://media/external/audio/albumart/2", context)
        assertEquals(Scale.FILL, art.request.scale)
        assertEquals(Precision.INEXACT, art.request.precision)
    }

    @Test
    fun rapidColdSelectionsNeverUseAnotherCoversPlaceholder() {
        val sources = listOf("content://media/external/audio/albumart/1", "content://media/external/audio/albumart/2", null)
        sources.forEach { url ->
            val art = PlayerArtwork(url, context)
            assertEquals(url, art.url)
            assertFalse(art.loaded)
            assertNull(art.request.placeholderMemoryCacheKey)
        }
    }

    @Test
    fun sameUrlThumbnailCannotReplaceTheFullDecodeReadByThePlayer() = runBlocking {
        val full = image(PLAYER_ART_PX)
        val thumbnail = image(128)
        var fetches = 0
        val loader = ImageLoader.Builder(context).components {
            add(Fetcher.Factory<Uri> { _, options, _ ->
                Fetcher {
                    fetches++
                    val small = options.size == Size(128, 128)
                    ImageFetchResult(if (small) thumbnail else full, isSampled = small, DataSource.DISK)
                }
            })
        }.build()
        try {
            val url = "https://example.invalid/shared-album-art.jpg"
            val prefetch = playerCoverRequest(context, url).apply { extras[SpeculativeArtworkRequest] = true }.build()
            val prepared = loader.execute(prefetch) as SuccessResult
            val small = loader.execute(ImageRequest.Builder(context).data(url).size(128).build()) as SuccessResult
            val selected = loader.execute(PlayerArtwork(url, context).request) as SuccessResult
            val sameAlbum = loader.execute(PlayerArtwork(url, context).request) as SuccessResult

            assertEquals(PLAYER_ART_PX.toString(), prepared.memoryCacheKey?.extras?.get(PLAYER_ART_MEMORY_CACHE_KEY_EXTRA))
            assertNotEquals(prepared.memoryCacheKey, small.memoryCacheKey)
            assertEquals(prepared.memoryCacheKey, selected.memoryCacheKey)
            assertSame(thumbnail, small.image)
            assertSame(full, selected.image)
            assertSame(full, sameAlbum.image)
            assertEquals(DataSource.MEMORY_CACHE, selected.dataSource)
            assertEquals(2, fetches)
        } finally {
            loader.shutdown()
        }
    }

    private fun image(px: Int) = object : Image {
        override val width = px
        override val height = px
        override val size = px.toLong() * px * 4
        override val shareable = true
        override fun draw(canvas: coil3.Canvas) = Unit
    }
}
