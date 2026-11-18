package com.music.bitchord.data.settings

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.music.bitchord.sync.EncryptedSyncVault
import com.music.bitchord.sync.SyncLocalState
import com.music.bitchord.sync.SyncLocalStateStore
import com.music.bitchord.sync.SyncVaultKeyProvider
import com.music.bitchord.sync.SyncVaultStorage
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Android's fail-closed, device-bound wrapper for the profile key and device token. */
internal class AndroidSyncVault(context: Context) : SyncLocalStateStore {
    private val prefs = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val vault = EncryptedSyncVault(PreferencesStorage(prefs), AndroidKeystoreKeyProvider())

    override fun load(): SyncLocalState? = vault.load()

    override fun save(state: SyncLocalState) = vault.save(state)

    override fun clear() = vault.clear()

    private class PreferencesStorage(private val prefs: SharedPreferences) : SyncVaultStorage {
        override fun read(): String? = prefs.getString(SEALED_STATE, null)

        override fun write(value: String): Boolean = prefs.edit().putString(SEALED_STATE, value).commit()

        override fun clear(): Boolean = prefs.edit().clear().commit()
    }

    private class AndroidKeystoreKeyProvider : SyncVaultKeyProvider {
        override fun getOrCreateKey(): SecretKey {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            generator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            return generator.generateKey()
        }
    }

    private companion object {
        const val FILE_NAME = "bitchord_sync_vault"
        const val SEALED_STATE = "sealed_state"
        const val KEY_ALIAS = "bitchord_sync_master_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
}
