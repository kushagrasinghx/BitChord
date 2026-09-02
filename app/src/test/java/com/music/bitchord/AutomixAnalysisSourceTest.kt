package com.music.bitchord

import com.music.bitchord.playback.smart.AutomixAnalysisSource
import com.music.bitchord.playback.QualityUpgrade
import com.music.bitchord.data.sources.StreamFormat
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class AutomixAnalysisSourceTest {
    @Test
    fun `YouTube analysis accepts only its canonical Opus cache key`() {
        assertTrue(AutomixAnalysisSource.isCanonicalYouTubeRendition("video", "video"))
        assertFalse(AutomixAnalysisSource.isCanonicalYouTubeRendition("video", "video#alt"))
        assertFalse(AutomixAnalysisSource.isCanonicalYouTubeRendition("video", "video#hifi"))
    }

    @Test
    fun `analysis URI asks the resolver to bypass playback substitutions`() {
        assertTrue(AutomixAnalysisSource.opusUri("video").contains("v=video&automix_opus=1"))
        assertTrue(AutomixAnalysisSource.requestsYouTubeOpus("1"))
        assertFalse(AutomixAnalysisSource.requestsYouTubeOpus(null))
        assertFalse(AutomixAnalysisSource.requestsYouTubeOpus("0"))
    }

    @Test
    fun `second quality upgrade gets a new rendition marker`() {
        val jio = QualityUpgrade.upgradedUri("bitchord://watch?v=video")
        val lossless = QualityUpgrade.upgradedUri(jio)
        assertTrue(jio.endsWith("q=hifi"))
        assertTrue(lossless.endsWith("q=hifi-2"))
    }
}
