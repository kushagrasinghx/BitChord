package com.music.bitchord.data.backup

import android.content.Context
import android.content.SharedPreferences
import com.music.bitchord.auth.AuthStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The optional cloud copy of [com.music.bitchord.data.stats.Backup]'s payload.
 *
 * Kept out of [com.music.bitchord.data.settings.AppSettings] on purpose: a
 * backup is precisely the thing that must not be copied into another backup,
 * and keeping the destination here means the export/import path cannot
 * accidentally carry a bucket or access key into a file the user shares.
 *
 * WebDAV has no destination of its own. It writes through the WebDAV source
 * the listener already configured in Sources — same URL, username and password
 * — so there is one place to fix a moved server and no second set of
 * credentials to keep in step. Only the folder inside that server belongs
 * here. S3 credentials go to [AuthStore]; everything else is ordinary prefs.
 */
object CloudBackupSettings {

    enum class Provider { WEBDAV, S3 }

    data class Config(
        val enabled: Boolean = false,
        val provider: Provider = Provider.WEBDAV,
        val webDavFolder: String = "BitChord",
        val s3Endpoint: String = "",
        val s3Region: String = "auto",
        val s3Bucket: String = "",
        val s3Prefix: String = "BitChord",
        val s3AccessKey: String = "",
        val s3PathStyle: Boolean = true,
    ) {
        fun normalized(): Config = copy(
            webDavFolder = normalizePath(webDavFolder),
            s3Endpoint = s3Endpoint.trim().trimEnd('/'),
            s3Region = s3Region.trim().ifBlank { "auto" },
            s3Bucket = s3Bucket.trim().trim('/'),
            s3Prefix = normalizePath(s3Prefix),
            s3AccessKey = s3AccessKey.trim(),
        )
    }

    private const val PREFS = "bitchord_cloud_backup"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_PROVIDER = "provider"
    private const val KEY_WEBDAV_FOLDER = "webdav_folder"
    private const val KEY_S3_ENDPOINT = "s3_endpoint"
    private const val KEY_S3_REGION = "s3_region"
    private const val KEY_S3_BUCKET = "s3_bucket"
    private const val KEY_S3_PREFIX = "s3_prefix"
    private const val KEY_S3_ACCESS_KEY = "s3_access_key"
    private const val KEY_S3_PATH_STYLE = "s3_path_style"
    private const val KEY_LAST_BACKUP_AT = "last_backup_at"
    private const val KEY_LAST_ERROR = "last_error"

    private lateinit var prefs: SharedPreferences
    private lateinit var authStore: AuthStore

    private val _config = MutableStateFlow(Config())
    val config = _config.asStateFlow()

    private val _s3SecretKey = MutableStateFlow("")
    val s3SecretKey = _s3SecretKey.asStateFlow()

    private val _s3SessionToken = MutableStateFlow("")
    val s3SessionToken = _s3SessionToken.asStateFlow()

    private val _lastBackupAt = MutableStateFlow(0L)
    val lastBackupAt = _lastBackupAt.asStateFlow()

    /** Last automatic or manual backup failure, cleared by the next success. */
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError = _lastError.asStateFlow()

    private val _working = MutableStateFlow(false)
    val working = _working.asStateFlow()

    fun init(context: Context, authStore: AuthStore) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        this.authStore = authStore
        _config.value = Config(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            provider = runCatching {
                Provider.valueOf(prefs.getString(KEY_PROVIDER, Provider.WEBDAV.name)!!)
            }.getOrDefault(Provider.WEBDAV),
            webDavFolder = prefs.getString(KEY_WEBDAV_FOLDER, "BitChord").orEmpty(),
            s3Endpoint = prefs.getString(KEY_S3_ENDPOINT, "").orEmpty(),
            s3Region = prefs.getString(KEY_S3_REGION, "auto").orEmpty(),
            s3Bucket = prefs.getString(KEY_S3_BUCKET, "").orEmpty(),
            s3Prefix = prefs.getString(KEY_S3_PREFIX, "BitChord").orEmpty(),
            s3AccessKey = prefs.getString(KEY_S3_ACCESS_KEY, "").orEmpty(),
            s3PathStyle = prefs.getBoolean(KEY_S3_PATH_STYLE, true),
        ).normalized()
        _s3SecretKey.value = authStore.cloudBackupS3SecretKey.orEmpty()
        _s3SessionToken.value = authStore.cloudBackupS3SessionToken.orEmpty()
        _lastBackupAt.value = prefs.getLong(KEY_LAST_BACKUP_AT, 0L)
        _lastError.value = prefs.getString(KEY_LAST_ERROR, null)
    }

    /**
     * Stores a completed edit. The secret arguments are write-through to
     * [AuthStore]; passing an empty string forgets a credential. WebDAV needs
     * no arguments: it reuses the Sources connection, so there is nothing of
     * its own to store.
     */
    fun save(
        config: Config,
        s3SecretKey: String,
        s3SessionToken: String,
    ) {
        val next = config.normalized()
        _config.value = next
        prefs.edit()
            .putBoolean(KEY_ENABLED, next.enabled)
            .putString(KEY_PROVIDER, next.provider.name)
            .putString(KEY_WEBDAV_FOLDER, next.webDavFolder)
            .putString(KEY_S3_ENDPOINT, next.s3Endpoint)
            .putString(KEY_S3_REGION, next.s3Region)
            .putString(KEY_S3_BUCKET, next.s3Bucket)
            .putString(KEY_S3_PREFIX, next.s3Prefix)
            .putString(KEY_S3_ACCESS_KEY, next.s3AccessKey)
            .putBoolean(KEY_S3_PATH_STYLE, next.s3PathStyle)
            .apply()

        _s3SecretKey.value = s3SecretKey
        _s3SessionToken.value = s3SessionToken
        authStore.cloudBackupS3SecretKey = s3SecretKey.ifEmpty { null }
        authStore.cloudBackupS3SessionToken = s3SessionToken.ifEmpty { null }
    }

    fun setEnabled(enabled: Boolean) {
        _config.value = _config.value.copy(enabled = enabled)
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun markSuccess(at: Long = System.currentTimeMillis()) {
        _lastBackupAt.value = at
        _lastError.value = null
        prefs.edit()
            .putLong(KEY_LAST_BACKUP_AT, at)
            .remove(KEY_LAST_ERROR)
            .apply()
    }

    fun markFailure(message: String?) {
        _lastError.value = message ?: "Backup failed"
        prefs.edit().putString(KEY_LAST_ERROR, _lastError.value).apply()
    }

    fun setWorking(value: Boolean) {
        _working.value = value
    }

    /**
     * Whether the selected provider has enough configuration to attempt a
     * transfer. The WebDAV server lives with the Sources connection rather
     * than in this store, so its URL is passed in by the caller.
     */
    fun isConfigured(config: Config = _config.value, webDavUrl: String): Boolean =
        when (config.provider) {
            Provider.WEBDAV -> com.music.bitchord.data.webdav.WebDavConfig.isConfigured(webDavUrl)
            Provider.S3 -> config.s3Endpoint.isNotBlank() && config.s3Bucket.isNotBlank()
        }

    /** Trims a relative folder/prefix and removes empty or repeated separators. */
    fun normalizePath(raw: String): String = raw
        .trim()
        .split('/')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("/")
}
