package com.music.bitchord.sync

import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Platform ports. Android supplies a fail-closed Keystore key and private preferences. */
fun interface SyncVaultKeyProvider {
    fun getOrCreateKey(): SecretKey
}

interface SyncVaultStorage {
    fun read(): String?
    fun write(value: String): Boolean
    fun clear(): Boolean
}

/** Device-local sync credentials and the last successful merge baseline. */
@Serializable
data class SyncLocalState(
    val serverUrl: String,
    val profileId: String,
    val deviceId: String,
    val deviceToken: String,
    /** Base64 for the random 32-byte profile data-encryption key. */
    val profileKey: String,
    val revision: Long = 0,
    val baselinePreferences: Map<String, SyncValue> = emptyMap(),
    val enabled: Boolean = false,
    val version: Int = CURRENT_VERSION,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

class SyncVaultException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** AES-GCM sealed local state. Keystore failure or authentication failure never falls back to plaintext. */
class EncryptedSyncVault(
    private val storage: SyncVaultStorage,
    private val keyProvider: SyncVaultKeyProvider,
) : SyncLocalStateStore {
    override fun load(): SyncLocalState? {
        val encoded = storage.read() ?: return null
        return try {
            require(encoded.length <= MAX_ENCODED_LENGTH) { "Sync vault record is too large" }
            val payload = Base64.getDecoder().decode(encoded)
            require(payload.size in MIN_PAYLOAD_LENGTH..MAX_PAYLOAD_LENGTH) { "Malformed sync vault record" }
            require((payload[0].toInt() and 0xff) == FORMAT_VERSION) { "Unsupported sync vault format" }

            val nonceStart = 1
            val nonceEnd = nonceStart + NONCE_BYTES
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                keyProvider.getOrCreateKey(),
                GCMParameterSpec(TAG_BITS, payload.copyOfRange(nonceStart, nonceEnd)),
            )
            cipher.updateAAD(AAD)
            val plaintext = cipher.doFinal(payload.copyOfRange(nonceEnd, payload.size))
            require(plaintext.size <= MAX_PLAINTEXT_LENGTH) { "Sync vault plaintext is too large" }
            val state = json.decodeFromString<SyncLocalState>(plaintext.decodeToString())
            validate(state)
            state
        } catch (error: Exception) {
            if (error is SyncVaultException) throw error
            throw SyncVaultException("Sync credentials cannot be decrypted; this device must be re-linked.", error)
        }
    }

    override fun save(state: SyncLocalState) {
        validate(state)
        val plaintext = json.encodeToString(state).encodeToByteArray()
        require(plaintext.size <= MAX_PLAINTEXT_LENGTH) { "Sync vault state is too large" }
        try {
            val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.ENCRYPT_MODE,
                keyProvider.getOrCreateKey(),
                GCMParameterSpec(TAG_BITS, nonce),
            )
            cipher.updateAAD(AAD)
            val encrypted = cipher.doFinal(plaintext)
            val payload = ByteArrayOutputStream(1 + nonce.size + encrypted.size).apply {
                write(FORMAT_VERSION)
                write(nonce)
                write(encrypted)
            }.toByteArray()
            val encoded = Base64.getEncoder().encodeToString(payload)
            check(storage.write(encoded)) { "Could not persist encrypted sync credentials" }
        } catch (error: Exception) {
            if (error is SyncVaultException) throw error
            throw SyncVaultException("Could not securely store sync credentials.", error)
        }
    }

    override fun clear() {
        check(storage.clear()) { "Could not clear encrypted sync credentials" }
    }

    private fun validate(state: SyncLocalState) {
        require(state.version == SyncLocalState.CURRENT_VERSION) { "Unsupported sync vault schema" }
        require(state.serverUrl.isNotBlank() && state.serverUrl.length <= MAX_URL_LENGTH) { "Invalid sync server URL" }
        require(state.profileId.isNotBlank() && state.profileId.length <= MAX_ID_LENGTH) { "Invalid sync profile ID" }
        require(state.deviceId.isNotBlank() && state.deviceId.length <= MAX_ID_LENGTH) { "Invalid sync device ID" }
        require(state.deviceToken.isNotBlank() && state.deviceToken.length <= MAX_TOKEN_LENGTH) { "Invalid sync device token" }
        require(state.revision >= 0) { "Invalid sync revision" }
        require(Base64.getDecoder().decode(state.profileKey).size == PROFILE_KEY_BYTES) {
            "Sync profile key must be 256 bits"
        }
    }

    companion object {
        private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        private val random = SecureRandom()
        private val AAD = "bitchord-sync-local-vault-v1".encodeToByteArray()
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val FORMAT_VERSION = 1
        private const val NONCE_BYTES = 12
        private const val TAG_BITS = 128
        private const val TAG_BYTES = TAG_BITS / 8
        private const val PROFILE_KEY_BYTES = 32
        private const val MAX_ID_LENGTH = 128
        private const val MAX_TOKEN_LENGTH = 512
        private const val MAX_URL_LENGTH = 2_048
        private const val MAX_PLAINTEXT_LENGTH = 1 * 1024 * 1024
        private const val MIN_PAYLOAD_LENGTH = 1 + NONCE_BYTES + TAG_BYTES
        private const val MAX_PAYLOAD_LENGTH = 1 + NONCE_BYTES + MAX_PLAINTEXT_LENGTH + TAG_BYTES
        private const val MAX_ENCODED_LENGTH = ((MAX_PAYLOAD_LENGTH + 2) / 3) * 4
    }
}
