package com.music.bitchord.data.settings

import java.net.URI
import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One-use local invitation; the profile key travels directly between devices, never to the API. */
@Serializable
data class SyncInvitation(
    val serverUrl: String,
    val profileId: String,
    val pairingToken: String,
    val profileKey: String,
    val version: Int = CURRENT_VERSION,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

object SyncInvitationCodec {
    private const val PREFIX = "bitchord-sync:v1:"
    private const val MAX_INVITATION_LENGTH = 4_096
    private const val MAX_PROFILE_ID_LENGTH = 128
    private const val MAX_PAIRING_TOKEN_LENGTH = 512
    private const val KEY_BYTES = 32
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }

    fun encode(invitation: SyncInvitation): String {
        validate(invitation)
        val payload = json.encodeToString(invitation).encodeToByteArray()
        require(payload.size <= MAX_INVITATION_LENGTH) { "Sync invitation is too large" }
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(payload)
    }

    fun decode(value: String): SyncInvitation {
        require(value.length <= MAX_INVITATION_LENGTH && value.startsWith(PREFIX)) { "Invalid BitChord sync invitation" }
        val encoded = value.removePrefix(PREFIX)
        val payload = Base64.getUrlDecoder().decode(encoded)
        require(payload.size <= MAX_INVITATION_LENGTH) { "Sync invitation is too large" }
        val invitation = json.decodeFromString<SyncInvitation>(payload.decodeToString())
        validate(invitation)
        return invitation
    }

    private fun validate(invitation: SyncInvitation) {
        require(invitation.version == SyncInvitation.CURRENT_VERSION) { "Unsupported sync invitation version" }
        require(invitation.profileId.isNotBlank() && invitation.profileId.length <= MAX_PROFILE_ID_LENGTH) {
            "Invalid sync profile ID"
        }
        require(invitation.pairingToken.isNotBlank() && invitation.pairingToken.length <= MAX_PAIRING_TOKEN_LENGTH) {
            "Invalid one-time pairing token"
        }
        require(Base64.getDecoder().decode(invitation.profileKey).size == KEY_BYTES) {
            "Sync profile key must be 256 bits"
        }
        val uri = runCatching { URI(invitation.serverUrl) }
            .getOrElse { throw IllegalArgumentException("Invalid sync server URL") }
        val host = uri.host?.lowercase() ?: throw IllegalArgumentException("Sync server URL must include a host")
        val localHttp = uri.scheme.equals("http", ignoreCase = true) && host in LOCAL_HTTP_HOSTS
        require(uri.scheme.equals("https", ignoreCase = true) || localHttp) {
            "Sync invitation requires an HTTPS server URL"
        }
        require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
            "Sync server URL cannot contain credentials, query, or fragment"
        }
    }

    private val LOCAL_HTTP_HOSTS = setOf("localhost", "127.0.0.1", "::1")
}
