package com.music.bitchord.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaylistOrderTest {
    /** Replays [moves] the way YouTube applies them, one after another. */
    private fun apply(start: List<String>, moves: List<PlaylistMove>): List<String> {
        val list = start.toMutableList()
        moves.forEach { move ->
            list.remove(move.setVideoId)
            val at = move.before?.let { list.indexOf(it) } ?: list.size
            list.add(at, move.setVideoId)
        }
        return list
    }

    @Test
    fun unchangedOrderSendsNothing() {
        assertTrue(playlistMoves(listOf("a", "b", "c"), listOf("a", "b", "c")).isEmpty())
    }

    @Test
    fun oneTrackDraggedUpIsOneMove() {
        val current = listOf("a", "b", "c", "d")
        val target = listOf("d", "a", "b", "c")
        val moves = playlistMoves(current, target)
        assertEquals(listOf(PlaylistMove("d", "a")), moves)
        assertEquals(target, apply(current, moves))
    }

    @Test
    fun oneTrackDraggedToTheEndMovesToTheEnd() {
        val current = listOf("a", "b", "c", "d")
        val target = listOf("b", "c", "d", "a")
        val moves = playlistMoves(current, target)
        assertEquals(listOf(PlaylistMove("a", null)), moves)
        assertEquals(target, apply(current, moves))
    }

    @Test
    fun anyRearrangementReplaysToTheTarget() {
        val current = (1..30).map { "e$it" }
        val random = kotlin.random.Random(7)
        repeat(50) {
            val target = current.shuffled(random)
            assertEquals(target, apply(current, playlistMoves(current, target)))
        }
    }

    @Test
    fun reversingMovesAllButOne() {
        val current = listOf("a", "b", "c", "d", "e")
        val target = current.reversed()
        val moves = playlistMoves(current, target)
        assertEquals(current.size - 1, moves.size)
        assertEquals(target, apply(current, moves))
    }

    @Test
    fun listsThatNoLongerMatchAreLeftAlone() {
        assertTrue(playlistMoves(listOf("a", "b"), listOf("b", "a", "c")).isEmpty())
        assertTrue(playlistMoves(listOf("a", "b"), listOf("b", "c")).isEmpty())
    }
}
