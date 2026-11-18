package com.music.bitchord.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SyncSnapshotTest {
    @Test
    fun preferenceValuesRoundTripWithoutLosingSharedPreferencesTypes() {
        val values = listOf<Any>(true, 42, 9L, 1.25f, "text", setOf("z", "a"))
        val encoded = values.map { requireNotNull(SyncValue.fromPreference(it)) }

        assertEquals(listOf("bool", "int", "long", "float", "string", "string_set"), encoded.map { it.type })
        assertEquals(values.toSet(), encoded.map { it.toPreference() }.toSet())
    }

    @Test
    fun mergeChoosesNewerFieldClockIndependentlyOfSnapshotOrder() {
        val older = SyncSnapshot(
            fields = mapOf("theme_mode" to SyncField(FieldClock(3, "phone-a"), SyncValue("string", "dark"))),
        )
        val newer = SyncSnapshot(
            fields = mapOf("theme_mode" to SyncField(FieldClock(4, "phone-b"), SyncValue("string", "light"))),
        )
        val expected = newer.fields.getValue("theme_mode")

        assertEquals(expected, mergeSnapshots(older, newer).fields["theme_mode"])
        assertEquals(expected, mergeSnapshots(newer, older).fields["theme_mode"])
    }

    @Test
    fun localChangesUseCounterAboveSnapshotAndRepresentDeletionAsTombstone() {
        val snapshot = SyncSnapshot(
            fields = mapOf(
                "theme_mode" to SyncField(FieldClock(8, "phone-a"), SyncValue("string", "dark")),
                "hide_recents" to SyncField(FieldClock(7, "phone-b"), SyncValue("bool", "true")),
                "autoplay" to SyncField(FieldClock(6, "phone-a"), SyncValue("bool", "true")),
            ),
        )
        val baseline = mapOf(
            "theme_mode" to SyncValue("string", "dark"),
            "hide_recents" to SyncValue("bool", "true"),
            "autoplay" to SyncValue("bool", "true"),
        )
        val local = mapOf(
            "theme_mode" to SyncValue("string", "light"),
            "autoplay" to SyncValue("bool", "true"),
        )

        val changed = recordLocalChanges(snapshot, baseline, local, "phone-c")

        assertEquals(SyncField(FieldClock(9, "phone-c"), SyncValue("string", "light")), changed.fields["theme_mode"])
        assertEquals(SyncField(FieldClock(9, "phone-c"), null), changed.fields["hide_recents"])
        assertEquals(snapshot.fields["autoplay"], changed.fields["autoplay"])
    }

    @Test
    fun identicalFieldClockWithDifferentValuesIsRejected() {
        val left = SyncSnapshot(
            fields = mapOf("theme_mode" to SyncField(FieldClock(5, "phone-a"), SyncValue("string", "dark"))),
        )
        val right = SyncSnapshot(
            fields = mapOf("theme_mode" to SyncField(FieldClock(5, "phone-a"), SyncValue("string", "light"))),
        )

        assertFailsWith<IllegalArgumentException> { mergeSnapshots(left, right) }
    }
}
