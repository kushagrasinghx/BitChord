package com.music.bitchord.ui.player

import androidx.compose.ui.unit.IntSize
import com.music.bitchord.data.canvas.CanvasArtwork
import com.music.bitchord.data.canvas.CanvasSource
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
    fun matchingGenerationDoesNotReserveAStandbyCanvas() {
        val owner = AutoMixCanvasPair()
        owner.displayed(outgoing, clip, rendered = true)
        assertNull(owner.start(visual.copy(artworkDecisionPending = true), outgoing))
        assertNull(owner.start(visual.copy(bypassArtwork = true), outgoing))
        assertNull(owner.visual)
        assertSame(clip, requireNotNull(owner.start(visual.copy(generation = 2), outgoing)).pinnedClip)
    }

    @Test
    fun sharedCanvasKeepsTheNativeStateKeyIncludingItsSelectedFallback() {
        val mounted = CanvasArtwork("main.m3u8", fallbackUrl = "fallback.mp4", title = "Outgoing", source = CanvasSource.APPLE)
        val selected = mounted.copy(title = "Incoming", artist = "Other credit")
        assertFalse(mounted == selected) // Android keys its rendition and rendered state on the object.
        assertSame(mounted, stableCanvasPresentation(mounted, selected))
        val changedFallback = selected.copy(fallbackUrl = "different.mp4")
        assertSame(changedFallback, stableCanvasPresentation(mounted, changedFallback))
        assertNull(stableCanvasPresentation(mounted, null))
    }

    @Test
    fun disablingArtworkRejectsTheHeldGenerationButNotTheNextMix() {
        val owner = AutoMixCanvasPair()
        val tracks = requireNotNull(owner.start(visual, outgoing))
        tracks.incomingClip = clip
        tracks.started = true
        assertTrue(tracks.usesGestureSleeve(gesturing = true))
        val disabled = AutoMixArtworkGate(enabled = false, skipThrough = visual.generation)
        assertFalse(disabled.allows(tracks.generation))
        owner.clear()
        assertNull(owner.visual)
        val enabled = disabled.copy(enabled = true)
        val remounted = AutoMixCanvasPair()
        assertNull(remounted.start(visual, outgoing, enabled))
        assertTrue(remounted.start(visual.copy(generation = visual.generation + 1), outgoing, enabled) != null)
    }

    @Test
    fun revealProgressMovesBetweenControllerSamplesButIsCappedAhead() {
        val sampled = visual.copy(
            phase = AutoMixVisualPhase.MIXING,
            audioSeconds = 1.0,
            artworkWindow = AutoMixArtworkWindow(0.0, 2.0),
            artworkProgress = 0.5f,
            sampledAtNanos = 1_000_000_000L,
        )
        assertEquals(0.5f, sampled.artworkProgressAt(1_000_000_000L))
        // 10ms after the sample: 0.01s of a 2s window further on.
        assertEquals(0.505f, sampled.artworkProgressAt(1_010_000_000L), 1e-4f)
        // A stalled controller cannot carry it more than the cap ahead.
        val capped = 0.5f + (AutoMixArtworkTuning.MAX_PROGRESS_EXTRAPOLATION_SECONDS / 2.0).toFloat()
        assertEquals(capped, sampled.artworkProgressAt(5_000_000_000L), 1e-4f)
        // Never behind the sample, and untouched without a window or once cancelled.
        assertEquals(0.5f, sampled.artworkProgressAt(500_000_000L))
        assertEquals(0.5f, sampled.copy(phase = AutoMixVisualPhase.CANCELLED).artworkProgressAt(1_010_000_000L))
    }

    @Test
    fun skippingOntoTheMixShortensThePrepareLeadInsteadOfDroppingTheDissolve() {
        val t = AutoMixArtworkTuning
        // Armed long ago: the usual lag and full window.
        val settled = requireNotNull(t.windowAfter(10.0, 20.0, preparedForSeconds = 5.0))
        assertEquals(10.0 + t.START_LAG_AFTER_SWAP, settled.startSeconds, 1e-9)
        assertEquals(t.WINDOW_SECONDS, settled.endSeconds - settled.startSeconds, 1e-9)
        // Just armed with room: waits out the whole prepare lead.
        val lead = requireNotNull(t.windowAfter(10.0, 20.0, preparedForSeconds = 0.0))
        assertEquals(10.0 + t.MIN_PREPARE_LEAD_SECONDS, lead.startSeconds, 1e-9)
        // Just armed by a skip with little overlap left: the lead gives way, the dissolve stays.
        val skipped = requireNotNull(t.windowAfter(10.0, 11.5, preparedForSeconds = 0.0))
        assertEquals(11.5, skipped.endSeconds, 1e-9)
        assertEquals(t.MIN_WINDOW_SECONDS, skipped.endSeconds - skipped.startSeconds, 1e-9)
        assertTrue(skipped.startSeconds >= 10.0)
        // Only an overlap shorter than a minimum window cuts.
        assertNull(t.windowAfter(10.0, 10.0 + t.MIN_WINDOW_SECONDS - 0.01, preparedForSeconds = 0.0))
    }

    @Test
    fun outgoingSpotifyCanvasRetiresInsteadOfJoiningTheReveal() {
        val spotify = CanvasArtwork("outgoing.mp4", source = CanvasSource.SPOTIFY)
        val owner = AutoMixCanvasPair()
        owner.displayed(outgoing, spotify, rendered = true)
        val tracks = requireNotNull(owner.start(visual, outgoing))
        assertNull(tracks.pinnedClip)
        owner.beginMix(tracks)
        assertNull(tracks.pinnedClip)
        assertSame(spotify, tracks.retiringClip)
        assertFalse(tracks.liveOutgoing)
    }

    @Test
    fun squareOutgoingCanvasStaysLiveAndIsPinned() {
        val square = CanvasArtwork("outgoing.mp4", source = CanvasSource.APPLE)
        val owner = AutoMixCanvasPair()
        owner.displayed(outgoing, square, rendered = true)
        val tracks = requireNotNull(owner.start(visual, outgoing))
        owner.beginMix(tracks)
        assertSame(square, tracks.pinnedClip)
        assertNull(tracks.retiringClip)
        assertTrue(tracks.liveOutgoing)
        assertFalse(tracks.freezeOutgoing)
        assertTrue(CanvasArtwork("x", source = CanvasSource.SPOTIFY).retiresBeforeAutoMix)
        assertFalse(square.retiresBeforeAutoMix)
    }

    @Test
    fun onlyStartedMixedArtworkUsesTheMovingSleeve() {
        val tracks = AutoMixCanvasTracks(1L, outgoing, incoming, clip) { visual }
        assertFalse(tracks.usesGestureSleeve(gesturing = true))
        tracks.started = true
        assertTrue(tracks.usesGestureSleeve(gesturing = true))
        assertFalse(tracks.usesGestureSleeve(gesturing = false))
        tracks.pinnedClip = null
        assertFalse(tracks.usesGestureSleeve(gesturing = true))
        tracks.incomingClip = clip
        assertTrue(tracks.usesGestureSleeve(gesturing = true))
    }

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
    fun onlyDistinctReadyCanvasesWithMatchingStationaryHeroSlotsShareTheFeatherCorrection() {
        val pinned = CanvasArtwork("outgoing.mp4")
        val mix = visual.copy(phase = AutoMixVisualPhase.MIXING, artworkWindow = AutoMixArtworkWindow(1.0, 3.0))
        val tracks = AutoMixCanvasTracks(visual.generation, outgoing, incoming, pinned) { mix }
        tracks.started = true
        tracks.incomingClip = clip
        assertNull(tracks.matchedHeroReveal(matchesFootprint = true, stationary = true))

        tracks.reportIncoming(true)
        assertTrue(tracks.sealIncoming())
        tracks.windowOpen = true
        tracks.progress = 0.5f
        assertNull(tracks.matchedHeroReveal(matchesFootprint = false, stationary = true))
        assertNull(tracks.matchedHeroReveal(matchesFootprint = true, stationary = false))

        val reveal = requireNotNull(tracks.matchedHeroReveal(matchesFootprint = true, stationary = true))
        val outgoingReveal = reveal.outgoingLiveHeroReveal { 1f }
        assertSame(outgoingReveal.livePair, reveal.incoming.livePair)
        assertSame(outgoingReveal.progress, reveal.incoming.progress)
        assertEquals(visual.generation, outgoingReveal.generation)
        assertEquals(tracks.standbyOpacity(), outgoingReveal.progress())
        assertEquals(IntSize.Zero, reveal.maskedIncomingBounds)
        assertNull(reveal.incoming.outgoingLiveAlpha)
        assertNull(outgoingReveal.outgoingStill)

        tracks.incomingClip = pinned
        assertNull(tracks.matchedHeroReveal(matchesFootprint = true, stationary = true))
        tracks.incomingClip = clip
        tracks.reportIncoming(false)
        assertNull(tracks.matchedHeroReveal(matchesFootprint = true, stationary = true))
        assertEquals(0f, outgoingReveal.progress())
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
