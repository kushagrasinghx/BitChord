package com.music.bitchord.sync

import java.util.Base64

/** Async boundary for all network operations used by the sync coordinator. */
interface SyncRemote : AutoCloseable {
    suspend fun createProfile(): CreatedProfile
    suspend fun getSnapshot(profileId: String, deviceToken: String): RemoteSnapshot
    suspend fun putSnapshot(
        profileId: String,
        deviceToken: String,
        expectedRevision: Long,
        schemaVersion: Int,
        ciphertext: String,
    ): Long
    suspend fun createPairing(profileId: String, deviceToken: String): PairingTicket
    suspend fun claimPairing(profileId: String, pairingToken: String): ClaimedDevice
    suspend fun listDevices(profileId: String, deviceToken: String): List<SyncDevice>
    suspend fun revokeDevice(profileId: String, deviceToken: String, targetDeviceId: String)
    suspend fun deleteProfile(profileId: String, deviceToken: String)
    override fun close()
}

/** Storage boundary for device-encrypted sync state. */
interface SyncLocalStateStore {
    fun load(): SyncLocalState?
    fun save(state: SyncLocalState)
    fun clear()
}

/** Settings adapter exports only the explicit policy allowlist and applies a merged snapshot. */
interface SyncSettingsPort {
    suspend fun export(): Map<String, SyncValue>
    suspend fun apply(snapshot: SyncSnapshot, expectedCurrent: Map<String, SyncValue>)
}

class SyncProfileMissing : Exception("Sync profile is not available on this device")
class SyncProfileDisabled : Exception("Sync is disabled on this device")
class SyncConflictRetriesExhausted : Exception("Sync could not save after repeated revision conflicts")

/**
 * Coordinates encrypted snapshot synchronization. Calls are suspendable, so network work never
 * blocks the caller's playback thread. The profile data key is kept only in encrypted local state.
 */
class SyncCoordinator(
    private val remoteFactory: (baseUrl: String) -> SyncRemote,
    private val vault: SyncLocalStateStore,
    private val settings: SyncSettingsPort,
) {
    suspend fun syncNow() {
        val profileId = vault.load()?.profileId ?: throw SyncProfileMissing()
        SyncOperationMutex.withProfileLock(profileId) {
            syncNowLocked()
        }
    }

    private suspend fun syncNowLocked() {
        val initialState = vault.load() ?: throw SyncProfileMissing()
        if (!initialState.enabled) throw SyncProfileDisabled()
        val profileKey = decodeProfileKey(initialState.profileKey)
        val capturedPreferences = settings.export()
        repeat(MAX_CAS_ATTEMPTS) { attempt ->
            val remote = remoteFactory(initialState.serverUrl)
            try {
                val remoteState = remote.getSnapshot(initialState.profileId, initialState.deviceToken)
                val remoteSnapshot = remoteState.snapshot?.let { envelope ->
                    require(envelope.schemaVersion == SyncSnapshot.CURRENT_SCHEMA_VERSION) {
                        "Unsupported sync schema: ${envelope.schemaVersion}"
                    }
                    SyncSnapshotCrypto.decrypt(envelope.ciphertext, initialState.profileId, profileKey)
                } ?: SyncSnapshot()

                // No remote snapshot means this is a fresh/unseeded profile. Treat every
                // current allowlisted preference as local state, even if a stale local
                // baseline exists (for example, pairing was claimed before an interrupted import).
                val baseline = if (remoteState.snapshot == null) emptyMap() else initialState.baselinePreferences
                val withLocalEdits = recordLocalChanges(
                    snapshot = remoteSnapshot,
                    baseline = baseline,
                    current = capturedPreferences,
                    deviceId = initialState.deviceId,
                )
                val needsUpload = remoteState.snapshot == null || withLocalEdits != remoteSnapshot
                val uploadedSnapshot: SyncSnapshot
                val newRevision: Long
                if (needsUpload) {
                    val ciphertext = SyncSnapshotCrypto.encrypt(withLocalEdits, initialState.profileId, profileKey)
                    try {
                        newRevision = remote.putSnapshot(
                            initialState.profileId,
                            initialState.deviceToken,
                            remoteState.revision,
                            withLocalEdits.schemaVersion,
                            ciphertext,
                        )
                    } catch (conflict: RevisionConflict) {
                        if (attempt == MAX_CAS_ATTEMPTS - 1) throw SyncConflictRetriesExhausted()
                        return@repeat
                    }
                    uploadedSnapshot = withLocalEdits
                } else {
                    uploadedSnapshot = withLocalEdits
                    newRevision = remoteState.revision
                }

                // Do not apply over preference edits made after the network snapshot was captured.
                val latestPreferences = settings.export()
                val safeToApply = uploadedSnapshot.fields.filter { (key, _) ->
                    latestPreferences[key] == capturedPreferences[key]
                }
                settings.apply(SyncSnapshot(uploadedSnapshot.schemaVersion, safeToApply), latestPreferences)
                vault.save(
                    initialState.copy(
                        revision = newRevision,
                        baselinePreferences = uploadedSnapshot.fields
                            .filterKeys { it in SyncPreferencePolicy.allowedKeys }
                            .mapNotNull { (key, field) -> field.value?.let { key to it } }
                            .toMap(),
                    ),
                )
                return
            } finally {
                remote.close()
            }
        }
        // The loop exits only after a final RevisionConflict, which is translated above.
        throw SyncConflictRetriesExhausted()
    }

    private fun decodeProfileKey(encoded: String): ByteArray {
        val decoded = runCatching { Base64.getDecoder().decode(encoded) }.getOrElse { throw SyncProfileMissing() }
        if (decoded.size != PROFILE_KEY_BYTES) throw SyncProfileMissing()
        return decoded
    }

    companion object {
        const val MAX_CAS_ATTEMPTS = 3
        private const val PROFILE_KEY_BYTES = 32
    }
}
