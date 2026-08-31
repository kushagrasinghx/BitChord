package com.music.bitchord

import com.music.bitchord.data.sources.SourceKind
import com.music.bitchord.playback.smart.AutomixAnalysisSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomixAnalysisSourceTest {
    @Test
    fun `JioSaavn source tracks are excluded from Automix analysis`() {
        assertFalse(AutomixAnalysisSource.canAnalyzeSourceBackedTrack(SourceKind.JIOSAAVN))
        assertTrue(AutomixAnalysisSource.canAnalyzeSourceBackedTrack(SourceKind.MODULE))
    }

    @Test
    fun `YouTube analysis accepts only its canonical Opus cache key`() {
        assertTrue(AutomixAnalysisSource.isCanonicalYouTubeRendition("video", "video"))
        assertFalse(AutomixAnalysisSource.isCanonicalYouTubeRendition("video", "video#alt"))
        assertFalse(AutomixAnalysisSource.isCanonicalYouTubeRendition("video", "video#hifi"))
    }
}
