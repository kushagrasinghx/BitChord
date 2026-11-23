package com.music.bitchord.playback

import com.music.bitchord.data.settings.AudioFocusOption
import com.music.bitchord.data.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFocusOptionTest {

    @Test
    fun `auto pause option is flagged as auto pause`() {
        assertTrue(AudioFocusOption.AUTO_PAUSE.isAutoPause)
        assertFalse(AudioFocusOption.KEEP_PLAYING.isAutoPause)
    }

    @Test
    fun `setting audio focus option updates state flow`() {
        AppSettings.setAudioFocusOption(AudioFocusOption.KEEP_PLAYING)
        assertEquals(AudioFocusOption.KEEP_PLAYING, AppSettings.audioFocusOption.value)

        AppSettings.setAudioFocusOption(AudioFocusOption.AUTO_PAUSE)
        assertEquals(AudioFocusOption.AUTO_PAUSE, AppSettings.audioFocusOption.value)
    }
}
