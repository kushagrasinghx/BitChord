package com.music.bitchord.alarm

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmSnoozeNotificationTest {
    private val song = AlarmSong("song", "Instant Crush", "Daft Punk")

    private fun alarm(id: String, order: Long = 1) = AlarmConfig(
        id = id,
        creationOrder = order,
        label = "Alarm $id",
        enabled = true,
        hour = 7,
        repeatDays = setOf(1, 2, 3, 4, 5),
        song = song,
        generation = 4,
    )

    private fun snooze(id: String, token: String, epoch: Long, order: Long = 1) =
        alarm(id, order).copy(
            scheduledEpochMillis = epoch + 86_400_000L,
            scheduledToken = "$id:next",
            scheduleMode = AlarmScheduleMode.EXACT,
            snoozeEpochMillis = epoch,
            snoozeToken = token,
        )

    @Test fun `snooze creates persistent pending state with the displayed wake time`() {
        val active = AlarmCollection(
            alarms = listOf(alarm("a")),
            activeSession = AlarmSession("a", "active", 30_000_000L, 3),
        )
        val transition = requireNotNull(AlarmStateMachine.snooze(active, "a", "active", 25_200_000L))
        val pending = transition.collection.alarms.single()
        assertEquals(25_800_000L, pending.snoozeEpochMillis)
        assertEquals(transition.token, pending.snoozeToken)

        val formatter = SimpleDateFormat("HH:mm", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        assertEquals("07:10", formatSnoozeWakeTime(transition.epochMillis, formatter))
    }

    @Test fun `cancel snooze removes only A temporary occurrence and keeps its recurring schedule`() {
        val a = snooze("a", "a:snooze", 25_800_000L)
        val b = snooze("b", "b:snooze", 30_000_000L, order = 2)
        val updated = requireNotNull(
            AlarmStateMachine.cancelSnooze(AlarmCollection(alarms = listOf(a, b)), "a", "a:snooze"),
        )
        val updatedA = updated.alarms.first { it.id == "a" }
        assertNull(updatedA.snoozeEpochMillis)
        assertNull(updatedA.snoozeToken)
        assertEquals(a.scheduledEpochMillis, updatedA.scheduledEpochMillis)
        assertEquals(a.scheduledToken, updatedA.scheduledToken)
        assertEquals(b, updated.alarms.first { it.id == "b" })
    }

    @Test fun `stale cancel action is rejected and cannot cancel another alarm`() {
        val a = snooze("a", "a:current", 25_800_000L)
        val b = snooze("b", "b:current", 30_000_000L, order = 2)
        val state = AlarmCollection(alarms = listOf(a, b))
        assertNull(AlarmStateMachine.cancelSnooze(state, "a", "a:stale"))
        assertNull(AlarmStateMachine.cancelSnooze(state, "b", "a:current"))
    }

    @Test fun `disabling or editing parent invalidates its pending snooze`() {
        val pending = snooze("a", "a:snooze", 25_800_000L)
        val disabled = AlarmStateMachine.edit(pending, pending.copy(enabled = false))
        assertNull(disabled.snoozeEpochMillis)
        assertNull(disabled.snoozeToken)
    }

    @Test fun `deleting parent removes its pending snooze without touching B`() {
        val b = snooze("b", "b:snooze", 30_000_000L, order = 2)
        val result = AlarmStateMachine.remove(
            AlarmCollection(alarms = listOf(snooze("a", "a:snooze", 25_800_000L), b)),
            "a",
        )
        assertEquals(listOf(b), result.alarms)
    }

    @Test fun `snooze trigger clears pending state before ringing`() {
        val pending = snooze("a", "a:snooze", 25_800_000L)
        val result = requireNotNull(
            AlarmStateMachine.trigger(
                AlarmCollection(alarms = listOf(pending)),
                "a",
                "a:snooze",
                25_800_000L,
                25_800_000L,
                snooze = true,
            ),
        )
        assertNull(result.collection.alarms.single().snoozeEpochMillis)
        assertNull(result.collection.alarms.single().snoozeToken)
    }

    @Test fun `notification tags and cancel actions are isolated by alarm and token`() {
        assertNotEquals(snoozeNotificationTag("a"), snoozeNotificationTag("b"))
        assertNotEquals(
            alarmPendingIdentity("a", "cancel-snooze/a:one"),
            alarmPendingIdentity("a", "cancel-snooze/a:two"),
        )
        assertNotEquals(
            alarmPendingIdentity("a", "cancel-snooze/a:one"),
            alarmPendingIdentity("b", "cancel-snooze/a:one"),
        )
        assertTrue(snoozeNotificationTag("a").contains("a"))
    }
}
