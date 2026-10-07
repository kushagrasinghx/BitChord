package com.music.bitchord.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopQualityTest {

    @Test
    fun aRungDecidesWhichKindsOfSourceMayAnswer() {
        // Everything, including the sources that can serve bit-exact audio.
        DesktopSourceKind.entries.forEach {
            assertTrue(DesktopAudioQuality.LOSSLESS.permits(it), it.name)
        }
        // No lossless answer is wanted, and a source that can serve one is the slow half of the
        // list.
        assertFalse(DesktopAudioQuality.HIGH.permits(DesktopSourceKind.ADDON))
        assertFalse(DesktopAudioQuality.HIGH.permits(DesktopSourceKind.CUSTOM_MODULE))
        assertTrue(DesktopAudioQuality.HIGH.permits(DesktopSourceKind.JIOSAAVN))
        // The lower rungs are YouTube's own ladder and nothing else.
        listOf(DesktopAudioQuality.MEDIUM, DesktopAudioQuality.LOW).forEach { rung ->
            assertFalse(rung.permits(DesktopSourceKind.ADDON), rung.name)
            assertFalse(rung.permits(DesktopSourceKind.JIOSAAVN), rung.name)
        }
    }

    @Test
    fun youtubeAnswersOnEveryRung() {
        // It is what the cap applies to, and the only source that can answer at all when the ones
        // above it are skipped.
        DesktopAudioQuality.entries.forEach {
            assertTrue(it.permits(DesktopSourceKind.YOUTUBE), it.name)
        }
    }

    @Test
    fun onlyTheLowestRungIsActuallyABitrateCap() {
        assertEquals(64, DesktopAudioQuality.LOW.maxKbps)
        listOf(DesktopAudioQuality.MEDIUM, DesktopAudioQuality.HIGH, DesktopAudioQuality.LOSSLESS)
            .forEach { assertEquals(Int.MAX_VALUE, it.maxKbps, it.name) }
    }

    @Test
    fun anOlderBuildsCeilingIsMigratedRatherThanReinterpreted() {
        // Three-rung builds had no source gating, so their "HIGH" meant "best available from
        // anything" — this model's LOSSLESS.
        assertEquals(DesktopAudioQuality.LOSSLESS, DesktopAudioQuality.stored("HIGH", hasFourRungs = false))
        assertEquals(DesktopAudioQuality.MEDIUM, DesktopAudioQuality.stored("MEDIUM", hasFourRungs = false))
        assertEquals(DesktopAudioQuality.LOW, DesktopAudioQuality.stored("LOW", hasFourRungs = false))
        // Once the four rungs have been seen, a stored HIGH is a real choice.
        assertEquals(DesktopAudioQuality.HIGH, DesktopAudioQuality.stored("HIGH", hasFourRungs = true))
    }

    @Test
    fun anUnsetOrUnreadableCeilingDefaultsToLossless() {
        assertEquals(DesktopAudioQuality.LOSSLESS, DesktopAudioQuality.stored(null, hasFourRungs = true))
        assertEquals(DesktopAudioQuality.LOSSLESS, DesktopAudioQuality.stored("", hasFourRungs = true))
        assertEquals(DesktopAudioQuality.LOSSLESS, DesktopAudioQuality.stored("nonsense", hasFourRungs = true))
        // The download rungs' spelling, in case one was ever written here.
        assertEquals(DesktopAudioQuality.MEDIUM, DesktopAudioQuality.stored("STANDARD", hasFourRungs = true))
    }
}
