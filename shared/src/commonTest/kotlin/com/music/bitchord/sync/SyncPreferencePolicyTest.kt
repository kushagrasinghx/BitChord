package com.music.bitchord.sync

import kotlin.test.Test
import kotlin.test.assertEquals

class SyncPreferencePolicyTest {
    @Test
    fun exportIncludesOnlyExplicitlyAllowedKeysAndPreservesPreferenceTypes() {
        val stored = mapOf<String, Any?>(
            "theme_mode" to "DARK",
            "lyrics_offset_ms" to 250,
            "audio_cache_limit_bytes" to 1_024L,
            "lastfm_session_key" to "private-session",
            "onboarding_complete" to true,
            "signature_api_url" to "http://private-host",
            "future_preference" to "unknown",
        )

        assertEquals(
            mapOf(
                "theme_mode" to SyncValue("string", "DARK"),
                "lyrics_offset_ms" to SyncValue("int", "250"),
            ),
            SyncPreferencePolicy.export(stored),
        )
    }

    @Test
    fun incomingPartialSyncChangesOnlyAllowedKeysWithExpectedTypes() {
        val local = mapOf<String, Any?>(
            "theme_mode" to "DARK",
            "lyrics_offset_ms" to 250,
            "lastfm_session_key" to "private-session",
            "onboarding_complete" to true,
            "future_preference" to "keep-me",
        )
        val remote = mapOf(
            "theme_mode" to SyncValue("string", "LIGHT"),
            "lyrics_offset_ms" to SyncValue("string", "not-an-int"),
            "lastfm_session_key" to SyncValue("string", "must-not-import"),
            "onboarding_complete" to SyncValue("bool", "false"),
            "future_preference" to SyncValue("string", "must-not-import"),
            "pinned_playlists" to SyncValue("string", "PL-1,PL-2"),
        )

        assertEquals(
            local + ("theme_mode" to "LIGHT") + ("pinned_playlists" to "PL-1,PL-2"),
            SyncPreferencePolicy.merge(local, remote),
        )
    }

    @Test
    fun diffContainsOnlyAllowlistedWritesAndDeletes() {
        val local = mapOf<String, Any?>(
            "theme_mode" to "DARK",
            "pinned_playlists" to "PL-1",
            "lastfm_session_key" to "private-session",
        )
        val remote = mapOf(
            "theme_mode" to SyncField(FieldClock(3, "phone-a"), SyncValue("string", "LIGHT")),
            "pinned_playlists" to SyncField(FieldClock(4, "phone-b"), null),
            "lastfm_session_key" to SyncField(FieldClock(5, "phone-c"), SyncValue("string", "ignore-me")),
        )

        assertEquals(
            SyncPreferenceDelta(
                upserts = mapOf("theme_mode" to "LIGHT"),
                removals = setOf("pinned_playlists"),
            ),
            SyncPreferencePolicy.diff(local, remote),
        )
    }

    @Test
    fun snapshotAppliesTombstonesWithoutTouchingUnknownOrDeviceLocalSettings() {
        val local = mapOf<String, Any?>(
            "theme_mode" to "DARK",
            "pinned_playlists" to "PL-1",
            "onboarding_complete" to true,
            "signature_api_url" to "http://private-host",
            "future_preference" to "keep-me",
        )
        val remote = mapOf(
            "theme_mode" to SyncField(FieldClock(3, "phone-a"), SyncValue("string", "LIGHT")),
            "pinned_playlists" to SyncField(FieldClock(4, "phone-b"), null),
            "onboarding_complete" to SyncField(FieldClock(5, "phone-c"), SyncValue("bool", "false")),
            "future_preference" to SyncField(FieldClock(6, "phone-d"), SyncValue("string", "ignore-me")),
        )

        assertEquals(
            mapOf<String, Any?>(
                "theme_mode" to "LIGHT",
                "onboarding_complete" to true,
                "signature_api_url" to "http://private-host",
                "future_preference" to "keep-me",
            ),
            SyncPreferencePolicy.applySnapshot(local, remote),
        )
    }
}
