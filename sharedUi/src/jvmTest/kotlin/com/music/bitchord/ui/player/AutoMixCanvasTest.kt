package com.music.bitchord.ui.player

import com.music.bitchord.data.canvas.CanvasArtwork
import com.music.bitchord.data.model.QueueEntryIdentity
import com.music.bitchord.playback.AutoMixArtworkTuning
import com.music.bitchord.playback.AutoMixArtworkWindow
import com.music.bitchord.playback.AutoMixVisualPhase
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
    fun spatialAndOpacityStandbyShareTheSameClockAndGeneration() {
        val tracks = AutoMixCanvasTracks(42L, outgoing, incoming, null) { visual.copy(generation = 42L) }
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
    fun revealDrawUsesTheCurrentAudioSampleEvenWhenTheCollectorIsBehind() {
        val window = AutoMixArtworkWindow(1.0, 3.0)
        var sample = visual.copy(phase = AutoMixVisualPhase.MIXING, artworkWindow = window)
        val tracks = AutoMixCanvasTracks(visual.generation, outgoing, incoming, null) { sample }
        tracks.windowOpen = true
        tracks.incomingClip = clip
        tracks.reportIncoming(true)
        assertTrue(tracks.sealIncoming())
        tracks.progress = 0.25f
        sample = sample.copy(audioSeconds = 2.0, artworkProgress = 0.5f)
        assertEquals(AutoMixArtworkTuning.revealProgress(0.5f), tracks.standbyOpacity())

        sample = sample.copy(phase = AutoMixVisualPhase.CANCELLED, artworkWindow = null, artworkProgress = 0f)
        assertEquals(0f, tracks.standbyOpacity())
        sample = visual.copy(generation = visual.generation + 1, artworkWindow = window, artworkProgress = 0.75f)
        assertEquals(0f, tracks.standbyOpacity())
    }

    @Test
    fun readyIncomingTransfersWithoutReplacingItsClip() {
        val owner = AutoMixCanvasPair()
        val tracks = requireNotNull(owner.start(visual, outgoing))
        tracks.incomingClip = clip
        tracks.incomingReady = true
        assertTrue(tracks.sealIncoming())
        owner.finish(tracks)
        assertNull(owner.visual)
        assertSame(tracks, owner.promoted)
        assertSame(clip, owner.promoted?.incomingClip)
    }

    @Test
    fun frameAfterDeadlineCannotRejoinTheDissolve() {
        val tracks = AutoMixCanvasTracks(1L, outgoing, incoming, null) { visual }
        tracks.incomingClip = clip
        assertFalse(tracks.sealIncoming())
        tracks.reportIncoming(true)
        assertFalse(tracks.sealIncoming())
        assertFalse(tracks.liveIncoming)
    }

    @Test
    fun frameAfterAudioDeadlineCannotWinBeforeTheCollectorSealsTheChoice() {
        val window = AutoMixArtworkWindow(1.0, 3.0)
        val missed = visual.copy(
            phase = AutoMixVisualPhase.MIXING,
            swapSeconds = 0.5,
            artworkWindow = window,
            audioSeconds = window.startSeconds - AutoMixArtworkTuning.CANVAS_READY_MARGIN_SECONDS,
        )
        val tracks = AutoMixCanvasTracks(visual.generation, outgoing, incoming, null) { missed }
        tracks.incomingClip = clip
        tracks.reportIncoming(true)
        assertFalse(tracks.sealIncoming())
        assertFalse(tracks.liveIncoming)

        var sample = missed.copy(generation = visual.generation + 1L, audioSeconds = 0.5)
        val onTime = AutoMixCanvasTracks(visual.generation, outgoing, incoming, null) { sample }
        onTime.incomingClip = clip
        onTime.reportIncoming(true)
        assertFalse(onTime.incomingReady)
        sample = missed.copy(audioSeconds = missed.audioSeconds - 0.001)
        onTime.reportIncoming(true)
        assertTrue(onTime.sealIncoming())
        assertTrue(onTime.liveIncoming)
    }

    @Test
    fun acceptedClipThatLosesItsSurfaceFallsBackForTheRestOfTheMix() {
        val tracks = AutoMixCanvasTracks(1L, outgoing, incoming, null) { visual }
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
        tracks.incomingReady = true
        assertTrue(tracks.sealIncoming())
        owner.finish(tracks)
        owner.displayed(outgoing, null, false)
        owner.displayed(incoming, null, false)
        assertNull(owner.promoted)
        assertNull(owner.start(visual, outgoing))
    }
}
