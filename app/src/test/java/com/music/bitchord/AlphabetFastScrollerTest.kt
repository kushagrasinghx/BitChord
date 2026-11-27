package com.music.bitchord

import com.music.bitchord.ui.components.alphabetIndexBucket
import com.music.bitchord.ui.components.buildAlphabetSectionMap
import org.junit.Assert.assertEquals
import org.junit.Test

class AlphabetFastScrollerTest {

    @Test
    fun `alphabetIndexBucket normalizes letters and groups non-letters into hash`() {
        assertEquals('A', alphabetIndexBucket("apricot"))
        assertEquals('Z', alphabetIndexBucket("  Zebra"))
        assertEquals('#', alphabetIndexBucket("1989"))
        assertEquals('#', alphabetIndexBucket("(Bonus Track)"))
        assertEquals('#', alphabetIndexBucket(""))
    }

    @Test
    fun `buildAlphabetSectionMap records first index for each bucket`() {
        val titles = listOf(
            "123",
            "Alpha",
            "Another",
            "Beta",
            "Zeta",
        )
        val map = buildAlphabetSectionMap(titles)
        assertEquals(0, map['#'])
        assertEquals(1, map['A'])
        assertEquals(3, map['B'])
        assertEquals(4, map['Z'])
        assertEquals(null, map['C'])
    }
}
