package com.music.bitchord

import com.music.bitchord.playback.smart.AutomixAnalysisSource
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
}
