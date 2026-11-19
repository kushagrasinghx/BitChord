package com.music.bitchord.ui.player

import coil3.Image
import com.music.bitchord.data.model.QueueEntryIdentity
import com.music.bitchord.playback.AutoMixArtworkWindow
import com.music.bitchord.playback.AutoMixVisualPhase
import com.music.bitchord.playback.AutoMixVisualState
import com.music.bitchord.playback.AutoMixVisualTrack
import org.jetbrains.skia.Canvas
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class AutoMixCoverTest {
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

    private fun cover() = DecodedAutoMixCover(object : Image {
        override val width = 1
        override val height = 1
        override val size = 4L
        override val shareable = true
        override fun draw(canvas: Canvas) = error("This test exercises cover ownership, not rendering")
    }, null)
}
