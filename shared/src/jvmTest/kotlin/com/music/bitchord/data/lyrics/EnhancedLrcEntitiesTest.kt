package com.music.bitchord.data.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals

class EnhancedLrcEntitiesTest {

    @Test
    fun `numeric entities beyond the basic plane decode to the whole character`() {
        assertEquals("fire 🔥", EnhancedLrc.decodeEntities("fire &#128293;"))
        assertEquals("fire 🔥", EnhancedLrc.decodeEntities("fire &#x1F525;"))
    }

    @Test
    fun `an entity naming no character is left as written`() {
        assertEquals("&#99999999999; &#x110000;", EnhancedLrc.decodeEntities("&#99999999999; &#x110000;"))
    }
}
