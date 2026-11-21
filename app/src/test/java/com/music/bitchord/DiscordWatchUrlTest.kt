package com.music.bitchord

import com.music.bitchord.data.discord.DiscordRPC
import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

/** The presence card's link must reach a real page whatever the track's source. */
class DiscordWatchUrlTest {

    private fun song(id: String) = Song(videoId = id, title = "Starboy", artist = "The Weeknd", thumbnailUrl = null)

    @Test
    fun `a YouTube track links to its watch page`() {
        assertEquals(
            "https://music.youtube.com/watch?v=34Na4j8AVgA",
            DiscordRPC.watchUrl(song("34Na4j8AVgA")),
        )
    }

    @Test
    fun `a local file links to a search rather than its content uri`() {
        assertEquals(
            "https://music.youtube.com/search?q=Starboy+The+Weeknd",
            DiscordRPC.watchUrl(song("content://media/external/audio/media/42")),
        )
    }
}
