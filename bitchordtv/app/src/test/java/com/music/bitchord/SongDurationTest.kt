package com.music.bitchord

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.durationMillis
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A row's duration arrives as a display string, and the download path's lyrics
 * lookup needs it back as a quantity.
 *
 * Worth its own tests because every way of getting this wrong is silent. Three
 * of the four lyric databases match on the track's length and LRCLIB *ranks* on
 * it, so a duration that parses to zero doesn't fail the lookup — it matches the
 * shortest edit of the song in the database and embeds timings for a different
 * recording. There is nothing to notice until someone plays the file.
 */
class SongDurationTest {

    private fun song(durationText: String?) = Song(
        videoId = "id",
        title = "Title",
        artist = "Artist",
        thumbnailUrl = null,
        durationText = durationText,
    )

    @Test
    fun `minutes and seconds`() {
        assertEquals(225_000L, song("3:45").durationMillis())
    }

    @Test
    fun `a single-digit minute field, which is how most rows arrive`() {
        assertEquals(62_000L, song("1:02").durationMillis())
    }

    @Test
    fun `hours, minutes and seconds`() {
        // The long-mix case. Two of the app's older duration parsers return 0
        // here, which is the reason this one is written out separately.
        assertEquals(3_753_000L, song("1:02:33").durationMillis())
    }

    @Test
    fun `surrounding and interior whitespace is tolerated`() {
        assertEquals(225_000L, song(" 3 : 45 ").durationMillis())
    }

    @Test
    fun `a row with no duration is zero rather than null`() {
        assertEquals(0L, song(null).durationMillis())
    }

    @Test
    fun `anything that isn't a duration is zero`() {
        assertEquals(0L, song("").durationMillis())
        assertEquals(0L, song("LIVE").durationMillis())
        assertEquals(0L, song("3:45:xx").durationMillis())
        assertEquals(0L, song("225").durationMillis())
        assertEquals(0L, song("1:2:3:4").durationMillis())
    }

    @Test
    fun `a negative field cannot produce a negative duration`() {
        // Nothing sends this, but a caller's only check is `<= 0`, so the
        // guard has to hold rather than be argued about.
        assertEquals(0L, song("-3:45").durationMillis())
    }
}
