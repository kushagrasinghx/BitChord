package com.music.bitchord

import com.music.bitchord.data.settings.PlaybackProvider
import com.music.bitchord.data.settings.availablePlaybackProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackProviderTest {
    @Test
    fun freshAndUnknownPreferencesUseYouTube() {
        listOf(null, "", "unknown", "apple_music").forEach {
            assertEquals(PlaybackProvider.YOUTUBE_MUSIC, availablePlaybackProvider(it, true, true))
        }
    }

    @Test
    fun appleRequiresBothAdapterAndAccount() {
        assertEquals(PlaybackProvider.APPLE_MUSIC, availablePlaybackProvider("APPLE_MUSIC", true, true))
        assertEquals(PlaybackProvider.YOUTUBE_MUSIC, availablePlaybackProvider("APPLE_MUSIC", false, true))
        assertEquals(PlaybackProvider.YOUTUBE_MUSIC, availablePlaybackProvider("APPLE_MUSIC", true, false))
        assertEquals(PlaybackProvider.YOUTUBE_MUSIC, availablePlaybackProvider("APPLE_MUSIC", false, false))
    }

    @Test
    fun connectingAppleDoesNotChangeAnExplicitYouTubeChoice() {
        assertEquals(PlaybackProvider.YOUTUBE_MUSIC, availablePlaybackProvider("YOUTUBE_MUSIC", true, true))
    }
}
