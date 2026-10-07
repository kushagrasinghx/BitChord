package com.music.bitchord

import com.music.bitchord.data.scrobbling.primaryArtist
import org.junit.Assert.assertEquals
import org.junit.Test

class PrimaryArtistTest {
    @Test
    fun `takes the first artist from common collaboration credits`() {
        assertEquals("Anitta", "Anitta & Shakira".primaryArtist())
        assertEquals("Anitta", "Anitta, Shakira".primaryArtist())
    }

    @Test
    fun `leaves a single artist unchanged`() {
        assertEquals("AC/DC", "AC/DC".primaryArtist())
        assertEquals("Florence and the Machine", "Florence and the Machine".primaryArtist())
    }
}
