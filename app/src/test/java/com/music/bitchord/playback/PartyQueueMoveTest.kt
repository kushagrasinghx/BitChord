package com.music.bitchord.playback

import com.music.bitchord.data.listentogether.PartyTrack
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PartyQueueMoveTest {

    @Test
    fun partyTrackKeepsAutoplaySectionMetadataOnTheWire() {
        val encoded = Json.encodeToString(PartyTrack.serializer(), PartyTrack("A", fromAutoplay = true))
        val decoded = Json.decodeFromString(PartyTrack.serializer(), encoded)

        assertEquals(true, decoded.fromAutoplay)
    }

    @Test
    fun testDetectSingleMoveForward() {
        val oldList = listOf("A", "B", "C", "D")
        // Move "B" (index 1) to index 3 -> "A", "C", "D", "B"
        val newList = listOf("A", "C", "D", "B")
        val delta = detectSingleMove(oldList, newList)

        assertNotNull(delta)
        assertEquals(1, delta?.fromIndex)
        assertEquals(3, delta?.toIndex)
        assertEquals("B", delta?.videoId)
    }

    @Test
    fun testDetectSingleMoveBackward() {
        val oldList = listOf("A", "C", "D", "B")
        // Move "B" (index 3) to index 1 -> "A", "B", "C", "D"
        val newList = listOf("A", "B", "C", "D")
        val delta = detectSingleMove(oldList, newList)

        assertNotNull(delta)
        assertEquals(3, delta?.fromIndex)
        assertEquals(1, delta?.toIndex)
        assertEquals("B", delta?.videoId)
    }

    @Test
    fun testDetectSingleMoveWithBaseOffset() {
        val oldList = listOf("B", "C", "D")
        // Move "D" (local index 2) to local index 0 -> "D", "B", "C"
        val newList = listOf("D", "B", "C")
        val delta = detectSingleMove(oldList, newList, baseOffset = 5)

        assertNotNull(delta)
        assertEquals(7, delta?.fromIndex)
        assertEquals(5, delta?.toIndex)
        assertEquals("D", delta?.videoId)
    }

    @Test
    fun testIdenticalListsReturnNull() {
        val list = listOf("A", "B", "C")
        assertNull(detectSingleMove(list, list))
    }

    @Test
    fun testDifferentSizesReturnNull() {
        val oldList = listOf("A", "B")
        val newList = listOf("A", "B", "C")
        assertNull(detectSingleMove(oldList, newList))
    }

    @Test
    fun testMultipleSwapsReturnNull() {
        val oldList = listOf("A", "B", "C", "D")
        // Two independent swaps: (A, B) and (C, D) -> "B", "A", "D", "C"
        val newList = listOf("B", "A", "D", "C")
        assertNull(detectSingleMove(oldList, newList))
    }

    @Test
    fun testDuplicatesInList() {
        val oldList = listOf("A", "B", "A", "C")
        // Both moving "B" to index 2 or moving "A" to index 0/1 produce "A", "A", "B", "C"
        val newList = listOf("A", "A", "B", "C")
        val delta = detectSingleMove(oldList, newList)

        assertNotNull(delta)
        // Verify that applying the detected delta produces newList
        val reconstructed = oldList.toMutableList().apply {
            val item = removeAt(delta!!.fromIndex)
            add(delta.toIndex, item)
        }
        assertEquals(newList, reconstructed)
    }

    @Test
    fun testPartyPlaybackSerializationPreservesSeparateSequences() {
        val playback = com.music.bitchord.data.listentogether.PartyPlayback(
            seq = 10,
            queueSeq = 25,
            queueIndex = 2,
            isPlaying = true,
            positionMs = 5000L,
            anchorMs = 100000L,
        )
        val encoded = Json.encodeToString(com.music.bitchord.data.listentogether.PartyPlayback.serializer(), playback)
        val decoded = Json.decodeFromString(com.music.bitchord.data.listentogether.PartyPlayback.serializer(), encoded)

        assertEquals(10L, decoded.seq)
        assertEquals(25L, decoded.queueSeq)
        assertEquals(true, decoded.isPlaying)
        assertEquals(5000L, decoded.positionMs)
        assertEquals(100000L, decoded.anchorMs)
    }

    @Test
    fun testQueueOperationDoesNotAdvancePlaybackSeqContract() {
        val initialPlayback = com.music.bitchord.data.listentogether.PartyPlayback(
            seq = 5,
            queueSeq = 1,
            isPlaying = true,
            positionMs = 12000L,
            anchorMs = 50000L,
        )
        // A queue move arrives: queueSeq advances, playback seq must remain 5
        val updatedPlayback = initialPlayback.copy(
            queueSeq = initialPlayback.queueSeq + 1,
        )

        assertEquals(5L, updatedPlayback.seq)
        assertEquals(2L, updatedPlayback.queueSeq)
        // Playback anchor and position are untouched
        assertEquals(initialPlayback.anchorMs, updatedPlayback.anchorMs)
        assertEquals(initialPlayback.positionMs, updatedPlayback.positionMs)
    }
}
