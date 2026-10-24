package com.music.bitchord.ui.tv.performance

import org.junit.Assert.assertEquals
import org.junit.Test

class TvArtworkSizingTest {
    @Test fun smallAvatarIsNotDecodedAtTheFullProfileLimit() {
        assertEquals(48 to 48, capTvArtworkDimensions(48, 48, 512))
        assertEquals(160 to 160, capTvArtworkDimensions(160, 160, 512))
    }

    @Test fun largeCoversAreCappedForBothDisplayOrientations() {
        assertEquals(512 to 288, capTvArtworkDimensions(1920, 1080, 512))
        assertEquals(288 to 512, capTvArtworkDimensions(1080, 1920, 512))
        assertEquals(512 to 512, capTvArtworkDimensions(960, 960, 512))
    }

    @Test fun thinImagesRetainAPositiveDecodeDimension() {
        assertEquals(1 to 512, capTvArtworkDimensions(1, 4000, 512))
    }
}
