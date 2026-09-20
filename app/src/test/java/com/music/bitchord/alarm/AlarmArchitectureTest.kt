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

    @Test fun `dedicated ringing service repeats exactly one media item until user response`() {
        val ringing = source("java/com/music/bitchord/alarm/AlarmRingingService.kt")
        val start = ringing.substringAfter("private fun startResolvedStream").substringBefore("private fun pauseOrdinaryBitChordPlayback")
        assertTrue(start.contains("setMediaItem("))
        assertFalse(start.contains("setMediaItems("))
        assertTrue(start.contains("Player.REPEAT_MODE_ONE"))
        assertFalse(start.contains("Player.REPEAT_MODE_OFF"))
        assertFalse(ringing.contains("Player.STATE_ENDED"))
        assertTrue(ringing.contains("onPlayerError"))
    }

    @Test fun `explicit stop and snooze release the repeating alarm player`() {
        val ringing = source("java/com/music/bitchord/alarm/AlarmRingingService.kt")
        val receiver = source("java/com/music/bitchord/alarm/AlarmReceiver.kt")
        assertTrue(ringing.contains("player?.repeatMode = Player.REPEAT_MODE_OFF"))
        assertTrue(ringing.contains("player?.stop()"))
        assertTrue(ringing.contains("player?.release()"))
        assertTrue(ringing.contains("abandonAudioFocusRequest"))
        assertTrue(receiver.contains("AlarmScheduler.stop(context, id, token)"))
        assertTrue(receiver.contains("AlarmScheduler.snooze(context, id, token)"))
        assertTrue(receiver.contains("AlarmRingingService.stop(context, end.previousAlarmVolume)"))
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
