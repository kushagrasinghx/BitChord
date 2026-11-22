package com.music.bitchord.data.telegram

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.drinkless.tdlib.TdApi
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypted Telegram credentials and TDLib database key, kept outside Android backup. */
internal class TelegramSecureStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "telegram/credentials.enc"))
    private val alias = "bitchord.telegram.credentials.v1"

    @Synchronized
    fun read(): TelegramSecrets? {
        if (!file.baseFile.exists()) return null
        val bytes = file.openRead().use { it.readBytes() }
        require(bytes.size > 12) { "Telegram credential file is incomplete" }
        val nonce = bytes.copyOfRange(0, 12)
        val cipherText = bytes.copyOfRange(12, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, nonce))
        cipher.updateAAD(AAD)
        val json = JSONObject(String(cipher.doFinal(cipherText), Charsets.UTF_8))
        val selected = json.optJSONArray("selectedChats") ?: JSONArray()
        return TelegramSecrets(
            apiId = json.optInt("apiId"),
            apiHash = json.optString("apiHash"),
            databaseKey = json.optString("databaseKey"),
            selectedChatIds = (0 until selected.length()).mapNotNull { selected.optLong(it).takeIf { id -> id != 0L } }.toSet(),
        )
    }

    @Synchronized
    fun write(secrets: TelegramSecrets) {
        val selected = JSONArray().also { array -> secrets.selectedChatIds.sorted().forEach(array::put) }
        val plain = JSONObject()
            .put("apiId", secrets.apiId)
            .put("apiHash", secrets.apiHash)
            .put("databaseKey", secrets.databaseKey)
            .put("selectedChats", selected)
            .toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(AAD)
        val encrypted = cipher.doFinal(plain)
        val output = file.startWrite()
        try {
            output.write(cipher.iv)
            output.write(encrypted)
            file.finishWrite(output)
        } catch (error: Throwable) {
            file.failWrite(output)
            throw error
        }
    }

    @Synchronized
    fun clear() {
        file.delete()
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (store.containsAlias(alias)) store.deleteEntry(alias)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private val AAD = "bitchord.telegram.credentials.v1".toByteArray(Charsets.UTF_8)
    }
}

internal data class TelegramSecrets(
    val apiId: Int,
    val apiHash: String,
    val databaseKey: String,
    val selectedChatIds: Set<Long> = emptySet(),
)

data class TelegramChat(val id: Long, val title: String, val isChannel: Boolean, val selected: Boolean = false) {
    val displayTitle: String
        get() = title.trim().ifBlank { "Chat $id" }

    val typeLabel: String
        get() = if (isChannel) "Channel" else "Group"
}

internal fun TdApi.Chat.toTelegramChat(previous: TelegramChat? = null): TelegramChat? {
    val channel = when (val chatType = type) {
        is TdApi.ChatTypeSupergroup -> chatType.isChannel
        is TdApi.ChatTypeBasicGroup -> false
        else -> return null
    }
    return TelegramChat(
        id = id,
        title = title?.trim().orEmpty().ifBlank { previous?.title.orEmpty() },
        isChannel = channel,
        selected = previous?.selected ?: false,
    )
}

internal fun updateTelegramSelection(current: Set<Long>, chatId: Long, selected: Boolean): Set<Long> =
    if (selected) current + chatId else current - chatId

sealed interface TelegramAuthStatus {
    data object CredentialsRequired : TelegramAuthStatus
    data object Starting : TelegramAuthStatus
    data object PhoneNumberRequired : TelegramAuthStatus
    data object CodeRequired : TelegramAuthStatus
    data object EmailAddressRequired : TelegramAuthStatus
    data object EmailCodeRequired : TelegramAuthStatus
    data object PasswordRequired : TelegramAuthStatus
    data class Ready(val accountName: String, val phoneNumber: String) : TelegramAuthStatus
    data class Error(val message: String) : TelegramAuthStatus
}
