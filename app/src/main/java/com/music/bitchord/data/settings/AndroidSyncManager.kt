package com.music.bitchord.data.settings

import android.content.Context
import com.music.bitchord.sync.SyncApiClient
import com.music.bitchord.sync.SyncCoordinator
import com.music.bitchord.sync.SyncDevice
import com.music.bitchord.sync.SyncLocalState
import com.music.bitchord.sync.SyncLocalStateStore
import com.music.bitchord.sync.SyncOperationMutex
import com.music.bitchord.sync.SyncPreferencePolicy
import com.music.bitchord.sync.SyncRemote
import com.music.bitchord.sync.SyncSettingsPort
import com.music.bitchord.sync.SyncSnapshot
import com.music.bitchord.sync.SyncSnapshotCrypto
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun interface SyncSchedulePort {
    fun setEnabled(enabled: Boolean)
}

/** Pairing was claimed, but local credential persistence failed; the source device must revoke the orphan. */
internal class SyncPairingPersistenceException(
    val deviceId: String,
    cause: Throwable,
) : IllegalStateException(
    "Pairing was claimed, but this device couldn't save its sync credentials. Revoke device ${deviceId.take(8)} " +
        "from another linked device, then create a fresh invitation.",
    cause,
)

/** Owns opt-in profile creation and device pairing for the Android app. */
internal class AndroidSyncManager(
    private val vault: SyncLocalStateStore,
    private val remoteFactory: (String) -> SyncRemote,
    private val settings: SyncSettingsPort,
    private val syncSchedule: SyncSchedulePort = SyncSchedulePort { },
) {
    constructor(context: Context) : this(
        AndroidSyncVault(context),
        { baseUrl -> SyncApiClient.create(baseUrl) },
        AppSettingsSyncPort,
        AndroidSyncSchedule(context),
    )

    suspend fun createProfile(serverUrl: String): SyncLocalState = withContext(Dispatchers.IO) {
        check(vault.load() == null) { "This device already has a sync profile" }
        val profileKeyBytes = ByteArray(PROFILE_KEY_BYTES).also(SecureRandom()::nextBytes)
        val remote = remoteFactory(serverUrl)
        try {
            val created = remote.createProfile()
            val state = SyncLocalState(
                serverUrl = serverUrl.trimEnd('/'),
                profileId = created.profileId,
                deviceId = created.deviceId,
                deviceToken = created.deviceToken,
                profileKey = Base64.getEncoder().encodeToString(profileKeyBytes),
                revision = created.revision,
                baselinePreferences = emptyMap(),
                enabled = true,
            )
            try {
                vault.save(state)
            } catch (saveError: Exception) {
                runCatching { remote.deleteProfile(created.profileId, created.deviceToken) }
                    .exceptionOrNull()
                    ?.let(saveError::addSuppressed)
                throw saveError
            }
            state
        } finally {
            remote.close()
        }

        try {
            coordinator().syncNow()
        } finally {
            syncSchedule.setEnabled(runCatching { vault.load()?.enabled == true }.getOrDefault(false))
        }
        requireNotNull(vault.load()) { "Sync profile was not saved" }
    }

    suspend fun createPairingInvitation(): String = withContext(Dispatchers.IO) {
        val state = requireEnabledState()
        val remote = remoteFactory(state.serverUrl)
        try {
            val ticket = remote.createPairing(state.profileId, state.deviceToken)
            SyncInvitationCodec.encode(
                SyncInvitation(
                    serverUrl = state.serverUrl,
                    profileId = state.profileId,
                    pairingToken = ticket.pairingToken,
                    profileKey = state.profileKey,
                ),
            )
        } finally {
            remote.close()
        }
    }

    suspend fun acceptPairingInvitation(code: String): SyncLocalState = withContext(Dispatchers.IO) {
        check(vault.load() == null) { "Unlink the existing profile before joining another" }
        val invite = SyncInvitationCodec.decode(code)
        val profileKey = Base64.getDecoder().decode(invite.profileKey)
        val remote = remoteFactory(invite.serverUrl)
        try {
            try {
                val claimed = remote.claimPairing(invite.profileId, invite.pairingToken)
                // Persist the one-use claim immediately so an interrupted import cannot strand the device.
                val initialState = SyncLocalState(
                    serverUrl = invite.serverUrl.trimEnd('/'),
                    profileId = claimed.profileId,
                    deviceId = claimed.deviceId,
                    deviceToken = claimed.deviceToken,
                    profileKey = invite.profileKey,
                    revision = 0,
                    baselinePreferences = settings.export(),
                    enabled = true,
                )
                try {
                    vault.save(initialState)
                } catch (persistenceError: Exception) {
                    throw SyncPairingPersistenceException(claimed.deviceId, persistenceError)
                }

                val remoteState = remote.getSnapshot(claimed.profileId, claimed.deviceToken)
                val snapshot = remoteState.snapshot?.let { envelope ->
                    require(envelope.schemaVersion == SyncSnapshot.CURRENT_SCHEMA_VERSION) {
                        "Unsupported sync schema: ${envelope.schemaVersion}"
                    }
                    SyncSnapshotCrypto.decrypt(envelope.ciphertext, claimed.profileId, profileKey).also {
                        require(it.schemaVersion == envelope.schemaVersion) { "Sync snapshot schema mismatch" }
                    }
                }

                if (snapshot == null) {
                    // An empty remote profile should adopt this device's current allowlisted preferences.
                    vault.save(initialState.copy(revision = remoteState.revision, baselinePreferences = emptyMap()))
                } else {
                    val expectedCurrent = settings.export()
                    settings.apply(snapshot, expectedCurrent)
                    vault.save(
                        initialState.copy(
                            revision = remoteState.revision,
                            baselinePreferences = snapshot.fields
                                .filterKeys { it in SyncPreferencePolicy.allowedKeys }
                                .mapNotNull { (key, field) -> field.value?.let { key to it } }
                                .toMap(),
                        ),
                    )
                }
            } finally {
                remote.close()
            }

            coordinator().syncNow()
            requireNotNull(vault.load()) { "Paired sync state was not saved" }
        } finally {
            syncSchedule.setEnabled(runCatching { vault.load()?.enabled == true }.getOrDefault(false))
        }
    }

    suspend fun listDevices(): List<SyncDevice> = withContext(Dispatchers.IO) {
        val state = vault.load() ?: throw IllegalStateException("No sync profile is linked")
        val remote = remoteFactory(state.serverUrl)
        try {
            remote.listDevices(state.profileId, state.deviceToken)
        } finally {
            remote.close()
        }
    }

    suspend fun revokeDevice(targetDeviceId: String) = withContext(Dispatchers.IO) {
        val state = vault.load() ?: throw IllegalStateException("No sync profile is linked")
        require(targetDeviceId != state.deviceId) { "This device cannot revoke itself" }
        val remote = remoteFactory(state.serverUrl)
        try {
            remote.revokeDevice(state.profileId, state.deviceToken, targetDeviceId)
        } finally {
            remote.close()
        }
    }

    suspend fun syncNow() = withContext(Dispatchers.IO) {
        coordinator().syncNow()
    }

    suspend fun setEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        val state = vault.load() ?: throw IllegalStateException("No sync profile is linked")
        SyncOperationMutex.withProfileLock(state.profileId) {
            val latest = vault.load() ?: throw IllegalStateException("No sync profile is linked")
            vault.save(latest.copy(enabled = enabled))
            syncSchedule.setEnabled(enabled)
        }
    }

    suspend fun clearLocalSyncData() = withContext(Dispatchers.IO) {
        val lockKey = runCatching { vault.load()?.profileId }.getOrNull() ?: LOCAL_RESET_LOCK
        SyncOperationMutex.withProfileLock(lockKey) {
            try {
                vault.clear()
            } finally {
                syncSchedule.setEnabled(false)
            }
        }
    }

    suspend fun localState(): SyncLocalState? = withContext(Dispatchers.IO) { vault.load() }

    private fun requireEnabledState(): SyncLocalState {
        val state = vault.load() ?: throw IllegalStateException("No sync profile is linked")
        check(state.enabled) { "Sync is disabled on this device" }
        return state
    }

    private fun coordinator() = SyncCoordinator(remoteFactory, vault, settings)

    private companion object {
        const val PROFILE_KEY_BYTES = 32
        const val LOCAL_RESET_LOCK = "local-sync-reset"
    }
}
