package com.music.bitchord.data.sources

import com.music.bitchord.data.model.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression tests for kushagrasinghx/BitChord#1854: a track whose title sits
 * entirely inside brackets — Radiohead's "(Nice Dream)", Slipknot's "(sic)" —
 * was never looked up in any source, because parseTitle() filed the whole
 * title as packaging context and queries() then returned an empty list.
 */
class TrackMatcherBracketedTitleTest {
    private fun song(title: String, artist: String) =
        Song(videoId = "x", title = title, artist = artist, thumbnailUrl = null)

    @Test
    fun aFullyBracketedTitleIsQueried() {
        assertEquals(
            listOf("nice dream radiohead", "nice dream"),
            TrackMatcher.queries(TrackMatcher.Target("(Nice Dream)", "Radiohead")),
        )
        assertEquals(
            listOf("sic slipknot", "sic"),
            TrackMatcher.queries(TrackMatcher.Target("(sic)", "Slipknot")),
        )
    }

    @Test
    fun aFullyBracketedTitleMatchesItsUnbracketedListing() {
        assertTrue(TrackMatcher.matches(song("Nice Dream", "Radiohead"), "(Nice Dream)", "Radiohead"))
        assertTrue(TrackMatcher.matches(song("(Nice Dream)", "Radiohead"), "Nice Dream", "Radiohead"))
        assertTrue(TrackMatcher.matches(song("(Nice Dream)", "Radiohead"), "(Nice Dream)", "Radiohead"))
    }

    @Test
    fun aFullyBracketedTitleKeepsItsVersionMarker() {
        val parsed = TrackMatcher.parseTitle("(Nice Dream) (Live)")
        assertEquals(listOf("nice", "dream"), parsed.words)
        assertEquals(setOf("live"), parsed.versions)
        assertTrue(TrackMatcher.matches(song("Nice Dream (Live)", "Radiohead"), "(Nice Dream) (Live)", "Radiohead"))
        assertFalse(TrackMatcher.matches(song("Nice Dream", "Radiohead"), "(Nice Dream) (Live)", "Radiohead"))
    }

    @Test
    fun packagingBracketsAreStillStripped() {
        assertEquals(
            listOf("song radiohead", "song"),
            TrackMatcher.queries(TrackMatcher.Target("Song (Official Video)", "Radiohead")),
        )
        assertTrue(TrackMatcher.matches(song("Song", "Radiohead"), "Song (Official Video)", "Radiohead"))
    }

    @Test
    fun aNoiseOnlyBracketedTitleStillAsksForNothing() {
        assertEquals(
            emptyList(),
            TrackMatcher.queries(TrackMatcher.Target("(Official Video)", "Radiohead")),
        )
    }
}
