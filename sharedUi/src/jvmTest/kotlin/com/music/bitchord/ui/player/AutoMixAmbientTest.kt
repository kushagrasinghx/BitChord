package com.music.bitchord.ui.player

import com.music.bitchord.data.model.QueueEntryIdentity
import com.music.bitchord.playback.AutoMixVisualPhase
import com.music.bitchord.playback.AutoMixVisualState
import com.music.bitchord.playback.AutoMixVisualTrack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class AutoMixAmbientTest {
    private val outgoing = QueueEntryIdentity("a", null, 0)
    private val incoming = QueueEntryIdentity("b", null, 0)
    private val other = QueueEntryIdentity("c", null, 0)
    private val mix = AutoMixVisualState(
        1L, AutoMixVisualTrack(outgoing, 0, null), AutoMixVisualTrack(incoming, 1, null),
        phase = AutoMixVisualPhase.MIXING,
    )
    private val off = AutoMixArtworkGate(enabled = false)

    @Test
    fun onlyTheFullArtworkGateControlsAmbientHandoff() {
        val miniOff = AutoMixArtworkPreferences(artTransition = true, miniPlayerTransition = false).gate(automix = true)
        assertNull(directAutoMixAmbientSelection(mix, incoming, miniOff, null))
        assertEquals(1L to incoming, directAutoMixAmbientSelection(mix, incoming, off, null))
        assertNull(directAutoMixAmbientSelection(mix.copy(phase = AutoMixVisualPhase.ARMED), outgoing, off, null))
        assertNull(directAutoMixAmbientSelection(mix, other, off, null))
    }

    @Test
    fun reenableDoesNotReplayAndLateAssetsStillSettleWithoutAFade() {
        val reenabled = AutoMixArtworkGate(skipThrough = mix.generation)
        val selected = directAutoMixAmbientSelection(mix, outgoing, reenabled, null)
        val completed = mix.copy(phase = AutoMixVisualPhase.COMPLETED, swapSeconds = 1.0)
        val handedOff = directAutoMixAmbientSelection(completed, incoming, reenabled, selected)
        assertEquals(1L to incoming, handedOff)
        val ambient = AutoMixAmbient(direct = handedOff)
        assertEquals(handedOff, ambient.direct)
        assertNull(ambient.snapKey(targetReady = false), "A direct selection must not replace the backdrop's composition identity")
        assertNull(ambient.mixFor(targetReady = false))
        assertEquals(handedOff, AutoMixAmbient(settled = handedOff).snapKey(targetReady = true))
        assertEquals(handedOff, directAutoMixAmbientSelection(null, incoming, reenabled, handedOff))
        assertNull(directAutoMixAmbientSelection(completed, other, reenabled, handedOff))
        assertNull(directAutoMixAmbientSelection(completed, incoming, reenabled, null))
        assertNull(directAutoMixAmbientSelection(mix.copy(generation = 2L, outgoing = mix.incoming), incoming, reenabled, handedOff))
    }

    @Test
    fun terminalSampleOvertakingMetadataStillSnapsOnce() {
        val cancelled = mix.copy(phase = AutoMixVisualPhase.CANCELLED, swapSeconds = 1.0)
        assertEquals(1L to incoming, directAutoMixAmbientSelection(cancelled, incoming, off, null, outgoing))
        assertNull(directAutoMixAmbientSelection(cancelled, incoming, off, null, other))
        assertNull(directAutoMixAmbientSelection(cancelled.copy(swapSeconds = null), incoming, off, null, outgoing))
    }

    @Test
    fun preferenceCleanupDropsOnlyBlockedPreparedAmbient() {
        val original = preparedAutoMixAmbient
        try {
            val prepared = PreparedAutoMixAmbient(mix.generation, "incoming")
            preparedAutoMixAmbient = prepared
            val miniOff = AutoMixArtworkPreferences(artTransition = true, miniPlayerTransition = false).gate(automix = true)
            discardBlockedAutoMixAmbient(miniOff::allows)
            assertSame(prepared, preparedAutoMixAmbient)
            discardBlockedAutoMixAmbient(off::allows)
            assertNull(preparedAutoMixAmbient)
        } finally {
            preparedAutoMixAmbient = original
        }
    }
}
