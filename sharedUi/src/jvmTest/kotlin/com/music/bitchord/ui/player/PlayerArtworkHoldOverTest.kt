package com.music.bitchord.ui.player

import coil3.PlatformContext
import coil3.memory.MemoryCache
import com.music.bitchord.data.model.PLAYER_ART_MEMORY_CACHE_KEY_EXTRA
import com.music.bitchord.data.model.PLAYER_ART_PX
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayerArtworkHoldOverTest {
    private val previous = MemoryCache.Key("content://media/external/audio/albumart/1")

    @Test
    fun theNextCoverLoadsOverTheLastOne() {
        val art = PlayerArtwork("content://media/external/audio/albumart/2", PlatformContext.INSTANCE, previous)
        assertEquals(previous, art.request.placeholderMemoryCacheKey)
    }

    @Test
    fun playerArtworkUsesAResolutionSpecificMemoryCacheIdentity() {
        val art = PlayerArtwork("content://media/external/audio/albumart/2", PlatformContext.INSTANCE)
        assertEquals(PLAYER_ART_PX.toString(), art.request.memoryCacheKeyExtras[PLAYER_ART_MEMORY_CACHE_KEY_EXTRA])
        // The AutoMix prepared cover builds on the same request, so the player reads its decode.
        val prepared = playerCoverRequest(PlatformContext.INSTANCE, art.url).build()
        assertEquals(prepared.memoryCacheKeyExtras, art.request.memoryCacheKeyExtras)
    }

    @Test
    fun aCoverSkippedBeforeItLoadedPassesTheLastOneOn() {
        val skipped = PlayerArtwork("content://media/external/audio/albumart/2", PlatformContext.INSTANCE, previous)
        assertEquals(previous, skipped.lastCover)
    }

    @Test
    fun theFirstCoverHasNothingToStandOn() {
        val art = PlayerArtwork("content://media/external/audio/albumart/1", PlatformContext.INSTANCE)
        assertNull(art.request.placeholderMemoryCacheKey)
        assertNull(art.lastCover)
    }
}
