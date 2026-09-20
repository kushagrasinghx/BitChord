package com.music.bitchord.alarm
import androidx.media3.common.C
import android.media.AudioManager
import org.junit.Assert.*
import org.junit.Test
class AlarmUxPolicyTest{
 @Test fun `volume mapping is bounded`() { assertEquals(8,AlarmVolumePolicy.streamIndex(80,0,10));assertEquals(80,AlarmVolumePolicy.percent(8,0,10));assertEquals(1,AlarmVolumePolicy.streamIndex(20,1,7)) }
 @Test fun `volume button default and mappings`() {assertEquals(AlarmVolumeButtonAction.SNOOZE,AlarmConfig().volumeButtonAction);assertEquals(AlarmVolumeButtonCommand.SNOOZE,volumeButtonCommand(AlarmVolumeButtonAction.SNOOZE,true));assertEquals(AlarmVolumeButtonCommand.STOP,volumeButtonCommand(AlarmVolumeButtonAction.STOP,true));assertEquals(AlarmVolumeButtonCommand.ADJUST_ALARM_VOLUME,volumeButtonCommand(AlarmVolumeButtonAction.ADJUST_VOLUME,true))}
 @Test fun `dedicated player uses alarm usage`() {assertEquals(C.USAGE_ALARM,AlarmRingingService.ALARM_ATTRIBUTES.usage)}
 @Test fun `alarm volume targets alarm stream`() {assertEquals(AudioManager.STREAM_ALARM,AlarmVolumeController.STREAM);assertNotEquals(AudioManager.STREAM_MUSIC,AlarmVolumeController.STREAM)}
}
