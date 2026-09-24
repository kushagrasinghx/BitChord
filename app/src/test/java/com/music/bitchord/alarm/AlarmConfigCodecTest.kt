package com.music.bitchord.alarm
import org.junit.Assert.*
import org.junit.Test
class AlarmConfigCodecTest{
 private val song=AlarmSong("v","Song","Artist")
 @Test fun `two alarms retain independent settings through persistence`() {val a=AlarmConfig("a",1,label="Morning",enabled=true,song=song,targetVolumePercent=70,snoozeMinutes=5);val b=AlarmConfig("b",2,label="Evening",song=song.copy(videoId="w"),targetVolumePercent=90,volumeButtonAction=AlarmVolumeButtonAction.STOP);val decoded=AlarmConfigCodec.decode(AlarmConfigCodec.encode(AlarmCollection(alarms=listOf(a,b))));assertEquals(listOf("a","b"),decoded.alarms.map{it.id});assertEquals(70,decoded.alarms[0].targetVolumePercent);assertEquals(AlarmVolumeButtonAction.STOP,decoded.alarms[1].volumeButtonAction);assertNotEquals(decoded.alarms[0].song,decoded.alarms[1].song)}
 @Test fun `old single alarm schema resets cleanly`() {assertTrue(AlarmConfigCodec.decode("{\"schemaVersion\":2,\"enabled\":true}").alarms.isEmpty())}
 @Test fun `corrupt data fails closed`() {assertEquals(AlarmCollection(),AlarmConfigCodec.decode("not-json"))}
}
