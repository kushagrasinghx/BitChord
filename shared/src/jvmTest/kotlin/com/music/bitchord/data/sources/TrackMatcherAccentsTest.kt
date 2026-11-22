package com.music.bitchord.data.sources

import com.music.bitchord.data.model.Song
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrackMatcherAccentsTest {
    private fun song(title: String, artist: String) =
        Song(videoId = "x", title = title, artist = artist, thumbnailUrl = null)

    @Test
    fun aTurkishTitleMatchesItsUnaccentedUpload() {
        assertTrue(TrackMatcher.matches(song("Simarik", "Tarkan"), "Şımarık", "Tarkan"))
        assertTrue(TrackMatcher.matches(song("Şımarık", "Tarkan"), "Simarik", "Tarkan"))
    }

    @Test
    fun anAccentedArtistMatchesItsUnaccentedSpelling() {
        assertTrue(TrackMatcher.matches(song("Nilüfer", "Muslum Gurses"), "Nilüfer", "Müslüm Gürses"))
    }

    @Test
    fun aDifferentTitleStillDoesNotMatch() {
        assertFalse(TrackMatcher.matches(song("Kuzu Kuzu", "Tarkan"), "Şımarık", "Tarkan"))
    }

    @Test
    fun otherScriptsKeepTheirMarks() {
        // Hindi vowel signs are combining marks; they tell these two words apart.
        assertTrue(TrackMatcher.foldLatin("दिल") != TrackMatcher.foldLatin("दल"))
    }

    @Test
    fun aCjkTitlesAccentedRomanizationStillFilesTheUpload() {
        // The bracketed romaji is compared with the other title's (folded) core.
        val cjk = TrackMatcher.parseTitle("東京 (Tōkyō)")
        val latin = TrackMatcher.parseTitle("Tōkyō")
        assertTrue(TrackMatcher.isTranslationMatch(cjk, latin))
        assertTrue(TrackMatcher.isTranslationMatch(latin, cjk))
    }
}
