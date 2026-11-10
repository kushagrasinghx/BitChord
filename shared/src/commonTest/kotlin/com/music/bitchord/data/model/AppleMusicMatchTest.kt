package com.music.bitchord.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppleMusicMatchTest {
    private fun song(id: String) = Song(
        videoId = id,
        title = "Song",
        artist = "Artist",
        thumbnailUrl = null,
        fromAutoplay = false,
    )

    @Test
    fun applePendingRowsAreUnresolved() {
        assertTrue(song("am:123").isAppleMusicMatchPending)
        assertTrue(song("am:123").isUnresolvedExternal)
        assertNull(song("am:123").appleMusicCatalogId)
    }

    @Test
    fun appleMissesRetainPlayableCatalogIds() {
        val track = song("am-miss:12345")
        assertTrue(track.isAppleMusicMatchMissing)
        assertFalse(track.isUnresolvedExternal)
        assertEquals("12345", track.appleMusicCatalogId)
    }

    @Test
    fun spotifySemanticsArePreserved() {
        assertTrue(song("sp:abc").isUnresolvedExternal)
        assertTrue(song("sp-miss:abc").isUnresolvedExternal)
        assertTrue(song("sp-miss:abc").isUnresolvedSpotify)
        assertFalse(song("youtube-id").isUnresolvedExternal)
        assertFalse(song("am-miss:123").isUnresolvedSpotify)
    }

    @Test
    fun libraryIdsAndMalformedSuffixesAreNeverCatalogIds() {
        listOf("am-miss:", "am-miss:i.library", "am-miss:0", "am-miss:0123", "am-miss:123/456", "am-miss:１２３", "am-miss:" + "1".repeat(33))
            .forEach { assertNull(song(it).appleMusicCatalogId) }
    }
}
