
package moe.rukamori.archivetune.deezer

import android.net.Uri
import androidx.core.net.toUri
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

internal object DeezerCrypto {
    const val CHUNK_SIZE = 2048

    const val ENCRYPTED_CHUNK_STRIDE = 3

    const val SCHEME = "deezer"

    private const val PARAM_URL = "u"
    private const val PARAM_KEY = "k"
    private const val PARAM_SALT = "s"

    const val DEFAULT_KEY_SALT = "g4el58wc0zvf9na1"

    private val CHUNK_IV = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7)

    fun deriveKey(
        trackId: String,
        salt: String = DEFAULT_KEY_SALT,
    ): ByteArray {
        val effective = if (salt.length >= 16) salt else DEFAULT_KEY_SALT
        val md5Hex =
            MessageDigest
                .getInstance("MD5")
                .digest(trackId.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        return ByteArray(16) { i ->
            (md5Hex[i].code xor md5Hex[i + 16].code xor effective[i].code).toByte()
        }
    }

    fun isEncryptedChunk(chunkIndex: Long): Boolean = chunkIndex % ENCRYPTED_CHUNK_STRIDE == 0L

    fun decryptChunk(
        chunk: ByteArray,
        length: Int,
        key: ByteArray,
    ) {
        if (length < 8) return

        val decryptable = length - (length % 8)
        val cipher = Cipher.getInstance("Blowfish/CBC/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "Blowfish"), IvParameterSpec(CHUNK_IV))
        cipher.doFinal(chunk, 0, decryptable, chunk, 0)
    }

    fun buildUri(
        url: String,
        trackId: String,
        salt: String? = null,
    ): String =
        Uri
            .Builder()
            .scheme(SCHEME)
            .authority("stream")
            .appendQueryParameter(PARAM_URL, url)
            .appendQueryParameter(PARAM_KEY, trackId)
            .apply {
                if (!salt.isNullOrBlank() && salt != DEFAULT_KEY_SALT) {
                    appendQueryParameter(PARAM_SALT, salt)
                }
            }.build()
            .toString()

    data class StreamRef(
        val url: Uri,
        val trackId: String,
        val salt: String,
    )

    fun parseUri(uri: Uri): StreamRef? {
        if (!uri.scheme.equals(SCHEME, ignoreCase = true)) return null
        val url = uri.getQueryParameter(PARAM_URL)?.takeIf { it.isNotBlank() } ?: return null
        val trackId = uri.getQueryParameter(PARAM_KEY)?.takeIf { it.isNotBlank() } ?: return null
        val salt = uri.getQueryParameter(PARAM_SALT)?.takeIf { it.isNotBlank() } ?: DEFAULT_KEY_SALT
        return StreamRef(url.toUri(), trackId, salt)
    }
}
