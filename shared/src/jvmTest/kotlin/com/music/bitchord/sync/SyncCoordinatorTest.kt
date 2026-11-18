package com.music.bitchord.sync

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncCoordinatorTest {
    private val profileKey = ByteArray(32) { (it + 11).toByte() }
    private val profileKeyEncoded = Base64.getEncoder().encodeToString(profileKey)

    @Test
    fun appliesRemoteOnlySettingsWithoutUploading() = runSyncTest {
        val remoteSnapshot = snapshot("crossfade_seconds", SyncValue("int", "9"), 2, "other")
        remote.currentSnapshot = RemoteSnapshot(4, envelope(remoteSnapshot))

        coordinator.syncNow()

        assertEquals(9, settings.values["crossfade_seconds"]?.toPreference())
        assertTrue(remote.puts.isEmpty())
        assertEquals(4L, requireNotNull(vault.state).revision)
    }

    @Test
    fun uploadsLocalEditsAsEncryptedSnapshot() = runSyncTest {
        vault.state = localState(baseline = mapOf("theme_mode" to SyncValue("string", "LIGHT")))
        settings.values = mapOf("theme_mode" to SyncValue("string", "DARK"))
        remote.currentSnapshot = RemoteSnapshot(1, envelope(snapshot("theme_mode", SyncValue("string", "LIGHT"), 1, "device-1")))

        coordinator.syncNow()

        val uploaded = remote.puts.single().ciphertext
        assertFalse(uploaded.contains("DARK"))
        assertEquals("DARK", SyncSnapshotCrypto.decrypt(uploaded, "profile-1", profileKey)
            .fields.getValue("theme_mode").value?.value)
        assertEquals(2L, requireNotNull(vault.state).revision)
    }

    @Test
    fun retriesCasConflictAndMergesBothDevices() = runSyncTest {
        vault.state = localState(baseline = mapOf("theme_mode" to SyncValue("string", "LIGHT")))
        settings.values = mapOf("theme_mode" to SyncValue("string", "DARK"))
        remote.currentSnapshot = RemoteSnapshot(1, envelope(snapshot("theme_mode", SyncValue("string", "LIGHT"), 1, "device-1")))
        remote.onPutConflict = {
            remote.currentSnapshot = RemoteSnapshot(2, envelope(snapshot("crossfade_seconds", SyncValue("int", "7"), 2, "device-2")))
        }

        coordinator.syncNow()

        assertEquals(2, remote.puts.size)
        assertEquals(7, settings.values["crossfade_seconds"]?.toPreference())
        assertEquals("DARK", settings.values["theme_mode"]?.toPreference())
        val uploaded = SyncSnapshotCrypto.decrypt(remote.puts.last().ciphertext, "profile-1", profileKey)
        assertEquals("DARK", uploaded.fields.getValue("theme_mode").value?.value)
        assertEquals("7", uploaded.fields.getValue("crossfade_seconds").value?.value)
    }

    @Test
    fun stopsAfterBoundedCasConflictsWithDomainError() = runSyncTest {
        vault.state = localState()
        remote.currentSnapshot = RemoteSnapshot(0, null)
        remote.alwaysConflict = true

        val error = runCatching { coordinator.syncNow() }.exceptionOrNull()
        assertTrue(error is SyncConflictRetriesExhausted)
        assertEquals(SyncCoordinator.MAX_CAS_ATTEMPTS, remote.puts.size)
    }

    @Test
    fun localChangeDuringNetworkFlightIsNotOverwrittenAndRemainsPending() = runSyncTest {
        vault.state = localState(baseline = mapOf("theme_mode" to SyncValue("string", "LIGHT")))
        settings.values = mapOf("theme_mode" to SyncValue("string", "DARK"))
        remote.currentSnapshot = RemoteSnapshot(1, envelope(snapshot("theme_mode", SyncValue("string", "LIGHT"), 1, "device-1")))
        remote.afterPut = { settings.values = mapOf("theme_mode" to SyncValue("string", "CONCURRENT")) }

        coordinator.syncNow()

        assertEquals("CONCURRENT", settings.values["theme_mode"]?.toPreference())
        assertEquals(SyncValue("string", "DARK"), requireNotNull(vault.state).baselinePreferences["theme_mode"])
        remote.afterPut = null
        coordinator.syncNow()
        assertEquals("CONCURRENT", SyncSnapshotCrypto.decrypt(remote.puts.last().ciphertext, "profile-1", profileKey)
            .fields.getValue("theme_mode").value?.value)
    }

    @Test
    fun localChangeBetweenFinalCheckAndApplyIsNotOverwritten() = runSyncTest {
        val original = SyncValue("string", "ORIGINAL")
        val localEdit = SyncValue("string", "LOCAL-EDIT")
        val remoteValue = SyncValue("string", "REMOTE")
        vault.state = localState(baseline = mapOf("theme_mode" to original))
        settings.values = mapOf("theme_mode" to original)
        remote.currentSnapshot = RemoteSnapshot(3, envelope(snapshot("theme_mode", remoteValue, 2, "device-2")))
        settings.beforeApply = { settings.values = mapOf("theme_mode" to localEdit) }

        coordinator.syncNow()

        assertEquals(localEdit, settings.values["theme_mode"])
        assertEquals(remoteValue, requireNotNull(vault.state).baselinePreferences["theme_mode"])
        assertTrue(remote.puts.isEmpty())
    }

    @Test
    fun emptyRemoteProfileSeedsLocalSettingsEvenWhenLocalBaselineMatches() = runSyncTest {
        val current = SyncValue("string", "DARK")
        vault.state = localState(baseline = mapOf("theme_mode" to current))
        settings.values = mapOf("theme_mode" to current)
        remote.currentSnapshot = RemoteSnapshot(0, null)

        coordinator.syncNow()

        val uploaded = SyncSnapshotCrypto.decrypt(remote.puts.single().ciphertext, "profile-1", profileKey)
        assertEquals(current, uploaded.fields.getValue("theme_mode").value)
    }

    private fun runSyncTest(block: suspend Fixture.() -> Unit) = kotlinx.coroutines.runBlocking { Fixture().block() }

    private inner class Fixture {
        val vault = FakeVault(localState())
        val settings = FakeSettings()
        val remote = FakeRemote()
        val coordinator = SyncCoordinator(
            remoteFactory = { remote },
            vault = vault,
            settings = settings,
        )

        init { settings.values = emptyMap() }
    }

    private class FakeVault(var state: SyncLocalState?) : SyncLocalStateStore {
        override fun load(): SyncLocalState? = state
        override fun save(state: SyncLocalState) { this.state = state }
        override fun clear() { state = null }
    }

    private class FakeSettings : SyncSettingsPort {
        var values: Map<String, SyncValue> = emptyMap()
        var beforeApply: (() -> Unit)? = null
        override suspend fun export(): Map<String, SyncValue> = values.toMap()
        override suspend fun apply(snapshot: SyncSnapshot, expectedCurrent: Map<String, SyncValue>) {
            beforeApply?.invoke()
            beforeApply = null
            val updated = values.toMutableMap()
            snapshot.fields.forEach { (key, field) ->
                if (key !in SyncPreferencePolicy.allowedKeys || values[key] != expectedCurrent[key]) return@forEach
                if (field.value == null) updated.remove(key) else updated[key] = field.value
            }
            values = updated
        }
    }

    private class FakeRemote : SyncRemote {
        data class Put(val expectedRevision: Long, val ciphertext: String)
        val puts = mutableListOf<Put>()
        var currentSnapshot = RemoteSnapshot(0, null)
        var closeCount = 0

        var alwaysConflict = false
        var onPutConflict: (() -> Unit)? = null
        var afterPut: (() -> Unit)? = null

        override suspend fun createProfile() = CreatedProfile("profile-1", "device-1", "device-token", 0)
        override suspend fun getSnapshot(profileId: String, deviceToken: String): RemoteSnapshot = currentSnapshot
        override suspend fun putSnapshot(profileId: String, deviceToken: String, expectedRevision: Long, schemaVersion: Int, ciphertext: String): Long {
            puts += Put(expectedRevision, ciphertext)
            afterPut?.invoke()
            if (alwaysConflict || (onPutConflict != null && puts.size == 1)) {
                onPutConflict?.invoke()
                throw RevisionConflict(currentSnapshot.revision)
            }
            currentSnapshot = RemoteSnapshot(expectedRevision + 1, SnapshotEnvelope(schemaVersion, ciphertext))
            return expectedRevision + 1
        }
        override suspend fun createPairing(profileId: String, deviceToken: String) = PairingTicket("pair", "later")
        override suspend fun claimPairing(profileId: String, pairingToken: String) = ClaimedDevice(profileId, "device-2", "token-2")
        override suspend fun listDevices(profileId: String, deviceToken: String) = emptyList<SyncDevice>()
        override suspend fun revokeDevice(profileId: String, deviceToken: String, targetDeviceId: String) = Unit
        override suspend fun deleteProfile(profileId: String, deviceToken: String) = Unit
        override fun close() { closeCount++ }
    }

    private fun localState(baseline: Map<String, SyncValue> = emptyMap()) = SyncLocalState(
        serverUrl = "https://sync.example.test",
        profileId = "profile-1",
        deviceId = "device-1",
        deviceToken = "device-token",
        profileKey = profileKeyEncoded,
        revision = 0,
        baselinePreferences = baseline,
        enabled = true,
    )

    private fun snapshot(key: String, value: SyncValue, counter: Long, device: String) =
        SyncSnapshot(fields = mapOf(key to SyncField(FieldClock(counter, device), value)))

    private fun envelope(snapshot: SyncSnapshot) = SnapshotEnvelope(
        snapshot.schemaVersion,
        SyncSnapshotCrypto.encrypt(snapshot, "profile-1", profileKey),
    )
}
