package com.music.bitchord.sync

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** AES-256-GCM snapshot codec. The profile key is supplied by the platform vault and never persisted here. */
object SyncSnapshotCrypto {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val random = SecureRandom()

    /** Encrypts one schema-versioned snapshot; the returned Base64 payload is safe to upload. */
    fun encrypt(snapshot: SyncSnapshot, profileId: String, profileKey: ByteArray): String {
        validateProfile(profileId, profileKey)
        val plaintext = json.encodeToString(snapshot).encodeToByteArray()
        require(plaintext.size <= MAX_PLAINTEXT_BYTES) { "Sync snapshot is too large" }

        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(profileKey.copyOf(), AES),
            GCMParameterSpec(TAG_BITS, nonce),
        )
        cipher.updateAAD(associatedData(profileId, snapshot.schemaVersion))
        val encrypted = cipher.doFinal(plaintext)

        val payload = ByteArrayOutputStream(FORMAT_HEADER_BYTES + nonce.size + encrypted.size).apply {
            write(FORMAT_VERSION)
            write(ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN).putInt(snapshot.schemaVersion).array())
            write(nonce)
            write(encrypted)
        }.toByteArray()
        return Base64.getEncoder().encodeToString(payload)
    }

    /** Decrypts and authenticates both the ciphertext and its profile/schema binding. */
    fun decrypt(ciphertext: String, profileId: String, profileKey: ByteArray): SyncSnapshot {
        validateProfile(profileId, profileKey)
        require(ciphertext.length <= MAX_ENCODED_BYTES) { "Encrypted sync snapshot is too large" }
        val payload = Base64.getDecoder().decode(ciphertext)
        require(payload.size in MIN_PAYLOAD_BYTES..MAX_PAYLOAD_BYTES) { "Malformed encrypted sync snapshot" }
        require(payload[0].toInt() and 0xff == FORMAT_VERSION) { "Unsupported encrypted sync format" }

        val schemaVersion = ByteBuffer.wrap(payload, 1, Int.SIZE_BYTES)
            .order(ByteOrder.BIG_ENDIAN)
            .int
        require(schemaVersion == SyncSnapshot.CURRENT_SCHEMA_VERSION) { "Unsupported sync schema: $schemaVersion" }

        val nonceStart = FORMAT_HEADER_BYTES
        val nonceEnd = nonceStart + NONCE_BYTES
        val nonce = payload.copyOfRange(nonceStart, nonceEnd)
        val encrypted = payload.copyOfRange(nonceEnd, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(profileKey.copyOf(), AES),
            GCMParameterSpec(TAG_BITS, nonce),
        )
        cipher.updateAAD(associatedData(profileId, schemaVersion))
        val plaintext = cipher.doFinal(encrypted)
        require(plaintext.size <= MAX_PLAINTEXT_BYTES) { "Decrypted sync snapshot is too large" }
        val snapshot = json.decodeFromString<SyncSnapshot>(plaintext.decodeToString())
        require(snapshot.schemaVersion == schemaVersion) { "Encrypted schema header does not match payload" }
        return snapshot
    }

    private fun validateProfile(profileId: String, profileKey: ByteArray) {
        require(profileId.isNotBlank() && profileId.length <= MAX_PROFILE_ID_LENGTH) { "Invalid sync profile ID" }
        require(profileKey.size == KEY_BYTES) { "Sync profile key must be 256 bits" }
    }

    private fun associatedData(profileId: String, schemaVersion: Int): ByteArray {
        val id = profileId.encodeToByteArray()
        return ByteArrayOutputStream().apply {
            write(AAD_DOMAIN.encodeToByteArray())
            write(0)
            write(PROTOCOL_VERSION)
            write(ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN).putInt(id.size).array())
            write(id)
            write(ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN).putInt(schemaVersion).array())
        }.toByteArray()
    }

    private const val AES = "AES"
    private const val AAD_DOMAIN = "bitchord-sync-snapshot"
    private const val PROTOCOL_VERSION = 1
    private const val FORMAT_VERSION = 1
    private const val KEY_BYTES = 32
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val TAG_BYTES = TAG_BITS / 8
    private const val FORMAT_HEADER_BYTES = 1 + Int.SIZE_BYTES
    private const val MAX_PROFILE_ID_LENGTH = 256
    private const val MAX_PLAINTEXT_BYTES = 5 * 1024 * 1024
    private const val MIN_PAYLOAD_BYTES = FORMAT_HEADER_BYTES + NONCE_BYTES + TAG_BYTES
    private const val MAX_PAYLOAD_BYTES = FORMAT_HEADER_BYTES + NONCE_BYTES + MAX_PLAINTEXT_BYTES + TAG_BYTES
    private const val MAX_ENCODED_BYTES = ((MAX_PAYLOAD_BYTES + 2) / 3) * 4
}
