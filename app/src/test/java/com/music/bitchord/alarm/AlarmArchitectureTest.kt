package com.music.bitchord.alarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AlarmArchitectureTest {
    private fun source(path: String): String {
        val root = File(".").canonicalFile
        val base = if (File(root, "app/src/main").exists()) File(root, "app/src/main") else File(root, "src/main")
        return File(base, path).readText()
    }

    @Test fun `ordinary playback and AutoPlay contain no alarm special case`() {
        val ordinary = source("java/com/music/bitchord/playback/PlaybackService.kt") + source("java/com/music/bitchord/playback/Autoplay.kt")
        assertFalse(ordinary.contains("PlaybackSourceType.ALARM"))
        assertFalse(ordinary.contains("AlarmRingingService"))
    }

    @Test fun `dedicated ringing service owns exactly one media item`() {
        val ringing = source("java/com/music/bitchord/alarm/AlarmRingingService.kt")
        assertTrue(ringing.contains("setMediaItem("))
        assertFalse(ringing.contains("setMediaItems("))
        assertTrue(ringing.contains("Player.REPEAT_MODE_OFF"))
    }

    @Test fun `snooze notification is quiet persistent and never full screen`() {
        val notification = source("java/com/music/bitchord/alarm/AlarmNotification.kt")
        val pending = notification.substringAfter("fun showSnoozePending").substringBefore("fun cancelSnooze")
        assertTrue(pending.contains("setSilent(true)"))
        assertTrue(pending.contains("setOngoing(true)"))
        assertTrue(pending.contains("PRIORITY_LOW"))
        assertTrue(pending.contains("alarm_cancel_snooze"))
        assertFalse(pending.contains("setFullScreenIntent"))
    }
}
