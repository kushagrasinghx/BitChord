package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The case that made addons useless outside search. */
class DesktopMatcherTest {

    private val youtubeRow = Song(
        videoId = "kb_6Vz-k7Wc",
        title = "Tera Mera Rishta -  New Version (From \"Awarapan 2\")",
        artist = "Mithoon, Saaj Bhatt, Sayeed Quadri & Mustafa Zahid",
        thumbnailUrl = null,
    )

    @Test
    fun aCatalogueIsAskedForTheTitleWithoutItsPackaging() {
        val queries = DesktopTrackMatcher.queries(youtubeRow)

        // The film, and the four-way credit, are not part of the question.
        assertEquals(listOf("tera mera rishta version mithoon", "tera mera rishta version"), queries)
    }

    @Test
    fun theCatalogueListingOfTheSameTakeMatchesADecoratedYouTubeTitle() {
        // What JioSaavn actually files this under, film and extra credits and upload decoration all
        // absent.
        val catalogueRow = Song(
            videoId = "jiosaavn:wfTj8NYC",
            title = "Tera Mera Rishta - New Version",
            artist = "Mithoon",
            thumbnailUrl = null,
            durationText = "6:05",
        )

        assertNotNull(DesktopTrackMatcher.best(listOf(catalogueRow), youtubeRow))
    }

    @Test
    fun aDifferentTakeOfTheSameSongIsStillRefused() {
        // "New Version" is a take, so the plain listing is a different recording and must not stand
        // in for it.
        val plainListing = Song("jiosaavn:other", "Tera Mera Rishta", "Mithoon", null)

        assertNull(DesktopTrackMatcher.best(listOf(plainListing), youtubeRow))
    }

    @Test
    fun theUploadConventionOfArtistFirstIsUnderstood() {
        // "Artist - Title" hands the title over to the tail rather than eating it, so this is the
        // same recording as a catalogue's plain listing.
        val upload = Song("v", "Mithoon - Tera Mera Rishta (Official Video)", "Mithoon", null)

        assertEquals("tera mera rishta", DesktopTrackMatcher.searchableTitle(upload.title, upload.artist))
    }

    @Test
    fun aTakeIsIdentityAndHasToAgree() {
        val target = Song("v", "Song Title", "An Artist", null)
        val live = Song("a", "Song Title (Live)", "An Artist", null)
        val album = Song("b", "Song Title", "An Artist", null)

        // Asking for the album cut must not land on the live take…
        assertNull(DesktopTrackMatcher.best(listOf(live), target))
        // …and asking for the live take must not land on the album cut.
        assertNull(DesktopTrackMatcher.best(listOf(album), live))
        assertNotNull(DesktopTrackMatcher.best(listOf(album), target))
    }

    @Test
    fun packagingThatOnlyLooksLikeATakeIsIgnored() {
        val target = Song("v", "Song Title (Album Version)", "An Artist", null)
        val plain = Song("a", "Song Title", "An Artist", null)

        // "Album Version" describes the ordinary release; treating it as a take would stop a source
        // ever matching the plain listing.
        assertNotNull(DesktopTrackMatcher.best(listOf(plain), target))
    }

    @Test
    fun anUploadsTrailingLabelIsNotPartOfTheTitle() {
        assertEquals("paniyon sa", DesktopTrackMatcher.searchableTitle("Paniyon Sa Full Song", ""))
        // Never stripped to nothing: a track really called "Song" keeps its name.
        assertEquals("song", DesktopTrackMatcher.searchableTitle("Song", ""))
    }

    @Test
    fun onlyTheFirstCreditedArtistGoesIntoTheQuery() {
        assertEquals("mithoon", DesktopTrackMatcher.primaryArtist("Mithoon, Saaj Bhatt & Sayeed Quadri"))
        assertEquals("arijit singh", DesktopTrackMatcher.primaryArtist("Arijit Singh feat. Someone"))
    }
}
