package com.music.bitchord

import com.music.bitchord.data.sources.SourceStream
import com.music.bitchord.playback.QualityUpgrade
import com.music.bitchord.playback.StreamChoice
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which of the two quality rows the player menu leads with.
 *
 * They are the same row in the same place: "Revert to original" for a track
 * playing a substituted copy, "Upgrade quality" for one held on YouTube's own.
 * Choosing between them used to read the listener's own revert pin and nothing
 * else, so an upgrade that was found, swapped in and then failed to prove
 * itself — which reverts automatically and pins nothing — left the menu
 * offering to revert a track already playing YouTube's Opus.
 */
class PlayerQualityMenuTest {

    private val videoId = "i1o1p_DD6TU"

    @After
    fun tearDown() {
        StreamChoice.forget(videoId)
    }

    /** The reported case: reverted after a failed upgrade, back on YouTube's Opus. */
    @Test
    fun `a track back on YouTube's own stream is offered an upgrade`() {
        StreamChoice.remember(videoId, SourceStream("https://yt/opus"), substituted = false)

        // The revert put the pre-upgrade URI back, so there is no marker on it.
        assertTrue(QualityUpgrade.isKnownToBePlayingYouTubesOwn(videoId, rendition = null))
    }

    /** A track actually playing a substitute is the one the revert row is for. */
    @Test
    fun `a track playing a substituted copy is not offered an upgrade`() {
        StreamChoice.remember(videoId, SourceStream("https://tidal/flac"), substituted = true)

        assertFalse(QualityUpgrade.isKnownToBePlayingYouTubesOwn(videoId, rendition = null))
    }

    /**
     * The half that needs the marker. [StreamChoice] records what the *first*
     * resolve settled on and is not cleared when an upgrade swaps a better copy
     * in, so on a successfully upgraded track it still names YouTube — and that
     * track is exactly the one that should keep the revert row.
     */
    @Test
    fun `an upgraded track keeps its revert row despite the stale YouTube choice`() {
        StreamChoice.remember(videoId, SourceStream("https://yt/opus"), substituted = false)

        assertFalse(QualityUpgrade.isKnownToBePlayingYouTubesOwn(videoId, rendition = "hifi"))
    }

    /**
     * Nothing resolved it in this process — it is playing off the disk cache and
     * what is in that entry is genuinely unknown. The revert stays on offer,
     * because it is the escape hatch for a substitution nothing announced.
     */
    @Test
    fun `an unresolved cached track keeps its revert row`() {
        assertFalse(QualityUpgrade.isKnownToBePlayingYouTubesOwn(videoId, rendition = null))
    }
}
