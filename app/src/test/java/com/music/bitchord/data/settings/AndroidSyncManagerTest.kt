package com.music.bitchord.data.settings

import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.music.bitchord.sync.ClaimedDevice
import com.music.bitchord.sync.CreatedProfile
import com.music.bitchord.sync.RemoteSnapshot
import com.music.bitchord.sync.RevisionConflict
import com.music.bitchord.sync.SyncApiException
import com.music.bitchord.sync.SyncLocalState
import com.music.bitchord.sync.SyncLocalStateStore
import com.music.bitchord.sync.SyncPreferencePolicy
import com.music.bitchord.sync.SyncRemote
import com.music.bitchord.sync.SyncSettingsPort
import com.music.bitchord.sync.SyncDevice
import com.music.bitchord.sync.SyncSnapshot
import com.music.bitchord.sync.SyncSnapshotCrypto
import com.music.bitchord.sync.SyncValue
import com.music.bitchord.data.settings.SyncSchedulePort
import com.music.bitchord.sync.SnapshotEnvelope
import com.music.bitchord.sync.PairingTicket

class AndroidSyncManagerTest {
    private val profileKey = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })

    @Test
    fun createsOptInProfileAndUploadsOnlyEncryptedSettings() = runBlocking {
        val vault = MemoryVault()
        val settings = MemorySettings(mapOf("theme_mode" to SyncValue("string", "DARK")))
        val remote = FakeRemote()
        val manager = AndroidSyncManager(vault, { remote }, settings)

        val state = manager.createProfile("https://sync.example.com/sync")

        assertTrue(state.enabled)
        assertEquals("profile-1", state.profileId)
        assertEquals(1L, state.revision)
        assertEquals(1, remote.putCount)
        assertFalse(remote.lastCiphertext.contains("DARK"))
        assertEquals(
            "DARK",
            SyncSnapshotCrypto.decrypt(remote.lastCiphertext, "profile-1", Base64.getDecoder().decode(state.profileKey))
                .fields.getValue("theme_mode").value?.value,
        )
        assertEquals(2, remote.closeCount)
    }

    @Test
    fun deletesNewRemoteProfileWhenLocalCredentialSaveFails() = runBlocking {
        val vault = MemoryVault(failOnSave = true)
        val remote = FakeRemote()
        val manager = AndroidSyncManager(vault, { remote }, MemorySettings(emptyMap()))

        var saveFailure = false
        try {
            manager.createProfile("https://sync.example.com/sync")
        } catch (_: IllegalStateException) {
            saveFailure = true
        }

        assertTrue(saveFailure)
        assertEquals("profile-1", remote.deletedProfileId)
        assertEquals(1, remote.closeCount)
    }

    @Test
    fun pairingInvitationUsesOneTimeTokenAndAcceptAppliesRemoteSnapshot() = runBlocking {
        val sourceVault = MemoryVault(initialState())
        val targetVault = MemoryVault()
        val settings = MemorySettings(mapOf("theme_mode" to SyncValue("string", "LIGHT")))
        val remote = FakeRemote().apply {
            currentSnapshot = RemoteSnapshot(
                6,
                SnapshotEnvelope(
                    1,
                    SyncSnapshotCrypto.encrypt(
                        SyncSnapshot(fields = mapOf(
                            "theme_mode" to com.music.bitchord.sync.SyncField(
                                com.music.bitchord.sync.FieldClock(2, "source-device"),
                                SyncValue("string", "DARK"),
                            ),
                        )),
                        "profile-1",
                        Base64.getDecoder().decode(profileKey),
                    ),
                ),
            )
        }
        val sourceManager = AndroidSyncManager(sourceVault, { remote }, settings)
        val targetManager = AndroidSyncManager(targetVault, { remote }, settings)

        val code = sourceManager.createPairingInvitation()
        val invite = SyncInvitationCodec.decode(code)
        val linked = targetManager.acceptPairingInvitation(code)

        assertEquals("one-time", invite.pairingToken)
        assertEquals("DARK", settings.values["theme_mode"]?.value)
        assertEquals("device-2", linked.deviceId)
        assertEquals(6L, linked.revision)
        assertEquals(SyncValue("string", "DARK"), linked.baselinePreferences["theme_mode"])
    }

    @Test
    fun pairingVaultSaveFailureExplainsHowToRevokeTheClaimedDevice() = runBlocking {
        val vault = MemoryVault(failOnSave = true)
        val remote = FakeRemote()
        val schedule = MemorySyncSchedule()
        val manager = AndroidSyncManager(vault, { remote }, MemorySettings(emptyMap()), schedule)
        val code = SyncInvitationCodec.encode(
            SyncInvitation(
                serverUrl = "https://sync.example.com/sync",
                profileId = "profile-1",
                pairingToken = "one-time",
                profileKey = profileKey,
            ),
        )

        var failure: Exception? = null
        try {
            manager.acceptPairingInvitation(code)
        } catch (error: Exception) {
            failure = error
        }

        assertTrue(failure is SyncPairingPersistenceException)
        assertEquals("device-2", (failure as SyncPairingPersistenceException).deviceId)
        assertTrue(requireNotNull(failure.message).lowercase().contains("revoke"))
        assertEquals(1, remote.closeCount)
        assertEquals(listOf(false), schedule.enabledChanges)
    }

    @Test
    fun listsLinkedDevicesAndRevokesOnlyTheSelectedPeer() = runBlocking {
        val vault = MemoryVault(initialState())
        val remote = FakeRemote().apply {
            devices = listOf(
                SyncDevice("device-1", "2026-11-01T12:00:00Z", current = true),
                SyncDevice("device-2", "2026-11-02T12:00:00Z", current = false),
            )
        }
        val manager = AndroidSyncManager(vault, { remote }, MemorySettings(emptyMap()))

        assertEquals(2, manager.listDevices().size)
        manager.revokeDevice("device-2")

        assertEquals("device-2", remote.revokedDeviceId)
        assertEquals(2, remote.closeCount)
    }

    @Test
    fun resetUnreadableLocalSyncDataAndCancelAutomaticSchedule() = runBlocking {
        val vault = MemoryVault(initialState(), failOnLoad = true)
        val schedule = MemorySyncSchedule()
        val manager = AndroidSyncManager(vault, { FakeRemote() }, MemorySettings(emptyMap()), schedule)

        manager.clearLocalSyncData()

        assertNull(vault.state)
        assertEquals(listOf(false), schedule.enabledChanges)
    }

    @Test
    fun pausingAndResumingSyncUpdatesAutomaticSchedule() = runBlocking {
        val vault = MemoryVault(initialState())
        val schedule = MemorySyncSchedule()
        val manager = AndroidSyncManager(vault, { FakeRemote() }, MemorySettings(emptyMap()), schedule)

        manager.setEnabled(false)
        assertFalse(requireNotNull(vault.state).enabled)
        assertEquals(listOf(false), schedule.enabledChanges)

        manager.setEnabled(true)
        assertTrue(requireNotNull(vault.state).enabled)
        assertEquals(listOf(false, true), schedule.enabledChanges)
    }

    private fun initialState() = SyncLocalState(
        serverUrl = "https://sync.example.com/sync",
        profileId = "profile-1",
        deviceId = "device-1",
        deviceToken = "device-1-token",
        profileKey = profileKey,
        enabled = true,
    )

    private class MemoryVault(
        var state: SyncLocalState? = null,
        private val failOnSave: Boolean = false,
        private val failOnLoad: Boolean = false,
    ) : SyncLocalStateStore {
        override fun load(): SyncLocalState? {
            if (failOnLoad) throw IllegalStateException("local vault unreadable")
            return state
        }
        override fun save(state: SyncLocalState) {
            if (failOnSave) throw IllegalStateException("local vault write failed")
            this.state = state
        }
        override fun clear() { state = null }
    }

    private class MemorySyncSchedule : SyncSchedulePort {
        val enabledChanges = mutableListOf<Boolean>()
        override fun setEnabled(enabled: Boolean) { enabledChanges += enabled }
    }

    private class MemorySettings(var values: Map<String, SyncValue>) : SyncSettingsPort {
        override suspend fun export(): Map<String, SyncValue> = values
        override suspend fun apply(snapshot: SyncSnapshot, expectedCurrent: Map<String, SyncValue>) {
            values = SyncPreferencePolicy.applySnapshot(
                values.mapValues { it.value.toPreference() },
                snapshot.fields.filter { (key, _) -> values[key] == expectedCurrent[key] },
            ).mapNotNull { (key, value) -> SyncValue.fromPreference(value)?.let { key to it } }.toMap()
        }
    }

    private class FakeRemote : SyncRemote {
        var currentSnapshot = RemoteSnapshot(0, null)
        var devices: List<SyncDevice> = emptyList()
        var revokedDeviceId: String? = null
        var deletedProfileId: String? = null
        var putCount = 0
        var closeCount = 0
        var lastCiphertext = ""

        override suspend fun createProfile() = CreatedProfile("profile-1", "device-1", "device-1-token", 0)
        override suspend fun getSnapshot(profileId: String, deviceToken: String) = currentSnapshot
        override suspend fun putSnapshot(
            profileId: String,
            deviceToken: String,
            expectedRevision: Long,
            schemaVersion: Int,
            ciphertext: String,
        ): Long {
            putCount++
            lastCiphertext = ciphertext
            currentSnapshot = RemoteSnapshot(expectedRevision + 1, SnapshotEnvelope(schemaVersion, ciphertext))
            return expectedRevision + 1
        }
        override suspend fun createPairing(profileId: String, deviceToken: String) = PairingTicket("one-time", "expires")
        override suspend fun claimPairing(profileId: String, pairingToken: String) = ClaimedDevice(profileId, "device-2", "device-2-token")
        override suspend fun listDevices(profileId: String, deviceToken: String) = devices
        override suspend fun revokeDevice(profileId: String, deviceToken: String, targetDeviceId: String) {
            revokedDeviceId = targetDeviceId
        }
        override suspend fun deleteProfile(profileId: String, deviceToken: String) {
            deletedProfileId = profileId
        }
        override fun close() { closeCount++ }
    }
}
