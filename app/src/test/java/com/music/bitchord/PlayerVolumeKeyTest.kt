package com.music.bitchord

import android.media.AudioManager
import android.view.KeyEvent
import com.music.bitchord.ui.components.volumeAdjustDirectionForKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerVolumeKeyTest {

    @Test
    fun `volumeAdjustDirectionForKey maps hardware volume keys`() {
        assertEquals(AudioManager.ADJUST_RAISE, volumeAdjustDirectionForKey(KeyEvent.KEYCODE_VOLUME_UP))
        assertEquals(AudioManager.ADJUST_LOWER, volumeAdjustDirectionForKey(KeyEvent.KEYCODE_VOLUME_DOWN))
        assertNull(volumeAdjustDirectionForKey(KeyEvent.KEYCODE_BACK))
        assertNull(volumeAdjustDirectionForKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
    }
}
