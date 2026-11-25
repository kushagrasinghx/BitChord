package com.music.bitchord.ui.player

import androidx.compose.ui.unit.IntSize
import coil3.Image
import com.music.bitchord.data.canvas.CanvasArtwork
import com.music.bitchord.data.model.QueueEntryIdentity
import com.music.bitchord.playback.AutoMixArtworkTuning
import com.music.bitchord.playback.AutoMixArtworkWindow
import com.music.bitchord.playback.AutoMixVisualPhase
import com.music.bitchord.playback.AutoMixVisualState
import com.music.bitchord.playback.AutoMixVisualTrack
import org.jetbrains.skia.Canvas
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AutoMixCoverTest {
    @Test
    fun incomingStillUsesTheSettledSquareOrBannerInsteadOfTheOutgoingSleeve() {
        for (fullBleed in listOf(false, true)) {
            val end = if (fullBleed) 1f else 0f
            val promoted = autoMixStaticSleeveShare(fullBleed, end, revealing = false)
            assertEquals(promoted, autoMixStaticSleeveShare(fullBleed, 0.72f, revealing = true))
            assertEquals(0.72f, autoMixStaticSleeveShare(fullBleed, 0.72f, revealing = false))
        }
    }

    private val outgoing = QueueEntryIdentity("a", null, 0)
    private val incoming = QueueEntryIdentity("b", null, 0)
    private val visual = AutoMixVisualState(
        1L, AutoMixVisualTrack(outgoing, 0, null), AutoMixVisualTrack(incoming, 1, null),
        phase = AutoMixVisualPhase.MIXING, swapSeconds = 0.5,
        artworkWindow = AutoMixArtworkWindow(1.0, 3.0), audioSeconds = 0.5,
    )

    @Test
    fun missingThumbnailAtOverlapUsesFallbackAndCannotJoinLater() {
        val base = cover()
        var prepared: DecodedAutoMixCover? = null
        val state = AutoMixCoverState({ base }, { prepared })
        state.bind(incoming, null, visual)
        state.update(visual)
        assertNull(state.framesFor(incoming))
        prepared = cover()
        state.update(visual.copy(audioSeconds = 0.75))
        state.update(visual.copy(audioSeconds = 1.5, artworkProgress = 0.25f))
        assertNull(state.framesFor(incoming))
    }

    @Test
    fun preparedThumbnailFollowsTheClockAndPromotesOnCompletion() {
        val base = cover()
        val next = cover()
        val state = AutoMixCoverState({ base }, { next })
        state.bind(incoming, null, visual)
        state.update(visual)
        assertSame(base, state.framesFor(incoming)?.base)
        assertSame(next, state.framesFor(incoming)?.incoming)
        assertEquals(0f, state.framesFor(incoming)?.progress())
        state.update(visual.copy(audioSeconds = 1.5, artworkProgress = 0.25f))
        val frames = assertNotNull(state.framesFor(incoming))
        assertSame(next, frames.incoming)
        assertEquals(0.25f, frames.progress())
        state.update(visual.copy(phase = AutoMixVisualPhase.COMPLETED))
        assertSame(next, state.framesFor(incoming)?.base)
        assertNull(state.framesFor(incoming)?.incoming)
    }

    @Test
    fun missingDeadlineOrCancellationCannotStartTheSameGenerationLater() {
        val base = cover()
        var prepared: DecodedAutoMixCover? = null
        val state = AutoMixCoverState({ base }, { prepared })
        state.bind(outgoing, null, visual)
        state.update(visual)
        state.update(visual.copy(audioSeconds = 1.1, artworkProgress = 0.05f))
        prepared = cover()
        state.update(visual.copy(audioSeconds = 1.2, artworkProgress = 0.1f))
        assertNull(state.framesFor(outgoing))
        state.update(visual.copy(phase = AutoMixVisualPhase.CANCELLED))
        state.update(visual.copy(audioSeconds = 1.5, artworkProgress = 0.25f))
        assertNull(state.framesFor(outgoing))
    }

    @Test
    fun alreadyPromotedPixelsSurviveMetadataArrivingBeforeTheNextCollectorTick() {
        val base = cover()
        val next = cover()
        val last = cover()
        val state = AutoMixCoverState({ if (it.generation == 1L) base else null }, { if (it == 1L) next else last })
        state.bind(incoming, null, visual)
        state.update(visual)
        state.update(visual.copy(phase = AutoMixVisualPhase.COMPLETED))
        val reverse = visual.copy(generation = 2L, outgoing = visual.incoming, incoming = visual.outgoing)
        state.bind(outgoing, null, reverse)
        state.update(reverse)
        val frames = assertNotNull(state.framesFor(outgoing))
        assertSame(next, frames.base)
        assertSame(last, frames.incoming)
        assertEquals(2L, frames.generation)
    }

    @Test
    fun liveToStillUsesTheActiveGenerationAndAudioProgressAcrossMetadataHandoff() {
        val base = cover()
        val next = cover()
        val other = QueueEntryIdentity("c", null, 0)
        val coverState = AutoMixCoverState({ base }, { next })
        val mixes = listOf(
            visual,
            visual.copy(generation = 2L),
            visual.copy(generation = 3L, incoming = AutoMixVisualTrack(other, 1, null)),
        )
        for (mix in mixes) {
            var sample = mix
            val source = mix.outgoing.identity
            val destination = mix.incoming.identity
            val tracks = AutoMixCanvasTracks(mix.generation, source, destination, CanvasArtwork("live.mp4")) { sample }
            tracks.started = true
            tracks.sealIncoming()
            assertTrue(tracks.liveOutgoing && tracks.selectedStill)
            coverState.bind(source, null, mix)
            coverState.update(mix)
            val frames = assertNotNull(coverState.framesFor(source))
            coverState.bind(destination, null, mix)
            assertSame(frames, coverState.framesFor(destination))
            assertSame(next, frames.incoming)
            assertEquals(tracks.generation, frames.generation)
            sample = mix.copy(audioSeconds = 2.0, artworkProgress = 0.5f)
            assertEquals(AutoMixArtworkTuning.revealProgress(0.5f), tracks.reveal())
            coverState.update(sample.copy(phase = AutoMixVisualPhase.COMPLETED))
            assertSame(next, coverState.framesFor(destination)?.base)
        }
    }

    @Test
    fun outgoingBannerCompositorRequiresTheSameLiveToStillGenerationAndMeasuredDestination() {
        val tracks = AutoMixCanvasTracks(visual.generation, outgoing, incoming, CanvasArtwork("outgoing.mp4")) { visual }
        val frames = AutoMixCoverFrames(cover(), cover(), visual.generation)
        val bounds = IntSize(1080, 1272)
        fun reveal(candidate: AutoMixCoverFrames? = frames, size: IntSize = bounds) =
            tracks.outgoingStillBannerReveal(candidate, size, 0.42f) { 1f }

        assertNull(reveal()) // Not mixed yet.
        tracks.started = true
        assertNull(reveal(size = IntSize.Zero))
        assertNull(reveal(AutoMixCoverFrames(frames.base, frames.incoming, visual.generation + 1)))
        assertNull(reveal(AutoMixCoverFrames(frames.base))) // Already promoted or no destination.
        val selected = assertNotNull(reveal())
        assertEquals(visual.generation, selected.generation)
        assertEquals(bounds, selected.outgoingStill?.bounds)
        assertEquals(0.42f, selected.outgoingStill?.bottomFade)
        tracks.incomingClip = CanvasArtwork("incoming.mp4")
        tracks.incomingReady = true
        tracks.sealIncoming()
        assertNull(reveal()) // A live destination owns a different path.
    }

    @Test
    fun artworkPreferencesAreIndependentUnderTheAutoMixParent() {
        val defaults = AutoMixArtworkPreferences()
        // Both artwork transitions are opt-in.
        assertFalse(defaults.gate(automix = true).allows(1L))
        assertFalse(defaults.gate(automix = true, mini = true).allows(1L))
        for (autoMix in listOf(false, true)) for (art in listOf(false, true)) for (mini in listOf(false, true)) {
            val preferences = defaults.copy(artTransition = art, miniPlayerTransition = mini)
            assertEquals(autoMix && art, preferences.gate(automix = autoMix).allows(1L))
            assertEquals(autoMix && mini, preferences.gate(automix = autoMix, mini = true).allows(1L))
            assertEquals(mini, preferences.miniPlayerTransition)
        }
    }

    @Test
    fun disablingFullArtDoesNotCancelTheMiniPlayersCurrentGeneration() {
        val base = cover()
        val next = cover()
        val mini = AutoMixCoverState({ base }, { next })
        mini.bind(incoming, null, visual)
        mini.update(visual)
        val disabled = AutoMixArtworkPreferences(artTransition = false, miniPlayerTransition = true, skipArtworkThrough = visual.generation)
        assertEquals(false, disabled.allows(automix = true, generation = visual.generation))
        val miniGate = disabled.gate(automix = true, mini = true)
        mini.update(visual, miniGate)
        assertSame(next, mini.framesFor(incoming, miniGate)?.incoming)
        val reenabled = disabled.copy(artTransition = true)
        assertEquals(false, reenabled.allows(automix = true, generation = visual.generation))
        assertTrue(reenabled.allows(automix = true, generation = visual.generation, mini = true))
    }

    @Test
    fun miniOnlyCancellationSettlesSelectedArtworkWithoutReplayingTheMix() {
        val base = cover()
        val next = cover()
        val full = AutoMixCoverState({ base }, { next })
        val mini = AutoMixCoverState({ base }, { next })
        for (surface in listOf(full, mini)) {
            surface.bind(incoming, null, visual)
            surface.update(visual)
            assertSame(next, surface.framesFor(incoming)?.incoming)
        }
        val disabled = AutoMixArtworkPreferences(artTransition = true, miniPlayerTransition = false, skipMiniThrough = visual.generation)
        val miniGate = disabled.gate(automix = true, mini = true)
        assertSame(next, mini.framesFor(incoming, miniGate)?.base)
        assertNull(mini.framesFor(incoming, miniGate)?.incoming)
        mini.update(visual, miniGate)
        val enabled = disabled.copy(miniPlayerTransition = true)
        mini.update(visual, enabled.gate(automix = true, mini = true))
        assertNull(mini.framesFor(incoming)?.incoming)
        assertTrue(enabled.gate(automix = true, mini = true).allows(visual.generation + 1))
        assertSame(next, full.framesFor(incoming, enabled.gate(automix = true))?.incoming)
    }

    private fun cover() = DecodedAutoMixCover(object : Image {
        override val width = 1
        override val height = 1
        override val size = 4L
        override val shareable = true
        override fun draw(canvas: Canvas) = error("This test exercises cover ownership, not rendering")
    }, null)
}
