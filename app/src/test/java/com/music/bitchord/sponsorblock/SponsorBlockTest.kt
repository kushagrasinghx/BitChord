package com.music.bitchord.sponsorblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SponsorBlockTest {

    @Test
    fun parsesSortsAndFiltersSegments() {
        val body = """
            [
              {"category":"outro","segment":[200.5,210],"UUID":"b"},
              {"category":"sponsor","segment":[10,20],"UUID":"a"},
              {"category":"music_offtopic","segment":[50,40],"UUID":"inverted"},
              {"category":"unknown","segment":[1,2],"UUID":"x"}
            ]
        """.trimIndent()
        val segments = SponsorBlockClient.parseSegments(body)
        assertEquals(listOf("a", "b"), segments.map { it.uuid })
        assertEquals(SponsorBlockCategory.SPONSOR, segments[0].category)
        assertEquals(10_000L, segments[0].startMs)
        assertEquals(200_500L, segments[1].startMs)
    }

    @Test
    fun malformedOrEmptyInputYieldsNothing() {
        assertTrue(SponsorBlockClient.parseSegments("").isEmpty())
        assertTrue(SponsorBlockClient.parseSegments("not json").isEmpty())
        assertTrue(SponsorBlockClient.parseSegments("{}").isEmpty())
    }

    @Test
    fun segmentContainsIsStartInclusiveEndExclusive() {
        val s = SponsorSegment("u", SponsorBlockCategory.INTRO, 1000, 2000)
        assertFalse(s.contains(999))
        assertTrue(s.contains(1000))
        assertTrue(s.contains(1999))
        assertFalse(s.contains(2000))
    }

    @Test
    fun everyCategoryKeyRoundTrips() {
        SponsorBlockCategory.entries.forEach {
            assertEquals(it, SponsorBlockCategory.fromKey(it.key))
        }
    }

    @Test
    fun userManualSeekIntoSegmentWhitelistsIt() {
        val segment = SponsorSegment("uuid-intro", SponsorBlockCategory.INTRO, 0, 5000)
        SponsorBlockManager.setSegmentsForTest("test-vid", listOf(segment))

        // Normal check skips past segment
        var skippedTo = -1L
        SponsorBlockManager.checkAndSkip(2000) { skippedTo = it }
        assertEquals(5030L, skippedTo)

        // Rule 1: User seeks to 0 to restart the track -> skips immediately to music start, NOT whitelisted
        skippedTo = -1L
        SponsorBlockManager.onUserSeek(0) { skippedTo = it }
        assertEquals(5030L, skippedTo)

        // Rule 2: User explicitly scrubs into the interior (e.g. 2500ms) -> whitelisted, no skip
        skippedTo = -1L
        SponsorBlockManager.onUserSeek(2500) { skippedTo = it }
        assertEquals(-1L, skippedTo)
        SponsorBlockManager.checkAndSkip(2500) { skippedTo = it }
        assertEquals(-1L, skippedTo) // Should NOT skip

        // When playback advances beyond the intro, whitelist clears
        SponsorBlockManager.checkAndSkip(6000) {}
        skippedTo = -1L
        SponsorBlockManager.checkAndSkip(2500) { skippedTo = it }
        assertEquals(5030L, skippedTo) // Skips again if entered anew
    }
}
