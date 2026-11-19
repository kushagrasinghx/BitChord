package com.music.bitchord.ui.player

import androidx.compose.ui.geometry.Rect
import com.music.bitchord.data.canvas.CanvasArtwork
import com.music.bitchord.data.model.QueueEntryIdentity
import com.music.bitchord.playback.AutoMixVisualState
import com.music.bitchord.playback.AutoMixVisualTrack
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun outgoingArtworkStaysOpaqueUntilOnlyTheUncoveredTailNeedsRetiring() {
        assertEquals(1f, outgoingArtworkTailAlpha(0f))
        assertEquals(1f, outgoingArtworkTailAlpha(0.5f))
        assertEquals(1f, outgoingArtworkTailAlpha(0.8f))
        val tail = (80..100).map { outgoingArtworkTailAlpha(it / 100f) }
        assertTrue(tail.zipWithNext().all { (a, b) -> a >= b })
        assertTrue(tail[10] in 0.4f..0.6f)
        assertEquals(0f, tail.last())
    }

    @Test
    fun onlyMatchingHeroSlotsCanUseTheLiveReveal() {
        assertTrue(sharesHeroRevealFootprint(true, true, 0f, 0f))
        assertTrue(sharesHeroRevealFootprint(false, false, 1f, 16f / 9f))
        assertFalse(sharesHeroRevealFootprint(true, false, 1f, 1f))
        assertFalse(sharesHeroRevealFootprint(false, false, 1f, 0.56f))
        assertTrue(sharesHeroRevealFootprint(false, false, 0.56f, 0.56f))
        assertFalse(sharesHeroRevealFootprint(false, false, 0.56f, 0.63f))
        assertFalse(sharesHeroRevealFootprint(false, false, 1f, 0f))
    }

    @Test
    fun containedRevealCoordinatesFollowThePictureNotTheTextureView() {
        assertEquals(Rect(0f, 0f, 360f, 640f), canvasRevealFrame(360, 800, 9f / 16f, true, true))
        assertEquals(Rect(0f, 80f, 360f, 720f), canvasRevealFrame(360, 800, 9f / 16f, true, false))
        assertEquals(Rect(25f, 0f, 475f, 800f), canvasRevealFrame(500, 800, 9f / 16f, true, true))
        assertEquals(Rect(0f, 0f, 500f, 800f), canvasRevealFrame(500, 800, 9f / 16f, false, true))
        assertEquals(Rect(0f, 0f, 500f, 800f), canvasRevealFrame(500, 800, 0f, true, true))
    }

    @Test
    fun spatialAndOpacityStandbyShareTheSameClockAndGeneration() {
        val tracks = AutoMixCanvasTracks(42L, outgoing, incoming, null)
        val spatial = tracks.standbyReveal(allowSpatialMask = true)
        val fallback = tracks.standbyReveal(allowSpatialMask = false)
        assertEquals(42L, spatial.generation)
        assertEquals(spatial.generation, fallback.generation)
        assertSame(spatial.progress, fallback.progress)
        assertTrue(spatial.allowSpatialMask)
        assertFalse(fallback.allowSpatialMask)
        assertEquals(0f, spatial.progress())
        tracks.incomingClip = clip
        tracks.reportIncoming(true)
        tracks.sealIncoming()
        assertEquals(0f, fallback.progress(), message = "a ready frame is still hidden before the window")
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
    fun missingSelectionDoesNotConsumeTheGeneration() {
        val owner = AutoMixCanvasPair()
        assertNull(owner.start(visual, null))
        assertNull(owner.start(visual, incoming))
        val started = requireNotNull(owner.start(visual, outgoing))
        assertSame(started, owner.visual)
        assertNull(owner.start(visual, outgoing))
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
