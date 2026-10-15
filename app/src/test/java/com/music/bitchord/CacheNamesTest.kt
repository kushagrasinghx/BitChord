package com.music.bitchord

import com.music.bitchord.data.debrid.CacheNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The deterministic-filename cache check, pinned to a fixed example:
 * Monkeys Spinning Monkeys by Kevin MacLeod, catalogue track 359051850.
 */
class CacheNamesTest {

    @Test fun `filenames match the seeder convention exactly`() {
        val names = CacheNames.candidates("Monkeys Spinning Monkeys", "Kevin MacLeod", 359051850)
            .map { it.name }
        assertEquals(
            listOf(
                "Monkeys Spinning Monkeys - Kevin MacLeod [BitTor 359051850 - Hi-Res].flac",
                "Monkeys Spinning Monkeys - Kevin MacLeod [BitTor 359051850 - HiFi CD].flac",
                "Monkeys Spinning Monkeys - Kevin MacLeod [BitTor 359051850 - 320kbps].m4a",
                "Monkeys Spinning Monkeys - Kevin MacLeod [BitTor 359051850 - 96kbps].m4a",
            ),
            names,
        )
    }

    @Test fun `hashes are lowercase hex md5`() {
        // Fixed vector first, so a broken digest fails loudly instead of
        // merely disagreeing with itself below.
        assertEquals("900150983cd24fb0d6963f7d28e17f72", CacheNames.md5Hex("abc"))
        val md5 = CacheNames.md5Hex("Monkeys Spinning Monkeys - Kevin MacLeod [BitTor 359051850 - Hi-Res].flac")
        assertTrue(md5.matches(Regex("[0-9a-f]{32}")))
    }

    @Test fun `clean strips exactly the hostile characters`() {
        assertEquals("ACDC Live", CacheNames.clean("AC/DC: Live?"))
        assertEquals("ab", CacheNames.clean("  a|b\"  "))
        assertEquals("", CacheNames.clean("///"))
    }

    @Test fun `only name-equal hits count`() {
        val wanted = CacheNames.candidates("Song", "Singer", 1L).associateBy { it.md5 }
        val byName = wanted.values.associate { it.name to it.md5 }
        val hits = mapOf(
            byName["Song - Singer [BitTor 1 - 320kbps].m4a"]!! to
                "Song - Singer [BitTor 1 - 320kbps].m4a",
            // Present by hash but holding another file: not this track.
            byName["Song - Singer [BitTor 1 - Hi-Res].flac"]!! to
                "Something else entirely.flac",
        )
        assertEquals(listOf(CacheNames.TIERS[2]), CacheNames.matchedTiers(wanted, hits))
    }

    @Test fun `tier formats describe the real container`() {
        assertEquals("flac", CacheNames.formatOf(CacheNames.TIERS[0]).codec)
        assertEquals(null, CacheNames.formatOf(CacheNames.TIERS[0]).kbps)
        assertEquals("mp4", CacheNames.formatOf(CacheNames.TIERS[2]).codec)
        assertEquals(320, CacheNames.formatOf(CacheNames.TIERS[2]).kbps)
        assertEquals(96, CacheNames.formatOf(CacheNames.TIERS[3]).kbps)
    }

    @Test fun `query overlap separates same words from noise`() {
        assertTrue(CacheNames.queryOverlap("blinding lights weeknd", "Blinding Lights", "The Weeknd") >= 0.5)
        assertEquals(0.0, CacheNames.queryOverlap("xyzzy qqq", "Blinding Lights", "The Weeknd"), 0.0)
    }
}
