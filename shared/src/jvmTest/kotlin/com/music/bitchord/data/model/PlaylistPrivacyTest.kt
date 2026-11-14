package com.music.bitchord.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaylistPrivacyTest {

    @Test
    fun `an own playlist's header states its privacy`() {
        assertEquals(PlaylistPrivacy.PRIVATE, PlaylistPrivacy.fromHeader("Playlist • Private • 2026"))
        assertEquals(PlaylistPrivacy.UNLISTED, PlaylistPrivacy.fromHeader("Playlist • Unlisted • 2025"))
        assertEquals(PlaylistPrivacy.PUBLIC, PlaylistPrivacy.fromHeader("Playlist • Public • 2024"))
    }

    @Test
    fun `a header that doesn't say, or says it in another language, gives no guess`() {
        assertNull(PlaylistPrivacy.fromHeader("Playlist • 2026"))
        assertNull(PlaylistPrivacy.fromHeader("Playlist • Privée • 2026"))
        // A word that merely contains one isn't one.
        assertNull(PlaylistPrivacy.fromHeader("Playlist • Publicity stunts"))
    }
}
