package com.music.bitchord.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmStateMachineTest {

    @Test
    fun `editing invalidates old schedule and advances generation`() {
        val current = scheduled(alarm(generation = 4L), 1_000L)
        val edited = AlarmStateMachine.edit(current, current.copy(hour = 8))

        assertEquals(5L, edited.generation)
        assertNull(edited.scheduledToken)
        assertNull(edited.scheduledEpochMillis)
        assertEquals(8, edited.hour)
    }

    @Test
    fun `enabling without a song fails closed`() {
        val current = AlarmConfig()
        val edited = AlarmStateMachine.edit(current, current.copy(enabled = true))
        assertFalse(edited.enabled)
    }

    @Test
    fun `enabled alarm with song is ready to schedule`() {
        val current = AlarmConfig()
        val edited = AlarmStateMachine.edit(
            current,
            current.copy(enabled = true, song = song()),
        )
        assertTrue(edited.isReadyToSchedule())
    }

    @Test
    fun `exact access selects exact scheduling`() {
        assertEquals(AlarmScheduleMode.EXACT, chooseAlarmScheduleMode(true))
    }

    @Test
    fun `missing exact access selects inexact scheduling`() {
        assertEquals(AlarmScheduleMode.INEXACT, chooseAlarmScheduleMode(false))
    }

    @Test
    fun `scheduling creates a stable nonblank token`() {
        val first = scheduled(alarm(generation = 2L), 9_000L)
        val second = scheduled(alarm(generation = 2L), 9_000L)
        assertNotNull(first.scheduledToken)
        assertEquals(first.scheduledToken, second.scheduledToken)
    }

    @Test
    fun `stale token is rejected`() {
        val config = scheduled(alarm(), 10_000L)
        assertNull(AlarmStateMachine.consumeTrigger(config, "old", 10_000L, 10_100L))
    }

    @Test
    fun `stale epoch is rejected`() {
        val config = scheduled(alarm(), 10_000L)
        assertNull(
            AlarmStateMachine.consumeTrigger(
                config,
                requireNotNull(config.scheduledToken),
                9_999L,
                10_100L,
            ),
        )
    }

    @Test
    fun `one-shot disables after accepted trigger`() {
        val config = scheduled(alarm(days = emptySet()), 10_000L)
        val transition = AlarmStateMachine.consumeTrigger(
            config,
            requireNotNull(config.scheduledToken),
            10_000L,
            10_100L,
        )
        assertNotNull(transition)
        assertFalse(requireNotNull(transition).config.enabled)
        assertFalse(transition.recurring)
    }

    @Test
    fun `recurring alarm remains enabled after trigger`() {
        val config = scheduled(alarm(days = setOf(1, 3)), 10_000L)
        val transition = requireNotNull(
            AlarmStateMachine.consumeTrigger(
                config,
                requireNotNull(config.scheduledToken),
                10_000L,
                10_100L,
            ),
        )
        assertTrue(transition.config.enabled)
        assertTrue(transition.recurring)
        assertNull(transition.config.scheduledToken)
    }

    @Test
    fun `active stop requires matching unexpired token`() {
        val config = AlarmConfig(activeToken = "active", activeUntilEpochMillis = 20_000L)
        assertTrue(AlarmStateMachine.canStop(config, "active", 19_999L))
        assertFalse(AlarmStateMachine.canStop(config, "other", 19_999L))
        assertFalse(AlarmStateMachine.canStop(config, "active", 20_001L))
    }

    @Test
    fun `editing preserves current stop token while replacing future schedule`() {
        val current = AlarmConfig(
            enabled = true,
            song = song("video1"),
            activeToken = "ringing",
            activeUntilEpochMillis = 20_000L,
            scheduledToken = "old",
            scheduledEpochMillis = 15_000L,
        )
        val edited = AlarmStateMachine.edit(current, current.copy(song = song("video2")))
        assertEquals("ringing", edited.activeToken)
        assertNotEquals("old", edited.scheduledToken)
    }

    private fun alarm(
        generation: Long = 1L,
        days: Set<Int> = emptySet(),
    ) = AlarmConfig(
        enabled = true,
        hour = 7,
        minute = 0,
        repeatDays = days,
        song = song(),
        generation = generation,
    )

    private fun song(videoId: String = "video123") = AlarmSong(
        videoId = videoId,
        title = "Morning",
        artist = "BitChord Artist",
    )

    private fun scheduled(config: AlarmConfig, epoch: Long): AlarmConfig =
        AlarmStateMachine.scheduled(config, epoch, AlarmScheduleMode.EXACT)
}
