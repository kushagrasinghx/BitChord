package com.music.bitchord.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Which side of the panel each line is sung from. */
class DesktopLyricAlignmentTest {

    @Test
    fun anUnattributedSongStaysOnOneSide() {
        val sides = desktopLineAlignments(listOf(null, null, null), emptyMap())
        assertTrue(sides.all { it == DesktopLyricAlignment.Start })
    }

    @Test
    fun twoVoicesAlternate() {
        val sides = desktopLineAlignments(
            listOf("v1", "v1", "v2", "v1"),
            mapOf("v1" to "person", "v2" to "person"),
        )
        assertEquals(
            listOf(
                DesktopLyricAlignment.Start,
                DesktopLyricAlignment.Start,
                DesktopLyricAlignment.End,
                DesktopLyricAlignment.Start,
            ),
            sides,
        )
    }

    @Test
    fun aThirdVoiceGetsTheOppositeSideRatherThanSharingOne() {
        val sides = desktopLineAlignments(
            listOf("v1", "v2", "v3"),
            mapOf("v1" to "person", "v2" to "person", "v3" to "person"),
        )
        assertEquals(DesktopLyricAlignment.Start, sides[0])
        assertEquals(DesktopLyricAlignment.End, sides[1])
        assertEquals(DesktopLyricAlignment.Start, sides[2])
    }

    @Test
    fun everyoneAtOnceBelongsToNeitherSide() {
        // A chorus sung by the group sits on the left and must not disturb whose turn it is.
        val sides = desktopLineAlignments(
            listOf("v1", "v1000", "v2"),
            mapOf("v1" to "person", "v2" to "person"),
        )
        assertEquals(DesktopLyricAlignment.Start, sides[0])
        assertEquals(DesktopLyricAlignment.Start, sides[1])
        assertEquals(DesktopLyricAlignment.End, sides[2])
    }

    @Test
    fun aSongLaidOutEntirelyOnTheRightIsFlipped() {
        // Apple's reserved "other singer" opens the alternation away from the left.
        val sides = desktopLineAlignments(listOf("v2000", "v2000", "v2000"), emptyMap())
        assertTrue(sides.all { it == DesktopLyricAlignment.Start })
    }
}
