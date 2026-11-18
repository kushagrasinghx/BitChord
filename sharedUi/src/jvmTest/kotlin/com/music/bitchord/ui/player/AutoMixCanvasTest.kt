package com.music.bitchord.ui.player

import com.music.bitchord.data.canvas.CanvasArtwork
import com.music.bitchord.data.model.QueueEntryIdentity
import com.music.bitchord.playback.AutoMixVisualState
import com.music.bitchord.playback.AutoMixVisualTrack
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AutoMixCanvasTest {
    private val outgoing = QueueEntryIdentity("a", null, 0)
    private val incoming = QueueEntryIdentity("b", null, 0)
    private val clip = CanvasArtwork("incoming.mp4")
    private val visual = AutoMixVisualState(
        1L,
        AutoMixVisualTrack(outgoing, 0, null),
        AutoMixVisualTrack(incoming, 1, null),
    )

    @Test
    fun onlyMatchingCroppedHeroSlotsCanUseTheLiveReveal() {
        assertTrue(sharesHeroRevealFootprint(true, true, 0f, 0f))
        assertTrue(sharesHeroRevealFootprint(false, false, 1f, 16f / 9f))
        assertFalse(sharesHeroRevealFootprint(true, false, 1f, 1f))
        assertFalse(sharesHeroRevealFootprint(false, false, 1f, 0.56f))
        assertFalse(sharesHeroRevealFootprint(false, false, 0.56f, 0.56f))
        assertFalse(sharesHeroRevealFootprint(false, false, 1f, 0f))
    }

    @Test
    fun readyIncomingTransfersWithoutReplacingItsClip() {
        val owner = AutoMixCanvasPair()
        val tracks = requireNotNull(owner.start(visual, outgoing))
        tracks.incomingClip = clip
        tracks.reportIncoming(true)
        assertTrue(tracks.sealIncoming())
        owner.finish(tracks)
        assertNull(owner.visual)
        assertSame(tracks, owner.promoted)
        assertSame(clip, owner.promoted?.incomingClip)
    }

    @Test
    fun frameAfterDeadlineCannotRejoinTheDissolve() {
        val tracks = AutoMixCanvasTracks(1L, outgoing, incoming, null)
        tracks.incomingClip = clip
        assertFalse(tracks.sealIncoming())
        tracks.reportIncoming(true)
        assertFalse(tracks.sealIncoming())
        assertFalse(tracks.liveIncoming)
    }

    @Test
    fun acceptedClipThatLosesItsSurfaceFallsBackForTheRestOfTheMix() {
        val tracks = AutoMixCanvasTracks(1L, outgoing, incoming, null)
        tracks.incomingClip = clip
        tracks.reportIncoming(true)
        assertTrue(tracks.sealIncoming())
        tracks.reportIncoming(false)
        tracks.reportIncoming(true)
        assertNull(tracks.incomingClip)
        assertFalse(tracks.liveIncoming)
    }

    @Test
    fun leavingPromotedOccurrenceAndReturningDoesNotRestoreOldOwnership() {
        val owner = AutoMixCanvasPair()
        val tracks = requireNotNull(owner.start(visual, outgoing))
        tracks.incomingClip = clip
        tracks.reportIncoming(true)
        assertTrue(tracks.sealIncoming())
        owner.finish(tracks)
        owner.displayed(outgoing, null, false)
        owner.displayed(incoming, null, false)
        assertNull(owner.promoted)
        assertNull(owner.start(visual, outgoing))
    }
}
