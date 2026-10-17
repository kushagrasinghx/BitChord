package com.music.bitchord.playback

import com.music.bitchord.data.model.Song
import kotlin.test.Test
import kotlin.test.assertEquals

class QueueBuilderRemixTest {
    private fun song(id: String, title: String, artist: String = "Rihanna") =
        Song(videoId = id, title = title, artist = artist, thumbnailUrl = null)

    @Test
    fun originalIsChosenBeforeRemixWhenBothAreInRadioBatch() {
        val queue = QueueBuilder.extend(
            existing = emptyList(),
            candidates = listOf(
                song("remix", "Kiss It Better (R3hab Remix)"),
                song("original", "Kiss It Better"),
            ),
            limit = 1,
        )

        assertEquals(listOf("original"), queue.map { it.videoId })
    }

    @Test
    fun noRepeatTreatsRemixAsAlreadyHeardOriginal() {
        val queue = QueueBuilder.extend(
            existing = listOf(song("original", "Kiss It Better")),
            candidates = listOf(
                song("remix", "Kiss It Better (R3hab Remix)"),
                song("fresh", "Needed Me"),
            ),
            limit = 10,
            avoidAlternateVersions = true,
        )

        assertEquals(listOf("fresh"), queue.map { it.videoId })
    }

    @Test
    fun noRepeatRecognizesSavageLoveSubtitleAcrossDifferentArtistCredits() {
        val queue = QueueBuilder.extend(
            existing = listOf(
                song("original", "Savage Love (Laxed - Siren Beat)", "Jawsh 685 & Jason Derulo"),
            ),
            candidates = listOf(
                song("viral", "Savage Love", "Viral DJs"),
                song("fresh", "Levitating", "Dua Lipa"),
            ),
            limit = 10,
            avoidAlternateVersions = true,
        )

        assertEquals(listOf("fresh"), queue.map { it.videoId })
    }

    @Test
    fun noRepeatKeepsIdenticalBareTitlesFromDifferentArtistsDistinct() {
        val queue = QueueBuilder.extend(
            existing = listOf(song("first", "Home", "Artist One")),
            candidates = listOf(song("second", "Home", "Artist Two")),
            limit = 10,
            avoidAlternateVersions = true,
        )

        assertEquals(listOf("second"), queue.map { it.videoId })
    }
}
