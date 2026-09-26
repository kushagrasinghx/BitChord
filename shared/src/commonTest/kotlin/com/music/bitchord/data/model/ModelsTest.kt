package com.music.bitchord.data.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ModelsTest {
    @Test
    fun durationIsSharedAcrossTargets() {
        assertEquals(225_000L, "3:45".durationMillis())
        assertEquals(3_723_000L, "1:02:03".durationMillis())
        assertEquals(0L, "not-a-duration".durationMillis())
        assertEquals(0L, null.durationMillis())
    }

    @Test
    fun artworkSizeHintIsSharedAcrossTargets() {
        assertEquals(
            "https://example.test/w720-h720.jpg",
            "https://example.test/w120-h120.jpg".artworkAt(720),
        )
        assertEquals("https://example.test/cover.jpg", "https://example.test/cover.jpg".artworkAt(720))
    }
}
