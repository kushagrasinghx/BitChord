package com.music.bitchord

import com.music.bitchord.data.discord.DiscordRPC
import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Button labels Discord will accept, whatever the variables expand to (#1258). */
class DiscordButtonLabelTest {

    private val song = Song(
        videoId = "abcdefghijk",
        title = "A Song Title That Runs Well Past Thirty-Two Characters",
        artist = "Artist",
        thumbnailUrl = null,
    )

    @Test
    fun `variables are filled in`() {
        assertEquals("Hear Artist", DiscordRPC.buttonLabel("Hear {artist_name}", "Listen", song))
    }

    @Test
    fun `a long song name is kept inside Discord's limit`() {
        val label = DiscordRPC.buttonLabel("Play {song_name}", "Listen", song)
        assertTrue(label.codePointCount(0, label.length) <= DiscordRPC.BUTTON_LABEL_MAX)
        assertTrue(label.endsWith("…"))
    }

    @Test
    fun `a label a variable leaves empty falls back`() {
        assertEquals("Listen", DiscordRPC.buttonLabel("{album_name}", "Listen", song))
    }

    @Test
    fun `an emoji is never cut in half`() {
        assertEquals("🎵".repeat(31) + "…", DiscordRPC.buttonLabel("🎵".repeat(40), "Listen", song))
    }
}
