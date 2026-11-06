package com.music.bitchord

import com.music.bitchord.data.sources.StreamFormat
import com.music.bitchord.playback.QualityUpgrade
import com.music.bitchord.playback.TIER_ATMOS
import com.music.bitchord.playback.TIER_LOSSLESS
import com.music.bitchord.playback.isReusableUpgradeKey
import com.music.bitchord.playback.missingRanges
import com.music.bitchord.playback.reusableTierOf
import com.music.bitchord.playback.tierPlayable
import com.music.bitchord.playback.upgradeKeysOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules that decide whether an upgrade swapped in mid-song is kept whole
 * on disk and read by the next play of the track, instead of that play
 * starting on the old copy and swapping up to the same file again.
 */
class KeptUpgradeTest {

    @Test
    fun `upgrade tags are the two generations a swap can leave, the later first`() {
        assertEquals(listOf("hifi-2", "hifi"), QualityUpgrade.UPGRADE_TAGS)
        assertTrue(QualityUpgrade.isUpgradeTag("hifi"))
        assertTrue(QualityUpgrade.isUpgradeTag("hifi-2"))
        assertFalse("a revert is not an upgrade", QualityUpgrade.isUpgradeTag("original"))
        assertFalse(QualityUpgrade.isUpgradeTag(null))
        assertFalse(QualityUpgrade.isUpgradeTag("alt"))
    }

    /** The later generation wins: it is the one that beat the earlier. */
    @Test
    fun `a track's upgrade keys are looked at newest first`() {
        assertEquals(listOf("90DKXLbzLto#hifi-2", "90DKXLbzLto#hifi"), upgradeKeysOf("90DKXLbzLto"))
    }

    @Test
    fun `only a YouTube track's own upgrade rendition is reusable`() {
        assertTrue(isReusableUpgradeKey("90DKXLbzLto#hifi"))
        assertTrue(isReusableUpgradeKey("90DKXLbzLto#hifi-2"))

        assertFalse("the base entry", isReusableUpgradeKey("90DKXLbzLto"))
        assertFalse("a substitute's entry", isReusableUpgradeKey("90DKXLbzLto#alt"))
        assertFalse("a revert's entry", isReusableUpgradeKey("90DKXLbzLto#original"))
        assertFalse("the watch's capped copy", isReusableUpgradeKey("90DKXLbzLto#hifi#watch"))
        assertFalse("a source row's rendition", isReusableUpgradeKey("tidal-1|12345#hifi"))
        assertFalse("no track at all", isReusableUpgradeKey("#hifi"))
    }

    @Test
    fun `lossless and Atmos are kept, anything lossy is not`() {
        assertEquals(TIER_LOSSLESS, reusableTierOf(StreamFormat(codec = "flac")))
        assertEquals(TIER_LOSSLESS, reusableTierOf(StreamFormat(codec = "alac")))
        assertEquals(TIER_ATMOS, reusableTierOf(StreamFormat(codec = "eac3-joc")))

        assertNull(reusableTierOf(StreamFormat(codec = "aac", kbps = 320)))
        assertNull(reusableTierOf(StreamFormat(codec = "opus", kbps = 160)))
        assertNull("an unstated codec is not lossless", reusableTierOf(StreamFormat(kbps = 1411)))
    }

    /** An Atmos mix kept while the setting was on must not come back once it is off. */
    @Test
    fun `a kept Atmos mix follows the Atmos setting, lossless always plays`() {
        assertTrue(tierPlayable(TIER_LOSSLESS, atmosWanted = false))
        assertTrue(tierPlayable(TIER_LOSSLESS, atmosWanted = true))
        assertTrue(tierPlayable(TIER_ATMOS, atmosWanted = true))
        assertFalse(tierPlayable(TIER_ATMOS, atmosWanted = false))
        assertFalse(tierPlayable(null, atmosWanted = true))
        assertFalse(tierPlayable("320", atmosWanted = true))
    }

    @Test
    fun `missing ranges are the chunks not fully on disk`() {
        assertEquals(
            listOf(0L to 2L, 2L to 2L, 4L to 1L),
            missingRanges(length = 5, chunk = 2) { _, _ -> 0L },
        )
        assertEquals(emptyList<Pair<Long, Long>>(), missingRanges(length = 5, chunk = 2) { _, size -> size })
        // The stretch between Automix's head and the swap point: only the
        // middle chunk is missing, and one byte short still counts.
        assertEquals(
            listOf(2L to 2L),
            missingRanges(length = 6, chunk = 2) { position, size -> if (position == 2L) size - 1 else size },
        )
        assertEquals(emptyList<Pair<Long, Long>>(), missingRanges(length = 0, chunk = 2) { _, _ -> 0L })
    }
}
